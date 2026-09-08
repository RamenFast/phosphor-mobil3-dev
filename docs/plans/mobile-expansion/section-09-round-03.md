# SECTION9 ROUND3 independent full-section R15 review

## Decision

**Overall full-section score: 7/10.** This reviews all of canonical SECTION9, R10 and R11, at the exact combined pin. It is not a correction05-only review. The implementation substantially meets the source intent, but a finite terminal-release reversal counterexample and unverified target interaction keep it below the canonical 8 anchor.

The largest remaining source mismatch is narrow: a reversal first represented by pointer-up changes the observed release direction but cannot repay raw slow-pull distance. A completed raw200 pull followed by up at190 can still close. D1 below states the exact input condition and source arithmetic. It is not a claimed phone reproduction.

The original full ROUND1 and ROUND2 scores remain **7/10 and 7/10**, byte-for-byte unchanged. Herb's Compose ordering audit and Bat's correction04 investigation are bounded investigations, not full rounds. This is the third full review of at most four. A later correction or acceptance receipt must not rewrite any original score.

## Identity, authority and boundaries

- Reviewer: Jcode worker `bug`, session `session_bug_1788880655342_7937b73dc71474de`. Assignment specifies GPT Astra / high. Swarm status confirms independent worker custody under root `parrot`. No worker was spawned, assigned or used by this reviewer.
- Mobile: `485bc9fa946cf3e33cc32c062868f4192a0327b6`.
- Shared: `0ffd658d7f19e68180c2720e0500b23644619e90`. The object exists as a commit and equals the inherited shared-head receipt. No shared production file was needed or read.
- Comparisons: original implementation baseline `6355074c1d20158c29e749da9fd4c50e0d2bd2a7` and ROUND2 pin `a38f39ef9b47c4c9fdb52a7930d35766dea022a4`.
- Authority read: workspace governance, context standards, bounded complete-build/private-file skills, pinned `docs/AGENTS.md`, canonical plan SECTION9 and R15, `spec/EXPANSION.md:161–169,192–196`, complete section09 contract and writer handoff, original full reviews, corrections03–05, retained Compose addendum and correction04 investigation, and exact section10/11 integration receipt.
- Budget: **22 distinct production paths**, including the manifest, below the 35-file ceiling. The sealed readset contains 61 revision/path objects, including 36 production snapshots across comparison revisions and six focused test files. Capturing or comparing a whole file does not mean unrelated features received a full audit.
- All product source came from immutable Git objects or private copies of those objects. No mutable working-source file was inspected.
- Only this new private scratch report/readset and its evidence files were written. No source edits, Git mutation, test/build execution, Android/JNI/GPU target, device/ADB, GUI, audio, network, download, service operation, Python or subagent occurred. `git hash-object` was used without `-w`.

The user supplied the reviewer route/effort. This worker did not perform a new provider capability lookup or independently inspect harness routing configuration. Root retains that routing receipt, implementation, integration, builds, devices, Git, retention and cleanup authority.

## Evidence classes

**S:** independently inspected exact application source, object identities, byte comparisons, static inventory and manually evaluated schedules. No application code ran here.

**H:** inherited coordinator gate `405519lhyk`, plus historical writer/correction receipts. The exact retained runner, APKs, XML archive and source manifest were hash-checked. Reading successful receipts does not make their executions independent.

**Q:** dependency ordering already established by the retained Compose1.11.4 audit. That audit establishes a queued drag consumer versus ancestor Final boundary, not the target's actual dispatcher or scheduling incidence. It was read, not repeated.

**U:** unexecuted target behavior: pointer delivery, callback timing, attached semantics, keyboard/TalkBack/switch access, layout, pixels, measured targets, gesture calibration and lifecycle interaction.

## Finite material findings and smallest checks

### D1. P2: terminal-only reversal cannot repay the slow-distance branch

**Requirement:** R10, canonical plan197–199 and contract26–28. Reversal pays back direct displacement before release. A release must not decide from movement that the same observation shows has been reversed.

**Exact source:** `ui/SettingsGestureAdapter.kt:51–59,96–105,108–126`, `ui/SettingsInteraction.kt:30–46,94–112`, actual observer `ui/SettingsSheetAdapter.kt:155–173`. All line references use the mobile pin unless stated otherwise.

