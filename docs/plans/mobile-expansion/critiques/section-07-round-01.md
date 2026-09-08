# SECTION7 / R16 independent full-section critique

## Decision

**Round 1 of at most 4. Overall section score: 7/10. Material corrections required before the next critique.**

This is the complete Section 7 preset outcome review, not a core-only score. The typed records, native admission primitive, browser and Activity workflow form a substantial implementation. Three concrete Activity integration gaps remain. Android outcome acceptance is still open.

The largest intent mismatch is F2: a delayed whole-settings document can overwrite a more recent preset recall or authored edit. The instrument should preserve the user's latest action, not let an earlier provider read become an unannounced later tuning command.

The rating uses canonical R15's 6–7 anchor: mostly implemented with material gaps. It is not an arithmetic average or a phone acceptance score. Intent fidelity and privacy have strong source support. Functional ordering and recovery have the defects below. UX/accessibility, lifecycle scheduling, durable storage and actual rendering lack the required phone evidence.

## Identity, immutable scope and authority

- Requested route: `openai-oauth:gpt-6-astra`, high effort, confirmed by the coordinator before assignment. This worker did not spawn agents or rerun the root ritual. Provider telemetry was not independently queried in this bounded review.
- Mobile: `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`, commit `5b1be193e6fbcb0d3f779a8c0da5774a9a05efc6`.
- Shared: `/home/ben/Dev/ClaudeWorkspace/phosphor`, commit `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Governing feature sources: canonical `MOBILE-EXPANSION-PLAN.md` Section 7, R15 and R16 validation, active `spec/EXPANSION.md:138-151`, and the living-instrument vision.
- Read all six named Section 7 documents: instrument contract, core handoff, native contract, workflow contract, core validation and workflow validation.
- Coordinator parrot owns corrections, Git, Gradle and devices. This report changed no product source, contract, test, original receipt or inherited report.
- Private output: `/home/ben/.jcode/scratch/r16-full-critic-round1-20260908/`.
- Source inspection ended and **all read holds were released at 11:22:20 UTC on 2026-09-08**. No hold remains on either repository or inherited artifacts. The coordinator was notified. No source reading followed release.

All production paths below are relative to the mobile commit unless explicitly marked shared. `MainActivity.kt` means `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`. Instrument Kotlin files are under that package's `settings/instrument/` directory. UI paths are under its `ui/` directory.

## Method and limits

This was a bounded static source critique. The three traces below are the complete reproduction set. None was executed on Android or on a substituted Activity.

Executed checks were exact Git-object extraction, per-file SHA256 comparison, and private snapshot verification. No Kotlin/JUnit, Cargo, rustc, Gradle, app build, JNI, GPU, ADB, phone, browser, GUI, audio, service or network execution occurred. No Python, downloads, workers or Git mutations occurred. There are no executed behavioral test runners or JVM/Rust dependencies to claim or hash. The executed shell receipt scripts and their command binaries are recorded separately.

The readset contains 53 tracked files, including 28 production Kotlin/Rust/XML files. This is below the 45-source-file ceiling. Tests and named contracts are additional. Six top-level documentation files were captured for context but not used as substantive review evidence. Two guessed nonexistent filenames produced empty private placeholders. They are explicitly excluded from the exact-source manifest in `capture-notes.txt`. They are not substituted production sources.

Both requested commits were available. Every tracked snapshot file was compared with its requested Git object, then checked again through `sha256sum -c`. All 53 matched. Snapshot sources were made read-only before report preparation.

## Findings

### F2. High: delayed whole-settings import can overwrite a later preset or edit

**Requirement:** instrument contract `:39`, coherent application step 5. Serialize preset application with manual tuning and import so a stale import cannot overwrite a later edit. Active R16 also requires coherent recall and preserved authored control intent.

**Exact source:**

- `MainActivity.kt:627-662`: the existing whole-settings picker starts an independent provider/decoder thread. Its success posts `acceptSettingsArchive(decoded)` without a tuning revision, operation ticket or stale-edit check.
- `MainActivity.kt:573-580`: acceptance checks Activity destruction and the uncertainty latch. It settles a current native request, but does not check whether tuning changed after this document operation began.
- `MainActivity.kt:583-612`: acceptance merges and commits preferences, then invokes `restoreTuning` and `externalRestoreSaved`.
- `MainActivity.kt:1687-1732`: restore replays mode, gain, beam, geometry, grid and focus from preferences into native and authored UI state.

**Bounded static trace, case 2:**

1. A whole-settings archive A contains focus 0.3 and a different mode from preset B. Choose A and delay its provider read.
2. After the picker returns, apply B and let B commit. Alternatively, edit focus to 1.2 while A is still being checked.
3. Release A's provider read. The success callback reaches acceptance with a live Activity and no current pending preset request.
4. A commits and `restoreTuning` overwrites the later B/focus action. There is no stale rejection or refreshed explicit acceptance.

**Observed versus inferred:** absence of revision checking and the unconditional restore call are directly observed. The interleaving is a finite source-derived Android callback trace, not an executed SAF result. Whether a specific provider exposes a long enough window remains a device check. The current code has no mechanism that would reject that ordering.

**Why existing tests do not close it:** `InstrumentWorkflowTest:121-143` checks synchronous nested import/edit ordering. `InstrumentActivityWiringTest:39-48` checks strings and placement of settle/write calls. Neither exercises the asynchronous whole-settings document callback after a later authored action. Instrument preset import itself is inert and does not have this defect.

**Smallest coordinator correction/check:** carry an authored-tuning revision from the whole-settings operation's initiation through acceptance. Reject stale results or offer a fresh explicit acceptance. Hold one real provider read behind a barrier, apply B, release archive A and verify that B is not overwritten silently. Do not solve this by disabling source-stop or unrelated controls.

**Confidence:** high in the static mechanism and requirement conflict. Android execution and provider timing remain unverified.

### F1. Medium: choosing a relay persists one field of an explicitly unsaved preset

**Requirement:** instrument contract `:39` preserves prior tuning on persistence failure. Workflow contract `:27-29` requires truthful active-but-unsaved state, preservation of prior values, and explicit complete-save recovery. `:39` preserves the known active unsaved gain tuple across source changes.

**Exact source:**

- `InstrumentWorkflow.kt:103-110,162-168`: after native commitment, publication installs the new setup. A failed persistence callback keeps it active with `unsaved=true` and retains prior undo.
- `MainActivity.kt:287-300`: preset persistence snapshots exactly the instrument keys and uses rollback on failure.
- `MainActivity.kt:256-265`: publication places the active candidate's authored manual gain into `gainValue`.
- `MainActivity.kt:1365-1367`: `startRemoteHost` settles the request, then unconditionally writes `gainValue` to the ordinary tuning preferences.
- `MainActivity.kt:1611-1658`: lifecycle preservation snapshots the preference values that exist at that later moment. It cannot recover the original gain once the source-selection write replaced it.

**Bounded static trace, case 1:**

1. Durable setup A has gain 1.0. Apply B with gain 4.0 and different other tuning.
2. B is admitted natively. Its settings commit fails, and rollback successfully restores the complete durable A snapshot. The UI truthfully reports B active but not saved.
3. Choose a saved relay. `startRemoteHost` writes B's gain 4.0 alone, without RETRY SAVE CURRENT or a tuning edit.
4. Preferences now contain A's remaining tuning plus B's gain. A later successful lifecycle save preserves that already-mixed map while `unsaved` remains true. A subsequent restore can land on the mixed setup.

**Observed versus inferred:** the unconditional source-action write and publication of B into `gainValue` are observed. The commit-failure/rollback/restart sequence is static, not a real filesystem result. The fixture needs one failed preset commit, successful rollback, and a subsequent successful relay-selection write.

**Smallest coordinator correction/check:** do not persist active unsaved instrument fields from source selection. Keep source operations independent of tuning durability. Exercise the failed-commit/successful-rollback trace, choose a relay and compare the exact instrument preference map, including absent keys, against A. The active B gain tuple should remain available without becoming partially durable.

**Confidence:** high in source reachability, with real Android durability unverified. This affects failure recovery, not ordinary successful recall.

### F3. Medium: ordinary light/archive rollback failure does not latch shared tuning uncertainty

**Requirement:** workflow contract `:29` says failed rollback blocks tuning, APPLY, UNDO and whole-settings import until explicit complete-save recovery. The full R16 owner includes manual light and whole-settings import boundaries, not only `persistInstrument`.

**Exact source:**

- `settings/SettingsWriteOwner.kt:15-31`: failures retain a typed `restored` flag.
- `MainActivity.kt:299-300`: the preset persistence adapter correctly preserves that type as `InstrumentWorkflow.PersistenceFailure`.
- `MainActivity.kt:600-604`: the whole-settings adapter instead throws only `error(it.message())`.
- `MainActivity.kt:2291-2298`: ordinary light persistence stores only `failure.message()` and returns false.
- `InstrumentWorkflow.kt:37-41,140,162-168`: blocking depends on the workflow's own uncertainty fields. Only its save path updates `storageUncertain`.
- `MainActivity.kt:1611-1613`: autosave preserves instrument keys only when the workflow knows that tuning is unsaved.

**Bounded static trace, case 3:**

1. Start with a healthy workflow and known active setup A.
2. An ordinary light write fails and its rollback also fails. This can leave uncertain disk state even if the Android in-memory map appears restored.
3. The Activity records a light-error string, but `unsaved=false` and `editsBlocked=false` remain unchanged in the workflow.
4. APPLY, UNDO, manual edits and whole-settings import remain admitted. Lifecycle autosave does not enter the preservation branch. The same typed-information loss exists in whole-settings import.

**Observed versus inferred:** typed-information loss is observed in both adapters. The exact disk state after double failure is intentionally unknown. The defect is the missing block/recovery state, not a claim that a particular filesystem must retain a particular value.

**Why existing tests do not close it:** `InstrumentWorkflowTest:301-337` exercises failed rollback through the preset persistence callback. It does not execute either actual Activity adapter that converts the failure to a string. The dedicated preset collection store separately latches uncertainty correctly.

**Smallest coordinator correction/check:** route shared tuning persistence failures into one typed recovery state. Preserve source-stop controls. Inject commit false plus rollback false through the actual light and archive adapters. Verify that tuning, APPLY, UNDO and import remain blocked, autosave preserves instrument keys, and only explicit complete-save success clears the latch.

**Confidence:** high in the missing propagation. This is a full-section integration finding, not a defect in the pure preset collection. Real storage failure remains unexecuted.

## Complete requirement coverage

The following rows distinguish source evidence from inherited tests and unaccepted outcomes. “No defect found” does not mean runtime acceptance.

| Section 7 requirement | Inspected evidence and result | Remaining uncertainty |
|---|---|---|
| Named surface near Light, separate from appearance/archive | `ui/Sheets.kt:1353-1382`, `ui/PhosphorScreen.kt:268-272,792-813`, `ui/InstrumentPresetSheet.kt:59-78,109-154`. Dedicated routing and clearly named document controls exist. | Only one settings row into the browser was found. The separately requested compact recall action is not clearly distinguishable from that row. Treat this as an intent/UX follow-up, not a fourth reproduction finding. |
| Complete typed authored setup, no preference-map serialization | `InstrumentSetup.kt:20-60`, codec `:67-107`, preferences `:4-13`. All enumerated fields and existing six-color validation are represented. | No new core defect found. Android control values at all legacy restore boundaries were not exhaustively exercised. |
| Local authored gain, local auto-gain and focus | Activity `:248-284,989-1006,1988-1996`. Snapshot reads `gainValue`, `localAutoGain`, shared focus and underlying light. Measured/remote display values are separate. | F1 is a later durability side effect. Actual audio-only relay display and recreation need device evidence. |
| Create, save, update, duplicate, rename, delete | Collection `:46-79,128-139`, Activity `:377-416`, sheet `:79-120`. Stable IDs and explicit save actions exist. Curated records cannot be updated/deleted directly. | UI validation, failure presentation and app-update survival remain Android checks. |
| Saved records remain unchanged by live edits, modified association | Workflow `:43,103-107,183-192`; Activity `:338-347`; collection values are defensive. Matching rename/delete association paths exist. | Actual recomposition and displayed modified state remain unaccepted. |
| Curated Clean XY, Spectral bench, Ambient | Setup `:96-115`, shared DSP mode order `crates/phosphor-dsp/src/lib.rs:67-85`, shared beam theme array `crates/phosphor-beam/src/lib.rs:106-122`, curated tuple tests. Values agree with contract and avoid calibration claims. | Visual suitability is source-supported, not measured phone pixels. |
| Validate complete setup before renderer mutation | Codec strict fields/types and `rust/src/instrument.rs:71-102`; request construction validates again. Native metadata is excluded. | Host decoder tests are inherited. No JNI execution performed. |
| Coherent native admission, no partial late apply | `instrument.rs:119-209`, `jni_glue.rs:23-61`, `render.rs:512-539`. One receipt and command, finite deadline, exact terminal outcome, bounded receipt retention, no native setter replay from Activity publication. | Actual render command is inspected statically. Host request tests do not execute Android render admission or prove whole-frame observation. |
| Manual edits, settings import, lifecycle ordering | Workflow `:91-145,195-201`; Activity `:350-361,573-662,913-959,1845-1924,2280-2358`. Committed cancellation reconciliation and stale native-reply rejection are implemented. | F2 is a real asynchronous archive boundary not protected by the synchronous owner. F3 misses shared recovery propagation. |
| Immediate one-level undo | Workflow `:77-81,103-108`; sheet `:70-72`. Same guard/admission path, one slot, consumed after committed undo. | Guard, source changes, storage faults and displayed control restoration still need Android acceptance. |
| Rapid guard, six-color/range preservation | Setup `:78-81`, `ui/LightSettings.kt:21-27,94-101`, workflow `:57-58,83-88`, sheet `:73-77`. Decode/import never acknowledges. Guarded recall compares against original association and reports modified. | Real acknowledgement UI and safety interaction are unaccepted. No guard bypass found in preset path. |
| HOLD image and inspection remain untouched by preset | Render command `:512-539` does not touch pause History/inspection. Held path `:575-626` presents the retained image separately. Activity publication has no pause/source/surface call. | No phone pixels or inspection gesture execution. R13 freshness and root stereo remain separate unaccepted features, not rescored here. |
| Source/transport/permission/theme/HDR/brightness exclusions | Setup/codec exact allowlists, Activity publication/persistence, native assignment and source capability checks. No capture/root/mic/relay startup, volume, pause or Surface command in preset apply. | Actual playback position, audio continuity and device volume are not verified. F1 demonstrates the reverse boundary, a source action leaking into failed-preset durability. |
| Remote geometry truthful refusal, audio-only relay local | Activity `:325,1380-1384`, native `render.rs:513`, `remote.rs:637-648`, sheet `:66-68`. Native admission rechecks actual geometry ownership and does not send remote setters. | Source capability mirrors through Activity recreation need Android confirmation. A generic rejected result is not a full remote-state UI proof. |
| Durable collection, corrupt bytes preserved, update survival | Store `:13-63`, collection constants, dedicated SharedPreferences adapter `MainActivity.kt:304-315`. Failed collection commits restore original bytes or latch uncertainty. Backup XML includes settings and excludes private runtime/endpoint containers. | Real SharedPreferences disk rollback, Android backup/restore and app updates remain unproven. |
| Versioned bounded documents, checksum and strict parsing | Codec `:15-60,110-154,194-305`; deterministic empty/nonempty test vectors, Unicode/name/UUID and collection limits inspected. Full six-slot setup and native field agreement covered by inherited tests. | No production codec execution rerun by this critic. |
| Inert complete import and explicit duplicate choices | Collection `:85-116,151-159`, Activity `:437-474,542-563`, sheet `:126-153`. Preview validates before choices. Resolver rejects stale collection, missing choices and duplicate replacement targets. No tuning apply in document import. | Actual provider and UI flows remain unaccepted. This is distinct from whole-settings F2. |
| Export all or selected, cancellation and bounded document ownership | Store stream reader `:66-86`, document owner `:14-38`, Activity `:427-563`. Snapshot export precedes picker, provider errors warn of partial document, cancelled ticket retains slot until return. | Process recreation and blocked-provider resource lifetime require real Android checks. No claim of provider cancellation or durable export. |
| Large-font and 48dp accessibility | Sheet `:159-175` requests 48dp minimums, unlimited button lines and labeled text fields. Source/refusal/recovery controls exist. | No measured target size, TalkBack traversal, focus retention, large-font layout or performance test. Long lists use an eager scrollable Column. |

## Evidence provenance

### Independently executed during this critique

1. Confirmed both exact commit objects exist.
2. Extracted source from immutable Git objects only. No live working file was used as product-source evidence.
3. Compared all 53 tracked private snapshot files against the requested commit contents by SHA256. All matched.
4. Ran `sha256sum -c sources.sha256`. All 53 passed.
5. Independently hashed the inherited writer's changed-path manifest, original report and runner. Manifest/report match the hashes cited in the immutable workflow validation document. This checks artifact identity, not the truth of every inherited runtime claim.
6. Sealed the new private report/readset/receipt. Shell runner and command binary hashes are retained in the release inventory.

### Inherited, not rerun or promoted

The immutable `section-07-workflow-validation.md` reports coordinator gate `580618965w`: 624 JVM tests in 57 suites, 121 native host tests, 3 offscreen Vulkan GPU tests, lint, both debug APKs, engine/release-helper and production source boundary passed. It reports unchanged complete source lists/hashes during the working-source gate.

That gate was a working-source integration pass, not a clean final installation freeze. No APK installation or phone action occurred. The source-level hashes above independently pin this critique, but do not turn the earlier gate into a reviewed final installation gate.

The writer's sealed release reports 86 actual-source host tests across ten suites. Its manifest SHA256 is `f20dc827b8d2da59269bdc56817a5a3990015ed97bbebe0128d951ff313e041a`. This critic inspected the existing runner but did not run it. The original files remain untouched.

The core handoff/core validation also report strict codec/CRUD coverage, native request tests and four Kotlin-to-Rust setup vectors. These are inherited, scoped host results. String-based Activity wiring checks are supplementary. No stubbed Android adapter or copied substitute production logic was used here.

## Readset and receipt hashes

| Artifact | SHA256 |
|---|---|
| `sources.sha256` | `7316899ba4a53744a54b9126900cc13a87f3bb03aabc4839598f5985c0f65759` |
| `git-objects.tsv` | `491461d06fcecc5312d87c96f2387b752ed9313fea3994266f38fe4728be6078` |
| Inherited writer `changed-paths.sha256` | `f20dc827b8d2da59269bdc56817a5a3990015ed97bbebe0128d951ff313e041a` |
| Inherited writer `RELEASE.md` | `a939c83404485aa620976eae66ad02c0b474aa8cc7af5504e042571e6756bf1a` |
| Inherited writer `run-tests.sh`, inspected but not executed | `85a82ab7278aa2a40d219498d1041871d59686b6fbdde49035b3a3841bf51936` |

`SEAL.txt` contains this report's final hash and the complete private release-inventory hash. `sources.sha256` enumerates exact content hashes for every tracked snapshot file. `git-objects.tsv` maps each path to its immutable source commit and blob ID.

## Disposition and remaining acceptance

Prioritize F2's stale document ordering, then F1's source-selection durability leak and F3's shared rollback propagation. Keep these corrections narrow. Recheck the corrected full Activity boundaries with actual production adapters before the next independent section critique. This remains round 1, not three rounds for three findings.

Before delivery, the coordinator still needs clean reviewed source, the full gate, exact dual-APK identity and authorized Android checks for CRUD/apply/undo, SAF, storage faults, local/audio-only relay continuity, remote refusal, HOLD/inspection, source position, volume and accessibility. Host or compilation success cannot replace those checks.

**Blocked:** exact Android, SAF, storage, GPU-pixel, accessibility and hardware evidence is unavailable within these bounds. **Evidence:** this assignment excludes those operations, and the inherited workflow receipt explicitly leaves them unaccepted. **Best current result:** a complete finite source critique with three material traces, full requirement coverage and exact immutable readset. **Next step:** the smallest coordinator-owned correction and targeted adapter/provider checks named under each finding, followed by the authorized acceptance sequence.

R13, root stereo, SoundCloud and other expansion sections retain their separate prior dispositions. They are neither accepted nor rescored by this Section 7 review.

This original report is immutable after sealing. Any later correction belongs in a separate narrow addendum. All source reads and holds are released, with no worker or background task left by this critic.
