# Section 10 R04/R12 independent full review, ROUND 1

## Decision

**R15 overall score: 7/10.** This is one full-section review, not a newest-diff review, implementation handoff, device acceptance, or release approval.

The appearance core substantially implements the requested preservation and recovery contract. Actual runtime consumers still have material gaps. Four finite findings below prevent an 8. The score reflects those source issues, not merely the absence of a phone.

**Largest intent mismatch:** Ben should be able to change one part of the current look without another owner restoring old styling. The retained ROOM controls instead combine stale legacy overrides with a saved-record baseline. A CORNERS action can replace the current character, motion, density, typography and labels. The document remains internally valid while the visible result no longer matches the action. Finding F1 is the clearest breach of the single appearance-owner intent.

### Identity and boundary

- Reviewer: Bird, independent of the implementation writers. Assigned GPT Astra/high by the task. No workers spawned or assigned.
- Route inspection: `swarm list_models` reported `gpt-6-astra` and the pin `openai-oauth:gpt-6-astra`, with high among the exposed effort options. The task supplies the actual high-effort assignment. No route change was made.
- Mobile: `485bc9fa946cf3e33cc32c062868f4192a0327b6`.
- Shared: `0ffd658d7f19e68180c2720e0500b23644619e90`, tree `51f828e60c5bbfd7003694bf28b7455daf07db1e`.
- Source inspection ended **2026-09-08T15:30:59Z**, before the 15:40 UTC limit. Only report/readset sealing follows.
- Production ceiling: **33 distinct mobile production files**, including the manual availability declaration. No sibling production file was needed after tracing appearance actions to Kotlin-only presentation and persistence owners. The shared pin, inherited engine head and unchanged inventory were inspected. This is not an independent GPU implementation review.
- Readset: 63 exact mobile Git-object snapshots, comprising 33 production files, 13 test files and 17 documents. Whole snapshots are retained, but large Activity/UI files were inspected by requirement-relevant regions rather than unrelated source features.
- Originals were not altered. No changing live product source was read. No builds, tests, Android/JNI/GPU execution, ADB, GUI, audio, network, services, downloads, Python, Git mutation or source edits occurred.
- Governance and context standards were read first. The no-Python boundary took precedence over the skill's optional prose-lint procedure. Prose was reviewed manually.

## Evidence classification

**S** means independently inspected immutable source or test implementation. It does not mean execution.

**I** means inherited execution evidence. The retained logs, result JSON, archive headers and named artifact hashes were inspected, not rerun.

**U** means a required runtime/device observation remains unestablished. A source assertion is not Android callback, frame, accessibility or compositor acceptance.

### Inherited coordinator gate405519lhyk

Canonical receipt: `docs/plans/mobile-expansion/section-10-11-integration.md:3-29` at the pinned mobile commit.

Observed from retained evidence:

- Results JSON reports zero exits for Android, native, GPU, boundary and both inventory checks. `installed=false` and `android_device_acceptance=false`.
- Streaming the retained JVM XML archive produced **73 suites, 871 tests, zero failures, zero errors**. This was archive inspection only.
- Gradle log ends `BUILD SUCCESSFUL`, with lint, debug app APK, debug androidTest APK and unit-test tasks. The two APKs are app plus companion, not proof of debug and release installation.
- Native log reports **126 passed**. GPU log reports **3 passed** on host RADV. These retained-frame tests do not test Android theme contrast or Compose motion.
- Boundary JSON reports `status=ok`, 12 source checks, zero artifact checks and zero trusted-runtime exemptions.
- Mobile and shared pre/post inventory copies are byte-identical.
- All **46 reviewed production/test snapshots** match the corresponding SHA-256 entries in the inherited mobile inventory. Every retained snapshot also matches its pinned Git blob ID.
- The inherited mobile HEAD file says `acb3d5d688ea05617706fb9edb9852dfd4431b4f`. The gate was a working-source gate. Matching the 46 reviewed paths binds this review's relevant source to it, but does not turn it into a clean whole-repository gate at `485bc9f`.

Rehashed inherited artifacts, all matching the committed receipt:

