# SECTION7 / R16 independent full-outcome critique

## Decision

**Round 3 of at most 4. Overall R15 score: 7/10. One material source-level recovery defect remains. Android acceptance remains open.**

The startup correction now creates `InstrumentWorkflow` before `restoreTuning`. The first failed light rollback reaches the shared recovery owner. The earlier automatic-save leak, stale whole-settings import, post-initialization failure forwarding and compact recall gaps remain corrected on the inspected paths.

The remaining intent mismatch is different from the discarded startup latch. A replacement Activity can offer persistence-only recovery without knowing the retained renderer's active light tuple. The recovery can save fresh UI defaults, clear the block and claim that current tuning was saved while native light remains different. A truthful instrument must distinguish known active tuning from an unconfirmed startup mirror.

The score follows R15's 6–7 anchor: mostly implemented with a material gap. It is one full-section rating, not an arithmetic average, a startup-only rating or an Android rating. Intent fidelity, pure functionality and privacy have strong source support. Lifecycle recovery has the concrete defect below. Accessibility, rendering, performance and real storage/provider behavior still require Android evidence. Compilation and inherited host tests do not close those boundaries.

Original round1 and round2 remain **7/10 and 7/10**, unchanged. This report does not assess coordinator changes made after the pinned commit.

## Identity and immutable scope

- Assigned approved route: GPT Astra (`gpt-6-astra`), high effort. The coordinator stated the routing ritual had passed. This worker did not spawn workers or repeat that ritual. Provider telemetry was not independently queried.
- Mobile repository: `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`.
- Exact mobile commit: `6355074c1d20158c29e749da9fd4c50e0d2bd2a7`.
- Shared repository: `/home/ben/Dev/ClaudeWorkspace/phosphor`.
- Exact shared commit: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Private report directory: `/home/ben/.jcode/scratch/section7-round3-20260908-iUAbcM/`.
- Authority: binding workspace governance, `ben-context-standards`, canonical `MOBILE-EXPANSION-PLAN.md` Section7/R15/R16, living-instrument vision, active `spec/EXPANSION.md`, instrument/native/workflow contracts, correction designs and original critiques.

All product-source evidence came from immutable Git objects or private copies extracted from them. No mutable live UI source was inspected. The exact readset contains **62 tracked files, including 29 production Kotlin/Rust/XML paths**, below the 45-production-file ceiling. Tests, fixtures and contracts form the remainder. No alternative production implementation or behavioral harness was created.

`readset.tsv` records repository, full commit, blob ID, SHA256, tracked path and private copy for every captured file. `inspection-scope.md` distinguishes focused inspection from whole-file capture. Large files were inspected at the relevant ownership boundaries, not represented as line-by-line whole-repository audits.

Semantic source inspection ended at **2026-09-08T12:45:58Z**. The subsequent immutable-object/hash verification finished at 12:46:58 UTC. Later work only prepared and sealed this private report. No source locks were required. The explicit final release is recorded in `OWNERSHIP-RELEASE.txt`.

## Material finding R3-F1: persistence-only recovery can save an unconfirmed startup light mirror

**Priority: medium. Confidence: high in the static mechanism.** This is a finite source-derived lifecycle/fault schedule, not an executed Android reproduction.

### Requirement

The workflow contract requires RETRY SAVE CURRENT to persist the **known active authored tuple** without submitting native work. A successful complete save may clear storage uncertainty, but saving cannot establish an unknown native outcome. The instrument contract requires coherent actual controls and native ownership, truthful failure recovery and preserved authored setup. The correction02 design also requires preservation of the known active tuple.

### Exact source chain

All paths below are relative to the mobile commit. `MainActivity.kt` means `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`. `InstrumentWorkflow.kt` and the other instrument files are under `app/src/main/kotlin/dev/phosphor/mobil3/settings/instrument/`. UI files are under that package's `ui/` directory.

