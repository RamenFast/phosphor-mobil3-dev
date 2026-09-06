# Phase 12 supplement: system rotation authority and sheet continuity

Date: 2026-09-06 UTC. Refs #4 and #5.

**Status: reviewed source and frozen host validation only. Physical Android acceptance remains OPEN.**

This supplement follows the protected build request lines 19-21 and current EXPERIENCE sections 4-5. It does not close B17 or the complete B1-B21 matrix. Mobile baseline is `859c674b59ebe8655ac3965eb202baf6e8d2e841`. Shared dependency remains `aa09b8e14f8b8912b125e3312b4ddeec09404089`, unchanged.

## Requirement, implementation and evidence

| Requirement | Production owner and check | Limit |
|---|---|---|
| Respect Android rotation authority without replacing saved preferences | MainActivity reads authority before rotation mutations and stale setters. ScopeUiState separates saved choices from applied presentation. Existing lifecycle and 500ms tick refresh authority. Dependent chips are disabled. Pure policies and labeled source-only caller checks cover hold, import and unlock. | Actual OS setting delivery, recreation and device rotation remain unmeasured. Unknown-authority copy still describes lock-on rather than read failure. |
| Preserve heartbeat and physical cardinal signs | System volume remains the first unconditional tick publication. Rotation refresh follows it. RotationDetent uses one Surface/sensor conversion with literal aligned C0/D0, C270/D1, C180/D2 and C90/D3 expectations. | Native local view-rotation wiring is inspected. Physical beam orientation is not established by arithmetic tests. |
| Use actual applied frames for geometry | Insets maps physical safe/corner edges using the total applied quadrant. Odd frames exchange gutter axes before corner math. Popout uses its explicit physical frame without a second permutation. | Numeric asymmetric fixtures and source linkage do not replace Android notch, RTL and short-window measurements. |
| Keep a retained first-pull host current | PhosphorScreen retains the pull host and reads updated applied state when a pull begins. A real PullRevealState test exercises immediate drag, provisional/actual measurement and release. | This is host state execution, not Compose pointer dispatch or recomposition proof. |
| Preserve the intended entry edge | UI-locked landscape retains viewer-right entry, including absolute right under RTL. The inherited upside-down portrait mismatch uses a top-local anchor and negative-local entry, placing it at the physical console edge. | Literal policies and source wiring are verified. Physical rendering and gesture acceptance remain open. |
| Continue a tracked dismissal without turning sideways or moving its base | SheetHost latches the first exit direction. Actual SheetDismissState captures the currently applied Animatable value at commitment. The Column reads this immutable committed displacement. State and nested callbacks reject later writes, queued operations recheck ownership, and the existing scope schedules restoration stop. | Immediate protection comes from the immutable getter. Android scheduling of the stop and real late-pointer delivery remain unmeasured. |

No native/JNI, shared renderer, sensor-filter tolerance, dependency or new production filename was changed by this supplement. EXPERIENCE folds the required behavior back into the source of truth.

## Correction history

The first eight-path gate failed one preserved heartbeat assertion among 398 JVM cases. Independent source review confirmed the refresh had been inserted before volume publication. It was relocated without weakening the original assertion.

Correction02 passed 409 JVM cases. Reviews then established two inherited SheetHost defects: upside-down portrait entered from the opposite physical edge, and local-Y tracking turned to local-X on committed dismissal. The raw landscape requirement explicitly preserves viewer-right entry. Earlier broader reviewer wording and subsequent clarification remain immutable.

Correction03 passed 415 JVM cases but still allowed late end/cancel and queued or active restoration to move the base during an already committed exit. One reviewer found this source-first. The other independently confirmed the coordinator-supplied partial-drag, Back, late-cancel sequence. Passing policy tests had not protected the actual displacement owner.

Correction04 changed only Sheets.kt, SheetEntryPolicyTest.kt and EXPERIENCE section 4 from that snapshot. It tests the actual dismissal owner rather than a copied model. Four new behavioral methods cover all entry enums, late end/cancel/reversal/fling, queued begin/snap/restore, raw-versus-applied position, precommit restore, first-wins commitment, and a real Animatable stopped at an intermediate restoration value. A fifth source-only method binds the rendered Host offset and callbacks to that owner. The synthetic dispatcher and BroadcastFrameClock do not constitute Android execution.

All original failure logs, blocked reviews and before copies remain retained. The correction03 evidence collector initially treated native-format.log as a test log and stopped before writing a receipt. Its corrected collector named six actual native test logs. That collection error did not change the successful gate. An older freeze receipt had a hash transcription error, recorded in a separate note rather than silently changed.

## Final frozen gate

Root task 432126sk8a completed at11:20:58 UTC, exit0 after226.6s.

- 420 actual JVM testcases across 34 suites, zero failures, errors or skips. All five new dismissal cases passed.
- 50 selected native tests: engine 27, terminal 9, activation 4, close 1, remote media 8 and watchdog 1.
- 26 shared beam/CPU/GPU tests, including offscreen readback without an adapter skip.
- Shared consumer compilation and scoped renderer Clippy passed. Native format, protected archive 3, Android packaging/checkEngine and source privacy checks passed.
- Lint retained 11 warnings and zero errors. These are not claimed as warning fixes.
- All 829 full input rows rehashed. Before and after inventories were byte-identical. All 13 reviewed source/test/spec paths matched their frozen candidate. Shared HEAD and tree were unchanged.