| Artifact | SHA-256 |
| --- | --- |
| Runner | `0cf6646452e63e2771c8acaf7c5c847006ffd4ebd6e896931fff67fc6d62f42e` |
| App APK | `be540e93204cb8cac705465687199f9468492462f8fa60d266f9887933ffa2bc` |
| androidTest APK | `0c706903bf4d4098b0cab6034de1812fe94e5f6bd514786e5c9c7cd4e3cb98e9` |
| JVM XML archive | `4e0d2c6479dd183a63f67bb9b610ef95bd0d988ae10a6143af5a6f63668fd42c` |
| Mobile source inventory | `e2ab65282095ea3eee038203dec823fbb286bd7925bc22993dc4ae5adff2e854` |

Original evidence prefix: `dev/scratch/mobile-expansion-20260908T001819Z/appearance-runtime-manual-r02`.

The earlier pure-core 84-test, bridge 64,350-combination, runtime 116-test and first failed combined-gate receipts were read as historical, narrower evidence. Their counts are not added to 871 as independent coverage. `section-10-integration-correction-01.md` explains the three source-form assertion corrections and the prose scanner change. No scanner exemption or deleted test is claimed.

## Finite prioritized findings

Priority P1 means resolve before section acceptance. P2 means a concrete missing or incorrect section behavior. No crash, source takeover or corrupt-byte destruction is alleged without evidence.

### F1, P1: ROOM style controls reintroduce stale override authority

**Requirement:** one theme model, exact current-look preservation, and explicit style edits. Canonical section 10, appearance contract lines 20-39 and runtime contract lines 7-13, 39-43.

**Source:**

- `MainActivity.kt:1957-1971` populates `ui.styleOverride` from legacy preference keys.
- `MainActivity.kt:337-355` publishes a new committed/effective appearance and explicit style, but does not reconcile that override state.
- `ui/Sheets.kt:1041-1075` labels and cycles ROOM controls from `state.styleOverride`.
- `MainActivity.kt:2113-2121` starts restyling from the associated saved record, not necessarily the current edited style. It applies the entire supplied override tuple, then persists the resulting complete appearance.
- `ui/RoomStyle.kt:93-109` deliberately makes a non-null character replace all coupled defaults.

**Worked schedule, derived from source:**

1. Start from valid legacy `room=glass`, `ov_char=2`, with motion, radius and labels absent. Migration correctly preserves an Annotated current appearance. `ui.styleOverride.character` is Annotated.
2. Load the curated AMOLED draft and APPLY. The committed appearance is now Engraved, CUT, true black, with density 1 and ordinary prose. The old Annotated override remains in `ui.styleOverride`.
3. Open ROOM. FEEL still says Annotated even though the authoritative appearance is AMOLED.
4. Press CORNERS once, from match to 0. The callback includes the stale Annotated character.
5. `setRoomStyle` looks up curated AMOLED, then `overridden` replaces it with BenchStyle before setting radius 0. The app persists Annotated, Detented, density 0.9, monospace prose and designators. A corner-only action changed several unrelated style fields.

A second bounded case exists without migration: edit a curated-associated draft's motion/density, APPLY, then use a ROOM control. Looking up the curated record at lines 2117-2119 can restore its saved style and lose active modifications.

**Why inherited tests miss it:** `AppearancePaletteTest:24-38` proves the legacy resolver, not current Activity synchronization. `RoomStyleOverrideTest:99-123` mutates `ScopeUiState` directly. The updated wiring assertions at lines 139-160 verify callback strings and the explicit provider, not this schedule. `AppearanceRuntimeWiringTest:102-110` verifies `restyled` when already given the correct style.

**Smallest root correction/check:** keep the old ROOM UI, but make its displayed choices and next-field action resolve from the authoritative current appearance. Preserve the specified coupled FEEL semantics. Add the two schedules above to the actual adapter boundary and assert every untouched authored style field. No codec or collection rewrite is needed.

### F2, P1: passing authored contrast checks do not protect actual curated text and beam-following controls

**Requirement:** 4.5:1 normal text, 3:1 essential boundaries, readable Glass over moving content, and explicit non-destructive corrections.

**Source:**

