# SECTION9 ROUND1 independent R15 critique

## Decision and exact scope

**Overall R15 score: 7/10.** The section substantially implements the intended settings rework, but material dismissal, presentation-lifetime and accessibility gaps remain. This is not accepted Android touch delivery.

- Reviewer: Jcode worker maple, GPT Astra, `openai-oauth:gpt-6-astra`, high. The root confirmed the route and ritual before assignment. No workers were started here.
- Reviewed mobile: `374326fb29580c60c4fe7d95e305d6ac04f78964`.
- Integration parent: `8c9d56e3a9cf27fa540c578751f67f150374f47b`.
- Original writer baseline: `6355074c1d20158c29e749da9fd4c50e0d2bd2a7`.
- Shared engine: inherited gate identifies `0ffd658d7f19e68180c2720e0500b23644619e90`. No shared production source was needed or read.
- Authority: canonical `MOBILE-EXPANSION-PLAN.md:193–207` and critique protocol, `spec/EXPANSION.md:161–169,192–196`, and `docs/plans/mobile-expansion/section-09-settings-contract.md`. The writer handoff and integration-validation receipt were read separately as evidence, not as acceptance authority.
- Read budget: 15 distinct production paths, 27 production revision/path snapshots, plus six contracts/governance/receipts and four focused test files. Total Git-object readset: 37 rows. Repeated reads used these private copies.
- No mutable working-source file was inspected. No product edits, Git mutation, Python, build, test execution, Gradle, Android/JNI/GPU target, device, ADB, GUI, audio, network or service operation occurred.

The canonical score anchor for this result is “mostly works with material gaps.” Source integration and inherited host checks are substantial. The main Compose interaction and accessibility outcomes are not verified, and the finite source counterexamples below prevent a higher finding.

## Largest intent mismatch

Ben asked for dismissal that respects deliberate intent, including reversal. A short upward reversal immediately before release can still be classified as a fast downward flick. The first-to-last velocity average retains earlier downward movement. The new actual adapter wires that owner into Settings, so this is not merely an unused pure-fixture problem.

The main positive result is also important: settings alone has the resistant direct-pointer policy. Six independent inline groups retain the complete authoritative control inventory. The opening host and legacy other-sheet mechanics remain intact by pinned byte comparisons.

## Evidence classes

- **S: source-observed.** Exact pinned source, callpath inspection, byte comparisons and deterministic arithmetic traces. No product code executed here.
- **H: inherited host/integration evidence.** Existing gate788107bhla and writer receipts. Their hashes and read-only result summaries were checked here, but the gate was not rerun.
- **U: unexecuted Android acceptance.** Actual Compose pointer scheduling, touch comfort, ScrollState layout, targets, large fonts, TalkBack, switch access and keyboard workflows.

A passing static comparison is not a passing UI test. A built androidTest APK is not an executed instrumentation suite.

## Prioritized finite defects

### D1. P1: immediate upward reversal can commit a downward flick

**Requirement:** R10. Contract lines26–28 require reversal repayment and prohibit an upward release from qualifying as a downward flick.

**Source:** `ui/SettingsInteraction.kt:29–38,53–90`, `ui/SettingsGestureAdapter.kt:44–55,66–102`, and actual Compose delivery in `ui/SettingsSheetAdapter.kt:138–148,162–177`.

`release()` divides the first-to-last displacement in the retained sample window by elapsed time. It does not check the last nonzero direction. Reversal reduces raw travel but does not invalidate positive historical flick velocity unless raw travel reaches zero.

Representative direct-adapter trace, in dp and milliseconds:

| Event | Physical y/time | Eligible header delta | Raw travel | Result |
| --- | --- | --- | --- | --- |
| Fresh down and Final | 0 / 0 | none | 0 | ticket admitted |
| Initial, header, Final | 80 / 40 | +80 | 80 | sample window contains0@0 and80@40 |
| Initial, header, Final | 70 / 50 | -10 | 70 | upward reversal is repaid |
| Up Initial and Final | 70 / 50 | none | 70 | average velocity is+1400dp/s, so CLOSE |

This is source arithmetic, not an executed fixture. Raw70 exceeds64 but remains below192. The last nonzero motion is upward at1000dp/s. Only the historical positive average makes the flick qualify.

The Compose header detector removes touch slop before delivering its first delta. The same example can use a first physical move of `slop + 80`, with admitted header travel80, then physical reversal10. Raw travel remains70 and the whole-window average remains positive above the threshold. The example does not require assuming that the detector forwards pre-slop travel.

