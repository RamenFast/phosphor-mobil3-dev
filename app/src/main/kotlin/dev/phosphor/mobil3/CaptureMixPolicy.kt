package dev.phosphor.mobil3

import kotlin.math.*

internal data class CaptureMixSettings(val include: Boolean = false, val playback: Float = 1f, val microphone: Float = 1f) {
    init { require(playback.isFinite() && playback in 0f..1f && microphone.isFinite() && microphone in 0f..1f) }
    companion object {
        const val INCLUDE = "capture_include_mic"
        const val PLAYBACK = "capture_playback_level"
        const val MICROPHONE = "capture_mic_level"
        fun read(values: Map<String, *>): CaptureMixSettings {
            fun level(key: String) = (values[key] as? Float)?.takeIf { it.isFinite() && it in 0f..1f } ?: 1f
            return CaptureMixSettings(values[INCLUDE] as? Boolean ?: false, level(PLAYBACK), level(MICROPHONE))
        }
    }
}

internal data class CaptureClock(val frame: Long, val nanos: Long)
internal data class CapturePcm(val rate: Int, val channels: Int) {
    init { require(rate in 8_000..192_000 && channels in 1..2) }
}

/** One bounded frame timeline. All access is serialized by CaptureMixCore. */
internal class CaptureMixInput {
    var format: CapturePcm? = null; private set
    var epoch = -1L; private set
    var generation = -1L; private set
    var discontinuities = 0L; private set
    var invalidSamples = 0L; private set
    var estimated = true; private set
    var correctionPpm = 0.0; private set
    var queuedFrames = 0; private set
    private var samples = FloatArray(0)
    private var first = 0L
    private var end = 0L
    private var anchor: CaptureClock? = null
    private var previous: CaptureClock? = null
    private var validClock = false
    private var lastReceipt = 0L

    fun clear() {
        first = 0; end = 0; queuedFrames = 0; anchor = null; previous = null
        validClock = false; estimated = true; correctionPpm = 0.0; lastReceipt = 0
    }
    fun reset(generation: Long, epoch: Long) {
        clear(); this.generation = generation; this.epoch = epoch
    }
    fun offer(pcm: FloatArray, count: Int, fmt: CapturePcm, frame: Long, clock: CaptureClock?,
              receipt: Long, generation: Long, epoch: Long) {
        require(count in 0..pcm.size && count % fmt.channels == 0 && frame >= 0 && receipt >= 0)
        require(count / fmt.channels <= fmt.rate / 5)
        if (this.generation != generation || this.epoch != epoch || format != fmt) {
            reset(generation, epoch); format = fmt
            samples = FloatArray((fmt.rate / 5 + 32) * 2)
        }
        val frames = count / fmt.channels
        if (frames == 0) return
        if (queuedFrames > 0 && frame != end) { discontinuities++; clear() }
        val capacity = samples.size / 2
        if (queuedFrames == 0) { first = frame; end = frame }
        if (frame > Long.MAX_VALUE - frames) { discontinuities++; clear(); return }
        for (i in 0 until frames) {
            val index = ((frame + i) % capacity).toInt() * 2
            for (ch in 0..1) {
                val value = pcm[i * fmt.channels + min(ch, fmt.channels - 1)]
                if (!value.isFinite()) invalidSamples++
                samples[index + ch] = if (value.isFinite()) value else 0f
            }
        }
        end = frame + frames
        if (end - first > fmt.rate / 5) { first = end - fmt.rate / 5; discontinuities++ }
        queuedFrames = (end - first).toInt()
        lastReceipt = receipt
        updateClock(clock, CaptureClock(end, receipt), fmt.rate)
    }
    private fun updateClock(clock: CaptureClock?, fallback: CaptureClock, rate: Int) {
        val usable = clock?.takeIf { it.frame >= 0 && it.nanos > 0 &&
            abs(it.nanos.toDouble() - fallback.nanos) <= 1_000_000_000.0 }
        if (usable == null) {
            // Retain a recent good anchor across an occasional unavailable timestamp.
            if (validClock && previous?.let { fallback.nanos - it.nanos in 0..1_000_000_000L } == true) return
            if (anchor == null || validClock) anchor = fallback
            validClock = false; estimated = true; previous = null
            return
        }
        val old = previous
        if (old != null && usable == old) return
        if (old != null) {
            val df = usable.frame - old.frame; val dt = usable.nanos - old.nanos
            if (df <= 0 || dt <= 0) { discontinuities++; clear() }
            else if (dt >= 100_000_000L) {
                val ppm = (df.toDouble() * 1e9 / dt / rate - 1.0) * 1e6
                if (abs(ppm) <= 1000) {
                    val slew = 50.0 * dt / 1e9
                    correctionPpm += (ppm - correctionPpm).coerceIn(-slew, slew)
                } else { discontinuities++; clear() }
            }
        }
        // Accumulate enough clock span to avoid fitting per-read timestamp jitter.
        anchor = usable
        if (previous == null || old == null || usable.nanos - old.nanos >= 100_000_000L) previous = usable
        validClock = true; estimated = false
    }
    fun sample(nanos: Long, channel: Int): Float? {
        val fmt = format ?: return null
        val at = anchor ?: return null
        if (queuedFrames == 0 || nanos - lastReceipt > 200_000_000L) return null
        val position = at.frame + (nanos - at.nanos).toDouble() / 1e9 * fmt.rate * (1 + correctionPpm / 1e6)
        val center = floor(position).toLong()
        if (center < first || center >= end) return null
        val capacity = samples.size / 2
        if (fmt.rate == 48_000 && correctionPpm == 0.0 && abs(position - center) < 1e-5) {
            return samples[(center % capacity).toInt() * 2 + channel]
        }
        // 32-tap Hann-windowed sinc; no history from a retired generation or visual epoch.
        if (center - 15 < first || center + 16 >= end) return null
        val cutoff = .45 * min(1.0, 48_000.0 / (fmt.rate * (1 + correctionPpm / 1e6)))
        var sum = 0.0; var weight = 0.0
        for (tap in -15..16) {
            val delta = center + tap - position
            val x = 2 * cutoff * delta
            val sinc = if (abs(x) < 1e-12) 1.0 else sin(PI * x) / (PI * x)
            val window = .5 + .5 * cos(PI * delta / 16.0)
            val coefficient = 2 * cutoff * sinc * window
            sum += samples[((center + tap) % capacity).toInt() * 2 + channel] * coefficient
            weight += coefficient
        }
        return if (abs(weight) < 1e-12) 0f else (sum / weight).toFloat()
    }
}