`initial` assigns a motion budget only while both `pressed` and `previouslyPressed` are true. Pointer-up therefore receives budget0 even if its y differs from the preceding move. `admit` also rejects `releasePending`. Final observes the up position, but `observe` changes samples/direction rather than raw distance. The slow branch independently accepts raw>=192.

A finite direct top-edge schedule, in dp and monotonic milliseconds:

| Step | Actual adapter call/order | State/result |
| --- | --- | --- |
| 1 | `initial(1,0,0,true,false,1)`, then Final | Fresh ticket, raw0 |
| 2 | Move Initial to200 at1000, direct top `remainder(200,true,true)`, then consumed Final | Completed nested receipt, raw200, pendingDelivery=false |
| 3 | Up Initial at190 at1020, with `pressed=false, previouslyPressed=true` | No prior unresolved receipt exists. Budget becomes0 and releasePending=true |
| 4 | Final, without an earlier callback repaying the up event's -10 | Observed direction becomes upward. Raw remains200. Flick is ineligible, but slow branch commits CLOSE |

If -10 is instead delivered as an ordinary eligible move before an unchanged-position up, raw becomes190 and this upward-terminal sequence returns. That is the intent difference. The issue does not require an old queued reversal to overtake up, so correction04's pending-delivery latch does not close it.

**Condition and limit:** this is a source counterexample for an up sample whose coordinate differs from the previous delivered move, with no separately admitted repayment. The production observer forwards that up coordinate. The retained audit's DragStopped path does not establish an application repayment for it. This review did not measure how often the target supplies such a terminal sample. It does not claim all real releases take this schedule.

**Attribution:** the zero up budget/release admission rule is retained from ROUND2. Corrections04/05 did not introduce it. The new observation does not reopen the already-fixed short *move-event* reversal mechanism.

**Fixture gap:** `SettingsGestureAdapterTest.kt:416–447` supplies reversal through a move callback and then releases at the identical y. The helpers at171–183 permit a different up y, but the relevant existing cases do not exercise it. The queued cases62–89 cover a different condition.

**Smallest correction/check for root:** add the single completed200/moved-up190 fixture to the actual adapter suite and require RETURN or a conservative non-closing cancellation. Resolve unaccounted terminal reversal locally before slow commit. Do not grant positive dismissal distance from unproven up travel, change opening constants, or redesign the nested protocol. Preserve an unchanged-position192 slow release,64 qualified downward flick, and the existing queued cancellations. The smallest target follow-up records the last move/up coordinates and resulting release for this one case.

### U1. Material acceptance boundary: interruption safety is established for the retained schedules, usability is not

**Requirement:** R10 deliberate slow/flick closure, ordinary scroll behavior, predictable interruption and recovery.

**Source:** `SettingsGestureAdapter:28–29,72–94,108–143`, `SettingsSheetAdapter:79–109,118–184`, `Sheets:295–307,357–375,509–525`, `PhosphorScreen:833–860`.

Independent manual traces confirm the finite retained closures:

1. Raw200, queued reverse100, consumed Final without completed post, then up Initial: `pendingDelivery` triggers `cancel()` before budget replacement. Raw clears, `requiresReopen` latches, no CLOSE occurs. A completed reverse/post before up instead repays to100 and permits ordinary RETURN.
2. Raw100, queued40, consumed Final, then new10 Initial: cancellation occurs before installing10. Neither delayed40 nor subsequent10 can spend a newer budget.
3. After that latch, late remainder/header callbacks, a fresh down and untagged post-fling cannot clear it. `postFling()` is read-only NONE. An old connection captures its old owner.
4. Close/Back commits the exit and retires the owner. The Settings branch is removed after exit. Reopening creates a fresh dismissal owner, while the separate presentation owner survives.

This is correct conservative cancellation for those schedules, not a drain protocol or universal producer identity proof. A delayed ordinary body move can also trigger the latch, including a move that has admitted no dismissal distance. The source intentionally disables header and body drag dismissal for the rest of that opening. The inline message offers Close/Back then reopen. Child exclusion must not clear an existing ambiguous body latch, and it does not.

