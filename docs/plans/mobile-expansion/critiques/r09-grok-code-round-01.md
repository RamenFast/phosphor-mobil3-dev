# R09 code review, attempt 1 of 4

- Tree: `phosphor-mobil3` HEAD `7204b9112804f5626bae7b22593ad0b63d7e9a8f` plus dirty unreleased R09 sources.
- Freeze file: `dev/scratch/r09-implementation-20260910/source-r09.sha256`.
- All 19 freeze hashes matched the working tree. This reviewer holds no freeze.
- APK SHA-256 `d466bd0e471ae942d20efbdd6b571d83de8056ce1aa6521f5fed15b3c27b587f` is recorded, not device-accepted.
- Reviewer: Grok code quality only. No visual-taste judgment.
- Score: **7/10**.
- Gate: Ben accepts >= 8/10. Attempt 1 does not pass. One fencing correction is required, then independent round 2.

This is source code-quality review. It is not device, accessory, or beauty acceptance.

## Evidence classes

Verified by reading freeze files after hash match, plus dirty tests/docs only as wiring evidence:

- Nonexported `MicCaptureService` is the sole recorder owner. `MicController` is its adapter. Activity destroy unbinds and cancels pending starts; it does not `mic.stop()` an established owner.
- Route callbacks and `setPreferredDevice` run before `startRecording`. Actual route is checked after start (`getRoutedDevice` / API36 `getRoutedDevices`, all IDs must match) before `session.offer` of mic PCM.
- Mixer output is `PhosphorNative.pushCaptureRead` into the visualization ring only. No `pushCaptureSamples`, `AudioTrack`, Oboe, deck audible, or relay path consumes mixed PCM.
- `CaptureMixCore.offer` rejects old attachment generation and old visual epoch. Tests in `CaptureMixPolicyTest.oldInputAndVisualEpochCannotPublishOrLeakFilterHistory` cover that core fence.
- Whole-source stop invalidates the mixer, stops mic for that session, joins readers, then `mix.finish()` which joins the publisher and releases the ring. Optional include-off detaches only. Projection end does not convert the composite into standalone mic.
- Portable schema/2 keys `capture_include_mic`, `capture_playback_level`, `capture_mic_level` are strict in `SettingsArchive`. Import writes prefs and restores UI; it does not start, stop, or `mixSession().settings()`. Device key and Bluetooth ack live in excluded `phosphor.runtime`.
- Root stays on `startRoot()` / `pushCaptureRead`. Standard mix is not created for ROOT. Product policy still rejects root-plus-mic startup.
- U1/R06 files in this freeze only gained mic actions/state, linger wording, and wake-source string locks. No look-version, brightness-pin, or tactile-console contract change.

Inherited, not rerun: parent 945 JVM tests 0 fail/0 error/0 skip, lintDebug, assembleDebug, assembleDebugAndroidTest, checkEngine.

Unobserved: ASUS accessory matrix, API29/30/31/36 runtime FGS, Bluetooth quality, hardware timestamps, acoustic latency.

## Material findings

### F1. `CaptureMixSession.offer` rewinds mixer epoch to a stale pre-read epoch

**Priority: high. Contract:** pre-read visual epochs; epoch change discards queues/history/pending output; never relabel an old block at dequeue; a blocked read keeps its old epoch and is discarded after resume. Evidence: source, high confidence.

`CaptureMixCore.epoch` / `offer` already implement the fence: a later `core.epoch(4)` clears queues, and `offer(..., readEpoch=3)` returns false. `CaptureMixSession.offer` undoes that at the production seam:

```
if (!live || (mic && !accepts(generation))) return
core.epoch(epoch)
core.offer(..., epoch)
```

`core.epoch(value)` sets the mixer epoch to the incoming read epoch whenever it differs, then resets both input queues. The publisher already samples `PhosphorNative.captureReadEpoch()` each 10 ms and renders under that current epoch. A blocked read that sampled epoch N, then HOLD-resume to N+1, can therefore:

1. Let the publisher advance to N+1 and discard old queues (correct).
2. Offer the late N block, which calls `core.epoch(N)` and rewinds.
3. Accept the old block into the mixer and render epoch N.
4. Native `pushCaptureRead` still rejects N against current N+1, so the beam does not display old HOLD audio, but the N+1 queues just built are dumped. The next publisher loop advances again and publishes zeros / a dropout.

That is relabeling an old block inside the mixer and discarding current-epoch audio. Core unit tests never construct `CaptureMixSession`, so they cannot catch it.

**Smallest correction:** do not call `core.epoch(epoch)` from `offer`. Only the publisher (and init) should set mixer epoch from the current native visual epoch. Keep `core.offer`'s `readEpoch != epoch` reject. Optional tighter form: set epoch from an offer only when `epoch == PhosphorNative.captureReadEpoch()` (never from a stale read). Add a session-level test: publisher at epoch 6, late offer of epoch 5 must not clear epoch-6 playback/mic queues and must not make `render` return 5.

## Residual notes

These are not attempt-1 fails.

R1. Mixer clock may publish zeros before the recorder has a verified route. Mic PCM is still gated on actual route. Honest missing-input zero, not unverified samples.

R2. Empty capability arrays build 16 format tuples and `take(12)` drops 8 kHz. Advertised 8 kHz still trials. Legacy SCO 8 kHz is a device gap unless the accessory reports it.

R3. `ManifestBoundary` allowlists `BLUETOOTH_CONNECT` / `BLUETOOTH`. This tree does not declare them. Keep it that way.

R4. Mix keys are schema/2 specs but not `v2Only`. A hand-built v1 archive could carry them. Normal v1 exports will not.

R5. `setPreferredDevice` false aborts that candidate before start. Actual-route proof never runs for that tuple. Conservative; OEM false-return remains a hardware gap.

## Requirement map

| Review seam | Result |
| --- | --- |
| Standard-only mic service | Pass. Nonexported FGS, START_NOT_STICKY, notification Stop owner-fenced, Activity is admission/observation only. |
| Route proof before PCM | Pass for mic PCM. API36 multi-route reject is in policy. Accessory/API runtime unobserved. |
| Visualization-only mixer | Pass. Single publisher, two queues, no audible sink. |
| Generation/epoch fencing | **Fail F1** at `CaptureMixSession.offer`. Core fence is correct. |
| Stop ordering | Pass. Composite teardown joins mic then mixer; include-off detaches only; failed cleanup does not release the ring. |
| Portable settings | Pass. Strict archive decode; inert import; runtime device identity excluded. |
| Root remains inert | Pass. No mixer, no root-plus-mic start, product deferral unchanged. |
| Mixed PCM never reaches audio output | Pass. `pushCaptureRead` → visualization `SampleRing` only. |
| U1/R06 collateral | Pass on inspected freeze diffs. |

## Limits

No source, build, device, or Git mutations. No freeze held. Hardware-unavailable accessory/API branches were not scored as fails.

## Disposition

**7/10. Correction required on F1, then independent code round 2.** Do not treat this round as source acceptance.
