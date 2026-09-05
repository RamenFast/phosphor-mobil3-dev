# Phase 2: B9 local seek and navigation

- Date: 2026-09-05, America/Los_Angeles.
- Status: VERIFY. Targeted responsiveness checks passed. The required logcat evidence remains unavailable.
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

**Blocked:** The phone's `/system/bin/logcat` runs a different executable and rejects standard logcat arguments.
**Evidence:** Both `adb logcat -d` and the explicit device binary failed. Android DropBox supplied the actual crash stack instead.
**Best current result:** Direct seeking and queue navigation remained responsive under the recorded stress runs.
**Next step:** Obtain working device logs or explicitly accept the recorded MediaSession, UI, and process-exit evidence as the phase substitute.
Final Phase 15 regression remains pending. B9 remains VERIFY in the receipt index.

## Redaction

This receipt omits literal device addresses, private media names, relay endpoints, and raw private logs.