**Attribution:** `SettingsInteraction.kt` is byte-identical to original6355074. This is a pre-existing SECTION9 owner-law defect now connected to the real Settings path, not a claim that this commit changed that predicate.

**Coverage gap:** `SettingsGestureAdapterTest:236–249` reverses at120ms. That ages the earliest sample out and does not cover a prompt reversal inside the positive velocity window. `SettingsInteractionTest:65–74` covers full repayment to zero, not this partial reversal.

**Smallest correction and observable check:** track or derive the latest nonzero eligible release direction and prevent an upward terminal segment from taking the flick branch. Add this finite adapter sequence and require RETURN, while preserving a monotonic qualified downward flick and the192dp slow alternative. Verify the same header reversal in Compose later. Do not raise the global opening velocity.

### D2. P2: the retained presentation owner is below branches that dispose it

**Requirement:** R11 retained expansion/scroll on returning to Settings, without portable tuning side effects.

**Source:** `ui/PhosphorScreen.kt:225–230,267–268`, `ui/SettingsSheetAdapter.kt:180–230`, `MainActivity.kt:83–94,819–826,965–981`, and Settings' explicit ENTER PiP at `ui/Sheets.kt:1208–1211`.

`rememberSettingsPresentationState()` sits after the full-screen-hidden and PiP early returns. It uses `remember(scroll)` for the expanded IDs. The retained `ScrollState` is also created inside that conditional composition lifetime. `SettingsPresentationState.retire()` stores the scroll value on the same soon-discarded owner. No external presentation holder restores those IDs.

**Causal sequence:** expand Beam and Display, scroll, activate ENTER PiP, let the PiP branch compose, then return to the full app and reopen Settings. The old presentation holder has left composition. A new holder starts with only Signal expanded. The old scroll owner is not protected by an enclosing saveable-state holder for this conditional removal. The same loss applies whenever the hidden branch actually composes.

`MainActivity.onStop()` sets `presentationVisible=false`, but this review does not claim every stop necessarily produces that intermediate composition. Lifecycle-paused recomposition may skip it. PiP is the direct visible composition branch establishing the ownership defect.

**Not a blanket rotation defect:** `AndroidManifest.xml:31` handles orientation, screenSize, screenLayout and smallestScreenSize changes. `MainActivity:830–835` handles configuration changes without replacing `setContent`. Ordinary rotation and room-theme changes can retain this owner. Activity recreation beyond that configuration policy is unexecuted and should not be presented as an observed failure.

**Attribution:** the early-return branches are inherited. Placing the new retained Settings holder beneath them is a new integration ownership gap.

**Coverage gap:** `SettingsGestureAdapterTest:222–233,281–296` retains the same pure object and calls cancel. It does not remove and recreate the actual Compose holder. The writer correctly describes return navigation, but that does not close this lifetime boundary.

**Smallest correction and observable check:** retain only the small presentation state above visibility/PiP branching or in an existing screen-level saved holder. Keep hidden content and animation disposed. Expand two groups, record scroll, enter and exit PiP, reopen Settings, and compare IDs and a bounded scroll/header position. No source or tuning value may change.

### D3. P2: expanded tuning sliders are unavailable to non-pointer adjustment

**Requirement:** full R11 meaningful controls and logical TalkBack, switch and keyboard access, not just accessible section headers.

**Source:** `ui/Sheets.kt:524–541,549–591,1140–1142,1167–1183`, `ui/Controls.kt:84–102`, and `ui/Gestures.kt:189–257`.

GAIN, FOCUS, BEAM, GLOW and the two random ranges use `SliderLane`. Its Box installs only a pointer recognizer and drawing. The recognizer returns `pointerInput` only. There is no progress/range semantic, semantic set-progress action, focusable slider, or key adjustment adapter. The neighboring label/value text does not expose an operation that changes the slider. Pointer-free users can expand the group and read values but cannot perform these tuning actions through this control.

The range arm row at `Sheets.kt:564–582` is a clickable drawing rather than a semantic checked control. `ChipCell` uses active state for border/text color but exposes no selected/toggle state (`Controls.kt:389–415`). Literal on/off labels help many toggles, but frame/beam-rate selection relies on color within its option row.

