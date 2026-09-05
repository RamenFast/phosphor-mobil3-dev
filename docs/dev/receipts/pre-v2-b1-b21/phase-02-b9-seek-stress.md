# Phase 2: B9 local seek and navigation

- Date: 2026-09-05, America/Los_Angeles.
- Status: PASS for Phase 2. The logged rerun below closes task 2.5. B9 remains VERIFY until final regression.
- Private issue: #1.
- Mobile source: `e289a74c02b7e757f480ffc8636a4d0583ac6f37`.
- Sibling source: `c0cf967c4afa0aa7bf907dee915480aed8bd0530`.
- Device: S25, SM-S931U, Android 16, API 36, build `S931USQSBCZF5_OYNBCZF5`.
- ADB serial: [redacted], authorized wireless connection.
- Package: `dev.phosphor.mobil3.debug`, `2.0.0-debug`, code `2000000`.
- Local and installed APK SHA-256: `f953f1b61469500d3a3c01366f23c73d8c5db65c430a8d68f69f9f17b2de76dd`.
- Signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.

## Starting state and installation

The phone was unlocked, unplugged, and already authorized. The installed APK matched the saved Phase 0 baseline.
The executor backed up current preferences before installing the pinned Phase 2 artifact with `dev/pm3 --serial "$D" install "$APK"`.
The command exited 0 and verified the installed APK bytes and signer. All preference-file hashes matched immediately after installation.
No app data was cleared. No release, tag, remote write, or production-package operation occurred.

## Automated evidence

The unchanged implementation commit records 35 passing Rust tests, passing Android unit tests, rustfmt, diff checks, and three passing reviews.
Those checks were not rerun during this device-only session. The pinned APK hash was checked again before installation.

## Live procedure and observations

1. Open a local WAV through Android's document picker. Its displayed duration was 4:33.
2. Pause playback, drag the in-app seek control, then resume. The resumed position moved to the selected region.
3. Run eight pause/scrub/resume attempts from 00:19:01 through 00:19:52.
4. Seven drags changed position. The first 20-pixel drag did not cross the gesture threshold and is not counted as a successful seek.
5. Open a three-item, nonrecursive local folder queue through Android's tree picker.
6. Run eight paused next/next/previous batches from 00:23:59 through 00:24:48, using 24 visible-control taps.
7. Every queue batch ended with item 1 playing and the same process alive. End-of-queue next taps include expected no-ops.
8. Resume once more and read the UI. Playback position advanced, controls responded, and the screenshot showed a lit trace.

All stress checkpoints retained the same app PID. Android's process-exit record showed no new crash or ANR during either stress run.
The observed runs did not reproduce the B9 freeze. They do not prove every timing race impossible.

## Separate observations

- At 00:17:00, before stress testing, the service crashed during the file-picker transition after capture denial.
  Android DropBox identifies `Missing implementation to handle COMMAND_RELEASE` in `PlaybackService.onDestroy`.
  Phase 6 task 6.3 already owns this fix. The activity recovered and local playback started in a new process.
- A paused local seek temporarily reports position 0. Resuming restores the requested destination. This is a position-reporting defect, not the tested freeze.
- After switching from capture to the local queue, the status band said `no source` despite playing audio state and a lit trace.
  Source-state truth needs a separate check. A zero segment sample alone did not establish a dark scope.
- Recursive folders, corrupt entries, and B4 acceptance were not tested.

## Evidence

Raw artifacts remain in the ignored `dev/scratch/pre-v2-20260829T072841Z/phase-02/s25-20260905/` directory.
The two shell scripts there contain the exact coordinate sequences, waits, media commands, and state queries.

| Artifact | SHA-256 |
|---|---|
| `install.json` | `73ca7240add3f89cb1e63b87f9d33c922e78bf2a88b3e14492c501ff1dc03e6b` |
| `direct-seek-stress.log` | `4181e3adebddccbea1a2b6e2a4c4cf942076778e105b7c181bcc6be5e97b05d7` |
| `queue-stress.log` | `9f54bb00f019f08e74f693b0b74bdc5aff76a0972170b99f8db1d1866da114f1` |
| `exit-info-after.txt` | `d984cd8ef2d6f03e3dee0a57141b1e909a0e5617c8bd14d9f62089dcf11b8e4c` |
| `crash-today.txt` | `0b4307230b999877e4278e2a18c5a207125483a61f15d0dc055d5fd20099381a` |

## Restoration and remaining evidence

Playback was paused after testing. Screen timeout, screensaver, brightness, and brightness mode were unchanged.
User tuning and saved-relay preference files remained byte-identical. Android bookkeeping and runtime preference files changed during normal use.
The saved Phase 0 APK remains the rollback artifact. No rollback was needed.

Original logging blocker, resolved by the later rerun below:

**Blocked:** The phone's `/system/bin/logcat` runs a different executable and rejects standard logcat arguments.
**Evidence:** Both `adb logcat -d` and the explicit device binary failed. Android DropBox supplied the actual crash stack instead.
**Best current result:** Direct seeking and queue navigation remained responsive under the recorded stress runs.
**Next step:** Obtain working device logs or explicitly accept the recorded MediaSession, UI, and process-exit evidence as the phase substitute.
Final Phase 15 regression remains pending. B9 remains VERIFY in the receipt index.

