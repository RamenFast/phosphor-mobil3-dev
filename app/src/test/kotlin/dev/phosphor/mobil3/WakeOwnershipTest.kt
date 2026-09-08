package dev.phosphor.mobil3

import java.io.File
import dev.phosphor.mobil3.ui.ScopeUiState
import org.json.JSONObject
import org.junit.Test
import kotlin.test.*

/** Executes production policy, lock calls and reader callbacks. Does not execute Android PowerManager. */
class WakeOwnershipTest {
    private class Lock : SourceWakeLock.Backend {
        override var isHeld = false
        var acquires = 0
        var releases = 0
        var failAcquire = false
        var failRelease = false
        override fun acquire() {
            if (failAcquire) throw SecurityException("denied")
            check(!isHeld)
            acquires++
            isHeld = true
        }
        override fun release() {
            if (failRelease) throw IllegalStateException("release failed")
            check(isHeld)
            releases++
            isHeld = false
        }
    }

    private class Owner {
        val lock = Lock()
        val failures = mutableListOf<RuntimeException>()
        var created = 0
        val wake = SourceWakeLock(create = { created++; lock }, onFailure = { failures.add(it) })
    }

    /** JNI JSON and Android scheduling are synthetic boundaries, the owners below are production. */
    private class LocalTerminalHarness {
        val owner = Owner()
        val truth = PlaybackTruth()
        val output = LocalOutputState()
        val transport = LocalTransportIntent()
        val survival = LocalSourceSurvival()
        val queue = LocalQueuePolicy()
        val worker = ArrayDeque<() -> Unit>()
        val main = ArrayDeque<() -> Unit>()
        val events = ArrayDeque<String>()
        var metadata = """{"path":"/same.flac","duration_ms":null}"""
        var duration: Long? = null
        var terminals = 0
        var afterTerminal: () -> Unit = {}
        var nativeOpen = 0L

        fun prepare() {
            survival.nativeReplacing()
            owner.wake.stop()
            queue.beginSelection()
            truth.opened("/same.flac", "track", { true }, ++nativeOpen, newItem = false)
        }
        fun publish() {
            output.opened()
            transport.publish(true)
            survival.published()
            queue.published()
            update()
        }
        fun update() = owner.wake.localChanged(
            !survival.loss().native, transport.playing, output.ready, output.failed,
        )
        fun event(name: String) { events.add("""{"event":"$name","path":"/same.flac","open_id":$nativeOpen}""") }
        fun poll() = truth.poll(
            schedule = { worker.add(it) }, onMain = { main.add(it) },
            readEvent = { events.removeFirstOrNull() }, readMetadata = { metadata }, readArtwork = { null },
            publish = { duration = it.durationMs },
            terminal = { result, current ->
                if (current()) {
                    terminals++
                    output.ended(result)
                    transport.publish(false)
                    update()
                    afterTerminal()
                }
            },
        )
        fun drainMain() { while (main.isNotEmpty()) main.removeFirst().invoke() }
        fun drain() {
            while (worker.isNotEmpty()) worker.removeFirst().invoke()
            drainMain()
        }
        fun observe(name: String) { event(name); poll(); drain() }
        fun start() { prepare(); publish(); observe("track_started") }
    }

    @Test fun unknownDurationAndEarlyDecodeEndReleaseThroughQueuedOwnerPublication() {
        for (duration in listOf("null", "900000")) {
            val h = LocalTerminalHarness()
            h.metadata = """{"path":"/same.flac","duration_ms":$duration}"""
            h.start()
            assertEquals(duration.toLongOrNull(), h.duration)
            h.observe("playback_ended") // EOF and post-start decode failure share this native event.
            assertTrue(h.owner.lock.isHeld)
            assertTrue(h.output.ready)
            h.event("playback_drained"); h.poll(); h.worker.removeFirst().invoke()
            assertTrue(h.owner.lock.isHeld) // No worker-thread Media3/wake publication.
            h.drainMain()
            assertEquals(PlaybackTruth.Terminal.ENDED, h.output.terminal)
            assertFalse(h.transport.playing)
            assertFalse(h.owner.lock.isHeld)
            repeat(3) { h.observe("playback_drained"); h.update() }
            // Even a later play intent cannot make terminal output READY without a successful reopen.
            h.transport.publish(true); h.update()
            assertFalse(h.owner.lock.isHeld)
            assertEquals(1, h.terminals)
            assertEquals(1, h.owner.lock.releases)
        }
    }

