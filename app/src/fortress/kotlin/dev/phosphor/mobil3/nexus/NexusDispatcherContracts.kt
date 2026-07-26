package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.PhosphorAction
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenSetOf

/**
 * A Phase 05a snapshot candidate. It records what was checked while projecting an operation.
 * It is not a commit permit, a currentness proof, or an atomic shared-store fence.
 */
sealed interface NexusAuthorizationCandidate {
    val sessionId: NexusSessionId
    val tokenId: NexusTokenId
    val trustKey: NexusTrustKey
    val generation: NexusGeneration
    val capability: Capability
}

private class SnapshotAuthorizationCandidate(
    override val sessionId: NexusSessionId,
    override val tokenId: NexusTokenId,
    override val trustKey: NexusTrustKey,
    override val generation: NexusGeneration,
    override val capability: Capability,
) : NexusAuthorizationCandidate

/**
 * Pure projection only. It cannot invoke Android, a socket, persistence, UI, renderer, a real
 * store, or a commit. A later shared-store integration must atomically re-establish currentness.
 */
@ConsistentCopyVisibility
data class NexusDispatchProjection private constructor(
    val action: PhosphorAction,
    val request: ActionRequest,
    val candidate: NexusAuthorizationCandidate,
) {
    init {
        require(request.principal.kind == PrincipalKind.NEXUS) {
            "Nexus projection requires a Nexus principal"
        }
        require(request.principal.stableId == candidate.trustKey.principalStableId) {
            "request principal must equal the authenticated trust tuple principal"
        }
        require(request.sessionId == candidate.sessionId.value) {
            "request session must equal the authorization candidate session"
        }
        require(request.requestedCapability == action.type.requiredCapability) {
            "request capability must equal the typed action requirement"
        }
        require(request.requestedCapability == candidate.capability) {
            "authorization candidate capability must equal the request capability"
        }
    }

    companion object {
        fun authorize(
            session: NexusSession,
            token: NexusTokenGrant,
            grants: NexusGrantLedger,
            trustPolicy: NexusTrustPolicy,
            action: PhosphorAction,
            request: ActionRequest,
            nowMonotonicMillis: Long,
        ): NexusDispatchProjection {
            val sessionId = requireNotNull(session.id) { "authenticated session is required" }
            val trustKey = requireNotNull(session.trustKey) { "authenticated trust tuple is required" }
            val tokenId = requireNotNull(session.tokenId) { "authenticated token is required" }
            require(session.lifecycle == NexusLifecycle.OBSERVING || session.lifecycle == NexusLifecycle.DRIVING) {
                "closing or absent session cannot project dispatch"
            }
            val expectedTransport = when (session.reachPath) {
                NexusReachPath.BINDER -> Transport.BINDER
                NexusReachPath.TAILNET -> Transport.TAILNET
                null -> error("present session requires a reach path")
            }
            require(request.transport == expectedTransport) {
                "request transport must equal the authenticated reach path"
            }
            require(request.principal == PrincipalId(PrincipalKind.NEXUS, trustKey.principalStableId)) {
                "request principal must equal the authenticated Nexus principal"
            }
            require(request.sessionId == sessionId.value) {
                "request session does not match authenticated session"
            }
            val capability = action.type.requiredCapability
            require(request.requestedCapability == capability) {
                "request capability does not match typed action"
            }
            require(capability.isNexusControlCapability()) {
                "mutating projection accepts only control capabilities"
            }
            require(session.capabilities.contains(capability)) {
                "session does not carry the required capability"
            }
            require(grants.allows(trustKey, sessionId, capability)) {
                "capability ledger does not grant the required capability"
            }
            require(!trustPolicy.isTokenRevoked(tokenId)) {
                "trust policy revoked the token before projection"
            }
            val tokenFailure = token.validate(
                presentedId = tokenId,
                presentedTrustKey = trustKey,
                requestedCapabilities = frozenSetOf(capability),
                nowMonotonicMillis = nowMonotonicMillis,
            )
            require(tokenFailure == null) { tokenFailure?.fix ?: "token validation failed" }
            return NexusDispatchProjection(
                action = action,
                request = request,
                candidate = SnapshotAuthorizationCandidate(
                    sessionId = sessionId,
                    tokenId = tokenId,
                    trustKey = trustKey,
                    generation = session.generation,
                    capability = capability,
                ),
            )
        }
    }
}

@ConsistentCopyVisibility
data class NexusObservationProjection private constructor(
    val capability: Capability,
    val candidate: NexusAuthorizationCandidate,
) {
    init {
        require(capability.isNexusObserveCapability()) { "observation projection requires an observe capability" }
        require(candidate.capability == capability) { "observation candidate capability mismatch" }
    }

    companion object {
        fun authorize(
            session: NexusSession,
            token: NexusTokenGrant,
            grants: NexusGrantLedger,
            trustPolicy: NexusTrustPolicy,
            capability: Capability,
            nowMonotonicMillis: Long,
        ): NexusObservationProjection {
            require(capability.isNexusObserveCapability()) { "requested capability is not observational" }
            val sessionId = requireNotNull(session.id) { "authenticated session is required" }
            val trustKey = requireNotNull(session.trustKey) { "authenticated trust tuple is required" }
            val tokenId = requireNotNull(session.tokenId) { "authenticated token is required" }
            require(session.lifecycle == NexusLifecycle.OBSERVING || session.lifecycle == NexusLifecycle.DRIVING) {
                "closing or absent session cannot project observation"
            }
            require(capability in session.capabilities) { "session does not carry the observe capability" }
            require(grants.allows(trustKey, sessionId, capability)) {
                "capability ledger does not grant observation"
            }
            require(!trustPolicy.isTokenRevoked(tokenId)) {
                "trust policy revoked the token before observation projection"
            }
            val tokenFailure = token.validate(
                presentedId = tokenId,
                presentedTrustKey = trustKey,
                requestedCapabilities = frozenSetOf(capability),
                nowMonotonicMillis = nowMonotonicMillis,
            )
            require(tokenFailure == null) { tokenFailure?.fix ?: "token validation failed" }
            return NexusObservationProjection(
                capability = capability,
                candidate = SnapshotAuthorizationCandidate(
                    sessionId = sessionId,
                    tokenId = tokenId,
                    trustKey = trustKey,
                    generation = session.generation,
                    capability = capability,
                ),
            )
        }
    }
}

/**
 * Phase 05b integration contract marker. No Phase 05a implementation or evidence value exists.
 * A future implementation must originate inside the shared causal store, atomically fence current
 * session/token/grant/revocation state, durably consume a one-use receipt, and then transition to
 * driving. A Phase 05a projection or candidate cannot satisfy this requirement.
 */
sealed interface NexusFreshCommitRequirement {
    val projection: NexusDispatchProjection
}
