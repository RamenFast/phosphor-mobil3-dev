# SECTION 8 / R17 full outcome critique, round 3

## Result and custody

**R15 score: 8/10. Disposition: stop corrective critique at round 3. No new material source defect found in the finite reviewed scope.**

This is a full section-8 outcome critique, not a correction02 spot check. The score covers intent, actual adapters, original corrections, cross-layer provenance, UI entry, lifecycle, privacy, and retained verification. It is not Android acceptance, installation, release approval, or a new score for R13, R16, or R09. The original 7/10 reports remain unchanged.

- Reviewer: independent GPT Astra/high worker under the coordinator-supplied verified `openai-oauth:gpt-6-astra` route. The coordinator completed the routing ritual. This worker did not repeat it or spawn workers.
- Mobile repository: `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`.
- Mobile product pin: `a0d3c128fe65bceb3302f70e50a6d7fcaecf59ca`.
- Shared repository: `/home/ben/Dev/ClaudeWorkspace/phosphor`.
- Shared pin: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Private bundle: `/home/ben/.jcode/scratch/section8-r17-round3-a0d3c12-20260908T1337Z/`.
- Review began at 13:35 UTC on 2026-09-08. Semantic production-source inspection ended at 13:44:53 UTC. No further product investigation was needed after finite coverage closed.
- Scope: **39 production files, three standalone test files, and 13 project contracts/governance/vision/prior-evidence documents**. Embedded Rust tests belong to their production files. The 40-production-file ceiling was respected.
- All product and project-document content came from exact Git objects or their private read-only snapshots. Binding machine governance and the explicitly permitted private prior report and retained gate receipts are separate authorized reads.
- `READSET.tsv` records side, classification, full commit, Git blob ID, full-file SHA-256, and exact repository-relative path for all 55 acquired Git blobs. Inspection used finite numbered excerpts and targeted searches. This is not a claim that every line of each large file received exhaustive semantic review.
- Readset SHA-256: `08fead654ebf21c26a57e40ff8c9b7f00bdd49a74d77f9c951101908d19d1866`.
- Exact inherited gate source comparison: **42/42 reviewed production and standalone test files match** their respective retained mobile/shared source manifests. `GATE-SOURCE-COMPARISON.tsv` SHA-256: `56a1c3f23f7db9e8b0c7ce0a6caa47e064b3928388666093c71703e6cd6d0230`.

The original R17 round-1 snapshot has SHA-256 `ac7cd8be899f75f42d1c7a737c424c1f0b62db81718b094f6750112a3fe14645`. The exact private round-2 report was verified against the requested SHA-256 `2a2e111c194bab960fbe6edd5fd7e585404b3876a33f8966102d852151174ac4` and copied as `PRIOR-ROUND2.md`. No original was edited. The later `aa9cedc` report object was unnecessary and was not read. No later product pin was substituted.

## Intent comparison and score rationale

The living-instrument vision requires a truthful beam with controls that explain rather than take over. The canonical plan's section 8 and `spec/EXPANSION.md:153-159` require current, inert, bounded observations. The reviewed implementation serves that intent through the real recorder/helper/native/service/Activity chain, not a second audio path.

The two round-2 source contradictions are resolved. Retirement health is published before asynchronous cleanup, and zero-read-only progress now expires using the named monotonic freshness bound. All five round-1 corrected paths remain present. The inherited gate verifies the reviewed source identities, including the actual Android/Kotlin/JNI compilation and production host fixtures. No remaining finite source schedule established a material defect during this review.

The **largest remaining outcome mismatch is accepted physical reality versus source and host evidence**. R17 still lacks the authorized Android silence, signal, clipping, denial, route-loss, root-failure, hold/resume, and rapid-source/picker observations required by the plan. Local original-format/raw-ingress and relay original-recorder-format getters remain unavailable, and diagnostics say so. These are retained limits, not fabricated failures inferred from a missing phone.

Eight is the bounded section-review judgment. It supports ending this corrective review loop under R15. It does not turn the mandatory device gate into minor polish or declare R17 delivered. Nine would overstate edge and Android integration evidence. Ten would overstate the untested scope.

