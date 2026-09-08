# SECTION 8 / R17 full signal-check outcome review, round 1

## Result and custody

**R15 score: 7/10. Disposition: correction required, then independent round 2.**

This is the independent source-only review of the approved signal-check outcome. The assigned reviewer route is GPT Astra/high, as authorized by the coordinator. The coordinator completed session routing. This reviewer spawned no workers and made no product changes.

- Mobile: `15e4072e86054af15f483f395d5adc8bcb09803f`.
- Shared: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Review began at 2026-09-08 12:09 UTC. Source investigation ended by 12:19 UTC, following the coordinator's stop-expansion instruction.
- Private bundle: `/home/ben/.jcode/scratch/section8-round1-independent-BoJ2kk/`.
- Canonical readset: `READSET.tsv`.
- Readset SHA-256: `3520cd7096c5389315eb4ecec2d3e6f6836d8e5c4a89ac30ece31843e2e102c5`.
- Readset scope: 34 production files, one test file, seven documents. The 40-production-file ceiling was respected.
- Each readset row records repository, pinned commit, path, Git blob, full-blob SHA-256, line count and inspection method.
- Citations below use immutable versions, never the mutable working trees. Inspection used numbered excerpts and targeted searches. Full requirement coverage does not mean exhaustive semantic review of every line of every file.
- The report's own hash is in detached `HASHES.sha256` and the completion report. An ordinary file cannot include its own full-file hash without circularity.

`K/Foo.kt` means mobile `app/src/main/kotlin/dev/phosphor/mobil3/Foo.kt`. `U/Foo.kt` means its `ui/` subdirectory. `N/foo.rs` means mobile `rust/src/foo.rs`. `H/AudioPolicyMain.java` means mobile `root-helper/java/dev/phosphor/mobil3/root/AudioPolicyMain.java`. `S/path` means a shared-repository path.

The five findings below are finite. No additional defect is implied by an unknown device result. The original report remains immutable. Subsequent corrections or evidence require separate receipts or addenda.

## Intent comparison and rating

The implementation substantially follows the intended quiet, private, read-only instrument. It adds real recorder observations, root raw-mono measurements, native admission/consumption counters, local output counters, relay ingress observations and a reachable diagnostic surface. It does not substitute reconstruction settings for input negotiation. It explicitly keeps missing local original/raw data and relay original recorder format unavailable.

The largest intent mismatch is temporal and owner truth at the actual adapters. A historical local counter increase can become a fresh sample receipt. Picker completion/cancellation can erase or strand selected-source identity. A consumed peak can appear without a matching input owner. These undermine the question R17 exists to answer: what is feeding this beam now?

Functional accuracy also has a real relay positive-rail defect and a root counter-unit defect. These are not hardware hypotheses. Their deterministic source schedules appear below.

UX/accessibility has a real expandable entry, no-signal/error entry, readable body text, scrolling and 48 dp signal actions. Actual focus traversal, large-font layout, contrast and phone interaction remain unverified. Lifecycle/performance/privacy have useful source evidence, but no independent runtime measurements. A score of 8 would overstate verified main paths while these defects and Android acceptance remain open. Seven follows the R15 anchor for substantial implementation with material gaps. It is not an arithmetic average or delivery acceptance.

## Finite findings and corrective schedules

### F1. Historical local output progress is relabeled as fresh input

**Priority: high. Requirements:** plan 184-186, 190-191. Observation contract 33, 39 and 55. Evidence confidence: high, source-derived, not executed.

`K/SignalNativeObservation.kt:7-19` retains `localOwner` and `localFrames` across diagnostic visibility gaps. Any later positive count delta sets `localProgress = now`. `K/MainActivity.kt:1066-1068` correctly suspends diagnostic joins while hidden, but does not invalidate that baseline. A paused READY player still maps to RUNNING at `K/PlaybackService.kt:1494-1500`.

Concrete schedule:

1. At t=100 ms, a visible local snapshot records open 1, popped count 100.
2. Hide signal check. The same open reaches count 200 and then pauses or stops receiving output.
3. Keep it without positive output for 30 seconds. Do not end or replace the open.
4. Reopen diagnostics at t=60000 ms. The snapshot still contains open 1, count 200.
5. The adapter assigns receipt time 60000. `K/SignalPresentation.kt:22-35` reports samples arriving with level unavailable, despite no recent output.

