# Phase 7: B3 relay folders and offline Linux setup checks

- Date: 2026-09-05, UTC.
- Status: VERIFY. Corrected source, installer and independent human-intent reviews passed within their recorded scope. Root gate 3 passed the five added callback regressions. The exact retained debug artifact is pending. Live B3 acceptance remains open.
- B ID and private issue: B3, #3.
- Behavior baseline: mobile `09a3286874a1dab4dd3a42ccb41717f778f390d1`. The behavior commit is the commit introducing this receipt.
- Corrected source and checker freeze: `c9b54bb84a96aa335c9ae12e9f60b2607f3331a2119f01bd1d335e54844667e0`.
- Root gate input inventory, identical before and after: `8677d3ceb53f140088cf9ca383ffc9f6e6ea3eab0e76a4708e462076a91f1d9d`.
- Active sibling HEAD during the gate: `cf168579ffd56ef8de54c9f92d4f5ab945935ce5`. The six consumed dependency crates and manifests match reviewed source `4dc0f2c3ec27c560b497b887f2a8e9c9031a967f`. No sibling edit belongs to this phase.
- Target package: `dev.phosphor.mobil3.debug`, version `2.0.0-debug`, code `2000000`. No production signing change.
- Device, Android build, installed read-back and live relay service fields: not measured. No device or live relay operation ran in this phase.

## Boundary and starting state

Ben requested quiet completion while listening to a podcast. This phase changes source, tests, specifications and local receipts.
The previously installed Phase 4 baseline remains untouched. Phase 5 and Phase 6 APKs are retained artifacts, not new installations.
No phone, emulator, application launch, physical audio output, visible GUI, permission change, live service operation or publication was performed.

A worker's extra native test run did violate its explicit no-sockets boundary by creating a self-only TCP fixture. That discrepancy is recorded below.
It must not be hidden by the otherwise passing tests or described as a boundary-compliant full run.

Before this repair, relay file selection did not construct a recursive folder queue. The remote browser had no dedicated current-folder Play action.
The canonical installer lacked the new discovery/error contract and the confined fresh-HOME local/remote acceptance fixture.
This work preserves protocol v2, existing JNI entry points, the FileSession owner, and the established source and transport paths.

## Requirement-linked changes and observations

