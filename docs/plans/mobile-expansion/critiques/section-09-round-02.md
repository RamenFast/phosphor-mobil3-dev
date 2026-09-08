# SECTION9 round2 independent R15 critique

## Decision

**Overall R15 score: 7/10.** This is a full SECTION9 review at the corrected pin, not a rescore or replacement of round1. The corrections materially improve the implementation. A remaining primary-action keyboard-focus defect and the unclosed production callback-order boundary keep this in the canonical “mostly works with material gaps” category. The score is not an arithmetic tally of repaired defects.

The largest remaining intent mismatch is that the source still cannot establish that the final direct top-edge movement is accounted for before deciding dismissal. U1 below is a supported source schedule and an explicitly unrun Android integration risk, not an observed accidental close. Separately, actual primary Settings actions have no rendered keyboard-focus cue in their pinned modifier path.

### Identity and boundaries

- Reviewed mobile: `a38f39ef9b47c4c9fdb52a7930d35766dea022a4`.
- Compared original mobile: `374326fb29580c60c4fe7d95e305d6ac04f78964`.
- Shared engine: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Correction01: `4a0e8b667690da798ddbfb0928a252992c164488`.
- Correction02: `f9a110660c7a6ad1e9b15cf4bc7637d0d219fb96`.
- Reviewer: independent Jcode worker assigned GPT Astra, `openai-oauth:gpt-6-astra`, high. The coordinator reports the approved route and effort verified at13:35UTC. The session ritual was already complete. This worker neither repeated it nor started workers.
- Authority: binding workspace governance, `ben-context-standards`, canonical `MOBILE-EXPANSION-PLAN.md:193–207` and R15 critique protocol, `spec/EXPANSION.md:161–169,192–196`, section09 contract, corrections01/02, and immutable round1 critique. Receipts support evidence, not acceptance authority.
- Readset: **21 distinct production paths**, 41 production revision/path snapshots, six focused test files and ten contracts/governance/receipts. `readset.tsv` contains all57 Git-object rows with revision, blob, SHA256 and path. Reading or comparing a complete snapshot does not mean unrelated lines received a full audit.
- Root parrot retains implementation, integration, build, device, Git, retention and cleanup ownership. This worker has none of those roles.

No mutable production source was read. No source edit, Git mutation, test, build, Gradle, Cargo, Android, JNI, GPU, device, ADB, GUI, audio, network, service, Python or worker execution occurred. No target or review runner was executed. Direct shell work was limited to Git-object/text/archive reads, private text copies, comparisons and hashes.

## Evidence classes

- **S: independently observed source/static evidence.** Pinned object reads, actual Compose caller/modifier inspection, byte comparisons and manual causal reasoning.
- **H: inherited host/integration execution.** Existing gate1246216zt4, its runner, retained artifacts and XML/logs were read and hash-checked. They were not rerun.
- **U: unexecuted Android acceptance.** Pointer delivery, nested callback scheduling, actual layout/scroll/focus, key dispatch, TalkBack/switch traversal, hit bounds, pixels and gesture calibration remain unrun.

A real host semantics node is stronger evidence than a copied semantic implementation. It is still not an attached Android semantics tree or a key-delivery test. A compiled androidTest APK is not an executed instrumentation suite.

## Finite prioritized findings

### D1. P2: primary FlatKey actions lack a visible keyboard-focus cue

**Requirement:** full R11 accessible primary actions and usable keyboard focus, not only slider adjustment or expandable headers.

**Exact source:** `ui/Controls.kt:325–342`, `ui/SettingsControlAccess.kt:37–43`, and actual Settings callers at `ui/Sheets.kt:1235–1259`.

`FlatKey` collects only `collectIsPressedAsState()`. Its background, border and text depend on active/pressed state. Its `clickable` explicitly uses `indication = null`. Correction02 adds height growth and selected semantics, but no focused-state observer or `settingsFocusBorder` in this path. The actual Settings callers pass no extra modifier.

