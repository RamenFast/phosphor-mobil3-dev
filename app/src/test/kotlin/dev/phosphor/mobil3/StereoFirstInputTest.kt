package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.*

class StereoFirstInputTest {
    @Test fun serviceGetterReadsTheLiveRecorderAndKeepsRetirementSnapshots() {
        val path = "src/main/kotlin/dev/phosphor/mobil3/MicCaptureService.kt"
        val source = listOf(java.io.File(path), java.io.File("app/$path")).first { it.isFile }.readText()
        val getter = source.substringAfter("internal fun signalObservation(): SignalInput?").substringBefore("internal fun status()")
        assertTrue(getter.contains("val fresh = recorder.observation()"))
        assertTrue(getter.contains("current.stopping || current.destroyed"))
        assertTrue(getter.contains("owner === current && current.recorder === recorder"))
        assertFalse(getter.contains("publish()"))
        assertFalse(getter.contains("start("))
    }
    @Test fun sameOwnerFormatChangesRetireQueuedDiagnosticReads() {
        val owner = SignalRecorderWindow(7)
        val first = SignalDescriptor(SignalFormat(48_000, 2, "PCM16"), "route", 100,
            deviceFormat = SignalFormat(48_000, 2, "PCM16"))
        owner.observe(first)
        val oldRead = owner.token()
        owner.floats(oldRead, floatArrayOf(.25f, -.5f), 2, 100)
        assertEquals(1L, owner.snapshot().second!!.stereo!!.nonSilentPairs)
        owner.observe(first.copy(observedAt = 200))
        assertSame(oldRead, owner.token(), "timestamps do not invent format changes")
        owner.observe(first.copy(deviceFormat = SignalFormat(48_000, 1, "PCM16")))
        assertNotSame(oldRead, owner.token())
        owner.floats(oldRead, floatArrayOf(.25f, -.5f), 2, 210)
        assertNull(owner.snapshot().second!!.stereo, "old read cannot populate the new format")
        owner.floats(owner.token(), floatArrayOf(.25f, .25f), 2, 220)
        assertEquals(SignalStereo(1, 1, 0.0), owner.snapshot().second!!.stereo)
    }
    @Test fun allBoundedStereoFormatsPrecedeMonoAndRecoveryRemains() {
        val formats = MicrophoneRoutePolicy.candidates(intArrayOf(), intArrayOf(), intArrayOf())
        val rates = listOf(48_000, 44_100, 16_000, 8_000)
        val stereo = rates.flatMap { rate -> listOf(true, false).map { MicrophoneRoutePolicy.Format(rate, 2, it) } }
        val mono = rates.take(2).flatMap { rate -> listOf(true, false).map { MicrophoneRoutePolicy.Format(rate, 1, it) } }
        assertEquals(stereo + mono, formats)
        assertEquals(12, formats.size)
        assertTrue(formats.last().channels == 1 && !formats.last().floating)
    }

    @Test fun advertisementsBoundStereoFirstWithoutInventingFormats() {
        val rates = intArrayOf(8_000, 11_025, 12_000, 16_000, 22_050, 24_000, 32_000, 44_100, 48_000)
        val expected = listOf(2, 1).flatMap { channels -> listOf(48_000, 44_100, 16_000, 8_000).map {
            MicrophoneRoutePolicy.Format(it, channels, false)
        } }
        assertEquals(expected, MicrophoneRoutePolicy.candidates(rates, intArrayOf(1, 2), intArrayOf(2)))
        assertEquals(listOf(MicrophoneRoutePolicy.Format(8_000, 1, false)),
            MicrophoneRoutePolicy.candidates(intArrayOf(8_000), intArrayOf(1), intArrayOf(2)))
        assertEquals(listOf(MicrophoneRoutePolicy.Format(96_000, 2, true)),
            MicrophoneRoutePolicy.candidates(intArrayOf(96_000), intArrayOf(2), intArrayOf(4)))
        assertTrue(MicrophoneRoutePolicy.candidates(intArrayOf(4_000), intArrayOf(2), intArrayOf(2)).isEmpty())
        assertTrue(MicrophoneRoutePolicy.candidates(intArrayOf(48_000), intArrayOf(4), intArrayOf(2)).isEmpty())
        assertTrue(MicrophoneRoutePolicy.candidates(intArrayOf(48_000), intArrayOf(2), intArrayOf(99)).isEmpty())
        assertTrue(MicrophoneRoutePolicy.candidates(IntArray(1000) { 8_000 + it }, intArrayOf(), intArrayOf()).size <= 12)
    }

