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

## Review and final device verification

Prime reviewed the runtime diff. Review added a format guard: only 48 kHz stereo bypasses the mixer.
44.1 kHz stereo and 48 kHz mono keep the resampling/up-mixing path. A host behavior test checks both formats.
The direct path mutates a reusable reader buffer only after raw observation, before the next AudioRecord read replaces it.

The first mixed-mode device check exposed an edge in the new delay calculation. A rejected clock clears the queue and frame end.
That cleared frame zero must not become a latency estimate. The calculation now requires queued frames.
A host regression starts from frame 4,800,000, rejects a clock, and verifies zero fabricated delay after the reset.

Final owned command: `./gradlew -q :app:testDebugUnitTest :app:assembleDebug`.
Result: exit 0, all tests passed. Receipt: `test-and-build-clock-reset.log`.
The previous full run had 971 passing tests. This run added the clock-reset regression.
Rust: 135 passing library tests. No Rust files changed for this fix.
`git diff --check` passes for the three owned code/test files.

Installed through `dev/pm3 --serial NAAIB70036673ZC install`, without uninstall or data reset:

- Package: `dev.phosphor.mobil3.debug`, version `2.0.0-debug`.
- Local and installed APK SHA-256: `bc966368faad62f93eb1d3facb56be214bafadb4bb460c74febf32bdf673f46e`.
- Signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- Device build: `asus/WW_AI2202/ASUS_AI2202:14/UKQ1.230924.001/34.0304.2004.145:user/release-keys`.

Real UI flow: SRC → everything playing → Android Start now. NewPipe played FearofDark - Spectronosis.
STREAM_MUSIC stayed at 1. The scope visibly drew the track in both direct and mixed modes.
The saved XY45/quarter-size setting and largely matching stereo channels produce a narrow vertical trace in these screenshots.

- `final-newpipe-mixed.png`: visible trace with INCLUDE MIC on.
- `final-newpipe-mixed-band.png`: same mixed session with source band.
- `final-mixed-audio.txt`: active, unsilenced REMOTE_SUBMIX and MIC recorders.
- `final-mixed-logcat.txt`: nonzero native amplitudes, including L/R 0.3984/0.4012 and 0.5333/0.5375.
- `final-newpipe-direct.png`: visible trace after returning INCLUDE MIC to off.
- `final-direct-audio.txt`: only the playback capture recorder remains active.
- `sha256.json`: hashes of the main receipts and screenshots.

The normal UI toggle switched direct → mixed → direct without a new projection or process restart.
No fake grants, revocations, uninstall, data clearing, or system/root changes were used.
The temporary reader logging was removed before the final installed build.

Cleanup: NewPipe paused, verified by media session state PAUSED. INCLUDE MIC restored off.
Phosphor was force-stopped to end capture. No active audio recorders remained in the cleanup dump.
The temporary PhosphorAcceptance log property was reset to empty.
Primary ASUS ownership returned to Prime at 17:00 local time.

## Concurrent work and remaining risk

The working tree also contains Prime and Designer changes. This fix did not edit their files.
A later concurrent UI test run overwrote the shared XML results and reported four failures unrelated to capture:
InstrumentActivityWiringTest (two source-based cases), ManualContentTest (one), and SheetEntryPolicyTest (one).
Prime owns that integration run. The final owned capture build/test command above passed before those UI edits.

Mixed mode retains the existing clock-discontinuity and 200 ms history policies. It can briefly zero-fill after a real discontinuity or starvation.
The new counters make those gaps visible. This test proves recovery and normal mixed playback, not an overnight or Bluetooth-route soak.
Apps that opt out of Android playback capture still remain silent by design.

## Files owned by this change

- `app/src/main/kotlin/dev/phosphor/mobil3/CaptureMixPolicy.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/CaptureMixSession.kt`
- `app/src/test/kotlin/dev/phosphor/mobil3/CaptureMixPolicyTest.kt`
- `docs/plans/transpose/capture-regression.md`

No commit or push was made by the auditor. Protected archives were not touched.
