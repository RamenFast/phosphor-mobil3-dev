package dev.phosphor.mobil3.nexus.binder

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Binder
import android.os.IBinder
import dev.phosphor.mobil3.nexus.NexusClosureCause
import org.json.JSONObject
import java.security.MessageDigest
import java.util.LinkedHashMap

internal data class NexusBinderCaller(
    val uid: Int,
    val packageName: String,
    val currentSigningCertificateSha256: String,
    val signingLineageSha256: List<String>,
)

internal data class NexusBinderCallerPolicy(
    val packageName: String,
    val acceptedCurrentSigningCertificateSha256: String,
    val acceptedSigningLineageSha256: List<String>,
) {
    init {
        require(packageName.isNotBlank())
        require(acceptedCurrentSigningCertificateSha256.isCanonicalSha256()) {
            "Binder current signer must be a canonical lowercase SHA-256 hex digest."
        }
        require(acceptedSigningLineageSha256.isNotEmpty())
        require(acceptedSigningLineageSha256.all { it.isCanonicalSha256() }) {
            "Binder signing lineage must contain only canonical lowercase SHA-256 hex digests."
        }
        require(acceptedSigningLineageSha256.toSet().size == acceptedSigningLineageSha256.size) {
            "Binder signing lineage must be distinct and ordered."
        }
        require(acceptedSigningLineageSha256.last() == acceptedCurrentSigningCertificateSha256) {
            "Binder current signer must be the final certificate in the accepted signing lineage."
        }
    }

    fun accepts(caller: NexusBinderCaller): Boolean =
        caller.packageName == packageName &&
            caller.currentSigningCertificateSha256 == acceptedCurrentSigningCertificateSha256 &&
            caller.signingLineageSha256 == acceptedSigningLineageSha256
}

private val CANONICAL_SHA256 = Regex("^[0-9a-f]{64}$")
private fun String.isCanonicalSha256(): Boolean = CANONICAL_SHA256.matches(this)

internal interface NexusBinderRuntimeOwner {
    val callerPolicy: NexusBinderCallerPolicy
    fun handleVerified(request: NexusBinderRequest, caller: NexusBinderCaller): String
    fun closeFromBinderDeath(caller: NexusBinderCaller, cause: NexusClosureCause = NexusClosureCause.BINDER_DEATH)
}

internal fun interface NexusBinderRuntimeFactory {
    fun create(context: Context): NexusBinderRuntimeOwner?
}

internal object NexusBinderRuntimeRegistry {
    @Volatile private var factory: NexusBinderRuntimeFactory? = null
    fun install(factory: NexusBinderRuntimeFactory) { this.factory = factory }
    fun clear() { factory = null }
    fun owner(context: Context): NexusBinderRuntimeOwner? = factory?.create(context.applicationContext)
}

internal class NexusBinderReplayGuard(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val windowMillis: Long = REPLAY_WINDOW_MILLIS,
) {
    private val seen = object : LinkedHashMap<String, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 256
    }

    fun accept(caller: NexusBinderCaller, request: NexusBinderRequest): NexusBinderErrorCode? = synchronized(seen) {
        val now = clock()
        if (request.issuedAtMillis < now - windowMillis || request.issuedAtMillis > now + windowMillis) return@synchronized NexusBinderErrorCode.STALE
        val key = "${caller.uid}:${caller.packageName}:${request.requestId}:${request.nonce}"
        if (seen.putIfAbsent(key, request.issuedAtMillis) != null) NexusBinderErrorCode.REPLAY else null
    }
}

internal class NexusBinderCallerVerifier(private val context: Context) {
    fun verify(policy: NexusBinderCallerPolicy, uid: Int = Binder.getCallingUid()): NexusBinderCaller? {
        val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
        return packages.asSequence()
            .mapNotNull { packageName -> signingEvidence(packageName)?.let { NexusBinderCaller(uid, packageName, it.current, it.lineage) } }
            .firstOrNull(policy::accepts)
    }

    private data class PackageSigningEvidence(val current: String, val lineage: List<String>)