- `settings/appearance/AppearanceValue.kt:45-61` defines curated Light and Glass colors.
- `AppearanceValue.kt:96-107` checks opaque authored roles against authored plane/surfaces and checks `lineStrong`, not every runtime composite.
- `ui/Console.kt:81-107,129-131` gives StatusBand an opaque background only for Glass, then draws source/gain text with `ink2` at 70% alpha.
- `ui/PhosphorScreen.kt:538-541,682-690` places that band above the full-bleed scope, without a Light backplate.
- `ui/Palette.kt:36-52` supplies measured `liveAccent` to every palette and replaces Glass structural accent when beam following is enabled. `MainActivity.kt:1180-1187` wires the sampled value.
- `ui/Controls.kt:335,344,409,414-419` draws active text/outlines directly with that accent. Normal unselected outlines use `line`, while the host essential-boundary test checks `lineStrong`.
- `ui/AppearanceEditor.kt:136-142` warns about authored checks and varying measured accent, but does not resolve these runtime colors.

**Worked schedules, derived from source:**

1. Apply curated Light. Close Settings and show the status band over a black, no-signal scope region. Source and gain text use `#5C5244` at 70% alpha over black, not the warm surface used by the green test. Even opaque `#5C5244` on black is below 3:1 under the included sRGB formula. Adding transparency reduces it further. These are normal-size text roles requiring 4.5:1.
2. Apply curated Glass with beam-following accent enabled, and use an authored black beam color. `withBeam(0,0,0)` resolves accent to `#304038`. Active control text now draws that dark color on the opaque Glass `#10131C` surface. The text backplate does not fix foreground contrast. The saved curated accent remains `#AACDFF`, so the static authored check still passes.

These are source-derived failing composites, not independently measured Android pixels. The host black-beam case is a finite input, not an unbounded color search.

**Smallest root correction/check:** check actual StatusBand alpha/background and active-control accent resolution at this boundary. Provide a readable presentation role/backplate where needed, without modifying authored colors. Check Light over black and bright scope content, Glass with the black-beam input, and one unselected essential outline. Keep the explicit custom-color correction and its disclosure. Then observe the same cases on the authorized target.

### F3, P2: density and authored plane are stored controls without the promised active-layout effect

**Requirement:** the shared editor controls background and density, with live preview and actual screen-space treatment.

**Source:**

- `ui/AppearanceEditor.kt:62-66,111-114,130-133` exposes plane and density inputs. The parser and codec retain them exactly.
- `ui/AppearancePalette.kt:68-89` transfers both fields to Palette/RoomStyle.
- `ui/Controls.kt:39-53` consumes density only in the padding of the old `LiveStyleSample`.
- An exact-commit search for `densityScale` found no other active layout consumer. The remaining matches are the model, codec, parser, editor and bridge.
- Settings rows use fixed padding at `ui/SettingsSheetAdapter.kt:287-311`. The editor uses fixed 8/6/10 dp spacing at `ui/AppearanceEditor.kt:85-92,187-201`. Console spacing remains fixed at `ui/Console.kt:210-228`.
- The only UI paint of a room plane is `ui/Sheets.kt:983`, using each immutable built-in ROOM tile's `room.plane`. `PhosphorScreen.kt:538-541` leaves the native scope underneath without painting the current authored plane. The current authored plane is otherwise used for conversion and contrast calculations, not visible current chrome.

**Worked schedule, derived from source:**

1. Keep all appearance fields fixed except density. PREVIEW 0.85, then 1.25.
2. The complete value changes, but the visible Appearance editor, section rows and console retain the same spacing. Only the separate legacy TRY sample uses the value when opened later.
3. Change only authored plane from black to a distinct RGB value and PREVIEW/APPLY. The draft and document change, but current chrome does not draw that plane. Legacy tile planes remain their own constants.

**Smallest root correction/check:** connect density to the intended shared chrome spacing while retaining minimum targets and text size. Give the plane field a concrete app-chrome preview/use, or explicitly resolve its intended role before claiming background editing. Do not recolor the native beam or HUD to make a theme knob appear functional. A two-value geometry comparison and one isolated plane change close these specific gaps.

### F4, P2: R04 motion ownership and purposeful glyph behavior are only partially integrated

**Requirement:** meaningful state animation, final reduced-motion authority, and clocks bounded by lifecycle and actual visibility.

**Source:**