1. `MainActivity.kt:768,783–784` creates a fresh `ScopeUiState`, initializes the workflow, then restores tuning. This correctly fixes round2's missing-owner order.
2. `ui/ScopeUiState.kt:129` initializes `light` to `LightSettings()`. `ui/LightSettings.kt:9–19` makes that an empty color bank, selection0, preset7 and default timing. No retained native light is read into this initial value.
3. `rust/src/render.rs:165–175` keeps one process-wide render sender/thread. Its `LightCycle` lives outside the loop at `:286–289`. Surface loss at `:395–398` clears only the active surface and acknowledges retirement. It does not reset the light setup. Surface creation at `:315–388` also does not replace the loop-owned light value.
4. `MainActivity.kt:1894–1900` reads the saved light and calls `setLight` during normal restore. `:2413–2426` commits preferences before native light publication. On a failed commit, `settings/SettingsWriteOwner.kt:23–31` skips publication and attempts rollback. `MainActivity.kt:2430–2434` assigns `ui.light` only on success, after the failure return.
5. `MainActivity.kt:581–586` now forwards a double failure correctly. `InstrumentWorkflow.kt:180–197` latches `storageUncertain` and `unsaved`. It does not establish whether the fresh UI snapshot equals the retained native setup.
6. `MainActivity.kt:256–261` captures that fresh `ui.light` as authored truth. `ui/InstrumentPresetSheet.kt:72` offers RETRY SAVE CURRENT when the owner is unsaved. `MainActivity.kt:427` invokes `retryPersistence`.
7. `InstrumentWorkflow.kt:234–243` calls `save(snapshot())`, without a native request or a confirmed retained-state read. On success, `:171–177` clears storage uncertainty and unsaved state. The status becomes `Current authored setup saved. Tuning unchanged.`
8. `MainActivity.kt:295–309` persists the captured tuple and deliberately performs no native publication. `InstrumentPreferences.kt:4–13` includes its entire light map. Nothing in that successful retry changes native light A into the fresh UI light7 that was just saved.

### Finite schedule

| Step | Operation | Result derived from the pinned source |
|---|---|---|
| 1 | Successfully apply and save Ambient A, or another setup with a nondefault selected color bank. Let its exact receipt complete. | Kotlin, native and saved light agree on A. |
| 2 | Destroy and recreate the Activity in the same process, without a later explicit light edit. | The process render owner retains A. The new `ScopeUiState.light` starts empty at preset7. |
| 3 | Allow non-light restore normally. During the restore-time light transaction, return false from the preference commit and false from rollback. | No native light command is queued. The Activity does not publish the restored light into `ui.light`. The workflow correctly latches storage uncertainty. No particular disk contents are assumed. |
| 4 | Open Instrument presets and use RETRY SAVE CURRENT after storage becomes writable. Let this complete save succeed. | The captured light is the fresh empty preset7 tuple. Preferences now contain it. Native still contains A, because this path has no native publication. |
| 5 | Observe the owner after retry. | `storageUncertain=false`, `unsaved=false`, automatic saving and tuning are allowed, and the status claims current authored tuning was saved without changing tuning. The saved/UI tuple does not describe the retained renderer's light. |

The failure is not merely an inaccessible error message. Recovery can replace a previously saved custom bank with a default bank and declare success without reconciling the active instrument. A later save/create or undo snapshot can also use that incorrect UI mirror. The schedule does not require a missing native receipt, a lost reply, a malicious archive or an unspecified disk outcome.

### Why current checks do not cover this schedule

`InstrumentWorkflowTest.kt:17–20` initializes `current`, `rendered` and `saved` to the same setup. The new `freshOwnerRetainsRestoreTimeRollbackFailureBeforeAnyPresetRequest` test at `:93–112` checks latch admission and pure save recovery under that equality assumption. It never creates a fresh Activity light default beside a different retained native light.

`InstrumentActivityWiringTest.kt:111–120` verifies initialization order and the restore-time `setLight` call. It does not verify the startup tuple that `captureInstrument` later supplies. These checks support the real latch correction, but not the assumption required by persistence-only recovery after Activity replacement.

### Smallest corrective work and check

Before offering persistence-only recovery, establish that startup's authored snapshot describes the active renderer. If that fact is unavailable, keep a distinct unconfirmed-startup state and a precise recovery action. Do not treat fresh UI defaults as confirmed native state. Preserve the no-native retry path for ordinary failed saves whose committed active setup is known.

The smallest next check is the five-step schedule above with retained native Ambient light, fresh Activity defaults, failed startup light commit/rollback and a later successful recovery. Assert that recovery either establishes one confirmed full setup across native/UI/preferences or remains explicitly blocked. It must not report a successful current save with different native light. Exercise the production startup/recovery adapters rather than only a Rig that starts with matching values. Android execution remains coordinator-owned and unavailable in this assignment.

The coordinator received this material trace at 12:44:03 UTC and acknowledged it at 12:46:58 UTC. That acknowledgment is coordination, not additional test evidence. Later implementation choices are outside this immutable review.

## Prior findings: corrected source paths and retained limits

