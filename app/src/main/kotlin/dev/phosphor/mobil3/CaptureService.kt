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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import androidx.core.content.IntentCompat
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CompletableFuture

// The projection foreground service must start before MediaProjection is obtained.
// Apps that disallow playback capture yield silence.
open class CaptureService : Service() {
    internal open val backend = CaptureBackend.STANDARD
    @Volatile private var rootSession: RootCaptureSession? = null
    private var rootCheck: RootCaptureCheck? = null

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    @Volatile private var running = false
    private var metadataBridgeActive = false
    private var cleanedUp = false
    private var lifecycleState = STATE_IDLE
    private var reader: Thread? = null
    private val main = Handler(Looper.getMainLooper())
    private val stopCompletion = CaptureStopLifecycle()
    private var destroyed = false
    private val micStopStatus = MicStopStatusToken()
    private val taskRevision = BackgroundLifecycle.policy.revision
    private val captureOwnerId = captureOwners.incrementAndGet()
    private val sourceWake = SourceWakeLock.forOwner(this, "capture")

    override fun onCreate() {
        super.onCreate()
        if (owner == null) owner = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Synchronized
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (owner !== this) {
            if (intent?.action == ACTION_STOP) owner?.onStartCommand(intent, flags, startId)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {

            retryRetiredStops()
            val owned = record != null || rootSession != null || reader != null || running || !retirement.pending.isDone
            val micRequest = intent.getStringExtra(EXTRA_MIC_REQUEST)
            micStopStatus.select(micRequest)
            finishCapture("stopped by user", CaptureStatus.idle(), retry = true)
            val requestId = intent.getLongExtra(EXTRA_STOP_REQUEST, -1)
            val reply = IntentCompat.getParcelableExtra(intent, EXTRA_STOP_REPLY, ResultReceiver::class.java)
            retirement.pending.thenAccept { error ->
                if (micStopStatus.accepts(micRequest)) {
                    // Superseded callbacks must not overwrite the latest request's idle snapshot.
                    publishStatus((if (error == null) CaptureStatus.idle() else
                        CaptureStatus.error("capture stop failed", error)).copy(micRequest = micRequest))
                }
                reply?.send(if (error == null) 0 else 1, Bundle().apply {
                    putLong(EXTRA_STOP_REQUEST, requestId)
                    putString(EXTRA_STOP_ERROR, error)
                    putBoolean(EXTRA_STOP_OWNED, owned)
                })
            }
            return START_NOT_STICKY
        }
        if (cleanedUp) return START_NOT_STICKY
        if (BackgroundLifecycle.policy.removed || !BackgroundLifecycle.accepts(intent)) {
            if (!running) finishCapture("task request retired", CaptureStatus.idle())
            return START_NOT_STICKY
        }
        val previousStop = retirement.pending
        if (!previousStop.isDone || previousStop.getNow(null) != null) {
            finishCapture("previous owner still stopping", CaptureStatus.error(
                "capture is still stopping", "Wait for source cleanup, then choose capture again",
            ))
            return START_NOT_STICKY
        }
        if (lifecycleState == STATE_STARTING || lifecycleState == STATE_FLOWING) {
            // Activity retries and duplicate intents are idempotent. Never replace a
            // live AudioRecord/MediaProjection pair or start a second reader thread.
            publishStatus(lastStatus)
            return START_NOT_STICKY
        }
        if (backend == CaptureBackend.ROOT) return startRoot()
        val resultData = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_RESULT, Intent::class.java)
        }
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
                    "Return to Phosphor and approve Android's foreground capture prompt again",
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
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            finishCapture("recorder did not start", CaptureStatus.error(
                "playback capture did not start", "Choose the source again and approve Android's capture prompt",
            ))
            return START_NOT_STICKY
        }
        PhosphorNative.deckSetPaused(true) // capture takes the beam; deck resumes on stop
        PhosphorNative.setRingActive(true)
        running = true
        metadataBridgeActive = true
        startService(
            BackgroundLifecycle.stamp(Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_CAPTURE_STARTED)
                .putExtra(EXTRA_CAPTURE_OWNER, captureOwnerId), taskRevision)
        )
        publishStatus(CaptureStatus.flowing())
        try {
            reader = Thread {
                val chunk = FloatArray(48_000 / 100 * 2) // 10 ms stereo
                readSourceSamples(
                    running = { running },
                    read = { rec.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING) },
                    push = { n -> PhosphorNative.pushCaptureSamples(chunk, n) },
                    failed = { error -> main.post {
                        retireSourceReaderFailure(
                            isCurrent = { owner === this && !cleanedUp && record === rec && running },
                            stop = {
                                Log.e(TAG, "capture reader failed", error)
                                finishCapture("capture reader failed", CaptureStatus.error(
                                    "playback capture ended", "Choose the source again and approve Android's capture prompt",
                                ))
                            },
                        )
                    } },
                )
            }.also { it.start() }
            sourceWake.captureChanged(recording = running, projection = projection === proj)
        } catch (error: RuntimeException) {
            Log.e(TAG, "capture reader could not start", error)
            finishCapture("capture reader could not start", CaptureStatus.error(
                "playback capture could not start", "Stop the source and try capture again",
            ))
            return START_NOT_STICKY
        }
        Log.i(TAG, "playback capture running")
        // MediaProjection consent cannot be renewed silently after process death.
        return START_NOT_STICKY
    }

    private fun startRoot(): Int {
        val check = if (BuildConfig.DEBUG) RootCaptureService.takeCheck() else null
        rootCheck = check
        val sourceRevision = PlaybackService.localSourcePublication.current.revision
        if (check != null && !check.accepts()) {
            finishCapture("controlled request cancelled", CaptureStatus.idle())
            return START_NOT_STICKY
        }
        if (check == null && !RootCaptureSettings.enabled(this)) {
            finishCapture("root opt-in missing", CaptureStatus.error("root capture is off", "Enable ROOT CAPTURE in the hidden bestiary first"))
            return START_NOT_STICKY
        }
        publishStatus(CaptureStatus(STATE_STARTING, "root capture starting", RootCapturePolicy.CAPABILITY, false))
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTIF_ID, buildNotification())
            val session = RootCaptureSession(applicationContext, if (check == null) 2 else 3)
            rootSession = session
            reader = Thread({
                var inputShown = false
                session.run(
                    ready = {
                        val accepted = CompletableFuture<Boolean>()
                        main.post {
                            try {
                                if (owner === this && !cleanedUp && rootSession === session && session.live &&
                                    PlaybackService.localSourcePublication.accepts(sourceRevision) &&
                                    (check == null || (check.accepts() && MicController.quiescent() && !PlaybackService.ownsLocal() && !PlaybackService.ownsRelay()))) {
                                    PhosphorNative.deckSetPaused(true)
                                    PhosphorNative.setRingActive(true)
                                    running = true
                                    metadataBridgeActive = true
                                    startService(BackgroundLifecycle.stamp(Intent(this, PlaybackService::class.java)
                                        .setAction(PlaybackService.ACTION_CAPTURE_STARTED)
                                        .putExtra(EXTRA_CAPTURE_OWNER, captureOwnerId), taskRevision))
                                    sourceWake.rootChanged(recording = true, helper = session.live)
                                    publishStatus(CaptureStatus(STATE_FLOWING, "root connected · waiting for input", RootCapturePolicy.CAPABILITY, true))
                                    accepted.complete(true)
                                } else accepted.complete(false)
                            } catch (error: Exception) { accepted.completeExceptionally(error) }
                        }
                        check(accepted.get(2, java.util.concurrent.TimeUnit.SECONDS)) { "Root READY owner was retired" }
                        check?.ready(session.generation)
                    },
                    samples = { pcm, normalized ->
                        if (running && rootSession === session && owner === this) {
                            PhosphorNative.pushCaptureSamples(normalized, normalized.size)
                            check?.samples(session.generation, pcm, normalized.size / 2)
                            if (!inputShown) {
                                inputShown = true
                                main.post {
                                    if (owner === this && !cleanedUp && rootSession === session && running)
                                        publishStatus(CaptureStatus(STATE_FLOWING, "root connected · samples arriving", RootCapturePolicy.CAPABILITY, true))
                                }
                            }
                        }
                    },
                    idle = { inputShown = false; main.post {
                        if (owner === this && !cleanedUp && rootSession === session && running) {
                            publishStatus(CaptureStatus(STATE_FLOWING, "root connected · no input", RootCapturePolicy.CAPABILITY, true))
                        }
                    } },
                )
                main.post {
                    if (owner === this && !cleanedUp && rootSession === session) {
                        val result = session.completion.getNow(null)
                        finishCapture("root session ended", if (check != null && result?.error == null) CaptureStatus.idle() else
                            CaptureStatus.error("root capture ended", RootCaptureSettings.fix(result?.error)))
                    }
                }
            }, "root-capture-${session.generation}").also { it.start() }
        } catch (error: Exception) {
            finishCapture("root startup failed", CaptureStatus.error("root capture could not start", RootCaptureSettings.fix(error.message)))
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun finishCapture(reason: String, status: CaptureStatus, retry: Boolean = false) {
        sourceWake.stop()
        if (cleanedUp) {
            if (retry && stopCompletion.result.isDone && stopCompletion.result.getNow(null) == ReaderStop.TIMEOUT) {
                val completion = stopCompletion.retry()
                retirement.add(this, completion.result)
                Thread({
                    val error = ReaderStop.finish(reader, {}, {}, {})
                    main.post {
                        publishStatus(if (error == null) status else CaptureStatus.error("capture stop failed", error))
                        completion.cleanupFinished(error)
                        if (stopCompletion.shouldStopService(error)) stopSelf()
                    }
                }, "capture-stop-retry").start()
            }
            return
        }
        cleanedUp = true
        retirement.add(this, stopCompletion.result)
        if (running) Log.i(TAG, "capture stopped: $reason")
        // An idle STOP service never activated the ring and must not darken an old local deck.
        val ownedRing = running
        running = false
        val oldRecord = record
        val oldReader = reader
        val oldRoot = rootSession
        oldRoot?.requestStop()
        record = null
        // Null first: MediaProjection.stop() synchronously calls our callback on some
        // builds, and a second stop must be a harmless no-op rather than recursion.
        val oldProjection = projection
        projection = null
        Thread({
            val rootError = oldRoot?.awaitStop()
            val readerError = ReaderStop.finish(
                reader = oldReader,
                stop = { if (oldReader != null) oldRecord?.stop() },
                release = { oldRecord?.release() },
                cleanup = {
                    try {
                        oldProjection?.stop()
                    } finally {
                        cleanupOwnedSource(ownedRing) { PhosphorNative.setRingActive(false) }
                    }
                },
            )
            val error = rootError ?: readerError
            main.post {
                rootCheck?.finished(oldRoot?.generation, oldRoot?.completion?.getNow(null), error)
                rootCheck = null
                if (error == null) rootSession = null
                if (metadataBridgeActive) {
                    metadataBridgeActive = false
                    PlaybackService.captureStopped(captureOwnerId)
                }
                publishStatus(if (error == null) status else CaptureStatus.error(
                    "capture stop failed", error,
                ))
                if (!destroyed) stopForeground(STOP_FOREGROUND_REMOVE)
                // Keep a failed stop available to subsequent request-specific retries.
                stopCompletion.cleanupFinished(error)
                if (stopCompletion.shouldStopService(error)) stopSelf()
            }
        }, "capture-stop").start()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        BackgroundLifecycle.removeTask(this, taskRevision)
    }

    override fun onDestroy() {
        destroyed = true
        sourceWake.destroy()
        if (!cleanedUp) finishCapture("service destroyed", CaptureStatus.idle())
        if (owner === this) owner = null
        super.onDestroy()
        stopCompletion.ownerDestroyed()
    }

    private fun publishStatus(status: CaptureStatus) {
        if (owner != null && owner !== this) return
        val observed = status.copy(sequence = statusSequence.incrementAndGet(), backend = backend)
        lifecycleState = observed.state
        lastStatus = observed
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATE, status.state)
                .putExtra(EXTRA_MESSAGE, status.message)
                .putExtra(EXTRA_FIX, status.fix)
                .putExtra(EXTRA_LIVE, status.live)
                .putExtra(EXTRA_SEQUENCE, observed.sequence)
                .putExtra(EXTRA_MIC_REQUEST, observed.micRequest)
                .putExtra(EXTRA_BACKEND, backend.name)
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
        @Volatile private var owner: CaptureService? = null
        internal fun stopIntent(context: android.content.Context) = Intent(context, owner?.javaClass ?: CaptureService::class.java).setAction(ACTION_STOP)
        internal fun rootOwned() = owner?.backend == CaptureBackend.ROOT
        internal fun rootObservation(): Pair<Long, Long>? = owner?.rootSession?.takeIf { it.live }?.let { it.generation to it.frames }
        internal fun stopCheck(check: RootCaptureCheck): CompletableFuture<String?> =
            if (owner?.rootCheck === check) stopExisting() else CompletableFuture.completedFuture(null)
        internal fun quiescent() = owner == null && retirement.pending.isDone && retirement.pending.getNow(null) == null
        internal fun hasLiveWakeSource(): Boolean = owner?.sourceWake?.live == true
        private val captureOwners = AtomicLong()
        private val retirement = SourceRetirement()
        internal const val EXTRA_CAPTURE_OWNER = "capture_owner"
        internal fun isOwner(id: Long): Boolean = owner?.let {
            it.captureOwnerId == id && !it.cleanedUp && it.running
        } == true

        internal fun ownsCapture(): Boolean = owner?.let {
            !it.cleanedUp && RootCapturePolicy.owns(it.backend, it.running, it.projection != null, it.record != null, it.rootSession?.live == true)
        } == true

        internal fun currentOwnerId(): Long? = owner?.captureOwnerId?.takeIf { ownsCapture() }

        internal fun removeTask(keep: Boolean) {
            if (!keep) {
                val current = owner
                current?.stopCompletion?.removeTask()
                stopExisting()
                if (current != null && current.stopCompletion.result.isDone &&
                    current.stopCompletion.shouldStopService(current.stopCompletion.result.getNow(null))) {
                    current.stopSelf()
                }
            }
        }

        internal fun stopExisting(): CompletableFuture<String?> {
            retryRetiredStops()
            val current = owner ?: return retirement.pending
            current.micStopStatus.select(null)
            current.finishCapture("owner stopped", CaptureStatus.idle(), retry = true)
            return retirement.pending
        }

        private fun retryRetiredStops() {
            retirement.retryFailed { retired ->
                (retired as CaptureService).finishCapture("retry retired capture", CaptureStatus.idle(), retry = true)
            }
        }

        private const val TAG = "phosphor-mobil3"
        const val EXTRA_RESULT = "projection_result"
        const val ACTION_STOP = "dev.phosphor.mobil3.CAPTURE_STOP"
        const val EXTRA_STOP_REQUEST = "stop_request"
        const val EXTRA_STOP_REPLY = "stop_reply"
        const val EXTRA_STOP_ERROR = "stop_error"
        const val EXTRA_STOP_OWNED = "stop_owned"
        const val EXTRA_MIC_REQUEST = "mic_request"
        const val ACTION_STATUS = "dev.phosphor.mobil3.CAPTURE_STATUS"
        const val EXTRA_BACKEND = "capture_backend"
        const val EXTRA_STATE = "capture_state"
        const val EXTRA_MESSAGE = "capture_message"
        const val EXTRA_FIX = "capture_fix"
        const val EXTRA_LIVE = "capture_live"
        const val EXTRA_SEQUENCE = "capture_sequence"
        const val STATE_IDLE = "idle"
        const val STATE_STARTING = "starting"
        const val STATE_FLOWING = "flowing"
        const val STATE_PERMISSION_NEEDED = "permission_needed"
        const val STATE_ERROR = "error"
        const val RESULT_OK_CODE = -1 // Activity.RESULT_OK
        private const val NOTIF_ID = 1002
        private const val CHANNEL = "capture"
        private val statusSequence = AtomicLong()

        @Volatile
        private var lastStatus = CaptureStatus.idle()

        fun currentStatus(): CaptureStatus = lastStatus

        fun statusFrom(intent: Intent): CaptureStatus = CaptureStatus(
            state = intent.getStringExtra(EXTRA_STATE) ?: STATE_IDLE,
            message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
            fix = intent.getStringExtra(EXTRA_FIX).orEmpty(),
            live = intent.getBooleanExtra(EXTRA_LIVE, false),
            sequence = intent.getLongExtra(EXTRA_SEQUENCE, 0),
            micRequest = intent.getStringExtra(EXTRA_MIC_REQUEST),
            backend = if (intent.getStringExtra(EXTRA_BACKEND) == CaptureBackend.ROOT.name) CaptureBackend.ROOT else CaptureBackend.STANDARD,
        )
    }

    data class CaptureStatus(
        val state: String,
        val message: String,
        val fix: String,
        val live: Boolean,
        val sequence: Long = 0,
        val micRequest: String? = null,
        internal val backend: CaptureBackend = CaptureBackend.STANDARD,
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
