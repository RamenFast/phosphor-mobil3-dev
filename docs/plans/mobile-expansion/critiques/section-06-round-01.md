# R15 section 6, round 1: independent critique

## Decision

**Score: 7/10.** The implementation mostly realizes Ben's six-color instrument intent, but material state-publication and cycle-identity gaps prevent an 8. This is a source and narrow-host critique, not phone acceptance or release approval.

The largest intent mismatch is a split light authority during concurrent import and editing. A legal schedule can leave the visible controls and saved setup describing B while the native beam uses A. That undermines a truthful realtime instrument even when every individual tuple is valid.

The six-slot model, independent membership, generated owner, whole-state guard, native clock, and archive migration are substantial and appropriately bounded. No new audio reader, source command surface, permission, or shared renderer fork was needed. The finite corrections below should precede round 2. R13 is not rescored. Its carried 6/7/7 results and remaining gaps stay carried.

## Exact scope and provenance

- Reviewer: iwazaru, independent of implementation, GPT Astra/high through the coordinator-supplied enforced `openai-oauth:gpt-6-astra` route. Routing approval was inherited, not repeated by this worker.
- Review started: 2026-09-08T08:44:41Z. Source inspection and bounded repros ended before 08:54Z. Deadline: 08:59:40Z.
- Mobile commit: `7ac1e5546a58711a68c4b09bb8f18fe9f057df43`.
- Mobile Git tree: `bd8ff4bb8d12479b20560c66e1c3e99e431ed9e1`.
- Shared commit: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Shared Git tree: `51f828e60c5bbfd7003694bf28b7455daf07db1e`.
- Private Git-archive snapshot and all new artifacts: `/home/ben/.jcode/scratch/r15-s6-round1-astra-ExXWDhqd/`.
- `readset.json` SHA-256: `371b61b8c1463952d140cc8eb1fe1afed5494b32d26951bd1f68cae68ff77be2`.
- `readset.sha256` SHA-256: `c0f7a06830c3124d66af8631d5f4f81068641d1f699f0d5bebc3726744c52a08`.
- `host-artifacts.sha256` SHA-256: `fc51e81848ca8ee580945282a87d2578f7d500a8e869e922e8c6342efbfdc687`.

The 27-file readset contains exact Git blob IDs, full-file SHA-256 hashes, and inspected line ranges. Every listed snapshot file was independently verified against its specified Git object. Excerpt review is not mislabeled as full-file review. Whole trees were extracted for private immutable-source access, but their unread bodies are not claimed as reviewed.

All product line references below are relative to the frozen mobile tree unless prefixed `shared/`. MainActivity means `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`. LightSettings and LightSheet mean the files under that package's `ui/`. Archive means its `settings/SettingsArchive.kt`.

No live source changed. No Git mutation, Gradle, Android/JNI target execution, GPU, ADB, GUI, audio, network, service action, or worker spawn occurred. Existing reports were preserved. Only new private scratch snapshot/report/readset/host artifacts were written.

## Inherited evidence, not newly executed acceptance

The reviewed validation receipt, `docs/plans/mobile-expansion/section-06-color-validation.md:9-27`, records gate `942127xywq`: 539 JVM tests, 104 native tests, three offscreen GPU tests, lint, both debug APKs, engine integration, helper build and source boundary. It records unchanged complete mobile/shared manifests and explicitly says neither APK was installed.

At 08:50:52Z, the coordinator supplied the later clean exact-7ac1 gate `113021dsr0`, passed at 08:46, also without installation:

| Artifact | Supplied SHA-256 |
|---|---|
| Clean source archive | `72bb4928412f0b3b5c056277ad12c4808c3cf02a23aeae571c1374b00963fda6` |
| App APK | `cb08c744d06f99fcc7d8d9ce0e74a95d45966549a14894ba7e00be5163e15ca5` |
| Test APK | `bead1ca1e2d3d943825a9ce36fcfe3dd99cadad880b6e761fe22023a6ba0226d` |

These are inherited receipts. This reviewer did not rerun those gates or independently hash their APKs. The later root-contract-only commit was not substituted for the review baseline. Compile counts did not determine the score.

## Finite findings and smallest corrections

### F1. High priority: concurrent import and edit can split native, saved and UI state

**Requirement:** one validated coherent snapshot and preservation of effective omitted settings. Contract: `section-06-color-contract.md:7,28,36,60`.

