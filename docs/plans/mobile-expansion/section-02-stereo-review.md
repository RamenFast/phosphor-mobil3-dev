# Released own-UID stereo increment: independent read-only review

## Decision

**Not ready for the requested one bounded S25 debug trial in this exact released form.** Two cleanup-proof defects and one source-completion defect need correction or explicit failure-state validation first. This is a source-readiness decision, not R15 acceptance or a new human approval gate.

The implementation has useful identity, frame, routing, and ownership bounds. The independent private host stereo suite passed 57 checks. No device success, SoundCloud capture, physical gain restoration, acoustic latency, or full product acceptance is established here.

Review date: 2026-09-08 UTC. Review began approximately 05:06 and covered the original released increment only. Coordinator-supplied baseline is HEAD `eae5bfd` plus the 15-file receipt. I did not run Git commands to independently confirm HEAD. All 15 working-source hashes matched the supplied receipt before copying. All 15 private copies matched again. Subsequent coordinator corrections are excluded and require a separate addendum.

## Scope and boundaries

Read governance, the context standards skill, scratch-placement rules, local docs governance, the stereo contract, all eight named implementation areas, and focused tests. Inspected supporting native runtime and lease code to trace cleanup decisions.

No repository edits, Git changes, Gradle tasks, Android builds, ADB, GUI, audio, services, network requests, root commands, external research, system/vendor writes, or partition operations occurred. No workers were started. Only this private scratch directory received files and host Java class output. Existing reports were not changed.

Private snapshot root:
`/home/ben/.jcode/scratch/stereo-review-20260908-d2TDwd`

Source references below are relative to `source/` under that directory, unless marked `support/`. Findings describe source traces, not observed Samsung failures.

## Material findings

### F1. Rejected app protocol evidence can clear the cleanup latch

Priority: high. Blocks the requested cleanup-validity condition.

`app/src/debug/kotlin/dev/phosphor/mobil3/RootStereoProbe.kt`:

- Lines 151-155 assign `final` before checking its generation.
- Lines 178-193 call `consume()` in the main try and handle exceptions without setting `protocolClean=false`.
- Lines 203 and 205 set that flag only for later cleanup-phase exceptions.
- Lines 212-214 use retained `final`, `protocolClean`, and child exit to release `RootHelperLease` as clean.
- `RootAudioProtocol.kt:36-38` resets decoder byte count before semantic frame validation occurs. A rejected complete JSON frame does not leave a partial-frame error for `eof()` to detect.

Concrete state trace:

1. Start with an acquired lease, a launched supervisor, and `protocolClean=true`.
2. Consume a complete kind14 JSON frame with a wrong generation, `cleanup_confirmed=true`, `killed=false`, and `child_started=false`.
3. Line153 stores the JSON. Line154 rejects its generation.
4. Catch191 records an error and attempts STOP. The protocol flag remains true.
5. The process exits. No further bytes remain. Cleanup `consume()` and `decoder.eof()` succeed.
6. Line212 evaluates true because the retained FINAL claims no helper was started. Line214 releases the lease clean.

A missing-generation variant is not merely an invented JSON shape: supporting `root-helper/native/src/platform.rs:395-399` emits exactly such an untagged FINAL when native setup fails. That actual path may have created no helper, but its rejected identity must not become accepted cleanup proof. A stale-generation frame demonstrates the broader invalid-evidence problem.

The report status still becomes error because `error != null`. The defect is false `cleanup_confirmed` and replacement availability, not false overall status ok. Supporting `RootCapturePolicy.kt:20-28` confirms that `release(true)` clears active ownership without setting uncertainty.

Smallest follow-up: make protocol rejection sticky across every consume call, validate FINAL before publishing it, and test both wrong and missing generation with an empty terminal pipe. Also test a semantic error before otherwise valid RESULT/FINAL. None may clear the uncertain-cleanup latch. No device fault injection is needed to establish this parser property.

### F2. Native-filtered protocol failures bypass the app protocol latch

Priority: high for the explicit requested rule that protocol failures cannot falsely authorize clean replacement. This is separate from F1.

Supporting `root-helper/native/src/platform.rs`:

- Lines 527-535 and 581-595 reject malformed or unexpected helper frames and retain a session failure. Rejected frames are not forwarded to the app.
- Lines 627-638 publish `cleanup_confirmed` from `phase == Reaped`, independently of protocol validity. FINAL carries an error status and cause.

