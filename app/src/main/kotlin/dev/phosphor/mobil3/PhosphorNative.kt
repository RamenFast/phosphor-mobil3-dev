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
    /** Beam integration rate: 1×=120, 2×=240, 4×=480 sub-steps per displayed frame. */
    external fun setOversample(n: Int)

    // The instrument feel (Act I): gain/glow/camera verbs + envelope control.
    external fun setGain(gain: Float)
    /** Beam focus px (0.3..3.0, desktop slider) — smaller = sharper. */
    external fun setFocus(focus: Float)
    external fun setGlow(persistence: Float)
    external fun orbitBy(dyaw: Float, dpitch: Float)
    external fun dollyBy(delta: Float)
    external fun setReducedMotion(reduced: Boolean)
    external fun gainNow(): Float
    /** True when an active source has been silent past the sleep window (resting beam up). */
    external fun scopeSilent(): Boolean

    // Custom light + cycle (LIGHT sheet). rgb = 9 floats (3 slots × linear RGB).
    external fun setCustomBeam(rgb: FloatArray, count: Int)
    external fun setBeamCycle(seconds: Float, perTrack: Boolean)
    external fun cycleAdvance()
    /** Live beam color packed 0xRRGGBB (accent_follows_beam chrome breathing). */
    external fun beamColorNow(): Int

    // Remote source (Tailscale bridge): connect to a desktop phosphor-relay.
    external fun remoteConnect(host: String, port: Int): Boolean
    external fun remoteTransport(cmd: String) // "next" | "prev" | "playpause"
    external fun remoteMetadata(): String
    external fun remoteDisconnect()

    // Capture/mic ingest (M4): interleaved stereo f32 chunks into the scope ring.
    external fun pushCaptureSamples(samples: FloatArray, count: Int)
    external fun setRingActive(active: Boolean)

    /** Debug receipts hatch: deterministic offscreen render → selftest.json/png. */
    external fun selfTest(filesDir: String): String

    // Deck: open a local file, drive the transport, read state.
    external fun deckOpen(path: String): Boolean
    external fun deckToggle(): Boolean
    external fun deckPositionMs(): Long
    external fun deckSetPaused(paused: Boolean)
    external fun deckSeekMs(ms: Long): Boolean
    external fun deckClose()
    external fun deckMetadata(): String
    external fun deckCoverArt(): ByteArray?
}
