# Phosphor Mobile v2 feature ledger

**Measured:** 2026-08-05

Status values:

- `implemented`: present in source and covered by an automated check.
- `device-check`: implemented but still needs field evidence.
- `deferred`: intentionally left for a later stage.
- `blocked`: requires an external human or credential gate.

## Core instrument

| Capability | Status | Evidence or next check |
|---|---|---|
| Full-screen native beam renderer | implemented | Clean debug compilation, 74 Android tests, and 34 native tests |
| Eleven scope views | implemented | Existing renderer tests and S25 receipts |
| Geometry effects | implemented | Existing renderer tests and S25 receipts |
| Thirteen interface rooms | implemented | Compose source and saved-setting behavior |
| File playback | implemented | Playback service and player tests |
| Folder queue | implemented | Document picker flow and queue state |
| Android media controls | implemented | Media3 session service |
| Microphone source | implemented | Permission-first activity flow |
| Playback capture | implemented | API 29 to 36 compatibility branches and lint |
| Track metadata and artwork | device-check | Optional notification-listener path; recheck grant and denial |
| Picture-in-picture | device-check | API 29 to 31 manual entry, API 31+ auto-entry |
| Rotation and placement locks | device-check | Unit logic exists; turning-phone and large-screen receipts remain |
| HUD on, auto, off | implemented | Direct UI state and migration tests |
| Settings export and import | implemented | Strict archive validation and atomic preference commit |

## PC relay

| Capability | Status | Evidence or next check |
|---|---|---|
| Empty first-run host list | implemented | Store initialization tests |
| User add, edit, and remove | implemented | Store and editor-flow tests |
| Tailscale-only saved hosts | implemented | MagicDNS, `.ts.net`, and `100.64.0.0/10` tests |
| Protocol v2 audio | implemented | Relay and native Rust tests |
| Protocol v2 geometry | implemented | Relay and native Rust tests |
| Metadata and artwork | device-check | Restart current relay service, then verify end to end |
| Transport control | device-check | Verify play, pause, next, previous, and seek on the PC |
| Link state honesty | implemented | `RemoteLinkTruthTest` and force-link-state script |
| Bind and protocol hardening | deferred | Networking polish stage |
| Long-session latency tuning | deferred | Networking polish stage |

## Repository cleanup

| Capability | Status | Evidence or next check |
|---|---|---|
| One Android product | implemented | No flavor graph or flavor source sets |
| Debug package isolation | implemented | `dev.phosphor.mobil3.debug` |
| Android 10 floor | implemented | `minSdk 29`, unit suite, and lint |
| Wrapper-only Gradle build | implemented | Bootstrap and environment scripts |
| Locked native builds | implemented | Cargo NDK tasks use `--locked` |
| Dormant authority and audit graph removed | implemented | Source boundary and stale-symbol checks |
| Developer CLI narrowed to local tooling | implemented | `scripts/test-pm3.sh` |
| Tracking and reporting SDKs absent | implemented | Dependency and source scan |
| Privacy policy | implemented | `PRIVACY.md` and in-app manual link |
| Protected starting material preserved | implemented | Archive `SHA256SUMS` gate |
| Release provenance | implemented | Dirty, unknown, mismatched, untagged, or dirty path-dependency source fails closed |
| Canonical release package | implemented | Distinct direct-APK and Play-upload signers, bundletool, 16 KiB, exact sources, manifest, and checksum gate; a dual-signer synthetic fixture passes and approved signers remain blocked |
| Exact device install identity | implemented | `dev/pm3 install` verifies installed APK bytes and signer |

## Google Play readiness

| Requirement | Status | Evidence or blocker |
|---|---|---|
| Target API 36 | implemented | Gradle configuration |
| 16 KiB native compatibility | implemented gate | Dual-signer ephemeral release fixture and fresh debug APK pass ZIP/ELF checks; production-signed evidence remains blocked |
| Typed foreground services | implemented | Merged manifest and service startup order |
| Full-display projection default | implemented | API 34+ `MediaProjectionConfig` path |
| Optional microphone declaration | implemented | Manifest feature marked not required |
| Notification permission not requested unnecessarily | implemented | Permission removed from manifest |
| Privacy policy and Data Safety truth | device-check | Policy exists; Console form and final URL remain human gates |
| Free initial product | implemented | No trial, entitlement, billing, or purchase surface |
| Upload signing identity | blocked | Play enrollment and approved upload key required |
| Current signed AAB | blocked | Signing inputs are not provisioned in this workspace |
| Store listing and declarations | blocked | Play Console access and human submission required |
| Closed or internal testing | blocked | Tester enrollment and Console workflow required |

## Re-verify

```bash
source scripts/env.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:checkEngine
cargo test --manifest-path rust/Cargo.toml --locked
cargo test --manifest-path relay/Cargo.toml --locked
scripts/test-pm3.sh
scripts/test-play-boundary.sh
scripts/check-play-boundary.sh source --json
scripts/ship-check.sh --json
```

A signed release additionally requires a clean exact tag, both approved signing identities, `scripts/ship-check.sh --only=release.bundle`, and an explicit `dev/pm3 --profile release --serial <serial> install app/build/outputs/release-package/v2.0.0/phosphor-mobil3-2.0.0.apk` on the target phone.