- `MainActivity.kt:855-865` reads Android reduced motion once and supplies a plain Boolean to the composition. `onResume` at lines 871-887 does not refresh it. The only assignment from `readReducedMotion` is the onCreate assignment.
- `ui/Motion.kt:185-191` reads the current Android transition/animator scales, but no observed update path calls it after Activity creation.
- `ui/Console.kt:337-347` starts the BenchPost's three 160 ms timed reveals and 900 ms retirement without reduced-motion or chrome-visibility inputs.
- `ui/PhosphorScreen.kt:693-702` composes BenchPost whenever designators are enabled, even when a sheet covers that location. Curated Light enables designators.
- `ui/SettingsSheetAdapter.kt:285-311` swaps static `▴`/`▾` text and mounts/unmounts content. It has no state-change animation or rotating chevron for non-reduced Eased themes.
- `ui/Glyphs.kt:551-598,608-645` retains static settings/overflow vector rendering. The new `AppearanceMotionPolicy` has tests but no production caller in the inspected motion path.

**Worked schedules, derived from source:**

1. Launch with Android animations enabled. Enable Remove animations outside the app without destroying this Activity, then resume. The composition still receives the original false value. Burn-in and title marquee remain eligible at `Console.kt:62-67,242-246`.
2. With reduced motion already true, select a designator-enabled look. BenchPost still executes the 160/320/480 ms reveal schedule and retires at 1380 ms. If a sheet covers it, the same finite timers continue until completion or composition retirement. This is bounded work, not an alleged infinite leak, but it bypasses the stated reduced/visibility policy.
3. With curated Dark or Light and reduced motion off, expand a settings section. Its chevron changes glyph instantly. The requested meaningful expansion animation has not been integrated.

**Smallest root correction/check:** propagate current reduced-motion state through the Activity lifecycle, apply reduced/visible policy to BenchPost, and implement one finite expansion-chevron transition for the non-reduced/non-CUT path. Keep static CUT/reduced behavior. Observe each schedule with a controlled Compose clock or exact lifecycle adapter, then verify zero hidden callbacks. Do not add idle progress spinners, a theme poller or a new animation framework.

## Full requirement-to-source/check coverage

Paths below are relative to `app/src/main/kotlin/dev/phosphor/mobil3/`, unless identified as documents or tests. All lines refer to the pinned commit.