internal class CaptureMixCore(private val microphoneOnly: Boolean) {
    val playback = CaptureMixInput()
    val microphone = CaptureMixInput()
    var epoch = -1L; private set
    var attachment = 0L; private set
    var settings = CaptureMixSettings(); private set
    var outputPeak = 0f; private set
    var limitedSamples = 0L; private set
    private var playbackLevel = 1f
    private var micLevel = 0f
    private var microphoneEnabled = microphoneOnly
    @Synchronized fun settings(value: CaptureMixSettings) { settings = value }
    @Synchronized fun attachment(generation: Long, enabled: Boolean) {
        attachment = generation; microphoneEnabled = enabled
        microphone.reset(generation, epoch)
    }
    @Synchronized fun epoch(value: Long) {
        if (epoch != value) { epoch = value; playback.reset(0, value); microphone.reset(attachment, value) }
    }
    @Synchronized fun offer(mic: Boolean, pcm: FloatArray, count: Int, format: CapturePcm, frame: Long,
                            clock: CaptureClock?, receipt: Long, generation: Long, readEpoch: Long): Boolean {
        if (readEpoch != epoch || (mic && (!microphoneEnabled || generation != attachment))) return false
        (if (mic) microphone else playback).offer(pcm, count, format, frame, clock, receipt, generation, readEpoch)
        return true
    }
    @Synchronized fun render(start: Long, result: FloatArray): Long {
        require(result.size == 960)
        var peak = 0f
        for (i in 0 until 480) {
            val at = start + i * 1_000_000_000L / 48_000
            val p0 = if (!microphoneOnly) playback.sample(at, 0) else null
            val m0 = if (microphoneEnabled) microphone.sample(at, 0) else null
            val targetP = if (p0 != null) settings.playback else 0f
            val targetM = if (m0 != null) settings.microphone else 0f
            playbackLevel += (targetP - playbackLevel).coerceIn(-1f / 960, 1f / 960)
            micLevel += (targetM - micLevel).coerceIn(-1f / 960, 1f / 960)
            for (ch in 0..1) {
                val p = if (ch == 0) p0 else if (!microphoneOnly) playback.sample(at, ch) else null
                val m = if (ch == 0) m0 else if (microphoneEnabled) microphone.sample(at, ch) else null
                val gp = if (p != null) playbackLevel else 0f
                val gm = if (m != null) micLevel else 0f
                val value = (gp * (p ?: 0f) + gm * (m ?: 0f)) / max(1f, gp + gm)
                if (value.isFinite() && abs(value) > 1f) limitedSamples++
                val safe = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
                result[i * 2 + ch] = safe; peak = max(peak, abs(safe))
            }
        }
        outputPeak = peak
        return epoch
    }
    @Synchronized fun describe(): String = "48 kHz stereo · queues ${playback.queuedFrames}/${microphone.queuedFrames} frames · " +
        "alignment ${if ((microphoneOnly || !playback.estimated) && (!microphoneEnabled || !microphone.estimated)) "timestamp observed" else "estimated"} · " +
        "drift ${playback.correctionPpm.toInt()}/${microphone.correctionPpm.toInt()} ppm · discontinuities ${playback.discontinuities}/${microphone.discontinuities} · output peak $outputPeak"
}
