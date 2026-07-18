package dev.phosphor.mobil3

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
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
import java.io.File

// M5: the app. Compose chrome over the scope SurfaceView; the loaded deck owns the transport
// through a MediaController, so console + notification + lock screen never disagree.
class MainActivity : ComponentActivity(), ScopeActions {

    private val ui = ScopeUiState()
    private val mic = MicController()
    private var controller: MediaController? = null
    private var pendingCaptureRequest = false

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            holder.surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
        override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
            PhosphorNative.surfaceCreatedOrChanged(holder.surface, w, h, resources.displayMetrics.density)
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
            }
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) { mic.start(); ui.sourceLabel = "mic" }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContent { PhosphorScreen(ui, this) }
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
                    }
                })
                ui.playing = c.isPlaying
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        PhosphorNative.setRenderPaused(true)
        controller?.release()
        controller = null
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("open")?.let { openDeck(it, "library") }
        if (intent.getBooleanExtra("capture", false)) startCapture()
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
            runOnUiThread { openDeck(dst.absolutePath, "library") }
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
        val c = controller
        if (c != null) { if (c.isPlaying) c.pause() else c.play() }
        else ui.playing = PhosphorNative.deckToggle() // no session yet (nothing loaded)
    }

    override fun openFile() = openFileLauncher.launch(arrayOf("audio/*"))

    override fun startMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) { mic.start(); ui.sourceLabel = "mic" } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun startCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureConsent.launch(mpm.createScreenCaptureIntent())
    }

    override fun setMode(index: Int) { PhosphorNative.setMode(index); ui.modeIndex = index }
    override fun setBeam(index: Int) { PhosphorNative.setBeamColor(index); ui.beamIndex = index }
    override fun setFps(value: Int) { PhosphorNative.setTargetFps(value); ui.fpsValue = value }
    override fun setRoom(room: Palette) { ui.room = room }
}
