# Phase 02: product consolidation

**Measured:** 2026-08-05
**Branch:** `release/phosphor-2.0.0`
**Starting implementation commit:** `1e05687bc354`
**Status:** source cleanup and local release machinery verified; final production identity remains deliberately blocked

## Scope completed

- Removed the retired product administration, authority, audit, entitlement, flavor, Binder, provider, and product-CLI surfaces.
- Kept one Android product with debug and release build types.
- Set `minSdk 29`, `targetSdk 36`, `compileSdk 36`, arm64-v8a, debug package `dev.phosphor.mobil3.debug`, and production package `dev.phosphor.mobil3`.
- Made Gradle the only Android and native build authority and made Cargo path-dependency resolution locked.
- Replaced the removed shared state graph with direct private preference state, a one-startup HUD migration, and an obsolete-state scrub.
- Kept PC relay playback, limited saved hosts to Tailscale names and `100.64.0.0/10`, and removed process-wide Wi-Fi/mobile binding.
- Added API 29 to 36 capture compatibility, microphone-before-projection ordering, and full-display capture by default on API 34 and newer.
- Added the privacy policy, removed unnecessary notification permission, and retained zero analytics, advertising, behavior tracking, or automatic reporting SDKs.
- Added fail-closed release provenance, distinct direct-APK and Play-upload signing identities, canonical APK/AAB packaging, bundletool validation, 16 KiB ZIP/ELF validation, exact-source archive, build manifest, checksums, and exact installed-APK byte/signer verification.

## Local validation

### Android

Command:

```bash
./gradlew --no-daemon clean \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:checkEngine
```

Result:

- 74 unit tests, zero failures, zero errors, zero skipped.
- Lint passed at API 29 with 12 reviewed warnings and no baseline or source suppression. Counts match `docs/dev/LINT-DISPOSITIONS.md`.
- Debug package: `dev.phosphor.mobil3.debug`.
- Version: `2.0.0-debug`, code `2000000`.
- SDK range: 29 to 36.
- ABI: arm64-v8a.
- APK signer: Android debug certificate SHA-256 `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- APK SHA-256: `6a0bf180533e0bc5972d29c72459084d99128666e41a30f2fc0d600f49b947a1`.
- ZIP alignment passed with `zipalign -P 16`.
- All three native libraries had a minimum ELF LOAD alignment of 16384 bytes.
- Defined DEX packages contain no retired product classes or known tracking SDK namespaces. Product-specific protocol strings are absent.
- Generated `BUILD_COMMIT` was `1e05687bc354-dirty`, which truthfully identifies the uncommitted validation tree instead of claiming a clean commit.

### Native and relay

- Mobile Rust: formatting passed, 34 tests passed, strict Clippy passed with `-D warnings`.
- PC relay: formatting passed, 26 tests passed, strict Clippy passed with `-D warnings`.
- The Android cross-build still prints the sibling engine's target-specific `Dispatch::Avx2` dead-code warning. That code lives in the separate `../phosphor` repository and is not changed by this cleanup. The mobile crate's strict Clippy gate is green.

### Boundaries and tooling

- `scripts/test-pm3.sh`: passed, including explicit device serial, debug package isolation, exact installed bytes/signer, and explicit canonical production APK path fixtures.
- `scripts/test-play-boundary.sh`: passed.
- `scripts/check-play-boundary.sh source --json`: 11 source checks passed.
- `scripts/test-release-gates.sh`: passed dirty, untagged, mismatched, dirty-dependency, missing-artifact, and selected-gate false-green regressions.
- `bash -n dev/pm3 scripts/*.sh`: passed.
- `shellcheck dev/pm3 scripts/*.sh`: passed.
- Active documentation has zero retired product promises.
- Runtime and developer tooling have zero retired product control symbols outside the scripts that intentionally detect them.
- Source and dependencies have zero tracking, advertising, or reporting SDK hits.
- `git diff --check`: passed.

## Dual-signer synthetic release proof

A clean isolated fixture created an exact synthetic `v2.0.0` tag and two distinct disposable non-production signing identities. The canonical release gate passed with no skipped evidence.

- Fixture mobile commit: `10277a57a18bfd560ba8c61382e83bc57b0bb7ff`.
- Fixture sibling-engine commit: `c0cf967c4afa0aa7bf907dee915480aed8bd0530`.
- Direct APK signer SHA-256: `3ac7d878941d8c36938228e4b72dc3afc5148ff5875de636dc654cbc35110951`.
- Play-upload AAB signer SHA-256: `2acd3678eaff75758034213b379a32c37cfc78939271029eae6ed23e10d0ac73`.
- APK SHA-256: `d27153cf00754051f13bfac59563ad25c10984d6b68b2f621e9cad4b4104cd37`.
- AAB SHA-256: `35505e0d9e7b4fc8c9e0be02f33ac7d187a30ab12023086aa10c72f3dd8cb759`.
- Combined source archive SHA-256: `4b3045489b13332af4a39736d7ff07532d4ea85d3c9b484751b582dd7a37d0a7`.
- `BUILD-MANIFEST.json` SHA-256: `c6ae47a6b1b63f84a48f26fd0cf35538460fd0c5e66a63bfa3521a0248b52c9d`.
- APK, AAB, bundletool, signer identity, package/version, 16 KiB ZIP/ELF, exact sources, manifest, and `SHA256SUMS` checks passed.
- The disposable fixture and both ephemeral key files were removed after the proof was captured.

## Full ship-gate state

`scripts/ship-check.sh --json` measured 21 gates:

- 18 local implementation gates green.
- 0 skipped.
- `signing.release` red because neither approved real signing identity is provisioned.
- `provenance.release` red because the cleanup tree is not yet committed and the final `v2.0.0` tag is not approved.
- `release.bundle` red because it correctly depends on both red prerequisite gates and cannot false-green by skipping them.

This is the intended pre-release state. It is not a production release receipt.

## Protected and external boundaries

- The three protected starting files still match `docs/dev/archive/2026-08-05-scope-reset/protected/SHA256SUMS`.
- The public divergence ledger remains append-only.
- No final `v2.0.0` tag was created.
- No protected remote branch was pushed or rewritten.
- No approved production or Play upload key was created, copied, or modified.
- The legacy `dev.phosphor.mobil3.fortress` package was not uninstalled because that would delete its private data and requires explicit approval.
- S25 installation and field behavior are the next phase.
- Android 10 and large-screen field coverage still require additional devices or emulators.
- One historical estate relay at `100.66.109.56` was offline during the link-state check; the current relay at `100.114.165.77` answered its protocol probe. Relay polish remains deferred.
