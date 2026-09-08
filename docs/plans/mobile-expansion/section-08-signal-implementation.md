# Section 08 SIGNAL CHECK implementation and handoff

## Pre-edit runtime specification

Written before runtime edits on 2026-09-08 against mobile `5b1be193e6fbcb0d3f779a8c0da5774a9a05efc6`. Shared remains `0ffd658d7f19e68180c2720e0500b23644619e90`, read-only. Canonical acceptance and the observation contract remain unchanged.

### Context and outcome

An expandable SIGNAL CHECK inside Signal & Startup explains the single current input. A compact no-signal/error entry opens the same detail. Reading never starts, grants, probes, routes, connects, changes volume or transport, or acquires a second sample reader. Existing source-picker actions remain the only recovery mutations and require a tap.

### Runtime components and publication

1. Pure `SignalObservation.kt` owns stable source/recorder identity, typed lifecycle, nullable format/route, per-channel window values, and explicit units. `SignalAggregate.kt` accumulates finite complete input frames at existing read boundaries. One current tumbling window lasts at most 500 ms, resets before admitting the next block beyond that boundary, and retains no audio. Publication copies only bounded metadata. Saturating lifetime counters distinguish completed reads, positive input frames, valid frames, and invalid samples. Zero-length reads update progress only. A positive invalid-only block cannot become measured silence. Float rails are absolute amplitude at least 1. PCM16 rails count both -32768 and +32767. Age freshness is 1500 ms for input observations, not a reader-death timeout.
2. Capture and mic each retain a recorder-lifetime identity independently from request cancellation and native admission owner. After successful start, actual AudioRecord getters supply rate, channels, encoding and routed-device descriptor. A bounded getter refresh at the existing read boundary observes route changes at most twice per second. Failure to read a descriptor produces unavailable, not requested defaults. Read telemetry executes after the existing running fence, before the existing push. Retirement makes late recorder publications inaccessible. Failure is retained as a typed terminal outcome without reviving a recorder.
3. Root retains typed validated READY format and native read owner. Its existing PCM batch supplies raw mono measurements before normalization. Its progress time is separate from positive PCM time. Diagnostic state uses the existing startup/progress/cleanup outcome and never issues helper control. Root input remains 16 kHz mono PCM16 only after READY. Normalized transport is explicitly 48 kHz float duplicated mono. Wrong visual epochs can leave real ingress without admitted frames.
4. Native capture admission and render consumption use small counters inside their already-held stereo-window lock. Counters carry the native owner and distinguish accepted stereo frames from epoch rejection. Old-owner rejections cannot update the new owner's counters. No ring length is flow. A non-consuming diagnostic getter reads these counters and never calls `StereoWindow.take`.
5. Local output gets a session-owned atomic positive-output counter in its existing callback. It measures popped normalized stereo frames, not original file format or decoder ingress. No new callback allocation, lock, JSON or clock syscall is added. A service snapshot exposes current native open identity, terminal player state, and transport intent. Original format and a raw local ingress meter remain unavailable with a named missing getter.
6. Relay gets session-owned bounded valid-media measurements before output policy and per-field relay-reported RMS timestamps from existing K parsing. A/G accepted counts carry units, unlike bytes/packets. Geometry is not PCM silence. Output counters are atomic and session-owned, distinguishing positive popped frames, finalized output frames and synthetic zero-fill. Existing classified link truth is retained by PlaybackService's existing poll. Diagnostic snapshots validate session identity before and after gathering. Remote metadata transport and requested transport stay separate. No protocol extension, event-queue consumer or relay connection is added.
7. Pure `SignalPresentation.kt` implements all 13 precedence rows and independent display labels. Identity mismatch discards owner measurements. Terminal failure, permission state, retirement and link failure win over old levels. Fresh positive receipt with unavailable levels stays samples arriving, not zero. Stale input does not prove a dead reader. Single-input contribution is explicit. Unsupported observations explain the missing seam.
8. Pure refresh policy permits a join only when expanded, foreground, focused, unobscured, non-PiP and presentation-visible, at intervals of at least 500 ms. Activity uses its existing 500 ms tick, not a new poller. Collapse, navigation, stop and destruction make refresh ineligible. Source revision is checked before/after the join. Already-acquired scopeStats may be displayed only as consumed scope-tap data. No second destructive meter acquisition is added.
9. UI uses existing palette and text components, flat sharp borders and at least 48 dp explicit actions. Settings expansion is local state. Recovery opens existing SOURCES, preserving source ownership until an explicit existing action is tapped. Diagnostics do not enter settings archives.