The native path supports this schedule: `N/deck.rs:81-96` counts actual pops. `N/deck_events.rs:51-59` returns zero while transport is paused. No ongoing positive pop is required for the later UI delta.

**Correction:** invalidate comparisons across unobserved intervals, including collapsed, unfocused, background and delayed ticks. A count increase proves progress within an observation interval, not exactly at the newest read time. Use conservative bounded interval semantics or an actual producer observation. Keep the callback free of added blocking work. Do not introduce a hidden poller.

**Next smallest check:** a production-adapter fixture with the schedule above, plus same-owner continuous reads, visibility gaps, counter regression, replacement and unknown snapshots. Existing tests at `SignalObservationTest.kt:182-190` cover short-interval deltas and reset cases, not the hidden interval.

### F2. Relay PCM16 positive full-scale samples become a false no-full-scale claim

**Priority: high. Requirements:** plan 184, 191. Observation contract 37. Observation audit A3 clipping terminology, line 77. Evidence confidence: high, source-derived, not executed.

`N/bridge_core.rs:189-194` accepts the actual s16le stereo wire format and divides each sample by 32768. `N/remote.rs:1477-1482` feeds the decoded floats into the signal aggregate. `N/engine.rs:399-405` counts a rail only when absolute float amplitude is at least 1.

Concrete schedule:

1. A current relay sends a valid A block containing +32767 in both channels.
2. The decoder produces 32767/32768, exactly 0.999969482421875, in each channel.
3. The aggregate records positive peaks but zero full-scale counts.
4. The fresh RUNNING presenter says `No full-scale samples in this measured window` at `K/SignalPresentation.kt:75-77`.

The same wire path maps -32768 to -1 and does count that rail. The asymmetry is already demonstrated by the existing parser fixture at `N/bridge_core.rs:633-637`, but the signal aggregate test uses float values of 1 and -1 at `N/engine.rs:436-449`. It does not join the real decoder to the new meter.

**Correction:** preserve known wire-PCM16 provenance through the relay observation adapter, or count both PCM16 rails before normalization. Label this as the received representation's boundary, not original recorder negotiation or analog clipping. Do not weaken honest unavailable original-format labels.

**Next smallest check:** feed actual accepted A bytes for positive-only, negative-only and below-rail stereo through the production parser and aggregate, then through the presenter. Both rails must warn independently.

### F3. Root received protocol frames are mislabeled as completed recorder reads

**Priority: medium. Requirements:** plan 184 and 187. Observation contract 21 and 35. Evidence confidence: high, source-derived, not executed.

The actual helper performs a nonblocking recorder read at `H/AudioPolicyMain.java:162`. A positive read emits PCM at line 166. The same loop independently emits a 250 ms progress frame at line 167. These are two protocol observations, not two AudioRecord reads.

`K/RootCaptureSession.kt:125` calls `signalMeter.pcm16` for PCM. Lines 140-141 call `signalMeter.progress` for progress. Both call `SignalAggregate.begin`, which increments `reads` at `K/SignalAggregate.kt:33`. The presenter labels that count `completed reads` at `K/SignalPresentation.kt:66-67`.

Concrete schedule: the first positive helper read at elapsed 0 emits one PCM frame and one progress frame because `nextProgress` starts at zero. Two aggregate calls display two completed reads although the helper performed one read. Conversely, intervening zero-length helper reads usually emit no progress frame, so the number is not a faithful read count in either direction.

**Correction:** label these as received PCM/progress observations with separate units, or expose a genuine completed-read counter if needed. The narrow label/counter separation can avoid a protocol change. Positive raw mono frames and normalized stereo frames must remain distinct.

**Next smallest check:** a fixture that delivers PCM plus progress from one helper iteration, followed by zero reads with and without progress publication. Assert exact counter units rather than treating observation count as recorder count.

### F4. Reused consumed peak has neither owner provenance nor measurement age

