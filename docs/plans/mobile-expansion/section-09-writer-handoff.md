# Section9 settings source handoff

## Outcome and custody

This bounded work implements R10 and R11 in the actual mobile settings UI. The writer
read the governance, skills, canonical section9 and complete settings contract before
editing. Concrete adapter decisions entered the contract before runtime changes.

The implementation baseline is `6355074c1d20158c29e749da9fd4c50e0d2bd2a7`.
The root advanced its separate recovery work to `914f58529b4297379baf21f97015d34279eb0466`
during this lane. The writer did not modify those recovery files or perform Git mutation.
The released source archive contains baseline6355074 plus only this lane's seven paths.
It deliberately excludes the root's concurrent changes. Integrate these paths into the
root's current tree, not the whole baseline archive over newer recovery work.

The source result is integrated into `SettingsSheet`, `SheetHost` and `PhosphorScreen`.
It is not an Android build, device acceptance, threshold calibration or release APK claim.
The explicit swarm completion report releases all writer ownership and the build hold.
No further edits follow that report.

## Concrete runtime seams

- `SheetHost.settingsScroll == null` keeps the original policy for every other sheet.
  The original72dp settle, nested-scroll behavior, header detector and entry/exit
  animations remain present. Settings alone supplies its retained scroll owner.
- `SettingsGestureAdapter` is the actual host-testable pointer adapter used by Compose.
  An Initial-pass observer outside the translated card creates a fresh single-pointer
  ticket. Observation does not consume child input. The header detector consumes only
  admitted header motion. Content contributes only direct top-edge nested remainder.
- Each move has a bounded physical-motion budget. The adapter subtracts child-consumed
  travel. A delayed nested callback can use the latest remaining budget before the next
  pointer event. The Final pass owns release. Post-fling cannot commit or close again.
- The unchanged pure `SettingsDismissOwner` provides192dp slow travel,64dp minimum
  flick travel,920dp/s downward velocity and160dp progressive resistance. These remain
  provisional settings-only values, not device-calibrated UX evidence.
- Header cancellation, additional pointers, lost pointer identity, source-key changes,
  geometry changes and retirement cancel. A below-threshold return uses a finite160ms
  tween with no overshoot. Reduced motion snaps. Explicit close captures one exit edge.
- `SettingsPresentationState` and its actual `ScrollState` live beside the screen's
  sheet switch. The enum IDs and multiple expanded sections survive return navigation.
  The state is presentation-only. It does not enter tuning, imports or preset storage.
- Expansion removes or inserts the body directly. One placement receipt schedules one
  frame-delayed anchor correction. Request identity and revision are checked immediately
  before synchronous `dispatchRawDelta`. New pointer/nested-scroll input, a newer toggle,
  configuration change or disposal invalidates it. No suspended scroll can resume later.
- Six headers use one vertical order in both orientations. Headers, settings navigation
  rows and the settings close target have a48dp minimum. Headers wrap labels/summaries,
  expose heading and expanded/collapsed state, and hide decorative chevron semantics.
  Existing child controls keep their original implementations and action ownership.

## Control inventory and authoritative actions

The before/after source check found the same38 `actions` member names and42 references.
It also compared the normalized action-call expression lines, not only labels or counts.
The complete original-to-new inventory remains in `section-09-settings-contract.md`.

| New group | Retained controls and actions |
| --- | --- |
| Signal & Startup | GAIN `setGainAbsolute`, AUTO-GAIN `setGainAuto`, VIEW LOCK `setViewLock`, relay latency `setRemoteLatencyMode`. The actual `SignalCheckEntry(state, p)` stays intact. A source row uses existing `state.showSourcePicker` navigation. |
| Beam & Light | FOCUS through existing `onFocus` and `actions::setFocus`, BEAM and die/range actions, GLOW and die/range actions, GRID and GRID DATA. LIGHT uses `openLight`. INSTRUMENT PRESETS uses `openInstrument` and the unchanged screen routing. |
| Display & HUD | HOLD/BLACK, display-only pause/live and inspection reset. Controls visibility, double-tap playback, PiP preference and entry, floating HUD preference/background/show/hide/status, linger explanation, fullscreen, scope rotation and UI placement. Android rotation-lock disabled states remain. |
| Motion & Performance | FRAME RATE, BEAM RATE and both explanations. In-app STATS HUD uses `setHudMode`. BAND retains its existing bounded `state.bandMode` transition. `hudControlStatus` remains visible. Reconstruction is not called capture negotiation. |
| Appearance | Current room summary and `openRoom`. No theme identities or appearance owners change. |
| About & Manual | Existing `importSettings` and `exportSettings`, multiline transfer status, manual through `openManual`, license/about text and conditional calibration/designator stamp. Portable preset import remains separate. |

No enabled HDR, brightness, startup or mixing control was invented. The code continues
to use the existing `reduced` input without inventing a persisted reduced-motion setting.
R16 recovery, stale-import and modified-state owners are not bypassed. R17 diagnostic
content and its source navigation remain owned by their original code.