| Requirement | Source and inspected check | Assessment |
| --- | --- | --- |
| Canonical intent and R15 whole-section protocol | `MOBILE-EXPANSION-PLAN.md:207-227,294-309,321,329,332`, living-instrument vision, active `spec/EXPANSION.md:161-169` | S. Full section considered, one score and four finite findings. No newest-diff-only inference. |
| One authored model, four entry points, no separate UIs | `AppearanceValue.kt:5-69`, `AppearanceDocument.kt:52-57`, `AppearancePalette.kt:68-89`, `AppearanceEditor.kt:104-109`, screen provider `PhosphorScreen.kt:530-535` | S/I. Shared model and component tree are real. Curated/palette tests exercise mapping. Visible meaning remains partial under F2/F3 and the editor limitation below. |
| Exact thirteen built-in looks | `Palette.kt:88-224`, `AppearancePalette.kt:31-65`, `AppearanceMigration.kt:21-41` | S/I. `AppearancePaletteTest:11-38` compares all actual production rows and 64,350 coupled override combinations, including alpha-bearing separators. It is not a copied palette oracle. |
| Missing, sentinel, unknown and out-of-domain legacy values | `AppearancePalette.kt:31-39`, `AppearanceMigration.kt:63-75`, `MainActivity.kt:1957-1971` | S/I. Palette tests 41-64 and Migration tests 36-83 retain unknown identity, fallback, absence/sentinels and reject radius >64 rather than clamp. Current fallback rendering remains until migration succeeds. |
| Immutable legacy snapshots, current override snapshot, repeated migration | `AppearanceMigration.kt:24-41`, `AppearanceDocument.kt:58-61,86-95`, `AppearanceWorkflow.kt:42-64` | S/I. Initial migration stores all 13 plus optional current. Existing valid bytes win without writing. Migration tests 23-61 and Workflow tests 51-86 cover repeated and clean startup. No silent nearest-curated mapping found. |
| Preserve AMOLED clean default and existing user look | `AppearanceMigration.kt:28-41`, `AppearanceValue.kt:63-67`, Activity startup 858-860 | S/I for storage/startup. Clean defaults and existing valid documents have explicit tests. F1 affects later legacy edits, not initial resolver fidelity. U for same-package phone update. |
| Strict complete document, limits, identity/name rules and canonical checksum | `AppearanceDocument.kt:64-97`, `AppearanceDocumentCodec.kt:18-60,91-154`, shared `InstrumentPresetCodec.kt:158-305`, `InstrumentPresetCollection.kt:6-32` | S/I. Inspected tests cover exact fields/types, Unicode, duplicate keys, decimal-before-Float bounds, 32 users, 64 code points, UUID/reserved identities, 128 KiB, depth/nodes and fixed digests. No material codec defect found. |
| Defensive immutable records and provenance | `AppearanceDocument.kt:20-47,71-96`, `AppearanceCollection.kt:5-58` | S/I. Document tests check caller/list mutation, order, signed provenance extremes and identity associations. Legacy records remain immutable through collection APIs. |
| Schema /1 and /2 round trips and one-MiB bound | `SettingsArchive.kt:19-21,89,132-140,150-201,288-315` | S/I. `AppearanceArchiveTest:35-100` checks nested validity, /1 newer-key skip, wrong types, runtime-field exclusion and inner/outer limits. Actual Android SAF transport remains U. |
| Appearance omission and unrelated archive import preserve recoverable bytes | `AppearanceMigration.kt:44-60`, `SettingsArchive.kt:319-328`, Activity 666-689 | S/I. Migration test 85-90 avoids parsing corrupt old state for unrelated imports. Merge occurs under SettingsWriteOwner before snapshots/editor. |
| Explicit old-room archive patch and new-state precedence | `AppearanceMigration.kt:45-60`, Activity 668-698 | S/I. Migration tests 93-139 exercise complete replacement over corruption, legacy patches, exact merged provenance and retained user/legacy records. No new-state merge reads old corrupt bytes. |
| Startup corrupt-byte preservation and explicit repair | `AppearanceWorkflow.kt:42-64,118-131`, `AppearancePreferences.kt:12-18`, editor 96-101 | S/I. Workflow tests 88-149 cover absence, invalid types, corrupt strings, failed replacement and explicit complete replacement. The repair button discloses discarding the unavailable appearance document. |
| Commit-before-publish and exact rollback | `AppearancePreferences.kt:12-18`, `SettingsWriteOwner.kt:8-31`, Activity 2171-2212, workflow 101-115 | S/I. Controlled storage tests observe ordering and exact whitespace/raw-type/absence restoration, including thrown commit. Android disk durability remains U. |
| Shared uncertainty and ordered recovery | workflow 134-166, Activity 302-331,367-368,489-490,645-651, `InstrumentWorkflow.kt:184-209,259-272` | S/I. Five real dual-owner interleavings use controlled I/O ports. Failed appearance recovery prevents unrelated instrument save from clearing uncertainty. Recovery writes committed A, not preview B or rejected C. No contradiction found in this owner order. |
| Independent appearance/instrument import tickets | workflow 67-79,101-104,198, Activity 715-781,1594-1612 | S/I. Both tickets captured before picker, appearance checked before read and before acceptance. Workflow test 280-315 covers authored actions, cancellation and retirement. Interleaving tests 66-100,143-151 preserve later C and instrument association. Android callback scheduling remains U. |
| Preview, APPLY, CANCEL, SAVE separation | workflow 29,73-115,187-191, editor 71-110,145-165 | S/I. Preview is transient, APPLY and SAVE persist, cancel restores committed value. Owner/export test and recreation test cover separation. Live preview effectiveness is incomplete for F3 fields. |
| CRUD, active deletion, reset and modified summary | collection 13-41, workflow 31-39,91-99, editor 152-176 | S/I. Collection tests verify every record kind, active-delete preservation, reset retaining records/provenance, conflict rejection and user cap. UI action labels are present. Real focus/action reachability remains U. |
| No appearance source, native-tuning, permission or instrument-association side effects | workflow constructor/API, Activity 337-366,2101-2121, `captureInstrument` 263-269 | S/I. Ordinary appearance actions have no source/native setter authority. Pending-native interleaving retains association and request. Explicit shared recovery intentionally calls existing instrument recovery. Whole-settings import retains its existing tuning-restoration behavior and is not equivalent to an appearance-only action. U for physical source continuity. |
| Exclude transient sampled beam from persistence and export | private authored bridge 43-65, `Palette.withBeam` 41-52, Activity 337-355,1180-1187, saveTuning 1817-1874 | S/I. No legacy/custom theme key is derived from sampled ui.room during autosave. Palette and Workflow tests cover authored-vs-sampled separation. |
| All color/style inputs, custom validation and explicit readable proposal | editor 111-143, `AppearanceEditorValues.kt:8-37`, AppearanceValue bounds 35-39 | S/I. All 13 roles and all style fields are representable and validated. Eight parser tests include exact hex, numeric endpoints and no silent normalization. F3 distinguishes stored representability from working controls. |
| Light meaning | curated Light 45-50, shared glyph/controls, StatusBand 98-107 | S/I for warm palette and shared annotated styling. F2 shows dark ink on actual uncovered black scope. The always-dark editor is not a four-theme representative layout. U for daylight/large-font meaning review. |
| Dark meaning | curated Dark 51-56, shared Carved controls, finite transition screen 240-256 | S. Charcoal hierarchy, restrained accent and finite transitions are present. No independent pixels/frame timing. |
| Glass meaning and fallback | curated Glass 57-61, SheetHost 473-520, Console 223-246, StatusBand 98 | S. Opaque text/control interiors and explicit fallback copy exist. No blur or HUD-permission claim. F2 shows foreground contrast is still unresolved. U for moving-content/compositor readability. |
| AMOLED meaning | curated AMOLED 63-67, Engraved controls 133-146,179-193 | S/I for true-black authored base/surfaces and CUT semantics. Editor 57-58,85 unconditionally paints curated Dark's surface even for AMOLED. Treat the disclosed repair surface as a limitation, not proof the whole expanded AMOLED layout meets the no-grey-slabs contract. U for actual expanded layout. |
| Semantic roles preserve signal/edit/selection/context/unavailable/error meanings | Palette 30-52, Controls 324-344,402-421, editor 93-110,136-158,186-190 | S. Labels and separate runtime signals exist. No new idle busy indicator found. Runtime color distinction/readability needs F2 correction. |
| Purposeful vector/glyph animation | `SettingsGlyphIcon`, `OverflowHandleGlyph`, expandable section 285-311 | S. Static icons retain semantic shape. R04's non-reduced expansion animation is missing, F4. No need to invent signal animation for static mode legends. |
| Hidden/PiP/HUD/background motion ownership | screen 228-233,684-734, Console 62-88,211,242-246, Activity 88-99,889-903,1042-1070 | S/I. Early returns remove full UI when hidden/PiP. ROOM infinite pulse is gone. Band burn-in stops under sheet/overflow; console follows its actual visibility. Reduced-motion freshness and BenchPost remain F4. U for real callback/frame counts and paused-but-visible windows. |
| Finite authored revision replacement | screen 234-257, workflow 198, palette 59-75 | S. Cancellable LaunchedEffect uses authored revision, captures lastShown and guards the pre-effect composition. Measured beam changes do not restart it. Source assertion verifies wiring, not frames. No definite replacement bug found. U for A/B/C Compose frame schedule. |
| Preview cancellation on close, collapse, background, PiP and retirement | editor DisposableEffect 55-56, SheetHost onClosing 301-307, Settings 1175-1179, Activity 889-903,1042-1070 | S/I. Owner cancellation/recreation tests and source hooks present. Disposal and configuration timing remain U. Ordinary Android configuration handling may preserve draft only while the same owner survives. |
| Expandable Appearance, original ROOM reachability and unchanged opening ownership | Sheets 1414-1418,1494-1525, settings presentation owner, gesture child hook | S. Legacy picker remains reachable as required pending acceptance. Multiple sections share one screen-owned scroll. Editor fields use settingsChildInput rather than new dismissal thresholds. No separate R10 review score is issued here. U for exact opening fixtures and physical gesture regressions on the final artifact. |
| 48 dp targets, wrapping, keyboard, TalkBack/switch focus | editor 186-205, settings rows 289-307, Type.kt, SettingsControlAccess.kt, ROOM 965-989 | S. Editor/rows carry target sizes, text labels, role semantics and focus borders. Collapsed content leaves composition. Do not infer actual 48 dp targets or clipping solely from constants elsewhere, because Compose hit slop and font/viewport behavior need observation. U for TalkBack, switch, keyboard order, IME, large fonts and rotated multi-window layouts. |
| Better actual screen-space use, beam-first hierarchy, representative layouts before replacement | screen full-bleed native surface and shared chrome, Sheets responsive widths 428-436, retained ROOM | S. Beam remains primary and no source restyle is introduced. F3 density is not applied to actual chrome spacing. No exact four collapsed/expanded layout acceptance receipt was found. Legacy picker was correctly not retired. |
| Manual availability and honest delivery state | `ui/ManualContent.kt:81-82`, integration receipt 27-29 | S. Appearance is explicitly `In development`. Do not promote this review, host gate or code presence to manual/device acceptance. |

