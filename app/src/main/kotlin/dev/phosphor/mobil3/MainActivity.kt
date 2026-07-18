package dev.phosphor.mobil3

import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat

// M1: the stage. One SurfaceView, edge-to-edge; the Rust render thread owns every pixel.
// Compose chrome arrives with M5 and will layer above this view.
class MainActivity : ComponentActivity(), SurfaceHolder.Callback {

    private lateinit var scope: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The scope stays awake while it burns (plan D6); ember dim comes later.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        scope = SurfaceView(this)
        scope.holder.addCallback(this)
        setContentView(scope)

        // M3: the service owns the deck (MediaSession, focus, notification).
        // adb shell am start … -e open <file-in-filesDir>; SAF picker arrives with M5.
        intent.getStringExtra("open")?.let { path ->
            startService(
                android.content.Intent(this, PlaybackService::class.java)
                    .putExtra(PlaybackService.EXTRA_OPEN, path)
            )
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Android 15 parks many surfaces at 60 Hz unless told otherwise (plan D2).
        holder.surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface is fully sized here; created-before-resume races are avoided by
        // treating changed as the create-or-resize signal.
        val density = resources.displayMetrics.density
        PhosphorNative.surfaceCreatedOrChanged(holder.surface, width, height, density)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        PhosphorNative.surfaceDestroyed() // blocks until Rust dropped the window ref
    }

    override fun onStart() {
        super.onStart()
        PhosphorNative.setRenderPaused(false)
    }

    override fun onStop() {
        // Screen-off does NOT fire surfaceDestroyed — park the render thread ourselves.
        PhosphorNative.setRenderPaused(true)
        super.onStop()
    }
}
