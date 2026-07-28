package dev.phosphor.mobil3.nexus.binder

import android.content.Context
import android.os.SystemClock
import dev.phosphor.mobil3.BuildConfig
import dev.phosphor.mobil3.PhosphorApplication
import dev.phosphor.mobil3.nexus.AndroidSigningEvidence
import dev.phosphor.mobil3.nexus.CertificateSha256
import dev.phosphor.mobil3.nexus.NexusClosureCause
import dev.phosphor.mobil3.nexus.NexusDispatchProjection
import dev.phosphor.mobil3.nexus.NexusGeneration
import dev.phosphor.mobil3.nexus.NexusGrantLedger
import dev.phosphor.mobil3.nexus.NexusGrantScope
import dev.phosphor.mobil3.nexus.NexusCapabilityGrant
import dev.phosphor.mobil3.nexus.NexusAuthorityImage
import dev.phosphor.mobil3.nexus.NexusAuthorityStoreTransaction
import dev.phosphor.mobil3.nexus.NexusAuthorityTransactionResult
import dev.phosphor.mobil3.nexus.NexusNonceConsumption
import dev.phosphor.mobil3.nexus.NexusObservationProjection
import dev.phosphor.mobil3.nexus.NexusReachPath
import dev.phosphor.mobil3.nexus.NexusRuntimeDispatcher
import dev.phosphor.mobil3.nexus.NexusRuntimeManager
import dev.phosphor.mobil3.nexus.NexusRuntimeManagers
import dev.phosphor.mobil3.nexus.NexusSession
import dev.phosphor.mobil3.nexus.NexusSessionId
import dev.phosphor.mobil3.nexus.NexusTokenGrant
import dev.phosphor.mobil3.nexus.NexusTokenId
import dev.phosphor.mobil3.nexus.NexusTrustKey
import dev.phosphor.mobil3.nexus.NexusTrustPolicy
import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.PhosphorAction
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.store.PhosphorStateStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.LinkedHashMap

internal object NexusBinderBootstrap {
    @Volatile private var owner: NexusBinderRuntimeOwner? = null

    fun owner(context: Context): NexusBinderRuntimeOwner? {
        owner?.let { return it }
        val app = context.applicationContext as? PhosphorApplication ?: return null
        return synchronized(this) {
            owner ?: buildOwner(app.causalStore).also { owner = it }
        }
    }

    private fun buildOwner(store: PhosphorStateStore): NexusBinderRuntimeOwner {
        val lineage = BuildConfig.NEXUS_BINDER_SIGNING_LINEAGE.split(',')
            .map { it.trim().lowercase().replace(":", "") }
            .filter { it.isNotBlank() }
        val currentSigner = BuildConfig.NEXUS_BINDER_CURRENT_SIGNER_SHA256.trim().lowercase().replace(":", "")
        val policy = NexusBinderCallerPolicy(BuildConfig.NEXUS_BINDER_PACKAGE, currentSigner, lineage)
        val state = ProductionNexusBinderState(policy, store, NexusRuntimeManagers.forStore(store))
        return NexusBinderDispatcherOwner(
            callerPolicy = policy,
            dispatcherProvider = { state.dispatcher },
            store = store,
            projectionFactory = state,
            sessionCloser = state,
        )
    }
}

