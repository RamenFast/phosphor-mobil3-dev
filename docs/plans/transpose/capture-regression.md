# Everything playing capture regression

## Required behavior and failure cases

The standard capture reader must draw permitted other-app PCM without relying on timestamps when no microphone is attached.
With microphone mixing, both inputs use one presentation time based on delivered audio, not the hardware head.
Failure cases: capture delivery lead exceeds 50 ms; lead grows mid-stream; timestamp jitter; a stalled mic; stale visual epochs; switching direct/mixed mode; source opt-out.
Preserve independent stereo samples, playback level, source ownership, and stale-epoch rejection.
Never replay already published audio after a delay change. Count unavailable mixed frames in diagnostics.

## Reproduction

Primary ASUS NAAIB70036673ZC. NewPipe 0.29.1 playing FearofDark - Spectronosis at STREAM_MUSIC 1.
Real MediaProjection consent approved in the normal app flow. Blank scope persists.
AudioPolicy reports allowPlaybackCapture=true for NewPipe and Spotify. NewPipe is an active media AudioTrack without capture opt-out flags.
Temporary reader diagnostics show PCM peaks 0.74 to 1.0 while the visualization mixer output remains zero.
Example: 48,000 frames read, hardware timestamp frame 52,224. Delivered audio trails the timestamp by approximately 88 ms.
CaptureMixSession requested now minus 50 ms, beyond the delivered queue. The mixer filled the whole output with zeros.
The recent Settings, gain, renderer flags, and console commits did not cause the capture failure.

Evidence: build/transpose/captures/capture-debug/. Temporary diagnostic logging will be removed before the final build.

## Fix

- Playback-only AudioRecord reads publish directly to the native ring. The timestamp mixer cannot silence this path.
- Direct publication keeps stereo, playback level, native ownership, and pre-read visual epoch checks.
- A session monitor serializes direct publication, mixed publication, and microphone attachment changes.
- Mixed playout derives delivery lag from each stream’s timestamp and delivered frame end. It adds a 30 ms read/interpolation margin.
- Delay rises when needed and releases at 1 ms per second. Backward presentation becomes counted silence instead of replay.
- Signal diagnostics now report the playout delay, missing input frames, delay-gap frames, and direct/mixed mode.
- The stop path releases its monitor before joining the publisher, avoiding a stop/join deadlock.
- The obsolete source-text assertions in CaptureMixPolicyTest were removed. Their epoch behavior assertions remain.

## Host verification

`./gradlew -q :app:testDebugUnitTest :app:assembleDebug`: passed, 970 tests, zero failures/errors/skips.
`cd rust && cargo test --locked --lib`: passed, 135 tests.
The first new host test run failed before the implementation because renderLive did not exist.
The mixed alignment test exposed a fixture expectation error of one 480-frame block. The expected timestamp mapping was corrected.

Device verification follows below.
