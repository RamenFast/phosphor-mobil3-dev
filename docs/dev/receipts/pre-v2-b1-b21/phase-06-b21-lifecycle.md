# Phase 6: B21 task removal and optional background playback

- Date: 2026-09-05, UTC.
- Status: VERIFY. Offline implementation, separate source and human-intent reviews, unit/JNI/lint gates and root boundary components passed. Android acceptance and the Phase 6 artifact remain pending.
- B ID and private issue: B21, #1.
- Behavior baseline: mobile `5215120513fc025215526ba3d91abc53a021ca04`. The behavior commit is the commit introducing this receipt.
- Frozen source: 15 files, manifest SHA-256 `f2399b807d25f546dc3e2086015e28cad44a5d535a92fe04b71c3a4b4202f699`.
- Shared reviewed source: `4dc0f2c3ec27c560b497b887f2a8e9c9031a967f`. No sibling or native runtime edit belongs to this phase.
- Device, Android build and installed read-back fields: not measured. No device command ran.
- Planned retained package: `dev.phosphor.mobil3.debug`. No production package or signing change.

## Boundary and starting state

Ben requested quiet completion while listening to a podcast. This phase changes code, tests, active specifications and local receipts only.
No phone, emulator, install, launch, playback, permission, GUI, active relay operation or publication occurred.
The previously verified installed Phase 4 baseline remains untouched. Phase 5 has a separately verified retained APK, not a new installation.

Before this phase, the services did not implement the requested task-removal policy. The Activity did not stop its own microphone on destruction.
Capture cleanup could start PlaybackService just to publish STOPPED. Local and capture player faces advertised release without an immediate handler.
The relay face disconnected native state on release, separate from service-owned teardown. No portable linger option existed.

## Requirement-linked changes and observations

| Human outcome or integration boundary | Production path | Concrete offline evidence | Remaining live check |
|---|---|---|---|
| Stop by default, preserve only approved existing service sources | One false-default `BackgroundLifecyclePolicy` reads actual local, relay and capture owners at removal. | All owner combinations and paused-local policy pass. No microphone-linger branch exists. | Actual recents removal for each active and paused source. |
| One persistent full-settings control | One SettingsSheet chip uses the existing ScopeActions/SheetActions adapter. Activity saves immediately and restores the same Boolean. | Default false, true/false archive round trips, wrong-type rejection and missing-key merge pass. | Actual chip operation, persistence and update/archive workflows. |
| PiP remains independent | Linger uses only `linger_background`. Runtime consent and portable tuning remain separate. | Source review and settings wiring find no PiP read/write in the feature. | Later PiP and linger combinations. B12 remains separate. |
| Late work cannot revive a removed task | Task/Activity revisions guard source intents, permission, consent, picker results, handoff replies and runtime source saves. | Production policy and LatestRequestSlot tests retire pending starts and reject prepared late publication. | Real delayed Android callbacks during removal and recreation. |
| Same retained service handles later removal safely | Activity records taskId. Service callbacks consult current appTasks membership before choosing a revision. | Newer-present task rejects old removal. Reopen, disable linger and absent-task removal pass at the actual policy seam. | Actual membership visibility and callback timing. |
| Microphone remains Activity-owned | Activity destruction cancels requests and stops its own MicController. Ordinary onStop does not stop it. | Ownership and wiring regressions pass. Old Activity cannot retire a newer Activity token. | Picker preservation and actual microphone-only callback delivery. |
| Ordinary service destruction does not stop another source | Playback onDestroy queues only owned native teardown. Actual task removal selects reader shutdown. Unowned teardown does not publish false NONE. | Separate source and intent reviews close the demonstrated ownership regression. Production wiring checks both paths. | Real controller disconnect/service destruction while mic or capture survives. |
| Native shutdown is serial and cannot be superseded | One terminal request stops polling, retires publication and closes owned native state on the existing executor. | Actual latest-request policy tests and root compilation pass. A real future dependency test waits for predecessor success without manufacturing a timeout failure. | Actual JNI closure, audio cessation and provider-stall behavior. |
| Capture stop cannot resurrect or erase another owner | STOPPED reaches only an existing matching Playback owner. Projection/ring cleanup remains with CaptureService. | Actual owner-token tests reject absent/newer owners. Source wiring contains no STOPPED startService. | Projection release, notification removal and stale stop delivery. |
| Surviving capture regains its mirror | New Playback session reads a guarded current capture-owner ID and restores the existing mirror. | Production token restore is idempotent. Wiring uses existing mirror setup, not new projection or source startup. | Real title/artist, seek and transport restoration without another prompt. |
| Reader failure remains honest and recoverable | Retired capture owners retain stop-only retry access. Retry carries prior destruction acknowledgement. | A real Java test thread produces ReaderStop.TIMEOUT, exits after destruction, then joins through an explicit production-helper retry. Pending/error results remain blocking. | Actual AudioRecord timeout/destruction/retry. A test thread is not Android recording. |
| A removed capture service ends after returned cleanup, even on failure | Exact-owner task-removal state controls service termination. Failure and reader reference survive independently. | Initial, retry and already-failed completion policies pass. No timeout becomes joined success. | Started-service disappearance and separate actual reader/process state. |
| Face release does not perform a second stop | Local, capture and relay handleRelease return immediate futures. Service remains the resource owner. | Three actual production handlers are invoked three times each. Futures are immediately done and callback hooks remain untouched. | Real public SimpleBasePlayer.release on its proper Looper. |
| Old controller connection cannot replace a new UI binding | Separate per-start generation rejects and releases stale futures. Failed future handling returns without a main-thread exception. | Production binding test completes B before A and rejects A. Existing action/owner wiring compiles. | Actual Activity stop/start and asynchronous MediaController connections. |