**Source:** MainActivity starts import on a raw background thread at 239-243. It reads and merges current preferences at 267-269, commits at 285, publishes native light at 295, then schedules UI restoration at 302-306. Ordinary LIGHT edits commit and publish independently at 1909-1933. Neither path shares a transaction lock, serialized owner, nor publication revision. `restoreTuning(true)` rereads preferences at 1410-1413 and skips native publication at 1416.

**Finite reproduction schedule:**

1. Import prepares valid snapshot A and successfully commits A at line 285.
2. Before import reaches line 295, a LIGHT edit commits B at line 1915 and publishes B at line 1920.
3. Import then publishes A at line 295, after B in the native queue.
4. Import's UI callback calls `restoreTuning(true)`. Preferences now contain B, so UI becomes B without republishing B.
5. The native queue resolves to A. Preferences and UI remain B until a later light change.

This is a source-established legal interleaving, not an Android scheduling observation. A related stale-merge schedule can overwrite a user edit to an omitted light field because the partial import was expanded into a complete tuple before the edit.

**Smallest correction:** share one serialization boundary for effective merge, guard, persistence, native publication, rollback and accepted UI revision. Keep document reading/checksum work off-thread. Do not reread an unrelated later preference snapshot and label it already published. Add a latch-controlled test of the exact A/B schedule and a partial-import-versus-edit case.

### F2. Medium priority: identical TRACK publication invents a color step

**Requirement:** TRACK holds one color and steps once per accepted track event. Contract: 44,48. Unchanged settings are not a new membership/order change.

**Source:** `rust/src/light_cycle.rs:38-44` clears the bag and calls `next_color()` on every valid apply. Lines 81-85 advance ordered selection relative to `last_id`. There is no unchanged-settings check. LightSheet line 137 republishes `light.copy(perTrack = true)` even when TRACK is already active. MainActivity 1909-1933 accepts this redundant publication.

**Executed reproduction:** `cycle-review.rs` imports the original frozen Rust module by path. Start ordered TRACK with red/green/blue selected. Apply the exact same settings twice without calling `track()`.

Observed `cycle-review.log`:

```text
IDENTICAL_TRACK_APPLY before=[1.0, 0.0, 0.0] after=[0.0, 1.0, 0.0]
```

Thus clicking already-active TRACK has a source-backed path to a spurious step. The host effect is proven. The phone click itself was not executed.

**Smallest correction:** make unchanged publication idempotent when no temporary roll needs retirement. Distinguish owner/membership changes from timing-only or inactive-field edits. Preserve legitimate explicit roll and owner activation semantics. Add same-snapshot TRACK and TIMER tests. The existing native test at 143-151 repeatedly applies identical settings and expects a changed identity, so change that fixture to exercise an actual membership change rather than enshrining redundant-apply advancement.

### F3. Medium priority: deletion loses saved-slot identity and can immediately repeat

**Requirement:** no immediate repeat across a membership reset when another selected identity exists, including distinct identities with equal RGB. Contract: 7,50.

**Source:** Kotlin correctly deletes and shifts selected bits at LightSettings 49-53. Native `apply` replaces the colors but retains old `last_id` at `light_cycle.rs:38-44`. The bag-refill repeat guard compares that stale positional index at 77-85. After deleting an earlier slot, the same retained saved color has a different index.

**Executed reproduction:** `delete-review.rs` uses the exact native module, seed 1, TRACK plus shuffle, selected red/green/blue. Accept one track event, yielding blue. Delete red and apply the resulting green/blue bank with mask 3.

Observed `delete-review.log`:

```text
REPEAT_AFTER_DELETE seed=1 track_advances=1 before=[0.0, 0.0, 1.0] after=[0.0, 0.0, 1.0] two distinct surviving slots
```

Blue repeats immediately although green remains selected. The initial narrower seed-only probe did not reproduce this. The preserved second harness includes the necessary accepted track event.

**Smallest correction:** preserve native slot identity through delete/reindex. Supply a deletion remap or equivalent operation knowledge instead of treating old indexes as current identities. RGB equality alone is insufficient because equal-valued slots remain distinct identities. Add deletion-before-current tests, including duplicate RGB identities, alongside the existing unchanged-bank refill test.

### F4. Medium priority: native-rejection rollback reports success without checking it

**Requirement:** failed publication preserves the prior settings, and a blocked outcome names the actual persistence failure. Contract: 36,38,72.

