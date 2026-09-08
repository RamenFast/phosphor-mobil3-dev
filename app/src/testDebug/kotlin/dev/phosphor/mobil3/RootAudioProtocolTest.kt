package dev.phosphor.mobil3

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RootAudioProtocolTest {
    private val build = "a".repeat(64)
    private fun tagged(uid: Int = 10401, actual: Int = 0): ByteArray {
        val json = JSONObject().put("protocol", 2).put("generation", 1).put("mode", 0).put("build", build).put("uid", actual).put("original_uid", uid).toString().toByteArray()
        return ByteBuffer.allocate(80 + json.size).order(ByteOrder.LITTLE_ENDIAN).putInt(uid).put(build.toByteArray()).putLong(1).putInt(0).put(json).array()
    }
    private fun result() = JSONObject().put("status", "ok").put("cleanup_confirmed", true)
        .put("frames", 80000).put("nonzero", 70000).put("frequency_hz", 997.0).put("tone_ratio", 0.9)
        .put("rms", 0.017).put("sample_rate", 16000).put("channels", 1).put("encoding", 2).put("elapsed_ms", 5001)
    private fun terminal() = JSONObject().put("status", "ok").put("cleanup_confirmed", true).put("killed", false).put("child_wait_status", 0)
    @Test fun exactActionOnly() {
        assertTrue(SelfTestReceiver.accepts("dev.phosphor.mobil3.ROOT_AUDIO_PROBE"))
        assertFalse(SelfTestReceiver.accepts("dev.phosphor.mobil3.ROOT_AUDIO_PROBE.extra"))
        assertFalse(SelfTestReceiver.accepts(null))
    }
    @Test fun partialFrameAndEof() {
        val decoder = RootAudioProtocol.Decoder()
        val wire = RootAudioProtocol.control(3)
        wire.dropLast(1).forEach { assertNull(decoder.byte(it.toInt() and 255)) }
        assertThrows(IllegalArgumentException::class.java) { decoder.eof() }
        assertEquals(3, decoder.byte(wire.last().toInt() and 255)!!.first)
        decoder.eof()
    }
    @Test fun frameLimits() {
        assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.control(99) }
        val decoder = RootAudioProtocol.Decoder()
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(RootAudioProtocol.MAGIC).putInt(11).putInt(4097).array()
        assertThrows(IllegalArgumentException::class.java) { header.forEach { decoder.byte(it.toInt() and 255) } }
    }
    @Test fun identityChecks() {
        assertEquals(0, RootAudioProtocol.helper(tagged(), 10401, build).getInt("uid"))
        assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.helper(tagged(), 10402, build) }
        assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.helper(tagged(actual = 10401), 10401, build) }
        assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.helper(tagged(), 10401, "b".repeat(64)) }
    }
    @Test fun completeSuccessGate() {
        assertTrue(RootAudioProtocol.success(true, result(), terminal(), 0, true))
        assertFalse(RootAudioProtocol.success(false, result(), terminal(), 0, true))
        assertFalse(RootAudioProtocol.success(true, result(), terminal(), null, true))
        assertFalse(RootAudioProtocol.success(true, result(), terminal(), 0, false))
        assertFalse(RootAudioProtocol.success(true, result(), terminal().put("killed", true), 0, true))
        assertFalse(RootAudioProtocol.success(true, result(), terminal().put("cleanup_confirmed", false), 0, true))
        assertFalse(RootAudioProtocol.success(true, result().put("cleanup_confirmed", false), terminal(), 0, true))
    }
    @Test fun nonzeroIsNotToneProof() {
        for ((key, value) in listOf("frequency_hz" to 440, "frames" to 1, "rms" to 0, "tone_ratio" to 0, "sample_rate" to 48000)) {
            assertFalse(key, RootAudioProtocol.success(true, result().put(key, value), terminal(), 0, true))
        }
    }
    @Test fun failuresRetainFrameworkCause() {
        val report = result().put("status", "error").put("error", "register: SecurityException: MODIFY_AUDIO_ROUTING")
        assertFalse(RootAudioProtocol.success(true, report, terminal(), 0, true))
        assertEquals("register: SecurityException: MODIFY_AUDIO_ROUTING", report.getString("error"))
    }
}