Some tests assert production source wiring. Player-release tests use constructor-free instances and invoke protected handlers, not public Android release.
No policy test proves ActivityManager timing, MediaProjection teardown, Compose input or actual audio output.

## Executed gates

Commands source `scripts/env.sh`, use the repository wrapper, two workers, low CPU/I/O priority, offline resolution and locked Cargo.

```bash
./gradlew --offline --no-daemon --max-workers=2 :app:testDebugUnitTest --rerun :app:lintDebug --rerun :app:cargoBuildDebug --rerun :app:checkEngine --rerun
cargo test --manifest-path rust/Cargo.toml --locked
cargo test --manifest-path ../phosphor/Cargo.toml -p phosphor-audio --locked
cargo test --manifest-path relay/Cargo.toml --locked
scripts/test-pm3.sh
scripts/test-play-boundary.sh
scripts/test-release-gates.sh
scripts/check-play-boundary.sh source --json
scripts/ship-check.sh --only=scope. --json
```

- Worker full02 passed in 3m 12s: 225 tests across 23 suites, zero failures/errors/skips. All four requested Gradle tasks executed.
- Focused final suites: lifecycle 21, actual release handlers 3, settings archive 12. Existing microphone handoff 15 also passed.
- Worker lint completed with zero errors and 15 retained warnings. No detector suppression or cache deletion occurred.
- All 15 owned source files and 168 worker build inputs matched before and after. Separate source and source-first human-intent reviewers matched the same 15 identities.
- Root independently reran all 225 Android tests with `--rerun`. Lint and engine checks passed. Native 41, shared audio 30 and relay 26 tests passed.
- Root developer CLI, production boundary and release-provenance fixtures passed. Source boundary passed 11 checks. Native and relay formatting/Clippy gates passed.
- The original root aggregate exited 2 at scope.docs. Concurrent documentation commit `9591c32` introduced a collaborator name that the gate misread as retired product language.
- Narrow correction `c147ff7` preserves the separately governed dwelling exclusion through the existing canonical governance reference. Both actual scope gates then passed, without detector changes.
- Root separately completed privacy, no-Python, anti-stub, formatting, scoped shared whitespace and protected-archive checks. The before/after runtime input inventories remained identical.
- The failed aggregate remains a failed historical command. The successful boundary follow-through does not relabel it as an all-in-one PASS. Exact retained artifact verification follows separately.
- Concurrent unrelated sibling desktop work remains untouched. No whole-original-sibling cleanliness is claimed from scoped dependency checks.

