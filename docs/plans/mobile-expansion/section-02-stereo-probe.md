# Section 2 finite stereo feasibility contract

## Context and outcome

This source-only increment tests independent own-UID stereo before any product transport change. It does not close R01, prove SoundCloud capture, or establish physical audibility. Research source: `/home/ben/.jcode/scratch/root-audio-stereo-20260908/REPORT.md`, read in full. Samsung runtime behavior remains unmeasured.

## Fixed platform and modes

Protocol2 adds only debug mode4 (per-track BY_SYSTEM) and mode5 (per-track BY_NONE). Release rejects both during SELECT parsing before provider access. The fixed installed PIE, sealed fd6 ART, generation/build/package identity, original credential UID, grant/namespace checks, trusted classpath, parent death, revocation, EOF and RootHelperLease remain authoritative. Modes0–3 and PCM15 retain their exact format. Modes4/5 emit aggregate READY11/RESULT12 and progress16 only, never PCM15.

DUMP-protected debug actions are `dev.phosphor.mobil3.ROOT_STEREO_SYSTEM_TEST` and `dev.phosphor.mobil3.ROOT_STEREO_NONE_TEST`. They accept no configuration extras and share existing single-flight ownership. There is no new UID, package, path, network, recording or administration surface.

Before launch, require idle music and no existing capture, microphone, local or relay owner. Main-thread observations have a1second deadline and cancellation gate. Recheck quiescence before READY acceptance and each heartbeat. Never stop another owner. The100-control-frame cap fits within2000pipe bytes. Fixed aggregate receipts are `root-stereo-system.json` and `root-stereo-none.json` in private app files.

## Identity, routing and gain preflight

Before registering a policy, create a 48kHz PCM16 stereo MEDIA monitor at per-track gain1. Play bounded silence. Match its generated audio session ID and actual helper PID against exactly one active AudioTrack/MEDIA playback configuration. Read hidden getClientUid under actual root MODIFY_AUDIO_ROUTING authority. Retain and recheck its player interface ID. Require a nonnegative actual monitor UID distinct from the original app UID. Process.myUid and attribution-source metadata alone are not proof. Preserve actual framework attribution without spoofing.

Observe the playing monitor's actual getRoutedDevice. Require an output other than remote submix, and preserve its ID/type/address throughout the probe. Reject missing/ambiguous identity, communication mode, unresolved cleanup, and route or identity changes. Do not set preferred devices, audio focus, physical volume or process capture policy.

Register only originalUID AND (MEDIA OR GAME), LOOP_BACK only, privileged capture false. Create the record through createAudioRecordSink so only the framework adds the fixed-submix-volume tag. Require actual record and monitor formats48000/2/PCM16. Unit monitor gain does not prove gain preservation. Physical volume, concurrent legacy submix, fixed-volume restoration and audible restoration require parent preflight/postflight checks.

## Finite acquisition and lifetime

Setup including READY/START is at most10s. Silence is cleared before READY. START precedes source playback. The fixture is exactly240000 stereo frames, L997Hz/R1499Hz at48000frames/s, amplitude0.025,10ms endpoint ramps, exact per-track BY_SYSTEM or BY_NONE. Save source write counts and playback head/timestamp progress. Do not change app process capture policy.

Stop the source when its playback head reaches240000frames, or after5500ms wall time, whichever comes first. The static buffer cannot add frames. Success requires the complete source head. A5second wall cutoff cannot establish5seconds of source progress when startup has latency. Any output parsing or validation exception makes protocol cleanup uncertain. Validate FINAL generation before retaining terminal evidence.

Native protocol rejection is also sticky, including errors filtered before app delivery and frames drained after waitpid. FINAL cleanup requires an accepted terminal result, intact protocol, natural reaping and no forced kill. A valid helper error can still establish cleanup. A rejected later frame cannot restore that claim.

One nonblocking record reader uses a fixed480-frame stereo buffer. Each unchanged block feeds aggregate statistics and the monitor. Nonblocking partial monitor writes retain their exact pending offset. No next read occurs until the pending block completes. Reject odd sample counts, negative reads/writes,100ms stalls, more than4800 queued monitor frames, route/identity changes or deadline exhaustion. No pending audio silently disappears or reorders. Capture including tail is at most5.5s. Read progress16 uses the existing identity and sequence, every250ms.