Released `root-helper/native/src/lib.rs`:

- Lines 276-280 accept the first RESULT, set `result=true`, and reject a second RESULT or an unexpected frame.
- Lines 193-199 preserve a failure while stopping.
- Lines 307-316 allow the reaped state but prevent native success when a failure exists.

App `RootStereoProbe.kt:212-214` does not inspect FINAL's protocol-error cause when deciding cleanup. It checks the cleanup booleans and `killed`, but not whether native rejected the terminal evidence stream.

Concrete state trace:

1. Helper sends a valid identity-tagged RESULT whose JSON says `cleanup_confirmed=true`.
2. Helper sends a second RESULT or another invalid frame before natural exit.
3. Native accepts and forwards the first RESULT, rejects the second, and records `helper_state`.
4. The helper exits naturally. Native reaps it and emits FINAL with cleanup true, killed false, child_started true, status error, and the protocol failure cause.
5. The app receives only well-formed accepted frames. Its consume wrapper need not throw, even if F1 is fixed.
6. With fixture cleanup successful, the original app cleanup expression releases the lease clean using the first RESULT and the native reap flag.

This does not prove a real policy leak. The first RESULT might truthfully describe cleanup. It proves that protocol-integrity failure does not latch replacement closed under the requested conservative contract. Reaping alone proves process retirement, not validity of the policy-cleanup evidence stream.

Smallest follow-up: distinguish clean retirement after an ordinary experiment failure from a native protocol-integrity failure. Carry or derive a typed terminal protocol-validity predicate and include it in the app cleanup decision. A blanket ban on every error status would unnecessarily reject ordinary cleanly retired stereo failures. Add the valid-RESULT, rejected-terminal-frame, natural-exit fixture and require cleanup unavailable. Test both normal native receive and terminal drain paths.

### F3. The five-second wallclock cutoff can truncate a valid fixed source

Priority: medium. Blocks validity of this exact finite-source trial, independently of feedback safety.

App `RootStereoProbe.kt:136-138` sends START, records `toneStart`, and calls `track.play()`. Line183 stops on `now - toneStart >= 5000`, regardless of source playback head. Lines 216-217 require at least 239520 head frames and exactly 240000 written frames.

The written static buffer is exactly 240000 frames, which is five seconds at 48000 frames/s. Its write count does not prove those frames played.

Concrete arithmetic: with 30ms startup delay and the first wall deadline observation at 5002ms, only 4972ms of source progression occurred. At the requested rate this is 238656 frames, below 239520. The app stops the source and reports failure despite valid nominal-rate playback. The relevant cutoff is startup lag minus polling overshoot greater than 10ms, not an unconditional claim that every delay above 10ms fails.

Conversely, the original acceptance tolerance permits a source missing its final 480 frames. That contradicts using exact finite playback completion as the source proof. The endpoint ramp can make this loss less audible, but cannot make it complete.

Smallest follow-up: retain the fixed 240000-frame buffer and stop on completed playback head, with an independent absolute wall deadline such as 5500ms. Require head completion for success. Test startup delay, exact completion, and deadline exhaustion separately. Do not add audio frames or infer acoustic latency from this change.

## Checks and supporting evidence

### Identity and feedback exclusion

`StereoProbe.java:16-37` requires the generated monitor session, actual helper PID, nonnegative actual UID different from the original app UID, active AudioTrack player type/state, MEDIA usage, and a stable player interface ID. Ambiguous session matches fail. Route evidence includes output ID, type, and address and excludes unknown and remote-submix routes.

Lines 58-84 check routing permission and actual monitor format, play bounded silence, find actual framework identity, then pause/flush and require a zero head. Lines 88-98 register the original UID with MEDIA/GAME usages, LOOP_BACK only, and privileged capture disabled. The monitor's per-track capture policy is BY_NONE. There is no attribution spoofing in this path.

Stable identity and route are checked immediately before registration, before READY, periodically during acquisition, and at termination. Runtime visibility of Samsung hidden APIs remains unproven here. A failure before registration is a valid blocked outcome. Periodic checks do not prove there can never be an intervening route transition.

The source side has a generated session and actual write/head/timestamp observations. It does not independently resolve the source player interface ID through privileged playback configurations. For this fixed app-owned AudioTrack, original credential UID plus track ownership is the implemented source boundary. This is not evidence about SoundCloud's player identity.

### Lifetime and cleanup

