package dev.phosphor.mobil3.store

import dev.phosphor.mobil3.state.AcceptedActionRecord
import dev.phosphor.mobil3.state.ActionAcknowledgement
import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.AuditKind
import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.IdempotencyRecord
import dev.phosphor.mobil3.state.PhosphorAction
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.Transport

sealed interface PhosphorDispatchResult {
    data class Accepted(val acknowledgement: ActionAcknowledgement, val acceptedRecord: AcceptedActionRecord) : PhosphorDispatchResult
    data class Replayed(val acknowledgement: ActionAcknowledgement) : PhosphorDispatchResult
    data class Refused(val refusal: Refusal) : PhosphorDispatchResult
    data class Failed(val refusal: Refusal) : PhosphorDispatchResult
}

fun interface PhosphorStateListener {
    fun onState(snapshot: PhosphorStateSnapshot)
}

fun interface PhosphorStoreHealthListener {
    fun onHealth(health: StoreHealth)
}

data class PhosphorStoreObservation(
    val snapshot: PhosphorStateSnapshot,
    val auditRecords: FrozenList<AuditRecord>,
    val health: StoreHealth,
)

const val LOCAL_HUMAN_PRINCIPAL_ID: String = "local-human"
const val LOCAL_HUD_MIGRATION_PRINCIPAL_ID: String = "local-hud-preferences"

internal val NEXUS_LIFECYCLE_AUDIT_KINDS: Set<AuditKind> = setOf(
    AuditKind.AUTHENTICATION,
    AuditKind.HEARTBEAT_EXPIRY,
    AuditKind.BINDER_DEATH,
    AuditKind.GRANT,
    AuditKind.REVOKE,
)

