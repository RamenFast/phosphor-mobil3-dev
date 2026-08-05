# phosphor-mobil3 developer interface

## Project surface

- `./gradlew` owns Android compilation, native packaging, signing verification, and release artifacts.
- `dev/pm3` is local developer tooling for build, install, launch, diagnostics, screenshots, recordings, and self-test receipts.
- `relay/` is the retained PC audio and geometry relay.
- `rust/` is the Android JNI runtime.

Do not add a runtime administration protocol to the app. Device operations always require an explicit adb serial.

## Repository laws

- Read the root governance file and relevant skills before changing code or documentation.
- Do not use Python.
- Use `$JCODE_SCRATCH_DIR` or an ignored project directory for temporary files.
- Keep the Gradle wrapper as the sole Android build runtime.
- Use locked Cargo resolution in build and test commands.
- Do not commit credentials, keystores, passwords, private host lists, or generated artifacts.
- Preserve historical decisions and receipts. Append to append-only ledgers.
- Preserve `docs/dev/archive/2026-08-05-scope-reset/protected/` byte-for-byte.
- Do not push, force-push, publish, or submit to a store without explicit approval.

## Product boundaries

- One application: debug and release build types only.
- Debug package: `dev.phosphor.mobil3.debug`.
- Production package: `dev.phosphor.mobil3`.
- Minimum SDK 29; target and compile SDK 36.
- No account, ads, usage tracking, behavior tracking, or automatic reporting service.
- No first-run relay endpoints or network connection.
- Saved PC relays must remain inside the supported Tailscale address space.
- The first public release is free and has no purchase flow.

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

For device work, use the explicit Galaxy S25 serial from the Android skill. `dev/pm3 install` must read back the installed base APK and prove its SHA-256 and signer. Record package, version, signer, device build, commands, observations, and artifact hashes.

A release claim additionally needs approved signing inputs, a clean exact release tag, clean sibling-engine source, `scripts/ship-check.sh --only=release.bundle`, and installation of the exact packaged release APK on the phone.
