package dev.phosphor.mobil3

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.phosphor.mobil3.ui.Palette
import dev.phosphor.mobil3.ui.PhosphorScreen
import dev.phosphor.mobil3.ui.ScopeActions
import dev.phosphor.mobil3.ui.ScopeUiState
import dev.phosphor.mobil3.ui.readReducedMotion
import java.io.File

// M5: the app. Compose chrome over the scope SurfaceView; the loaded deck owns the transport
// through a MediaController, so console + notification + lock screen never disagree.
class MainActivity : ComponentActivity(), ScopeActions {

    private val ui = ScopeUiState()
    private val mic = MicController()
    private var controller: MediaController? = null
    private var reduced = false
    private var gainValue = 1.0f
    private val tick = Handler(Looper.getMainLooper())

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            holder.surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
        override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
            PhosphorNative.surfaceCreatedOrChanged(holder.surface, w, h, resources.displayMetrics.density)
            // Mobile ships a sharper default focus than the desktop's 1.6 — density-3
            // panels turn the desktop default to fuzz. The settings rule adjusts live.
            PhosphorNative.setFocus(1.1f)
        }
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            PhosphorNative.surfaceDestroyed()
        }
    }

    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { loadUri(it) }
        }

    private val captureConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.let { data ->
                startForegroundService(
                    Intent(this, CaptureService::class.java)
                        .putExtra(CaptureService.EXTRA_RESULT, data)
                )
                ui.sourceLabel = "capture"
                ui.live = true
            }
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) { mic.start(); ui.sourceLabel = "mic"; ui.live = true }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        reduced = readReducedMotion(this)
        PhosphorNative.setReducedMotion(reduced)
        setContent { PhosphorScreen(ui, this, reduced) }
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        PhosphorNative.setRenderPaused(false)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { ui.playing = isPlaying }
                    override fun onMediaMetadataChanged(m: MediaMetadata) {
                        ui.trackTitle = m.title?.toString()
                        ui.trackArtist = m.artist?.toString()
                        // Track boundary: advance a per-song light cycle (engine ignores
                        // it unless per-track cycling is active).
                        PhosphorNative.cycleAdvance()
                    }
                })
                ui.playing = c.isPlaying
            }
        }, MoreExecutors.directExecutor())
        tick.post(uiTick)
    }

    override fun onStop() {
        tick.removeCallbacks(uiTick)
        PhosphorNative.setRenderPaused(true)
        controller?.release()
        controller = null
        super.onStop()
    }

    // One gentle heartbeat for display facts Compose can't observe directly:
    // seek position from the controller, the resting-beam flag, the breathing accent.
    private var baseRoom: Palette? = null
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
            // accent_follows_beam rooms breathe with the live beam (desktop law: 82%
            // toward the beam hue). Recomputed at 2 Hz — gentle, not flickery.
            val base = baseRoom ?: ui.room
            if (base.accentFollowsBeam) {
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

    // Copy the picked document into a cache file named after its real display name (so the
    // deck/session title reads right), then open by path. fd-passing to skip the copy is a
    // documented later optimization.
    private fun loadUri(uri: Uri) {
        Thread {
            val name = queryDisplayName(uri) ?: "track.wav"
            val dst = File(filesDir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                dst.outputStream().use { input.copyTo(it) }
            }
            runOnUiThread { openDeck(dst.absolutePath, "deck") }
        }.start()
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun openDeck(path: String, label: String) {
        mic.stop()
        startService(Intent(this, PlaybackService::class.java).putExtra(PlaybackService.EXTRA_OPEN, path))
        ui.sourceLabel = label
    }

    // ---- ScopeActions ----
    override fun makeSurface(): SurfaceView =
        SurfaceView(this).apply { holder.addCallback(surfaceCallback) }

    override fun togglePlay() {
        if (ui.remote) { PhosphorNative.remoteTransport("playpause"); ui.playing = !ui.playing; return }
        val c = controller
        if (c != null) { if (c.isPlaying) c.pause() else c.play() }
        else ui.playing = PhosphorNative.deckToggle() // no session yet (nothing loaded)
    }

    override fun next() {
        if (ui.remote) PhosphorNative.remoteTransport("next") else controller?.seekToNext()
    }

    override fun prev() {
        if (ui.remote) PhosphorNative.remoteTransport("prev") else controller?.seekToPrevious()
    }

    override fun seekTo(ms: Long) {
        if (!ui.remote) controller?.seekTo(ms)
    }

    override fun startRemote() {
        mic.stop()
        Thread {
            val ok = PhosphorNative.remoteConnect(REMOTE_HOST, REMOTE_PORT)
            runOnUiThread {
                if (ok) {
                    ui.remote = true
                    ui.playing = true
                    ui.sourceLabel = "remote"
                    pollRemoteMeta()
                }
            }
        }.start()
    }

    private fun pollRemoteMeta() {
        if (!ui.remote) return
        Thread {
            while (ui.remote) {
                val m = runCatching { org.json.JSONObject(PhosphorNative.remoteMetadata()) }.getOrNull()
                if (m != null) runOnUiThread {
                    ui.trackTitle = m.optString("title").ifBlank { null }
                    ui.trackArtist = m.optString("artist").ifBlank { null }
                    if (m.has("playing")) ui.playing = m.optBoolean("playing")
                }
                Thread.sleep(1000)
            }
        }.start()
    }

    override fun openFile() = openFileLauncher.launch(arrayOf("audio/*"))

    override fun startMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) { mic.start(); ui.sourceLabel = "mic"; ui.live = true }
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun startCapture() {
        markConsentSeen()
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureConsent.launch(mpm.createScreenCaptureIntent())
    }

    override fun stopLive() {
        mic.stop()
        startService(
            Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)
        )
        PhosphorNative.setRingActive(false)
        ui.live = false
        if (ui.sourceLabel == "capture" || ui.sourceLabel == "mic") ui.sourceLabel = "no source"
    }

    // The consent moment (spec §2.3): one calm card before the system dialog, first time.
    private fun prefs() = getSharedPreferences("phosphor.prefs", MODE_PRIVATE)
    override fun captureConsentNeeded(): Boolean = !prefs().getBoolean("consent_seen", false)
    private fun markConsentSeen() = prefs().edit().putBoolean("consent_seen", true).apply()

    override fun setMode(index: Int) { PhosphorNative.setMode(index); ui.modeIndex = index }

    // Laptop (thinkcenter) over Tailscale. The host list lands with bridge v2 (Act II).
    private val REMOTE_HOST = "100.66.109.56"
    private val REMOTE_PORT = 45777
    override fun setBeam(index: Int) { PhosphorNative.setBeamColor(index); ui.beamIndex = index }
    override fun setFps(value: Int) { PhosphorNative.setTargetFps(value); ui.fpsValue = value }
    override fun setOversample(n: Int) { PhosphorNative.setOversample(n); ui.oversample = n }
    override fun setRoom(room: Palette) { baseRoom = room; ui.room = room }
    override fun setFocus(focus: Float) { PhosphorNative.setFocus(focus) }

    override fun setGainAbsolute(g: Float) {
        gainValue = g.coerceIn(0.05f, 16f)
        PhosphorNative.setGain(gainValue)
        ui.gain = gainValue
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
    }

    override fun setBeamCycle(seconds: Float, perTrack: Boolean) {
        PhosphorNative.setBeamCycle(seconds, perTrack)
        ui.cycleSeconds = seconds
        ui.cyclePerTrack = perTrack
    }

    // Photosensitivity acceptance persists forever, as on desktop.
    override fun epilepsyAcknowledged(): Boolean = prefs().getBoolean("epilepsy_ack", false)
    override fun ackEpilepsy() { prefs().edit().putBoolean("epilepsy_ack", true).apply() }
}
