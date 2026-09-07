# Fresh ASUS local and rotation replay

2026-09-07, 04:40-04:51 UTC. Ben requested continuation after the earlier capture-route blocker.
This pass exercised the remaining local workflows on the installed exact debug APK, without controlling Spotify.
It replaces the prior-only evidence status for atomic IDs A1, C6, P2/P7 and L3 within the measured scope below.

## Tested identity and boundaries

- ASUS_AI2202, Android 14. ADB identity: [redacted]. S25 excluded.
- Implementation `743d8bb`, package `dev.phosphor.mobil3.debug`, version `2.0.0-debug`.
- APK SHA-256: `eba2a0bc433055dec0a01650e49e5df0c4e8318fc1b227d4c50f8d933a3b57e5`.
- Signer retained from supported install verification: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- MUSIC remained 1/30. Spotify reported a paused remote session at the initial check. No Spotify command or route transfer occurred.
- No production or fixture code changed. No workers, release operation, push, merge or Linux deployment occurred.

## Fresh requirement-to-result map

| Atomic requirement | Actual public-path check | Observed result |
|---|---|---|
| A1: local item gain reset | Real SOURCE folder picker and actual 48kHz PCM16 queue. Loud sine peak0.8, quiet sine peak0.1. | Loud settled at1.15. Newly selected quiet showed5.11, then6.00. A screenshot confirmed a visible trace. |
| A1: silent gain hold and EOF | Actual third silent WAV, not a mocked source. | Gain stayed6.00 while time advanced0:00 to0:06 with Pause shown. EOF showed0:30/0:30 and Play. |
| C6: local paused seek truth | Pause quiet playback, swipe the actual seek rail, inspect two system media-session dumps. | Both retained PAUSED, position9000ms, speed0 and update timestamp8231569. UI showed0:09 and Play. |
| P2/P7: inward/outward slow3D response without gain | Actual folder queue selected the90-second quadrature helix WAV. Four delayed240-step inward pinches, then four inverse pinches. | Gain stayed1.83 and playback reached0:40. Top trace edge771 to785 to768px. Left edge5 to23 to7px. Camera perspective changed and reversed. |
| L3: Android-dependent controls reject edits while locked | Read real accessibility ancestors, then physically tap both disabled controls. | Both ancestors were disabled. Labels remained SCOPE ROTATION locked and UI PLACEMENT follow. |
| L3: held import and unlock | Actual settings picker imported the validated five-setting landscape archive. Then Android autorotate changed0 to1. | UI reported imported5. All five stored values matched the fixture. Held screenshot stayed1080×2400 with LOCKED Activity request. Unlock produced2400×1080 with LANDSCAPE request. |

The retained `local2-check-results.rb` asserts every row against the actual XML, media dumps, preferences and screenshots.
It passed all six result groups in `local2-results.json`. This is analysis of real device outputs, not a substituted app harness.
The camera check uses focal perspective, not an incorrect uniform-shrink expectation. Its actual source played throughout the comparison.

Raw evidence remains in ignored `dev/scratch/asus-timeline-20260907T0113Z/`, with the `local2-` prefix.
The adjacent [SHA-256 manifest](phase-16-asus-local-replay-2026-09-07.sha256) pins the checks and evidence.

## Test navigation corrections

The initial direct-file picker was canceled without selecting a file. The accepted3D replay used the existing owned tree grant instead.
Back navigated within DocumentsUI before exiting. No unrelated file was opened or deleted.
An edge swipe dismissed settings, so the successful scroll used the center of its content.
The first disabled-ancestor checker had a Ruby syntax error and ran no assertions. The corrected checker passed before the taps.
These navigation and checker failures are not product failures or successful acceptance runs.

## Restoration and limits

- The owned tree grant was released. Sorted system URI permission entries then matched the pre-pass baseline exactly.
- Both original preference files compared byte-for-byte after restoration. The empty test-generated host file was removed.
- Original notification-listener cache, empty diagnostic log property and rotation0/0 matched the four-line baseline exactly.
- Requested stay-awake7, screensaver0 and unplugged timeout600000ms remain unchanged. MUSIC still reports1/30.
- Only owned WAV/archive/XML/JAR files were removed. The empty owned directory was removed without recursion.
- The app is stopped, source services are absent and projection is null. Private evidence and rollback artifacts remain retained.

These are fresh final-artifact observations after the requirement map, not rechecks of an older candidate recording.
Physical gravity/cardinal poses, every gesture deadline, live remote audio, capture transient variants and release acceptance remain separate.
Spotify remained remote at the availability check. Its capture-route restriction was not bypassed.
