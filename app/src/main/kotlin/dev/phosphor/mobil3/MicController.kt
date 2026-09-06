package dev.phosphor.mobil3

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CompletableFuture

// Mic → the beam ("room" mode). Foreground-app scope only for now (no FGS); the watching
// use case keeps the app in front. Stereo because XY needs two channels.
class MicController(private val onRecordingChanged: (Boolean) -> Unit = {}) {
    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var reader: Thread? = null
    private var ownsRing = false
    private var generation = 0L
    private var stopped = CompletableFuture.completedFuture<String?>(null)

    @SuppressLint("MissingPermission") // caller gates on RECORD_AUDIO
    fun start(
        onStarted: (String?) -> Unit,
        isCurrent: () -> Boolean,
        onFailed: (String) -> Unit = {},
    ) {
        if (!isCurrent()) return
        if (running) {
            onStarted(null)
            return
        }
        val request = ++generation
        val previous = owner
        if (previous != null && previous !== this) {
            previous.stop()
            stopped = previous.stopped
        }
        owner = this
        if (!stopped.isDone) {
            stopped.thenAccept { error ->
                main.post {
                    if (generation == request) {
                        if (error == null) start(onStarted, isCurrent, onFailed) else if (isCurrent()) onStarted(error)
                    }
                }
            }
            return
        }
        stopped.getNow(null)?.let { error ->
            Log.e("phosphor-mobil3", "mic remains stopped: $error")
            onStarted(error)
            return
        }
        val rec = try {
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(48_000)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val min = AudioRecord.getMinBufferSize(
                48_000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(48_000 * 2 * 4 / 10)
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(min)
                .build()
        } catch (error: RuntimeException) {
            if (owner === this) owner = null
            onStarted("Microphone could not open: ${error.message}. Check microphone permission and retry")
            return
        }
        record = rec
        val error = startMicRecording(
            initialized = { rec.state == AudioRecord.STATE_INITIALIZED },
            startRecording = { rec.startRecording() },
            recording = { rec.recordingState == AudioRecord.RECORDSTATE_RECORDING },
            armScope = {
                PhosphorNative.deckSetPaused(true)
                ownsRing = true
                PhosphorNative.setRingActive(true)
            },
        )
        if (error != null) {
            stop()
            onStarted(error)
            return
        }
        running = true
        try {
            reader = Thread {
                val chunk = FloatArray(48_000 / 100 * 2) // 10 ms stereo
                readSourceSamples(
                    running = { running },
                    read = { rec.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING) },
                    push = { n -> PhosphorNative.pushCaptureSamples(chunk, n) },
                    failed = { error -> main.post {
                        retireSourceReaderFailure(
                            // Recorder identity fences replacement. cancelStart may advance the
                            // request generation without retiring this still-live recorder.
                            isCurrent = { owner === this && record === rec && running },
                            stop = {
                                Log.e("phosphor-mobil3", "microphone reader failed", error)
                                stop()
                            },
                            publishFailure = {
                                onFailed("Microphone stopped. Check microphone permission and the audio route, then select built-in mic again")
                            },
                        )
                    } },
                )
            }.also { it.start() }
        } catch (error: RuntimeException) {
            stop()
            onStarted("Microphone reader could not start: ${error.message}. Stop the source and retry")
            return
        }
        onRecordingChanged(true)
        Log.i("phosphor-mobil3", "mic capture running")
        onStarted(null)
    }

    fun cancelStart() { ++generation }
    internal fun isRecording(): Boolean = owner === this && running
    internal fun ownsSource(): Boolean = owner === this && (record != null || !stopped.isDone)

    fun stop() {
        ++generation
        running = false
        onRecordingChanged(false)
        val oldRecord = record
        if (oldRecord == null && (!stopped.isDone || stopped.getNow(null) == null)) return
        if (oldRecord == null && stopped.getNow(null) != ReaderStop.TIMEOUT) return
        val oldReader = reader
        val ownedRing = ownsRing
        ownsRing = false
        record = null
        val completion = CompletableFuture<String?>()
        stopped = completion
        Thread({
            val error = ReaderStop.finish(
                reader = oldReader,
                stop = { if (oldRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) oldRecord.stop() },
                release = { oldRecord?.release() },
                cleanup = { cleanupOwnedSource(ownedRing) { PhosphorNative.setRingActive(false) } },
            )
            main.post {
                if (error == null && owner === this && record == null) owner = null
                completion.complete(error)
                if (error != null) Log.e("phosphor-mobil3", "mic stop: $error")
            }
        }, "mic-stop").start()
    }

    companion object {
        private val main = Handler(Looper.getMainLooper())
        // Only a stop rendezvous. The activity's controller still owns AudioRecord and start.
        private var owner: MicController? = null

        fun stopForLocal(requestId: Long, reply: (Long, String?, Boolean) -> Unit) {
            val stop = {
                val current = owner
                if (current == null) {
                    reply(requestId, null, false)
                } else {
                    current.stop()
                    current.stopped.thenAccept { error ->
                        if (error == null && owner === current && current.record == null) owner = null
                        reply(requestId, error, true)
                    }
                }
            }
            if (Looper.myLooper() == main.looper) stop() else main.post { stop() }
        }
    }
}

/** The real capture readers share this loop. A terminal read dispatches once, then exits. */
internal fun readSourceSamples(
    running: () -> Boolean,
    read: () -> Int,
    push: (Int) -> Unit,
    failed: (RuntimeException) -> Unit,
) {
    try {
        while (running()) {
            val count = read()
            if (count < 0) throw IllegalStateException("AudioRecord read failed: $count")
            if (running() && count > 0) push(count)
        }
    } catch (error: RuntimeException) {
        failed(error)
    }
}

/** Called on the owner's main thread after a reader reports failure. */
internal fun retireSourceReaderFailure(
    isCurrent: () -> Boolean,
    stop: () -> Unit,
    publishFailure: () -> Unit = {},
) {
    if (isCurrent()) {
        stop()
        publishFailure()
    }
}
