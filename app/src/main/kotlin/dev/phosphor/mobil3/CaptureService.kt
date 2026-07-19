package dev.phosphor.mobil3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log

// M4: other-app audio -> the beam. MediaProjection consent arrives via the launch intent;
// FGS type mediaProjection MUST be running before getMediaProjection (API 34+ rule).
// Honesty law: apps that opt out (Spotify, YT Music, DRM) arrive as silence.
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    @Volatile private var running = false
    private var metadataBridgeActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture("stopped by user")
            return START_NOT_STICKY
        }
        val resultData = intent?.getParcelableExtra(EXTRA_RESULT, Intent::class.java)
        if (resultData == null) {
            Log.e(TAG, "no MediaProjection consent in intent")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(RESULT_OK_CODE, resultData)
        if (proj == null) {
            Log.e(TAG, "getMediaProjection returned null")
            stopCapture("projection unavailable")
            return START_NOT_STICKY
        }
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopCapture("projection ended (lock or system stop)")
        }, null)

        val config = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(48_000)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val rec = AudioRecord.Builder()
            .setAudioFormat(format)
            .setAudioPlaybackCaptureConfig(config)
            .setBufferSizeInBytes(48_000 * 2 * 4 / 5) // 200 ms float stereo
            .build()
        record = rec

        PhosphorNative.deckSetPaused(true) // capture takes the beam; deck resumes on stop
        PhosphorNative.setRingActive(true)
        running = true
        rec.startRecording()
        metadataBridgeActive = true
        startService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_CAPTURE_STARTED)
        )
        Thread {
            val chunk = FloatArray(48_000 / 100 * 2) // 10 ms stereo
            while (running) {
                val n = rec.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (n > 0) PhosphorNative.pushCaptureSamples(chunk, n)
            }
        }.start()
        Log.i(TAG, "playback capture running")
        return START_STICKY
    }

    private fun stopCapture(reason: String) {
        if (running) Log.i(TAG, "capture stopped: $reason")
        running = false
        record?.run { runCatching { stop() }; release() }
        record = null
        // Null first: MediaProjection.stop() synchronously calls our callback on some
        // builds, and a second stop must be a harmless no-op rather than recursion.
        val oldProjection = projection
        projection = null
        runCatching { oldProjection?.stop() }
        PhosphorNative.setRingActive(false)
        if (metadataBridgeActive) {
            metadataBridgeActive = false
            startService(
                Intent(this, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_CAPTURE_STOPPED)
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCapture("service destroyed")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Scope capture", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentTitle("Phosphor is listening to what's playing")
            .setContentText("Sound becomes light on this screen. Nothing is recorded.")
            .build()
    }

    companion object {
        private const val TAG = "phosphor-mobil3"
        const val EXTRA_RESULT = "projection_result"
        const val ACTION_STOP = "dev.phosphor.mobil3.CAPTURE_STOP"
        const val RESULT_OK_CODE = -1 // Activity.RESULT_OK
        private const val NOTIF_ID = 1002
        private const val CHANNEL = "capture"
    }
}
