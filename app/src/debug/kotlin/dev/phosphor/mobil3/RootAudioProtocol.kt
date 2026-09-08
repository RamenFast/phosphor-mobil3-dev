package dev.phosphor.mobil3

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject

internal object RootAudioProtocol {
    const val MAGIC = 0x31524150
    const val MAX = 4096
    fun control(kind: Int): ByteArray {
        require(kind in 1..3) { "Unknown fixed control" }
        return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(MAGIC).putInt(kind).putInt(0).array()
    }
    class Decoder {
        private val bytes = ByteArray(MAX + 12)
        private var count = 0
        fun byte(value: Int): Pair<Int, ByteArray>? {
            require(value in 0..255 && count < bytes.size) { "Frame overflow or invalid byte" }
            bytes[count++] = value.toByte()
            if (count < 12) return null
            val h = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(h.int == MAGIC) { "Frame magic mismatch" }
            val kind = h.int
            val length = h.int
            require(length in 0..MAX) { "Frame length invalid" }
            if (count < length + 12) return null
            val payload = bytes.copyOfRange(12, count)
            count = 0
            return kind to payload
        }
        fun eof() { require(count == 0) { "Partial frame at EOF" } }
    }
    fun helper(payload: ByteArray, uid: Int, build: String): JSONObject {
        require(payload.size in 69..MAX) { "Helper payload length invalid" }
        val h = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        require(h.int == uid) { "Helper original UID mismatch" }
        val identity = ByteArray(64).also(h::get).toString(Charsets.US_ASCII)
        require(identity == build && build.matches(Regex("[a-f0-9]{64}"))) { "Helper build mismatch" }
        return JSONObject(payload.copyOfRange(68, payload.size).toString(Charsets.UTF_8)).also {
            require(it.getInt("uid") == 0 && it.getInt("original_uid") == uid && it.getString("build") == build && it.getInt("protocol") == 1) { "Helper actual identity invalid" }
        }
    }
    fun success(ready: Boolean, result: JSONObject?, final: JSONObject?, exit: Int?, fixtureClean: Boolean): Boolean =
        ready && result?.optString("status") == "ok" && result.optBoolean("cleanup_confirmed") &&
            final?.optString("status") == "ok" && final.optBoolean("cleanup_confirmed") &&
            !final.optBoolean("killed", true) && final.optInt("child_wait_status", -1) == 0 && exit == 0 && fixtureClean &&
            result.optInt("frames") in 64000..80000 && result.optInt("nonzero") > 32000 &&
            result.optDouble("frequency_hz", 0.0) in 975.0..1019.0 && result.optDouble("tone_ratio", 0.0) >= 0.1 &&
            result.optDouble("rms", 0.0) > 0.00001 && result.optInt("sample_rate") == 16000 &&
            result.optInt("channels") == 1 && result.optInt("encoding") == 2 && result.optLong("elapsed_ms") in 5000..5600
}
