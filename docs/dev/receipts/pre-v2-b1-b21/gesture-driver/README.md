# Android gesture acceptance fixture

This is retained test input, not an app runtime endpoint or installed administration tool.
The root operator owns device selection, authorization, coordinates, backups and cleanup.
Version 2 requires explicit width and height before the command.
Read the current screenshot dimensions and `wm size` before each orientation-specific run.
The earlier S25 receipt used version 1 with fixed 1080 by 2340 bounds.
ASUS portrait tests use 1080 by 2400. Do not target the excluded S25.
Validate every coordinate before injecting the first event. Reject nonfinite coordinates,
out-of-bounds points and invalid duration without delivering a partial gesture.

Build through the project wrapper:

```bash
source scripts/env.sh
./gradlew --offline --no-daemon \
  -I docs/dev/receipts/pre-v2-b1-b21/gesture-driver/build.init.gradle \
  compilePhoneGestureDriver
```

Output: `build/phone-gesture/driver.jar`.
The build runs host bounds checks against the same validator before packaging the driver.
These check valid ASUS corners and seven invalid/nonfinite inputs, without Android injection.
After uploading to a unique owned phone path and making the jar read-only, run:

```text
CLASSPATH=OWNED_JAR app_process /system/bin PhoneGesture 1080 2400 probe
```

`probe` initializes the input manager without injecting events.
It must report `input_injected:false` and exit zero before gesture tests.
`pinch` accepts four start coordinates, four end coordinates and a duration in milliseconds.
`doubletap` accepts x, y and the delay between the first release and second press.
Accepted input events do not prove application behavior. Compare the actual app state.

The original driver used `InputManager.getInstance()`.
On the tested Android 16 build, that call threw a context-related NullPointerException.
Android then killed the shell process, hiding the exception behind exit 137.
The repaired fixture uses `InputManagerGlobal.getInstance()` and passed the actual S25 probe and four pinch checks.

The fixture does not synchronize to Compose layout or animation completion.
It cannot establish the exact 333 ms settle boundary by itself.
See the adjacent [receipt](../phase-15-16-gesture-recovery-2026-09-06.md) for observations and limits.