private class ProductionNexusBinderState(
    private val policy: NexusBinderCallerPolicy,
    private val store: PhosphorStateStore,
    private val manager: NexusRuntimeManager,
    private val wallClockMillis: () -> Long = { System.currentTimeMillis() },
) : NexusBinderProjectionFactory, NexusBinderSessionCloser {
    private val monitor = Any()
    private var generation = NexusGeneration(0)
    private var trustPolicy = NexusTrustPolicy.of()
    private var session = NexusSession.absent(generation, NexusClosureCause.EXPLICIT_DISCONNECT)
    private var token: NexusTokenGrant? = null
    private var grants = NexusGrantLedger.of()
    private val challenges = LinkedHashMap<String, BinderChallenge>()
    private val approvedCapabilities = BuildConfig.NEXUS_BINDER_CAPABILITIES.split(',').mapNotNull { it.trim().toCapabilityOrNull() }.toSet()
    val dispatcher: NexusRuntimeDispatcher get() = manager.dispatcher()

    override fun challenge(request: NexusBinderRequest, caller: NexusBinderCaller): JSONObject = synchronized(monitor) {
        require(policy.accepts(caller)) { "caller must match accepted Binder policy" }
        require(approvedCapabilities.isNotEmpty()) { "Fortress Binder capability ceiling is absent." }
        val nowWall = wallClockMillis()
        pruneChallenges(nowWall)
        val challenge = BinderChallenge(
            id = "binder-challenge-${UUID.randomUUID()}",
            serverNonce = "binder-server-${UUID.randomUUID()}",
            clientNonce = request.nonce,
            requestId = request.requestId,
            callerUid = caller.uid,
            callerPackage = caller.packageName,
            currentSigningCertificateSha256 = caller.currentSigningCertificateSha256,
            signingLineageSha256 = caller.signingLineageSha256,
            issuedAtMillis = nowWall,
            expiresAtMillis = nowWall + REPLAY_WINDOW_MILLIS,
        )
        challenges[challenge.id] = challenge
        while (challenges.size > MAX_BINDER_CHALLENGES) {
            challenges.remove(challenges.entries.first().key)
        }
        JSONObject()
            .put("challenge_id", challenge.id)
            .put("server_nonce", challenge.serverNonce)
            .put("protocol", NEXUS_BINDER_PROTOCOL)
            .put("expires_at_millis", challenge.expiresAtMillis)
    }

    override fun authenticate(request: NexusBinderRequest, caller: NexusBinderCaller): JSONObject = synchronized(monitor) {
        require(policy.accepts(caller)) { "caller must match accepted Binder policy" }
        val payload = request.payload
        require(approvedCapabilities.isNotEmpty()) { "Fortress Binder capability ceiling is absent." }
        val challengeId = payload.optString("challenge_id")
        val challengeRequestId = payload.optString("challenge_request_id")
        val clientNonce = payload.optString("client_nonce")
        val serverNonce = payload.optString("server_nonce")
        val challenge = challenges.remove(challengeId) ?: throw NexusBinderStaleException("Request a fresh Binder challenge before auth.")
        if (challenge.callerUid != caller.uid ||
            challenge.callerPackage != caller.packageName ||
            challenge.currentSigningCertificateSha256 != caller.currentSigningCertificateSha256 ||
            challenge.signingLineageSha256 != caller.signingLineageSha256 ||
            challenge.requestId != challengeRequestId ||
            challenge.clientNonce != clientNonce ||
            challenge.serverNonce != serverNonce
        ) {
            throw NexusBinderStaleException("Retry with the nonce-bound challenge issued to this caller.")
        }
        if (request.issuedAtMillis !in challenge.issuedAtMillis..challenge.expiresAtMillis) {
            throw NexusBinderStaleException("Retry with a non-expired Binder challenge.")
        }
        val principalStableId = payload.optString("principal_stable_id").ifBlank { caller.packageName }
        val requiredProfile = requiredBinderAuthProfile(BuildConfig.DEBUG)
        val requestedProfile = parseBinderAuthProfile(payload, requiredProfile)
        val trustKey = NexusTrustKey.android(
            packageName = caller.packageName,
            signing = AndroidSigningEvidence.of(
                current = CertificateSha256(caller.currentSigningCertificateSha256),
                history = caller.signingLineageSha256.map(::CertificateSha256),
            ),
            principalStableId = principalStableId,
            profile = requiredProfile,
        )
        val requested = payload.optJSONArray("capabilities").toCapabilities().ifEmpty { setOf(Capability.OBSERVE_STATE) }
        require(Capability.OBSERVE_STATE in requested) { "Binder auth requires observe.state" }
        val durableAuthority = consumeChallengeNonce(challenge, request, caller, trustKey)
        val durableGranted = durableAuthority.grants.grants
            .filter { it.scope == NexusGrantScope.PERSISTENT && it.trustKey == trustKey }
            .map { it.capability }
            .toSet()
        val effective = requested.intersect(approvedCapabilities).intersect(durableGranted)
        require(effective == requested) { "Requested capabilities are not durably granted for this exact Nexus trust tuple." }
        val sessionId = NexusSessionId("binder-session-${UUID.randomUUID()}")
        val tokenId = NexusTokenId("binder-token-${UUID.randomUUID()}")
        val now = SystemClock.elapsedRealtime()
        val nextToken = NexusTokenGrant.of(tokenId, trustKey, effective, now, now + 10 * 60_000L)
        val nextGrants = NexusGrantLedger.of(durableAuthority.grants.grants + effective.map { capability ->
            NexusCapabilityGrant(trustKey, capability, NexusGrantScope.TRANSIENT, sessionId, "binder-grant-${capability.wireName}")
        })
        val nextGeneration = generation.next()
        val established = NexusSession.binderObserving(
            trustKey = trustKey,
            sessionId = sessionId,
            tokenId = tokenId,
            generation = nextGeneration,
            capabilities = effective,
        )
        manager.attachBinder(established, nextToken, nextGrants, trustPolicy)
        generation = nextGeneration
        session = established
        token = nextToken
        grants = nextGrants
        JSONObject()
            .put("authenticated", true)
            .put("session_id", sessionId.value)
            .put("token_id", tokenId.value)
            .put("generation", generation.value)
            .put("capabilities", JSONArray(effective.map { it.wireName }))
            .put("package", caller.packageName)
            .put("accepted_signing_lineage", JSONArray(caller.signingLineageSha256))
            .put("capability_ceiling", JSONArray(approvedCapabilities.map { it.wireName }))
    }

    override fun heartbeat(request: NexusBinderRequest, caller: NexusBinderCaller): JSONObject = synchronized(monitor) {
        requireActive(request, caller)
        val sessionId = requireNotNull(session.id)
        val tokenId = requireNotNull(session.tokenId)
        if (!manager.confirmBinderAlive(sessionId, generation, tokenId)) throw NexusBinderStaleException("Retry heartbeat after re-authenticating the shared Nexus session.")
        JSONObject().put("alive", true).put("generation", generation.value)
    }

    override fun observation(request: NexusBinderRequest, caller: NexusBinderCaller): NexusObservationProjection = synchronized(monitor) {
        requireActive(request, caller)
        val capability = when (request.verb) {
            NexusBinderVerb.AUDIT -> Capability.OBSERVE_AUDIT
            else -> request.payload.optString("capability").toCapabilityOrNull() ?: Capability.OBSERVE_STATE
        }
        NexusObservationProjection.authorize(session, requireNotNull(token), grants, trustPolicy, capability, SystemClock.elapsedRealtime())
    }

    override fun dispatch(request: NexusBinderRequest, caller: NexusBinderCaller): NexusDispatchProjection = synchronized(monitor) {
        requireActive(request, caller)
        val action = request.payload.optJSONObject("action").toAction()
        val requestRecord = ActionRequest(
            principal = PrincipalId(PrincipalKind.NEXUS, requireNotNull(session.trustKey).principalStableId),
            idempotencyKey = request.payload.optString("idempotency_key").ifBlank { request.requestId },
            expectedRevision = request.payload.optLong("expected_revision", 0L),
            reason = request.payload.optString("reason").ifBlank { "Nexus Binder dispatch ${request.requestId}" },
            requestedCapability = action.type.requiredCapability,
            transport = Transport.BINDER,
            sessionId = requireNotNull(session.id).value,
        )
        NexusDispatchProjection.authorize(session, requireNotNull(token), grants, trustPolicy, action, requestRecord, SystemClock.elapsedRealtime())
    }

    override fun close(request: NexusBinderRequest, caller: NexusBinderCaller, cause: NexusClosureCause) = synchronized(monitor) {
        requireActive(request, caller)
        closeLocked(caller, cause)
    }

    override fun closeFromDeath(caller: NexusBinderCaller, cause: NexusClosureCause) = synchronized(monitor) {
        closeLocked(caller, cause)
    }

    private fun closeLocked(caller: NexusBinderCaller, cause: NexusClosureCause) {
        if (!policy.accepts(caller) || session.id == null) return
        if (!manager.close(cause)) throw NexusBinderRevokedException("Shared Nexus authority did not persist the Binder closure; fail closed and re-authenticate.")
        val transition = if (cause == NexusClosureCause.BINDER_DEATH) session.binderDied(grants) else session.disconnect(cause, grants)
        session = transition.session
        grants = transition.grants
        generation = session.generation
        token = null
    }

    fun verifyActive(request: NexusBinderRequest, caller: NexusBinderCaller) = synchronized(monitor) {
        requireActive(request, caller)
    }

    private fun requireActive(request: NexusBinderRequest, caller: NexusBinderCaller) {
        require(policy.accepts(caller)) { "caller must match accepted Binder policy" }
        if (session.id == null || token == null) throw NexusBinderStaleException("Authenticate before using Binder authority.")
        if (request.payload.optString("session_id") != session.id?.value ||
            request.payload.optString("token_id") != session.tokenId?.value ||
            request.payload.optLong("generation", Long.MIN_VALUE) != session.generation.value
        ) throw NexusBinderStaleException("Retry with the current Binder session_id, token_id, and generation.")
    }

    private fun pruneChallenges(nowWallTimeMillis: Long) {
        val expired = challenges.filterValues { it.expiresAtMillis <= nowWallTimeMillis }.keys.toList()
        expired.forEach(challenges::remove)
    }

    private fun consumeChallengeNonce(
        challenge: BinderChallenge,
        request: NexusBinderRequest,
        caller: NexusBinderCaller,
        trustKey: NexusTrustKey,
    ): NexusAuthorityImage {
        val binding = listOf(
            caller.uid.toString(),
            caller.packageName,
            caller.currentSigningCertificateSha256,
            caller.signingLineageSha256.joinToString("+"),
            request.requestId,
            request.nonce,
            challenge.requestId,
            challenge.clientNonce,
            challenge.id,
            challenge.serverNonce,
            trustKey.subject,
            trustKey.currentCertificate?.value.orEmpty(),
            trustKey.signingHistory.joinToString("+") { it.value },
            trustKey.profile.wireName,
            trustKey.protocol,
            trustKey.principalStableId,
        ).joinToString(":")
        return when (val result = NexusAuthorityStoreTransaction.consumeNonce(
            store = store,
            consumption = NexusNonceConsumption(
                value = "binder-auth-${challenge.id}",
                binding = binding,
                issuedWallTimeMillis = challenge.issuedAtMillis,
                expiresWallTimeMillis = challenge.expiresAtMillis,
            ),
            nowWallTimeMillis = request.issuedAtMillis,
        )) {
            is NexusAuthorityTransactionResult.Committed -> result.image.withoutTransientGrants()
            is NexusAuthorityTransactionResult.Refused -> throw NexusBinderRevokedException(result.refusal.fix)
        }
    }
}

