# ASUS mode-grid and channel rendering checks

2026-09-06 23:53 UTC through 2026-09-07 00:03 UTC.
ASUS_AI2202, Android14, native Vulkan, 1080×2400 portrait. Installed implementation remains `6183aa8`.
This is additional B20 device evidence, not overall B1–B21 or release approval. S25 was not accessed.

## All eleven grid modes

The real SETTINGS controls enabled GRID and always-visible controls.
The real MODE picker selected xy, xy45, swirl, dots, 3d, helix, wave, ring, spec, radial and tunnel.
Each settled mode received a screenshot before reopening the picker. Its live HUD label confirmed the selected mode.

The measurement uses a 1000×800 ROI at (40,300), above the idle trace and below the HUD.
Pixels with red intensity at least 20 are grid candidates. Five-pixel neighbor matches distinguish axis-aligned and diagonal lines.
The expected orientation must score more than three times the opposite orientation, with at least 1000 candidate pixels.
Synthetic Cartesian and diagonal fixtures first passed the classifier's self-check. They do not substitute for the phone screenshots.

| Real mode | Candidate pixels | Axis score | Diagonal score | Result |
|---|---:|---:|---:|---|
| xy45 | 14946 | 0.010438 | 0.502810 | Diagonal, PASS |
| Each of the other ten modes | 13194 | 0.502122 | 0.007882 | Cartesian, PASS |

All ten Cartesian ROIs gave the same scores. This proves the actual grid switches back after Xy45, not merely a label change.

An initial evidence check incorrectly expected mode preferences to update immediately after each tap.
All preference snapshots still contained mode1. Source inspection showed mode applies immediately to UI/native state but snapshots in `onStop`.
The check was corrected to use the actual live HUD labels. No persistence fix or product failure is claimed from that invalid test assumption.

## Asymmetric trace and independent raw meters

The retained left-only and right-only PCM fixtures were each extended from 15 to 90 seconds by repeating their sample data six times.
Both are stereo PCM16, 48000Hz. The active channel peaks at 3277/32768, approximately 0.100 and −20.0dBFS.
The other channel is exactly zero. Calculated RMS is approximately −23.0104dBFS, which is distinct from the displayed raw peak amplitude.
The real Android file picker selected only the uniquely named owned files. Audio remained muted at MUSIC0/30.

The bright-trace principal axis was measured from red pixels at least 200 in a 1000×1000 ROI at (40,650).
The acceptance tolerance was three degrees. Screen coordinates increase downward.

| Fixture/mode | Bright pixels | Measured axis | Expected |
|---|---:|---:|---:|
| Left / xy | 3524 | 0° | 0° |
| Left / xy45 | 3736 | −45° | −45° |
| Right / xy | 3524 | 90° | 90° |
| Right / xy45 | 3746 | 44.999989° | 45° |

Both trace and grid obey the Xy45 rotation contract. These are real native screenshots, not a copied renderer test.
Settled UI trees also showed L0.100/−20.0dBFS with R0.000/−∞ for the left fixture, and the inverse for the right fixture.
Earlier snapshots during each source startup showed `no data`; they were retained and not counted as the settled meter checks.
Some UI dumps sampled a zero-segment frame while adjacent screenshots had an active trace. No continuity claim is inferred from those dumps.

Disabling GRID DATA removed both raw-meter rows from the actual UI tree.
Disabling GRID left a clear 1000×400 region at (40,300) with maximum red intensity1, confirming no grid remained there.

## Restoration and limits

Both baseline preference files were restored byte-for-byte. The app was force-stopped, with no source active and MUSIC0/30.
The two test WAVs and UI dump were removed after device/host hash comparisons. The test-created empty host file was removed.
Ben's plugged-in stay-awake and disabled screensaver settings remain in effect. No display, lock, release or Linux-service change occurred.

Private evidence is under `dev/scratch/asus-grid-modes-20260906T2353Z/`:
`run-mode-grid.sh`, `measure-grid.rb`, eleven grid screenshots, four asymmetric screenshots,
`grid-orientation-results.json`, `trace-angles.json`, `channel-fixtures.json`, and baseline/after/restored preference archives.
UI trees are retained in the earlier ASUS scratch directory with `grid-`, `left-` and `right-` prefixes.
The first right-source tap expected `open file…`; the active source uses `✓ open file…`. The helper stopped on that mismatch and then used the actual label.

Extended WAV hashes:

- Left: `f0de12cd1967f3f9b1871e6797d353f29ea8a1c2af6d4502a5cf7883f4e0deda`
- Right: `fe0a6530b31baa6170c753fce2a07dc785f403a848bba5761aa96202803256c7`

Remaining: system-lock/import/cardinal presentation variants, broader source/item auto-gain resets, translated-emulator failures,
exact physical333ms timing, unreproduced B8 and separate human/release gates. Drift remains21.
