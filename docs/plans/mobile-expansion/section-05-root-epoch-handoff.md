# R13 producing-read epoch implementation handoff

## Receipt and ownership

Implementation worker route: enforced `openai-oauth:gpt-6-astra`, high. No workers were spawned.
Writer window began 2026-09-08 08:49:10 UTC. Deadline: 09:19:10 UTC.
The owned source snapshot was captured at 09:13:17 UTC. No source edits or builds follow this handoff.
The final swarm report releases all source and build ownership to the coordinator.
No background process, device action, source lock, or follow-up task remains owned by this worker.

Contract: `docs/plans/mobile-expansion/section-05-correction-03.md` from coordinator commit `85ca2122351b1412fbbbeef052a97f7873625ad3`.
The retained research and pre-code contract were not edited.
Starting product baseline: mobile `7ac1e5546a58711a68c4b09bb8f18fe9f057df43`, shared `0ffd658d7f19e68180c2720e0500b23644619e90`.
Concurrent coordinator work advanced repository HEAD to `1e68b6327252b22424839b5f6c8464cd5731f3da` at receipt time.
This worker made no Git mutation and did not edit the coordinator's color files.

Private handoff directory: `/home/ben/.jcode/scratch/r13-epoch-20260908T084910Z`.

- `CHANGED-FILES.sha256` lists exactly 15 owned changed files, including new files.
- Manifest SHA256: `4255b891a3a75be79b6a5f5fc801cee9561003f97e20b7affb817702ca9b95f4`.
- `owned-source.tar.gz` contains those exact files, including untracked additions.
- Archive SHA256: `4d61485b2781ec2ca107e7aa2858af1959ee41b5039873196b6f81dc46246dbe`.
- `tracked-changes.patch` supplements the archive. It does not contain untracked additions.
- `COMMANDS.md`, `kotlin-check.sh`, final logs and Java-generated binary vectors preserve the checks.
- The handoff, receipts, archive and vectors are sealed read-only. Source files remain under coordinator ownership.

## Implemented production path

Java `RootEpoch` is the helper's single-owner state primitive. SET kind4 binds an immutable `(controlSequence, epoch)` pair.
Initial adoption emits ACK kind17 with next PCM sequence0 before START. Epoch0 and high-bit epochs remain valid.
`AudioPolicyMain` processes later controls between reads after prior PCM serialization. It takes `epochs.beforeRead()` immediately before `record.read`.
PCM serialization uses that retained binding, not current epoch at send time. Zero reads emit no PCM.
The existing recorder, helper thread, policy, privilege rules, read size, finite frame budget and cleanup remain in place.

Java `Protocol`, native `Session` and Kotlin `RootAudioProtocol.Stream` agree on the fixed offsets:

- Control: generation0, control sequence8, epoch16. Payload24.
- ACK: identity0..79, control sequence80, epoch88, next PCM sequence96. Payload104.
- PCM: original metadata0..103, control sequence104, producing-read epoch112, samples120. Count1..160.
- READY requires top-level integer `pcm_epoch_schema:1` for modes2/3. Old PCM shapes fail closed.

The app and supervisor keep one pending request. The app keeps one coalesced desired epoch.
Both enforce a125ms request floor and a1000ms pending deadline independent of PCM, heartbeat and progress liveness.
Epoch order uses unsigned comparison. Sequence exhaustion fails without wrap. Long.MAX_VALUE is the exhausted next-sequence sentinel.
A late matching ACK can be validated for terminal draining, but records an operational timeout and cannot restart capture.
RESULT cancels pending state. STOP permits an already-requested ACK before RESULT. Unsolicited, duplicate and post-terminal ACKs fail.
Both existing native platform drain callsites still use `Session.helper`. The four-receive terminal drain bound is unchanged.
The Kotlin post-waitpid drain now shares the protocol-clean latch handling rather than relying on error-message heuristics.

`CaptureService` transfers the original nonzero activation token through `CompletableFuture<Long>`.
`RootCaptureSession` binds that immutable owner to its visual normalizer and samples desired epoch through `captureReadEpoch`.
It parses old PCM and advances transport sequence, but does not normalize stale batches.
The actual `RootPcmNormalizer` is replaced only before the first eligible batch of a different epoch.
Root publication calls existing `pushCaptureRead(normalized, normalized.size, readOwner, batch.readEpoch)`.
No samples-only fallback, latest-owner lookup, resume restart, recorder flush or new native ABI was added.
Controlled mode3 signal counters still observe transport PCM rather than claiming native admission.