    @Test fun pausedTailAfterDecoderTimeoutCanResumeBeforeRealDrainPublication() {
        val h = LocalTerminalHarness()
        h.start()
        h.transport.publish(false); h.update()
        h.observe("playback_ended")
        assertTrue(h.output.ready)
        assertFalse(h.owner.lock.isHeld)
        repeat(3) { h.poll(); h.drain(); h.update() }
        h.transport.publish(true); h.update()
        assertTrue(h.owner.lock.isHeld)
        h.observe("playback_drained")
        assertFalse(h.output.ready)
        assertFalse(h.owner.lock.isHeld)
        assertEquals(2, h.owner.lock.releases)
    }

    @Test fun outputErrorStopsPublishedOwnerWithoutDurationOrDecoderEvent() {
        val h = LocalTerminalHarness()
        h.start()
        h.observe("output_error")
        assertEquals(PlaybackTruth.Terminal.OUTPUT_FAILED, h.output.terminal)
        assertTrue(h.output.failed)
        repeat(3) { h.update(); h.observe("output_error") }
        assertEquals(1, h.terminals)
        assertEquals(1, h.owner.lock.releases)
        assertFalse(h.owner.lock.isHeld)
    }

    @Test fun terminalQueueContinuationCannotReacquireUntilReplacementPublication() {
        val h = LocalTerminalHarness()
        h.start()
        h.afterTerminal = { h.prepare() }
        h.observe("playback_drained")
        repeat(3) { h.update() }
        assertFalse(h.owner.lock.isHeld)
        assertFalse(h.queue.mayAdvance())
        h.publish(); h.observe("track_started")
        assertTrue(h.queue.mayAdvance())
        assertTrue(h.owner.lock.isHeld)
        assertEquals(2, h.owner.lock.acquires)
        assertEquals(1, h.owner.lock.releases)
    }

    @Test fun delayedSamePathTerminalAndDestroyedOwnerCannotAffectReplacementWake() {
        for (destroy in listOf(false, true)) {
            val h = LocalTerminalHarness()
            h.start()
            h.event("output_error"); h.poll(); h.worker.removeFirst().invoke()
            h.prepare(); h.publish() // Same-path successful seek/reopen creates a new Open.
            if (destroy) h.owner.wake.destroy()
            h.drainMain(); h.update()
            assertEquals(0, h.terminals)
            assertTrue(h.output.ready)
            assertEquals(!destroy, h.owner.lock.isHeld)
        }
    }

    private class MicFailureHarness {
        val owner = Owner()
        val ui = ScopeUiState()
        val main = ArrayDeque<() -> Unit>()
        val recorder = Any()
        var currentRecorder: Any? = recorder
        var running = false
        var taskCurrent = true
        var activityDestroyed = false
        var selected = 1
        var requestGeneration = 1
        var stops = 0
        var reports = 0
        var saved = "none"
        private lateinit var publishFailure: () -> Unit
        fun start() {
            // Install the Activity callback at recorder start, not at failure dispatch.
            publishFailure = {
                publishMicReaderFailure(ui, { taskCurrent && !activityDestroyed }) {
                    reports++
                    saved = runtimeInputSource(ui, running)
                }
            }
            assertNull(startMicRecording(
                initialized = { true }, startRecording = { running = true },
                recording = { running }, armScope = {},
            ))
            owner.wake.microphoneChanged(running, false)
            ui.live = true
            ui.sourceLabel = "mic"
            saved = runtimeInputSource(ui, running)
        }
        fun failRead(throws: Boolean) {
            readSourceSamples(
                readEpoch = { 0L },
                running = { running }, read = { if (throws) throw SecurityException("revoked") else -6 },
                push = { _, _ -> fail("failed read pushed samples") },
                failed = { main.add {
                    retireSourceReaderFailure(
                        isCurrent = { currentRecorder === recorder && running },
                        stop = { stops++; running = false; owner.wake.microphoneChanged(false, false) },
                        publishFailure = publishFailure,
                    )
                } },
            )
        }
        fun drain() { while (main.isNotEmpty()) main.removeFirst().invoke() }
    }