## Intent, accuracy and evidence assessment

**Intent fidelity:** the authoring/persistence design respects Ben's separate appearance and instrument concepts. The single-owner goal is weakened by the retained raw override state in F1. The visible customization and motion outcomes are less complete than the data model.

**Functional accuracy:** no material defect found in strict document validation, immutable collection behavior, initial legacy resolution, corrupt-state preservation, one-key rollback, independent tickets or ordered cross-family recovery within the inspected seams. These are supported by substantial actual-source inherited tests. F1 is an adapter-state error that those tests do not cover.

**UX/accessibility:** the editor has exact labels, visible validation, explicit actions and an opaque repair surface. It is a long fixed-dark form rather than accepted four-theme representative layouts. F2 and F3 are concrete failures of actual readability/customization. Large-font, focus, TalkBack and compact viewport claims remain open.

**Lifecycle/performance/privacy:** no new appearance poller, source authority, native tuning setter or permission path was introduced in ordinary appearance actions. Finite palette ownership is materially improved and the idle ROOM clock is removed. F4 and the unobserved frame/callback boundaries prevent full R04 acceptance. Offscreen GPU retention tests do not resolve these issues.

## Finite root follow-through

1. Correct F1 and add its two adapter schedules. Assert untouched authored style fields and legacy label consistency.
2. Correct F2 at actual palette/material consumers. Check the three named composites without altering authored values.
3. Make the two F3 fields visibly meaningful within app chrome. Compare two density values and one isolated plane change.
4. Close F4 with one reduced-motion resume schedule, one reduced/obscured BenchPost schedule and one finite chevron transition schedule.
5. Run the existing root-owned exact-source integration gate after those corrections. Preserve the inherited gate and this ROUND 1 report. Do not replace failing behavior checks with mere source-form assertions.
6. On the authorized target, perform one bounded combined acceptance pass: four collapsed/expanded looks, bright/black Glass, large fonts and input/focus, preview-close-recreate, exact migration/update survival, rapid A/B/C transition replacement and hidden-clock cancellation. Include source continuity and unchanged instrument association/tuning. Retain exact dual-APK provenance/readback. Current receipts establish no such execution.

