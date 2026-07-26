package dev.phosphor.mobil3.store

import dev.phosphor.mobil3.state.AcceptedActionRecord
import dev.phosphor.mobil3.state.ActionAcknowledgement
import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.ActionType
import dev.phosphor.mobil3.state.AuditIndexSnapshot
import dev.phosphor.mobil3.state.AuditKind
import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.AuditRetentionPolicy
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.FrozenMap
import dev.phosphor.mobil3.state.IDEMPOTENCY_TTL_MILLIS
import dev.phosphor.mobil3.state.IdempotencyIndexPolicy
import dev.phosphor.mobil3.state.IdempotencyIndexSnapshot
import dev.phosphor.mobil3.state.IdempotencyRecord
import dev.phosphor.mobil3.state.PhosphorAction
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.ProvenanceStamp
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.SettingsRowProjection
import dev.phosphor.mobil3.state.SettingsSchema
import dev.phosphor.mobil3.state.StateChange
import dev.phosphor.mobil3.state.StateDelta
import dev.phosphor.mobil3.state.StateField
import dev.phosphor.mobil3.state.StringStateValue
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenListOf

private val allowedHudModes = setOf("auto", "on", "off")

sealed interface DisplayHudReduction {
    data class Accepted(
        val acknowledgement: ActionAcknowledgement,
        val previousSnapshot: PhosphorStateSnapshot,
        val snapshot: PhosphorStateSnapshot,
        val audit: AuditRecord,
        val idempotencyRecord: IdempotencyRecord,
        val acceptedRecord: AcceptedActionRecord,
    ) : DisplayHudReduction

    data class Replayed(
        val acknowledgement: ActionAcknowledgement,
        val snapshot: PhosphorStateSnapshot,
        val audit: AuditRecord,
    ) : DisplayHudReduction

    data class Refused(
        val refusal: Refusal,
        val snapshot: PhosphorStateSnapshot,
        val audit: AuditRecord,
    ) : DisplayHudReduction
}

data class DisplayHudReducerConfig(
    val auditPolicy: AuditRetentionPolicy = AuditRetentionPolicy(maximumRecords = 128, maximumAgeMillis = IDEMPOTENCY_TTL_MILLIS),
    val idempotencyPolicy: IdempotencyIndexPolicy = IdempotencyIndexPolicy(maximumRecords = 128),
)