The affected primary actions are PAUSE DISPLAY ONLY / RETURN DISPLAY TO LIVE, conditional RESET INSPECTION, ENTER PiP, and SHOW / HIDE FLOATING HUD. Merely focusing one of these keys does not select it or press it. The source therefore has no drawing branch that marks this focus. This is a source rendering omission, not a claim that Android key traversal or activation failed during this review. Accessibility-service focus overlays are a different mechanism and were not tested.

The new scalar and endpoint controls correctly install `settingsFocusBorder` at `Sheets.kt:548,616,623`. That makes the omission finite and locally repairable.

**Attribution:** the indication suppression and pressed-only styling are inherited from original374326f. Correction02 leaves them in place while bringing other Settings controls into accessible input. This is not a regression in the new range math, and it does not invalidate the repaired slider semantics.

**Coverage:** `SettingsControlAccessTest:28–33,80–92` inspects actual choice/range semantics. Lines95–117 check provider/layout/setter source wiring. None composes or focuses a FlatKey or verifies its focused drawing.

**Smallest correction/check:** add the existing focus-border seam to FlatKey only inside the Settings context, before its clickable focus target. Keep original actions and non-Settings styling unchanged. Check focused versus unfocused rendering for these actual primary actions, then retain the separate real Android keyboard traversal/activation check. Do not claim a modifier source assertion proves pixels.

### U1. P1 acceptance investigation: last-move/reversal versus pointer-up is still unordered at the nested bridge

**Requirement:** R10 direct travel, reversal repayment, one release decision and no accidental dismissal from stale motion.

**Exact source:** `ui/SettingsGestureAdapter.kt:19–25,44–52,57–102`, `ui/SettingsSheetAdapter.kt:94–117,120–160`, and `ui/SettingsInteraction.kt:94–112`.

The actual Compose ancestor observes Initial, allows child delivery, then decides release at Final. The nested bridge forwards source, available/consumed delta and `scroll.value == 0`. It forwards no pointer ticket or event sequence identity. The adapter retains one mutable budget through Final, resets it at the next Initial, and clears it at release.

This bridge and adapter are byte-identical to original374326f. Correction01 changes final-direction velocity, not these ordering boundaries. The following remains a permitted source-level schedule:

| Step | Physical event or callback | Source consequence |
| --- | --- | --- |
| 1 | A current direct top pull has admitted200dp | raw200, active ticket |
| 2 | Initial and Final observe an upward100dp movement, but its nested pre-scroll is delayed | physical direction becomes upward, raw remains200 |
| 3 | Pointer-up Initial then Final run first | Initial clears the reversal budget. Release takes the independent raw>=192 slow branch and commits |
| 4 | The delayed reversal reaches nested pre-scroll | No ticket/budget remains. It cannot repay raw or undo the committed close |

If reversal arrived before up, raw would be100 and the same upward-terminal gesture would return. The final-direction fix correctly blocks the flick branch, but cannot correct this stale raw-distance branch.

The converse is also finite: raw180 followed by a40dp eligible downward move delivered only after up returns instead of reaching220. A stale callback arriving after another Initial may spend that newer same-direction budget because the callback itself has no event identity.

**Important limit:** these are source counterexample schedules, not established Compose runtime schedules. This review did not inspect or execute the installed Compose scheduler to prove that such delay occurs. There is no claimed Android incidence or frequency. This finding must not be relabeled as a reproduced runtime defect.

**Coverage:** `SettingsGestureAdapterTest:83–108` explicitly covers delayed nested delivery after Final but before the next pointer event. Its helpers at7–18 call admission before Final and release directly. Tests172–198 cover fling/retirement, not last-move nested delivery overtaken by up. The seven control-access host cases add no pointer-order coverage.

**Smallest next check:** trace the finite last downward move, upward repayment and up ordering through the actual production Compose observer plus scrollable bridge. Establish whether nested delivery is drained before release. If not, use a ticketed/drained or safely cancelled release boundary. Do not create a second post-fling commit opportunity or alter opening thresholds.

### U2. Required Android delivery/calibration remains blocked

The inherited gate explicitly records `installed:false` and `android_device_acceptance:false`. The authorized ASUS acceptance workflow was unavailable in the inherited contract and device activity is prohibited in this review. S25 was not used or disturbed. These facts establish the review boundary, not a fresh hardware discovery.