| R15 dimension | Evidence and judgment |
| --- | --- |
| Intent fidelity | The expandable and no-signal entries remain within the instrument. Observation does not grant access, start a reader, probe root, connect, or change transport. Original and normalized representations stay distinct. |
| Functional accuracy | Actual Java/Kotlin/Rust and Activity/PlaybackService/CaptureService adapters were traced. Original five corrections and both round-2 corrections resolve their finite source schedules. Inherited production fixtures and source-manifest equality support main-path correctness. No behavioral test was run by this reviewer. |
| UX/accessibility | Signal actions have button roles and 48 dp minimum targets. Body text wraps and the surfaces scroll. Current nested settings removal disposes diagnostic content. Actual TalkBack, contrast, large-font, occlusion, gesture, and phone-layout outcomes remain unverified. |
| Lifecycle/performance/privacy | Owner/session/selection fencing, visible-only 500 ms UI sampling, no second consumed tap, bounded metadata, and nonblocking relay observation are present. No new history/export/endpoint appears in the reviewed paths. No physical latency, callback-time, allocation, battery, or thermal measurement was made. |

## Finding disposition and finite correction schedules

**Material source defects found this round: none.** No correction03 is requested from conjecture. The following traces explain why the original findings are closed at this pin. They are source-derived schedules, supported where stated by inherited fixture receipts, not newly executed reproductions.

### Correction02: R2-F1, health during capture retirement

Authority: `K/CaptureService.kt:401-466,514-543`, `K/SignalObservation.kt:13-20`, `K/ReaderStop.kt:13-35,69-90`, `K/BackgroundLifecyclePolicy.kt:83-95`, `T:78-139`.

1. Current owner A has a fresh nonzero or exact-zero finite input window and a published FLOWING status.
2. Explicit stop, permission loss, or reader failure enters `finishCapture`.
3. Line 419 publishes the owner-local `SignalRetirement` before `cleanedUp=true`, `running=false`, recorder removal, root-stop request, and cleanup-thread creation.
4. While cleanup is pending, `signalObservation` consults that receipt before the old FLOWING status and before root health can supply RUNNING.
5. Ordinary retirement yields STOPPING. Known FAILED or PERMISSION retains the observed reason. Contribution is false, and the presenter refuses current raw level/full-scale claims outside RUNNING.
6. A cleanup error yields CLEANUP_UNCONFIRMED. Retry replaces the completion future, not the original owner-local terminal reason. The snapshot rechecks retirement and completion identities as well as owner/status identity.
7. Successful source-release acknowledgement still waits for cleanup and destruction through the existing `SourceStopCompletion`. When no current service remains, an old FLOWING/STARTING publication cannot revive a live owner.

The fix is diagnostic-only. It does not broadcast IDLE early, release the audio ownership barrier early, or change reader-stop semantics. `SourceStopCompletion` intentionally keeps successful completion pending until owner destruction. Therefore the diagnostic may continue to say Stopping between cleanup and destruction, rather than claiming replacement is already safe.

The five new correction fixtures cover pending cleanup with fresh nonzero/silent windows, known failure/permission with timeout and retry, completed stop/replacement, zero-read age boundaries, and source wiring. **Limit:** these combine the production decision with futures and the actual presenter, plus source assertions. They do not instantiate a real Android CaptureService with a delayed physical AudioRecord. The actual adapter order was independently inspected here rather than inferred from those predicates alone.

### Correction02: R2-F2, zero-read-only freshness

Authority: `K/SignalPresentation.kt:22-40`, `K/SignalAggregate.kt:23-38,69-81`, `K/CaptureReadFence.kt:12-20`, `K/RootCaptureSession.kt:137-143`, `T:51-76,319-328,364-374`.

1. A current owner records one zero-length read at t=100 ms, with no positive sample receipt or finite channel measurement.
2. At t=100 and t=1600, the zero-read age is within the inclusive 1500 ms bound. The result says read-loop progress, not silence.
3. At t=1601 or t=10000, it says stale measurement and unavailable reader health. Backward, future, or negative timestamps do not create progress.
4. A fresh explicit progress timestamp restores recent progress wording without inventing PCM. Root's existing progress observation follows this same production branch.
5. No completed read still means starting/waiting. Positive finite zeros later mean measured silence. Positive nonzero samples later mean flow. Terminal lifecycle precedence remains above these cases.

The correction adds no reader-killing timeout, timer, root protocol, or hidden poller. The retained test specifically covers 0/1500/1501 ms, 10000 ms, backward time, negative explicit progress, fresh explicit progress, and absent reads for mic/standard kinds. Root uses the same reducer and its distinct protocol-receipt adapter was separately traced. It is not claimed that an on-device root stall was induced.

