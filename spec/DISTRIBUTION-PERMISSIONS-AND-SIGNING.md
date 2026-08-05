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
| network state and routing | Remote link truth and selected transport |
| foreground service | Active playback and capture |
| media playback FGS | Background local or remote playback |
| media projection FGS | User-approved playback capture |
| `RECORD_AUDIO` | Microphone and playback-capture `AudioRecord` |
| notification listener | Optional local media metadata, only if retained and disclosed |

`POST_NOTIFICATIONS` remains only if a tested user flow needs a runtime grant.

Forbidden production access includes Binder authority permissions, package management, Shizuku, ADB, overlay, accessibility, root, privileged capture, advertising ID, and installation ID.

## 5. Backup and migration

Backup rules must exclude remote hosts, connection state, consent markers, obsolete authority state, and release credentials.

A one-release settings migration may preserve current user-facing display values. It must delete obsolete Nexus, causal audit, principal, token, grant, and session data.

## 6. Privacy surface

The app must link to a stable HTTPS privacy policy and show an in-app privacy summary.

The summary must describe microphone audio, playback capture, media metadata and artwork, local files, remote endpoints, and the absence of analytics or silent reporting.

## 7. Release artifacts

A canonical release contains:

- signed APK
- signed AAB for Play delivery
- source archive from the exact tag
- `SHA256SUMS`
- build manifest with commit, version, package, toolchains, signer fingerprint, and artifact hashes

Artifact filenames, manifest values, Git tag, release notes, and on-device package information must agree.

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