Measure the middle3seconds of captured frames. Streaming Hann tone accumulators retain no recording. Evidence includes requested/actual formats, actual monitor UID/session/player/route, record/read/frame counts, monitor write/head/timestamp progression, RMS/peak/correlation, both frequencies in both channels, separation and cleanup. `physical_audibility_proven=false` always.

Stop/flush/release monitor before record stop/release and synchronous policy unregister. Join the policy thread within500ms. Native retains its one-second grace and two-second reap bound. App cleanup is bounded by8s. A blocked Binder call cannot manufacture completion. Native watchdog/death handling terminates ownership, but killed or incomplete cleanup latches replacement closed. Apply the same order before READY and on identity failure. Do not retry automatically.

## Acceptance map

| Requirement | Check and acceptance |
|---|---|
| Fixed debug scope | Host Rust selection tests accept4/5 only in debug and reject before grant in release. Java/Kotlin identity tests keep modes disjoint. |
| Unchanged mono product | Existing protocol tests still pass. PCM15 rejects probe modes. No ring/JNI/beam change. |
| Independent channels | Host Java and authored Android tests exercise valid stereo, swapped channels, duplicated mono, silence, cross-leak and partial chunks. |
| Signal thresholds | Both RMS>0.001, no clipped sample, absolute correlation<0.1, intended997L/1499R separation>=30dB. Swapping is failure. |
| Actual fidelity | Parent checks actual48000/2/PCM16 and frame/timestamp rate within1percent using boundary-aware receipts. No96/192k claim. |
| Feedback exclusion | Parent confirms exact active monitor player/session UID differs from original app UID before policy, physical route stable throughout. |
| Read/write bounds | Host partial-write accounting tests and source review. Parent observes counts, queue limits and abnormal termination. |
| Start/death/cleanup | Parent exercises pre-READY failure, normal stop, owned helper death, app death, EOF/revocation and verifies no policy/process residue. |
| Physical playback/gain | Parent independently checks audibility, physical volume/route/fixed-submix state restoration. Writes never prove audibility. |
| Product/SoundCloud | Deferred until stereo feasibility, gain and lifecycle checks pass. Modes2/PCM15 remain unchanged. |

## Valid blocked outcome

If runtime identity cannot be proved, fail before policy registration with its exact cause and cleanup evidence. If host checks pass without Android execution, report source-only readiness, not hardware success. Parent owns all Gradle, device, integration, Git and independent acceptance work.

## Timestamp and source identity addendum

Pinned Android16 `AudioPlaybackConfiguration.java` exposes hidden public getSessionId, getClientUid, getClientPid, getPlayerInterfaceId, getPlayerType and getPlayerState. Anonymized copies clear UID/PID/session. The probe requires exactly one matching generated session, actual helper PID, active AudioTrack/MEDIA and stable player ID. It does not require inaccessible PlayerBase state. Source: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/media/java/android/media/AudioPlaybackConfiguration.java .

Record and monitor first/last timestamp evidence uses the monotonic timebase and records validity, frame positions, nanoseconds, read/written frame totals and monitor head at observation. Both first timestamps must follow the capture epoch. At least1second of paired progress must measure48000frames/s within1percent for each endpoint. Frame zero resets after startup silence. Every partial write preserves the source-frame index. Requested/actual buffers and queue highwater measure software buffering only, not acoustic or Bluetooth end-to-end latency. Timestamp backlog above the actual record buffer fails as an overrun. Monitor underrun counts are reported without hiding startup starvation. The fixed static fixture holds exactly240000 generated frames without a writer thread. Actual source head/timestamp and total writes remain acceptance evidence. No source recording is saved.

## Authored increment receipt

The existing Hann sums also report intended997L/1499R sine-reference phase radians and exact steady-window start frame48000. These can support later source-offset estimates. Clock resets and filter phase remain caveats. Phase does not establish acoustic timing.

