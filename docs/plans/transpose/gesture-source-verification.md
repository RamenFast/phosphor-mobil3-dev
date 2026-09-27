# Sheet gesture and SRC device verification

Auditor for Prime · 2026-09-26 · primary ASUS `NAAIB70036673ZC` only.
No commits or production-code edits. App data preserved. No uninstall, clear-data, root operation, or permission change.

## Builds and evidence

Initial installed build: slice d / HEAD `3103fd436f9df9900363086b37e21f09ccaecc97` as identified by Prime.
The original reported incident happened earlier on `29d6587`. That exact earlier binary was not reinstalled.

Installed test candidate through pm3:

`dev/pm3 --serial NAAIB70036673ZC install /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/dev/scratch/recovery-unit1-20260913/transpose-slice-e.apk`

APK SHA-256: `17347627e546357b0e2f617a97fac4afbf1822d7d2782aa64839b5911f395728`.
pm3 read back the installed APK and verified its hash and signer. See `install-guard.json`.

All evidence is in `build/transpose/captures/gesture-retention/`.
`sha256.json` identifies the main screenshots, preference snapshots, dumps and install receipt.

## Protocol

Before testing, read `shared_prefs/phosphor.prefs.xml` with run-as into `prefs-before.xml`.
Before every test gesture, check Android’s current focused window and dump the open SETTINGS hierarchy.
Before and after each gesture, snapshot and compare typed preference values.
Retain the input coordinates, command durations and differences in a per-case JSON receipt.

Slow drags use DOWN, MOVE in 30 px increments, then UP. Observed per-command duration was approximately 41–73 ms.
Fling cases use a 110 ms Android input swipe. No polling sleeps were used.
Also inspect the displayed glow value: an empty preference diff alone does not prove that an unsaved native/UI value stayed unchanged.

For deliberate boolean changes, force-stop the app, replace only the changed XML entry with run-as sed, and read it back.
Confirm the complete preference map equals the pre-action snapshot before continuing.

## Original-build reproduction result

No minimal failing gesture was reproduced on installed `3103fd4`.
Seven guarded strokes were tried, including plain scroll from glow/toggle, fullscreen downward scroll,
long scroll-through-top/pull-close, and short glow/toggle pull-and-spring-back.
All produced empty preference diffs. The visible glow remained 70%.
One long stroke started in the sheet gutter, not on a control; it is recorded but is not evidence of control ownership.

This does **not** disprove Prime’s earlier incident or prove its cause.
The older 29d6587 build and the exact earlier interleaving of flings, re-grabs and animations were not reproduced.

## Guard-build negative cases

| Receipt | Start / motion | Observed result | Preference diff |
|---|---|---|---|
| 10 | Glow lane, down 90 px in 30 px steps | Sheet returns home; glow remains 70% | empty |
| 11 | Glow lane, down 420 px in 30 px steps | Sheet closes; no tuning change | empty |
| 12 | Glow lane, up 660 px slowly | Content scrolls; glow remains 70% | empty |
| 13 | Glow lane, up 405 px in 110 ms | Content flings; no value change | empty |
| 14 | Fullscreen row, up 300 px slowly | Content scrolls; fullscreen remains on | empty |
| 15 | Fullscreen row, up 407 px in 110 ms | Content flings; fullscreen remains on | empty |
| 16 | Auto PiP row, up 300 px slowly | Content scrolls; auto PiP remains on | empty |
| 17 | Auto PiP row, down 400 px in 110 ms | Content flings back; auto PiP remains on | empty |
| 19–20 | Controlled navigation scrolls toward PiP | Content scrolls, no toggles | empty |

Pull/spring/close was exercised from the glow lane while content was at the top.
Fullscreen and auto PiP lie below a screenful of content, so one on-screen drag beginning there cannot also traverse all content back to the top.
Those rows were tested for ordinary scroll and fling. The same shared pull owner was tested at the reachable top section.
No claim is made that every target was tested in every geometrically impossible start/top combination.

## Intended actions still work

- Receipt 18: plain fullscreen tap changed `true → false`. Exact XML value restored to true and verified.
- Receipt 21: plain auto PiP tap changed `true → false`. Exact XML value restored to true and verified.
- Receipt 22: plain glow-lane tap at x=220 changed the visible value `70% → 11%`.
- Receipt 23: horizontal glow drag x=350→820 changed the visible value `70% → 82%`.

The glow actions changed UI/native tuning, but the saved preference remained 0.7, including after a normal activity stop in the tap check.
MainActivity.saveTuning can preserve instrument-owned values when automatic persistence is not allowed.
This persistence condition was not diagnosed further in this bounded test. It means preference diffs must be paired with UI observations.
Each deliberate glow test ended with a force-stop/relaunch, confirming the original 70% was restored.

## SRC flows

NewPipe played `FearofDark - Spectronosis`. STREAM_MUSIC stayed at step 1.

1. SRC → everything playing opened Android’s real MediaProjection consent. Start now was accepted.
2. `src-newpipe-direct.png` shows the audio trace with INCLUDE MIC off. Android reported one active REMOTE_SUBMIX recorder.
3. INCLUDE MIC on remained in the source sheet and exposed both level sliders. Android reported capture plus microphone, both active.
4. `src-newpipe-mixed.png` shows the mixed trace. INCLUDE MIC off returned to one recorder.
5. Tapping the active everything-playing row’s stop action ended capture. Android reported no active recorders.
6. The `built-in` microphone row started one MIC recorder and changed its trailing action to stop.
7. Tapping that active row again stopped it. Android reported no active recorders.
8. Microphone names are human-facing: `built-in` and `built-in · second`, not raw ASUS model/ID labels.
9. Track names → allow opened Android’s `Phosphor capture metadata` notification-access page, not ordinary notification permission.
   The system permission was not enabled or changed. Back returned to Phosphor.

The narrow vertical traces reflect the largely matching music channels, XY45 mode and Ben’s saved quarter-size framing.
No zoom or other presentation setting was changed to make a larger screenshot.

## Code observations and limits

The new guard records scroll/pull history in the parent, resets on pointer DOWN in Initial pass, and gates subsequent tap callbacks.
SliderLane checks the guard for start, scrub and commit. This addresses the proposed child-Main-pass versus parent-consumption race.
It does not constitute a reproduced root-cause proof of the older incident.

Additional review risk reported to Prime: the guard resets only on pointer DOWN, while wrapped clickable/toggleable/selectable callbacks
also serve keyboard and accessibility activation. After a touch scroll sets moved=true, a later non-pointer activation may remain blocked.
This needs a non-touch-after-scroll behavior check or pointer-sequence-scoped suppression. It was not device-tested in this task.

Minor SRC observation: a normal user microphone stop shows “Microphone stopped” plus retry. The redesign intended retry only for failure.
Actual start/stop recorder ownership worked.

## Cleanup and handoff

NewPipe is paused, verified through its media session. INCLUDE MIC is off.
Phosphor was force-stopped after source shutdown. Final dumpsys audio shows no active recorders.
No notification-access grant changed. Volume remains at the requested quiet step 1.

**Final `phosphor.prefs.xml` difference: `{}`.**
The final XML text also matches the initial snapshot exactly, not only the parsed values.
The dedicated final result is `final-prefs-diff.json`.
Primary ASUS ownership returned to Prime after the final checks.