| Human outcome or integration boundary | Production path and concrete observation | Remaining live check |
|---|---|---|
| Play the configured root or a selected nested folder | Directory classification precedes extension checks. Actual FileSession tests open the empty root path and an `album.wav` directory, decode valid WAVs, and retain nested successors. | Regular-app folder selection and audible/beam output. |
| Stable complete-folder ordering | Sorted traversal and canonical local directory identity prevent cycles and select the first alias. Final relative paths sort lexically. The actual seven-item root fixture verifies nested/root-file interleaving, outside-root links, hidden/broken/non-audio filtering and terminal EOF. | Real library contents and large-folder responsiveness. |
| Preserve direct-file behavior | A direct file still starts at its requested index in sorted siblings, without adding nested tracks. Actual FileSession decoding verifies the requested file and its next sibling. | Direct root-file playback through the phone. |
| Do not publish stale remote work | Accepted Play advances the fetch token. Real SessionState handlers reject an old completion and cancelled requests, then retain the current prepared queue. | Real network cancellation and rapid source changes. |
| Advance the active file only | Finite ffmpeg decoding produces nonzero stereo PCM frames and real FileEof events. The production EOF handler advances nested tracks and rejects stale or replayed pump IDs. Tests terminate at memory channels, not an audio device. | End-to-end nested EOF advance, metadata and audible continuity. |
| Preserve a prepared remote folder queue | Existing prefetch returns the complete Selection. The isolated rclone command fixture exercises actual prefetch/open/advance, exact lsjson/copyto argv, first-file cache reuse and nested successors. No directory is sent to copyto. | Real rclone backend behavior, if claimed. |
| Accept literal backslashes without weakening traversal checks | Parser tests preserve a backslash directory/file and an ordinary sibling. Actual isolated direct/folder command checks preserve literal argv, decode both files and reach EOF. Slash, NUL, dot components and mismatched listing paths remain rejected. | Backend-specific filename encoding. |
| Distinguish browsing from playing | One `PLAY FOLDER -> QUEUE` action uses existing remotePlayFile. Directory/up callbacks only browse. Direct files still play. Twenty-one actual production-helper tests and Kotlin compilation cover these roles. | Actual Compose taps and visible pending/error feedback. |
| Old host or browser callbacks cannot control the newly selected peer | One immutable request holds peer/root/path and listing baseline. Selection/closure invalidates it synchronously. Callbacks recheck live peer and request identity. Tests reject cached/older listings, host/port changes, close/reopen, root round trips and mixed-generation reads, then accept a fresh replacement-peer listing. | Actual host switches and reconnect timing. Same-peer reconnect entirely between UI observations remains unproven. |
| Retained root callbacks and dismissed actions lose authority | Root selection requires the captured request, current peer and open browser. Play retires that request before dispatch and dismissal. Source-sheet dismissal and disposal also retire it. Five added tests cover valid root selection, close/host/request rejection, reentrant and repeated Play, idempotent retirement and production wiring. | Actual Compose disposal timing and taps, not executed by helper or source-string tests. |
| Fresh configuration behaves predictably | Seven invocations of the actual built relay's config/library-list commands use a fresh HOME and empty PATH. Absent Music, present Music, malformed config errors, repaired custom settings and unchanged saved bytes pass. | Installed binary dependencies, readable media contents and actual Linux user activation. |
| Installer has an honest public interface | The unchanged production installer runs through strict private command mocks. Fourteen named groups cover discovery, argument/dependency errors, JSON escaping, copied bytes, unit content, HOME spaces, normal local/remote paths and retry recovery. Eight additional actual fixture discovery/error invocations pass. | Real installer execution on the authorized Linux host. |
| A failed doctor check is not reported as environment readiness | Both fixture branches preserve `doctor_all_ok:false` and warn. Failed or malformed diagnostics stop activation. Config remains unchanged. Doctor checks the configured port, not a serve override. | Real dependency, readable-root and listener ownership observations. |
| Preserve upgrade and recovery truth | Mocked local/remote repeat installs preserve config bytes. Actual private unit redirection failure and retry pass. Linger failure reports false. The installer does not claim an already-running process restarted or adopted new bytes. | Real rollback, service lifetime and explicit upgrade restart. |
| Preserve the network/product boundary | Source boundary reports eleven checks, with no trusted-runtime exemption. No new protocol, privileged path or seeded endpoint was added. | Tailscale routing, protected listener, disconnect/reconnect and independent live audio/geometry streams. |

## Executed scoped gate

Root sourced the existing environment, used low CPU/I/O priority, two Cargo build jobs, two Rust test threads and offline resolution.
Android commands use only the repository wrapper with two workers. Cargo resolution is locked.

```bash
./gradlew --offline --no-daemon --max-workers=2 :app:testDebugUnitTest --rerun :app:lintDebug :app:checkEngine
cargo test --manifest-path relay/Cargo.toml --locked folder_session
cargo test --manifest-path relay/Cargo.toml --locked
cargo test --manifest-path rust/Cargo.toml --locked --lib -- --skip bridge_core::tests::shutdown_wakes_a_blocked_read
cargo test --manifest-path ../phosphor/Cargo.toml -p phosphor-audio --locked --lib
scripts/test-relay-install.sh
scripts/test-play-boundary.sh
scripts/check-play-boundary.sh source --json
```

- Corrected focused relay tests: 17 passed. Full relay package tests: 43 passed.
- Android: 246 tests across 24 suites, zero failures/errors/skips. The folder-action suite has 21 methods.
- Native library subset: 40 passed, one exact socket test filtered out. Shared audio library: 30 passed.
- Android lint/checkEngine, selected native/relay formatting and Clippy gates, scope/privacy, no-Python, anti-stub and whitespace checks passed.
- Production boundary fixture and eleven source checks passed. All three protected archive hashes passed.
- The root gate passed as one scoped command, with identical before/after source inventories. Its exclusions below remain part of the result.
- The actual relay debug binary was built into an explicit target directory. Its expected real path and SHA-256 were checked before every allowed CLI invocation and again afterward.
- Actual read-only relay binary SHA-256: `d3ff37b3c67364860973a5871ee874de5376bcad055be7aff3e53e352f83b12c`. This binary was not installed, served or used for doctor.

### Deliberate exclusions

