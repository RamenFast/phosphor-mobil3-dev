package dev.phosphor.mobil3

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit

/** Device-local intent only. No grant or availability is serialized. Main-thread mutation. */
internal object RootCaptureSettings {
    private var revision = 0L
    private val main = Handler(Looper.getMainLooper())
    private var authorization: RootCaptureSession? = null
    @Volatile var busy = false
        private set
    @Volatile var message = RootCapturePolicy.DEFERRED
        private set
    private fun prefs(context: Context) = context.getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).let {
        RootCapturePolicy.backend(it.getBoolean(RootCapturePolicy.ENABLED, false), it.getBoolean(RootCapturePolicy.PROFILE_ACK, false)) == CaptureBackend.ROOT
    }
    fun enable(context: Context) {
        if (!RootCapturePolicy.PRODUCT_AVAILABLE) {
            message = RootCapturePolicy.DEFERRED
            return
        }
        if (busy || enabled(context)) return
        ++revision
        val app = context.applicationContext
        prefs(app).edit { putBoolean(RootCapturePolicy.PROFILE_ACK, true) }
        check(RootCapturePolicy.mayAuthorize(true, prefs(app).getBoolean(RootCapturePolicy.PROFILE_ACK, false)))
        busy = true
        message = "Checking existing KernelSU authorization · no audio capture"
        val session = RootCaptureSession(app, 1)
        authorization = session
        Thread({
            session.run()
            main.post {
                if (authorization === session) {
                    val result = session.completion.getNow(null)
                    val accepted = result?.authorized == true && result.cleanup
                    prefs(app).edit { putBoolean(RootCapturePolicy.ENABLED, accepted) }
                    message = if (accepted) "Enabled · ${RootCapturePolicy.CAPABILITY}" else fix(result?.error)
                    authorization = null
                    busy = false
                }
            }
        }, "root-authorization").start()
    }
    fun disable(context: Context) {
        if (!RootCapturePolicy.PRODUCT_AVAILABLE) return
        val request = ++revision
        prefs(context).edit { putBoolean(RootCapturePolicy.ENABLED, false) }
        val old = authorization
        authorization = null
        old?.requestStop()
        busy = false
        message = "Off · root owner is retiring"
        val stopped = if (CaptureService.rootOwned()) CaptureService.stopExisting() else null
        if (stopped != null) stopped.thenAccept { error -> main.post { if (revision == request) message = error ?: "Off · no helper is probed" } }
        else if (old != null) old.completion.thenAccept { result -> main.post { if (revision == request) message = if (result.cleanup) "Off · no helper is probed" else fix(result.error) } }
        else message = "Off · no helper is probed"
    }
    fun fix(cause: String?): String = when {
        cause == null -> "Retry root capture, or explicitly choose standard capture"
        cause.contains("ksu_fd") || cause.contains("ksu_grant") || cause.contains("uid_zero") ->
            "Authorization unavailable: $cause. In the existing KernelSU manager, verify Phosphor's grant and supported Default/inherited profile, then retry"
        cause.contains("provider") || cause.contains("namespace") ->
            "Unsupported provider/profile: $cause. This build requires KernelSU32525/UAPI2/flags5 and an existing Default/inherited profile. Use standard capture instead"
        else -> "$cause. Retry root, or explicitly choose standard capture. Do not change system policy"
    }
}
