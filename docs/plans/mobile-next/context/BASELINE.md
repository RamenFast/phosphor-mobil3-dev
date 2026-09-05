# Baseline and evidence ledger

Snapshot: 2026-09-05, after B2 offline delivery and before new roadmap implementation.
A source PASS, a built APK and a real device PASS are different facts.

This is the historical planning snapshot, not live progress. B2 later passed five exact-artifact phone cycles and remains installed.
Use [current repairs](CURRENT-REPAIRS.md) for the next action and its linked live receipt. The original artifact identities below remain unchanged.

## Repositories and artifacts

| Item | Pinned fact |
|---|---|
| Mobile root | `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3` |
| Mobile behavior commit | `47ff7cdc71c3b8e4315d265844fdfbfcfeb2136c` |
| Mobile branch | `release/phosphor-2.0.0` |
| Private repository | `RamenFast/phosphor-mobil3-dev` |
| Shared engine root | `/home/ben/Dev/ClaudeWorkspace/phosphor` |
| Shared engine commit | `2a45b0f4d05696efe98970f51ac5358c052b565f` |
| Shared public repository | `RamenFast/phosphor` |
| Debug package | `dev.phosphor.mobil3.debug` |
| Retained B2 version code/name | `2000000`, `2.0.0-debug`, checked offline with apkanalyzer |
| Production package | `dev.phosphor.mobil3` |
| Android baseline | min SDK 29, target/compile SDK 36 |
| Real acceptance device | Galaxy S25, Android 16. Discover and explicitly pin its serial at execution, never take the first device. |

Retained B2 debug APK:

`dev/scratch/pre-v2-20260829T072841Z/phase-04/candidate-47ff7cdc71c3/phase-04-47ff7cdc71c3.apk`

SHA-256: `ad9a726eb510d3c5ecd066af7218fbc95fdbbef137acc7603ed5771118d42993`.
Debug signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
It passed clean-source build and packaged-boundary checks. It is not installed.
Do not replace it with a build from a later documentation commit without recording a new identity.

The Phase 3 rollback APK hash is `98523b2eadd1ea2807ccd9f6ebcfc0850d6d3ec430c8d744ad330c7ca2d8be0a`.
The accepted Phase 3 device candidate was source `9bf8527`.

## Observed checks, not future promises

- B2 targeted policy tests: 15 passed with zero failures, errors or skips.
- Full Android unit suite: 177 passed across 19 suites with zero failures, errors or skips.
- Android debug lint: zero errors and 15 warnings. This is not warning-free.
- `checkEngine` and clean `assembleDebug`: passed.
- Mobile Rust tests: 39 passed. Shared audio tests: 27 passed. Relay tests: 26 passed.
- Mobile and relay strict formatting/Clippy gates: passed.
- CLI fixtures, boundary fixtures, source privacy/scope checks and release-rejection fixtures: passed.
- Retained debug artifact: 11 source checks and 5 packaged-artifact checks passed.
- Protected archive hashes and root gate before/after source manifests matched.

No Android instrumentation test files were present in the tracked `app/src/androidTest` surface at this snapshot.
`app/src/debug/kotlin/dev/phosphor/mobil3/SelfTestReceiver.kt` is an existing debug-only seam.
Future instrumentation names in this roadmap are deliverables, not evidence of current coverage.

## Acceptance state

| Work | True state |
|---|---|
| B9 local seek repair | Phase 2 live rerun passed. Final regression remains open. |
| B4 recursive local folders | Phase 3 required live acceptance passed in receipt commit `2401239`. Final regression remains open. |
| B4 oversized provider stress | A separate 20,001-file fixture triggered Android storage-provider ANR. Recovery succeeded. Arbitrary-size folder acceptance was not claimed. |
| B2 capture-to-mic | Implementation, corrected independent source review, independent intent review and clean retained APK passed. Five real candidate cycles and installation remain pending. |
| B1-B21 overall | Incomplete. Keep `spec-version: pre-v2-b1-b21` and `drift: 21`. |
| Release readiness | Not claimed. Signing, final provenance and release bundle remain external gates. |

Do not use older conversation-memory summaries that call B4 device acceptance pending unlock.
The later committed Phase 3 receipt is authoritative for that observation.

## Repair lessons that motivate the roadmap

The initial B2 source review reproduced a newest-request stall despite passing policy tests.
Overlapping STOP callbacks could publish the latest idle first and then overwrite its retained snapshot with an obsolete idle.
A stopped Activity receiver missed the good broadcast and reread only the stale snapshot.
The corrected service fences correlated publication with the latest STOP token while preserving every stop reply.
Independent compiled-policy reproduction verified the correction, but did not instantiate Android services.

Legacy session callbacks can still publish a capture-labelled live face while mic handoff is pending.
Some release errors leave a pending mic policy until retry or a newer source selection.
The existing mic reader loop does not report later nonpositive reads or reader death to UI.
These findings motivate future integrated lifecycle tests and one deliberate publication contract.
They are not permission to widen the current B2 phase or claim its device acceptance.

## Durable evidence entry points

- `docs/dev/receipts/pre-v2-b1-b21/phase-02-b9-seek-stress.md`.
- `docs/dev/receipts/pre-v2-b1-b21/phase-03-b4-folder-tree.md`.
- `docs/dev/receipts/pre-v2-b1-b21/phase-04-b2-mic.md`.
- `docs/SERIOUS-TODOS.md`, explicit uncertainty and deferred structural work.
- `PRE-V2-B1-B21-EXECUTION-PLAN.md`, inherited execution order and repair gates.

Private raw logs, screenshots, preference archives and addresses stay under ignored scratch.
Public sibling commits and issues receive sanitized findings only.
