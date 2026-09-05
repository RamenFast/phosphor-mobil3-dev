# Phase 03: a dead reader cannot look like a live instrument

Entry: Phase 02 PASS. Preserve B2 STOP correlation and B4 cleanup/destruction acknowledgments.
Outcome: terminal read failures end only their owner, while silence and ordinary stop remain distinct.

## ▸ 03.1 Add terminal-health classification without moving recorder ownership

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/MicController.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/CaptureService.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ReaderStop.kt`.
NEW: `M/app/src/main/kotlin/dev/phosphor/mobil3/ReaderHealthPolicy.kt`, `M/app/src/test/kotlin/dev/phosphor/mobil3/ReaderHealthPolicyTest.kt`.

Create a small pure classifier for positive reads, zero reads, negative AudioRecord results, exceptions, explicit cancellation and stale generations.
An owner emits at most one terminal failure. Positive reads remain samples, not a second startup success event.
Zero reads alone assert neither disconnection nor new live success. Use interruptible 1 ms pacing after a zero read to prevent a busy loop.
Stop must wake that pacing and revoke the generation before unblocking the recorder.
Negative results or thrown read errors terminate that generation and name the ordinary retry/select-source remedy.
Cancellation and supersession are not user-facing hardware failures.

Use an in-process read-operation seam around the production loop, not a replacement recorder framework.
Test positive-zero-positive, each negative result, exception, stop during zero pacing, late error after replacement and duplicate terminal notification.
Also test stop-before-cleanup, cleanup-before-destroy, both callback orders, timeout/retry and old null STOP supersession.

✅ Run:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests dev.phosphor.mobil3.ReaderHealthPolicyTest \
  --tests dev.phosphor.mobil3.ReaderStopTest \
  --tests dev.phosphor.mobil3.MicHandoffPolicyTest
```

Expected: named adverse-order cases pass. Timeout is failed completion, never permission for a conflicting recorder to start.
The classifier is inert until task 03.2. Existing lifecycle owners and native entry points do not change here.

## ▸ 03.2 Wire terminal outcomes through the existing stop path

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/MicController.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/CaptureService.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`.
NEW: `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/ReaderLifecycleTest.kt`.

The read thread posts a tagged terminal observation to its existing owner. It does not join itself or synchronously block the main looper.
The owner revokes sample submission and follows the existing stop/cleanup rendezvous before reporting released resources.
UI failure goes through SourceFace only if the failed identity still owns that face.
Every request-specific B4 reply survives newest-status fencing. Explicit retry remains available after a join timeout.
Keep Activity-owned mic and service-owned projection. Never auto-renew capture consent or repeatedly reprompt after an error.

Adapter tests use actual production loop and cleanup methods with injected in-process read outcomes.
They prove old cleanup cannot clear a new active ring under the accepted stop protocol.
If they reproduce a native stale write/clear that existing owner ordering cannot prevent, stop and compile the narrow JNI ownership change first.
That conditional design must name all push/clear callers, paired Kotlin/Rust migration and rollback. Do not silently migrate remote/local rings in this phase.

✅ Run host cases, native compilation and the guarded device class:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest --tests dev.phosphor.mobil3.ReaderHealthPolicyTest
./gradlew --no-daemon :app:lintDebug :app:checkEngine :app:assembleDebugAndroidTest
export TEST_CLASS=dev.phosphor.mobil3.ReaderLifecycleTest
run_device_case
```

Expected: a terminal read failure publishes one truthful error and releases only its owner. No busy zero-read loop or stale cleanup survives.
Run D1 and D7 with ordinary denial/revocation/retry. Do not force hardware failure on Ben's phone.
Injected failures prove adapter behavior, not every physical AudioRecord failure mode. State that limit in the receipt.

↩ Rollback: revert read-loop activation first, then classifier/test seam if unused.
Keep accepted B2/B4 fixes intact. Restore the exact compatible previous debug APK only during an authorized window.
⛔ No new microphone foreground service, automatic consent, universal resource manager or timeout-as-success rule.