Still unrun: deliberate header/top slow pulls and qualified flicks, accidental top/content flings, slider interaction, reversal/cancel, direct close/Back/scrim, other-sheet closes, actual return/theme/rotation/PiP scroll retention, touched-header anchoring, collapsed focus removal, keyboard delivery, TalkBack/switch traversal, large-font/narrow-window pixels, measured48dp bounds and target overlap. Contrast and frame/jank measurements were not made.

No numerical gesture tuning is accepted by this review. The192/64/920/160 constants remain engineering inputs pending observed deliberate/accidental trials. A longer slow alternative and explicit close are present in source.

## Original findings versus corrections

The original report remains **7/10** and byte-for-byte unchanged. Its SHA256 is `175f9457fe60157af1dd227c31c1f5b3c33237ad393e5e5323d50983e2b378bb`. The original private `REPORT.md` equals the pinned `docs/plans/mobile-expansion/critiques/section-09-round-01.md`.

| Round1 item | Corrected-pin source result | Attribution and honest limit |
| --- | --- | --- |
| Original D1: prompt upward reversal borrowed prior downward velocity | `SettingsInteraction:37–46` resets samples at nonzero direction changes, retains equal-position terminal direction, and release102 requires positive final direction for flick | Fixed in correction01,4a0e8b6. GA252–282 contains short reversal, slow renewal, fast renewal and unchanged deliberate-distance fixtures. H passed. Real header delivery remains U |
| Original D2: presentation holder below hidden/PiP early returns | `PhosphorScreen:227` now precedes hidden228 and PiP229. `rememberSettingsPresentationState` retains one scroll and expansion holder outside sheet navigation | Fixed source custody in correction01. GA285–295 asserts actual source order. Android recomposition/scroll remains U. No process-death or arbitrary Activity recreation promise |
| Original D3: pointer-only scalar/range controls and missing selected/checked state | `SettingsControlAccess:45–85`, `Sheets:534–630` add semantic set-progress, finite legal ranges, keyboard adjustment, separate lower/upper controls and checkbox arm state. `Controls:332,406` adds selected button semantics | Repaired at source/host seam in correction02,f9a1106. Actual setter and semantics host tests pass. D1 above retains primary-action focus styling gap. Android key/TalkBack delivery remains U |
| Original D4: fixed40/44dp child controls and single-line ellipsis | Settings-only48dp lanes, growing48dp-minimum FlatKey/ChipCell, multiline Mono default, scalar/range label stacking | Source causes repaired in correction02. Non-Settings40/44dp fallback retained. Actual widths, font-scale pixels, close visibility and hit bounds remain U, not established by constants |
| Original U1 | Actual bridge and adapter unchanged | Carried exactly as a source-supported, unrun scheduling risk. Not silently closed by the direction correction |

Manual correction01 arithmetic, not an executed fixture here: down0@0, header80@40, reverse70@50, up70@50 leaves raw70 with upward final direction and returns. Renewing to71@60 gives only100dp/s and returns. Renewing to90@60 gives2000dp/s and qualifies independently. A210dp pull repaid only to200 still closes under the explicitly preserved192dp slow law.

## Complete control inventory and authoritative callpaths

Independent extraction of the complete original and corrected Settings bodies found **38 distinct `actions` members and42 references**, with identical multiplicities and normalized action-expression lines. The two rotation-lock queries each appear three times. The full inventories are sealed as `original-actions.txt`, `mobile-actions.txt` and both `*-action-lines.txt` files.

