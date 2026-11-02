# phosphor-mobil3 developer interface

## Project surface

- `./gradlew` owns Android compilation, native packaging, signing verification, and release artifacts.
- `dev/pm3` is local developer tooling: build, install, launch, diagnostics, screenshots, recordings.
- `rust/` is the Android JNI runtime. Its path dependencies expect the desktop engine checked out beside this repo as `../phosphor`.
- `relay/` is the PC audio relay.

## Laws

- Ben's current direction decides. When it is unclear, ask him.
- No Python in the project.
- The Gradle wrapper is the only Android build runtime. Cargo always runs with `--locked`.
- Temporary files go in an ignored directory, never in tracked paths.
- Never commit credentials, keystores, passwords, private host lists, or build artifacts. `local.properties` holds signing secrets: never print or copy it.
- Device commands always name an explicit adb serial.
- No push, force-push, publish, or store submission without Ben's explicit yes.

## Product boundaries

- One application with two build types: debug (`dev.phosphor.mobil3.debug`) and release (`dev.phosphor.mobil3`).
- Minimum SDK 29; target and compile SDK 36.
- No account, ads, usage or behavior tracking, or automatic reporting.
- No runtime administration protocol inside the app.
- No network connection until the user adds a relay, and saved relays stay inside the Tailscale address space.
- The first public release is free, with no purchase flow.

## Validation

```bash
source scripts/env.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:checkEngine
cargo test --manifest-path rust/Cargo.toml --locked
cargo test --manifest-path relay/Cargo.toml --locked
scripts/test-pm3.sh
scripts/test-play-boundary.sh
scripts/test-release-gates.sh
scripts/check-play-boundary.sh source --json
scripts/ship-check.sh --json
```

Device work runs on the ASUS test phone; take its serial from `adb devices`. `dev/pm3 install` reads back the installed APK and proves its SHA-256 and signer.

A release also needs approved signing inputs, a clean release tag, clean sibling-engine source, `scripts/ship-check.sh --only=release.bundle`, and the exact packaged release APK installed on the phone.
