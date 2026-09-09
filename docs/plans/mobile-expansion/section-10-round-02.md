# SECTION 10 R04/R12 independent full review, ROUND 2

## Decision

**R15 overall score: 7/10.** This is the second full-section critique, not a newest-diff review, device acceptance, or release approval.

The original F1, F3 and F4 source defects are corrected at their actual consumers. The original F2 StatusBand and black-beam control schedules are also corrected. One material R12 contrast gap remains in the same presentation boundary: the pause-state label still draws dark Light-theme text directly over the native scope. This finding, not missing hardware alone, prevents an 8.

**Largest remaining intent mismatch:** a curated look must not make the instrument's held/black/live-source explanation hard to read. That explanation remains outside the new readable presentation layer. The appearance core, migration, exact storage and recovery behavior show no additional material defect in this review's inspected seams.

The original ROUND 1 report and correction receipts remain unchanged. Do not reinterpret this review as undoing the corrections that are present.

## Identity, bounds and evidence

- Assigned reviewer: independent GPT Astra/high. `swarm list_models` identified the current model as `gpt-6-astra`, exposed the `openai-oauth:gpt-6-astra` pin and listed high as an effort option. The task supplied the high assignment. No route or effort setting was changed.
- Mobile commit: `4f285a6fc705c3ba3e81a91678c18a5f16158d4f`, resolved from `4f285a6`.
- Mobile tree: `5fec6c2c562cc2e029649e665061b0714408124b`.
- Shared commit: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Shared tree: `51f828e60c5bbfd7003694bf28b7455daf07db1e`.
- Private evidence directory: `/home/ben/.jcode/scratch/section10-round2-4f285a6-BVNeMq`.
- Readset: 71 mobile Git-object snapshots, comprising 37 production files, 17 test files and 17 documents. The production count includes the Rust Cargo manifest inspected for the sibling-engine seam. This stays below the 45-production-file ceiling. Large files were inspected by requirement-relevant regions. READSET.md states the limits.
- Shared inspection established the exact commit/tree identity only. Appearance authoring and presentation resolve through Kotlin owners. This is not an independent native/GPU implementation audit.
- Governance, context standards, complete-build practice, private-folder rules and house UI rules were read. The canonical mobile AMOLED requirement supersedes the generic house default. The explicit no-Python boundary supersedes prose-linter instructions.
- No changing live product source was read. The concurrent MainActivity openLink work was outside this pinned review.
- No build, test, runtime, Android/JNI/GPU execution, installation, ADB, GUI, audio, network, service, download, Python, Git mutation, product edit or worker operation occurred. Only private review/readset files were created. Model-catalog inspection did not start a worker.

Evidence labels:

- **S:** independently inspected pinned source or test implementation. A test body is not a test execution.
- **I:** inherited coordinator execution receipt. No test counts are claimed as independently rerun.
- **U:** runtime, physical layout, accessibility, persistence or exact-artifact acceptance remains unobserved here.

### Inherited gate409985gj6g only

The exact committed receipt is `docs/plans/mobile-expansion/section-10-correction-02.md:57-68` at the mobile pin.

It records a passing frozen working-source rerun at 2026-09-09 01:30 UTC: 891 JVM tests across 74 suites, 126 native tests, three offscreen GPU tests, Android compilation, lint, both debug APKs, engine/release-helper/source-boundary checks, and unchanged mobile/shared inventories.

The receipt explicitly does not establish a final clean reviewed freeze or device acceptance. I read the committed receipt, not the mutable build tree, APKs or XML archive. I did not independently rehash its binaries or bind every pinned source file to its working-source inventory. Those remain inherited claims, not an exact-commit execution claim by this reviewer.

Retained prefix: `dev/scratch/mobile-expansion-20260908T001819Z/appearance-correction02-integration-r2`.