    @Test fun negativeAndThrownMicReadClearRealFaceReleaseWakeAndPersistNoSource() {
        for (throws in listOf(false, true)) {
            val h = MicFailureHarness()
            h.start()
            assertEquals("mic", h.saved)
            h.failRead(throws)
            assertTrue(h.ui.live)
            assertTrue(h.owner.lock.isHeld)
            h.drain()
            assertEquals(1, h.stops)
            assertEquals(1, h.reports)
            assertEquals(1, h.owner.lock.releases)
            assertFalse(h.ui.live)
            assertEquals("no source", h.ui.sourceLabel)
            assertEquals("none", h.saved)
            assertEquals("none", runtimeInputSource(h.ui, h.running))
        }
    }

    @Test fun oldMicFailureCannotClearReplacementRecorderSourceTaskOrDestroyedFace() {
        for (replacement in listOf("mic", "deck", "capture", "remote", "task", "destroyed")) {
            val h = MicFailureHarness()
            val replacementOwner = Owner()
            h.start(); h.failRead(false)
            when (replacement) {
                "mic" -> h.currentRecorder = Any()
                "task" -> h.taskCurrent = false
                "destroyed" -> { h.activityDestroyed = true; h.running = false; h.owner.wake.destroy() }
                else -> {
                    h.selected++; h.ui.sourceLabel = replacement
                    when (replacement) {
                        "deck" -> replacementOwner.wake.localChanged(true, true, true, false)
                        "capture" -> replacementOwner.wake.captureChanged(true, true)
                        "remote" -> replacementOwner.wake.remoteChanged(true, RemoteLinkState.STREAMING)
                    }
                }
            }
            val face = h.ui.sourceLabel
            h.drain()
            assertEquals(0, h.reports)
            assertTrue(h.ui.live)
            assertEquals(face, h.ui.sourceLabel)
            if (replacement == "mic") {
                assertEquals(0, h.stops)
                assertTrue(h.owner.lock.isHeld)
            }
            if (replacement == "destroyed") assertFalse(h.owner.lock.isHeld)
            if (replacement in listOf("deck", "capture", "remote")) {
                assertTrue(replacementOwner.lock.isHeld)
                assertEquals(0, replacementOwner.lock.releases)
            }
        }
    }

    @Test fun cancelStartGenerationDoesNotHideCurrentMicFailureOrPersistPhantomMic() {
        val h = MicFailureHarness()
        h.start(); h.failRead(true)
        h.requestGeneration++
        h.drain()
        assertEquals(1, h.stops)
        assertEquals(1, h.reports)
        assertEquals("none", h.saved)
        h.ui.live = true; h.ui.sourceLabel = "mic" // Persistence independently rejects a stale face.
        assertEquals("none", runtimeInputSource(h.ui, h.running))
    }

