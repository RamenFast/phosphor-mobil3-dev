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

- local files and recursive folder trees
- microphone input
- Android playback capture
- explicitly enabled, authorized root playback capture as an alternative backend for everything playing
- user-selected PC relay hosts, files, and recursive folder trees

Each source must publish a clear state: unavailable, waiting for consent, starting, flowing, silent, stalled, reconnecting, or stopped where applicable.

A folder is a tree. Local and relay folder playback must visit supported audio descendants in stable order. An invalid entry must report and skip without freezing the interface, stopping a valid queue, or showing dark false-success.

## 3. Renderer and playback

The Rust renderer remains the visual authority inside one active Android presentation surface. Full app, PiP, and floating HUD transfer that ownership without duplicating the renderer or source. Compose owns surrounding chrome and interaction. `EXPANSION.md` defines surface generations and user pause separately from lifecycle suspension.

Local playback must remain sample-locked to the scope input. MediaSession controls, audio focus, noisy-route handling, background playback, and picture-in-picture must remain functional.

Transport state must follow the current source. The product must not show a play, pause, seek, title, artist, or source state that the active local, capture, or relay path cannot support.

Local seek and navigation work must leave the main looper responsive. Superseded local requests and stale metadata must not replace the latest requested item.

The app must not fork desktop engine code into this repository. Shared engine changes belong in the sibling `phosphor` repository.

## 4. State and persistence

User-facing settings use direct typed persistence. The product has no general command bus, causal authority plane, principal model, mutation authorization layer, or behavioral audit history.

A one-release migration may read the old causal settings envelope only to preserve user-facing values. The migration must then remove obsolete authority and audit data.

Remote hosts remain private runtime data. Android backup rules must exclude remote hosts and other connection-specific values.

The portable settings contract includes these keys and defaults:

| Key | Default | Surface |
|---|---:|---|
| `pip_auto_enter` | `true` | Full and quick settings |
| `controls_always_visible` | `false` | Full settings |
| `grid_data` | `false` | Full settings |
| `double_tap_playback` | `true` | Full settings |
| `linger_background` | `false` | Full settings |
| `auto_frame_scale` | `1.0` | AUTO stage zoom and framing reset |

These keys must survive same-package updates and settings archive round trips. Picture-in-picture auto-entry and background linger are independent behaviors.

A clean install must seed the accepted instrument defaults: AMOLED, auto-gain and fullscreen on, grid off, mode 1, beam 7, range 6 through 20, a 3-second cycle, automatic HUD and band, focus 0.3, gain 1.8332275, geometry 0.6, and scope rotation lock. The legal cycle range is 0.1 through 60 seconds. Unknown custom RGB values must remain absent with `custom_count=0`.

Removing Phosphor from recents must stop local, relay, capture, and microphone sources when `linger_background=false`. When linger is enabled, the product may retain only a source that already has a real service owner. The approved service-owned microphone expansion must preserve this rule. Until that owner is implemented and verified, an activity-owned microphone cannot claim background survival.

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

Phosphor requests permission only for a visible user-selected feature or its explicitly configured default-source startup. A fresh unconfigured install requests no optional access. The startup rules in `EXPANSION.md` serialize missing-access requests and respect cancellation.

- `RECORD_AUDIO` supports microphone and playback capture.
- MediaProjection consent supports playback capture and is requested for every session required by Android.
- Notification-listener access may support local source metadata only if the UI explains the access before opening system settings.
- Notification permission must be absent if the app does not need it. Otherwise the app must request it contextually.
- Microphone hardware must be optional.

Hidden opt-in root playback capture and user-started overlay access are approved under `EXPANSION.md`. Root-manager authorization is separate from Android permissions. Bluetooth and microphone service permissions map only to their selected input feature. The product still excludes Shizuku, ADB sidecars, accessibility authority, package management, and runtime administration.

## 7. Remote playback

The PC relay remains a first-class source.

The app must retain host editing, file browsing, playback, metadata, artwork, transport control, stream toggles, latency policy, Tailscale routing, reconnect truth, and forced link-state diagnostics that already work.

Protocol v2 has no application-layer authentication or encryption. The UI and documentation must require Tailscale. Open-internet and direct-LAN use are unsupported.

The cleanup must not redesign the relay protocol.

## 8. Interface

The beam remains the primary surface. Existing display modes, geometry effects, beam and glow controls, themes, source controls, settings transfer, rotation controls, and picture-in-picture remain part of the product where the implementation already supports them.

The cleanup must preserve behavior before extracting or consolidating large components.

## 9. Developer tooling

`dev/pm3` is the project CLI. It may expose build, install, run, logcat, screenshot, record, smoke, doctor, schema, and related development operations.

The CLI must follow the workspace developer CLI standard. It must not expose product state mutation, runtime administration, audit export, authority grants, or product automation transport.

## 10. Distribution and commerce

The current product has no trial, entitlement, purchase, or billing behavior.

Commerce requires a separate accepted decision, Play Console product setup, complete BillingClient behavior, and acceptance coverage. Dormant scaffolding must not imply that commerce exists.

## 11. Deferred work

The following items are outside this stage:

- relay authentication redesign
- ProjectM
- Shizuku and ADB sidecars
- unannounced future core features
- calibration playground, beam image export, and session timer
- UI systems outside the approved expansion

Deferred work must not leave active services, permissions, dependencies, settings, or product promises behind.
