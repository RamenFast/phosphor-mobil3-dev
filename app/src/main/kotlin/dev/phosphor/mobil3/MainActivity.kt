package dev.phosphor.mobil3

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
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
import dev.phosphor.mobil3.ui.PENDING_HUD_SETTINGS_IMPORT_KEY
import dev.phosphor.mobil3.ui.settingsExportPreferences
import dev.phosphor.mobil3.ui.settingsImportAppliedCount
import dev.phosphor.mobil3.ui.settingsImportHudOperation
import dev.phosphor.mobil3.ui.pendingHudCleanupResult
import dev.phosphor.mobil3.ui.pendingHudMarkerRead
import dev.phosphor.mobil3.ui.pendingHudMarkerWakeDecision
import dev.phosphor.mobil3.ui.pendingHudResumeDecision
import dev.phosphor.mobil3.ui.PreferenceValueSnapshot
import dev.phosphor.mobil3.ui.PendingHudCleanupResult
import dev.phosphor.mobil3.ui.PendingHudMarkerTransaction
import dev.phosphor.mobil3.ui.hudImportStatusWithCleanup
import dev.phosphor.mobil3.ui.preferenceValueSnapshots
import dev.phosphor.mobil3.settings.SettingsArchive
import dev.phosphor.mobil3.settings.CausalStatePreferences
import dev.phosphor.mobil3.distribution.DistributionCapabilities
import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenMap
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenSetOf
import dev.phosphor.mobil3.store.LOCAL_HUD_MIGRATION_PRINCIPAL_ID
import dev.phosphor.mobil3.store.LOCAL_HUMAN_PRINCIPAL_ID
import dev.phosphor.mobil3.store.PhosphorDispatchResult
import dev.phosphor.mobil3.store.PhosphorStateStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

// M5: the app. Compose chrome over the scope SurfaceView; the loaded deck owns the transport
// through a MediaController, so console + notification + lock screen never disagree.
class MainActivity : ComponentActivity(), ScopeActions {