| Inherited artifact | SHA-256 copied from the committed receipt |
| --- | --- |
| App APK | `9d8efbc099e40ef444f2e78364673a28696ca7ba95d419c5df43a7851a9c3a58` |
| Companion APK | `b0ed73a8b86af785406977f2630e9883a5e9229b454091a0fcd3df8364a19340` |
| JVM XML archive | `e34e83729972b7adf0ba6d9e5f29988afa265c68a48f70067c55b9ffa4d1ba02` |
| Runner | `5b8315e6457b6b98675157715879a712858b23de000542f622e38ced272a18d8` |

The earlier correction host gate and older core/runtime gates are historical supporting receipts. Their counts are not added to 891. The 64,350 legacy combinations are cases inside one test, not 64,350 additional JUnit tests.

The prior StageGesturePolicyTest failure was a source-form assertion. Current lines 245-250 require the console bounds report before the first density gutter and before the density-scaled inner padding. Production Console lines 227-237 have that ordering. This inspection supports the described correction without claiming a new execution or physical gesture acceptance.

## F1 through F4: complete current traces

Paths in this section are relative to `app/src/main/kotlin/dev/phosphor/mobil3/`. Bare appearance-core basenames refer to `settings/appearance/`. Test paths are relative to `app/src/test/kotlin/dev/phosphor/mobil3/`. All line numbers use the mobile pin.

### F1: current ROOM controls, corrected in source

1. `ui/Sheets.kt:1041-1079` reads `state.appearanceStyle` for labels and all four next actions. CORNERS and LABELS no longer send a stale tuple or a misleading match state.
2. `ui/RoomStyle.kt:85-96` emits a one-field StyleOverride from the displayed style. CORNERS cycles 0, 8, 12. An authored radius outside those choices starts at 0.
3. `MainActivity.kt:2112-2117` passes `owner.effective` to `AppearancePalette.editCurrentStyle`. Saved-record association supplies only the association, not a style baseline.
4. `ui/AppearancePalette.kt:92-104` requires exactly one field, starts from the complete current style and preserves authored colors and palette flags.
5. `ui/RoomStyle.kt:107-124` retains intentional coupling. FEEL selects the character defaults. MOTION selects its duration default. CORNERS and LABELS preserve every other field.
6. `AppearanceWorkflow.kt:101-115` commits before publication. `MainActivity.kt:337-355` publishes the effective value/style and clears the obsolete in-memory override tuple. Legacy preference bytes remain provenance.
7. `MainActivity.kt:2104-2109` selects a built-in legacy room without stale overrides. `saveTuning:1820-1878` does not rewrite theme or appearance keys.

**Original schedules:** migrated Glass plus Annotated override, then curated AMOLED APPLY, then CORNERS now yields AMOLED with radius 8 only. A modified curated-associated style with duration 1.7, density 1.25 and radius 41 now reaches radius 0 without restoring the saved style. `ui/AppearancePresentationTest.kt:31-89` checks those exact owner/adapter schedules, every untouched authored field, coupled FEEL and stale-tuple rejection. Lines 239-252 bind the tested adapter to Activity and ROOM source.

Assessment: S/I correction established. Actual Android event delivery and persistence readback remain U, not an additional source finding.

### F2: original composites corrected, one remaining material consumer below

1. `ui/Palette.kt:41-53` and `AppearancePresentationPolicy.kt:5-9` resolve opaque foregrounds against each exact opaque component background. Passing requested colors stay unchanged. Failing presentation colors use readable black or white.
2. `ui/Console.kt:83-138` gives StatusBand its authored opaque plane and resolves ink/ink2/muted against that plane. Source, gain and contextual text no longer reduce alpha over the native scope.
3. `ui/Controls.kt:330-362,403-436` supplies opaque FlatKey/ChipCell interiors, resolved selected text and resolved essential outlines. `Console.kt:317-336` does the same for the overflow handle.
4. `ui/Controls.kt:218-226,315-316` protects stone labels with their exact opaque text interior. `SettingsSheetAdapter.kt:285-309` resolves section-header roles against its opaque section surface.
5. `ui/Palette.kt:58-69` still changes transient beam/structural accent only. Presentation copies do not mutate AppearanceValue, exports or native beam values. Editor disclosure and explicit authored correction remain at `ui/AppearanceEditor.kt:98,140-146`.

