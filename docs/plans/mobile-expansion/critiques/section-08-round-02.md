# SECTION 8 / R17 full signal-check outcome review, round 2

## Result and custody

**R15 score: 7/10. Disposition: two finite corrections required, then independent round 3.**

This is the full corrected SECTION8/R17 review, not a five-fix spot check. It consumes round 2 only. It does not rescore R16 or R13. The original R17 round-1 score of 7/10 and original reports remain unchanged.

- Reviewer: independent GPT Astra/high worker, Jcode session `session_tulip_1788872243388_716eb7b1165f8f25`. The coordinator supplied approved routing and completed ritual context. No worker was spawned here.
- Mobile repository: `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`.
- Mobile pin: `6355074c1d20158c29e749da9fd4c50e0d2bd2a7`.
- Shared repository: `/home/ben/Dev/ClaudeWorkspace/phosphor`.
- Shared pin: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Private bundle: `/home/ben/.jcode/scratch/section8-r17-round2-6355074-20260908T1258Z/`.
- Review began at 2026-09-08 12:57 UTC. Production-source scope closed at 13:05 UTC after finite findings. Remaining coverage inspection of already-acquired files ended at 13:06 UTC.
- Readset: **34 production files, one standalone test file, ten contracts/governance/receipt documents**. Native embedded tests are within their production files. The 45-production-file ceiling was respected.
- `READSET.tsv` records side, classification, full commit, Git blob ID, full-file SHA-256 and exact repository-relative path for every acquired blob.
- Readset SHA-256: `6125aeba8edbb745e266b93fad93962b1ef1955484da6bfe519454a3650ab417`.
- Original round-1 report snapshot SHA-256: `ac7cd8be899f75f42d1c7a737c424c1f0b62db81718b094f6750112a3fe14645`. This matches the inherited correction receipt's immutable original-report identity.

All product and project-document reads resolve to those Git objects or their private read-only copies. No live mutable UI/source file was read. Root's later settings changes and later gate are outside this report. Full requirement coverage means a finite trace for each requirement, not exhaustive semantic inspection of every line of every acquired file.

### Citation notation

`P` means mobile `MOBILE-EXPANSION-PLAN.md`. `C` means `docs/plans/mobile-expansion/section-08-signal-check-contract.md`. `I` means `section-08-signal-implementation.md` in that directory. `O` means `section-08-signal-observations.md`. `D` means `section-08-correction-01-design.md`. `V` means `section-07-08-correction-validation.md`.

`K/Foo.kt` means mobile `app/src/main/kotlin/dev/phosphor/mobil3/Foo.kt`. `U/Foo.kt` means its `ui/` subdirectory. `N/foo.rs` means mobile `rust/src/foo.rs`. `J/RootPcmNormalizer.java` means mobile `app/src/main/java/dev/phosphor/mobil3/RootPcmNormalizer.java`. `H/AudioPolicyMain.java` means mobile `root-helper/java/dev/phosphor/mobil3/root/AudioPolicyMain.java`. `T` means mobile `app/src/test/kotlin/dev/phosphor/mobil3/SignalObservationTest.kt`. `S/path` means that path in the shared pin.

## Intent comparison and R15 rating

The corrected implementation substantially serves the intended quiet, truthful instrument. It obtains bounded metadata from existing source owners, keeps original versus normalized formats distinct, and does not open another audio path. The five original findings are addressed in their actual adapters, not merely relabeled in a receipt.

The largest remaining mismatch is **current reader truth during retirement and before the first positive sample**. An owner that has already stopped admitting reads can still display its former flowing/silent primary status while cleanup waits. A single old zero-length read can claim current read-loop progress indefinitely. Both are source-demonstrable implementation defects, independent of device availability or missing original recorder-format getters.

The rating follows `P:294-309`, especially the 6–7 anchor at line 305: substantial implementation with material gaps. It is not an arithmetic average. The two defects below prevent the 8 anchor of verified main paths with minor polish remaining. Missing Android acceptance also remains explicit, not inferred from inherited compilation. A later source-only repair does not by itself prove complete R17 delivery.

