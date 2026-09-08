# SECTION7 / R16 independent full-outcome critique

## Decision

**Round 2 of at most 4. Overall R15 score: 7/10. One material source defect remains. Android outcome acceptance remains open.**

The complete preset feature has substantial source support. F1's automatic persistence leak and F2's delayed archive overwrite are corrected on the inspected paths. F3's ordinary post-initialization adapters now retain rollback information, but startup invokes the light transaction before the recovery owner exists. That first failure is still discarded.

The largest intent mismatch is this startup recovery gap. The app can tell the user to use RETRY SAVE CURRENT, then create an owner that neither blocks tuning nor offers that recovery action. This conflicts with a truthful, recoverable instrument.

The score uses R15's 6–7 anchor: mostly implemented with a material gap. It is one overall rating, not an average or a phone rating. Intent fidelity, core functionality and privacy have strong source support. Functional lifecycle recovery has the concrete defect below. UX, performance, durable storage and rendering lack required Android acceptance. The inherited gate does not establish independent or device evidence.

## Identity, immutable inputs and boundary

- Assigned approved route: GPT Astra (`gpt-6-astra`), high effort. The coordinator stated that the root routing ritual was complete. This worker did not spawn workers or repeat it. Provider telemetry was not independently queried.
- Mobile repository: `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`.
- Reviewed mobile commit: `15e4072e86054af15f483f395d5adc8bcb09803f`.
- Shared repository: `/home/ben/Dev/ClaudeWorkspace/phosphor`.
- Reviewed shared commit: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Comparison mobile commit: `5b1be193e6fbcb0d3f779a8c0da5774a9a05efc6`.
- Authority: binding workspace governance, `ben-context-standards`, canonical `MOBILE-EXPANSION-PLAN.md` Section 7/R15/R16, living-instrument vision, active `spec/EXPANSION.md`, and the Section 7 instrument/core/native/workflow contracts.
- Read the original critique, correction01 design, owner validation, core/workflow validation and Section 7–8 integration validation as separate immutable inputs.
- Private output directory: `/home/ben/.jcode/scratch/section7-round2-20260908T1209/`.

This is a source-only review. No build, test runner, Kotlin/JVM execution, Rust execution, JNI, Android, GPU, ADB, GUI, browser, audio, network, service or device operation occurred. No Python, downloads, Git mutations, product edits or worker spawning occurred. Shell operations only extracted and inspected pinned objects, wrote private review artifacts, and checked their identities.

The readset contains 57 tracked object versions: 31 current production files, two baseline production versions, 16 contracts/receipts and eight test files. Even counting the two baseline versions separately, production inspection is 33 files, below the 40-file ceiling. The manifest identifies captured files, not a claim that every line of each large file was read. `inspection-scope.md` specifies the focused inspection boundaries. Three failed guessed paths left empty private placeholders. They are excluded from the tracked readset and explicitly listed there.

All source reasoning used immutable Git objects or their private copies. No mutable source hold was requested or needed. Semantic source inspection ended at **2026-09-08T12:18:49Z**. Later operations only prepared and verified private receipts. Root remains free to edit both live repositories.

## Material finding: F3-startup, medium priority

### The first light rollback failure occurs before the shared recovery owner exists

**Affected requirements:** workflow contract lines 27–29, correction01 design lines 45–51, canonical R16's coherent failure/recovery and lifecycle requirements. Every ordinary tuning write must preserve typed rollback uncertainty. Failed rollback blocks tuning, APPLY, UNDO and whole-settings import until explicit complete-save recovery. Source stop remains available.

All paths below are relative to the reviewed mobile commit. `MainActivity.kt` denotes `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`. Instrument files are under `settings/instrument/` in that package.

**Exact source chain:**

1. `MainActivity.kt:248` initializes `instrumentWorkflow` to null.
2. `MainActivity.kt:783–784` calls `restoreTuning()` before `initializeInstruments()`.
3. `MainActivity.kt:1804,1896–1902` uses the default `lightPublished=false` and calls `setLight(it)` during startup restore.
4. `MainActivity.kt:358–369,2410–2415` permits the tuning block when the workflow is null. The ordinary light transaction therefore runs before the workflow exists.
5. `MainActivity.kt:2421–2428` commits light, rolls back on failure and calls `reportTuningWriteFailure` with the typed result.
6. `settings/SettingsWriteOwner.kt:23–31` returns `Failure(..., restored=false)` when both commit and rollback fail or throw.
7. `MainActivity.kt:581–586` removes queued gain persistence and creates a recovery message, but forwards the failure using `instrumentWorkflow?.persistenceFailed(...)`. At startup this is a null no-op. No pending typed failure is retained.
8. `MainActivity.kt:312–343` then creates a fresh `InstrumentWorkflow`. Its defaults at `InstrumentWorkflow.kt:38–46` are `unsaved=false`, `uncertain=false`, `storageUncertain=false`, and automatic persistence allowed.
9. `MainActivity.kt:346–352` exposes RETRY SAVE CURRENT only from the owner's unsaved state. `ui/InstrumentPresetSheet.kt:70–72` therefore has no recovery action for this discarded startup failure.