**Attribution:** these are byte-identical inherited child-control seams from6355074, not regressions in `SettingsExpandableSection`. They still prevent full R11 acceptance. The new header has a heading, expansion state, labeled click and conditional body removal.

**Smallest correction and observable check:** add bounded semantic and keyboard adjustment to the shared slider seam while retaining the same authoritative setters. Give each range thumb an identified value/action. Expose checked/selected state where color currently carries it. In the expanded group, navigate and change GAIN and each range endpoint without pointer input, verify the existing adapter receives the bounded value, then collapse and confirm those nodes leave focus.

### D4. P2: inherited child layouts truncate meaningful values at large fonts

**Requirement:** R11 readable large-font layouts and meaningful current-value controls.

**Source:** `ui/Controls.kt:389–415`, `ui/Type.kt:34–49`, and `ui/Sheets.kt:1143–1158,1266–1279,1299–1318,1320–1340,1393–1404`.

Small `ChipCell` fixes its content height at40dp and renders `Mono` with the default single line and ellipsis. Signal puts AUTO-GAIN and VIEW LOCK in one-third-width boxes with a spare third. Motion and transfer controls use similarly bounded rows. At sufficiently large supported font scale, these labels cannot wrap or grow. Their ending on/off values and action distinctions can disappear. Wrapping section summaries does not fix the revealed controls.

**Attribution:** inherited child component and row layouts, retained by this regrouping. The new headers and settings navigation rows explicitly wrap and have48dp minima.

**Target-size boundary:** source also shows FlatKey44dp (`Dimens.kt:16`) and SliderLane44dp (`SliderGeometry.kt:8`), plus the40dp small-chip layout. These are not measured48dp targets. Compose may expand a clickable hit region, so a source height alone is not proof of the final physical hit size. Actual hit/semantic bounds and overlap need measurement. Do not call every40dp layout an observed40dp Android touch target.

**Smallest correction and observable check:** allow settings child labels and value-bearing actions to wrap/grow and switch crowded rows to a narrower-window/large-font arrangement. At a representative narrow portrait width and large font scale, require full AUTO-GAIN/VIEW LOCK state, distinct import/export text and readable rate choices. Measure each resulting target rather than relying on constants.

## U1. Material adapter ordering limit, not a claimed on-device reproduction

The actual nested bridge is `SettingsSheetAdapter.kt:94–117`. Initial pointer admission and Final release live at120–160. The bridge passes a source flag, child-consumed delta and actual `scroll.value == 0`, but no event or gesture ticket accompanies a nested callback. `SettingsGestureAdapter.kt:24–25,47,74–102` stores one mutable per-event budget and clears it at the next Initial or release.

The supported tested delayed case is explicitly “after Final, before the next pointer event” (`SettingsGestureAdapterTest:83–108`). Writer handoff lines143–144 explicitly says nested delivery after pointer-up is rejected. Neither host fixture establishes the real scrollable's delivery order.

Finite ordering schedule to check next:

1. An earlier direct top pull has accumulated raw200dp.
2. A physical upward move of100dp reaches Initial and Final, but its nested pre-scroll reversal callback has not run yet.
3. Up reaches Initial, which resets the budget to zero, then Final commits because raw remains200.
4. The delayed reversal arrives with no ticket/budget and is ignored. The already committed close cannot be reversed.

Similarly, a last downward move that would cross the slow threshold can be discarded if delivered after up. A callback delayed past another Initial can spend the newer budget because the callback itself has no sequence identity.

These are permitted source-level callback schedules, not measured Android schedules. The current source does not prove they occur with the installed Compose runtime. Therefore they are carried as a specific integration risk and coverage gap, not promoted to a reproduced device defect. The next smallest check is a finite production-Compose event-order trace for last move/reversal versus up, followed by a ticketed/drained or safe-cancelled release boundary if that order occurs. Do not turn post-fling into another close decision.

## Complete control inventory and authority map

The private static runner extracted the complete SettingsSheet bodies from original, parent and pin. It compared exact action-member multiplicities and normalized call-expression lines. All three have the same **38 distinct `actions` members and42 references**. The extra four references are the two lock queries, each used three times. The full sorted inventory and expressions are sealed beside this report.

