# R13 root visual-epoch correction: bounded implementation research

Status: design handoff only. No implementation, build, test execution, device acceptance, critique round, or rating. Research began 2026-09-08 08:36:10 UTC. Product-source reads ended by 08:41:25 UTC. Source/read ownership is released with this report. Root owns implementation, gate5254318h78, integration and the phone.

## Identity and scope

- Mobile commit: `0591d7345cdbc701dd2549ef62037959c1fbdaca`.
- Mobile tree: `309d81671a186dd88a3be77bedc924c9d369913a`.
- Shared commit: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Shared tree: `51f828e60c5bbfd7003694bf28b7455daf07db1e`.
- Exact readset: `READSET.sha256.txt`, SHA256 `c32a9fa16b5279e2fa234601d138a542eec381f7566350b73f2e8b002f7a4627`.
- Route supplied by coordinator: `openai-oauth:gpt-6-astra`, high. No workers started.

Only pinned product Git objects were inspected. Governance and the specifically allowed retained report were the non-object inputs. That report supplied only F1, lines44-72. The initial read returned broader context than needed, but no other finding was analyzed or rescored. The readset discloses partial and discovery-only reads. Hashing a whole blob does not mean its whole source was reviewed.

No live tracked source reads, edits, builds, tests, Git mutations, device/ADB, GUI/GPU, network, audio, service, policy, route, grant, system/vendor/boot/vbmeta actions occurred. The only task files written are this private report and its readset. The first manifest command was refused before execution, then explicitly authorized within the user's private-file allowance.

## 1. Exact failure and smallest correction

F1's trace remains: helper reads marker A during HOLD, resume publishes a new VISUAL_EPOCH and clears visual ingress, then the same root session delivers A through samples-only JNI. Kotlin session ownership passes but carries no producing-read identity. A newly eligible render can therefore consume A.

Evidence, with `K/` meaning `app/src/main/kotlin/dev/phosphor/mobil3/`:

- `AudioPolicyMain.java:139-156` starts the recorder before READY, reads up to160 mono shorts nonblocking, and serializes PCM after read.
- `Protocol.java:73-78` and `K/RootAudioProtocol.kt:63-73` carry identity, PCM sequence, format and count, not epoch.
- `K/RootCaptureSession.kt:86-96` normalizes samples and invokes a samples-only callback.
- `K/CaptureService.kt:328,343-346` discards activation's producer token and calls `pushCaptureSamples`.
- `rust/src/jni_glue.rs:345-356` delegates to the unguarded owner/epoch path `deck.rs:38-39`.
- The reusable alternative already exists at `jni_glue.rs:368-384` and `engine.rs:127-144`: original owner, active state and read epoch are checked together with ring publication under ring then meter locks.
- `pause.rs:132-158` publishes VISUAL_EPOCH and clears visual pending samples/measurement inside ring, meter, History ordering. Visual clear preserves capture ownership.

**Decision:** extend only the existing private fixed root streaming protocol with one epoch control and one acknowledgement. Attach the producer's adopted epoch to every PCM batch. Retain the original app activation token and use existing `pushCaptureRead`. Do not add a new source, recorder, command endpoint, shared network message, or GPU operation.

A necessary small detail is the visual normalizer. `app/src/main/java/dev/phosphor/mobil3/RootPcmNormalizer.java:5-20` retains `previous` across blocks. Merely tagging new PCM would interpolate an old-epoch sample into the first new-epoch output. Reset this visual-only interpolation state on an adopted epoch change before converting that epoch's first eligible batch. Constructing a fresh normalizer is sufficient, with no normalizer API change. Same-epoch chunk continuity remains unchanged.

## 2. Fixed wire contract proposal

These are proposed fields, not existing behavior. Keep the outer magic `0x31524150`, 12-byte little-endian frame header and4096-byte payload maximum. Keep protocol2 and exact packaged build identity, with a mandatory `pcm_epoch_schema:1` READY field for modes2/3. This is a coordinated stream-schema change, not backwards-compatible PCM. Missing capability or old-sized PCM fails closed. Exact helper build and sealed DEX prevent mixing packages. Do not silently fall back to samples-only delivery.

