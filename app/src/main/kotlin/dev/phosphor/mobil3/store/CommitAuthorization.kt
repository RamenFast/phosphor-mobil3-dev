package dev.phosphor.mobil3.store

import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.ActionType
import dev.phosphor.mobil3.state.CanonicalPayload
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.PhosphorAction
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.Transport

/**
 * Nexus-free shared-store fence invoked only while [PhosphorStateStore] holds its mutation monitor.
 *
 * A transport-specific authority owns the external session/token/grant monitor and must retain it
 * while calling `dispatchAuthorized`. The returned decision is then bound to the exact action,
 * request, and current store revision before reduction or replay lookup can occur.
 */
internal fun interface CommitAuthorizationFence {
    fun authorize(
        action: PhosphorAction,
        request: ActionRequest,
        snapshot: PhosphorStateSnapshot,
        monotonicMillis: Long,
        wallTimeMillis: Long,
    ): CommitAuthorizationDecision
}

internal sealed interface CommitAuthorizationDecision {
    data class Refused(val refusal: Refusal) : CommitAuthorizationDecision

    class Authorized private constructor(
        val principal: PrincipalId,
        val sessionId: String,
        val transport: Transport,
        val capability: Capability,
        val snapshotRevision: Long,
        val actionType: ActionType,
        val canonicalPayload: CanonicalPayload,
    ) : CommitAuthorizationDecision {
        init {
            require(principal.kind == PrincipalKind.NEXUS) { "authorized remote commit requires a Nexus principal" }
            require(sessionId.isNotBlank()) { "authorized remote commit requires a session id" }
            require(transport == Transport.BINDER || transport == Transport.TAILNET) {
                "authorized remote commit requires Binder or tailnet transport"
            }
            require(snapshotRevision >= 0L) { "authorized remote commit revision must not be negative" }
            require(capability == actionType.requiredCapability) {
                "authorized remote commit capability must equal the typed action requirement"
            }
        }

        fun matches(
            action: PhosphorAction,
            request: ActionRequest,
            snapshot: PhosphorStateSnapshot,
        ): Boolean =
            principal == request.principal &&
                sessionId == request.sessionId &&
                transport == request.transport &&
                capability == request.requestedCapability &&
                snapshotRevision == snapshot.revision &&
                actionType == action.type &&
                canonicalPayload == action.canonicalPayload()

        companion object {
            fun exact(
                action: PhosphorAction,
                request: ActionRequest,
                snapshot: PhosphorStateSnapshot,
            ): Authorized = Authorized(
                principal = request.principal,
                sessionId = requireNotNull(request.sessionId) { "authorized Nexus request requires a session" },
                transport = request.transport,
                capability = request.requestedCapability,
                snapshotRevision = snapshot.revision,
                actionType = action.type,
                canonicalPayload = action.canonicalPayload(),
            )
        }
    }
}
