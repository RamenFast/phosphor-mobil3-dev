# R15 section 6, round 2: independent correction critique

## Decision

**Score: 7/10.** Correction 01 closes the original import/edit race, rollback-message defect, exact-deletion identity defect, and complete-archive repair defect at the inspected source and pure-host level. Identical publication and timing-only changes are now idempotent. One material F2 seam remains: changing inactive saved storage can still advance TRACK without a track event.

This is not a repeat of round 1's identical-publication finding. Two finite ordinary UI operations reach the remaining defect. Editing an unselected RGB slot advances a selected saved TRACK cycle. Deleting a saved slot advances automatic generated TRACK even though generated color still owns the beam. Those violate the correction's explicit inactive-setting contract and prevent an 8. The correction makes substantial progress, but test totals do not replace the behavioral requirement.

This score applies only to exact mobile `1e68b6327252b22424839b5f6c8464cd5731f3da`, paired with shared `0ffd658d7f19e68180c2720e0500b23644619e90`. It is neither Android integration approval nor device acceptance. **R13's separate 6/7/7 evidence and remaining gaps remain unchanged. R13 is not rescored.**

## Source identity and review boundary

- Reviewer: pawprint, independent critic, not an implementation writer. Coordinator-supplied enforced route: `openai-oauth:gpt-6-astra`, explicit high. No route change, routing ritual, or worker spawn occurred.
- Start: `2026-09-08T09:13:01Z`. Deadline: `2026-09-08T09:28:01Z`.
- Substantive source inspection ended at 09:18:15Z. Tests ended at 09:17:25Z. Final source-object identity verification ended at 09:20:13Z. No source reading or testing remains active.
- Mobile tree: `5e774aad3bb49eef3b377f21c5f2c8a7cf224db6`.
- Round-1 mobile checkpoint: `7ac1e5546a58711a68c4b09bb8f18fe9f057df43`, tree `bd8ff4bb8d12479b20560c66e1c3e99e431ed9e1`.
- Shared tree: `51f828e60c5bbfd7003694bf28b7455daf07db1e`.
- Private artifact directory: `/home/ben/.jcode/scratch/section6-round2-pawprint-20260908T091301Z/`.

All product source came from exact Git objects or the private `git archive` snapshot. No mutable working-source body was inspected. The full archive was extracted for safe access, but unread bodies are not claimed as reviewed. `readset.tsv` distinguishes human excerpts, compiled source, old-side diff hunks, and discovery-only keyword scans. Its 62 entries include discovery scope, not 62 fully reviewed files. Every entry's bytes were verified against its exact Git blob ID. Baseline comparison is preserved in `correction-delta.patch`.

The immutable round-1 report and its separate process addendum were read from the exact correction commit and left unchanged. Correction 01's source contract was read before this worker's tests. Its pre-runtime authorship is supplied coordinator history, not a chronology independently proven by the combined correction commit.

No tracked edits, Git mutation, Gradle, Android/JNI target execution, GPU, device/ADB/GUI/audio action, privileged command, service control, Python, or additional worker occurred. Only new private snapshot, report, runner, and host-evidence files were written. Procedural disclosure: the mandatory harness integration lookup for local Git returned HTTP 400. This was a failed catalog request, not a product network test. No network command, download, or product service interaction followed. A malformed batch Git-tool entry also failed before command execution.

## New material finding: inactive storage changes fabricate TRACK steps

**Priority: medium. Disposition: F2 partially corrected, still open.**

**Requirements:** `section-06-correction-01.md`, required behavior 3, says timing-only and inactive-setting changes do not fabricate TRACK events. `section-06-color-contract.md:11,44,48` separates generated ownership from saved storage and requires TRACK to hold its color between accepted events. `spec/EXPANSION.md:142-144` preserves independent slot editing and separate generated ownership.

### Case A: edit an unselected saved slot

Use saved red, green, blue with selected mask 3, ordered TRACK, seed 1. Red and green are selected. Blue is not. Apply the tuple, then publish an identical tuple and a timing-only change as positive controls. Both preserve red. Change only the unselected blue slot's red component to 0.5. Do not call `track()`.

Observed exact native module result:

```text
UNSELECTED_RGB_EDIT no_track_calls=true before=[1.0, 0.0, 0.0] after=[0.0, 1.0, 0.0] unchanged=false
```

Cause: `rust/src/light_cycle.rs:66-72` compares the entire saved RGB bank in `same_owner`, not only the active selected bank. An unselected RGB edit therefore reaches `apply_edit:58-62` and calls `next_color()`. Neither the active owner nor selected membership/RGB changed.

This is reachable through the real source path. `LightSheet.kt:95-117` exposes RGB editing for every saved slot, without requiring selection. It calls `onLightChange`, forwarded by `PhosphorScreen.kt:788-795` to `MainActivity.setLight`. `MainActivity.kt:1907,1912-1935` persists and publishes the complete tuple. JNI queues it at `jni_glue.rs:543-565`. The renderer calls `apply_edit` at `render.rs:506-509`, then uses its observed color at `723-725`.