## Redaction

This receipt omits literal device addresses, private media names, relay endpoints, and raw private logs.

## Logged rerun, 00:49:00 through 00:51:06

The package, APK, signer, sibling source, and running app process remained unchanged.
The root reran `cargo test --manifest-path rust/Cargo.toml --locked`: 35 tests passed.
The root reran `./gradlew --no-daemon :app:testDebugUnitTest` after sourcing `scripts/env.sh`: exit 0.

### Logging recovery

A temporary 17 KB arm64 diagnostic read Android's main, system, and crash buffers directly from logd.
It used the existing shell UID and local `SOCK_SEQPACKET` reader socket. No system file, mount, privilege, or app code changed.
This is actual Android log-buffer evidence, not output from the affected stock `logcat` command.
The helper remained an ignored test fixture, not a product sidecar or installed app feature.

The root verified a unique `PhosphorB9` marker in a 100-record finite dump before testing.
The root also reran 215 parser checks under both a strict host build and ASan/UBSan, plus 17 CLI/error-path checks.
The full 150-second follow captured all PIDs, without a text filter, and ended normally with 77,993 valid records.
Raw packets replayed on the host to byte-identical text. No malformed record, premature EOF, or reader timeout occurred.
All test markers, the final trace marker, and the end marker appeared in order.
The reviewed window contained no application error/fatal record, AndroidRuntime/DEBUG crash report, ANR, panic, or reported log-reader loss.

### Requirement-linked observations

| Check | Observed result |
|---|---|
| Pause long enough to fill the audible ring, then seek | Eight individually marked drags each reached one native deck reopen and resumed at a changed position. |
| Replace pending seek work | Five rapid drags produced three native reopens. The final request opened at 14.220 seconds, and the resumed session reported 14.274 seconds. |
| Navigate while paused | Eight next/next/previous batches produced item 2 then item 1 native opens. Every batch ended with item 1 PLAYING. The second next included an expected boundary no-op. |
| Keep the app responsive | The process stayed unchanged. Controls responded, final position advanced, and `final-live.png` showed a lit trace. |
| Detect crashes or ANRs | The process-exit history was byte-identical before and after stress. The complete log window contained no matching failure. |
| Preserve user state | Tuning and saved-relay preference hashes matched their pre-test values. UI placement returned to follow. Both media sessions were paused and projection was inactive. |

An earlier logged attempt used stale portrait coordinates after the app rotated its chrome.
Native logs showed that those drags did not seek. That attempt was stopped and is not counted as B9 acceptance.
The root temporarily enabled the existing UI PLACEMENT lock, verified the geometry, and ran the corrected test above.
The root then restored the toggle through Settings and proved the complete tuning XML byte-identical.

Screen timeout, screensaver, and automatic-brightness mode were unchanged.
Automatic brightness readback changed from 4 to 15 without a brightness-setting write. This is not a fixed-brightness B5 test.
The root removed the temporary device reader, both raw-log files, and the generated UI dump after saving private evidence.
The pre-existing logcat override was left untouched. Disk space remained approximately 19 GiB available.

### Rerun evidence

Artifacts below are inside the original private evidence directory's `log-reader/` subdirectory.
`paused-ring-rerun.sh`, `reader.c`, and `README.md` preserve exact commands, wire-layout assumptions, and reproduction steps.

| Artifact | SHA-256 |
|---|---|
| `pinned-rerun.log` | `9e39bcc09f51e830c1bd18c8e983576ce68308b8f5029762037b0423e0539db7` |
| `pinned-logd.txt` | `ef8ecdf0ccf1b42d33dda8cf450a38f5f9dfbeb8acedc0a8bc909102c6efc9fb` |
| `pinned.raw` | `f17238dc981733ea054e7e49df763df4c15b94849e79bfd6ac49a46a86a9b03c` |
| `replay-status.txt` | `6e107ff8ce22864f31e58a81304c93a9a30317174135dbcf4b0aaccee830dfff` |
| `final-live.png` | `6eb15d82f9dfca3465e48a398d1a64f79e2002df2d2c98f61150d0b1817336fe` |
| `restored-settings.txt` | `e1d40ab75680bb2e526656418dd8ef3f35571380b67112ba1108c2bf48fe8b24` |
| `cleanup.txt` | `b699ac657e3caf54e9f18c624b291b32c0c969defe6120151b30bc5c701a36ab` |
| `root-host-checks.txt` | `bdb0555442e4b8a619acf657d3b34bda5f8c599833d20bff84454397c02b1572` |
| `rust-tests.log` | `b117ee4a032e36d22978c2b56e7345afd63b3e79249b3fe208d8fb08ababe370` |
| `android-unit-tests.log` | `d210e16840109d9e875986833ec8cd1c87bab7d62c6599fd8b7286059471cbea` |

Reader source SHA-256: `4fd64cfc93fc52dc7f49556ba429ded309703a32d9a893af1996c09dff60938b`.
Executed reader SHA-256: `c60ff2e5bf39c837a5106f93e58cd6e22392b6fbeb02fa66cb6639635c963ea5`.

Phase 2 task 2.5 now passes. Phase 3 can proceed. Final Phase 15 regression and the separate observations above remain open.
