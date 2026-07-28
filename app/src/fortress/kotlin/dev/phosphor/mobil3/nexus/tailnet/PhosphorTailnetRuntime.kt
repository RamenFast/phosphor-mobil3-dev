package dev.phosphor.mobil3.nexus.tailnet

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

const val PHOSPHOR_TAILNET_RPC_PROTOCOL = "phosphor.tailnet.rpc/1"
const val PHOSPHOR_TAILNET_WELCOME_TAG: Int = 0x57
const val PHOSPHOR_TAILNET_FRAME_TAG: Int = 0x50
const val PHOSPHOR_TAILNET_MAX_JSON_FRAME_BYTES: Int = 256 * 1024
private const val MIN_HEARTBEAT_MS = 1_000L
private const val MAX_HEARTBEAT_MS = 5_000L
private const val MAX_PENDING_WRITES = 64

data class TailnetRuntimeConfig(
    val host: String,
    val port: Int,
    val endpointIdentity: String,
    val tokenId: String,
    val tokenSecret: String,
    val principal: String,
    val profile: String = "nexus",
    val requestedCapabilities: Set<String> = setOf("observe.state", "observe.audit", "control.display"),
    val heartbeatMillis: Long = 2_000L,
    val connectTimeoutMillis: Int = 2_000,
    val maxFrameBytes: Int = PHOSPHOR_TAILNET_MAX_JSON_FRAME_BYTES,
    val reconnectBaseMillis: Long = 500L,
    val reconnectMaxMillis: Long = 30_000L,
    val jitterMillis: Long = 250L,
) {
    constructor(
        endpoints: List<TailnetEndpoint>,
        tokenId: String,
        tokenSecret: String,
        principal: String = "phosphor-mobil3-fortress",
        heartbeatMillis: Long = 2_000L,
        connectTimeoutMillis: Int = 2_000,
        maxFrameBytes: Int = PHOSPHOR_TAILNET_MAX_JSON_FRAME_BYTES,
        reconnectBaseMillis: Long = 500L,
        reconnectMaxMillis: Long = 30_000L,
        jitterMillis: Long = 250L,
    ) : this(endpoints.singleOrNull()?.host ?: "", endpoints.singleOrNull()?.port ?: 0, endpoints.singleOrNull()?.pinnedIdentitySha256 ?: "", tokenId, tokenSecret, principal, "nexus", setOf("observe.state", "observe.audit", "control.display"), heartbeatMillis, connectTimeoutMillis, maxFrameBytes, reconnectBaseMillis, reconnectMaxMillis, jitterMillis)

    init {
        require(host.isNotBlank()) { "tailnet host required" }
        require(!host.equals("localhost", ignoreCase = true) && host != "127.0.0.1" || java.lang.Boolean.getBoolean("phosphor.tailnet.tests.allowLoopback")) { "tailnet endpoint must be explicit and non-loopback" }
        require(port in 1..65535) { "tailnet port out of range" }
        require(endpointIdentity.isNotBlank()) { "tailnet endpoint identity required" }
        require(tokenId.isNotBlank()) { "tailnet token id required" }
        require(tokenSecret.isNotBlank()) { "tailnet token secret required" }
        require(principal.isNotBlank()) { "tailnet principal required" }
        require(profile == "nexus" || profile == "local_dev") { "tailnet profile must be nexus or local_dev" }
        require(requestedCapabilities.isNotEmpty()) { "tailnet capabilities required" }
        require(heartbeatMillis in MIN_HEARTBEAT_MS..MAX_HEARTBEAT_MS) { "heartbeat must be 1-5s" }
        require(connectTimeoutMillis in 1..30_000) { "connect timeout out of range" }
        require(maxFrameBytes in 1..PHOSPHOR_TAILNET_MAX_JSON_FRAME_BYTES) { "max frame too large" }
        require(reconnectBaseMillis in 1..reconnectMaxMillis) { "backoff base must be bounded" }
        require(reconnectMaxMillis <= 300_000L) { "backoff max must be bounded" }
        require(jitterMillis in 0..reconnectMaxMillis) { "jitter must be bounded" }
    }
}