**Priority: high for owner attribution, medium for age presentation. Requirements:** plan 183-184, 187 and 191. Observation contract 13, 22, 33 and 39. Evidence confidence: high for missing provenance and attachment, not a device race reproduction.

`U/ScopeUiState.kt:161` defines `GridData.Reading` with levels only. `K/MainActivity.kt:1060-1065` acquires the existing destructive stats result before `refreshSignalCheck` starts its selection/publication baseline at lines 1237-1239. Lines 1278-1280 append the reused peak whenever the display is not paused. They carry neither an owner token nor an actual measurement age.

Concrete schedule:

1. Capture owns the scope and supplies a finite peak.
2. The user chooses a local document. `signalSelected` is LOCAL while the existing capture remains alive until explicit handoff.
3. During that transition, PlaybackService still describes capture. `SignalNativeObservation.local` returns null at line 12.
4. `SignalPresentation.present` accepts a null input at lines 48-49 and includes extra rows at line 80.
5. The selected-local view has no matched actual input, yet includes the old capture's consumed peak and potentially capture transport details.

The main primary label remains waiting for the current owner, and the contribution row is unavailable. This is not evidence that the primary reducer calls that local silence. The defect is that an unowned old numerical tap survives in the same view despite the contract requiring mismatched levels to be discarded. A source change between the stats read and later join also cannot be detected by the later-only baseline.

The native window has a bounded 500 ms expiry at `N/engine.rs:113-135`, but that bound is not an owner identifier or an exact exported measurement age. The renderer measures its sole consumed tap at `N/render.rs:668-680`. A UI read time must not be presented as the measurement time.

**Correction:** retain and validate owner/age provenance with the already-acquired tap, or render it unavailable in signal check. Do not acquire a second `scopeStats` result. Gate extra transport rows to the matched owner too. Preserve useful raw input observations during HOLD.

**Next smallest check:** exercise typed LOCAL selection while capture remains active, plus an owner boundary between stats acquisition and diagnostic join. No old peak or transport may be attached as a current selected-input fact. Keep the single-acquisition assertion.

### F5. Actual picker adapters strand or erase selected-source identity

**Priority: high. Requirements:** plan 183, 189 and 191. Observation contract 13, 17 and 47. Evidence confidence: high, source-derived, not an Android picker execution.

`K/MainActivity.kt:1507-1509` selects LOCAL before opening the file picker. Lines 2495-2497 do the same for a folder. The file callback at lines 189-192 does nothing for a null URI. The folder callback returns for null at lines 202-205. Neither restores the prior selection.

Cancellation schedule:

1. A capture or relay is genuinely playing.
2. Open the file or folder picker and cancel it.
3. No source was installed or stopped by the picker result, but `signalSelected` remains LOCAL.
4. `refreshSignalCheck` chooses the local adapter. It returns null because PlaybackService still owns the previous kind.
5. The diagnostic view indefinitely says selected local and waiting for a current owner, suppressing the still-active input's health until another selection changes it.

A successful folder result has the opposite error: line 208 calls untyped `selectSource()`, whose default at line 1286 is UNKNOWN. During local preparation, lines 1244-1249 can infer the old capture face instead of preserving the selected LOCAL intent. With a still-live old capture observation, lines 1252-1259 select that observation and the presenter can show its silence/contribution as the chosen source's current story.

This is relevant to the requested worked expectation. Keeping an explicit LOCAL selection rejects an old capture primary measurement. The folder callback defeats that protection by reverting to UI-face inference. No audio takeover needs to occur for the diagnostic error.

**Correction:** carry typed source intent through folder completion. Treat a picker as a pending request, and restore or retain the previous actual selection on cancellation without changing audio. Keep source-revision fencing and reject late old observations.

**Next smallest check:** actual adapter or extracted-owner fixtures for file cancel, folder cancel, folder success with delayed reader retirement, and late capture status after LOCAL selection. Assert Selected, Actual owner, Contribution and numeric rows, not only pure reducer labels.

## Complete requirement-to-source coverage

Status here means source evidence, not runtime acceptance. Every section-8 bullet and the explicit review priorities are mapped below.