### Original round-1 corrections remain intact

| Original finding | Current corrected production schedule and attribution | Exact test/evidence boundary |
| --- | --- | --- |
| F1, historical local output becomes fresh | `K/SignalNativeObservation.kt:12-32` requires a matched positive native open, valid counter, same owner, no regression, and a monotonic 1..1500 ms comparison interval. A new delta carries the previous observation time as an age upper bound. Hidden long gaps, failed snapshots, and replacement establish a new baseline. | `T:263-271,290-317` covers hidden increment then pause, continued bounded progress, repeats, replacement, failed snapshots, and backward clock. `N/deck.rs:81-96` counts real positive pops. `N/deck_events.rs:51-66` returns zero pops while paused. This is correction01, unchanged by correction02. |
| F2, relay positive PCM16 rail lost | Actual accepted A bytes decode through `N/bridge_core.rs:184-194`, then `N/remote.rs:1477-1482` calls `observe_wire_pcm16`. `N/engine.rs:385-413` counts +32767/32768 and -1.0 without changing audible float samples. | Real decode-to-meter test `N/engine.rs:444-460` covers both rails, their neighbors, unequal channels, and zero. Native test counts are inherited. Original recorder negotiation remains unavailable. Correction01, not a new correction02 change. |
| F3, root traffic called recorder reads | `H/AudioPolicyMain.java:162-167` independently emits positive PCM and periodic progress. `K/RootCaptureSession.kt:27-29,120-143` counts those as received PCM/progress observations. `K/SignalPresentation.kt:72-76` carries root-specific units and labels. | `T:319-328` gives one PCM plus one progress receipt and expects two observations, not two AudioRecord calls. Raw mono frame count stays separate. Correction01. |
| F4, unowned tap and foreign extras | `K/MainActivity.kt:1061-1069,1277-1282` retains one normal scopeStats call and renders the diagnostic scope peak unavailable. `K/SignalPresentation.kt:54-58,87` rejects foreign/null-source extras. `K/SignalNativeObservation.kt:81-101` matches playback kind and local-open/relay-session identity before details. | `T:24-44,331-361` checks single acquisition, inert join, and unmatched extra rejection. Capture-controller ownership is independently checked by `K/PlaybackService.kt:1502-1507`. No owner/age metadata is invented for the old normal tap. Correction01. |
| F5, picker source identity lost | File/folder launch preserves `signalSelected` while advancing the existing cancellation revision. Null callbacks do not select LOCAL. Successful file load and folder result explicitly select LOCAL. `K/MainActivity.kt:189-213,1138-1145,1506-1508,2501-2503`. Typed LOCAL refuses an old capture primary during delayed handoff. | `T:331-349` is actual callback source-structure coverage, not SAF execution. Successful and cancelled Android picker workflows remain a device obligation. Correction01. |

Original round-1 and round-2 reports remain historical judgments of their respective pins. This disposition neither rewrites their findings nor changes their scores.

## Complete requirement-to-source/test map

Citation notation: `P` is `MOBILE-EXPANSION-PLAN.md`, especially section 8 and R15. `E` is `spec/EXPANSION.md:153-159,192-196`. `C` is `docs/plans/mobile-expansion/section-08-signal-check-contract.md`. `K/Foo.kt` is mobile `app/src/main/kotlin/dev/phosphor/mobil3/Foo.kt`. `U/Foo.kt` is its `ui/` directory. `N/foo.rs` is mobile `rust/src/foo.rs`. `J` is `app/src/main/java/dev/phosphor/mobil3/RootPcmNormalizer.java`. `H` is `root-helper/java/dev/phosphor/mobil3/root/AudioPolicyMain.java`. `S/path` is the shared pin. `T` is `app/src/test/kotlin/dev/phosphor/mobil3/SignalObservationTest.kt`.

Every section-8 bullet and requested review priority has a finite trace below. “Traced” means source evidence. Host fixtures are inherited. A source map is not complete runtime acceptance.

