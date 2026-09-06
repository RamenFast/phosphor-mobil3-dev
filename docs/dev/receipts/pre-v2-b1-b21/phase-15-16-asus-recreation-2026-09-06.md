# ASUS moving-trace recreation and gesture continuation

2026-09-06, 23:37–23:48 UTC. This is bounded physical acceptance, not B1–B21 or release signoff.
The ASUS_AI2202 Android14 / API34 phone ran the unchanged installed implementation `6183aa8`.
Its APK SHA256 remains the previously verified `5d30d7e62d47ebdb7e67ef94bd71f7a2455fd524197699e2b3dc1626bc2e5dc3`.
No app rebuild or installation occurred. The S25 was not accessed.

## Device access and wake policy

Ben restored the USB cable and explicitly requested keep-awake settings at 23:31 UTC.
The ASUS USB route is authorized. Model and serial were checked before testing.
`stay_on_while_plugged_in` changed from 0 to 7 and `screensaver_enabled` from 1 to 0.
Android reported `mWakefulness=Awake` and `mStayOn=true` before and after testing.
The unplugged timeout remains 600000ms. No PIN, keyguard or lock policy changed.
These requested wake settings remain enabled. They are not temporary test settings.

## Moving trace through complete surface recreation

The retained 90-second stereo 440Hz quadrature fixture supplied deterministic geometry.
The real Android picker selected only the uniquely named owned file.
MUSIC remained 0/30. No microphone, capture, remote relay or PC audio was started.

The actual settings screen changed BEAM to approximately 22.
Preferences recorded `beam_energy=21.99764`, with FOCUS 0.30 and auto-gain enabled.
Automatic PiP was disabled through the real UI so Home caused full surface teardown.
Brightness was temporarily set to 30 with automatic mode off, then restored in an EXIT trap.

Six screenshots per phase measured the same 1000×1400 ROI at (40,300).
The red-channel fraction above 50 percent measures bright deposited trace, not calibrated panel luminance.
The first screenshot visibly contains the circle, not the idle center dot.

| Phase | Mean bright fraction | Min–max | Mean change |
|---|---:|---|---:|
| Baseline | 0.0077236917 | 0.00748143–0.00790500 | 0% |
| First recreation | 0.0076816683 | 0.00747929–0.00788929 | −0.5441% |
| Second recreation | 0.0078132117 | 0.00766500–0.00789214 | +1.1590% |

These are overlapping ranges and small mean changes, not identical pixels or a human visual verdict.
Native thread 30323 in app process 30254 recorded both complete sequences:

```text
16:40:41.842 surface torn down
16:40:43.452 surface up 1080x2400 density 2.75
16:40:51.696 surface torn down
16:40:53.339 surface up 1080x2400 density 2.75
```

Device logs use local time, UTC−7. Surface absence lasted 1.610s and 1.643s.
Platform session snapshots remained PLAYING at positions 3096, 15096 and 24096ms.
This closes the missing moving-trace/full-surface-recreation sample on the native ASUS runtime.
It does not prove Activity/process recreation, every mode/rotation/source variant or emulator translation.

## ASUS physical gestures

The retained finite shell fixture now requires explicit surface width and height.
Version 1 used fixed S25 bounds. Version 2 used the verified ASUS portrait dimensions, 1080×2400.
The no-input probe passed on Android14 and reported those dimensions.
All start and end coordinates are validated before the first event, including nonfinite rejection.
The wrapper build runs valid-corner and seven rejection checks against the same validator.
Compiler and test output now reaches Gradle's captured log instead of only its daemon output.

Actual settings enabled always-visible controls and disabled auto-gain. VIEW LOCK was already off.
All gestures lasted 500ms and emitted accepted two-pointer events. Persisted gain supplied the result:

| Case | Observed gain | Result |
|---|---|---|
| Free stage pinch out | 1.8332275 → 3.2801042 | Allowed |
| Second pointer begins at y2200, then leaves the 88dp bottom band | 3.2801042 → 3.2801042 | Rejected through all-up |
| Second pointer remains at y2110 inside the console margin | 3.9117644 → 3.9117644 | Blocked |
| Second pointer remains at y2020 outside that margin | 3.9117644 → 6.776893 | Allowed |

An earlier margin probe moved its second pointer from y2110 to y1900 and gain increased to 4.91112.
That is not a held-margin test: chrome exclusion can release after all pointers leave the margin.
The subsequent free sample reached the gain cap of 7. A free pinch inward reset it to 3.9117644 before the held-margin comparison.
Those exploratory results remain retained, not hidden or counted as failed product requirements.

All 17 existing StageGesturePolicy host tests passed, including the 332/333/334ms boundary.
**Exact physical 333ms remains unproved.** The shell fixture does not observe Compose's final layout/motion timestamp.
`StageGeometry` uses `SystemClock.uptimeMillis()` internally. A delay measured from the injected tap cannot establish that boundary.
No runtime administration endpoint, production timing hook or speculative gesture-policy change was added.

## Restoration and evidence

Both original `phosphor.prefs.xml` and `phosphor.runtime.xml` were restored byte-for-byte from the pretest archive.
The app was force-stopped. The test-created empty host file was removed after confirming it was absent from the baseline.
The owned WAV, input jar and UI dump were removed after host/device hash comparisons.
Automatic brightness mode returned to 1. Its live numeric value later adjusted to 48, as expected under automatic control.
MUSIC remains 0/30. Requested plugged-in wake and screensaver settings remain enabled.

Private evidence lives under `dev/scratch/asus-recreation-20260906T2337Z/`:

- `run-recreation.sh`, 18 `{base,recreate1,recreate2}-N.png` files, `trace-statistics.json`, lifecycle and session logs.
- `measured-phosphor-prefs.xml` and the real UI dumps under the earlier ASUS scratch directory.
- `run-gestures.sh`, `run-held-margin.sh`, per-case input JSONL and preference snapshots.
- `gesture-visible-build-tests.txt`, `driver-probe.jsonl`, baseline/after/restored preference archives and `final-state.txt`.

The initial post-measurement read used the wrong filename `tuning.xml` and returned an error.
That failed read remains in `measured-tuning.xml`. The corrected actual preference read is named above.

| Retained artifact | SHA256 |
|---|---|
| Circle WAV | `f44ed7b2e8f09be87fdbe629d21f5b2828b3ba27c4019f399c50425aed7a31de` |
| Tested and rebuilt gesture v2 jar | `759c43cf9a0dc1e68afe71fc650d2e69e6415c48906f9c54726710d60498299c` |

B8 remains unreproduced. Independent debugging workers still await explicit model/effort confirmation.
Exhaustive variants, fresh-Linux authorization, Ben's visual acceptance and release/signing/publication remain separate gates.
No push, release, Linux service action or change to `drift: 21` occurred.
