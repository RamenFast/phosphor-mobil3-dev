# Handoff: mobile expansion execution

**Updated:** 2026-09-08, 06:58 UTC. **Status:** section 1 accepted at `3e5e00e`, independent score 9/10. Reviewed stereo candidate `06f84e2` remains installed. Its SYSTEM trial initialized 48 kHz two-channel PCM, then stopped cleanly at the unchanged 100 ms queue guard. Corrected `15bcbe5` now has a clean isolated dual-APK build and retained hashes, but is not installed while unrelated music plays. Corrected R02 HUD passes 505 Android unit tests, lint, native integration, and 79 native host tests. Its source review reached 8/10, and the separate callback addendum closes the remaining source defect. Neither stereo nor HUD has hardware acceptance.

## Read first

- Start with the canonical [MOBILE-EXPANSION-PLAN.md](MOBILE-EXPANSION-PLAN.md), [active expansion contract](spec/EXPANSION.md), and [single execution ledger](docs/plans/mobile-expansion/EXECUTION.md).
- Ben approved implementation and reversible S25 work at 00:18:19 UTC. This supersedes the prior S25 exclusion for this task. Preserve unrelated ASUS volume, PC-audio, signing, publication, and irreversible-action boundaries.
- Ben's 02:47 UTC boundary forbids writes to `/system`, including `/system/bin`, `/vendor`, and boot/vbmeta partitions through any path. Reads are allowed. No remount, flash or bootloader change. Use only installed app code and owned private data for the helper.
- Installed app is clean `06f84e2eb7da46c758e9f8b388c3537e6c67905d`, APK SHA256 `4a370170cda5410caf8abc82d8cd9a997fd0777c718cbd1ba31c2cd3aa9127fa`. Both app and androidTest APKs were built together and hashed before installation. At05:39 installed readback and signer matched. Preferences remained byte-identical to baseline. The first stereo SYSTEM trial stopped cleanly at its100ms monitor queue bound. Its two-channel initialization is not independent stereo acceptance. See [exact stereo receipts](docs/plans/mobile-expansion/section-02-stereo-trials.md). Historical `ccee7c8` trials below are superseded as installed state only.
- The installed PIE obtains existing KernelSU authority, keeps the mount namespace, reads trusted platform classpaths and loads sealed fd6 DEX. [Earlier a93 feasibility](docs/plans/mobile-expansion/section-02-helper-trials.md) passed three997Hz captures, same-process repeat, actual parent-death cleanup and recovery. Those exact protocol1 receipts do not accept the new product. Physical audibility remains unproven.
- Ben clarified at04:17 and04:21 that true stereo is required and SoundCloud is the smoke test. He reports SoundCloud fails standard capture while Spotify works. Do not accept duplicated mono or generic unsupported labels. Preserve sample-rate fidelity. Existing48/96/192kHz settings are beam reconstruction over48kHz input, not capture-rate negotiation. Report actual source and reconstruction rates separately.
- The protocol2 product uses shared root/standard retirement, actual specialUse FGS, local-only authorization and fixed debug SYSTEM/NONE/TONE checks. Its clean build passed477 Android tests,26 native tests,207 Java assertions, lint and engine checks. Actual SYSTEM failed its PCM assertion, with cleanup/preservation passing. NONE measured80128silentframes, exposing the finite-mode overrun, and passed helper/policy/service cleanup. Its overall guard failed on the external volume change. The reviewed stereo checkpoint corrected failure-aggregate retention and exact finite read bounds, without weakening assertions. Those corrections are installed in`06f84e2`, but their product checks have not been repeated.
- Ben confirmed Astra/high routing at01:11:40. Section1 reviews scored7/7/9. Poodle and Nautilus released HUD implementation and correction receipts, then stopped. The coordinator independently passed the [HUD integration gate](docs/plans/mobile-expansion/section-04-hud-validation.md). Dragon's immutable reviews scored6/8 and separately assess the final callback correction. Hare's [R13 HOLD audit](docs/plans/mobile-expansion/section-05-hold-research.md) and Vole's [stereo correction addendum](docs/plans/mobile-expansion/section-02-stereo-review-addendum-03.md) remain unchanged. Coordinator owns device actions, the only Gradle slot, integration and commits. Two live workers maximum, one implementation writer. Do not repeat the beacon.
- Ben required minimal added latency at04:38. Measure against normal playback on the same output and verify alignment with heard music. Current re-render latency is unmeasured. The [stereo research](docs/plans/mobile-expansion/section-02-stereo-research.md) distinguishes combined-route restrictions from a scoped LOOP_BACK monitor candidate. Direct tee mutation remains unexecuted while safe ownership is unresolved. The SoundCloud installed manifest explicitly opts out of projection capture, but actual root stereo support remains untested.
- At04:56 and04:58 Ben authorized an honest root-ready authorization surface if bounded stereo investigation remains blocked. Keep encouraging copy and working authorization, without claiming stereo audio. Continue independent expansion work. The [finite stereo contract](docs/plans/mobile-expansion/section-02-stereo-probe.md), [direct tee audit](docs/plans/mobile-expansion/section-02-direct-tee-audit.md) and [native policy addendum](docs/plans/mobile-expansion/section-02-native-policy-audit.md) preserve current evidence and limits.
- [Exact stereo freeze and trials](docs/plans/mobile-expansion/section-02-stereo-trials.md) records the installed experiment and the newer clean `15bcbe5` dual-artifact candidate. App SHA256 is `4708959a55ef2c3ccac0e3ea97c29ce2b8db7cf2a7294f39c8ebf6913c7c2e86`. Companion SHA256 is `611000a6516dbdb76ada7f984e43ea2d1297dec94d9496ffc8009545d2829903`. That reviewed stereo-only candidate is retained, not installed. At06:54 bounded read-only preflight still found active unrelated MEDIA playback, no policy mix, and byte-identical baseline preferences. Do not interrupt it. After a fresh quiet preflight, use the exact retained candidate and reviewed v2 SYSTEM runner. R09 awaits accepted input/clock evidence. R02 source is ready for its clean freeze after final review, not a device success.
- Historical muted-speaker receipts are superseded by the current Bluetooth route above. Existing `no_backup` is Android-managed 0771 and must not be chmodded. New helper directories alone use 0700. Preference archives still match the original baseline byte-for-byte.
- The [fixed debug authorization probe](docs/plans/mobile-expansion/section-02-authorization.md) distinguishes normal-app root execution from the observed manager grant and failed `run-as` lookup. It cannot execute caller commands or capture audio.

