# Phase 03: exact Galaxy S25 validation

**Measured:** 2026-08-05
**Branch:** `release/phosphor-2.0.0`
**Exact tested implementation commit:** `ba1871a51bfc8c8987f1aba0302d46ac6baebe32`
**Device:** Galaxy S25, `SM-S931U`, Android 16, serial `100.102.2.83:5555`
**Status:** the current implementation is locally release-ready and field-verified on the S25; production signing and the final tag remain deliberately blocked

This receipt records the exact implementation build. A later documentation-only commit may contain this receipt and its screenshots. That evidence commit does not claim to be the source commit embedded in the tested APK.

## Exact debug artifact

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Package: `dev.phosphor.mobil3.debug`
- Version: `2.0.0-debug`, code `2000000`
- SDK range: API 29 to 36
- ABI: arm64-v8a
- APK SHA-256: `582c57e5a0e29f9480be45a8832f5f40f114677ad93e14bdb428914562d46bf2`
- Signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`
- Installed base APK read-back SHA-256: `582c57e5a0e29f9480be45a8832f5f40f114677ad93e14bdb428914562d46bf2`

`dev/pm3` installed the APK on the explicit serial, read the installed base APK back from the phone, and verified both its exact bytes and signer. A clean final gate run rebuilt the same implementation commit and reproduced the same APK hash.

The native debug self-test passed:

```json
{"ok":true,"size":512,"segments":3999,"lit_pixels":42197,"pixel_fnv1a":"7e91d807fdb542eb","dsp_ms":0.400781,"total_ms":106.57619799999999}
```

Evidence:

- [`ba1871a-installed.png`](ba1871a-installed.png)
- [`ba1871a-selftest.png`](ba1871a-selftest.png)

## Orientation and input

- The S25 ran the app upright in landscape at 2340 by 1080.
- Window state reported `SCREEN_ORIENTATION_LANDSCAPE` with display rotation `ROTATION_90`.
- The corrected cardinal mapping sends sensor rotation 90 to reverse landscape and 270 to landscape. Unit tests cover every cardinal mapping.
- Portrait remained upright during the transition check.
- The source sheet and Android capture disclosure were usable with the corrected landscape mapping.

This closes the reported upside-down landscape regression on the exact tested implementation.

## Playback capture

The Android 14+ capture request defaulted to **Share entire screen**.

Grant path:

- Android granted MediaProjection to `dev.phosphor.mobil3.debug`.
- `CaptureService` ran as foreground-service type `mediaProjection` (`0x20`).
- The HUD changed to `src · capture`.

Lifecycle path:

- Force-stopping the package removed its PID, MediaProjection session, and `CaptureService`.
- Relaunch returned to `src · no source`.

Denial paths:

- Cancelling the projection disclosure left no MediaProjection session, no capture service, and no active source.
- Revoking `RECORD_AUDIO`, starting playback capture, and choosing **Don’t allow** stopped before projection. No MediaProjection session or capture service started.
- Microphone permission was restored after the denial proof.
- `POST_NOTIFICATIONS` is not requested by this product, so there is no notification-permission denial path to exercise.

Evidence:

- [`ba1871a-capture-prompt.png`](ba1871a-capture-prompt.png)
- [`ba1871a-capture-active.png`](ba1871a-capture-active.png)
- [`ba1871a-after-process-death.png`](ba1871a-after-process-death.png)
- [`ba1871a-projection-denied.png`](ba1871a-projection-denied.png)
- [`ba1871a-mic-permission.png`](ba1871a-mic-permission.png)
- [`ba1871a-mic-denied.png`](ba1871a-mic-denied.png)

## Tailscale relay

The saved relay endpoint was `interserve-linux:100.114.165.77:45777`. The phone was on Tailscale address `100.102.2.83`.

- The relay accepted the phone peer and negotiated audio without geometry.
- The selected source was `alsa_output.pci-0000_0b_00.4.analog-stereo.monitor`.
- A deterministic low-volume tone removed the HUD’s `no sound` state.
- The live HUD showed the remote source, approximately 1.6 Mb/s, and bridge buffer statistics.
- Disconnect returned the HUD to `src · no source` and removed the app-owned established socket.
- A fresh launch retained the saved endpoint but made no app-owned network connection until the user selected it.
- Relay traffic followed the Android and Tailscale route. No process-wide Wi-Fi or mobile bind was present.

Evidence:

- [`ba1871a-relay-audio-live.png`](ba1871a-relay-audio-live.png)
- [`ba1871a-relay-disconnected.png`](ba1871a-relay-disconnected.png)

## Multi-window

WMShell placed task 1234 in the right split-screen stage with bounds `[1166,0][2340,1080]`.

- The app stayed visible and resumed.
- Its PID remained `22945` before, during, and after the transition.
- Exiting split screen restored the same task to full-screen landscape.

Evidence:

- [`ba1871a-multiwindow.png`](ba1871a-multiwindow.png)
- [`ba1871a-multiwindow-restored.png`](ba1871a-multiwindow-restored.png)

## Final implementation gates

The final clean run used implementation commit `ba1871a51bfc8c8987f1aba0302d46ac6baebe32` and passed:

- Android: 76 unit tests, zero failures or errors; debug compilation, lint, and assembly.
- Mobile Rust: formatting, 34 tests, and strict Clippy.
- PC relay: formatting, 26 tests, and strict Clippy.
- `bash -n dev/pm3 scripts/*.sh`.
- `shellcheck dev/pm3 scripts/*.sh`.
- `scripts/test-pm3.sh`.
- `scripts/test-play-boundary.sh`.
- `scripts/check-play-boundary.sh source --json`.
- `scripts/test-release-gates.sh`.
- Protected archive SHA-256 verification.
- Append-only prefix verification for `docs/dev/PUBLIC-RELEASE-DIVERGENCE.md` against the rollback tag.

`scripts/ship-check.sh --json` measured 21 gates:

- 18 implementation gates green.
- 0 skipped.
- `signing.release` red because the approved direct-release and Play-upload identities are not provisioned.
- `provenance.release` red because the final `v2.0.0` tag is not approved or present.
- `release.bundle` red because it correctly depends on both blocked prerequisites.

This is the intended pre-production state. A required release artifact cannot false-green by skipping missing signing or provenance evidence.

## Boundaries and remaining external coverage

- Local `master` and `release/phosphor-2.0.0` were reconciled to the same implementation commit before this evidence-only receipt.
- No protected remote branch was pushed or rewritten.
- No final `v2.0.0` tag was created.
- No production signing identity was created, copied, or modified.
- `dev.phosphor.mobil3` and `dev.phosphor.mobil3.fortress` remain installed and untouched. Fortress was not uninstalled because that would delete its private data and requires explicit approval.
- The true screen-lock/keyguard callback was not exercised because the device has a real PIN and changing keyguard state would be intrusive. Process death and projection revocation were exercised independently.
- Android 10 and large-screen field coverage still require another device or emulator.
