# Phase 07: measure lifetime and immediacy before optimizing

Entry: Phase 06 PASS. This phase measures real boundaries. It does not promise a universal teardown rewrite.
Outcome: regressions in native presentation, source shutdown and resource survival become visible and attributable.

## ▸ 07.1 Exercise lifecycle adapters and record unresolved ownership

📁 Existing: `M/rust/src/render.rs`, `M/rust/src/jni_glue.rs`, `M/rust/src/remote.rs`, `M/rust/src/bridge_core.rs`, `M/relay/src/session.rs`, `M/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/PlaybackService.kt`.
NEW: `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/InstrumentLifetimeTest.kt`, `M/docs/dev/receipts/mobile-next/LIFETIMES.md`.

Create a resource table with one owner, cancellation request, actual release acknowledgment and failed-stop remedy for each resource.
Cover AudioRecord, projection, decoder, audible/scope ring, Oboe stream, NativeWindow, MediaController, browse cursor and relay child/thread.
Instrument bounded debug test observations only. Test repeated recreation, PiP, task removal and stop during startup against production adapters.
Reuse Phase 03 native-substage tests at cfg-free bridge_core and relay seams. Android remote teardown remains an Android integration check.
Keep one socket writer, protocol v2 and current Tailscale routing. Do not restart a service during authoring or host fixtures.

⚠ Root and critic inspected `jni_glue.rs:69-86` and `render.rs::Active`.
The current two-second SurfaceDestroyed wait can expire while the worker still owns the old window.
An unavailable flag or refusal to create a successor cannot protect that old window after Android returns from the callback.
Do not call that a lifetime fix. Do not remove the timeout, spawn a replacement renderer or add a new timeout policy in this phase.
Any reproduced unsafe window retention blocks affected activation and requires a separate ownership design and exact native-device gate.
Record that known structural risk even if ordinary recreation tests pass.

✅ Run deterministic checks and guarded real lifetime cases D7/D8:

```bash
cd "$M"
cargo test --manifest-path rust/Cargo.toml --locked bridge_core
cargo test --manifest-path relay/Cargo.toml --locked
./gradlew --no-daemon :app:checkEngine :app:assembleDebugAndroidTest
export TEST_CLASS=dev.phosphor.mobil3.InstrumentLifetimeTest
run_device_case
```

Expected: normal fixtures leave no owned child/thread/cursor/recorder survivor. Refused joins report unresolved ownership, not successful cleanup.
Actual Android recreation and task-swipe checks pass without crash or stale source. A required unavailable relay/API test remains BLOCKED.
This bounded PASS does not establish safe recovery from an arbitrarily wedged GPU.

## ▸ 07.2 Freeze measured performance allowances and compare candidates

📁 Existing: `M/rust/src/render.rs`, `M/rust/src/jni_glue.rs`, `M/app/src/debug/kotlin/dev/phosphor/mobil3/SelfTestReceiver.kt`, `M/dev/pm3`.
NEW: `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/InstrumentPerformanceTest.kt`, `M/app/src/test/kotlin/dev/phosphor/mobil3/PerformanceComparisonTest.kt`, `M/docs/dev/receipts/mobile-next/PERFORMANCE.md`.

First run five paired repetitions of the unchanged accepted APK on the same hardware, OS/driver, scene, cadence, window and controlled thermal state.
Measure cold/warm starts, quiet/sounding frames, PiP/resume, native surface recreation, steady local audio and rapid chrome interaction.
Retain p50/p95/p99 timings, missed deadlines, audio underruns, main-looper delay, warm allocation counts, memory trend and resource counts.
Measure native presentation separately from Compose. `pm3 fps` uses gfxinfo and cannot establish wgpu presentation timing alone.
Use fixed-size debug counters/histograms and explicit end-of-test readback. No production per-frame JNI, logging, file write or observer allocation.

Freeze each metric's allowance before candidate measurements: maximum observed unchanged-pair delta plus measurement resolution.
For a lower-is-better metric, candidate paired delta must not exceed that allowance. Reverse the inequality for higher-is-better metrics.
Reject a baseline whose uncertainty masks the regression being tested. Exact sample conservation, zero normal survivors and instrument constants receive no statistical tolerance.
Compute cadence deadlines from the selected supported rate and actual panel capability. Do not invent a universal FPS or physical latency claim.
Only a measured failing owner can justify an optimization. Compile its bounded task, source ownership, expected metric improvement and rollback before changing it.

✅ Run comparison rejection fixtures, then the guarded D9 measurement:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest --tests dev.phosphor.mobil3.PerformanceComparisonTest
./gradlew --no-daemon :app:assembleDebugAndroidTest :app:checkEngine
export TEST_CLASS=dev.phosphor.mobil3.InstrumentPerformanceTest
run_device_case
```

Expected: missing/noisy/mixed-identity measurements reject. Five comparable candidate pairs meet the pre-frozen allowances.
Store raw summaries and calculations in the receipt. Host timing or a screenshot cannot replace missing native presentation evidence.

↩ Rollback: revert any separately approved measured optimization first, then debug observation wiring if necessary.
Keep baselines and failed measurements. Restore task-changed device settings and exact compatible prior debug APK.
⛔ No broad renderer extraction, new polling service, lock-free rewrite, timeout-as-release or background diagnostic history.
