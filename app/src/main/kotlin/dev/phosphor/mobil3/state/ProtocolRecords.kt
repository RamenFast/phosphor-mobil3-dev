package dev.phosphor.mobil3.state

data class ActionRequest(
    val principal: PrincipalId,
    val idempotencyKey: String,
    val expectedRevision: Long,
    val reason: String,
    val requestedCapability: Capability,
    val transport: Transport,
    val sessionId: String? = null,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotency key must be nonblank" }
        require(idempotencyKey.length <= 256) { "idempotency key must not exceed 256 characters" }
        require(expectedRevision >= 0L) { "expected revision must not be negative" }
        require(reason.isNotBlank()) { "action reason must be nonblank" }
    }
}

data class StateChange(
    val field: StateField,
    val oldValue: StateValue,
    val newValue: StateValue,
    val provenance: ProvenanceStamp,
) {
    init {
        require(oldValue.kind == field.valueKind && newValue.kind == field.valueKind) {
            "state change values must match ${field.wireName}'s ${field.valueKind} contract"
        }
        require(oldValue != newValue) { "state changes must describe an effective value change" }
    }
}

data class StateDelta(
    val event: String = "state.delta",
    val revision: Long,
    val sequence: Long,
    val changes: FrozenList<StateChange>,
    val provenance: ProvenanceStamp,
) {
    init {
        require(event == "state.delta") { "state delta event must remain canonical" }
        require(revision > 0L) { "delta revision must be positive" }
        require(sequence > 0L) { "delta sequence must be positive" }
        require(changes.isNotEmpty()) { "a delta requires at least one change" }
        require(changes.all { it.provenance === provenance }) {
            "every change must reuse the exact accepted provenance object"
        }
        require(changes.map { it.field }.distinct().size == changes.size) {
            "a delta must not change the same field more than once"
        }
    }
}

enum class EffectKind(val wireName: String) {
    PERSIST_STATE("persist.state"),
    HUD_PROVENANCE("hud.provenance"),
    RENDERER_COMMAND("renderer.command"),
    TRANSPORT_COMMAND("transport.command"),
    OPEN_HUMAN_FLOW("human_flow.open"),
}

/** Description only. Phase 03 has no effect runner. */
data class InertEffectDescription(
    val kind: EffectKind,
    val description: String,
    val provenance: ProvenanceStamp,
) {
    init {
        require(description.isNotBlank()) { "effect description must be nonblank" }
    }
}

data class ActionAcknowledgement(
    val receiptId: String,
    val actionType: ActionType,
    val changed: Boolean,
    val revision: Long,
    val sequence: Long,
    val effectiveValue: StateValue?,
    val provenance: ProvenanceStamp,
    val delta: StateDelta?,
    val effects: FrozenList<InertEffectDescription>,
) {
    init {
        require(receiptId.isNotBlank()) { "acknowledgement receipt id must be nonblank" }
        require(receiptId == provenance.receiptId) { "acknowledgement must reuse provenance receipt id" }
        require(revision >= 0L && sequence >= 0L) { "acknowledgement ordering must not be negative" }
        if (changed) {
            requireNotNull(delta) { "a changed acknowledgement requires a delta" }
            require(delta.revision == revision && delta.sequence == sequence) {
                "acknowledgement and delta ordering must match"
            }
            require(delta.provenance === provenance) {
                "acknowledgement and delta must reuse the exact provenance object"
            }
            require(effects.all { it.provenance === provenance }) {
                "effects must reuse the exact provenance object"
            }
            val changedFields = delta.changes.map { it.field }.toSet()
            require(changedFields.all { it in actionType.affectedFields }) {
                "delta fields must be declared by the accepted action type"
            }
            if (delta.changes.size == 1) {
                val change = delta.changes.single()
                requireNotNull(effectiveValue) { "a single-field acknowledgement requires its effective value" }
                require(effectiveValue.kind == change.field.valueKind && effectiveValue == change.newValue) {
                    "acknowledgement effective value must equal the accepted field value"
                }
            } else {
                require(effectiveValue == null) {
                    "multi-field acknowledgements must use the typed delta rather than one ambiguous effective value"
                }
            }
        } else {
            require(delta == null) { "a no-op must not emit a delta" }
            if (actionType.opensHumanFlow) {
                require(effectiveValue == null) { "a human-flow request must not claim an effective state value" }
                require(effects.isNotEmpty()) { "an accepted human-flow request requires an inert open-flow effect" }
                require(effects.all { it.kind == EffectKind.OPEN_HUMAN_FLOW && it.provenance === provenance }) {
                    "human-flow effects must be explicit and reuse accepted provenance"
                }
            } else if (actionType.affectedFields.size == 1) {
                require(effects.isEmpty()) { "a state no-op must not describe effects" }
                requireNotNull(effectiveValue) { "a single-field no-op requires its effective value" }
                require(effectiveValue.kind == actionType.affectedFields.single().valueKind) {
                    "no-op effective value must match the action field type"
                }
            } else {
                require(effects.isEmpty()) { "a state no-op must not describe effects" }
                require(effectiveValue == null) {
                    "zero- or multi-field no-ops must not publish an ambiguous effective value"
                }
            }
        }
    }
}