| Prior item | Current source assessment |
|---|---|
| Round1 F1, source/automatic gain leaks failed preset into saved tuple | `MainActivity.kt:1471–1474,1730–1740` uses `automaticPersistenceAllowed` before automatic gain and preserves instrument keys during lifecycle saves. `InstrumentWorkflow.kt:46` excludes pending, unsaved and uncertain state. The original finite source-action leak is corrected. |
| Round1 F2, delayed whole-settings archive overwrites later tuning | `MainActivity.kt:647–705,1514–1529` carries one owner ticket from picker to provider acceptance. `InstrumentWorkflow.kt:199–225` checks identity and authored revision before mutation. Apply/manual/undo/retirement invalidate stale intent, including equal-value round trips. |
| Round1 F3, ordinary light/archive drops typed rollback state | `MainActivity.kt:581–586,620–624,2419–2426` forwards typed restoration state to the same owner. Source stop remains outside edit admission. Actual disk rollback remains unaccepted. |
| Round2 startup latch gap | `MainActivity.kt:783–784` now creates the workflow first. Its constructor only retains callbacks, so restore-time failure forwarding reaches a live owner. R3-F1 concerns the correctness of the subsequently captured tuple, not a recurrence of null-owner forwarding. |
| Compact recall ambiguity | `ui/LightSheet.kt:62–71,162–168` has RECALL INSTRUMENT. `ui/PhosphorScreen.kt:806–808` opens the existing browser without applying. Settings keeps its separate route at `ui/Sheets.kt:1354–1364`. |

## Full requirement-to-source coverage

“No additional material finding” means bounded source inspection only. It does not mean a behavioral test was run or an Android outcome was accepted.

