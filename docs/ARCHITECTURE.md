# phosphor-mobil3 — Architecture

> **Authority notice, 2026-07-22:** This describes the working MVP baseline. Future architecture is governed by `vision/`, `spec/`, and `docs/dev/PHOSPHOR-NEXIDEX-IMPLEMENTATION-HANDOFF.md`. The binding additions are the Play/Fortress compile-time seam, one causal store with provenance, signature-guarded Binder plus the outbound Nexidex/tailnet gate, the reopened shell-audio spike, and the ProjectM blended scope-view architecture.

Decisions ratified in the 2026-07-18 planning session (web-verified). The full plan lived
in the session plan file; this is the standing reference.

## Shape

Kotlin + Jetpack Compose chrome (Material-free, house tokens) over ONE `SurfaceView`
(`setZOrderOnTop(false)`); the Rust core owns every pixel inside it. The desktop engine
crates are consumed **unmodified** via path deps to the sibling checkout
(`../phosphor/crates/*`), same pinned rustc — parity by construction.

```
Kotlin main thread     Compose UI · MediaSession callbacks · SurfaceHolder · consent flows
Rust render thread     wgpu device/queue/surface · Computer · GpuRenderer (FIFO-paced)
Rust decode thread     spawn_player (symphonia → rubato → gapless → AudibleRing+SampleRing)
oboe RT callback       pops AudibleRing — no alloc, no JNI
Rust event thread      the ONE upward JNI path → GlobalRef'd Kotlin listener
Kotlin AudioRecord     capture/mic sources → ~10 ms f32 chunks over JNI → SampleRing
```

**Zero per-frame JNI.** Commands go down (postcard-encoded enum), events come up
(postcard-encoded enum), cheap atomic reads for position/duration.

## Render

- Process-lifetime wgpu Instance/Adapter/Device; the `wgpu::Surface` is created/dropped
  with the Android Surface (`ndk NativeWindow::from_surface` → `create_surface_unsafe`).
- **`surfaceDestroyed` blocks** until the render thread drops Surface + NativeWindow ref
  (Android invalidates the window when the callback returns — crash class #1). Render
  thread parks keeping decay textures; beam survives backgrounding. Also honor `onStop`
  (screen-off does NOT fire surfaceDestroyed).
- `PresentMode::Fifo`, `desired_maximum_frame_latency = 2` (Adreno guidance; Mailbox burns
  battery rendering dropped frames).
- Kotlin must call `surface.setFrameRate(120f)` or Android 15 holds many surfaces at 60.
  Verify via `dumpsys SurfaceFlinger`; fall back to FRAME_RATE_COMPATIBILITY_FIXED_SOURCE.
- Prefer the sRGB surface format — `GpuRenderer` derives `hardware_encodes` from it.
- Supersample 2× default; render-scale + fps caps in settings from v1 (thermal governs).

## Audio

- **Own deck:** `oboe` crate (AAudio, LowLatency/Shared, f32 stereo 48 kHz, Usage::Media).
  The oboe callback pops `AudibleRing` exactly where the desktop PipeWire stream did; the
  scope taps `SampleRing::take_stereo_samples` — sample-locked picture, gapless inherited.
  AAudio streams die on route change (BT): rebuild-and-resume, ring keeps content.
- **Upstream (the only desktop changes):** `phosphor-audio` gets a default
  `pipewire-backend` feature gating `engine.rs`/`mirror.rs`/`targets.rs`; `AudioEvent`
  moves to lib.rs. Android consumes `default-features = false` for
  playback.rs/ring.rs/metadata.rs.
- **MediaSession:** Media3 `MediaSessionService` + `SimpleBasePlayer` bridge. Hand-rolled:
  AudioFocusRequest (transient loss/duck), BECOMING_NOISY → pause, AudioDeviceCallback.
  FGS type `mediaPlayback`. **The loaded deck owns the transport** (desktop law); when
  scoping another app, the deck sheet drives that app's `MediaController`.
- **Capture:** MediaProjection (consent per session) + FGS `mediaProjection` +
  `AudioPlaybackCaptureConfiguration`. **Spotify/YT Music/DRM apps opt out — silence;
  say so honestly.** Android 15+ auto-stops capture on lock. Mic = stereo AudioRecord.
  Visualizer API permanently rejected (8-bit mono kills XY). Shizuku spike tracked in M4.

## Build

- AGP 9.x (built-in Kotlin — no kotlin-android plugin) · Gradle 9.3.1 wrapper · minSdk 35 /
  target+compile 36 · NDK r28c (16 KB ELF alignment by default) · arm64-v8a only.
- cargo-ndk via a plain Gradle `Exec` task (no plugins, no Python) wired before
  `mergeJniLibFolders`; `checkEngine` task guards the path-dep seam.
- `dev/pm3` is the AGENT-CLI-STANDARD conforming surface (the APK has no argv/stdout);
  ruling recorded in docs/AGENTS.md.

## Risks (ranked, from planning)

1. "Why is Spotify silent" — certainty; honest labels. 2. Surface lifecycle races.
3. AAudio disconnects. 4. SimpleBasePlayer state sync. 5. Thermal at 120 Hz.
6. 60 Hz cap sticking. 7. Consent fatigue + lock auto-stop. 8. JNI GlobalRef leaks on
config change. 9. Path-dep coupling. 10. NDK/AGP drift (pinned in libs.versions.toml).
