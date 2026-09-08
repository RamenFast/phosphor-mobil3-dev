package dev.phosphor.mobil3

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.phosphor.mobil3.ui.Palette
import dev.phosphor.mobil3.ui.PhosphorScreen
import dev.phosphor.mobil3.ui.RotationDetent
import dev.phosphor.mobil3.ui.ScopeActions
import dev.phosphor.mobil3.ui.ScopeUiState
import dev.phosphor.mobil3.ui.readReducedMotion
import dev.phosphor.mobil3.ui.rollModeExcluding
import dev.phosphor.mobil3.settings.SettingsArchive
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

// Compose chrome overlays the scope SurfaceView. The loaded deck owns transport through one
// MediaController so the console, notification, and lock screen remain consistent.
class MainActivity : ComponentActivity(), ScopeActions {

    private lateinit var ui: ScopeUiState
    private val micWake = SourceWakeLock.forOwner(this, "microphone")
    private val mic = MicController { recording ->
        micWake.microphoneChanged(recording, activityDestroyed)
        if (activityStarted && !activityDestroyed) reassertSourceWake()
    }
    private var taskRevision = -1L
    private var activityRevision = -1L
    private var activityDestroyed = false
    private var activityStarted = false
    private var scopeSurface: SurfaceView? = null
    private val controllerBinding = ActivityControllerBinding()
    private fun taskIsCurrent(): Boolean = !activityDestroyed &&
        BackgroundLifecycle.policy.accepts(taskRevision) && BackgroundLifecycle.policy.acceptsActivity(activityRevision)

    private fun startSourceService(intent: Intent) {
        if (taskIsCurrent()) startService(BackgroundLifecycle.stamp(intent, taskRevision)
            .putExtra(BackgroundLifecycle.EXTRA_ACTIVITY_REVISION, activityRevision))
    }