    private fun signingEvidence(packageName: String): PackageSigningEvidence? = try {
        val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return null
        val currentSigners = signing.apkContentsSigners.orEmpty()
        val current = currentSigners.singleOrNull()?.let { cert -> sha256(cert.toByteArray()) } ?: return null
        val lineage = if (signing.hasPastSigningCertificates()) signing.signingCertificateHistory else currentSigners
        PackageSigningEvidence(current, lineage.map { cert -> sha256(cert.toByteArray()) })
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

internal class NexusBinderRequestHandler(
    private val ownerProvider: () -> NexusBinderRuntimeOwner?,
    private val callerVerifier: (NexusBinderCallerPolicy) -> NexusBinderCaller?,
    private val replayGuard: NexusBinderReplayGuard = NexusBinderReplayGuard(),
) {
    private data class ClientBinding(val token: IBinder, val recipient: IBinder.DeathRecipient)
    private val deathBindings = LinkedHashMap<String, ClientBinding>()
    private val cancelledSetups = LinkedHashMap<String, IBinder>()
    constructor(
        ownerProvider: () -> NexusBinderRuntimeOwner?,
        callerVerifier: NexusBinderCallerVerifier,
        replayGuard: NexusBinderReplayGuard = NexusBinderReplayGuard(),
    ) : this(ownerProvider, { policy -> callerVerifier.verify(policy) }, replayGuard)
    fun handle(raw: String?, clientToken: IBinder?): String {
        val parsed = when (val result = parseBoundedBinderRequest(raw)) {
            is EitherError.Error -> return errorJson(result.code, result.requestId, result.detail)
            is EitherError.Value -> result.value
        }
        val owner = ownerProvider() ?: return errorJson(NexusBinderErrorCode.AUTHORITY_UNAVAILABLE, parsed.requestId)
        val caller = callerVerifier(owner.callerPolicy) ?: return errorJson(NexusBinderErrorCode.UNAUTHORIZED_CALLER, parsed.requestId)
        replayGuard.accept(caller, parsed)?.let { return errorJson(it, parsed.requestId) }
        val bindingKey = "${caller.uid}:${caller.packageName}"
        if (parsed.verb == NexusBinderVerb.AUTH && clientToken == null) {
            return errorJson(NexusBinderErrorCode.REVOKED, parsed.requestId, "Auth requires a Binder client token for death ownership.")
        }
        if (parsed.verb == NexusBinderVerb.AUTH && clientToken != null) synchronized(deathBindings) {
            val existing = deathBindings[bindingKey]
            if (existing != null && existing.token !== clientToken) {
                return errorJson(NexusBinderErrorCode.REVOKED, parsed.requestId, "Authenticated Binder client token replacement is not allowed; disconnect first.")
            }
        }
        if (parsed.verb == NexusBinderVerb.DISCONNECT && clientToken != null) {
            val existing = synchronized(deathBindings) { deathBindings[bindingKey] }
            if (existing != null && existing.token !== clientToken) {
                return errorJson(NexusBinderErrorCode.REVOKED, parsed.requestId, "Disconnect requires the exact authenticated Binder client token.")
            }
            if (existing != null) {
                synchronized(deathBindings) {
                    deathBindings.remove(bindingKey)
                    cancelledSetups.remove(bindingKey)
                }
                runCatching { existing.token.unlinkToDeath(existing.recipient, 0) }
                return try {
                    owner.closeFromBinderDeath(caller, NexusClosureCause.EXPLICIT_DISCONNECT)
                    successJson(parsed.requestId, parsed.verb.wireName, JSONObject().put("closed", true))
                } catch (revoked: NexusBinderRevokedException) {
                    errorJson(NexusBinderErrorCode.REVOKED, parsed.requestId, revoked.message)
                }
            }
            synchronized(deathBindings) {
                cancelledSetups[bindingKey] = clientToken
                while (cancelledSetups.size > MAX_PENDING_SETUP_CANCELLATIONS) {
                    cancelledSetups.remove(cancelledSetups.entries.first().key)
                }
            }
            return successJson(
                parsed.requestId,
                parsed.verb.wireName,
                JSONObject().put("closed", false).put("setup_cancellation_pending", true),
            )
        }
        return try {
            val response = owner.handleVerified(parsed, caller)
            if (parsed.verb == NexusBinderVerb.AUTH && clientToken != null && JSONObject(response).optBoolean("ok", false)) {
                val cancelled = synchronized(deathBindings) {
                    val pending = cancelledSetups.remove(bindingKey)
                    pending === clientToken
                }
                if (cancelled) {
                    owner.closeFromBinderDeath(caller, NexusClosureCause.EXPLICIT_DISCONNECT)
                    return errorJson(NexusBinderErrorCode.REVOKED, parsed.requestId, "Binder setup was cancelled before authentication completed.")
                }
                synchronized(deathBindings) {
                    if (!deathBindings.containsKey(bindingKey)) {
                        val recipient = IBinder.DeathRecipient {
                            synchronized(deathBindings) { deathBindings.remove(bindingKey) }
                            owner.closeFromBinderDeath(caller)
                        }
                        runCatching {
                            clientToken.linkToDeath(recipient, 0)
                            deathBindings[bindingKey] = ClientBinding(clientToken, recipient)
                        }.onFailure {
                            owner.closeFromBinderDeath(caller)
                            return errorJson(NexusBinderErrorCode.REVOKED, parsed.requestId, "Binder death recipient link failed; session closed fail-safe.")
                        }
                    }
                }
            }
            if (parsed.verb == NexusBinderVerb.DISCONNECT) synchronized(deathBindings) {
                deathBindings.remove(bindingKey)?.let { binding ->
                    runCatching { binding.token.unlinkToDeath(binding.recipient, 0) }
                }
            }
            boundResponse(response, parsed.requestId)
        } catch (revoked: NexusBinderRevokedException) {
            errorJson(NexusBinderErrorCode.REVOKED, parsed.requestId, revoked.message)
        } catch (stale: NexusBinderStaleException) {
            errorJson(NexusBinderErrorCode.STALE, parsed.requestId, stale.message)
        } catch (bad: IllegalArgumentException) {
            errorJson(NexusBinderErrorCode.MALFORMED, parsed.requestId, bad.message)
        }
    }

    private fun boundResponse(response: String, requestId: String): String =
        if (response.toByteArray(Charsets.UTF_8).size <= MAX_BINDER_RESPONSE_BYTES) response
        else errorJson(NexusBinderErrorCode.RESPONSE_OVERSIZED, requestId, "Response exceeded $MAX_BINDER_RESPONSE_BYTES bytes.")
}

private const val MAX_PENDING_SETUP_CANCELLATIONS = 64

internal class NexusBinderRevokedException(message: String) : RuntimeException(message)
internal class NexusBinderStaleException(message: String) : RuntimeException(message)

internal class MinimalNexusBinderRuntimeOwner(
    override val callerPolicy: NexusBinderCallerPolicy,
) : NexusBinderRuntimeOwner {
    @Volatile var lastClosureCause: NexusClosureCause? = null
        private set
    private val challenges = LinkedHashMap<String, Pair<String, String>>()
    private var authenticated = false

    override fun handleVerified(request: NexusBinderRequest, caller: NexusBinderCaller): String = when (request.verb) {
        NexusBinderVerb.CHALLENGE -> {
            val id = "challenge-${request.requestId}"
            val serverNonce = "server-${request.nonce}"
            challenges[id] = request.requestId to request.nonce
            successJson(
                request.requestId,
                request.verb.wireName,
                JSONObject()
                    .put("challenge_id", id)
                    .put("server_nonce", serverNonce)
                    .put("protocol", NEXUS_BINDER_PROTOCOL)
                    .put("expires_at_millis", request.issuedAtMillis + REPLAY_WINDOW_MILLIS),
            )
        }
        NexusBinderVerb.AUTH -> {
            val challengeId = request.payload.optString("challenge_id")
            val expected = challenges.remove(challengeId) ?: throw NexusBinderStaleException("Request a fresh Binder challenge before auth.")
            if (request.payload.optString("challenge_request_id") != expected.first || request.payload.optString("client_nonce") != expected.second) {
                throw NexusBinderStaleException("Retry with the nonce-bound challenge issued to this caller.")
            }
            authenticated = true
            successJson(request.requestId, request.verb.wireName, JSONObject().put("authenticated", true))
        }
        NexusBinderVerb.OBSERVE -> successJson(request.requestId, request.verb.wireName, JSONObject().put("state", "runtime_owner_not_bootstrapped_to_store"))
        NexusBinderVerb.AUDIT -> successJson(request.requestId, request.verb.wireName, JSONObject().put("records", org.json.JSONArray()))
        NexusBinderVerb.DISPATCH -> successJson(request.requestId, request.verb.wireName, JSONObject().put("accepted", false).put("fix", "Install a MainActivity runtime owner wired to NexusRuntimeDispatcher and PhosphorStateStore."))
        NexusBinderVerb.HEARTBEAT -> {
            if (!authenticated) throw NexusBinderStaleException("Authenticate before using Binder authority.")
            successJson(request.requestId, request.verb.wireName, JSONObject().put("alive", true))
        }
        NexusBinderVerb.DISCONNECT -> { authenticated = false; closeFromBinderDeath(caller, NexusClosureCause.EXPLICIT_DISCONNECT); successJson(request.requestId, request.verb.wireName, JSONObject().put("closed", true)) }
    }

    override fun closeFromBinderDeath(caller: NexusBinderCaller, cause: NexusClosureCause) { lastClosureCause = cause }
}
