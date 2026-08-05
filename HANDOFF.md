# Handoff: primetime cleanup

**Date:** 2026-08-05
**Branch:** `release/phosphor-2.0.0`
**Rollback tag:** `checkpoint/phosphor-2.0.0-pre-primetime-cleanup`
**Exact tested implementation commit:** `ba1871a51bfc8c8987f1aba0302d46ac6baebe32`

The final receipt commit is documentation-only. The APK installed and field-tested on the S25 embeds the exact implementation commit above.

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
- Canonical packaging verifies APK/AAB signers, bundletool, 16 KiB alignment, exact sources, build manifest, and checksums.
- `dev/pm3 install` reads the installed base APK back and verifies its bytes and signer.

## Verified

- Clean Android matrix: 76 unit tests, zero failures or errors; debug compilation, lint, assembly, and engine checks passed.
- Mobile Rust: 34 tests, formatting, and strict Clippy passed.
- PC relay: 26 tests, formatting, and strict Clippy passed.
- Shell syntax and ShellCheck passed for `dev/pm3` and every release script.
- Settings, routing-boundary, release-provenance, artifact-omission, false-green, and developer-CLI fixtures pass.
- `scripts/test-pm3.sh`, `scripts/test-play-boundary.sh`, `scripts/check-play-boundary.sh source --json`, and `scripts/test-release-gates.sh` pass.
- Exact S25 debug APK: package `dev.phosphor.mobil3.debug`, version `2.0.0-debug` (`2000000`), SHA-256 `582c57e5a0e29f9480be45a8832f5f40f114677ad93e14bdb428914562d46bf2`.
- `dev/pm3` verified that the installed base APK bytes and signer exactly match the local artifact.
- The S25 matrix passed upright landscape rotation, full-display capture grant, projection denial, microphone-before-projection denial, process death cleanup, true split-screen, relay audio over Tailscale, disconnect cleanup, and dormant-network checks.
- The native device self-test passed with 3,999 segments and 42,197 lit pixels.
- The canonical scoreboard is 18 of 21 gates green with zero skipped. Only approved signing, final-tag provenance, and the dependent release bundle are red.
- Protected starting files still match `docs/dev/archive/2026-08-05-scope-reset/protected/SHA256SUMS`.
- The public divergence ledger remains append-only relative to the rollback tag.
- Detailed evidence is in `docs/dev/receipts/primetime-cleanup/phase-03-s25-validation.md`.

## Next execution order

1. Provision both approved release signing identities.
2. Obtain explicit approval to create the final `v2.0.0` tag.
3. Run `scripts/ship-check.sh --json --only=release.bundle`, then install and verify the exact signed production APK.
4. Exercise the true keyguard callback only with explicit approval to manipulate the PIN-protected lock state.
5. Preserve any wanted settings from `dev.phosphor.mobil3.fortress`, then uninstall it only after Ben explicitly approves the destructive package-data removal.
6. Add Android 10 and large-screen field coverage on another device or emulator.
7. Do not push or rewrite a protected remote branch without Ben's explicit approval.

## External blockers

- Approved direct-release and Play-upload keystore inputs are not available in this workspace.
- The final `v2.0.0` tag has not been approved or created.
- The retired `dev.phosphor.mobil3.fortress` package remains installed; uninstalling it would delete package data.
- The S25 has a real PIN, so a true lock-screen callback test requires explicit approval before changing keyguard state.
- Play Console enrollment, declarations, listing assets, tester access, and submission require Ben.
- Android 10 and large-screen compatibility need additional devices or emulators beyond the S25.

## Durable boundaries

- The Gradle wrapper is the only Android build authority.
- Release signing fails closed and verifies the expected certificate SHA-256.
- Release builds fail closed on dirty, unknown, mismatched, or untagged source and on dirty sibling-engine source.
- A selected required ship gate cannot report success by skipping unavailable evidence.
- Relay traffic follows Android's Tailscale route and never binds the process to Wi-Fi or mobile data.
- A fresh install makes no Phosphor-owned network connection until the user selects a saved PC relay.
- The relay protocol is for Tailscale endpoints and must not be exposed to the public internet.
- Historical decisions and receipts remain immutable at their existing paths.
- The three protected starting files remain byte-identical in the private archive.