| Criterion | Inspected production authority | Outcome / remaining boundary |
|---|---|---|
| Expandable under Signal & Startup, quiet closed screen | `U/Sheets.kt:1105-1109`, `U/SignalCheckSheet.kt:23-27` | Entry expands only diagnostic content. No reader/source action. |
| No-signal/error entry without leaving instrument | `U/PhosphorScreen.kt:654-660,809` | Conditional entry opens the same content in a sheet. Phone reachability remains D1. |
| Selected source versus actual owner/backend | `K/MainActivity.kt:1236-1275`, `K/CaptureService.kt:512-532`, `K/PlaybackService.kt:1483-1511` | Explicit kind, service owner and root session exist. F5 breaks picker lifecycle. F4 leaves unowned extras. |
| Same-kind replacement and retired callbacks | `K/MicController.kt:25-30,125-145`, `K/CaptureService.kt:282-299,343-357,512-532`, `K/RootCaptureSession.kt:119-142`, `N/remote.rs:208-227,1043`, `K/SignalNativeObservation.kt:13-19,29-41` | Recorder/session/open identities and native owner fencing are real. Not just UI label matching. Full delayed Android schedule remains D10. |
| Late capture after selecting local | `K/MainActivity.kt:1254-1258,1261,1286-1294`, `K/SignalPresentation.kt:8-10,48-51` | Typed LOCAL does not consume capture as the primary input or contribution. F4 leaks its tap. F5 can discard typed LOCAL during folder completion. Full worked outcome is not closed. |
| Requested versus actual mic route and format | `K/MicController.kt:71-84,110-132,223-232`, `K/CaptureService.kt:260-263,282-288`, `K/SignalPresentation.kt:58-64` | Actual AudioRecord getters, nullable failure, bounded route refresh. Requested route is honestly system default. Explicit accessory selection/service-owned mic remains R09. |
| Root raw format versus normalization | `H/AudioPolicyMain.java:124-152`, `K/RootCaptureSession.kt:80-92,125-132`, `K/RootEpoch.kt:46-58`, Java `RootPcmNormalizer.java:3-22` | Validated 16000 Hz mono PCM16 only after READY. Separate 48000 Hz float duplicated-mono transport. N input frames produce 3N normalized stereo frames. No original stereo claim. |
| Local original/raw and normalized output | `N/deck.rs:189-219,138-141`, `K/SignalNativeObservation.kt:20-23,86-89`, `S/crates/phosphor-audio/src/playback.rs:299-323,527-539` | Original format and raw decoder meter explicitly unavailable. Actual normalized output pop count is not a recorder meter. F1 affects freshness. |
| Relay original versus wire/phone representation | `N/bridge_core.rs:189-194`, `N/remote.rs:1477-1490`, `K/SignalNativeObservation.kt:61-67,91-106` | Original recorder format remains unavailable. Received PCM is before phone output policy. Known wire PCM16 rail provenance is lost in F2. |
| Bounded finite windows, zero versus unavailable | `K/SignalAggregate.kt:23-81`, `N/engine.rs:381-416`, `K/SignalPresentation.kt:22-35,70-78` | Tumbling 500 ms finite-frame aggregates, separate invalid counts, positive receipt and read progress. Invalid-only fresh windows have no level. Exact zero finite windows are silence. No unavailable numeric zero invented. |
| Peak/RMS and representation clipping | Same aggregate/presenter paths, root Java normalizer | Root PCM16 counts both rails. Float channels use finite RMS/peak. Relay positive PCM16 rail fails F2. Full-scale warning does not claim analog clipping. |
| Ingress versus normalized admission versus consumed meter | `K/RootCaptureSession.kt:125-132`, `N/engine.rs:295-360,465-483`, `N/render.rs:668-680`, `K/SignalNativeObservation.kt:77-85` | Separate counters and units. Old capture-owner rejection does not update new counters. Wrong visual epoch does not erase real raw ingress. F4 affects reused tap provenance. |
| Per-field freshness, clocks and units | `K/SignalObservation.kt:3,70`, `K/SignalNativeObservation.kt:54-60,94-114,126`, `K/SignalPresentation.kt:64,66-74`, `N/remote.rs:212-225` | Rust checked ages convert to Kotlin receipt-local instants. Relay RMS and RMS peak age independently. F1 falsifies local age, F3 mislabels root reads, F4 omits tap age. Raw measured age is used for freshness but not shown as a separate numeric row. |
| Reader/link state and failure/consent precedence | `K/SignalPresentation.kt:8-35`, `K/CaptureService.kt:517-530`, `K/PlaybackService.kt:1493-1511`, `K/SignalNativeObservation.kt:29-41` | Terminal FAILED precedes consent and measurements. Retirement/link states precede levels. Generic consent precedes disconnected/stopping in the pure reducer, so combined-state fixture coverage is not exhaustive. No additional reachable defect asserted here. |
| Display HOLD/BLACK versus source transport | `K/SignalPresentation.kt:38-44,79`, `U/ScopeUiState.kt:35-43`, `K/MainActivity.kt:1278-1283`, `N/render.rs:615-646`, `K/PlaybackService.kt:601-631` | Separate display labels and capture-controller transport observations. HOLD does not become input silence. No held frame and pending present are represented. D7 must prove actual hold/resume and source behavior. |
| Transport intent versus observation | `K/SignalNativeObservation.kt:72-75,100-103`, `K/PlaybackService.kt:612-619,1502-1510`, `K/CapturePauseObservation.kt:8-17` | Requested transport is separate. Relay metadata is not command acknowledgement. Capture observation is controller/owner-fenced. F4 requires extras to follow matched input ownership. |
| Relay geometry, mute and synthetic output zeros | `N/remote.rs:471-482,1482-1511`, `N/engine.rs:500-518`, `K/SignalNativeObservation.kt:59-67,91-106` | Geometry receipt has point units, not PCM samples. Raw relay meter is before mute/zero-fill. Popped/finalized/zero-filled counts are distinct. Actual geometry/output interaction remains D8. |
| Playback/mic independent health and actual contribution | `K/SignalInput` in `SignalObservation.kt:39-54`, capture/mic service adapters, `K/SignalPresentation.kt:55-57` | Current single installed input is explicitly not R09 mixing. No assertion that both inputs currently contribute. Contribution Boolean describes installed RUNNING input, not a measured per-input mixer share. R09 stays open. |
| Plain-language existing recovery, no unsolicited fixes | `U/SignalCheckSheet.kt:27,34,51-57`, `U/PhosphorScreen.kt:268-269,783-788`, `U/Sheets.kt:564-608` | OPEN SOURCES changes sheet only. Existing explicit source/grant actions require another user gesture. No automatic retry, root probe, route, volume, dial or recording is added by inspection. |
| Visible-only bounded refresh and hidden work | `K/SignalPresentation.kt:90-98`, `K/MainActivity.kt:797,814,1022,1066-1068,2383`, `U/SignalCheckSheet.kt:40-48`, `U/PhosphorScreen.kt:227,782-809` | Existing 500 ms tick, focus/resume/PiP/presentation checks, clipped layout bounds and disposal. No new poller. Actual occlusion, navigation and handoff scheduling remain D1/D10. |
| No second reader, stats take or event consumer | `K/MainActivity.kt:1060-1068,1236-1283`, `N/jni_glue.rs:901-908`, `N/engine.rs:355-360,510-518`, `N/deck.rs:138-141` | One existing scopeStats acquisition. New getter copies metadata. No extra ring drain or deck event poll. Source trace, not execution, establishes this. |
| Bounded realtime work and privacy | `K/SignalAggregate.kt`, `N/remote.rs:231-234,471-480`, `N/engine.rs:500-518`, UI/action paths above | Bounded metadata, nonblocking relay observation lock, no added callback clock/JSON and no new endpoint/export/history. Existing shared ring history at `S/.../ring.rs:16-55` is not new R17 retention and was not used for diagnostics. No claim that the entire inherited engine stores no transient audio. |
| Entry/recovery accessibility and readable presentation | `U/SignalCheckSheet.kt:50-65`, `U/Type.kt:34-73`, `U/Sheets.kt:342,474-477` | Signal actions have explicit button role and 48 dp minimum. Body wraps and scrolls. Shared close glyph lacks an explicit 48 dp minimum/semantic label in this inspected call. TalkBack traversal, large-font clipping, contrast and shared close-target behavior remain unverified rather than fabricated passes. |
| Test exact precedence and prove Android reality | `SignalObservationTest.kt:24-45,47-235`, native embedded tests, integration validation lines 17-20,40-47 | Read production fixtures and inherited receipts only. Five adapter gaps show why pure module tests are insufficient. All device acceptance remains open in this review. |

