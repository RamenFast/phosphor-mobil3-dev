# Phase 04: prove signal, beam and Android integration separately

Entry: Phase 03 PASS. Accepted B5/B15/B16/B20 beam and gain behavior remains the baseline.
Outcome: a dark frame, swapped channel or stale output fails a named check without adding another renderer.

## ▸ 04.1 Build fixed scenes through the production math

📁 Existing: `M/rust/src/selftest.rs`, `M/rust/src/engine.rs`, `M/rust/src/lib.rs`, `E/crates/phosphor-render-gpu/tests/cross_snapshot.rs`, `E/crates/phosphor-dsp/tests/golden_replay.rs`.
NEW: `M/rust/src/test_scenes.rs`, `M/rust/tests/instrument_scenes.rs`, `M/docs/dev/receipts/mobile-next/SCENES.md`.

Use the existing real DSP and renderers. Fixtures have explicit sample rate, channel order, chunk boundaries, seed, size, density and elapsed time.
Keep production transforms shared with tests. Extract only the cfg-free math seam needed to call them, never copy the implementation.
The existing self-test's reference remains 4,800 stereo frames at 48 kHz, 660/440 Hz, amplitude 0.75, 512 square, eight advances.
Keep its parameters explicit when adding scenes. Do not change the original scene just to accommodate new assertions.

| Scene | Fixed input | Required distinction |
|---|---|---|
| Silence | All-zero stereo, numbered elapsed-time advances | Sounding-to-rest transition obeys accepted silence law, not wall-clock sleeps |
| Left-only / right-only | Reference sinusoid on exactly one channel, the other zero | Raw side and absolute dBFS match, no channel reversal |
| Xy / Xy45 | Same reference stereo segments | Trace and grid rotate together, other modes restore Cartesian grid |
| Waveform / one accepted 3D mode | Same PCM, fixed camera, phase and clock | Mode geometry changes, not sample identity or invented time |
| Loud-silence-loud | Reference, zero, reference with recorded chunk lengths | Accepted 0.02 hold threshold, 6.0 clamp, 0.92 headroom and 0.05 glide remain |
| Surface-size replay | Same scene at 512 square and 800 by 600 | Correct scale/aspect and retained nondefault beam settings |

Record generated fixture hashes and full effective parameters before baselining output.
Assert exact counts/channel identity and finite coordinates. Keep existing golden replay tolerances and GPU cell tolerance 2.5 unchanged.
Compare trace/grid ROIs separately, including luminance, footprint, clipping, edge sharpness and decay.
Same-backend regression and CPU/GPU numerical comparison are separate checks. No universal cross-driver PNG hash promise.
Add negative fixtures with dark output, swapped channels and incorrect Xy45 grid. They must fail the corresponding assertion.

✅ Run outside Ben's media/GPU-sensitive window:

```bash
cargo test --manifest-path "$M/rust/Cargo.toml" --locked --test instrument_scenes
cargo test --manifest-path "$E/Cargo.toml" --locked -p phosphor-dsp --test golden_replay
cargo test --manifest-path "$E/Cargo.toml" --locked -p phosphor-render-gpu --test cross_snapshot -- --nocapture
```

Expected: every named scene and negative case executes. Required GPU comparisons record adapter identity and completed scene count.
⚠ Existing `gpu_or_skip` can return without running. A zero Cargo exit with that skip remains unavailable, not visual PASS.

## ▸ 04.2 Verify the real audio buffer and Android beam path

📁 Existing: `M/rust/src/deck.rs`, `M/rust/src/lib.rs`, `E/crates/phosphor-audio/src/playback.rs`, `M/app/src/debug/kotlin/dev/phosphor/mobil3/SelfTestReceiver.kt`.
NEW: `M/rust/src/deck_output.rs`, `M/rust/tests/instrument_audio.rs`, `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/AudioBeamIntegrationTest.kt`.

Extract only the callback's pure fill operation, if the inherited repairs did not already do so. Production `DeckOutput` calls that exact helper.
Host fixtures use real AudibleRing and decoder paths without opening a speaker. Cover sample indices, channel order, underrun padding, pause, seek and EOF drain.
Compare audible and scope samples from one decoded fixture with the actual buffering offset. Do not equate their timestamps by assumption.
Wire compiled fixed scenes into the existing debug self-test after host checks. Accept no arbitrary scene code, URL, filesystem path or runtime command.
Android tests exercise actual Oboe/JNI/surface paths. Collect bounded test-only counters and surface-inclusive frames.
Record no live microphone waveform, personal media metadata or listening history.

✅ Run, then activate only through the guarded device procedure:

```bash
cargo test --manifest-path "$M/rust/Cargo.toml" --locked --test instrument_audio
cargo test --manifest-path "$E/Cargo.toml" --locked -p phosphor-audio --no-default-features
cd "$M"
./gradlew --no-daemon :app:checkEngine :app:assembleDebugAndroidTest
export TEST_CLASS=dev.phosphor.mobil3.AudioBeamIntegrationTest
run_device_case
```

Expected: fixtures detect missing/duplicated samples, stale-item output, wrong channels and pause/EOF errors.
Actual Android observations close D2/D6. Software observations do not claim physical speaker-to-panel latency.

↩ Rollback: revert mobile helper activation, then owned shared test changes in reverse receipt order.
Keep failed fixtures and baseline evidence. Use the exact compatible debug rollback artifact if installed.
⛔ No beam-law fork, new visual mode, live recording archive or production per-frame observer allocation.