`ui/AppearancePresentationTest.kt:101-151` checks Light StatusBand over black/white content, Glass with `withBeam(0,0,0)`, pressed and unpressed selected text, unselected essential boundaries and unchanged authored bytes. Lines 255-279 check the real named consumers. These checks do not cover the separate pause-status Prose call.

Assessment: the two original F2 examples are corrected in S/I. The full normal-text requirement remains open under R2-F2 below.

### F3: density and plane reach active chrome, corrected in source

`RoomStyle.space` at `ui/RoomStyle.kt:96` uses the same `AppearancePresentationPolicy.spacing` function at line 11. The active editor uses it for the frame, inner padding, gaps and input/button padding at `AppearanceEditor.kt:88-96,190-209`. Settings section rows use it at `SettingsSheetAdapter.kt:300-313`. Console uses it at `Console.kt:233-237,257-295`.

A density-only change from 0.85 to 1.25 changes shared spacing. For example, a 12 dp base becomes 10.2 versus 15 dp by the inspected multiplication. Text remains expressed in sp. Editor, FlatKey, ChipCell and section-header targets retain explicit 48 dp minima rather than multiplying their minima by density.

An isolated authored plane change reaches StatusBand at `Console.kt:84,102`, console gutters at lines 234-235, and the editor frame at `AppearanceEditor.kt:60-61,88`. The native full-bleed surface at `PhosphorScreen.kt:538-541` is not repainted to fake a working background knob. HUD transparency is not involved.

`ui/AppearancePresentationTest.kt:153-179,255-279` covers spacing endpoints, plane isolation and production wiring. Its pure target helper is not proof of actual Compose geometry. The independent inspection of unconditional control minima supplies a separate source check.

Assessment: S/I closes the original dead-consumer gaps. Portrait/landscape/multi-window/HUD dimensions, accessible focus and measured geometry remain U.

### F4: current reduced motion and finite purposeful state, corrected in source

- `MainActivity.kt:140` makes reduced motion observable Compose state. Lines 856-866 initialize the current value and provide it to PhosphorScreen. Lines 872-875 refresh it on resume and give native motion the same value.
- `PhosphorScreen.kt:228-233` removes the full chrome composition when presentation is hidden or PiP is active. Its provider at lines 530-532 carries current reduced motion and explicit authored style.
- `Console.kt:345-366` keys a cancellable effect on the computed POST policy. `AppearancePresentationPolicy.kt:16-35` produces no timed work for hidden/static paths. Eligible reveal performs three 160 ms waits, then one 900 ms retirement wait. Reduced/CUT displays all lines statically.
- `PhosphorScreen.kt:693-702` supplies sheet/overflow visibility. `BenchPost` retains retirement within its composition owner. Hiding an unfinished reveal cancels that effect. Reopening after retirement within that owner does not restart it.
- `SettingsSheetAdapter.kt:309-313` keeps immediate content ownership and changes a finite chevron. `Glyphs.kt:647-655` maps expansion to 0/180 degrees. `Glyphs.kt:609-643` maps overflow's existing active state to a finite index movement.
- `Motion.kt:200-209` uses current reduced-motion/CUT policy and bounded 80-200 ms state transitions, without an idle timer. Its visible flags are constant because caller composition owns glyph presence. This is not independent measured-occlusion detection.
- Burn-in at `Console.kt:64-78` and marquee at lines 251-255 obey caller visibility and reduced motion. The ROOM tile pulse is absent from `Sheets.kt:955-1089`.

`ui/AppearancePresentationTest.kt:181-234` tests every POST policy combination, exact finite callback order, coroutine cancellation and glyph endpoints. Lines 282-304 bind policy to actual source consumers. Coroutine cancellation through controlled delay is not Compose-clock or Android-resume execution.

Assessment: S/I closes the named F4 source gaps. Exact frames, Android callback scheduling, offscreen/covered components and jank remain U. No new poller, audio reader, service or idle progress indicator was found in this appearance path.

