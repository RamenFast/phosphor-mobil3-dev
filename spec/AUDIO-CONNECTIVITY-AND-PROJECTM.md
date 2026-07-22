# Audio, connectivity, and ProjectM specification

**Status:** Binding architecture plus required spikes
**Primary target:** Galaxy S25, Android 16, locked bootloader, no root

## 1. Audio source hierarchy

Phosphor supports these source classes:

| Source | Build | Fidelity | Main limit |
|---|---|---|---|
| Own deck | BOTH | Full stereo PCM | Phosphor owns playback |
| Microphone | BOTH | Device input PCM | Environmental, not internal output |
| AudioPlaybackCapture / MediaProjection | BOTH | Full stereo where allowed | Source app may opt out; consent/lifecycle rules |
| Desktop/laptop relay over tailnet | BOTH | Full remote PCM/geometry | Network and host setup |
| Shell remote-submix capture | FORTRESS SPIKE | Potential whole output PCM | Must prove Shizuku/ADB implementation and edge cases |
| Physical loopback | FORTRESS fallback | Device-dependent | Hardware path |

The Android `Visualizer` API remains rejected because its 8-bit, often mono/limited data is inadequate for Phosphor's vectorscope.

## 2. Corrected Shizuku finding

The 2026-07-18 test proved only this:

> Shell cannot grant `CAPTURE_AUDIO_OUTPUT` to the ordinary Phosphor app package.

It did not prove that a process already running as shell cannot capture output.

Read-only S25 evidence on 2026-07-22 established:

- `com.android.shell` already has `CAPTURE_AUDIO_OUTPUT`;
- it also has `CAPTURE_MEDIA_OUTPUT`, `MODIFY_AUDIO_ROUTING`, and `MANAGE_MEDIA_PROJECTION` on this device;
- a remote-submix path is present;
- scrcpy documents and implements whole-output capture through an `AudioRecord` remote-submix path as shell on supported Android versions;
- Shizuku UserService can run app-supplied code as UID 2000.

Therefore a shell-process capture sidecar is plausible and P0 for the Fortress build. It remains unproven until PCM is captured and passed into Phosphor on the real device.

## 3. Controlled capture spike

### 3.1 Stage A, establish shell capture ground truth

Use scrcpy or a minimal pre-existing shell-capable tool to capture Android output from the S25 while testing:

- Phosphor deck;
- Spotify;
- SoundCloud app;
- SoundCloud in browser;
- YouTube Music;
- local browser/video;
- game audio;
- Bluetooth output;
- speaker output;
- wired/USB output if available.

Record whether PCM exists, whether it matches what is heard, channel count, sample rate, latency, dropouts, and what happens on route change, lock, app background, and reboot.

Calls, communications audio, secure DRM, protected content, alarms, and private capture policies are separate categories and must not be promised from a music-output success.

### 3.2 Stage B, Shizuku UserService prototype

If Stage A succeeds, build a Fortress-only Shizuku UserService that:

- runs as UID 2000;
- opens the proven remote-submix or audio-policy source;
- normalizes to f32 stereo at 48 kHz;
- streams through Binder shared memory or another bounded local IPC path;
- supplies timestamps and route metadata;
- has explicit start/stop and liveness;
- never exposes a network listener;
- dies closed on Shizuku loss or revocation;
- keeps privileged code absent from Play.

Do not stream PCM through high-frequency JSON or per-sample Binder calls.

### 3.3 Stage C, ADB sidecar fallback

If Shizuku cannot host the required native/audio path but shell capture works, create a Fortress-only sidecar launched by ADB from `/data/local/tmp` or another appropriate shell-owned location.

The sidecar may use a local abstract Unix socket or Binder endpoint between shell and the app. This local PCM transport is not the Nexidex agent gate, but it must still be authenticated and app-private. It must not bind TCP/UDP.

Nexidex and `pm3` expose install/status/start/stop/doctor receipts. The UI says when ADB is required and when the sidecar is gone.

### 3.4 Stop rules

Stop and document the route if:

- PCM cannot be obtained as shell on the target S25;
- it requires platform signing or root beyond Ben's device model;
- it destabilizes audio routing;
- it captures protected communications in a way that violates user expectations or law;
- the only implementation would leak elevated code into Play.

A failed spike does not remove deck, MediaProjection, microphone, or relay sources.

## 4. MediaProjection honesty

PLAY and FORTRESS standard capture use the public MediaProjection path.

Rules:

- ask only after the user chooses `Other apps`;
- keep the active token/session alive while the foreground service remains valid;
- do not reprompt during the same valid active session;
- after Android stops or invalidates the projection, request consent again;
- do not claim persistent authorization across lock, reboot, force-stop, or token invalidation;
- distinguish source opt-out silence from no signal where evidence permits;
- surface the current captured app/package only when Android legitimately provides it.

Android 15/16 lifecycle and single-use token behavior are system authority.

## 5. Source compatibility matrix

Each release receives a dated matrix with:

```text
app/source
version
device/build
route
capture method
speaker/BT/USB
result = flowing | silent/opted-out | no-signal | error
latency
notes
receipt
```

At minimum test Spotify, SoundCloud, YouTube Music, Chrome media, Samsung Internet media, local deck, microphone, and both relay hosts.

The app copy is driven by known categories, not hardcoded optimism for a brand.

## 6. Tailscale relay P0

### 6.1 User topology

```text
Phosphor phone -> Tailscale private network -> trusted relay on home PC or Linux laptop
```