**Finite source-derived schedule:**

1. Start an Activity with a valid readable light tuple, or with absent light keys that use the normal defaults.
2. During `onCreate`, let the restore-time light commit return false after its in-memory mutation.
3. Let restoration of the exact prior preference snapshots also return false. No particular final disk contents are assumed.
4. The Activity records a light-error message. The shared workflow is still null, so it cannot latch uncertainty.
5. `initializeInstruments()` installs a healthy owner. Opening the preset browser does not show RETRY SAVE CURRENT for this failure.
6. A subsequent manual edit, APPLY or whole-settings import is admitted. Automatic gain/lifecycle persistence also sees an allowed policy instead of preserving the uncertain instrument map. UNDO becomes available after a later successful apply without any recovery of the original uncertainty.

**Observed facts versus inference:** initialization order, nullable failure forwarding, fresh state defaults and subsequent guards are directly observed. The two false results are an explicit fault-injection schedule, not an executed Android storage failure. The exact filesystem state is intentionally unknown. The defect is the missing recovery state, not an assertion about which bytes Android will retain.

**Why current checks miss it:** `InstrumentWorkflowTest.kt:17–60` constructs a live owner before every Rig transaction. Its light/archive double-failure test at `:549–584` reports failures to that already-created owner. `InstrumentActivityWiringTest.kt:101–108` verifies forwarding text but does not check startup ordering or exercise the null-owner branch. These tests support the post-initialization correction, not this first restore transaction.

**Smallest correction:** make shared recovery state available before any restore-time settings transaction, or retain typed startup failures and transfer them into the initialized owner before controls or automatic saves can run. Preserve the known active authored tuple. Do not solve this by disabling source stop or silently clearing the error on browser open.

**Smallest actual acceptance check:** fault-inject commit false plus rollback false through the real startup light adapter and actual owner initialization order. After creation, assert storage uncertainty, blocked tuning/apply/undo/import, suppressed automatic gain, preserved instrument keys during lifecycle save, and a reachable RETRY SAVE CURRENT. Source stop must still work. Then explicitly save the complete known active tuple successfully and verify that only this recovery clears uncertainty without a native request. Include a commit-false/rollback-true control that remains editable. A pure preconstructed-owner test or another substring assertion does not close this startup seam.

**Confidence:** high in the source defect and contract conflict. Android failure behavior and the proposed acceptance check were not executed. This is a remaining F3 lifecycle path, not a claim that the original report anticipated this exact schedule. The original report remains unchanged.

## Original findings and correction disposition

### F1: failed preset followed by automatic gain, relay selection and lifecycle save

**Source correction supported.** `InstrumentWorkflow.kt:46` centralizes automatic persistence policy. `MainActivity.kt:169–171` and `:1473–1475` both call the guarded helper at `:1732–1734`. `saveTuning` at `:1737–1788` settles pending work, snapshots all enumerated instrument keys when automatic persistence is forbidden, and restores those snapshots at the end of the same editor transaction. `publishInstrument:264–265` removes queued gain work. Typed external rollback failure also removes it at `:581–582`.

The enumerated gain writes are now only the guarded helper and lifecycle editor, apart from full explicit preset persistence. `applyLocalGainPolicy:2117–2125` retains the active local auto-gain choice when unsaved. Source selection/stop are not gated on tuning recovery. `PlaybackService.kt:1332–1341` reads durable gain for the existing desktop control, not a local preference write or native local tuning overwrite.

Static schedule: durable A, native B committed, B persistence fails, rollback restores exact A, then a queued gain callback, relay selection and lifecycle save occur. The first two skip partial gain writes. The lifecycle editor restores every preserved instrument key, including absence. Explicit retry can save B completely. `InstrumentWorkflowTest:415–438` supports the policy/map portion. `InstrumentActivityWiringTest:91–98` supports caller placement only. Real preferences and relay selection remain an Android check. F3-startup is a separate case where policy state is never latched.