## Remaining finite material finding

### R2-F2, P2: pause-status text bypasses readable presentation

**Requirement:** canonical section 10 at `MOBILE-EXPANSION-PLAN.md:223-226`, active `spec/EXPANSION.md:165-169`, and appearance contract lines 43, 80, 96. Normal-size contextual state text requires 4.5:1. The living-instrument vision also requires an intentional held image to be labeled truthfully.

**Exact source trace:**

- `AppearanceValue.kt:45-49` supplies curated Light muted ink `#615847`.
- `AppearancePalette.kt:68-71` converts that exact authored color. `Palette.withBeam:58-69` does not change muted ink.
- `PhosphorScreen.kt:234-257` eventually presents that exact target palette after its finite authored transition.
- `MainActivity.kt:1469-1476` copies actual native pause/black/frame state into ScopeUiState. `ScopeUiState.kt:46-53` obtains the label from `PauseDisplayPolicy.status`.
- `PauseDisplayPolicy.kt:12-15` returns labels including `display black`, `display held`, `no held frame` and the live-source suffix.
- `PhosphorScreen.kt:679-680` draws `Prose(state.pauseLabel, p.muted, ...)` directly over the full-bleed native surface. Its only local modifier is alignment and top padding.
- `ui/Type.kt:29,56-73` makes this normal 13 sp text. Prose adds no backplate or contrast resolution. The parent stage boxes at `PhosphorScreen.kt:538-541,551-557` add none either.
- The corrected StatusBand at lines 684-690 is a separate sibling, not the background or text owner of this label.

**Bounded user schedule:**

1. Apply curated Light and let the finite theme transition finish.
2. Set BAND to OFF so the separate status band cannot supply incidental coverage.
3. Select BLACK pause presentation and pause a display with a retained frame.
4. Close Settings and leave the stage unobscured.
5. The pause explanation uses `#615847` over the native black ground, without the new protection.

This is a source-derived failing composite, not an Android pixel observation. All three channels of `#615847` are at most 97/255. The monotone sRGB formula in `AppearanceValue.kt:110-115` therefore bounds its luminance below 0.12. Its contrast against black is below `(0.12 + 0.05) / 0.05 = 3.4`, already below 4.5. No hardware-specific inference or broad color search is needed. The actual ratio is not claimed as a measured device value.

**Material effect:** the app's curated entry point weakens the state explanation precisely when black output could otherwise be confused with a stopped or failed source. This is not merely decorative text or a custom combination the user intentionally made unreadable.

**Why the inherited checks can pass:** `AppearanceValueTest.kt:9-14` checks authored roles on authored backplates. `AppearancePresentationTest.kt:101-137,255-279` checks StatusBand and named controls. Neither supplies the actual pause-label foreground/background pair or verifies a backplate on this sibling Prose call.

**Smallest coordinator correction and acceptance:** give this one status consumer an opaque authored plane/backplate and resolve its text against that same plane, or place it within an existing protected status owner. Keep it visible with BAND OFF. Do not modify saved colors, native BLACK/HOLD behavior or source ownership.

Add one production-boundary contrast regression for the Light/BAND-OFF/BLACK schedule and its white/bright-held-content counterpart. Check that the presentation remains at least 4.5:1 and the authored document bytes remain unchanged. Then observe the same two states in the coordinator's authorized target pass. No renderer rewrite, new theme engine or general color search is needed.

## Full requirement-to-evidence coverage

The following table covers the complete section, including unchanged core/runtime behavior. S/I marks source inspection plus the inherited gate, not independent execution. U is carried separately.