    private fun startCaptureService(intent: Intent) {
        if (taskIsCurrent()) startForegroundService(BackgroundLifecycle.stamp(intent, taskRevision)
            .putExtra(BackgroundLifecycle.EXTRA_ACTIVITY_REVISION, activityRevision))
    }
    private var micReleaseRevision: Long? = null
    private var captureStatusSequence = 0L
    private var pendingCaptureConsent: Long? = null
    private val micHandoff = MicHandoffPolicy(
        start = { done ->
            if (micRequestIsCurrent()) {
                applyLocalGainPolicy()
                mic.start(done, ::micRequestIsCurrent) { error ->
                    publishMicReaderFailure(ui, { taskIsCurrent() && !isDestroyed }) {
                        runtimePrefs().edit { putString("last_source", "none") }
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                micHandoffCancel()
            }
        },
        publish = { error ->
            ui.live = error == null
            ui.sourceLabel = if (error == null) "mic" else "no source"
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        },
    )
    private var pendingAudioPermission = AudioPermissionPurpose.NONE
    private var controller: MediaController? = null
    private var reduced = false
    private var gainValue = 1.8332275f
    private var lastRandomTrackTitle: String? = null
    private var scopeRotationLockState by mutableStateOf(true)
    private var uiPlacementLockState by mutableStateOf(false)
    private var lockedUiLandscape by mutableStateOf(false)
    private var lockedScopeOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var lockedUiOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    // Route gravity to the beam, individual elements, or the whole chrome for the four lock combinations.
    private var gravityListener: android.hardware.SensorEventListener? = null
    private var lastSourceReopened = false
    private var sourceSelection = 0L
    private var lastSensorDeg = OrientationEventListener.ORIENTATION_UNKNOWN

    /**
     * The cardinal the chrome is currently committed to, or -1 before the first reading.
     *
     * Paired with the two tolerances below to give rotation a detent: once an
     * orientation is taken it holds through a wide sloppy range, and only a decisive
     * turn close to the next cardinal takes it away. A single symmetric window instead
     * flipped the chrome the instant the phone crossed 45°, which is what made rotation
     * feel twitchy.
     */
    private var committedCardinal = RotationDetent.NONE
    private var rotationAuthorityNeedsRouting = true
    private var captureStatusReceiverRegistered = false
    private val tick = Handler(Looper.getMainLooper())
    private val persistGain = Runnable {
        prefs().edit { putFloat("gain", gainValue) }
    }
    private val captureStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CaptureService.ACTION_STATUS) {
                applyCaptureStatus(CaptureService.statusFrom(intent))
            } else if (intent?.action == PlaybackService.ACTION_LOCAL_SOURCE_CHANGED) {
                applyLocalSourcePublication(intent.getLongExtra(PlaybackService.EXTRA_SOURCE_REVISION, -1))
            }
            reassertSourceWake()
        }
    }

    private enum class AudioPermissionPurpose {
        NONE,
        MICROPHONE,
        PLAYBACK_CAPTURE,
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            reassertSourceWake()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                holder.surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
        }
        override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
            PhosphorNative.surfaceCreatedOrChanged(holder.surface, w, h, resources.displayMetrics.density)
            // Restore the persisted focus whenever Android recreates the surface.
            PhosphorNative.setFocus(focusPref)
            reassertSourceWake()
        }
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            PhosphorNative.surfaceDestroyed()
        }
    }

    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (taskIsCurrent()) uri?.let { loadUri(it) }
        }

    // The service owns traversal, staging and open. Binder carries only the tree identity.
    private val openFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (!taskIsCurrent()) return@registerForActivityResult
            uri ?: return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectSource()
                applyLocalGainPolicy()
                startSourceService(Intent(this, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_OPEN_TREE)
                    .putExtra(PlaybackService.EXTRA_TREE_URI, uri.toString())
                    .putExtra(PlaybackService.EXTRA_QUEUE_START, 0))
            }.onFailure {
                Toast.makeText(this, "Folder access failed, choose a readable tree", Toast.LENGTH_LONG).show()
            }
        }

    private val createSettingsArchive =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            saveTuning()
            ui.settingsTransferStatus = "exporting settings…"
            Thread {
                runCatching {
                    val result = SettingsArchive.export(
                        sourcePackage = packageName,
                        sourceVersion = BuildConfig.VERSION_NAME,
                        sourceDistribution = if (BuildConfig.DEBUG) "debug" else "release",
                        exportedAt = java.time.Instant.now().toString(),
                        allPreferences = prefs().all,
                    )
                    val output = contentResolver.openOutputStream(uri, "wt")
                        ?: error("Android did not provide a writable document")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(result.json) }
                    "exported ${result.exportedKeys.size} settings · ${result.contentSha256.take(12)}"
                }.onSuccess { status ->
                    runOnUiThread { ui.settingsTransferStatus = status }
                }.onFailure { error ->
                    runOnUiThread {
                        ui.settingsTransferStatus = "export failed · ${error.message ?: "choose another document"}"
                    }
                }
            }.start()
        }

    private val openSettingsArchive =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            ui.settingsTransferStatus = "checking settings…"
            Thread {
                runCatching {
                    val input = contentResolver.openInputStream(uri)
                        ?: error("Android did not provide a readable document")
                    val text = input.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > SettingsArchive.MAX_BYTES) {
                                throw SettingsArchive.ArchiveException(
                                    "archive_too_large",
                                    "Settings archive exceeds ${SettingsArchive.MAX_BYTES} bytes",
                                    "Choose an original .phossettings export",
                                )
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                    val imported = SettingsArchive.decode(text)
                    val priorValues = preferenceValueSnapshots(prefs().all, imported.values.keys)
                    val editor = prefs().edit()
                    imported.values.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Float -> editor.putFloat(key, value)
                            is String -> editor.putString(key, value)
                            else -> error("unsupported imported preference type for $key")
                        }
                    }
                    if (!editor.commit()) {
                        val restored = restorePreferenceSnapshots(priorValues)
                        error(
                            if (restored) {
                                "Android could not commit imported settings; restored previous settings"
                            } else {
                                "Android could not commit imported settings; previous settings restore also failed"
                            },
                        )
                    }
                    imported
                }.onSuccess { imported ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        restoreTuning()
                        applyScopeRotationPreference()
                        applyImmersive()
                        updatePictureInPictureParams()
                        ui.settingsTransferStatus = buildString {
                            append("imported ${imported.values.size}")
                            append(" from ")
                            append(imported.sourceVersion)
                            if (imported.skippedKeys.isNotEmpty()) {
                                append(" · skipped ${imported.skippedKeys.size} newer fields")
                            }
                        }
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        ui.settingsTransferStatus = when (error) {
                            is SettingsArchive.ArchiveException ->
                                "${error.error} · ${error.fix}"
                            else -> "import failed · ${error.message ?: "choose another archive"}"
                        }
                    }
                }
            }.start()
        }

    private val captureConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val selection = pendingCaptureConsent
            pendingCaptureConsent = null
            if (!taskIsCurrent() || selection == null || selection != sourceSelection) return@registerForActivityResult
            val data = result.data
            if (result.resultCode != android.app.Activity.RESULT_OK || data == null) {
                applyCaptureStatus(
                    CaptureService.CaptureStatus.permissionNeeded(
                        "capture permission not granted",
                        "Choose everything playing and approve Android's capture prompt",
                    )
                )
                return@registerForActivityResult
            }
            withSourcesReleased {
                applyLocalGainPolicy()
                applyCaptureStatus(CaptureService.CaptureStatus.starting())
                runCatching {
                    startCaptureService(
                        Intent(this, CaptureService::class.java).putExtra(CaptureService.EXTRA_RESULT, data)
                    )
                }.onFailure {
                    applyCaptureStatus(CaptureService.CaptureStatus.error(
                        "capture service could not start",
                        "Return to Phosphor and approve Android's foreground capture prompt again",
                    ))
                }
            }
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!taskIsCurrent()) return@registerForActivityResult
            val purpose = pendingAudioPermission
            pendingAudioPermission = AudioPermissionPurpose.NONE
            if (!granted) {
                if (purpose == AudioPermissionPurpose.PLAYBACK_CAPTURE) {
                    applyCaptureStatus(
                        CaptureService.CaptureStatus.permissionNeeded(
                            "microphone permission not granted",
                            "Grant microphone access so Android can provide playback audio",
                        )
                    )
                }
                return@registerForActivityResult
            }
            when (purpose) {
                AudioPermissionPurpose.MICROPHONE -> startMic()
                AudioPermissionPurpose.PLAYBACK_CAPTURE -> launchCaptureConsent()
                AudioPermissionPurpose.NONE -> Unit
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskRevision = BackgroundLifecycle.policy.enterActivity(taskId)
        activityRevision = BackgroundLifecycle.policy.activityRevision
        ui = ScopeUiState()
        enableEdgeToEdge()
        // Visible flags mirror actual source owners. Idle chrome must remain sleep-eligible.
        reassertSourceWake()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        reduced = readReducedMotion(this)
        PhosphorNative.setReducedMotion(reduced)
        ui.bindRandomModeRequest(::armAndRollRandomMode)
        restoreTuning()
        refreshCaptureMetadataAccess()
        applyScopeRotationPreference()
        // The scope starts immersive; an edge swipe can reveal system bars temporarily.
        applyImmersive()
        setContent { PhosphorScreen(ui, this, reduced) }
        // Post after layout so picture-in-picture receives a valid source rectangle.
        window.decorView.post { updatePictureInPictureParams() }
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshRotationAuthority(force = true)
        refreshCaptureMetadataAccess()
        // Resume only passive live sources once per process. Files and relays remain explicit choices.
        if (taskIsCurrent() && !lastSourceReopened) {
            lastSourceReopened = true
            if (!ui.live && ui.sourceLabel == "no source") {
                when (runtimePrefs().getString("last_source", "none")) {
                    "capture" -> if (!captureConsentNeeded()) startCapture()
                    "mic" -> startMic()
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.pip = isInPictureInPictureMode
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshRotationAuthority(force = true)
        // The SurfaceView is still full-bleed and receives its new buffer dimensions
        // through surfaceChanged. Only PiP's advertised frame needs explicit refresh.
        updatePictureInPictureParams()
    }

    private fun updatePictureInPictureParams() {
        setPictureInPictureParams(pictureInPictureParams())
    }

    private fun pictureInPictureParams(): android.app.PictureInPictureParams {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sourceRectHint = Rect()
        val hasSourceRectHint = window.decorView.getGlobalVisibleRect(sourceRectHint)
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(
                if (landscape) android.util.Rational(16, 9)
                else android.util.Rational(9, 16)
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(PictureInPicturePolicy.platformAutoEnter(Build.VERSION.SDK_INT, ui.pipAutoEnter))
        }
        if (hasSourceRectHint) builder.setSourceRectHint(sourceRectHint)
        return builder.build()
    }

    override fun onUserLeaveHint() {
        if (PictureInPicturePolicy.enterOnLeave(Build.VERSION.SDK_INT, ui.pipAutoEnter, isInPictureInPictureMode)) {
            enterPictureInPictureMode(pictureInPictureParams())
        }
        super.onUserLeaveHint()
    }

    override fun enterPictureInPicture() {
        if (PictureInPicturePolicy.enterManually(isInPictureInPictureMode)) {
            enterPictureInPictureMode(pictureInPictureParams())
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        reassertSourceWake()
        val bindingRevision = controllerBinding.start()
        if (!captureStatusReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                captureStatusReceiver,
                IntentFilter(CaptureService.ACTION_STATUS).apply { addAction(PlaybackService.ACTION_LOCAL_SOURCE_CHANGED) },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            captureStatusReceiverRegistered = true
        }
        applyCaptureStatus(CaptureService.currentStatus())
        PlaybackService.localSourcePublication.current.let {
            if (it.source == LocalSourcePublication.Source.NONE ||
                it.source == LocalSourcePublication.Source.RELEASED_READERS) applyLocalSourcePublication(it.revision)
        }
        PhosphorNative.setRenderPaused(false)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(current: MediaController, extras: Bundle) {
                    if (taskIsCurrent() && controllerBinding.accepts(bindingRevision) && controller === current) {
                        ui.playing = sessionPlaying(current)
                        AcceptanceTrace.record("capture_observed_ui") {
                            "state=${extras.getInt(CaptureMirrorPolicy.OBSERVED_STATE)} ui=${ui.playing}"
                        }
                    }
                }
            }).buildAsync()
        future.addListener({
            if (!taskIsCurrent() || !controllerBinding.accepts(bindingRevision)) {
                runCatching { future.get().release() }
                return@addListener
            }
            val connected = runCatching { future.get() }.getOrElse {
                android.util.Log.w("PhosphorPlayback", "Media session unavailable, reopen Phosphor to reconnect", it)
                return@addListener
            }
            controller = connected.also { c ->
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        reassertSourceWake()
                        AcceptanceTrace.record("activity_player") {
                            "capture=${c.mediaMetadata.extras?.getString("source") == "capture"} " +
                                "state=${c.playbackState} ready=${c.playWhenReady} playing=${c.isPlaying} ui=${ui.playing}"
                        }
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { ui.playing = sessionPlaying(c) }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (c.mediaMetadata.extras?.getString("source") == "capture") ui.playing = sessionPlaying(c)
                    }
                    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                        syncCaptureCommands(c)
                    }
                    // The relay's failures arrive here carrying the engine's fix text.
                    // Keeping the message means the REMOTE sheet can show a remedy instead
                    // of a dead end; a recovered link clears it.
                    override fun onPlayerErrorChanged(error: PlaybackException?) {
                        if (ui.remote) ui.remoteFailure = error?.message.orEmpty()
                    }
                    override fun onTimelineChanged(
                        t: androidx.media3.common.Timeline, reason: Int,
                    ) {
                        syncQueue(c)
                        syncSessionFace(c)
                        ui.playing = sessionPlaying(c)
                    }
                    override fun onMediaItemTransition(
                        item: androidx.media3.common.MediaItem?, reason: Int,
                    ) = syncQueue(c)
                    override fun onMediaMetadataChanged(m: MediaMetadata) {
                        syncSessionFace(c, m)
                        ui.playing = sessionPlaying(c)
                        // Track boundary: advance a per-song light cycle (engine ignores
                        // it unless per-track cycling is active).
                        PhosphorNative.cycleAdvance()
                    }
                })
                // Initial sync: the world may have moved while the Activity slept
                // (earbud skips with the screen off) — mirror the session's truth now,
                // not just on the next change event.
                ui.playing = sessionPlaying(c)
                syncSessionFace(c)
            }
        }, MoreExecutors.directExecutor())
        tick.post(uiTick)
    }

    override fun onStop() {
        activityStarted = false
        reassertSourceWake()
        controllerBinding.cancel()
        saveTuning()
        tick.removeCallbacks(uiTick)
        PhosphorNative.setRenderPaused(true)
        if (captureStatusReceiverRegistered) {
            unregisterReceiver(captureStatusReceiver)
            captureStatusReceiverRegistered = false
        }
        controller?.release()
        controller = null
        super.onStop()
    }

    private fun sessionPlaying(player: MediaController): Boolean = CaptureMirrorPolicy.displayedPlaying(
        capture = player.mediaMetadata.extras?.getString("source") == "capture",
        isPlaying = player.isPlaying,
        observedState = player.sessionExtras.getInt(CaptureMirrorPolicy.OBSERVED_STATE),
    )

    override fun onDestroy() {
        if (taskIsCurrent() && mic.ownsSource() && runtimePrefs().getString("last_source", "none") == "mic") {
            runtimePrefs().edit { putString("last_source", "none") }
        }
        activityDestroyed = true
        val retiredGravityListener = gravityListener
        gravityListener = null
        retiredGravityListener?.let {
            getSystemService(android.hardware.SensorManager::class.java)?.unregisterListener(it)
        }
        micWake.destroy()
        activityStarted = false
        reassertSourceWake()
        scopeSurface = null
        controllerBinding.cancel()
        selectSource()
        BackgroundLifecycle.policy.leaveActivity(activityRevision)
        mic.stop()
        super.onDestroy()
    }

    // One gentle heartbeat for display facts Compose can't observe directly:
    // seek position, resting-beam flag and breathing accent.
    private var baseRoom: Palette? = null
    private var lastRxBytes = 0L
    private val uiTick = object : Runnable {
        override fun run() {
            refreshRotationAuthority()
            reassertSourceWake()
            refreshRootState()
            controller?.let { c ->
                val dur = c.duration
                ui.seekable = !ui.remote && dur > 0 &&
                    c.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                ui.durationMs = if (dur > 0) dur else 0L
                ui.positionMs = c.currentPosition.coerceAtLeast(0L)
            }
            ui.noSignal = PhosphorNative.scopeSilent()
            val rs = if (ui.remote) runCatching {
                org.json.JSONObject(PhosphorNative.remoteStatus())
            }.getOrNull() else null
            val remoteScope = rs?.optJSONObject("scope")
            val remoteGain = remoteScope?.optJSONObject("gain")
            // Show measured renderer and relay gain rather than saved preference values.
            ui.localAutoGain = PhosphorNative.gainAutoNow()
            if (!ui.remoteGeometry) ui.gain = PhosphorNative.gainNow()
            if (ui.remote) {
                remoteGain?.let { ui.autoGain = it.optBoolean("auto", false) }
            } else {
                ui.autoGain = ui.localAutoGain
            }
            // When the desktop supplies geometry, show its measured mode and gain.
            ui.remoteScopeLine = if (ui.remote && ui.remoteGeometry) {
                remoteScope?.let { sc ->
                    val mode = sc.optString("mode", "—")
                    val g = sc.optJSONObject("gain")
                    when {
                        g == null -> "$mode · pc"
                        g.optBoolean("auto") ->
                            "%s · auto ×%.2f · pc".format(mode, g.optDouble("effective", 0.0))
                        else -> "%s · ×%.2f · pc".format(mode, g.optDouble("effective", 0.0))
                    }
                }
            } else null
            val stats = if (dev.phosphor.mobil3.ui.GridData.needsStats(ui.hudMode, ui.gridData)) {
                runCatching {
                    org.json.JSONObject(PhosphorNative.scopeStats())
                }.getOrNull()
            } else null
            ui.gridReading = dev.phosphor.mobil3.ui.GridData.read(stats)
            if (ui.hudMode != 2) {
                val rx = rs?.optLong("rx_bytes") ?: 0L
                val mbps = if (lastRxBytes in 1 until rx) {
                    (rx - lastRxBytes) * 8f * 2f / 1_000_000f // 500 ms tick → per-second
                } else 0f
                lastRxBytes = rx
                ui.hudLine = buildString {
                    append("%.1f fps".format(stats?.optDouble("fps") ?: 0.0))
                    append(" · ${stats?.optInt("segs") ?: 0} segs")
                    if (ui.remote) append(" · %.1f Mb/s".format(mbps))
                }
                // Bridge health (the hardening made visible): live buffer depth,
                // catch-up skips, channel drops, and the leak counter that must
                // stay zero. All real numbers from the session's atomics.
                ui.hudLine2 = if (rs != null) buildString {
                    append("bridge · buf ${rs.optInt("audio_buf_ms")} ms")
                    append(" · tgt ${rs.optInt("audio_target_ms")} ms")
                    append(" · und ${rs.optInt("audio_underruns")}")
                    append(" · skip ${rs.optInt("audio_skips")}")
                    val skipMs = rs.optInt("audio_skip_ms")
                    if (skipMs > 0) append(" (${skipMs} ms)")
                    append(" · drop ${rs.optInt("a_drops")}")
                    val leaked = rs.optInt("leaked_threads")
                    if (leaked > 0) append(" · LEAK $leaked")
                } else ""
            }
            // Moving chrome accents follow the measured beam color; structural accents opt in per room.
            val base = baseRoom ?: ui.room
            run {
                val rgb = PhosphorNative.beamColorNow()
                val beam = floatArrayOf(
                    ((rgb shr 16) and 0xff) / 255f,
                    ((rgb shr 8) and 0xff) / 255f,
                    (rgb and 0xff) / 255f,
                )
                // beamColorNow is already gamma-encoded; withBeam lifts linear — feed it
                // the linearized value so the lift round-trips.
                fun lin(v: Float) = Math.pow(v.toDouble(), 2.2).toFloat()
                ui.room = base.withBeam(floatArrayOf(lin(beam[0]), lin(beam[1]), lin(beam[2])))
            }
            tick.postDelayed(this, 500)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        selectSource()
        taskRevision = BackgroundLifecycle.policy.enterActivity(taskId)
        activityRevision = BackgroundLifecycle.policy.activityRevision
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("open")?.let { openDeck(it) }
        if (intent.getBooleanExtra("capture", false)) startCapture()
        if (intent.getBooleanExtra("remote", false)) startRemote()
    }

    // Direct documents use the same serial staging and validation path as tree entries.
    private fun loadUri(uri: Uri) {
        selectSource()
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        applyLocalGainPolicy()
        startSourceService(Intent(this, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_OPEN_DOCUMENT)
            .putExtra(PlaybackService.EXTRA_DOCUMENT_URI, uri.toString()))
    }

    // Mirror the session's timeline into the deck sheet's queue rows (ghost items from
    // the remote deck are filtered by their reserved ids).
    private fun syncQueue(c: MediaController) {
        val n = c.mediaItemCount
        val titles = mutableListOf<String>()
        var remoteGhosts = false
        for (i in 0 until n) {
            val item = c.getMediaItemAt(i)
            if (item.mediaId.startsWith("remote:")) { remoteGhosts = true; break }
            titles += item.mediaMetadata.title?.toString() ?: "track ${i + 1}"
        }
        ui.queueTitles = if (remoteGhosts) emptyList() else titles
        ui.queueIndex = c.currentMediaItemIndex.coerceAtLeast(0)
    }

    /** The one MediaSession is the source of truth for every visible now-playing face. */
    private fun syncSessionFace(c: MediaController, metadata: MediaMetadata = c.mediaMetadata) {
        syncCaptureCommands(c)
        acceptTrackTitle(metadata.title?.toString())
        ui.trackArtist = metadata.artist?.toString()
        ui.artwork = metadata.artworkData?.takeIf { it.isNotEmpty() }
        val source = metadata.extras?.getString("source")
            ?: c.currentMediaItem?.mediaId?.takeIf { it.startsWith("q") }?.let { "local" }
        ui.remote = source == "remote"
        when (source) {
            "local" -> {
                ui.sourceLabel = "deck"
                ui.live = false
                ui.remote = false
            }
            "capture" -> {
                ui.sourceLabel = "capture"
                ui.live = true
            }
            "remote" -> {
                ui.live = false
                val conn = metadata.extras?.getString("conn")
                val host = metadata.extras?.getString("host") ?: "remote"
                ui.sourceLabel = when (conn) {
                    "CONNECTING" -> "remote · connecting…"
                    // A welcome frame alone is not a live media link.
                    "GREETED" -> "remote · waiting for audio"
                    // The relay says it is sending silence. Without this the user sees a
                    // dark scope and cannot tell whether the desktop is quiet or the link
                    // is broken.
                    "SILENT" -> "remote · $host · no sound"
                    // Frozen socket, not a dropped one. Acceptance H-04 forbids showing a
                    // frozen live trace, and this is what stops that happening.
                    "STALLED" -> "remote · signal stalled"
                    "LOST" -> "remote · reconnecting…"
                    "FAILED" -> "remote · unreachable"
                    else -> "remote · $host"
                }
            }
            else -> if (ui.sourceLabel == "capture" && c.mediaItemCount == 0) {
                // Capture ended (including a system MediaProjection stop): clear the
                // entire face now. No old title or art may survive into "no source".
                ui.sourceLabel = "no source"
                ui.live = false
            }
        }
    }

    private fun syncCaptureCommands(player: Player) {
        val capture = player.mediaMetadata.extras?.getString("source") == "capture"
        ui.captureCanPlay = capture && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
        ui.captureCanNext = capture && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        ui.captureCanPrevious = capture && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    }

    private fun openDeck(path: String) {
        selectSource()
        applyLocalGainPolicy()
        startSourceService(Intent(this, PlaybackService::class.java).putExtra(PlaybackService.EXTRA_OPEN, path))
    }

    private fun micHandoffCancel() {
        micHandoff.cancel()
        mic.cancelStart()
        micReleaseRevision = null
    }

    private fun selectSource(): Long {
        micHandoffCancel()
        pendingAudioPermission = AudioPermissionPurpose.NONE
        pendingCaptureConsent = null
        return ++sourceSelection
    }

    private fun micRequestIsCurrent(): Boolean = taskIsCurrent() && !isDestroyed &&
        micReleaseRevision?.let { PlaybackService.localSourcePublication.accepts(it) } == true

    private fun withSourcesReleased(
        selection: Long = selectSource(),
        micRequest: String? = null,
        start: () -> Unit,
    ) {
        if (!taskIsCurrent()) return
        val reply = object : ResultReceiver(tick) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val revision = resultData?.getLong(PlaybackService.EXTRA_SOURCE_REVISION, -1) ?: -1
                if (resultCode == 0 && taskIsCurrent() && selection == sourceSelection && !isDestroyed &&
                    PlaybackService.localSourcePublication.accepts(revision)) {
                    if (micRequest != null) micReleaseRevision = revision
                    start()
                }
            }
        }
        startSourceService(Intent(this, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_RELEASE_LOCAL)
            .putExtra(CaptureService.EXTRA_MIC_REQUEST, micRequest)
            .putExtra(PlaybackService.EXTRA_RELEASE_REPLY, reply))
    }

    // ---- ScopeActions ----
    override fun makeSurface(): SurfaceView = SurfaceView(this).apply {
        scopeSurface?.keepScreenOn = false
        scopeSurface = this
        holder.addCallback(surfaceCallback)
        reassertSourceWake()
    }

    private fun reassertSourceWake() {
        val awake = SourceWakePolicy.visible(
            started = activityStarted && !activityDestroyed,
            sourceLive = micWake.live || PlaybackService.hasLiveWakeSource() || CaptureService.hasLiveWakeSource(),
        )
        scopeSurface?.keepScreenOn = awake
        if (awake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // The transport law, unified: ALL transport goes through the one MediaController —
    // the session's player routes to the local deck or the bridge. Notification, lock
    // screen, earbuds and the console are therefore the same code path.
    override fun togglePlay() {
        val c = controller
        if (c != null) {
            val playing = if (c.mediaMetadata.extras?.getString("source") == "capture") sessionPlaying(c) else c.playWhenReady
            if (playing) c.pause() else c.play()
        }
        else ui.playing = PhosphorNative.deckToggle() // no session yet (nothing loaded)
    }

    override fun next() { controller?.seekToNext() }

    override fun prev() { controller?.seekToPrevious() }

    override fun seekTo(ms: Long) {
        if (!ui.remote) controller?.seekTo(ms)
    }

    override fun startRemote() {
        val first = remoteHosts().firstOrNull() ?: return
        startRemoteHost(first.first, first.second.first, first.second.second)
    }

    // The relay hosts the phone knows. Seeded once from BuildConfig (a build-machine
    // fact, never source) and thereafter owned by the user, so the Play distribution,
    // which compiles an empty seed, can still reach a desktop the user runs.
    private val remoteHostStore by lazy {
        RemoteHostStore(this, "")
    }

    override fun remoteHosts(): List<Pair<String, Pair<String, Int>>> =
        remoteHostStore.hosts().map { it.label to (it.host to it.port) }

    override fun saveRemoteHost(
        existingHost: String,
        existingPort: Int,
        label: String,
        host: String,
        port: String,
    ): String? {
        // The port arrives as raw text because the field is a text field. Refuse it here
        // in the same fix-bearing shape the store uses, so the sheet has one error path.
        val parsedPort = port.trim().toIntOrNull()
            ?: return "Enter a port number from 1 through 65535."
        val outcome = if (existingHost.isEmpty()) {
            remoteHostStore.add(label, host, parsedPort)
        } else {
            remoteHostStore.update(existingHost, existingPort, label, host, parsedPort)
        }
        return when (outcome) {
            is RemoteHostOutcome.Saved -> null
            is RemoteHostOutcome.Refused -> "${outcome.message} ${outcome.fix}"
            is RemoteHostOutcome.Failed -> "${outcome.message} ${outcome.fix}"
        }
    }

    override fun removeRemoteHost(host: String, port: Int): String? =
        when (val outcome = remoteHostStore.remove(host, port)) {
            is RemoteHostOutcome.Saved -> null
            is RemoteHostOutcome.Refused -> "${outcome.message} ${outcome.fix}"
            is RemoteHostOutcome.Failed -> "${outcome.message} ${outcome.fix}"
        }

    override fun startRemoteHost(label: String, host: String, port: Int) {
        selectSource()
        prefs().edit { putFloat("gain", gainValue) }
        ui.sourceLabel = "remote · connecting…" // honest immediately (kills the race)
        ui.remoteFailure = "" // a fresh attempt clears the previous failure's fix
        ui.remote = true
        startSourceService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_REMOTE_CONNECT)
                .putExtra(PlaybackService.EXTRA_HOST, host)
                .putExtra(PlaybackService.EXTRA_PORT, port)
                .putExtra(PlaybackService.EXTRA_LABEL, label)
        )
    }

    override fun setRemoteStreams(audio: Boolean, geometry: Boolean) {
        PhosphorNative.remoteSetStreams(audio, geometry)
        ui.remoteAudio = audio
        ui.remoteGeometry = geometry
    }

    override fun disconnectRemote() {
        selectSource()
        startSourceService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_REMOTE_DISCONNECT)
        )
        ui.remote = false
        ui.sourceLabel = "no source"
        applyLocalGainPolicy()
    }

    override fun openFile() {
        selectSource()
        openFileLauncher.launch(arrayOf("audio/*"))
    }

    override fun exportSettings() = createSettingsArchive.launch(
        "phosphor-settings-${BuildConfig.VERSION_NAME}.phossettings"
    )

    override fun importSettings() = openSettingsArchive.launch(
        arrayOf("application/json", "application/octet-stream", "text/plain")
    )

    override fun startMic() {
        if (!taskIsCurrent()) return
        val selection = selectSource()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val request = UUID.randomUUID().toString()
            ui.live = false
            micHandoff.request(request, CaptureService.currentStatus().sequence)
            withSourcesReleased(selection, request) {
                // Re-read the observation too: the receiver may have been stopped by Android UI.
                observeMicCaptureStatus(CaptureService.currentStatus())
                micHandoff.sourcesReleased(request)
            }
        }
        else {
            pendingAudioPermission = AudioPermissionPurpose.MICROPHONE
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun startCapture() = startCaptureBackend(explicitStandard = false)

    override fun startStandardCapture() = startCaptureBackend(explicitStandard = true)

    private fun startCaptureBackend(explicitStandard: Boolean) {
        if (!taskIsCurrent()) return
        val backend = if (!explicitStandard && RootCaptureSettings.enabled(this)) CaptureBackend.ROOT else CaptureBackend.STANDARD
        val alreadyCapturing = CaptureService.ownsCapture() && CaptureService.currentStatus().backend == backend && !micHandoff.isPending
        val selection = selectSource()
        if (alreadyCapturing) return
        if (backend == CaptureBackend.ROOT) {
            withSourcesReleased(selection = selection) {
                if (!RootCaptureSettings.enabled(this)) return@withSourcesReleased
                applyLocalGainPolicy()
                applyCaptureStatus(CaptureService.CaptureStatus(CaptureService.STATE_STARTING,
                    "root capture starting", RootCapturePolicy.CAPABILITY, false, backend = CaptureBackend.ROOT))
                runCatching { startCaptureService(Intent(this, RootCaptureService::class.java)) }.onFailure {
                    applyCaptureStatus(CaptureService.CaptureStatus.error("root capture could not start", RootCaptureSettings.fix(it.message)).copy(backend = CaptureBackend.ROOT))
                }
            }
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAudioPermission = AudioPermissionPurpose.PLAYBACK_CAPTURE
            ui.captureStatus = "microphone permission needed for playback capture"
            ui.captureFix = "Grant microphone access, then approve Android's capture prompt"
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        launchCaptureConsent()
    }

    private fun launchCaptureConsent() {
        pendingCaptureConsent = sourceSelection
        markConsentSeen()
        ui.captureStatus = "waiting for Android capture permission"
        ui.captureFix = "Approve the prompt to connect playback audio"
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureConsent.launch(screenCaptureIntent(mpm))
    }

    private fun screenCaptureIntent(manager: MediaProjectionManager): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            fullDisplayCaptureIntent(manager)
        } else {
            // Android 10 through 13 only offer full-display projection.
            manager.createScreenCaptureIntent()
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun fullDisplayCaptureIntent(manager: MediaProjectionManager): Intent =
        manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())

    override fun setRootCapture(enabled: Boolean) {
        if (!taskIsCurrent()) return
        selectSource()
        if (enabled) RootCaptureSettings.enable(this) else RootCaptureSettings.disable(this)
        refreshRootState()
    }

    override fun openRootManager() {
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("me.weishu.kernelsu"))
        }.onFailure {
            Toast.makeText(this, "Open your installed KernelSU manager and verify Phosphor's existing grant and Default/inherited profile", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshRootState() {
        ui.rootCaptureEnabled = RootCaptureSettings.enabled(this)
        ui.rootCaptureBusy = RootCaptureSettings.busy
        val status = CaptureService.currentStatus()
        ui.rootCaptureStatus = if (status.backend == CaptureBackend.ROOT && status.message.isNotBlank()) {
            "${status.message} · ${status.fix}"
        } else if (ui.rootCaptureEnabled && RootCaptureSettings.message.startsWith("Off")) {
            "Enabled · ${RootCapturePolicy.CAPABILITY} · authorization checked at capture start"
        } else RootCaptureSettings.message
    }

    override fun stopLive() {
        withSourcesReleased {
            PhosphorNative.setRingActive(false)
            ui.live = false
            if (ui.sourceLabel.startsWith("capture") || ui.sourceLabel == "mic") {
                ui.sourceLabel = "no source"
                acceptTrackTitle(null)
                ui.trackArtist = null
                ui.artwork = null
            }
            ui.captureStatus = ""
            ui.captureFix = ""
        }
    }

    private fun applyCaptureStatus(status: CaptureService.CaptureStatus) {
        if (!taskIsCurrent()) return
        if (status.sequence > 0) {
            if (status.sequence <= captureStatusSequence) return
            captureStatusSequence = status.sequence
        }
        if (micHandoff.isPending) {
            observeMicCaptureStatus(status)
            return
        }
        val wasCapture = ui.sourceLabel.startsWith("capture")
        ui.captureRoot = status.backend == CaptureBackend.ROOT
        ui.captureStatus = status.message
        ui.captureFix = status.fix
        when (status.state) {
            CaptureService.STATE_STARTING -> {
                ui.sourceLabel = "capture · starting…"
                ui.live = false
            }
            CaptureService.STATE_FLOWING -> {
                ui.sourceLabel = "capture"
                ui.live = true
            }
            else -> if (wasCapture) {
                ui.sourceLabel = "no source"
                ui.live = false
                acceptTrackTitle(null)
                ui.trackArtist = null
                ui.artwork = null
            }
        }
    }

    private fun observeMicCaptureStatus(status: CaptureService.CaptureStatus) {
        if (micReleaseRevision != null && !micRequestIsCurrent()) {
            micHandoffCancel()
            return
        }
        micHandoff.captureStatus(
            sequence = status.sequence,
            idle = status.state == CaptureService.STATE_IDLE,
            requestId = status.micRequest,
            error = status.fix.takeIf { status.state == CaptureService.STATE_ERROR },
        )
    }

    private fun applyLocalSourcePublication(revision: Long) {
        val publication = PlaybackService.localSourcePublication
        if (!publication.accepts(revision)) return
        when (publication.current.source) {
            LocalSourcePublication.Source.LOCAL -> {
                ui.sourceLabel = "deck"
                ui.live = false
                ui.remote = false
            }
            LocalSourcePublication.Source.NONE, LocalSourcePublication.Source.RELEASED_READERS -> {
                if (publication.current.source == LocalSourcePublication.Source.RELEASED_READERS &&
                    !publication.current.clearsReaderFace(ui.sourceLabel)) return
                ui.sourceLabel = "no source"
                ui.live = false
                ui.remote = false
                acceptTrackTitle(null)
                ui.trackArtist = null
                ui.artwork = null
            }
            LocalSourcePublication.Source.OTHER -> Unit
        }
    }

    // Runtime consent state is separate from portable instrument settings.
    private fun prefs() = getSharedPreferences(PhosphorApplication.PREFERENCES_NAME, MODE_PRIVATE)
    private fun runtimePrefs() = getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, MODE_PRIVATE)

    // ── Tuning persistence: the scope remembers its knobs across launches. ──
    private var focusPref = 0.3f
    private fun saveTuning() {
        prefs().edit {
            putInt("mode", ui.modeIndex)
            putBoolean("random_mode_armed", ui.randomModeArmed)
            putString("random_ban_modes", ui.randomBanModes.sorted().joinToString(","))
            putInt("beam", ui.beamIndex)
            putInt("fps", ui.fpsValue)
            putInt("oversample", ui.oversample)
            // AUTO-GAIN breathes ui.gain; the manual landing remains the saved knob.
            putFloat("gain", gainValue)
            putFloat("beam_energy", ui.beamEnergy)
            putFloat("glow", ui.glow)
            putBoolean("beam_random_armed", ui.beamRandomArmed)
            putString("beam_random_range", "${ui.beamRandomLo},${ui.beamRandomHi}")
            putBoolean("glow_random_armed", ui.glowRandomArmed)
            putString("glow_random_range", "${ui.glowRandomLo},${ui.glowRandomHi}")
            putInt("geom_fx", ui.geomFx)
            putFloat("geom_amount", ui.geomAmount)
            putBoolean("grid", ui.grid)
            putBoolean(dev.phosphor.mobil3.ui.GridData.KEY, ui.gridData)
            putFloat("focus", focusPref)
            putString("room", ui.room.id)
            // Relay auto-gain is display truth, not authority for an absent local preference.
            putBoolean("auto_gain", prefs().getBoolean("auto_gain", true))
            putInt("hud_mode", ui.hudMode)
            putInt("band_mode", ui.bandMode)
            putBoolean("fullscreen", ui.fullscreen)
            putBoolean("linger_background", ui.lingerBackground)
            putBoolean("view_lock", ui.viewLock)
            putInt("custom_count", ui.customCount)
            putFloat("cycle_seconds", ui.cycleSeconds)
            putBoolean("cycle_per_track", ui.cyclePerTrack)
            putBoolean("double_tap_playback", ui.doubleTapPlayback)
            putBoolean(dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.KEY, ui.controlsAlwaysVisible)
            putBoolean(PictureInPicturePolicy.KEY, ui.pipAutoEnter)
            putBoolean("scope_rotation_locked", scopeRotationLockState)
            putInt("scope_locked_orientation", lockedScopeOrientation)
            putBoolean("ui_placement_locked", uiPlacementLockState)
            putBoolean("ui_locked_landscape", lockedUiLandscape)
            putInt("remote_latency_mode", ui.latencyMode)
            putBoolean("amoled_seen", ui.amoledCaptionSeen)
            putInt("ov_char", ui.styleOverride.character?.ordinal ?: -1)
            putInt("ov_motion", ui.styleOverride.motion?.ordinal ?: -1)
            putInt("ov_radius", ui.styleOverride.radiusDp ?: -1)
            putInt("ov_desig", when (ui.styleOverride.designators) {
                null -> -1; true -> 1; false -> 0
            })
        }
        runtimePrefs().edit {
            putString("random_track_title", lastRandomTrackTitle)
            // The remembered input and calibration date are device runtime metadata.
            if (taskIsCurrent()) putString(
                "last_source",
                runtimeInputSource(ui, mic.isRecording()),
            )
            putString(
                "cal_date",
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date()),
            )
        }
    }

    private fun restoreTuning() {
        val p = prefs()
        ui.lingerBackground = BackgroundLifecyclePolicy.linger(p.all)
        ui.doubleTapPlayback = p.getBoolean("double_tap_playback", true)
        ui.controlsAlwaysVisible = dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.alwaysVisible(p.all)
        ui.pipAutoEnter = PictureInPicturePolicy.autoEnter(p.all)
        updatePictureInPictureParams()
        ui.modeIndex = p.getInt("mode", 1).also { PhosphorNative.setMode(it) }
        ui.randomModeArmed = p.getBoolean("random_mode_armed", false)
        lastRandomTrackTitle = runtimePrefs().getString("random_track_title", null)
        ui.randomBanModes = (p.getString("random_ban_modes", "") ?: "")
            .split(",").mapNotNull { it.toIntOrNull() }
            .filter { it in dev.phosphor.mobil3.ui.ModeLabels.indices }.toSet()
            .let { if (dev.phosphor.mobil3.ui.ModeLabels.size - it.size < 2) emptySet() else it }
        ui.beamIndex = p.getInt("beam", 7).also { PhosphorNative.setBeamColor(it) }
        ui.fpsValue = p.getInt("fps", 0).also { PhosphorNative.setTargetFps(it) }
        ui.oversample = p.getInt("oversample", 1).also { PhosphorNative.setOversample(it) }
        gainValue = p.getFloat("gain", 1.8332275f)
        ui.gain = gainValue
        PhosphorNative.setGain(gainValue)
        val autoGain = p.getBoolean("auto_gain", true)
        PhosphorNative.setGainAuto(autoGain)
        ui.autoGain = autoGain
        ui.localAutoGain = autoGain
        ui.beamEnergy = p.getFloat("beam_energy", 8f).also { PhosphorNative.setBeamEnergy(it) }
        ui.glow = p.getFloat("glow", 0.7f).also { PhosphorNative.setGlow(it) }
        // The dice: restore range + armed state; never roll on restore — the last landed
        // BEAM/GLOW values above are the truth until the next track boundary.
        fun range(key: String, min: Float, max: Float, dLo: Float, dHi: Float): Pair<Float, Float> {
            val parts = (p.getString(key, "") ?: "").split(",")
            if (parts.size != 2) return dLo to dHi
            val lo = parts[0].toFloatOrNull() ?: return dLo to dHi
            val hi = parts[1].toFloatOrNull() ?: return dLo to dHi
            if (!lo.isFinite() || !hi.isFinite() || lo !in min..max || hi !in lo..max) return dLo to dHi
            return lo to hi
        }
        range("beam_random_range", 1f, 30f, 6f, 20f).let { (lo, hi) ->
            ui.beamRandomLo = lo; ui.beamRandomHi = hi
        }
        range("glow_random_range", 0f, 0.98f, 0.30f, 0.90f).let { (lo, hi) ->
            ui.glowRandomLo = lo; ui.glowRandomHi = hi
        }
        ui.beamRandomArmed = p.getBoolean("beam_random_armed", false)
        ui.glowRandomArmed = p.getBoolean("glow_random_armed", false)
        ui.bestiaryFound = p.getBoolean("bestiary_found", false)
        refreshRootState()
        ui.geomFx = p.getInt("geom_fx", 0).coerceIn(0, 4).also { PhosphorNative.setGeomFx(it) }
        ui.geomAmount = p.getFloat("geom_amount", 0.6f).coerceIn(0f, 1f)
            .also { PhosphorNative.setGeomAmount(it) }
        ui.grid = p.getBoolean("grid", false).also { PhosphorNative.setGrid(it) }
        ui.gridData = p.getBoolean(dev.phosphor.mobil3.ui.GridData.KEY, dev.phosphor.mobil3.ui.GridData.DEFAULT)
        ui.gridReading = null
        focusPref = p.getFloat("focus", 0.3f).also { PhosphorNative.setFocus(it) }
        ui.hudMode = p.getInt("hud_mode", 1).coerceIn(0, 2)
        ui.bandMode = p.getInt("band_mode", 1)
        ui.fullscreen = p.getBoolean("fullscreen", true)
        ui.viewLock = p.getBoolean("view_lock", false)
        scopeRotationLockState = p.getBoolean("scope_rotation_locked", true)
        lockedScopeOrientation = p.getInt(
            "scope_locked_orientation",
            if (scopeRotationLockState) exactCurrentOrientation() else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        )
        uiPlacementLockState = p.getBoolean("ui_placement_locked", false)
        lockedUiOrientation = p.getInt(
            "ui_locked_orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        )
        updateOrientationSensor()
        lockedUiLandscape = p.getBoolean(
            "ui_locked_landscape",
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
        ui.latencyMode = p.getInt("remote_latency_mode", 2).coerceIn(0, 2)
            .also { PhosphorNative.remoteSetLatencyMode(it) }
        ui.calDate = runtimePrefs().getString("cal_date", "") ?: ""
        ui.amoledCaptionSeen = p.getBoolean("amoled_seen", false)
        ui.styleOverride = dev.phosphor.mobil3.ui.StyleOverride(
            character = p.getInt("ov_char", -1).takeIf { it >= 0 }
                ?.let { dev.phosphor.mobil3.ui.ChromeCharacter.entries.getOrNull(it) },
            motion = p.getInt("ov_motion", -1).takeIf { it >= 0 }
                ?.let { dev.phosphor.mobil3.ui.MotionFeel.entries.getOrNull(it) },
            radiusDp = p.getInt("ov_radius", -1).takeIf { it >= 0 },
            designators = when (p.getInt("ov_desig", -1)) {
                1 -> true; 0 -> false; else -> null
            },
        )
        dev.phosphor.mobil3.ui.paletteById(p.getString("room", "amoled") ?: "amoled")
            .let { baseRoom = it; ui.room = it }
        // Restore the custom beam only after validating all nine RGB components.
        val customCount = p.getInt("custom_count", 0).takeIf { it in 1..3 } ?: 0
        val customRgb = p.getString("custom_rgb", null)
            ?.split(",")?.map { it.toFloatOrNull() }
            ?.takeIf { values -> values.size == 9 && values.all { it != null && it.isFinite() && it in 0f..1f } }
            ?.map { requireNotNull(it) }
        ui.cycleSeconds = p.getFloat("cycle_seconds", 3.0f)
        ui.cyclePerTrack = p.getBoolean("cycle_per_track", false)
        if (customRgb != null) {
            ui.customColors = (0..2).map {
                androidx.compose.ui.graphics.Color(
                    customRgb[it * 3], customRgb[it * 3 + 1], customRgb[it * 3 + 2],
                )
            }
        }
        ui.customCount = if (customRgb != null) customCount else 0
        // SetBeamColor alone does not retire native custom mode. Zero count selects the preset.
        PhosphorNative.setCustomBeam(customRgb?.toFloatArray() ?: FloatArray(9), ui.customCount)
        PhosphorNative.setBeamCycle(ui.cycleSeconds, ui.cyclePerTrack)
    }
    override fun captureConsentNeeded(): Boolean = !RootCaptureSettings.enabled(this) && !runtimePrefs().getBoolean("consent_seen", false)
    private fun markConsentSeen() {
        if (taskIsCurrent()) runtimePrefs().edit { putBoolean("consent_seen", true) }
    }

    override fun setLingerBackground(on: Boolean) {
        ui.lingerBackground = on
        prefs().edit { putBoolean(BackgroundLifecyclePolicy.LINGER_KEY, on) }
    }

    override fun setViewLock(on: Boolean) {
        ui.viewLock = on
        prefs().edit { putBoolean("view_lock", on) }
    }

    override fun setDoubleTapPlayback(on: Boolean) {
        ui.doubleTapPlayback = on
        prefs().edit { putBoolean("double_tap_playback", on) }
    }

    override fun setControlsAlwaysVisible(on: Boolean) {
        ui.controlsAlwaysVisible = on
        prefs().edit { putBoolean(dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.KEY, on) }
    }

    override fun setPipAutoEnter(on: Boolean) {
        ui.pipAutoEnter = on
        prefs().edit { putBoolean(PictureInPicturePolicy.KEY, on) }
        updatePictureInPictureParams()
    }

    override fun openCaptureMetadataSettings() {
        val component = ComponentName(this, CaptureNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component.flattenToString(),
            )
        runCatching { startActivity(detail) }.onFailure {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun markBestiaryFound() {
        ui.bestiaryFound = true
        prefs().edit { putBoolean("bestiary_found", true) } // found is forever
    }

    override fun openLink(url: String) {
        // Cards leave through the user's own browser — the app renders no web content.
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    }

    private fun refreshCaptureMetadataAccess() {
        val component = ComponentName(this, CaptureNotificationListenerService::class.java)
        ui.captureMetadataAccess = getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component)
    }

    private fun acceptTrackTitle(title: String?) {
        ui.trackTitle = title
        if (title != null && title != lastRandomTrackTitle) {
            lastRandomTrackTitle = title
            if (ui.randomModeArmed) rollRandomMode()
            if (ui.beamRandomArmed) applyBeamEnergy(rollIn(ui.beamRandomLo, ui.beamRandomHi))
            if (ui.glowRandomArmed) applyGlow(rollIn(ui.glowRandomLo, ui.glowRandomHi))
        }
    }

    private fun armAndRollRandomMode() {
        ui.randomModeArmed = true
        rollRandomMode()
    }

    private fun rollRandomMode() = applyMode(rollModeExcluding(ui.modeIndex, ui.randomBanModes))

    private fun applyMode(index: Int) {
        PhosphorNative.setMode(index); ui.modeIndex = index
        // Remote geometry uses the desktop renderer's mode names.
        if (ui.remote && ui.remoteGeometry) {
            PhosphorNative.remoteScopeCtl("mode", dev.phosphor.mobil3.ui.ModeTags[index])
        }
    }
    override fun setMode(index: Int) {
        ui.randomModeArmed = false
        applyMode(index)
    }
    override fun setBeam(index: Int) {
        PhosphorNative.setBeamColor(index); ui.beamIndex = index
        // LIGHT presets are the desktop's theme names, verbatim (theme = beam).
        if (ui.remote && ui.remoteGeometry) {
            PhosphorNative.remoteScopeCtl("theme", dev.phosphor.mobil3.ui.BeamColors[index].label)
        }
    }
    override fun setFps(value: Int) { PhosphorNative.setTargetFps(value); ui.fpsValue = value }
    override fun setOversample(n: Int) { PhosphorNative.setOversample(n); ui.oversample = n }
    override fun setRoom(room: Palette) { baseRoom = room; ui.room = room }
    override fun setFocus(focus: Float) { focusPref = focus; PhosphorNative.setFocus(focus) }

    override fun setGainAbsolute(g: Float) {
        gainValue = g.coerceIn(0.1f, 7f)
        PhosphorNative.setGain(gainValue)
        ui.gain = gainValue
        ui.autoGain = false
        ui.localAutoGain = false
        prefs().edit { putBoolean("auto_gain", false) }
        tick.removeCallbacks(persistGain)
        tick.postDelayed(persistGain, 250)
        // Pinch drives the DESKTOP's gain while it renders the beam (throttled —
        // the gesture fires per-frame; the scope only needs ~10 Hz).
        if (ui.remote) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastRemoteGainMs > 100) {
                lastRemoteGainMs = now
                PhosphorNative.remoteScopeCtl("gain", String.format(java.util.Locale.US, "%.2f", gainValue))
            }
        }
    }
    private var lastRemoteGainMs = 0L

    override fun setGainAuto(on: Boolean) {
        prefs().edit { putBoolean("auto_gain", on) }
        // Keep the local renderer ready for local/captured remote audio, while a
        // remote source also receives the desktop's existing typed gain verb.
        PhosphorNative.setGainAuto(on)
        ui.localAutoGain = on
        ui.autoGain = on
        if (ui.remote) {
            PhosphorNative.remoteScopeCtl(
                "gain",
                if (on) "auto" else String.format(java.util.Locale.US, "%.2f", gainValue),
            )
        }
    }

    override fun setHudMode(mode: Int) {
        val safeMode = mode.coerceIn(0, 2)
        if (prefs().edit().putInt("hud_mode", safeMode).commit()) {
            ui.hudMode = safeMode
            ui.hudControlStatus = ""
        } else {
            ui.hudControlStatus = "HUD change could not be saved · retry after storage is available"
        }
    }

    private data class PreferenceValueSnapshot(val present: Boolean, val value: Any?)

    private fun preferenceValueSnapshots(
        allPreferences: Map<String, Any?>,
        keys: Set<String>,
    ): Map<String, PreferenceValueSnapshot> = keys.associateWith { key ->
        PreferenceValueSnapshot(
            present = allPreferences.containsKey(key),
            value = allPreferences[key],
        )
    }

    private fun restorePreferenceSnapshots(
        snapshots: Map<String, PreferenceValueSnapshot>,
    ): Boolean {
        val editor = prefs().edit()
        snapshots.forEach { (key, snapshot) ->
            if (!snapshot.present) {
                editor.remove(key)
                return@forEach
            }
            when (val value = snapshot.value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    val strings = value.filterIsInstance<String>().toSet()
                    if (strings.size == value.size) editor.putStringSet(key, strings) else editor.remove(key)
                }
                null -> editor.remove(key)
                else -> editor.remove(key)
            }
        }
        return editor.commit()
    }

    override fun setRemoteLatencyMode(mode: Int) {
        ui.latencyMode = mode.coerceIn(0, 2)
        prefs().edit { putInt("remote_latency_mode", ui.latencyMode) }
        PhosphorNative.remoteSetLatencyMode(ui.latencyMode)
    }

    private fun applyLocalGainPolicy() {
        val on = prefs().getBoolean("auto_gain", true)
        PhosphorNative.setGain(gainValue) // restores the remembered manual landing
        PhosphorNative.setGainAuto(on)
        ui.gain = gainValue
        ui.autoGain = on
        ui.localAutoGain = on
    }

    private fun applyImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            if (ui.fullscreen) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun setFullscreen(on: Boolean) {
        ui.fullscreen = on
        prefs().edit { putBoolean("fullscreen", on) }
        applyImmersive()
    }

    override fun isScopeRotationLocked(): Boolean = scopeRotationLockState

    override fun setScopeRotationLocked(locked: Boolean) {
        if (!rotationAllowed()) return
        if (scopeRotationLockState == locked) return
        scopeRotationLockState = locked
        if (locked) lockedScopeOrientation = exactCurrentOrientation()
        prefs().edit {
            putBoolean("scope_rotation_locked", locked)
            putInt("scope_locked_orientation", lockedScopeOrientation)
        }
        applyScopeRotationPreference()
        // The sensor must run for scope-locked + UI-follow (chrome-to-gravity) too.
        updateOrientationSensor()
    }

    override fun isUiPlacementLocked(): Boolean = uiPlacementLockState
    override fun lockedUiLandscape(): Boolean = lockedUiLandscape

    override fun setUiPlacementLocked(locked: Boolean) {
        if (!rotationAllowed()) return
        if (uiPlacementLockState == locked) return
        if (locked) {
            lockedUiLandscape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            lockedUiOrientation = exactCurrentOrientation()
        }
        uiPlacementLockState = locked
        applyScopeRotationPreference()
        updateOrientationSensor()
        prefs().edit {
            putBoolean("ui_placement_locked", locked)
            putBoolean("ui_locked_landscape", lockedUiLandscape)
            putInt("ui_locked_orientation", lockedUiOrientation)
        }
    }

    /** Refresh before mutations, including callbacks retained before Android locked rotation. */
    private fun rotationAllowed(): Boolean {
        if (!taskIsCurrent()) return false
        val locked = runCatching {
            android.provider.Settings.System.getInt(
                contentResolver,
                android.provider.Settings.System.ACCELEROMETER_ROTATION,
                0,
            ) != 1
        }.getOrDefault(true)
        if (ui.systemRotationLocked != locked) rotationAuthorityNeedsRouting = true
        ui.systemRotationLocked = locked
        if (locked) {
            // LOCKED holds Android's observed current orientation, not a saved cardinal.
            if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
        }
        return !locked
    }

    private fun refreshRotationAuthority(force: Boolean = false) {
        if (!rotationAllowed()) return
        if (rotationAuthorityNeedsRouting || force) {
            rotationAuthorityNeedsRouting = false
            applyScopeRotationPreference()
            routeOrientation(force = true)
        }
    }

    private fun applyScopeRotationPreference() {
        if (!rotationAllowed()) return
        requestedOrientation = if (scopeRotationLockState) {
            if (lockedScopeOrientation in setOf(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                )
            ) lockedScopeOrientation else exactCurrentOrientation()
        } else if (uiPlacementLockState) {
            // UI PLACEMENT locked with scope free: the Activity never rotates — the
            // BEAM follows gravity instead (see the orientation sensor). Chrome
            // physically cannot move; scope content stays upright.
            if (lockedUiOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                lockedUiOrientation else exactCurrentOrientation()
        } else {
            // NO LOCK — the detent owns rotation here, not Android. Returning
            // SCREEN_ORIENTATION_UNSPECIFIED at this point is what kept undoing the
            // detent: this runs on resume, on config change and after settings imports,
            // so whatever the detent had pinned was overwritten moments later and
            // Android's twitchy sensor logic took the wheel again.
            applyDetentedOrientation()
            return
        }
    }

    // Runs ALWAYS, not only under a lock. The detent governs plain rotation too: with
    // no lock the Activity used to be SCREEN_ORIENTATION_UNSPECIFIED, which hands the
    // decision to Android's own (very twitchy) sensor logic, so the detent was dead code
    // for anyone who had not turned a lock on. Ben reported
    // rotation "still super sensitive" for exactly this reason.
    private fun updateOrientationSensor() {
        if (!taskIsCurrent()) return
        run {
            if (gravityListener == null) {
                val sm = getSystemService(android.hardware.SensorManager::class.java)
                val accel = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
                if (accel != null) {
                    gravityListener = object : android.hardware.SensorEventListener {
                        private var gx = 0f; private var gy = 0f; private var gz = 0f
                        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
                        override fun onSensorChanged(e: android.hardware.SensorEvent) {
                            if (!taskIsCurrent() || gravityListener !== this) return
                            // Low-pass to gravity, then two gates before any quadrant
                            // moves: (1) FLATNESS — a phone within ~20° of lying flat has
                            // no meaningful "up"; a desk phone must never rotate its
                            // chrome (Ben: "rotations are messed up"). (2) A DETENT —
                            // see below.
                            gx = 0.8f * gx + 0.2f * e.values[0]
                            gy = 0.8f * gy + 0.2f * e.values[1]
                            gz = 0.8f * gz + 0.2f * e.values[2]
                            val horiz = kotlin.math.sqrt(gx * gx + gy * gy)
                            if (horiz < 3.4f) return // flatter than ~20° tilt: hold
                            val degrees = ((Math.toDegrees(
                                kotlin.math.atan2(-gx.toDouble(), gy.toDouble())
                            ) + 360.0) % 360.0).toInt()
                            // The detent (Ben: "you have to really rotate it and then
                            // it's set"). Not a timer — a timer makes a correct turn feel
                            // laggy. The rule is asymmetric instead: keeping the current
                            // orientation is easy, taking a new one needs a real turn.
                            // Lives in RotationDetent so it can be tested on the host.
                            if (!RotationDetent.shouldCommit(committedCardinal, degrees)) return
                            val previousCardinal = committedCardinal
                            committedCardinal = RotationDetent.nearestCardinal(degrees)
                            lastSensorDeg = degrees
                            // Route when the COMMITTED ORIENTATION changes, not when the
                            // raw angle does. Gating on the raw degree meant a phone held
                            // steady at one angle stopped routing entirely (the reading
                            // repeats), while a phone jittering by a degree routed
                            // constantly. The orientation is the thing that matters.
                            if (previousCardinal != committedCardinal) routeOrientation()
                        }
                    }.also {
                        android.util.Log.i("PhosphorRotation", "gravity sensor registered")
                        sm.registerListener(
                            it, accel, android.hardware.SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }
            }
            // A mode toggle re-routes the last known gravity now: the sensor only fires
            // on CHANGE, so a stationary phone would otherwise keep the prior mode's fields.
            routeOrientation(force = true)
        }
    }

    // q is the counter-clockwise quadrant from the pinned display to gravity-up.
    private fun routeOrientation(force: Boolean = false) {
        if (!rotationAllowed()) return
        val next = RotationDetent.presentation(
            systemRotationLocked = ui.systemRotationLocked,
            current = ui.rotationPresentation,
            scopeLocked = scopeRotationLockState,
            uiLocked = uiPlacementLockState,
            cardinal = committedCardinal,
            displayQuadrant = currentDisplayRotation(),
        )
        if (next != ui.rotationPresentation || force) {
            ui.rotationPresentation = next
            PhosphorNative.setViewRotation(next.beamQuadrant)
        }
        // The free Activity follows the committed gravity cardinal, not a display delta.
        if (!scopeRotationLockState && !uiPlacementLockState) {
            applyDetentedOrientation()
        }
    }

    /**
     * Pin the Activity to the orientation the detent has committed to.
     *
     * Respecting the user's OS rotation lock matters here: if they have locked their
     * phone to portrait system-wide, an app that rotates anyway is broken, however good
     * its detent is.
     */
    private fun applyDetentedOrientation() {
        if (!rotationAllowed()) return
        if (scopeRotationLockState || uiPlacementLockState) return
        val target = when (RotationDetent.screenTarget(committedCardinal)) {
            RotationDetent.ScreenTarget.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            RotationDetent.ScreenTarget.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            RotationDetent.ScreenTarget.REVERSE_PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            RotationDetent.ScreenTarget.REVERSE_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            RotationDetent.ScreenTarget.UNSPECIFIED ->
                return // No credible gravity yet. Keep the current orientation.
        }
        if (requestedOrientation != target) {
            // Keep the detent decision observable during physical rotation tests.
            android.util.Log.i(
                "PhosphorRotation",
                "detent commit=$committedCardinal deg=$lastSensorDeg -> $target",
            )
            requestedOrientation = target
        }
    }

    private fun exactCurrentOrientation(): Int {
        val rotation = currentDisplayRotation()
        return when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                if (rotation == Surface.ROTATION_270)
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Configuration.ORIENTATION_PORTRAIT ->
                if (rotation == Surface.ROTATION_180)
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun currentDisplayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    // Android can reveal system bars when focus returns, so restore the selected immersive state.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshRotationAuthority(force = true)
            applyImmersive()
            reassertSourceWake()
        }
    }

    override fun orbitBy(dyaw: Float, dpitch: Float) = PhosphorNative.orbitBy(dyaw, dpitch)
    override fun dollyBy(delta: Float) = PhosphorNative.dollyBy(delta)

    override fun setCustomBeam(colors: List<androidx.compose.ui.graphics.Color>, count: Int) {
        val rgb = FloatArray(9)
        colors.take(3).forEachIndexed { i, c ->
            rgb[i * 3] = c.red; rgb[i * 3 + 1] = c.green; rgb[i * 3 + 2] = c.blue
        }
        PhosphorNative.setCustomBeam(rgb, count)
        ui.customCount = count
        // Persist the custom beam immediately because it is edited outside the general save cycle.
        prefs().edit {
            putString("custom_rgb", rgb.joinToString(","))
            putInt("custom_count", count)
        }
    }

    override fun setBeamCycle(seconds: Float, perTrack: Boolean) {
        PhosphorNative.setBeamCycle(seconds, perTrack)
        ui.cycleSeconds = seconds
        ui.cyclePerTrack = perTrack
        prefs().edit {
            putFloat("cycle_seconds", seconds)
            putBoolean("cycle_per_track", perTrack)
        }
    }

    // Photosensitivity acceptance persists forever, as on desktop.
    override fun epilepsyAcknowledged(): Boolean = runtimePrefs().getBoolean("epilepsy_ack", false)
    override fun ackEpilepsy() { runtimePrefs().edit { putBoolean("epilepsy_ack", true) } }

    // Keep mobile tuning limits aligned with the desktop engine.
    private fun applyBeamEnergy(e: Float) { PhosphorNative.setBeamEnergy(e); ui.beamEnergy = e.coerceIn(1f, 30f) }
    private fun applyGlow(g: Float) { PhosphorNative.setGlow(g); ui.glow = g.coerceIn(0f, 0.98f) }
    private fun rollIn(lo: Float, hi: Float) = lo + kotlin.random.Random.nextFloat() * (hi - lo)

    // Manual drag of a rule is a takeover: it disarms that die, exactly like picking a
    // mode disarms the mode-⚄.
    override fun setBeamEnergy(e: Float) { ui.beamRandomArmed = false; applyBeamEnergy(e) }
    override fun setGlow(g: Float) { ui.glowRandomArmed = false; applyGlow(g) }

    // The checkbox is a true toggle: check = arm + roll now; uncheck = disarm, the last
    // rolled value simply stays on the rule.
    override fun tapBeamRandom() {
        ui.beamRandomArmed = !ui.beamRandomArmed
        if (ui.beamRandomArmed) applyBeamEnergy(rollIn(ui.beamRandomLo, ui.beamRandomHi))
    }
    override fun tapGlowRandom() {
        ui.glowRandomArmed = !ui.glowRandomArmed
        if (ui.glowRandomArmed) applyGlow(rollIn(ui.glowRandomLo, ui.glowRandomHi))
    }
    override fun setBeamRandomRange(lo: Float, hi: Float) {
        ui.beamRandomLo = lo.coerceIn(1f, 30f)
        ui.beamRandomHi = hi.coerceIn(ui.beamRandomLo, 30f)
    }
    override fun setGlowRandomRange(lo: Float, hi: Float) {
        ui.glowRandomLo = lo.coerceIn(0f, 0.98f)
        ui.glowRandomHi = hi.coerceIn(ui.glowRandomLo, 0.98f)
    }
    override fun setGeomFx(kind: Int) { ui.geomFx = kind.coerceIn(0, 4); PhosphorNative.setGeomFx(ui.geomFx) }
    override fun setGeomAmount(v: Float) { ui.geomAmount = v.coerceIn(0f, 1f); PhosphorNative.setGeomAmount(ui.geomAmount) }
    override fun setGrid(on: Boolean) { PhosphorNative.setGrid(on); ui.grid = on }
    override fun setGridData(on: Boolean) {
        ui.gridData = on
        ui.gridReading = null
        prefs().edit { putBoolean(dev.phosphor.mobil3.ui.GridData.KEY, on) }
    }

    // ── Deck sheet verbs ──
    override fun openFolder() {
        selectSource()
        openFolderLauncher.launch(null)
    }
    override fun jumpToQueue(index: Int) { controller?.seekTo(index, 0) }

}

/** Failure-only publication. Generic recorder stop must not clear a replacement source face. */
internal fun publishMicReaderFailure(ui: ScopeUiState, isCurrent: () -> Boolean, report: () -> Unit) {
    if (!isCurrent() || !ui.live || ui.sourceLabel != "mic") return
    ui.live = false
    ui.sourceLabel = "no source"
    report()
}

internal fun runtimeInputSource(ui: ScopeUiState, micRecording: Boolean): String = when {
    ui.live && ui.sourceLabel == "capture" -> "capture"
    ui.live && ui.sourceLabel == "mic" && micRecording -> "mic"
    else -> "none"
}