data class TailnetEndpoint(val host: String, val port: Int, val pinnedIdentitySha256: String)
data class TailnetSession(val id: String, val generation: Long, val endpoint: TailnetEndpoint)
enum class TailnetTransport { TAILNET }
data class TailnetSessionAttachment(val session: TailnetSession, val transport: TailnetTransport = TailnetTransport.TAILNET, val replacedSession: TailnetSession? = null)
enum class TailnetPresence { PRESENT, CLOSING, ABSENT }

interface SharedNexusRuntimeAuthority {
    fun authenticate(request: TailnetAuthenticationRequest): TailnetSessionAttachment
    fun observe(identity: TailnetIdentity): JSONObject
    fun audit(identity: TailnetIdentity, args: JSONObject): JSONObject
    fun dispatch(identity: TailnetIdentity, operation: TailnetOperation): TailnetDispatchResult
    fun heartbeat(identity: TailnetIdentity, sequence: Long, monotonicMillis: Long)
    fun closing(identity: TailnetIdentity, reason: String)
    fun absent(identity: TailnetIdentity, reason: String)
    fun disconnect(identity: TailnetIdentity, reason: String)
}

data class TailnetAuthenticationRequest(
    val identity: TailnetIdentity,
    val endpoint: TailnetEndpoint,
    val protocol: String,
    val profile: String,
    val requestedPhosphorCapabilities: Set<String>,
    val heartbeatMillis: Long,
    val leaseMillis: Long,
)

data class TailnetIdentity(val principal: String, val pinnedEndpointIdentity: String, val tokenId: String, val generation: Long) {
    fun json(): JSONObject = JSONObject().put("principal", principal).put("pinned_endpoint_identity", pinnedEndpointIdentity).put("token_id", tokenId).put("generation", generation)
}

data class TailnetOperation(val op: String, val args: JSONObject)
data class TailnetDispatchResult(val accepted: Boolean, val result: JSONObject) {
    companion object {
        fun accepted(result: JSONObject): TailnetDispatchResult = TailnetDispatchResult(true, result)
        fun refused(result: JSONObject): TailnetDispatchResult = TailnetDispatchResult(false, result)
    }
}

interface TailnetLogger { fun info(message: String); fun warn(message: String) }
object NoopTailnetLogger : TailnetLogger { override fun info(message: String) = Unit; override fun warn(message: String) = Unit }
class TailnetProtocolException(message: String) : Exception(message)

class TailnetHeartbeatPolicy(val intervalMillis: Long, val closingAfterMisses: Int = 3, val absentAfterMisses: Int = 4) {
    init { require(intervalMillis in MIN_HEARTBEAT_MS..MAX_HEARTBEAT_MS); require(closingAfterMisses in 1..absentAfterMisses) }
    fun evaluate(missedHeartbeats: Int, nowMonotonicMillis: Long, activeLifecycle: Boolean): TailnetPresence {
        require(nowMonotonicMillis >= 0L)
        if (!activeLifecycle) throw TailnetProtocolException("heartbeat lifecycle is no longer active")
        return when { missedHeartbeats >= absentAfterMisses -> TailnetPresence.ABSENT; missedHeartbeats >= closingAfterMisses -> TailnetPresence.CLOSING; else -> TailnetPresence.PRESENT }
    }
}

object TailnetRedactor {
    private val sensitive = Regex("(?i)(token_id|token|nonce|secret|proof|pinned_endpoint_identity|endpoint_identity)\"?\\s*[:=]\\s*\"?[^,}\\s\"]+")
    fun redact(value: String): String = sensitive.replace(value) { m -> m.value.substringBefore(':').substringBefore('=').trim() + "=<redacted>" }
}