| Requirement | Exact inspected source and outcome | Validation boundary |
|---|---|---|
| Dedicated surface near Beam/Light, compact recall, separate appearance/archive semantics | `ui/Sheets.kt:1354–1384`, `ui/LightSheet.kt:62–71`, `ui/PhosphorScreen.kt:798–823`. Two named entries reach one browser. Navigation has no apply call. | Actual reachability, back/focus behavior and gesture ownership remain Android checks. |
| Enumerated complete typed setup | `InstrumentSetup.kt:20–60`, codec `:67–107`, preferences `:4–13`. Mode/bans, geometry, manual gain/auto, focus, energy/glow/random ranges, grid/readout, reconstruction and whole light are represented. | Distinctive full-tuple UI recall still needs device comparison. |
| Authored local rather than measured/remote gain and temporary light | Activity `:256–292,1039–1058,2018–2051,2115–2123` separates local auto-gain/manual gain from observations. `:2438–2440` does not overwrite underlying light on a temporary roll. | R3-F1 exposes startup mirror uncertainty. Normal local/audio-only relay control display remains unaccepted. |
| Exclude source, transport, mic/mix, volume, grants/root/startup, private targets, appearance, HDR/brightness and inspection | Setup/codec/preference exact allowlists, Activity `:264–309`, native `render.rs:512–539`. No source start, volume, pause or Surface call appears in preset admission/publication. | Actual unchanged audio, source position, device volume, theme and permissions need observation. No global privacy audit claimed. |
| Create/name/save/update/duplicate/rename/delete and explicit apply | Collection `:46–79,128–139`, Activity `:385–424`, browser `:79–120`. Stable IDs, immutable records, explicit save and confirmed deletion are wired. Curated entries are not directly editable. | Complete UI CRUD and failure presentation remain Android checks. |
| Saved records unchanged by current edits, modified association | Workflow `:47,109–115,140–168,246–255`, Activity `:346–355`. Association compares authored setup and refreshes after stored rename/update/delete. | Recomposition and displayed modified state need actual UI checks. R3-F1 can corrupt what counts as current after failed startup. |
| Curated restrained starting set | Setup `:96–115`, shared DSP `crates/phosphor-dsp/src/lib.rs:67–85`, shared beam `crates/phosphor-beam/src/lib.rs:106–120`. Clean XY=mode0/green, Spectral=8/ice, Ambient=7/slow two-color setup. | Contract/test tuples agree by inspection. Visual suitability and physical display are not measured. |
| Whole validation before mutation | Setup constructors, codec `:81–154`, native `instrument.rs:71–102,120–129`. Exact field/type/range checks precede native request or collection publication. | Production parser tests are inherited, not rerun. JNI execution remains open. |
| Versioned documents, deterministic checksum and bounds | Codec `:15–60,110–154,194–305`. Strict UTF-8, duplicate-key rejection, bounded nesting/nodes, 1MiB bytes, 64 records and canonical SHA256. Tests inspect fixed empty/nonempty fixtures and signed-zero preservation. | Real SAF round-trip and malformed document UI handling remain open. |
| Coherent native admission and exact receipts | Native `instrument.rs:119–209`, `jni_glue.rs:23–61`, `PhosphorNative.kt:75–82`. 750ms finite deadline, at most four receipts, exact terminal outcome, explicit release and inert cancelled queued work. | Host primitive tests are inherited. Android request/admission/wait scheduling is not executed here. |
| One whole native configuration before a live frame | `render.rs:291–314,512–539`. One command assigns mode/gain/geometry/beam/grid/focus/reconstruction/light, clears obsolete flip, and makes no driver or source call. Engine `:665–668` and shared DSP `set_sample_rate` affect reconstruction, not source audio. | Actual whole-frame observation and performance require Android. Plain assignments are not pixel evidence. |
| Main-thread owner, off-main waiting, later manual/import ordering | Workflow `:96–153,199–225`, Activity `:326–343,358–369,593–705,1972–2051,2408–2489`. Cancel reconciles committed setup before later edits. Wait callback is off-main then posted. Stale receipt callbacks are ignored. | Source-linked assertions supplement production owner fixtures. They are not Activity execution. |
| Source changes and lifecycle retirement | Activity `:964–1013,1162–1169,1284–1292,1486–1502,1701–1723`, Workflow `:258–265`. Settle/release precedes source or lifecycle actions. | R3-F1 is the remaining concrete replacement-Activity recovery defect. Real replacement scheduling remains open. |
| Immediate one-level undo | Workflow `:82–87,113`, browser `:70–72`. Same guard/request route, one slot, consumed after committed undo and replaced after later apply. | Undo under HOLD, source capability changes and persistence faults needs Android acceptance. |
| Six-color/range/random guard preservation | `ui/LightSettings.kt:21–27,94–101`, Setup `:78–81`, Workflow `:62–63,89–94`, browser `:73–77`, native light validator `light_cycle.rs:14–20`. Safe clamp remains modified relative to recalled original. Decode/import never acknowledges. | Actual six-slot UI, random-minimum warning, KEEP SAFE and acknowledgment/undo need device checks. |
| HOLD image and inspection remain untouched | Preset command `render.rs:512–539` does not mutate `pause::DISPLAY`, history or inspection. Held path `:575–626` presents pinned image/inspection separately. `pause.rs:28–119` keeps those values under separate ownership. | No retained-image GPU test or phone pixels were run. R13 freshness and source-age acceptance remain separate. |
| Truthful remote geometry refusal, audio-only relay local path | Activity `:333,1486–1490`, renderer `:513`, remote `:682–693`, browser `:66–68`. Native checks actual render-owner capability and presets send no remote setter burst. | Actual relay capability mirroring, geometry refusal and audio continuity remain Android checks. |
| Durable bounded collection and failed/corrupt-byte preservation | Store `:13–63`, Activity `:312–324`, collection constants. Exact base bytes, one encoded write, rollback of string/absence, corrupt bytes not replaced with empty. Backup XML includes user settings and excludes runtime/private containers. | Real SharedPreferences disk behavior, same-package update and backup/restore remain unaccepted. |
| Inert complete import with explicit conflicts | Collection `:85–116,151–159`, Activity `:445–481,550–572`, browser `:126–153`. Full validated preview, choice per record, stable replacement ID, stale-base/duplicate-target checks, one final save. | Real provider, crossed conflicts, cancel, stale preview and storage failure need UI acceptance. |
| Export all/selected, document cancellation and owner bounds | Activity `:435–547`, DocumentOwner `:14–38`, bounded stream reader `InstrumentPresetStore.kt:66–86`. Export snapshots before picker. Cancelled provider work retains its slot until return. Failure discloses possible partial output. | No immediate provider cancellation, durable export or process-recreation result is claimed. |
| Persistence recovery and source/safety availability | Activity `:295–309,581–586,1730–1785`, Workflow `:171–243`. Known committed saves retain typed recovery and suppression of automatic instrument writes. | R3-F1 prevents full acceptance of startup recovery. Source-stop availability is source-supported, not executed. |
| UX/accessibility/performance/privacy | Browser `:59–78,159–175` provides status, labeled inputs, button roles, 48dp minimums and unlimited button lines. One bounded eager list, no new service/endpoint/audio history. | Font scaling, TalkBack, hit targets, focus retention, maximum-list responsiveness, memory and jank need Android evidence. |

A minor wording boundary remains at `ui/InstrumentPresetSheet.kt:64–65`: any `displayPaused` state receives HOLD wording, even when BLACK or no retained image applies. Use the existing pause-state facts for truthful wording during later UI polish. This does not alter native HOLD preservation and is not a second material execution finding.

