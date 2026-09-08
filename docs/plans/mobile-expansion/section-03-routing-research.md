# R09 microphone routing and service/mixer handoff

Research only. Prepared 2026-09-08 by zebra for coordinator parrot. Not implementation, physical acceptance, or R15 critique.

**Coordinator update, 04:25 UTC:** Ben now requires original stereo and SoundCloud smoke acceptance. Fuller-fidelity R01 work takes priority before R09 implementation. The 16kHz duplicated-mono statements below describe the earlier changing Section2 contract, not the revised acceptance target. Re-read the accepted R01 format and timing contract before implementing R09. This research performed no SoundCloud or stereo-device acceptance. Remaining small questions are the accepted R01 PCM format/channel provenance, capture-clock availability, and which physical microphone accessories can be tested. No further source search was performed.

## Scope and evidence

Tracked code was read only with `git show 089382d:<path>`. The resolved baseline is `089382d1f1d1fc264c7b9f7077f8803ef024b682`. Paths below are relative to `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/` unless stated otherwise.

The normative requirement is `MOBILE-EXPANSION-PLAN.md:113–123`, with defaults and import boundaries at `66–83`. The separately read `docs/plans/mobile-expansion/section-02-root-product.md` is a **changing contract**, observed around 04:12 UTC. It is not accepted code. Reconcile these seams against the committed R01 result before editing.

Read ben-context-standards first, machine governance, docs/AGENTS, source-vision and handoff planning skills. No source edits, Git mutation, builds, Python, workers, device connections, GUI, settings, grants, or audio operations occurred. Public-source evidence and this report reside only under `/home/ben/.jcode/scratch/r09-research-089382d/`.

## Verified Android facts

### Enumeration, negotiation, and actual route

1. `AudioManager.getDevices(GET_DEVICES_INPUTS)` lists framework-exposed connected inputs. Register `AudioDeviceCallback`, then re-enumerate on additions/removals. Filter by `isSource`, not product-name text. Relevant types are `TYPE_BUILTIN_MIC`, `TYPE_WIRED_HEADSET`, `TYPE_USB_DEVICE`, `TYPE_USB_ACCESSORY`, `TYPE_USB_HEADSET`, `TYPE_BLUETOOTH_SCO`, and API31+ `TYPE_BLE_HEADSET`. Wired headphones and ordinary A2DP playback endpoints do not establish headset-microphone availability. AOSP also represents A2DP source devices, so do not confuse that distinct role with an ordinary headset mic. [P1, P3]
2. Device sample-rate, channel-mask/count, and encoding arrays describe capabilities. Empty arrays mean arbitrary supported values, not no support. Unknown encoding entries should be ignored. These arrays are not proof that a particular combination opens or that policy will route to that device. `getMinBufferSize` has no device argument. [P2:1382–1432, P3:490–540,599–617]
3. `AudioRecord` needs granted `RECORD_AUDIO`. Construction, initialization, start, and read can fail separately. A nonpositive `getMinBufferSize` result is an error, not a byte count to hide using `coerceAtLeast`. The minimum does not guarantee smooth recording under load. Read units must match the encoding: short samples for PCM16, float samples for PCM_FLOAT, complete interleaved frames. [P2, P6]
4. `AudioRecord.setPreferredDevice(input)` is available throughout API29–36. A successful return does **not** prove actual recording route. `getPreferredDevice` reports the request. `getRoutedDevice` is meaningful only while recording. Register the general `AudioRouting.OnRoutingChangedListener`, start recording, then verify the requested input against the actual route before publishing mic PCM. On API36, `getRoutedDevices` can expose the full route list. Its documentation mistakenly says “playing” for AudioRecord. Do not use the API36 method on earlier releases. [P2:1930–1953,2178–2210, P6]
5. `AudioRecord.getFormat` is the client stream format. `AudioRecordingConfiguration.getClientFormat` and `getFormat` distinguish client and actual device formats. Conversion by Android means a 48kHz client does not prove a 48kHz microphone. API29 recording callbacks and `getActiveRecordingConfiguration` expose route/configuration changes and `isClientSilenced`. Register the recording callback before capture starts and take a current snapshot as well. [P4:199–213,248–292, P6, P10]
6. `getTimestamp(AudioTimestamp, TIMEBASE_MONOTONIC or TIMEBASE_BOOTTIME)` exists before API29. It reports a frame-position/time anchor at the earliest available capture-pipeline point. It may return `ERROR_INVALID_OPERATION`. Stop/start resets frame count. Timestamp validity, clock domain, and restart generation therefore belong in input metadata. Read-return time is not the capture time. [P2:1353–1379, P6]