Identity `I` remains80 bytes: original UID u32,64 ASCII build bytes, session generation u64, mode u32. Generation stays positive and at most signed Long.MAX_VALUE. Producer token stays app-local and is not added to privileged IPC.

| Frame | Proposed fixed payload | Length and rule |
|---|---|---|
|20 SELECT|existing generation, mode|12 bytes, unchanged|
|1 HEARTBEAT,2 START,3 STOP|existing generation|8 bytes, unchanged|
|4 SET_VISUAL_EPOCH|generation u64, control_seq u64, visual_epoch u64|24 bytes, only streaming modes2/3|
|17 VISUAL_EPOCH_ACK|I, control_seq u64, visual_epoch u64, next_pcm_seq u64|104 bytes, exactly one per accepted request|
|15 PCM|I, pcm_seq u64, rate u32, channels u32, encoding u32, count u32, control_seq u64, read_epoch u64, count i16 samples|120+2*count bytes, count1..160, maximum440|
|16 PROGRESS|I, existing progress_seq u64|88 bytes, unchanged|
|11 READY,12 RESULT,13 evidence,14 FINAL|existing identity/state rules|retain4096 payload bound and terminal handling|

PCM offsets are identity0..79, seq80, rate88, channels92, encoding96, count100, control_seq104, read_epoch112, shorts120. ACK offsets are control_seq80, epoch88, next_pcm_seq96. No strings, caller paths, executable names, selectors or arbitrary parameters enter the new controls.

Use control_seq1 through signed Long.MAX_VALUE, exact next sequence, no replay or wrap. PCM and progress sequence domains remain independent and do not reset at epoch changes. Align checked sequence exhaustion across Java/Kotlin/Rust, rejecting rather than wrapping or emitting a frame whose next expected sequence cannot be represented. Read epochs are opaque native u64 bits transported through Long. Zero is valid because VISUAL_EPOCH starts at zero. Use unsigned comparison for strictly advancing epoch requests after initial binding. Do not impose `epoch > 0` or signed-positive token checks. Repeated same epoch produces no request. An observed regression/wrap ends this capture fence as an explicit error rather than accepting aliasing. This bounded root session does not redesign global History counter exhaustion.

ACK means: this helper owner adopted this epoch before the next AudioRecord read, and the first subsequent positive read will use next_pcm_seq. It does not mean first input was freshly produced, a frame was rendered, or anything reached the panel. Zero reads may leave next_pcm_seq unchanged across several epoch acknowledgements.

## 3. Ownership and ordering, by file

### App activation and control owner

1. In `CaptureService.startRoot`, keep the existing main-thread READY validation, sourceRevision, owner and rootSession checks.
2. Store the nonzero token returned by `setRingActive(true)` in the READY future result rather than discarding it. Transfer an immutable binding to the existing root reader thread through that future. Do not use a mutable global latest token in the samples callback.
3. Have `RootCaptureSession.run` obtain the native epoch through a fixed callback backed by `captureReadEpoch`. This samples desired control state, not PCM age.
4. After READY ownership is accepted, send initial SET_VISUAL_EPOCH. Wait for its ACK, then send existing START. Initial ACK has next_pcm_seq0. No PCM is legal before START and initial ACK.
5. Re-read desired VISUAL_EPOCH in the existing owner loop before each bounded consume pass. Keep one desired value and at most one outstanding request. Coalesce newer desired epochs while an ACK is pending. Never grow a queue of resumes.
6. Only this owner writes child stdin. Main/UI/native resume threads never write pipe controls or wait for an ACK while holding ownership locks. The existing native resume transaction remains unchanged.
7. When ACK6 arrives after desired epoch7, validate ACK6 but do not treat6 as current visual ingress. Send7 at the next allowed control slot. Old PCM is parsed for wire sequencing, then rejected for visual publication.

`RootCaptureService` remains the same backend wrapper. Its debug check interface can remain transport-observation-only. Do not claim `RootCaptureCheck.samples` proves native admission. The existing JNI returns Unit, although its production engine primitive returns bool. Use native ring assertions for acceptance tests rather than broadening this ABI merely for a receipt.

### Supervisor: `root-helper/native/src/lib.rs` and `platform.rs`

