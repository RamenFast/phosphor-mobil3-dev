package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.store.PhosphorStateStore
import dev.phosphor.mobil3.store.StoreHealth
import dev.phosphor.mobil3.state.frozenSetOf

/**
 * Phase 05b-1 observation-only Nexus authority.
 *
 * This class is intentionally in-memory and Fortress-only. Its monitor is the only authority for
 * the current Nexus session, token grant, grant ledger, and trust policy. Observation revalidates a
 * Phase 05a snapshot candidate while holding this monitor, and keeps holding it through the causal
 * store's own atomic [PhosphorStateStore.observe] read so revocation/closure and observation have a
 * single linearization point.
 */
internal class NexusObservationDispatcher(
    private val store: PhosphorStateStore,
    initialSession: NexusSession,
    initialToken: NexusTokenGrant,
    initialGrants: NexusGrantLedger,
    initialTrustPolicy: NexusTrustPolicy,
) {
    init {
        require(initialSession.lifecycle == NexusLifecycle.OBSERVING || initialSession.lifecycle == NexusLifecycle.DRIVING) {
            "observation authority requires an active observable session"
        }
        val sessionId = requireNotNull(initialSession.id) { "observation authority requires a session id" }
        val trustKey = requireNotNull(initialSession.trustKey) { "observation authority requires a trust tuple" }
        val tokenId = requireNotNull(initialSession.tokenId) { "observation authority requires a token id" }
        require(initialToken.id == tokenId) { "observation authority token must match current session" }
        require(initialToken.trustKey == trustKey) { "observation authority token must match current trust tuple" }
        require(!initialToken.revoked) { "observation authority cannot start with a revoked token grant" }
        require(!initialTrustPolicy.isTokenRevoked(tokenId)) { "observation authority cannot start with a policy-revoked token" }
        require(initialToken.capabilities.containsAll(initialSession.capabilities)) {
            "session capabilities must remain inside token authority"
        }
        require(initialSession.capabilities.all { initialGrants.allows(trustKey, sessionId, it) }) {
            "session capabilities must be present in the grant ledger"
        }
    }

    private val monitor = Any()
    private var currentSession: NexusSession = initialSession
    private var currentToken: NexusTokenGrant = initialToken
    private var currentGrants: NexusGrantLedger = initialGrants
    private var currentTrustPolicy: NexusTrustPolicy = initialTrustPolicy

    fun observe(
        projection: NexusObservationProjection,
        nowMonotonicMillis: Long,
    ): NexusObservationResult = synchronized(monitor) {
        revalidate(projection, nowMonotonicMillis)?.let { refusal ->
            return@synchronized NexusObservationResult.Refused(refusal)
        }
        when (projection.capability) {
            Capability.OBSERVE_STATE -> store.observe().let { observation ->
                NexusObservationResult.State(
                    snapshot = observation.snapshot,
                    health = observation.health,
                )
            }
            Capability.OBSERVE_AUDIT -> store.observe().let { observation ->
                NexusObservationResult.Audit(
                    auditRecords = FrozenList.copyOf(observation.auditRecords),
                    health = observation.health,
                )
            }
            Capability.OBSERVE_GEOMETRY -> NexusObservationResult.GeometryUnavailable(
                fix = "Read geometry from the future renderer-owned high-rate geometry stream after that authority is approved.",
            )
            else -> error("NexusObservationProjection admitted a non-observe capability")
        }
    }

    fun apply(transition: NexusSessionTransition): Boolean = synchronized(monitor) {
        if (transition.previousSession !== currentSession) return@synchronized false
        if (transition.previousSessionId != currentSession.id) return@synchronized false
        if (transition.previousGeneration != currentSession.generation) return@synchronized false
        if (!currentGrants.containsAll(transition.grants)) return@synchronized false
        currentSession = transition.session
        currentGrants = transition.grants
        true
    }

    fun apply(revocation: NexusTokenRevocation): Boolean = synchronized(monitor) {
        if (revocation.previousPolicy !== currentTrustPolicy) return@synchronized false
        if (revocation.revokedTokenId != currentToken.id) return@synchronized false
        if (!revocation.policy.isTokenRevoked(currentToken.id)) return@synchronized false
        if (revocation.previousSession !== currentSession) return@synchronized false
        if (revocation.previousGeneration != currentSession.generation) return@synchronized false
        if (revocation.closedSessionId != currentSession.id) return@synchronized false
        if (revocation.session.lifecycle != NexusLifecycle.ABSENT) return@synchronized false
        if (!currentGrants.containsAll(revocation.grants)) return@synchronized false
        currentTrustPolicy = revocation.policy
        currentSession = revocation.session
        currentGrants = revocation.grants
        true
    }

    fun recordHeartbeat(
        sessionId: NexusSessionId,
        generation: NexusGeneration,
        nowMonotonicMillis: Long,
    ): Boolean = synchronized(monitor) {
        if (currentSession.id != sessionId) return@synchronized false
        if (currentSession.generation != generation) return@synchronized false
        currentSession = try {
            currentSession.heartbeat(nowMonotonicMillis)
        } catch (_: IllegalArgumentException) {
            return@synchronized false
        }
        true
    }

    private fun revalidate(
        projection: NexusObservationProjection,
        nowMonotonicMillis: Long,
    ): NexusPreAuthRefusal? {
        val candidate = projection.candidate
        val sessionId = currentSession.id ?: return unavailable()
        val trustKey = currentSession.trustKey ?: return unavailable()
        val tokenId = currentSession.tokenId ?: return unavailable()
        if (currentSession.lifecycle != NexusLifecycle.OBSERVING && currentSession.lifecycle != NexusLifecycle.DRIVING) {
            return unavailable()
        }
        if (remoteHeartbeatRefuses(currentSession, nowMonotonicMillis)) return unavailable()
        if (candidate.sessionId != sessionId) return tokenInvalid()
        if (candidate.tokenId != tokenId) return tokenInvalid()
        if (candidate.trustKey != trustKey) return trustRejected()
        if (candidate.generation != currentSession.generation) return tokenInvalid()
        if (candidate.capability != projection.capability) return capabilityDenied()
        if (projection.capability !in currentSession.capabilities) return capabilityDenied()
        if (currentSession.reachPath == null) return unavailable()
        if (!currentGrants.allows(trustKey, sessionId, projection.capability)) return capabilityDenied()
        if (currentTrustPolicy.isTokenRevoked(tokenId)) return tokenRevoked()
        return currentToken.validate(
            presentedId = tokenId,
            presentedTrustKey = trustKey,
            requestedCapabilities = frozenSetOf(projection.capability),
            nowMonotonicMillis = nowMonotonicMillis,
        )
    }

    private fun remoteHeartbeatRefuses(session: NexusSession, nowMonotonicMillis: Long): Boolean {
        if (session.reachPath != NexusReachPath.TAILNET) return false
        val policy = session.heartbeatPolicy ?: return true
        val lastHeartbeat = session.lastHeartbeatMonotonicMillis ?: return true
        return try {
            policy.evaluate(lastHeartbeat, nowMonotonicMillis, session.lifecycle) != session.lifecycle
        } catch (_: IllegalArgumentException) {
            true
        }
    }

    private fun NexusGrantLedger.containsAll(candidate: NexusGrantLedger): Boolean = grants.containsAll(candidate.grants)

    private fun unavailable(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
        "Establish a fresh observing Nexus session before retrying.",
    )

    private fun trustRejected(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.TRUST_TUPLE_REJECTED,
        "Re-authorize this exact trusted identity before retrying observation.",
    )

    private fun tokenInvalid(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.TOKEN_INVALID,
        "Re-authorize with the current session generation and token.",
    )

    private fun tokenRevoked(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.TOKEN_REVOKED,
        "Ask the user to issue a replacement token after reviewing grants.",
    )

    private fun capabilityDenied(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.CAPABILITY_DENIED,
        "Request only currently granted observation capabilities.",
    )
}

internal sealed interface NexusObservationResult {
    data class State(
        val snapshot: PhosphorStateSnapshot,
        val health: StoreHealth,
    ) : NexusObservationResult

    data class Audit(
        val auditRecords: FrozenList<AuditRecord>,
        val health: StoreHealth,
    ) : NexusObservationResult

    data class GeometryUnavailable(val fix: String) : NexusObservationResult {
        init {
            fix.requireNexusToken("geometry unavailable fix", maxLength = 512)
        }
    }

    data class Refused(val refusal: NexusPreAuthRefusal) : NexusObservationResult
}
