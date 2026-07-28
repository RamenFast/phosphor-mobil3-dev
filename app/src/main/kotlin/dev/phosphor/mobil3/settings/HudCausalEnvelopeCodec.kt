package dev.phosphor.mobil3.settings

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import dev.phosphor.mobil3.state.ActionAcknowledgement
import dev.phosphor.mobil3.state.ActionType
import dev.phosphor.mobil3.state.AuditIndexSnapshot
import dev.phosphor.mobil3.state.AuditKind
import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.AuditRetentionPolicy
import dev.phosphor.mobil3.state.EffectKind
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.FrozenSet
import dev.phosphor.mobil3.state.IDEMPOTENCY_TTL_MILLIS
import dev.phosphor.mobil3.state.IdempotencyIndexPolicy
import dev.phosphor.mobil3.state.IdempotencyIndexSnapshot
import dev.phosphor.mobil3.state.IdempotencyRecord
import dev.phosphor.mobil3.state.InertEffectDescription
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.ProvenanceStamp
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.StateChange
import dev.phosphor.mobil3.state.StateDelta
import dev.phosphor.mobil3.state.StateField
import dev.phosphor.mobil3.state.StringStateValue
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenListOf
import dev.phosphor.mobil3.state.frozenMapOf
import dev.phosphor.mobil3.store.PhosphorStoreImage
import dev.phosphor.mobil3.store.NEXUS_LIFECYCLE_AUDIT_KINDS

object HudCausalEnvelopeCodec {
    const val LEGACY_SCHEMA = "phosphor.causal.hud/1"
    const val SCHEMA = "phosphor.causal.hud/2"
    internal const val MAX_BYTES = 512 * 1024
    private val hudReceipt = Regex("hud-(\\d+)")

    fun encode(image: PhosphorStoreImage, compiledBaseSnapshot: PhosphorStateSnapshot): String {
        validateCompiledBaseSlice(image.snapshot, compiledBaseSnapshot)
        return encode(image)
    }