### F2: picker-before-provider authored ticket and delayed archive A after preset B

**The original later-edit overwrite is corrected on the inspected owner/Activity path.** `MainActivity.kt:1516–1536` obtains and retains a ticket before opening the picker. `InstrumentWorkflow.kt:199–205` settles prior native work before capturing its authored identity. The picker at `MainActivity.kt:647–660` consumes the pending ticket and checks it before starting provider work. The main callback at `:686–705` finishes the same ticket before entering `acceptSettingsArchive`.

Apply intent and committed publication change authored identity at `InstrumentWorkflow.kt:56,111`. Manual outer edits do so at `:148`, undo intent at `:85`, successful external restore at `:166`, explicit recovery at `:239`, and close at `:263`. Equal-value edits still invalidate old operations. `finishSettingsImport:219–225` rejects stale identity before the callback can write preferences or invoke native setters. A consumed or cancelled ticket cannot claim a new ticket.

Static schedule: begin import A, pick A, hold provider decoding, apply B and receive its exact commitment, finish decoding A, deliver its main callback. The ticket revision no longer matches. Acceptance returns false before the settings write callback. B remains the active/saved setup. This specifically includes decoding old archive A after B, not only an already-decoded value.

Cancellation, stale duplicate results, queued later apply, undo intent, and retirement are covered by the inspected production-owner tests at `InstrumentWorkflowTest:441–547,599–611`. The decoder barrier test at `:480–518` uses actual `SettingsArchive.decode` with controlled owner/native adapters. These tests were read, not run here. `onDestroy:989–996` closes the owner before retiring callbacks. Android picker restoration, replacement-Activity/task callback ordering, provider delays and actual `runOnUiThread` delivery remain acceptance boundaries. No claim of complete platform callback coverage follows from the owner tests.

### F3: all ordinary typed adapters and explicit recovery

**Post-initialization adapter correction supported, startup correction incomplete.** The three `SettingsWriteOwner.commit` callers in MainActivity are preset persistence at `:307`, whole-settings import at `:620`, and ordinary light at `:2421`. Preset persistence returns typed failure. The latter two now route through `reportTuningWriteFailure`, preserving `restored` into the same owner.

`InstrumentWorkflow.kt:180–196` latches failed rollback, does not invent uncertainty for a verified rollback, and does not clear an existing latch merely because a later failure restored its own snapshot. `retryPersistence:233–243` saves the complete known authored setup without requesting native application. A missing native receipt remains distinct and cannot be repaired by a storage retry. Existing owner tests at `:302–353,549–611` cover these branches with controlled adapters. The startup null-owner schedule above prevents declaring F3 complete.

## Full requirement-to-source coverage

“No additional defect found” below is a bounded source finding, not runtime acceptance. Paths are relative to the pinned mobile commit unless marked shared.