| Dimension | Evidence and judgment |
|---|---|
| Intent fidelity | Expandable, inert, private observation is present. Current-truth claims still fail the two finite schedules below. |
| Functional accuracy | Real capture/mic/root ingress, local normalized output and relay pre-policy measurements are traced. Five prior adapter fixes are present. Retirement and zero-only freshness still need correction. |
| UX/accessibility | Same instrument provides settings and no-signal/error entry. Signal actions have button roles and 48 dp minimum targets. Content uses wrapping body text and scrollable surfaces. No phone layout, contrast, TalkBack, large-font or gesture behavior was observed. |
| Lifecycle/performance/privacy | Existing UI tick and visibility gates control the join. Source counters are bounded and no new audio history, endpoint or export is introduced by these paths. Source inspection is not a timing, allocation, battery or callback-latency measurement. |

## Finite defects

### R2-F1. Standard capture retirement retains a flowing primary diagnosis until asynchronous cleanup completes

**Priority: high. Requirements:** `P:183-186,191`, `C:43-48,55-57`. Confidence: high for the source schedule. No Android race or physical recorder failure was executed.

The reader failure path invokes `finishCapture` with a terminal error at `K/CaptureService.kt:291-299`. That method immediately sets `cleanedUp=true` and `running=false`, retires the recorder reference, and starts asynchronous cleanup at `418-445`. It retains the requested final `CaptureStatus` in the closure and publishes it only after cleanup at `447-461`.

During that interval, `signalObservation` reads the old `lastStatus`. Its `STATE_FLOWING -> RUNNING` case at `517-520` wins before the `cleanedUp` fallback at `522`. It reuses the prior finite window at `525-526`. It sets `contributing=false` at `530`, but the primary reducer does not use that flag to reject the RUNNING lifecycle at `K/SignalPresentation.kt:22-35`.

Concrete finite schedule:

1. Current standard capture owner A publishes `STATE_FLOWING`. Its finite nonzero window is measured at t=100 ms.
2. Before t=600 ms, explicit stop or a negative recorder read enters `finishCapture`. Owner A is retained during cleanup. `running=false` and `cleanedUp=true` are already facts.
3. Cleanup has not returned by the t=600 ms diagnostic tick. This is a permitted ordering because stop/join/release/cleanup run off-main. `K/ReaderStop.kt:20,30-35` includes a 2,000 ms join allowance and executes the supplied operations before returning.
4. `CaptureService.signalObservation` still yields A, RUNNING, the t=100 window and contribution=false.
5. The Activity's owner/session recheck at `K/MainActivity.kt:1268-1275` still matches A. The presenter returns `Signal flowing`. With a zero-valued finite window it returns `Measured silence · samples arriving` instead.
6. Only the later main-thread cleanup publication exposes the requested terminal status. Before then, the primary label contradicts the already-known retired reader.

This is not a claim that every stop lasts long enough to cross a UI tick. The defect is an admitted asynchronous ordering with no observation-state fence. A prompt cleanup can conceal it in ordinary testing. HOLD and BLACK do not fix it because they are independent display labels.

**Smallest correction:** publish source-owned diagnostic retirement/failure state at retirement entry, before waiting. Preserve known failure over a generic STOPPING state. Derive the snapshot lifecycle from that state before old FLOWING status. Keep the existing cleanup/replacement barrier and owner identity. Do not change audio handoff semantics or fake a successful stop.

**Smallest check:** a production adapter/owner test with an injected cleanup latch. Start from a fresh finite window, trigger explicit stop and reader failure separately, and inspect while cleanup remains pending. Assert STOPPING or the known failure, contribution=false, and no current flowing/silence/full-scale claim. Release cleanup, test timeout, and verify late completion cannot affect a replacement owner. Repeat the presenter with live, HOLD and BLACK display states. Existing `T:119-153` tests supplied terminal enums, not this actual delayed service transition.

### R2-F2. Zero-read-only observations claim current loop progress without checking their age

**Priority: medium. Requirements:** `P:184-186,191`, `C:35,39,53-55`. Confidence: high for the deterministic reducer result. No stalled physical reader was induced.

`K/SignalAggregate.kt:23-38` updates `lastReadAt` for every completed observation and leaves `lastPositiveAt` absent when count is zero. `K/CaptureReadFence.kt:15-19` deliberately publishes zero reads once without pushing samples. Root progress uses the same zero-count aggregate path at `K/RootCaptureSession.kt:137-143`.

