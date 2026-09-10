package dev.phosphor.mobil3

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MicHandoffPolicyTest {
    private class Rig {
        var starts = 0
        var live = false
        val publications = mutableListOf<String?>()
        val completions = mutableListOf<(String?) -> Unit>()
        val policy = MicHandoffPolicy(
            start = { done -> starts++; completions += done },
            publish = { error -> live = error == null; publications += error },
        )
        fun request(id: String = "mic-A", sequence: Long = 10) {
            live = false
            policy.request(id, sequence)
        }
        fun idle(sequence: Long = 11, id: String = "mic-A") =
            policy.captureStatus(sequence, idle = true, requestId = id)
        fun release(id: String = "mic-A") = policy.sourcesReleased(id)
    }

    @Test
    fun staleIdleAndUnrelatedLaterIdleNeverStart() {
        val rig = Rig()
        rig.request()
        rig.release()
        rig.idle(9)
        rig.idle(10)
        rig.idle(11, "older-stop")
        rig.policy.captureStatus(12, idle = true, requestId = null)
        rig.policy.captureStatus(13, idle = false, requestId = "mic-A")
        assertEquals(0, rig.starts)
        assertFalse(rig.live)
        assertTrue(rig.policy.isPending)
    }

    @Test
    fun postStopIdleStartsOnceOnlyAfterB4ReleaseInEitherDeliveryOrder() {
        for (idleFirst in listOf(false, true)) {
            val rig = Rig()
            rig.request()
            if (idleFirst) rig.idle() else rig.release()
            assertEquals(0, rig.starts)
            if (idleFirst) rig.release() else rig.idle()
            assertEquals(1, rig.starts)
            assertFalse(rig.live)
            rig.idle()
            rig.idle(12)
            rig.release()
            assertEquals(1, rig.starts)
            rig.completions.single()(null)
            assertTrue(rig.live)
            assertFalse(rig.policy.isPending)
            rig.idle(13)
            rig.release()
            rig.completions.single()(null)
            assertEquals(1, rig.starts)
            assertEquals(listOf<String?>(null), rig.publications)
        }
    }

    @Test
    fun captureReaderCleanupAndDestructionStillGateTheCorrelatedIdle() {
        for (destroyFirst in listOf(false, true)) {
            val rig = Rig()
            val completion = SourceStopCompletion()
            rig.request()
            completion.result.thenAccept { error ->
                rig.policy.captureStatus(11, error == null, "mic-A", error)
                if (error == null) rig.release()
            }
            if (destroyFirst) completion.ownerDestroyed() else completion.cleanupFinished(null)
            assertEquals(0, rig.starts)
            if (destroyFirst) completion.cleanupFinished(null) else completion.ownerDestroyed()
            assertEquals(1, rig.starts)
            assertFalse(rig.live)
        }
    }

    @Test
    fun captureStopFailureCannotStartOrBecomeLiveAfterLateIdle() {
        val rig = Rig()
        rig.request()
        rig.policy.captureStatus(11, false, "mic-A", ReaderStop.TIMEOUT)
        rig.release()
        rig.idle(12)
        assertEquals(0, rig.starts)
        assertFalse(rig.live)
        assertEquals(listOf<String?>(ReaderStop.TIMEOUT), rig.publications)
    }

    @Test
    fun cancellationForEveryNewSourceRejectsIdleReleaseAndLateStartupCompletion() {
        for (source in listOf("local", "remote", "capture", "stop", "destroy")) {
            for (alreadyStarting in listOf(false, true)) {
                val rig = Rig()
                rig.request()
                if (alreadyStarting) { rig.idle(); rig.release() }
                rig.policy.cancel()
                rig.idle()
                rig.release()
                rig.completions.forEach { it(null) }
                assertEquals(if (alreadyStarting) 1 else 0, rig.starts, source)
                assertFalse(rig.live, source)
                assertTrue(rig.publications.isEmpty(), source)
            }
        }
    }

    @Test
    fun supersededMicRequestIgnoresOlderCompletionEvenWithSameBaselineSequence() {
        val rig = Rig()
        rig.request()
        rig.idle()
        rig.release()
        val oldCompletion = rig.completions.single()
        rig.request("mic-B", 10)
        rig.idle(12, "mic-A")
        rig.release("mic-A")
        oldCompletion(null)
        assertFalse(rig.live)
        assertEquals(1, rig.starts)
        rig.idle(13, "mic-B")
        rig.release("mic-B")
        assertEquals(2, rig.starts)
        oldCompletion("old start failed")
        assertTrue(rig.publications.isEmpty())
        rig.completions.last()(null)
        assertTrue(rig.live)
    }

    @Test
    fun newerB4SourceSelectionInvalidatesQueuedReleaseBeforeMicStart() {
        val publication = LocalSourcePublication()
        val revision = publication.published(LocalSourcePublication.Source.OTHER).revision
        val rig = Rig()
        rig.request()
        rig.idle()
        publication.selected()
        // MainActivity uses the same accepts check on the queued release reply and actual start.
        if (publication.accepts(revision)) rig.release()
        assertEquals(0, rig.starts)
        assertFalse(rig.live)
    }

    @Test
    fun initializationRecordingAndScopeFailuresRemainNonLive() {
        for (failure in listOf("init", "start-throws", "not-recording", "state-throws", "arm")) {
            val rig = Rig()
            rig.request()
            rig.idle()
            rig.release()
            val steps = mutableListOf<String>()
            val error = startMicRecording(
                initialized = { steps += "init"; failure != "init" },
                startRecording = {
                    steps += "start"
                    if (failure == "start-throws") throw SecurityException("permission revoked")
                },
                recording = {
                    steps += "recording"
                    if (failure == "state-throws") error("recording state unavailable")
                    failure != "not-recording"
                },
                armScope = { steps += "arm"; if (failure == "arm") error("ring unavailable") },
            )
            assertNotNull(error, failure)
            rig.completions.single()(error)
            assertFalse(rig.live, failure)
            assertFalse(rig.policy.isPending)
            if (failure == "init") assertEquals(listOf("init"), steps)
            if (failure != "arm") assertFalse("arm" in steps)
            rig.idle(12)
            assertEquals(1, rig.starts)
        }
    }

    @Test
    fun successfulRecordingArmsScopeBeforePublishingLive() {
        val rig = Rig()
        rig.request()
        rig.idle()
        rig.release()
        val steps = mutableListOf<String>()
        val error = startMicRecording(
            initialized = { steps += "init"; true },
            startRecording = { assertFalse(rig.live); steps += "start" },
            recording = { steps += "recording"; true },
            armScope = { assertFalse(rig.live); steps += "arm" },
        )
        assertNull(error)
        assertEquals(listOf("init", "start", "recording", "arm"), steps)
        assertFalse(rig.live)
        rig.completions.single()(error)
        assertTrue(rig.live)
    }

    @Test
    fun failedUninitializedMicCleanupDoesNotClearAnotherOwnersRing() {
        var ringActive = true
        var released = false
        assertNull(ReaderStop.finish(null, {}, { released = true }, {
            cleanupOwnedSource(false) { ringActive = false }
        }))
        assertTrue(released)
        assertTrue(ringActive)
    }

    @Test
    fun stoppedReceiverRereadsNewestIdleInEitherCallbackOrder() {
        for (order in listOf(listOf("mic-A", "mic-B"), listOf("mic-B", "mic-A"))) {
            val token = MicStopStatusToken()
            val rig = Rig()
            val replies = mutableListOf<String>()
            var snapshot: String? = null
            var sequence = 10L
            token.select("mic-A")
            token.select("mic-B")
            rig.request("mic-B")
            for (id in order) {
                if (token.accepts(id)) { snapshot = id; sequence++ }
                replies += id
            }
            assertEquals(order, replies)
            assertEquals("mic-B", snapshot)
            // No broadcast was observed while the receiver was stopped.
            rig.idle(sequence, requireNotNull(snapshot))
            rig.release("mic-B")
            assertEquals(1, rig.starts)
        }
    }

    @Test
    fun sharedCaptureCompletionCannotOverwriteTheLatestMicSnapshot() {
        for (destroyFirst in listOf(false, true)) {
            val token = MicStopStatusToken()
            val completion = SourceStopCompletion()
            val publications = mutableListOf<String>()
            val replies = mutableSetOf<String>()
            for (id in listOf("mic-A", "mic-B")) {
                token.select(id)
                completion.result.thenAccept { error ->
                    assertNull(error)
                    if (token.accepts(id)) publications += id
                    replies += id
                }
            }
            if (destroyFirst) completion.ownerDestroyed() else completion.cleanupFinished(null)
            assertTrue(publications.isEmpty())
            if (destroyFirst) completion.cleanupFinished(null) else completion.ownerDestroyed()
            assertEquals(listOf("mic-B"), publications)
            assertEquals(setOf("mic-A", "mic-B"), replies)
        }
    }

    @Test
    fun retryUsesNewCompletionWithoutRevivingTheFailedMicRequest() {
        val token = MicStopStatusToken()
        val rig = Rig()
        val failed = SourceStopCompletion()
        token.select("mic-A")
        rig.request()
        failed.result.thenAccept { error ->
            if (token.accepts("mic-A")) rig.policy.captureStatus(11, error == null, "mic-A", error)
        }
        failed.cleanupFinished(ReaderStop.TIMEOUT)
        assertFalse(rig.policy.isPending)
        assertEquals(0, rig.starts)

        val retry = SourceStopCompletion()
        token.select("mic-B")
        rig.request("mic-B", 11)
        retry.result.thenAccept { error ->
            if (token.accepts("mic-B")) rig.policy.captureStatus(12, error == null, "mic-B", error)
        }
        failed.ownerDestroyed()
        retry.cleanupFinished(null)
        assertEquals(0, rig.starts)
        retry.ownerDestroyed()
        rig.release("mic-B")
        assertEquals(1, rig.starts)
        assertFalse(token.accepts("mic-A"))
    }

    @Test
    fun nonMicStopRetiresEveryPendingMicStatusToken() {
        val token = MicStopStatusToken()
        token.select("mic-A")
        token.select("mic-B")
        token.select(null)
        assertFalse(token.accepts("mic-A"))
        assertFalse(token.accepts("mic-B"))
        assertFalse(token.accepts(null))
        token.select("mic-C")
        assertTrue(token.accepts("mic-C"))
        assertFalse(token.accepts("mic-B"))
    }

    @Test
    fun productionWiringUsesThePolicyAndCompletionNotATimer() {
        fun source(name: String): String {
            val relative = "src/main/kotlin/dev/phosphor/mobil3/$name.kt"
            val file = listOf(File(relative), File("app/$relative")).first { it.isFile }
            return file.readText()
        }
        val policy = source("MicHandoffPolicy")
        val mic = source("MicController")
        val activity = source("MainActivity")
        val capture = source("CaptureService")
        val playback = source("PlaybackService")
        val startMic = activity.substringAfter("override fun startMic()").substringBefore("private fun micStartEligible")
        for (text in listOf(policy, mic, startMic)) {
            for (timer in listOf("Thread.sleep", "postDelayed", "delay(", "Timer(", "schedule(")) {
                assertFalse(timer in text, timer)
            }
        }
        assertTrue("MicHandoffPolicy(" in activity)
        assertTrue("micHandoff.request(request, CaptureService.currentStatus().sequence)" in startMic)
        assertTrue("micHandoff.sourcesReleased(request)" in startMic)
        assertTrue("mic.established() && mic.standalone()" in startMic)
        val requestPath = startMic.substringAfter("val selection = selectSource(SignalKind.MIC)")
        assertTrue("ui.live = false" in requestPath)
        assertFalse("ui.live = true" in requestPath)
        assertTrue("candidate.startRecording()" in mic)
        assertTrue("it.state != AudioRecord.STATE_INITIALIZED" in mic)
        assertTrue("candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING" in mic)
        assertTrue("session.detach(attachment)" in mic)
        assertTrue("cleanupOwnedSource(ownedRing)" in capture)
        assertTrue("retirement.pending.thenAccept" in capture)
        assertTrue("retirement.add(this, stopCompletion.result)" in capture)
        assertTrue("micStopStatus.select(micRequest)" in capture)
        assertTrue("if (micStopStatus.accepts(micRequest))" in capture)
        assertTrue("statusSequence.incrementAndGet()" in capture)
        assertTrue(".putExtra(EXTRA_SEQUENCE, observed.sequence)" in capture)
        assertTrue("CaptureService.stopIntent(this)" in playback)
        assertTrue("owner?.javaClass ?: CaptureService::class.java" in capture)
        assertTrue(".putExtra(CaptureService.EXTRA_MIC_REQUEST, micRequest)" in playback)
        assertTrue("check(current()) { \"Microphone request cancelled\" }" in mic)
        assertTrue("if (isCurrent()) {" in mic)
        assertTrue("PlaybackService.localSourcePublication.accepts(revision)" in activity)
        val startCapture = activity.substringAfter("override fun startCapture()").substringBefore("private fun launchCaptureConsent")
        assertTrue(startCapture.contains("selectSource("))
        assertTrue(startCapture.indexOf("selectSource(") < startCapture.indexOf("if (alreadyCapturing) return"))
        for (entry in listOf("private fun loadUri", "private fun openDeck", "override fun startRemoteHost", "override fun disconnectRemote",
            "override fun startCapture", "override fun openFile", "override fun openFolder")) {
            assertTrue("selectSource(" in activity.substringAfter(entry).substringBefore("\n    }"), entry)
        }
    }
}
