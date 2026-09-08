# ADDENDUM: section 6 correction 02

## Disposition

**Correction 02 accepted at the bounded source and pure-host level. Section 6: 8/10 for exact mobile `73e13eaf6a65df94557f8eb8107e5a44252a13bd`, paired with shared `0ffd658d7f19e68180c2720e0500b23644619e90`.** Both remaining F2 inactive-storage paths are fixed. No regression was found in the required native state seams below. This is a narrow correction disposition, not Android callback, pixel, installation or release acceptance.

The original round-2 verdict remains **7/10 for `1e68b6327252b22424839b5f6c8464cd5731f3da`**. Its preserved SHA-256 is `3dbc5fb961fb3b904b12fecd9934dcb3a75ee48c6baa0e0f2069289b90dd5b70`. Round 1 and its separate process addendum are unchanged. No tracked file was edited. The new 8 reflects closure of the last material F2 seam against round 2's bounded prior coverage, not a retrospective change to either original report. F1, F4 and F5 remain carried source/host closures, not newly rerun Android tests. R13 is not rescored.

## Exact scope

- Independent assessment, not implementation. Coordinator supplied verified enforced `openai-oauth:gpt-6-astra`, explicit high. No routing ritual or worker spawn occurred.
- Mobile tree: `2bb54a5395a19da65817c4fbeecafd25a57b4c8c`. Shared tree: `51f828e60c5bbfd7003694bf28b7455daf07db1e`.
- Comparison: exact `1e68b6327252b22424839b5f6c8464cd5731f3da`. Main inspected implementation delta: `rust/src/light_cycle.rs`.
- Private directory: `/home/ben/.jcode/scratch/section06-correction02-assessment.tN7rCn/`.
- Correction 02, correction 01 and original round 2 were read before tests. Native tests ended at 09:45:17 UTC. Product reading ended at 09:47:36 UTC, 2026-09-08.

All product bodies came from immutable Git objects or exact private snapshots. The 21-row `readset.tsv` distinguishes seven fully read bodies, native old-side hunks, six forwarding diff sides, and seven identity-only rows. Identity-only files are not claimed as reviewed. Every snapshot was checked against its Git blob. Shared commit/tree identity was verified, but no shared product body was inspected.

LightSheet, LightSettings, SettingsArchive, SettingsWriteOwner, PhosphorNative and render have identical blobs across the two mobile commits. MainActivity, PhosphorScreen and JNI diffs change only unrelated display-pending forwarding, not LIGHT publication or exact deletion forwarding. Those hunks were inspected without repeating the broad review. Other section-5/root deltas remain outside this assessment.

## Bounded requirement evidence

| Requirement | Evidence and outcome |
|---|---|
| Unselected saved RGB edit must not advance TRACK | `same_owner:76-82` compares selected identities and their RGB, not the full stored bank. New independent case A repeats round 2's ordered seed-1 red/green/blue operation. Red holds without `track()`, and three subsequent real events match the unedited control. Passed. |
| Generated storage edit/deletion must preserve ownership and RNG | `apply_edit:62-66` allows the generated owner to retain its state after validated deletion. Independent case B runs one fixed edit/delete trace in TRACK and randomized TIMER, seed 18. Immediate observations and six fixed future observations match an unedited control. Passed. This checks continuation, not only equal RGB at the edit instant. |
| Timer leg and random state preservation | Production `inactive_saved_rgb_preserves_track_and_timer_state` and `generated_owner_deletion_preserves_color_leg_and_rng` assert live/from/to/start/duration, RNG state, draw count and suspension equality. Both passed in TRACK and TIMER. Native `begin_leg:123-127` consumes updated timing only at the next boundary. Existing redundant-publication/timing tests passed. No Android timing measurement is claimed. |
| Unselected deletion preserves bag order and identity | `apply_edit:45-60` validates exact old-bank removal and shifted mask, then remaps surviving `last_id` and queued indices once without a draw. Production `unselected_deletion_remaps_bag_without_consuming_an_event` passed for both modes and distinct/duplicate RGB. It checks the exact ordered bag, mapped identity, unchanged leg/RNG, and next TRACK identity. |
| Selected deletion, duplicate RGB and genuine membership | `removed_selected` prevents the inactive fast path for saved ownership. The existing activation still clears/refills after remapping the old identity. Production exact-deletion duplicate-RGB, membership-reset and duplicate-identity refill tests all passed. |
| Genuine selected RGB, active shuffle and owner changes still activate | `same_owner` retains those distinctions. Independent case C follows a fixed ordered trace through selected RGB change, membership change, shuffle enablement, then generated and saved ownership. Expected saved colors and no-repeat activation passed. |
| Temporary roll retirement and invalid-deletion no-mutation | The `!temporary` guard prevents the inactive fast path from swallowing explicit retirement. All rejecting returns occur before `last_id`/bag/settings mutation. Existing invalid-deletion and roll tests passed. Case C also rejects a malformed deletion during a held temporary roll, verifies unchanged settings/beam, then explicitly applies and verifies roll retirement and the next saved event. |

