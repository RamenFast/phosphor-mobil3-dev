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

### Exact manifest gate

The local boundary command parses XML by namespace and rejects malformed documents, DTDs, external entities, and unknown permission/component identities. Comments and formatting cannot grant an exception. Its private parser uses the existing pinned JDK's standard XML library. This adds no product dependency, Android build runtime, public helper command, or installed service.

Base permission entries are `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `RECORD_AUDIO`, and `WAKE_LOCK`. Optional expansion entries are `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, and legacy `BLUETOOTH` bounded through API 30. Adding an entry to this permitted set does not grant access or establish tested product behavior.

Merged AndroidX output may add `ACCESS_NETWORK_STATE` and the production package's signature-only `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. This is not authorization for process-wide physical-network routing. App source retains the existing forced-routing ban. Production rejects debug package identity, `debuggable=true`, `testOnly=true`, shared system UID, persistent application mode, instrumentation, unknown services/providers/receivers, and activity aliases.

| Component | Exposure | Required role |
|---|---|---|
| `MainActivity` | Exported | MAIN/LAUNCHER only |
| `PlaybackService` | Exported | MediaSessionService action and mediaPlayback FGS |
| `CaptureService` | Private | mediaProjection FGS |
| `CaptureNotificationListenerService` | Private | NotificationListenerService action and BIND_NOTIFICATION_LISTENER_SERVICE |
| `MicCaptureService` | Private, optional expansion | microphone FGS |
| `RootCaptureService` | Private, optional expansion | specialUse FGS with a nonempty declared purpose |
| `FloatingHudService` | Private, optional expansion | specialUse FGS with a nonempty declared purpose |
| `androidx.startup.InitializationProvider` | Private, merged only | Exact package-local androidx-startup authority and reviewed initializers |
| `androidx.profileinstaller.ProfileInstallReceiver` | Exported, merged only | DUMP permission and the four existing profileinstaller actions |

Optional component identities reserve a checkable boundary, not a final backend implementation or Play approval. Before adding a service, prove its actual platform role and add its paired manifest permission. Root/HUD specialUse candidates require real Android acceptance and distribution review. Do not silently broaden the permitted type or export rule when a candidate fails.

Keep archive/dependency/reporting/seeded-endpoint scans and exact release provenance/signing checks. A clean source gate is not a compiled-artifact gate. Missing parser/toolchain evidence returns exit 2 with a fix. XML/policy violations return exit 4 through the command's normal error envelope.

#### Packaged evidence and error regressions (section 1, round 1 correction)

The artifact gate always decodes the named archive's own manifest before accepting it. APK evidence is exactly one root `AndroidManifest.xml`, decoded by the existing Android SDK `apkanalyzer`. AAB evidence is exactly one `base/manifest/AndroidManifest.xml`, decoded by the existing pinned bundletool through a standalone Gradle task. This single-module product rejects additional module manifests, mixed APK/AAB manifest layouts, duplicate ZIP member names, missing manifests, and malformed encoded manifests. Plaintext XML in place of a binary APK or protobuf AAB manifest is not packaged evidence. Do not implement a new binary XML or protobuf parser.

`--manifest` remains an optional supplementary XML policy check. It never replaces packaged decoding, and no unrelated build-intermediates manifest is selected automatically. The result's existing `manifest` field identifies the supplementary path when supplied, otherwise the archive member. Existing modes, result keys, archive protections, exact SDK policy, dependency scans, libc++ derivation, and signing gates remain unchanged. A boundary pass alone does not prove release signing or store acceptance.

SDK decoders can print compiled enum values as decimal or hexadecimal numbers. Merged evidence accepts only the exact numeric equivalents of signature protection (2), mediaPlayback (2), mediaProjection (32), microphone (128), and specialUse (1073741824). Combined flags and unknown values remain forbidden. Source XML still uses the named values. Real-format positive fixtures exercise these roles and signature permissions, not only an empty application.

The focused `writeBoundaryBundletoolClasspath` Gradle task resolves the existing pinned configuration without building the app. Direct boundary calls use that task offline. The existing `checkPlayBoundary` Gradle caller supplies the same resolved classpath through its private `PHOSPHOR_BOUNDARY_BUNDLETOOL_CLASSPATH` environment to avoid recursively launching Gradle while it holds build locks. This is decoder runtime selection, never detached manifest evidence. The gate verifies bundletool's declared pinned version before decoding. Missing runtime capability returns exit 2.

Before source launching the private XML parser, verify that the selected JDK exposes `jdk.compiler` and `java.xml`. A compilerless Java runtime returns exit 2, `manifest_parser_unavailable`, and a concrete JDK fix. Missing SDK decoder or pinned bundletool capability also returns unavailable exit 2. Invalid archive encoding and manifest policy violations return exit 4 with a fix. Every JSON string escapes all representable C0 characters, including XML 1.1 character references such as U+0001. Bash arguments cannot contain NUL.

Regression acceptance uses isolated AAPT2-compiled production APK fixtures and AAPT2 protobuf plus pinned-bundletool AAB fixtures. Both formats have accepted production manifests and rejected debug identity/flags, unknown permission, malformed, missing, mixed, and duplicate evidence cases. A real compiled debug APK paired with unrelated production XML must fail. Public CLI checks parse each response with jq, exercise every representable C0 byte, and run a compilerless Java launcher that must return exit 2 with a fix. These fixtures do not build, sign, install, or replace the application.

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
