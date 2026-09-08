# ADDENDUM-02: owned AudioTrack startup-buffer correction

Review date: 2026-09-08 UTC. Independent read-only review began at approximately05:48. This is a separate immutable addendum. It does not revise either earlier report.

## Decision

**Blocked for source readiness under the complete requested checks.** The narrow buffer configuration and queue-accounting correction is coherent at source level. The existing private host suite passes75 assertions. Two material blockers remain: terminal JSON has no encoded-byte fit guarantee, and the exact runner has unbounded transport waits with incomplete failure-path postflight.

These findings do not establish a phone failure in the corrected source. No corrected Android compilation or device trial occurred here. No R15 or full-product score is assigned.

**Best current result:** owned-monitor configuration, API gating, queue guards, rejected highwater retention, policy ordering, and prior terminal-cleanup fixes pass this source review.

**Next step:** coordinator makes the smallest receipt-budget and runner-deadline corrections, retains new exact hashes, completes Android validation, and creates a new reviewed source/artifact freeze before considering another bounded trial.

## Scope and immutable inputs

Private review root:
`/home/ben/.jcode/scratch/r01-stereo-addendum02-20260908T0549Z`

The four requested narrow files and the runner matched all supplied hashes before source reading. Private copies were created, made read-only, and checked again. All five still matched at the final snapshot check. References below use their original repository-relative paths, or the corresponding flattened `support/` copies.

```text
a1788af9b579d92851ccf7b17ac3a01fea42896669528c3abab1f7bc8a41b879  root-helper/java/dev/phosphor/mobil3/root/StereoPending.java
feec0cd81598ec4b681937a423b0c7d4f27fa23698cfbeb5f463701bcf126225  root-helper/java/dev/phosphor/mobil3/root/StereoProbe.java
394dcf5afd51d65ef035560afd2eb89fa9892edf1b72aa76ddc7d5a00deed5d0  root-helper/tests/StereoProbeTest.java
1ce936a2442ff866e76022633ff3a15c693d42601d8f290ae65e40e431687e99  docs/plans/mobile-expansion/section-02-stereo-probe.md
a54117c69440d5ecb8701dc3348e470c423cc0680ad51779f34d291f765e0e73  dev/scratch/mobile-expansion-20260908T001819Z/run-root-stereo-check-wireless.sh
```

The exact runner hash was inherited from the coordinator and independently matched against script bytes for static inspection. The script was never invoked or sourced. Packaged debug manifest SHA-256 `2f8ed062c6c875e3e7eb851aa52f43000f668a1149438c54d926008f8e9388cc` is inherited coordinator evidence only. I did not decode the APK or independently verify its packaged manifest.

Support came exclusively from Git objects at independently resolved commit `06f84e2eb7da46c758e9f8b388c3537e6c67905d`:

```text
36469888e60bdb9bcfcdb269567cc0892be9372496258ebdd0b1c02b1df195cf  root-helper/java/dev/phosphor/mobil3/root/AudioPolicyMain.java
66880244e68ffc6f1c9eab95e15ac1cb4976eb83ae5db83464e65039e8a9ab39  root-helper/java/dev/phosphor/mobil3/root/Protocol.java
1aad73407b6633df5e84d88013e9036385a13c02d7b8574361d6c7492db0ac6f  root-helper/java/dev/phosphor/mobil3/root/StereoStats.java
fabe5c1e23b7c4cee2bb8444ee949fb9466fbb403b41e4f68de03b8166f04f8a  app/src/debug/kotlin/dev/phosphor/mobil3/RootStereoProbe.kt
cc2b0e89eadfc7ed514107212bf39c76248553b99e671caab5ba36825b2f6621  app/src/main/kotlin/dev/phosphor/mobil3/RootHelperCode.kt
f00ffb96f6313ca47a9ac98699cb070961173361d74672ddb59226940c4edf98  app/src/main/kotlin/dev/phosphor/mobil3/RootCapturePolicy.kt
d19b595534ebbf0936166d644e6f75f4ff71814ae02ca0a5112fa912da18fe44  root-helper/native/src/lib.rs
5411d69b1065c50068bcefebb1e093db3eebfbf3fb7e9cc875101484dd546ccb  root-helper/native/src/platform.rs
```