## Checks actually run

Final production host run observed at13:00UTC on2026-09-08:

- `SettingsInteractionTest`: all15 existing cases pass, without modifying its source.
- `SettingsGestureAdapterTest`: all23 new cases pass. Total: **38 tests pass**.
- The cached Kotlin2.4.10 compiler compiled the actual pure production files directly.
  JUnit4.13.2 ran on OpenJDK21.0.11. No copied production owner, fabricated adapter,
  Android stub or native substitute was used.
- `check-inventory.sh` passed action-call counts, action expression comparisons, required
  status/error/disabled surfaces and all six stable section declarations.
- The same check compared legacy nested, settle and header source slices byte-for-byte.
  Sheet animation and reveal-geometry source slices also match the baseline.
- `SettingsInteraction.kt`, `SheetEntryPolicy.kt` and `StageGesturePolicy.kt` are unchanged.
  `PhosphorScreen.kt` changes only retained state, its SettingsSheet argument and a stale
  two-column comment. Opening gesture hosts, thresholds, flick law and reveal code are untouched.
- Owned tracked paths pass `git diff --check`.

The first host run passed33 cases. Delayed nested delivery then received explicit sampling
and child-budget coverage. The second run passed34 cases. Four final cancellation,
velocity and all-six retention cases brought the final run to38 passing cases.
These are finite host adapter fixtures, not Compose pointer delivery measurements.

## Requirement-to-evidence map

| Requirement | Evidence | Remaining gap |
| --- | --- | --- |
| Fresh direct pointer only | Existing-finger, child-consumed slider, no-motion, non-top, non-user and multipointer fixtures | Android pointer pass and cancellation delivery |
| Deliberate release with resistance | Threshold, measured-header-flick, delayed-remainder, reversal and resistance fixtures | Actual touch comfort and calibration |
| Cancel and retire without stale close | Cancel/source/geometry seam, lost-pointer, non-finite input, retirement and fling-order fixtures | Real geometry/source change while dragging |
| Stable presentation and anchor | Multiple/all-six expansion, single-use layout, replacement, cancellation and clamped correction fixtures | Actual ScrollState layout positions after expansion and return |
| Complete action inventory | Same38 names,42 references and action expressions, plus focus/band/diagnostics/status checks | Full UI click-through against root's latest owners |
| Opening and other sheets preserved | Byte-equal policy/animation/reveal slices and minimal screen diff | Existing Compose-dependent entry/stage suites and touch matrix |
| Accessible flat headers | Source inspection of48dp target, heading/state semantics, wrapped text and conditional bodies | Measured targets, large fonts, TalkBack, switch and keyboard navigation |
| Delivery | Seven-path hash manifest, immutable source/evidence archives and exact command/dependency receipts | Root's full build, independent critique and authorized device acceptance |

## Evidence location and reproduction

All receipts live under:

`/home/ben/.jcode/scratch/section09-settings-20260908T1237Z/`

- `run-pure-tests.sh`: exact compiler/JUnit command and direct production inputs.
- `dependencies.sha256`: absolute cached dependency paths and hashes.
- `pure-tests-final.log`: final command trace and38-test result.
- `check-inventory.sh` and `inventory-final.log`: source comparison commands and results.
- `settings-body-before.kt.txt`, `settings-body-after.kt.txt`, action-expression/count
  files and preservation slices: the concrete before/after audit inputs.
- `released/CHANGED-SHA256SUMS`: exact seven changed paths, including this handoff.
- `released/TEST-INPUT-SHA256SUMS`: exact production and test inputs for the host run.
- `released/section09-source-on-6355074.tar.gz`: immutable baseline plus this lane's source.
- `released/section09-evidence.tar.gz`: commands, dependencies, logs, snapshots and report.
- `released/ARCHIVE-SHA256SUMS` and `released/RELEASE.txt`: archive integrity and custody.

The release command marks its artifacts read-only. SHA-256 manifests, not a mutable branch
name, identify the delivered source. The root may commit and build after explicit release.

## Blocked acceptance and smallest root follow-up

**Blocked:** Android/Compose type-check, full Gradle/JNI/native gates and real touch execution
are outside this writer's explicit authority. No Android target or device was executed.
The complete `SheetEntryPolicyTest` and `StageGesturePolicyTest` suites were not run here.
They depend on actual Compose or the Android-hosted `SheetDismissState` source. PSI or
source inspection is not a substitute, and no Android compilation success is inferred.

**Best current result:** integrated settings source, complete preserved action inventory,
and38 passing tests of the actual pure production owners and adapters.

**Next step:** after the explicit release, the root runs its prepared full gate on the
combined current tree and obtains independent section9 critique. The root then runs the
real opening, header/top-edge dismissal, slider, fling, multipointer, return navigation,
rotation, reduced-motion and accessibility matrix on the authorized device when available.
Nested delivery after pointer-up is rejected by design. The device matrix must confirm
that real Compose delivery still permits intended slow pulls and qualified flicks.
The S25 remains untouched by this workstream.
