package dev.phosphor.mobil3

import android.view.Surface

// The one JNI seam. Grows per plan (command channel, events); M1 carries the surface
// lifecycle + the engine handshake.
object PhosphorNative {
    init {
        System.loadLibrary("phosphor_mobil3_core")
    }

    /** JSON string from the Rust core proving the engine crates link and run on-device. */
    external fun engineInfo(): String

    // Surface lifecycle (render thread). surfaceChanged is idempotent on the Rust side:
    // same-surface resize reconfigures, new surface rebuilds.
    fun surfaceCreatedOrChanged(surface: Surface, width: Int, height: Int, density: Float) =
        surfaceCreated(surface, width, height, density)

    private external fun surfaceCreated(surface: Surface, width: Int, height: Int, density: Float)
    external fun surfaceChanged(width: Int, height: Int)

    /** Blocks until the Rust render thread dropped the wgpu surface + window ref. */
    external fun surfaceDestroyed()

    external fun setRenderPaused(paused: Boolean)

    /** Debug receipts hatch: deterministic offscreen render → selftest.json/png. */
    external fun selfTest(filesDir: String): String

    // Deck (M2): open a local file, toggle play/pause, read the position clock.
    external fun deckOpen(path: String): Boolean
    external fun deckToggle(): Boolean
    external fun deckPositionMs(): Long
}