    @Test fun cancelledPickerOrConsentRetainsCurrentMicFailureAuthorityBeforeAndAfterRead() {
        for (throws in listOf(false, true)) {
            for (selectionBeforeRead in listOf(false, true)) {
                val h = MicFailureHarness()
                h.start()
                val startSelection = h.selected
                if (!selectionBeforeRead) h.failRead(throws)
                // File/folder pickers and capture consent advance only start authority.
                // A cancelled result never releases or replaces this recorder.
                h.selected++
                h.requestGeneration++
                assertNotEquals(startSelection, h.selected)
                assertTrue(h.running)
                if (selectionBeforeRead) h.failRead(throws)
                h.drain()
                assertEquals(1, h.stops)
                assertEquals(1, h.reports)
                assertEquals(1, h.owner.lock.releases)
                assertFalse(h.owner.lock.isHeld)
                assertFalse(h.ui.live)
                assertEquals("no source", h.ui.sourceLabel)
                assertEquals("none", h.saved)
            }
        }
    }

    @Test fun idleMicFaceDoesNotPublishReaderFailure() {
        val idle = ScopeUiState().apply { sourceLabel = "mic"; live = false }
        publishMicReaderFailure(idle, { true }) { fail("idle face was published as a read failure") }
        assertFalse(idle.live)
    }

    @Test fun productionTerminalAndMicAdaptersUseTheExercisedOwners() {
        val base = "src/main/kotlin/dev/phosphor/mobil3/"
        val activity = source(base + "MainActivity.kt")
        val mic = source(base + "MicController.kt")
        val playback = source(base + "PlaybackService.kt")
        val player = source(base + "PhosphorPlayer.kt")
        assertTrue(mic.contains("owner === this && record === rec && running"))
        assertTrue(mic.contains("publishFailure = {"))
        assertTrue(mic.contains("Check microphone permission and the audio route, then select built-in mic again"))
        assertTrue(activity.contains("publishMicReaderFailure(ui, { taskIsCurrent() && !isDestroyed })"))
        val micStart = activity.substringAfter("private val micHandoff = MicHandoffPolicy(")
            .substringBefore("publish = { error ->")
        assertFalse(micStart.contains("sourceSelection"))
        assertTrue(activity.contains("runtimeInputSource(ui, mic.isRecording())"))
        val failure = activity.substringAfter("publishMicReaderFailure(ui, { taskIsCurrent()")
            .substringBefore("private var pendingAudioPermission")
        assertTrue(failure.contains("putString(\"last_source\", \"none\")"))
        assertTrue(failure.contains("Toast.makeText(this, error"))
        assertTrue(player.contains("private val output = LocalOutputState()"))
        val terminal = player.substringAfter("internal fun onNativeTerminal(")
            .substringBefore("internal fun onTrackMetadata")
        assertTrue(terminal.contains("output.ended(result)"))
        assertTrue(terminal.contains("playing = false"))
        assertTrue(player.contains("!output.ready -> Player.STATE_ENDED"))
        assertTrue(player.contains(".setPlayerError(if (output.failed)"))
        val watcher = playback.substringAfter("private fun startEndWatcher()")
            .substringBefore("private fun startRemote")
        assertTrue(watcher.contains("if (isCurrent() && session?.player === localPlayer)"))
        assertTrue(watcher.indexOf("localPlayer.onNativeTerminal(result)") < watcher.indexOf("if (continueQueue)"))
        assertTrue(watcher.contains("result != PlaybackTruth.Terminal.OUTPUT_FAILED"))
        assertTrue(watcher.contains("dur > 0 && pos >= dur - 350")) // Ordinary known-duration trigger is retained.
        assertFalse(watcher.substringAfter("val dur =").contains("localPlayer.playWhenReady = false"))
        assertFalse(watcher.substringAfter("terminal = {").substringBefore("val dur =").contains("closeOpenedLocal()"))
    }

