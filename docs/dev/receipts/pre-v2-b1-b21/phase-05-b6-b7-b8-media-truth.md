# Phase 5: B6/B7/B8 media truth

- Date: 2026-09-05, UTC.
- Status: VERIFY. Frozen source and separate human-intent reviews passed. Runtime, fixture and boundary gates passed. A clean-snapshot debug candidate was verified at 18:58 UTC. Original concurrent trees remain untouched. Android acceptance remains deferred.
- B IDs: B6, B7, B8.
- Private issue: #2.
- Mobile baseline: `1f94a4d` after the narrow scope-document correction. The behavior commit is the commit introducing this receipt.
- Shared audio test commit: `4dc0f2c3ec27c560b497b887f2a8e9c9031a967f`.
- Frozen behavior manifest: `ec3c9b931b441d2e4af527cb71e7981bfcfab463304c28997e51de0d457cc439`.
- Device, Android build and installed candidate fields: not measured in this phase. No device command ran.
- Candidate package: `dev.phosphor.mobil3.debug`, retained debug build only. No production package change or installation.

## Starting state and boundary

Ben requested quiet completion while listening to a podcast. Work in this phase changes source, tests and local receipts only.
No phone install, launch, permission change, visible window, playback command, active relay operation or publication occurred.
The Phase 4 APK remains the previously installed baseline. This receipt does not infer current phone state from source tests.

The sibling already published exact path-bearing `TrackStarted` after metadata/art preparation and `PlaybackEnded` at natural termination.
No shared runtime change was needed. The sibling commit adds real decoder/thread/channel tests only.
Concurrent unrelated desktop work later made the sibling tree dirty. Reviewed shared dependencies remain subject to exact hash checks.
A clean-sibling build precondition cannot be replaced by a claim that unrelated changes probably do not matter.

## Behavior changes and requirement-linked evidence

| Original outcome / boundary | Implemented path | Concrete offline observation | Remaining acceptance |
|---|---|---|---|
| B6 tagged local title and artist | One existing service watcher polls the native receiver on the existing serial local-deck executor. Metadata and art are read after exact `track_started`. | Real shared tagged WAV decode publishes metadata before started/end. `PlaybackTruthTest` publishes matching tags and art once. | Tagged direct file and nested folder must show matching in-app and notification title/artist on Android. |
| B6 blank tags | Boundary whitespace and terminal RIFF NULs are normalized. Original selected filename is the title fallback. Blank artist/album remain absent. | Null, blank, whitespace and NUL cases pass. The real RIFF fixture exposed terminal NUL and is retained. | Verify untagged file face and no stale artist on phone. |
| Latest request, same-path seek and failures | Exact native-open object plus the existing latest-request predicate guards main publication. Failed replacement clears the cache. Failed preflight retains only surviving audio metadata. | Queued-main/same-path tests reject superseded snapshots. Failed native open, retained source and retry pass. | Rapid local seek/next/folder replacement with real Android callbacks and output. |
| Asynchronous decoder reopen failure | Pre-start `playback_ended` triggers only the current owner's existing serial failure/close path. Started EOF does not advance through the event consumer. | Failed-reopen, retained-failure and recovery regression passes. Real shared failed decoder emits ended without started. | Actual file-race failure and recovery on Android. Existing position-based EOF timing remains unchanged. |
| Valid local duration | Only positive milliseconds through `Long.MAX_VALUE / 1000` reach Media3 microseconds. Invalid duration stays unknown, not clamped. | Negative, zero, ordinary, safe maximum and overflowing boundaries pass. | Valid-duration UI behavior and malformed-file recovery. |
| B6 captured metadata and absence | Active controller generation rejects superseded callbacks. Missing access/session and NONE/ERROR clear tags, artwork, duration and actions. | Binding generation, same-controller rebind and unavailable-state policy regressions pass. | Actual access revoke/grant, session destruction/replacement and notification parity. |
| B7 useful captured seek | Real bounded duration plus `ACTION_SEEK_TO` is required in mirror availability, UI gating and fresh-controller routing. Valid targets are clamped to the real duration. | State/action/duration matrix passes. Production wiring retains all three checks. | Drag the actual seek rule and observe captured app position. Nonseekable sessions must expose no rule. |
| B8 honest glyph | Capture maps the existing active platform-state set through `playWhenReady`. Local and remote retain `isPlaying`. Initial, metadata and timeline sync cover source transitions. | Playing/buffering/transition/pause matrix and buffering-to-paused-local/remote regression pass. | Observe play, buffering, pause and resume against audio and notification state. |
| Captured commands match availability | Play capability matches the current glyph. Missing captured actions are hidden while SRC remains. Fresh play/skip routers recheck current state and action. | NONE/ERROR with lingering SKIP bits route nothing. PLAYING/PAUSED matching actions pass. Source assertions connect Activity commands to Console visibility. | Real Compose control visibility and actual Android transport routing. |
| No blocking or competing owner | Native poll uses `try_lock`/`try_recv`. One poll remains in flight until main completion. PhosphorPlayer no longer reads metadata/art through JNI. | Two native channel/lock tests pass. Repeated watcher scheduling creates one queued poll. Kotlin wiring assertion finds one consumer. | Android looper responsiveness, Oboe output and existing reader ownership regression. |

