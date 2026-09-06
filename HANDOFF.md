# Handoff: S25 testing and four installed corrections

**Updated:** 2026-09-06. **Status:** partial device acceptance, not release-ready and not all B1-B21 passed.

## Read first

- [Final expanded phone pass and exact remaining gates](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-final-pass-2026-09-06.md).
- [Earlier continuation and its limits](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-continuation-2026-09-06.md).
- [Earlier phone receipt and per-card gaps](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-s25-2026-09-06.md).
- [Original execution plan](PRE-V2-B1-B21-EXECUTION-PLAN.md), especially Phases 15-17. Its old volume requirements are superseded.
- [Receipt index](docs/dev/receipts/pre-v2-b1-b21/README.md).

## Ben's current boundaries

Ben authorized comprehensive phone testing, then required quiet testing while sleeping. Keep Android MUSIC volume at **at most 15 percent**. It is currently **2/15, about 13 percent**. Do not restore the earlier higher volume. Keep PC audio silent. Do not manipulate the PIN/keyguard, publish, push, sign a release or deploy/restart Linux services under this phone-testing authorization.

The console volume slider is unwanted. It is removed, not moved elsewhere. Preserve the remaining UI design. The repeated Spotify play/pause-symbol and grid-visibility reports are priority requirements, not requests Ben should repeat.

## Installed now

- Mobile implementation `63fc7ef`, including final-item replay repair, volume removal `9b3cc62` and grid contract `9f65bd1`.
- Shared implementation `297e88b`.
- Debug package `dev.phosphor.mobil3.debug`, version `2.0.0-debug` (`2000000`).
- APK SHA256 `f5afd99617e7a1abf21246c612590212adeed3a82b588c6235e8448a68b48834`.
- Installed APK bytes and signer were verified through `dev/pm3 install`.
- Exact APK and original installed rollback are retained under ignored `dev/scratch/phone-acceptance-20260906T1720Z/`.

Four corrections are installed:
1. No console VOL row, percentage, spare row or app-owned volume adapter. The console contracts without restyling its other controls.
2. Shared minor/axis grid coefficients are 0.035/0.08. Three matched low-brightness captures improved minor-line contrast from 8 to 43 sRGB code values while the background stayed at 1. This is measured improvement, not Ben's visual acceptance.
3. SOURCE queue taps now reach the existing track-switch handler through the previously missing Media3 command. Actual selection of the nested left-only fixture passed.
4. Final-item playback now drains to native ENDED instead of pausing its tail early. Actual Play after completion restarts the item near zero.

## Verified and still open

419 Android JVM tests, lint, builds, 15 renderer library cases, 10 actual GPU integration cases, selected strict Clippy, source privacy and protected archive checks passed. Actual phone evidence covers five earlier-candidate mic transitions, local seek stress, nested/corrupt file handling, fixed queue selection, left/stereo dBFS, a silent-gap gain hold, stable-session Spotify control/reattachment, permission recovery, temporary-allowed automatic PiP, existing relay connect/disconnect, and capture/mic recents removal.

**B8 is not closed.** Stable Spotify checks matched the icon to the session, but they did not reproduce Ben's reported inversion. A later final-candidate attempt lost the external Spotify session. No speculative glyph fix was made. Later next-track testing observed real Spotify and mirrored BUFFERING then PLAYING. Stable controls still matched. The original inversion remains unconfirmed, not fixed.

Paused local seek position had an intermittent zero observation, although four later paused seeks published correct positions. Final-item replay is now repaired. Additional right-only dBFS, style persistence, PiP on/off/manual entry, local recents removal, and main/PiP wake beyond a shortened timeout passed bounded checks. A suspected PiP-return defect failed matched-control diagnosis, so the experimental patch was discarded. The later pass verified double-tap on/off, slider-lane samples, all 42 exported settings through real import and checksum rejection, all-source linger/removal, and bounded trace stability. Exact multi-pointer timing, full surface recreation, exhaustive variants, clean-emulator defaults and fresh-Linux acceptance remain open. The input driver was killed before injection; prior emulator renderer failures remain documented. The latest receipt names each limitation. Keep `drift: 21`.

## Restoration

Original tuning was restored byte-for-byte except retaining the grid's already-enabled state. Saved relay hosts are byte-identical. Test-only controls, double-tap, PiP auto-entry, grid-data and mode changes were restored. Android's original PiP denial and notification-listener membership were restored. Automatic brightness, timeout, screensaver and rotation policy were restored. Automatic brightness may adjust its live numeric value.

Phone fixture files and owned diagnostic processes/files are cleaned up. The app was verified in no-source state and left in the background without PiP. MUSIC remains 2/15. Backups and raw evidence are private and retained. The final 240-second diagnostic window replayed 82,861 records exactly, with no fatal/ANR/panic candidates. Earlier failed/truncated logging attempts are explicitly not counted as complete evidence.

## Release boundaries unchanged

Approved signing inputs, final-tag approval and the dependent release bundle remain external blockers. No push, tag, signing, publication, production/Fortress removal or Play submission occurred. Historical August release evidence remains in `docs/dev/receipts/primetime-cleanup/`.