### Case B: delete inactive saved storage under automatic generated TRACK

Use three saved slots, mask 7, generated automatic color enabled, TRACK, seed 18. A regular saved RGB edit is a positive control and preserves the generated color. Delete saved index 0, pass exact deletion metadata `Some(0)`, and shift the mask to 3. Do not call `track()`.

Observed exact native module result:

```text
GENERATED_OWNER_DELETE no_track_calls=true before=[1.0, 0.3062256, 0.3062256] after=[0.12349445, 0.88045156, 1.0] unchanged=false
```

Cause: `light_cycle.rs:43-52` handles deletion before the `same_owner` fast path. After valid lineage validation and remapping, it falls through to `58-62` and generates a new color. The automatic generated owner has not changed. Unlike ordinary inactive RGB edits, deletion bypasses the inactive-owner preservation branch.

The actual deletion UI calls `onDeleteSlot(index)` at `LightSheet.kt:107-109`. `PhosphorScreen.kt:793` forwards the index through `ScopeActions.deleteLightSlot`. `MainActivity.kt:1908-1909` derives the deletion from current `ui.light` and sends the exact index with the tuple. The Kotlin native signature, JNI command, and render forwarding retain it unchanged. This finding therefore uses the new production deletion route, not a hypothetical archive field or guessed RGB lineage.

**Evidence strength:** native outcomes are executed and deterministic. UI/JNI/render reachability is source-established. No Android callback, rendered pixel, or actual phone click was executed. `inactive-review.rs` imports the unmodified frozen native module by path. It contains two finite cases, no fuzz loop. Its successful exit records the observed defects with explicit diagnostic assertions. It does not mean the desired no-step behavior passed.

**Smallest remaining outcome:** inactive stored-slot edits and deletions must preserve the active TRACK state while retaining exact deletion remapping for later saved ownership. Keep genuine selected-bank changes, explicit rolls, and owner activation distinct. No change to DISPLAY locking, GPU ownership, audio readers, or source authority is indicated by this finding.

## F1–F5 requirement coverage

| Seam | Exact inspected behavior and executed check | Disposition and uncertainty |
|---|---|---|
| F1: UI owner serializes effective import merge and ordinary LIGHT edits | `MainActivity.kt:247-293` checks Activity liveness, merges current preferences on the main callback, guards, commits, queues, rolls back, then restores UI before another main event. `299-332` keeps document decode off-thread. `1912-1935` uses the same owner for edits. `SettingsWriteOwner.kt:8-12` rejects foreign-thread and nested writes before their blocks. Production tests `decodedPartialImportMergesAfterAnInterveningOwnerEdit` and `foreignAndNestedWritesCannotInterleaveTheCommitPublicationBoundary` passed. | Original legal commit-A/edit-B/publish-A interleaving is removed by the single main-thread callback. Pure owner race coverage and source wiring support closure of the bounded defect. No Android scheduling or drag-latency acceptance. UI restoration follows the owner block but remains in the same non-suspending main callback. |
| F2: identical TRACK/TIMER and timing-only changes | `light_cycle.rs:53-56,66-72` now stores same-owner settings without resetting the bag, leg, or RNG. Production `redundant_track_and_timer_apply_preserve_leg_and_rng` passed for TRACK and TIMER. Private controls also preserved TRACK across identical publication and changes to fixed seconds, random-interval enablement, minimum, and maximum. `begin_leg:113-117` reads updated timing at the next actual leg boundary. | Original redundant-apply defect is corrected. Inactive storage cases A and B remain material. Next-boundary timing application is source-established, not separately measured on Android. |
| F3: exact deletion identity, duplicate RGB, validation and real forwarding | `LightSettings.kt:49-53` deletes and shifts bits. Real typed action and native forwarding are traced above. `light_cycle.rs:43-52` validates exact old-bank removal and shifted mask before mutation, then remaps the prior index. Production `explicit_deletion_remaps_duplicate_rgb_slot_identity` passed for distinct and duplicate RGB. `invalid_deletion_does_not_mutate_settings_or_identity` passed. | Original deletion-before-current immediate repeat is corrected, including duplicate identity. Ordinary replacement does not infer deletion from RGB. Queue acceptance remains different from renderer-side lineage acceptance and a displayed frame. No valid UI sequence producing a lineage rejection was established. The inactive generated deletion activation is classified under F2, not a failure of the identity remap. |
| F4: truthful rollback failures | Both import and edit use `SettingsWriteOwner.commit` with `restorePreferenceSnapshots` as the rollback callback. The helper returns the actual editor result at `MainActivity.kt:1585-1608`. Owner lines `15-31` distinguish restored from uncertain persistence and catch callback exceptions. Production `nativeRejectionReportsBothActualRollbackOutcomes` and `failedSaveDoesNotPublishAndExceptionsStillAttemptRollback` passed. | Original false restoration claim is corrected. Actual SharedPreferences disk failure and process restart recovery were not injected. The tests exercise production transaction control with fake storage/publication callbacks. |
| F5: complete /2 repair with invalid partial preservation | `SettingsArchive.kt:309-318` allows a complete /2 tuple to validate from imported values without first parsing corrupt existing light. Partial imports still parse and validate their effective base. `MainActivity.kt:252-256` merges and guards before editor writes. Production `completeVersionTwoTupleRepairsCorruptDestinationButPartialImportDoesNot` passed and preserved unrelated gain. Other six-slot, /1, /2, partial-merge and checksum tests in `LightArchiveTest` passed. | Original complete-repair rejection is corrected for the tested corrupt destination. Invalid partial import remains rejected without map mutation. SAF interaction, Android persistence, and installed corrupt-state recovery remain unobserved. |