Policy and source-wiring tests do not instantiate Android MediaSession callbacks, Activity lifecycle or Compose controls.
The shared tests execute the production decoder and player thread with no audible output target. They are stronger than a copied event fixture, but not hardware playback acceptance.

## Commands and observed results

Commands source `scripts/env.sh`, run at `nice -n 15 ionice -c 3`, use two Gradle/Cargo workers and locked Cargo resolution.
Root gates also force dependency resolution offline. The sole Android runtime is the repository Gradle wrapper.

```bash
./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest :app:lintDebug :app:cargoBuildDebug :app:checkEngine
cargo test --manifest-path rust/Cargo.toml --locked
cargo test --manifest-path ../phosphor/Cargo.toml -p phosphor-audio --locked
cargo test --manifest-path ../phosphor/Cargo.toml -p phosphor-audio --locked --no-default-features playback_events
cargo test --manifest-path relay/Cargo.toml --locked
scripts/test-pm3.sh
scripts/test-play-boundary.sh
scripts/test-release-gates.sh
scripts/check-play-boundary.sh source --json
scripts/ship-check.sh --only=scope. --json
```

- Worker freeze03: 198 Android tests across 21 suites, zero failures/errors/skips. Focused truth tests: 11 local plus 10 capture.
- Worker full gate: lintDebug, cargoBuildDebug and checkEngine passed in 3m 23s. All 14 source hashes matched before and after.
- Root first gate: Android build/lint/checkEngine passed and validated the same 198-result XML. The test task was up-to-date, not a new independent execution.
- Root native suite: 41 passed. Shared audio suite: 30 passed. Relay suite: 26 passed.
- Shared real event tests: 3 passed under both default and mobile no-default-features configurations. Independent shared reviewer reran both.
- Root developer CLI, production boundary and release-provenance fixtures passed. Fixture install/signing tests do not install or sign a real application.
- Source boundary: 11 checks passed. Native and relay formatting/Clippy gates passed.
- Root scope gate first failed on a collaborator name in future roadmap brief ownership text, not a retired product promise. The wording now points to the canonical ask ledger. Both real scope gates pass without weakening their detector.
- Root attempt 2 used `./gradlew --offline --no-daemon --max-workers=2 :app:testDebugUnitTest --rerun :app:lintDebug :app:checkEngine`. Android tests executed again: 198 passed, zero failures/errors/skips. Native 41, shared 30 and relay 26 passed again. CLI, boundary, provenance-fixture, Rust lint, scope, privacy, no-Python and anti-stub gates passed.
- Root attempt 2 then failed the whole-sibling whitespace check on concurrent unrelated `crates/phosphor-app/src/main.rs` edits. It remains an overall failed command, not a full clean-tree gate PASS.
- Root separately verified every frozen mobile, relay, six shared-crate and gate-input hash after this failure. Scoped shared-dependency whitespace checks, mobile whitespace checks and protected-archive checks passed. No concurrent desktop edit was changed.

