# Phase 02: one face contract, not one new service owner

Entry: Phase 01, including its real harness check, passes. Reuse the accepted B6/B7/B8 event and transport policies.
Outcome: late callbacks cannot make a different source look live, and unsupported actions never look available.

## ▸ 02.1 Define the inert source-face projection and adverse-order cases

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/LocalPlaybackPolicy.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/RemoteLinkTruth.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/MicHandoffPolicy.kt`.
NEW: `M/app/src/main/kotlin/dev/phosphor/mobil3/SourceFace.kt`, `M/app/src/test/kotlin/dev/phosphor/mobil3/SourceFaceTest.kt`.

Keep resource ownership unchanged. Activity owns mic observations. PlaybackService supplies its existing local/capture/remote observations.
Define one pure projection used by UI-facing adapters. It owns no coroutine, thread, service, permission or persistence.
Inputs distinguish intended selection, accepted owner identity and observed state. Selection alone cannot produce playing or live.
Reuse accepted request/revision scopes. Do not compare unrelated producer clocks as a global sequence.
Outputs contain source kind, pending/ready/playing/paused/unavailable state, metadata identity, supported actions, position/duration and error/fix.
Silence is a measured condition, not disconnect. Unknown duration removes seek capability rather than inventing zero-length media.

For every old/new source-kind pair, test stale metadata, late release, failed startup and missed receiver delivery.
Include same-path reopen with new identity, pending mic plus capture metadata, destroyed Activity plus late controller, and failed release followed by retry.
Preserve the valid old audible owner while a replacement is only prepared. Indicate pending selection without inventing replacement samples.
The native event receiver established by B6 stays the only destructive consumer. Projection never drains native events.

✅ Run after authoring production projection and cases:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests dev.phosphor.mobil3.SourceFaceTest \
  --tests dev.phosphor.mobil3.MicHandoffPolicyTest \
  --tests dev.phosphor.mobil3.RemoteLinkTruthTest
```

Expected: all named cases execute without failure or skip. Stale observations cannot alter the current accepted face.
The projection is not yet called by production UI. Existing B2/B4 request-specific cleanup replies remain unchanged.

## ▸ 02.2 Replace conflicting face writes at the existing Activity adapter

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorPlayer.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/RemotePlayer.kt`.
NEW: `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/SourceFaceIntegrationTest.kt`.

Route source-labelled UI writes from `syncSessionFace`, controller callbacks, receiver and mic callbacks through the tested projection.
Keep measured gain, beam color and frame counters separate. They cannot select a source or promote a failed recorder.
Use existing lifecycle identities to discard a MediaController that completes construction after Activity stop, and release it exactly once.
Media3 adapters retain service-owned playback authority. Check the same capability rules where they project that state.
Do not start PlaybackService solely to describe mic, add mic to Media3, or promise background mic survival.

✅ Run host floor, then the guarded exact-device class and D1-D3:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest --tests dev.phosphor.mobil3.SourceFaceTest
./gradlew --no-daemon :app:lintDebug :app:checkEngine :app:assembleDebugAndroidTest
# After the DEVICE.md permission, build and artifact procedure:
export TEST_CLASS=dev.phosphor.mobil3.SourceFaceIntegrationTest
run_device_case
```

Expected: actual Activity/service callbacks cannot resurrect obsolete capture/mic labels. UI and Media3 agree where their source domains overlap.
Activity-local mic stays truthful without a new service. No duplicated EOF consumption or double advance appears in D2.
Pure tests alone do not close this activation task.

↩ Rollback: revert the adapter activation commit, then the unused projection commit if needed.
Use the exact compatible rollback APK and settings procedure in [DEVICE](../verification/DEVICE.md).
⛔ No general event bus, universal source service, snapshot history, new backend or broad Activity rewrite.