## Evidence accounting and Android limits

Independent work performed: immutable Git object lookup, exact snapshot copies, numbered source inspection, cross-layer control/data-flow tracing, and deterministic schedule derivation. Full-blob SHA-256 and Git blob identities seal the readset. No fixture, build or test was executed by this reviewer.

The inherited integration document records 653 JVM tests, 125 native tests, three host GPU tests, lint, Android compilation and dual APK build. It explicitly says no APK installation, Android callback, SAF, physical audio or phone UI acceptance occurred. Those figures are inherited compiler/test evidence, not independently rerun results, not device acceptance, and not proof of this review's runtime schedules.

The preparatory observation report uses an earlier mobile commit. Its contracts and H1-H10/D1-D10 checklist informed coverage. Every production finding above was re-grounded at the pinned review commit rather than borrowed from its older line numbers.

| Device row | Honest remaining uncertainty |
|---|---|
| D1 | No-source/permission/disconnected entry inertness, actual navigation and recovery taps on an authorized phone. |
| D2 | Actual standard recorder format and measured signal/digital silence matching the normal face. |
| D3 | Actual mic route, finite level, safe controlled full-scale input and observation getter behavior. |
| D4 | Accessory removal and desired-versus-routed proof, blocked on R09's actual route/owner support. |
| D5 | Android denial/revocation callback precedence and stale measurements. |
| D6 | Actual root helper failure/retirement, READY-backed 16k mono, progress versus PCM and normalized admission. |
| D7 | Input under HOLD, BLACK/HOLD transitions, separate transport pause and resume without stale history. |
| D8 | Relay real signal/silence, positive PCM rail, mute, underrun, geometry and link loss. |
| D9 | Local file output, hidden intervals, paused/ended/failed transport and true output freshness. |
| D10 | Rapid source changes, old callbacks, picker cancellation, full-app/HUD/PiP and hidden refresh cessation. |

