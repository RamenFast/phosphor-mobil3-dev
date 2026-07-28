package dev.phosphor.mobil3.nexus.tailnet

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class PhosphorTailnetRuntimeTest {
    private val endpointIdentity = "relay-ed25519:abc123"

    @Test fun welcomeAndPFramesUseExactTagsBigEndianAndRejectOversizeBeforeAllocation() {
        val codec = TailnetPFrameCodec(maxFrameBytes = 64)
        val welcome = codec.encode(PHOSPHOR_TAILNET_WELCOME_TAG, TailnetRelayJson.welcome())
        assertEquals(0x57, welcome[0].toInt() and 0xff)
        assertEquals(welcome.size - 5, beLen(welcome))
        assertTrue(codec.readWelcome(ByteArrayInputStream(welcome)).getJSONObject("caps").getBoolean("phosphor_rpc"))
        val p = codec.encode(PHOSPHOR_TAILNET_FRAME_TAG, JSONObject().put("t", "ack"))
        assertEquals(0x50, p[0].toInt() and 0xff)
        assertEquals("ack", codec.readP(ByteArrayInputStream(p)).getString("t"))
        assertFailsWith<TailnetProtocolException> { codec.readP(ByteArrayInputStream(byteArrayOf(0x50, 0, 0, 0, 65))) }
    }

    @Test fun proofUsesUtf8PartsEachFollowedByNulExactly() {
        val actual = TailnetRelayJson.proof("p", "eid", "tid", 7, "cn", "sn", "secret")
        val md = MessageDigest.getInstance("SHA-256")
        listOf(TailnetRelayJson.CLIENT_AUTH_DOMAIN, PHOSPHOR_TAILNET_RPC_PROTOCOL, "p", "eid", "tid", "7", "cn", "sn", "secret").forEach { md.update(it.toByteArray(Charsets.UTF_8)); md.update(0) }
        assertEquals(md.digest().joinToString("") { "%02x".format(it) }, actual)
    }

    @Test fun loopbackAuthStateAuditHudAndRefusalUseRelayJson() {
        System.setProperty("phosphor.tailnet.tests.allowLoopback", "true")
        val state = RecordingAuthority()
        val gotDispatches = CountDownLatch(4)
        FakeRelay { socket ->
            val codec = TailnetPFrameCodec(); val input = socket.getInputStream(); val output = socket.getOutputStream()
            output.write(codec.encode(0x57, TailnetRelayJson.welcome())); output.flush()
            val hello = codec.readP(input)
            assertEquals("hello", hello.getString("t"))
            val h = hello.getJSONObject("hello")
            assertEquals(PHOSPHOR_TAILNET_RPC_PROTOCOL, h.getString("protocol"))
            val ident = h.getJSONObject("identity")
            assertEquals(endpointIdentity, ident.getString("pinned_endpoint_identity"))
            val gen = ident.getLong("generation")
            val clientNonce = h.getString("client_nonce")
            val now = System.currentTimeMillis()
            codec.writeP(output, challenge(gen, clientNonce, "server-nonce", now))
            val auth = codec.readP(input)
            assertEquals(TailnetRelayJson.proof("principal", endpointIdentity, "token-id", gen, clientNonce, "server-nonce", "super-secret"), auth.getString("proof"))
            codec.writeP(output, JSONObject().put("t", "authenticated").put("generation", gen).put("heartbeat_ms", 1000).put("lease_ms", 4000))
            val hb = codec.readP(input); assertEquals("heartbeat", hb.getString("t"))
            codec.writeP(output, JSONObject().put("t", "ack").put("generation", gen).put("sequence", hb.getLong("sequence")).put("accepted", true))
            codec.readP(input) // observe poll
            codec.writeP(output, JSONObject().put("t", "observe").put("generation", gen).put("requests", JSONArray()
                .put(req("s", gen, 1, "state.get"))
                .put(req("a", gen, 2, "audit.list"))
                .put(req("h", gen, 3, "display.hud.set"))
                .put(req("x", gen, 4, "unknown.op"))))
            repeat(4) {
                val d = codec.readP(input)
                assertEquals("dispatch", d.getString("t"))
                assertTrue(d.getJSONObject("identity").getLong("generation") == gen)
                if (d.getString("request_id") == "x") assertFalse(d.getBoolean("accepted"))
                gotDispatches.countDown()
            }
        }.use { relay ->
            val runtime = runtime(relay.port, state)
            runtime.start()
            assertTrue(gotDispatches.await(4, TimeUnit.SECONDS), relay.errors.joinToString("\n") { it.stackTraceToString() })
            runtime.shutdown()
        }
        assertEquals(listOf("state", "audit", "hud"), state.calls)
    }

    @Test fun heartbeatThreeMissClosingFourAbsentAndLateAckCannotRevive() {
        val policy = TailnetHeartbeatPolicy(1_000)
        assertEquals(TailnetPresence.CLOSING, policy.evaluate(3, 3_000, true))
        assertEquals(TailnetPresence.ABSENT, policy.evaluate(4, 4_000, true))
        val state = RecordingAuthority()
        state.closing(TailnetIdentity("p", endpointIdentity, "t", 1), "heartbeat_missed_3")
        state.absent(TailnetIdentity("p", endpointIdentity, "t", 1), "heartbeat_missed_4")
        assertTrue(state.absentGenerations.contains(1))
        assertFalse(state.lateAckAccepted(TailnetIdentity("p", endpointIdentity, "t", 1), 99))
    }

    @Test fun reconnectAdvancesGenerationAndShutdownStopsWorker() {
        System.setProperty("phosphor.tailnet.tests.allowLoopback", "true")
        val generations = Collections.synchronizedList(mutableListOf<Long>())
        val auths = CountDownLatch(2)
        FakeRelay(maxAccepts = 2) { socket ->
            val codec = TailnetPFrameCodec(); val input = socket.getInputStream(); val output = socket.getOutputStream()
            output.write(codec.encode(0x57, TailnetRelayJson.welcome())); output.flush()
            val hello = codec.readP(input).getJSONObject("hello")
            val gen = hello.getJSONObject("identity").getLong("generation"); generations += gen
            codec.writeP(output, challenge(gen, hello.getString("client_nonce"), "s$gen", System.currentTimeMillis()))
            codec.readP(input)
            codec.writeP(output, JSONObject().put("t", "authenticated").put("generation", gen).put("heartbeat_ms", 1000).put("lease_ms", 4000))
            auths.countDown()
        }.use { relay ->
            val runtime = runtime(relay.port, RecordingAuthority())
            runtime.start()
            assertTrue(auths.await(5, TimeUnit.SECONDS), relay.errors.joinToString("\n") { it.stackTraceToString() })
            runtime.shutdown()
        }
        assertEquals(listOf(1L, 2L), generations)
    }

    @Test fun productionTailnetCodeDoesNotBindListenDiscoverOrFallback() = assertNoProductionMatches("\\b(ServerSocket|DatagramSocket|MulticastSocket|NetworkInterface|accept\\s*\\(|listen\\s*\\()|localhost fallback|discovery")
    @Test fun productionTailnetCodeDoesNotCreateParallelNexusAuthority() = assertNoProductionMatches("(NexusObservationDispatcher\\s*\\(|PhosphorStateStore\\s*\\(|NexusNonceLedger\\s*\\(|NexusTokenGrant\\.of|object\\s*:\\s*NexusRuntimeDispatcher)")
    @Test fun productionTailnetHasNoDeprecatedLegacyAuthoritySurface() = assertNoProductionMatches("Deprecated|LegacyAuthority|TailnetRuntimeDispatcherAdapter")
    @Test fun profileAllowsOnlyNexusOrLocalDevAndCarriesToAuthenticate() {
        TailnetRuntimeConfig("example.invalid", 7443, endpointIdentity, "token-id", "secret", "principal", profile = "nexus")
        TailnetRuntimeConfig("example.invalid", 7443, endpointIdentity, "token-id", "secret", "principal", profile = "local_dev")
        assertFailsWith<IllegalArgumentException> { TailnetRuntimeConfig("example.invalid", 7443, endpointIdentity, "token-id", "secret", "principal", profile = "fortress") }
    }
    @Test fun redactsSecrets() {
        val redacted = TailnetRedactor.redact("token_id:AAA token=BBB nonce=CCC secret=DDD proof=EEE pinned_endpoint_identity=FFF")
        listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF").forEach { assertFalse(redacted.contains(it)) }
    }

    private fun assertNoProductionMatches(pattern: String) {
        val root = Paths.get("src/fortress/kotlin/dev/phosphor/mobil3/nexus/tailnet")
        val forbidden = Regex(pattern)
        val offenders = Files.walk(root).filter { it.toString().endsWith(".kt") }.flatMap { path ->
            Files.readAllLines(path).mapIndexed { index, line -> "${path}:${index + 1}:$line" }.stream()
        }.filter { line -> forbidden.containsMatchIn(line.substringBefore("//")) }.toList()
        assertEquals(emptyList(), offenders)
    }

    private fun beLen(bytes: ByteArray) = ((bytes[1].toInt() and 0xff) shl 24) or ((bytes[2].toInt() and 0xff) shl 16) or ((bytes[3].toInt() and 0xff) shl 8) or (bytes[4].toInt() and 0xff)
    private fun challenge(generation: Long, clientNonce: String, serverNonce: String, now: Long) = JSONObject().put("t", "challenge").put("challenge", JSONObject()
        .put("server_nonce", serverNonce)
        .put("client_nonce", clientNonce)
        .put("issued_at_ms", now)
        .put("expires_at_ms", now + 30_000)
        .put("server_proof", TailnetRelayJson.serverProof("principal", endpointIdentity, "token-id", generation, clientNonce, serverNonce, "super-secret")))
    private fun req(id: String, generation: Long, sequence: Long, op: String) = JSONObject().put("id", id).put("generation", generation).put("sequence", sequence).put("operation", JSONObject().put("op", op).put("args", JSONObject()))
    private fun runtime(port: Int, state: RecordingAuthority) = PhosphorTailnetRuntime(TailnetRuntimeConfig("127.0.0.1", port, endpointIdentity, "token-id", "super-secret", "principal", profile = "local_dev", heartbeatMillis = 1000, reconnectBaseMillis = 1, reconnectMaxMillis = 2, jitterMillis = 0), state)

    private class RecordingAuthority : SharedNexusRuntimeAuthority {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val absentGenerations = Collections.synchronizedSet(mutableSetOf<Long>())
        var revived = false
        val authRequests = Collections.synchronizedList(mutableListOf<TailnetAuthenticationRequest>())
        override fun authenticate(request: TailnetAuthenticationRequest): TailnetSessionAttachment {
            authRequests += request
            assertEquals(PHOSPHOR_TAILNET_RPC_PROTOCOL, request.protocol)
            assertEquals("local_dev", request.profile)
            assertEquals(setOf("observe.state", "observe.audit", "control.display"), request.requestedPhosphorCapabilities)
            return TailnetSessionAttachment(TailnetSession("s-${request.identity.generation}", request.identity.generation, TailnetEndpoint("relay", 1, request.identity.pinnedEndpointIdentity)))
        }
        override fun observe(identity: TailnetIdentity): JSONObject { calls += "state"; return JSONObject().put("status", "ok").put("state", true) }
        override fun audit(identity: TailnetIdentity, args: JSONObject): JSONObject { calls += "audit"; return JSONObject().put("status", "ok").put("events", JSONArray()) }
        override fun dispatch(identity: TailnetIdentity, operation: TailnetOperation): TailnetDispatchResult { calls += "hud"; return TailnetDispatchResult.accepted(JSONObject().put("status", "ok").put("hud", operation.args)) }
        fun lateAckAccepted(identity: TailnetIdentity, sequence: Long): Boolean = !absentGenerations.contains(identity.generation)
        override fun heartbeat(identity: TailnetIdentity, sequence: Long, monotonicMillis: Long) { if (absentGenerations.contains(identity.generation)) revived = true }
        override fun closing(identity: TailnetIdentity, reason: String) = Unit
        override fun absent(identity: TailnetIdentity, reason: String) { absentGenerations += identity.generation }
        override fun disconnect(identity: TailnetIdentity, reason: String) = Unit
    }

    private class FakeRelay(private val maxAccepts: Int = 1, private val handler: (Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        private val thread = thread(name = "fake-tailnet-relay", isDaemon = true) {
            repeat(maxAccepts) { runCatching { server.accept().use { socket -> socket.soTimeout = 2_000; handler(socket) } }.onFailure { errors += it } }
        }
        override fun close() { server.close(); thread.join(1_000) }
    }
}
