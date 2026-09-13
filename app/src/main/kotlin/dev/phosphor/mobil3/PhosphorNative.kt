package dev.phosphor.mobil3

import android.view.Surface

// The one JNI seam. Grows per plan (command channel, events); M1 carries the surface
// lifecycle + the engine handshake.
object PhosphorNative {
    /** Non-consuming source metadata. This does not acquire scopeStats or another audio tap. */
    external fun signalObservation(): String
    init {
        System.loadLibrary("phosphor_mobil3_core")
    }

    /** JSON string from the Rust core proving the engine crates link and run on-device. */
    external fun engineInfo(): String

    // SurfaceHost is the only caller. Its generation lease fences attach, resize, and detach.
    /** -1 unavailable, 0 solid, 1 confirmed premultiplied transparent output. */
    external fun attachSurface(surface: Surface, width: Int, height: Int, density: Float, transparent: Boolean): Int

    /** Blocks until the Rust render thread dropped the wgpu surface + window ref. */
    external fun surfaceDestroyed(): Boolean

    external fun setRenderPaused(paused: Boolean)
    /** Pins CPU ownership before transport. Never waits for GPU work. */
    external fun setDisplayPaused(paused: Boolean)
    external fun observeTransportPaused(paused: Boolean)
    external fun invalidateHeldFrame()
    /** Bits: paused=1, BLACK=2, available pinned image=4, pending current application present=8. */
    external fun displayPauseState(): Int
    external fun setPauseBlack(black: Boolean)
    external fun setHdrRequested(requested: Boolean, api: Int)
    external fun hdrObservation(): String
    external fun inspectHeld(dx: Float, dy: Float, scale: Float, reset: Boolean)


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
    external fun setAutoFrameScale(scale: Float)
    /** Beam focus px (0.3..3.0, desktop slider) — smaller = sharper. */
    external fun setFocus(focus: Float)
    /** Beam brightness budget (1.0..30.0, desktop "Beam" slider). */
    external fun setBeamEnergy(energy: Float)
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

    /** Exact saved RGB triples in their existing numeric convention. One atomic publication. */
    external fun setLight(rgb: FloatArray, selectedMask: Int, preset: Int, seconds: Float,
        perTrack: Boolean, generatedAuto: Boolean, shuffle: Boolean, randomInterval: Boolean,
        intervalMin: Float, intervalMax: Float, deletedSlot: Int = -1): Boolean
    external fun rollLight(): Boolean
    external fun cycleAdvance()

    /** Positive owned request ID. Errors: -1 invalid setup, -2 receipt capacity, -3 disconnected renderer. */
    external fun requestInstrument(setupJson: String): Long
    /** Off-main only. 1 committed, 2 cancelled, 3 geometry-owned rejection, 4 unavailable ID. */
    external fun awaitInstrument(requestId: Long): Int
    /** Cancels Pending only. A committed result remains committed. Same outcome codes as await. */
    external fun cancelInstrument(requestId: Long): Int
    /** Releases this receipt and cancels any Pending queued work. Never changes other requests. */
    external fun releaseInstrument(requestId: Long): Boolean

    /** Live beam color packed 0xRRGGBB (accent_follows_beam chrome breathing). */
    external fun beamColorNow(): Int
    /** Existing HUD heartbeat also consumes a <=500ms raw L/R grid_data window, or null for no data. */
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

    // Root PCM remains samples-only until the helper supplies a producer fence.
    external fun pushCaptureSamples(samples: FloatArray, count: Int)
    external fun captureReadEpoch(): Long
    external fun pushCaptureRead(samples: FloatArray, count: Int, owner: Long, readEpoch: Long)
    external fun setRingActive(active: Boolean): Long

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
