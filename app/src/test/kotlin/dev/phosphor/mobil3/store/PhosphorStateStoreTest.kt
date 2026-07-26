package dev.phosphor.mobil3.store

import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenMap
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenSetOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.concurrent.thread
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PhosphorStateStoreTest {
    private val human = PrincipalId(PrincipalKind.HUMAN, "local-human")
    private val migration = PrincipalId(PrincipalKind.MIGRATION, LOCAL_HUD_MIGRATION_PRINCIPAL_ID)

    private fun base(mode: String = "off"): PhosphorStateSnapshot = InitialSnapshots.play(10L).copy(
        desired = InitialSnapshots.play(10L).desired.copy(displayHud = mode, fullscreen = true, sourceKind = "file"),
        effective = InitialSnapshots.play(10L).effective.copy(displayHud = mode, fullscreen = true, sourceKind = "file"),
        capabilities = FrozenMap.copyOf(mapOf(
            human to frozenSetOf(Capability.CONTROL_DISPLAY),
            migration to frozenSetOf(Capability.CONTROL_DISPLAY),
        )),
    )

    private fun request(expected: Long = 0L, key: String = "k") = ActionRequest(
        principal = human,
        idempotencyKey = key,
        expectedRevision = expected,
        reason = "test",
        requestedCapability = Capability.CONTROL_DISPLAY,
        transport = Transport.UI,
    )

    @Test fun persistenceFailureLeavesPublishedStateAuditIdempotencyAndListenersUnchanged() {
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix persistence.", 0L)
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())), failSaves = true, failure = failure)
        val store = PhosphorStateStore(port, base())
        var listenerCalls = 0
        store.addListener { listenerCalls++ }
        val before = store.snapshot
        val result = assertIs<PhosphorDispatchResult.Failed>(store.dispatch(SetDisplayHud("on"), request(), 1L, 20L))
        assertSame(failure, result.refusal)
        assertSame(before, store.snapshot)
        assertEquals(emptyList(), store.auditRecords)
        assertEquals(emptyList(), store.idempotencyRecords)
        assertEquals(0, listenerCalls)
        assertTrue(store.health.readable)
        assertFalse(store.health.writable)
    }

    @Test fun processRestoreUsesInjectedImageAndPreservesNonHudFields() {
        val restored = PhosphorStoreImage(base("on").copy(revision = 7L, sequence = 4L), nextReceiptOrdinal = 9L, nextIdempotencyOrdinal = 5L)
        val store = PhosphorStateStore(MemoryPort(PhosphorStoreLoadResult.CausalImage(restored)), base("off"))
        assertSame(restored.snapshot, store.snapshot)
        assertEquals("on", store.snapshot.effective.displayHud)
        assertEquals(true, store.snapshot.effective.fullscreen)
        assertEquals("file", store.snapshot.effective.sourceKind)
    }

    @Test fun corruptLoadIsReadableReadOnlyHealthAndDispatchFailure() {
        val refusal = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Delete corrupt causal envelope.", 0L)
        val store = PhosphorStateStore(MemoryPort(PhosphorStoreLoadResult.CorruptEnvelope(refusal)), base())
        assertTrue(store.health.readable)
        assertFalse(store.health.writable)
        assertSame(refusal, store.health.fix)
        assertIs<PhosphorDispatchResult.Failed>(store.dispatch(SetDisplayHud("on"), request(), 1L, 20L))
    }

    @Test fun legacyBootstrapMigratesThroughAcceptedMigrationActionAndPersists() {
        val port = MemoryPort(PhosphorStoreLoadResult.LegacyBootstrap(hudModeRaw = null, nerdHudRaw = "true"))
        val store = PhosphorStateStore(port, base("off"), initialWallTimeMillis = 20L)
        assertEquals("on", store.snapshot.effective.displayHud)
        assertEquals(1L, store.snapshot.revision)
        assertEquals(1, port.saved.size)
        assertEquals("on", port.saved.single().snapshot.effective.displayHud)
        assertEquals(PrincipalKind.MIGRATION, store.snapshot.provenance.values.single().writer.kind)
    }

    @Test fun explicitLegacyHudModeUsesIntegerMappingAndWinsOverNerdHud() {
        val port = MemoryPort(PhosphorStoreLoadResult.LegacyBootstrap(hudModeRaw = "1", nerdHudRaw = "true"))
        val store = PhosphorStateStore(port, base("off"), initialWallTimeMillis = 20L)
        assertEquals("auto", store.snapshot.effective.displayHud)
        assertEquals(1L, store.snapshot.revision)
    }

    @Test fun malformedExplicitLegacyHudModeLocksStoreReadOnlyWithoutFallbackOrSave() {
        val port = MemoryPort(PhosphorStoreLoadResult.LegacyBootstrap(hudModeRaw = "9", nerdHudRaw = "true"))
        val store = PhosphorStateStore(port, base("off"), initialWallTimeMillis = 20L)
        assertEquals("off", store.snapshot.effective.displayHud)
        assertFalse(store.health.writable)
        assertEquals(RefusalCode.INVALID_VALUE, store.health.fix?.code)
        assertTrue(port.saved.isEmpty())
    }

    @Test fun persistenceFailurePublishesReadOnlyHealth() {
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix persistence.", 0L)
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())), failSaves = true, failure = failure)
        val store = PhosphorStateStore(port, base())
        var observed: StoreHealth? = null
        store.addHealthListener { observed = it }
        assertIs<PhosphorDispatchResult.Failed>(store.dispatch(SetDisplayHud("on"), request(), 1L, 20L))
        assertSame(store.health, observed)
        assertSame(failure, observed?.fix)
    }

    @Test fun healthFailureCallbacksAreOrderedOutsideMonitorAndExceptionIsolated() {
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix persistence.", 0L)
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())), failSaves = true, failure = failure)
        val store = PhosphorStateStore(port, base())
        val observed = mutableListOf<String>()
        store.addHealthListener { health ->
            assertFalse(Thread.holdsLock(store), "health callback must not run while the store monitor is held")
            observed += "first:${health.fix?.fix}"
            error("health listener failure must be isolated")
        }
        store.addHealthListener { health ->
            assertFalse(Thread.holdsLock(store), "later health callback must not run while the store monitor is held")
            observed += "second:${health.fix?.fix}"
        }

        assertIs<PhosphorDispatchResult.Failed>(store.dispatch(SetDisplayHud("on"), request(), 1L, 20L))

        assertEquals(listOf("first:Fix persistence.", "second:Fix persistence."), observed)
        assertSame(failure, store.health.fix)
    }

    @Test fun healthFailureCallbackAllowsCrossThreadRemoveBeforeSlowCallbackReturns() {
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix persistence.", 0L)
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())), failSaves = true, failure = failure)
        val store = PhosphorStateStore(port, base())
        val callbackEntered = CountDownLatch(1)
        val allowCallbackReturn = CountDownLatch(1)
        val removeCompleted = CountDownLatch(1)
        lateinit var listener: PhosphorStoreHealthListener
        listener = PhosphorStoreHealthListener {
            assertFalse(Thread.holdsLock(store), "slow health callback must not run while the store monitor is held")
            callbackEntered.countDown()
            thread {
                store.removeHealthListener(listener)
                removeCompleted.countDown()
            }
            assertTrue(removeCompleted.await(1, TimeUnit.SECONDS), "cross-thread remove must not wait for the slow callback to return")
            allowCallbackReturn.await(1, TimeUnit.SECONDS)
        }
        store.addHealthListener(listener)

        val dispatchFinished = AtomicBoolean(false)
        val dispatcher = thread {
            assertIs<PhosphorDispatchResult.Failed>(store.dispatch(SetDisplayHud("on"), request(), 1L, 20L))
            dispatchFinished.set(true)
        }

        assertTrue(callbackEntered.await(1, TimeUnit.SECONDS), "health callback should be reached")
        assertFalse(dispatchFinished.get(), "dispatch should wait for the slow callback even though the monitor is free")
        allowCallbackReturn.countDown()
        dispatcher.join(1_000)
        assertFalse(dispatcher.isAlive, "dispatch thread must finish without deadlock")
        assertTrue(dispatchFinished.get())
    }

    @Test fun restartReplaysOriginalReceiptAndContinuesReceiptCounter() {
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())))
        val firstStore = PhosphorStateStore(port, base())
        val originalRequest = request(key = "restart-key")
        val accepted = assertIs<PhosphorDispatchResult.Accepted>(
            firstStore.dispatch(SetDisplayHud("on"), originalRequest, 1L, 20L),
        )
        val persisted = port.saved.single()
        val restartPort = MemoryPort(PhosphorStoreLoadResult.CausalImage(persisted))
        val restarted = PhosphorStateStore(restartPort, base())

        val replay = assertIs<PhosphorDispatchResult.Replayed>(
            restarted.dispatch(SetDisplayHud("on"), originalRequest, 2L, 21L),
        )
        assertEquals(accepted.acknowledgement, replay.acknowledgement)
        assertEquals(1L, restarted.snapshot.revision)

        val next = assertIs<PhosphorDispatchResult.Accepted>(
            restarted.dispatch(SetDisplayHud("off"), request(expected = 1L, key = "next-key"), 3L, 22L),
        )
        assertEquals("hud-3", next.acknowledgement.receiptId)
        assertEquals(2L, restarted.snapshot.revision)
    }

    @Test fun exhaustedIdempotencyCounterStillAllowsReplayAndRefusalButRejectsNewAcceptance() {
        val seedPort = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())))
        val seedStore = PhosphorStateStore(seedPort, base())
        val originalRequest = request(key = "existing")
        val accepted = assertIs<PhosphorDispatchResult.Accepted>(
            seedStore.dispatch(SetDisplayHud("on"), originalRequest, 1L, 20L),
        )
        val exhaustedImage = seedPort.saved.single().copy(nextIdempotencyOrdinal = Long.MAX_VALUE)
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(exhaustedImage))
        val store = PhosphorStateStore(port, base())

        val replay = assertIs<PhosphorDispatchResult.Replayed>(
            store.dispatch(SetDisplayHud("on"), originalRequest, 2L, 21L),
        )
        assertEquals(accepted.acknowledgement, replay.acknowledgement)

        val refusal = assertIs<PhosphorDispatchResult.Refused>(
            store.dispatch(SetDisplayHud("off"), request(expected = 99L, key = "stale"), 3L, 22L),
        )
        assertEquals(RefusalCode.REVISION_CONFLICT, refusal.refusal.code)

        val failed = assertIs<PhosphorDispatchResult.Failed>(
            store.dispatch(SetDisplayHud("off"), request(expected = 1L, key = "new"), 4L, 23L),
        )
        assertEquals(RefusalCode.SYSTEM_UNAVAILABLE, failed.refusal.code)
        assertTrue(failed.refusal.fix.contains("counter is exhausted"))
        assertEquals("on", store.snapshot.effective.displayHud)
    }

    @Test fun refusalIsPersistedWithoutChangingPublishedSnapshot() {
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())))
        val store = PhosphorStateStore(port, base())
        val before = store.snapshot
        val result = assertIs<PhosphorDispatchResult.Refused>(
            store.dispatch(SetDisplayHud("on"), request(expected = 99L), 1L, 20L),
        )
        assertEquals(RefusalCode.REVISION_CONFLICT, result.refusal.code)
        assertSame(before, store.snapshot)
        assertEquals(1, port.saved.size)
        assertEquals(dev.phosphor.mobil3.state.AuditKind.REFUSAL, port.saved.single().audit.records.single().kind)
        assertEquals(result.refusal, port.saved.single().audit.records.single().refusal)
    }

    @Test fun wallClockCannotMoveBehindLatestAuditOnlyEvent() {
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())))
        val store = PhosphorStateStore(port, base())
        assertIs<PhosphorDispatchResult.Refused>(
            store.dispatch(SetDisplayHud("on"), request(expected = 99L), 1L, 30L),
        )
        val result = assertIs<PhosphorDispatchResult.Failed>(
            store.dispatch(SetDisplayHud("on"), request(key = "later"), 2L, 29L),
        )
        assertEquals(RefusalCode.INVALID_REQUEST, result.refusal.code)
        assertTrue(result.refusal.fix.contains("30"))
        assertEquals(1, port.saved.size)
    }

    @Test fun emptyLegacyBootstrapPersistsRevisionZeroOffBaseline() {
        val port = MemoryPort(PhosphorStoreLoadResult.LegacyBootstrap(hudModeRaw = null, nerdHudRaw = null))
        val store = PhosphorStateStore(port, base("off"), initialWallTimeMillis = 20L)
        assertEquals("off", store.snapshot.effective.displayHud)
        assertEquals(0L, store.snapshot.revision)
        assertEquals(1, port.saved.size)
        assertEquals("off", port.saved.single().snapshot.effective.displayHud)
        assertTrue(port.saved.single().snapshot.provenance.isEmpty())
    }

    @Test fun adversarialAuditWatermarkDoesNotThrowAfterNoOpRefusalAndReplay() {
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base("off"))))
        val store = PhosphorStateStore(port, base("off"))
        assertIs<PhosphorDispatchResult.Accepted>(store.dispatch(SetDisplayHud("off"), request(key = "noop"), 1L, 20L))
        assertIs<PhosphorDispatchResult.Refused>(store.dispatch(SetDisplayHud("on"), request(expected = 99L, key = "refusal"), 2L, 21L))
        assertIs<PhosphorDispatchResult.Replayed>(store.dispatch(SetDisplayHud("off"), request(key = "noop"), 3L, 22L))
        assertEquals(listOf("hud-1", "hud-2", "hud-1"), store.auditRecords.map { it.receiptId })
    }

    @Test fun adversarialListenerThrowAndReentrantDispatchCannotFailDurableCommitOrPublishWrongSnapshot() {
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base("off"))))
        val store = PhosphorStateStore(port, base("off"))
        val observed = mutableListOf<String>()
        val secondListenerObserved = mutableListOf<String>()
        store.addListener { snapshot ->
            observed += snapshot.effective.displayHud
            if (snapshot.effective.displayHud == "on") {
                store.dispatch(SetDisplayHud("auto"), request(expected = snapshot.revision, key = "reentrant"), 2L, 21L)
            }
            error("listener failure must be isolated")
        }
        store.addListener { snapshot -> secondListenerObserved += snapshot.effective.displayHud }
        val accepted = assertIs<PhosphorDispatchResult.Accepted>(store.dispatch(SetDisplayHud("on"), request(key = "outer"), 1L, 20L))
        assertTrue(accepted.acknowledgement.changed)
        assertEquals("auto", store.snapshot.effective.displayHud)
        assertEquals(listOf("on", "auto"), observed)
        assertEquals(listOf("on", "auto"), secondListenerObserved)
    }

    @Test fun listenerRegisteredAfterQueuedCommitsDoesNotReceiveSnapshotsOlderThanRegistrationSnapshot() {
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base("off"))))
        val store = PhosphorStateStore(port, base("off"))
        val existingListenerObserved = mutableListOf<String>()
        val registeredDuringDrainObserved = mutableListOf<String>()
        var registrationSnapshot: PhosphorStateSnapshot? = null
        var registeredDuringDrain = false
        store.addListener { snapshot ->
            existingListenerObserved += snapshot.effective.displayHud
            if (snapshot.effective.displayHud == "on") {
                assertIs<PhosphorDispatchResult.Accepted>(
                    store.dispatch(SetDisplayHud("auto"), request(expected = snapshot.revision, key = "s2"), 2L, 21L),
                )
                registrationSnapshot = store.addListener { laterSnapshot ->
                    registeredDuringDrainObserved += laterSnapshot.effective.displayHud
                }
                registeredDuringDrain = true
            }
        }

        assertIs<PhosphorDispatchResult.Accepted>(store.dispatch(SetDisplayHud("on"), request(key = "s1"), 1L, 20L))

        assertTrue(registeredDuringDrain, "test must register the listener before the queued S2 drain runs")
        assertSame(store.snapshot, registrationSnapshot)
        assertEquals(2L, registrationSnapshot?.revision)
        assertEquals("auto", registrationSnapshot?.effective?.displayHud)
        assertEquals(listOf("on", "auto"), existingListenerObserved)
        assertEquals(emptyList(), registeredDuringDrainObserved, "listener registered at S2 must not receive queued S1 or S2")

        assertIs<PhosphorDispatchResult.Accepted>(
            store.dispatch(SetDisplayHud("off"), request(expected = 2L, key = "s3"), 3L, 22L),
        )
        assertEquals(listOf("off"), registeredDuringDrainObserved)
        assertEquals(listOf("on", "auto", "off"), existingListenerObserved)
    }

    @Test fun adversarialConcurrentDispatchSerializesCountersAndState() {
        val port = MemoryPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base("off"))))
        val store = PhosphorStateStore(port, base("off"))
        (0 until 20).map { index ->
            thread {
                store.dispatch(SetDisplayHud("off"), request(expected = 0L, key = "k$index"), index.toLong() + 1L, 20L)
            }
        }.map { it.join() }
        assertEquals(0L, store.snapshot.revision)
        assertEquals((1L..20L).map { "hud-$it" }, store.auditRecords.map { it.receiptId }.sortedBy { it.removePrefix("hud-").toLong() })
        assertEquals((1L..20L).toList(), store.idempotencyRecords.map { it.ordinal }.sorted())
    }

    @Test fun adversarialLegacyBootstrapFailurePreservesUnderlyingPersistenceFixAndCoversMalformedDefaults() {
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Restore writable preferences file.", 0L)
        val absentPort = MemoryPort(PhosphorStoreLoadResult.LegacyBootstrap(hudModeRaw = null, nerdHudRaw = null), failSaves = true, failure = failure)
        val absentStore = PhosphorStateStore(absentPort, base("off"), initialWallTimeMillis = 20L)
        assertSame(failure, absentStore.health.fix)

        val defaultPort = MemoryPort(PhosphorStoreLoadResult.LegacyBootstrap(hudModeRaw = null, nerdHudRaw = "false"), failSaves = true, failure = failure)
        val defaultStore = PhosphorStateStore(defaultPort, base("on"), initialWallTimeMillis = 20L)
        assertSame(failure, defaultStore.health.fix)

        val malformed = PhosphorStateStore(MemoryPort(PhosphorStoreLoadResult.LegacyBootstrap(hudModeRaw = null, nerdHudRaw = "wat")), base("off"), initialWallTimeMillis = 20L)
        assertEquals(RefusalCode.INVALID_VALUE, malformed.health.fix?.code)
        assertTrue(malformed.health.fix!!.fix.contains("nerd_hud"))
    }

    @Test fun adversarialFortressBootstrapUsesFortressSnapshot() {
        val fortress = InitialSnapshots.fortress(30L).copy(
            capabilities = FrozenMap.copyOf(mapOf(
                human to frozenSetOf(Capability.CONTROL_DISPLAY),
                migration to frozenSetOf(Capability.CONTROL_DISPLAY),
            )),
        )
        val port = MemoryPort(PhosphorStoreLoadResult.LegacyBootstrap(hudModeRaw = "0", nerdHudRaw = null))
        val store = PhosphorStateStore(port, fortress, initialWallTimeMillis = 40L)
        assertEquals(fortress.effective.sourceKind, store.snapshot.effective.sourceKind)
        assertEquals("on", store.snapshot.effective.displayHud)
    }

    private class MemoryPort(
        private val loadResult: PhosphorStoreLoadResult,
        private val failSaves: Boolean = false,
        private val failure: Refusal = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix test persistence.", 0L),
    ) : PhosphorStatePersistencePort {
        val saved = mutableListOf<PhosphorStoreImage>()
        override fun load(): PhosphorStoreLoadResult = loadResult
        override fun save(image: PhosphorStoreImage): PhosphorStorePersistenceResult = synchronized(this) { if (failSaves) {
            PhosphorStorePersistenceResult.Failed(failure)
        } else {
            saved += image
            PhosphorStorePersistenceResult.Saved
        } }
    }
}
