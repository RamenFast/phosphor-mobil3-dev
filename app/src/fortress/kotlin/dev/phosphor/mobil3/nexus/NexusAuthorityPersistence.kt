package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.AuditKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.store.PhosphorStateStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Fortress-only durable authority plane carried as one encoded string inside PhosphorStoreImage. */
internal sealed interface NexusAuthorityDecodeResult {
    data object Absent : NexusAuthorityDecodeResult
    data class Loaded(val image: NexusAuthorityImage) : NexusAuthorityDecodeResult
    data class Corrupt(val refusal: Refusal) : NexusAuthorityDecodeResult
}

internal data class NexusAuthorityImage(
    val grants: NexusGrantLedger = NexusGrantLedger.of(),
    val revokedTokenIds: Set<NexusTokenId> = emptySet(),
    val consumedIdempotencyKeys: Set<String> = emptySet(),
    val consumedNonces: Set<NexusNonceRecord> = emptySet(),
) {
    init {
        require(consumedIdempotencyKeys.all { it.isNotBlank() }) { "idempotency keys must be nonblank" }
        require(consumedNonces.size <= MAX_AUTHORITY_NONCES) { "consumed nonce ledger exceeds cap" }
    }

    fun withoutTransientGrants(): NexusAuthorityImage = copy(
        grants = NexusGrantLedger.of(grants.grants.filter { it.scope == NexusGrantScope.PERSISTENT }),
    )
}

internal const val MAX_AUTHORITY_NONCES = 256
internal const val NEXUS_NONCE_RETENTION_MILLIS = 24L * 60L * 60L * 1000L

internal data class NexusNonceRecord(
    val value: String,
    val issuedWallTimeMillis: Long,
    val expiresWallTimeMillis: Long,
    val binding: String = value,
) {
    init {
        value.requireNexusToken("consumed nonce")
        binding.requireNexusToken("consumed nonce binding", maxLength = 1024)
        require(issuedWallTimeMillis >= 0L) { "nonce issue wall time must not be negative" }
        require(expiresWallTimeMillis > issuedWallTimeMillis) { "nonce expiry must follow issue" }
        require(expiresWallTimeMillis - issuedWallTimeMillis <= NEXUS_NONCE_RETENTION_MILLIS) { "nonce retention must not exceed 24 hours" }
    }
}

internal data class NexusNonceConsumption(
    val value: String,
    val binding: String,
    val issuedWallTimeMillis: Long,
    val expiresWallTimeMillis: Long,
) {
    fun record(): NexusNonceRecord = NexusNonceRecord(value, issuedWallTimeMillis, expiresWallTimeMillis, binding)
}

internal sealed interface NexusAuthorityTransactionResult {
    data class Committed(val image: NexusAuthorityImage) : NexusAuthorityTransactionResult
    data class Refused(val refusal: Refusal) : NexusAuthorityTransactionResult
}

internal object NexusAuthorityStoreTransaction {
    fun grantPersistent(
        store: PhosphorStateStore,
        grant: NexusCapabilityGrant,
        wallTimeMillis: Long,
    ): NexusAuthorityTransactionResult {
        if (grant.scope != NexusGrantScope.PERSISTENT) return NexusAuthorityTransactionResult.Refused(
            Refusal(RefusalCode.INVALID_REQUEST, "Persistent authority grants must not be scoped to a session."),
        )
        var committed: NexusAuthorityImage? = null
        var refusal: Refusal? = null
        val failure = store.commitAuthorityPlane(
            update = { storeImage ->
                val current = when (val decoded = NexusAuthorityCodec.decode(storeImage.authorityPlane)) {
                    NexusAuthorityDecodeResult.Absent -> NexusAuthorityImage()
                    is NexusAuthorityDecodeResult.Corrupt -> return@commitAuthorityPlane storeImage.also { refusal = decoded.refusal }
                    is NexusAuthorityDecodeResult.Loaded -> decoded.image.withoutTransientGrants()
                }
                val next = current.copy(grants = NexusGrantLedger.of(current.grants.grants + grant))
                committed = next
                storeImage.copy(authorityPlane = NexusAuthorityCodec.encode(next))
            },
            lifecycleAuditKind = AuditKind.GRANT,
            wallTimeMillis = wallTimeMillis,
        )
        failure?.let { return NexusAuthorityTransactionResult.Refused(it) }
        refusal?.let { return NexusAuthorityTransactionResult.Refused(it) }
        return committed?.let { NexusAuthorityTransactionResult.Committed(it) } ?: NexusAuthorityTransactionResult.Refused(nexusAuthorityPersistenceFailure())
    }