### Verification and protected boundaries

Actual pure Kotlin/JUnit compilation and locked/offline Rust host tests run only in a new private scratch bundle. Tests cover production aggregation, identity replacement, PCM16 rails, zero versus unavailable, format provenance, precedence, refresh visibility, and no action on observation. Native tests cover owner/epoch counters and window measurements. Source assertions supplement, but do not replace, Android/JNI compilation and runtime acceptance.

No Gradle, Android target, JNI target, GPU, device, ADB, GUI, audio, network, service, Git mutation, worker or shared-repository write is authorized. No instrument implementation or existing instrument Activity method changes. Root owns frozen integration, independent review and eventual authorized ASUS acceptance. R09 mixing and original root stereo remain unaccepted.

### Valid partial boundary

A missing observation renders unavailable with its exact cause. Source completion is not full R17 acceptance. All D1-D10 device rows remain coordinator-owned and unexecuted here. The final receipt will distinguish authored Android adapters from compiled host production cases, list exact files and hashes, and release source/build ownership no later than 11:53 UTC.

## Implemented detail and final host checks

The source includes B1-B6 adapters, the expanded settings view, compact instrument access, and one read-only join on the existing 500 ms UI tick. The settings heading is Signal & Startup. OPEN SOURCES is the explicit recovery action and does not select a source by itself.

The local native open is joined to the service's already-published open ID. A local counter baseline alone proves no recent flow. Only a positive delta for the same open reports output progress. Local original format and raw decoder ingress level remain unavailable because the existing decoder has no owner getter. The separately labeled normalized output counters and already-acquired consumed scope peak remain available.

Relay diagnostics use a new observation-only lifetime ID for each SessionShared, separate from the existing connection-request generation. Automatic reconnect therefore cannot inherit prior-session loudness or transport. Existing service-classified link observations carry that ID, source revision and Kotlin receipt age. Capture external-controller observations carry the current CaptureService owner as well as controller identity.

Relay receive observation uses try_lock and never waits for the UI. The getter copies fixed metadata then releases its lock before JSON formatting. If contention skips a diagnostic block, an explicit counter reports the gap. Relay input and geometry counters mean observed valid blocks, not guaranteed total network receipt. Existing bytes, packet counters, source policy, media admission and protocol remain unchanged. Raw relay level means received PCM before local mute and zero-fill, not an original desktop recorder getter.

Native output counters use session-owned atomics with an observation sequence. A mid-update snapshot returns unavailable. No new callback allocation, lock, JSON or clock call was added. Native ring-counter getters use try_lock and copy scalar metadata before serialization. The renderer increments consumed frames at its existing sole drain. Capture admission counters count normalized stereo frames and epoch rejection separately from recorder ingress.

Kotlin windows use elapsedRealtime. Rust exports checked ages in its own monotonic domain. The pure native adapter converts those ages to a Kotlin-local observation instant at receipt. It never subtracts raw Kotlin and Rust origins. Input freshness is 1500 ms. Relay classified-state freshness is 2500 ms because the existing service poll is 1 Hz. These labels do not add a reader-killing timeout.

### Requirement-to-check map

