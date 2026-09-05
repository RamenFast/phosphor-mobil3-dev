# Phase 01: make the evidence trustworthy

Entry: Phase 00 PASS. Use the [executor bootstrap](../EXECUTION.md).
Outcome: an old, failed or mismatched self-test cannot pass, and test fixtures cannot overwrite the real build.
This changes developer verification, not product features.

## ▸ 01.1 Isolate fake-ADB artifacts and reject false-green smoke receipts

📁 Existing: `M/dev/pm3`, `M/scripts/test-pm3.sh`, `M/app/src/debug/kotlin/dev/phosphor/mobil3/SelfTestReceiver.kt`, `M/rust/src/selftest.rs`.
Boundary owners: `M/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt`, `M/rust/src/jni_glue.rs`, `M/scripts/test-play-boundary.sh`.

⚠ Root checked `dev/pm3:495-525`: smoke accepts a nonempty report without parsing `ok` or matching a run.
Root checked `scripts/test-pm3.sh:11-29`: fixtures replace and restore the canonical debug APK.
These are source findings. No phone reproduction occurred during planning.

First change fixtures to use explicit scratch APK paths through the existing install interface. Hash the canonical APK before and after, if present.
Add offline package/debuggable/version/signer validation before pm3 calls adb install. A selected debug profile must reject a production APK without touching a device.
Use the verified offline inspection contract in DEVICE.md. Add wrong-package and wrong-signer fixtures that assert zero adb calls.

Then add an opaque caller run ID, validated as 32 lowercase hex characters. Reject foreign actions before native work.
Serialize self-tests. Write PNG first, then atomically replace the complete JSON report in app-owned storage.
The report contains schema, run ID, build identity, scene ID, dimensions, PNG SHA-256, `ok`, and error/fix on failure.

CLI success requires the current run, installed build identity, all required types and the pulled PNG hash.
Old schema or mismatched APK/CLI fails with a rebuild/install remedy. Do not retain a permissive legacy fallback.

Add fake-ADB cases for stale report, `ok:false`, malformed/partial JSON, missing image, wrong hash/build/run, timeout, concurrent request and fresh success.
Existing shell envelope, schema and exits 0/2/3/4 remain authoritative. Do not create another CLI or runtime endpoint.

✅ Run after implementing the cases:

```bash
cd "$M"
scripts/test-pm3.sh
scripts/test-play-boundary.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:checkEngine
```

Expected: all negative cases fail closed with error/fix. Fresh matching image/report passes. Canonical APK identity stays unchanged by fixtures.
The debug receiver remains absent from release. New observations add no release mutation surface.
This gate is synthetic. A real fresh smoke is required in the next permitted device window.

## ▸ 01.2 Compile the Android instrumentation boundary

📁 Existing: `M/app/build.gradle.kts`, `M/gradle/libs.versions.toml`, `M/app/src/debug/AndroidManifest.xml`.
NEW: `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/InstrumentHarnessTest.kt`, `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/InstrumentTestSupport.kt`.

Pin test-only AndroidX runner `1.7.0`, ext-junit `1.3.0`, and Compose UI-test JUnit4 under existing BOM `2026.06.01`.
Configure `androidx.test.runner.AndroidJUnitRunner` and testApplicationId `dev.phosphor.mobil3.debug.test`.
That is an instrumentation APK, not a product flavor. Do not add dependencies to release runtime configurations.
Root verified runner/ext-junit availability in Google Maven metadata on 2026-09-05. Project compatibility remains unbuilt.

Use actual `MainActivity` through ActivityScenario/Compose Android testing. Test support preserves preferences, establishes an idle fixture, and restores touched values.
It uses in-process access in the test package, not an exported control receiver. No automatic real-source startup or saved-host connection.
Harness cases prove package/build identity, real Activity launch/recreation, test isolation, cleanup after failed assertion and no source start during setup.
Add no general action injection protocol. Later tests exercise real controls and their production callbacks.

✅ Compile, then use the guarded device procedure only in an authorized window:

```bash
cd "$M"
./gradlew --no-daemon :app:assembleDebugAndroidTest :app:lintDebug :app:checkEngine
```

Expected: test APK compiles, test dependencies remain absent from release runtime, and all harness cases are discoverable.
The [device procedure](../verification/DEVICE.md) must later run `dev.phosphor.mobil3.InstrumentHarnessTest` with nonzero executed tests.
Compilation alone does not close the phase. Missing dependencies or a device window is BLOCKED, not a skipped PASS.

↩ Rollback: revert test-boundary activation, then smoke protocol/fixture commits, in reverse order from the receipt.
No settings schema changed. If installed, restore the compatible exact previous debug APK during an authorized window.
⛔ No production uninstall, test permission forcing, live-audio fixture or unselected device.