    fun revokePersistent(
        store: PhosphorStateStore,
        trustKey: NexusTrustKey,
        capability: Capability,
        wallTimeMillis: Long,
    ): NexusAuthorityTransactionResult {
        var committed: NexusAuthorityImage? = null
        var refusal: Refusal? = null
        val failure = store.commitAuthorityPlane(
            update = { storeImage ->
                val current = when (val decoded = NexusAuthorityCodec.decode(storeImage.authorityPlane)) {
                    NexusAuthorityDecodeResult.Absent -> NexusAuthorityImage()
                    is NexusAuthorityDecodeResult.Corrupt -> return@commitAuthorityPlane storeImage.also { refusal = decoded.refusal }
                    is NexusAuthorityDecodeResult.Loaded -> decoded.image.withoutTransientGrants()
                }
                val next = current.copy(grants = current.grants.revoke(trustKey, capability))
                committed = next
                storeImage.copy(authorityPlane = NexusAuthorityCodec.encode(next))
            },
            lifecycleAuditKind = AuditKind.REVOKE,
            wallTimeMillis = wallTimeMillis,
        )
        failure?.let { return NexusAuthorityTransactionResult.Refused(it) }
        refusal?.let { return NexusAuthorityTransactionResult.Refused(it) }
        return committed?.let { NexusAuthorityTransactionResult.Committed(it) } ?: NexusAuthorityTransactionResult.Refused(nexusAuthorityPersistenceFailure())
    }

    fun consumeNonce(
        store: PhosphorStateStore,
        consumption: NexusNonceConsumption,
        nowWallTimeMillis: Long,
    ): NexusAuthorityTransactionResult {
        var committed: NexusAuthorityImage? = null
        var refusal: Refusal? = null
        val failure = store.commitAuthorityPlane(update = { storeImage ->
            val current = when (val decoded = NexusAuthorityCodec.decode(storeImage.authorityPlane)) {
                NexusAuthorityDecodeResult.Absent -> NexusAuthorityImage()
                is NexusAuthorityDecodeResult.Corrupt -> return@commitAuthorityPlane storeImage.also {
                    refusal = decoded.refusal
                    committed = null
                }
                is NexusAuthorityDecodeResult.Loaded -> decoded.image
            }
            val liveNonces = current.consumedNonces
                .filter { it.expiresWallTimeMillis > nowWallTimeMillis }
                .toSet()
            if (liveNonces.any { it.value == consumption.value || it.binding == consumption.binding }) {
                committed = null
                return@commitAuthorityPlane storeImage
            }
            val next = current.copy(
                consumedNonces = NexusAuthorityCodec.evictNonceRecords(liveNonces + consumption.record()),
            )
            committed = next
            storeImage.copy(authorityPlane = NexusAuthorityCodec.encode(next))
        })
        failure?.let { return NexusAuthorityTransactionResult.Refused(it) }
        refusal?.let { return NexusAuthorityTransactionResult.Refused(it) }
        return committed?.let { NexusAuthorityTransactionResult.Committed(it) } ?: NexusAuthorityTransactionResult.Refused(
            Refusal(RefusalCode.IDEMPOTENCY_CONFLICT, "Request a fresh nonce-bound Nexus challenge and retry once."),
        )
    }
}

internal object NexusAuthorityCodec {
    const val SCHEMA = "phosphor.nexus.authority/1"
    private const val MAX_BYTES = 128 * 1024
    private val checksumPattern = Regex("[0-9a-f]{64}")