The first saved grid remains an intentional separate rule. Editing or deleting its storage may change saved-grid RGB while preserving the active beam. Generated ownership still derives its grid from generated RGB. No broader all-pixels-unchanged claim is made.

## Executed checks and inherited gate

Rust **1.96.0**, host `x86_64-unknown-linux-gnu`, compiled the exact unmodified native module: **16/16 production tests passed**, including all 13 prior tests. A separate private runner imports that module by path and executes exactly **three additional finite cases**, all passed. Its 16 embedded production tests were filtered out, not rerun. No copied policy, fuzzing or seed search was used. Both compilations and all tests succeeded on their first execution. Final input checksums matched.

Inherited coordinator gate **315155k95w** reports **557 JVM / 111 native / 3 offscreen GPU**, lint, dual APK builds, engine/source boundary and complete unchanged mobile/shared source hashes. The exact target's `section-05-06-correction-validation.md` records that gate as working source based on `79af079d` plus the frozen delta, not a clean-commit artifact freeze. This assessment does not independently promote it to a newly executed exact-commit build. Neither APK was installed. Source-adapter checks and pure host tests are distinct from Android callbacks and pixels.

No Gradle, Android/JNI target execution, GPU, ADB/device/GUI/audio, network, services, Python, Git mutation, product edits or workers occurred. A computed-path scratch-write command was refused before execution, then permitted with an explicit new-private-directory justification. No prohibited fallback followed.

## Hashes and limits

| Artifact | SHA-256 |
|---|---|
| Exact corrected `light_cycle.rs` | `bf00b260e61d4f018c9dd346c5446af687d3c7481372da12c427b511e8ffbbe9` |
| Exact comparison `light_cycle.rs` | `b293dbe1ec8bdce4dde0b97c0f060ceac48ecf26d829633a76562005d33e69d4` |
| `readset.tsv` | `69c07cd1cb6751f43c8a062432ecade0a8506161c0aa6be73bcae6901aed38f0` |
| `run-native.sh` | `1f4c46ec371238c1e720e4f918ba99d6c302ff04c92883cdaa3c69168a83646a` |
| `behavior-review.rs` | `42b6cfab380255055dcc9f48c24e75cb2d37d37f7719e76a27651e4908231cd6` |
| `seal-readset.sh` | `f1f8fe886c3badfcb378af2b73b7be7fb397c32ba0f7866d0fdaa73a0e192a01` |
| Actual Rust 1.96 compiler | `ba4b837efb6612dfa8d941c5a72b8a50d1d03a0f36216743b173949aa8d9eb75` |
| Preserved round-1 report | `4c5ad2cbc19764fd27bdf69ffb57e1c7d2ac43b148a3169ea7f85c290455545c` |

`evidence.sha256` binds snapshots, readset, runners, logs, binaries and this addendum. `seal.sha256` separately publishes the addendum/readset/manifest hashes without a self-reference. The addendum and readset are sealed mode 444.

No new material blocker remains in this narrow correction. Device responsiveness, persistence-fault behavior, accessibility, actual callbacks and installed pixels remain unobserved. Full source-time and physical scanout remain unrelated open gaps. The source/host evidence supports an 8, not a claim that those acceptance gaps disappeared.

**All source/read/testing ownership and review holds are released. No background process, implementation ownership or follow-up testing is retained. Work stops after delivery. Root owns integration, builds, device actions and session shutdown.**
