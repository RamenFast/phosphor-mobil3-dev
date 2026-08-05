# phosphor-mobil3 agent interface

## Project surface

`dev/pm3` is the project developer CLI. The Android APK has no command-line product interface.

The CLI may build, install, run, inspect, capture receipts, and test Phosphor. It must not expose product state mutation, Nexus administration, authority grants, audit export, or agent control.

`dev/pm3 schema` is the contract source. Structured one-shots use the workspace envelope. Errors name a fix and use exit codes 2, 3, or 4. Declared streams keep their documented format.

Debug builds may include self-test components that are absent from release builds.

## Repository laws

- Read `vision/`, `spec/`, and the newest decision before changing product behavior.
- Treat `decisions/` and `docs/dev/receipts/` as immutable history.
- Treat `docs/dev/archive/` as historical context, not active authority.
- Keep the desktop engine source in the sibling `../phosphor` repository.
- Do not fork shared engine crates into this repository.
- Do not author or invoke Python.
- Keep the Android toolchain under the gitignored `.toolchain/` directory.
- Use the Gradle wrapper version as the build authority.
- Use locked Rust dependencies for release work.
- Do not push `main` or publish a release without Ben's explicit approval.

## Product boundaries

The production app is one Play-safe package, `dev.phosphor.mobil3`. Debug uses `.debug`.

The active product contains no analytics, behavior tracking, Nexus integration, product-agent transport, Fortress flavor, root capture, Shizuku, or ADB sidecar.

The PC relay remains supported. Protocol v2 belongs on a trusted local network or Tailscale.

## Android validation

The supported floor is Android 10, API 29. Compile and target SDK remain 36.

Compatibility checks cover API 29, 31, 34, and 36. Physical S25 checks always use:

```bash
D=100.102.2.83:5555
adb -s "$D" ...
```

Never select an implicit ADB device. Never guess the phone PIN.

## Completion evidence

Each phase records:

1. the intended behavior and boundary;
2. the exact changed paths;
3. the commands that passed;
4. any physical-device evidence;
5. the rollback point;
6. remaining human gates.

Compilation is not enough when the requirement concerns consent, audio, networking, signing, or installation.
