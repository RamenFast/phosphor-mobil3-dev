# One-tap microphone and landscape verification

Auditor for Prime, 2026-09-26. Primary ASUS `NAAIB70036673ZC` only.
No uninstall, clear-data, synthetic permission grant, connectedAndroidTest, or commit.

## Task 3: slice i, one-tap inputs

Installed with pm3 and verified by installed-APK readback.
APK SHA-256: `2a0f3dcd1fac07b10e6c0b06274e386524b081a0c1cc6a3a8bd9fbcf83e1d078`.
Evidence: `build/transpose/captures/mic-one-tap/`.

The phone exposes two built-in input choices:

- A, `built-in`: audio-policy device port 19, `AUDIO_DEVICE_IN_BUILTIN_MIC`, address `bottom`.
- B, `built-in · second`: port 21, `AUDIO_DEVICE_IN_BACK_MIC`, address `back`.

Checks:

1. Tap idle non-selected B once. Its row shows stop. One unsilenced MIC recorder starts.
   Audio policy confirms both preferred and routed port 21.
2. Tap B again. The recorder stops.
3. Start A. Audio policy confirms port 19. Tap B while A runs. The route changes to port 21.
4. With real MediaProjection consent, everything-playing capture and include mic, switch A to B.
   The remote-submix route remains present while the microphone preferred and routed port changes 19 to 21.
5. The first NewPipe track reached its end during preparation. Restart it and repeat the mixed switch.
   `repeat-switch-newpipe-playing.txt` confirms NewPipe PLAYING for the repeated check.
   `repeat-mix-A-policy.txt` and `repeat-mix-B-policy.txt` show the actual route change.

The mixed capture recorder remains active, but dumpsys audio reports `no config` for that recorder in this run.
The audio-policy dump still reports its remote-submix route. The microphone configuration is present and unsilenced.
This task proves input switching, not a new sample-level capture-quality measurement.

Cleanup: include mic off, capture stopped, NewPipe PAUSED at STREAM_MUSIC step 1, app stopped, no active recorders.
`phosphor.prefs.xml` matches the pre-task XML text exactly.

Selected microphone is stored separately in `phosphor.runtime.xml`, not the tuning preference file.
The prior source verification used A. After the slice-j microphone check, the selection is back on A:
`microphone_selected_input = "15\nbottom\nASUS_AI2202"`.
That final runtime key is recorded in the landscape evidence. No claim of byte equality is made for volatile runtime metadata.

## Task 5: slice j, landscape and stopped-mic presentation

Installed with pm3 and verified by installed-APK readback.
APK SHA-256: `deaea0236d904b12a27bdc152a992b5882a3939d53e3857c9505468f27ab0104`.
Evidence: `build/transpose/captures/landscape-sheets/`.

Saved Android system settings before any change:

| Setting | Before | Final |
|---|---:|---:|
| accelerometer_rotation | 1 | 1 |
| user_rotation | 0 | 0 |
| font_scale | 1.0 | 1.0 |

With the app stopped, set accelerometer rotation to 0 and user rotation to 1.
Launch the app. UI hierarchy reports rotation 1 and a 2400 × 1080 display.
The saved scope-rotation preference did not need editing.

Screenshots inspected:

- `settings-landscape.png`: two headed columns.
- `light-landscape.png`: color collection left, random controls right; current Solar Gold mark remains.
- `look-landscape.png`: room collection left, style controls right.
- `src-landscape.png`: library/capture left, microphone/remote right.
- `mode-landscape.png`: mode groups left, geometry/skip right.
- `manual-landscape.png` and `manual-chapter-landscape.png`: narrower readable single-column manual.
- `settings-landscape-font2-top.png`: font scale 2.0, readable wrapping without observed horizontal truncation.

The larger text leaves less content visible. Rows can extend below the scrolling viewport.
This is not a claim that every off-screen row was visually inspected at font scale 2.0.
Changing Android font scale recreated the activity and closed the sheet. Reopen Settings for the actual font-2 check.

A real microphone start routed to port 19. Normal stop removed its stop action and showed no retry or error row.
See `src-mic-stopped-no-retry.png` and the paired UI dumps.

Real Android Back keys also verified the manual chain: chapter → index → Settings.
The existing Settings scroll position was retained after returning from the manual.

After testing, stop the app and restore all three Android settings to the exact saved values.
Read them back and compare dictionaries. `system-before.json` equals `system-final.json`.
Final `phosphor.prefs.xml` matches the task-5 starting XML text exactly.
No presentation preference needed restoration. No notification-access setting changed.

## Limits

- No new physical keyboard or TalkBack test ran on the phone. Task 4 adds a non-pointer-after-scroll regression test.
- The instrumented suite was not run on the primary device.
- Device screenshots and manual interactions are distinct from the test suite's compile-only receipt.