The one source-adapter test `activityWiringKeepsDecodeOffOwnerAndAcceptedWritesOnOwner` also passed against the frozen Activity file. It is a textual assertion, not a callback test. UI, JNI and render source tracing was performed independently of that assertion.

## Executed checks versus inherited evidence

### Executed by this reviewer

1. Extracted the exact mobile archive and read exact baseline/shared objects. Verified every readset row's Git blob and SHA-256 using shell tools.
2. Ran the frozen production `light_cycle.rs` tests with Rust 1.96.0: **13 passed**. This includes redundant TRACK/TIMER publication, exact duplicate-RGB deletion, invalid-deletion preservation, bag behavior, and retained cycle policy controls.
3. Ran one private native diagnostic harness with **two finite inactive-storage cases**. Both reproduced an unwanted TRACK step. Positive controls preserved identical publication, inactive timing changes, and ordinary saved editing under generated ownership.
4. Compiled only frozen `LightSettings`, `SettingsArchive`, `SettingsWriteOwner`, and their two selected production test classes with cached Kotlin 2.4.10. JUnit on OpenJDK 21.0.11 reported **12 passed: 11 pure behavioral tests and one source-adapter assertion**. No cached application class or Android callback was substituted.
5. Verified the final evidence manifest successfully with `sha256sum -c`. Runner files and receipt files are sealed read-only. This report is separately sealed mode 444.

No additional reproductions, repeated broad gate, GPU test, Android compilation, or device execution occurred. Kotlin's compiler-version command printed missing-Kotlin-home warnings. The actual compilation used explicit cached dependencies and succeeded on its first attempt. The native diagnostic emitted only unused-method warnings.

### Inherited, not executed or promoted to acceptance

The coordinator supplied native 13, pure JVM 57, and nine source-adapter passes for the correction. `section-06-correction-01.md` records those checks, JNI syntax parsing, and diff checking. Those counts are coordinator evidence, not this worker's independent total.

`section-06-color-validation.md` and the unchanged round-1 report preserve the older clean-7ac1 Android/native/GPU/boundary receipts. They do **not** establish a pass for corrected `1e68b6`. **The full corrected Android gate and dual APK build remain pending until the root protocol writer releases its disjoint files.** Device acceptance remains separate. No root-protocol implementation was reviewed and no R13 result was inherited as newly passed.

## Evidence hashes

All paths below are relative to the private artifact directory.

| Artifact | SHA-256 |
|---|---|
| `readset.tsv` | `a7ca6b0f10a723d39b4ba497aebced1a251cb8f16f0aec82b66d60deab4688d3` |
| `evidence.sha256` | `a733f14d7579f2c8463181b3c0f567a90275c8cdfba64646d9c2a4b308aa772b` |
| `run-native.sh` | `90c8e1744ae33b88768ddd7369857b51a93ad2ed0b1c6736bc5dba1a288b7a62` |
| `run-kotlin.sh` | `a9f7b481e2ccf6e401a14b3b0d1122c4a8795410e9e58bc76433d74940c89104` |
| `inactive-review.rs` | `8db6efb8916444600d2d0764aa6b093490cb12d205f4fac8a9923390b5716c00` |
| `seal-readset.sh` | `ecc6c704c5cf820a9cf8fbafd54f919f95139dd8ca314aa5e59875cc57958953` |

`evidence.sha256` binds the readset, exact correction diff, provenance, runner manifest, host tool hashes, cached Kotlin dependency hashes, logs, and produced host executables/JAR. This report's own hash is published in `report.sha256` and the completion report. No self-referential hash is asserted.

## Uncertainty, disposition, and source release

Confidence is high in the two executed native defects and the bounded source/host closure of F1, F3, F4, and F5. Confidence is lower at Android integration boundaries because this worker was correctly prohibited from exercising them. No phone responsiveness, actual persistence fault, lifecycle callback, accessibility, JNI ABI, metadata delivery, HOLD pixel, or source/audio continuity claim is made.

The bounded review is complete, not blocked. The remaining material correction is F2's inactive-storage behavior. The next smallest permitted action is a narrow correction and finite regression checks, recorded against a new exact checkpoint in a **separate addendum**. Preserve this original report and both round-1 documents unchanged. Root retains final gate, device, and commit ownership.

**All source reading, testing, and review holds are explicitly released. No implementation ownership, worker, background job, or source mutation is retained.**