    @Test fun pairedWindowsDistinguishSilenceDuplicationAndDifferencesWithoutAudioHistory() {
        val meter = SignalAggregate(7, 2)
        meter.floats(floatArrayOf(0f, 0f, .25f, .25f, -.5f, -.5f), 6, 100)
        assertEquals(SignalStereo(2, 2, 0.0), meter.latest.stereo)
        meter.floats(floatArrayOf(.25f, -.5f, -.25f, .5f), 4, 600)
        assertEquals(2L, meter.latest.stereo!!.nonSilentPairs)
        assertEquals(0L, meter.latest.stereo!!.identicalPairs)
        assertEquals(.75, meter.latest.stereo!!.differenceRms, 1e-12)
        meter.floats(floatArrayOf(0f, 0f), 2, 1100)
        assertEquals(SignalStereo(0, 0, 0.0), meter.latest.stereo)
        val mono = SignalAggregate(8, 1)
        mono.floats(floatArrayOf(.25f), 1, 100)
        assertNull(mono.latest.stereo)
        val invalid = SignalAggregate(9, 2)
        invalid.floats(floatArrayOf(Float.NaN, .25f), 2, 100)
        assertNull(invalid.latest.stereo)
    }

    @Test fun clientDeviceAndPhysicalTruthStaySeparateAndExpireWithOwner() {
        val meter = SignalAggregate(7, 2)
        meter.floats(floatArrayOf(.25f, -.5f), 2, 100)
        val descriptor = SignalDescriptor(SignalFormat(48_000, 2, "PCM16"), "actual route", 100,
            deviceFormat = SignalFormat(48_000, 1, "PCM16"), requestedRoute = "selected input")
        val source = SignalInput(SignalKind.MIC, 7, life = SignalLife.RUNNING, descriptor = descriptor, window = meter.latest)
        val display = SignalDisplay(false, false, false, false)
        fun rows(input: SignalInput, now: Long = 100) = SignalPresentation.present(SignalKind.MIC, input, now, display).rows.toMap()
        assertEquals("48000 Hz · stereo · PCM16", rows(source)["Recorder client format"])
        assertEquals("48000 Hz · mono · PCM16", rows(source)["Platform device format"])
        assertEquals("selected input", rows(source)["Requested mic route"])
        assertEquals("actual route", rows(source)["Actual mic route"])
        assertTrue(rows(source)["Channel origin"]!!.contains("platform duplication possible"))
        assertTrue(rows(source)["Measured L/R"]!!.contains("0/1 identical"))
        val stereoDevice = source.copy(descriptor = descriptor.copy(deviceFormat = SignalFormat(48_000, 2, "PCM16")))
        assertTrue(rows(stereoDevice)["Channel origin"]!!.contains("independence unproven"))
        assertTrue(rows(source, 2000)["Measured L/R"]!!.startsWith("Unavailable"))
        assertTrue(rows(source.copy(owner = 8, session = 8))["Measured L/R"]!!.startsWith("Unavailable"))
        val mono = source.copy(descriptor = descriptor.copy(format = SignalFormat(48_000, 1, "PCM16")))
        assertTrue(rows(mono)["Channel origin"]!!.startsWith("Mono client"))
    }

    @Test fun microphoneAndMixedRenderKeepTheTwoChannelsAtNativeAndResampledRates() {
        for (rate in listOf(44_100, 48_000)) for (micOnly in listOf(true, false)) {
            val core = CaptureMixCore(micOnly)
            core.epoch(3)
            core.attachment(8, true)
            val format = CapturePcm(rate, 2)
            val microphone = FloatArray(rate / 5) { if (it % 2 == 0) .25f else -.5f }
            assertTrue(core.offer(true, microphone, microphone.size, format, 0,
                CaptureClock(0, 1_000_000_000), 1_100_000_000, 8, 3))
            if (!micOnly) {
                val playback = FloatArray(9600) { if (it % 2 == 0) .5f else -.25f }
                assertTrue(core.offer(false, playback, playback.size, CapturePcm(48_000, 2), 0,
                    CaptureClock(0, 1_000_000_000), 1_100_000_000, 0, 3))
            }
            val output = FloatArray(960)
            repeat(3) { core.render(1_030_000_000 + it * 10_000_000L, output) }
            val left = if (micOnly) .25f else .375f
            val right = if (micOnly) -.5f else -.375f
            for (i in output.indices step 2) {
                assertEquals(left, output[i], 0.0001f, "rate=$rate micOnly=$micOnly left")
                assertEquals(right, output[i + 1], 0.0001f, "rate=$rate micOnly=$micOnly right")
            }
        }
    }
}