| Requirement | Inspected source and result | Smallest remaining acceptance |
|---|---|---|
| Dedicated surface near Beam/Light, distinct from appearance and archives | `ui/Sheets.kt:1354–1384` provides separate Light, Instrument and settings archive actions. `ui/PhosphorScreen.kt:798–821` routes one browser owner. | Reach each entry and verify no tuning application during navigation. |
| Compact LIGHT recall | `ui/LightSheet.kt:62–71,162–168` adds a labeled 48dp-minimum RECALL INSTRUMENT action. Screen `:806–808` opens the same browser without APPLY. The original ambiguity is corrected. | Phone reachability, hit target and return/focus behavior. |
| Typed complete authored tuple | `InstrumentSetup.kt:20–60`, codec `:67–107`, preferences `:4–13` enumerate mode, mode bans/arms, geometry, local gain/auto, focus, energy/glow/random ranges, grid/readout, DSP reconstruction and the whole light tuple. No arbitrary preference map enters the preset. | Save a deliberately distinctive full tuple and compare all recalled controls. |
| Local authored gain, focus, underlying light | Activity `:256–261,264–292,1039–1058,2018–2053,2117–2125` separates manual gain, local auto-gain and observed peer/measured values. Temporary rolls do not replace `ui.light`. | Local and audio-only relay save/recall, auto-gain breathing, temporary roll, recreation. |
| Excluded source/transport/permissions/root/startup/theme/HDR/brightness/inspection | Exact setup/codec/preference allowlists, Activity `:264–309`, render `:512–539` contain no source start, volume, pause, inspection or Surface operation. Documents include no grants or private targets. | Observe unchanged selected source, transport position, device volume, permissions, appearance and inspection through apply/undo. |
| Create, save/update, duplicate, rename and delete | Collection `:46–79,128–139`, Activity `:385–424`, sheet `:79–120` use immutable records, stable IDs, explicit save and confirmed deletion. Curated entries have no direct update/delete path. | Execute all CRUD operations and failures on the installed app. |
| Preserve saved records and show modified association | Workflow `:47,109–115,156–168,246–255`, Activity `:346–355`. Current edits do not mutate collection values. Rename/update/delete refresh association only. | Modify focus after recall, rename/delete associated record, verify current tuning unchanged. |
| Curated setups match purpose and supported modes | Setup `:96–115`, shared DSP `crates/phosphor-dsp/src/lib.rs:67–85`, shared beam `crates/phosphor-beam/src/lib.rs:106–120`. Clean XY=0/green, Spectral=8/ice, Ambient=7/slow two-color tuple. Values match the contract without calibration claims. | Visually judge exact supported modes and restrained defaults on Android. |
| Whole validation, versioning and canonical checksum | Codec `:15–60,81–154,194–305`, Setup `:44–60`, Collection `:21–38,128–139`. Strict fields/types, Unicode, integer syntax, duplicate keys, bounds and SHA256 precede collection publication. | Round-trip a real exported six-slot document and reject malformed/checksum/version input through SAF. |
| Native wire and bounded receipt admission | `rust/src/instrument.rs:71–209`, `jni_glue.rs:23–61`, `PhosphorNative.kt:75–82`. Validated metadata-free setup, 16KiB input, 750ms deadline, at most four owned receipts, no eviction of unresolved work. | JNI cancellation/deadline/lost-reply checks on the exact APK. |
| Coherent native apply, no partial late apply | `render.rs:512–539` applies a single candidate before a live frame. CPU configuration only. Engine `:637–640` and shared DSP `set_sample_rate` configure reconstruction, not source audio. Request `admit/cancel/wait` preserves exact terminal outcome. | Actual render admission barrier and observed whole frame, not only a copied state tuple. |
| Authored UI publication and manual/lifecycle serialization | Activity `:264–343,358–369,964–1013,1974–2053,2410–2491`, Workflow `:96–153`. Publication has no native setter replay. Later edits reconcile committed requests before acting. | Startup F3 fault check, then delayed apply followed by manual edit, source change, stop and destroy. |
| Immediate one-level undo | Workflow `:82–87,113`, sheet `:70–72`. Same guard/admission path, one slot, replaced on later apply and consumed after committed undo. | Apply twice then undo once, including HOLD, source capability changes and storage failure. |
| Six-color/random guard preservation | `ui/LightSettings.kt:21–27,94–101`, Setup `:78–81`, Workflow `:62–63,89–94`, sheet `:73–77`. Safe tuple remains modified against the original association. Import does not acknowledge. | Guard UI with all six slots, random minimum below one second, KEEP SAFE, explicit acknowledgment and undo. |
| HOLD preserves image and inspection | Render `:512–539` does not touch pause/history/inspection. Held presentation at `:575–626` uses pinned image and inspection separately. Browser `:64–65` labels readiness for LIVE. | Compare held image and inspection before/after apply/undo, then verify changed live setup on resume. |
| Truthful remote geometry capability, audio-only local rendering | Activity `:333,1488–1504`, render `:513`, remote `:682–693`, browser `:66–68`. Complete local apply rejects remote geometry and sends no remote setter burst. | Geometry-owned refusal without source change, audio-only successful apply with uninterrupted sound. |
| Dedicated durable collection, corrupt bytes and app updates | Store `:13–63`, Activity `:312–324`, collection constants, both backup XML files. One serialized value, exact base-byte check, byte/absence rollback, corrupt data preserved, uncertain store blocks writes. | Real disk rollback and update survival with existing records. Backup allowlisting is not backup execution. |
| Inert import and explicit conflicts | Collection `:85–116,151–159`, Activity `:445–481,550–572`, sheet `:126–153`. Full decode before preview, explicit per-record choice, stale-base and whole-result validation, no apply/acknowledgment. | Conflict by ID/name, crossed conflicts, stale preview, cancel, invalid record and failed commit through actual UI. |
| Export all/selected, bounded document ownership | Activity `:484–547`, DocumentOwner `:14–38`, stream reader `InstrumentPresetStore.kt:66–86`. Export snapshot precedes picker. Cancelled ticket holds its slot until return. Partial output is disclosed. | Cancel picker/provider, reopen browser, destroy Activity, selected/all exports with real providers. No immediate provider cancellation is claimed. |
| Failure persistence and recovery | F1 policy and post-init F3 adapters above are wired. Startup F3 remains material. Whole-settings F2 is separately fenced. | One real startup fault-injection check plus the exact three original schedules. |
| Browser accessibility and performance | `InstrumentPresetSheet.kt:159–175` requests 48dp minimums, button roles, unlimited button lines and labeled input. Browser is an eager scrollable Column with at most 64 records. No new service/endpoint/history is added. | Large-font layout, TalkBack/switch/keyboard traversal, gesture focus, maximum-list responsiveness. Source dimensions are not measured targets. |
| Manual tuning and user documentation | All inspected instrument setters enter the shared edit owner. The in-app manual at `ui/ManualSheet.kt:230–245` still omits instrument presets and describes only three custom slots. Browser prose explains the feature and recovery locally. | Carry the stale manual into the planned manual section. Do not claim complete documentation now. This is cross-section documentation debt, not a second material R16 execution finding. |