`RootHelperLease` is defined at `RootCapturePolicy.kt:20-28`, not a separate file in this baseline. The four narrow Git-object baselines were also read through focused diffs. No changing HUD implementation was inspected.

Prior context and retained coordinator evidence read:

```text
afb7399b6d863a6334810574607af263af37bc588e379f36c60520cabadf6eb0  docs/plans/mobile-expansion/section-02-stereo-review.md
f059a483f689da9961d7099d878545a5e930632a5d419d642a54343b00c7a890  docs/plans/mobile-expansion/section-02-stereo-review-addendum-01.md
6603ba089e057845d9462b96174dae675b3271418cd6a4543c785f2934031516  dev/scratch/mobile-expansion-20260908T001819Z/root-stereo-06f84e2eb7da-freeze.json
b39e90cf1d80d5439b38ad2cf54821c26cf73935f386b961b9979d0ac0d1f80f  dev/scratch/mobile-expansion-20260908T001819Z/root-stereo-06f84e2eb7da-system-1.json
```

Governance read set: `/home/ben/Dev/ClaudeWorkspace/AGENTS.md`, project `docs/AGENTS.md`, `ben-context-standards`, and `folder-management-user-preferences`. The coordinator's pinned AOSP Android16 AudioTrack.java lines2249–2280 and2306–2362 remain inherited research evidence. No network research occurred.

## Requirement-to-check results

| Requirement | Result and concrete check |
|---|---|
| Only owned monitor startup buffering changes | Pass at source level. `StereoProbe.java:41-51,85-92,107-108` applies setters only to the locally owned streaming AudioTrack. The diff adds no physical volume, focus, preferred-device, process policy, or global write. |
| Setter/readback compatibility | Pass against the supplied API contract. Lines43-50 retain both setter results and compare them with observed effective size and threshold. `StereoPending.java:6-8` rejects negative/error results, stale mismatches, nonpositive sizes, size above capacity or960, and threshold outside1..size. Host lines39-51 exercise valid/clamped and invalid cases. Actual Samsung calls remain unexecuted. |
| API29–30 derivation versus API31+ observation | Pass with qualification. Lines33-39,45-46 gate newer threshold calls at31. Below31, threshold and threshold_result are derived from effective size, not independently observed threshold or a threshold-setter result. `threshold_api=false` identifies that distinction. On31+, the getter is observed. The480 request is not a guarantee that clamping cannot return another accepted value at most effective size. |
| Route-known reconfiguration and later bounds | Pass at sampled-check level. Lines104-108 configure again after identity/route discovery and pause/flush/head reset. Lines53-60 check route identity and buffer bounds. Calls at120,128,141,167 cover registration, READY,100ms capture checks, and termination. A transient between checks or blocking Binder call is not ruled out. |
| No policy registration before configuration and identity success | Pass. Initial configuration92 precedes first play/write. Identity104 and second configuration108 precede registration121. Stable identity/route/buffer validation120 immediately precedes registration. Failures enter finally without an attempted registration. |
| Existing100ms and4800-frame guards remain | Pass. Lines159,162 retain100ms write/read stalls. `StereoPending.java:5,25-30` retains exactly4800 queued frames. Test54 accepts4800 and55 rejects4992. Buffer configuration did not enlarge that queue allowance. |
| Rejected queue value retained | Pass. Highwater updates at28 before the over-limit throw29. Test55 explicitly checks4992 after rejection. Invalid negative/head-ahead inputs still fail without recording an invented queue. |
| Ordered pending writes and cleanup order | Pass for this delta. Lines142-164 retain one ordered pending block. Lines180-191 still stop/flush/release monitor before record and policy cleanup. Separate release attempts survive stop exceptions. Thread join remains500ms. |
| Stale/invalid terminal cleanup fixes preserved | Pass at source level. See dedicated trace below. Support blob hashes match prior corrected code. No new clean-latch escape was found. |
| RESULT fits4096 including maximum cleanup error | Fail. F1 below. The permitted JSON budget is4016 after identity, and character caps are not serialized UTF-8 byte caps. No existing host assertion covers the terminal JSON serializer. |
| Runner exact artifact/source and own fixed actions | Pass within stated inherited freeze trust. Lines9-29 restrict fixture/trial/commit and verify archive plus both retained APK hashes. Lines30-32 match installed debug base bytes. Lines14-15,54 dispatch only fixed explicit own-package actions without extras. Helper staging is digest-pinned in `RootHelperCode.kt:21-27,41-69`. The runner does not install or run instrumentation. |
| Runner normal idle audio and no protected/global writes | Pass for visible script actions. Lines47-52 reject existing helper, policy mix, non-normal mode, and started playback. Visible device writes are limited to the own debug broadcast and its app-owned behavior. There are no system/vendor/boot/vbmeta, settings, route, volume, or install writes. `scripts/env.sh` was not sourced or separately audited. |
| Finite runner waits and meaningful postflight | Partial, blocked by F2. Normal postflight72,77-85 compares preferences/music-volume fields and checks helper/policy/service residue plus aggregate receipt. ADB calls can hang indefinitely and early failures after launch can skip postflight. |