**Unknown:** actual owner dispatcher, input-versus-consumer incidence, frequency during routine scrolling or rapid pulls, visible return/message timing, and comfort of repeated recovery. The retained Q audit explicitly leaves these unknown. No external research or bytecode audit was repeated.

**Smallest check:** under root's authorized target scope, capture one slow body pull, one fast move/up sequence, and one ordinary scroll with Initial/Final consumption, nested completion, interrupted state and release outcome. Then close/reopen once and verify old callbacks cannot affect the new owner. If ordinary use rarely interrupts and recovery works, retain the conservative design. Do not request a rewrite without that observation.

### U2. Required target delivery remains blocked, not passed by source fixtures

**Requirement:** full R10/R11 and canonical validation327–328. Real gesture comfort, touch/focus semantics, header anchoring, state retention and readable large-font layouts are acceptance behavior.

**Evidence:** inherited result JSON explicitly says `installed:false` and `android_device_acceptance:false`. The exact integration receipt reports no ASUS and no S25 operation. The older contract names ASUS calibration and leaves S25 undisturbed. This review was expressly forbidden to use any device or execute UI checks, so it did not refresh hardware discovery.

The root still needs the bounded matrix in the coverage table below. A built androidTest APK, host semantics node and string assertion do not establish an attached Android tree, consumed pointer path or focused pixel. No numerical threshold or measured48dp/contrast claim is accepted here.

**Smallest next step:** use the authorized target when available for the named gesture/accessibility/retention matrix, including D1 and U1. Preserve the blocked result if the target remains unavailable. Do not tune numerical thresholds blindly or disturb another handset to erase this gap.

## Original findings and narrow appearance integration

| Earlier item | Current independently inspected source | Disposition |
| --- | --- | --- |
| ROUND1 short upward move borrowed positive historic flick velocity | Interaction37–46 resets the direction segment. Release102 requires positive terminal direction. Raw repayment remains bounded | Source mechanism repaired. Ordinary80/-10/up70 returns. D1 concerns displacement first observed on up, not this repaired case |
| ROUND1 holder below hidden/PiP branches | Screen228 precedes229–232. Presentation owner and actual ScrollState are outside the sheet switch | Source custody repaired. Real return/rotation/focus remains U |
| ROUND1 pointer-only scalar/range controls | SettingsControlAccess45–85 and Sheets543–558,583–635 install finite semantic/key actions and separately labeled endpoints | Source/host seam repaired. Actual key/TalkBack/switch delivery remains U |
| ROUND1 fixed heights and ellipsized child labels | Controls94,332–344,407–421, Type39 use Settings48dp/growth and multiline text | Original fixed-height/single-line causes repaired. Width, overlap and rendered large fonts remain U |
| ROUND2 primary focus cue absent | Controls329–337 now installs `settingsFocusBorder` before clickable. Access37–42 draws a2dp accent outline on focus | Source omission repaired in correction03. Source assertion is not focused-pixel evidence |
| Retained queued Q1/Q2 | Initial cancels unresolved consumed delivery before replacing its budget | Safely closed by abandoning dismissal. U1 retains incidence/recovery limits |
| Bat's consumed child falsely created an interruption | Access provider in Sheets525 supplies actual Settings owner. SliderLane95 excludes fresh child down before real seek recognizer. Adapter68–70 cancels only matching pointer | Source mechanism repaired by correction05. Wrong pointer and prior-latch retention cases exist. Real input delivery remains U |

The new Appearance editor is now inside the actual Appearance section (`Sheets1414–1418,1516–1519`). It receives the same Settings gesture provider as the other body controls. Each `BasicTextField` installs `settingsChildInput()` on that control only (`AppearanceEditor194–205`). The editor/container and its normal buttons are not blanket-excluded. No custom editor pointer-drag recognizer appears in the inspected editor file.

The hook (`SettingsSheetAdapter49–65`) observes Initial, does not consume, and emits no setting value. Its null-owner branch returns the original modifier. The slider and text-field exclusion therefore does not add another source/persistence owner or alter child selection. Actual text selection, vertical drift and scroll handoff remain U. Source-only wiring tests (`AppearanceRuntimeWiringTest89–99`, `SettingsGestureAdapterTest7–60`) are correctly classified as source evidence.