## Independent reviews and retained failures

Separate implementation and source review used the approved Astra high route. The source reviewer did not edit the implementation.
Final source review passed against the 14 freeze03 identities. The reviewer closed the demonstrated stale capture skip and duration overflow findings.
The separate human-intent reviewer also passed offline intent fit after tracing the raw B6/B7/B8 outcomes through production sources. Root read both full reports and verified their before/after inventories match freeze03. Neither review claims Android acceptance.

The first worker full gate timed out after 600 seconds while lint traversed Kotlin comments. That changing-source attempt is not a full PASS.
Two thread dumps and the failed log remain retained. Immutable freeze02 then passed, followed by corrected freeze03.
No lint detector suppression, cache deletion or unrelated process kill occurred. The evidence does not establish a definitive timeout cause.
The first shared tagged fixture assertion failed on a valid terminal NUL. The corrected assertion and mobile tag normalization passed real decode tests.
Root gate attempt 1 remains an overall failed attempt because scope.docs stopped it. Attempt 2 remains failed at the unrelated sibling whitespace guard. Passing earlier components do not turn either into a full PASS.

All raw evidence is under ignored `dev/scratch/pre-v2-20260829T072841Z/phase-05/`.

| Retained evidence | SHA-256 |
|---|---|
| `worker-source-freeze-03.sha256` | `ec3c9b931b441d2e4af527cb71e7981bfcfab463304c28997e51de0d457cc439` |
| `gradle-gates-03.log` | `0169775876dc493973634b88581af4d3aee1ab9f27fa87b78987cf14050e3627` |
| Root attempt 2 frozen input inventory | `64fc8d74b58d9bd45c67a57b1bfe131acae7fdc8a864aec523ea93821e35beed` |
| Root attempt 2 Android execution log | `e18b9e38850c977fb9a1059347566dd8d132e6e342cb1ef1c94fd354a478a1cf` |
| Final source review | `7ebd458e70d3416682fc7906c89909b833640e5a9a387ddac623a8745130faf1` |
| Final source review before/after inventory | `dd422c8b403f82eb14a5604aecfd043176a2f287dde8beb57ff44a0783984149` |
| Initial source FAIL report | `edc15059069d30641d64dd94eec87eba350df02ae7019d314fb9ec4c6fdd525b` |
| Initial duration FAIL report | `57bf7a066ed67a4b5ca6a9ece244d677cb673890a87a6121e1f5fac3d99496db` |

## B-card results and later live matrix

- B6: VERIFY. Offline event/publication/recovery checks pass. Android local/capture title and artist acceptance remains pending.
- B7: VERIFY. Offline seek capability/routing checks pass. Actual captured seeking remains pending.
- B8: VERIFY. Offline state/glyph mapping checks pass. Actual audible/UI/notification agreement remains pending.

A later authorized matrix must cover tagged/blank local files, nested folders, same-path seek supersession, invalid replacement, capture permission absence/recovery, stale/destroyed controllers, play/buffer/pause/resume, valid/nonseekable durations, source transitions and notification parity.
Retain package, source/APK/signer identity, read-back hash, timestamps, Android build and redacted action/state evidence then.
No current acceptance checkbox or `drift: 21` is changed by these offline results.

## Rollback and restoration