| Intent group | All authoritative members | Corrected Settings lines |
| --- | --- | --- |
| Signal & Startup,4/4 | `setGainAbsolute`, `setGainAuto`, `setViewLock`, `setRemoteLatencyMode` |1175–1205,1386–1407,1487–1492 |
| Beam & Light,10/10 | `setBeamEnergy`, `tapBeamRandom`, `setBeamRandomRange`, `setGlow`, `tapGlowRandom`, `setGlowRandomRange`, `setGrid`, `setGridData`, `openLight`, `openInstrument` |1206–1230,1413–1432,1493–1498 |
| Display & HUD,17/21 | `setPauseBlack`, `toggleDisplayPause`, `resetInspection`, `setControlsAlwaysVisible`, `setPipAutoEnter`, `enterPictureInPicture`, `setFloatingHudEnabled`, `setFloatingHudTransparent`, `hideFloatingHud`, `showFloatingHud`, `setDoubleTapPlayback`, `setLingerBackground`, `setFullscreen`, `isScopeRotationLocked`, `setScopeRotationLocked`, `isUiPlacementLocked`, `setUiPlacementLocked` |1231–1335,1499–1503 |
| Motion & Performance,3/3 | `setFps`, `setOversample`, `setHudMode` |1336–1385,1504–1508 |
| Appearance,1/1 | `openRoom` |1408–1412,1509–1512 |
| About & Manual,3/3 | `exportSettings`, `importSettings`, `openManual` |1433–1478,1513–1518 |

Additional preserved surfaces are outside that member count:

- FOCUS uses `onFocus` at1207, supplied as `actions::setFocus` by `PhosphorScreen:822–824`.
- BAND uses the existing bounded `state.bandMode = (state.bandMode + 1) % 3` at1378.
- Source navigation at1176–1178 sets the existing `showSourcePicker` flag, consumed by `PhosphorScreen:269–270`.
- `SignalCheckEntry` remains at1179. Its actual inline body at `SignalCheckSheet:23–28,40–58` uses current diagnostic state and removes its composed content when collapsed. Disposal clears its visible flag. This review preserves, but does not rescore, R17 diagnostic accuracy or lifecycle findings.
- Floating-HUD and transfer status, stats status, linger/microphone explanation, system-rotation-lock disabled states/explanation, reconstruction notes, relay-only explanation, license/manual and conditional calibration stamp remain present.
- Six summaries at1487–1514 use real source/gain, focus/beam/glow, pause/HUD/fullscreen, frame/reconstruction, room and transfer state. There are no invented enabled HDR, brightness, startup, mixing or persisted reduced-motion controls.

`PhosphorScreen:342–413` delegates to authoritative ScopeActions. `withSheetRouting:852–865` overrides navigation only. Instrument opening819 calls `openInstrumentPresets`, not apply. `MainActivity:395–405` separates opening from `applyInstrument`. Focus/gain/beam/glow/range actions still enter their existing `instrumentEdit` setters at2022–2058,2470–2497. Range setters2483–2489 change endpoints without arming a die. Scalar beam/glow setters retain explicit manual takeover. Import1515–1531 retains its workflow ticket and uncertainty/error status. The whole MainActivity file is byte-identical to the original comparison pin.

Expansion itself changes only stable presentation IDs and anchor requests. It does not call those source/tuning setters. The new accessibility actions call the supplied authoritative callbacks rather than introducing a second persistence or instrument owner.

## Requirement-to-source/test coverage

Main paths below use the `app/src/main/kotlin/dev/phosphor/mobil3/` prefix. Tests are under `app/src/test/kotlin/dev/phosphor/mobil3/ui/`. GA means `SettingsGestureAdapterTest`, IO means `SettingsInteractionTest`, CA means `SettingsControlAccessTest`. All test outcomes are H, not executions by this reviewer.