| Group | All authoritative members | Pinned Settings source |
| --- | --- | --- |
| Signal & Startup,4 members/4 refs | `setGainAbsolute`, `setGainAuto`, `setViewLock`, `setRemoteLatencyMode` | `Sheets.kt:1135–1165,1346–1367,1447–1452` |
| Beam & Light,10/10 | `setBeamEnergy`, `tapBeamRandom`, `setBeamRandomRange`, `setGlow`, `tapGlowRandom`, `setGlowRandomRange`, `setGrid`, `setGridData`, `openLight`, `openInstrument` | `1166–1190,1373–1392,1453–1458` |
| Display & HUD,17/21 | `setPauseBlack`, `toggleDisplayPause`, `resetInspection`, `setControlsAlwaysVisible`, `setPipAutoEnter`, `enterPictureInPicture`, `setFloatingHudEnabled`, `setFloatingHudTransparent`, `hideFloatingHud`, `showFloatingHud`, `setDoubleTapPlayback`, `setLingerBackground`, `setFullscreen`, `isScopeRotationLocked`, `setScopeRotationLocked`, `isUiPlacementLocked`, `setUiPlacementLocked` | `1191–1295,1459–1463` |
| Motion & Performance,3/3 | `setFps`, `setOversample`, `setHudMode` | `1296–1345,1464–1468` |
| Appearance,1/1 | `openRoom` | `1368–1372,1469–1472` |
| About & Manual,3/3 | `exportSettings`, `importSettings`, `openManual` | `1393–1438,1473–1478` |

Additional preserved surfaces are not hidden by that count:

- FOCUS uses the existing `onFocus` at1167, supplied as `actions::setFocus` by `PhosphorScreen:822–823`.
- BAND retains `state.bandMode = (state.bandMode + 1) % 3` at1338. It is not counted as an actions member.
- Signal Check retains `SignalCheckEntry(state, p)` at1139. New source navigation sets the existing `state.showSourcePicker` flag at1137, consumed by `PhosphorScreen:269–271`.
- `hudControlStatus`, `floatingHudStatus`, `settingsTransferStatus`, rotation-lock disabled states, linger/microphone-lifetime explanation, reconstruction notes, relay-only explanation, license/manual and conditional calibration stamp all remain present.
- No enabled fake HDR, brightness, startup, mixing or persisted reduced-motion control was added before those owners exist. The six summaries use real current source/gain, beam, pause/HUD/fullscreen, frame/reconstruction, room and transfer state (`Sheets.kt:1447–1475`).

`PhosphorScreen:342–413` still delegates controls to the authoritative ScopeActions. `withSheetRouting:852–865` overrides only navigation. Instrument navigation at819 calls `openInstrumentPresets` then switches sheet. `MainActivity:395–405` separates opening from applying. Tuning setters still enter `instrumentEdit` (`MainActivity:2022–2058,2470–2497`). Settings import still gets the existing workflow ticket and preserves uncertainty/status handling (`1515–1531`). These are bounded preservation checks, not a new acceptance judgment on the full R16 implementation.

Signal Check's actual inline body remains at `SignalCheckSheet.kt:23–28`. Its visibility owner is disposed when the outer Signal body collapses (`41–48`), so regrouping does not leave that collapsed diagnostic content composed. Its explicit source action remains the existing picker flag. This review does not rescore the earlier R17 findings or claim real diagnostic/source acceptance.

## Requirement-to-source/test map

Paths in this table are under `app/src/main/kotlin/dev/phosphor/mobil3/`, unless a test is named. `GA` means `SettingsGestureAdapterTest`, and `IO` means `SettingsInteractionTest` under `app/src/test/kotlin/dev/phosphor/mobil3/ui/`.