    @Test fun cancelledPickerAndConsentAdaptersPreserveTheExistingRecorder() {
        val activity = source("src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt")
        assertTrue(activity.contains("private fun selectSource("))
        val selection = activity.substringAfter("private fun selectSource(")
            .substringBefore("private fun micRequestIsCurrent")
        assertTrue(selection.contains("micHandoffCancel()"))
        assertTrue(selection.contains("++sourceSelection"))
        assertFalse(selection.contains("mic.stop()"))
        val cancel = activity.substringAfter("private fun micHandoffCancel()")
            .substringBefore("private fun selectSource")
        assertTrue(cancel.contains("mic.cancelStart()"))
        assertFalse(cancel.contains("mic.stop()"))
        val file = activity.substringAfter("private val openFileLauncher =")
            .substringBefore("private val openFolderLauncher =")
        assertTrue(file.contains("uri?.let { loadUri(it) }"))
        val folder = activity.substringAfter("private val openFolderLauncher =")
            .substringBefore("runCatching")
        assertTrue(folder.contains("uri ?: return@registerForActivityResult"))
        val consent = activity.substringAfter("private val captureConsent =")
            .substringBefore("withSourcesReleased {")
        assertTrue(consent.contains("result.resultCode != android.app.Activity.RESULT_OK || data == null"))
        assertTrue(consent.contains("CaptureService.CaptureStatus.permissionNeeded("))
        assertTrue(consent.contains("return@registerForActivityResult"))
        assertFalse(consent.contains("mic.stop()"))
        val status = activity.substringAfter("val wasCapture = ui.sourceLabel.startsWith(\"capture\")")
            .substringBefore("private fun observeMicCaptureStatus")
        assertTrue(status.contains("else -> if (wasCapture)"))
    }

    @Test fun localPlayingPausedResumedEndedAndErrorFollowRealPolicy() {
        val owner = Owner()
        with(owner.wake) {
            localChanged(published = false, playing = true, ready = true, failed = false)
            assertFalse(live)
            assertEquals(0, owner.created)
            localChanged(published = true, playing = true, ready = true, failed = false)
            assertTrue(live)
            assertTrue(owner.lock.isHeld)
            localChanged(published = true, playing = false, ready = true, failed = false)
            assertFalse(live)
            localChanged(published = true, playing = true, ready = true, failed = false)
            assertTrue(live)
            localChanged(published = true, playing = true, ready = false, failed = false)
            assertFalse(live) // ended or idle cannot borrow stale play intent
            localChanged(published = true, playing = true, ready = true, failed = true)
            assertFalse(live)
        }
        assertEquals(2, owner.lock.acquires)
        assertEquals(2, owner.lock.releases)
        assertEquals(1, owner.created)
    }

    @Test fun nativeRetirementBlocksAStaleLocalPlayerUntilPublication() {
        val survival = LocalSourceSurvival()
        val owner = Owner()
        fun update() = owner.wake.localChanged(!survival.loss().native, true, true, false)
        update()
        survival.nativeReplacing()
        owner.wake.stop()
        update()
        assertFalse(owner.wake.live)
        survival.published()
        update()
        assertTrue(owner.wake.live)
        owner.wake.destroy()
        update()
        assertFalse(owner.wake.live)
    }

    @Test fun remoteConnectingGreetedLiveSilentLostFailedAndStoppedUseLinkTruth() {
        val owner = Owner()
        val cases = listOf(
            """{"state":"connecting"}""" to false,
            """{"state":"streaming","rx_a":0,"media_received":false,"media_live":false}""" to false,
            """{"state":"streaming","rx_a":1,"media_received":true,"media_live":true,"remote_rms":0.2}""" to true,
            """{"state":"streaming","rx_g":1,"media_received":true,"media_live":true,"remote_rms":0.0}""" to true,
            """{"state":"streaming","rx_a":900,"media_received":true,"media_live":false}""" to false,
            """{"state":"streaming","rx_a":900,"media_received":false,"media_live":false}""" to false,
            """{"state":"streaming","rx_a":900}""" to false,
            """{"state":"stalled","rx_a":1}""" to false,
            """{"state":"reconnecting"}""" to false,
            """{"state":"failed"}""" to false,
        )
        cases.forEach { (json, expected) ->
            owner.wake.remoteChanged(true, RemoteLinkTruth.read(JSONObject(json)).state)
            assertEquals(expected, owner.wake.live, json)
            assertEquals(expected, owner.lock.isHeld, json)
        }
        owner.wake.remoteChanged(true, RemoteLinkState.STREAMING)
        owner.wake.remoteChanged(false, RemoteLinkState.STREAMING)
        assertFalse(owner.wake.live)
        owner.wake.remoteChanged(true, null)
        assertFalse(owner.wake.live)
    }