`K/SignalPresentation.kt:26` returns `No samples observed · read loop progresses` whenever `positiveAt==null` and `w.lastReadAt!=null`. It does not call `signalAge` for that timestamp. The age-sensitive progress fallback at `27-29` is unreachable until a positive receipt exists.

Concrete finite schedule:

1. Current RUNNING owner A completes one zero-length read at t=100 ms. Its window has reads=1, lastReadAt=100, no positive receipt and no channels.
2. No later completed observation is published. No terminal owner state has yet arrived.
3. Present A at t=10,000 ms.
4. The current primary remains `No samples observed · read loop progresses`, although the displayed read age is 9,900 ms. The configured freshness threshold is only 1,500 ms.

For root, a similar zero-PCM/progress-only gap can falsely claim fresh progress between the 1,500 ms presentation threshold and the existing 4,500 ms helper progress deadline (`K/RootCaptureSession.kt:185`). No new reader-killing timeout is needed to fix the diagnostic statement.

**Smallest correction:** age-check read/progress observations in the no-positive branch too. A fresh zero read proves recent read-loop progress, not silence. An old or invalid timestamp should produce stale/unavailable reader health. Do not infer a dead reader, DRM or source opt-out from that interval.

**Smallest check:** extend the actual aggregate-to-presenter fixture at `T:51-58`. Present the same zero-read window at ages 0, 1,500 and 1,501 ms, with backward time and owner replacement. Renew zero reads and then publish positive zero/nonzero samples. Verify freshness wording, unavailable level, later measured silence/flow and terminal precedence. Existing zero-read tests inspect the observation only at its initial time.

These are the complete finite defect set from this review. Unsupported formats and unexecuted hardware outcomes below are not additional invented implementation failures.

## Original round-1 findings: corrected-source disposition

The round-1 report remains immutable. “Addressed” below means the correction is present at this pin and its finite source path resolves the original schedule. It does not mean this reviewer ran its fixture or accepted Android behavior.

| Original finding | Corrected production path | Fixture evidence inspected and remaining limit |
|---|---|---|
| F1: historical local output becomes fresh | `K/SignalNativeObservation.kt:12-32` requires same owner, valid count, monotonic interval of 1..1500 ms and no regression. A positive delta records the previous observation time, explicitly an upper age bound. Long gaps establish a new baseline. `K/SignalPresentation.kt:69-70` labels the bound. | `T:182-190,209-235` covers hidden pause, repeat count, fresh progress, failed snapshot, replacement, regression and backward clock. Not executed here. Actual local producer capture time remains unavailable. |
| F2: relay +32767 rail lost | `N/bridge_core.rs:189-194` decodes actual s16le. The accepted A branch calls `observe_wire_pcm16` at `N/remote.rs:1477-1482`. `N/engine.rs:385-413` uses positive 32767/32768 and negative -1.0 thresholds. Audible floats stay unchanged. | Real SessionMedia decode-to-meter fixture at `N/engine.rs:444-460` includes both rails, both neighbors, channel separation and zeros. `K/SignalNativeObservation.kt:109` explains wire provenance, not original recorder negotiation. |
| F3: root protocol traffic called completed reads | `K/RootCaptureSession.kt:27-29` sets received PCM/progress observation units. `K/SignalPresentation.kt:66-70` carries those units and the root-specific progress label. Raw mono frame count remains separate. | `T:238-247` models one PCM plus one progress observation. Helper `H/AudioPolicyMain.java:162-167` still independently publishes those two messages. This is not a claimed true AudioRecord-call counter. |
| F4: unowned peak/foreign extras | `K/MainActivity.kt:1276-1278` supplies unavailable scope peak instead of an unowned numeric tap. `K/SignalPresentation.kt:81` requires an accepted non-null source for extras. `K/SignalNativeObservation.kt:81-91` gates playback kind and local-open/relay-session identity. | `T:250-280` checks single stats acquisition and rejects unmatched extras. `K/MainActivity.kt:1060-1065` remains the sole normal stats read. No second tap added. Owner/measurement-age support for a diagnostic scope peak remains deliberately absent. |
| F5: picker cancellation/completion corrupts selection | `K/MainActivity.kt:1505-1507,2493-2495` preserves the current diagnostic kind while advancing the existing cancellation/revision path. Null callbacks at `189-192,202-205` do not select LOCAL. Successful file load at `1137-1144` and folder result at `208-213` explicitly select LOCAL. | `T:250-261` is source-structure coverage, not Android SAF lifecycle execution. Typed LOCAL prevents old capture primary data from being accepted by the reducer during handoff. |

