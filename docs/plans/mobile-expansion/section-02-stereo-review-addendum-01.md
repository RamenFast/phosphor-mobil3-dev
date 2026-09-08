# Correction addendum to the released stereo review

Date: 2026-09-08 UTC, approximately05:14-05:16. Scope: only the coordinator's requested corrections after the initial immutable snapshot.

Baseline report remains unchanged:
`/home/ben/.jcode/scratch/stereo-review-20260908-d2TDwd/REPORT.md`

Baseline SHA-256:
`afb7399b6d863a6334810574607af263af37bc588e379f36c60520cabadf6eb0`

## Decision

**The inspected correction delta resolves F1-F3 at source level. No remaining material source blocker was found in this narrow reassessment.** The corrected source is suitable to proceed through the coordinator's normal Android validation and then one bounded own-UID S25 debug trial. This does not establish that an existing APK includes the corrections. It does not establish device success, physical cleanup, independent SoundCloud stereo, low latency, or R15 acceptance.

This is a separate assessment, not a retroactive approval of the original released increment. The baseline verdict remains not ready.

## Changes inspected

Compared private copies of exactly five corrected files against the baseline copies, including the previously inspected supporting native runtime. No unrelated implementation was reviewed or changed. The contract now states source head completion, sticky app protocol rejection, validate-before-retain FINAL, and sticky native protocol rejection.

### F1: resolved by the inspected source paths

Corrected `RootStereoProbe.kt` introduces `validatedFinal(payload,generation)` and publishes its return value only after validation succeeds. A wrong or missing generation can no longer leave a newly assigned FINAL behind.

The original decoder loop is now `consumeUnchecked()`. Every call site continues through `consume()`, whose catch sets `protocolClean=false` and rethrows. This flag is never restored to true. A complete semantic rejection in the main loop therefore remains disqualifying even if later drain and EOF succeed.

The main-loop `decoder.eof()` call remains outside the new consume wrapper. This is not a new escape from the latch: the unchanged decoder preserves its partial byte count when `eof()` throws. The cleanup phase calls EOF again and records `protocolClean=false`. If a tail completes only during cleanup, the process-exit ordering and final drain remain relevant runtime boundaries, not host-established process behavior.

New Kotlin test source checks wrong-generation rejection before return. It does not execute the full fake-process exception-to-lease trace or explicitly test missing generation. Those remain useful regression additions, but the direct code path closes the baseline defect. No corrected Android tests were run by this reviewer.

### F2: resolved by the inspected state machine and runtime call sites

Corrected `Session` adds a default-true `protocol_clean` flag. `reject(now,cause)` clears it before calling stop. No transition restores the flag.

`cleanup_confirmed()` now requires reaped state, an accepted RESULT, no supervisor-forced kill, and intact protocol. Native success uses that predicate plus exit0 and no failure.

Corrected `platform.rs` uses `reject` for app semantic/decode errors, helper semantic/decode errors, missing helper RESULT, terminal helper decode/semantic errors, and incomplete terminal drain evidence. The post-waitpid path uses the same sticky operation. Its FINAL cleanup field now uses `s.cleanup_confirmed()` rather than reap alone.

This closes the baseline accepted-RESULT, rejected-terminal-frame, natural-exit trace. It also preserves truthful cleanup for ordinary valid helper errors or cleanly completed EOF/revocation retirement. It does not impose a blanket success-status requirement on cleanup. The app still requires the helper JSON cleanup flag when a helper was started.

Observed private host validation:

```text
CARGO_TARGET_DIR=<private scratch>/correction-host/target
cargo test --manifest-path <private scratch>/correction-host/Cargo.toml --locked --offline --lib
30 passed, 0 failed
```

This includes the three new tests for rejected terminal protocol, partial terminal/control rejection, and ordinary helper error/EOF distinctions. Tests ran from private copies. No coordinator build directory was used. These are host library tests, not execution of Android-specific `platform.rs` process supervision. Both native runtime rejection branches were checked by source diff.

### F3: resolved by the inspected stop and success conditions

Corrected `sourceShouldStop(head,elapsedMs)` returns true at head240000 or elapsed5500ms. The loop samples head before applying that predicate. Success now requires head at least240000, while the static buffer and exact240000-frame write requirement remain unchanged.

The new Kotlin fixture source checks delayed startup at5000ms, incomplete head just before deadline, completed head, and absolute deadline exhaustion. No extra audio frames are generated. A source that cannot finish within5500ms still fails honestly. Source/capture clock offset and acoustic latency remain unproved.

## Exact correction receipt

Private corrected files live under:
`/home/ben/.jcode/scratch/stereo-review-20260908-d2TDwd/correction/`

```text
b475ac79aec41c9dc1654dc650dc5e68730467fe8f988e849b5960f032f08e16  docs/plans/mobile-expansion/section-02-stereo-probe.md
fabe5c1e23b7c4cee2bb8444ee949fb9466fbb403b41e4f68de03b8166f04f8a  app/src/debug/kotlin/dev/phosphor/mobil3/RootStereoProbe.kt
b2f8d9e51695f76f5db767fb40a2c609623bc2bc26c771716d06b93fc59ecdca  app/src/testDebug/kotlin/dev/phosphor/mobil3/RootStereoProbeTest.kt
d19b595534ebbf0936166d644e6f75f4ff71814ae02ca0a5112fa912da18fe44  root-helper/native/src/lib.rs
5411d69b1065c50068bcefebb1e093db3eebfbf3fb7e9cc875101484dd546ccb  root-helper/native/src/platform.rs
```

The baseline15-file receipt plus these replacements and the added supporting-platform hash defines this reviewed delta. No claim is made that every other live dirty file stayed unchanged while the coordinator worked.

## Remaining uncertainty and ownership

The baseline runtime limits and uncertainties still apply. In particular, blocking framework cleanup calls are not proven to meet the nominal app cleanup deadline, and hidden monitor identity, physical route stability, fixed-volume restoration, timestamps, audibility, death/EOF/revocation cleanup, and SoundCloud require coordinator-owned runtime evidence.

The corrected source does not claim low latency or stereo product acceptance. A bounded trial may legitimately fail identity preflight or timing checks. Do not treat such a failure as authority to spoof identity, change physical volume, broaden the UID rule, drop frames, or retry unresolved cleanup.

No repository edits, Git, Gradle, Android build, ADB, GUI, audio, network, root, external research, system/vendor/partition writes, or workers occurred in this addendum. Only private copies and a bounded offline host Rust test were added. Both reports are now complete. Further source changes need a new separate assessment rather than modification of either report.