class TailnetPFrameCodec(private val maxFrameBytes: Int = PHOSPHOR_TAILNET_MAX_JSON_FRAME_BYTES) {
    init { require(maxFrameBytes in 1..PHOSPHOR_TAILNET_MAX_JSON_FRAME_BYTES) }
    fun readWelcome(input: InputStream): JSONObject = read(input, PHOSPHOR_TAILNET_WELCOME_TAG)
    fun readP(input: InputStream): JSONObject = read(input, PHOSPHOR_TAILNET_FRAME_TAG)
    fun read(input: InputStream, expectedTag: Int = PHOSPHOR_TAILNET_FRAME_TAG): JSONObject {
        val tag = input.read(); if (tag < 0) throw EOFException("tailnet frame eof")
        if (tag != expectedTag) throw TailnetProtocolException("frame tag rejected")
        val header = input.readExactly(4)
        val size = ((header[0].toInt() and 0xff) shl 24) or ((header[1].toInt() and 0xff) shl 16) or ((header[2].toInt() and 0xff) shl 8) or (header[3].toInt() and 0xff)
        if (size < 0 || size > maxFrameBytes) throw TailnetProtocolException("frame size rejected")
        return JSONObject(input.readExactly(size).toString(Charsets.UTF_8))
    }
    fun encode(tag: Int, json: JSONObject): ByteArray {
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        if (payload.size > maxFrameBytes) throw TailnetProtocolException("frame size rejected")
        return byteArrayOf(tag.toByte(), (payload.size ushr 24).toByte(), (payload.size ushr 16).toByte(), (payload.size ushr 8).toByte(), payload.size.toByte()) + payload
    }
    @Synchronized fun writeP(output: OutputStream, json: JSONObject) { output.write(encode(PHOSPHOR_TAILNET_FRAME_TAG, json)); output.flush() }
    @Synchronized fun write(output: OutputStream, json: JSONObject) = writeP(output, json)
    private fun InputStream.readExactly(count: Int): ByteArray { val b = ByteArray(count); var o = 0; while (o < count) { val n = read(b, o, count - o); if (n < 0) throw EOFException("tailnet frame eof"); o += n }; return b }
}
typealias TailnetFrameCodec = TailnetPFrameCodec