Add optional acknowledged binding, optional pending request, next control sequence and last request time to `Session`. The pending record is a fixed tuple `(control_seq, epoch, requested_at)`. Keep `Session.app`'s forwarding result: true forwards START or the validated new control, false retains heartbeat/STOP handling.

Permit SET_VISUAL_EPOCH only in Ready or Capture for modes2/3, after READY, with no existing pending request. START in those modes requires acknowledged initial binding. Non-streaming modes retain their old START rule.

Accept ACK only for the exact pending tuple, matching I, legal phase and exact `next_pcm_seq == Session.sequence`. Validate it in both ordinary helper draining and the post-waitpid terminal drain. Install the acknowledged binding only after validation. Before ACK, in-flight PCM must match the previous acknowledged binding. After ACK, only the new binding is legal. FIFO output and exact PCM sequence prove that old serialized PCM precedes the ACK. Do not drop bytes from the pipe or reset parsers to seek an ACK.

A pending ACK may be drained once during Stopping if its request was already forwarded, and only before RESULT. It does not restart capture. RESULT cancels the pending request and forbids subsequent PCM/ACK/progress. Do not classify a clean finite mode3 ending while an ACK was pending as protocol corruption merely because no ACK followed. Duplicate, unsolicited, wrong-sequence or post-terminal ACK is corruption.

`platform.rs` already forwards every successfully validated helper frame in both drain paths. No new process, descriptors, command argv, environment, privilege or grant behavior is needed. Keep that shared validation path, rather than recognizing ACK only in the normal loop.

### Java producer: `Protocol.java`, `AudioPolicyMain.java`

Keep AudioRecord and control processing on the current single helper owner thread. Add a small pure epoch state primitive, either nested in Protocol or one narrowly named Java file, and use that exact primitive in production and host fixtures.

The existing `control(waiting)` boolean means a received command exits the wait-for-START loop. Do not make epoch receipt return the same meaning as START. For modes2/3, explicitly distinguish NONE, EPOCH, START and STOP. Preserve the existing mode0/4/5 control behavior and StereoProbe callsites. A separate streaming control method avoids touching those probe semantics.

For initial binding, adopt epoch in the Ready wait loop and write ACK0 before accepting START. For subsequent binding, process control between reads, after the previous read's PCM serialization finishes. Assign adopted `(control_seq, epoch)`, write ACK with the current next PCM sequence, then take a local immutable snapshot of that pair immediately before `record.read`. Use that snapshot for all returned samples and serialization. A zero read sends no PCM. Progress retains its own250ms schedule.

An artificially held read cannot process a new control concurrently. Its old PCM is sent with its old pair, then the pending control is adopted and acknowledged before the next read. If future refactoring adds asynchronous serialization, the batch still owns the original snapshot. Never read a mutable current epoch when finally sending the batch.

Do not call stop/startRecording on resume. Do not drain or flush AudioRecord as part of this correction. Leave capture rules, UID filtering, privileged capture flag and route flags untouched.

### Kotlin parser, normalizer and native publication

Return a typed `PcmBatch(samples, pcmSequence, controlSequence, readEpoch)` from `RootAudioProtocol.Stream.pcm`, not a bare ShortArray. Stream state validates the requested ACK and the PCM binding exactly as the supervisor does. Parse every valid old batch and advance transport sequence even when it is visually ineligible.

`RootCaptureSession` filters against current desired/native epoch without changing the batch tag. It replaces its visual normalizer before the first eligible batch of a different readEpoch. Do not normalize stale batches into the new normalizer. If resume races conversion or JNI array copy, preserve the original epoch and let native reject it. The next new-epoch conversion resets again as needed.

`CaptureService` calls `pushCaptureRead(normalized, normalized.size, originalOwner, batch.readEpoch)` under the existing running/session/service predicate. Never call `setRingActive` from a sample callback. The native engine predicate remains authoritative because Kotlin checks can race replacement or resume.

Reuse `engine::publish_capture_read` unchanged. Under ring then meter it verifies nonzero original token, exact capture owner, active source and current VISUAL_EPOCH before pushing. No DISPLAY lock or pipe/GPU work is added there. A token change rejects an old root session even if the visual epoch happens to match. A visual epoch change rejects an old read while preserving the continuing producer token.