### Bluetooth permissions and communication routing

- The AudioManager device-list and AudioRecord routing APIs do not document a blanket `BLUETOOTH_CONNECT` prerequisite. Do not request Nearby devices just because a picker opens. If implementation calls BluetoothAdapter/BluetoothDevice/profile APIs for paired-device communication or identity, API31+ `BLUETOOTH_CONNECT` applies. Scanning and discoverability separately need SCAN and ADVERTISE. R09 can avoid scanning, location permission, and custom pairing. Legacy Bluetooth declarations, if actually used, should end at API30. [P1, P6, P9, P12]
- `MODIFY_AUDIO_SETTINGS` is needed for legacy SCO start/stop and `setMode`. It is not the privileged `MODIFY_AUDIO_ROUTING` permission. Pinned AudioService verifies these checks. The reviewed `setCommunicationDevice` entry itself has no equivalent blanket Bluetooth permission check. That observation is not proof about every downstream OEM implementation. Catch and report permission failures rather than requesting unrelated grants. [P1:3182–3278, P5:6633–6642,7160–7220,7390–7493]
- API31+ `getAvailableCommunicationDevices` returns **output/sink** candidates. `setCommunicationDevice` takes one of those outputs, and Android chooses the paired input automatically. Passing the AudioRecord input endpoint is wrong. An accepted request is asynchronous. Observe `OnCommunicationDeviceChangedListener`, then still verify AudioRecord's input route. [P1:9074–9194, P11, P12]
- Communication selection persists until clear, disconnect, or process death. Competing requests favor the audio-mode owner. This path can change output routing and Bluetooth quality. Prefer per-recorder selection first. Use communication routing only for a demonstrated SCO/BLE need, with the approved first-use explanation. The official BLE example is a VoIP guide, not proof that a visualizer must become a calling app. [P1, P11]
- API29–30 legacy fallback uses `startBluetoothSco` and `ACTION_SCO_AUDIO_STATE_UPDATED`. Register first and inspect its sticky state. Wait for CONNECTED with a finite timeout. Call `stopBluetoothSco` after success **or failed establishment**. Telephony can take the connection and it is not automatically returned. The legacy public contract specifies mono 8kHz input. Higher-rate SCO support must be measured, not inferred from modern headsets. SCO start/stop is deprecated from API34 in favor of communication-device routing. [P1:3182–3278, P12]
- Do not copy recipes that always call `setBluetoothScoOn(true)`, force speakerphone, set `MODE_IN_CALL`, or change system microphone mute. Those are not narrow input selection. If a tested communication path needs `MODE_IN_COMMUNICATION` or VOICE_COMMUNICATION capture, record the output and preprocessing consequences. VOICE_COMMUNICATION is privacy-sensitive by default. [P1, P10]

### Precise restoration obligations

**Verified:** clear a communication selection when its requesting activity/service ends. Stop every owned legacy SCO request, including failed connection attempts. `setPreferredDevice(null)` restores the recorder's default preference. Stop/release the owned AudioRecord and unregister callbacks. [P1, P2]

**Recommended application rule:** one generation owns a small routing lease containing only changes this app actually made. Release it on stop, failed start, cancellation, explicit input loss, task retirement, and service destruction. Late callbacks must not release a replacement lease.

Do not “restore” the globally observed communication device by selecting it again. That can create a new app request and override another app's choice. Clear our request instead. Likewise, a preexisting `getMode()` value may belong to another app. Pinned AudioService removes the caller's mode owner when that caller sets MODE_NORMAL (`6684–6695`). If R09 set a temporary mode, release its own mode request. Restore an earlier mode only when it was an explicitly tracked same-app request. Do not force the global mode to a stale snapshot. Existing communication mode should be an explicit conflict, not silently taken over. [P5]