| Audit row | Actual check and result | Remaining limit |
| --- | --- | --- |
| H1 | Production Kotlin tests cover all 13 precedence rows, stale fallback and all five display labels. Passed. | Android rendering is authored, not compiled here. |
| H2 | Kotlin replacement, native relay lifetime-ID fixtures, local open replacement/regression, and Rust old-owner rejection passed. Source inspection confirms recorder/controller/publication fences. | Delayed Android callbacks need the coordinator's target gate and D10. |
| H3 | Production Kotlin finite stereo, exact zero, unequal channels, invalid pairs, odd tail, bounded window, RMS, peak and rails passed. Rust aggregate equivalents passed. | Actual recorder route/getter values require D2-D3. |
| H4 | Actual RootPcmNormalizer.java and RootEpoch.kt compile on the host. Tests cover N=1/160, 3N normalized stereo frames, identical L/R, both PCM16 rails and epoch rejection. Passed. | READY Android/helper adapter is authored, not target-compiled. Root original stereo is not claimed. |
| H5 | Four existing Rust capture-fence tests and the new ingress/admission fixture passed. Kotlin read-fence test rejects post-retirement observations. | Real blocked recorder behavior needs D6-D7. |
| H6 | Production input/display policy tests preserve failures under HOLD/BLACK. The raw aggregate sits before normalization. Source assertion finds one existing scopeStats acquisition. | Continuous real input under HOLD remains D7. |
| H7 | Eight existing pure Rust remote_media tests passed. Kotlin session/geometry/unknown-RMS/output-policy tests passed. | Native Android relay adapters are source-authored only. D8 is not run. |
| H8 | Production refresh policy verifies visibility, foreground, focus, obscuration, PiP and 500 ms admission. Source assertions reject start/grant/connect/transport calls inside the new UI/join. Passed. | Actual lifecycle and accessibility behavior remains D1/D10. |
| H9 | Single-stats source assertion, native non-consuming getter assertion, checked-age fixtures and source-revision join inspection passed. | Android thread/lifecycle integration is not a host runtime claim. |
| H10 | Production local delta/reset/EOF policy and independent failure fixtures passed. Service transport keeps intent separate from observed metadata/controller state. | Full EOF/output-loss/optimistic command workflow remains D9/D8. |

The private bundle contains the exact cached-compiler command, actual-source test logs, a 22-file source manifest, and a source-only tar archive. Rust host runs passed 4 new signal tests, 4 existing capture-fence tests, and 8 existing media-validity tests. Kotlin's final focused suite contains 17 tests. Earlier 14-test and 16-test passing runs are retained as intermediate receipts, not added to the final test count.

### Authored versus compiled

Host-compiled production: SignalObservation, SignalAggregate, SignalPresentation, SignalNativeObservation, CaptureReadFence, existing RemoteLinkTruth, existing RootEpoch/RootAudioProtocol, and existing Java RootPcmNormalizer. Rust host compilation covers engine observations and existing bridge media policy. Focused tests exercise real production modules, not copied substitutes.

Source-authored only: AudioRecord getters/lifecycle adapters, RootCaptureSession, CaptureService, MicController, PlaybackService, MainActivity, Compose UI, and Android-only Rust deck/remote/render/JNI adapters. No Gradle, Android/JNI target, GPU, device, ADB, GUI, audio, network or service operation ran. No shared-repository write or Git mutation occurred. Root separately changed and committed only its disjoint instrument paths and documents during this window.

### Remaining acceptance and source boundaries

- R17 is not accepted until the root's frozen Android/JNI build, independent review, and authorized ASUS scenarios pass. S25 is excluded and ASUS was absent during this work.
- Local original format and raw decoder ingress level have no existing getter. They render unavailable with that concrete cause. Output progress and consumed-tap peak are not substituted as original input measurements.
- Relay original recorder negotiation is unavailable. Relay-reported RMS and RMS peak keep independent ages and never become sample clipping proof.
- Default mic route is requested system default. The observed AudioRecord device is reported when available. Explicit accessory selection, service-owned mic, route recovery and two-input mixing remain R09.
- Numeric input health never proves protected-source opt-out, DRM, analog clipping, hardware negotiation or original root stereo.
- Portable settings, schemas, manifests, Gradle, packaging, helper protocols, source mutation semantics and instrument implementations were not changed by this writer.

The detached private HASHES receipt records the immutable archive and per-file manifest SHA-256. That manifest is the exact owned-path list. Live-source SHA-256 verification precedes the explicit source/build release. Root owns all integration and subsequent changes after release.

