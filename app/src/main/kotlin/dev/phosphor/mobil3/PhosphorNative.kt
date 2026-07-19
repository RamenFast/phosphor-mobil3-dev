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
    /** Bottom pull 0..1: raises real beam deposit energy; P7 textures own its decay. */
    external fun setBloomPull(pull: Float)
    /** Beam-to-gravity quadrant (0..3) — UI-locked mode rotates the figure, not the chrome. */
    external fun setViewRotation(quadrant: Int)
    /** Geometry FX stage: 0 off · 1 kaleido · 2 spin · 3 tunnel · 4 pulse (phone-local). */
    external fun setGeomFx(kind: Int)
    /** Geometry FX depth 0..1. */
    external fun setGeomAmount(amount: Float)
    /** Graticule on/off (desktop grid_enabled). */
    external fun setGrid(on: Boolean)
    external fun setGlow(persistence: Float)
    external fun orbitBy(dyaw: Float, dpitch: Float)
    external fun dollyBy(delta: Float)
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
    /** Nerd-HUD stats: {"fps":119.9,"segs":960}. */
    external fun scopeStats(): String

    // Remote source (Tailscale bridge, protocol v2 — docs/BRIDGE.md).
    // connect is NON-BLOCKING: it spawns the link manager; observe remoteStatus().
    external fun remoteConnect(host: String, port: Int, audio: Boolean, geometry: Boolean): Boolean
    external fun remoteTransport(cmd: String) // bare verb or JSON {cmd,ms}
    external fun remoteMetadata(): String
    external fun remoteDisconnect()
    // Includes audio_latency_mode/audio_target_ms/audio_underruns for the Nerd HUD.
    external fun remoteStatus(): String // {state,host,port,rx_*,art_id,*_gen,scope,welcome,last_error,...}
    external fun remoteScopeCtl(verb: String, value: String) // drive the DESKTOP scope (mode/theme/ui/gain)
    external fun remoteSetStreams(audio: Boolean, geometry: Boolean)
    external fun remoteSetMuted(muted: Boolean)
    /** 0=tight (~80 ms), 1=balanced (~150 ms), 2=safe; applies during a live stream. */
    external fun remoteSetLatencyMode(mode: Int)
    external fun remoteSeekMs(ms: Long)
    external fun remoteRequestSources()
    external fun remoteSources(): String
    external fun remoteChooseSource(id: String)
    external fun remoteBrowse(root: String, path: String)
    external fun remoteListing(): String
    external fun remotePlayFile(root: String, path: String)
    external fun remoteStopFile()
    external fun remoteRequestArt(id: String)
    external fun remoteArt(): ByteArray?
    external fun remoteMetaGeneration(): Int
    external fun remoteArtGeneration(): Int
    external fun remoteSourcesGeneration(): Int
    external fun remoteListingGeneration(): Int

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