The samples-only API can remain for unrelated callers in this smallest patch, but root production must no longer reach it. Update its stale explanatory comment and source-wiring checks. No shared Rust changes are required. Shared `SampleRing::clear_pending` preserves history and has no audible queue access.

## 4. Bounds and failure handling

Retain existing bounds:

- App consumes at most65536 stdout bytes and8192 stderr bytes per pass, sleeps5ms, heartbeats every250ms.
- Supervisor reads at most8192 bytes per receive call. Terminal reaping performs at most four further receives,32768 bytes total. Decoder retains at most4108 bytes.
- Java writes nonblocking. Supervisor send fails on backpressure/closed pipe rather than accumulating an unbounded output queue.
- Supervisor heartbeat timeout exceeds2000ms. Ready budget10000ms. Helper progress timeout exceeds3000ms. App startup12000ms and progress4500ms checks remain.
- Mode2 remains continuous. Mode3 remains debug-only, own-UID, at most5000ms Java read loop and80000 mono frames. Epoch changes never reset start time or streamFrames. Non-mode2 supervisor capture budget6000ms and app mode3 total18000ms stay fixed.
- STOP kill escalation remains1000ms, supervisor reap bound3000ms, app cleanup7500ms and awaitStop8s. Keep post-exit terminal parsing and protocol-clean latch.

Proposed new finite bounds: at most one outstanding epoch request and one coalesced desired epoch. Allow at most one new epoch request per125ms, burst one, in app and supervisor. Initial request is immediately eligible. Fixed request36 wire bytes and ACK116 wire bytes stay below existing limits. Use a1000ms pending-ACK deadline from request forwarding, enforced independently in supervisor and app. Heartbeats, old PCM, ordinary PROGRESS and repeated desired epoch changes do not extend it. ACK does not update helper PCM/progress liveness. These are service bounds, not promises of physical latency.

On timeout, schema failure, illegal control or bad metadata, stop root visual publication and use existing STOP/EOF retirement. Never fall back to samples-only, auto-restart capture or silently select another backend. Distinguish an operational ACK timeout with otherwise clean terminal cleanup from malformed protocol. `Session.reject` latches protocol_clean=false for malformed frames. A natural exit cannot erase that latch.

Keep helper cleanup: record stop/release, unregister only the policy already created by this session, and bounded HandlerThread join. Keep sole supervisor waitpid ownership. The app does not destroy a privileged process. `RootHelperLease.release(clean)` preserves its sticky uncertain state. Replacement remains blocked if cleanup is unconfirmed. An ACK is neither authorization nor cleanup evidence.

Modes0/1/4/5 reject the new epoch frames. Modes4/5 remain aggregate-only stereo probes, accept their existing PROGRESS and bounded RESULT, and reject PCM. No monitor playback, buffer, route, timestamp, cleanup or fixed probe threshold changes are proposed. Mode3's existing signal counters still count transport PCM even if visual admission rejects some batches. Report those meanings separately.

## 5. Source age: what actually exists and what does not

### Observed from these objects

Mode2/3 `AudioPolicyMain` has AudioRecord actual sample rate, channel count, encoding and read return count. It tracks `streamFrames`, PCM sequence, elapsed time and progress. It does not call `getTimestamp`, obtain framePosition/nanoTime, or serialize a source-frame origin. Mode3 terminal `frames` comes from ToneStats, which is not populated by the streaming branch. It must not be substituted for stream frame position.

Root session `lastPcmAt` is elapsedRealtime at app receipt. Java loop elapsed time and an epoch ACK are not AudioRecord sample timestamps. PCM sequence identifies transport batches, not source frame age. The standard app read fence likewise records call order, not production time.

Actual timestamp instrumentation exists only in `StereoProbe.java:61-69,125-126,136-176`: `record.getTimestamp(AudioTimestamp, TIMEBASE_MONOTONIC)`, status, record framePosition/nanoTime when successful, monitor AudioTrack timestamp/validity, observed System.nanoTime, cumulative read/written frames, playback head, actual record buffer frames, and first/last timestamp-rate checks. It estimates record backlog as timestamp framePosition minus readFrames. These are the separate48kHz stereo modes4/5 and their different route/policy experiment. Source code that can collect this evidence is not a retained measurement proving that the16kHz privileged mono mode2/3 sink supports the same timestamp semantics. Do not transfer its rate checks or acceptance to product root capture.

