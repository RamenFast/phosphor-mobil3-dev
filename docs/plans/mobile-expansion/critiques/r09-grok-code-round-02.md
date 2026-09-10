# R09 code review, attempt 2 of 4

- Tree: `phosphor-mobil3` HEAD `7204b9112804f5626bae7b22593ad0b63d7e9a8f` plus dirty unreleased R09 sources after the F1 correction.
- Freeze file: `dev/scratch/r09-implementation-20260910/source-r09.sha256`.
- All 19 freeze hashes matched the working tree. This reviewer holds no freeze.
- APK SHA-256 `4e436e263443c1d9b4f4ef572b7f08251b7f93b6e1971ad60708e6008debcfd8` is recorded, not device-accepted.
- Reviewer: Grok code quality only. No visual-taste judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. Attempt 2 passes. F1 is closed.
- Historical: attempt 1 = **7/10**, unchanged.

This is source acceptance of the fencing correction. It is not device, accessory, or beauty acceptance.

## Diff versus attempt 1

Unchanged seams: nonexported mic service ownership, actual-route proof before mic PCM, visualization-only mixer, stop ordering, portable mix keys, root inert, mixed PCM never audible, U1/R06 collateral. Residuals R1-R5 are unchanged and are not fails.

Changed: `CaptureMixSession.offer` no longer calls `core.epoch`. Init and the publisher still set mixer epoch from `PhosphorNative.captureReadEpoch()`. `finish` still sets `core.epoch(-1)` after the publisher joins. `CaptureMixCore.offer` still rejects `readEpoch != epoch`. New test `stalePreReadOfferMustNotRewindMixerEpochOrDropCurrentQueues`. Parent inherited 946 JVM tests, 0 fail. APK hash is the post-correction build, not attempt 1's recorded hash.

## F1 closed

Attempt 1 failed because `offer` called `core.epoch(readEpoch)` and could rewind the mixer to a stale pre-read epoch, dumping current-epoch queues.

Current `offer`:

```
if (!live || (mic && !accepts(generation))) return
core.offer(..., epoch)
```

`core.epoch(` now appears only at session init, the 10 ms publisher loop, and `finish`. A late offer of epoch 5 while the mixer is at 6 returns false from `core.offer`, leaves playback `queuedFrames` unchanged, and `render` returns 6. Native `pushCaptureRead` still does the final owner/epoch check.

The new test proves that core reject, then reads `CaptureMixSession.kt` and asserts the `offer` body contains `core.offer(` and does not contain `core.epoch(`. It does not construct `CaptureMixSession`. That is enough to close the production seam F1 named. Playback and mic readers still sample `captureReadEpoch()` before `read` and pass that epoch into `session.offer`.

## Evidence classes

Verified by reading freeze files after hash match, plus dirty tests/docs only as wiring evidence:

- Nonexported `MicCaptureService` is the sole recorder owner. `MicController` is its adapter. Activity destroy unbinds and cancels pending starts. It does not `mic.stop()` an established owner.
- Route callbacks and `setPreferredDevice` run before `startRecording`. Actual route is checked after start (`getRoutedDevice` / API36 `getRoutedDevices`, all IDs must match) before `session.offer` of mic PCM.
- Mixer output is `PhosphorNative.pushCaptureRead` into the visualization ring only. No `pushCaptureSamples`, `AudioTrack`, Oboe, deck audible, or relay path consumes mixed PCM.
- `CaptureMixCore.offer` rejects old attachment generation and old visual epoch. `CaptureMixSession.offer` no longer retargets mixer epoch to the incoming read.
- Whole-source stop invalidates the mixer, stops mic for that session, joins readers, then `mix.finish()` which joins the publisher and releases the ring. Optional include-off detaches only. Projection end does not convert the composite into standalone mic.
- Portable schema/2 keys `capture_include_mic`, `capture_playback_level`, `capture_mic_level` are strict in `SettingsArchive`. Import writes prefs and restores UI. It does not start, stop, or `mixSession().settings()`. Device key and Bluetooth ack live in excluded `phosphor.runtime`.
- Root stays on `startRoot()` / `pushCaptureRead`. Standard mix is not created for ROOT. `mixSession()` is STANDARD-only. Product policy still rejects root-plus-mic startup.
- U1/R06 files in this freeze only gained mic actions/state, linger wording, and wake-source string locks. No look-version, brightness-pin, or tactile-console contract change.

Inherited, not rerun: parent 946 JVM tests 0 fail.

Unobserved: ASUS accessory matrix, API29/30/31/36 runtime FGS, Bluetooth quality, hardware timestamps, acoustic latency.

## Material findings

None on the inspected correction source.

## Residual notes

These are not attempt-2 fails. R1-R5 are unchanged from attempt 1.

R1. Mixer clock may publish zeros before the recorder has a verified route. Mic PCM is still gated on actual route. Honest missing-input zero, not unverified samples.

R2. Empty capability arrays build 16 format tuples and `take(12)` drops 8 kHz. Advertised 8 kHz still trials. Legacy SCO 8 kHz is a device gap unless the accessory reports it.

R3. `ManifestBoundary` allowlists `BLUETOOTH_CONNECT` / `BLUETOOTH`. This tree does not declare them. Keep it that way.

R4. Mix keys are schema/2 specs but not `v2Only`. A hand-built v1 archive could carry them. Normal v1 exports will not.

R5. `setPreferredDevice` false aborts that candidate before start. Actual-route proof never runs for that tuple. Conservative. OEM false-return remains a hardware gap.

R6. The F1 regression test is a core unit plus a source scan of `offer`. It does not run a publisher thread. Wiring is verified by reading freeze source.

## Requirement map

| Review seam | Result |
| --- | --- |
| Standard-only mic service | Pass. Nonexported FGS, START_NOT_STICKY, notification Stop owner-fenced, Activity is admission/observation only. |
| Route proof before PCM | Pass for mic PCM. API36 multi-route reject is in policy. Accessory/API runtime unobserved. |
| Visualization-only mixer | Pass. Single publisher, two queues, no audible sink. |
| Generation/epoch fencing | **Pass.** F1 closed. Publisher/init own mixer epoch. `core.offer` rejects stale `readEpoch`. |
| Stop ordering | Pass. Composite teardown joins mic then mixer. Include-off detaches only. Failed cleanup does not release the ring. |
| Portable settings | Pass. Strict archive decode. Inert import. Runtime device identity excluded. |
| Root remains inert | Pass. No mixer, no root-plus-mic start, product deferral unchanged. |
| Mixed PCM never reaches audio output | Pass. `pushCaptureRead` to visualization `SampleRing` only. |
| U1/R06 collateral | Pass on inspected freeze diffs. |

## Limits

No source, build, device, or Git mutations. No freeze held. Hardware-unavailable accessory/API branches were not scored as fails.

## Disposition

**8/10. Pass.** Preserve attempt 1 = 7. Source-accept the F1 correction. Do not treat this as device or APK acceptance.
