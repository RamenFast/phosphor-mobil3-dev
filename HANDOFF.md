# Handoff: primetime cleanup

**Date:** 2026-08-05
**Branch:** `release/phosphor-2.0.0`
**Rollback tag:** `checkpoint/phosphor-2.0.0-pre-primetime-cleanup`

## Current implementation

- One Android product with debug and release build types.
- Debug package `dev.phosphor.mobil3.debug`; production package `dev.phosphor.mobil3`.
- Minimum SDK 29, target and compile SDK 36, arm64-v8a.
- Direct private preference state with a one-startup HUD migration and legacy-data scrub.
- Developer-only `dev/pm3` v1 interface.
- Empty first-run relay list and user-managed Tailscale endpoints.
- Android and Tailscale own relay routing; the app has no process-wide Wi-Fi or mobile bind.
- Privacy policy in `PRIVACY.md` and linked from the in-app manual.
- No advertising, usage tracking, behavior tracking, or automatic reporting dependency.
- Release provenance rejects dirty, untagged, unknown, mismatched, or dirty path-dependency source.
- Canonical packaging verifies the APK/AAB signers, bundletool, 16 KiB alignment, exact sources, build manifest, and checksums.
- `dev/pm3 install` reads the installed base APK back and verifies its bytes and signer.

## Verified so far

- Clean Android matrix: 74 unit tests, zero failures; debug lint passed at the API 29 floor with the 12 reviewed warnings in `docs/dev/LINT-DISPOSITIONS.md`; debug APK and engine checks passed.
- Mobile Rust: 34 tests, formatting, and strict Clippy passed.
- PC relay: 26 tests, formatting, and strict Clippy passed.
- Settings, routing-boundary, release-provenance, artifact-omission, false-green, and developer-CLI fixtures pass.
- `scripts/test-pm3.sh`: passed.
- `scripts/test-play-boundary.sh`: passed.
- `scripts/check-play-boundary.sh source --json`: 11 checks passed.
- A clean exact-tag synthetic release used two distinct ephemeral signers and passed APK, AAB, bundletool, 16 KiB ZIP/ELF, exact-source, manifest, and checksum gates. The disposable keys and fixture were removed after recording the result.
- Pre-commit validation artifact: `dev.phosphor.mobil3.debug`, version `2.0.0-debug` (`2000000`), API 29 to 36, arm64-v8a, SHA-256 `6a0bf180533e0bc5972d29c72459084d99128666e41a30f2fc0d600f49b947a1`; its embedded commit truthfully says `1e05687bc354-dirty`.
- Protected starting files still match `docs/dev/archive/2026-08-05-scope-reset/protected/SHA256SUMS`.

## Next execution order

1. Run the final Android, native Rust, relay, CLI, boundary, shell, protected-file, and stale-scope gates.
2. Commit the cleanup, then re-run provenance to prove the only remaining source gate is the explicitly unapproved `v2.0.0` tag.
3. Build and install the current debug APK on the explicit Galaxy S25 serial.
4. Test capture permission denial and grant, full-display selection, microphone, playback capture, notification denial, process death, rotation, multi-window, and Tailscale relay playback.
5. Preserve any wanted settings from `dev.phosphor.mobil3.fortress`, then uninstall it only after Ben explicitly approves the destructive package-data removal.
6. Provision both approved release signing identities and obtain explicit tag approval.
7. Run `scripts/ship-check.sh --json --only=release.bundle`, then install and verify the exact signed release APK.
8. Reconcile branch ancestry locally. Do not push or rewrite the protected remote default branch without Ben's explicit approval.

## External blockers

- Approved direct-release and Play-upload keystore inputs are not available in this workspace.
- The final `v2.0.0` tag has not been approved or created.
- The retired `dev.phosphor.mobil3.fortress` package remains installed; its private sandbox cannot be scrubbed by the new package and uninstalling it would delete package data.
- Play Console enrollment, declarations, listing assets, tester access, and submission require Ben.
- Android 10 and large-screen compatibility need additional devices or emulators beyond the S25.

## Durable boundaries

- The Gradle wrapper is the only Android build authority.
- Release signing fails closed and verifies the expected certificate SHA-256.
- Release builds also fail closed on dirty, unknown, mismatched, or untagged source and on dirty sibling-engine source.
- A selected required ship gate cannot report success by skipping unavailable evidence.
- Relay traffic follows Android's Tailscale route and never binds the process to Wi-Fi or mobile data.
- A fresh install makes no Phosphor-owned network connection until the user selects a saved PC relay.
- The relay protocol is for Tailscale endpoints and must not be exposed to the public internet.
- Historical decisions and receipts remain immutable at their existing paths.
- The three protected starting files remain byte-identical in the private archive.