These checks are a finite set, not permission for broad refactoring, unlimited fuzzing, source/renderer rewrites, or operation of an excluded phone. Root owns all follow-through. Below 8, R15 permits correction and the next full round, up to four total. This original report must remain unchanged. Later corrections need a separate addendum.

## Readset, hashes and release

Private directory: `/home/ben/.jcode/scratch/section10-round1-485bc9-jjzFcM`.

- `objects.tsv` lists repository, exact commit, Git blob and path for all 63 snapshots. SHA-256: `54ecd314f5ad623c6cd2b92a82823f4d335d198551053e7c8231b5171c48b1e1`.
- `SOURCE-SHA256.txt` lists exact bytes for all snapshots. SHA-256: `2de3b518cd06975c0c5bd789898360bf16ce24985cac28051601e0f847a284ed`.
- `INHERITED-SOURCE-COMPARISON.tsv` records 46 matching production/test hashes. SHA-256: `75cb6be4c286821eea75d501006336220e2353f0a4eabd9187197aee3a5d58ea`.
- `inherited/` retains copies of the gate logs, result JSON and pre/post inventories. The report records the separately rehashed retained APK/archive/runner evidence without copying those large binaries.
- `MANIFEST.sha256` seals this report, the source/readset files and the retained evidence copies. `SEAL.txt` records the final report and manifest hashes. Read-only file permissions are a seal against accidental changes, not a claim of filesystem immutability against the account owner.

**All source, read and build holds are explicitly released.** No implementation, build, device or Git ownership was taken from root. No worker, background job or service was started. Product source reading stopped at 15:30:59 UTC. The bounded review is complete and no further source reading is planned.