Appearance close integration is explicit: Settings passes `actions::cancelAppearancePreview` at1179, SheetHost invokes it after one committed close at301–307, and editor disposal also calls the current action (`AppearanceEditor55–56`). Screen350–359 and MainActivity358–368 delegate to the appearance owner. This bounded review does not rescore full appearance CRUD, migration or storage correctness.

## Complete existing control inventory and authority

Independent extraction found the original and ROUND2 inventories byte-equal: **38 distinct actions members,42 references**. Current Settings retains every one and adds only `actions::cancelAppearancePreview`, giving39 distinct members/43 references in that extraction. The editor's separate action interface is not disguised inside that count. Full source comparison of the Settings body adds the editor, preview-close callback and appearance summary, not replacements for old control callbacks.

| Group | Complete retained actions/surfaces | Current source |
| --- | --- | --- |
| Signal & Startup | Source request, Signal Check, GAIN `setGainAbsolute`, AUTO-GAIN `setGainAuto`, VIEW LOCK `setViewLock`, relay latency `setRemoteLatencyMode`, relay routing explanation | Sheets1181–1210,1392–1412,1494–1499 |
| Beam & Light | FOCUS via `onFocus`, BEAM `setBeamEnergy`, beam die/range `tapBeamRandom`/`setBeamRandomRange`, GLOW `setGlow`, glow die/range `tapGlowRandom`/`setGlowRandomRange`, GRID `setGrid`, GRID DATA `setGridData`, LIGHT `openLight`, presets `openInstrument` | Sheets1212–1235,1420–1438,1500–1505 |
| Display & HUD | HOLD/BLACK `setPauseBlack`, display-only live/pause `toggleDisplayPause`, `resetInspection`, `setControlsAlwaysVisible`, `setPipAutoEnter`, `enterPictureInPicture`, `setFloatingHudEnabled`, `setFloatingHudTransparent`, `showFloatingHud`/`hideFloatingHud`, `setDoubleTapPlayback`, `setLingerBackground`, `setFullscreen`, scope/UI rotation queries and setters, actual HUD status, linger/mic and Android-lock explanations | Sheets1237–1340,1506–1510 |
| Motion & Performance | `setFps`, `setOversample`, real frame/reconstruction notes, `setHudMode`, bounded BAND transition, `hudControlStatus` | Sheets1342–1390,1511–1515 |
| Appearance | Existing room navigation `openRoom`, now preceded by real inline editor and current appearance summary. New preview cancellation remains separate | Sheets1414–1418,1516–1519 |
| About & Manual | `exportSettings`, `importSettings`, transfer status/error, archive boundary text, `openManual`, GPL/about and conditional calibration/designator stamp | Sheets1440–1484,1520–1525 |

Additional authority checks:

- Screen348–429 delegates existing setters to ScopeActions. `withSheetRouting` at872–884 overrides navigation only. FOCUS remains `actions::setFocus` at842.
- Source navigation uses `showSourcePicker` at Sheets1183 and Screen275–276. SignalCheckEntry23–27 retains its diagnostic toggle and source action. Collapsing its composed content clears visibility through40–48. This is not a new R17 accuracy verdict.
- BAND remains `(state.bandMode + 1) % 3` at Sheets1384. The two rotation-lock queries account for the four repeated references in the original42.
- MainActivity420–430,2123–2158,2569–2596 retains instrument edit ownership, bounded setters, scalar manual takeover and distinct range-arm actions. Expanding a section does not call these setters.
- Instrument navigation at Screen838 calls `openInstrumentPresets`, whose MainActivity456–463 path loads/opens rather than applies. Apply remains a separate operation at465–466.
- Import at MainActivity1594–1612 retains the instrument ticket/status and adds an appearance ticket before opening the picker. This review preserves the action boundary, not an independent full import transaction audit.
- Display/PiP/HUD actions retain their real host adapters at MainActivity895–945,1401–1436,1475–1483. Preference changes remain distinct from an explicit Show operation.
- There are no invented enabled HDR, brightness, startup or mixing controls. The contract deliberately waits for those real owners. The Display worked example is not falsely rendered as implemented capability.

## Full requirement coverage and smallest acceptance observations

Paths below are relative to `app/src/main/kotlin/dev/phosphor/mobil3/`. Test names refer to the six retained test files. Passing execution is H only.