Host locked Cargo tests passed27/27. Strict host javac (`-Xlint:all -Werror`) passed207 existing protocol/tone checks and57 stereo checks. Seven focused testDebug methods are authored but unrun. SDK javap confirmed the referenced public format, buffer, timestamp and remote-submix constants. The framework helper and Kotlin path were not compiled or executed in this writer window. No Gradle, device, installation or Git action occurred.

Parent requested a related mode3 bound correction during implementation. `Protocol.readCount` now caps each mode3 read to its remaining80000-frame budget. Host cases cover partial prior counts79841,79900,79999 and80000. Modes0–2 and PCM15 format remain unchanged. Parent observed80128frames on the prior mode3 build. This confirms the old cap defect, not every previous SYSTEM failure cause.

Remaining checks belong to parent: Android compilation/tests, actual Samsung hidden-API identity visibility, timestamp availability and origin, measured buffer stability, stereo SYSTEM/NONE capture, gain/audibility, fixed-volume and physical route restoration, owner/death/EOF/revocation cleanup, then SoundCloud. A strict preflight or timing failure is evidence to investigate, not permission to raise volume, spoof identity, drop frames or relax cleanup.

## Measured startup-buffer correction, 05:44 UTC

The first installed SYSTEM trial stopped cleanly at the unchanged100ms monitor queue limit. Android returned8793monitor frames despite the960frame constructor request. Pinned [Android16 AudioTrack](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/media/java/android/media/AudioTrack.java) lines2249–2280 and2306–2362 distinguish capacity, effective buffer size and streaming start threshold. The default threshold is capacity. Actual trial thresholds remain unmeasured.

Before the first silent write, configure only the owned streaming monitor with effective buffer960frames and start threshold480frames on API31+. Read back capacity, effective size and threshold, including setter returns. API29–30 use the effective buffer as their documented startup bound without calling newer methods. Require positive matching setter/readback values, threshold no greater than effective size, and effective size no greater than960frames. Unsupported or inconsistent configuration fails before policy registration. Do not change another player, physical route, focus, volume, sample rate or global setting.

Repeat the configuration after the physical route becomes known and startup silence is cleared, before policy registration. Subsequent route checks also verify that effective size and threshold remain within the accepted bounds. Capture queue allowance stays4800frames, stalls stay100ms and source/deadline/cleanup criteria stay unchanged. This is a fixed buffer correction, not a broad adaptive tuning sweep.

Report default and configured monitor capacities, effective sizes, start thresholds, setter returns and actual performance mode. Include the queue value that triggered rejection in highwater evidence. Successful buffer configuration alone does not prove stereo, actual latency, no underrun, or physical audibility. Host tests cover large default capacity with a bounded effective buffer, refused/clamped settings, stale readback, invalid threshold and the unchanged queue failure boundary. Coordinator-owned Android compilation and a new clean reviewed dual freeze precede any second trial.

## Terminal byte budget and runner deadline correction, 06:01 UTC

Independent addendum02 accepted the narrow buffer configuration at source level but identified two retained validation gaps. A700-character escaped cause or500-character cleanup value can exceed the4016-byte JSON allowance. Finite loop counts do not bound a stuck ADB command. The report remains unchanged.

Modes4/5 will pass RESULT through a serialized UTF-8 byte check before writing. Keep full JSON unchanged when it fits. Otherwise send a fixed compact failure envelope with the validated root/originalUID, build, protocol, generation and mode identity, truthful cleanup boolean, original encoded length and explicit diagnostic truncation. Do not copy unbounded exception text into the fallback. A compacted result is failure, never success. Keep the4096-byte frame cap and all native/app cleanup validation. Test actual JSONObject serialization with escaped controls, multibyte text, maximum cause and cleanup lengths, both buffer receipts and exact-limit boundaries. Actual Samsung serializer execution remains a device check.

A separate versioned coordinator runner will preserve the original runner. Every ADB operation gets a finite timeout and kill-after bound. After dispatch, an EXIT trap attempts all postflight snapshots with individual status recording, even after an observation failure. A nonzero or timed-out postflight leaves cleanup unknown and blocks acceptance. Limit receipt polling by elapsed time as well as iterations. Never auto-retry a fixture or kill a phone process to hide missing evidence. Host-only wrapper fixtures use a fake ADB process under private scratch, not the phone.