## Previous repair receipts

- **Fresh local continuation:** [04:40 ASUS local/rotation replay](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-local-replay-2026-09-07.md). Exact installed743d8bb passed actual gain reset, silence/EOF, paused seek, reversible3D perspective, disabled controls and held import/unlock. These now have fresh post-mapping device evidence. Phone state is restored. Spotify stayed remote and was not controlled.

- **Post-mapping rerun:** The atomic receipt now reports every ID after a forced full regression and new physical checks. Slow gain again rose1.833 to2.0023825 without deferred travel. Three Activity replacements again retained one listener. Fresh Spotify transport replay is blocked: its actual Connect route is playing on the excluded S25. Do not transfer or pause that playback. Earlier capture evidence remains valid, not a fresh pass. ASUS test state is restored.

- **Atomic traceability:** [47 requirements and changed outputs](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-atomic-traceability-2026-09-07.md). Each has a concrete check and observed result. The additional real-app default-off check passed with a VERBOSE reader. Host fixture failure returned its expected error envelope. Phone preferences were restored again. Source-only and unobserved live paths remain labeled.

- **Final-artifact audit:** [Requirement-level real-path checks and exact cleanup](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-final-artifact-audit-2026-09-07.md). The installed743d8bb replay produced ten capture commands, ten glyph changes and zero premature changes. Source-sheet timing, slow gain, 3D perspective, VIEW LOCK, paused seek and listener retirement were checked on that artifact. Remaining unobserved paths are explicit.

- **Final installed checkpoint:** [Local gain, paused seek, held rotation import and exact restoration](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-final-checkpoint-2026-09-07.md). Source743d8bb is installed and readback verified. Temporary phone state is restored. Remaining scenario and human/release gates are explicit.

- **Latest continuation:** [Activity sensor leak reproduced and corrected](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-sensor-retirement-2026-09-07.md). That tested candidate is superseded by the exact installed build below.440 Android tests pass. Ben confirmed exclusive ASUS ownership and autonomous takeover.

- **2026-09-07 slow-pinch checkpoint:** [Eight measured runs and correction](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-slow-pinch-2026-09-07.md). Candidate SHA40f1a847 is installed, with capture fix61e0630 and slow-pinch accumulation. 438 tests and lint pass. Temporary state and remaining acceptance are not closed.

- **2026-09-07 continuation:** [Capture prediction race reproduced and corrected](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-capture-race-2026-09-07.md). Five premature glyph flips before the fix, zero across seven routed commands afterward. This supersedes the blanket “unreproduced” description for that transient failure only. Gesture and other acceptance work is ongoing.
- **Current volume override:** Ben set ASUS MUSIC to **1/30** and asked that it not be raised. Do not restore historical 0/30. S25 exclusion and requested keep-awake remain binding.

- [Real ASUS Spotify capture and permission restoration](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-spotify-2026-09-07.md). Repeated source/mirror/glyph agreement passed. B8 remains unreproduced, not fixed.
- [All eleven grid modes, asymmetric trace angles and raw channels](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-mode-grid-2026-09-07.md). Pixel-based mode checks and left/right labels passed on ASUS, with exact restoration.
- [ASUS moving-trace recreation and version 2 physical gestures](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-asus-recreation-2026-09-06.md). Two complete surface cycles and held-margin checks passed. Exact physical 333ms remains open.
- [ASUS clean-start acceptance and two installed repairs](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-asus-2026-09-06.md).
- [Gesture-driver repair and actual native surface recreation](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-gesture-recovery-2026-09-06.md).
- [Final expanded phone pass and exact remaining gates](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-final-pass-2026-09-06.md).
- [Earlier continuation and its limits](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-continuation-2026-09-06.md).
- [Earlier phone receipt and per-card gaps](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-s25-2026-09-06.md).
- [Original execution plan](PRE-V2-B1-B21-EXECUTION-PLAN.md), especially Phases 15-17. Its old volume requirements are superseded.
- [Receipt index](docs/dev/receipts/pre-v2-b1-b21/README.md).