## Full requirement-to-source coverage

Every section-8 bullet (`P:182-191`) and the requested cross-layer priorities are mapped. “Traced” is source evidence only. Unavailable or unexecuted rows are not passed acceptance tests.

| Requirement / boundary | Pinned authority | Source outcome and remaining boundary |
|---|---|---|
| Expandable entry under Signal & Startup | `U/Sheets.kt:1105-1109`, `U/SignalCheckSheet.kt:23-27` | Expansion changes diagnostic state only. Regular settings controls are separate. |
| No-signal/error access without leaving instrument | `U/PhosphorScreen.kt:654-660,809` | Conditional instrument entry opens the same observation content. Actual reachability/layout is unexecuted. |
| Selected source versus active backend | `K/MainActivity.kt:1236-1281`, `K/SignalPresentation.kt:48-60` | Explicit selection kind and current owner/session are separate. Mismatched primary source is rejected. Source-changing/null input does not inherit numeric extras. |
| Same-kind owner replacement and stale callbacks | `K/CaptureService.kt:282-299,512-532`, `K/MicController.kt:25-30,125-145`, `K/MainActivity.kt:1268-1275` | Recorder identity, service identity and session recheck exist. Raw meter session is matched independently. R2-F1 affects retirement lifecycle, not the existence of these fences. |
| Picker selection/cancel and old capture after LOCAL | `K/MainActivity.kt:189-213,1137-1144,1254-1258,1284-1292,1505-1507,2493-2495` | Corrected typed selection remains through successful callbacks. Cancel does not replace active diagnosis. `K/SignalPresentation.kt:8-10,48-51,81` rejects foreign input and extras. Actual delayed callbacks and normal-face agreement still need D10. |
| Standard capture actual read and admission | `K/CaptureService.kt:257-299`, `K/CaptureReadFence.kt:12-20`, `N/engine.rs:154-173` | One existing AudioRecord read. Bounded raw aggregate after running/recorder fence, before existing native push. Native owner/epoch admission is separate from ingress. |
| Mic actual read and route | `K/MicController.kt:71-84,110-145,223-232`, `K/SignalPresentation.kt:61-64` | Requested system default is distinct from recorder-routed device getters. Format/route getter failure is unavailable, not copied from request. Actual route and accessory support remain unverified/R09. |
| Negotiated input versus reconstruction/normalized transport | `K/SignalObservation.kt:13-22,50`, `K/MicController.kt:223-232`, `K/SignalPresentation.kt:58-59` | Recorder getters supply observed rate/channels/encoding. Configured 48k pipeline is labeled separately. No 48/96/192k reconstruction selection is used as input negotiation. |
| Root READY identity and raw format | `H/AudioPolicyMain.java:143-152`, `K/RootAudioProtocol.kt:48-61,102-114`, `K/RootCaptureSession.kt:80-94` | Actual initialized/recording 16k mono PCM16 getters back validated READY. UID/build/generation/mode and PCM count/format are checked. No original stereo claim. |
| Root raw ingress versus normalization and admission | `K/RootCaptureSession.kt:120-143`, `K/RootEpoch.kt:46-58`, `J/RootPcmNormalizer.java:7-22`, `N/engine.rs:164-173` | Raw mono aggregate precedes conversion. N mono frames yield 3N normalized stereo frames. Both channels duplicate mono. Wrong visual epoch may reject normalized frames without erasing real ingress. |
| Root progress versus reader calls | `H/AudioPolicyMain.java:154-169`, `K/RootCaptureSession.kt:27-29,137-143,183-195` | PCM/progress receipt units are now correct. Root owner timeout/failure is independent of silence. R2-F2 affects zero-PCM progress freshness before terminal state. |
| Local output versus original decoder input | `N/deck.rs:81-96,138-141,189-219`, `N/deck_events.rs:51-77`, `S/crates/phosphor-audio/src/playback.rs:299-323,527-539` | Session-owned counter measures positive popped normalized stereo output. Paused callbacks return zero pops. Shared decoder normalizes channels, but its public session exposes no original format/raw-ingress meter getter. Honest unavailable labels remain. |
| Local owner/terminal join | `K/SignalNativeObservation.kt:12-32`, `K/PlaybackService.kt:1178-1195,1483-1511`, `K/PhosphorPlayer.kt:83-86,140,191-196` | Native open matches service publication. Existing sole event path publishes EOF/output failure. Diagnostic getter does not consume those events. No output progress is inferred from queue ownership. |
| Relay accepted media/read boundary | `N/bridge_core.rs:168-194`, `N/remote.rs:1455-1504`, `K/RemoteLinkTruth.kt:72-98` | Valid A/G receipt is distinct from W/K traffic and process packet/byte counters. Retired media is rejected. Existing link classifier is reused, not invented from UI cadence. |
| Relay session/join coherence | `N/remote.rs:208-227,239-243,1043-1046`, `K/PlaybackService.kt:1306-1311,1504-1511`, `K/SignalNativeObservation.kt:35-76` | Native session has its own lifetime ID, checked before/after snapshot. Classified link needs matching session/revision and age. Reconnect does not inherit the prior session's meter. |
| Relay wire level, RMS and clipping provenance | `N/remote.rs:1482,1582-1595`, `N/engine.rs:385-424`, `K/SignalNativeObservation.kt:109-113,127-130` | Known wire-PCM16 rails are counted correctly. Relay-reported RMS and RMS peak have independent ages, with stale/missing values unavailable. RMS peak is not sample clipping proof. Original recorder format stays unavailable. |
| Relay geometry, mute and synthetic zeros | `N/remote.rs:471-508,1483-1511`, `N/engine.rs:528-546`, `K/SignalNativeObservation.kt:67-76,109-122` | Geometry points are not PCM samples. Raw received PCM is before phone mute/zero-fill. Popped/finalized/zero-filled output counters are separate. Actual geometry/audio policy behavior remains D8. |
| Finite measured level and exact zero | `K/SignalAggregate.kt:23-81`, `N/engine.rs:389-424`, `K/SignalPresentation.kt:22-35,71-79` | Tumbling 500 ms finite-frame windows, separate invalid samples, per-channel RMS/peak/rails. Positive exact-zero finite frames mean digital silence. Zero read or invalid-only window is not silence. R2-F2 concerns stale loop wording, not fabricated amplitude. |
| Selection/owner/age and snapshot failure | `K/SignalObservation.kt:72`, `K/SignalNativeObservation.kt:18-27,127-142`, `K/MainActivity.kt:1237-1275` | Monotonic ages, checked cross-runtime age conversion and local baseline invalidation exist. Snapshot failure becomes unavailable rather than relabeling the last success. Two finite lifecycle/freshness exceptions are reported above. |
| Reader/link terminal precedence | `K/SignalPresentation.kt:8-29`, `K/PlaybackService.kt:1493-1510`, `K/CaptureService.kt:400-465,517-530` | Pure FAILED/PERMISSION/STOPPING/ENDED/disconnected/stalled precedence exists. Actual capture retirement does not supply the right state soon enough in R2-F1. |
| Consent/startup versus healthy input | `K/MainActivity.kt:1261-1267,1281`, `K/SignalPresentation.kt:11-26` | Consent/startup does not become zero. Opening the view invokes no permission launcher. Real denial/revocation remains D5. |
| HOLD/BLACK versus input and external transport | `K/SignalPresentation.kt:38-44,80`, `N/render.rs:615-646,668-680`, `K/PlaybackService.kt:601-631,1502-1507` | Paused render returns before the sole live ring drain. Input observations continue at owners. Chosen capture-controller transport is tagged separately. Display pause does not become source silence or hide a correctly supplied failure. |
| Requested transport versus observed transport | `K/SignalNativeObservation.kt:81-91,116-119`, `K/CapturePauseObservation.kt:8-17`, `K/RemotePlayer.kt:273-283` | Requested Play/Pause is not command acknowledgement. Relay metadata and chosen capture-controller observation are labeled separately. Foreign playback extras are gated. |
| Per-input contribution and future mixing | `K/SignalObservation.kt:39-56`, `K/SignalPresentation.kt:57`, capture/mic/relay adapters above | Current single installed input is explicit, not R09 mixing. Fixtures can present separate healthy/failed inputs (`T:305-308`), but no current composite UI/owner or two-input runtime is implemented. R09 remains open. |
| Existing recovery, no action on diagnostic open | `U/SignalCheckSheet.kt:24-35,51-57`, `U/PhosphorScreen.kt:268-269,782-809`, `K/MainActivity.kt:1236-1281` | OPEN SOURCES changes sheet only. Grant/retry/source mutation uses later explicit existing controls. No root probe, new reader, consent, volume/routing change or relay dial occurs in the join/content path. |
| Visible-only bounded refresh | `K/SignalPresentation.kt:90-99`, `K/MainActivity.kt:797,814,1020-1022,1066-1068`, `U/SignalCheckSheet.kt:40-48`, `U/PhosphorScreen.kt:227-230,782-839` | Existing tick, at least 500 ms interval, foreground/focus/presentation/PiP gates, layout visibility and disposal. Modal navigation replaces content. No new hidden diagnostic poller. Actual clipping/occlusion/lifecycle behavior remains D1/D10. |
| No second audio tap/read/event consumer | `K/MainActivity.kt:1060-1068,1236-1281`, `N/jni_glue.rs:900-908`, `N/engine.rs:107-110,355-360`, `N/deck.rs:138-141` | One normal scopeStats acquisition. Diagnostics copy scalar metadata and never call StereoWindow.take or drain the ring. Local event consumption remains the existing service watcher. |
| Bounded realtime work and privacy | `K/SignalAggregate.kt:7-20,80-83`, `N/remote.rs:231-234,471-480`, `N/engine.rs:528-546`, `U/ScopeUiState.kt:12-14` | Latest bounded aggregates and saturating counters. Relay observation uses try_lock, output telemetry uses atomics without new callback clock/JSON. Diagnostic state is transient. No new endpoint or portable/private export in these paths. No independent latency measurement. |
| Existing ring history is not diagnostic history | `S/crates/phosphor-audio/src/ring.rs:16-55,85-117`, native getter paths above | Existing ring retains audio for other engine behavior. R17 does not copy history or add an audio recording buffer. This is not a claim that the whole inherited engine retains no samples. |
| Fixture tests plus actual Android reality | `T:8-45,51-308`, native embedded tests, `V:27-52`, `P:191,334` | Production fixtures and inherited receipts were read, not run. Coverage gaps for both new schedules are explicit. All D1-D10 target scenarios remain unexecuted by this reviewer. |