**Source:** MainActivity 295-297 calls `restorePreferenceSnapshots(priorValues)` after native queue rejection but ignores its Boolean and unconditionally reports “Previous preferences restored.” The helper returns the actual rollback commit result at 1585-1608. The initial import-commit failure branch at 285-292 already distinguishes rollback failure correctly. Ordinary edit failure paths at 1915-1923 also discard rollback results.

**Finite reproduction:** allow the initial settings commit, reject native publication, and make the rollback commit return false. The import then emits a restoration claim it did not establish. Persistence can remain uncertain even while native retains the prior setup.

This branch outcome is source-proven. Android storage failure was not injected. The presence of rollback calls in `KnownDefaultsTest:180-206` does not test their return values.

**Smallest correction:** propagate the rollback result in all light failure paths, matching the existing import-commit branch. Retain the last accepted tuple and report when durable recovery is uncertain. Test accepted commit plus native rejection plus both rollback outcomes. Do not claim restoration merely because rollback was attempted.

### F5. Medium priority, conditional recovery path: complete valid import cannot repair invalid current light

**Requirement:** meaningful recovery and complete-tuple import validation. Contract: 36,72. MainActivity's own restore error offers a valid LIGHT archive at 1417-1419.

**Source:** Archive 309-313 eagerly evaluates `LightSettings.read(existing)` before merging any imported fields. This rejects invalid current light even when the import contains every valid replacement field.

**Executed reproduction:** `ArchiveReview.kt` uses the original frozen LightSettings and Archive classes, not a copied validator. Export and decode a valid complete default /2 tuple. Set only the existing map to count 0, empty RGB, mask 1. Import the complete valid replacement.

Observed `archive-review.log`:

```text
FULL_REPAIR rejected=invalid_light_tuple message=Select only existing saved slots fix=Include matching RGB, count and selection, and an ordered interval range
WHOLE_GUARD control=pass
```

The replacement itself is valid. Eager parsing of the destination blocks the advertised repair. This is conditional on preexisting invalid settings. No installed corruption or origin of such corruption is claimed. The finding does not justify resetting unrelated settings.

**Smallest correction:** validate the effective merged raw tuple, or allow a complete replacement to establish its own valid base. Keep partial-import rejection strict when omitted destination fields remain invalid. Add paired complete-repair and invalid-partial-preservation cases.

## Requirement coverage and evidence limits

| Approved requirement | Inspected evidence and result | Remaining uncertainty or defect |
|---|---|---|
| Six saved slots across UI, persistence, archive, JNI and Rust | LightSettings 9-89, ScopeUiState 112-128, LightSheet 93-124, Archive 113-130, JNI 543-563, native settings 3-24. Capacity and exact RGB/mask validation agree. | Actual six-slot CRUD not performed on phone. |
| Independent selection, deletion, one solid, zero explicit preset | LightSettings toggle/delete/preset preserve bank independently. Native moving/custom rules and solid test at 213-217 agree. UI exposes labeled separate actions. | Native identity across deletion is F3. |
| Generated roll and automatic owner | LightSheet 79-85,126-134. Native 49-65,93-101. Generated owner takes precedence, rolls do not alter settings, saved shuffle remains retained. | Actual visible owner transition and rapid repeated roll not observed. |
| Random interval within 0.1-60 seconds | Kotlin finite/min-max validation and native settings validation agree. Native begin_leg 87-92 draws once per new leg and treats equal bounds as fixed. | Device timing accuracy not measured. |
| Shuffle bag and no immediate repeat | Native 66-85,193-204 covers bag/refill and duplicate-valued identities for unchanged storage. | Delete/reindex reset violates the guarantee, F3. |
| Rust-only randomization and cycle clock | Render owns LightCycle at 285-288. Observation draws only when it starts a new leg. No Kotlin random source or audio-thread cycle work was added. | Redundant apply causes unnecessary activation, F2. |
| TIMER interpolation and TRACK stepping | Native 98-118 and tests 125-141,175-190. TRACK interval controls are absent at LightSheet 138-150. | Identical TRACK apply steps without metadata, F2. |
| HOLD pixels and hidden work | Render suspends light at 294-296, receives commands while idle, handles TRACK at 512, and exits retained presentation at 616 before live color observation at 723. | Actual Android HOLD/hidden/handoff timing untested. Unchanged SurfaceHost requires a valid lease before accepting metadata at 93-99. No claim that every no-surface track is delivered. R13 gaps remain carried. |
| Whole-state rapid guard | LightCycleGuard 94-100 and MainActivity 268-272,1910-1915 guard before publication. Min/max clamp coherently. LightSheet 64-76 handles pending/dismiss/accept. Independent host control confirmed safe min=max=1 with complete pending intent. | Actual warning, Back, dismissal and persisted local acknowledgement not exercised on phone. |
| /1 and /2 migration, partial import and omission preservation | LightSettings 68-89, Archive 256-314, LightArchiveTest full body. Old nine components/count 0 retain three slots, count-only import retains six-slot bank, RGB-only invalid shrink rejects, unknown scalar keys are checksum-covered. | F1 concurrent stale merge and F5 recovery. Pure map tests do not exercise Activity transactions. |
| Invalid tuples and failure preservation | Kotlin and JNI reject finite/count/mask violations before queueing. Apply returns false before mutation on invalid native settings. | F1 transaction ordering and F4 rollback truth. JNI Boolean proves validation plus queue acceptance, not a rendered GPU frame. |
| UI access and source/remote control preservation | LightKey minimum 48dp, labels/roles/state, LightRule range semantics/setProgress/keyboard arrows, RGB alternatives to pixel HSV. PhosphorScreen 787-794 uses existing actions. MainActivity 1516-1519 preserves explicit accepted preset remote-theme forwarding only. | TalkBack, switch access, large font, narrow layout and touch affordances remain unobserved. |
| Existing source, shared theme and privacy boundaries | The section6 commit diff leaves SurfaceHost, SurfaceOwner, capture/root/playback services, manifest, deck and engine unchanged. Shared Theme remains a single RGB triple at shared beam lib 98-104,167-171. Existing sRGB conversion remains at 350-356. Inherited production boundary passed. | No phone audio/source continuity claim. No release/artifact verification rerun by this reviewer. |

