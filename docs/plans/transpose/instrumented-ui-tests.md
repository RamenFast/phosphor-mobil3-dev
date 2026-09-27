# Instrumented transpose UI tests

Task 4 adds 18 real-composable behavior tests in `TransposeUiTest.kt`.
This is a compile-verified suite, not a device-pass claim.

## Coverage

- Toggle and slider: scroll, fling and pull do not activate them. A later real tap still works.
- A completed pointer scroll does not block semantic OnClick or focused keyboard Enter.
- Content scroll followed by pull dismisses. Fling does not dismiss. A short pull springs home.
- Settings headings, key rows, conditional HUD controls and startup choices are reachable.
- Toggle role/state semantics and the seven-tap developer unlock are tested.
- LIGHT preset/saved swatches publish changes and update their selected marks.
- SRC starts a non-selected idle input in one tap and stops it with the next tap.
- MODE skip cells remain available before random mode and retain at least two choices.
- Settings at font scale 2.0 exposes real text-layout results without overflow or ellipsis.
- Manual Back visits chapter, index, then Settings.

Tests use production composables with a strict recording actions boundary.
Unexpected activity actions fail. They do not invoke native audio, network, permissions or persistence.
The test-only runner supplies plain Application to avoid production startup preference migrations.
The official debug test manifest supplies a blank ComponentActivity instead of MainActivity.

## Build and execution boundary

Compile with the repository wrapper:

`./gradlew :app:compileDebugAndroidTestKotlin`

Receipts: `build/transpose/instrumented-compile-receipt.json` and `build/transpose/auditor-instrumented-compile.log`.
Dependencies follow the existing version catalog and Compose BOM.
The project has no active Gradle dependency locking or verification metadata to update.

`dev/pm3-ui-test schema` describes the explicit-serial runner.
It runs only an already installed compatible debug app/test APK pair through `adb shell am instrument`.
It never installs, uninstalls, clears data, or invokes connectedAndroidTest.
It refuses primary serial `NAAIB70036673ZC` unless `--allow-primary` is explicit.
The flag is not a substitute for device ownership or user authorization.
Logs and SHA-256 receipts go under ignored `build/transpose/`.

No instrumentation ran on a phone during Task 4. A compatible app/test pair must be installed on an authorized test bed for runtime acceptance.

## Limits

The host supplies external actions and top-level sheet routing. ManualSheet itself owns chapter navigation.
The font-scale assertions focus on Settings in a portrait-width test host, not every sheet or device orientation.
Real hardware checks for SRC routes, landscape sheets and manual Back are documented separately in `mic-landscape-verification.md`.
