# Product scope reset

**Date:** 2026-08-05

**Status:** Accepted

## Context

Phosphor accumulated two release flavors, a Nexus authority plane, product-facing CLI control, causal audit state, speculative capture lanes, and commerce scaffolding. Those systems increased the release surface without improving the instrument that users touch.

Ben reset the product before the next core-feature stage. The cleanup must preserve the working instrument and remove architecture that no longer serves it.

## Decision

Phosphor is one Play-safe Android application.

The production package is `dev.phosphor.mobil3`. Debug builds use a separate `.debug` package suffix. The build has ordinary `debug` and `release` variants. It has no Fortress product flavor.

Phosphor supports Android 10, API 29, and newer. It continues to compile and target API 36. New platform features use explicit version or capability checks.

The active product includes:

- local file and folder playback
- microphone input
- Android playback capture through MediaProjection
- the native Phosphor renderer and DSP
- picture-in-picture and the current mobile interface
- user-selected PC relay playback over a trusted local network or Tailscale
- local developer tooling through `dev/pm3`

The active product excludes:

- analytics, telemetry, advertising identifiers, and behavior or usage tracking
- Nexus, Nexidex, Binder authority, agent control, and product-facing CLI transport
- root, Shizuku, ADB sidecars, privileged audio capture, and DRM bypass work
- ProjectM and any unapproved future core feature
- trial, entitlement, and billing behavior until a separate commerce decision exists

`dev/pm3` remains a project developer tool. It may build, install, run, inspect, and test the app. It is not a product control protocol.

The PC relay remains supported. This cleanup does not redesign its protocol. Active documentation must state that protocol v2 has no application-layer authentication or encryption and must stay inside a trusted network or Tailscale.

## Privacy contract

Phosphor does not collect or transmit analytics, diagnostic reports, usage history, advertising identifiers, or installation identifiers.

Runtime logs may exist in Android logcat for local troubleshooting. The app does not upload, retain, or export those logs as product data.

The app must not create a Phosphor-owned network connection after fresh installation until the user selects or invokes a remote feature.

## Historical material

Earlier decisions and release receipts remain immutable history. This decision removes them from the active authority chain where they conflict with the scope above.

Obsolete planning and protocol documents move to `docs/dev/archive/2026-08-05-scope-reset/`. Their bodies remain unchanged.

## Consequences

The cleanup will delete the Nexus implementation and its tests, remove dual distribution abstractions, simplify settings persistence, and rebuild release gates around one product.

The cleanup will preserve legacy user-facing settings where practical. It will remove obsolete Nexus principals, sessions, audit records, and authorization preferences during a bounded migration.

Future features require a new decision and acceptance update. Neither archived plans nor implementation remnants can silently reactivate them.
