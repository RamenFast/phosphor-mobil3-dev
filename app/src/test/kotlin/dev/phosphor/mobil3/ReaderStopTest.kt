package dev.phosphor.mobil3

import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderStopTest {
    @Test
    fun idleCaptureCleanupPreservesTheRingAndStillWaitsForDestruction() {
        var ringActive = true
        val completion = SourceStopCompletion()
        val error = ReaderStop.finish(null, {}, {}, {
            cleanupOwnedSource(owned = false) { ringActive = false }
        })
        completion.cleanupFinished(error)
        assertTrue(ringActive)
        assertFalse(completion.result.isDone)
        completion.ownerDestroyed()
        assertNull(completion.result.get(1, TimeUnit.SECONDS))
        assertTrue(ringActive)
    }

    @Test
    fun ownedCaptureCleanupDisablesItsRingBeforeSuccess() {
        var ringActive = true
        val error = ReaderStop.finish(null, {}, {}, {
            cleanupOwnedSource(owned = true) { ringActive = false }
        })
        assertNull(error)
        assertFalse(ringActive)
    }

    @Test
    fun idleRemoteCleanupPreservesPublishedNativeOwnership() {
        val survival = LocalSourceSurvival()
        survival.published()
        var disconnects = 0
        cleanupOwnedSource(owned = false) {
            survival.nativeReplacing()
            disconnects++
        }
        assertEquals(0, disconnects)
        assertFalse(survival.loss().native)
    }

    @Test
    fun ownedRemoteCleanupRecordsLossBeforeDisconnecting() {
        val survival = LocalSourceSurvival()
        var disconnects = 0
        cleanupOwnedSource(owned = true) {
            survival.nativeReplacing()
            assertTrue(survival.loss().native)
            disconnects++
        }
        assertEquals(1, disconnects)
        assertTrue(survival.loss().native)
    }

    @Test
    fun successRequiresJoinedReaderAndCompletedCleanup() {
        val unblock = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val reader = Thread { entered.countDown(); unblock.await() }.apply { start() }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        val steps = mutableListOf<String>()
        val error = ReaderStop.finish(reader,
            stop = { steps += "stop"; unblock.countDown() },
            release = { assertFalse(reader.isAlive); steps += "release" },
            cleanup = { assertFalse(reader.isAlive); steps += "ring cleanup" },
        )
        assertNull(error)
        assertEquals(listOf("stop", "release", "ring cleanup"), steps)
        assertFalse(reader.isAlive)
    }

    @Test
    fun clearingRunningWithoutJoiningCannotAcknowledgeSuccess() {
        val running = AtomicBoolean(true)
        val unblock = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val reader = Thread { entered.countDown(); unblock.await() }.apply { start() }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        try {
            assertNotNull(ReaderStop.finish(reader, { running.set(false) }, {}, {}, timeoutMs = 1))
            assertFalse(running.get())
            assertTrue(reader.isAlive)
        } finally {
            unblock.countDown()
            reader.join(1_000)
        }
    }

    @Test
    fun explicitRetrySucceedsOnlyAfterReaderActuallyFinishes() {
        val unblock = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val reader = Thread { entered.countDown(); unblock.await() }.apply { start() }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        try {
            assertNotNull(ReaderStop.finish(reader, {}, {}, {}, timeoutMs = 1))
            unblock.countDown()
            assertNull(ReaderStop.finish(reader, {}, {}, {}, timeoutMs = 1_000))
            assertFalse(reader.isAlive)
        } finally {
            unblock.countDown()
            reader.join(1_000)
        }
    }

    @Test
    fun cleanupFailureIsNotSuccessAndReleaseStillRuns() {
        val released = AtomicBoolean(false)
        val error = ReaderStop.finish(null, {}, { released.set(true) }, { error("ring cleanup failed") })
        assertTrue(released.get())
        assertEquals("ring cleanup failed", error)
    }

    @Test
    fun stopFailureDoesNotSkipResourceAndRingCleanup() {
        val steps = mutableListOf<String>()
        val error = ReaderStop.finish(null,
            stop = { error("stop failed") },
            release = { steps += "release" },
            cleanup = { steps += "cleanup" },
        )
        assertNotNull(error)
        assertEquals(listOf("release", "cleanup"), steps)
    }

    @Test
    fun staleAcknowledgementCannotCompleteAnotherRequest() {
        val request = SourceStopRequest(12)
        request.complete(11, null)
        assertTrue(request.await(1) { true }!!.contains("timed out"))
        request.complete(12, null)
        assertNull(request.await(1) { true })
    }

    @Test
    fun requestSpecificFailureIsPreserved() {
        val request = SourceStopRequest(12)
        request.complete(12, "reader did not finish")
        assertEquals("reader did not finish", request.await(1) { true })
    }

    @Test
    fun successfulAckIsRejectedAfterLatestSlotSupersedesIt() {
        val tasks = ArrayDeque<() -> Unit>()
        val consumed = mutableListOf<String>()
        lateinit var slot: LatestRequestSlot<String>
        slot = LatestRequestSlot({ tasks.addLast(it) }) { item, isLatest ->
            consumed += item
            if (item == "tree") {
                val request = SourceStopRequest(1)
                slot.enqueue("remote")
                request.complete(1, null)
                assertEquals("Source request was superseded", request.await(1, isLatest))
            }
        }
        slot.enqueue("tree")
        assertTrue(consumed.isEmpty())
        tasks.removeFirst().invoke()
        assertEquals(listOf("tree", "remote"), consumed)
    }

    @Test
    fun noAcknowledgementTimesOutInsteadOfClaimingRelease() {
        val request = SourceStopRequest(99)
        assertTrue(request.await(1) { true }!!.contains("timed out"))
    }

    @Test
    fun captureCleanupBeforeDestroyDoesNotReleaseReplacementEarly() {
        val completion = SourceStopCompletion()
        completion.cleanupFinished(null)
        assertFalse(completion.result.isDone)
        completion.ownerDestroyed()
        assertTrue(completion.result.isDone)
        assertNull(completion.result.getNow("pending"))
    }

    @Test
    fun captureDestroyBeforeCleanupStillWaitsForReaderCompletion() {
        val completion = SourceStopCompletion()
        completion.ownerDestroyed()
        assertFalse(completion.result.isDone)
        completion.cleanupFinished(null)
        assertTrue(completion.result.isDone)
        assertNull(completion.result.getNow("pending"))
    }

    @Test
    fun captureErrorIsImmediateAndCannotTurnIntoSuccessOnDestroy() {
        val completion = SourceStopCompletion()
        completion.cleanupFinished(ReaderStop.TIMEOUT)
        assertEquals(ReaderStop.TIMEOUT, completion.result.getNow(null))
        completion.ownerDestroyed()
        assertEquals(ReaderStop.TIMEOUT, completion.result.getNow(null))
        val retry = SourceStopCompletion()
        retry.cleanupFinished(null)
        assertFalse(retry.result.isDone)
        retry.ownerDestroyed()
        assertNull(retry.result.getNow("pending"))
    }
}