| Requirement | Pinned source and inspected checks | Disposition |
| --- | --- | --- |
| Canonical intent and R15 whole-section critique | Plan 207-227, 294-310, 321, 329, 332. Living-instrument vision. Active EXPANSION 161-169. Original full report and correction 02. | S. Whole section reviewed. One overall score. Round 2 of four maximum. |
| One authored model and four entry points | AppearanceValue 5-69, AppearanceDocument 52-57, AppearancePalette 68-89, AppearanceEditor 108-114, PhosphorScreen 530-532. Palette and value tests. | S/I. Same component tree and semantic roles, not four engines. |
| Light contract | Curated Light 45-50, shared annotated/mono style, corrected StatusBand and shared section rows. | S/I for warm paper, compact labels and shared controls. R2-F2 remains. Four-layout/daylight evidence U. |
| Dark contract | Curated Dark 51-56, RoomStyle/Controls, finite authored and glyph transitions. | S/I for charcoal hierarchy and restrained state changes. Pixels/jank U. |
| Glass contract and opaque fallback | Curated Glass 57-61, Sheets 473-520, Console 229-237, Palette readable roles. Presentation test 117-151. | S/I. Sharp eased curated Glass, translucent outer material and protected interiors. No blur or HUD capability claim. Moving compositor evidence U. |
| AMOLED clean default and true black | Curated AMOLED 63-67, AppearanceMigration 28-30, AppearanceValueTest 17-25, shared Engraved controls. | S/I for authored black surfaces and CUT. Disclosed dark repair editor remains a visual limitation, not an undisclosed saved-theme rewrite. Expanded layout U. |
| All 13 legacy palettes and coupled overrides | Palette 105-243, AppearancePalette 31-65, RoomStyle 52-80,107-124. AppearancePaletteTest 11-91 checks actual production rows and 64,350 combinations. | S/I. Exact RGB/ARGB and style preserved. No nearest-curated substitution. |
| Missing/sentinel/unknown/invalid legacy values | AppearancePalette 31-39, AppearanceMigration 63-75, Activity 1960-1975. Migration tests 36-83. | S/I. Raw unknown identity retained with Blossom Dark fallback. Missing differs from sentinels. Radius outside 0..64 rejects, not clamps. |
| Immutable snapshots, current overrides and repeated migration | AppearanceMigration 21-41, AppearanceDocument 58-61,86-95, Workflow 42-64. Migration tests 23-61 and Workflow tests 51-85. | S/I. All 13 plus optional current snapshot. Valid existing state wins without writing. Same-package update U. |
| Complete document schema and bounds | AppearanceDocumentCodec 14-60,91-154, AppearanceDocument 64-97. Codec tests 23-127,143-303 and Document tests 91-169. | S/I. Exact fields/types, RGB/ARGB, enums, decimal-before-Float bounds, 128 KiB, user32/name64, checksum and reserved identities. No material codec defect found. |
| Strict grammar, Unicode and canonical bytes | InstrumentPresetCodec 158-305, InstrumentPresetCollection 6-32. Codec tests 23-31,240-290. | S/I. Shared grammar retains duplicate/trailing/depth/node rejection. Fixed digest vectors and strict UTF-8 paths present. No parser fork. |
| Defensive values, record order and raw provenance | AppearanceDocument 20-47,71-96. Document tests 48-59,123-164. | S/I. Defensive list copies, locale-independent conflicts, immutable provenance and exact signed integers. |
| Explicit appearance CRUD/apply/reset | AppearanceCollection 13-52, Workflow 91-115. Collection tests 80-169 and Workflow tests 250-277. | S/I. Active deletion keeps exact value, reset keeps records/provenance, failed validation preserves previous state. |
| Full shared editor, exact inputs and modified summary | AppearanceEditor 54-180, EditorValues 8-38, Workflow 31-39. Eight parser tests and runtime wiring test 89-110. | S/I. All 13 color roles and style fields, separate draft selection/preview/apply/save/cancel and explicit readable proposal. Actual action reachability U. |
| Preview is reversible and excluded from storage/export/instrument | Workflow 29,73-115,178-191. Activity 224-240,263-269,1820-1878. Workflow tests 152-179,298-333. | S/I. No authored save before explicit action. Recreated owner reads committed state. Android lifecycle disk behavior U. |
| Current ROOM actions use effective appearance | Full F1 trace and Presentation tests 31-89,239-252. | S/I. Original authority defects corrected. Raw legacy preferences remain inert provenance. |
| Schema /1 and /2, nested validation and outer cap | SettingsArchive 19-21,89,132-140,150-180,204-328. AppearanceArchiveTest 35-100. | S/I. /1 skips newer field after archive validation. /2 validates nested appearance. 1 MiB outer cap retained. SAF transport U. |
| Omitted appearance preserves current/corrupt bytes | AppearanceMigration 44-60 and SettingsArchive 319-328. Migration test 85-90 and Archive test 62-74. | S/I. Unrelated import does not parse or rewrite appearance bytes. |
| Explicit legacy patch, new-state precedence, complete repair | AppearanceMigration 45-60. Migration tests 93-139. Activity 668-699. | S/I. Complete replacement validates without old bytes. Legacy patch preserves saved users and immutable legacy records. |
| Commit-before-publish and exact rollback | AppearancePreferences 12-18, SettingsWriteOwner 8-31, Workflow 101-115, Activity 2167-2207. Workflow tests 88-149,168-219. | S/I. Raw absence/type/whitespace restoration. Thrown commit still rolls back. Real SharedPreferences durability U. |
| Corrupt-state recovery and shared uncertainty | Workflow 118-166, Activity 303-332,368-369,646-651, InstrumentWorkflow 184-209,259-272. Interleaving tests 103-140. | S/I. No default over corrupt bytes. Failure blocks edits. Recovery saves authoritative appearance before instrument recovery. |
| Independent stale import tickets | Workflow 67-79,101-104,198, Activity 716-782,1597-1614. Workflow tests 280-315 and interleaving tests 66-100,143-151. | S/I. Captured before picker and checked before read and acceptance. Later appearance choice wins. Actual Android callback interleavings U. |
| No source/native tuning/permission/instrument side effects | Ordinary Activity actions 359-367,2104-2117, captureInstrument 263-269, Workflow API. Interleaving tests 84-100, runtime wiring 44-55. | S/I. Ordinary look operations lack those calls. Shared recovery is explicitly separate. Whole-settings import retains its existing tuning-restore semantics. Source continuity U. |
| Sampled beam exclusion | Palette 58-69, AppearancePalette private authored adapter 43-65, Activity 337-355,1180-1190 and saveTuning. Palette test 68-77, Workflow test 152-165. | S/I. Beam tick stays transient. No sampled value enters an appearance document or instrument snapshot. |
| Active spacing/background and beam-first layout | Full F3 trace, Dimens 13-48, Sheets 428-440, PhosphorScreen 538-557. Presentation tests 153-179. | S/I for real shared spacing/plane consumers and unchanged full-bleed native surface. Measured portrait/landscape/window/HUD layouts U. |
| 48 dp, accessible type, semantics, focus and collapsed ownership | Editor 190-209, Controls 339-345,419-424, SettingsSheetAdapter 291-313, SettingsControlAccess 30-85, Type 34-73. SettingsControlAccessTest 28-140. | S/I for minima, text labels, selected/range semantics and wrapping. Collapsed content leaves composition. TalkBack/switch/keyboard order, IME and large-font geometry U. |
| Preserve opening/dismissal/anchor ownership | SettingsGestureAdapter 149-185, SettingsInteraction 138-168, SettingsSheetAdapter 257-313, Sheets 295-307,1167-1179,1490-1525. StageGesturePolicyTest 245-256. | S/I. Chevron does not animate content geometry under the anchor. Existing gesture owner and child hook remain. Physical regression fixtures U. No separate R10 score issued. |
| Normal text and essential-boundary contrast | AppearanceContrast 73-115, Palette 41-53 and F2 consumers/tests. | S/I for corrected named controls and StatusBand. R2-F2 is a definite remaining source-derived normal-text failure. Actual composited pixels U. |
| Purposeful vector animation without idle busy state | Glyphs 609-655, Motion 200-209, SettingsSheetAdapter 309. Presentation tests 225-234,295-304. | S/I. Finite expansion/overflow endpoints. No idle progress spinner added. Frames/jank U. |
| Lifecycle/component/reduced-motion clock gates | Full F4 trace, early screen returns, Console burn-in/marquee/POST, Activity resume/stop/destroy. Presentation tests 181-234,282-304. | S/I. Original gaps corrected. Real visibility cancellation and resumed settings propagation U. |
| Rapid authored revision replacement, not beam-tint restarts | PhosphorScreen 234-257 and Workflow 198. Runtime wiring test 68-75. | S. Cancellable finite revision effect starts from lastShown and guards the pre-effect frame. No definite new source defect found. A/B/C Compose-clock acceptance U. |
| Preview retirement across close/collapse/background/PiP/destroy | Editor 55-56, Sheets 301-307,1179, section conditional content, Activity 892-904,1045-1074. Workflow tests 298-333 and runtime wiring 57-65. | S/I. Explicit cancellation hooks and committed recreation. Actual callback order U. |
| Design contracts and representative collapsed/expanded layouts before picker retirement | Appearance contract 41-52,99, shared six section rows and retained legacy ROOM at Sheets 1414-1418. | S for preimplementation contracts and unchanged hierarchy. Representative four-look physical evidence absent. Legacy picker correctly remains reachable. |
| Privacy and honest availability | Authored model excludes source/device fields, SettingsArchive whitelist, ManualContent 81-82, correction receipt 61-68. | S/I. No new permission, polling, network or service owner in appearance. Manual remains In development. No installation/device claim. |

