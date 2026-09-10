package dev.phosphor.mobil3

import android.media.AudioRecord
import android.media.AudioTimestamp
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

/** Owned by CaptureService or the standalone microphone service, never by a view. */
internal class CaptureMixSession(val microphoneOnly: Boolean, settings: CaptureMixSettings,
                                 private val failed: (String) -> Unit) {
    val id = ids.incrementAndGet()
    val core = CaptureMixCore(microphoneOnly)
    val nativeOwner: Long
    @Volatile var live = true; private set
    @Volatile private var micGeneration = 0L
    private val publisher: Thread
    init {
        core.settings(settings)
        PhosphorNative.deckSetPaused(true)
        nativeOwner = PhosphorNative.setRingActive(true)
        core.epoch(PhosphorNative.captureReadEpoch())
        publisher = Thread({
            try {
                val output = FloatArray(960)
                var deadline = SystemClock.elapsedRealtimeNanos()
                while (live) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (now < deadline) { LockSupport.parkNanos(deadline - now); continue }
                    if (now - deadline > 20_000_000) deadline = now
                    core.epoch(PhosphorNative.captureReadEpoch())
                    val epoch = core.render(deadline - 50_000_000, output)
                    if (live) PhosphorNative.pushCaptureRead(output, output.size, nativeOwner, epoch)
                    deadline += 10_000_000
                }
            } catch (error: RuntimeException) {
                live = false
                failed("Visualization mixer stopped: ${error.message}. Stop the source and retry")
            }
        }, "capture-mix-$id")
        try { publisher.start() } catch (error: RuntimeException) {
            live = false; PhosphorNative.setRingActive(false); throw error
        }
    }
    @Synchronized fun attach(): Long {
        check(live) { "Capture ended. Start playback capture again" }
        val generation = ++micGeneration
        core.attachment(generation, true)
        return generation
    }
    @Synchronized fun accepts(generation: Long) = live && micGeneration == generation
    @Synchronized fun detach(generation: Long) {
        if (micGeneration != generation) return
        micGeneration++
        core.attachment(micGeneration, false)
    }
    fun settings(value: CaptureMixSettings) = core.settings(value)
    @Synchronized fun clearMicrophone(generation: Long) {
        if (micGeneration == generation) core.attachment(generation, true)
    }
    fun offer(mic: Boolean, data: FloatArray, count: Int, format: CapturePcm, frame: Long,
              clock: CaptureClock?, receipt: Long, generation: Long, epoch: Long) {
        if (!live || (mic && !accepts(generation))) return
        core.offer(mic, data, count, format, frame, clock, receipt, generation, epoch)
    }
    fun invalidate() { live = false; LockSupport.unpark(publisher) }
    /** Off-main; only the whole-source owner calls this after retiring both readers. */
    @Synchronized fun finish(): String? {
        invalidate()
        publisher.join(2_000)
        if (publisher.isAlive) return ReaderStop.TIMEOUT
        if (!ringReleased) { PhosphorNative.setRingActive(false); ringReleased = true }
        core.epoch(-1)
        return null
    }
    private var ringReleased = false
    companion object { private val ids = AtomicLong() }
}

internal fun captureClock(record: AudioRecord): CaptureClock? = runCatching {
    val timestamp = AudioTimestamp()
    if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS)
        CaptureClock(timestamp.framePosition, timestamp.nanoTime) else null
}.getOrNull()