    private lateinit var causalStore: PhosphorStateStore
    private lateinit var ui: ScopeUiState
    private val mic = MicController()
    private var controller: MediaController? = null
    private var reduced = false
    private var gainValue = 1.0f
    private var lastRandomTrackTitle: String? = null
    private var pendingHudResumeAttempted = false
    @Volatile private var localInFlightPendingHudRaw: String? = null
    private var scopeRotationLockState by mutableStateOf(false)
    private var uiPlacementLockState by mutableStateOf(false)
    private var lockedUiLandscape by mutableStateOf(false)
    private var lockedScopeOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var lockedUiOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    // Orientation sensor (Ben's asks #2–4): the Activity is pinned in three of the
    // four lock modes; this sensor publishes the gravity quadrant and ROUTES it —
    // beam-to-gravity (scope free + UI locked), element-upright (any UI locked), or
    // whole-chrome-to-gravity (scope locked + UI follow). See routeOrientation.
    private var gravityListener: android.hardware.SensorEventListener? = null
    private var lastSourceReopened = false
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
    private var lastRoutedQ = -1
    private var captureStatusReceiverRegistered = false
    private var pendingHudMarkerListenerRegistered = false
    private val pendingHudMarkerListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
        tick.post {
            val decision = pendingHudMarkerWakeDecision(
                changedKey = key,
                rawMarker = pendingHudMarkerRead(shared.all).raw,
                localInFlightRawMarker = localInFlightPendingHudRaw,
                lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                activityFinishing = isFinishing,
                activityDestroyed = isDestroyed,
            )
            if (!decision.shouldWake) return@post
            if (decision.reconcileImportedPreferences) reconcileImportedSettingsPreferences()
            pendingHudResumeAttempted = false
            resumePendingHudSettingsImport()
            pendingHudResumeAttempted = decision.keepResumeAttempted || pendingHudResumeAttempted
        }
    }
    private val tick = Handler(Looper.getMainLooper())
    private val persistGain = Runnable {
        prefs().edit().putFloat("gain", gainValue).apply()
    }
    private val captureStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CaptureService.ACTION_STATUS) {
                applyCaptureStatus(CaptureService.statusFrom(intent))
            }
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            holder.surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
        override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
            PhosphorNative.surfaceCreatedOrChanged(holder.surface, w, h, resources.displayMetrics.density)
            // Ben's default: maximum sharpness (0.3). Persisted; the settings rule adjusts.
            PhosphorNative.setFocus(focusPref)
        }
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            PhosphorNative.surfaceDestroyed()
        }
    }

    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { loadUri(it) }
        }

    // Folder → gapless queue (spec §2.2 Full). Persisted permission so the library
    // survives relaunches; audio files sorted by name = the album order law.
    private val openFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Thread {
                val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
                val audio = root?.listFiles().orEmpty()
                    .filter { f ->
                        f.isFile && (f.type?.startsWith("audio/") == true ||
                            f.name?.substringAfterLast('.')?.lowercase() in
                            setOf("wav", "flac", "mp3", "ogg", "opus", "m4a", "aac", "aiff"))
                    }
                    .sortedBy { it.name?.lowercase() ?: "" }
                if (audio.isEmpty()) return@Thread
                val intent = Intent(this, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_OPEN_QUEUE)
                    .putStringArrayListExtra(
                        PlaybackService.EXTRA_QUEUE_URIS,
                        ArrayList(audio.map { it.uri.toString() }),
                    )
                    .putStringArrayListExtra(
                        PlaybackService.EXTRA_QUEUE_TITLES,
                        ArrayList(audio.map { it.name ?: "track" }),
                    )
                    .putExtra(PlaybackService.EXTRA_QUEUE_START, 0)
                runOnUiThread {
                    startService(intent)
                    ui.sourceLabel = "deck"
                }
            }.start()
        }

    private val createSettingsArchive =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            saveTuning()
            val causalExportSnapshot = causalStore.snapshot
            val causalExportWritable = causalStore.health.writable
            val exportPreferences = settingsExportPreferences(
                allPreferences = prefs().all,
                causalHudMode = when (causalExportSnapshot.effective.displayHud) {
                    "on" -> 0
                    "auto" -> 1
                    "off" -> 2
                    else -> error("unsupported causal display HUD mode ${causalExportSnapshot.effective.displayHud}")
                },
                causalStoreWritable = causalExportWritable,
            )
            ui.settingsTransferStatus = "exporting settings…"
            Thread {
                runCatching {
                    val result = SettingsArchive.export(
                        sourcePackage = packageName,
                        sourceVersion = BuildConfig.VERSION_NAME,
                        sourceDistribution = if (BuildConfig.DEBUG) {
                            "local_dev"
                        } else {
                            DistributionCapabilities.profile.distribution.wireName
                        },
                        exportedAt = java.time.Instant.now().toString(),
                        allPreferences = exportPreferences,
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
                    synchronized(PendingHudMarkerTransaction.lock) {
                        val importedHudMode = imported.values["hud_mode"] as? Int
                        val pendingHudImport = importedHudMode?.let { mode ->
                            settingsImportHudOperation(
                                pendingRaw = pendingHudMarkerRead(prefs().all).raw,
                                contentSha256 = imported.contentSha256,
                                hudMode = mode,
                                newOperationId = UUID.randomUUID().toString(),
                            )
                        }
                        val importedPreferenceKeys = imported.values.keys.filter { it != "hud_mode" }.toSet()
                        val compensatedKeys = importedPreferenceKeys + PENDING_HUD_SETTINGS_IMPORT_KEY
                        val priorValues = preferenceValueSnapshots(prefs().all, compensatedKeys)
                        val pendingHudRaw = pendingHudImport?.encode()
                        localInFlightPendingHudRaw = pendingHudRaw
                        val editor = prefs().edit()
                        imported.values.forEach { (key, value) ->
                            if (key == "hud_mode") return@forEach
                            when (value) {
                                is Boolean -> editor.putBoolean(key, value)
                                is Int -> editor.putInt(key, value)
                                is Float -> editor.putFloat(key, value)
                                is String -> editor.putString(key, value)
                                else -> error("unsupported imported preference type for $key")
                            }
                        }
                        pendingHudRaw?.let {
                            editor.putString(PENDING_HUD_SETTINGS_IMPORT_KEY, it)
                        }
                        if (!editor.commit()) {
                            val restored = restorePreferenceSnapshots(priorValues)
                            if (localInFlightPendingHudRaw == pendingHudRaw) {
                                localInFlightPendingHudRaw = null
                            }
                            error(
                                if (restored) {
                                    "Android could not commit imported settings; restored previous in-memory settings"
                                } else {
                                    "Android could not commit imported settings; previous in-memory settings restore was not committed"
                                },
                            )
                        }
                        Triple(imported, importedHudMode, pendingHudImport)
                    }
                }.onSuccess { (imported, importedHudMode, pendingHudImport) ->
                    runOnUiThread {
                        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || isFinishing || isDestroyed) {
                            // The durable marker is the handoff to the next foreground
                            // Activity/process. Never dispatch through a stale store.
                            pendingHudResumeAttempted = false
                            if (localInFlightPendingHudRaw == pendingHudImport?.encode()) {
                                localInFlightPendingHudRaw = null
                            }
                            return@runOnUiThread
                        }
                        var hudApplied = importedHudMode == null
                        val hudStatus = importedHudMode?.let { mode ->
                            val outcome = applyPendingHudSettingsImport(
                                pending = requireNotNull(pendingHudImport),
                                reason = "Apply display HUD from verified settings archive ${imported.contentSha256.take(12)}.",
                            )
                            hudApplied = outcome.applied
                            outcome.status
                        }
                        if (localInFlightPendingHudRaw == pendingHudImport?.encode()) {
                            localInFlightPendingHudRaw = null
                        }
                        restoreTuning()
                        applyScopeRotationPreference()
                        applyImmersive()
                        updatePictureInPictureParams()
                        ui.settingsTransferStatus = buildString {
                            append("imported ")
                            append(settingsImportAppliedCount(imported.values.size, hudApplied))
                            append(" from ")
                            append(imported.sourceVersion)
                            if (imported.skippedKeys.isNotEmpty()) {
                                append(" · skipped ${imported.skippedKeys.size} newer fields")
                            }
                            if (hudStatus != null) append(" · $hudStatus")
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
            applyLocalGainPolicy()
            mic.stop()
            applyCaptureStatus(CaptureService.CaptureStatus.starting())
            runCatching {
                startForegroundService(
                    Intent(this, CaptureService::class.java)
                        .putExtra(CaptureService.EXTRA_RESULT, data)
                )
            }.onFailure {
                applyCaptureStatus(
                    CaptureService.CaptureStatus.error(
                        "capture service could not start",
                        "Allow Phosphor notifications and foreground media projection, then retry",
                    )
                )
            }
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                applyLocalGainPolicy()
                mic.start(); ui.sourceLabel = "mic"; ui.live = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        causalStore = (application as PhosphorApplication).causalStore
        ui = ScopeUiState(causalStore)
        enableEdgeToEdge()
        // The scope is something you WATCH, so the screen must not dim or lock under it.
        // This is sufficient on its own: the flag holds a SCREEN_BRIGHT_WAKE_LOCK for as
        // long as this window is in front (verified on the S25), and Doze/App Standby
        // only bite once the screen is off, which this prevents. A playback foreground
        // service covers the audio path when the app is backgrounded. No battery
        // optimisation exemption is requested, because none is needed and asking for one
        // is a Play-policy liability for no user benefit.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        reduced = readReducedMotion(this)
        PhosphorNative.setReducedMotion(reduced)
        ui.bindRandomModeRequest(::armAndRollRandomMode)
        restoreTuning()
        refreshCaptureMetadataAccess()
        applyScopeRotationPreference()
        // Fullscreen by default (Ben's ask): the scope owns the whole panel;
        // system bars return transiently on an edge swipe.
        applyImmersive()
        setContent { PhosphorScreen(ui, this, reduced) }
        // PiP (spec §3): Home while the beam is live → the scope becomes the floating
        // window. Post once so the source-rectangle hint uses laid-out content bounds.
        window.decorView.post { updatePictureInPictureParams() }
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!pendingHudResumeAttempted) {
            pendingHudResumeAttempted = true
            resumePendingHudSettingsImport()
        }
        refreshCaptureMetadataAccess()
        // Reopen the last source once per process (Ben's ask): capture re-raises the
        // system share dialog (only if the in-app consent was already given some day);
        // mic restarts silently. Deck/remote stay manual — files and networks are
        // deliberate acts.
        if (!lastSourceReopened) {
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
        // The SurfaceView is still full-bleed and receives its new buffer dimensions
        // through surfaceChanged. Only PiP's advertised frame needs explicit refresh.
        updatePictureInPictureParams()
    }

    private fun updatePictureInPictureParams() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sourceRectHint = Rect()
        val hasSourceRectHint = window.decorView.getGlobalVisibleRect(sourceRectHint)
        val builder = android.app.PictureInPictureParams.Builder()
            .setAutoEnterEnabled(true)
            .setAspectRatio(
                if (landscape) android.util.Rational(16, 9)
                else android.util.Rational(9, 16)
            )
        if (hasSourceRectHint) builder.setSourceRectHint(sourceRectHint)
        setPictureInPictureParams(builder.build())
    }

    override fun onStart() {
        super.onStart()
        if (!pendingHudMarkerListenerRegistered) {
            prefs().registerOnSharedPreferenceChangeListener(pendingHudMarkerListener)
            pendingHudMarkerListenerRegistered = true
        }
        if (!captureStatusReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                captureStatusReceiver,
                IntentFilter(CaptureService.ACTION_STATUS),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            captureStatusReceiverRegistered = true
        }
        applyCaptureStatus(CaptureService.currentStatus())
        PhosphorNative.setRenderPaused(false)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { ui.playing = isPlaying }
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
                    }
                    override fun onMediaItemTransition(
                        item: androidx.media3.common.MediaItem?, reason: Int,
                    ) = syncQueue(c)
                    override fun onMediaMetadataChanged(m: MediaMetadata) {
                        syncSessionFace(c, m)
                        // Track boundary: advance a per-song light cycle (engine ignores
                        // it unless per-track cycling is active).
                        PhosphorNative.cycleAdvance()
                    }
                })
                // Initial sync: the world may have moved while the Activity slept
                // (earbud skips with the screen off) — mirror the session's truth now,
                // not just on the next change event.
                ui.playing = c.isPlaying
                syncSessionFace(c)
            }
        }, MoreExecutors.directExecutor())
        tick.post(uiTick)
    }

    override fun onStop() {
        saveTuning()
        tick.removeCallbacks(uiTick)
        PhosphorNative.setRenderPaused(true)
        if (pendingHudMarkerListenerRegistered) {
            prefs().unregisterOnSharedPreferenceChangeListener(pendingHudMarkerListener)
            pendingHudMarkerListenerRegistered = false
        }
        if (captureStatusReceiverRegistered) {
            unregisterReceiver(captureStatusReceiver)
            captureStatusReceiverRegistered = false
        }
        controller?.release()
        controller = null
        super.onStop()
    }

    // One gentle heartbeat for display facts Compose can't observe directly:
    // seek position from the controller, the resting-beam flag, the breathing accent.
    private var baseRoom: Palette? = null
    private var lastRxBytes = 0L
    private val uiTick = object : Runnable {
        override fun run() {
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
            // Gain readouts are receipts, not preference echoes. Local follows the
            // render-thread glide; remote follows the desktop K/status truth.
            ui.localAutoGain = PhosphorNative.gainAutoNow()
            if (!ui.remoteGeometry) ui.gain = PhosphorNative.gainNow()
            if (ui.remote) {
                remoteGain?.let { ui.autoGain = it.optBoolean("auto", false) }
            } else {
                ui.autoGain = ui.localAutoGain
            }
            // Band honesty (Ben's ask): while the desktop renders the beam, the
            // band shows ITS mode + live gain — `auto · pc` under autogain.
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
            if (ui.hudMode != 2) {
                val stats = runCatching {
                    org.json.JSONObject(PhosphorNative.scopeStats())
                }.getOrNull()
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
            // Every room breathes with the live beam now (Ben's ask: the moving parts
            // of the chrome — seek, rule fills — glow the trace's phosphor). Rooms with
            // accent_follows_beam additionally let it take the structural accent
            // (desktop law: 82% toward the beam hue). Recomputed at 2 Hz.
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
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("open")?.let { openDeck(it, "deck") }
        if (intent.getBooleanExtra("capture", false)) startCapture()
        if (intent.getBooleanExtra("remote", false)) startRemote()
    }

    // Copy the picked document into files/staged named after its real display name (so
    // the deck/session title reads right), then open by path. Staged copies are
    // transient — earlier singles are deleted before the new one lands, and the service
    // sweeps the whole staged dir on create/destroy (441 MB leak, Ben's storage audit).
    // fd-passing to skip the copy is a documented later optimization.
    private fun loadUri(uri: Uri) {
        Thread {
            val name = queryDisplayName(uri) ?: "track.wav"
            val staged = File(filesDir, "staged").apply { mkdirs() }
            staged.listFiles()?.forEach { if (it.isFile && it.name != name) it.delete() }
            val dst = File(staged, name)
            contentResolver.openInputStream(uri)?.use { input ->
                dst.outputStream().use { input.copyTo(it) }
            }
            runOnUiThread { openDeck(dst.absolutePath, "deck") }
        }.start()
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
        acceptTrackTitle(metadata.title?.toString())
        ui.trackArtist = metadata.artist?.toString()
        ui.artwork = metadata.artworkData?.takeIf { it.isNotEmpty() }
        val source = metadata.extras?.getString("source")
        ui.remote = source == "remote"
        when (source) {
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
                    // Greeted but no frame yet. The old code called this connected, which
                    // claimed a live link before anything had flowed.
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

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun openDeck(path: String, label: String) {
        mic.stop()
        stopCaptureService()
        ui.live = false
        applyLocalGainPolicy()
        startService(Intent(this, PlaybackService::class.java).putExtra(PlaybackService.EXTRA_OPEN, path))
        ui.sourceLabel = label
    }

    // ---- ScopeActions ----
    override fun makeSurface(): SurfaceView =
        SurfaceView(this).apply { holder.addCallback(surfaceCallback) }

    // The transport law, unified: ALL transport goes through the one MediaController —
    // the session's player routes to the local deck or the bridge. Notification, lock
    // screen, earbuds and the console are therefore the same code path.
    override fun togglePlay() {
        val c = controller
        if (c != null) { if (c.playWhenReady) c.pause() else c.play() }
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
        RemoteHostStore(this, DistributionCapabilities.profile.seededRemoteHosts)
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
        mic.stop()
        prefs().edit().putFloat("gain", gainValue).apply()
        ui.sourceLabel = "remote · connecting…" // honest immediately (kills the race)
        ui.remoteFailure = "" // a fresh attempt clears the previous failure's fix
        ui.remote = true
        startService(
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
        startService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_REMOTE_DISCONNECT)
        )
        ui.remote = false
        ui.sourceLabel = "no source"
        applyLocalGainPolicy()
    }

    override fun openFile() = openFileLauncher.launch(arrayOf("audio/*"))

    override fun exportSettings() = createSettingsArchive.launch(
        "phosphor-settings-${BuildConfig.VERSION_NAME}.phossettings"
    )

    override fun importSettings() = openSettingsArchive.launch(
        arrayOf("application/json", "application/octet-stream", "text/plain")
    )

    override fun startMic() {
        stopCaptureService()
        ui.live = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) { applyLocalGainPolicy(); mic.start(); ui.sourceLabel = "mic"; ui.live = true }
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun startCapture() {
        markConsentSeen()
        // Already capturing: do NOT ask again. Android issues a single-use projection
        // token, so a redundant prompt would tear down a working session to rebuild an
        // identical one, and the user would blame us for the extra dialog. `live` plus a
        // capture source is the honest signal that a projection is currently held.
        if (ui.live && ui.sourceLabel.startsWith("capture")) return
        ui.captureStatus = "waiting for Android capture permission"
        ui.captureFix = "Approve the prompt to connect playback audio"
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Ask for the whole display, not a single app. The row this comes from says
        // "everything playing"; with no config Android 14+ preselects "Share one app",
        // so the dialog contradicted its own label and quietly captured one app's audio.
        // The user can still narrow it in the dialog — we just stop defaulting to the
        // opposite of what we promised.
        captureConsent.launch(
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),
        )
    }

    override fun stopLive() {
        mic.stop()
        stopCaptureService()
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

    private fun stopCaptureService() {
        startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
    }

    private fun applyCaptureStatus(status: CaptureService.CaptureStatus) {
        val wasCapture = ui.sourceLabel.startsWith("capture")
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

    // The consent moment (spec §2.3): one calm card before the system dialog, first time.
    private fun prefs() = getSharedPreferences(PhosphorApplication.PREFERENCES_NAME, MODE_PRIVATE)
    private fun runtimePrefs() = getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, MODE_PRIVATE)

    // ── Tuning persistence: the scope remembers its knobs across launches. ──
    private var focusPref = 0.3f
    private fun saveTuning() {
        prefs().edit()
            .putInt("mode", ui.modeIndex)
            .putBoolean("random_mode_armed", ui.randomModeArmed)
            .putString("random_ban_modes", ui.randomBanModes.sorted().joinToString(","))
            .putInt("beam", ui.beamIndex)
            .putInt("fps", ui.fpsValue)
            .putInt("oversample", ui.oversample)
            // AUTO-GAIN breathes ui.gain; the manual landing remains the saved knob.
            .putFloat("gain", gainValue)
            .putFloat("beam_energy", ui.beamEnergy)
            .putFloat("glow", ui.glow)
            .putBoolean("beam_random_armed", ui.beamRandomArmed)
            .putString("beam_random_range", "${ui.beamRandomLo},${ui.beamRandomHi}")
            .putBoolean("glow_random_armed", ui.glowRandomArmed)
            .putString("glow_random_range", "${ui.glowRandomLo},${ui.glowRandomHi}")
            .putInt("geom_fx", ui.geomFx)
            .putFloat("geom_amount", ui.geomAmount)
            .putBoolean("grid", ui.grid)
            .putFloat("focus", focusPref)
            .putString("room", ui.room.id)
            .putBoolean("auto_gain", prefs().getBoolean("auto_gain", ui.autoGain))
            .putInt("band_mode", ui.bandMode)
            .putBoolean("fullscreen", ui.fullscreen)
            .putBoolean("scope_rotation_locked", scopeRotationLockState)
            .putInt("scope_locked_orientation", lockedScopeOrientation)
            .putBoolean("ui_placement_locked", uiPlacementLockState)
            .putBoolean("ui_locked_landscape", lockedUiLandscape)
            .putInt("remote_latency_mode", ui.latencyMode)
            .putInt("remote_network_mode", ui.networkMode)
            .putBoolean("amoled_seen", ui.amoledCaptionSeen)
            .putInt("ov_char", ui.styleOverride.character?.ordinal ?: -1)
            .putInt("ov_motion", ui.styleOverride.motion?.ordinal ?: -1)
            .putInt("ov_radius", ui.styleOverride.radiusDp ?: -1)
            .putInt("ov_desig", when (ui.styleOverride.designators) {
                null -> -1; true -> 1; false -> 0
            })
            .apply()
        runtimePrefs().edit()
            .putString("random_track_title", lastRandomTrackTitle)
            // The remembered input and calibration date are device runtime metadata.
            .putString(
                "last_source",
                when {
                    ui.live && ui.sourceLabel == "capture" -> "capture"
                    ui.live && ui.sourceLabel == "mic" -> "mic"
                    else -> "none"
                },
            )
            .putString(
                "cal_date",
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date()),
            )
            .apply()
    }

    private fun restoreTuning() {
        val p = prefs()
        ui.modeIndex = p.getInt("mode", 0).also { PhosphorNative.setMode(it) }
        ui.randomModeArmed = p.getBoolean("random_mode_armed", false)
        lastRandomTrackTitle = runtimePrefs().getString("random_track_title", null)
        ui.randomBanModes = (p.getString("random_ban_modes", "") ?: "")
            .split(",").mapNotNull { it.toIntOrNull() }
            .filter { it in dev.phosphor.mobil3.ui.ModeLabels.indices }.toSet()
            .let { if (dev.phosphor.mobil3.ui.ModeLabels.size - it.size < 2) emptySet() else it }
        ui.beamIndex = p.getInt("beam", 0).also { PhosphorNative.setBeamColor(it) }
        ui.fpsValue = p.getInt("fps", 0).also { PhosphorNative.setTargetFps(it) }
        ui.oversample = p.getInt("oversample", 1).also { PhosphorNative.setOversample(it) }
        gainValue = p.getFloat("gain", 1f)
        ui.gain = gainValue
        PhosphorNative.setGain(gainValue)
        val autoGain = p.getBoolean("auto_gain", false)
        PhosphorNative.setGainAuto(autoGain)
        ui.autoGain = autoGain
        ui.localAutoGain = autoGain
        ui.beamEnergy = p.getFloat("beam_energy", 8f).also { PhosphorNative.setBeamEnergy(it) }
        ui.glow = p.getFloat("glow", 0.7f).also { PhosphorNative.setGlow(it) }
        // The dice: restore range + armed state; never roll on restore — the last landed
        // BEAM/GLOW values above are the truth until the next track boundary.
        fun range(key: String, min: Float, max: Float, dLo: Float, dHi: Float): Pair<Float, Float> {
            val parts = (p.getString(key, "") ?: "").split(",").mapNotNull { it.toFloatOrNull() }
            if (parts.size != 2) return dLo to dHi
            val lo = parts[0].coerceIn(min, max)
            return lo to parts[1].coerceIn(lo, max)
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
        ui.geomFx = p.getInt("geom_fx", 0).coerceIn(0, 4).also { PhosphorNative.setGeomFx(it) }
        ui.geomAmount = p.getFloat("geom_amount", 0.6f).coerceIn(0f, 1f)
            .also { PhosphorNative.setGeomAmount(it) }
        ui.grid = p.getBoolean("grid", true).also { PhosphorNative.setGrid(it) }
        focusPref = p.getFloat("focus", 0.3f)
        ui.bandMode = p.getInt("band_mode", 0)
        ui.fullscreen = p.getBoolean("fullscreen", true)
        ui.viewLock = p.getBoolean("view_lock", false)
        scopeRotationLockState = p.getBoolean("scope_rotation_locked", false)
        lockedScopeOrientation = p.getInt(
            "scope_locked_orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
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
        ui.networkMode = p.getInt("remote_network_mode", 0).coerceIn(0, 2)
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
        dev.phosphor.mobil3.ui.paletteById(p.getString("room", "blossom_dark") ?: "blossom_dark")
            .let { baseRoom = it; ui.room = it }
        // Custom light survives relaunch (persistence-audit gap, Ben's ask).
        val customCount = p.getInt("custom_count", 0).coerceIn(0, 3)
        val customRgb = p.getString("custom_rgb", null)
            ?.split(",")?.mapNotNull { it.toFloatOrNull() }
        ui.cycleSeconds = p.getFloat("cycle_seconds", 3.0f)
        ui.cyclePerTrack = p.getBoolean("cycle_per_track", false)
        if (customCount >= 1 && customRgb?.size == 9) {
            ui.customColors = (0..2).map {
                androidx.compose.ui.graphics.Color(
                    customRgb[it * 3], customRgb[it * 3 + 1], customRgb[it * 3 + 2],
                )
            }
            ui.customCount = customCount
            PhosphorNative.setCustomBeam(customRgb.toFloatArray(), customCount)
            PhosphorNative.setBeamCycle(ui.cycleSeconds, ui.cyclePerTrack)
        }
    }
    override fun captureConsentNeeded(): Boolean = !runtimePrefs().getBoolean("consent_seen", false)
    private fun markConsentSeen() = runtimePrefs().edit().putBoolean("consent_seen", true).apply()

    override fun setViewLock(on: Boolean) {
        ui.viewLock = on
        prefs().edit().putBoolean("view_lock", on).apply()
    }

    override fun openCaptureMetadataSettings() {
        // Land on OUR toggle directly (API 30+): "notification ACCESS" is a different
        // switch than the app-info "allow notifications" one, and the ambiguity already
        // cost a round trip with Ben. Fall back to the full access list.
        val component = ComponentName(this, CaptureNotificationListenerService::class.java)
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
        prefs().edit().putBoolean("bestiary_found", true).apply() // found is forever
    }

    override fun openLink(url: String) {
        // Cards leave through the user's own browser — the app renders no web content.
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
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
        // Remote-render control (Ben's ask): while the DESKTOP renders the beam,
        // the mode tap drives the desktop scope over the bridge. ModeTags are the
        // desktop's own mode names, verbatim.
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
        prefs().edit().putBoolean("auto_gain", false).apply()
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
        prefs().edit().putBoolean("auto_gain", on).apply()
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
        val result = dispatchHudMode(
            mode = mode,
            principal = PrincipalId(PrincipalKind.HUMAN, LOCAL_HUMAN_PRINCIPAL_ID),
            transport = Transport.UI,
            idempotencyKey = UUID.randomUUID().toString(),
            reason = "Local user changed display HUD from settings.",
        )
        ui.hudControlStatus = hudResultText(result)
    }

    private fun dispatchHudMode(
        mode: Int,
        principal: PrincipalId,
        transport: Transport,
        idempotencyKey: String,
        reason: String,
    ): PhosphorDispatchResult {
        val wireMode = when (mode) {
            0 -> "on"
            1 -> "auto"
            2 -> "off"
            else -> return PhosphorDispatchResult.Failed(
                dev.phosphor.mobil3.state.Refusal(
                    dev.phosphor.mobil3.state.RefusalCode.INVALID_VALUE,
                    "Use HUD mode on, auto, or off.",
                    causalStore.snapshot.revision,
                ),
            )
        }
        return causalStore.dispatch(
            action = SetDisplayHud(wireMode),
            request = ActionRequest(
                principal = principal,
                idempotencyKey = idempotencyKey,
                expectedRevision = causalStore.snapshot.revision,
                reason = reason,
                requestedCapability = Capability.CONTROL_DISPLAY,
                transport = transport,
            ),
            monotonicMillis = SystemClock.elapsedRealtime(),
            wallTimeMillis = System.currentTimeMillis(),
        )
    }

    private fun hudResultText(result: PhosphorDispatchResult): String = when (result) {
        is PhosphorDispatchResult.Accepted -> if (result.acknowledgement.changed) {
            "HUD accepted · revision ${result.acknowledgement.revision}"
        } else {
            "HUD already ${ui.hudModeLabel()}"
        }
        is PhosphorDispatchResult.Replayed -> "HUD import replayed · ${result.acknowledgement.receiptId}"
        is PhosphorDispatchResult.Refused -> "HUD refused · ${result.refusal.fix}"
        is PhosphorDispatchResult.Failed -> "HUD unavailable · ${result.refusal.fix}"
    }

    private data class PendingHudApplyOutcome(
        val applied: Boolean,
        val status: String,
    )

    private fun resumePendingHudSettingsImport() {
        val marker = pendingHudMarkerRead(prefs().all)
        if (marker.wrongType) {
            pendingHudResumeAttempted = true
            marker.status?.let { ui.settingsTransferStatus = it }
            return
        }
        val raw = marker.raw ?: return
        val decision = pendingHudResumeDecision(
            rawMarker = raw,
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            activityFinishing = isFinishing,
            activityDestroyed = isDestroyed,
        )
        pendingHudResumeAttempted = decision.attempted
        if (!decision.shouldDispatch) {
            decision.status?.let { ui.settingsTransferStatus = it }
            return
        }
        val pending = dev.phosphor.mobil3.ui.PendingHudSettingsImport.decode(raw)
            ?: return
        val outcome = applyPendingHudSettingsImport(
            pending = pending,
            reason = "Resume verified settings HUD import ${pending.contentSha256.take(12)} after lifecycle recovery.",
        )
        ui.settingsTransferStatus = "resumed HUD import · ${outcome.status}"
    }

    private fun reconcileImportedSettingsPreferences() {
        restoreTuning()
        applyScopeRotationPreference()
        applyImmersive()
        updatePictureInPictureParams()
    }

    private fun applyPendingHudSettingsImport(
        pending: dev.phosphor.mobil3.ui.PendingHudSettingsImport,
        reason: String,
    ): PendingHudApplyOutcome {
        val result = dispatchHudMode(
            mode = pending.hudMode,
            principal = PrincipalId(PrincipalKind.MIGRATION, LOCAL_HUD_MIGRATION_PRINCIPAL_ID),
            transport = Transport.MIGRATION,
            idempotencyKey = pending.idempotencyKey,
            reason = reason,
        )
        val applied = result is PhosphorDispatchResult.Accepted || result is PhosphorDispatchResult.Replayed
        if (!applied) return PendingHudApplyOutcome(false, hudResultText(result))

        val status = hudImportStatusWithCleanup(
            hudStatus = hudResultText(result),
            cleanup = clearPendingHudImportMarker(pending.encode()),
        )
        return PendingHudApplyOutcome(true, status)
    }

    private fun clearPendingHudImportMarker(expectedRaw: String): PendingHudCleanupResult {
        synchronized(PendingHudMarkerTransaction.lock) {
            val allBeforeCleanup = prefs().all
            val current = pendingHudMarkerRead(allBeforeCleanup).raw
            if (current != expectedRaw) {
                val observedMarker = pendingHudMarkerRead(prefs().all)
                return pendingHudCleanupResult(
                    expectedRaw = expectedRaw,
                    currentRaw = current,
                    observedRawAfterCleanup = observedMarker.raw,
                    observedWrongTypeAfterCleanup = observedMarker.wrongType,
                    removeCommitted = false,
                    restoreCommitted = false,
                )
            }
            val priorValues = preferenceValueSnapshots(
                allBeforeCleanup,
                setOf(PENDING_HUD_SETTINGS_IMPORT_KEY),
            )
            val removeCommitted = prefs().edit().remove(PENDING_HUD_SETTINGS_IMPORT_KEY).commit()
            val restoreCommitted = if (!removeCommitted) {
                // Android SharedPreferences mutates its in-memory map before commit() reports
                // a disk failure. Restore the durable operation marker in memory as well, so
                // this process and the next restart agree that cleanup is still pending.
                restorePreferenceSnapshots(priorValues)
            } else {
                false
            }
            val observedMarker = pendingHudMarkerRead(prefs().all)
            return pendingHudCleanupResult(
                expectedRaw = expectedRaw,
                currentRaw = current,
                observedRawAfterCleanup = observedMarker.raw,
                observedWrongTypeAfterCleanup = observedMarker.wrongType,
                removeCommitted = removeCommitted,
                restoreCommitted = restoreCommitted,
            )
        }
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

    private fun ScopeUiState.hudModeLabel(): String = when (hudMode) {
        0 -> "on"
        1 -> "auto"
        else -> "off"
    }

    override fun setRemoteLatencyMode(mode: Int) {
        ui.latencyMode = mode.coerceIn(0, 2)
        prefs().edit().putInt("remote_latency_mode", ui.latencyMode).apply()
        PhosphorNative.remoteSetLatencyMode(ui.latencyMode)
    }

    override fun setRemoteNetworkMode(mode: Int) {
        ui.networkMode = mode.coerceIn(0, 2)
        prefs().edit().putInt("remote_network_mode", ui.networkMode).apply()
        if (ui.remote) {
            startService(
                Intent(this, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_REMOTE_POLICY_CHANGED)
            )
        }
    }

    private fun applyLocalGainPolicy() {
        val on = prefs().getBoolean("auto_gain", false)
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
        prefs().edit().putBoolean("fullscreen", on).apply()
        applyImmersive()
    }

    override fun isScopeRotationLocked(): Boolean = scopeRotationLockState

    override fun setScopeRotationLocked(locked: Boolean) {
        if (scopeRotationLockState == locked) return
        scopeRotationLockState = locked
        if (locked) lockedScopeOrientation = exactCurrentOrientation()
        prefs().edit()
            .putBoolean("scope_rotation_locked", locked)
            .putInt("scope_locked_orientation", lockedScopeOrientation)
            .apply()
        applyScopeRotationPreference()
        // The sensor must run for scope-locked + UI-follow (chrome-to-gravity) too.
        updateOrientationSensor()
    }

    override fun isUiPlacementLocked(): Boolean = uiPlacementLockState
    override fun lockedUiLandscape(): Boolean = lockedUiLandscape

    override fun setUiPlacementLocked(locked: Boolean) {
        if (uiPlacementLockState == locked) return
        if (locked) {
            lockedUiLandscape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            lockedUiOrientation = exactCurrentOrientation()
        }
        uiPlacementLockState = locked
        applyScopeRotationPreference()
        updateOrientationSensor()
        prefs().edit()
            .putBoolean("ui_placement_locked", locked)
            .putBoolean("ui_locked_landscape", lockedUiLandscape)
            .putInt("ui_locked_orientation", lockedUiOrientation)
            .apply()
    }

    private fun applyScopeRotationPreference() {
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
            // Return rotation ownership to Android. This follows the sensor when the
            // system allows rotation and respects the user's OS-level rotation lock.
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Runs whenever (scopeRotationLocked || uiPlacementLocked). A single sensor; the
    // per-mode routing lives in routeOrientation.
    private fun updateOrientationSensor() {
        val needed = scopeRotationLockState || uiPlacementLockState
        if (needed) {
            if (gravityListener == null) {
                val sm = getSystemService(android.hardware.SensorManager::class.java)
                val accel = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
                if (accel != null) {
                    gravityListener = object : android.hardware.SensorEventListener {
                        private var gx = 0f; private var gy = 0f; private var gz = 0f
                        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
                        override fun onSensorChanged(e: android.hardware.SensorEvent) {
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
                            committedCardinal = RotationDetent.nearestCardinal(degrees)
                            if (degrees == lastSensorDeg) return
                            lastSensorDeg = degrees
                            routeOrientation()
                        }
                    }.also {
                        sm.registerListener(
                            it, accel, android.hardware.SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }
            }
            // A mode toggle re-routes the last known gravity now: the sensor only fires
            // on CHANGE, so a stationary phone would otherwise keep the prior mode's fields.
            routeOrientation(force = true)
        } else {
            gravityListener?.let {
                getSystemService(android.hardware.SensorManager::class.java)
                    ?.unregisterListener(it)
            }
            gravityListener = null
            lastRoutedQ = -1
            ui.uprightQuadrant = 0
            ui.chromeQuadrant = 0
            PhosphorNative.setViewRotation(0)
        }
    }

    // The single routing point — the asks-#4 matrix. q = CCW quadrants from the
    // pinned display to gravity-up (same figure the beam-rotation verb consumes).
    private fun routeOrientation(force: Boolean = false) {
        if (!(scopeRotationLockState || uiPlacementLockState)) return
        val degrees = lastSensorDeg
        if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return
        val deviceQ = ((degrees + 45) / 90) % 4          // clockwise from natural
        val displayQ = display?.rotation ?: Surface.ROTATION_0
        // Sign fixed by Ben's field receipt ("right way if they weren't upside down —
        // it thinks it's on the wrong side"): device CW = content CCW on the pinned
        // screen, i.e. +deviceQ in our CCW quadrant convention, not its inverse.
        val q = ((deviceQ - displayQ) % 4 + 4) % 4
        if (q == lastRoutedQ && !force) return
        lastRoutedQ = q
        when {
            uiPlacementLockState -> {
                // Element-upright in BOTH UI-locked modes. Beam-to-gravity only when
                // the scope is free; when the scope is also locked the beam stays put.
                ui.uprightQuadrant = q
                ui.chromeQuadrant = 0
                PhosphorNative.setViewRotation(if (scopeRotationLockState) 0 else q)
            }
            scopeRotationLockState -> {
                // Scope locked + UI follow: the whole chrome rotates to gravity; the
                // scope stays pinned and upright (no beam rotation to gravity here).
                ui.chromeQuadrant = q
                ui.uprightQuadrant = 0
                PhosphorNative.setViewRotation(0)
            }
        }
    }

    private fun exactCurrentOrientation(): Int {
        val rotation = display?.rotation ?: Surface.ROTATION_0
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

    // Android can undo an onCreate-time hide when the window (re)gains focus —
    // the classic immersive pattern re-asserts here (caught by a live receipt).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
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
        // Persistence-audit gap (Ben's ask): a custom light must survive relaunch —
        // saved immediately, like the other live-settings setters.
        prefs().edit()
            .putString("custom_rgb", rgb.joinToString(","))
            .putInt("custom_count", count)
            .apply()
    }

    override fun setBeamCycle(seconds: Float, perTrack: Boolean) {
        PhosphorNative.setBeamCycle(seconds, perTrack)
        ui.cycleSeconds = seconds
        ui.cyclePerTrack = perTrack
        prefs().edit()
            .putFloat("cycle_seconds", seconds)
            .putBoolean("cycle_per_track", perTrack)
            .apply()
    }

    // Photosensitivity acceptance persists forever, as on desktop.
    override fun epilepsyAcknowledged(): Boolean = runtimePrefs().getBoolean("epilepsy_ack", false)
    override fun ackEpilepsy() { runtimePrefs().edit().putBoolean("epilepsy_ack", true).apply() }

    // Desktop-parity tuning verbs (Ben's audit ask): same fields, same clamps.
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

    // ── Deck sheet verbs ──
    override fun openFolder() = openFolderLauncher.launch(null)
    override fun jumpToQueue(index: Int) { controller?.seekTo(index, 0) }

    private val audioMan by lazy { getSystemService(AUDIO_SERVICE) as android.media.AudioManager }
    override fun volumeFrac(): Float {
        val max = audioMan.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val cur = audioMan.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        // Inverse of the cubic taper so the rule position matches perception.
        return Math.cbrt((cur.toFloat() / max).toDouble()).toFloat()
    }

    override fun setVolume(frac: Float) {
        val max = audioMan.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val cubic = frac.coerceIn(0f, 1f).let { it * it * it } // spec: cubic-taper rule
        audioMan.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            (cubic * max).toInt().coerceIn(0, max),
            0,
        )
    }
}