A disconnected original output cannot be restored physically. Report the observed post-cleanup mode and route, plus missing hardware. Successful cleanup means our requests are gone, not that Android must reproduce an unavailable route.

### Microphone foreground service, API29–36

| Runtime API | Applicable contract for this target36 app |
|---|---|
| 29 | Real foreground service and RECORD_AUDIO support background capture. No microphone FGS type constant at runtime. Use the base FGS role for a dedicated mic service. |
| 30 | Microphone FGS type is required for microphone access. A service started while the app is background cannot access mic. |
| 31–33 | General background-FGS start restrictions also apply. Foreground-started mic service can continue after the activity becomes background. |
| 34–36 | Declare FOREGROUND_SERVICE_MICROPHONE, base FOREGROUND_SERVICE, microphone type, and RECORD_AUDIO. While-in-use eligibility is checked when creating/promoting the service. Background startup can throw SecurityException despite checkSelfPermission returning GRANTED. |

Sources: [P7, P8, P13, P14].

Start from a visible activity after the real runtime permission result. Recheck visibility and selection after asynchronous source retirement. Promote the service promptly before slow routing work. Catch start-not-allowed, security, and recorder failures independently. A previously running root specialUse service does not authorize a later background microphone start.

Adding microphone to an existing service uses another `startForeground` call with the applicable declared type bits and current prerequisites. A dedicated microphone service avoids making root-only startup require RECORD_AUDIO. Do not declare every possible type and blindly activate all of them. [P7, P14]

A HUD permission or visible overlay is not a blanket microphone while-in-use exemption. API35+ visible-overlay requirements concern the general background-start exemption. The separate microphone restrictions still apply. PiP/HUD continuation of an already valid session is different from restarting a dead microphone owner. Use a visible-activity retry for the baseline design. Do not add boot, sticky restart, timer, or device-reconnect auto-start to evade this. [P8]

Provide a real ongoing recording notification and explicit in-app mic state/stop control. API33 notification denial can hide an FGS notification from the drawer while Task Manager still shows its notice. Do not confuse notification visibility with mic permission or recording state. [P15]

Zero PCM does not establish privacy mute or lost authorization. Android10+ input arbitration can leave a recorder running but silenced. Read configuration silencing and AudioManager microphone-mute state, then distinguish policy-silenced, globally muted, no samples, ordinary silence, route missing, and terminal failure. Do not claim a precise silencing cause when only zero energy is known. [P1:3420–3443, P4, P10]

## Exact baseline seams

Bare Kotlin filenames in this table resolve under `app/src/main/kotlin/dev/phosphor/mobil3/`.