### Exact blocker

There is no timestamp/frame-position tuple for the mode2/3 recorder tied to a returned PCM batch, no established read-frame origin for that sink, and no observed hardware evidence for its timestamp availability, discontinuities or mapping to audible playback. AudioRecord starts before READY/START and can already hold buffered samples. Therefore an epoch-new read may return pre-boundary produced samples. This proposal closes the producer/pipe/app queued-old-read trace, not that buffered-source-age gap.

The public API suggested by the existing stereo source is `AudioRecord.getTimestamp(..., TIMEBASE_MONOTONIC)`. If added later for bounded evidence, retain raw status, framePosition, nanoTime and an observation-time bracket. Track cumulative successfully read source frames and the returned batch's first-frame index from a proven common origin. With continuity and nominal rate established, a mapping such as `time(frame) = timestamp.nanoTime + (frame - timestamp.framePosition) * 1e9 / actualRate` can estimate a batch interval. It is conditional evidence, not an API promise of exact per-sample time. Detect timestamp failures, backward/jumping position, overrun, resampling/timebase mismatch and discontinuity. Do not substitute observation time on failure. Use the same CLOCK_MONOTONIC basis for the actual app resume boundary, not elapsedRealtime/BOOTTIME mixed with nanoTime.

### Finite distinguishing experiment, for root's later phone gate

Use a bounded own-UID mode3 fixture with a fixed two-marker program, no new caller commands. Play coded marker A, a guard interval, then distinct marker B on one continuing AudioTrack. Record marker content, actual AudioTrack presentation frame/time where available, and the native visual-boundary epoch/time. AudioRecord/policy continue unchanged.

Run two independent holds, each bounded, for example100ms with an outer existing finite session deadline:

1. **Transport hold:** hold one completed helper read containing A before serialize/delivery, cross resume, release. A must keep the old readEpoch and be rejected. ACK and the next coordinated B batch must follow the new pair. This proves the protocol fix.
2. **Source-buffer hold:** hold immediately before an AudioRecord read while A is presented and accumulates in the capture buffer. Cross resume after A's presentation interval ends, then release the read and produce B after the boundary. Record whether the first epoch-new batch still contains A. A here is direct evidence of buffered-source age, despite correct read fencing. Confirm ordering from presentation/timestamp evidence, not when an AudioTrack write returned. If timestamp support or audible timing cannot establish that A ended before the boundary, mark the trial inconclusive.

Log only bounded per-batch counters/markers and timestamp observations. Do not flush audible queues, seek, restart the recorder, change grants/routes/policies, or lengthen the existing mode3 fixture. Do not change the existing997Hz success gate and then claim the new marker fixture passed it. Keep the marker test as a separately named finite acceptance observation using the same production primitives and fixed own-UID boundary. An approval is needed before any such runtime work, which belongs to root.

No zero-transport-latency claim follows from either test. Choose and record a finite age/timing tolerance with measured uncertainty. Already-buffered network playback that is currently audible can be the correct visual source timeline, not stale UI backlog. `remote.rs:1429-1431` explicitly assigns scope truth to the audible callback when audio is enabled. The receive-only fallback's post-payload epoch at1414 cannot establish pre-receive source age. This report proposes no shared network redesign or jitter/audible-queue flush.

## 6. Finite production-primitive tests to implement, not tests run here