## Assessment and finite coordinator follow-through

Intent fidelity is substantially improved: current-look authority now survives one-field ROOM changes, saved associations and migration. Functional preservation remains supported by the inspected strict core and inherited host integration. No further material corruption, takeover or recovery defect was established.

UX/accessibility remains the limiting dimension because of R2-F2. The long fixed-dark repair editor is explicitly disclosed and useful for recovery. It is not accepted evidence of the four complete expanded design contracts. Treat that and real layout/focus observations as acceptance limitations, not invented hardware defects.

Lifecycle/performance/privacy have stronger actual consumer wiring. The inherited GPU tests do not measure Compose clocks, Android lifecycle behavior, text contrast or accessibility. No claim of those outcomes follows from their passing count.

Smallest remaining schedule for the coordinator:

1. Correct R2-F2 at the existing pause-status consumer and add the two named contrast cases with authored-byte preservation.
2. Retain all existing F1-F4 behavior tests. Run the coordinator-owned exact-source integration gate after that correction, preserving prior failed and passing receipts.
3. Obtain the next full independent R15 review only after correction and checks. This was round 2, leaving at most two total full rounds. Stop at the first score of 8 or more. Do not restart the four-round count.
4. Keep one bounded authorized target acceptance pass: four collapsed/expanded looks, Light pause status and bright/black Glass, density endpoints, plane-only preview, 48 dp/large fonts/focus, close-collapse-recreate, same-package migration/archive survival, A/B/C transition replacement, reduced-motion resume and hidden-clock cancellation. Observe source continuity and unchanged instrument association. Bind the pass to exact dual-APK hashes/readback and source inventories.

This review does not authorize a device, build, installation or release action. Root owns those checks. Missing target evidence remains open but does not create extra speculative findings.

## Seal and explicit release

READSET.tsv records each exact Git blob and SHA-256. READSET.md records inspection scope. `snapshot.sh`, `supplement.sh` and `metadata.sh` reproduce only the private object copies and pin metadata. `seal.sh` verifies copied blob identities and hashes, then creates SOURCE-SHA256.txt, SHELL-SHA256.txt, MANIFEST.sha256 and SEAL.txt. These are review artifacts, not product tests.

The seal records the final source-read stop and release timestamp, report/readset/manifest hashes and byte verification result. Read-only permissions prevent accidental edits but do not claim filesystem immutability against the account owner.

**All source, read and build holds are explicitly released on sealing.** No build slot, device, implementation or Git ownership was taken. No worker, background job or service was started. Completion is reported only after the seal succeeds, before 01:59 UTC and within the bounded review window. No source or artifact reread follows completion.