| Baseline path and symbol | Verified behavior and R09 seam |
|---|---|
| `app/src/main/kotlin/dev/phosphor/mobil3/MicController.kt:14–121`, `start` | Activity-owned 48kHz float stereo MIC. Calls `deckSetPaused`, arms ring, then pushes straight to JNI at 97. Replace format assumption and separate reader publication from ring ownership. |
| Same file `128–175`, `stop`, `stopForLocal` | Recorder-identity failure fencing, asynchronous ReaderStop, owner rendezvous. Preserve stop acknowledgements, not Activity start authority. |
| `MainActivity.kt:65–109`, `mic`, `micHandoff` | Owns mic wake lock/controller and starts from a rendezvous callback. Service command and observed service status should replace controller ownership. |
| `MainActivity.kt:307–358,781–816,947–965` | Consent-purpose state, selection/task revision fencing, `withSourcesReleased`, and mic request UUID. `MicHandoffPolicy.kt:9–55` waits for matching newer capture-idle status AND local release. Retain admission fencing for mic-only source replacement. Do not use this replace-everything path merely to attach mic to an existing composite. |
| `MainActivity.kt:568–585` | Activity destruction clears mic source preference and stops mic. Service-owned capture must instead survive view recreation without restart. |
| `CaptureService.kt:109–158,244–274,288–355` | Projection FGS, direct playback PCM producer, mirrored source owner, generation/identity failure handling, retirement, task callback. Redirect playback chunks into a composite input sink without changing projection ownership. |
| `BackgroundLifecyclePolicy.kt:45–49,59–107,130–157` | Removal keeps only playback/capture roles. Mic stop is unconditional even with linger. Extend the actual owner facts and keep/stop result to mic-only/composite. Preserve task/activity revisions and SourceRetirement. |
| `SourceWakePolicy.kt:11–15` | Microphone wake uses `!activityDestroyed`. Replace with actual mic-service owner and live recorder facts. Visibility remains separate. |
| `ReaderStop.kt:12–35,69–89` | Join timeout is 2000ms. Stop/release themselves are not bounded by this helper. Cleanup success plus service destruction completes retirement. Do not describe the join timeout as a total stop deadline. |
| `app/src/main/AndroidManifest.xml:5–10,39–42` | No microphone FGS permission/service. CaptureService declares only mediaProjection. R01 changes must be reconciled before adding the mic role. |
| `rust/src/jni_glue.rs:332–353` | `pushCaptureSamples` accepts float array/count only, allocates a Vec, calls `deck::push_capture`. No timestamp, input ID, format, or generation. |
| `rust/src/deck.rs:28–49` | `push_capture` appends to one scope ring. Its mutex serializes writes, not audio time. `set_ring_active(true)` clears pending frames. Keep exactly one publisher/activation owner. |

### Narrow recommended ownership and mixer design [inference, not accepted spec]

Use one nonexported `MicCaptureService` to own the existing mic reader, route lease, notification, and bounded stop. Keep R01's playback service/backend ownership. One logical capture session owns the two input queues, one mixer producer, and the ring lease. Mic-only uses the same mic reader/output contract with one input active. Do not add a generic plugin or routing framework.

Format trials should be finite and deterministic. Rank advertised mono/stereo PCM16 and PCM_FLOAT candidates, retaining a mono PCM16 fallback. Prefer the selected device's rates over a forced 48kHz client. For arbitrary-rate capabilities, test a short declared list such as 48/44.1/16/8kHz. Legacy SCO includes mono8kHz. Release each failed recorder before the next attempt. Stop trials on permission denial, device loss, or supersession. A successful initialization still requires actual-route verification. Record the chosen client and device formats, not only the requested tuple.

On an observed route change, invalidate queued mic blocks and the input epoch before resuming publication. Routing callbacks can lag the hardware transition. A public-API route observation is not sample-by-sample physical provenance. Do not promise an impossible zero-sample race guarantee.

Readers send bounded blocks tagged with session generation, input generation, frame count/start position, client rate/channels/encoding, capture-clock anchor, and timestamp-validity state. Neither reader toggles the ring or publishes directly once composite mode is active. The mixer alone publishes normalized 48kHz interleaved float stereo. Preserve true stereo. Duplicate mono with truthful labeling. Downmix extra channels with a documented bounded matrix. Use stateful resampling, with anti-alias filtering when downsampling.

Recommended initial testable bounds, subject to Section3 contract approval: 10ms output blocks, each input capped at 200ms, initial alignment target 50ms. Overflow drops old complete frames and records a discontinuity. Missing input contributes zero after a bounded wait. No repeated stale chunk, infinite wait, or unbounded catch-up. Align by capture timestamps in one clock domain. Estimate drift from frame/time anchors and slowly adjust resampler ratio. Reset timing and interpolation state on route/rate/restart generation changes.

For input gains constrained to 0..1, one simple clipping-safe rule is `(gp*p + gm*m) / max(1, gp+gm)`, followed by finite-value sanitization and a final clamp. Preserve playback-only unity when include mic is off. Smooth gain changes. Report normalization semantics and pre-limit peaks. Do not repurpose display auto-gain as mix clipping protection.

The output remains a scope-ring publication. Do not write mixed PCM into AudioTrack, Oboe audible output, or playback deck queues. Speaker sound may still enter the physical microphone acoustically. That is not electronic monitoring feedback and may cause audible-source duplication in the visualization.