## Realtime and UX follow-up, not a measured latency finding

Every HSV pointer update and RGB/timing slider update reaches synchronous `SharedPreferences.Editor.commit()` through LightSheet 111-116,144-150,222-234 and MainActivity 1913-1915. This is definite UI-thread disk work per change. Actual stalls or audio loss were not measured, so no latency number or audible regression is claimed.

When closing F1, retain durable-before-accepted publication without moving more disk work onto the UI thread. A small serialized off-main path with bounded/coalesced continuous edits can serve both correctness and responsiveness. Measure drag responsiveness and frame pacing on the actual device before calling the realtime interaction accepted.

## Checks performed by this reviewer

1. Extracted only the specified Git objects to private scratch. Verified all 27 readset files against exact Git blob hashes.
2. Read canonical section 6 and R15, active expansion contracts, source map, writer handoff and validation.
3. Traced actual typed state, archive import/export, Activity rollback/publication, UI callbacks, JNI, native cycle and retained/live render branches.
4. Inspected existing guard, migration, archive, source-wiring and metadata-gate tests. Did not rerun broad suites.
5. Compiled and executed two narrow Rust diagnostic harnesses with `rustc 1.96.0`, importing the unmodified frozen native module. Their logs establish F2 and F3.
6. Compiled only frozen LightSettings, Archive and the private ArchiveReview harness with cached Kotlin 2.4.10 and org.json. Ran on OpenJDK 21.0.11. No Android classes or cached app implementation were substituted. The diagnostic establishes F5, with one asserted positive guard control.
7. Inspected the exact section6 render delta and confirmed the named source-control files have no delta against parent `39ba832966d6836b0d2a322d827642f98d30352f`.

These diagnostics exit successfully because they record the observed defect, not because the desired behavior passed. Host-artifact hashes preserve source, binaries and logs. The first attempted shell artifact-write command was refused before execution. New explicit scratch files were then created through the file tool.

## Prioritized disposition and release

1. Close F1 with shared transaction/revision ownership and latch-controlled race tests. Close F4 in the same narrow failure seam.
2. Make unchanged native apply idempotent and preserve identity through deletion, F2 and F3. Test actual operation changes, including duplicate-valued slots.
3. Repair the finite complete-import recovery path, F5, without weakening partial merge validation or deleting unrelated settings.
4. Run the coordinator's exact-source integration gate on the corrections, then obtain round 2. Keep this round's original checkpoint and receipts unchanged.
5. Complete permitted phone acceptance for six-slot editing, owner summaries, accessibility, warning flows, timer/shuffle behavior, HOLD/TRACK handoff and responsiveness. Build success alone does not close these.

The review is complete within its boundary, not blocked on unavailable phone acceptance. All review/source holds are explicitly released. No implementation ownership or further source edits are retained. Coordinator corrections may proceed after preserving this immutable report and readset.