## Confirmed material findings

### F1. Terminal receipt character limits do not satisfy the frame byte limit

Priority: high for the explicit receipt/cleanup-evidence requirement. This is a source-level representable-input counterexample, not an observed corrected-run failure.

Trace:

1. `AudioPolicyMain.java:65-71` retains up to700 Java characters in a cause chain. It does not sanitize JSON escapes or bound encoded bytes.
2. `StereoProbe.java:179` retains that cause in the main error. Lines183-191 can also accumulate cleanup causes.
3. Lines193-202 keep the entire terminal evidence object, including both new before/after buffer receipts. Line195 caps cleanup_error at500 characters, not bytes.
4. `AudioPolicyMain.java:40-41` serializes that object. `Protocol.java:53-55` requires JSON UTF-8 bytes plus the80-byte identity to fit4096.
5. If serialization exceeds4016 JSON bytes, `tagged_frame_limit` prevents RESULT transmission. `StereoProbe.java:204` only sets local clean=false. No compact terminal fallback is sent.
6. The preserved supervisor detects missing RESULT at `platform.rs:600-601`. Its cleanup predicate requires accepted RESULT at `lib.rs:322-323`, so replacement remains uncertain. This is lost evidence, not false cleanup success.

Reproducible source arithmetic without a new executed harness:

- A RuntimeException message containing repeated U+0001 controls is representable by the catch path. JSON encodes each as six ASCII bytes, `\u0001`.
- `RuntimeException: ` has18 characters. A700-character retained cause can therefore contain682 controls. Its encoded content alone is18 +682×6 =4110 bytes, already over4016 before the stage prefix, JSON key, or other evidence.
- The explicit maximum-cleanup case also fails. The prefix ` monitor_stop:RuntimeException: ` has32 characters. A500-character cleanup value can retain468 controls, encoded as32 +468×6 =2840 bytes.
- The retained trial's compact helper JSON is2135 bytes, measured by `jq -c '.data.helper' ... | wc -c` returning2136 including the newline. Its empty cleanup string needs no content bytes. Keeping that observed-shaped evidence and adding the maximum cleanup content gives4975 bytes before either new buffer receipt. This illustrates the terminal budget defect without claiming that exception occurred on the phone.

The long-string weakness predates this narrow delta. The new nested receipts consume additional headroom. It must nevertheless be resolved to pass the user's explicit maximum-cleanup receipt condition.

Smallest correction: enforce a serialized UTF-8 byte budget before send. Bound verbose exception text and retain a compact terminal-failure fallback with truthful cleanup/status and required identity. Keep the4096 protocol cap and cleanup latch unchanged. Coordinator should verify full success and failure receipts with the actual serializer, including500-character cleanup,700-character cause, escaping, and both buffer receipts. Do not substitute a plain ASCII character-count assertion for encoded fit.

### F2. The exact runner does not bound all waits or guarantee postflight after launch failure

Priority: medium, blocking the requested finite-runner condition.

`run-root-stereo-check-wireless.sh:54` wraps only the broadcast in `timeout 55`. The200-iteration loop at58 is finite only if every ADB command returns.

Reproducible control-flow traces from the script:

1. Broadcast54 starts the owned fixture.
2. Poll59 opens an ADB exec-out call without timeout. A stuck transport leaves the shell in that command indefinitely. Reaching the next iteration and its finite loop bound is impossible.
3. The broadcast subprocess can reach its own timeout while the parent remains blocked in poll59. The EXIT trap56 is not reached while the parent is stuck.
4. Even if polling finishes, `capture_state after`72 invokes six unbounded ADB calls at36-41. A hung postflight command prevents a finite conclusion.
5. A nonzero during-observation command at61 or63-65 instead triggers `set -e`. The EXIT trap only waits for broadcast. It does not attempt postflight, so a launched fixture can end without independent residue/preservation evidence.

These paths do not authorize global cleanup or killing another owner. The app/native finite ownership logic remains separate and unchanged. The defect is the runner's ability to finish and establish meaningful postflight on transport errors.

Smallest correction: use a deadline-bounded ADB wrapper for every transport operation, with finite kill-after behavior. After dispatch, retain a bounded best-effort postflight path on both success and error. Collect individual failures rather than aborting before remaining checks. Report cleanup unknown when postflight cannot establish it. Keep fixed actions, artifact checks, no protected writes, and no automatic retry after uncertain cleanup.

## Preserved prior cleanup corrections

The narrow Java diff leaves final cleanup statements intact. Unchanged Git-object support retains the previously corrected properties:

- `RootStereoProbe.kt:40-43,159` validates FINAL generation before publishing it.
- Lines173-175 make consume rejection sticky. Lines212-214 preserve rejection during terminal drain and EOF.
- Lines221-223 require fixture/protocol/terminal cleanup before releasing the lease clean. `RootCapturePolicy.kt:27` keeps uncertainty sticky.
- Native `lib.rs:195-197` makes rejection sticky. Lines322-323 require reaped state, accepted RESULT, no forced kill, and intact protocol.
- `platform.rs:588,595,600-601` rejects terminal semantic/decode/missing-result failures. Lines627-638 derive FINAL cleanup from that predicate.
- `RootStereoProbe.kt:39,192,224-226` retains head240000-or5500ms stopping and exact source completion for success.

These are source checks, not renewed whole-subsystem acceptance or an Android execution claim.

## Validation and runtime uncertainties

Only the existing pure host stereo suite was compiled and executed from the private snapshot:

```text
javac --release 17 -Xlint:all -Werror -d <private>/classes \
  <private>/support/Protocol.java <private>/support/StereoStats.java \
  <private>/root-helper/java/dev/phosphor/mobil3/root/StereoPending.java \
  <private>/root-helper/tests/StereoProbeTest.java
java -cp <private>/classes dev.phosphor.mobil3.root.StereoProbeTest
Stereo host checks passed: 75
```

This validates pure bounds, partial-write accounting, queue rejection retention, rate arithmetic, protocol mode separation, and generated-signal statistics. It does not compile StereoProbe against Android or exercise real setters, policy, routing, JSON terminal serialization, or process lifecycle.

The retained first trial confirms requested960 versus actual8793 buffer frames, initialized48000/2/PCM16, monitorUID0 distinct from sourceUID10401, and monitor_queue_overrun after4992 read/written frames. Its old highwater4512 omits the rejected value. Its elapsed81ms is not a100ms wall-stall observation. The4800-frame limit corresponds to100ms at48kHz. The new plan's shorthand should be read as that frame budget, not as a measured100ms wall delay.

The failed-run actual start threshold was not measured. The supplied AOSP default-capacity behavior makes the startup-threshold explanation plausible, not proven. The API29–30 derivation is not an independent threshold readback. Samsung setter/readback behavior and post-route stability remain unmeasured for this correction.

Coordinator evidence reports clean retirement and preserved preferences, volume, and route for the first trial. I inspected its retained aggregate receipt, not the phone or independent before/after dumps. No true stereo, latency, physical audibility, SoundCloud, death/revocation, or full-product acceptance follows from this review.

No source edits, Git mutations, Gradle, Android or Cargo builds, ADB, phone, GUI, app/framework execution, network, Python, workers, protected/global writes, or runner invocation occurred. Only private snapshot/report files and authorized host class output were created. Earlier reports remain unchanged. No speculative executable harness was authored.

## Ownership release

Review complete. **The coordinator may release all four frozen narrow source files and the runner now.** Further corrections are outside this immutable addendum and require their own exact-hash reassessment. The coordinator retains sole ownership of source changes, Git, Android validation, device actions, and any subsequent trial.
