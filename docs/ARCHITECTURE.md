# phosphor-mobil3 architecture

## Product shape

Phosphor Mobile is one Android application with two build types:

- `debug`: package `dev.phosphor.mobil3.debug`, includes the explicit self-test receiver.
- `release`: package `dev.phosphor.mobil3`, non-debuggable, signed only after certificate verification.

The app contains no account system, advertising, usage tracking, behavior tracking, or automatic reporting service.

## Runtime layers

1. **Compose interface** presents the console, sheets, queue, source selection, settings, and status.
2. **MainActivity** owns Android permissions, system pickers, orientation, picture-in-picture, lifecycle, and JNI intent calls.
3. **Android services** own local playback, playback capture, and optional media-metadata observation.
4. **Rust JNI runtime** owns DSP, rendering, remote protocol state, audio buffering, and the native surface.
5. **PC relay** is a separate Rust program under `relay/` that captures or plays desktop audio and serves protocol v2.

The Android interface never performs socket or real-time audio work in Compose callbacks. JNI setters publish bounded intent to the native runtime.

## Rendering

A `SurfaceView` is placed below Compose chrome. The native renderer receives surface lifecycle events and display density. Android 11 and newer request a 120 Hz frame rate where supported. Android 10 uses the normal surface cadence.

Rendering reuses sibling desktop phosphor crates through pinned path dependencies. Gradle invokes Cargo NDK with the repository lockfile and packages only generated arm64 libraries. There is no unmanaged `app/src/main/jniLibs` fallback.

## Audio sources

### Local files

Android's document picker grants access to files and folders. The playback service stages and cleans files as needed, maintains the queue, and publishes Android media controls.

### Microphone

The app requests `RECORD_AUDIO` before starting microphone capture. Denial leaves the current source unchanged and provides a retry path.

### Android playback capture

The user first approves microphone permission because Android's capture audio path requires it. The app then opens the MediaProjection consent flow.

- API 34 and newer request the default display explicitly.
- API 29 through 33 use the full-display capture intent available on those releases.
- The capture service enters the typed foreground state before obtaining the projection.
- Projection revocation, screen lock behavior, permission denial, and service failure are surfaced to the interface.

Playback audio and microphone audio are processed in memory. Phosphor does not record or upload them.

### PC relay

A fresh install has no relay hosts and creates no Phosphor-owned network traffic. The user must save and select a host.

Accepted hosts are Tailscale MagicDNS names, `.ts.net` names, legacy `.tailnet` names, or IPv4 addresses in `100.64.0.0/10`. The app then opens protocol v2 to receive audio or geometry and send playback commands. The protocol relies on Tailscale for identity and encryption. Relay authentication and bind-address polish remain deferred work.

## State and migration

User-facing settings live in ordinary private preferences and are exported only after an explicit user action. Settings import validates the archive before one atomic commit and restores the previous snapshot if the commit fails.

One startup migration preserves the existing HUD mode from older preference layouts. It then deletes obsolete preference files and the old Android Keystore alias. The removed command graph and audit history are not retained at runtime.

Saved relay hosts, consent state, metadata access state, and runtime settings are excluded from Android cloud backup.

## Android compatibility

- Minimum SDK: 29
- Compile and target SDK: 36
- ABI: arm64-v8a

Version-specific calls are guarded for frame-rate requests, display rotation, rounded corners, notification-listener settings, picture-in-picture behavior, and MediaProjection configuration. The activity is resizeable and does not depend on orientation locks being honored on large screens.

## Build and release

The Gradle wrapper is the sole Android build authority. `app/build.gradle.kts` owns:

- application version and version code
- debug and release package identities
- Rust JNI generation
- signing selection and certificate verification
- runtime dependency evidence
- the production artifact boundary task

Release signing profiles are `production` and `play-upload`. Both require external keystore inputs and an expected SHA-256 certificate. Release compilation fails closed when inputs are incomplete or mismatched.

`scripts/ship-check.sh` is the fixed release scoreboard. `dev/pm3` remains local developer tooling and has no runtime product-control path.

## Known risks

1. The API 29 floor needs a real Android 10 or API 29 emulator receipt.
2. Large-screen, foldable, desktop-window, and multi-window layouts need a device matrix.
3. The PC relay binds broadly and depends on the host firewall plus Tailscale. Binding and protocol hardening are deferred to the networking stage.
4. Google Play enrollment, signing evidence, declarations, listing assets, and submission are human gates.