Modes0/1/4/5 reject the new controls. Their old START/progress and aggregate probe implementations remain unchanged.
The sole extra authorized test edit is `root-helper/tests/StereoProbeTest.java:61`, approved by coordinator at09:05:27 UTC.
It supplies an immutable binding to the existing negative mode4/5 PCM assertion. No assertion was removed.

## Observed host checks

| Check | Observed result | Receipt |
|---|---|---|
| Rust1.96.0 locked/offline, private CARGO_TARGET_DIR | 37 passed, 0 failed. Original30 tests retained. | `rust-final.log` |
| Java --release17 -Xlint:all -Werror, production pure sources | Compilation passed. | `COMMANDS.md` |
| Original Java baseline | ProtocolTone207 + StereoProbe75 + StereoTerminal154 =436 assertions passed. | `java-final.log` |
| New Java RootEpoch test | 32 assertions passed, including a two-second bounded held-read barrier. | `java-final.log` |
| Cached Kotlin2.4.10 compiler and JUnit4.13.2, private outputs | 10 tests passed, 0 failed. | `kotlin-final-lease.log` |
| Owned tracked diff whitespace check | Passed. | Command recorded in `COMMANDS.md` |

The Java test queues a new control while the actual producer-state read snapshot is held.
After release, the old PCM retains epoch0 and control1. The next ACK and zero PCM carry high-bit epoch and control2.
Those exact generated binary frames pass the actual native and Kotlin decoders at all819 split points in the818-byte concatenated stream.
The parser fixtures cover wrong identity, sequence and binding, old shape, count limits, pending STOP/RESULT and terminal rejection.
Native fixtures verify late ACK does not update PCM/progress liveness and operational timeout can retain clean cleanup.
Kotlin fixtures verify coalescing, zero-read ACK sequence continuity, timeout, sequence exhaustion and missing capability.
The actual normalizer passes all159 same-epoch chunk splits and produces no old interpolation residue after an eligible epoch change.
The actual lease primitive runs in an isolated classloader and retains its sticky uncertain state. ACK cannot release it.
Source assertions verify immutable app owner wiring, the Java pre-read snapshot order, fixed finite budgets and both native validation callsites.
These source assertions are not Android runtime acceptance.

An initial Kotlin run had one boxed Integer-versus-Long expected-value assertion failure. The test expectation was corrected, then all final checks passed.
Inspection found an out-of-scope old Java test signature before the full baseline compile. Coordinator authorized that one adaptation, then all436 baseline assertions passed.

## Authored but unrun and remaining gaps

`app/src/test/kotlin/dev/phosphor/mobil3/RootCaptureProductTest.kt` was adapted to perform the initial epoch handshake and inspect typed PCM samples.
That broader test class was not run by this worker because its production dependency file includes Android lifecycle code.
The unchanged debug `RootAudioProtocolTest.kt` and full app unit/lint suite were not run here.

The Android-only `AudioPolicyMain`, `RootCaptureSession` and `CaptureService` orchestration were source-wiring checked, not compiled against Android or executed.
No Gradle, Android build, JNI target execution, GPU, device, ADB, GUI, audio, network, service, grant, install, shared repository edit or Git mutation occurred.
The cached host Kotlin compiler, JVM tests and locked/offline Rust host crate were the executed build checks.

Actual `engine::publish_capture_read` owner replacement and ring-lock race tests were not run in this worker window.
The existing guarded primitive and ABI remain untouched. Its authoritative owner/epoch admission still needs coordinator's engine/JNI integration gates.
The held-read test proves immutable producing-read transport tagging, not Android AudioRecord behavior or native ring acceptance.
The two native platform callsites were verified by source assertion and actual Session parsing, not by running the privileged supervisor process.

Buffered AudioRecord source age remains explicitly unproven. An epoch-new read may return samples produced before resume.
No timestamp, source-frame origin, flush, recorder restart or physical-presentation claim was introduced.
The coordinator's separate finite two-marker runtime experiment and full frozen-source packaging gates remain required.

**Blocked acceptance:** Android orchestration and guarded native publication were outside the permitted worker checks, and source age has no runtime evidence.
**Best current result:** all three production wire languages and immutable original-owner ingress are wired, with all permitted final host checks passing.
**Next smallest correction:** coordinator verifies this source manifest, freezes the owned paths, then runs exact Kotlin/helper/JNI compilation and native publication gates.
If an outside source-wiring fixture fails on the updated signature or strings, adapt that exact fixture without adding a compatibility fallback.
