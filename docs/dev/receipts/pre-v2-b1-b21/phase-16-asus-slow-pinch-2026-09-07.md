# ASUS slow pinch and physical settle timing

2026-09-07. ASUS_AI2202 Android14, explicit serial `NAAIB70036673ZC`. S25 excluded.
MUSIC remained 1/30. No source or audio playback was used for these gestures.

## Demonstrated failure and correction

The old pinch code replaced its reference distance every frame, even below its 0.001 scale threshold.
A 40px expansion over 240 injected moves therefore produced no gain change in the retained before-series.
The corrected accumulator keeps sub-threshold travel until it crosses that same threshold.
Blocked and Rebase frames reset the reference, so excluded travel cannot become a later jump.
Gain limits, VIEW LOCK, auto-gain ownership and 3D routing remain unchanged.

The shell-only version-three driver adds a validated tap followed immediately by 240 two-pointer moves.
It remains finite and outside the app. Injection timestamps are actual device uptime, not requested scheduling deadlines.
The existing Gradle fixture task compiled it. Host rejected-coordinate checks and the ASUS no-input probe passed.

## Installed candidate and gate

- APK SHA256: `40f1a8478d57213fd5d881244ced22012c76c35b46bb0bb11c02e790606cf984`.
- Debug signer: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- `dev/pm3 install` verified matching installed APK bytes and signer.
- 438 Android tests, lint, debug assembly and `checkEngine` passed.
- Six accumulator tests cover slow inward/outward motion, sample density, guard resets, jitter and invalid distances.
- An obsolete source assertion failed the first gate. It was updated to require the actual new reset wiring, then the full gate passed.

## Eight physical runs

The fixture opened the portrait overflow, closed it, and pinched the exposed stage at y800.
Each run supplied 241 observed two-pointer decisions. All eight produced gain growth after settling.
Gain progressed from 2.576 to 4.729037 across the series, with native telemetry truncation between runs.
Two runs contained additional one-pixel console movements. Each movement correctly restarted the per-card guard.
There were eleven rebases in total, all 336–342ms after the most recent card change.
Exact 332ms and 333ms samples were Blocked. No first Apply after a Rebase contained a deferred gain jump.
The earlier before-series also observed a 334ms Rebase. This after-series did not sample that exact millisecond.

The first analysis command omitted its required log argument. Raw input and observations were unaffected.
The original analyzer then rejected multiple rebases because it assumed a stationary console.
The corrected analyzer checks every interval against all three cards rather than requiring one rebase per run.
It permits only unchanged gain or the renderer's exact downward milli-unit telemetry truncation during a guarded interval.
One interval changed from 2.8559282 to the native readback 2.855. This is not unbounded numeric tolerance.

A later UI dump showed Settings and gain7, outside the recorded run window.
It does not invalidate or prove the recorded stage workflow. The capture itself contains no mounted Settings sheet.
Do not use that later dump as the measured end-state or overwrite later user tuning without checking ownership.

## Evidence and limits

Private evidence remains in `dev/scratch/asus-timeline-20260907T0113Z/`:
`gesture-overflow-before.log`, `gesture-overflow-series.log`, `overflow-*-after-input.jsonl`,
`check-gesture-v2.rb`, `gesture-series-analysis-v2.json`, and `pinch-fix-build-2.log`.

This proves the instrumented portrait overflow/console guard and slow two-pointer gain response.
It does not prove compositor scanout timing, every card/orientation, physical locked/3D behavior, or all B1/B18/B19 variants.
Final exact-commit installation, state restoration and the remaining rotation/auto-gain acceptance still belong to closeout.