private data class BinderChallenge(
    val id: String,
    val serverNonce: String,
    val clientNonce: String,
    val requestId: String,
    val callerUid: Int,
    val callerPackage: String,
    val currentSigningCertificateSha256: String,
    val signingLineageSha256: List<String>,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
)

private const val MAX_BINDER_CHALLENGES = 64

private fun JSONArray?.toCapabilities(): Set<Capability> = if (this == null) emptySet() else (0 until length()).mapNotNull { optString(it).toCapabilityOrNull() }.toSet()
private fun String.toCapabilityOrNull(): Capability? = Capability.entries.firstOrNull { it.wireName == this || it.name == this }
internal fun requiredBinderAuthProfile(debug: Boolean): dev.phosphor.mobil3.nexus.NexusBuildProfile =
    if (debug) dev.phosphor.mobil3.nexus.NexusBuildProfile.LOCAL_DEV else dev.phosphor.mobil3.nexus.NexusBuildProfile.NEXUS
internal fun parseBinderAuthProfile(
    payload: JSONObject,
    requiredProfile: dev.phosphor.mobil3.nexus.NexusBuildProfile,
): dev.phosphor.mobil3.nexus.NexusBuildProfile {
    require(payload.has("profile")) { "Binder auth requires explicit profile ${requiredProfile.wireName}." }
    val raw = payload.optString("profile")
    val parsed = dev.phosphor.mobil3.nexus.NexusBuildProfile.entries.firstOrNull { it.wireName == raw }
    require(parsed != null) { "Unsupported Binder auth profile $raw; expected ${requiredProfile.wireName}." }
    require(parsed == requiredProfile) { "Binder auth profile must be ${requiredProfile.wireName} for this build." }
    return parsed
}
private fun JSONObject?.toAction(): PhosphorAction {
    require(this != null) { "dispatch payload requires action" }
    require(keys().asSequence().toSet() == setOf("type", "mode")) { "Binder dispatch action must be exactly display.hud.set with mode." }
    val type = optString("type")
    require(type == "display.hud.set") { "Unsupported Binder dispatch action $type; use exact display.hud.set." }
    return SetDisplayHud(optString("mode"))
}
