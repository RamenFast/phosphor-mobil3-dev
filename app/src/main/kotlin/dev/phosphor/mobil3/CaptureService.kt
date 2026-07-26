package dev.phosphor.mobil3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
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
    private var cleanedUp = false
    private var lifecycleState = STATE_IDLE

    override fun onBind(intent: Intent?): IBinder? = null

    @Synchronized
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishCapture("stopped by user", CaptureStatus.idle())
            return START_NOT_STICKY
        }
        if (cleanedUp) return START_NOT_STICKY
        if (lifecycleState == STATE_STARTING || lifecycleState == STATE_FLOWING) {
            // Activity retries and duplicate intents are idempotent. Never replace a
            // live AudioRecord/MediaProjection pair or start a second reader thread.
            publishStatus(lastStatus)
            return START_NOT_STICKY
        }
        val resultData = intent?.getParcelableExtra(EXTRA_RESULT, Intent::class.java)
        if (resultData == null) {
            Log.e(TAG, "no MediaProjection consent in intent")
            finishCapture(
                "projection permission data missing",
                CaptureStatus.permissionNeeded(
                    "capture permission needed",
                    "Open SOURCES, choose everything playing, and approve Android's prompt",
                ),
            )
            return START_NOT_STICKY
        }

        publishStatus(CaptureStatus.starting())

        try {
            startForeground(
                NOTIF_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "capture foreground service could not start", error)
            finishCapture(
                "foreground service unavailable",
                CaptureStatus.error(
                    "capture could not start",
                    "Allow Phosphor notifications and foreground media projection, then retry",
                ),
            )
            return START_NOT_STICKY
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = try {
            mpm.getMediaProjection(RESULT_OK_CODE, resultData)
        } catch (error: SecurityException) {
            Log.e(TAG, "MediaProjection permission rejected", error)
            finishCapture(
                "projection permission rejected",
                CaptureStatus.permissionNeeded(
                    "capture permission rejected",
                    "Choose everything playing again and approve Android's prompt",
                ),
            )
            return START_NOT_STICKY
        }
        if (proj == null) {
            Log.e(TAG, "getMediaProjection returned null")
            finishCapture(
                "projection unavailable",
                CaptureStatus.permissionNeeded(
                    "capture permission unavailable",
                    "Choose everything playing again and approve Android's prompt",
                ),
            )
            return START_NOT_STICKY
        }
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = finishCapture(
                "projection ended (lock or system stop)",
                CaptureStatus.permissionNeeded(
                    "capture permission ended",
                    "Unlock the phone, choose everything playing, and approve Android's prompt again",
                ),
            )
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
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "playback capture lost microphone permission")
            finishCapture(
                "microphone permission required",
                CaptureStatus.permissionNeeded(
                    "microphone permission needed for playback capture",
                    "Grant Phosphor microphone permission in Android settings, then retry",
                ),
            )
            return START_NOT_STICKY
        }
        val rec = try {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(48_000 * 2 * 4 / 5) // 200 ms float stereo
                .build()
        } catch (error: SecurityException) {
            Log.e(TAG, "playback capture permission rejected", error)
            finishCapture(
                "microphone permission rejected",
                CaptureStatus.permissionNeeded(
                    "playback capture permission rejected",
                    "Grant microphone permission and approve Android's capture prompt again",
                ),
            )
            return START_NOT_STICKY
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "playback capture configuration rejected", error)
            finishCapture(
                "capture configuration unsupported",
                CaptureStatus.error(
                    "playback capture is unavailable on this route",
                    "Change the audio route or source, then retry; protected sources may remain silent",
                ),
            )
            return START_NOT_STICKY
        }
        record = rec

        try {
            rec.startRecording()
        } catch (error: SecurityException) {
            Log.e(TAG, "playback capture start rejected", error)
            finishCapture(
                "microphone permission rejected",
                CaptureStatus.permissionNeeded(
                    "playback capture permission rejected",
                    "Grant microphone permission and approve Android's capture prompt again",
                ),
            )
            return START_NOT_STICKY
        } catch (error: IllegalStateException) {
            Log.e(TAG, "playback capture failed to start", error)
            finishCapture(
                "capture failed to start",
                CaptureStatus.error(
                    "playback capture failed to start",
                    "Change the audio route or source, then retry",
                ),
            )
            return START_NOT_STICKY
        }
        PhosphorNative.deckSetPaused(true) // capture takes the beam; deck resumes on stop
        PhosphorNative.setRingActive(true)
        running = true
        metadataBridgeActive = true
        startService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_CAPTURE_STARTED)
        )
        publishStatus(CaptureStatus.flowing())
        Thread {
            val chunk = FloatArray(48_000 / 100 * 2) // 10 ms stereo
            while (running) {
                val n = rec.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (n > 0) PhosphorNative.pushCaptureSamples(chunk, n)
            }
        }.start()
        Log.i(TAG, "playback capture running")
        // MediaProjection consent cannot be renewed silently after process death.
        return START_NOT_STICKY
    }

    @Synchronized
    private fun finishCapture(reason: String, status: CaptureStatus) {
        if (cleanedUp) return
        cleanedUp = true
        if (running) Log.i(TAG, "capture stopped: $reason")
        running = false
        record?.run {
            runCatching { stop() }
            runCatching { release() }
        }
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
        publishStatus(status)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!cleanedUp) finishCapture("service destroyed", CaptureStatus.idle())
        super.onDestroy()
    }

    private fun publishStatus(status: CaptureStatus) {
        lifecycleState = status.state
        lastStatus = status
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATE, status.state)
                .putExtra(EXTRA_MESSAGE, status.message)
                .putExtra(EXTRA_FIX, status.fix)
                .putExtra(EXTRA_LIVE, status.live)
        )
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
        const val ACTION_STATUS = "dev.phosphor.mobil3.CAPTURE_STATUS"
        const val EXTRA_STATE = "capture_state"
        const val EXTRA_MESSAGE = "capture_message"
        const val EXTRA_FIX = "capture_fix"
        const val EXTRA_LIVE = "capture_live"
        const val STATE_IDLE = "idle"
        const val STATE_STARTING = "starting"
        const val STATE_FLOWING = "flowing"
        const val STATE_PERMISSION_NEEDED = "permission_needed"
        const val STATE_ERROR = "error"
        const val RESULT_OK_CODE = -1 // Activity.RESULT_OK
        private const val NOTIF_ID = 1002
        private const val CHANNEL = "capture"

        @Volatile
        private var lastStatus = CaptureStatus.idle()

        fun currentStatus(): CaptureStatus = lastStatus

        fun statusFrom(intent: Intent): CaptureStatus = CaptureStatus(
            state = intent.getStringExtra(EXTRA_STATE) ?: STATE_IDLE,
            message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
            fix = intent.getStringExtra(EXTRA_FIX).orEmpty(),
            live = intent.getBooleanExtra(EXTRA_LIVE, false),
        )
    }

    data class CaptureStatus(
        val state: String,
        val message: String,
        val fix: String,
        val live: Boolean,
    ) {
        companion object {
            fun idle() = CaptureStatus(STATE_IDLE, "", "", false)
            fun starting() = CaptureStatus(
                STATE_STARTING,
                "capture starting",
                "Keep Android's capture permission active while Phosphor connects",
                false,
            )
            fun flowing() = CaptureStatus(
                STATE_FLOWING,
                "playback capture connected",
                "If the beam stays still, the source may be silent, protected, or opted out",
                true,
            )
            fun permissionNeeded(message: String, fix: String) =
                CaptureStatus(STATE_PERMISSION_NEEDED, message, fix, false)
            fun error(message: String, fix: String) =
                CaptureStatus(STATE_ERROR, message, fix, false)
        }
    }
}