    fun encode(image: PhosphorStoreImage): String {
        val snapshot = image.snapshot
        validateSupportedImage(image)
        val snapshotProvenance = snapshot.provenance[StateField.DISPLAY_HUD]
        val provenancePool = linkedMapOf<String, ProvenanceStamp>()
        val retainedReceiptIds = mutableListOf<String>()
        fun remember(stamp: ProvenanceStamp?) {
            if (stamp != null) {
                val existing = provenancePool.putIfAbsent(stamp.receiptId, stamp)
                if (existing != null && existing !== stamp) {
                    throw HudCausalCodecException("duplicate_provenance_object", "HUD causal image repeats receipt '${stamp.receiptId}' with a different provenance object", "Rebuild the HUD image so each retained receipt id maps to exactly one provenance instance")
                }
                retainedReceiptIds += stamp.receiptId
            }
        }
        remember(snapshotProvenance)
        image.audit.records.forEach {
            retainedReceiptIds += it.receiptId
            remember(it.provenance)
        }
        image.idempotency.records.forEach { record ->
            retainedReceiptIds += record.acknowledgement.receiptId
            remember(record.acknowledgement.provenance)
            record.acknowledgement.delta?.let { delta ->
                retainedReceiptIds += delta.provenance.receiptId
                remember(delta.provenance)
                delta.changes.forEach {
                    retainedReceiptIds += it.provenance.receiptId
                    remember(it.provenance)
                }
            }
            record.acknowledgement.effects.forEach {
                retainedReceiptIds += it.provenance.receiptId
                remember(it.provenance)
            }
        }
        validateCounters(image.nextReceiptOrdinal, image.nextIdempotencyOrdinal, retainedReceiptIds, image.idempotency.records.map { it.ordinal })

        val payload = JSONObject()
            .put("revision", snapshot.revision)
            .put("sequence", snapshot.sequence)
            .put("wall_time_millis", snapshot.wallTimeMillis)
            .put("distribution", snapshot.distribution.wireName)
            .put("build_profile", snapshot.buildProfile.wireName)
            .put("display_hud", snapshot.effective.displayHud)
            .put("snapshot_provenance_receipt_id", snapshotProvenance?.receiptId ?: JSONObject.NULL)
            .put("next_receipt_ordinal", image.nextReceiptOrdinal)
            .put("next_idempotency_ordinal", image.nextIdempotencyOrdinal)
            .put("authority_plane", image.authorityPlane ?: JSONObject.NULL)
            .put("provenance_pool", JSONArray().also { array -> provenancePool.values.forEach { array.put(provenanceJson(it)) } })
            .put("audit", auditJson(image.audit))
            .put("idempotency", idempotencyJson(image.idempotency))
        val payloadBytes = canonicalJson(payload)
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("payload", payload)
            .put("checksum_sha256", sha256(payloadBytes))
        val encoded = canonicalJson(root)
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw HudCausalCodecException("envelope_too_large", "HUD causal envelope exceeds $MAX_BYTES bytes", "Reduce HUD audit/idempotency retention before saving")
        }
        return encoded
    }

    fun decode(encoded: String, compiledBaseSnapshot: PhosphorStateSnapshot): PhosphorStoreImage = try {
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw HudCausalCodecException("envelope_too_large", "HUD causal envelope exceeds $MAX_BYTES bytes", "Preserve the corrupt envelope and run explicit repair")
        }
        val root = try { JSONObject(encoded) } catch (error: Exception) {
            throw HudCausalCodecException("invalid_json", "HUD causal envelope is not valid JSON", "Preserve the corrupt envelope and run explicit migration")
        }
        rejectDecimalIntegerLiterals(encoded)
        root.requireKeys("root", setOf("schema", "payload", "checksum_sha256"))
        if (root.requiredString("schema") == LEGACY_SCHEMA) {
            throw HudCausalCodecException("schema_migration_required", "HUD causal schema '${root.requiredString("schema")}' must be migrated explicitly", "Run the explicit HUD causal schema migration so the v2 checksum-bound envelope can be verified before load")
        }
        if (root.requiredString("schema") != SCHEMA) {
            throw HudCausalCodecException("unsupported_schema", "Unsupported HUD causal schema '${root.requiredString("schema")}'", "Upgrade Phosphor or run explicit schema migration")
        }
        val payload = root.requiredObject("payload").also { it.requireKeys("payload", PAYLOAD_KEYS) }
        val checksum = root.requiredString("checksum_sha256")
        if (!checksum.matches(Regex("[0-9a-f]{64}")) || checksum != sha256(canonicalJson(payload))) {
            throw HudCausalCodecException("checksum_mismatch", "HUD causal envelope checksum does not match", "Do not fall back silently; preserve the corrupt envelope and run explicit repair")
        }
        val distribution = payload.requiredString("distribution")
        val buildProfile = payload.requiredString("build_profile")
        if (distribution != compiledBaseSnapshot.distribution.wireName || buildProfile != compiledBaseSnapshot.buildProfile.wireName) {
            throw HudCausalCodecException("compiled_identity_mismatch", "HUD causal envelope was saved for $distribution/$buildProfile", "Load with a matching Phosphor build or perform an explicit migration")
        }
        val displayHud = payload.requiredString("display_hud")
        if (displayHud !in setOf("on", "auto", "off")) {
            throw HudCausalCodecException("hud_mode_invalid", "HUD causal display_hud is invalid", "Repair to on, auto, or off through the store")
        }
        val provenanceArray = payload.requiredArray("provenance_pool", 256)
        val provenancePool = provenanceArray.mapObjects(::provenance).associateBy { it.receiptId }
        if (provenancePool.size != provenanceArray.length()) {
            throw HudCausalCodecException("duplicate_provenance", "HUD causal provenance pool repeats a receipt id", "Repair the causal envelope through the store")
        }
        val snapshotProvenance = payload.optNullableString("snapshot_provenance_receipt_id")?.let { receiptId -> provenancePool[receiptId] ?: throw missingProvenance(receiptId) }
        val snapshot = compiledBaseSnapshot.copy(
            revision = payload.requiredLong("revision"),
            sequence = payload.requiredLong("sequence"),
            wallTimeMillis = payload.requiredLong("wall_time_millis"),
            desired = compiledBaseSnapshot.desired.copy(displayHud = displayHud),
            effective = compiledBaseSnapshot.effective.copy(displayHud = displayHud),
            provenance = snapshotProvenance?.let { frozenMapOf(StateField.DISPLAY_HUD to it) } ?: compiledBaseSnapshot.provenance,
        )
        val audit = audit(payload.requiredObject("audit"), provenancePool)
        val idempotency = idempotency(payload.requiredObject("idempotency"), provenancePool)
        val nextReceiptOrdinal = payload.requiredLong("next_receipt_ordinal")
        val nextIdempotencyOrdinal = payload.requiredLong("next_idempotency_ordinal")
        val authorityPlane = payload.optNullableString("authority_plane")
        val retainedReceiptIds = mutableListOf<String>()
        val provenanceReferenceReceiptIds = mutableListOf<String>()
        snapshotProvenance?.let {
            retainedReceiptIds += it.receiptId
            provenanceReferenceReceiptIds += it.receiptId
        }
        audit.records.forEach { record ->
            retainedReceiptIds += record.receiptId
            record.provenance?.let { stamp ->
                retainedReceiptIds += stamp.receiptId
                provenanceReferenceReceiptIds += stamp.receiptId
            }
        }
        idempotency.records.forEach { record ->
            retainedReceiptIds += record.acknowledgement.receiptId
            retainedReceiptIds += record.acknowledgement.provenance.receiptId
            provenanceReferenceReceiptIds += record.acknowledgement.provenance.receiptId
            record.acknowledgement.delta?.let { delta ->
                retainedReceiptIds += delta.provenance.receiptId
                provenanceReferenceReceiptIds += delta.provenance.receiptId
                delta.changes.forEach { retainedReceiptIds += it.provenance.receiptId }
                delta.changes.forEach { provenanceReferenceReceiptIds += it.provenance.receiptId }
            }
            record.acknowledgement.effects.forEach { retainedReceiptIds += it.provenance.receiptId }
            record.acknowledgement.effects.forEach { provenanceReferenceReceiptIds += it.provenance.receiptId }
        }
        validateNoOrphanProvenance(provenancePool.keys, provenanceReferenceReceiptIds)
        validateCounters(nextReceiptOrdinal, nextIdempotencyOrdinal, retainedReceiptIds, idempotency.records.map { it.ordinal })
        PhosphorStoreImage(
            snapshot = snapshot,
            audit = audit,
            idempotency = idempotency,
            nextReceiptOrdinal = nextReceiptOrdinal,
            nextIdempotencyOrdinal = nextIdempotencyOrdinal,
            authorityPlane = authorityPlane,
        ).also { image ->
            validateSupportedImage(image)
            validateCanonicalEncoding(encoded, image)
        }
    } catch (error: HudCausalCodecException) {
        throw error
    } catch (error: JSONException) {
        throw HudCausalCodecException("malformed_payload", "HUD causal envelope contains a malformed JSON value", "Preserve the corrupt envelope and rebuild through explicit repair")
    } catch (error: ClassCastException) {
        throw HudCausalCodecException("malformed_payload", "HUD causal envelope contains an unsupported value type", "Preserve the corrupt envelope and rebuild through explicit repair")
    } catch (error: IllegalArgumentException) {
        throw HudCausalCodecException("malformed_payload", "HUD causal envelope violates the HUD causal contract: ${error.message ?: "invalid value"}", "Preserve the corrupt envelope and rebuild through explicit repair")
    }

    fun migrateLegacyV1(encoded: String, compiledBaseSnapshot: PhosphorStateSnapshot): String {
        val root = try { JSONObject(encoded) } catch (_: Exception) {
            throw HudCausalCodecException("invalid_json", "Legacy HUD causal envelope is not valid JSON", "Preserve the corrupt envelope and run explicit repair")
        }
        if (root.optString("schema") != LEGACY_SCHEMA) {
            throw HudCausalCodecException("unsupported_schema", "Explicit migration only accepts $LEGACY_SCHEMA", "Use the matching migration for this envelope schema")
        }
        root.requireKeys("root", setOf("schema", "payload", "checksum_sha256"))
        val legacyPayload = root.requiredObject("payload").also { it.requireKeys("payload", LEGACY_PAYLOAD_KEYS) }
        val legacyChecksum = root.requiredString("checksum_sha256")
        if (!legacyChecksum.matches(Regex("[0-9a-f]{64}")) || legacyChecksum != sha256(canonicalJson(legacyPayload))) {
            throw HudCausalCodecException("checksum_mismatch", "Legacy HUD causal envelope checksum does not match", "Do not migrate a tampered v1 envelope; preserve it and run explicit repair")
        }
        root.put("schema", SCHEMA)
        val payload = legacyPayload
        payload.put("authority_plane", JSONObject.NULL)
        root.put("checksum_sha256", sha256(canonicalJson(payload)))
        return canonicalJson(root).also { decode(it, compiledBaseSnapshot) }
    }

    private fun validateSupportedImage(image: PhosphorStoreImage) {
        if (image.snapshot.desired.displayHud != image.snapshot.effective.displayHud) {
            throw HudCausalCodecException("unsupported_image", "HUD causal image has divergent desired/effective HUD values", "Persist only a reconciled HUD slice image")
        }
        image.audit.records.forEach { record ->
            if (record.kind in NEXUS_LIFECYCLE_AUDIT_KINDS) {
                if (record.actionType != null || record.fields.isNotEmpty() || record.provenance != null || record.refusal != null) {
                    throw HudCausalCodecException("unsupported_image", "Nexus lifecycle audit records must be actionless", "Persist lifecycle audit with only receipt, kind, and wall time")
                }
                return@forEach
            }
            if (record.kind !in HUD_AUDIT_KINDS) {
                throw HudCausalCodecException("unsupported_image", "HUD causal audit kind is outside the HUD reducer grammar", "Persist only HUD reducer audit records")
            }
            if (record.actionType != ActionType.SET_DISPLAY_HUD) {
                throw HudCausalCodecException("unsupported_image", "HUD causal audit action is not exactly display.hud", "Persist only HUD-slice audit records")
            }
            if (record.fields != FrozenSet.copyOf(setOf(StateField.DISPLAY_HUD))) {
                throw HudCausalCodecException("unsupported_image", "HUD causal audit fields are not exactly display.hud", "Persist only HUD-slice audit records")
            }
            validatePrincipal(record.provenance)
        }
        image.idempotency.records.forEach { record ->
            if (record.action !is SetDisplayHud) {
                throw HudCausalCodecException("unsupported_image", "HUD causal idempotency contains a non-SetDisplayHud action", "Persist only HUD-slice idempotency records")
            }
            validatePrincipal(record.principal)
            validateHudAcknowledgement(record.acknowledgement)
            validateAcknowledgementOrder(record.acknowledgement, image.snapshot)
        }
        validatePrincipal(image.snapshot.provenance[StateField.DISPLAY_HUD])
        validatePolicyCaps(image)
    }

    private fun validateHudAcknowledgement(value: ActionAcknowledgement) {
        if (value.actionType != ActionType.SET_DISPLAY_HUD) {
            throw HudCausalCodecException("unsupported_image", "HUD causal acknowledgement is not for display.hud", "Persist only HUD-slice acknowledgements")
        }
        value.effectiveValue?.let {
            if (it !is StringStateValue) throw HudCausalCodecException("unsupported_image", "HUD causal acknowledgement effective value is not a HUD string", "Persist only HUD-slice acknowledgements")
        }
        value.delta?.changes?.forEach { change ->
            if (change.field != StateField.DISPLAY_HUD || change.oldValue !is StringStateValue || change.newValue !is StringStateValue) {
                throw HudCausalCodecException("unsupported_image", "HUD causal delta contains a non-HUD field or value", "Persist only HUD display.hud deltas")
            }
        }
        if (value.effects.isNotEmpty()) {
            throw HudCausalCodecException("unsupported_image", "HUD causal schema does not retain inert effects", "Persist only the reducer-emitted empty HUD effects list")
        }
        validatePrincipal(value.provenance)
    }

    private fun validateAcknowledgementOrder(value: ActionAcknowledgement, snapshot: PhosphorStateSnapshot) {
        if (value.revision > snapshot.revision || value.sequence > snapshot.sequence) {
            throw HudCausalCodecException("ordering_invalid", "HUD causal acknowledgement is newer than the retained snapshot", "Repair the envelope so retained acknowledgements are ordered before or at the snapshot")
        }
        value.delta?.let { delta ->
            if (delta.revision != value.revision || delta.sequence != value.sequence) {
                throw HudCausalCodecException("ordering_invalid", "HUD causal delta is not bound to its acknowledgement revision and sequence", "Repair the envelope through the store")
            }
            if (delta.provenance.receiptId != value.provenance.receiptId || delta.changes.any { it.provenance.receiptId != value.provenance.receiptId }) {
                throw HudCausalCodecException("ordering_invalid", "HUD causal delta provenance is not bound to its acknowledgement", "Repair the envelope through the store")
            }
        }
        snapshot.provenance[StateField.DISPLAY_HUD]?.let { stamp ->
            if (stamp.receiptId == value.receiptId && (value.revision != snapshot.revision || value.sequence != snapshot.sequence)) {
                throw HudCausalCodecException("ordering_invalid", "HUD causal acknowledgement for the snapshot HUD value does not match the snapshot revision and sequence", "Repair the envelope through the store")
            }
        }
    }

    private fun validatePolicyCaps(image: PhosphorStoreImage) {
        if (image.audit.policy.maximumRecords != 128 || image.audit.policy.maximumAgeMillis != IDEMPOTENCY_TTL_MILLIS) {
            throw HudCausalCodecException("field_invalid", "HUD causal audit policy is not the reducer default", "Persist only the exact HUD reducer audit retention policy")
        }
        if (image.idempotency.policy.maximumRecords != 128 || image.idempotency.policy.minimumTtlMillis != IDEMPOTENCY_TTL_MILLIS) {
            throw HudCausalCodecException("field_invalid", "HUD causal idempotency policy is not the reducer default", "Persist only the exact HUD reducer idempotency policy")
        }
        if (image.audit.policy.maximumRecords > 128 || image.audit.records.size > 128) throw capExceeded("audit.records", 128)
        if (image.idempotency.policy.maximumRecords > 128 || image.idempotency.records.size > 128) throw capExceeded("idempotency.records", 128)
        val provenanceIds = linkedSetOf<String>()
        fun remember(stamp: ProvenanceStamp?) { if (stamp != null) provenanceIds += stamp.receiptId }
        remember(image.snapshot.provenance[StateField.DISPLAY_HUD])
        image.audit.records.forEach { record ->
            if (record.fields.size > 64) throw capExceeded("audit.fields", 64)
            remember(record.provenance)
        }
        image.idempotency.records.forEach { record ->
            remember(record.acknowledgement.provenance)
            record.acknowledgement.delta?.let { delta ->
                if (delta.changes.size > 8) throw capExceeded("delta.changes", 8)
                remember(delta.provenance)
                validatePrincipal(delta.provenance)
                delta.changes.forEach { remember(it.provenance) }
                delta.changes.forEach { validatePrincipal(it.provenance) }
            }
            if (record.acknowledgement.effects.size > 32) throw capExceeded("acknowledgement.effects", 32)
            record.acknowledgement.effects.forEach { remember(it.provenance) }
        }
        if (provenanceIds.size > 256) throw capExceeded("provenance_pool", 256)
    }

    private fun validateCompiledBaseSlice(snapshot: PhosphorStateSnapshot, compiledBaseSnapshot: PhosphorStateSnapshot) {
        val nonHudProvenance = snapshot.provenance.filterKeys { it != StateField.DISPLAY_HUD }
        if (nonHudProvenance != compiledBaseSnapshot.provenance.filterKeys { it != StateField.DISPLAY_HUD }) {
            throw HudCausalCodecException("unsupported_image", "HUD causal image contains non-HUD provenance that would be dropped on decode", "Persist only DISPLAY_HUD provenance in the HUD causal slice")
        }
        val normalized = snapshot.copy(
            revision = compiledBaseSnapshot.revision,
            sequence = compiledBaseSnapshot.sequence,
            wallTimeMillis = compiledBaseSnapshot.wallTimeMillis,
            desired = snapshot.desired.copy(displayHud = compiledBaseSnapshot.desired.displayHud),
            effective = snapshot.effective.copy(displayHud = compiledBaseSnapshot.effective.displayHud),
            provenance = compiledBaseSnapshot.provenance,
        )
        if (normalized != compiledBaseSnapshot) {
            throw HudCausalCodecException("unsupported_image", "HUD causal image contains non-HUD snapshot state that would be dropped on decode", "Persist only a HUD slice over the compiled base snapshot")
        }
    }

    private fun validatePrincipal(stamp: ProvenanceStamp?) {
        if (stamp != null) validatePrincipal(stamp.writer, stamp.transport, stamp.sessionId)
    }

    private fun validatePrincipal(principal: PrincipalId) {
        if (!isAllowedPrincipal(principal, null, null)) {
            throw HudCausalCodecException("unsupported_image", "HUD causal principal is outside declared HUD writers", "Persist only the local HUD principals or an authenticated Nexus principal")
        }
    }

    private fun validatePrincipal(principal: PrincipalId, transport: Transport, sessionId: String?) {
        if (!isAllowedPrincipal(principal, transport, sessionId)) {
            throw HudCausalCodecException("unsupported_image", "HUD causal provenance is outside declared HUD writers", "Persist local HUD provenance without a session, or Nexus provenance through Binder/tailnet with a nonblank session")
        }
    }

    private fun isAllowedPrincipal(principal: PrincipalId, transport: Transport?, sessionId: String?): Boolean {
        val local = sessionId == null && (
            (principal.kind == PrincipalKind.HUMAN && principal.stableId == "local-human" && (transport == null || transport == Transport.UI)) ||
                (principal.kind == PrincipalKind.MIGRATION && principal.stableId == "local-hud-preferences" && (transport == null || transport == Transport.MIGRATION))
            )
        val nexus = principal.kind == PrincipalKind.NEXUS && when (transport) {
            null -> sessionId == null
            Transport.BINDER,
            Transport.TAILNET,
            -> !sessionId.isNullOrBlank()
            else -> false
        }
        return local || nexus
    }

    private fun provenanceJson(value: ProvenanceStamp): JSONObject = JSONObject()
        .put("receipt_id", value.receiptId)
        .put("principal_kind", value.writer.kind.wireName)
        .put("principal_id", value.writer.stableId)
        .put("reason", value.reason)
        .put("transport", value.transport.wireName)
        .put("session_id", value.sessionId ?: JSONObject.NULL)
        .put("monotonic_millis", value.monotonicMillis)
        .put("wall_time_millis", value.wallTimeMillis)

    private fun provenance(value: JSONObject): ProvenanceStamp = value.also { it.requireKeys("provenance", PROVENANCE_KEYS) }.let { provenanceJson -> ProvenanceStamp(
        receiptId = provenanceJson.requiredString("receipt_id"),
        writer = PrincipalId(principalKind(provenanceJson.requiredString("principal_kind")), provenanceJson.requiredString("principal_id")),
        reason = provenanceJson.requiredString("reason"),
        transport = transport(provenanceJson.requiredString("transport")),
        sessionId = provenanceJson.optNullableString("session_id"),
        monotonicMillis = provenanceJson.requiredLong("monotonic_millis"),
        wallTimeMillis = provenanceJson.requiredLong("wall_time_millis"),
    ).also(::validatePrincipal) }

    private fun auditJson(value: AuditIndexSnapshot): JSONObject = JSONObject()
        .put("policy_maximum_records", value.policy.maximumRecords)
        .put("policy_maximum_age_millis", value.policy.maximumAgeMillis)
        .put("observed_wall_time_millis", value.observedWallTimeMillis)
        .put("records", JSONArray().also { array ->
            value.records.forEach { record ->
                array.put(JSONObject()
                    .put("receipt_id", record.receiptId)
                    .put("kind", record.kind.wireName)
                    .put("wall_time_millis", record.wallTimeMillis)
                    .put("action_type", record.actionType?.wireName ?: JSONObject.NULL)
                    .put("fields", JSONArray(record.fields.map { it.wireName }))
                    .put("provenance_receipt_id", record.provenance?.receiptId ?: JSONObject.NULL)
                    .put("refusal", record.refusal?.let(::refusalJson) ?: JSONObject.NULL))
            }
        })

    private fun audit(value: JSONObject, provenancePool: Map<String, ProvenanceStamp>): AuditIndexSnapshot = value.also { it.requireKeys("audit", AUDIT_KEYS) }.let { auditJson ->
        val maximumRecords = auditJson.requiredInt("policy_maximum_records")
        val maximumAgeMillis = auditJson.requiredLong("policy_maximum_age_millis")
        if (maximumRecords != 128 || maximumAgeMillis != IDEMPOTENCY_TTL_MILLIS) {
            throw HudCausalCodecException("field_invalid", "HUD causal audit policy is not the reducer default", "Persist only the exact HUD reducer audit retention policy")
        }
        AuditIndexSnapshot(
        policy = AuditRetentionPolicy(maximumRecords, maximumAgeMillis),
        observedWallTimeMillis = auditJson.requiredLong("observed_wall_time_millis"),
        records = FrozenList.copyOf(auditJson.requiredArray("records", 128).mapObjects { item ->
            item.requireKeys("audit.records[]", AUDIT_RECORD_KEYS)
            val receiptId = item.optNullableString("provenance_receipt_id")
            AuditRecord(
                receiptId = item.requiredString("receipt_id"),
                kind = auditKind(item.requiredString("kind")),
                wallTimeMillis = item.requiredLong("wall_time_millis"),
                actionType = item.optNullableString("action_type")?.let(::actionType),
                fields = FrozenSet.copyOf(item.requiredArray("fields", 64).mapStrings().map(::stateField)),
                provenance = receiptId?.let { provenancePool[it] ?: throw missingProvenance(it) },
                refusal = if (item.isNull("refusal")) null else refusal(item.requiredObject("refusal")),
            ).also { record ->
                if (record.kind in NEXUS_LIFECYCLE_AUDIT_KINDS) {
                    if (record.actionType != null || record.fields.isNotEmpty() || record.provenance != null || record.refusal != null) {
                        throw HudCausalCodecException("unsupported_image", "Nexus lifecycle audit record is not actionless", "Repair lifecycle audit records to contain only receipt, kind, and wall time")
                    }
                    return@also
                }
                if (record.kind !in HUD_AUDIT_KINDS || record.actionType != ActionType.SET_DISPLAY_HUD || record.fields != FrozenSet.copyOf(setOf(StateField.DISPLAY_HUD))) {
                    throw HudCausalCodecException("unsupported_image", "HUD causal audit record is outside the DisplayHudReducer contract", "Repair retained audit records to SET_DISPLAY_HUD DISPLAY_HUD HUD reducer kinds")
                }
                validatePrincipal(record.provenance)
            }
        }),
    ) }

    private fun idempotencyJson(value: IdempotencyIndexSnapshot): JSONObject = JSONObject()
        .put("policy_minimum_ttl_millis", value.policy.minimumTtlMillis)
        .put("policy_maximum_records", value.policy.maximumRecords)
        .put("records", JSONArray().also { array ->
            value.records.forEach { record ->
                array.put(JSONObject()
                    .put("principal_kind", record.principal.kind.wireName)
                    .put("principal_id", record.principal.stableId)
                    .put("key", record.key)
                    .put("mode", (record.action as? SetDisplayHud)?.mode ?: JSONObject.NULL)
                    .put("acknowledgement", acknowledgementJson(record.acknowledgement))
                    .put("accepted_wall_time_millis", record.acceptedWallTimeMillis)
                    .put("expires_wall_time_millis", record.expiresWallTimeMillis)
                    .put("ordinal", record.ordinal))
            }
        })

    private fun idempotency(value: JSONObject, provenancePool: Map<String, ProvenanceStamp>): IdempotencyIndexSnapshot = value.also { it.requireKeys("idempotency", IDEMPOTENCY_KEYS) }.let { idempotencyJson ->
        val minimumTtlMillis = idempotencyJson.requiredLong("policy_minimum_ttl_millis")
        val maximumRecords = idempotencyJson.requiredInt("policy_maximum_records")
        if (minimumTtlMillis != IDEMPOTENCY_TTL_MILLIS || maximumRecords != 128) {
            throw HudCausalCodecException("field_invalid", "HUD causal idempotency policy is not the reducer default", "Persist only the exact HUD reducer idempotency policy")
        }
        IdempotencyIndexSnapshot(
        policy = IdempotencyIndexPolicy(
            minimumTtlMillis = minimumTtlMillis,
            maximumRecords = maximumRecords,
        ),
        records = FrozenList.copyOf(idempotencyJson.requiredArray("records", 128).mapObjects { item ->
            item.requireKeys("idempotency.records[]", IDEMPOTENCY_RECORD_KEYS)
            val mode = item.requiredString("mode")
            val action = SetDisplayHud(mode)
            IdempotencyRecord(
                principal = PrincipalId(principalKind(item.requiredString("principal_kind")), item.requiredString("principal_id")).also(::validatePrincipal),
                key = item.requiredString("key"),
                action = action,
                canonicalPayload = action.canonicalPayload(),
                acknowledgement = acknowledgement(item.requiredObject("acknowledgement"), provenancePool),
                acceptedWallTimeMillis = item.requiredLong("accepted_wall_time_millis"),
                expiresWallTimeMillis = item.requiredLong("expires_wall_time_millis"),
                ordinal = item.requiredLong("ordinal"),
            )
        }),
    ) }

    private fun acknowledgementJson(value: ActionAcknowledgement): JSONObject = JSONObject()
        .put("receipt_id", value.receiptId)
        .put("action_type", value.actionType.wireName)
        .put("changed", value.changed)
        .put("revision", value.revision)
        .put("sequence", value.sequence)
        .put("effective_value", (value.effectiveValue as? StringStateValue)?.value ?: JSONObject.NULL)
        .put("provenance_receipt_id", value.provenance.receiptId)
        .put("delta", value.delta?.let(::deltaJson) ?: JSONObject.NULL)
        .put("effects", JSONArray().also { array -> value.effects.forEach { array.put(effectJson(it)) } })

    private fun acknowledgement(value: JSONObject, provenancePool: Map<String, ProvenanceStamp>): ActionAcknowledgement = value.also { it.requireKeys("acknowledgement", ACKNOWLEDGEMENT_KEYS) }.let { ackJson -> ActionAcknowledgement(
        receiptId = ackJson.requiredString("receipt_id"),
        actionType = actionType(ackJson.requiredString("action_type")),
        changed = ackJson.requiredBoolean("changed"),
        revision = ackJson.requiredLong("revision"),
        sequence = ackJson.requiredLong("sequence"),
        effectiveValue = ackJson.optNullableString("effective_value")?.let(::StringStateValue),
        provenance = provenancePool[ackJson.requiredString("provenance_receipt_id")] ?: throw missingProvenance(ackJson.requiredString("provenance_receipt_id")),
        delta = if (ackJson.isNull("delta")) null else delta(ackJson.requiredObject("delta"), provenancePool),
        effects = FrozenList.copyOf(ackJson.requiredArray("effects", 0).mapObjects { effect(it, provenancePool) }),
    ) }

    private fun deltaJson(value: StateDelta): JSONObject = JSONObject()
        .put("revision", value.revision)
        .put("sequence", value.sequence)
        .put("provenance_receipt_id", value.provenance.receiptId)
        .put("changes", JSONArray().also { array ->
            value.changes.forEach { change ->
                array.put(JSONObject()
                    .put("field", change.field.wireName)
                    .put("old", (change.oldValue as StringStateValue).value)
                    .put("new", (change.newValue as StringStateValue).value)
                    .put("provenance_receipt_id", change.provenance.receiptId))
            }
        })

    private fun delta(value: JSONObject, provenancePool: Map<String, ProvenanceStamp>): StateDelta = value.also { it.requireKeys("delta", DELTA_KEYS) }.let { deltaJson -> StateDelta(
        revision = deltaJson.requiredLong("revision"),
        sequence = deltaJson.requiredLong("sequence"),
        changes = FrozenList.copyOf(deltaJson.requiredArray("changes", 8).mapObjects { item ->
            item.requireKeys("delta.changes[]", CHANGE_KEYS)
            StateChange(
                field = stateField(item.requiredString("field")),
                oldValue = StringStateValue(item.requiredString("old")),
                newValue = StringStateValue(item.requiredString("new")),
                provenance = provenancePool[item.requiredString("provenance_receipt_id")] ?: throw missingProvenance(item.requiredString("provenance_receipt_id")),
            )
        }),
        provenance = provenancePool[deltaJson.requiredString("provenance_receipt_id")] ?: throw missingProvenance(deltaJson.requiredString("provenance_receipt_id")),
    ) }

    private fun effectJson(value: InertEffectDescription): JSONObject = JSONObject()
        .put("kind", value.kind.wireName)
        .put("description", value.description)
        .put("provenance_receipt_id", value.provenance.receiptId)

    private fun effect(value: JSONObject, provenancePool: Map<String, ProvenanceStamp>): InertEffectDescription = value.also { it.requireKeys("effect", EFFECT_KEYS) }.let { effectJson -> InertEffectDescription(
        kind = effectKind(effectJson.requiredString("kind")),
        description = effectJson.requiredString("description"),
        provenance = provenancePool[effectJson.requiredString("provenance_receipt_id")] ?: throw missingProvenance(effectJson.requiredString("provenance_receipt_id")),
    ) }

    private fun refusalJson(value: Refusal): JSONObject = JSONObject()
        .put("code", value.code.wireName)
        .put("fix", value.fix)
        .put("current_revision", value.currentRevision ?: JSONObject.NULL)
        .put("original_receipt_id", value.originalReceiptId ?: JSONObject.NULL)

    private fun refusal(value: JSONObject): Refusal = value.also { it.requireKeys("refusal", REFUSAL_KEYS) }.let { refusalJson -> Refusal(
        code = refusalCode(refusalJson.requiredString("code")),
        fix = refusalJson.requiredString("fix"),
        currentRevision = if (refusalJson.isNull("current_revision")) null else refusalJson.requiredLong("current_revision"),
        originalReceiptId = refusalJson.optNullableString("original_receipt_id"),
    ) }

    private fun validateCounters(nextReceiptOrdinal: Long, nextIdempotencyOrdinal: Long, receiptIds: Iterable<String>, idempotencyOrdinals: Iterable<Long>) {
        if (nextReceiptOrdinal <= 0L || nextIdempotencyOrdinal <= 0L) {
            throw HudCausalCodecException("counter_invalid", "HUD causal counters must be positive", "Repair counters above retained receipt and idempotency ordinals")
        }
        val maxReceipt = receiptIds.mapNotNull { receipt -> hudReceipt.matchEntire(receipt)?.groupValues?.get(1)?.toLongOrNull() }.maxOrNull() ?: 0L
        val maxIdempotency = idempotencyOrdinals.maxOrNull() ?: 0L
        if (nextReceiptOrdinal <= maxReceipt) {
            throw HudCausalCodecException("counter_reuse", "nextReceiptOrdinal $nextReceiptOrdinal would reuse retained receipt ordinal $maxReceipt", "Compact or repair the HUD causal envelope with a strictly greater receipt counter")
        }
        if (nextIdempotencyOrdinal <= maxIdempotency) {
            throw HudCausalCodecException("counter_reuse", "nextIdempotencyOrdinal $nextIdempotencyOrdinal would reuse retained idempotency ordinal $maxIdempotency", "Compact or repair the HUD causal envelope with a strictly greater idempotency counter")
        }
    }

    private fun validateNoOrphanProvenance(poolIds: Set<String>, retainedReceiptIds: Iterable<String>) {
        val retained = retainedReceiptIds.toSet()
        val orphan = poolIds.firstOrNull { it !in retained }
        if (orphan != null) {
            throw HudCausalCodecException("orphan_provenance", "HUD causal provenance pool retains orphan receipt '$orphan'", "Repair the envelope so every provenance stamp is referenced by a retained HUD receipt")
        }
    }

    private fun validateCanonicalEncoding(encoded: String, image: PhosphorStoreImage) {
        val canonical = encode(image)
        if (encoded != canonical) {
            throw HudCausalCodecException("noncanonical_envelope", "HUD causal envelope bytes are not the canonical deterministic encoding", "Preserve the envelope and repair by re-encoding the decoded HUD causal image")
        }
    }

    /**
     * Canonical JSON for the private HUD envelope. Object members are sorted by
     * Unicode key order, arrays retain causal order, integers retain their exact
     * decimal form, and strings are escaped here rather than delegated to
     * JSONObject's implementation-defined map iteration or rendering.
     */
    internal fun canonicalJson(value: Any?): String = when {
        value == null || value === JSONObject.NULL -> "null"
        value is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
        ) { key -> "${canonicalJsonString(key)}:${canonicalJson(value.get(key))}" }
        value is JSONArray -> (0 until value.length()).joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ) { index -> canonicalJson(value.get(index)) }
        value is String -> canonicalJsonString(value)
        value is Boolean -> value.toString()
        value is Byte || value is Short || value is Int || value is Long -> value.toString()
        else -> throw HudCausalCodecException(
            "field_invalid",
            "HUD causal canonical JSON contains unsupported value type '${value::class.java.name}'",
            "Preserve the envelope and repair explicitly",
        )
    }

    private fun canonicalJsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (
                    character.code < 0x20 ||
                    character.code in 0xd800..0xdfff ||
                    character == '\u2028' ||
                    character == '\u2029'
                ) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun JSONObject.requiredString(key: String): String {
        if (!has(key) || isNull(key)) throw HudCausalCodecException("field_missing", "HUD causal field '$key' is missing", "Preserve the envelope and repair explicitly")
        val value = get(key)
        if (value !is String) throw HudCausalCodecException("field_invalid", "HUD causal field '$key' is not a string", "Preserve the envelope and repair explicitly")
        if (value.isBlank()) throw HudCausalCodecException("field_missing", "HUD causal field '$key' is missing", "Preserve the envelope and repair explicitly")
        return value
    }
    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else requiredString(key)
    private fun JSONObject.requiredLong(key: String): Long {
        if (!has(key) || isNull(key)) throw HudCausalCodecException("field_missing", "HUD causal field '$key' is missing", "Preserve the envelope and repair explicitly")
        val value = get(key)
        return when (value) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            else -> throw HudCausalCodecException("field_invalid", "HUD causal field '$key' is not a long", "Preserve the envelope and repair explicitly")
        }
    }
    private fun rejectDecimalIntegerLiterals(encoded: String) {
        val match = DECIMAL_INTEGER_FIELD.find(encoded) ?: return
        throw HudCausalCodecException("field_invalid", "HUD causal field '${match.groupValues[1]}' is not an integer literal", "Preserve the envelope and repair explicitly")
    }
    private fun JSONObject.requiredInt(key: String): Int {
        val value = requiredLong(key)
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) throw HudCausalCodecException("field_invalid", "HUD causal field '$key' is not an int", "Preserve the envelope and repair explicitly")
        return value.toInt()
    }
    private fun JSONObject.requiredBoolean(key: String): Boolean {
        if (!has(key) || isNull(key)) throw HudCausalCodecException("field_missing", "HUD causal field '$key' is missing", "Preserve the envelope and repair explicitly")
        val value = get(key)
        if (value !is Boolean) throw HudCausalCodecException("field_invalid", "HUD causal field '$key' is not a boolean", "Preserve the envelope and repair explicitly")
        return value
    }
    private fun JSONObject.requiredObject(key: String): JSONObject {
        if (!has(key) || isNull(key)) throw HudCausalCodecException("field_missing", "HUD causal object '$key' is missing", "Preserve the envelope and repair explicitly")
        return optJSONObject(key) ?: throw HudCausalCodecException("field_invalid", "HUD causal object '$key' is not an object", "Preserve the envelope and repair explicitly")
    }
    private fun JSONObject.requiredArray(key: String, max: Int): JSONArray = (optJSONArray(key) ?: throw HudCausalCodecException("field_missing", "HUD causal array '$key' is missing", "Preserve the envelope and repair explicitly")).also { if (it.length() > max) throw HudCausalCodecException("field_invalid", "HUD causal array '$key' is too large", "Reduce retention through the store") }
    private fun JSONObject.requireKeys(scope: String, expected: Set<String>) {
        val actual = keys().asSequence().toSet()
        if (actual != expected) throw HudCausalCodecException("field_invalid", "HUD causal $scope keys are not canonical", "Preserve the envelope and repair explicitly")
    }
    private fun JSONArray.mapStrings(): List<String> = List(length()) { index ->
        val value = get(index)
        if (value !is String) throw HudCausalCodecException("field_invalid", "HUD causal array item is not a string", "Preserve the envelope and repair explicitly")
        value
    }
    private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = List(length()) { index -> block(getJSONObject(index)) }

    private fun capExceeded(name: String, max: Int): Nothing = throw HudCausalCodecException("field_invalid", "HUD causal $name exceeds $max", "Reduce retention through the store")

    private fun principalKind(wire: String) = PrincipalKind.entries.firstOrNull { it.wireName == wire } ?: throw invalidEnum("principal_kind", wire)
    private fun transport(wire: String) = Transport.entries.firstOrNull { it.wireName == wire } ?: throw invalidEnum("transport", wire)
    private fun auditKind(wire: String) = AuditKind.entries.firstOrNull { it.wireName == wire } ?: throw invalidEnum("audit_kind", wire)
    private fun actionType(wire: String) = ActionType.entries.firstOrNull { it.wireName == wire } ?: throw invalidEnum("action_type", wire)
    private fun stateField(wire: String) = StateField.entries.firstOrNull { it.wireName == wire } ?: throw invalidEnum("state_field", wire)
    private fun effectKind(wire: String) = EffectKind.entries.firstOrNull { it.wireName == wire } ?: throw invalidEnum("effect_kind", wire)
    private fun refusalCode(wire: String) = RefusalCode.entries.firstOrNull { it.wireName == wire } ?: throw invalidEnum("refusal_code", wire)
    private fun invalidEnum(field: String, wire: String): Nothing = throw HudCausalCodecException("field_invalid", "HUD causal enum '$field' has unsupported value '$wire'", "Upgrade Phosphor or repair explicitly")
    private fun missingProvenance(receiptId: String): HudCausalCodecException = HudCausalCodecException("provenance_missing", "HUD causal record references missing provenance '$receiptId'", "Preserve the envelope and repair through the store")
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val PAYLOAD_KEYS = setOf("revision", "sequence", "wall_time_millis", "distribution", "build_profile", "display_hud", "snapshot_provenance_receipt_id", "next_receipt_ordinal", "next_idempotency_ordinal", "authority_plane", "provenance_pool", "audit", "idempotency")
    private val LEGACY_PAYLOAD_KEYS = PAYLOAD_KEYS - "authority_plane"
    private val DECIMAL_INTEGER_FIELD = Regex("\\\"(revision|sequence|wall_time_millis|next_receipt_ordinal|next_idempotency_ordinal|monotonic_millis|policy_maximum_records|policy_maximum_age_millis|observed_wall_time_millis|policy_minimum_ttl_millis|accepted_wall_time_millis|expires_wall_time_millis|ordinal|current_revision)\\\"\\s*:\\s*-?\\d+(?:\\.\\d+|[eE][+-]?\\d+)")
    private val PROVENANCE_KEYS = setOf("receipt_id", "principal_kind", "principal_id", "reason", "transport", "session_id", "monotonic_millis", "wall_time_millis")
    private val AUDIT_KEYS = setOf("policy_maximum_records", "policy_maximum_age_millis", "observed_wall_time_millis", "records")
    private val AUDIT_RECORD_KEYS = setOf("receipt_id", "kind", "wall_time_millis", "action_type", "fields", "provenance_receipt_id", "refusal")
    private val IDEMPOTENCY_KEYS = setOf("policy_minimum_ttl_millis", "policy_maximum_records", "records")
    private val IDEMPOTENCY_RECORD_KEYS = setOf("principal_kind", "principal_id", "key", "mode", "acknowledgement", "accepted_wall_time_millis", "expires_wall_time_millis", "ordinal")
    private val ACKNOWLEDGEMENT_KEYS = setOf("receipt_id", "action_type", "changed", "revision", "sequence", "effective_value", "provenance_receipt_id", "delta", "effects")
    private val DELTA_KEYS = setOf("revision", "sequence", "provenance_receipt_id", "changes")
    private val CHANGE_KEYS = setOf("field", "old", "new", "provenance_receipt_id")
    private val EFFECT_KEYS = setOf("kind", "description", "provenance_receipt_id")
    private val REFUSAL_KEYS = setOf("code", "fix", "current_revision", "original_receipt_id")
    private val HUD_AUDIT_KINDS = setOf(AuditKind.ACTION, AuditKind.NO_OP, AuditKind.IDEMPOTENT_REPLAY, AuditKind.IDEMPOTENCY_CONFLICT, AuditKind.REFUSAL)
}

class HudCausalCodecException(
    val error: String,
    override val message: String,
    val fix: String,
) : IllegalArgumentException(message)