| Requirement | Current pinned source and fixture evidence | Outcome and honest boundary |
| --- | --- | --- |
| Expandable SIGNAL CHECK under Signal & Startup, quiet closed view | `U/Sheets.kt:1135-1140,1447-1452`, `U/SettingsSheetAdapter.kt:251-287`, `U/SignalCheckSheet.kt:23-27`, `U/ScopeUiState.kt:12-14` | Current section-9 nesting still reaches the same diagnostic content. Closing either expansion removes content rather than keeping a hidden diagnostic view. |
| No-signal/error entry without leaving instrument | `U/PhosphorScreen.kt:655-661,809-810` | The conditional compact action opens the same signal sheet. Actual phone reachability and placement are unexecuted. |
| Selected versus actual owner/backend | `K/MainActivity.kt:1237-1282`, `K/SignalPresentation.kt:54-65`, `K/CaptureService.kt:522-543` | Typed intent is distinct from service owner, root generation, native admission token, and playback identity. A mismatched source does not donate levels or extras. |
| Selection revision and same-kind replacement | `K/MainActivity.kt:1238-1239,1269-1293`, `K/MicController.kt:25-30,125-145`, capture snapshot rechecks, `T:190-197,351-361` | Before/after selection/publication checks and source-owned session fences reject old observations. Actual rapid Android replacement remains D10. |
| Picker cancel/success and late capture after LOCAL | Actual callbacks and correction01 row above | Cancellation retains the active diagnostic kind. Successful LOCAL selection does not revert to normal-face inference. No new rollback/source owner was introduced. |
| Standard capture observed format versus request | `K/CaptureService.kt:184-188,258-264,282-289`, `K/MicController.kt:223-232` | 48k stereo float is the request. The descriptor comes from the actual started recorder getters. Getter failure is unavailable, not copied from request. |
| Mic requested versus actual routed device | `K/MicController.kt:71-84,110-132,223-232`, `K/SignalPresentation.kt:67-70` | Requested system default and observed routed device ID/type/name are separate. Explicit accessories and route-loss recovery belong to R09. No default request is asserted to prove built-in hardware. |
| Raw root input and helper identity | `H:124-152`, `K/RootAudioProtocol.kt:48-61,102-119`, `K/RootCaptureSession.kt:80-94` | READY follows initialized/recording getter checks. UID/build/generation/mode and PCM format/count/epoch are validated. Only then is 16000 Hz mono PCM16 observed input disclosed. |
| Root normalization and duplicated mono | `J:3-22`, `K/RootEpoch.kt:46-58`, root input at `120-133`, `T:8-20,237-243` | N mono frames produce 3N normalized stereo frames, with identical L/R. Raw measurements precede normalization and epoch rejection. No original stereo, 96k/192k recorder negotiation, or recovered fidelity claim. |
| Backend ownership is not permission identity | `K/RootCaptureService.kt:3-12`, `K/RootCapturePolicy.kt:9-16`, capture `318-398` | Root shares retirement, not MediaProjection identity. Standard requires projection/record, root requires helper and neither projection nor record. `RootCaptureProductTest:45-54,144-172` checks the production policy and actual wiring. |
| Completed reads versus positive samples | `K/CaptureReadFence.kt:12-20`, `K/SignalAggregate.kt:23-38`, `T:51-76,364-374`, `CaptureReadFenceTest` | One existing read is observed after retirement fencing. Zero reads do not push samples and never become zero amplitude. Negative/throwing reads dispatch failure once. |
| Ingress versus normalized admission/rejection | Root ingress `120-133`, capture/mic observations before push, `N/engine.rs:154-174`, `K/SignalNativeObservation.kt:94-101` | Actual ingress can remain real while a stale visual epoch rejects normalized frames. Old-owner rejection does not update the new owner's counters. Embedded native owner/epoch fixtures cover this seam. |
| Render consumption versus producer meters | `N/render.rs:668-680`, `N/engine.rs:107-110,355-360`, `N/jni_glue.rs:900-908` | Consumption counts the existing sole finite normalized drain. Diagnostic metadata never calls that drain or `StereoWindow.take`. The scope peak is unavailable rather than falsely attributed. |
| Local normalized output versus original decoder input | `N/deck.rs:81-96,138-141,189-219`, `S/crates/phosphor-audio/src/playback.rs:299-323,527-539`, native local adapter | The existing callback counts positive popped normalized output. Shared decoding duplicates mono or selects front L/R before output. No public original-format or raw-ingress-meter getter exists in the inspected session seam. Both fields remain explicitly unavailable. |
| Local output freshness, EOF and output failure | `K/SignalNativeObservation.kt:12-32`, `K/PlaybackService.kt:1178-1201,1483-1511`, `K/PhosphorPlayer.kt:83-92,140-145`, `N/deck_events.rs:51-107`, local fixtures above | Counter baseline alone proves no flow. Existing sole event consumption publishes terminal state. Diagnostics do not consume the event queue again or infer flow from queue ownership. |
| Relay valid media versus protocol traffic | `N/bridge_core.rs:153-194`, `N/remote.rs:1455-1504`, `K/RemoteLinkTruth.kt:72-98` | Accepted nonempty complete A/G media is distinct from W/K and bytes/packet counts. Retired-session media cannot regain authority. Geometry points are not PCM samples. |
| Relay lifetime/session/link join | `N/remote.rs:208-244,1041-1046`, `K/PlaybackService.kt:1306-1311,1504-1511`, `K/SignalNativeObservation.kt:35-76` | Native session ID is separate from connect generation. Service classification needs matching revision/session and a 2500 ms age. Missing classification is unavailable/starting, not proof of link failure. |
| Relay raw wire level and clipping | Accepted A callsite, corrected native PCM16 aggregate, `K/SignalNativeObservation.kt:109-113`, native real parser fixture | Input levels are pre-phone-mute/zero-fill s16le wire observations. Both rails count. Original recorder negotiation remains unavailable. Representation rails are not analog clipping proof. |
| Relay RMS/peak freshness and transport provenance | `N/remote.rs:1582-1595`, `K/SignalNativeObservation.kt:112-119,127-142` | RMS and RMS peak have separate checked ages. Missing/stale values are unavailable. RMS peak is not sample clipping. M transport is metadata, not command acknowledgement. |
| Relay geometry, mute and zero-filled output | `N/remote.rs:471-517,1482-1511`, `N/engine.rs:528-546`, `K/SignalNativeObservation.kt:67-76,109-122`, `T:274-287,377-389` | Positive pops, finalized output, synthetic zero-fill, raw wire input, and post-policy visual counters are separate. Geometry-only input has no invented PCM meter. Real relay workflow remains D8. |
| Finite RMS/peak, silence, invalid samples, clipping | `K/SignalAggregate.kt:23-81`, `K/SignalPresentation.kt:22-41,77-85`, `N/engine.rs:389-424`, `T:141-187` | Bounded 500 ms tumbling windows, finite complete frames, per-channel RMS/peak/rails, invalid counts. Positive finite exact zero is digital silence. Empty or invalid-only observations yield unavailable level. |
| Monotonic freshness and failed snapshots | `K/SignalObservation.kt:82`, `K/SignalNativeObservation.kt:18-27,127-142`, correction02 age branch and tests | Checked age conversion does not subtract raw Kotlin and Rust clock origins. Counter regression, long intervals, invalid time, failed reads, and replacement invalidate progress claims. |
| Lifecycle and exact terminal precedence | Capture retirement trace above, `K/MicController.kt:166-194`, `K/SignalPresentation.kt:8-41`, `T:78-139,200-234` | Known failure/permission, stopping, cleanup uncertainty, disconnect/reconnect, and ended states precede old levels. Zero-only health expires without diagnosing reader death. Real platform callback scheduling remains untested. |
| Consent/startup versus measured silence | `K/MainActivity.kt:1262-1268,1282`, presenter `11-38` | Pending access and local failures use typed states, not fabricated zero. Opening the diagnostic content launches no permission flow. |
| HOLD/BLACK distinct from input and external pause | `K/SignalPresentation.kt:44-49,86`, `N/render.rs:615-646,668-680`, `K/PlaybackService.kt:601-631`, `T:223-234` | Held/black rendering returns before the sole live drain. Producer observations are independent. No-frame and pending presentation remain explicit. Input failure is not masked by display pause. |
| Requested transport versus observed controller | `K/PlaybackService.kt:612-619,1502-1507`, `K/CapturePauseObservation.kt:8-17`, `K/SignalNativeObservation.kt:81-91,116-119`, `K/RemotePlayer.kt:273-284` | Intent, chosen capture-controller observation, and relay metadata are separate. Optimistic transport is not treated as acknowledgement. |
| Per-input contribution and no R09 mixing claim | `K/SignalObservation.kt:49-65`, presenter contribution row, actual capture/mic/relay adapters | Current single installed input is explicitly labeled “not R09 mixing.” No composite owner or two-input runtime is claimed. Independent pure health fixtures do not establish installed mixing. |
| Plain-language recovery, inert opening | `U/SignalCheckSheet.kt:23-35,50-57`, `U/PhosphorScreen.kt:269-270,783-810`, Activity join, `T:24-44` | OPEN SOURCES changes the sheet. A later explicit existing action performs grant/retry/source selection. No second reader, root probe, source switch, dial, route or volume change occurs in the observation path. |
| Visible-only bounded sampling and nested collapse | `K/SignalPresentation.kt:97-105`, `K/MainActivity.kt:798,815,1021-1023,1067-1069,2389`, `U/SignalCheckSheet.kt:40-48`, `U/SettingsSheetAdapter.kt:284-287`, `U/PhosphorScreen.kt:227-230,783-842`, `T:246-257` | Existing tick, minimum 500 ms interval, foreground/focus/unobscured/PiP/presentation gates, clipped bounds, and disposal. No added hidden diagnostic poller. Actual platform visibility behavior remains D1/D10. |
| No extra history/export/network endpoint | Aggregates, JNI getter, UI state and actions above, `S/crates/phosphor-audio/src/ring.rs:16-55,85-117` | Latest metadata only is added by R17. The inherited ring already retains bounded audio for other engine behavior. Diagnostics neither copy that history nor create recording/export/endpoint behavior. No claim that the whole inherited engine retains zero audio. |
| Realtime-reader impact | `K/SignalAggregate.kt:7-20,80-83`, `N/remote.rs:231-234,471-480`, `N/engine.rs:528-546` | Small metadata publications and atomics. Relay try_lock skips and counts contention instead of parking the receiver behind UI. No added output-callback clock or JSON. This is source evidence, not a latency measurement. |
| Accessibility and readability | `U/SignalCheckSheet.kt:50-65`, `U/Type.kt:34-73`, `U/SettingsSheetAdapter.kt:264-285` | 48 dp actions, explicit roles, wrapping detail, heading/expanded semantics on enclosing section, and disposed collapsed content. Actual contrast, target reachability, TalkBack and large fonts remain unverified. |
| Exact fixture checks plus Android proof | `T`, `CaptureReadFenceTest`, `RootCaptureProductTest`, embedded native tests, retained gate and D1-D10 below | Inherited main-path host evidence is tied to the same reviewed source hashes. No target acceptance or installation is inferred. |

