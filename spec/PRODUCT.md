# Product specification

## 1. Identity and platform

Phosphor is one Android application with production package `dev.phosphor.mobil3`.

- Minimum Android version: Android 10, API 29.
- Compile SDK: 36.
- Target SDK: 36.
- Production ABI: `arm64-v8a`.
- Debug package suffix: `.debug`.

The product has no Play/Fortress flavor split.

## 2. Supported sources

Phosphor must support:

- local files and folders
- microphone input
- Android playback capture
- user-selected PC relay hosts

Each source must publish a clear state: unavailable, waiting for consent, starting, flowing, silent, stalled, reconnecting, or stopped where applicable.

## 3. Renderer and playback

The Rust renderer remains the visual authority inside one Android `SurfaceView`. Compose owns surrounding chrome and user interaction.

Local playback must remain sample-locked to the scope input. MediaSession controls, audio focus, noisy-route handling, background playback, and picture-in-picture must remain functional.

The app must not fork desktop engine code into this repository. Shared engine changes belong in the sibling `phosphor` repository.

## 4. State and persistence

User-facing settings use direct typed persistence. The product has no general command bus, causal authority plane, principal model, mutation authorization layer, or behavioral audit history.

A one-release migration may read the old causal settings envelope only to preserve user-facing values. The migration must then remove obsolete Nexus and audit data.

Remote hosts remain private runtime data. Android backup rules must exclude remote hosts and other connection-specific values.

## 5. Privacy

The app must not include or emit:

- analytics or telemetry events
- crash-report uploads
- advertising or installation identifiers
- behavior or usage history
- silent diagnostic exports
- reporting endpoints

Local logcat output is allowed for developer troubleshooting. The app must not upload or retain it as product data.

A fresh install must remain network-idle until the user invokes a remote feature.

## 6. Permissions

Phosphor requests a permission only for a visible feature initiated by the user.

- `RECORD_AUDIO` supports microphone and playback capture.
- MediaProjection consent supports playback capture and is requested for every session required by Android.
- Notification-listener access may support local source metadata only if the UI explains the access before opening system settings.
- Notification permission must be absent if the app does not need it. Otherwise the app must request it contextually.
- Microphone hardware must be optional.

The product does not request root, Shizuku, ADB, overlay, accessibility, privileged audio, or package-management access.

## 7. Remote playback

The PC relay remains a first-class source.

The app must retain host editing, file browsing, playback, metadata, artwork, transport control, stream toggles, latency policy, network selection, reconnect truth, and forced link-state diagnostics that already work.

Protocol v2 has no application-layer authentication or encryption. The UI and documentation must restrict it to a trusted local network or Tailscale. Open-internet use is unsupported.

The cleanup must not redesign the relay protocol.

## 8. Interface

The beam remains the primary surface. Existing display modes, geometry effects, beam and glow controls, themes, source controls, settings transfer, rotation controls, and picture-in-picture remain part of the product where the implementation already supports them.

The cleanup must preserve behavior before extracting or consolidating large components.

## 9. Developer tooling

`dev/pm3` is the project CLI. It may expose build, install, run, logcat, screenshot, record, smoke, doctor, schema, and related development operations.

The CLI must follow the workspace agent CLI standard. It must not expose product state mutation, Nexus administration, audit export, authority grants, or product-agent transport.

## 10. Distribution and commerce

The current product has no trial, entitlement, purchase, or billing behavior.

Commerce requires a separate accepted decision, Play Console product setup, complete BillingClient behavior, and acceptance coverage. Dormant scaffolding must not imply that commerce exists.

## 11. Deferred work

The following items are outside this stage:

- relay authentication redesign
- ProjectM
- root or privileged capture
- Shizuku and ADB sidecars
- unannounced future core features
- speculative UI systems not already implemented

Deferred work must not leave active services, permissions, dependencies, settings, or product promises behind.
