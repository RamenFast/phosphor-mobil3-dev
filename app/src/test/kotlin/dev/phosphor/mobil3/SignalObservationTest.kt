package dev.phosphor.mobil3

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SignalObservationTest {
    @Test fun actualRootNormalizerPreservesRawMonoProvenanceAcrossEpochRejection() {
        for (n in listOf(1, 160)) {
            val raw = ShortArray(n) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
            val meter = SignalAggregate(9, 1)
            meter.pcm16(raw, 100)
            val normalizer = RootEpochNormalizer(9)
            val batch = RootAudioProtocol.PcmBatch(raw, 1, 1, 3)
            assertNull(normalizer.convert(batch, 4))
            assertEquals(n.toLong(), meter.latest.ingressFrames)
            val normalized = normalizer.convert(batch, 3)!!
            assertEquals(n * 3, normalized.size / 2)
            for (i in normalized.indices step 2) assertEquals(normalized[i], normalized[i + 1], 0f)
            assertEquals(n.toLong(), meter.latest.channels.single().fullScale)
        }
    }

    @Test fun AndroidSourceAssertionsSupplementThePureBehaviorTests() {
        fun source(path: String) = listOf(java.io.File(path), java.io.File("../$path"))
            .first { it.isFile }.readText()
        val base = "app/src/main/kotlin/dev/phosphor/mobil3/"
        val activity = source(base + "MainActivity.kt")
        assertEquals(1, Regex("PhosphorNative\\.scopeStats\\(").findAll(activity).count())
        val join = activity.substringAfter("private fun refreshSignalCheck()").substringBefore("private fun selectSource")
        for (forbidden in listOf("startService(", "startCapture(", "startMic(", ".launch(", "remoteConnect(", "setRingActive(", "scopeStats("))
            assertFalse(forbidden, join.contains(forbidden))
        val ui = source(base + "ui/SignalCheckSheet.kt")
        for (forbidden in listOf("PhosphorNative", "startCapture(", "startMic(", ".launch(", "remoteConnect(")) assertFalse(ui.contains(forbidden))
        assertTrue(ui.contains("heightIn(min = 48.dp)"))
        assertTrue(ui.contains("onDispose { state.signalCheckVisible = false }"))
        val native = source("rust/src/jni_glue.rs").substringAfter("fn Java_dev_phosphor_mobil3_PhosphorNative_signalObservation").substringBefore("#[unsafe(no_mangle)]")
        assertFalse(native.contains("take_stereo_stats"))
        assertTrue(native.contains("try_lock"))
        val remote = source("rust/src/remote.rs")
        val callback = remote.substringAfter("impl AudioOutputCallback for RemoteOutput").substringBefore("fn on_error_after_close")
        for (forbidden in listOf(".lock(", "serde_json", "vec![", "Vec::", "monotonic_ms()")) assertFalse(forbidden, callback.contains(forbidden))
        assertTrue(remote.contains("signal_id: SIGNAL_SESSIONS"))
        assertTrue(remote.contains("signal.try_lock()"))
    }

    private fun meter(values: FloatArray, at: Long = 100) = SignalAggregate(7, 2).also { it.floats(values, values.size, at) }
    private fun input(window: SignalWindow? = null, life: SignalLife = SignalLife.RUNNING) =
        SignalInput(SignalKind.MIC, 7, life = life, window = window, contributing = true)

    @Test fun zeroReadIsProgressNotMeasuredSilence() {
        val meter = meter(floatArrayOf())
        assertEquals(1L, meter.latest.reads)
        assertEquals(0L, meter.latest.ingressFrames)
        assertNull(meter.latest.lastPositiveAt)
        assertTrue(meter.latest.channels.isEmpty())
        assertEquals("No samples observed · read loop progresses", SignalPresentation.primary(SignalKind.MIC, input(meter.latest), 100))
    }

    @Test fun positiveExactZerosAreMeasuredSilence() {
        val meter = meter(floatArrayOf(0f, -0f, 0f, 0f))
        assertEquals(2L, meter.latest.ingressFrames)
        assertEquals(2L, meter.latest.validFrames)
        assertEquals(0.0, meter.latest.channels[0].rms, 0.0)
        assertEquals("Measured silence · samples arriving", SignalPresentation.primary(SignalKind.MIC, input(meter.latest), 101))
    }

    @Test fun nonFinitePairsAndPartialFramesAreUnavailable() {
        val meter = meter(floatArrayOf(Float.NaN, 0f, 0f, Float.POSITIVE_INFINITY, 1f))
        assertEquals(5L, meter.latest.invalidSamples)
        assertTrue(meter.latest.channels.isEmpty())
        assertEquals("Samples arriving · level unavailable", SignalPresentation.primary(SignalKind.MIC, input(meter.latest), 100))
    }

    @Test fun unequalChannelsRetainFiniteRmsPeakAndRails() {
        val meter = meter(floatArrayOf(1f, .25f, -1f, -.25f))
        assertEquals(1.0, meter.latest.channels[0].rms, 0.0)
        assertEquals(.25, meter.latest.channels[1].peak, 0.0)
        assertEquals(2L, meter.latest.channels[0].fullScale)
        assertEquals(0L, meter.latest.channels[1].fullScale)
        assertEquals("Signal flowing · full-scale samples", SignalPresentation.primary(SignalKind.MIC, input(meter.latest), 100))
    }

    @Test fun pcm16BothRailsCountBeforeNormalization() {
        val meter = SignalAggregate(8, 1)
        meter.pcm16(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), 100)
        assertEquals(2L, meter.latest.channels.single().fullScale)
        assertEquals(2L, meter.latest.ingressFrames)
        assertEquals(1.0, meter.latest.channels.single().peak, 0.0)
        meter.pcm16(ShortArray(160), 600)
        assertEquals(162L, meter.latest.ingressFrames)
        assertEquals(160L, meter.latest.validFrames)
        assertEquals(0L, meter.latest.channels.single().fullScale)
    }

    @Test fun boundedWindowResetsWithoutResettingLifetimeReceipt() {
        val meter = meter(floatArrayOf(1f, 1f), 0)
        meter.floats(floatArrayOf(.25f, .5f), 2, 499)
        assertEquals(2L, meter.latest.validFrames)
        meter.floats(floatArrayOf(0f, 0f), 2, 500)
        assertEquals(1L, meter.latest.validFrames)
        assertEquals(3L, meter.latest.ingressFrames)
        assertEquals(0.0, meter.latest.channels[0].peak, 0.0)
        val first = meter.latest
        assertSame(first, meter.latest)
        assertEquals(Long.MAX_VALUE, signalAdd(Long.MAX_VALUE - 1, 2))
    }

    @Test fun ownerReplacementDiscardsOldLevelsAndClockRegressionIsUnavailable() {
        val old = meter(floatArrayOf(1f, 1f)).latest
        val next = input(old).copy(owner = 8, session = 8)
        assertEquals("Starting · waiting for input", SignalPresentation.primary(SignalKind.MIC, next, 100))
        assertNull(signalAge(99, 100))
        val view = SignalPresentation.present(SignalKind.CAPTURE, input(old), 100, SignalDisplay(false, false, false, false))
        assertTrue(view.status.contains("source changing"))
        assertFalse(view.rows.any { it.second.contains("1.00000") })
    }

    @Test fun thirteenPrecedenceRowsAndStaleFallback() {
        val flowing = input(meter(floatArrayOf(.5f, .25f)).latest)
        fun primary(value: SignalInput?, now: Long = 100, selected: SignalKind = SignalKind.MIC, current: Boolean = true, consent: Boolean = false) =
            SignalPresentation.primary(selected, value, now, current, consent)
        assertEquals("Observation unavailable · source changing", primary(flowing, current = false))
        assertEquals("No source", primary(null, selected = SignalKind.NONE))
        assertEquals("Reader failed", primary(flowing.copy(life = SignalLife.FAILED, reason = "Reader failed"), now = 9000, consent = true))
        assertEquals("Waiting for consent", primary(null, consent = true))
        assertEquals("Stopping", primary(flowing.copy(life = SignalLife.STOPPING)))
        assertEquals("Cleanup unconfirmed", primary(flowing.copy(life = SignalLife.CLEANUP_UNCONFIRMED)))
        assertEquals("Reconnecting", primary(flowing.copy(life = SignalLife.RECONNECTING)))
        assertEquals("Disconnected", primary(flowing.copy(life = SignalLife.DISCONNECTED)))
        assertEquals("Input ended", primary(flowing.copy(life = SignalLife.ENDED)))
        assertEquals("Starting · waiting for input", primary(input(life = SignalLife.STARTING)))
        assertEquals("No recent input · read loop progresses", primary(flowing.copy(progressAt = 2000), now = 2000))
        assertEquals("Reader / link stalled", primary(flowing.copy(life = SignalLife.STALLED)))
        assertEquals("Samples arriving · level unavailable", primary(input(meter(floatArrayOf(Float.NaN, 0f)).latest)))
        assertEquals("Measured silence · samples arriving", primary(input(meter(floatArrayOf(0f, 0f)).latest)))
        assertEquals("Signal flowing · full-scale samples", primary(input(meter(floatArrayOf(1f, 0f)).latest)))
        assertEquals("Signal flowing", primary(flowing))
        assertEquals("Stale measurement · reader health unavailable", primary(flowing, now = 9000))
    }

    @Test fun displayIsIndependentAndBlackWinsWithoutHeldImage() {
        val states = listOf(
            SignalDisplay(true, true, false, false) to "Display black-on-pause",
            SignalDisplay(true, false, true, false) to "Display held",
            SignalDisplay(true, false, false, false) to "No held frame",
            SignalDisplay(false, false, false, true) to "Waiting for new frame",
            SignalDisplay(false, false, false, false) to "Live presentation")
        states.forEach { (display, expected) ->
            assertEquals(expected, SignalPresentation.display(display))
            val view = SignalPresentation.present(SignalKind.MIC, input().copy(life = SignalLife.FAILED, reason = "Route lost"), 100, display)
            assertEquals("Route lost", view.status)
        }
    }

    @Test fun rootFormatNeverBecomesOriginalStereoOrReconstructionRate() {
        val root = SignalInput(SignalKind.ROOT, 1, descriptor = SignalDescriptor(SignalFormat(16000, 1, "PCM16")),
            normalized = "48,000 Hz · float · duplicated mono")
        val view = SignalPresentation.present(SignalKind.ROOT, root, 100, SignalDisplay(true, false, true, false))
        assertEquals("16000 Hz · mono · PCM16", view.rows.toMap()["Observed input"])
        assertTrue(view.rows.toMap()["Normalized transport"]!!.contains("duplicated mono"))
        assertFalse(view.rows.any { it.second.contains("192000") })
    }

    @Test fun visibilityPermitsAtMostTwoReadsPerSecondAndNeverActions() {
        val refresh = SignalRefreshOwner()
        assertFalse(refresh.take(0, false, true, true, true, false))
        assertTrue(refresh.take(0, true, true, true, true, false))
        assertFalse(refresh.take(249, true, true, true, true, false))
        assertFalse(refresh.take(499, true, true, true, true, false))
        assertTrue(refresh.take(500, true, true, true, true, false))
        for (hidden in listOf(listOf(false, true, true, true, false), listOf(true, false, true, true, false),
            listOf(true, true, false, true, false), listOf(true, true, true, false, false), listOf(true, true, true, true, true))) {
            assertFalse(refresh.take(1000, hidden[0], hidden[1], hidden[2], hidden[3], hidden[4]))
        }
        assertEquals(0, SignalRefreshOwner::class.java.declaredFields.count { it.type.name.contains("Function") })
    }

    private fun playback(kind: SignalKind, life: SignalLife = SignalLife.RUNNING) =
        SignalPlayback(kind, 1, life, "", true, null, null, localOpen = 1)

    @Test fun localOutputProgressRequiresDeltaAndFencesReplacementAndRegression() {
        val join = SignalNativeObservation()
        fun native(id: Long, frames: Long) = JSONObject("""{"local":{"open_id":$id,"output":{"popped_stereo_frames":$frames}}}""")
        assertNull(join.local(native(1, 100), playback(SignalKind.LOCAL), 100)?.receiptAt)
        assertEquals(100L, join.local(native(1, 200), playback(SignalKind.LOCAL), 600)?.receiptAt)
        assertNull(join.local(native(2, 400), playback(SignalKind.LOCAL).copy(localOpen = 2), 1100)?.receiptAt)
        assertNull(join.local(native(2, 2), playback(SignalKind.LOCAL).copy(localOpen = 2), 1600)?.receiptAt)
        val failed = join.local(native(2, 20), playback(SignalKind.LOCAL, SignalLife.ENDED).copy(localOpen = 2), 2100)!!
        assertEquals("Input ended", SignalPresentation.primary(SignalKind.LOCAL, failed, 2100))
    }

    @Test fun relaySessionGeometryAndMissingRmsKeepProvenance() {
        val join = SignalNativeObservation()
        val reading = playback(SignalKind.RELAY).copy(relaySession = 3,
            link = RemoteLinkReading(RemoteLinkState.STREAMING, ""), linkAt = 1000)
        val native = JSONObject("""{"relay":{"session":3,"geometry_points":80,"geometry_age_ms":5,
            "input":{"input_stereo_frames":0,"valid_frames":0,"invalid_samples":0,"channels":null}}}""")
        val current = join.relay(native, reading, 1000)!!
        assertEquals(995L, current.receiptAt)
        assertEquals("observed valid geometry points (not PCM samples)", current.receiptUnit)
        assertTrue(current.window!!.channels.isEmpty())
        assertEquals("Samples arriving · level unavailable", SignalPresentation.primary(SignalKind.RELAY, current, 1000))
        assertTrue(join.details(native, current, reading, 1000).toMap()["Relay-reported RMS"]!!.contains("Unavailable"))
        assertFalse(join.relay(native, reading.copy(relaySession = 4), 1000)!!.contributing)
        assertEquals(SignalLife.FAILED, join.relay(native, reading.copy(life = SignalLife.FAILED), 1000)!!.life)
    }

    @Test fun hiddenLocalIncrementDoesNotBecomeFreshWhenTheViewReturns() {
        val join = SignalNativeObservation()
        fun native(frames: Int) = JSONObject("""{"local":{"open_id":1,"output":{"popped_stereo_frames":$frames}}}""")
        val reading = playback(SignalKind.LOCAL)
        join.local(native(100), reading, 100)
        val resumed = join.local(native(200), reading.copy(intent = false), 60000)!!
        assertNull(resumed.receiptAt)
        assertFalse(SignalPresentation.primary(SignalKind.LOCAL, resumed, 60000).contains("arriving"))
        assertNull(join.local(native(200), reading, 60500)?.receiptAt)
        val positive = join.local(native(201), reading, 61000)!!
        assertEquals(60500L, positive.receiptAt)
        val view = SignalPresentation.present(SignalKind.LOCAL, positive, 61000, SignalDisplay(false, false, false, false))
        assertTrue(view.rows.toMap().containsKey("Positive receipt age upper bound"))
        assertFalse(view.rows.toMap().containsKey("Last positive receipt"))
        assertTrue(view.status.contains("arriving"))
    }

    @Test fun failedLocalSnapshotsAndBackwardsClocksInvalidateProgressComparison() {
        val join = SignalNativeObservation()
        fun native(frames: Int) = JSONObject("""{"local":{"open_id":1,"output":{"popped_stereo_frames":$frames}}}""")
        val reading = playback(SignalKind.LOCAL)
        join.local(native(100), reading, 100)
        assertEquals(100L, join.local(native(200), reading, 600)?.receiptAt)
        assertNull(join.local(null, reading, 700)?.receiptAt)
        assertNull(join.local(native(300), reading, 800)?.receiptAt)
        assertNull(join.local(native(301), reading, 799)?.receiptAt)
        assertNull(join.local(native(302), reading, 799)?.receiptAt)
    }

    @Test fun rootPcmAndProgressCountAreReceivedObservationsNotCompletedReads() {
        val meter = SignalAggregate(9, 1)
        meter.pcm16(shortArrayOf(100), 100)
        meter.progress(100)
        val root = SignalInput(SignalKind.ROOT, 9, life = SignalLife.RUNNING, window = meter.latest,
            readUnit = "received PCM/progress observations (not AudioRecord read calls)")
        val rows = SignalPresentation.present(SignalKind.ROOT, root, 100, SignalDisplay(false, false, false, false)).rows.toMap()
        assertTrue(rows["Input receipt"]!!.contains("2 received PCM/progress observations"))
        assertFalse(rows["Input receipt"]!!.contains("completed reads"))
        assertTrue(rows.containsKey("Last received PCM / progress observation"))
    }

    @Test fun pickerAndTapAdaptersPreserveSourceTruthWithoutAnotherRead() {
        fun source(path: String) = listOf(java.io.File(path), java.io.File("../$path")).first { it.isFile }.readText()
        val activity = source("app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt")
        for ((start, end) in listOf("override fun openFile()" to "override fun exportSettings", "override fun openFolder()" to "override fun jumpToQueue")) {
            val body = activity.substringAfter(start).substringBefore(end)
            assertTrue(body.contains("selectSource(signalSelected)"))
            assertFalse(body.contains("selectSource(SignalKind.LOCAL)"))
        }
        val folder = activity.substringAfter("private val openFolderLauncher").substringBefore("private val createSettingsArchive")
        assertTrue(folder.indexOf("uri ?: return") < folder.indexOf("selectSource(SignalKind.LOCAL)"))
        val file = activity.substringAfter("private fun loadUri(").substringBefore("private fun")
        assertTrue(file.contains("selectSource(SignalKind.LOCAL)"))
        val join = activity.substringAfter("private fun refreshSignalCheck()").substringBefore("private fun selectSource")
        assertFalse(join.contains("ui.gridReading"))
        assertTrue(join.contains("no owner and measurement-age receipt"))
        val root = source("app/src/main/kotlin/dev/phosphor/mobil3/RootCaptureSession.kt")
        assertTrue(root.contains("readUnit = \"received PCM/progress observations"))
        assertEquals(1, Regex("PhosphorNative\\.scopeStats\\(").findAll(activity).count())
    }

    @Test fun unmatchedInputCannotInheritOldTransportOrPeakExtras() {
        val pending = SignalPresentation.present(SignalKind.LOCAL, null, 100, SignalDisplay(false, false, false, false),
            extra = listOf("Old capture peak" to "1.0", "Old transport" to "paused"))
        assertFalse(pending.rows.any { it.first.startsWith("Old") })
        val oldCapture = playback(SignalKind.CAPTURE).copy(transport = false, transportAt = 100)
        val join = SignalNativeObservation()
        val rows = join.details(JSONObject("{}"), input(), oldCapture, 100).toMap()
        assertEquals("Unavailable", rows["Transport intent"])
        assertTrue(rows["Observed external transport"]!!.startsWith("Unavailable"))
        val local = SignalInput(SignalKind.LOCAL, 2)
        assertEquals("Unavailable", join.details(JSONObject("{}"), local, playback(SignalKind.LOCAL), 100).toMap()["Transport intent"])
    }

    @Test fun observerRunsAfterRetirementFenceAndZeroReadsAreObservedOnce() {
        var running = true
        var observations = 0
        var pushes = 0
        readSourceSamples({ running }, { 1 }, { running = false; 2 }, { _, _ -> pushes++ }, { throw it }, { observations++ })
        assertEquals(0, observations)
        assertEquals(0, pushes)
        running = true
        readSourceSamples({ running }, { 1 }, { 0 }, { _, _ -> pushes++ }, { throw it }, { observations++; running = false })
        assertEquals(1, observations)
        assertEquals(0, pushes)
    }

    @Test fun partialNativeSnapshotCannotFabricateZeroOrAnUnmutedOutput() {
        val join = SignalNativeObservation()
        val native = JSONObject("""{"relay":{"session":3,"input":{}}}""")
        val reading = playback(SignalKind.RELAY).copy(relaySession = 3,
            link = RemoteLinkReading(RemoteLinkState.STREAMING, ""), linkAt = 100)
        val relayInput = join.relay(native, reading, 100)!!
        assertNull(relayInput.window)
        assertNull(relayInput.receiptCount)
        assertTrue(join.details(native, relayInput, reading, 100).toMap()["Relay output policy"]!!.contains("Unavailable"))
        val healthy = input(meter(floatArrayOf(.5f, .25f)).latest)
        val failed = SignalInput(SignalKind.CAPTURE, 9, life = SignalLife.FAILED, reason = "Reader failed")
        assertEquals("Signal flowing", SignalPresentation.primary(SignalKind.MIC, healthy, 100))
        assertEquals("Reader failed", SignalPresentation.primary(SignalKind.CAPTURE, failed, 100))
    }
}