## Evidence and exact provenance

### Independently performed here

1. Confirmed both requested commit objects exist and extracted only pinned source objects.
2. Recorded each tracked path's repository, commit, Git blob ID and SHA256 in `readset.tsv`.
3. Compared baseline/current Activity and workflow source to distinguish correction coverage from the remaining startup path.
4. Checked the original round1 critique's extracted SHA256: `9d439ab826632d0fa32c6c9183c34b2cb8f3a34508ac6182ea7b1541cd69e415`. It matches the correction receipt's preserved original identity.
5. Reverified every captured tracked file against its exact Git object and checked the independent SHA256 manifest. The verification receipt records the result.
6. Sealed the private report, readset, inspection boundaries, command-tool identities and receipts. `SEAL.txt` records the report/readset/inventory hashes.

The governance hash receipt records the binding workspace file and two skills read. These governance inputs are external read-only instructions, not mutable product-source evidence.

### Inherited, not independently rerun

The pinned `section-07-08-integration-validation.md` reports coordinator gate `783238n3mp`: 653 JVM tests, 125 native host tests, three offscreen GPU tests, lint, app plus androidTest APK builds, engine/helper and production source checks. It reports unchanged working-source manifests during that gate. The gate used mobile base `46d631b3d7636a9aa882f772a669b0f86ff93a8a` plus its recorded delta, not this critic's independent execution or a clean installed release.

The pinned pure-owner receipt reports 85 tests and explicitly says the Activity adapters were not yet corrected at that earlier checkpoint. The core and workflow receipts preserve their earlier 587/121/3 and 624/121/3 integration counts. These are historical scoped results, not totals to add together or Android successes.

This critic read the tracked receipts and selected exact test source. It did not read mutable build reports, execute their runners, verify all retained APK/JAR hashes or reproduce their runtime results. The 653/125/3 gate is therefore inherited evidence only. It cannot close the uncovered startup ordering branch or prove real SharedPreferences, SAF, JNI, pixels, accessibility or sound.

## Disposition and release

Correct F3-startup with the smallest owner-lifecycle change and actual startup-order check. Preserve the successful F1/F2 corrections and compact recall path. Then obtain round3 against the corrected full outcome under the existing four-round limit. Do not rewrite the original round1 report or this sealed round2 report. Later corrections belong in separate addenda.

**Blocked acceptance:** exact Android callbacks, durable storage faults, SAF providers, source continuity, rendered HOLD pixels, gestures and accessibility were unavailable under this source-only assignment. **Evidence:** those operations were expressly prohibited, and the inherited integration receipt leaves them unaccepted. **Best useful result:** full requirement-to-source coverage, one finite material lifecycle defect, exact R15 score and a sealed immutable readset. **Next smallest check:** the startup light commit/rollback double-failure sequence described above, through actual initialization order.

No APK installation, hardware success or final release is accepted here. R13 freshness, root stereo, SoundCloud, audibility, latency, R09 mixing, R17 and other expansion sections keep their separate dispositions.

**All ownership is released. No source hold, worker or background task remains. All review work stops after private receipt sealing and the required completion report.**