class DisplayHudReducer(
    private val config: DisplayHudReducerConfig = DisplayHudReducerConfig(),
) {
    fun reduce(
        action: PhosphorAction,
        request: ActionRequest,
        image: PhosphorStoreImage,
        monotonicMillis: Long,
        wallTimeMillis: Long,
        receiptId: String,
        ordinal: Long,
    ): DisplayHudReduction {
        require(monotonicMillis >= 0L) { "monotonic time must not be negative" }
        require(wallTimeMillis >= 0L) { "wall time must not be negative" }
        require(receiptId.isNotBlank()) { "receipt id must be nonblank" }
        require(ordinal >= 0L) { "ordinal must not be negative" }

        val state = image.snapshot
        if (wallTimeMillis < state.wallTimeMillis) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.INVALID_REQUEST, "Use a wall clock at or after snapshot time ${state.wallTimeMillis}.", state.revision))
        }
        val activeIdempotency = pruneIdempotency(image.idempotency.records, wallTimeMillis)
        val payload = action.canonicalPayload()
        if (action !is SetDisplayHud) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.INVALID_REQUEST, "Only SetDisplayHud is accepted by DisplayHudReducer.", state.revision))
        }
        action.validate()?.let { return refuse(action, state, wallTimeMillis, receiptId, it.copy(currentRevision = state.revision)) }
        if (action.mode !in allowedHudModes) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.INVALID_VALUE, "Use display.hud mode auto, on, or off.", state.revision))
        }
        if (request.principal.kind !in setOf(PrincipalKind.HUMAN, PrincipalKind.MIGRATION)) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.PERMISSION_REQUIRES_HUMAN, "Use a local HUMAN principal or MIGRATION control path; Nexus authority cannot change display HUD.", state.revision))
        }
        val declaredLocalPrincipal = when (request.principal.kind) {
            PrincipalKind.HUMAN -> request.principal.stableId == LOCAL_HUMAN_PRINCIPAL_ID
            PrincipalKind.MIGRATION -> request.principal.stableId == LOCAL_HUD_MIGRATION_PRINCIPAL_ID
            else -> false
        }
        if (!declaredLocalPrincipal) {
            return refuse(
                action,
                state,
                wallTimeMillis,
                receiptId,
                Refusal(
                    RefusalCode.INVALID_REQUEST,
                    "Use the declared local HUMAN or HUD migration principal; arbitrary stable IDs are not authorized for this slice.",
                    state.revision,
                ),
            )
        }
        if (!((request.principal.kind == PrincipalKind.HUMAN && request.transport == Transport.UI) ||
                (request.principal.kind == PrincipalKind.MIGRATION && request.transport == Transport.MIGRATION))) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.INVALID_REQUEST, "Use HUMAN principals only through UI and MIGRATION principals only through MIGRATION for display HUD changes.", state.revision))
        }
        if (request.sessionId != null) {
            return refuse(
                action,
                state,
                wallTimeMillis,
                receiptId,
                Refusal(
                    RefusalCode.INVALID_REQUEST,
                    "Use no session id for the local Phase 04 HUD slice; Nexus and remote sessions are not active here.",
                    state.revision,
                ),
            )
        }
        if (request.requestedCapability != Capability.CONTROL_DISPLAY) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.INVALID_REQUEST, "Request control.display for display.hud.set.", state.revision))
        }
        if (Capability.CONTROL_DISPLAY !in (state.capabilities[request.principal] ?: emptySet())) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.CAPABILITY_NOT_GRANTED, "Grant control.display to this local principal before changing display HUD.", state.revision))
        }
        val availability = state.availability.getValue(StateField.DISPLAY_HUD)
        if (availability.fix != null) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(availability.fix.code, availability.fix.message, state.revision))
        }
        val authority = state.authority.getValue(StateField.DISPLAY_HUD)
        if (authority.fix != null) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(authority.fix.code, authority.fix.message, state.revision))
        }

        activeIdempotency.firstOrNull { it.principal == request.principal && it.key == request.idempotencyKey }?.let { previous ->
            return if (previous.canonicalPayload == payload) {
                val audit = audit(
                    receiptId = previous.acknowledgement.receiptId,
                    kind = AuditKind.IDEMPOTENT_REPLAY,
                    wallTimeMillis = wallTimeMillis,
                    actionType = action.type,
                    provenance = previous.acknowledgement.provenance,
                )
                DisplayHudReduction.Replayed(previous.acknowledgement, state, audit)
            } else {
                refuse(
                    action,
                    state,
                    wallTimeMillis,
                    receiptId,
                    Refusal(
                        RefusalCode.IDEMPOTENCY_CONFLICT,
                        "Generate a new idempotency key, or replay the original SetDisplayHud payload for this key.",
                        state.revision,
                        previous.acknowledgement.receiptId,
                    ),
                    AuditKind.IDEMPOTENCY_CONFLICT,
                )
            }
        }
        if (activeIdempotency.size >= config.idempotencyPolicy.maximumRecords) {
            return refuse(
                action,
                state,
                wallTimeMillis,
                receiptId,
                Refusal(
                    RefusalCode.IDEMPOTENCY_INDEX_FULL,
                    "Wait for an idempotency key to expire after 24 hours, then retry with a fresh key.",
                    state.revision,
                ),
            )
        }
        if (request.expectedRevision != state.revision) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.REVISION_CONFLICT, "Refresh state and retry against revision ${state.revision}.", state.revision))
        }
        if (state.desired.displayHud != state.effective.displayHud) {
            return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Resolve display.hud desired/effective divergence before accepting a causal HUD write.", state.revision))
        }

        val stamp = ProvenanceStamp(receiptId, request.principal, request.reason, request.transport, request.sessionId, monotonicMillis, wallTimeMillis)
        val currentValue = StringStateValue(state.effective.displayHud)
        val requestedValue = StringStateValue(action.mode)
        val changed = currentValue != requestedValue
        val nextSnapshot = if (changed) {
            val nextRevision = checkedAdd(state.revision, 1L) ?: return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Compact or restore state because the revision counter is exhausted.", state.revision))
            val nextSequence = checkedAdd(state.sequence, 1L) ?: return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Compact or restore state because the sequence counter is exhausted.", state.revision))
            state.copy(
                revision = nextRevision,
                sequence = nextSequence,
                wallTimeMillis = wallTimeMillis,
                desired = state.desired.copy(displayHud = action.mode),
                effective = state.effective.copy(displayHud = action.mode),
                provenance = withProvenance(state.provenance, stamp),
            )
        } else {
            state
        }
        val delta = if (changed) {
            StateDelta(
                revision = nextSnapshot.revision,
                sequence = nextSnapshot.sequence,
                changes = frozenListOf(StateChange(StateField.DISPLAY_HUD, currentValue, requestedValue, stamp)),
                provenance = stamp,
            )
        } else {
            null
        }
        val acknowledgement = ActionAcknowledgement(
            receiptId = receiptId,
            actionType = ActionType.SET_DISPLAY_HUD,
            changed = changed,
            revision = nextSnapshot.revision,
            sequence = nextSnapshot.sequence,
            effectiveValue = requestedValue,
            provenance = stamp,
            delta = delta,
            effects = frozenListOf(),
        )
        val acceptedAudit = audit(receiptId, if (changed) AuditKind.ACTION else AuditKind.NO_OP, wallTimeMillis, action.type, stamp)
        val projection = if (changed) {
            SettingsRowProjection(
                schema = SettingsSchema.rows.single { it.field == StateField.DISPLAY_HUD },
                desired = requestedValue,
                effective = requestedValue,
                availability = nextSnapshot.availability.getValue(StateField.DISPLAY_HUD),
                authority = nextSnapshot.authority.getValue(StateField.DISPLAY_HUD),
                provenance = stamp,
                visibleLastWriter = stamp,
                fix = nextSnapshot.fixes[StateField.DISPLAY_HUD],
            )
        } else {
            null
        }
        val acceptedRecord = AcceptedActionRecord(
            request = request,
            action = action,
            canonicalPayload = payload,
            acknowledgement = acknowledgement,
            previousSnapshot = state,
            snapshot = nextSnapshot,
            audit = acceptedAudit,
            settingsProjections = projection?.let { frozenListOf(it) } ?: frozenListOf(),
        )
        val expiresAt = checkedAdd(wallTimeMillis, config.idempotencyPolicy.minimumTtlMillis)
            ?: return refuse(action, state, wallTimeMillis, receiptId, Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Use an earlier wall clock because idempotency expiry would overflow.", state.revision))
        val idem = IdempotencyRecord(
            principal = request.principal,
            key = request.idempotencyKey,
            action = action,
            canonicalPayload = payload,
            acknowledgement = acknowledgement,
            acceptedWallTimeMillis = wallTimeMillis,
            expiresWallTimeMillis = expiresAt,
            ordinal = ordinal,
        )
        return DisplayHudReduction.Accepted(acknowledgement, state, nextSnapshot, acceptedAudit, idem, acceptedRecord)
    }

    fun appendAudit(existing: Iterable<AuditRecord>, record: AuditRecord, observedWallTimeMillis: Long): AuditIndexSnapshot {
        val retained = (existing + record).filter { observedWallTimeMillis - it.wallTimeMillis <= config.auditPolicy.maximumAgeMillis }
            .takeLast(config.auditPolicy.maximumRecords)
        return AuditIndexSnapshot(config.auditPolicy, observedWallTimeMillis, FrozenList.copyOf(retained))
    }

    fun appendIdempotency(existing: Iterable<IdempotencyRecord>, record: IdempotencyRecord, observedWallTimeMillis: Long): IdempotencyIndexSnapshot {
        val retained = (pruneIdempotency(existing, observedWallTimeMillis) + record).takeLast(config.idempotencyPolicy.maximumRecords)
        return IdempotencyIndexSnapshot(config.idempotencyPolicy, FrozenList.copyOf(retained))
    }

    private fun refuse(action: PhosphorAction, state: PhosphorStateSnapshot, wallTimeMillis: Long, receiptId: String, refusal: Refusal, kind: AuditKind = AuditKind.REFUSAL): DisplayHudReduction.Refused =
        DisplayHudReduction.Refused(refusal, state, audit(receiptId, kind, wallTimeMillis, action.type, null, refusal))

    private fun audit(receiptId: String, kind: AuditKind, wallTimeMillis: Long, actionType: ActionType, provenance: ProvenanceStamp? = null, refusal: Refusal? = null): AuditRecord =
        AuditRecord(receiptId, kind, wallTimeMillis, actionType, actionType.affectedFields, provenance, refusal)

    private fun pruneIdempotency(records: Iterable<IdempotencyRecord>, wallTimeMillis: Long): List<IdempotencyRecord> =
        records.filter { it.expiresWallTimeMillis > wallTimeMillis }

    private fun checkedAdd(left: Long, right: Long): Long? = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

    private fun withProvenance(existing: FrozenMap<StateField, ProvenanceStamp>, stamp: ProvenanceStamp): FrozenMap<StateField, ProvenanceStamp> {
        val next = LinkedHashMap<StateField, ProvenanceStamp>()
        existing.forEach { (field, value) -> next[field] = value }
        next[StateField.DISPLAY_HUD] = stamp
        return FrozenMap.copyOf(next)
    }
}