## Uncertainty and smallest acceptance checks

### Honest unavailable capabilities, not false implementation successes

1. **Local original format and raw decoder ingress level:** no current public getter in the inspected shared session/native deck seam. Normalized output counters do not close that gap. The UI correctly says unavailable. Add the smallest owner-tagged decoder observation only under separate implementation authority.
2. **Relay original recorder negotiation:** received s16le wire provenance is known, but no original desktop recorder format is published. Both wire rails are now correct without pretending to know original capture format.
3. **Buffered source age:** Kotlin root timestamps the app's received PCM/progress. Relay timestamps validated receive-side media. Local age is a conservative interval bound on observed output-count growth. None proves original upstream capture age or latency through buffering. This is a provenance limit, not evidence of an R13 source-age repair or failure.
4. **Root stereo:** validated product input remains 16 kHz mono with 48 kHz duplicated-mono scope transport. Original stereo, audibility, protected-source coverage and SoundCloud behavior remain separate unresolved acceptance. No success or failure is inferred here.
5. **R09 mixing/accessories:** current diagnostics honestly show a single installed input. Actual two-input contribution and independent mixed-input health require the real composite owner and routed accessory work. A pure independent-input fixture does not establish runtime mixing.
6. **Observed-route getters:** source shows actual AudioRecord getters and nullable failures. It cannot prove what Android reported on any route or that a descriptor refresh met its timing target.