    @Test fun captureStartingLiveSilentRevokedAndStoppedUseRecorderAndProjectionNotMetadata() {
        val owner = Owner()
        owner.wake.captureChanged(recording = false, projection = false)
        owner.wake.captureChanged(recording = false, projection = true)
        assertEquals(0, owner.created)
        owner.wake.captureChanged(recording = true, projection = true)
        assertTrue(owner.wake.live)
        // Silence, absent metadata, or an external paused glyph does not stop an AudioRecord.
        repeat(4) { owner.wake.captureChanged(recording = true, projection = true) }
        assertEquals(1, owner.lock.acquires)
        owner.wake.captureChanged(recording = true, projection = false)
        assertFalse(owner.wake.live)
        owner.wake.captureChanged(recording = false, projection = true)
        owner.wake.stop()
        assertEquals(1, owner.lock.releases)
    }

    @Test fun microphoneRecordingBelongsToActivityLifetimeNotVisibilityOrRetiringOwnership() {
        val owner = Owner()
        owner.wake.microphoneChanged(recording = false, activityDestroyed = false)
        assertEquals(0, owner.created)
        owner.wake.microphoneChanged(recording = true, activityDestroyed = false)
        assertTrue(owner.wake.live)
        assertFalse(SourceWakePolicy.visible(started = false, sourceLive = owner.wake.live))
        // Activity stop clears visible flags. The actual recorder callback still releases its lock.
        owner.wake.microphoneChanged(recording = false, activityDestroyed = false)
        assertFalse(owner.lock.isHeld)
        owner.wake.microphoneChanged(recording = true, activityDestroyed = true)
        assertFalse(owner.lock.isHeld)
        assertEquals(1, owner.lock.releases)
    }

    @Test fun repeatedStopDestroyAndLateCallbacksCannotReacquireRetiredOwner() {
        val owner = Owner()
        owner.wake.localChanged(true, true, true, false)
        repeat(3) { owner.wake.stop() }
        repeat(3) { owner.wake.destroy() }
        owner.wake.localChanged(true, true, true, false)
        owner.wake.remoteChanged(true, RemoteLinkState.STREAMING)
        owner.wake.captureChanged(true, true)
        owner.wake.microphoneChanged(true, false)
        assertFalse(owner.wake.live)
        assertEquals(1, owner.lock.acquires)
        assertEquals(1, owner.lock.releases)
    }

    @Test fun replacementOwnersHaveIndependentLocksAndOldCleanupCannotReleaseNewLock() {
        val old = Owner()
        val replacement = Owner()
        old.wake.captureChanged(true, true)
        old.wake.destroy()
        replacement.wake.microphoneChanged(true, false)
        old.wake.stop()
        old.wake.captureChanged(true, true)
        assertFalse(old.lock.isHeld)
        assertTrue(replacement.lock.isHeld)
        replacement.wake.destroy()
        assertEquals(1, old.lock.releases)
        assertEquals(1, replacement.lock.releases)
    }

    @Test fun failedAcquisitionAndReleaseAreReportedAndRetriedWithoutFalseOwnershipClaims() {
        val owner = Owner()
        owner.lock.failAcquire = true
        owner.wake.captureChanged(true, true)
        assertTrue(owner.wake.live) // source truth, not an assertion that PowerManager succeeded
        assertFalse(owner.lock.isHeld)
        assertEquals(1, owner.failures.size)
        owner.lock.failAcquire = false
        owner.wake.captureChanged(true, true)
        owner.lock.failRelease = true
        owner.wake.stop()
        assertFalse(owner.wake.live)
        assertTrue(owner.lock.isHeld)
        owner.lock.failRelease = false
        owner.wake.destroy()
        assertFalse(owner.lock.isHeld)
        assertEquals(2, owner.failures.size)
        assertEquals(1, owner.created)
    }