## Inherited gate evidence, not rerun

The exact coordinator gate is `2634988b58`. Its retained prefix is `dev/scratch/mobile-expansion-20260908T001819Z/signal-retirement-integration-1314`.

- The retained XML archive hashes to `714465cca4cf40552355ada390997fd6ccfb71712cd840a0fc91af86aea1cc09`.
- Reading its suite headers yields **716 JVM tests across 61 suites, zero failures, zero errors, zero skipped**. This is an independently checked count of inherited XML, not independent test execution.
- The retained `SignalObservationTest.xml` lists **27 passed cases**, including all five correction02 additions. `CaptureReadFenceTest.xml` lists four. `RootCaptureProductTest.xml` lists 13. These are subsets of 716, not additional tests.
- **126 native and three offscreen GPU cases**, dual debug APKs, Android/Kotlin/JNI compilation, lint, engine/boundary/source checks are inherited from the coordinator and pinned correction02 receipt. They were not run here.
- Retained result envelope: all six listed exits are zero, `installed:false`, `android_device_acceptance:false`.
- Mobile source manifest SHA-256: `472ffb9afc38d9b643cc973d2f0476a2087651be5537029033283117697c7b66`.
- Shared source manifest SHA-256: `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9`.
- The gate's recorded base precedes the review commit. Rather than equating commit messages with tested bytes, this review compared every reviewed production/test full-file hash against the retained manifests. All 42 match. Documents added after a gate do not become runtime evidence.
- `INHERITED.tsv` records exact retained source paths, extracted XML members, private copies and hashes. Its SHA-256 is `e3cff35fb7fbbd56730050fb55fccfa814c6be63615ba1f9f4c37551bb7879f4`.