`StereoProbe.java:156-167` attempts monitor stop/flush and release before record stop/release and synchronous policy unregister. Monitor release is attempted even if stop fails. Cleanup failures set the helper clean flag false. The policy thread receives a 500ms join limit. Registration is marked attempted before the Binder registration call so failure cleanup includes unregister.

Binder calls themselves can block. The source does not claim they are interruptible. Native `lib.rs:284-305` bounds setup, finite capture, heartbeat, helper progress, and one-second stop grace. Supporting `platform.rs:611-639` kills the direct helper, bounds reap waiting, and avoids treating unreaped exit as cleanup proof. App `RootStereoProbe.kt:195-214` closes control, drains during a 7500ms deadline, and requires explicit terminal evidence. The stated eight-second app cleanup is not a proven upper bound on blocking framework calls such as source stop/release. Device cleanup and restoration remain coordinator-owned evidence.

`RootStereoProbe.kt:25-36,169-171,119-120,187` performs deadline-limited main-thread source-owner observations and rechecks ownership before accepting READY and at heartbeat. `SelfTestReceiver.kt:24-32,52-54` shares single-flight dispatch. The debug manifest protects the receiver with DUMP permission. Actions accept no configurable source inputs.

### Frames, partial protocol, and bounds

`RootAudioProtocol.kt:23-55` and `Protocol.java:18-64` bound frames at 4096 bytes, reject partial EOF, and validate generation/build/original UID/mode for tagged helper evidence. `lib.rs:20-44` rejects debug modes in release and keeps modes4/5 out of stream mode. `lib.rs:234-274` rejects probe PCM and validates progress sequence. Kotlin `Stream.pcm` rejects aggregate modes too.

`StereoPending.java:11-23` retains exact partial-write offsets, forbids reading over a pending block, rejects odd/negative/oversized samples, and bounds monitor queue at 4800 frames. `StereoProbe.java:114-140` has one fixed 960-sample buffer, reads at most the remaining 264000-frame budget, writes the same block, and checks 100ms read/write stalls. App control count is capped at100, or2000 bytes excluding the initial24-byte SELECT.

`Protocol.java:66-68` caps mode3 reads at the remaining80000 frames. Focused host tests exercise partial prior totals79841,79900,79999,80000. The current correction does not alter mono PCM layout.

### Signal, rate, and latency claims

`StereoStats.java:5-32` uses captured frame48000 as the beginning of a144000-frame window, streaming Hann tone sums, both channel RMS values, clipping, correlation, and intended997L/1499R separation. Fixed fixture endpoint ramps and the exact source frame bounds appear in `RootStereoProbe.kt:39-42,124-134`.

`StereoProbe.java:123-152` records paired record/monitor timestamps, requires first timestamp times after capture epoch, checks record backlog against actual record buffer, and requires each endpoint's measured frame rate within1percent. `StereoPending.rate` requires at least one second of timestamp progression. Timestamp availability, origin behavior, and discontinuity behavior are not hardware-tested here.

The capture epoch is not source-frame zero. The app sends START before playing, while the helper starts recording after receiving START. Their startup scheduling and device buffering can create a source/capture offset. The receipt exposes source head/timestamp, read/written totals, monitor head/timestamps, and tone phase, but does not establish an absolute source-to-output or acoustic delay. The implementation does not assert such a latency result. Its flags retain `physical_audibility_proven=false`, `hardware_acceptance=false`, and software-queue-only language.

Unit monitor gain and successful writes do not establish physical gain preservation or audibility. Idle external playback, physical volume, legacy submix state, fixed-volume restoration, and residue checks remain coordinator preflight/postflight responsibilities. No current stereo signal success closes low-latency or independent SoundCloud capture requirements.

## Validation performed and coverage limits

Observed in this review:

- Verified all15 released working files against supplied SHA256SUMS: all matched.
- Copied those15 files to private scratch and reverified: all matched.
- Compiled snapshotted Protocol, StereoPending, StereoStats, and StereoProbeTest with `javac -Xlint:all -Werror`: passed.
- Ran the compiled private `dev.phosphor.mobil3.root.StereoProbeTest`: **57 checks passed**.
- Read the released seven Kotlin stereo test methods,207-check Java protocol/tone source, native state-machine tests, and implementation failure paths.
- F1-F3 are line-backed traces and arithmetic. No Android lifecycle fixture was executed here. Existing focused Kotlin tests do not exercise the original run-loop exception-to-lease path.