## Retained failures and independent correction review

The first focused compile failed because the new setter was missing from the existing action adapter. Two forwarding lines corrected it.
The second focused gate passed 30 intermediate tests. Its nullable reflection warning was corrected before the final freeze.
Worker full01 ran 221 tests with one stale exact-source assertion failure. Native/JNI passed, but lintDebug did not complete.
The corrected assertion requires aggregate retirement completion and registration of the actual owner. Other microphone safety assertions remain unchanged.

Independent review rejected earlier snapshots for duplicate remote release, immutable timeout failures, lost destroyed-owner retry and avoidable capture-service retention.
It also found missing capture mirror restoration. Root found stale UI controller binding and ordinary destruction stopping separate readers.
Freeze02 corrected each demonstrated path. Source and separate human-intent reviews then passed the same immutable snapshot.
Earlier failed logs, XML and source-review reports remain retained.

## Evidence identities

Raw evidence lives under ignored `dev/scratch/pre-v2-20260829T072841Z/phase-06/`.

| Artifact | SHA-256 |
|---|---|
| `worker-freeze-02.sha256` | `f2399b807d25f546dc3e2086015e28cad44a5d535a92fe04b71c3a4b4202f699` |
| `worker-full-2.log` | `db53592a2a51b498bcfd7e94875e96949d3c4e68363708682d53cfb5e62a251a` |
| Worker build-input inventory | `2280d915c0d98374765bf156eed97a04c37b20db4644155f648bb99f9b46586d` |
| Root runtime-input before/after inventory | `3b1d9629da8b73be60612c36d9169a8415086705d185ea23543b3783133a2cc6` |
| Root Android execution log | `01e8acd88f3322370ca53f9af09e850a69abf66d09b1f6085a22941044e029bf` |
| Independent source review | `2abebd499ef7105ebef780026b23d4054039de8b2611c453e7f17b0b1d4985c1` |
| Independent human-intent review | `cf915d3ac1bbd3deafa995b72b1a5d9ffa45f454f9ba3d690f2676ac71b1c11a` |

## B21 result and later acceptance

B21: VERIFY. Offline ownership, persistence, release and recovery paths are implemented and tested at their stated levels.
All eight B21 regression conditions and the original B21 card remain open. Drift remains 21.

**BLOCKED[B21-MIC-OWNER]:** only actual microphone-only/no-service callback delivery is unmeasured.
The stop rendezvous and Activity-owned destruction path exist. Neither host tests nor source inspection proves Android delivers them for every recents-removal case.
The resolving check is an authorized phone receipt showing actual task/destruction delivery and AudioRecord termination, without adding a new microphone owner speculatively.

The full later matrix also covers active/paused local, connected/reconnecting relay, starting/flowing capture, consent reset, linger, second removal, stale callbacks, picker navigation, capture rebind, real public release and real reader recovery.
Record package/source/APK/read-back/signer identity, timestamps, Android build and redacted source/service/thread observations then.

## Rollback and restoration

No device, settings export, screen timeout, active relay or production state changed in this phase.
The prior retained Phase 5 APK is `phase-05/candidate-5215120513fc-isolated/phase-05-5215120513fc.apk` under the same ignored run directory.
Its SHA-256 is `65501d9a5281248d335f6cca7c122f4cdd2d1050283d0ed2262dc72a238f8403`.
Its debug signer SHA-256 is `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
A later authorized rollback reverts this phase's behavior commit and uses the retained prior artifact. No rollback or installation is currently needed.

## Redaction

No literal device serial, private host/address, credential, personal media path or raw private state is included.
Artifacts and raw logs remain ignored. Hashes identify only the measured scope, not unperformed Android acceptance.