| Requirement | Exact source result | Test/receipt evidence and smallest remaining observation |
| --- | --- | --- |
| Opening thresholds and velocity unchanged | Entire `ui/Gestures`, `Dimens`, `SheetEntryPolicy`, `StageGesturePolicy` are byte-equal to original6355074. Screen434–484 opening host is byte-equal too. Dim36–37 retains72/920 legacy constants | Existing stage/entry fixtures and H gate. U: repeat original opening at slow/flick speeds without requiring a faster upward swipe |
| Finger-tracked reveal and physical/chrome routing | Screen303–304,434–481 and Sheets438–462 retain original host/geometry laws | S comparison, H entry coverage. U: portrait, landscape and locked applied-frame opening |
| Opening finger cannot become close ticket | GestureAdapter37–45 requires fresh down. Observer162–165 carries actual prior/pressed identity | GA185–194. U: mount during an existing pull and release without dismissal takeover |
| Settings-only resistant direct policy | Sheets260,295,357–375,471,483–497,1175–1177 selects it only for Settings | S caller/branch inspection. U: direct header and top-body trials |
| Single direct touch/stylus, invalid/multi/lost/time cancellation | Observer155–165 and Adapter33–58 reject unsupported identity/type/time | GA318–334,462–488, IO100–123. U: pointer cancellation and geometry/source change mid-drag |
| Top-boundary body admission and child-first budget | Adapter72–105 subtracts child consumption, limits same-event travel and requires direct/top remainder. Nested bridge118–134 passes actual ScrollState top | GA205–244. U: body scroll to top versus a new intentional pull |
| Progressive resistance and slow/flick accessibility | Interaction18,94–112,128–134 separates raw from160*raw/(160+raw),192 slow,64 plus920 flick | IO31–63,125–139, GA256–283. U: calibrate deliberate versus accidental comfort, not universal threshold claims |
| Final reversal | Interaction37–46 resets terminal segment and reverse57–59 repays outstanding raw | GA286–295,400–447. D1 remains for up-only displacement. U: last move/up coordinates |
| Accidental short motion, ordinary scroll, top fling, side effects | No ticket/no budget/non-user cannot add eligible travel. Post-fling130 is NONE | GA196–244,336–348. Q closures traced. U1: count conservative interruptions in actual ordinary scrolling |
| Slider and intentional text-child consumption | Controls94–102, SheetAdapter49–65, Adapter68–70, AppearanceEditor199–205 exclude matching fresh down without consuming | GA7–60 and actual appearance source assertion89–99. U: vertical drift, text selection and a subsequent fresh header pull |
| Deferred nested receipt | Adapter112 retains consumed unfinished receipt until next Initial or completed direct post90–94 | GA62–169. Q1/Q2 safely cancel, not drain. U1 remains |
| Interruption, recovery and stale callback safety | Adapter132–143 latch, no fling clear. SheetAdapter79–109 finite return. Sheets522–525 recovery text,301–307 retirement. Screen833–860 removes old branch | S schedules. U: message, Close/Back, reopen identity and old callback routing |
| Firm return, reduced motion and one committed exit | SheetAdapter88–116 uses160ms tween/snap and revision fence. Sheets301–307 captures exit/offset once,421–425 draws captured displacement | IO88–98, GA297–316,336–362. U: observed return/no overshoot, no replacement-sheet close |
| Close, Back, scrim | Sheets310,365–381,509–515 use same dismiss commitment. New preview cancellation is explicit | S real paths and H entry checks. U: all three with keyboard/IME closed and active input as applicable |
| Other sheets | Source/Mode/Room/Light/Instrument/Manual/Signal omit settingsScroll. Default nested owner and SheetDismissState compare byte-equal to ROUND2 | Other-sheet gesture policy preserved, not whole-UI byte identity. Section10 adds Glass backplate/text, room access context, and48dp labeled close to other sheets. U: close/Back/header regression matrix |
| Six compact intent groups, glyph/title/summary/chevron | Sheets1494–1525 and SheetAdapter275–312 use six stable IDs, full-width rows, actual state summaries and hairline divisions | IO141–151, GA491–507 and S actual body. U: clarity in portrait/landscape/large font |
| Multi-open and expansion without setting mutation | Interaction138–169, SheetAdapter224–227 mutate presentation only. Bodies use independent enum membership | Pure owner fixtures. U: expand/collapse several sections and read back unchanged tuning/source |
| Scroll/expansion return, theme, rotation, PiP/hidden lifetime | Screen228 before early returns. SheetAdapter205–254 owns retained scroll/IDs. Manifest31 handles ordinary rotation/size config | GA449–460 is source ordering only. U: return from light/room/presets/manual, PiP and theme/rotation. No arbitrary Activity/process-death persistence claim |
| Touched-header anchor and stale correction | SheetAdapter216–247 reads actual coordinates,257–272 uses one layout receipt/frame then synchronous bounded delta. Adapter146–181 fences revision | GA364–398,491–507, IO153–175. U: compare header y and scroll near maxScroll, then interrupt before correction |
| Expanded/collapsed labels and focus tree | SheetAdapter289–307 exposes heading/state/labeled click. Conditional content309–311 removes hidden controls | S tree construction. U: TalkBack/switch/keyboard traversal, focus after collapse and across rotation/theme |
| Scalar/range input and primary focus | Access37–85 implements focus cue, range semantics, arrows/Home/End. Sheets555–558,620–635 preserve setters and endpoint identities. Controls336 adds primary focus | CA8 cases include actual host semantics and source assertions, not attached delivery. U: focus/read/change GAIN and both endpoints, activate real primary actions |
| Checked/selected state and distinct action types | Sheets585–586 range arm checkbox. Controls333/408 selected button semantics. Navigation and slider shapes stay distinct | CA28–33,80–92. U: state announcement and distinguishable visual/focus forms |
| At least48dp and readable large fonts | Header289, nav1137, close511, slider94, keys332/407, editor187/200 use48dp minima/sizes. Type39 wraps Settings labels | S dimensions are not measured hit bounds. U: narrow/large-font target width/height/overlap, full AUTO-GAIN/VIEW LOCK values, rate/import/export labels and visible close |
| Truthful summaries and all existing actions/statuses | Complete inventory above, ScopeUiState15–25,65–139, Screen delegation and MainActivity adapters | Independent S preservation plus H gate. U: complete click-through with actual state/status effects |
| Lifecycle, performance and privacy | Presentation holder alone remains above hidden branches. Settings bodies dispose. Return and anchor are bounded. No new source/network/storage owner in these seams | H native/boundary gates. U: frame/jank and actual lifecycle behavior. Full appearance/native/privacy audits are not claimed |
| R15 independence, identity and full scope | This single scored report maps all SECTION9 bullets, retains originals, and names exact unknowns |61 Git-object identities,28 inherited app-input matches, sealed report/readset. ROUND3 remains below8 without restarting the four-round cap |