    @Test fun visibleMainAndPipFlagsReassertLiveTruthAndClearAtStop() {
        for (started in listOf(false, true)) {
            for (live in listOf(false, true)) {
                assertEquals(started && live, SourceWakePolicy.visible(started, live))
            }
        }
    }

    @Test fun realReaderLoopPreservesPositiveAndZeroReadsThenStopsWithoutAnotherRead() {
        var running = true
        val reads = ArrayDeque(listOf(0, 8, 16))
        val pushed = mutableListOf<Int>()
        readSourceSamples(
            readEpoch = { 0L },
            running = { running },
            read = { reads.removeFirst() },
            push = { count, _ -> pushed.add(count); if (count == 16) running = false },
            failed = { fail("unexpected failure", it) },
        )
        assertEquals(listOf(8, 16), pushed)
        assertTrue(reads.isEmpty())
    }

    @Test fun explicitStopDuringReadDiscardsTheLateSamples() {
        val owner = Owner()
        var running = true
        owner.wake.microphoneChanged(true, false)
        readSourceSamples(
            readEpoch = { 0L },
            running = { running },
            read = { running = false; owner.wake.microphoneChanged(false, false); 8 },
            push = { _, _ -> fail("stopped reader pushed samples") },
            failed = { fail("explicit stop is not a reader failure", it) },
        )
        assertFalse(owner.lock.isHeld)
        assertEquals(1, owner.lock.releases)
    }

    @Test fun negativeReadAndThrownErrorRetireCurrentMicAndCaptureExactlyOnce() {
        for (mic in listOf(false, true)) {
            for (throws in listOf(false, true)) {
                val owner = Owner()
                var running = true
                var reads = 0
                var stops = 0
                if (mic) owner.wake.microphoneChanged(true, false) else owner.wake.captureChanged(true, true)
                readSourceSamples(
                    readEpoch = { 0L },
                    running = { running },
                    read = { reads++; if (throws) throw SecurityException("revoked") else -6 },
                    push = { _, _ -> fail("error read pushed samples") },
                    failed = { retireSourceReaderFailure(
                        isCurrent = { running },
                        stop = { stops++; running = false; owner.wake.stop() },
                    ) },
                )
                assertEquals(1, reads)
                assertEquals(1, stops)
                assertFalse(owner.lock.isHeld)
                assertEquals(1, owner.lock.releases)
            }
        }
    }

    @Test fun delayedOldReaderFailureCannotStopOrClearAReplacementRecorder() {
        for (mic in listOf(false, true)) {
            val owner = Owner()
            val oldRecord = Any()
            var currentRecord: Any? = oldRecord
            var failureOnMain: (() -> Unit)? = null
            var stops = 0
            if (mic) owner.wake.microphoneChanged(true, false) else owner.wake.captureChanged(true, true)
            readSourceSamples(
                readEpoch = { 0L },
                running = { true },
                read = { -6 },
                push = { _, _ -> fail("error read pushed samples") },
                failed = { failureOnMain = { retireSourceReaderFailure(
                    isCurrent = { currentRecord === oldRecord },
                    stop = { stops++; owner.wake.stop() },
                ) } },
            )
            owner.wake.stop()
            currentRecord = Any()
            if (mic) owner.wake.microphoneChanged(true, false) else owner.wake.captureChanged(true, true)
            assertNotNull(failureOnMain).invoke()
            assertEquals(0, stops)
            assertTrue(owner.lock.isHeld)
            owner.wake.destroy()
        }
    }

    @Test fun stoppedReaderFailureCannotReopenOrRepeatRetirement() {
        var current = true
        var stops = 0
        val stop = { stops++; current = false }
        repeat(3) { retireSourceReaderFailure({ current }, stop) }
        assertEquals(1, stops)
    }

