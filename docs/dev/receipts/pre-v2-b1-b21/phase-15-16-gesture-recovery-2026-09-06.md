# S25 gesture-driver recovery, 2026-09-06 20:27 UTC

This continuation fixes the failed test driver, not the unreproduced B8 inversion.
No mobile runtime source, APK, shared engine, relay service or release state changed.
Private evidence is retained in `dev/scratch/remaining-fixes-20260906T2027Z/`.

## Exit 137 diagnosis and correction

The original driver failed again with an explicit no-input argument.
The retained log reader recovered the actual exception from AndroidRuntime:
`PhoneGesture.main:32` called `InputManager.getInstance()`, which threw a NullPointerException through `Objects.requireNonNull`.
Android's failed crash-report path then killed the process. Exit 137 did not establish an external kill or memory exhaustion.
The phone's existing stock-logcat replacement rejects normal logcat flags. No system executable or unrelated phone file was changed.

The corrected [fixture](gesture-driver/README.md) uses `InputManagerGlobal.getInstance()` and includes a no-input `probe`.
The probe exited zero and reported `input_injected:false` on the S25.
All four subsequent pinch sequences acknowledged every injected event.
The retained Gradle build passed. Its Java source and compiled classes.dex match the physically tested fixture byte-for-byte.

## Physical gesture observations

The device was unlocked, portrait 1080 by 2340, with effective density 2.8125.
Original tuning was backed up before testing. AUTO-GAIN and VIEW LOCK were disabled through the actual SETTINGS controls.
Each gesture lasted 500 ms. Home then forced actual preference persistence for the numeric check.

| Check | Actual input | Observed gain | Evidence |
|---|---|---|---|
| Unrestricted positive control | (350,1000),(730,1000) to (200,1000),(880,1000) | 1.8332275 to 3.2801042 | `pinch-free.ndjson`, `pinch-free-prefs.xml` |
| Second finger enters bottom band, then leaves | (350,1900),(730,2150) to (200,1700),(880,1900) | Stayed 3.2801042 | `pinch-bottom.txt` |
| Second finger inside console margin | Console revealed and settled, (350,1800),(730,2060) to (200,1800),(880,2060) | Stayed 3.2801042 | `pinch-console-margin.txt` |
| Positive control outside margin | Console revealed and settled, (350,1800),(730,2000) to (200,1800),(880,2000) | 3.2801042 to 5.0963883 | `pinch-outside-margin.txt` |

The bottom band begins at physical y=2092.5. The margin samples used the actual console image and layout.
These checks establish physical multi-pointer acceptance and bounded rejection behavior, not exhaustive boundaries or rotations.
The driver has no Compose-layout synchronization. The exact 333 ms physical settle boundary remains open.

## Actual native surface recreation

A separate 14-second all-PID diagnostic window captured a real Home and return sequence in one process.
Native render-thread events showed surface up, surface torn down, then surface up at 1080 by 2340.
The teardown-to-recreation interval was approximately 633 ms. Final Home tore it down again.
All 8,166 raw records replayed byte-for-byte against the collected text.
This closes the missing proof that a complete native surface destruction and recreation occurred.
It does not establish moving-trace luminance stability across that sequence. Screenshots used the idle grid, not a deterministic audio fixture.

## Tests and restoration

- Focused Gradle tests executed: StageGesturePolicy 17 and CaptureMirrorPolicy 10, zero failures or errors.
- Original tuning and saved relay hosts matched the pre-test backup byte-for-byte after restoration and surface testing.
- Phone MUSIC remained 2/15. No audio, microphone, projection or relay source was started.
- The app was left in the background. Original system display settings were not changed.
- Installed APK readback SHA256 stayed `f5afd99617e7a1abf21246c612590212adeed3a82b588c6235e8448a68b48834`.
- Owned driver, reader, raw log and UI dump files were removed from the phone after retaining their evidence. Final runtime source was `none`.

## Remaining work

B8 remains unreproduced. No speculative glyph patch was made.
The five previous translated-emulator failures were inspected, not rerun without a new tested hypothesis.
Native emulator rendering, exact physical 333 ms timing, moving-trace recreation and exhaustive settings/source variants remain open.
Fresh-Linux, release and human visual gates remain outside this completed bounded pass.
Worker routing confirmation remains unanswered, so no worker was started and no independent review is claimed.
Drift remains 21.