| Requirement | Actual source and source result | Focused inherited coverage | Unclosed boundary |
| --- | --- | --- | --- |
| Opening thresholds, velocity and finger tracking unchanged | `PhosphorScreen:297,418–468`, unchanged `Gestures`, `Dimens`, `SheetEntryPolicy`, `StageGesturePolicy`. Opening-host/reveal slices byte-equal to original | `SheetEntryPolicyTest`, `StageGesturePolicyTest`, GA21–30 opening finger | Real opening in all applied frames, unchanged comfort |
| Fresh direct pointer admission | `SettingsSheetAdapter:131–145`, `SettingsGestureAdapter:19–53` require fresh single touch/stylus, reject existing finger, wrong ID/type/time | GA21–39,63–69,154–170,269–279. IO7–29 | Actual Compose pointer/cancel delivery |
| Child consumption and top-only remainder | `Sheets:469,1131,1444`, `SettingsSheetAdapter:94–110`, `SettingsGestureAdapter:57–83` bound physical budget and subtract child consumption | GA41–108, duplicate-budget and delayed-before-next-event cases | U1. Child fixture omits actual Compose delivery |
| No fling/programmatic/slider/expansion dismissal | Post-fling returns NONE. Non-UserInput cannot admit. Actual horizontal slider consumes through `Gestures:219–248`. Expansion mutates presentation only | GA32–108,172–198 and IO15–29,76–86 | Real top fling and child pass ordering. U1 |
| Slow/fast intentional travel and resistance | `SettingsInteraction:17,53–90,114–120`:192dp slow,64dp plus920dp/s fast,160dp resistant scale | IO31–63,125–139. GA92–119 | D1 and no device calibration |
| Reversal, cancellation, multi-pointer, lost identity | `SettingsGestureAdapter:26–42,66–68,108–116`, `SettingsSheetAdapter:157–170`, geometry/source pointer key in `Sheets:361–373` | IO65–123. GA122–198,236–279 | D1, U1, actual lifecycle/cancel timing |
| Firm return, reduced motion, committed exit | `SettingsSheetAdapter:58–92` uses160ms tween or snap. `Sheets:300–307,309–310,422–423` captures exit and offset once | Pure commit/cancel fixtures and inherited legacy commitment tests | Actual animation, reduced-motion presentation and callback cancellation |
| Close, scrim, Back and other sheets | `Sheets:261,295,301,355–359,366,379,480–511`: only Settings supplies scroll policy. Legacy nested/settle/header/animation slices byte-equal | `SheetEntryPolicyTest:90–164` and commitment/animation cases. Static slice checks | Actual source/light/instrument/manual dismissal matrix |
| All38 actions42 refs and existing surfaces | Exact inventory above. Body expression/count comparison passes against both baselines | Writer inventory receipt, repeated independent static check | Full click-through and action observations remain U |
| Six groups, multiple expansion, no setting mutation | `SettingsInteraction:124–154`, `SettingsSheetAdapter:199–202,250–289`, `Sheets:1447–1478` | IO141–175. GA200–233,281–296 | Actual interaction/focus, not pure objects only |
| Meaningful glyph/header/live summary/chevron | `SettingsSheetAdapter:263–283`, `Sheets:1447–1475` use current state and conditional bodies | Source inspection and six-declaration check | Contrast, visual distinction and screen measurements |
| Return, theme, rotation and scroll retention | Screen-owned holder survives ordinary sheet switch and theme. Manifest handles ordinary orientation | Pure owner retention fixtures | D2. Actual rotation/theme scroll/focus unexecuted |
| Anchored touched header, stale work fenced | `SettingsSheetAdapter:191–248` records actual coordinates, waits one frame, validates revision, applies synchronous bounded raw delta. Pointer/nested input and disposal cancel request | GA200–233,281–296. IO153–175 | Actual placement coordinates, maxScroll clamping, header position and frame timing |
| Collapsed controls leave layout/focus | `SettingsSheetAdapter:284–287` conditionally composes body, not alpha hiding | Source structural inspection | Real TalkBack/switch/keyboard tree and collapse focus behavior |
| Accessible child adjustment and selected states | Headers have heading/state/button labels. Actual child paths inspected | No focused accessibility execution in38 host cases | D3 |
| At least48dp targets and large-font layouts | New headers/navigation/close have48dp minimum/size and wrapped labels. Child seams remain fixed40/44 and one-line | Source dimensions only | D4. Actual hit bounds may differ from layout bounds |
| Lifecycle/performance/privacy | No new source/persistence/network owner. Return is finite. Collapsed bodies removed. Anchor application is bounded and fenced | Host ownership and source checks | D2. No measured frame/jank, lifecycle interaction or contrast acceptance |
| R15 and delivery evidence | This independent full-section critique identifies exact pin, one score and finite follow-up | Hash-checked inherited gate, source manifests and sealed review inputs | Does not waive device evidence or end sub8 corrective rounds |

This map covers every SECTION9 bullet and the detailed contract seams. Coverage records an honest open result where execution is absent. Aggregate test counts do not close those rows.

## Checks performed in this review

### Independent static and hash verification

`static-audit.sh` ran successfully at13:27UTC. Its log records:

- Same38 member names,42 references and call-expression lines across original, parent and pin.
- Retained status, diagnostics, focus, band and disabled-state expressions.
- Byte-equal legacy nested, settle, header, sheet-animation and reveal-geometry slices.
- Byte-equal opening host, and unchanged SettingsInteraction, entry/stage policies, Controls, Type, Dimens, SliderGeometry and Gestures against original.
- Exactly one declaration for each of the six stable section IDs.
- Parent-to-pin diff whitespace check passed.
- Every snapshot SHA matched both its recorded Git blob identity and a fresh read of that pinned object. All37 readset rows passed.

These commands execute shell read/comparison/hash work only. The arithmetic counterexample D1 was inspected manually, not run as Kotlin or as a copied implementation.

### Inherited gate verification, not rerun

`read-receipts.sh` ran successfully at13:27UTC. It copied only existing receipt text, verified the retained artifact inventory and read existing result logs/XML. Gate runner SHA matched the pinned receipt:

`c78684c435976b57dce6f4241e7574ecd5e3d8e543cccf2e5064898a2a3443f3`

Gate788107bhla ran13:06:28–13:08:02UTC on parent8c9d56e plus the seven released settings paths. Its existing result envelope has zero gate exits and explicitly says `installed:false` and `android_device_acceptance:false`.

- Retained XML readback:711 JVM tests,61 suites, zero failures/errors.
- Existing native log:126 passed.
- Existing offscreen GPU log:3 retained-frame API tests passed. These are not Android compositor results.
- Existing Gradle log: successful Android/Kotlin/JNI compilation, unit/lint/engine gate, release helper build, app APK and androidTest APK built together.
- Source boundary result succeeded.
- Writer38 host cases consist of15 IO cases and23 GA cases. They are included in711, not added again.
- Retained app APK, androidTest APK, JVM archive, mobile/shared manifests and result-envelope hashes passed verification.
- Existing mobile before/after source manifests match byte-for-byte. Existing shared before/after manifests also match. No mutable current tree was hashed for these assertions.
- All19 inspected pinned app runtime/test files match their entries in the inherited mobile gate source manifest. This directly ties the reviewed application inputs to that receipt.

## Blocked Android acceptance and disposition

**Blocked:** real Android/Compose interaction and target-device calibration were unavailable and explicitly prohibited in this review. The root reports no current ASUS connection. S25 is excluded. No device checks were requested or performed.

**Evidence:** pinned integration-validation lines45–51 retain those gaps. The gate result explicitly denies installation and device acceptance. The focused host tests call pure owners/adapters rather than actual Compose pointer and layout machinery.

**Best current result:** complete source-only SECTION9 critique, verified preserved control inventory and opening/legacy mechanics, hash-checked inherited compilation/host receipts, four finite source defects and one explicit ordering risk. No compilation result was converted into touch acceptance.

**Next smallest work for the coordinator:** address D1 with the finite immediate-reversal adapter sequence, retain presentation above PiP/hidden disposal for D2, and close the bounded settings child-accessibility/layout seams D3/D4. Add the finite delayed-reversal/up ordering check before another review claims the Compose boundary is closed. Device touch/anchor/accessibility/calibration remains deferred until authorized and available. This report does not ask Ben to connect or use a device.

This is round1. The result is below8, so canonical R15 calls for correction and checks before the next critique, up to four total rounds. Existing R16/R17 critiques and later root appearance work are neither overwritten nor rescored here.

## Artifact custody and release

Private directory:

`/home/ben/.jcode/scratch/section9-round1-374326f-20260908T1319/`

- `REPORT.md`: this review.
- `readset.tsv`: exact commit, Git blob, SHA256 and path for all pinned product/contract/test snapshots.
- `external-readset.tsv`: copied inherited receipt inputs and their source hashes. `receipts/retained.sha256` also identifies the hash-only APK and JVM archive inputs.
- `objects/`: unmodified private Git-object snapshots. A readset row means the file was captured and used for the stated scoped inspection/comparison, not that every unrelated line was audited.
- `read-object.sh`: exact readset capture runner.
- `static-audit.sh`, `static-audit.log`: source inventory/preservation and object/hash checks.
- `read-receipts.sh`, `receipt-verification.log`, `receipts/`: inherited-receipt capture and read/hash validation.
- `SHA256SUMS`, `seal-verification.log`: sealed-artifact verification. The manifest excludes itself and its verification log to avoid a circular hash.

Writer artifacts, earlier reviews, current source and Git state were not changed. The seal makes private artifacts read-only and preserves exact hashes. All source/read holds are explicitly released after sealing. No further source access or artifact edits are required from this worker.
