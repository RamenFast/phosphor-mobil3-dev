# Section 10 runtime writer handoff

## Adapter decisions recorded before runtime code, 2026-09-08 14:33 UTC

This writer owns only the requested Activity/UI paths and the new appearance owner, preferences adapter, editor and tests. Root owns Android builds, integration, device acceptance and commits. Source and build holds end before 15:10 UTC.

1. `AppearanceWorkflow` is a pure UI-thread owner. It separates committed documents, transient preview, appearance revision tickets, unavailable/corrupt storage and shared persistence uncertainty. It has no native, source or instrument setter API.
2. `AppearancePreferences` is a production callback adapter over the actual preferences map and one-key synchronous commit. It snapshots presence and the exact raw value before entering `SettingsWriteOwner.commit`. The Activity supplies the existing typed preference snapshot restoration for rollback. No JSON re-encoding occurs during rollback.
3. Startup reads valid `appearance_state` without writing. Absent state uses the released `AppearanceMigration.initial` and commits before publication. Invalid legacy or corrupt new data leaves legacy rendering and original bytes untouched. Only an explicitly labeled complete replacement can repair unavailable state.
4. The Activity publishes effective authored value, explicit `RoomStyle`, collection summary and revision. `baseRoom` contains only authored palette values. The existing beam tick remains the only transient tint supplier.
5. Whole-settings pickers capture the independent appearance ticket beside the existing instrument ticket before launch. Both are checked before reading and again before acceptance. `AppearanceMigration.merge` runs inside `SettingsWriteOwner`, before snapshots and editor construction. Successful import reloads the committed appearance on the same UI callback.
6. Every typed rollback failure reaches both owners. The production instrument persistence callback first asks appearance to persist its known authoritative committed document. Only success allows the existing instrument recovery save. This does not fork instrument recovery or cancel native work for appearance actions.
7. Legacy ROOM remains reachable. Room selection and coupled override actions call Activity appearance operations. Legacy compatibility keys become inert and lifecycle save never writes them.
8. The existing expandable Appearance body owns the editor composition. Disposal, sheet close, group collapse, background, PiP and owner retirement cancel preview. Editor draft values are local, exact and validated. PREVIEW, APPLY, CANCEL, named save/update/rename/delete and RESET remain separate actions.
9. All thirteen authored color roles and every style field have labeled exact RGB/ARGB or numeric controls. Readability failures are named. An explicit correction changes the draft only. A labeled safe editor presentation keeps repair controls readable without changing saved values.
10. Finite palette changes key on authored revision and start from the last displayed palette. Beam updates do not restart the transition. ROOM decorative breath is removed. Burn-in receives actual chrome visibility and remains absent under hidden, PiP or reduced-motion presentation.

## Validation and boundary

Pending implementation and actual-source host checks. Android compilation, Android callback execution, preferences disk durability, Glass pixels, accessibility and physical acceptance remain root checks. No device or target-build acceptance is claimed.

## Refined adapter decisions and approved additions, 14:52 UTC

- Root approved the new `settings/appearance/AppearanceEditorValues.kt` and matching new test at14:47UTC. The editor calls this exact pure parser. It validates decimal bounds before Float conversion. The explicit readable proposal replaces all color roles with the matching curated light/dark colors and preserves every authored style field. The action label discloses that complete color replacement.
- Instrument persistence refuses to save while appearance durability is uncertain. Explicit `retryInstrumentSave` and `recoverAppearance` first call appearance recovery, then the existing instrument retry. Appearance edits remain blocked through the instrument owner's shared storage uncertainty. Normal look changes never enter instrument edit/settle.
- Root requested one Settings-only delayed-input message, the `LocalSettingsGestureOwner` provider around the existing body, and `settingsChildInput()` on each appearance text field. Root owns those gesture APIs. This writer adds no gesture threshold or new dismissal authority.
- A default `SheetHost.onClosing` callback cancels preview as soon as Settings dismissal commits. Editor disposal also cancels preview on group collapse or composition retirement.
- All immutable migrated snapshots, including `legacy:current`, can be loaded into the draft from an expandable legacy list. The original ROOM grid remains separately reachable.
- The first actual-source host run passed94 tests in7 suites at14:46UTC. It compiled production Palette, RoomStyle, migration, collection, codec, preferences and workflow. Controlled storage/native ports in tests are I/O fixtures, not substitute production owners.

## Final surface refinements before code, 14:57 UTC

Glass keeps its translucent outer material and receives an opaque inner text/control surface. The sheet labels this fallback. The status band receives the same opaque authored surface while Glass is active. This is app chrome only and never changes authored colors, HUD permissions or compositor capabilities. The console title marquee stops with reduced motion or hidden chrome. Burn-in also stops when a sheet or overflow covers the status band.

The finite transition keeps the previous displayed palette during the first composition of a new authored revision. Its SideEffect cannot record the unanimated new target before the effect captures the old palette. This is source wiring, not observed Compose frame acceptance.