No phone, portable settings, runtime consent, screen timeout or active relay state was changed during this phase.
The Phase 4 retained APK is `phase-04/candidate-47ff7cdc71c3/phase-04-47ff7cdc71c3.apk` under the same ignored run directory.
Its SHA-256 is `ad9a726eb510d3c5ecd066af7218fbc95fdbbef137acc7603ed5771118d42993`.
Its debug signer SHA-256 is `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
Root rechecked the retained APK bytes during quiet work. No installation or rollback action is currently needed.
A later rollback reverts the Phase 5 mobile behavior commit and optional sibling test-only commit, then uses the retained Phase 4 APK through the authorized explicit-device workflow.

## Open boundaries

**Blocked:** Android and audio/visual acceptance would interrupt the protected podcast window.
**Evidence:** No live operation was attempted or authorized in this phase.
**Best current result:** Frozen implementation, real shared decoder integration tests, passing runtime/fixture gates and separate source/human-intent reviews.
**Next step:** Complete remaining quiet review/build receipts, then run the named live matrix only in an authorized noninterrupting window.

The initial clean-candidate blocker was resolved with exact detached source snapshots, documented below. Concurrent desktop work remains preserved, not reset or silently included.

## Clean-source artifact follow-through, 18:48:54 to 18:58:26 UTC

The root built from local detached snapshots of the two reviewed commits. Each snapshot had an empty Git status and no branch refs.
No Git worktree, new branch, network fetch or change to either original source tree was needed.
The original sibling tree remains concurrently edited. Cleanliness below applies to the actual build snapshots, not that original tree.
Both snapshots matched every input in the retained root attempt 2 inventory before and after the build. Whole-snapshot whitespace checks passed.
The existing local JDK, SDK and Gradle cache were reused. Native targets and Android outputs were separate from the active repository.

| Identity | Verified value |
|---|---|
| Mobile source | `5215120513fc025215526ba3d91abc53a021ca04` |
| Shared source | `4dc0f2c3ec27c560b497b887f2a8e9c9031a967f` |
| Package | `dev.phosphor.mobil3.debug` |
| Version | `2.0.0-debug`, code `2000000` |
| APK SHA-256 | `65501d9a5281248d335f6cca7c122f4cdd2d1050283d0ed2262dc72a238f8403` |
| Debug signer SHA-256 | `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d` |
| Android build log SHA-256 | `4dd04608ee16e3cd784a90e9053669825f961304803a42fef00965733fa5b801` |
| Full artifact boundary output SHA-256 | `b1bda14c558ab7b8c42e5556698e3f18209152371d9c06eda617d814f00d3528` |

The retained directory is `phase-05/candidate-5215120513fc-isolated/` under the ignored run directory.
It contains `phase-05-5215120513fc.apk`, XML test results, lint output, package metadata, merged manifest, signer report, runtime dependencies and checksums.
The root reread every retained checksum after successful task completion. All six matched.

```bash
./gradlew --offline --no-daemon --max-workers=2 :app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebug :app:checkEngine
./gradlew --offline --no-daemon --max-workers=2 :app:dependencies --configuration debugRuntimeClasspath --console=plain
scripts/check-play-boundary.sh all --artifact "$APK" --manifest "$MANIFEST" --dependencies "$DEPENDENCIES" --json
scripts/ship-check.sh --only=scope. --json
```

The wrapper ran at low CPU/I/O priority with two workers and offline Cargo resolution.
The Android build passed in 8m 58s, with all 55 tasks executed. Tests passed 198/198 with zero failures, errors or skips.
Lint, packaged JNI and debug assembly passed. The Android signer verifier accepted the APK and matched the retained Phase 4 debug signer.
The full boundary command passed 11 source and 5 artifact checks. Both scope gates passed. The protected archive remained byte-identical.
The complete artifact task exited zero in 571.9 seconds. Its before/after source verification outputs were identical.
The earlier failed root aggregate commands remain failed historical attempts. This separate clean-snapshot artifact success does not relabel them.

No device state was queried or changed. Installed read-back identity, Android lifecycle, real metadata/seek/glyph behavior and visual acceptance remain pending.
The artifact is available for the later authorized explicit-device workflow. No production signing, release, installation or publication occurred.

## Redaction check

- No literal device serial, private host/address, credential or personal media path is included.
- Raw logs and build artifacts remain ignored.
- Source hashes and test counts describe the exact scope checked, not phone acceptance.
