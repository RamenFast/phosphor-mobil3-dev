package dev.phosphor.mobil3.store

import dev.phosphor.mobil3.state.AuditIndexSnapshot
import dev.phosphor.mobil3.state.AuditRetentionPolicy
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.IDEMPOTENCY_TTL_MILLIS
import dev.phosphor.mobil3.state.IdempotencyIndexPolicy
import dev.phosphor.mobil3.state.IdempotencyIndexSnapshot
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.Refusal

interface PhosphorStatePersistencePort {
    fun load(): PhosphorStoreLoadResult
    fun save(image: PhosphorStoreImage): PhosphorStorePersistenceResult
}

sealed interface PhosphorStoreLoadResult {
    data class CausalImage(val image: PhosphorStoreImage) : PhosphorStoreLoadResult
    data class LegacyBootstrap(val hudModeRaw: String?, val nerdHudRaw: String?) : PhosphorStoreLoadResult
    data class CorruptEnvelope(val refusal: Refusal) : PhosphorStoreLoadResult
}

sealed interface PhosphorStorePersistenceResult {
    data object Saved : PhosphorStorePersistenceResult
    data class Failed(val refusal: Refusal) : PhosphorStorePersistenceResult
}

data class StoreHealth(
    val readable: Boolean = true,
    val writable: Boolean = true,
    val fix: Refusal? = null,
) {
    init {
        if (readable && writable) require(fix == null) { "ready store must not carry a fix" }
        else requireNotNull(fix) { "degraded store requires a fix-bearing refusal" }
        require(!writable || readable) { "a writable store must also be readable" }
    }
}

data class PhosphorStoreImage(
    val snapshot: PhosphorStateSnapshot,
    val audit: AuditIndexSnapshot = AuditIndexSnapshot(
        AuditRetentionPolicy(maximumRecords = 128, maximumAgeMillis = IDEMPOTENCY_TTL_MILLIS),
        snapshot.wallTimeMillis,
        FrozenList.copyOf(emptyList()),
    ),
    val idempotency: IdempotencyIndexSnapshot = IdempotencyIndexSnapshot(
        IdempotencyIndexPolicy(maximumRecords = 128),
        FrozenList.copyOf(emptyList()),
    ),
    val nextReceiptOrdinal: Long = 1L,
    val nextIdempotencyOrdinal: Long = 1L,
    val authorityPlane: String? = null,
) {
    init {
        require(nextReceiptOrdinal > 0L) { "next receipt ordinal must be positive" }
        require(nextIdempotencyOrdinal > 0L) { "next idempotency ordinal must be positive" }
        require(audit.observedWallTimeMillis >= snapshot.wallTimeMillis) {
            "audit observation time must not precede the published snapshot"
        }
        val latestReceiptOrdinal = (
            audit.records.map { it.receiptId } +
                snapshot.provenance.values.map { it.receiptId } +
                idempotency.records.map { it.acknowledgement.receiptId }
            ).mapNotNull { it.removePrefix("hud-").toLongOrNull() }.maxOrNull() ?: 0L
        require(nextReceiptOrdinal > latestReceiptOrdinal) { "next receipt ordinal must not rewind existing audit receipts" }
        val latestIdempotencyOrdinal = idempotency.records.maxOfOrNull { it.ordinal } ?: 0L
        require(nextIdempotencyOrdinal > latestIdempotencyOrdinal) { "next idempotency ordinal must not rewind existing reservations" }
    }
}
