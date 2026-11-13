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

    // Scope controls (M5).
    external fun setMode(index: Int)
    external fun currentMode(): Int
    external fun setBeamColor(index: Int)
    /** -1 unlimited · 0 panel vsync · N cap to N fps (N above the panel tears, honored). */
    external fun setTargetFps(fps: Int)
    /** DSP reconstruction: 1×=48 kHz, 2×=96 kHz, 4×=192 kHz; one display deposit/frame. */
    external fun setOversample(n: Int)

    // The instrument feel (Act I): gain/glow/camera verbs + envelope control.
    external fun setGain(gain: Float)
    external fun setGainAuto(on: Boolean)
    /** Beam focus px (0.3..3.0, desktop slider) — smaller = sharper. */
    external fun setFocus(focus: Float)
    /** Beam brightness budget (1.0..30.0, desktop "Beam" slider). */
    external fun setBeamEnergy(energy: Float)
    /** Beam-to-gravity quadrant (0..3) — UI-locked mode rotates the figure, not the chrome. */
    external fun setViewRotation(quadrant: Int)
    /** Graticule on/off (desktop grid_enabled). */
    external fun setGrid(on: Boolean)
    external fun setGlow(persistence: Float)
    external fun setReducedMotion(reduced: Boolean)
    external fun gainNow(): Float
    external fun gainAutoNow(): Boolean
    /** True when an active source has been silent past the sleep window (resting beam up). */
    external fun scopeSilent(): Boolean

    // Custom light + cycle (LIGHT sheet). rgb = 9 floats (3 slots × linear RGB).
    external fun setCustomBeam(rgb: FloatArray, count: Int)
    external fun setBeamCycle(seconds: Float, perTrack: Boolean)
    external fun cycleAdvance()
    /** Live beam color packed 0xRRGGBB (accent_follows_beam chrome breathing). */
    external fun beamColorNow(): Int
    /** Existing HUD heartbeat also consumes a <=500ms raw L/R grid_data window, or null for no data. */
    external fun scopeStats(): String

    // Capture/mic ingest (M4): interleaved stereo f32 chunks into the scope ring.
    external fun pushCaptureSamples(samples: FloatArray, count: Int)
    external fun setRingActive(active: Boolean)

    /** Deterministic offscreen render used by the debug self-test. */
    external fun selfTest(filesDir: String): String

    // Deck: open a local file, drive the transport, read state.
    external fun deckValidate(path: String): Boolean
    external fun deckOpen(path: String): Boolean
    /** Exact decoder instance, including same-path reopens. Read on the serial deck worker. */
    external fun deckOpenIdentity(): Long
    /** Only PlaybackTruth's current published new item may confirm its TrackStarted proof. */
    external fun confirmLocalItem(openId: Long)
    external fun deckPublish(paused: Boolean)
    external fun deckToggle(): Boolean
    external fun deckPositionMs(): Long
    external fun deckSetPaused(paused: Boolean)
    external fun deckSeekMs(ms: Long): Boolean
    external fun deckClose()
    external fun deckPollEvent(): String?
    external fun deckMetadata(): String
    external fun deckCoverArt(): ByteArray?
}