No APK was inspected, installed, or executed by this worker. Prior correction01 gate totals remain historical and were not added to the current counts.

## Prioritized remaining acceptance work and limits

These are outcome obligations, not new material implementation defects. The coordinator owns them. They do not require an invented source correction or another critique round solely because a device was unavailable during a prohibited-execution review.

| Priority / row | Required observed outcome and smallest finite check |
| --- | --- |
| P1, D5/D6 retirement | On the authorized target and exact installed artifact, observe one standard explicit stop and one known reader/permission failure while cleanup is pending. Input health must retire immediately, levels must be unavailable, and replacement must still wait for real cleanup. Repeat with root failure. A controlled adapter latch is useful supplementary evidence, not physical callback acceptance. |
| P1, D2/D3 signal/silence/clipping | Compare real standard/mic descriptors and normal source face against the diagnostic view. Use finite safe fixtures for nonzero, exact digital zero, and representation full scale. Verify no opt-out/DRM or analog-clipping inference. |
| P1, D7 pause | Observe continuing input under display HOLD, BLACK/HOLD change, separate external pause, and return to live. Confirm current measurements do not come from the frozen display tap. |
| P1, D9/D10 local and picker ownership | Play local audio, hide diagnostics through an output change/pause, then reopen. No historical count becomes fresh. Exercise file/folder cancel and successful delayed handoff, same-kind replacement, and late old capture status. |
| P1, D8 relay | Exercise real A PCM, both PCM16 rails, silence, mute, underrun/zero-fill, geometry-only, reconnect and link loss. Compare input, output and link labels rather than treating any counter as universal flow. |
| P2, D1/D10 UI/lifecycle/accessibility | Open from settings and no-signal/error. Collapse enclosing Signal & Startup, navigate away, background, transfer presentation, and enter PiP. Verify no hidden diagnostic refresh. Check large fonts, TalkBack, contrast, close action and recovery reachability. |
| Separate R09, D4 | Actual desired accessory selection, disconnect recovery, service-owned mic and composite two-input health require the R09 owner. Do not fabricate both contributors in the current single-input implementation. |
| Explicit unavailable seams | Local original format/raw-ingress meter, relay original recorder negotiation and owned/aged diagnostic scope peak remain unavailable. If acceptance requires those numeric fields, add the smallest owner-tagged getters and fixture them, without adding a reader or duplicate tap. |

