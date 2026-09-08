# ADDENDUM-03: F1/F2 correction reassessment

Date: 2026-09-08 UTC. Review began approximately06:15. Scope is only the two findings in immutable ADDENDUM-02. This report does not repeat or replace the baseline review.

## Decision

**F1 fixed at source level with independent host evidence. F2 fixed at source level with independent fake-transport evidence.** No remaining material blocker was found in these exact corrections. The coordinator may proceed with normal Android validation and a new reviewed source/artifact freeze. This is not approval of an existing APK or a device-success claim.

Baseline retained unchanged:
`/home/ben/.jcode/scratch/r01-stereo-addendum02-20260908T0549Z/ADDENDUM-02.md`

Baseline SHA-256: `e9cdc9aa1b310d8645e95e1e3c196b90c31730f4f7ca1b2ec6ee72efea9ae2a1`

Private correction snapshot and this addendum:
`/home/ben/.jcode/scratch/r01-stereo-addendum03-20260908T0615Z`

## Exact scope and hashes

All six supplied source/script hashes and the pinned JSON jar hash matched before content inspection. Private copies matched again and were made read-only. A later live-source check still matched all six frozen files. Baseline governance remains as read in this same review session.

```text
0479e9779ba0bb5f64bf5f5f53e1b6b69b95a1fcbc84bafdb4263a8cc2d2a763  root-helper/java/dev/phosphor/mobil3/root/StereoTerminal.java
eb943cda9b835fddae14cbd21ec939c4ed194f9548715ffbd35068fe8dce7f72  root-helper/java/dev/phosphor/mobil3/root/StereoProbe.java
212228bc83a48a8a5a9943d259f4e06c390af70e0517fbd54be0de5bfa0d0e13  root-helper/tests/StereoTerminalTest.java
a2f61a1e3b7fccaba2a879f35866cadcff8255a6bd799e476cafb09e3dcfb925  docs/plans/mobile-expansion/section-02-stereo-probe.md
d0102fc1fccddede249f955371efb27e18d0daeebeeac257721b9285797cc880  dev/scratch/mobile-expansion-20260908T001819Z/run-root-stereo-check-v2.sh
c0bbc0eb515673b93a7e1b39937efa1489d6570331ebb976590665d0829b8ca2  dev/scratch/mobile-expansion-20260908T001819Z/test-root-stereo-runner-v2.sh
3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed  json-20240303.jar
b39e90cf1d80d5439b38ad2cf54821c26cf73935f386b961b9979d0ac0d1f80f  dev/scratch/mobile-expansion-20260908T001819Z/root-stereo-06f84e2eb7da-system-1.json
```

Jar source:
`/home/ben/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar`

The unchanged pure-host dependencies came from the prior private snapshot: Protocol.java, StereoStats.java, StereoPending.java, and StereoProbeTest.java. Their exact hashes are retained in ADDENDUM-02. The unchanged native and app cleanup consumers were inspected only where necessary to validate compact RESULT acceptance.

Additional immutable consumer read: `06f84e2eb7da46c758e9f8b388c3537e6c67905d:app/src/main/kotlin/dev/phosphor/mobil3/RootAudioProtocol.kt`, copied privately as `RootAudioProtocol.kt`, SHA-256 `0cc02cdf176e5e28a31158f419a5033104b98eb6a695a277657e3e188065ca64`.

## F1: terminal encoded-byte overflow

**Fixed.** The correction measures the already-serialized JSON as UTF-8 bytes and supplies a fixed failure receipt when it exceeds the permitted budget.

Line-backed trace:

1. `StereoTerminal.java:13-17` requires an80-byte identity, actual root UID0, valid encoded original UID/build/generation, and probe mode4 or5.
2. Lines18-19 measure UTF-8 bytes. A JSON value at most4016 bytes is passed unchanged to `Protocol.tagged`.
3. Lines20-24 replace an oversized value with a fixed ASCII failure envelope. Variable values are validated identity fields, integer lengths, and a boolean, not exception text.
4. The envelope retains protocol2, actualUID0, original UID, build, generation, mode, cleanup truth, original encoded length, and `receipt_truncated=true`. It always reports `status=error` and `terminal_receipt_overflow`.
5. `StereoProbe.java:203-205` sends the prepared payload after cleanup. Compaction sets failure when no earlier failure exists. Line208 therefore cannot return success for a compacted terminal result.
6. If preparation or sending itself fails, line206 still makes cleanup uncertain. The correction does not invent a successful send or bypass cleanup validation.

The compact envelope contains every field required by the unchanged `RootAudioProtocol.kt:51-55` helper identity validator. Its80-byte tag remains intact. Native `lib.rs:282-285` can accept the single RESULT, while lines322-323 still require accepted RESULT, intact protocol, natural reaping, and no forced kill for cleanup proof. The app's unchanged `RootStereoProbe.kt:221-226` still distinguishes truthful cleanup from successful stereo evidence.

A compacted result can truthfully report clean retirement while the experiment remains an error. It omits signal and buffer diagnostics instead of treating incomplete evidence as stereo success. The fixed fallback text does not guarantee that the discarded runtime detail was otherwise persisted.

### F1 host checks

Compiled only pure host code with:

```text
javac --release 17 -Xlint:all -Werror -cp <private>/json-20240303.jar \
  -d <private>/classes \
  <prior-private>/support/Protocol.java \
  <prior-private>/support/StereoStats.java \
  <prior-private>/root-helper/java/dev/phosphor/mobil3/root/StereoPending.java \
  <prior-private>/root-helper/tests/StereoProbeTest.java \
  <private>/StereoTerminal.java <private>/StereoTerminalTest.java
```

Executed the authorized existing tests:

```text
Stereo terminal JSON checks passed: 154
Stereo host checks passed: 75
```

`StereoTerminalTest.java:30-46` uses the retained helper receipt and actual pinned JSONObject serialization. It tests both modes, both cleanup booleans, escaped controls, multibyte text, quotes/backslashes/newlines, maximum cleanup text, and both nested buffer receipts. Lines18-26 validate frame roundtrip, unchanged identity, and required parsed fields. Lines48-56 test exact4016-byte JSON fit, one-byte overflow, multibyte overflow, non-root actual UID, wrong identity length, and wrong probe mode.

These assertions close the serialized-byte counterexample from ADDENDUM-02. Host JSONObject is not Samsung's Android implementation. However, the production budget check runs after serialization and counts the resulting bytes, so it does not depend on identical escaping choices between the two implementations.

The full Android StereoProbe class was not compiled or executed here. Its only diff against the prior snapshot is the terminal preparation/send/failure sequence at203-205. Buffer controls, queue limits, and cleanup statements were not changed by this correction.

## F2: unbounded transport and missing failure-path postflight

**Fixed for the inspected runner paths.** Every visible ADB command has a finite timeout and kill-after. The runner now invokes bounded postflight after dispatch on normal exit and handled failure.

Line-backed trace in `run-root-stereo-check-v2.sh`:

- Lines5-6 centralize device reads behind5-second timeout and1-second kill-after. All polling, observation, identity, artifact, and snapshot transport calls use this wrapper.
- The sole separate ADB call, broadcast107, has55-second timeout and2-second kill-after.
- Lines110-121 bound polling by both200 iterations and an elapsed60-second deadline. A last iteration can extend beyond that deadline by its bounded commands. The runner does not promise an exact60-second total runtime.
- Lines103-109 install EXIT and handled-signal cleanup before marking dispatch owned. `finish_trial`47-64 waits for the bounded broadcast process, then invokes postflight even when original execution failed.
- Lines12-17 attempt each of six snapshot operations despite individual failures. Lines19-24 reject empty or structurally missing evidence, not just nonzero transport exits.
- Postflight36 retains snapshot failure as cleanup unknown. Lines58-62 require original success, broadcast success, fresh receipt, complete postflight, and no observation error before acceptance.
- During-observation failures114-118 now set a sticky failure flag instead of bypassing postflight through an immediate exit.

For the ordinary killable-host-process model, six postflight transports add at most six5+1-second timeout windows. Waiting on the broadcast cannot introduce the earlier unlimited ADB wait. Local filesystem failure, uninterruptible kernel behavior, external SIGKILL, or machine loss are not proven recoverable by this shell fixture.

### Fake-only execution isolation

Before execution, I read the entire exact runner and fixture. The fixture contains no call to `stereo_main`.

`test-root-stereo-runner-v2.sh:7-25` creates a private fake `adb` executable. Lines27-28 prepend its bin directory to PATH and assert `command -v adb` equals that exact private executable. That successful assertion precedes sourcing the runner at31.

The runner's line125 guard requires `BASH_SOURCE[0] == $0` before calling `stereo_main`. This is false when the differently named fixture sources it. The fixture uses serial `fixture-only` at32. Its fake executable either emits fixed text, returns a deliberate error, or runs a finite local sleep. It never delegates to Android's ADB binary.

The fixture hardcodes the coordinator's runner path. Immediately before execution, `cmp` confirmed that live held script equaled the immutable reviewed copy. The successful fixture run passed its PATH assertion before sourcing that exact guarded file.

### F2 independently observed checks

Both exact scripts passed `bash -n` and `shellcheck -x` with exit0 and no diagnostics.

The authorized fixture was invoked with its private scratch root and passed all five checks:

1. Complete synthetic before/after postflight:12 fake calls and cleanup preserved.
2. Failed policy transport: all six after snapshots attempted, cleanup unknown.
3. Empty successful policy output: cleanup remained unknown.
4. Fake transport ignoring TERM: killed within the fixture's8-second bound, with timeout/kill exit status.
5. Post-launch failure: original exit19 retained, all six after snapshots attempted, final fixture exit4 rather than success.

Retained fixture directory:
`/home/ben/.jcode/scratch/r01-stereo-addendum03-20260908T0615Z/stereo-runner-fixture.y8mrEi`

Its `exit-postflight.log` shows `runner_exit=19 broadcast_exit=0 fresh_receipt=0 observation_failed=1`, with six fake calls recorded separately. This establishes the reviewed trap behavior without launching the real main function.

```text
53b9f2f1d97310d5e4fc9d15c45cc179090f1e04575445d5c692c688e0e86506  stereo-runner-fixture.y8mrEi/bin/adb
b65ba68ea07eea9f1cc44846a27e05d75f1e5343babf9124e05aa7599d2cb5a7  stereo-runner-fixture.y8mrEi/exit-postflight.log
```

## Remaining uncertainty and release

No new source correction is requested by this narrow reassessment. Coordinator follow-through remains: Android compilation, source/artifact pinning, and any permitted device trial with independent postflight. A compact receipt remains an experiment failure and must not be used as stereo acceptance.

The original startup-threshold hypothesis remains unproven on the corrected phone path. This addendum claims no true stereo, latency, physical audibility, device cleanup, SoundCloud capture, or full-product acceptance.

No shared-source edits, Git mutations, Android builds, real runner main execution, real ADB/phone action, GUI, network, or workers occurred. Only authorized host Java tests, static shell checks, and the reviewed private fake-ADB fixture ran. Earlier reports and runners remain unchanged.

**Review complete. The coordinator may now release and change all six frozen correction files.** This immutable report covers only the hashes above. Later changes require a separate assessment.