The existing relay remains an audio source. Nexidex is the standard agent gate. These may share endpoint discovery and trust material, but audio and control remain typed channels with independent liveness.

### 6.2 Setup UX

The in-app remote setup MUST show:

1. Tailscale installed/running state when detectable.
2. Host entries and last working endpoint.
3. `TEST LINK` action.
4. Link phase: dialing, authenticating, connected, silent, stalled, backoff, error.
5. Exact repair text.
6. A concise link to full GitHub/manual instructions.

Forced Wi-Fi/mobile route behavior must explicitly warn that binding outside the VPN can make a 100.x tailnet address unreachable. Auto is the safe default until empirically proven.

### 6.3 Agent awareness

Nexidex sees:

- chosen host;
- endpoint and route class with secrets redacted;
- connection phase;
- reconnect delay;
- PCM/geometry flow state;
- buffer, underrun, and drop counters;
- last error and fix.

The human HUD and agent fields read the same link object.

## 7. ProjectM integration decision

The preferred first feasibility architecture is actual `libprojectM`, not merely a vaguely inspired shader collection, subject to licensing and performance proof.

ProjectM is OpenGL-based while Phosphor uses wgpu/Vulkan. The first spike SHOULD avoid a risky renderer rewrite.

### 7.1 Preferred composition

```text
One PCM source + one monotonic clock
  -> Phosphor true-beam/wgpu SurfaceView, transparent authoritative layer
  -> libprojectM GLES SurfaceTexture/TextureView, expressive field layer
  -> Android compositor combines them
  -> Compose sharp-stone chrome above
```

ProjectM may run at 30 or 60 Hz. The Phosphor beam keeps its higher presentation target. A future AHardwareBuffer/EGLImage interop path is optional only if compositor blending fails quality or latency gates.

### 7.2 Alternative

A native wgpu ProjectM-compatible or ProjectM-inspired engine is acceptable only if the actual library cannot meet Android, licensing, deterministic timing, or performance requirements. That choice requires a decision record and must preserve preset provenance. Do not silently rename an unrelated effect as ProjectM.

## 8. ProjectM layer contract

A blended frame has:

```text
true_beam.opacity >= product minimum
geom_fx.amount
projectm.opacity
projectm.preset_id
projectm.preset_source
projectm.license
clock_id
audio_source_id
quality_tier
silence_state
```

Rules:

- one source and clock for all layers;
- raw stereo PCM, not Android Visualizer data;
- true beam visibly readable at maximum supported field blend;
- ProjectM field labeled as field/effect, not signal trace;
- silence stops energy-driven evolution after a bounded decay;
- preset transitions are deterministic where the engine allows;
- ProjectM quality degrades before beam sampling, fidelity, or cadence;
- one audio analysis fan-out, not duplicate capture sessions;
- UI sound bus excluded;
- ProjectM crashes or context loss fall back to true beam without taking down the app.

## 9. ProjectM controls and packs

Controls:

- view select;
- preset select and shuffle;
- preset transition duration;
- field opacity;
- true-beam opacity with enforced floor;
- geometry-effect amount;
- quality: auto, battery, balanced, full;
- deterministic seed where supported;
- freeze field for inspection without freezing true beam;
- show preset info/license.

Preset packs are separate from UI theme packs, even if a bundle distributes both. Every preset/pack needs source and license metadata. Unclear-license preset content is not eligible for Play distribution.

## 10. Licensing gate

`projectM` is LGPL-2.1-or-later. Before integrating:

- record the exact upstream version and license files;
- prefer a dynamically linked shared library;
- include required notices and a written offer/source/relink path as applicable;
- ensure user replacement/relink rights are not defeated by packaging;
- audit every bundled preset separately because preset licenses may differ;
- include third-party notices in-app and in release artifacts;
- have a final human/legal review before Play submission.

This project is GPLv3 by Ben's default where a new project license is needed, but dependency compliance still requires explicit handling.

## 11. Performance and thermal ladder

Degradation order:

1. reduce ProjectM internal resolution;
2. reduce ProjectM cadence from 60 to 30;
3. reduce optional geometry effects;
4. simplify field transitions;
5. disable ProjectM field and retain true beam.

Never degrade source capture fidelity, true-beam timestamp correctness, or human/agent truth before decorative field layers.

## 12. Audio and ProjectM receipts

A successful implementation must produce:

- PCM capture matrix;
- shell/Shizuku spike report whether success or failure;
- same-source/clock proof for ProjectM and beam;
- silence video showing both layers rest honestly;
- thermal soak and frame-time traces;
- context-loss fallback proof;
- license inventory and preset provenance ledger;
- Nexidex snapshot showing view/layers/source/clock/drop state.

## 13. Primary research anchors

Recheck these sources at implementation time because Android and upstream projects evolve:

- Android playback capture: <https://developer.android.com/media/platform/av-capture>
- Android MediaProjection: <https://developer.android.com/media/grow/media-projection>
- scrcpy audio documentation and source tree: <https://github.com/Genymobile/scrcpy/blob/master/doc/audio.md>
- Shizuku API developer guide and UserService examples: <https://github.com/RikkaApps/Shizuku-API>
- projectM upstream: <https://github.com/projectM-visualizer/projectm>
- projectM CMake, Android GLES, and shared-library build guidance: <https://github.com/projectM-visualizer/projectm/blob/master/BUILDING-cmake.md>

Device-specific permission and route claims must still be proven again on the exact target build before code relies on them.
