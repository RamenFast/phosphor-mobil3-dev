# Distribution, permissions, and signing specification

## 1. One product

Phosphor has one production application and one release pipeline.

- Production package: `dev.phosphor.mobil3`.
- Debug package: `dev.phosphor.mobil3.debug`.
- Production variants: `debug` and `release`.
- Minimum SDK: 29.
- Compile and target SDK: 36.
- ABI: `arm64-v8a`.

No source set, manifest, package, dependency, or task may restore a Fortress distribution.

## 2. Build authority

Gradle is the sole Android build, signing, JNI packaging, and artifact authority.

The Gradle wrapper version is authoritative. Bootstrap and environment scripts must use the same version.

Native release commands must use the pinned Rust toolchain and locked dependencies. Generated JNI libraries belong under `app/build/`. Stale source-tree JNI binaries are prohibited.

The Android product version must have one declaration. The build must record the Git commit used to create each artifact.

## 3. Signing identities

Production releases use a Ben-controlled application-signing identity.

Google Play uses Play App Signing with a separate upload key. The repository stores no private key or password.

Release configuration must verify the expected signing-certificate SHA-256 fingerprint before packaging.

Debug builds use the standard debug identity and a separate application ID. They must install beside production without replacing it.

## 4. Permission inventory

Allowed manifest permissions must map to a supported feature:

| Permission or access | Purpose |
|---|---|
| `INTERNET` | User-selected PC relay connection |
| foreground service | Active playback and capture |
| media playback FGS | Background local or remote playback |
| media projection FGS | User-approved playback capture |
| microphone FGS | Actual service-owned microphone input, including optional visualization mixing |
| `SYSTEM_ALERT_WINDOW` | Explicitly enabled floating HUD |
| contextual Bluetooth access | Enumerating and routing a selected supported Bluetooth microphone |
| `RECORD_AUDIO` | Microphone and playback-capture `AudioRecord` |
| `WAKE_LOCK` | Keep the display awake only while a playback or capture source is live |
| notification listener | Optional local media metadata, only if retained and disclosed |

`POST_NOTIFICATIONS` remains only if a tested user flow needs a runtime grant.

Forbidden production access includes Binder authority permissions, package management, Shizuku, ADB product sidecars, accessibility authority, advertising ID, and installation ID. Hidden opt-in root capture and floating HUD are approved under `EXPANSION.md`. Root capture requires a measured foreground-service declaration for its real role, not a fictitious projection token. No helper exposes arbitrary commands or exported control IPC. Permission and component scanners use exact permitted entries rather than a blanket root/overlay string ban.

Ordinary source wake remains tied to actual live owners. The separate, off-by-default brightness pin may keep only the full foreground app window awake during pause or no-source use. It uses the window brightness override, never global brightness settings, and releases ownership outside full-app foreground. HUD/PiP cannot acquire that exception.

Each actual playback or capture owner may hold one non-reference-counted `SCREEN_BRIGHT_WAKE_LOCK`. PlaybackService owns local or remote playback and CaptureService owns projection capture. Microphone ownership transfers from the existing Activity to a real service under the expansion contract. A capture metadata mirror is not an owner. The shared platform wrapper creates one lock per owner and narrowly suppresses the deprecated constant there. It must not use a wake-up flag or change system brightness or timeout settings.

Pause, end, error, revocation, stop, destruction, and replacement release wake when that source ceases to be live. Repeated cleanup is idempotent. A destroyed owner cannot reacquire from a delayed callback. Acquisition or release failure is reported locally without claiming that PowerManager succeeded. Existing source update paths may retry. Picture-in-picture and background linger do not own wake state. Host policy and fake-lock tests do not replace Android screen timeout and sleep acceptance.

Local wake release consumes current-deck drained or stopped-output error truth through the existing event poll and main publication. Decoder EOF after a timed-out drain cannot discard a paused tail. Absent duration, early decoder termination, and output failure cannot leave a permanently READY source face. A microphone read failure releases its recorder lock and clears only its guarded mic face. No screen-lock timeout substitutes for these source transitions. Host tests exercise the real ring and ownership helpers with synthetic platform boundaries, not Android callback delivery or physical output latency.

Remote wake consumes current-session valid media receipt and freshness from the existing native owner. Control traffic, historical counters, and signal amplitude cannot grant or renew it. Silent valid audio and valid geometry-only flow qualify. Malformed media, stale media despite continuing heartbeats, and retired sessions do not. The existing 3-second media stall threshold and service status updates release wake without a new timer or owner. Socket death retains its separate 10-second threshold. Host regressions exercise the actual acceptance and status/watchdog helpers without sockets or audio. Android main and picture-in-picture timeout, recovery, and disconnect checks remain required.

## 5. Backup and migration

Backup rules must exclude remote hosts, connection state, consent markers, obsolete authority state, and release credentials.

A one-release settings migration may preserve current user-facing display values. It must delete obsolete audit, principal, token, grant, and session data.

Same-package upgrades and the existing settings archive must preserve accepted instrument values and the five portable keys: `pip_auto_enter`, `controls_always_visible`, `grid_data`, `double_tap_playback`, and `linger_background`. The archive accepts the complete legal 0.1-through-60-second cycle range. It must not invent missing custom RGB values.

## 6. Privacy surface

The app must link to a stable HTTPS privacy policy and show an in-app privacy summary.

The summary must describe microphone audio, playback capture, media metadata and artwork, local files, remote endpoints, and the absence of analytics or silent reporting.

## 7. Release artifacts

A canonical release contains:

- signed APK
- signed AAB for Play delivery
- combined source archive containing the exact mobile tag and exact sibling-engine commit
- `SHA256SUMS`
- build manifest with commit, version, package, toolchains, signer fingerprint, and artifact hashes

Artifact filenames, manifest values, Git tag, sibling-engine commit, release notes, and on-device package information must agree.

## 8. Play gates

Before upload:

- target API requirement passes
- release lint and unit tests pass
- AAB validates with bundletool
- signing identities match the approved fingerprints
- APK alignment and every native ELF satisfy 16 KiB requirements
- Data Safety and foreground-service declarations match runtime behavior
- the privacy policy is published and reachable
- internal testing verifies the release artifact

Production submission remains a human action.

## 9. Commerce

The current release is not defined by trial, entitlement, or in-app purchase behavior.

Adding digital commerce requires a new decision, BillingClient implementation, purchase acknowledgement, pending-purchase handling, restore behavior, persistence, Play product configuration, and acceptance tests.

## 10. Rollback

Each cleanup phase has a source checkpoint. The previous APK and settings export remain available until the replacement passes device validation.

A branch may be deleted only after remote `main` contains the selected tip. A production package may be uninstalled only after explicit approval when a signer migration requires it.