`bridge_core::tests::shutdown_wakes_a_blocked_read` remains in production test source but was not executed in the root lane. The native result is a subset.
The developer CLI fixture is NOT RUN because its real doctor dispatcher, inherited signing inputs and canonical APK replacement need a separate confined lane.
The release fixture is NOT RUN because it reaches a nested Gradle invocation without offline mode and creates Git commits with inherited hooks/signing settings.
No detector or production fixture was weakened. These deferred checks cannot be counted as passed.
The real relay doctor is excluded because it binds a listener. A mock doctor result is not a real dependency or Tailscale check.

## Failed attempts and corrections

The first independent source review requested changes for remote backslash rejection and stale-host exposure of the new folder action.
Both were corrected in the existing parser/UI seams. Separate corrected-source review passed their bounded production paths.
An added positive replacement-peer assertion changed a review snapshot. That failed identity check remains preserved, followed by a new exact freeze.
Root gate 1 passed Android, selected native and shared checks, then failed relay compilation with E0618. A test-local binding shadowed the resources factory.
A two-line test-only rename corrected it without changing assertions or production behavior. Root then ran the focused relay checks and the complete permitted gate successfully.
The original failed gate remains failed. Pre-correction green counts are not used as proof of corrected tests.

The independent human-intent review then found unguarded retained root-selector callbacks and incomplete dismissal retirement.
Root corrected those existing UI seams without changing protocol, JNI or source ownership. The original finding remains unchanged.
Gate 3 completed at 21:47:56 UTC with all five new regressions passing and its full source inventory unchanged.
These checks invoke production Kotlin helpers and assert actual wiring. They do not execute Compose callbacks.

The original runtime worker's extra full native suite executed the self-only TCP test despite its no-sockets condition. It reported the violation and stopped runtime work.
Its log and report remain unchanged. Root's later selected native result does not erase that mistake or justify a boundary-compliant label for the earlier run.

The worker report also named Android XML/lint archive paths that were absent. Root recovered the actual surviving 235-test XML and lint outputs before new builds.
Their timestamps match the original run. Later root gate XML is retained independently. This recovery is not a claim that the originally named archive existed.

## Evidence identities

Raw evidence is retained under ignored `dev/scratch/pre-v2-20260829T072841Z/phase-07/`.

| Artifact | SHA-256 |
|---|---|
| Corrected source review | `41d06660d7bb265de96b7e5d97926d02faae6aeb6ec65e433005187860b8c289` |
| Independent human-intent correction03 closeout | `96845a5b080be69f6ecdc9f2e2654841311844cfcff6471fafdc59937a37357a` |
| Installer source review | `45f73c1f6ec60be57671b3715f7450861bd113f5c4c9d8aa6f1a2e43e20d1134` |
| Gate source-confinement review and final provenance addendum | `1e06f6f5ec8e473fe11301f7ce1742b4045f9411373bd627a6e11896e7d50edb` |
| Root Android log | `5adb5bc2215af98c00f110edba2ae59468e1f3286e3640936f4e85fef1892354` |
| Root relay test log | `372bba307135feaf0430dd1dd576ec285d3b72ff8b47da422190d2e4fb18cd57` |
| Actual config/library CLI log | `d73e365b1e985c5f15fa811428049f993202d536d0184002de69f19f66f59b63` |
| Confined installer gate log | `3a01f0a9c75732c1f0f1b8a3deba64afe89e4b7949ffe5d46077761bf8209e56` |
| Source-boundary result | `830f331a9a4faed45603f319effdc2d345790a42730b05c6dfd173493e113a3a` |

## Remaining acceptance and rollback

B3 stays VERIFY, with its actual live checkboxes and drift 21 unchanged. Source/helper tests cannot prove the original Tailscale complaint is resolved.
Remaining observations are regular-app dormant launch, actual Linux installation, service/schema/dependencies/readable roots, protected Tailscale routing, live PC audio,
direct root file, complete folder first item and nested EOF, metadata/beam/audible output, Compose callbacks and disconnect/reconnect.
No same-peer session epoch or real backend freshness guarantee follows from the UI's host/request/revision checks.

The independent human-intent correction03 receipt closes the two identified callback-authority defects against source and executed helper evidence.
It preserves the original finding and explicitly excludes rendered Compose and live B3 acceptance. The exact clean-snapshot debug artifact remains next.
No installed relay or app state changed, so no live rollback was needed. Keep the verified prior APKs and source commits intact.
A future authorized deployment must back up the existing relay binary, unit and config and restore them on failure, as required by Phase 7.