| Requirement | Actual production source and static result | Relevant inherited check | Remaining result |
| --- | --- | --- | --- |
| Opening thresholds, velocity, physical/chrome mapping and tracked reveal unchanged | `PhosphorScreen:297–298,418–468`. Entire screen byte-equal after removing only relocated presentation line. Gestures, Dimens, entry/stage policies byte-equal | StageGesturePolicyTest18, SheetEntryPolicyTest19, GA21–30 opening finger | Real opening matrix U |
| Settings-only deliberate policy | `Sheets:264,298,358–376,472,483–497,1170–1173`. Only Settings supplies retained scroll | GA/IO pointer/top fixtures and static provider/caller checks | Actual Compose admission U1/U2 |
| Fresh single direct pointer and no opening-finger takeover | `SettingsGestureAdapter:19–53`, observer `SettingsSheetAdapter:131–145` admits touch/stylus and rejects wrong ID/type/time/multi-pointer | GA21–39,63–69,154–170,315–325. IO7–29 | Actual pointer pass/cancel delivery U |
| Physical movement budget and child-first top remainder | Adapter47,57–83 and actual nested bridge94–110 subtract child consumption and bound same-event admission | GA41–108, duplicate callbacks71–80 | Budget has no callback event identity. U1 |
| Slider/fling/programmatic/expansion cannot independently arm | Non-UserInput rejected. Post-fling returns NONE. Pointer slider in `Gestures:189–257` consumes horizontal ownership. Expansion only changes presentation | GA32–108,172–198. IO15–29,76–86. SliderGeometryTest | Source law present. Actual mixed child delivery U1/U2 |
| Raw distance independent of resistant pixels | `SettingsInteraction:18,62–82,128–134` uses192 slow,64 flick,920dp/s,160 scale. Offset monotonic, bounded and diminishing slope | IO31–63,125–139. GA92–119 | Calibration/comfort U. Constants are not accepted UX |
| Final-direction flick and predictable reversal | Owner37–46,78–80,101–102 resets direction segment and repays raw. Header/reverse admission separate | GA122–130,236–282 and IO65–74 | Original D1 fixed. Delayed raw repayment remains U1 |
| Cancellation, multi-pointer, invalid samples and source/geometry loss | Adapter26–42,74–80,108–116. PointerInput finally157–159. Sheets364–365 keys geometry/source | GA133–170,298–325. IO100–123 | Cancellation is not release. Actual lifecycle timing U |
| One close decision, fixed exit, no replacement dismissal | Adapter87–116. Sheets303–309 commits once and retires,426 uses captured offset | GA172–198. IO76–98. SheetEntryPolicy commitment/queued-writer cases | Actual animation and callback scheduling U |
| Firm return and reduced motion | `SettingsSheetAdapter:58–92` cancels prior return and uses160ms tween or snap, revision-fenced | Source inspection and owner return fixtures | Actual displayed animation/reduced motion U |
| Direct close, Back, scrim preserved | `Sheets:311,366–382,509–514`. Explicit dismissal retires settings and cancels anchor through current input | SheetEntryPolicy source/commit cases. Full SheetHost byte comparison | Real close/Back/scrim U |
| Other sheets unchanged | Whole SheetHost byte-equal. Source/Mode/Room callers omit settingsScroll. Light68, Instrument59, Manual130 and Signal32 omit it. Local access defaults false | Whole-file comparisons for other-sheet files. Entry/stage H checks | Other-sheet gesture matrix U |
| All actions and statuses preserved | Full38/42 inventory and exact normalized action-line comparison. MainActivity delegation unchanged | Independent static comparison plus existing gate source checks | Real click-through effects U |
| Six full-width groups, stable IDs, multiple expansion, truthful summaries | `SettingsInteraction:138–169`, `Sheets:1481–1518`, `SettingsSheetAdapter:250–289`. One vertical scroll and conditional bodies | IO141–175. GA200–233,327–343 | Actual readability/navigation U |
| No tuning/source mutation on expansion | Pure presentation owner, header calls only presentation.toggle. Existing authoritative action paths retained | Source owner/caller inspection and IO expansion checks | Device value readback U |
| State survives return/theme/ordinary rotation and hidden/PiP branches | Screen holder227 before early returns. Retained ScrollState and expanded owner226–230. Manifest31 handles ordinary orientation/size changes | GA285–295 source-order assertion and pure retention cases | Corrected custody. Actual recomposition/scroll/focus U. No process-death claim |
| Touched-header anchor and stale callback fencing | State191–223 records actual coordinates. Layout233–248 waits one frame then synchronous bounded delta. Input/geometry/toggle/disposal cancel revision | GA200–233,327–343. IO153–175 | Actual maxScroll/layout placement/header position U |
| Meaningful glyph, header summary and expanded semantics | `SettingsSheetAdapter:263–283` has heading, stateDescription, labeled button, wrapped summary and redundant chevron | Source structure inspection | TalkBack reading order/contrast U |
| Collapsed controls leave layout and focus | `SettingsSheetAdapter:284–287` conditionally composes content. Signal inline body also conditional | Source structure and owner fixtures | Attached semantics/focus tree, focus return after collapse U |
| Scalar/range keyboard and semantics, legal endpoints | `SettingsControlAccess:45–85`, `Sheets:534–630` finite set, clamping/no-op,1% step, Home/End and separately labeled endpoints | CA35–92 includes actual semantic set-progress callback | Source/action host seam repaired. Real key dispatch and range-node traversal U |
| Checkbox/selected state and authoritative setters | Range toggleable Role.Checkbox at579. SettingsChoice selected state in both keys/chips. Callbacks preserved | CA28–33,95–117, endpoint publication cases48–57 | Host semantics verified. Primary FlatKey focus cue D1. Android access U |
| Growing48dp targets, readable large fonts and close visibility | Controls94,331,405, Type39, Sheets labels/range columns and close511, headers264 use Settings-only growth/48dp | CA95–117, corrected SliderGeometryTest string | Height source is not measured width/hit area. Narrow-window pixels, overlap, close visibility and large fonts U |
| Lifecycle/performance/privacy | No added source/network/persistence owner. Finite return animation, bounded one-shot anchor, collapsed bodies removed. Holder survives hidden branches without composing Settings | Source custody inspection and inherited source-boundary check | No frame/jank or sustained Android measurement |
| R15 full independent critique and exact delivery | This report pins source, compares original/corrections and maps all requirements to evidence or explicit U |57 object hashes, inherited artifact hashes, immutable seal | Round2 remains below8. Required acceptance is not waived |