### Coordinator-owned device rows

| Row | Smallest remaining real observation |
|---|---|
| D1 | Open/close no-source, consent-needed and disconnected diagnostics. Observe zero unsolicited starts/prompts/dials, then explicit recovery. |
| D2 | Current standard owner with controlled nonzero and digital-zero input. Compare negotiated descriptor, ingress and normal source face. |
| D3 | Current mic route and safe controlled level/full-scale fixture. Do not use unsafe acoustic output to provoke clipping. |
| D4 | Desired accessory versus actual route, including removal, after R09 owner/routing support exists. |
| D5 | Denial/revocation and delayed cleanup. Known failure must immediately beat recent samples, including R2-F1's pending-cleanup interval. |
| D6 | Authorized root READY, PCM/progress, bounded failure and cleanup under the exact installed artifact. Keep mono and normalization labels distinct. |
| D7 | HOLD/BLACK/resume with actual input continuing, then independently paused transport. No inferred input silence from display state. |
| D8 | Relay signal/digital silence, positive and negative wire rails, mute, underrun, geometry, stale RMS and link loss. |
| D9 | Local same-open output, long hidden interval, pause/resume, EOF and output error. No historical increment relabeled as immediate producer time. |
| D10 | Picker cancel/success, delayed old callbacks, same-kind replacements, navigation, HUD/PiP, focus loss and offscreen/hidden refresh cessation. |