object TailnetRelayJson {
    const val CLIENT_AUTH_DOMAIN = "phosphor.tailnet.rpc.client-auth/v1"
    const val SERVER_CHALLENGE_DOMAIN = "phosphor.tailnet.rpc.server-challenge/v1"
    fun proof(principal: String, pinnedEndpointIdentity: String, tokenId: String, generation: Long, clientNonce: String, serverNonce: String, tokenSecret: String): String {
        return digest(CLIENT_AUTH_DOMAIN, principal, pinnedEndpointIdentity, tokenId, generation, clientNonce, serverNonce, tokenSecret)
    }
    fun serverProof(principal: String, pinnedEndpointIdentity: String, tokenId: String, generation: Long, clientNonce: String, serverNonce: String, tokenSecret: String): String {
        return digest(SERVER_CHALLENGE_DOMAIN, principal, pinnedEndpointIdentity, tokenId, generation, clientNonce, serverNonce, tokenSecret)
    }
    private fun digest(domain: String, principal: String, pinnedEndpointIdentity: String, tokenId: String, generation: Long, clientNonce: String, serverNonce: String, tokenSecret: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        listOf(domain, PHOSPHOR_TAILNET_RPC_PROTOCOL, principal, pinnedEndpointIdentity, tokenId, generation.toString(), clientNonce, serverNonce, tokenSecret).forEach { part -> md.update(part.toByteArray(Charsets.UTF_8)); md.update(0) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    fun welcome(): JSONObject = JSONObject().put("proto", 1).put("caps", JSONObject().put("phosphor_rpc", true))
    fun hello(config: TailnetRuntimeConfig, generation: Long, clientNonce: String, issuedAtMs: Long): JSONObject = JSONObject().put("t", "hello").put("hello", JSONObject().put("protocol", PHOSPHOR_TAILNET_RPC_PROTOCOL).put("identity", identity(config, generation).json()).put("client_nonce", clientNonce).put("issued_at_ms", issuedAtMs))
    fun auth(proof: String, presentedAtMs: Long): JSONObject = JSONObject().put("t", "auth").put("proof", proof).put("presented_at_ms", presentedAtMs)
    fun observe(identity: TailnetIdentity, sequence: Long): JSONObject = JSONObject().put("t", "observe").put("identity", identity.json()).put("sequence", sequence)
    fun dispatch(identity: TailnetIdentity, sequence: Long, requestId: String, accepted: Boolean, result: JSONObject): JSONObject = JSONObject().put("t", "dispatch").put("identity", identity.json()).put("sequence", sequence).put("request_id", requestId).put("accepted", accepted).put("result", result)
    fun heartbeat(identity: TailnetIdentity, sequence: Long, monotonicMs: Long): JSONObject = JSONObject().put("t", "heartbeat").put("identity", identity.json()).put("sequence", sequence).put("monotonic_ms", monotonicMs)
    fun disconnect(identity: TailnetIdentity, sequence: Long, reason: String): JSONObject = JSONObject().put("t", "disconnect").put("identity", identity.json()).put("sequence", sequence).put("reason", reason)
    fun identity(config: TailnetRuntimeConfig, generation: Long) = TailnetIdentity(config.principal, config.endpointIdentity, config.tokenId, generation)
}

class PhosphorTailnetRuntime(
    private val config: TailnetRuntimeConfig,
    private val sharedAuthority: SharedNexusRuntimeAuthority,
    private val logger: TailnetLogger = NoopTailnetLogger,
    private val socketFactory: (TailnetRuntimeConfig) -> Socket = { c -> Socket().apply { connect(InetSocketAddress(c.host, c.port), c.connectTimeoutMillis) } },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val randomBytes: (Int) -> ByteArray = { n -> ByteArray(n).also { SecureRandom().nextBytes(it) } },
    private val nowWallMillis: () -> Long = { System.currentTimeMillis() },
    private val nowMonotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val running = AtomicBoolean(false)
    private val authenticatedGenerations = Collections.synchronizedSet(mutableSetOf<Long>())
    private var worker: Thread? = null
    @Volatile private var activeSocket: Socket? = null
    private var generation = 0L

    fun start() { if (running.compareAndSet(false, true)) worker = thread(name = "phosphor-tailnet-client", isDaemon = true) { connectionLoop() } }
    fun shutdown() { stop("shutdown") }
    fun stop(reason: String = "stop") { running.set(false); activeSocket?.close(); worker?.interrupt(); worker?.join(2_000); worker = null }

    private fun connectionLoop() {
        var failures = 0
        while (running.get()) {
            val gen = ++generation
            val identity = TailnetRelayJson.identity(config, gen)
            try { socketFactory(config).use { socket -> activeSocket = socket; runSession(socket, identity); failures = 0 } }
            catch (e: Exception) { logger.warn("tailnet closed ${config.host}:${config.port} ${TailnetRedactor.redact(e.message ?: e.javaClass.simpleName)}") }
            finally { activeSocket = null; if (authenticatedGenerations.remove(identity.generation)) sharedAuthority.disconnect(identity, "connection_closed") }
            if (running.get()) sleeper(backoff(failures++))
        }
    }

    private fun runSession(socket: Socket, identity: TailnetIdentity) {
        socket.soTimeout = config.heartbeatMillis.toInt()
        val codec = TailnetPFrameCodec(config.maxFrameBytes)
        val input = socket.getInputStream(); val output = socket.getOutputStream()
        val welcome = codec.readWelcome(input)
        if (welcome.optInt("proto") != 1 || welcome.optJSONObject("caps")?.optBoolean("phosphor_rpc") != true) throw TailnetProtocolException("welcome rejected")
        val clientNonce = randomBytes(16).joinToString("") { "%02x".format(it) }
        val writer = Writer(output, codec); writer.start()
        var sequence = 0L
        fun send(json: JSONObject) { writer.send(json) }
        try {
            send(TailnetRelayJson.hello(config, identity.generation, clientNonce, nowWallMillis()))
            val challengeFrame = codec.readP(input)
            val challenge = challengeFrame.requireObject("challenge")
            if (challengeFrame.optString("t") != "challenge" || challenge.optString("client_nonce") != clientNonce) throw TailnetProtocolException("challenge rejected")
            val issuedAt = challenge.getLong("issued_at_ms")
            val expiresAt = challenge.getLong("expires_at_ms")
            val now = nowWallMillis()
            if (issuedAt > now || expiresAt <= issuedAt || now > expiresAt) throw TailnetProtocolException("challenge lifetime rejected")
            val serverNonce = challenge.getString("server_nonce")
            val expectedServerProof = TailnetRelayJson.serverProof(identity.principal, identity.pinnedEndpointIdentity, identity.tokenId, identity.generation, clientNonce, serverNonce, config.tokenSecret)
            if (!constantTimeEquals(challenge.getString("server_proof"), expectedServerProof)) throw TailnetProtocolException("server proof rejected")
            val proof = TailnetRelayJson.proof(identity.principal, identity.pinnedEndpointIdentity, identity.tokenId, identity.generation, clientNonce, serverNonce, config.tokenSecret)
            send(TailnetRelayJson.auth(proof, nowWallMillis()))
            val authenticated = codec.readP(input)
            if (authenticated.optString("t") != "authenticated") throw TailnetProtocolException("auth rejected")
            if (authenticated.optLong("generation") != identity.generation) throw TailnetProtocolException("stale generation rejected")
            val heartbeatMs = authenticated.optLong("heartbeat_ms", config.heartbeatMillis).coerceIn(MIN_HEARTBEAT_MS, MAX_HEARTBEAT_MS)
            val leaseMs = authenticated.optLong("lease_ms", heartbeatMs * 4)
            if (authenticated.optLong("heartbeat_ms", config.heartbeatMillis) !in MIN_HEARTBEAT_MS..MAX_HEARTBEAT_MS || leaseMs < heartbeatMs * 4 || leaseMs > heartbeatMs * 8) throw TailnetProtocolException("authenticated heartbeat lease rejected")
            val attachment = sharedAuthority.authenticate(
                TailnetAuthenticationRequest(
                    identity = identity,
                    endpoint = TailnetEndpoint(config.host, config.port, config.endpointIdentity),
                    protocol = PHOSPHOR_TAILNET_RPC_PROTOCOL,
                    profile = config.profile,
                    requestedPhosphorCapabilities = config.requestedCapabilities,
                    heartbeatMillis = heartbeatMs,
                    leaseMillis = leaseMs,
                ),
            )
            if (attachment.session.generation != identity.generation) throw TailnetProtocolException("attachment generation rejected")
            authenticatedGenerations += identity.generation
            pump(input, codec, writer, identity, heartbeatMs) { ++sequence }
        } finally { writer.close(); writer.join() }
    }

    private fun pump(input: InputStream, codec: TailnetPFrameCodec, writer: Writer, identity: TailnetIdentity, heartbeatMs: Long, nextSeq: () -> Long) {
        var misses = 0
        var closing = false
        while (running.get() && identity.generation == generation) {
            val hbSeq = nextSeq(); writer.send(TailnetRelayJson.heartbeat(identity, hbSeq, nowMonotonicMillis()))
            writer.send(TailnetRelayJson.observe(identity, nextSeq()))
            val deadline = nowMonotonicMillis() + heartbeatMs
            var acked = false
            while (running.get() && nowMonotonicMillis() <= deadline) {
                val msg = try { codec.readP(input) } catch (_: SocketTimeoutException) { break }
                when (msg.optString("t")) {
                    "ack" -> if (msg.optLong("generation") == identity.generation && msg.optLong("sequence") == hbSeq && msg.optBoolean("accepted")) acked = true
                    "observe" -> handleObserve(msg, identity, writer, nextSeq)
                    else -> throw TailnetProtocolException("unexpected message")
                }
            }
            misses = if (acked && !closing) 0 else misses + 1
            when (TailnetHeartbeatPolicy(heartbeatMs).evaluate(misses, nowMonotonicMillis(), running.get() && identity.generation == generation)) {
                TailnetPresence.PRESENT -> if (acked) sharedAuthority.heartbeat(identity, hbSeq, nowMonotonicMillis())
                TailnetPresence.CLOSING -> {
                    if (!closing) sharedAuthority.closing(identity, "heartbeat_missed_3")
                    closing = true
                }
                TailnetPresence.ABSENT -> { sharedAuthority.absent(identity, "heartbeat_missed_4"); throw TailnetProtocolException("heartbeat absent") }
            }
        }
    }

    private fun handleObserve(msg: JSONObject, identity: TailnetIdentity, writer: Writer, nextSeq: () -> Long) {
        if (msg.optLong("generation") != identity.generation) throw TailnetProtocolException("stale generation rejected")
        val requests = msg.optJSONArray("requests") ?: JSONArray()
        for (i in 0 until requests.length()) {
            val req = requests.getJSONObject(i)
            if (req.optLong("generation") != identity.generation) throw TailnetProtocolException("stale request rejected")
            val operation = req.requireObject("operation")
            val result = runCatching { execute(identity, TailnetOperation(operation.getString("op"), operation.optJSONObject("args") ?: JSONObject())) }
                .getOrElse { false to JSONObject().put("status", "refused").put("reason", "adapter_refused").put("error", it.message ?: it.javaClass.simpleName) }
            writer.send(TailnetRelayJson.dispatch(identity, nextSeq(), req.getString("id"), result.first, result.second))
        }
    }

    private fun execute(identity: TailnetIdentity, operation: TailnetOperation): Pair<Boolean, JSONObject> = when (operation.op) {
        "state.get" -> true to sharedAuthority.observe(identity)
        "audit.list" -> true to sharedAuthority.audit(identity, operation.args)
        "display.hud.set" -> sharedAuthority.dispatch(identity, operation).let { it.accepted to it.result }
        else -> false to JSONObject().put("status", "refused").put("reason", "unsupported_operation").put("op", operation.op)
    }

    private fun backoff(failures: Int): Long {
        val exp = config.reconnectBaseMillis * (1L shl min(failures, 6))
        val jitter = if (config.jitterMillis == 0L) 0 else ((randomBytes(1)[0].toInt() and 0xff) % (config.jitterMillis + 1)).toLong()
        return min(config.reconnectMaxMillis, exp + jitter)
    }

    private class Writer(private val output: OutputStream, private val codec: TailnetPFrameCodec) {
        private val queue = LinkedBlockingQueue<JSONObject>(MAX_PENDING_WRITES)
        private val open = AtomicBoolean(true)
        private val thread = thread(start = false, name = "phosphor-tailnet-writer", isDaemon = true) { while (open.get() || queue.isNotEmpty()) codec.writeP(output, queue.poll(100, TimeUnit.MILLISECONDS) ?: continue) }
        fun start() = thread.start()
        fun send(json: JSONObject) { if (!queue.offer(json, 250, TimeUnit.MILLISECONDS)) throw TailnetProtocolException("tailnet writer queue full") }
        fun close() { open.set(false) }
        fun join() { thread.join(2_000) }
    }

    companion object {
        fun authDigest(token: String, serverNonce: String, clientNonce: String): String = TailnetRelayJson.proof("legacy", "endpoint", "default", 0, clientNonce, serverNonce, token)
        fun parseSeededRemoteHosts(value: String, token: String): TailnetRuntimeConfig { val parts = value.trim().split('|'); val hp = parts[0].split(':'); return TailnetRuntimeConfig(hp[0], hp[1].toInt(), parts[1], "default", token, "phosphor-mobil3-fortress") }
    }
}

private fun JSONObject.requireObject(name: String): JSONObject = optJSONObject(name) ?: throw TailnetProtocolException("$name required")

private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var result = 0
    for (i in a.indices) result = result or (a[i].code xor b[i].code)
    return result == 0
}
