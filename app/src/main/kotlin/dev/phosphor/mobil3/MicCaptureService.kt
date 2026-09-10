package dev.phosphor.mobil3

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.content.ContextCompat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/** The sole microphone owner. No exported commands, automatic restart or Activity recorder. */
class MicCaptureService : Service() {
    private val ownerId = ids.incrementAndGet()
    private val taskRevision = BackgroundLifecycle.policy.revision
    private val wake by lazy { SourceWakeLock.forOwner(this, "microphone") }
    private var recorder: MicController? = null
    private var session: CaptureMixSession? = null
    private var attachment = 0L
    private var standalone = false
    private var stopping = false
    private var destroyed = false
    private var completion = SourceStopCompletion()
    private var startReply: ((String?) -> Unit)? = null
    private var startGuard: (() -> Boolean)? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); if (owner == null) owner = this }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            if (intent.getLongExtra(OWNER, -1) == ownerId) stop(null)
            else if (session == null) stopSelf()
            return START_NOT_STICKY
        }
        val request = pending?.takeIf { it.id == intent?.getLongExtra(REQUEST, -1) }
        if (request == null || owner !== this || stopping || !BackgroundLifecycle.accepts(intent) || !request.current()) {
            request?.let { pending = null; it.done("Microphone start was cancelled. Return to the app and retry") }
            if (session == null) stopSelf()
            return START_NOT_STICKY
        }
        pending = null
        if (session != null) { request.done("Microphone already active. Stop it before changing input"); return START_NOT_STICKY }
        startReply = request.done
        startGuard = request.current
        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION, notification)
            check(request.current()) { "Microphone request retired. Return to the app and retry" }
            val target = request.target
            if (target != null) check(CaptureService.mixSession() === target && target.live) { "Playback capture ended. Start it again" }
            standalone = target == null
            val mix = target ?: CaptureMixSession(true, request.settings) { message -> main.post { if (owner === this) stop(message) } }
            session = mix
            mix.settings(request.settings)
            attachment = mix.attach()
            val adapter = MicController(this, mix, attachment,
                changed = { if (owner === this && !stopping) { wake.microphoneChanged(true, false); publish() } },
                failed = { error -> if (owner === this && !stopping) stop(error) })
            recorder = adapter
            adapter.start { error ->
                if (owner !== this || stopping) { replyStart(error ?: "Microphone request retired"); return@start }
                val accepted = startGuard?.invoke() == true
                replyStart(if (accepted) error else "Microphone request retired. Return to the app and retry")
                if (error != null || !accepted) stop(error ?: "Microphone request retired") else publish()
            }
        } catch (error: RuntimeException) { stop("Microphone could not start: ${error.message}. Return to the visible app and retry") }
        return START_NOT_STICKY
    }
    private fun replyStart(error: String?) {
        val reply = startReply; startReply = null; startGuard = null
        reply?.invoke(error)
    }
    private fun publish() {
        if (owner !== this) return
        observation = recorder?.observation()
        detail = recorder?.detail() ?: detail
        observers.toList().forEach { it() }
    }
    private fun stop(error: String?, retry: Boolean = false) {
        if (stopping) {
            if (retry && completion.result.isDone && completion.result.getNow(null) != null) {
                completion = SourceStopCompletion().also { if (destroyed) it.ownerDestroyed() }
                retirement.add(this, completion.result)
                session?.let { sessionStops[it.id] = completion.result }
                finish(error)
            }
            return
        }
        stopping = true
        replyStart(error ?: "Microphone stopped before startup completed")
        recorder?.cancel()
        session?.detach(attachment)
        if (standalone) session?.invalidate()
        wake.stop()
        detail = error ?: "Microphone stopped"
        observation = recorder?.observation()?.copy(life = if (error == null) SignalLife.STOPPING else SignalLife.FAILED,
            reason = detail, contributing = false)
        retirement.add(this, completion.result)
        session?.let { sessionStops[it.id] = completion.result }
        sessionStops.entries.removeAll { it.key != session?.id && it.value.isDone && it.value.getNow(null) == null }
        observers.toList().forEach { it() }
        finish(error)
    }
    private fun finish(reason: String?) {
        val finishing = completion
        Thread({
            val readerError = recorder?.finish()
            val mixerError = if (standalone && readerError == null) session?.finish() else null
            val error = readerError ?: mixerError
            main.post {
                if (completion !== finishing) return@post
                detail = error ?: reason ?: "Microphone stopped"
                observation = observation?.copy(life = when { error != null -> SignalLife.CLEANUP_UNCONFIRMED; reason != null -> SignalLife.FAILED; else -> SignalLife.ENDED },
                    reason = detail, contributing = false)
                finishing.cleanupFinished(error)
                if (!destroyed) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                observers.toList().forEach { it() }
            }
        }, "microphone-stop-$ownerId").start()
    }
    override fun onTaskRemoved(rootIntent: Intent?) { BackgroundLifecycle.removeTask(this, taskRevision) }
    override fun onDestroy() {
        destroyed = true
        if (!stopping) stop(null)
        wake.destroy()
        if (owner === this) owner = null
        completion.ownerDestroyed()
        super.onDestroy()
    }
    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Microphone visualization", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, ownerId.toInt(), Intent(this, MicCaptureService::class.java)
            .setAction(STOP).putExtra(OWNER, ownerId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentTitle("Phosphor microphone is active")
            .setContentText("Sound becomes light. No audio is saved or monitored to speakers.")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Stop microphone", stop).build()).build()
    }
    companion object {
        private const val CHANNEL = "phosphor_microphone"
        private const val NOTIFICATION = 4103
        private const val STOP = "dev.phosphor.mobil3.MIC_STOP"
        private const val OWNER = "mic_owner"
        private const val REQUEST = "mic_request"
        private val ids = AtomicLong()
        private val main = Handler(Looper.getMainLooper())
        private val retirement = SourceRetirement()
        private val sessionStops = linkedMapOf<Long, CompletableFuture<String?>>()
        private data class Start(val id: Long, val target: CaptureMixSession?, val settings: CaptureMixSettings,
                                 val current: () -> Boolean, val done: (String?) -> Unit)
        private var pending: Start? = null
        @Volatile private var owner: MicCaptureService? = null
        @Volatile private var observation: SignalInput? = null
        @Volatile private var detail = "Microphone off"
        private val observers = linkedSetOf<() -> Unit>()
        internal fun observe(callback: () -> Unit) { observers += callback; callback() }
        internal fun unobserve(callback: () -> Unit) { observers -= callback }
        internal fun signalObservation(): SignalInput? = observation
        internal fun status(): String = owner?.takeIf { !it.stopping }?.recorder?.detail() ?: detail
        internal fun isRecording() = owner?.recorder?.isRecording() == true
        internal fun ownsSource() = owner?.session != null || !retirement.pending.isDone
        internal fun hasLiveWakeSource() = owner?.let { !it.stopping && it.recorder?.isRecording() == true } == true
        internal fun established() = owner?.let { !it.stopping && it.startReply == null && it.recorder?.isRecording() == true } == true
        internal fun standalone() = owner?.standalone == true
        internal fun quiescent() = owner == null && retirement.pending.isDone && retirement.pending.getNow(null) == null
        internal fun cancelStart() {
            pending?.done?.invoke("Microphone start cancelled"); pending = null
            owner?.takeIf { it.startReply != null }?.stop("Microphone start cancelled")
        }
        internal fun request(context: Context, target: CaptureMixSession?, settings: CaptureMixSettings,
                             current: () -> Boolean, done: (String?) -> Unit) {
            if (!current()) { done("Return to the visible app and retry"); return }
            if (!quiescent()) { done("Microphone is still active or stopping. Stop it and retry"); return }
            pending?.done?.invoke("Microphone start superseded")
            val request = Start(ids.incrementAndGet(), target, settings, current, done)
            pending = request
            try {
                ContextCompat.startForegroundService(context, BackgroundLifecycle.stamp(Intent(context, MicCaptureService::class.java)
                    .putExtra(REQUEST, request.id), BackgroundLifecycle.policy.revision))
            } catch (error: RuntimeException) {
                if (pending === request) pending = null
                done("Microphone foreground start failed: ${error.message}. Return to the app and retry")
            }
        }
        internal fun stop() { stopForLocal(ids.incrementAndGet()) { _, _, _ -> } }
        internal fun stopForLocal(requestId: Long, reply: (Long, String?, Boolean) -> Unit) {
            fun execute() {
                pending?.done?.invoke("Microphone start cancelled"); pending = null
                retirement.retryFailed { (it as MicCaptureService).stop(null, retry = true) }
                val current = owner
                val owned = current?.session != null || !retirement.pending.isDone
                current?.stop(null, retry = true)
                retirement.pending.thenAccept { error -> reply(requestId, error, owned) }
            }
            if (Looper.myLooper() == main.looper) execute() else main.post { execute() }
        }
        internal fun stopForSession(target: CaptureMixSession): CompletableFuture<String?> {
            val result = CompletableFuture<String?>()
            fun execute() {
                if (pending?.target === target) { pending?.done?.invoke("Playback capture ended"); pending = null }
                val current = owner
                if (current?.session === target) {
                    current.stop(null, retry = true)
                    current.completion.result.thenAccept { result.complete(it) }
                } else {
                    retirement.retryFailed { retired ->
                        (retired as MicCaptureService).takeIf { it.session === target }?.stop(null, retry = true)
                    }
                    (sessionStops[target.id] ?: CompletableFuture.completedFuture(null)).thenAccept { result.complete(it) }
                }
            }
            if (Looper.myLooper() == main.looper) execute() else main.post { execute() }
            return result
        }
        internal fun removeTask(keep: Boolean) { cancelStart(); if (!keep) stop() }
    }
}