## Independent checks and inherited gate provenance

### Static checks performed here

1. Captured61 exact Git objects into new private snapshots. Each retained blob matched `git hash-object` without writing Git objects. Each has a SHA256 in `readset.tsv`.
2. Compared original/ROUND2/current Settings action inventories. All original38 members/42 references remain. The sole new directly extracted reference is preview cancellation.
3. Compared original opening host and four opening policy/gesture files byte-for-byte. Compared ROUND2/current legacy SheetDismissState and nested owner byte-for-byte. These are text comparisons, not gesture tests.
4. Read actual pointer/child/provider/modifier/state/host action paths and evaluated the finite schedules manually. No copied implementation or custom Kotlin runner was used.
5. Matched all28 inspected current app runtime/test inputs against the recorded inherited gate source inventory. No mutable working tree was hashed for this claim.
6. Verified the five retained artifact hashes below. Read inherited result JSON, successful native/GPU/Gradle tails, and archived JVM XML. Compared retained mobile/shared before/after inventories byte-for-byte.
7. Verified original full review and bounded investigation hashes against the retained identities. No original was edited.

### Inherited gate405519lhyk, not rerun

`docs/plans/mobile-expansion/section-10-11-integration.md` identifies the runner and artifacts. The private location is a **filename prefix**, not a directory: `dev/scratch/mobile-expansion-20260908T001819Z/appearance-runtime-manual-r02-*`. An initial directory lookup found nothing, then the finite prefix lookup located the exact receipts.