No Android/device state was queried. The coordinator's receipt says ASUS was absent at its last enumeration and S25 should remain undisturbed. This reviewer did not reconfirm either condition. Original root stereo, SoundCloud coverage, acoustic/display latency, actual audibility, accessories and R09 mixing remain separate open requirements. Silence proves none of opt-out, DRM, privacy mute or universal capture failure.

## Verification, artifact notes and release

The canonical `READSET.tsv` contains only valid pinned blobs. A lookup for `U/GridData.kt` failed because GridData actually lives in `U/ScopeUiState.kt`. The failed lookup created an empty private scratch placeholder and a raw acquisition-log entry. Neither is claimed as a reviewed production blob. The lower-case `readset.tsv` is an unnormalized acquisition log, not the canonical readset.

A proposed final command to inspect additional root service/pause files was stopped by the tool's computed-write gate and never ran. After the coordinator requested no expansion, no additional production source was inspected. `U/Type.kt` had already been read from its immutable Git object and was copied only to seal the exact existing readset.

Governance read first: the binding workspace cabinet, then ben-context-standards. Complete-build and folder skills followed. Shared AGENTS was read from the pinned shared object. No governance file was edited. Python/STE execution was prohibited, so writing was reviewed manually.

No builds, tests, Gradle, target execution, JNI execution, GPU, ADB, GUI, audio, network, service or device operation occurred. No Git mutation, live source edit, worker spawn, installation, push or publication occurred. Scratch artifact creation and hashing are the only writes.

**Release:** this reviewer acquired no mutable-source hold. Source investigation is finished. All mobile/shared/source/build ownership is released to the coordinator. This original report and readset are to be sealed read-only before completion, with their exact hashes in the detached receipt and swarm report. After completion, this reviewer ceases work. The coordinator owns correction01, requirement-linked checks and the next independent full-section review.
