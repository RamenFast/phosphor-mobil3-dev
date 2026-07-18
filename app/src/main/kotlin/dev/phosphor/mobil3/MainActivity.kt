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
            // Ben's call (2026-07-18): maximum sharpness by default — 0.3 px, the
            // bottom of the desktop slider. The settings rule adjusts live.
            PhosphorNative.setFocus(0.3f)
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
                    override fun onTimelineChanged(
                        t: androidx.media3.common.Timeline, reason: Int,
                    ) = syncQueue(c)
                    override fun onMediaItemTransition(
                        item: androidx.media3.common.MediaItem?, reason: Int,
                    ) = syncQueue(c)
                    override fun onMediaMetadataChanged(m: MediaMetadata) {
                        ui.trackTitle = m.title?.toString()
                        ui.trackArtist = m.artist?.toString()
                        ui.artwork = m.artworkData
                        // The session's metadata extras are the remote deck's mirror
                        // channel: source/conn/host without a second state path.
                        val src = m.extras?.getString("source")
                        ui.remote = src == "remote"
                        if (ui.remote) {
                            val conn = m.extras?.getString("conn")
                            val host = m.extras?.getString("host") ?: "remote"
                            ui.sourceLabel = when (conn) {
                                "CONNECTING" -> "remote · connecting…"
                                "LOST" -> "remote · reconnecting…"
                                "FAILED" -> "remote · unreachable"
                                else -> "remote · $host"
                            }
                        }
                        // Track boundary: advance a per-song light cycle (engine ignores
                        // it unless per-track cycling is active).
                        PhosphorNative.cycleAdvance()
                    }
                })
                // Initial sync: the world may have moved while the Activity slept
                // (earbud skips with the screen off) — mirror the session's truth now,
                // not just on the next change event.
                ui.playing = c.isPlaying
                c.mediaMetadata.let { m ->
                    m.title?.toString()?.let { ui.trackTitle = it }
                    ui.trackArtist = m.artist?.toString()
                    val src = m.extras?.getString("source")
                    ui.remote = src == "remote"
                    if (ui.remote) {
                        val host = m.extras?.getString("host") ?: "remote"
                        ui.sourceLabel = "remote · $host"
                    }
                }
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

    override fun startRemote() = startRemoteHost("thinkcenter", "100.66.109.56", 45777)

    // The tailnet hosts the phone knows. Seeded with Ben's two machines; add/edit UI
    // rides the full settings port (Act V). thinkcenter = laptop, interserve-linux = PC.
    override fun remoteHosts(): List<Pair<String, Pair<String, Int>>> = listOf(
        "thinkcenter" to ("100.66.109.56" to 45777),
        "interserve-linux" to ("100.114.165.77" to 45777),
    )

    override fun startRemoteHost(label: String, host: String, port: Int) {
        mic.stop()
        ui.sourceLabel = "remote · connecting…" // honest immediately (kills the race)
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
    override fun setBeam(index: Int) { PhosphorNative.setBeamColor(index); ui.beamIndex = index }
    override fun setFps(value: Int) { PhosphorNative.setTargetFps(value); ui.fpsValue = value }
    override fun setOversample(n: Int) { PhosphorNative.setOversample(n); ui.oversample = n }
    override fun setRoom(room: Palette) { baseRoom = room; ui.room = room }
    override fun setFocus(focus: Float) { PhosphorNative.setFocus(focus) }

    override fun setGainAbsolute(g: Float) {
        gainValue = g.coerceIn(0.1f, 6f)
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

    // Desktop-parity tuning verbs (Ben's audit ask): same fields, same clamps.
    override fun setBeamEnergy(e: Float) { PhosphorNative.setBeamEnergy(e); ui.beamEnergy = e.coerceIn(1f, 30f) }
    override fun setGlow(g: Float) { PhosphorNative.setGlow(g); ui.glow = g.coerceIn(0f, 0.98f) }
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