class PhosphorStateStore(
    private val port: PhosphorStatePersistencePort,
    private val initialSnapshot: PhosphorStateSnapshot,
    private val reducer: DisplayHudReducer = DisplayHudReducer(),
    initialWallTimeMillis: Long = 0L,
) {
    private val listeners = linkedSetOf<PhosphorStateListener>()
    private val healthListeners = linkedSetOf<PhosphorStoreHealthListener>()
    private val pendingStateNotifications = ArrayDeque<StateNotification>()
    private val pendingHealthNotifications = ArrayDeque<HealthNotification>()
    private var deliveringStateNotifications = false
    private var deliveringHealthNotifications = false
    @Volatile
    private var runtimeHealth: StoreHealth = StoreHealth()
    @Volatile
    private var image: PhosphorStoreImage = restore(
        port.load(),
        initialWallTimeMillis.also { require(it >= 0L) { "initial wall time must not be negative" } },
    )

    val snapshot: PhosphorStateSnapshot get() = image.snapshot
    val health: StoreHealth get() = runtimeHealth
    val auditRecords: List<AuditRecord> get() = image.audit.records.toList()
    val idempotencyRecords: List<IdempotencyRecord> get() = image.idempotency.records.toList()
    internal val authorityPlane: String? get() = image.authorityPlane

    internal fun commitAuthorityPlane(
        update: (PhosphorStoreImage) -> PhosphorStoreImage,
        persisted: (PhosphorStoreImage) -> PhosphorStoreImage = { it },
        lifecycleAuditKind: AuditKind? = null,
        wallTimeMillis: Long? = null,
    ): Refusal? {
        val result = synchronized(this) {
            runtimeHealth.fix?.let { return@synchronized it }
            val updatedImage = update(image)
            if (updatedImage == image) return@synchronized null
            val nextImage = if (lifecycleAuditKind != null && updatedImage != image) {
                withLifecycleAuditLocked(updatedImage, lifecycleAuditKind, wallTimeMillis) ?: return@synchronized counterOverflow()
            } else {
                updatedImage
            }
            when (val saved = port.save(persisted(nextImage))) {
            PhosphorStorePersistenceResult.Saved -> {
                val changed = image.snapshot != nextImage.snapshot
                image = nextImage
                if (changed) {
                    val listeners = listeners.toList()
                    if (listeners.isNotEmpty()) pendingStateNotifications.addLast(StateNotification(nextImage.snapshot, listeners))
                }
                null
            }
            is PhosphorStorePersistenceResult.Failed -> {
                publishHealth(StoreHealth(readable = true, writable = false, fix = saved.refusal))
                saved.refusal
            }
            }
        }
        drainHealthNotifications()
        drainStateNotifications()
        return result
    }

    private fun withLifecycleAuditLocked(
        current: PhosphorStoreImage,
        kind: AuditKind?,
        wallTimeMillis: Long?,
    ): PhosphorStoreImage? {
        if (kind == null) return current
        require(kind in NEXUS_LIFECYCLE_AUDIT_KINDS) { "unsupported Nexus lifecycle audit kind" }
        val observedWallTimeMillis = maxOf(wallTimeMillis ?: current.snapshot.wallTimeMillis, current.snapshot.wallTimeMillis, current.audit.observedWallTimeMillis)
        val receiptOrdinal = nextOrdinalOrFailure(current.nextReceiptOrdinal) ?: return null
        val nextReceipt = checkedAdd(receiptOrdinal, 1L) ?: return null
        val record = AuditRecord(
            receiptId = "hud-$receiptOrdinal",
            kind = kind,
            wallTimeMillis = observedWallTimeMillis,
        )
        return current.copy(
            audit = reducer.appendAudit(current.audit.records, record, observedWallTimeMillis),
            nextReceiptOrdinal = nextReceipt,
        )
    }

    fun observe(): PhosphorStoreObservation = synchronized(this) {
        PhosphorStoreObservation(
            snapshot = image.snapshot,
            auditRecords = FrozenList.copyOf(image.audit.records),
            health = runtimeHealth,
        )
    }

    fun addListener(listener: PhosphorStateListener): PhosphorStateSnapshot = synchronized(this) {
        listeners += listener
        image.snapshot
    }

    fun removeListener(listener: PhosphorStateListener) = synchronized(this) {
        listeners.remove(listener)
    }

    fun addHealthListener(listener: PhosphorStoreHealthListener): StoreHealth = synchronized(this) {
        healthListeners += listener
        runtimeHealth
    }

    fun removeHealthListener(listener: PhosphorStoreHealthListener) = synchronized(this) {
        healthListeners.remove(listener)
    }

    fun dispatch(
        action: PhosphorAction,
        request: ActionRequest,
        monotonicMillis: Long,
        wallTimeMillis: Long,
    ): PhosphorDispatchResult = dispatchInternal(
        action = action,
        request = request,
        monotonicMillis = monotonicMillis,
        wallTimeMillis = wallTimeMillis,
        authorizationFence = null,
    )

    internal fun dispatchAuthorized(
        action: PhosphorAction,
        request: ActionRequest,
        monotonicMillis: Long,
        wallTimeMillis: Long,
        authorizationFence: CommitAuthorizationFence,
    ): PhosphorDispatchResult = dispatchInternal(
        action = action,
        request = request,
        monotonicMillis = monotonicMillis,
        wallTimeMillis = wallTimeMillis,
        authorizationFence = authorizationFence,
    )

    private fun dispatchInternal(
        action: PhosphorAction,
        request: ActionRequest,
        monotonicMillis: Long,
        wallTimeMillis: Long,
        authorizationFence: CommitAuthorizationFence?,
    ): PhosphorDispatchResult {
        val result = synchronized(this) {
            dispatchLocked(action, request, monotonicMillis, wallTimeMillis, authorizationFence)
        }
        drainHealthNotifications()
        drainStateNotifications()
        return result
    }

    private fun dispatchLocked(
        action: PhosphorAction,
        request: ActionRequest,
        monotonicMillis: Long,
        wallTimeMillis: Long,
        authorizationFence: CommitAuthorizationFence?,
    ): PhosphorDispatchResult {
        runtimeHealth.fix?.let { return PhosphorDispatchResult.Failed(it) }
        val earliestWallTime = maxOf(image.snapshot.wallTimeMillis, image.audit.observedWallTimeMillis)
        if (monotonicMillis < 0L || wallTimeMillis < earliestWallTime) {
            return PhosphorDispatchResult.Failed(
                Refusal(
                    RefusalCode.INVALID_REQUEST,
                    "Retry with a non-negative monotonic clock and a wall clock at or after $earliestWallTime.",
                    image.snapshot.revision,
                ),
            )
        }
        val receiptOrdinal = nextOrdinalOrFailure(image.nextReceiptOrdinal) ?: return PhosphorDispatchResult.Failed(counterOverflow())
        // Replays and refusals do not reserve a new idempotency record. Pass the
        // current positive ordinal through the pure reducer and fail closed only
        // if an accepted action actually needs to advance the exhausted counter.
        val idempotencyOrdinal = image.nextIdempotencyOrdinal
        val receiptId = "hud-$receiptOrdinal"
        val authorization = authorizationFence?.let { fence ->
            runCatching {
                fence.authorize(action, request, image.snapshot, monotonicMillis, wallTimeMillis)
            }.getOrElse {
                CommitAuthorizationDecision.Refused(
                    Refusal(
                        RefusalCode.AUTHORITY_UNAVAILABLE,
                        "Re-establish the authenticated session and retry through the shared Nexus dispatcher.",
                        image.snapshot.revision,
                    ),
                )
            }
        }
        val authorized = when (authorization) {
            null -> null
            is CommitAuthorizationDecision.Authorized -> authorization.takeIf {
                it.matches(action, request, image.snapshot)
            }
            is CommitAuthorizationDecision.Refused -> null
        }
        val authorizationRefusal = when {
            authorization is CommitAuthorizationDecision.Refused -> authorization.refusal
            authorization is CommitAuthorizationDecision.Authorized && authorized == null -> Refusal(
                RefusalCode.INVALID_REQUEST,
                "Retry through the current shared Nexus dispatcher; the commit authorization did not match this exact action and snapshot.",
                image.snapshot.revision,
            )
            else -> null
        }
        return when (val reduction = reducer.reduce(
            action,
            request,
            image,
            monotonicMillis,
            wallTimeMillis,
            receiptId,
            idempotencyOrdinal,
            authorized,
            authorizationRefusal,
        )) {
            is DisplayHudReduction.Accepted -> commitAccepted(reduction, wallTimeMillis, receiptOrdinal, idempotencyOrdinal)
            is DisplayHudReduction.Replayed -> commitAuditOnly(reduction.audit, wallTimeMillis, receiptOrdinal, replay = reduction.acknowledgement)
            is DisplayHudReduction.Refused -> commitAuditOnly(reduction.audit, wallTimeMillis, receiptOrdinal, refusal = reduction.refusal)
        }
    }

    private fun commitAccepted(
        reduction: DisplayHudReduction.Accepted,
        wallTimeMillis: Long,
        receiptOrdinal: Long,
        idempotencyOrdinal: Long,
    ): PhosphorDispatchResult {
        val nextReceipt = checkedAdd(receiptOrdinal, 1L) ?: return PhosphorDispatchResult.Failed(counterOverflow())
        val nextIdempotency = checkedAdd(idempotencyOrdinal, 1L) ?: return PhosphorDispatchResult.Failed(counterOverflow())
        val nextImage = image.copy(
            snapshot = reduction.snapshot,
            audit = reducer.appendAudit(image.audit.records, reduction.audit, wallTimeMillis),
            idempotency = reducer.appendIdempotency(image.idempotency.records, reduction.idempotencyRecord, wallTimeMillis),
            nextReceiptOrdinal = nextReceipt,
            nextIdempotencyOrdinal = nextIdempotency,
        )
        return when (val saved = port.save(nextImage)) {
            PhosphorStorePersistenceResult.Saved -> {
                val changed = image.snapshot !== nextImage.snapshot
                image = nextImage
                if (changed) {
                    val listeners = listeners.toList()
                    if (listeners.isNotEmpty()) {
                        pendingStateNotifications.addLast(StateNotification(nextImage.snapshot, listeners))
                    }
                }
                PhosphorDispatchResult.Accepted(reduction.acknowledgement, reduction.acceptedRecord)
            }
            is PhosphorStorePersistenceResult.Failed -> {
                publishHealth(StoreHealth(readable = true, writable = false, fix = saved.refusal))
                PhosphorDispatchResult.Failed(saved.refusal)
            }
        }
    }

    private fun commitAuditOnly(
        audit: AuditRecord,
        wallTimeMillis: Long,
        receiptOrdinal: Long,
        replay: ActionAcknowledgement? = null,
        refusal: Refusal? = null,
    ): PhosphorDispatchResult {
        val nextReceipt = checkedAdd(receiptOrdinal, 1L) ?: return PhosphorDispatchResult.Failed(counterOverflow())
        val nextImage = image.copy(
            audit = reducer.appendAudit(image.audit.records, audit, wallTimeMillis),
            nextReceiptOrdinal = nextReceipt,
        )
        return when (val saved = port.save(nextImage)) {
            PhosphorStorePersistenceResult.Saved -> {
                image = nextImage
                when {
                    replay != null -> PhosphorDispatchResult.Replayed(replay)
                    refusal != null -> PhosphorDispatchResult.Refused(refusal)
                    else -> error("audit-only commit requires replay or refusal")
                }
            }
            is PhosphorStorePersistenceResult.Failed -> {
                publishHealth(StoreHealth(readable = true, writable = false, fix = saved.refusal))
                PhosphorDispatchResult.Failed(saved.refusal)
            }
        }
    }

    private fun restore(load: PhosphorStoreLoadResult, wallTimeMillis: Long): PhosphorStoreImage = when (load) {
        is PhosphorStoreLoadResult.CausalImage -> load.image
        is PhosphorStoreLoadResult.CorruptEnvelope -> {
            publishHealth(StoreHealth(readable = true, writable = false, fix = load.refusal))
            // This is only a safe compiled display projection. It is never persisted or
            // treated as a migrated causal image after corruption.
            PhosphorStoreImage(initialSnapshot)
        }
        is PhosphorStoreLoadResult.LegacyBootstrap -> migrateLegacy(load, wallTimeMillis)
    }

    private fun migrateLegacy(legacy: PhosphorStoreLoadResult.LegacyBootstrap, wallTimeMillis: Long): PhosphorStoreImage {
        val mode = when {
            legacy.hudModeRaw != null -> legacyHudMode(legacy.hudModeRaw)
                ?: return migrationFailure("Repair legacy hud_mode to 0, 1, or 2 before causal HUD bootstrap.")
            legacy.nerdHudRaw == "true" -> "on"
            legacy.nerdHudRaw == "false" -> "off"
            legacy.nerdHudRaw != null -> return migrationFailure(
                "Repair legacy nerd_hud to true or false before causal HUD bootstrap.",
            )
            else -> return persistBaseline()
        }
        val request = ActionRequest(
            principal = PrincipalId(PrincipalKind.MIGRATION, LOCAL_HUD_MIGRATION_PRINCIPAL_ID),
            idempotencyKey = "legacy-display-hud-bootstrap",
            expectedRevision = initialSnapshot.revision,
            reason = "Adopt legacy display HUD preference into causal store.",
            requestedCapability = Capability.CONTROL_DISPLAY,
            transport = Transport.MIGRATION,
        )
        val start = PhosphorStoreImage(initialSnapshot)
        val reduction = reducer.reduce(SetDisplayHud(mode), request, start, 0L, wallTimeMillis, "hud-1", 1L)
        return if (reduction is DisplayHudReduction.Accepted) {
            val migrated = start.copy(
                snapshot = reduction.snapshot,
                audit = reducer.appendAudit(start.audit.records, reduction.audit, wallTimeMillis),
                idempotency = reducer.appendIdempotency(start.idempotency.records, reduction.idempotencyRecord, wallTimeMillis),
                nextReceiptOrdinal = 2L,
                nextIdempotencyOrdinal = 2L,
            )
            when (val saved = port.save(migrated)) {
                PhosphorStorePersistenceResult.Saved -> migrated
                is PhosphorStorePersistenceResult.Failed -> {
                    publishHealth(StoreHealth(readable = true, writable = false, fix = saved.refusal))
                    start
                }
            }
        } else {
            val fix = (reduction as? DisplayHudReduction.Refused)?.refusal ?: Refusal(
                RefusalCode.SYSTEM_UNAVAILABLE,
                "Retry causal HUD bootstrap after repairing the migration request.",
                initialSnapshot.revision,
            )
            publishHealth(StoreHealth(readable = true, writable = false, fix = fix))
            start
        }
    }

    private fun persistBaseline(): PhosphorStoreImage {
        val baseline = PhosphorStoreImage(initialSnapshot)
        return when (val saved = port.save(baseline)) {
            PhosphorStorePersistenceResult.Saved -> baseline
            is PhosphorStorePersistenceResult.Failed -> {
                publishHealth(StoreHealth(readable = true, writable = false, fix = saved.refusal))
                baseline
            }
        }
    }

    private fun legacyHudMode(raw: String): String? = when (raw) {
        "0", "on" -> "on"
        "1", "auto" -> "auto"
        "2", "off" -> "off"
        else -> null
    }

    private fun migrationFailure(fix: String): PhosphorStoreImage {
        publishHealth(StoreHealth(
            readable = true,
            writable = false,
            fix = Refusal(RefusalCode.INVALID_VALUE, fix, initialSnapshot.revision),
        ))
        return PhosphorStoreImage(initialSnapshot)
    }

    private fun publishHealth(next: StoreHealth) = synchronized(this) {
        if (runtimeHealth == next) return@synchronized
        runtimeHealth = next
        val listeners = healthListeners.toList()
        if (listeners.isNotEmpty()) {
            pendingHealthNotifications.addLast(HealthNotification(next, listeners))
        }
    }

    private fun drainHealthNotifications() {
        val ownsDrain = synchronized(this) {
            if (deliveringHealthNotifications || pendingHealthNotifications.isEmpty()) {
                false
            } else {
                deliveringHealthNotifications = true
                true
            }
        }
        if (!ownsDrain) return

        while (true) {
            val delivery = synchronized(this) {
                val notification = pendingHealthNotifications.removeFirstOrNull()
                if (notification == null) {
                    deliveringHealthNotifications = false
                    null
                } else {
                    notification
                }
            } ?: return
            delivery.listeners.forEach { listener ->
                runCatching { listener.onHealth(delivery.health) }
            }
        }
    }

    private fun drainStateNotifications() {
        val ownsDrain = synchronized(this) {
            if (deliveringStateNotifications || pendingStateNotifications.isEmpty()) {
                false
            } else {
                deliveringStateNotifications = true
                true
            }
        }
        if (!ownsDrain) return

        while (true) {
            val delivery = synchronized(this) {
                val notification = pendingStateNotifications.removeFirstOrNull()
                if (notification == null) {
                    deliveringStateNotifications = false
                    null
                } else {
                    notification
                }
            } ?: return
            delivery.listeners.forEach { listener ->
                runCatching { listener.onState(delivery.snapshot) }
            }
        }
    }

    private fun nextOrdinalOrFailure(value: Long): Long? = if (value == Long.MAX_VALUE) null else value

    private fun checkedAdd(left: Long, right: Long): Long? = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

    private fun counterOverflow(): Refusal = Refusal(
        RefusalCode.SYSTEM_UNAVAILABLE,
        "Restart with a compacted causal store image because the local receipt counter is exhausted.",
        image.snapshot.revision,
    )

    private data class HealthNotification(
        val health: StoreHealth,
        val listeners: List<PhosphorStoreHealthListener>,
    )

    private data class StateNotification(
        val snapshot: PhosphorStateSnapshot,
        val listeners: List<PhosphorStateListener>,
    )
}
