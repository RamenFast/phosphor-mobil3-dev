package dev.phosphor.mobil3

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RootStereoProbeTest {
    private fun ready() = JSONObject().put("stage", "ready").put("sample_rate", 48000).put("channels", 2).put("encoding", 2)
        .put("route_flags", 2).put("privileged_capture", false).put("physical_audibility_proven", false).put("pid", 400)
        .put("monitor_identity", JSONObject().put("uid", 1000).put("pid", 400).put("session", 47).put("player", 128))
        .put("monitor_route", JSONObject().put("id", 3).put("type", 8).put("address", "fixture"))
        .put("monitor_rate", 48000).put("monitor_channels", 2).put("monitor_encoding", 2).put("monitor_gain", 1.0)
    private fun signal() = JSONObject().put("stereo_proven", true).put("measured_frames", 144000)
        .put("rms_l", 0.017).put("rms_r", 0.017).put("peak", 0.025).put("correlation", 0.0)
        .put("separation_997_db", 60.0).put("separation_1499_db", 60.0).put("frames", 264000)
        .put("written_frames", 264000).put("pending_samples", 0).put("monitor_head", 262000)
        .put("queue_highwater_frames", 960).put("record_timestamp_rate", 48000.0).put("monitor_timestamp_rate", 48000.0)
        .put("sample_rate", 48000).put("channels", 2).put("encoding", 2)
    @Test fun exactFixedActions() {
        for (name in listOf("SYSTEM", "NONE")) {
            val action = "dev.phosphor.mobil3.ROOT_STEREO_${name}_TEST"
            assertTrue(SelfTestReceiver.accepts(action))
            assertFalse(SelfTestReceiver.accepts("$action.extra"))
        }
        assertEquals(4, RootStereoProbe.mode(true))
        assertEquals(5, RootStereoProbe.mode(false))
    }
    @Test fun sourceCompletionUsesActualFramesWithFiniteStartupAllowance() {
        assertFalse(RootStereoProbe.sourceShouldStop(237600, 5000))
        assertFalse(RootStereoProbe.sourceShouldStop(239999, 5499))
        assertTrue(RootStereoProbe.sourceShouldStop(240000, 5100))
        assertTrue(RootStereoProbe.sourceShouldStop(0, 5500))
    }
    @Test fun finalGenerationIsValidatedBeforeTerminalEvidenceCanBeRetained() {
        val payload = JSONObject().put("generation", 9).put("cleanup_confirmed", true).toString().toByteArray()
        assertEquals(9L, RootStereoProbe.validatedFinal(payload, 9).getLong("generation"))
        assertThrows(IllegalStateException::class.java) { RootStereoProbe.validatedFinal(payload, 10) }
    }
    @Test fun actualIdentityNotRawRootAssumption() {
        RootStereoProbe.validateReady(ready(), 10401)
        RootStereoProbe.validateReady(ready().apply { getJSONObject("monitor_identity").put("uid", 0) }, 10401)
        for (uid in listOf(-1, 10401)) {
            assertThrows(IllegalStateException::class.java) {
                RootStereoProbe.validateReady(ready().apply { getJSONObject("monitor_identity").put("uid", uid) }, 10401)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            RootStereoProbe.validateReady(ready().apply { getJSONObject("monitor_identity").put("pid", 401) }, 10401)
        }
    }
    @Test fun physicalRouteAndActualStereoRequired() {
        for ((key, value) in listOf("sample_rate" to 16000, "channels" to 1, "encoding" to 4, "route_flags" to 3)) {
            assertThrows(IllegalStateException::class.java) { RootStereoProbe.validateReady(ready().put(key, value), 10401) }
        }
        assertThrows(IllegalStateException::class.java) {
            RootStereoProbe.validateReady(ready().apply { getJSONObject("monitor_route").put("type", 25) }, 10401)
        }
    }
    @Test fun stereoRejectsSwappedDuplicatedSilenceCrossleakAndClip() {
        assertTrue(RootStereoProbe.signalValid(signal()))
        for ((key, value) in listOf("separation_997_db" to -60.0, "correlation" to 1.0, "rms_l" to 0.0,
            "separation_1499_db" to 20.0, "peak" to 1.0)) {
            assertFalse(key, RootStereoProbe.signalValid(signal().put(key, value)))
        }
    }
    @Test fun partialWritesMustCompleteAndActualFormatMustAgree() {
        for ((key, value) in listOf("pending_samples" to 2, "written_frames" to 263999, "monitor_head" to 0,
            "sample_rate" to 16000, "channels" to 1, "measured_frames" to 143999, "queue_highwater_frames" to 4801,
            "record_timestamp_rate" to 16000, "monitor_timestamp_rate" to 96000)) {
            assertFalse(key, RootStereoProbe.signalValid(signal().put(key, value)))
        }
    }
    @Test fun probeIdentityCannotCrossModesOrPublishMonoPcm() {
        val build = "a".repeat(64)
        for (mode in 4..5) {
            val selection = ByteBuffer.wrap(RootAudioProtocol.select(9, mode)).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(mode, selection.getInt(20))
            val id = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN).putInt(10401).put(build.toByteArray()).putLong(9).putInt(mode).array()
            RootAudioProtocol.tagged(id, 10401, build, 9, mode)
            assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.tagged(id, 10401, build, 9, if (mode == 4) 5 else 4) }
            assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.Stream(10401, build, 9, mode).pcm(id) }
        }
        assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.select(9, 6) }
    }
    @Test fun fixtureIsQuietIndependentAndHasExactEndpointRamps() {
        assertEquals(0.toShort(), RootStereoProbe.sample(0, 0))
        assertEquals(0.toShort(), RootStereoProbe.sample(239999, 1))
        var unequal = 0
        for (n in 0 until 48000) {
            val l = RootStereoProbe.sample(n, 0).toInt()
            val r = RootStereoProbe.sample(n, 1).toInt()
            assertTrue(kotlin.math.abs(l) <= 819 && kotlin.math.abs(r) <= 819)
            if (l != r) unequal++
        }
        assertTrue(unequal > 47000)
        assertThrows(IllegalArgumentException::class.java) { RootStereoProbe.sample(240000, 0) }
    }
}