## Previous ASUS session boundaries

The following records the earlier ASUS repair session. Its S25 exclusion is superseded only for the approved expansion above. The historical device receipts remain evidence for their exact artifacts, not new expansion acceptance.

**The S25 is excluded from further testing.** Ben supplied an ASUS_AI2202 Android14 replacement. The current verified USB serial is `NAAIB70036673ZC`. ASUS MUSIC is **1/30**, superseding the older muted baseline. No Phosphor package existed before this testing session, but the debug package is now installed.

Keep ASUS MUSIC at **1/30 or lower**, with no increase. The excluded S25 was left at 2/15. Keep PC audio silent. Do not manipulate the PIN/keyguard, publish, push, sign a release or deploy/restart Linux services under this phone-testing authorization.

USB access resumed at 23:32 UTC. Ben explicitly requested persistent plugged-in wake settings.
ASUS now has `stay_on_while_plugged_in=7`, `screensaver_enabled=0` and its unchanged 600000ms unplugged timeout.
Do not restore those two requested settings as if they were temporary tests.
The current continuation restored both original preference files byte-for-byte and stopped the app. Debug observations are disabled and owned test artifacts are removed.
Ben resolved the ownership question at02:25UTC: the ASUS is a dedicated test phone and Jcode is its only operator. Retake control autonomously. Exact installation and temporary-state restoration are complete for this checkpoint.
Older S25 narratives below are historical. Their moving-trace recreation gap is superseded by the latest ASUS receipt.

The console volume slider is unwanted. It is removed, not moved elsewhere. Preserve the remaining UI design. The repeated Spotify play/pause-symbol and grid-visibility reports are priority requirements, not requests Ben should repeat.

## Installed now on ASUS

- Implementation `743d8bb`, including observed capture truth `61e0630` and slow pinch `18569be`.
- Package `dev.phosphor.mobil3.debug`, version `2.0.0-debug`.
- Installed APK SHA256: `eba2a0bc433055dec0a01650e49e5df0c4e8318fc1b227d4c50f8d933a3b57e5`.
- Exact retained APK: `dev/scratch/asus-timeline-20260907T0113Z/final-743d8bb.apk`.
- `dev/pm3 install` verified matching installed bytes and signer. Exact-source Android gate passed 440 tests, lint, assembly and checkEngine.
- Earlier host-native 73, relay 43, mocked pm3, privacy and isolated release-gate fixtures passed. No deployment or release occurred.
- Real source-item gain, silence hold, paused seek, disabled rotation controls, held import and unlock passed their named checks.
- Final state: both baseline preference files restored exactly, app stopped, no source services/projection, MUSIC 1/30, requested wake settings preserved.
- Owned test-tree permission, phone fixtures and opt-in diagnostics are removed. Three older URI grants remain unchanged.
- Raw evidence and rollback stay private. This is not all B1-B21 acceptance or release approval.

## Previous ASUS checkpoint, superseded above

- Implementation `6183aa8`, including `1a0f6b9`. These add the LIGHT fast-TIMER guard repair and complete untouched-control exports.
- Debug package `dev.phosphor.mobil3.debug`, version `2.0.0-debug`.
- APK SHA256 `5d30d7e62d47ebdb7e67ef94bd71f7a2455fd524197699e2b3dc1626bc2e5dc3`.
- Exact retained APK: `dev/scratch/asus-acceptance-20260906T2051Z/snapshot-committed.apk`.
- All 426 Android tests and lint passed. Actual warning/recovery and 41-key mutation/import passed. Native logs confirmed safe timer behavior and custom-color reset.
- Final ASUS state: clean effective defaults, no source, app stopped, MUSIC 0/30. Private backups and receipts retained.

## Previous S25 installation, unchanged

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

The 20:27 UTC continuation diagnosed the input-driver exit137 as an Android16 InputManager initialization exception.
Its corrected retained fixture passed a no-input probe and four physical two-pointer checks.
A complete native surface destruction/recreation sequence now has replay-verified logs, but moving-trace luminance through that sequence remains unproven.
The exact 333ms physical boundary, B8 inversion and emulator failures remain open. No app runtime patch or new APK was needed for these checks.
Original tuning and hosts were restored byte-for-byte. No workers ran because routing confirmation remains unanswered.

Approved signing inputs, final-tag approval and the dependent release bundle remain external blockers. No push, tag, signing, publication, production/Fortress removal or Play submission occurred. Historical August release evidence remains in `docs/dev/receipts/primetime-cleanup/`.