| Requirement | Exact production seam and finite check |
|---|---|
|Wire agreement|Java Protocol encoder, Rust Session parser, Kotlin Stream parser consume the same fixed byte vectors. Verify all offsets, zero epoch, high-bit epoch, partial byte boundaries, coalesced frames, count1/160, malformed count0/161, old104-byte header and trailing byte rejection.|
|Old producing read across resume|Barrier in the production pure helper read/epoch primitive holds after snapshot before returning A. Change desired epoch4 to5, release A. Encode and parse actual PCM/ACK, publish through real `engine::publish_capture_read` and actual SampleRing. A yields no pending samples, next B under5 is accepted. Two-second barrier deadlines.|
|Old serialized/partially received frame|Hold encoded A after helper read and at each of header/payload/app callback boundaries. Publish native epoch5 and clear under production boundary locking. Parse A under4 without relabeling or breaking transport sequence. Assert native rejection.|
|Owner independent of epoch|Use actual engine primitive with ownerA and ownerB at the same epoch. Old root callback cannot publish, zero owner and inactive state cannot publish, visual-only clear retains A ownership.|
|Publication race|Hold producer behind the real ring lock. Change epoch while it waits, then release. Predicate rechecks inside lock and rejects. Include resume between conversion and JNI-equivalent publication.|
|Normalizer residue|Convert nonzero A under4, switch5, convert zero B with the actual RootPcmNormalizer. All first B output values are zero after replacement, not an A interpolation tail. Same-epoch every split1..159 remains equal to unsplit conversion.|
|ACK sequencing|Initial ACK before START, initial no-ACK PCM rejection, old PCM before ACK accepted as wire data but not visual data, new PCM before ACK rejected, old pair after ACK rejected. Duplicate/unsolicited/wrong-generation/wrong-seq ACK rejected. Zero reads permit unchanged next_pcm_seq.|
|Coalescing and limits|Using production state and fake monotonic clock, request5, desire6 then7 while pending. Store only7. ACK5 cannot admit into7, next request7 obeys125ms.1000ms pending deadline cannot be refreshed by heartbeats/progress/old PCM. No sleeps or unbounded test loops.|
|Stop/terminal races|STOP during pending epoch, ACK queued with child exit, RESULT while pending, duplicate RESULT, partial terminal EOF and ACK after RESULT. Exercise both platform terminal-validation callsites and actual Session cleanup latch. Keep four terminal-drain budget.|
|Lease and retirement|Real RootHelperLease acquire/release clean and uncertain cases plus existing retirement lifecycle primitives. No second owner after unconfirmed cleanup. ACK does not release lease.|
|Mode preservation|For0/1/4/5 reject SET/ACK and retain original START/progress/results. Mode4/5 reject PCM. For mode3 assert readCount reaches exactly80000 maximum and epoch changes never reset5s/6s/18s budgets. Mode2 survives the old absolute finite-probe deadline with real progress.|
|Overflow/backpressure|Checked generation/control/PCM/progress exhaustion, illegal epoch regression, payload4097 rejection, bounded pending state, write failure uses existing stop. No stale fast-path fallback.|
|Source wiring and ABI|CaptureService retains READY activation token and passes batch epoch to existing pushCaptureRead. Main helper calls the tested pre-read primitive. JNI still uses guarded engine publication. Compile exact Kotlin/JNI/helper packaging after root lifts the freeze. Wiring assertions supplement, not replace, runtime primitive tests.|
|Buffered source age|Perform the separate two-marker phone experiment above and retain timestamp availability/failure and source-to-batch mapping evidence. Host fake timestamps cannot close this gate.|

Host tests should call extracted production state machines and existing ring/normalizer implementations, not a copied model of the algorithm. Android-only orchestration still needs exact Gradle/ABI integration and the finite phone fixture. No test in this table was executed by this researcher.

## 7. Handoff and acceptance

Small production edit set: `RootAudioProtocol.kt`, `RootCaptureSession.kt`, `CaptureService.kt`, root `Protocol.java`, `AudioPolicyMain.java`, supervisor `lib.rs`, and narrowly necessary `platform.rs` integration. Add one pure helper primitive only if needed for production-test reuse. RootCaptureService, RootPcmNormalizer and native engine/epoch ABI can remain unchanged in implementation. Update the root-path comment and owned tests/spec correction record after the freeze, without altering canonical acceptance or historical reports.

**Blocked acceptance:** no implementation or runtime validation was allowed. The pinned mode2/3 source lacks timestamp/frame-position evidence for buffered-source age. **Best current result:** a concrete bounded owner/epoch/ACK contract that prevents receipt-time relabeling, rejects old producer batches and closes the normalizer carryover seam without touching audio transport. **Next step:** root implements after gate5254318h78, runs finite production-primitive and exact packaging gates, then owns the separately authorized phone evidence. The whole R13 contract remains unaccepted here.

Source/read ownership is explicitly released. No worker, process, build, device action, source lock or follow-up task remains owned by this research session. No further pinned-source reads are needed for this handoff.