Coordinator reported485 Android tests, lint, both APKs, release launcher,27 Rust host tests, and264 Java checks passing. Those results are attributed to the coordinator, not rerun or independently verified in this review. The independently rerun57 stereo checks are a subset of the reported264 Java checks, not additional acceptance coverage.

No speculative fuzzing occurred. No system behavior is inferred from successful host compilation. Exact Samsung identity attribution, policy mixing semantics, gain, output restoration, Binder lifetime, timestamp behavior, app/helper death, EOF/revocation cleanup, and SoundCloud remain runtime uncertainty.

## Source SHA receipt

Supplied receipt copy:
`/home/ben/.jcode/scratch/stereo-review-20260908-d2TDwd/SHA256SUMS`

Receipt SHA-256:
`3c1770f30c8148ed6b614524f82aa958e422eec6b8edc17faad2cb8a9b3f5809`

```text
1a404a92b1327b36bb5e31fc57088d1ff4ddbb28bed5ef34411419acb8bda9ad  docs/plans/mobile-expansion/section-02-stereo-probe.md
36469888e60bdb9bcfcdb269567cc0892be9372496258ebdd0b1c02b1df195cf  root-helper/java/dev/phosphor/mobil3/root/AudioPolicyMain.java
66880244e68ffc6f1c9eab95e15ac1cb4976eb83ae5db83464e65039e8a9ab39  root-helper/java/dev/phosphor/mobil3/root/Protocol.java
1aad73407b6633df5e84d88013e9036385a13c02d7b8574361d6c7492db0ac6f  root-helper/java/dev/phosphor/mobil3/root/StereoStats.java
4e2d9198f901e2b1518af1fd49c7f09458e9da7046b90a558991c27205a092ea  root-helper/java/dev/phosphor/mobil3/root/StereoPending.java
702c03bf51d62ffba1f7db86c4432cdf40c854d97c0a1208e56d7bedc4b05d39  root-helper/java/dev/phosphor/mobil3/root/StereoProbe.java
731c067c7d31bdfea9a42bd5dc130f60b5ab6dac5aad337be80f8fb199d10638  root-helper/tests/ProtocolToneTest.java
11b4b13da3acf946f77a26fe758f4f380619aeccdef158abd1a50b599a98aff4  root-helper/tests/StereoProbeTest.java
8076f51ab3e98f2393ad3308dee304397cc4e614726bb50ee46953148ffbd69f  root-helper/native/src/lib.rs
0cc02cdf176e5e28a31158f419a5033104b98eb6a695a277657e3e188065ca64  app/src/main/kotlin/dev/phosphor/mobil3/RootAudioProtocol.kt
2277686c9b13e0510d7afeb7a8836921c4e3471cec058c8a1f648d7efaac5c3e  app/src/debug/kotlin/dev/phosphor/mobil3/RootStereoProbe.kt
946979e5b7a2d18d9ff03713e0735cc18292a9d6e941071e2302317c9f3be408  app/src/debug/kotlin/dev/phosphor/mobil3/SelfTestReceiver.kt
2f8ed062c6c875e3e7eb851aa52f43000f668a1149438c54d926008f8e9388cc  app/src/debug/AndroidManifest.xml
82385eda3ee7a506a85750a9f695be8b0c7e8a1f32065d3bab1140ba11a9d5a5  app/src/testDebug/kotlin/dev/phosphor/mobil3/RootStereoProbeTest.kt
524a19be11965662a7850b46a3d285afc10c808b3693bcc74626d3331719fb9c  app/build.gradle.kts
```

Supporting files inspected and privately copied, outside the15-file increment:

```text
a9aac4c732e358f75fc19cad29ba4e4f93ba2af763434810953dc9dc8d9fef5b  support/root-helper/native/src/platform.rs
f00ffb96f6313ca47a9ac98699cb070961173361d74672ddb59226940c4edf98  support/app/src/main/kotlin/dev/phosphor/mobil3/RootCapturePolicy.kt
```

## Best current result and next step

The released exact increment is bounded and source-testable, but cleanup proof is not fail-closed for all requested protocol failures. Its original wallclock source stop also makes the finite trial unnecessarily inconclusive.

Coordinator owns corrections, Android/build/device slots, and any bounded trial. The smallest follow-up is targeted source correction and regression evidence for F1-F3, followed by a separate immutable correction addendum. This report does not assess or approve changes made after the initial snapshot.
