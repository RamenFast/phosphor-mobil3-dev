package dev.phosphor.mobil3

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

// Mic → the beam ("room" mode). Foreground-app scope only for now (no FGS); the watching
// use case keeps the app in front. Stereo because XY needs two channels.
class MicController {
    @Volatile private var running = false
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // caller gates on RECORD_AUDIO
    fun start() {
        if (running) return
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(48_000)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val min = AudioRecord.getMinBufferSize(
            48_000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(48_000 * 2 * 4 / 10)
        val rec = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(min)
            .build()
        record = rec
        PhosphorNative.deckSetPaused(true)
        PhosphorNative.setRingActive(true)
        running = true
        rec.startRecording()
        Thread {
            val chunk = FloatArray(48_000 / 100 * 2) // 10 ms stereo
            while (running) {
                val n = rec.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (n > 0) PhosphorNative.pushCaptureSamples(chunk, n)
            }
        }.start()
        Log.i("phosphor-mobil3", "mic capture running")
    }

    fun stop() {
        running = false
        record?.run { runCatching { stop() }; release() }
        record = null
        PhosphorNative.setRingActive(false)
    }
}