    fun encode(image: NexusAuthorityImage): String {
        val durable = image.withoutTransientGrants()
        val payload = JSONObject()
            .put("grants", JSONArray().also { grants ->
                durable.grants.grants.sortedWith(compareBy<NexusCapabilityGrant> { it.trustKey.kind.wireName }.thenBy { it.trustKey.subject }.thenBy { it.capability.wireName }.thenBy { it.receiptId })
                    .forEach { grants.put(grantJson(it)) }
            })
            .put("revoked_token_ids", JSONArray().also { ids -> durable.revokedTokenIds.map { it.value }.sorted().forEach(ids::put) })
            .put("consumed_idempotency_keys", stringArray(durable.consumedIdempotencyKeys))
            .put("consumed_nonces", JSONArray().also { array -> durable.consumedNonces.sortedBy { it.value }.forEach { array.put(nonceJson(it)) } })
        val checksum = sha256(canonicalJson(payload))
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("payload", payload)
            .put("checksum_sha256", checksum)
        return canonicalJson(root).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Nexus authority envelope exceeds $MAX_BYTES bytes" }
        }
    }

    fun decode(encoded: String?): NexusAuthorityDecodeResult {
        if (encoded == null) return NexusAuthorityDecodeResult.Absent
        return try {
            if (encoded.toByteArray(Charsets.UTF_8).size > MAX_BYTES) error("too_large")
            val root = JSONObject(encoded)
            root.requireKeys(setOf("schema", "payload", "checksum_sha256"), "root")
            if (root.getString("schema") != SCHEMA) error("schema")
            val payload = root.getJSONObject("payload")
            payload.requireKeys(setOf("grants", "revoked_token_ids", "consumed_idempotency_keys", "consumed_nonces"), "payload")
            val checksum = root.getString("checksum_sha256")
            if (!checksumPattern.matches(checksum) || checksum != sha256(canonicalJson(payload))) error("checksum")
            NexusAuthorityDecodeResult.Loaded(
                NexusAuthorityImage(
                    grants = NexusGrantLedger.of(payload.getJSONArray("grants").objects().map(::grant)),
                    revokedTokenIds = payload.getJSONArray("revoked_token_ids").strings().map(::NexusTokenId).toSet(),
                    consumedIdempotencyKeys = payload.getJSONArray("consumed_idempotency_keys").strings().toSet(),
                    consumedNonces = evictNonceRecords(payload.getJSONArray("consumed_nonces").objects().map(::nonce)),
                ),
            )
        } catch (_: Exception) {
            NexusAuthorityDecodeResult.Corrupt(
                Refusal(
                    RefusalCode.INVALID_REQUEST,
                    "Preserve the corrupt Nexus authority envelope and require explicit repair before accepting Nexus authority.",
                ),
            )
        }
    }

    private fun grantJson(grant: NexusCapabilityGrant): JSONObject = JSONObject()
        .put("trust", trustJson(grant.trustKey))
        .put("capability", grant.capability.wireName)
        .put("scope", grant.scope.wireName)
        .put("session_id", grant.sessionId?.value ?: JSONObject.NULL)
        .put("receipt_id", grant.receiptId)

    private fun grant(json: JSONObject): NexusCapabilityGrant {
        json.requireKeys(setOf("trust", "capability", "scope", "session_id", "receipt_id"), "grant")
        return NexusCapabilityGrant(
            trustKey = trust(json.getJSONObject("trust")),
            capability = Capability.entries.single { it.wireName == json.getString("capability") },
            scope = NexusGrantScope.entries.single { it.wireName == json.getString("scope") },
            sessionId = if (json.isNull("session_id")) null else NexusSessionId(json.getString("session_id")),
            receiptId = json.getString("receipt_id"),
        )
    }

    private fun nonceJson(record: NexusNonceRecord): JSONObject = JSONObject()
        .put("value", record.value)
        .put("binding", record.binding)
        .put("issued_wall_time_millis", record.issuedWallTimeMillis)
        .put("expires_wall_time_millis", record.expiresWallTimeMillis)

    private fun nonce(json: JSONObject): NexusNonceRecord {
        json.requireKeys(setOf("value", "binding", "issued_wall_time_millis", "expires_wall_time_millis"), "nonce")
        return NexusNonceRecord(json.getString("value"), json.getLong("issued_wall_time_millis"), json.getLong("expires_wall_time_millis"), json.getString("binding"))
    }

    fun evictNonceRecords(records: Collection<NexusNonceRecord>): Set<NexusNonceRecord> = records
        .sortedWith(compareByDescending<NexusNonceRecord> { it.expiresWallTimeMillis }.thenBy { it.value })
        .take(MAX_AUTHORITY_NONCES)
        .toSet()

    private fun trustJson(key: NexusTrustKey): JSONObject = JSONObject()
        .put("kind", key.kind.wireName)
        .put("subject", key.subject)
        .put("principal_stable_id", key.principalStableId)
        .put("current_certificate", key.currentCertificate?.value ?: JSONObject.NULL)
        .put("signing_history", JSONArray().also { array -> key.signingHistory.map { it.value }.forEach(array::put) })
        .put("pinned_endpoint_identity", key.pinnedEndpointIdentity?.value ?: JSONObject.NULL)
        .put("profile", key.profile.wireName)
        .put("protocol", key.protocol)

    private fun trust(json: JSONObject): NexusTrustKey {
        json.requireKeys(setOf("kind", "subject", "principal_stable_id", "current_certificate", "signing_history", "pinned_endpoint_identity", "profile", "protocol"), "trust")
        val profile = NexusBuildProfile.entries.single { it.wireName == json.getString("profile") }
        val protocol = json.getString("protocol")
        return when (NexusTrustKind.entries.single { it.wireName == json.getString("kind") }) {
            NexusTrustKind.ANDROID_PACKAGE -> NexusTrustKey.android(
                packageName = json.getString("subject"),
                signing = AndroidSigningEvidence.of(
                    CertificateSha256(json.getString("current_certificate")),
                    json.getJSONArray("signing_history").strings().map(::CertificateSha256),
                ),
                principalStableId = json.getString("principal_stable_id"),
                profile = profile,
                protocol = protocol,
            )
            NexusTrustKind.TAILNET_NODE -> NexusTrustKey.tailnet(
                nodePrincipal = json.getString("subject"),
                pinnedEndpointIdentity = PinnedEndpointIdentity(json.getString("pinned_endpoint_identity")),
                principalStableId = json.getString("principal_stable_id"),
                profile = profile,
                protocol = protocol,
            )
        }
    }

    internal fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { key -> JSONObject.quote(key) + ":" + canonicalJson(value.get(key)) }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonicalJson(value.get(it)) }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> error("unsupported json value")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun stringArray(values: Set<String>): JSONArray = JSONArray().also { array -> values.sorted().forEach(array::put) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.requireKeys(keys: Set<String>, label: String) {
        val actual = this.keys().asSequence().toSet()
        require(actual == keys) { "$label has unexpected keys" }
    }
}

internal fun nexusAuthorityPersistenceFailure(revision: Long? = null): Refusal = Refusal(
    code = RefusalCode.SYSTEM_UNAVAILABLE,
    fix = "Retry after Fortress authority storage is available; Nexus authority mutation was not committed.",
    currentRevision = revision,
)