Evidence lives under ignored `dev/scratch/pre-v2-20260829T072841Z/phase-12/rotation/`:

| Evidence | SHA256 |
|---|---|
| `root-correction04-candidate.sha256`,13 paths | `39fe85cf55f8fdf53a4b7f531d70599227bb7c2f28135f43b3c908de0750a5ad` |
| `root-correction04-pre-gate.md` | `df559a9e4d34c459f6f99c88a6281aefe08733c820fefb496a5186804a7b16f3` |
| `root-attempt04-evidence.sha256`,69 files | `4c617ecef60888abbbcbe7d29fe679283933244ac0526c909bee8c7f913de352` |
| `root-attempt04-result.md` | `06742d79ce82dda6c639faf87e47129f41a942da9ac4d69ad279c8f5ed59e65b` |
| SOURCE checkpoint `review-source04-astra-stage-a.md` | `dde4cfa8762bf5f1281d27dcde81cc8929eeebafd03eff974e0dbcaea23f8140` |
| Intent checkpoint `review-intent-correction04-astra-source.md` | `2e9856be3595d391dffd53063783d12a5e2d681de943433f980ed67a69ccf3c1` |

Independent SOURCE final `review-source04-astra.md` has SHA256 `fe1cbc0e8e50c76b2a87cce07b239e2cb21e011c33fd9618f234f94bbe6e8f5f`. Independent intent final `review-intent-correction04-astra-final.md` has SHA256 `9f314f8841b4bd7224454de91eb9d45b5d6b94e6b9065785acd21a922fde159f`. Both support bounded source/host closure of the committed-displacement defect. Both independently parsed the retained execution and rehashed all 829 current inputs. Source checkpoints preceded the explicit terminal evidence release. These are bounded addenda to prior independent assessments, not newly pristine whole-phase reviews. Earlier incidental ordering disclosures remain intact.

The full gate ran before this receipt and the settings-runtime append were integrated. All 13 reviewed source/test/spec bytes were reverified before and after that documentation-only step. This receipt is not evidence that the subsequently committed documentation ran through the earlier gate.

## Remaining device acceptance and recovery

This source freeze has not been installed or exercised on the S25. The separate settings receipt records partial pre-rotation emulator defaults and PiP observations, with native rendering blocked. The latest read-only S25 check at11:31 UTC found another app foreground, so no install, launch, input or settings change was made.

Keep actual Android OS hold, stale setter/import/unlock, all cardinal poses, local beam sign, safe corners/RTL, first-pull handoff and late-callback dismissal acceptance open. Also retain separate recreation/retired sensor-listener, natural-landscape, remote-geometry view-rotation bypass, header release-velocity and cancellation-threshold limits. No source or host result closes these platform boundaries.

Build the exact committed candidate before device activation. Keep the exact pre-rotation settings APK SHA256 `1323fa5e178ffe729bde65131fafc0575f4b2ec109b73103b0085647e17bb71d` and private current preference backup. Revert by a new scoped commit if required. Never reset shared history, clear the physical app, replace newer user tuning with an old backup, or uninstall the production package.

## Exact committed artifact, 2026-09-06T11:38Z

The reviewed implementation is committed as `bf872f21c4dcb91f7631b804777025d8ac471885`. The shared dependency remains `aa09b8e14f8b8912b125e3312b4ddeec09404089`. Root proved all 13 reviewed blobs match that mobile commit before building. The isolated build selected every regular tracked blob from the exact commits: 366 mobile and 459 shared files. No working-tree export or active-branch rewrite was used.

Artifact input manifest SHA256: `59ff1f938a70e06ead442a15a6ecce4d6249d49db20fdaf439d53c2b84ba1f16`.
Exact APK SHA256: `7489ca8762953f62c90e95d773bd12bf141a0a9fb9f20ed01cde2f62ff30bbe6`.
Package: `dev.phosphor.mobil3.debug`, version `2.0.0-debug`, code `2000000`.
Signer SHA256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.

Task5590414d9y completed successfully after86.1s. The exact clean snapshot rebuilt 420 JVM testcases across34 suites with zero failures, errors or skips. Lint, assembly and checkEngine succeeded. Source inventories remained unchanged. The packaged boundary returned status ok with11 source checks,5 artifact checks and1 trusted runtime exemption. Both scope gates were green with zero skips. That filtered scope result is not the complete release scoreboard.

The six-entry artifact manifest has SHA256 `ad9208c5e13cdfef4603ee215a8b150d93828f1e936e252300a511f295fe0a1c`. Root reverified every entry, APK signer and current source identities. Evidence and APK remain in the ignored `phase-12/candidate-bf872f21c4dc-isolated/` directory. This addendum is documentation after the build, not part of the built implementation commit.

The APK is not installed on the S25. Installed readback, settings preservation and physical Android acceptance remain open. The pre-rotation rollback artifact and private backups remain retained.