## Source result and observed checks, 15:00 UTC

Runtime source and editor adapters are authored. The final cached host run passed116 tests across10 suites, with zero failures. It includes44 new tests:22 workflow cases,8 editor-input cases,5 dual-owner interleavings,8 source-wiring assertions and1 production restyling case. The other72 cases exercise the released value/document/collection/migration bridge. Source-wiring assertions are inspection evidence, not Android execution.

The final host run is `/home/ben/.jcode/scratch/section10-runtime-20260908T1445/run-ZSJAUm/`. It compiled the actual repository source paths. No production source was copied, stubbed or replaced for the tests. The storage and native ports in tests are controlled I/O fixtures around the real production owners. The cached production Compose Color, Dp and runtime classes supply the real legacy palette bridge. No Gradle, Android target, JNI, GPU, device or GUI work ran.

Seven owned Android/UI files passed a Kotlin PSI syntax-only check. This does not resolve Android/Compose APIs or prove an Android build. `git diff --check` passed for the owned tracked changes. The sole compiler warning marks a test `lateinit` as unnecessary. It does not concern production code.

### Requirement-to-check map

| Requirement | Implemented production path | Observed check and remaining boundary |
| --- | --- | --- |
| Valid appearance wins without write | `AppearanceWorkflow.load` | `existingValidBytesWinWithoutWriteIncludingWhitespace`, including repeated load and invalid legacy inputs beside valid state. Passed. |
| Migration uses actual thirteen Rooms | `AppearanceMigration.initial` through owner | `cleanStartupCommitsActualMigrationBeforePublishingAndRetainsThirteenRows` and existing migration/palette suites. Passed. |
| Exact unknown ID and raw sentinels/current overrides | Actual migration bridge, unchanged provenance | `legacyUnknownAndSentinelsRemainExactAndCurrentOverrideIsSaved`, existing migration cases. Passed. |
| Absent clean install uses curated AMOLED | Owner startup migration | Clean-start test checks curated value,13 exact legacy rows and stored document. Passed. |
| Commit before publish | `AppearancePreferences.save`, then owner publication | Clean-start and apply trace tests observe commit before changed state. Passed with controlled I/O. Android disk behavior remains open. |
| Failed migration restores key absence | One-key raw snapshot rollback | `failedMigrationRestoresExactAbsenceAndPublishesNoCandidate`. Passed. |
| Failed commit restores exact bytes/type | One-key raw snapshot rollback | Whitespace byte restoration, wrong-type corrupt replacement and thrown-commit cases. Passed. |
| Invalid legacy stays untouched | Owner catches migration rejection, Activity reads legacy inputs defensively | Invalid radius/type host cases pass. Activity fallback is syntax/source-checked only. |
| Corrupt new state remains recoverable | Unavailable owner state, explicit complete replacement | Corrupt-byte preservation and blocked preview/apply/reset cases pass. Explicit replacement and failed replacement tests pass. |
| Shared rollback failure retains authoritative A | Both typed failure owners | Failed rollback clears preview, keeps A and blocks edits. Passed. |
| Recovery orders appearance before instrument | Explicit Activity retry gate and refusal in instrument persistence | Five real dual-owner interleavings cover failed recovery, unrelated save refusal and exact ordered success. Passed. Android callbacks remain open. |
| Recovery never saves preview | `AppearanceWorkflow.recover` uses committed document | Failed/successful recovery observes A, not preview B or rejected C. Passed. |
| Unrelated archive omission preserves appearance | `AppearanceMigration.merge` inside settings owner | Existing merge tests plus adapter-source ordering check pass. |
| Legacy import translates through production bridge | Released merge adapter | Existing actual migration merge tests pass. Runtime source check observes invocation before snapshots/editor. |
| Complete new replacement wins over legacy/corrupt old state | Released codec and migration merge | Existing replacement/repair merge cases pass. Activity publication is source-checked only. |
| Archive /1, /2, size/checksum/unknown rules remain | Read-only `SettingsArchive` and released core | Existing coordinator gate covers archive seam. This writer does not modify it. Host preview/export case passes. Full root regression gate remains required. |
| Both tickets captured before picker | Activity pending instrument and appearance tickets | New source check covers capture-before-launch and both callback checks. Real dual-owner test confirms independent revisions. Passed within host boundary. |
| Preview/apply/save/reset/rename/delete reject old replies | `AppearanceWorkflow` revision token | Every authored-action ticket case passes. Worked A/B/C interleaving preserves C and instrument association. |
| Theme operations do not cancel native or change source/tuning | Appearance owner lacks that authority | Real pending-native interleaving sees no cancel/request/publish/save from look actions. Source checks exclude native/tuning/source calls. Actual source continuity remains device acceptance. |
| Preview excluded from preferences/export/instrument/lifecycle | Committed-only persistence and removed legacy autosave writes | Host preview/export/instrument case and lifecycle-source assertion pass. Recreation reads committed A. |
| Cancellation on close/group/background/PiP/retire | Editor disposal, Settings closing callback, Activity lifecycle methods | Owner cancellation/retirement tests and source checks pass. Real Compose lifecycle order remains open. |
| Original ROOM remains reachable | Settings route and existing bounded grid | Source retained. Room/style actions call owner. Root owns old source-assertion update. Physical reachability remains open. |
| Legacy snapshots remain immutable and selectable | Existing collection and new expandable legacy draft list | Collection immutability/active deletion/reset cases pass. List and old ROOM are syntax/source-checked. |
| Runtime uses explicit authored style, not custom-ID lookup | Activity `AppearancePalette.style`, `state.appearanceStyle` provider | Production restyling test and source assertion pass. |
| Only measured tick supplies transient tint | Existing `baseRoom.withBeam` tick retained | No authored value is sampled from ui.room. Existing palette tests and owner export separation pass. Runtime beam continuity remains open. |
| Finite revision replacement starts at last displayed palette | PhosphorScreen revision guard and finite Animatable | Source checks pass, including pre-effect lastShown guard. No Compose frame observation is claimed. |
| No decorative ROOM clock | ROOM pulse removed | Source assertion confirms no infinite transition or breath. Passed. |
| Burn-in only on visible, uncovered chrome | Console/status visibility arguments and reduced/hidden/PiP guard | Source assertions pass. Real compositor visibility remains open. |
| No reduced/hidden title marquee | Console marquee conditional | Source inspection and syntax pass. Runtime motion observation remains open. |
| All13 color roles and every style field are editable | Single AppearanceEditor and exact parser |8 parser tests cover round trips, RGB/ARGB extrema, Float domains, decimal rounding rejection and readable proposal. Source check covers action surface. |
| Distinct preview/apply/cancel and named CRUD/reset | Editor callbacks into workflow | Owner save/rename/delete/modified/reset tests pass. UI action-map source assertion passes. |
| Low contrast remains authored exactly | Value/parser preserve colors, warnings are separate | Low-contrast test passes. Explicit proposal replaces all color roles only and preserves style. |
| Glass has a labeled readable inner surface | Existing sheet/console tree with opaque authored inner surface | Source and syntax checked. Bright moving trace/black composition and physical readability remain unaccepted. |
|48dp, wrapping, keyboard/focus and semantics | Editor BasicText/BasicTextField, action Columns, retained ROOM accessibility scope | Source check confirms target/focus/semantics/Escape paths. Large-font, TalkBack, keyboard and real touch acceptance remain open. |
| Root Settings interrupted-input message and child ownership | One inline message, provider and text-field modifier | Root requested and owns gesture APIs. Syntax/source checks pass. Root gesture acceptance remains separate. |
| Exact immutable evidence and no hidden owned work | Private source archive and manifests | Final REPORT and MANIFEST carry archive, source, dependency, runner and JDK hashes. No writer build/device/service task remains. |