The inherited head text is `acb3d5d688ea05617706fb9edb9852dfd4431b4f` plus released working-source inputs. It is not a clean485bc9f execution claim. Matching all28 relevant app objects to its recorded inventory ties this review's relevant source to that working-source gate.

- Archived XML readback: **871 tests,73 suites,0 failures,0 errors**.
- Native host log: **126 passed**. Offscreen public GPU log: **3 passed**. Neither is Android compositor or phone evidence.
- Gradle log: successful Android build/lint/unit/dual-APK/engine/release-helper gate,92 tasks with69 up-to-date. No instrumentation suite was executed.
- Result JSON: all six gate/source exits zero, `installed:false`, `android_device_acceptance:false`.
- Recorded mobile and shared before/after source inventories are byte-equal.
- Focused test declaration counts are15 Interaction,39 GestureAdapter and8 ControlAccess. These62 are part of871, not additive. Their reviewed bodies include source-form assertions, host semantics actions and pure fixtures.

| Artifact | Independently hash-checked SHA256 |
| --- | --- |
| Retained runner | `0cf6646452e63e2771c8acaf7c5c847006ffd4ebd6e896931fff67fc6d62f42e` |
| App APK | `be540e93204cb8cac705465687199f9468492462f8fa60d266f9887933ffa2bc` |
| androidTest APK | `0c706903bf4d4098b0cab6034de1812fe94e5f6bd514786e5c9c7cd4e3cb98e9` |
| JVM archive | `4e0d2c6479dd183a63f67bb9b610ef95bd0d988ae10a6143af5a6f63668fd42c` |
| Mobile source inventory | `e2ab65282095ea3eee038203dec823fbb286bd7925bc22993dc4ae5adff2e854` |

Preserved originals:

- ROUND1: `175f9457fe60157af1dd227c31c1f5b3c33237ad393e5e5323d50983e2b378bb`.
- ROUND2: `ecca882edbfc723788bb0f98c14bfd9bd47cae346f73fd0a5d13b02af31fb29c`.
- Correction04 investigation: `62f10cbdc0ec64a93963a97f0c9cd2ebd3fbd667f5191b6668e4b6279a75ce59`.

## Blocked outcome, disposition and release

**Blocked:** complete Android acceptance, target scheduling incidence and gesture/accessibility calibration cannot be established under the explicit no-execution/no-device boundary.

**Evidence:** exact source D1, retained Q audit limits, actual production adapters and fixture seams, plus inherited explicit no-install/no-device flags.

**Best current result:** full SECTION9 source review at the combined pin, complete action/requirement coverage, independently verified prior fixes, finite Q1/Q2 cancellation closure, one terminal-release source counterexample,61 exact readset objects and hash-checked inherited gate identities.

**Next work:** root can address D1 with one finite actual-adapter fixture and a local non-closing terminal reversal rule. The existing target matrix remains required when authorized. U1 does not justify another protocol redesign without incidence evidence. Below8 permits one remaining full round after correction/checks, not an endless review loop. If round4 remains below8, preserve the gaps and continue under canonical R15.

Private artifact directory: `/home/ben/.jcode/scratch/section09-round03-485bc9f-RQhrXR82/`.

- `REPORT.md`: this one full-section report.
- `readset.tsv`, `git-blobs.tsv`, `revisions.tsv`: exact revision/blob/SHA/path identities.
- `mobile/`, `original/`, `prior/`: private exact-object snapshots.
- `*-actions.txt`, `static-comparisons.log`, `*-comparison.diff`, `gesture-corrections.diff`: static comparison evidence.
- `evidence/`: retained gate receipt copies, artifact identities, source-input matches and XML summary.
- `REVIEW-CHECKS.md`: supplemental observed comparison/context identities and limitations.
- `SHA256SUMS`, `seal-verification.log`: sealed artifact manifest and readback. The manifest excludes itself and its verification log to avoid circular hashing.

The final completion report supplies existing paths and final hashes after sealing. **All source/read/build ownership is released at that report.** Root retains implementation, builds, devices, Git, retention, cleanup and worker-stop authority. No further product reads, edits or follow-up work will occur after completed action=report. Original reports, investigations and source remain unchanged.
