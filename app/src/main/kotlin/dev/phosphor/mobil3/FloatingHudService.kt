package dev.phosphor.mobil3

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/** Presentation only. Audio stays in the existing source service and MediaSession. */
class FloatingHudService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var host: SurfaceHost? = null
    private var added = false
    private var surfaceReady = false
    private var retired = false
    private var screenRegistered = false
    private var appOpsRegistered = false
    private var controller: MediaController? = null
    private var controllerGeneration = 0L
    private val connection = HudConnection<com.google.common.util.concurrent.ListenableFuture<MediaController>> {
        MediaController.releaseFuture(it)
    }
    private var taskRevision = -1L
    private var requestToken = -1L
    private lateinit var wm: WindowManager
    private lateinit var viewContext: Context
    private lateinit var params: WindowManager.LayoutParams
    private var info: TextView? = null
    private var presentationStatus = "HUD opening"
    private var legacyInsets = android.graphics.Insets.NONE
    private var play: Button? = null
    private var previous: Button? = null
    private var next: Button? = null
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF || locked()) finishHud("HUD closed on display lock")
        }
    }
    private val appOps = AppOpsManager.OnOpChangedListener { op, pkg ->
        if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && pkg == packageName) main.post {
            if (!Settings.canDrawOverlays(this)) finishHud("Overlay access removed. Allow access in the app before showing again")
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CLOSE) { finishHud("HUD off"); return START_NOT_STICKY }
        if (intent?.action != SHOW || !session.accepts(intent.getLongExtra(REQUEST, -1)) || retired) {
            if (!added) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (added) return START_NOT_STICKY
        requestToken = intent.getLongExtra(REQUEST, -1)
        instance = this
        taskRevision = BackgroundLifecycle.policy.revision
        runCatching {
            check(Settings.canDrawOverlays(this) && !locked()) { "Overlay access or unlocked display is unavailable" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating scope", NotificationManager.IMPORTANCE_LOW))
            val back = PendingIntent.getActivity(this, 40, returnIntent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val close = PendingIntent.getService(this, 41, Intent(this, FloatingHudService::class.java).setAction(CLOSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Phosphor floating scope")
                .setContentText("Existing source unchanged. Tap to return to the app")
                .setContentIntent(back).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Close HUD", close).build()).build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(304, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(304, notification)
            viewContext = if (Build.VERSION.SDK_INT >= 30) {
                val display = getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
                createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } else this
            wm = viewContext.getSystemService(WindowManager::class.java)
            val saved = HudPolicy.read(preferences().all)
            params = WindowManager.LayoutParams(dp(saved.width), dp(saved.height),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply {
                    gravity = Gravity.TOP or Gravity.LEFT; x = dp(16); y = dp(48)
                    if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
                }
            val panel = object : LinearLayout(viewContext) {
                override fun onWindowVisibilityChanged(visibility: Int) {
                    super.onWindowVisibilityChanged(visibility)
                    if (added && !retired) host?.let {
                        SurfaceHost.hudVisible(it, HudPolicy.presentationAllowed(
                            visibility == View.VISIBLE,
                            getSystemService(PowerManager::class.java).isInteractive,
                            !getSystemService(KeyguardManager::class.java).isKeyguardLocked,
                            Settings.canDrawOverlays(this@FloatingHudService)))
                    }
                }
            }.apply { orientation = LinearLayout.VERTICAL }
            panel.setOnApplyWindowInsetsListener { _, insets ->
                if (Build.VERSION.SDK_INT < 30 && legacyInsets != insets.stableInsets) {
                    legacyInsets = insets.stableInsets
                    main.post { updateBounds() }
                }
                insets
            }
            root = panel
            val header = row()
            val drag = label("PHOSPHOR · drag").apply { contentDescription = "Drag floating scope" }
            header.addView(drag, LinearLayout.LayoutParams(0, dp(48), 1f))
            drag.setOnTouchListener(gesture(false))
            header.addView(button("↗", "Return to app") { returnToApp() })
            header.addView(button("×", "Close floating scope") { finishHud("HUD off") })
            panel.addView(header)
            val scope = SurfaceHost(viewContext, saved.background == HudPolicy.Background.TRANSPARENT, status = { text ->
                presentationStatus = text
                publish(text)
                syncTransport()
            }, failed = { main.post { if (!retired) finishHud(presentationStatus) } },
                presented = {
                    if (!surfaceReady && !retired) { surfaceReady = true; publish(status) }
                    if (!retired) controller?.let { host?.metadataChanged(it) }
                })
            var heldX = 0f
            var heldY = 0f
            var heldSpan = 0f
            scope.view.setOnTouchListener { view, event ->
                if (PhosphorNative.displayPauseState() and 1 == 0) return@setOnTouchListener false
                val x = (0 until event.pointerCount).map { event.getX(it) }.average().toFloat()
                val y = (0 until event.pointerCount).map { event.getY(it) }.average().toFloat()
                val span = if (event.pointerCount >= 2) kotlin.math.hypot(
                    event.getX(0) - event.getX(1), event.getY(0) - event.getY(1)) else 0f
                if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                    PhosphorNative.inspectHeld((x - heldX) / view.width.coerceAtLeast(1),
                        (y - heldY) / view.height.coerceAtLeast(1),
                        if (span > 1f && heldSpan > 1f) span / heldSpan else 1f, false)
                }
                heldX = x; heldY = y; heldSpan = span
                true
            }
            host = scope
            panel.addView(scope.view, LinearLayout.LayoutParams(-1, 0, 1f))
            val transport = row()
            transport.addView(button("FIT", "Reset held image inspection") { PhosphorNative.inspectHeld(0f, 0f, 1f, true) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            transport.addView(button("SRC", "Choose source in app") { returnToApp(source = true) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            previous = button("‹", "Previous track") { controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) }?.seekToPrevious() }
            play = button("…", "Source transport unavailable") {
                controller?.takeIf { it.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }?.let { c ->
                    if (playing(c)) c.pause() else c.play()
                } ?: run {
                    PhosphorNative.setDisplayPaused(PhosphorNative.displayPauseState() and 1 == 0)
                    syncTransport()
                }
            }
            next = button("›", "Next track") { controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) }?.seekToNext() }
            listOf(previous, play, next).forEach { transport.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f)) }
            syncTransport()
            panel.addView(transport)
            val statusRow = row()
            info = label("Connecting existing source").apply { textSize = 10f }
            statusRow.addView(info, LinearLayout.LayoutParams(0, dp(48), 1f))
            statusRow.addView(button("↘", "Resize floating scope") {}.apply { setOnTouchListener(gesture(true)) })
            panel.addView(statusRow)
            clampBounds()
            wm.addView(panel, params)
            added = true
            check(session.shown(requestToken)) { "HUD request was retired" }
            ContextCompat.registerReceiver(this, screen, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            screenRegistered = true
            getSystemService(AppOpsManager::class.java).startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, appOps)
            appOpsRegistered = true
            publish("HUD opening · ${saved.background.name} requested")
            SurfaceHost.hud(scope)
            connectController()
        }.onFailure { finishHud("HUD could not open: ${it.message ?: "return to the app and retry"}") }
        return START_NOT_STICKY
    }
    private fun preferences() = getSharedPreferences(PhosphorApplication.PREFERENCES_NAME, MODE_PRIVATE)
    private fun locked(): Boolean = getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
        !getSystemService(PowerManager::class.java).isInteractive
    private fun density() = if (::viewContext.isInitialized) viewContext.resources.displayMetrics.density else resources.displayMetrics.density
    private fun dp(value: Int) = (value * density()).toInt().coerceAtLeast(1)
    private fun row() = LinearLayout(viewContext).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.rgb(19, 15, 23)) }
    private fun label(text: String) = TextView(viewContext).apply {
        this.text = text; setTextColor(Color.WHITE); typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(4), 0)
    }
    private fun button(text: String, description: String, action: () -> Unit) = Button(viewContext).apply {
        this.text = text; contentDescription = description; typeface = Typeface.MONOSPACE
        setTextColor(Color.rgb(229, 196, 235)); setBackgroundColor(Color.rgb(32, 25, 38))
        setPadding(0, 0, 0, 0); minWidth = dp(48); minimumWidth = dp(48)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { action() }
    }
    private fun gesture(resize: Boolean): View.OnTouchListener {
        var startX = 0f; var startY = 0f
        var x = 0; var y = 0; var width = 0; var height = 0
        return View.OnTouchListener { view, event ->
            if (!added || retired) return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    x = params.x; y = params.y; width = params.width; height = params.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt(); val dy = (event.rawY - startY).toInt()
                    if (resize) { params.width = width + dx; params.height = height + dy }
                    else { params.x = x + dx; params.y = y + dy }
                    updateBounds()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (resize) preferences().edit {
                        putInt(HudPolicy.WIDTH, (params.width / density()).toInt().coerceIn(240, 640))
                        putInt(HudPolicy.HEIGHT, (params.height / density()).toInt().coerceIn(240, 640))
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    true
                }
                else -> false
            }
        }
    }
    private fun clampBounds() {
        val metrics = viewContext.resources.displayMetrics
        val area = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds else android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val insets = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()) else legacyInsets
        val b = HudPolicy.bounds(params.x - insets.left, params.y - insets.top,
            params.width.coerceAtMost(dp(640)), params.height.coerceAtMost(dp(640)),
            area.width() - insets.left - insets.right, area.height() - insets.top - insets.bottom, dp(240))
        params.x = b.x + insets.left; params.y = b.y + insets.top; params.width = b.width; params.height = b.height
    }
    private fun updateBounds() {
        if (!added || retired) return
        runCatching { clampBounds(); wm.updateViewLayout(root, params) }
            .onFailure { finishHud("HUD window unavailable. Return to the app and retry") }
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); updateBounds() }
    private fun playing(c: MediaController) = CaptureMirrorPolicy.displayedPlaying(
        c.mediaMetadata.extras?.getString("source") == "capture", c.isPlaying,
        c.sessionExtras.getInt(CaptureMirrorPolicy.OBSERVED_STATE))
    private fun syncTransport() {
        val c = controller
        val source = c?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: c?.mediaMetadata?.extras?.getString("source") ?: "Choose source in app"
        val held = PhosphorNative.displayPauseState()
        val display = dev.phosphor.mobil3.ui.PauseDisplayPolicy.status(
            held and 1 != 0, held and 2 != 0, held and 4 != 0,
            (c != null && playing(c)) || (CaptureService.ownsCapture() && CaptureService.currentStatus().live),
        )
        info?.text = "$presentationStatus\n$source · $display"
        val controllable = c?.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) == true
        play?.isEnabled = true
        previous?.isEnabled = c?.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) == true
        next?.isEnabled = c?.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) == true
        play?.text = if (!controllable) { if (held and 1 != 0) "LIVE" else "HOLD" } else if (c != null && playing(c)) "Ⅱ" else "▷"
        play?.contentDescription = if (!controllable) "Pause or resume display only. Audio transport is unchanged" else if (c != null && playing(c)) "Pause source" else "Play source"
    }
    private fun connectController() {
        val generation = ++controllerGeneration
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) { syncTransport() }
                override fun onDisconnected(controller: MediaController) { if (this@FloatingHudService.controller === controller) { this@FloatingHudService.controller = null; syncTransport() } }
            }).buildAsync()
        connection.retain(future)
        future.addListener({
            if (retired || generation != controllerGeneration || !connection.accepts(future)) return@addListener
            val c = runCatching { future.get() }.getOrNull()
            controller = c
            c?.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { syncTransport() }
                override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                    if (!retired) controller?.let { host?.metadataChanged(it) }
                }
            })
            syncTransport()
            c?.let { host?.metadataChanged(it) }
        }, MoreExecutors.directExecutor())
    }
    private fun returnToApp(source: Boolean = false) {
        runCatching { startActivity(returnIntent(this).putExtra(SOURCE, source)) }
            .onSuccess { finishHud("HUD returned to app") }
            .onFailure { publish("Could not return to app. Open Phosphor from its launcher") }
    }
    private fun finishHud(message: String) { try { retire(message) } finally { stopSelf() } }
    private fun retire(message: String) {
        if (retired) return
        retired = true
        ++controllerGeneration
        main.removeCallbacksAndMessages(null)
        val cleanup = HudCleanup()
        cleanup.run("media controller") { connection.close() }; controller = null
        if (screenRegistered) { cleanup.run("screen listener") { unregisterReceiver(screen) }; screenRegistered = false }
        if (appOpsRegistered) { cleanup.run("permission listener") { getSystemService(AppOpsManager::class.java).stopWatchingMode(appOps) }; appOpsRegistered = false }
        val closingHost = host
        cleanup.run("surface") { closingHost?.close() }; host = null
        root?.let { view -> if (added || view.isAttachedToWindow) cleanup.run("window") { wm.removeViewImmediate(view) } }
        added = false; surfaceReady = false; root = null
        cleanup.run("foreground notification") { stopForeground(STOP_FOREGROUND_REMOVE) }
        val reason = closingHost?.failure ?: message
        val result = if (cleanup.failures.isEmpty()) reason else
            "$reason. Cleanup failed: ${cleanup.failures.joinToString()}. Close Phosphor from Android app settings before retrying"
        if (cleanup.failures.isNotEmpty()) android.util.Log.e("PhosphorHud", result)
        if (session.closing(requestToken)) publish(result)
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        try { BackgroundLifecycle.removeTask(this, taskRevision) }
        finally { finishHud("HUD closed with task") }
    }
    override fun onDestroy() {
        retire("HUD off")
        if (instance === this) instance = null
        session.destroyed(requestToken)
        super.onDestroy()
    }
    companion object {
        private const val SHOW = "dev.phosphor.mobil3.HUD_SHOW"
        private const val CLOSE = "dev.phosphor.mobil3.HUD_CLOSE"
        private const val REQUEST = "hud_request"
        private const val CHANNEL = "floating_scope"
        const val RETURN = "floating_hud_return"
        const val SOURCE = "floating_hud_source"
        private val session = HudSession()
        private var instance: FloatingHudService? = null
        private val listeners = linkedSetOf<() -> Unit>()
        var status: String = "HUD off"
            private set
        val active: Boolean get() = session.active
        val presenting: Boolean get() = instance?.let { it.added && it.surfaceReady } == true
        fun observe(listener: () -> Unit) { listeners.add(listener); listener() }
        fun unobserve(listener: () -> Unit) { listeners.remove(listener) }
        private fun publish(message: String) { status = message; listeners.toList().forEach { it() } }
        fun show(context: Context) {
            if (active) return
            val token = session.request() ?: run { publish("HUD is closing. Tap Show again after it closes"); return }
            publish("HUD opening")
            runCatching { ContextCompat.startForegroundService(context, Intent(context, FloatingHudService::class.java).setAction(SHOW).putExtra(REQUEST, token)) }
                .onFailure { session.destroyed(token); publish("HUD could not start. Return to the app and retry") }
        }
        fun hide(context: Context) {
            val token = session.generation
            session.closing(token)
            instance?.finishHud("HUD off") ?: run {
                context.stopService(Intent(context, FloatingHudService::class.java))
                session.destroyed(token)
                publish("HUD off")
            }
        }
        private fun returnIntent(context: Context) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(RETURN, true)
    }
}
