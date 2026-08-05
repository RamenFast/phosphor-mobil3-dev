# Phosphor Mobile improvement plan

**Measured:** 2026-08-05

This is the active development backlog after repository consolidation. Product authority lives in `vision/`, `spec/`, and the current dated decision.

## P1: finish the cleanup stage

1. **Complete the validation matrix**
   - Run Android unit tests, API 29 lint, debug assembly, native engine checks, mobile Rust tests, relay tests, CLI fixtures, boundary fixtures, and shell syntax checks.
   - Save a receipt with exact commands and counts.

2. **Verify the Galaxy S25 build**
   - Install with the explicit serial.
   - Check microphone denial and grant.
   - Check playback-capture denial and grant.
   - Confirm Android 14+ offers the full display rather than an individual app.
   - Check screen lock, process death, notification denial, rotation, picture-in-picture, and multi-window.

3. **Verify the PC relay end to end**
   - Use a user-saved Tailscale endpoint.
   - Check audio, geometry, metadata, artwork, transport controls, network loss, reconnect, and `scripts/force-link-states.sh`.
   - Confirm a fresh install makes no Phosphor-owned connection before host selection.

4. **Produce the signed release candidate**
   - Provision the approved direct-release or Play-upload signer.
   - Build the APK/AAB from the final source commit.
   - Verify signer, package, version, commit binding, archive boundary, ELF alignment, zip alignment, checksums, and phone installation.

5. **Reconcile Git history**
   - Confirm all local branch tips are ancestors of the cleanup branch or explicitly preserve them.
   - Merge locally as needed.
   - Do not push the protected remote default branch until Ben approves the exact result.

## P2: architecture after cleanup

1. Extract orientation and placement behavior from `MainActivity` into a lifecycle-aware controller with unit-tested state transitions.
2. Add API 29 emulator coverage and a large-screen or foldable test target.
3. Add instrumented permission-flow tests for microphone, projection, notification access, and process recreation.
4. Reduce stale comments to intent, invariants, and non-obvious platform constraints.
5. Add a reproducible release manifest that binds source commit, package, version, signer, ABI, SDK levels, artifact hashes, and validation receipt paths.

## P3: deferred networking stage

1. Add an explicit relay bind address and prefer the Tailscale interface.
2. Decide whether Tailscale identity remains sufficient or protocol-level authentication is required.
3. Improve discovery without creating first-run traffic.
4. Tune long-session latency and reconnect behavior.
5. Recheck remote library playback and metadata after service upgrades.

## External gates

- Release signing credentials.
- Google Play Console app registration and signing enrollment.
- Privacy-policy publication at a stable public URL.
- Data Safety and foreground-service declarations.
- Store listing, content rating, tester access, and submission.
- Additional Android 10 and large-screen test devices.

The next two major core features are intentionally not specified here. They begin only after Ben provides the feature brief and the cleanup release baseline is sealed.