The current assignment prohibits those operations. The inherited receipt says no ASUS was available at its earlier enumeration and S25 was to remain undisturbed. This reviewer queried neither device nor host Android state. Device availability was not independently reconfirmed.

## Evidence accounting, runner provenance and sealing

Independent validation performed: read-only Git-object lookup, private immutable snapshot acquisition, numbered excerpts/targeted scans, cross-layer source tracing, deterministic schedule derivation, and shell SHA-256/blob verification. **No builds or tests were run. No host, JNI, Android, native or GPU functional success is claimed.**

`V:27-33` records inherited 675 JVM tests, 126 native host tests, three host GPU tests, Android/Kotlin/JNI compilation, lint, dual APK and source-boundary gates. `V:50-52` explicitly distinguishes that working-source gate from a final exact reviewed freeze and installation. These are inherited records, not this reviewer's runtime evidence. Their runner and APK/result hashes are documented in the pinned receipt. The later root gate announced at 13:06 UTC is outside this report.

The private acquisition runner is `read-object.sh`, SHA-256 `ffc6532f92e1a8e14744e22e12e74fb0a48d66bc533abd17fc849a5e60c16cad`. It is a Bash script retained verbatim. It uses only pinned `git rev-parse` and `git cat-file blob`, writes new private copies and metadata, hashes them with `sha256sum`, and makes each copy read-only. Tool paths observed for custody are `/usr/bin/bash`, `/usr/bin/git` and `/usr/bin/sha256sum`. It is not a test runner or product program.

One attempted lookup, `mobile:rust/src/remote_media.rs`, failed because that file is absent at the pin. The relevant SessionMedia implementation is in the already-acquired `N/bridge_core.rs`. The failed lookup produced no blob/readset row and is not counted as a production file. A tool computed-write guard initially refused private runner creation before execution. The reissued creation explicitly justified the user-requested private report/readset. No product or original artifact was overwritten.

`verify-seal.sh` records the shell-only verification procedure. `VERIFICATION.txt` records its observed result. It recomputes each snapshot SHA-256 and non-writing Git blob identity, compares the pinned path's blob identity, checks the production-file ceiling, and seals the new report/readset/snapshots. `HASHES.sha256` is the detached artifact checksum receipt. The report hash belongs there and in the completion report, not inside this file's self-referential content.

No Git mutation, product edit, Gradle invocation, build/test run, Python, network, ADB, GUI, audio, service, device operation or worker spawn occurred. Private artifact creation and shell hashing were the only writes. Governance was read through the binding cabinet and context/coding/folder skills. Python-based prose lint was prohibited, so prose was checked manually.

**Ownership release:** no live mutable-source ownership, repository lock, device or build ownership was acquired. All immutable source investigation is complete. On sealed delivery this reviewer releases every read/source claim over mobile, shared, Kotlin, native and UI, including the root settings writer's paths. Only the private report bundle is owned. No further source work continues after completion reporting. Root owns separate corrections, runtime checks and any authorized subsequent round.