## Independent checks and inherited provenance

### Static work performed here

- Captured only exact Git objects into private `mobile/` and `original/` snapshots.
- Verified each snapshot with its Git blob identity and SHA256 against its pinned object. The first51 rows were verified by fresh object hashing, and six additional other-sheet rows by object capture and matching blob hashes. Final seal verification covers the complete57-row readset.
- Compared complete original/current Settings action-member multiplicities and normalized action-expression lines: identical38 members/42 references.
- Compared15 complete unchanged production files and complete SheetHost function: byte-equal. Entire PhosphorScreen differs only by moving the presentation-holder statement above early returns. `static-comparisons.log` records exact compared paths.
- All27 inspected corrected `app/` runtime/test files match the inherited gate's mobile source manifest. `gate-source-match.txt` records hashes and paths.
- Verified original round1 report equals its pinned retained copy and SHA256. No original report content or score was changed.

No custom source predicate, Kotlin harness, shell review script or inherited gate runner was executed. Manual arithmetic and source schedules are labeled as such. `REVIEW-COMMANDS.md` records the direct command families and their scope. The inherited runner is preserved as text in `evidence/inherited-gate-runner.sh`.

### Inherited gate1246216zt4, not rerun

Evidence prefix: `dev/scratch/mobile-expansion-20260908T001819Z/settings-access-final-integration-1345`.

Its mobile-head receipt is4a0e8b6 plus the correction02 working-source inputs. The gate ran before f9a1106/a38f39e committed the source and docs. Matching all27 reviewed app inputs to the recorded manifest ties this review's relevant source to those results. It is not a claim that the gate ran on an already-clean a38f39e checkout.

- Existing result JSON has zero GPU/native/Android/boundary/mobile-source/engine-source exits and explicitly denies installation and device acceptance.
- XML archive readback: **733 JVM tests,63 suites, zero failures/errors**.
- Settings-specific suites: IO15, GA27 and CA7, totaling49 included tests. The **seven actual modifier/action host cases are included in733**, not additive. Some of those seven test range math/source wiring. Two inspect actual choice/range semantics nodes, including invoking the real set-progress callback.
- Existing native log:126 tests passed. Existing offscreen GPU log:3 passed. These are not Android GPU/compositor observations.
- Existing Gradle log reports successful unit, lint, engine, release-helper and both debug app/androidTest APK tasks. Some unchanged tasks were UP-TO-DATE. No instrumented Android tests executed.
- Existing mobile source before/after manifests compare byte-for-byte. Existing engine manifests also compare byte-for-byte. No mutable working tree was hashed here.
- Shared pin exists as a Git commit and equals inherited engine-head text. No shared production file was needed or independently audited. Shared behavior and full shared source preservation remain inherited evidence, not a new engine review.

