package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.*
import kotlin.math.*

class CaptureMixPolicyTest {
    private val stereo = CapturePcm(48_000, 2)
    private fun input(rate: Int = 48_000, channels: Int = 2, frames: Int = rate / 10,
                      value: (Int, Int) -> Float = { _, _ -> .5f }): Pair<CaptureMixInput, FloatArray> {
        val data = FloatArray(frames * channels) { value(it / channels, it % channels) }
        val input = CaptureMixInput()
        input.offer(data, data.size, CapturePcm(rate, channels), 0, CaptureClock(0, 1_000_000_000),
            1_100_000_000, 1, 3)
        return input to data
    }
    @Test fun privateSelectionRejectsSinksMissingAndAmbiguousIdentity() {
        for (type in listOf(3, 7, 11, 12, 15, 22)) {
            assertTrue(MicrophoneRoutePolicy.supported(true, type, 29))
            assertFalse(MicrophoneRoutePolicy.supported(false, type, 36))
        }
        assertFalse(MicrophoneRoutePolicy.supported(true, 26, 30))
        assertTrue(MicrophoneRoutePolicy.supported(true, 26, 31))
        assertFalse(MicrophoneRoutePolicy.supported(true, 8, 36))
        val selected = MicrophoneChoice(7, 22, "USB", "port-A")
        assertNull(MicrophoneRoutePolicy.select(listOf(selected.copy(id = 8), selected.copy(id = 9)), selected.key))
        assertNull(MicrophoneRoutePolicy.select(listOf(MicrophoneChoice(1, 15, "Built-in")), selected.key))
        assertEquals(8, MicrophoneRoutePolicy.select(listOf(selected.copy(id = 8)), selected.key)?.id)
        assertFalse(MicrophoneRoutePolicy.routed(7, listOf(7, 9)))
        assertFalse(MicrophoneRoutePolicy.routed(7, emptyList()))
        assertTrue(MicrophoneRoutePolicy.routed(7, listOf(7)))
    }
    @Test fun duplicateBuiltInNamesKeepDistinctIds() {
        val a = MicrophoneChoice(3, 15, "ASUS_AI2202")
        val b = MicrophoneChoice(9, 15, "ASUS_AI2202")
        val out = MicrophoneRoutePolicy.disambiguate(listOf(a, b))
        assertEquals(2, out.map { it.label }.toSet().size)
        assertEquals(setOf(3, 9), out.map { it.id }.toSet())
        assertEquals(a.key, out.single { it.id == 3 }.key)
        assertEquals(a.name, out.single { it.id == 3 }.name)
        assertEquals("3", out.single { it.id == 3 }.displayTag)
        // Empty addresses still share one identity key. Display suffix must not invent a second key.
        assertNull(MicrophoneRoutePolicy.select(out, a.key))
        assertEquals(3, MicrophoneRoutePolicy.select(out, null)?.id)
        val front = MicrophoneChoice(3, 15, "ASUS_AI2202", "bottom")
        val back = MicrophoneChoice(9, 15, "ASUS_AI2202", "back")
        val raw = listOf(front, back)
        val split = MicrophoneRoutePolicy.disambiguate(raw)
        assertEquals(2, split.map { it.label }.toSet().size)
        assertEquals(front.key, split.single { it.id == 3 }.key)
        assertEquals(front.name, split.single { it.id == 3 }.name)
        assertEquals("3", split.single { it.id == 3 }.displayTag)
        assertEquals(3, MicrophoneRoutePolicy.select(split, front.key)?.id)
        assertEquals(3, MicrophoneRoutePolicy.select(raw, split.single { it.id == 3 }.key)?.id)
        assertEquals(9, MicrophoneRoutePolicy.select(raw, split.single { it.id == 9 }.key)?.id)
        assertTrue(split.any { it.label.endsWith("(3)") })
        assertTrue(split.any { it.label.endsWith("(9)") })
        val unique = MicrophoneRoutePolicy.disambiguate(
            listOf(front, MicrophoneChoice(4, 3, "Headset", "port")),
        )
        assertTrue(unique.all { it.displayTag.isEmpty() })
        assertEquals(front.key, unique.single { it.id == 3 }.key)
        assertEquals(3, MicrophoneRoutePolicy.select(listOf(front), unique.single { it.id == 3 }.key)?.id)
    }
    @Test fun candidateTrialsAreFiniteAndKeepMonoPcm16() {
        val any = MicrophoneRoutePolicy.candidates(intArrayOf(), intArrayOf(), intArrayOf())
        assertTrue(any.size <= 12)
        assertTrue(any.any { it.channels == 1 && !it.floating })
        assertEquals(listOf(MicrophoneRoutePolicy.Format(8_000, 1, false)),
            MicrophoneRoutePolicy.candidates(intArrayOf(8_000), intArrayOf(1), intArrayOf(2)))
        assertTrue(MicrophoneRoutePolicy.candidates(intArrayOf(4_000), intArrayOf(1), intArrayOf(2)).isEmpty())
    }
    @Test fun defaultsAreOffAndFiniteAndIndependent() {
        assertEquals(CaptureMixSettings(), CaptureMixSettings.read(emptyMap<String, Any>()))
        assertEquals(1f, CaptureMixSettings.read(mapOf(CaptureMixSettings.MICROPHONE to Float.NaN)).microphone)
        assertEquals(0f, CaptureMixSettings.read(mapOf(CaptureMixSettings.PLAYBACK to 0f)).playback)
    }
    @Test fun invalidFramesRatesAndBoundsFailBeforeCopy() {
        assertFailsWith<IllegalArgumentException> { CapturePcm(0, 2) }
        assertFailsWith<IllegalArgumentException> { CapturePcm(48_000, 3) }
        val input = CaptureMixInput()
        for (count in listOf(-1, 1, 3)) assertFailsWith<IllegalArgumentException> {
            input.offer(FloatArray(2), count, stereo, 0, null, 1, 1, 1)
        }
        assertFailsWith<IllegalArgumentException> { input.offer(FloatArray(20_000), 20_000, stereo, 0, null, 1, 1, 1) }
    }
    @Test fun oldInputAndVisualEpochCannotPublishOrLeakFilterHistory() {
        val core = CaptureMixCore(false)
        core.epoch(3); core.attachment(8, true)
        val chunk = FloatArray(960) { .5f }
        assertFalse(core.offer(true, chunk, 960, stereo, 0, null, 100, 7, 3))
        assertTrue(core.offer(true, chunk, 960, stereo, 0, null, 100, 8, 3))
        core.epoch(4)
        assertFalse(core.offer(true, chunk, 960, stereo, 480, null, 200, 8, 3))
        assertEquals(0, core.microphone.queuedFrames)
        val out = FloatArray(960)
        assertEquals(4, core.render(100, out))
        assertTrue(out.all { it == 0f })
        core.attachment(9, false)
        assertFalse(core.offer(true, chunk, 960, stereo, 960, null, 300, 8, 4))
    }
    @Test fun stalePreReadOfferMustNotRewindMixerEpochOrDropCurrentQueues() {
        val core = CaptureMixCore(false)
        core.epoch(6)
        val chunk = FloatArray(960) { .5f }
        assertTrue(core.offer(false, chunk, 960, stereo, 0, CaptureClock(0, 1_000_000_000), 1_100_000_000, 0, 6))
        val queued = core.playback.queuedFrames
        assertTrue(queued > 0)
        assertFalse(core.offer(false, chunk, 960, stereo, 480, CaptureClock(480, 1_110_000_000), 1_200_000_000, 0, 5))
        assertEquals(queued, core.playback.queuedFrames)
        val out = FloatArray(960)
        assertEquals(6, core.render(1_050_000_000, out))

    }
    @Test fun monoAndTrueStereoRemainDistinct() {
        val (mono) = input(16_000, 1)
        for (ch in 0..1) assertEquals(.5f, mono.sample(1_050_000_000, ch)!!, 1e-5f)
        val (stereo) = input { _, ch -> if (ch == 0) .25f else -.5f }
        assertEquals(.25f, stereo.sample(1_050_000_000, 0))
        assertEquals(-.5f, stereo.sample(1_050_000_000, 1))
    }
    @Test fun allSupportedRatesAreChunkInvariant() {
        for (rate in listOf(8_000, 16_000, 44_100, 48_000, 96_000)) {
            val (whole, data) = input(rate, 1) { frame, _ -> sin(2 * PI * 440 * frame / rate).toFloat() }
            val split = CaptureMixInput()
            val half = data.size / 2
            for (part in 0..1) {
                val values = data.copyOfRange(part * half, if (part == 0) half else data.size)
                split.offer(values, values.size, CapturePcm(rate, 1), (part * half).toLong(), CaptureClock(0, 1_000_000_000), 1_100_000_000, 1, 3)
            }
            repeat(100) { i ->
                val at = 1_030_000_000L + i * 100_000L
                assertEquals(whole.sample(at, 0)!!, split.sample(at, 0)!!, 1e-6f, "rate $rate")
            }
        }
    }
    @Test fun downsamplingRejectsAboveOutputNyquist() {
        val (low) = input(96_000, 1) { frame, _ -> sin(2 * PI * 3_000 * frame / 96_000).toFloat() }
        val (high) = input(96_000, 1) { frame, _ -> sin(2 * PI * 36_000 * frame / 96_000).toFloat() }
        fun power(input: CaptureMixInput): Double = (0 until 960).sumOf { i ->
            val value = input.sample(1_030_000_000 + i * 1_000_000_000L / 48_000, 0)!!.toDouble()
            value * value
        }
        assertTrue(power(high) < power(low) * .01, "at least20dB suppression of the36kHz fixture")
    }
    @Test fun queuesAreBoundedAndStallsNeverReplayLastChunk() {
        val (input, data) = input()
        repeat(10) { i -> input.offer(data, data.size, stereo, (i + 1L) * 4800, null, 1_200_000_000 + i * 100_000_000L, 1, 3) }
        assertTrue(input.queuedFrames <= 9600)
        assertTrue(input.discontinuities > 0)
        assertNull(input.sample(5_000_000_000, 0))
    }
    @Test fun invalidSamplesAndFullScaleMixRemainFiniteAndClipped() {
        val core = CaptureMixCore(false)
        core.epoch(3); core.attachment(1, true)
        val data = FloatArray(9600) { if (it == 0) Float.NaN else if (it == 1) Float.POSITIVE_INFINITY else 1f }
        for (mic in listOf(false, true)) core.offer(mic, data, data.size, stereo, 0, CaptureClock(0, 1_000_000_000), 1_100_000_000, if (mic) 1 else 0, 3)
        val output = FloatArray(960)
        repeat(3) { core.render(1_030_000_000 + it * 10_000_000L, output) }
        assertTrue(output.all { it.isFinite() && it in -1f..1f })
        assertEquals(1f, output.last(), 1e-5f)
        assertEquals(2L, core.microphone.invalidSamples)
        core.attachment(2, false)
        core.render(1_060_000_000, output)
        assertTrue(output.all { it == 1f }, "missing mic must not halve playback")
    }
    @Test fun timestampAbsenceAndResetHaveExplicitTruth() {
        val (input, data) = input()
        assertFalse(input.estimated)
        input.offer(data, data.size, stereo, 4800, CaptureClock(1, 900_000_000), 1_200_000_000, 1, 3)
        assertTrue(input.discontinuities > 0)
        input.reset(2, 3)
        input.offer(data, data.size, stereo, 0, null, 2_000_000_000, 2, 3)
        assertTrue(input.estimated)
    }
    // Failure cases: delayed delivery, unequal input latency, stalled reads, and a growing delay
    // must not cause permanent silence, channel loss, stale replay, or timeline rewind.
    @Test fun livePlaybackFollowsDeliveredAudioInsteadOfTheHardwareHead() {
        val core = CaptureMixCore(false)
        core.epoch(3)
        val output = FloatArray(960)
        val data = FloatArray(960) { if (it % 2 == 0) .25f else -.5f }
        repeat(100) { block ->
            val frame = block * 480L
            val now = 2_000_000_000L + block * 10_000_000L
            core.offer(false, data, data.size, stereo, frame,
                CaptureClock(frame + 480 + 4320, now), now, 0, 3)
            core.renderLive(now, output)
            if (block > 20) {
                assertEquals(.25f, output[0], 1e-5f)
                assertEquals(-.5f, output[1], 1e-5f)
                assertTrue(output.all { it != 0f }, "complete delayed block $block")
            }
        }
        core.renderLive(4_000_000_000L, output)
        assertTrue(output.all { it == 0f }, "stalled input cannot replay old sound")
    }
    @Test fun liveMixUsesOnePresentationTimeForDifferentDeliveryLatencies() {
        val core = CaptureMixCore(false)
        core.epoch(3); core.attachment(1, true)
        val output = FloatArray(960)
        repeat(100) { block ->
            val frame = block * 480L
            val now = 2_000_000_000L + block * 10_000_000L
            for (mic in listOf(false, true)) {
                val delay = if (mic) 480 else 4320
                // The same time ramp reaches each reader with a different delivery delay.
                val data = FloatArray(960) { ((frame + it / 2 - delay) / 48_000f) }
                core.offer(mic, data, data.size, stereo, frame,
                    CaptureClock(frame + 480 + delay, now), now, if (mic) 1 else 0, 3)
            }
            core.renderLive(now, output)
            if (block > 25) {
                val expected = (block * 480 + 480 - 5760) / 48_000f
                assertEquals(expected, output[0], 1e-4f, "aligned inputs at block $block")
            }
        }
    }
    @Test fun increasingLiveDelayNeverReplaysPublishedFramesAndEpochCanRestart() {
        val core = CaptureMixCore(false)
        core.epoch(3)
        val data = FloatArray(960) { .5f }
        val output = FloatArray(960)
        repeat(30) { block ->
            val now = 2_000_000_000L + block * 10_000_000L
            core.offer(false, data, 960, stereo, block * 480L,
                CaptureClock(block * 480L + 480, now), now, 0, 3)
            core.renderLive(now, output)
        }
        core.renderLive(2_280_000_000L, output)
        assertTrue(output.all { it == 0f }, "presentation cannot run backwards")
        core.epoch(4)
        core.renderLive(2_000_000_000L, output)
        assertTrue(output.all { it == 0f }, "retired epoch contains no samples")
    }
    @Test fun playbackOnlyReadsBypassClockAlignmentAndKeepStereoLevelAndEpoch() {
        val core = CaptureMixCore(false)
        core.epoch(3)
        core.settings(CaptureMixSettings(playback = .5f))
        val pcm = floatArrayOf(.5f, -.25f)
        assertTrue(core.preparePlaybackRead(pcm, pcm.size, stereo, 3))
        assertContentEquals(floatArrayOf(.25f, -.125f), pcm)
        assertFalse(core.preparePlaybackRead(pcm, pcm.size, stereo, 2))
        core.attachment(1, true)
        assertFalse(core.preparePlaybackRead(pcm, pcm.size, stereo, 3))
        core.attachment(2, false)
        assertTrue(core.preparePlaybackRead(pcm, pcm.size, stereo, 3))
        assertFalse(CaptureMixCore(true).preparePlaybackRead(pcm, pcm.size, stereo, -1))
    }
    @Test fun rejectedClockCannotTurnClearedFrameZeroIntoAPlaybackDelay() {
        val input = CaptureMixInput()
        val data = FloatArray(960) { .5f }
        val frame = 4_800_000L
        input.offer(data, 960, stereo, frame, CaptureClock(frame + 480, 100_000_000_000L),
            100_000_000_000L, 0, 3)
        input.offer(data, 960, stereo, frame + 480, CaptureClock(frame + 960, 100_200_000_000L),
            100_200_000_000L, 0, 3)
        assertEquals(0, input.queuedFrames)
        assertEquals(0L, input.deliveryDelayNanos)
        input.offer(data, 960, stereo, frame + 960, CaptureClock(frame + 1440, 100_210_000_000L),
            100_210_000_000L, 0, 3)
        assertEquals(0L, input.deliveryDelayNanos)
    }
    @Test fun nonNativePlaybackFormatsUseTheStereoResamplingMixerWithoutMic() {
        for (format in listOf(CapturePcm(44_100, 2), CapturePcm(48_000, 1))) {
            val core = CaptureMixCore(false)
            core.epoch(3)
            val output = FloatArray(960)
            val frames = format.rate / 100
            val data = FloatArray(frames * format.channels) { .5f }
            repeat(40) { block ->
                val now = 2_000_000_000L + block * 10_000_000L
                assertFalse(core.preparePlaybackRead(data, data.size, format, 3))
                assertFalse(core.directPlayback)
                core.offer(false, data, data.size, format, block.toLong() * frames,
                    CaptureClock((block + 1L) * frames, now), now, 0, 3)
                core.renderLive(now, output)
                if (block > 20) {
                    assertEquals(960, output.size)
                    assertTrue(output.all { abs(it - .5f) < 1e-5f })
                }
            }
        }
    }
    @Test fun growingDeliveryLatencyRecoversAndStarvedMicDoesNotSilencePlayback() {
        val core = CaptureMixCore(false)
        core.epoch(3); core.attachment(1, true)
        val output = FloatArray(960)
        var frame = 0L
        repeat(150) { block ->
            val now = 2_000_000_000L + block * 10_000_000L
            // Four missing reads increase delivery lag from 90 ms to 130 ms.
            if (block !in 50..53) {
                val data = FloatArray(960) { .5f }
                core.offer(false, data, data.size, stereo, frame,
                    CaptureClock(block * 480L + 480 + 4320, now), now, 0, 3)
                frame += 480
            }
            if (block < 40) {
                val mic = FloatArray(960) { .25f }
                core.offer(true, mic, 960, stereo, block * 480L,
                    CaptureClock(block * 480L + 480, now), now, 1, 3)
            }
            core.renderLive(now, output)
            if (block > 110) assertTrue(output.all { abs(it - .5f) < 1e-5f })
        }
        assertTrue(core.missingMicrophoneFrames > 0)
        assertTrue(core.playoutGapFrames > 0)
    }
    @Test fun simulatedClocksStayBoundedForSixtySeconds() {
        for (ppm in listOf(-500, 500)) {
            val input = CaptureMixInput()
            val data = FloatArray(960) { .25f }
            repeat(6000) { block ->
                val frame = block * 480L
                val at = 1_000_000_000L + (frame / (48_000.0 * (1 + ppm / 1e6)) * 1e9).toLong()
                input.offer(data, data.size, stereo, frame, CaptureClock(frame, at), at + 10_000_000, 1, 3)
                assertTrue(input.queuedFrames <= 9600)
                assertTrue(abs(input.correctionPpm) <= 1000)
                if (block > 20) assertNotNull(input.sample(at - 40_000_000, 0))
            }
        }
    }
}
