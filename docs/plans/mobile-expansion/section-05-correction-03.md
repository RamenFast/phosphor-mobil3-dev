# R13 correction03: root producing-read epoch

This coordinator-owned correction precedes implementation. It closes the old helper-read/pipe batch trace in R13 F1, not buffered-source age or F2 submission ordering. Exact baseline is mobile7ac1e5546a58711a68c4b09bb8f18fe9f057df43 and shared0ffd658d7f19e68180c2720e0500b23644619e90. Original reviews remain unchanged.

The retained [research](section-05-root-epoch-research.md) specifies offsets, ordering, bounds and finite tests. Its report SHA256 is8e5778096a6b3d633283d78d811dc453eda4202b007eb5c2a5c1fe99352aedc1. Coordinator adopts sections2–4 and6 as the implementation contract, with the source-age limits below. Do not ask Ben to choose these internal fields.

## Defined outcome and fixed wire changes

Modes2/3 keep one existing recorder, policy, helper thread and native supervisor. Retain the80-byte identity,4096-byte payload limit, fixed build identity and mode2 continuous/mode3 finite behavior. READY must declare `pcm_epoch_schema:1`. Missing capability and old PCM shapes fail closed. Modes0/1/4/5 remain unchanged and reject the new epoch messages.

- App control kind4: generation, control sequence, visual epoch as three little-endian64-bit fields,24-byte payload.
- Helper ACK kind17: identity plus control sequence, epoch and next PCM sequence,104-byte payload.
- PCM kind15: existing104-byte metadata, then control sequence at104 and producing-read epoch at112, then samples at120. Count remains1..160.
- First control sequence is1. Epoch0 is valid. Preserve opaque native64-bit epochs, including the high bit. Reject replay, regression and sequence exhaustion without wrapping.
- Initial epoch ACK precedes START. Adopt each later epoch between reads, after prior serialization, and send ACK before the next read. A batch retains the pair sampled before its read, never the epoch current at send/receipt.
- Keep one pending request plus one coalesced desired epoch. Request interval is at least125ms after the initial request. ACK deadline is1000ms and is not renewed by heartbeats, progress or old PCM.
- Validate the same metadata in Java, native supervisor and Kotlin. Both native normal and post-waitpid drains use the shared validation path. RESULT cancels pending state. An already-requested ACK may drain during STOP before RESULT, but cannot restart capture.

The app retains the original nonzero activation token returned by `setRingActive(true)`, transferred through the existing READY future. The root session samples desired epoch through the fixed native callback. It parses old batches for transport sequencing, discards them visually, resets its visual-only normalizer before the first eligible batch of a new epoch, and calls existing `pushCaptureRead` with the original owner and producing-read epoch. Native ring-lock validation stays authoritative across replacement and resume races. No samples-only fallback.

## Scope and protected paths

Production edits are limited to RootAudioProtocol.kt, RootCaptureSession.kt, CaptureService.kt, Protocol.java, AudioPolicyMain.java, supervisor lib.rs/platform.rs and narrowly named pure epoch state helpers. Add exact protocol/parser/normalizer/owner/liveness fixtures and source-wiring assertions. The existing app/native capture publication ABI, audible rings, root launch/grant/classpath rules, modes4/5 stereo probe and frame/cleanup bounds stay unchanged.

No device, audio, GUI, network, service, policy or grant action belongs to the implementation writer. No shared renderer edits. No protected phone writes, remounts, flashes or bootloader changes. Coordinator owns Gradle, Android, Git, integration, review and installation. Source remains frozen during full gates.

## Ordered verification and rollback

1. Add pure epoch state and fixed protocol vectors before activating streaming. Host tests exercise zero/high-bit epochs, partial/coalesced frames, wrong identity, old shape, bad sequences and count bounds.
2. Wire the single helper read/control owner. Test held old read, ACK order, zero reads, pending coalescing/deadline, STOP/RESULT/terminal races and unchanged finite mode3 budget.
3. Wire app original owner and typed batch. Test old batch rejection, same-epoch normalizer continuity, new-epoch zero input without old interpolation residue and replacement during native publication.
4. Retain original root30-test and Java436-assertion baseline coverage rather than replacing tests with a copied algorithm. Run narrowly bounded locked/offline host tests on production state with private outputs. Record exact counts, not expected totals.
5. After explicit writer release, coordinator runs Kotlin/JNI/helper compilation, full unit/lint/dual-artifact/engine/production gates on frozen source. Independent R13 review is reserved until the other material correction is ready.

Recovery is the clean baseline7ac1 plus retained installed06f APK and byte-identical preference archive. No installation happens during source authoring. Coordinator may revert only this correction's committed paths if the integration fails, preserving unrelated color work. Do not reset Git or overwrite worker-owned source.

## Remaining acceptance boundary

An ACK proves adoption before a read, not production time or physical presentation. AudioRecord can return samples buffered before resume. This correction neither flushes nor restarts it. Timestamp/frame-origin evidence and a finite separate two-marker device experiment remain required to measure source age against the actual audible timeline. No zero-latency promise, receipt-time relabeling, fabricated timestamp, arbitrary safety margin, or stereo claim is allowed.

**Blocked:** report the exact unclosed production seam, failing check and current source receipt. Release the writer window with an honest partial implementation. Do not expand into audio transport or lower a gate to obtain a pass.
