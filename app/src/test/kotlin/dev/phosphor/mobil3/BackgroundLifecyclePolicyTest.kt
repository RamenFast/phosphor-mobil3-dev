package dev.phosphor.mobil3

import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BackgroundLifecyclePolicyTest {
    @Test fun waitingSuccessorRetirementDoesNotPoisonTheNextOwnerWhenPredecessorFinishesLate() {
        val predecessor = CompletableFuture<String?>()
        val successor = CompletableFuture<String?>()
        val entered = CountDownLatch(1)
        val retirement = SourceRetirement()
        retirement.add("waiting B", successor)
        val worker = Thread({
            entered.countDown()
            successor.complete(SourceRetirement.await(predecessor))
        }, "b21-native-retirement-test").apply { start() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            // An observer can time out, but the production wait keeps the actual dependency.
            assertFailsWith<TimeoutException> { retirement.pending.get(5, TimeUnit.MILLISECONDS) }
            assertFalse(successor.isDone)
            predecessor.complete(null)
            assertNull(retirement.pending.get(5, TimeUnit.SECONDS))
            val nextOwnerBarrier = retirement.pending
            assertNull(SourceRetirement.await(nextOwnerBarrier))
        } finally {
            predecessor.complete(null)
            worker.join(2_000)
        }
        assertTrue(source("PlaybackService.kt").contains("SourceRetirement.await(priorShutdown)"))
    }

    @Test fun genuinePredecessorNativeFailureRemainsVisibleToTheNextOwner() {
        val failure = "native deck close failed"
        assertEquals(failure, SourceRetirement.await(CompletableFuture.completedFuture(failure)))
    }

    @Test fun taskRemovalStopsTheAndroidCaptureServiceEvenWhenItsReaderNeedsAnExplicitRetry() {
        val lifecycle = CaptureStopLifecycle()
        assertFalse(lifecycle.shouldStopService(ReaderStop.TIMEOUT)) // ordinary source switch retains retry service
        lifecycle.removeTask()
        lifecycle.cleanupFinished(ReaderStop.TIMEOUT)
        assertTrue(lifecycle.shouldStopService(ReaderStop.TIMEOUT))
        lifecycle.ownerDestroyed()
        assertFalse(lifecycle.shouldStopService(null)) // no old stopSelf against a newer instance
        assertEquals(ReaderStop.TIMEOUT, lifecycle.result.get())
        val retry = lifecycle.retry()
        assertFalse(lifecycle.result.isDone)
        retry.cleanupFinished(null)
        assertNull(lifecycle.result.get())
        val capture = source("CaptureService.kt")
        assertTrue(capture.contains("current?.stopCompletion?.removeTask()"))
        assertEquals(2, Regex("stopCompletion\\.shouldStopService\\(error\\)").findAll(capture).count())
    }

    @Test fun aReplacementPlaybackFaceRestoresOnlyTheSameRealSurvivingCaptureOwner() {
        val replacement = CaptureOwnerToken()
        var mirrorsAttached = 0
        replacement.restore(null) { mirrorsAttached++ }
        assertEquals(0, mirrorsAttached)
        replacement.restore(7) { mirrorsAttached++ }
        replacement.restore(7) { mirrorsAttached++ }
        assertEquals(1, mirrorsAttached)
        assertTrue(replacement.accepts(7))
        assertFalse(replacement.accepts(6))
        replacement.clear()
        assertFalse(replacement.accepts(7))
        assertTrue(source("PlaybackService.kt").contains(
            "captureOwner.restore(CaptureService.currentOwnerId(), ::beginCaptureMirror)"))
        assertTrue(source("CaptureService.kt").contains("captureOwnerId?.takeIf { ownsCapture() }"))
    }

    @Test fun olderControllerFutureAfterAStopStartCannotReplaceTheNewerBinding() {
        val binding = ActivityControllerBinding()
        val older = binding.start()
        binding.cancel()
        assertFalse(binding.accepts(older))
        val newer = binding.start()
        val released = mutableListOf<Long>()
        var published: Long? = null
        fun complete(request: Long) {
            if (binding.accepts(request)) published = request else released += request
        }
        complete(newer)
        complete(older)
        assertEquals(newer, published)
        assertEquals(listOf(older), released)
        binding.cancel()
        assertFalse(binding.accepts(newer))
        val activity = source("MainActivity.kt")
        assertTrue(activity.contains("val bindingRevision = controllerBinding.start()"))
        assertTrue(activity.contains("!controllerBinding.accepts(bindingRevision)"))
        assertEquals(2, Regex("controllerBinding\\.cancel\\(\\)").findAll(activity).count())
        assertTrue(activity.contains("runCatching { future.get().release() }"))
    }

    @Test fun lifecycleSourceWiringStopsOnlyTheOwnedActivityMicAndDoesNotReplaceTerminalWork() {
        val activity = source("MainActivity.kt")
        val destroy = activity.substringAfter("override fun onDestroy()").substringBefore("\n    }")
        assertTrue(destroy.contains("selectSource()"))
        assertFalse(destroy.contains("mic.stop()"))
        assertTrue(destroy.contains("MicCaptureService.unobserve(micChanged)"))
        assertTrue(source("MicCaptureService.kt").contains("completion.ownerDestroyed()"))
        assertFalse(destroy.contains("MicController.stopForLocal"))
        val stop = activity.substringAfter("override fun onStop()").substringBefore("\n    }")
        assertFalse(stop.contains("mic.stop()"))
        val playback = source("PlaybackService.kt")
        assertEquals(1, Regex("localDeckRequests\\.enqueue\\(LocalDeckRequest\\.Shutdown").findAll(playback).count())
        assertFalse(playback.substringAfter("override fun onDestroy()").substringBefore("\n    }").contains("localDeckRequests.enqueue"))
        assertTrue(playback.substringAfter("override fun onDestroy()").substringBefore("\n    }")
            .contains("beginShutdown(stopReaders = false)"))
        assertTrue(playback.substringAfter("private fun taskRemoved(").substringBefore("\n    }")
            .contains("beginShutdown(stopReaders = true)"))
        for (name in listOf("PlaybackService.kt", "CaptureService.kt")) {
            val taskRemoved = source(name).substringAfter("override fun onTaskRemoved(").substringBefore("\n    }")
            assertTrue(taskRemoved.contains("BackgroundLifecycle.removeTask"))
            assertFalse(taskRemoved.contains("super.onTaskRemoved"))
        }
        val capture = source("CaptureService.kt")
        assertFalse(capture.contains(".setAction(PlaybackService.ACTION_CAPTURE_STOPPED)"))
        assertTrue(capture.contains("PlaybackService.captureStopped(captureOwnerId)"))
    }

    private fun source(name: String): String = listOf(
        File("src/main/kotlin/dev/phosphor/mobil3/$name"),
        File("app/src/main/kotlin/dev/phosphor/mobil3/$name"),
    ).first { it.isFile }.readText()

    @Test fun sameLingeringServiceHandlesASecondTaskRemovalAfterLingerIsDisabled() {
        val policy = BackgroundLifecyclePolicy()
        val serviceRevision = policy.enterActivity(11)
        val first = policy.callbackRevision(serviceRevision, currentTaskPresent = false)!!
        assertEquals(BackgroundLifecyclePolicy.Removal(true, true), policy.remove(first, true, false, false, true))
        policy.enterActivity(22)
        val second = policy.callbackRevision(serviceRevision, currentTaskPresent = false)!!
        assertEquals(BackgroundLifecyclePolicy.Removal(false, false), policy.remove(second, false, false, false, true))
    }

    @Test fun aNewerLiveTaskRejectsDelayedRemovalOnTheSameRetainedService() {
        val policy = BackgroundLifecyclePolicy()
        val serviceRevision = policy.enterActivity(11)
        policy.remove(serviceRevision, true, true, false, false)
        val newer = policy.enterActivity(22)
        assertNull(policy.callbackRevision(serviceRevision, currentTaskPresent = true))
        assertTrue(policy.accepts(newer))
        assertNull(policy.callbackRevision(serviceRevision, currentTaskPresent = null))
        assertEquals(newer, policy.callbackRevision(serviceRevision, currentTaskPresent = false))
    }

    @Test fun absentLingerStopsEveryOwnedSourceCombination() {
        assertFalse(BackgroundLifecyclePolicy.linger(emptyMap<String, Any>()))
        for (local in listOf(false, true)) for (relay in listOf(false, true)) for (capture in listOf(false, true)) {
            val policy = BackgroundLifecyclePolicy()
            val task = policy.enterActivity()
            assertEquals(BackgroundLifecyclePolicy.Removal(false, false), policy.remove(task, false, local, relay, capture))
            assertFalse(policy.accepts(task))
        }
    }

    @Test fun lingerPreservesOnlyExistingServiceOwnersIncludingPausedLocal() {
        for (local in listOf(false, true)) for (relay in listOf(false, true)) for (capture in listOf(false, true)) {
            val policy = BackgroundLifecyclePolicy()
            val removal = policy.remove(policy.enterActivity(), true, local, relay, capture)
            assertEquals(BackgroundLifecyclePolicy.Removal(local || relay || capture, capture), removal)
        }
        // There is no microphone service owner or playing-state requirement in this decision.
        val micOnly = BackgroundLifecyclePolicy()
        assertEquals(BackgroundLifecyclePolicy.Removal(false, false), micOnly.remove(micOnly.enterActivity(), true, false, false, false))
    }

    @Test fun bothServiceCallbackOrdersRetireOnceAndRejectOldCallbacksAfterReentry() {
        for (firstOwner in listOf("capture", "playback")) {
            val policy = BackgroundLifecyclePolicy()
            val oldTask = policy.enterActivity()
            assertNotNull(policy.remove(oldTask, false, true, true, true), firstOwner)
            assertNull(policy.remove(oldTask, false, true, true, true))
            val newTask = policy.enterActivity()
            assertTrue(policy.accepts(newTask))
            assertNull(policy.remove(oldTask, false, true, true, true))
            assertTrue(policy.accepts(newTask))
            assertNotNull(policy.remove(newTask, false, true, true, true))
        }
    }

    @Test fun removedTaskAndActivityTokensStayRetiredEvenWithLinger() {
        for (linger in listOf(false, true)) {
            val policy = BackgroundLifecyclePolicy()
            val task = policy.enterActivity()
            val activity = policy.activityRevision
            val callbacks = List(4) { { policy.accepts(task) && policy.acceptsActivity(activity) } }
            policy.remove(task, linger, true, false, true)
            assertTrue(callbacks.none { it() })
            policy.enterActivity()
            assertTrue(callbacks.none { it() })
        }
    }

    @Test fun destructionRetiresOnlyItsActivityAndNotANewerActivity() {
        val policy = BackgroundLifecyclePolicy()
        val task = policy.enterActivity()
        val older = policy.activityRevision
        policy.enterActivity()
        val newer = policy.activityRevision
        policy.leaveActivity(older)
        assertTrue(policy.acceptsActivity(newer))
        assertFalse(policy.acceptsActivity(older))
        policy.leaveActivity(newer)
        assertFalse(policy.acceptsActivity(newer))
        assertTrue(policy.accepts(task)) // service-owned sources do not die on Activity destruction
    }

    @Test fun taskRemovalSupersedesPreparedNativePublicationAndPendingStart() {
        val policy = BackgroundLifecyclePolicy()
        val task = policy.enterActivity()
        val drains = ArrayDeque<() -> Unit>()
        val publications = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        lateinit var slot: LatestRequestSlot<String>
        slot = LatestRequestSlot({ drains.addLast(it) }) { request, current ->
            when (request) {
                "prepare" -> {
                    publications.addLast { if (current()) events += "late restart" }
                    slot.enqueue("pending start")
                    policy.remove(task, false, true, false, false)
                    slot.enqueue("shutdown")
                }
                else -> events += request
            }
        }
        slot.enqueue("prepare")
        assertTrue(events.isEmpty()) // caller does not run native shutdown
        drains.removeFirst().invoke()
        publications.removeFirst().invoke()
        assertEquals(listOf("shutdown"), events)
        assertFalse(policy.accepts(task))
    }

    @Test fun newSourceWaitsForCleanupAndOwnerDestructionInEitherOrder() {
        for (destroyFirst in listOf(false, true)) {
            val retirement = SourceRetirement()
            val old = SourceStopCompletion()
            retirement.add("old capture", old.result)
            val newSourceBarrier = retirement.pending
            if (destroyFirst) old.ownerDestroyed() else old.cleanupFinished(null)
            assertFalse(newSourceBarrier.isDone)
            if (destroyFirst) old.cleanupFinished(null) else old.ownerDestroyed()
            assertTrue(newSourceBarrier.isDone)
            assertNull(newSourceBarrier.get())
        }
    }

    @Test fun newerIdleOwnerCannotHideAnOlderUnfinishedCleanup() {
        val retirement = SourceRetirement()
        val old = CompletableFuture<String?>()
        retirement.add("old", old)
        retirement.add("new idle", CompletableFuture.completedFuture(null))
        val barrier = retirement.pending
        assertFalse(barrier.isDone)
        old.complete(null)
        assertTrue(barrier.isDone)
        assertNull(barrier.get())
    }

    @Test fun failureIsRetainedUntilTheSameOwnerExplicitlyRetries() {
        val retirement = SourceRetirement()
        retirement.add("capture", CompletableFuture.completedFuture(ReaderStop.TIMEOUT))
        retirement.add("unrelated", CompletableFuture.completedFuture(null))
        assertEquals(ReaderStop.TIMEOUT, retirement.pending.get())
        val retry = CompletableFuture<String?>()
        retirement.add("capture", retry)
        assertFalse(retirement.pending.isDone)
        retry.complete(null)
        assertNull(retirement.pending.get())
    }

    @Test fun completedNativeRetirementDoesNotPoisonARealReaderRetryOnTheNextSourceSelection() {
        val nativeRetirement = SourceRetirement()
        val readers = SourceRetirement()
        val nativeClosed = CompletableFuture<String?>()
        val timedOut = CaptureStopLifecycle()
        nativeRetirement.add("old playback", nativeClosed)
        readers.add("capture", timedOut.result)
        val entered = CountDownLatch(1)
        val exit = CountDownLatch(1)
        val reader = Thread({ entered.countDown(); exit.await() }, "b21-retirement-test").apply { start() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val error = ReaderStop.finish(reader, {}, {}, {}, timeoutMs = 1)
            assertEquals(ReaderStop.TIMEOUT, error)
            timedOut.cleanupFinished(error)
            timedOut.ownerDestroyed()
            nativeClosed.complete(null)
            val nextServiceNativeBarrier = nativeRetirement.pending
            assertNull(nextServiceNativeBarrier.get())
            assertEquals(ReaderStop.TIMEOUT, readers.pending.get())
            // The old service is gone. Reader exit alone cannot discard its failed receipt.
            exit.countDown()
            reader.join(2_000)
            assertFalse(reader.isAlive)
            assertEquals(ReaderStop.TIMEOUT, readers.pending.get())
            lateinit var retry: SourceStopCompletion
            readers.retryFailed { owner ->
                assertEquals("capture", owner)
                retry = timedOut.retry()
                readers.add(owner, retry.result)
            }
            val startAfterRealReaderStop = readers.pending
            assertFalse(startAfterRealReaderStop.isDone)
            retry.cleanupFinished(ReaderStop.finish(reader, {}, {}, {}, timeoutMs = 2_000))
            // No second Android destruction callback is needed for the retired owner.
            assertNull(startAfterRealReaderStop.get())
            assertNull(nextServiceNativeBarrier.get())
            val mirror = CaptureOwnerToken()
            mirror.attach(2)
            assertFalse(mirror.accepts(1))
            assertTrue(mirror.accepts(2))
        } finally {
            exit.countDown()
            reader.join(2_000)
        }
    }

    @Test fun aFailedRetryRemainsBlockedAndPendingOwnersAreNotRetriedTwice() {
        val retirement = SourceRetirement()
        val lifecycle = CaptureStopLifecycle()
        lifecycle.cleanupFinished(ReaderStop.TIMEOUT)
        lifecycle.ownerDestroyed()
        retirement.add("capture", lifecycle.result)
        var retries = 0
        lateinit var retry: SourceStopCompletion
        retirement.retryFailed { owner ->
            retries++
            retry = lifecycle.retry()
            retirement.add(owner, retry.result)
        }
        retirement.retryFailed { retries++ }
        assertEquals(1, retries)
        assertFalse(retirement.pending.isDone)
        retry.cleanupFinished(ReaderStop.TIMEOUT)
        assertEquals(ReaderStop.TIMEOUT, retirement.pending.get())
    }

    @Test fun lateOldCaptureCleanupTokenDoesNotMatchANewerOrAbsentMirrorOwner() {
        val token = CaptureOwnerToken()
        assertFalse(token.accepts(1))
        token.attach(1)
        assertTrue(token.accepts(1))
        token.attach(2)
        assertFalse(token.accepts(1))
        assertTrue(token.accepts(2))
        token.clear()
        assertFalse(token.accepts(2))
    }
}