| Retained artifact | Independently checked SHA256 |
| --- | --- |
| Inherited gate runner | `5eda874c5b56727308def2ae814373ca3e0c215dfe803c4a15f8f2c214e71f8c` |
| App APK | `c85ea8aa2cbfd9f06db60436873ae9a71891a8d002e13377893c2c427029a52a` |
| androidTest APK | `390aad2c5a8a7a39fa38b1d33f972c19966f00ea31a037ed6f82c8da8fa86b09` |
| JVM XML archive | `bd98065e495cafe45c9af170a084dcb975b7b1fa1be140f9b87fa4fb7b2d8e52` |
| Mobile source manifest | `37aaf7a874adff7edf310ecff1a422bd0f1abc037404a290c01cfd15b2122d2f` |
| Result JSON | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |
| Retained predecessor failure XML archive | `399cbb6946a88f16ad50e4a6522b0bc3f678c26daa944dab456c307643512d6b` |

Predecessor9794039m24 retained one failing `SliderGeometryTest.realRulesUseSharedGeometryAndOneRecognizerNotAnOverlayTapHandler`, at its source-string assertion line70. The failure XML and archive hash were read. The exact test diff changes the expected unconditional44dp source string to the Settings48dp/legacy44dp branch. It retains geometry, recognizer and no-overlay-handler assertions. This was an expected string adaptation, not runtime weakening or permission to discard the failed receipt. Final inherited gate passes all733.

## Blocked acceptance and disposition

**Blocked:** actual Android/Compose delivery and target-device calibration cannot be established under this review's no-execution/device boundary.

**Evidence:** actual adapter/test seams above, U1's finite unsupported ordering boundary, explicit inherited `installed:false` and `android_device_acceptance:false`, and source-only lifetime/layout checks.

**Best current result:** full corrected SECTION9 source review with verified original/correction attribution, complete action inventory and caller authority, gesture/presentation/accessibility requirement map,57 exact object rows,27 app-source manifest matches and hash-checked inherited gates. The original four defect mechanisms received substantial fixes. One finite primary-keyboard-focus source defect and the original ordering risk remain.

**Smallest next work for the coordinator:** correct D1 without changing non-Settings controls, then perform the finite production-Compose last-move/reversal/up trace for U1 when execution is authorized. Run the bounded Android gesture/accessibility/retention matrix on the identified authorized handset when available. Retain unavailable checks as unavailable rather than tuning blindly or treating host compilation as acceptance.

This is round2 of at most four total rounds. Below8 calls for finite correction/checks before another critique. There is no authority to restart an endless sequence or silently waive device evidence. Later source fixes require separate addenda or a separately pinned review. They do not alter this report or original round1.

## Artifact custody and all-ownership release

Private directory: `/home/ben/.jcode/scratch/section09-r15-round2-20260908T1348Z/`.

- `REPORT.md`: this one full immutable review.
- `readset.tsv`: exact revision/blob/SHA256/path rows. `readset.git.txt`: object capture index.
- `mobile/`, `original/`: private Git-object snapshots, not editable implementation copies.
- `external-readset.sha256`, `evidence/`: inherited provenance copies and external artifact identities.
- `static-comparisons.log`, action inventory files, `gate-source-match.txt`: independent static evidence.
- `REVIEW-COMMANDS.md`: command scope and no-execution declaration.
- `SOURCE-FINAL-VERIFICATION.log`: complete source/original hash verification before sealing.
- `SHA256SUMS`, `seal-verification.log`: final manifest and readback. The manifest excludes itself and its verification log to avoid circular hashing.

The private report and evidence are sealed read-only. Root retains all implementation, integration/build, device, Git/commit, retention, cleanup and worker-stop authority. **All source/read holds and all ownership are explicitly released after the seal.** This worker will make no further source reads, amend no report and perform no follow-up work after the completed swarm report. Root may retain/commit the immutable report. Later fixes belong in separate addenda.