Original root stereo, SoundCloud coverage, acoustic/display latency, universal app capture, R09 mixing, release signing and distribution acceptance remain outside this review's claims. This reviewer queried no Android/device state and does not claim any phone was connected, absent, healthy, or failing.

**Blocked for device acceptance:** the review authorization prohibits every target/device operation. **Evidence:** no target was invoked, and the exact inherited result says installed=false and android_device_acceptance=false. **Best current result:** full finite immutable source critique, original-schedule closure, and source-matched inherited host evidence. **Next check:** coordinator-owned installation/identity verification and the first authorized retirement observation above, followed by the finite D1-D10 matrix. Do not rerun an unbounded search for speculative defects.

## Commands, verification and release

`COMMANDS.log` records exact Git-blob acquisitions. `acquire.sh`, `acquire-final.sh`, and `retain-evidence.sh` retain the executed private acquisition/comparison procedures. `COMMAND-EVIDENCE.md` records the inspection command forms, inherited XML-header count, boundaries, and non-executed/failed lookup events. `GOVERNANCE.sha256` identifies the permitted machine-governance reads. `HASHES.sha256` is the detached artifact seal, including this report's exact full-file hash.

Independent checks performed: immutable object resolution, numbered source inspection, cross-layer data/control-flow tracing, finite schedule derivation, full-file SHA-256 recording, exact prior-report hash match, 42/42 reviewed-source gate comparison, and inherited XML count/member inspection. No behavioral fixture was executed.

No builds, Gradle, Cargo, JNI/Android/GPU execution, Python, ADB, GUI, audio, network, service, target, Git mutation, product edit, installation, push, worker spawn, or approval ritual occurred. Writes were confined to this new private text evidence bundle. Machine and project governance remain read-only. No report from an earlier round was changed or rescored. Later fixes or observations require a separate addendum, not modification of this sealed original.

**Release:** finite review is complete. This worker releases ALL ownership and holds over mobile, shared, source, build, device, Git, files, services, and review work. No mutable-source hold or process was acquired. After sealing and the required swarm completion report, all reads and work stop. Root parrot retains sole authority to retain, commit, integrate, verify, and stop this worker. No round-4 worker or further research is requested.