**Load-bearing changing-contract gap:** Section2 PCM kind15 (`section-02-root-product.md:36–54`) carries sequence, rate, channels, encoding and frame count, but no capture timestamp. Sequence plus frame totals detects continuity, not capture-clock alignment. App pipe arrival is an estimate contaminated by buffering and scheduling. After R01, extend the fixed protocol narrowly with a source-frame/capture-clock anchor and validity flag. Validate helper AudioRecord timestamp availability. Until then, label root+mic alignment estimated and do not claim timestamp-aligned acceptance. Preserve the existing root 16kHz mono truth and causal resampler delay. This is an R09 dependency, not an R01 critique.

## Highest-risk traps

1. Two JNI writers under a mutex append alternate chunks. They do not mix and can double the apparent timeline rate.
2. Successful `setPreferredDevice`, communication selection, or RECORDSTATE_RECORDING is not proof of the selected mic delivering samples.
3. Explicit input disconnect can silently reroute Android to built-in. Gate every mic publication on the current verified route. Never clear preference to default as recovery.
4. Root capture can run without app RECORD_AUDIO. INCLUDE MIC cannot inherit root authorization or specialUse eligibility.
5. Restoring global route/mode snapshots can seize another app's route. Release only the owned requests.
6. Callback and generation gates must cover PCM, route timers, source-stop acknowledgements, mixer stop, and notification commands, not only UI status.
7. Standard playback capture also needs RECORD_AUDIO. A per-reader mic failure can leave playback alive, but app-wide permission revocation may invalidate standard playback too. Continue only an actually healthy backend.
8. The changing R01 fixed debug checks reject communication mode (`section-02-root-product.md:83–89`). They cannot establish SCO/BLE composite acceptance unchanged.

## Finite coordinator acceptance map

These checks are **proposed and unrun**, not receipts. Add focused policy/mixer tests, then run the existing coordinator gates: `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:checkEngine` and `cargo test --manifest-path rust/Cargo.toml --locked`. Compilation alone does not close device rows.

| R09 plan line | Finite check and required observation |
|---|---|
| 115 enumeration | Host fixtures cover each listed type, sink rejection, duplicate names, empty capability arrays, removed IDs, and API gates. On each available accessory, perform three connect/disconnect cycles. Picker reflects actual input ports without scanning or permission prompts. Missing hardware remains untested. |
| 116 negotiation | Host PCM16 mono 8/16/44.1kHz, float stereo48kHz, extra-channel mapping, partial reads, unsupported min-buffer results, and failed build/start. Chunk-split and unsplit conversion agree within declared tolerance. Device records actual client and device formats for 30s per available input. |
| 117 permissions/route | Built-in and wired selection produces no Bluetooth request. Deny any genuinely needed Bluetooth permission once, then retry from foreground. Inject preferred-device accepted but actual-route mismatch. No wrong-device PCM reaches mixer. API36 checks full actual-route list. |
| 118 explanation/restoration | Cancel first Bluetooth explanation and observe zero route mutation. Activate and stop, fail connect, cancel while connecting, and disconnect. Each case ends within the declared route timeout and clears only owned requests. Before/after route and mode receipts identify unrelated owners or missing hardware. |
| 119 include/mix | For standard and root separately, run 30s playback-only, enable mic, change input and level, disable mic. Use distinct controlled playback and mic tones where hardware permits. Ring aggregates show both weighted contributions. No mixed samples enter audible output. Confirm original playback stays audible independently. |
| 120 composite timing | Host simulation: 60s with +/-500ppm input clocks, 44.1/48kHz conversion, bounded jitter, timestamp unavailable/reset/backwards, overflow, 500ms stall, NaN/Inf, and full-scale in-phase tones. Assert queue caps, one producer, bounded alignment error against the approved target, finite peaks <=1, and no stale replay. Root test includes real helper timestamps or remains estimated/unaccepted for precise alignment. |
| 121 service/FGS | Start mic from visible activity. Spend 30s in PiP/background and recreate activity three times. Owner/recorder IDs remain stable and recording indication remains honest. A background new-start attempt fails cleanly with a visible retry path. Check API29/30/31/34/36 branches using available runtime coverage, not API36-only claims. |
| 122 failures/retry | Inject mic read failure, privacy mute, competing recording, explicit unplug, and permission denial. Playback continues only when independently healthy and UI says mic unavailable/silenced as measured. No automatic alternate mic. Retry once per condition from eligible foreground state, with no old-reader publication. App-wide revoke tests allow standard playback to fail honestly. |
| 123 lifecycle | For mic-only and both composite backends, test linger off/on task removal, rapid source switch, old-task removal after new task, delayed old stop/status, and forced join timeout. Repeat switches 20 times. New source cannot start before required retirement acknowledgement. Partial mic stop cannot deactivate continuing playback's ring. |