## Honest partial boundary and root follow-up

This is a source-complete runtime/editor handoff inside the approved writer paths, with host-validated pure owners and syntax-checked Android adapters. It is not SECTION10 acceptance. MainActivity, PhosphorScreen, ScopeUiState, Sheets, Console and AppearanceEditor remain uncompiled against Android/Compose in this writer run. The root must resolve/type-check them in the existing Gradle gate before treating them as buildable Android runtime.

The known read-only `RoomStyleOverrideTest.productionProviderAndSingleSampleReadEffectiveStyleAndCurrentState` asserts the old custom-ID style provider and direct UI override writes. Those assertions contradict the settled runtime contract. Root was notified at14:53UTC and owns their update to explicit appearanceStyle and onStyle routing. Existing test files remain untouched by this writer.

The smallest next step is root integration review, the obsolete source-assertion update, then the frozen full Android/JVM/native/boundary gate. Root also owns exact dual-APK readback, authorized ASUS installation and physical acceptance. S25 remained untouched and ASUS remained absent. No work here establishes prefs disk durability, frame ordering, rapid transitions, Glass readability, focus/large fonts, source continuity or real recreation.

Owned existing edits: MainActivity.kt, ui/ScopeUiState.kt, ui/PhosphorScreen.kt, ui/Sheets.kt, ui/Console.kt and ui/AppearancePalette.kt. Allowed ui/Palette.kt was not changed. New production files: settings/appearance/AppearanceWorkflow.kt, AppearancePreferences.kt, AppearanceEditorValues.kt and ui/AppearanceEditor.kt. New tests: AppearanceWorkflowTest.kt, AppearanceInterleavingTest.kt, AppearanceEditorValuesTest.kt and ui/AppearanceRuntimeWiringTest.kt. This handoff is the only repository document written by this worker. Core, archive, instrument owner, Settings gesture adapters, Controls, Manual and shared-repository files remain outside this writer's edits.