The prior review's broader manual documentation debt remains inherited cross-section follow-through. This review inspected the browser's feature/recovery prose, not the current whole manual. It does not accept Section11 documentation or rescore adjacent features.

## Validation and provenance

### Independently performed in this review

1. Read governance and the pinned feature plans, contracts, correction records and original critiques.
2. Extracted 62 exact Git blobs into private read-only snapshot trees. No mutable product source supplied evidence.
3. Traced the complete R16 requirement set and the finite material schedule above. Read relevant production tests without executing them.
4. Recompared all 62 private files with their requested commit/blob contents. Every SHA256 matched.
5. Ran `sha256sum -c readset.sha256`. All 62 entries passed. `verification.txt` retains the result and timestamp.
6. Checked original-report hashes against the pinned correction receipts. Both originals match their recorded identities below.
7. Sealed this new report, readset, scope and verification artifacts. `SEAL.txt` records final report/readset/inventory hashes.

No builds, tests, Gradle, target/JNI/native execution, Python, network, ADB, GUI, audio, service operation, worker spawning, Git mutation or product edit occurred. No test runner, compiled substitute or JVM/Rust dependency was executed. Shell command identities are recorded in `shell-tools.sha256`. Governance input hashes are separate in `governance.sha256`.

### Inherited evidence, not rerun or promoted

Pinned `section-07-08-correction-validation.md` reports coordinator working-source gate **4931158kjm**: **675 JVM tests, 126 native host tests and 3 offscreen GPU tests**, plus lint, Android/Kotlin/JNI compilation, both debug APKs, engine/helper and production boundary checks. It reports unchanged complete source lists/hashes during that gate.

That gate used mobile base `f504b3fa961275ec6654e65a00e40b4ab497b0db` plus its recorded correction delta, and shared `0ffd658d7f19e68180c2720e0500b23644619e90`. Documentation added afterward was outside its source manifest. Both APKs were retained, not installed. The earlier failed and passing gate attempts remain separate in that receipt.

This reviewer inspected the tracked receipt and relevant exact test source. It did not read mutable build outputs, independently verify retained APK/JAR artifacts, execute those runners or reproduce their runtime results. The 675/126/3 count is inherited host evidence only. It is neither this review's test count nor Android acceptance. Historical core/workflow/owner gate counts are not additive.

### Exact identities

| Artifact | SHA256 |
|---|---|
| `readset.sha256`, all 62 files | `8dfc935330b473238ecd86fd4d199228e4426bfe59a3600b05a6eee5c17ab033` |
| `readset.tsv`, commit/blob/content provenance | `acc369b689170f9ad75606e04d9b7c72fa052994b143339fd6f286b78b2e127b` |
| `verification.txt` | `a5f18be9e729f76b78fab569c3b4959d3350b2bcec76fd770d7809d6528087b8` |
| Original round1 report | `9d439ab826632d0fa32c6c9183c34b2cb8f3a34508ac6182ea7b1541cd69e415` |
| Original round2 report | `605d14fedadd6aeb33c68fc59a7741206cea3f1144e8b561b7146796e1403b15` |

The report's own final hash belongs in `SEAL.txt`, avoiding a self-referential report hash.

## Disposition and release

Correct R3-F1 with a bounded startup authority/recovery change, preserve the known-active persistence-only retry, and run the exact retained-renderer/fresh-Activity failure schedule. Obtain the fourth and final permitted full-section critique if the coordinator proceeds with corrections. Do not rewrite either earlier score or this round3 report. A future correction belongs to a new immutable input and report.

**Blocked acceptance:** Android initialization, storage faults, SAF providers, JNI ordering, source/audio continuity, held pixels, gestures and accessibility cannot be accepted under this source-only assignment. **Evidence:** those operations are prohibited here, and the inherited gate explicitly leaves them unaccepted. **Best current result:** full requirement coverage, one concrete finite material defect, an honest 7/10 score and an exact verified private readset. **Smallest next check:** retained nondefault native light plus replacement Activity defaults, failed restore commit/rollback, then successful explicit recovery without a false coherence claim.

R13 source freshness, root stereo, SoundCloud, audibility/latency, R09 mixing, R17 and other expansion sections retain their separate dispositions. This report does not accept installation or final release.

**All read/source ownership is released at sealing. No live source hold, worker or background task remains. No further source inspection follows the recorded cutoff. After sealing and the required completion report, this worker stops all work until the root stops it.**