data class Refusal(
    val code: RefusalCode,
    val fix: String,
    val currentRevision: Long? = null,
    val originalReceiptId: String? = null,
) {
    init {
        require(fix.isNotBlank()) { "refusal fix must be nonblank" }
        require(currentRevision == null || currentRevision >= 0L) {
            "refusal current revision must not be negative"
        }
    }
}

enum class AuditKind(val wireName: String) {
    AUTHENTICATION("authentication"),
    HEARTBEAT_EXPIRY("heartbeat_expiry"),
    BINDER_DEATH("binder_death"),
    GRANT("grant"),
    REVOKE("revoke"),
    IDEMPOTENT_REPLAY("idempotent_replay"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    ACTION("action"),
    NO_OP("no_op"),
    REFUSAL("refusal"),
    CAPTURE_TRANSITION("capture_transition"),
    THEME_MUTATION("theme_mutation"),
    ELEVATED_OPERATION("elevated_operation"),
}

data class AuditRecord(
    val receiptId: String,
    val kind: AuditKind,
    val wallTimeMillis: Long,
    val actionType: ActionType? = null,
    val fields: FrozenSet<StateField> = frozenSetOf(),
    val provenance: ProvenanceStamp? = null,
    val refusal: Refusal? = null,
) {
    init {
        require(receiptId.isNotBlank()) { "audit receipt id must be nonblank" }
        require(wallTimeMillis >= 0L) { "audit wall time must not be negative" }
        if (provenance != null) {
            require(receiptId == provenance.receiptId) { "audit and provenance receipt ids must match" }
            require(kind == AuditKind.IDEMPOTENT_REPLAY || wallTimeMillis == provenance.wallTimeMillis) {
                "accepted audit time must reuse accepted provenance time"
            }
        }
        if (kind == AuditKind.REFUSAL || kind == AuditKind.IDEMPOTENCY_CONFLICT) {
            requireNotNull(refusal) { "$kind audit requires a refusal" }
            requireNotNull(actionType) { "$kind audit requires an action type" }
            require(provenance == null) { "$kind refusal audit must not invent accepted provenance" }
            require(fields == actionType.affectedFields) {
                "$kind audit fields must match the refused action contract"
            }
        }
        if (kind in setOf(
                AuditKind.ACTION,
                AuditKind.NO_OP,
                AuditKind.THEME_MUTATION,
                AuditKind.IDEMPOTENT_REPLAY,
            )
        ) {
            requireNotNull(actionType) { "$kind audit requires an action type" }
            requireNotNull(provenance) { "$kind audit requires accepted provenance" }
            require(fields == actionType.affectedFields) {
                "$kind audit fields must match the accepted action contract"
            }
        }
    }
}

data class AuditRetentionPolicy(
    val maximumRecords: Int,
    val maximumAgeMillis: Long,
) {
    init {
        require(maximumRecords > 0) { "audit retention must have a positive record bound" }
        require(maximumAgeMillis > 0L) { "audit retention must have a positive age bound" }
    }
}

data class AuditIndexSnapshot(
    val policy: AuditRetentionPolicy,
    val observedWallTimeMillis: Long,
    val records: FrozenList<AuditRecord>,
) {
    init {
        require(observedWallTimeMillis >= 0L) { "audit observation time must not be negative" }
        require(records.size <= policy.maximumRecords) { "audit index exceeds its record bound" }
        require(records.all { it.wallTimeMillis <= observedWallTimeMillis }) {
            "audit index cannot contain records from after its observation time"
        }
        require(records.all { observedWallTimeMillis - it.wallTimeMillis <= policy.maximumAgeMillis }) {
            "audit index contains a record older than its retention policy"
        }
    }
}

/**
 * Inert acceptance envelope that structurally enforces Fact 717's one authored provenance object.
 * It executes nothing. Phase 04 may emit this record only after authorization and reduction succeed.
 */
data class AcceptedActionRecord(
    val request: ActionRequest,
    val action: PhosphorAction,
    val canonicalPayload: CanonicalPayload,
    val acknowledgement: ActionAcknowledgement,
    val previousSnapshot: PhosphorStateSnapshot,
    val snapshot: PhosphorStateSnapshot,
    val audit: AuditRecord,
    val settingsProjections: FrozenList<SettingsRowProjection>,
) {
    init {
        val stamp = acknowledgement.provenance
        require(action.type == acknowledgement.actionType) {
            "accepted typed action and acknowledgement must agree"
        }
        require(action.validate() == null) { "an invalid typed action cannot be accepted" }
        require(canonicalPayload == action.canonicalPayload()) {
            "accepted canonical payload must be derived from the typed action"
        }
        require(request.principal == stamp.writer) { "accepted provenance writer must be the requesting principal" }
        require(request.reason == stamp.reason) { "accepted provenance reason must be the request reason" }
        require(request.transport == stamp.transport) { "accepted provenance transport must be the request transport" }
        require(request.sessionId == stamp.sessionId) { "accepted provenance session must be the request session" }
        require(request.requestedCapability == acknowledgement.actionType.requiredCapability) {
            "accepted request capability must match the action contract"
        }
        require(previousSnapshot.revision == request.expectedRevision) {
            "accepted action must name its exact previous revision"
        }
        require(previousSnapshot.wallTimeMillis <= stamp.wallTimeMillis) {
            "accepted action cannot precede its previous snapshot"
        }
        require(audit.receiptId == acknowledgement.receiptId) {
            "accepted acknowledgement and audit must share one receipt"
        }
        require(audit.actionType == acknowledgement.actionType) {
            "accepted acknowledgement and audit must share one action type"
        }
        require(audit.provenance === stamp) {
            "accepted audit must reuse the exact authored provenance object"
        }
        require(snapshot.revision == acknowledgement.revision && snapshot.sequence == acknowledgement.sequence) {
            "accepted snapshot ordering must match its acknowledgement"
        }

        if (acknowledgement.changed) {
            require(acknowledgement.sequence == Math.addExact(previousSnapshot.sequence, 1L)) {
                "a changed acceptance must advance exactly one sequence"
            }
            require(snapshot.wallTimeMillis == stamp.wallTimeMillis) {
                "a changed accepted snapshot must use the accepted provenance wall clock"
            }
            require(snapshot.schema == previousSnapshot.schema) { "an action cannot replace the state schema" }
            require(snapshot.distribution == previousSnapshot.distribution && snapshot.buildProfile == previousSnapshot.buildProfile) {
                "an action cannot replace the compiled distribution identity"
            }
            require(snapshot.session == previousSnapshot.session && snapshot.liveness == previousSnapshot.liveness) {
                "a settings action cannot mutate session liveness"
            }
            require(snapshot.capabilities == previousSnapshot.capabilities) {
                "a settings action cannot mutate capability grants"
            }
            require(snapshot.capture == previousSnapshot.capture) { "a settings action cannot mutate capture truth" }
            require(snapshot.availability == previousSnapshot.availability && snapshot.authority == previousSnapshot.authority) {
                "a settings action cannot mutate availability or authority planes"
            }
            require(snapshot.fixes == previousSnapshot.fixes) { "a settings action cannot mutate repair projections" }
            require(acknowledgement.revision == Math.addExact(request.expectedRevision, 1L)) {
                "a changed acceptance must advance exactly one revision"
            }
            require(audit.kind in setOf(AuditKind.ACTION, AuditKind.THEME_MUTATION)) {
                "a changed acceptance requires an accepted-action audit kind"
            }
            val delta = requireNotNull(acknowledgement.delta)
            val changesByField = delta.changes.associateBy { it.field }
            val actualChangedFields = StateField.entries.filterTo(linkedSetOf()) { field ->
                previousSnapshot.desired.valueOf(field) != snapshot.desired.valueOf(field) ||
                    previousSnapshot.effective.valueOf(field) != snapshot.effective.valueOf(field)
            }
            require(changesByField.keys == actualChangedFields) {
                "accepted delta must exactly cover the previous-to-post state diff"
            }
            changesByField.forEach { (field, change) ->
                require(
                    previousSnapshot.desired.valueOf(field) == change.oldValue &&
                        previousSnapshot.effective.valueOf(field) == change.oldValue,
                ) { "accepted delta old value must equal the previous snapshot" }
                require(
                    snapshot.desired.valueOf(field) == change.newValue &&
                        snapshot.effective.valueOf(field) == change.newValue,
                ) { "accepted delta new value must equal the post-state snapshot" }
            }
            StateField.entries.filter { it !in actualChangedFields }.forEach { field ->
                require(snapshot.provenance[field] === previousSnapshot.provenance[field]) {
                    "unchanged fields must preserve their exact provenance object"
                }
            }
            require((snapshot.contestedFields - previousSnapshot.contestedFields).all { it in actualChangedFields }) {
                "only changed fields may become contested"
            }
            require((previousSnapshot.contestedFields - snapshot.contestedFields).all { it in actualChangedFields }) {
                "only changed fields may clear contested status"
            }
            action.directStateIntent()?.let { intent ->
                require(intent.keys == action.type.affectedFields) {
                    "direct action intent must cover its complete affected-field contract"
                }
                require(changesByField.keys.all { it in intent.keys }) {
                    "direct action delta must remain within its typed intent"
                }
                changesByField.forEach { (field, change) ->
                    require(change.newValue == intent.getValue(field)) {
                        "accepted delta value must match the typed action intent"
                    }
                }
                intent.forEach { (field, value) ->
                    require(snapshot.desired.valueOf(field) == value && snapshot.effective.valueOf(field) == value) {
                        "accepted snapshot must expose every requested direct value"
                    }
                }
            }
            require(settingsProjections.map { it.schema.field }.toSet() == changesByField.keys) {
                "accepted settings projections must exactly cover changed fields"
            }
            changesByField.forEach { (field, change) ->
                require(snapshot.provenance[field] === stamp) {
                    "accepted snapshot field provenance must reuse the exact authored object"
                }
                val projection = settingsProjections.single { it.schema.field == field }
                require(projection.provenance === stamp && projection.visibleLastWriter === stamp) {
                    "accepted settings last-writer projection must reuse the exact authored object"
                }
                require(projection.desired == change.newValue && projection.effective == change.newValue) {
                    "accepted settings projection must expose the accepted effective value"
                }
            }
        } else {
            require(snapshot === previousSnapshot) {
                "a state no-op or effect-only human flow must retain the exact previous snapshot object"
            }
            require(snapshot.wallTimeMillis <= stamp.wallTimeMillis) {
                "a no-op snapshot cannot claim state from after its accepted request"
            }
            require(acknowledgement.revision == request.expectedRevision) {
                "a no-op acceptance must retain the expected revision"
            }
            if (action.type.opensHumanFlow) {
                require(acknowledgement.effects.isNotEmpty()) {
                    "an accepted human-flow action requires its explicit effect"
                }
                require(audit.kind == AuditKind.ACTION) {
                    "an accepted human-flow request requires an action audit"
                }
            } else {
                require(audit.kind == AuditKind.NO_OP) { "a no-op acceptance requires a no-op audit" }
            }
            require(settingsProjections.isEmpty()) { "a no-op acceptance must not project changed settings" }
            action.directStateIntent()?.let { intent ->
                require(intent.keys == action.type.affectedFields) {
                    "direct action intent must cover its complete affected-field contract"
                }
                intent.forEach { (field, value) ->
                    require(snapshot.desired.valueOf(field) == value && snapshot.effective.valueOf(field) == value) {
                        "a direct no-op requires the requested value to already be effective"
                    }
                }
                if (intent.size == 1) {
                    require(acknowledgement.effectiveValue == intent.values.single()) {
                        "a direct no-op effective value must match the typed action intent"
                    }
                }
            }
        }
    }
}

data class IdempotencyRecord(
    val principal: PrincipalId,
    val key: String,
    val action: PhosphorAction,
    val canonicalPayload: CanonicalPayload,
    val acknowledgement: ActionAcknowledgement,
    val acceptedWallTimeMillis: Long,
    val expiresWallTimeMillis: Long,
    val ordinal: Long,
) {
    init {
        require(key.isNotBlank()) { "idempotency record key must be nonblank" }
        require(acceptedWallTimeMillis >= 0L) { "accepted time must not be negative" }
        require(expiresWallTimeMillis >= acceptedWallTimeMillis) {
            "idempotency expiry must not precede acceptance"
        }
        require(expiresWallTimeMillis - acceptedWallTimeMillis >= IDEMPOTENCY_TTL_MILLIS) {
            "idempotency record must reserve the key for at least 24 hours"
        }
        require(ordinal >= 0L) { "idempotency ordinal must not be negative" }
        require(acknowledgement.provenance.writer == principal) {
            "idempotency principal must match the accepted acknowledgement writer"
        }
        require(action.type == acknowledgement.actionType) {
            "idempotency typed action and acknowledgement must match"
        }
        require(action.validate() == null) { "an invalid typed action cannot enter the idempotency index" }
        require(canonicalPayload == action.canonicalPayload()) {
            "idempotency payload must be derived from its typed action"
        }
        action.directStateIntent()?.let { intent ->
            require(intent.keys == action.type.affectedFields) {
                "direct idempotency intent must cover its complete affected-field contract"
            }
            if (acknowledgement.changed) {
                val changes = requireNotNull(acknowledgement.delta).changes.associateBy { it.field }
                require(changes.keys.all { it in intent.keys }) {
                    "idempotency delta must remain within its typed intent"
                }
                changes.forEach { (field, change) ->
                    require(change.newValue == intent.getValue(field)) {
                        "idempotency delta value must match the typed action intent"
                    }
                }
            } else if (intent.size == 1) {
                require(acknowledgement.effectiveValue == intent.values.single()) {
                    "direct idempotency no-op value must match the typed action intent"
                }
            }
        }
        require(acceptedWallTimeMillis == acknowledgement.provenance.wallTimeMillis) {
            "idempotency acceptance time must match accepted provenance"
        }
    }
}

enum class IdempotencyOverflowBehavior(val wireName: String) {
    REFUSE_NEW("refuse_new"),
}

data class IdempotencyIndexPolicy(
    val minimumTtlMillis: Long = IDEMPOTENCY_TTL_MILLIS,
    val maximumRecords: Int,
    val overflowBehavior: IdempotencyOverflowBehavior = IdempotencyOverflowBehavior.REFUSE_NEW,
) {
    init {
        require(minimumTtlMillis >= IDEMPOTENCY_TTL_MILLIS) {
            "idempotency policy must retain keys for at least 24 hours"
        }
        require(maximumRecords > 0) { "idempotency index must have a positive bound" }
    }
}

data class IdempotencyIndexSnapshot(
    val policy: IdempotencyIndexPolicy,
    val records: FrozenList<IdempotencyRecord>,
) {
    init {
        require(records.size <= policy.maximumRecords) { "idempotency index exceeds its bound" }
        require(records.all {
            it.expiresWallTimeMillis - it.acceptedWallTimeMillis >= policy.minimumTtlMillis
        }) { "idempotency record expires before policy minimum" }
        require(records.map { it.principal to it.key }.distinct().size == records.size) {
            "idempotency index keys must be unique per principal"
        }
    }
}