Each device receipt should include package/build, OS/API, session/input generations, requested/actual input IDs and types, client/device formats, timestamp validity/domain, queue/discontinuity counters, per-input state/peaks, FGS types, before/after routing, and stop completion. Keep private identities local. Store aggregate measurements, not microphone recordings. Coordinator owns device actions and exact installation proof.

## Unresolved hardware questions and minimal order

Hardware unknowns: which wired/USB/SCO/BLE accessories are present, exposed routes and real PCM formats, direct preferred-device support versus communication routing, per-device timestamp quality, actual Bluetooth output/quality changes, OEM concurrent mic/playback behavior, and root-helper timestamp availability. No physical acceptance is claimed.

1. Rebase this handoff mentally onto the committed R01 result. Write Section3 ownership, timing bounds, local device identity, portable include/gain defaults, and failure contracts. Include mic remains off initially and imports remain inert.
2. Add pure route-admission, normalization, bounded mixer, and lifecycle tests before activating new capture paths.
3. Move mic ownership to its real service while preserving mic-only admission and stop semantics. Prove recreation, task rules, and foreground startup with built-in mic.
4. Add enumeration and finite format trials. Verify built-in/wired/USB routes first, then conditional SCO/BLE routing and exact request cleanup.
5. Redirect standard playback and mic into the single producer. Prove timing, failure isolation, clipping, and no audible mixed output.
6. Add the narrow root timing metadata seam and verify root+mic. Finish UI controls, persistence/import boundaries, hardware matrix, and coordinator receipts. Revert an activation step by disabling only its new path, not by weakening retirement fences.

## Primary sources, capped at 15

AOSP files are pinned to `refs/tags/android-16.0.0_r1`. Java snapshots are saved beside this report. Android Developers pages were read on 2026-09-08. Their API-level badges supplement the pinned source. Current references already mention API37, which this report excludes.

- **P1 AudioManager:** https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/media/java/android/media/AudioManager.java
- **P2 AudioRecord:** https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/media/java/android/media/AudioRecord.java
- **P3 AudioDeviceInfo:** https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/media/java/android/media/AudioDeviceInfo.java
- **P4 AudioRecordingConfiguration:** https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/media/java/android/media/AudioRecordingConfiguration.java
- **P5 AudioService:** https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/services/core/java/com/android/server/audio/AudioService.java
- **P6 AudioRecord API:** https://developer.android.com/reference/android/media/AudioRecord
- **P7 FGS types:** https://developer.android.com/develop/background-work/services/fgs/service-types#microphone
- **P8 FGS startup restrictions:** https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- **P9 Bluetooth permissions:** https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- **P10 Input sharing:** https://developer.android.com/media/platform/sharing-audio-input
- **P11 BLE communication guide:** https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager
- **P12 AudioManager API:** https://developer.android.com/reference/android/media/AudioManager
- **P13 API30 microphone FGS:** https://developer.android.com/about/versions/11/privacy/foreground-services
- **P14 FGS launch/types:** https://developer.android.com/develop/background-work/services/fgs/launch
- **P15 Notification permission:** https://developer.android.com/develop/ui/views/notifications/notification-permission

Validation performed: pinned baseline resolution, exact source/symbol inspection, 15 primary-source cross-checks, and explicit nine-row R09 requirement mapping. Build, emulator, physical-device, and audio checks were intentionally not run. Writing received a manual pass. The Python style linter was not run because Python is prohibited.