    @Test fun cancelledStartRequestDoesNotHideFailureOfTheStillCurrentRecorder() {
        val record = Any()
        val currentRecord = record
        var requestGeneration = 1
        val readerStartedAt = requestGeneration
        val owner = Owner()
        owner.wake.microphoneChanged(true, false)
        requestGeneration++ // cancelStart invalidates a request, not an already-running recorder.
        assertNotEquals(readerStartedAt, requestGeneration)
        retireSourceReaderFailure(
            isCurrent = { currentRecord === record },
            stop = { owner.wake.microphoneChanged(false, false) },
        )
        assertFalse(owner.lock.isHeld)
    }

    // These are independent reachability checks, not Android lifecycle or Compose execution.
    private fun source(path: String): String = sequenceOf(File("."), File("app")).map { File(it, path) }
        .first { it.isFile }.readText()

    @Test fun androidOwnerAdaptersReachTheExercisedPolicyAndPreserveRetirement() {
        val base = "src/main/kotlin/dev/phosphor/mobil3/"
        val activity = source(base + "MainActivity.kt")
        val playback = source(base + "PlaybackService.kt")
        val capture = source(base + "CaptureService.kt")
        val mic = source(base + "MicController.kt")
        assertTrue(activity.contains("private val mic = MicController { recording ->"))
        assertTrue(activity.contains("micWake.microphoneChanged(recording, activityDestroyed)"))
        assertFalse(activity.contains("micWake.microphoneChanged(ui.live"))
        assertTrue(activity.contains("scopeSurface?.keepScreenOn = awake"))
        assertTrue(activity.contains("else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
        for (callback in listOf("override fun onStart()", "override fun onStop()", "override fun onWindowFocusChanged(")) {
            assertTrue(activity.substringAfter(callback).substringBefore("\n    }").contains("reassertSourceWake()"))
        }
        assertTrue(activity.substringAfter("private val uiTick =").substringBefore("private fun handleIntent(")
            .contains("reassertSourceWake()"))
        assertTrue(playback.contains("override fun onEvents(player: Player, events: Player.Events)"))
        assertTrue(playback.contains("sourceWake.localChanged("))
        assertTrue(playback.contains("sourceWake.remoteChanged("))
        assertFalse(playback.contains("sourceWake.captureChanged("))
        assertTrue(playback.contains("beginShutdown(stopReaders = false)"))
        assertTrue(capture.contains("sourceWake.captureChanged(recording = running, projection = projection === proj)"))
        assertTrue(capture.contains("private fun finishCapture(reason: String, status: CaptureStatus, retry: Boolean = false) {\n        sourceWake.stop()"))
        assertTrue(mic.contains("running = false\n        onRecordingChanged(false)"))
        assertTrue(mic.contains("owner === this && record === rec && running"))
        assertTrue(capture.contains("owner === this && !cleanedUp && record === rec && running"))
        for (text in listOf(mic, capture)) {
            assertTrue(text.contains("readSourceSamples("))
            assertTrue(text.contains("retireSourceReaderFailure("))
            assertTrue(text.contains("ReaderStop.finish("))
        }
    }

    @Test fun manifestAndBackendDeclareOnlyTheNarrowSourceScreenLock() {
        val manifest = source("src/main/AndroidManifest.xml")
        val backend = source("src/main/kotlin/dev/phosphor/mobil3/SourceWakeLock.kt")
        assertEquals(1, Regex("android.permission.WAKE_LOCK").findAll(manifest).count())
        assertEquals(1, Regex("PowerManager.SCREEN_BRIGHT_WAKE_LOCK").findAll(backend).count())
        assertTrue(backend.contains("lock.setReferenceCounted(false)"))
        assertEquals(1, Regex("@Suppress\\(\"DEPRECATION\"\\)").findAll(backend).count())
        assertFalse(backend.contains("ACQUIRE_CAUSES_WAKEUP"))
    }
}
