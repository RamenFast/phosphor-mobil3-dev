# Unit 1: quiet sound and remembered AUTO framing

## Vision and authority

Quiet instruments, voices and birds should become visible shapes that the hand can bring closer.
Ben approved continuing Unit 1 through the verified ASUS test card in Prime session
`01a09987-2c87-776d-aac8-f9a9d8765b01` on 2026-09-13. Git/GitHub work is included.
The recovery plan remains the scope authority. Stop for Ben's feedback after this slice.
No release, store submission, root work, recording, mixer rewrite, theme redesign or S25 operation.

## Gesture and persistence contract

- On live local 2D input with AUTO enabled, pinch and vertical one-finger zoom change one global preferred framing value.
- Outward pinch and upward drag mean closer. Neither gesture disables AUTO or overwrites the saved manual gain.
- `auto_frame_scale` defaults and resets to `1.0`. Its initial engineering range is `0.25..1.125`.
- The neutral framing target is 80 percent of the raw-amplitude viewport scale. The preference multiplies that target, leaving up to 90 percent fill.
- This target is not a promise that every mode has identical geometry. Verify actual XY and waveform geometry and all-mode finite output.
- Keep the existing manual gain range `0.1..7.0` when AUTO is off.
- The Settings GAIN slider remains an explicit manual-takeover control. Label that action truthfully.
- A nearby RESET AUTO FRAMING action changes only the global preference to `1.0`, not AUTO, manual gain, source or HOLD.
- Persist the preference through gestures, Activity recreation, process restart, source switch, and same-package updates.
- Include it in whole-settings archives. Old partial archives preserve the destination value when the key is absent.
- Reject invalid archive values atomically. Missing or malformed local preferences use the neutral default without breaking startup.
- Preserve instrument preset schema 1. Applying any preset preserves the global framing preference.
- VIEW LOCK, HOLD inspection, 3D orbit/dolly, mode/glow swipes, chrome exclusions, 333 ms settling, and physical edge bands retain their owners.
- Remote geometry remains desktop-owned. Do not claim local preferred framing for it or add a relay protocol in this slice.

## Visual controller contract

Replace the superseded mobile 0.02 gate and 6x ceiling. Keep the shared desktop implementation unchanged.
Use elapsed time rather than render-frame counts. Keep one existing raw stereo drain and all source ownership fences.
Visual framing never changes captured, mixed, decoded or audible PCM, microphone route, system volume or speaker monitoring.

The controller must enlarge sustained structured signals below the old 0.02 threshold.
Controlled fixtures must demonstrate useful gain above 20x, not only a larger numeric limit.
Do not choose a ceiling that leaves a 0.005-peak fixture tiny merely to preserve a round number.
An initial bounded ceiling up to 256x is permitted for measurement, not accepted as useful phone magnification without evidence.

Digital silence and invalid/empty input must not increase gain. Low-level noise must not cause endless gain hunting.
Do not learn a continuous quiet tone as noise and thereby suppress the requested experience.
Any noise/activity estimate uses the current raw input without filtering away frequency ranges or modifying PCM.
A current loud frame must reduce gain promptly before rendering, rather than gliding through a severely offscreen trace.
After a transient or earlier loud source, quiet structure must recover without an invented track or metadata reset.
Keep proven new-local-item identity fencing. AUTO toggles and unrelated metadata cannot invent new audio ownership.

Choose the smallest controller that passes the fixtures. Record its exact thresholds, bounds and time constants here before build.
Initial time-scale candidates are 0.35 s upward settling and 0.75 s protection-envelope release.
These are engineering choices subject to fixture and ASUS evidence, not quotations or fixed choices from Ben.

### Chosen engineering policy

The mobile controller uses the current finite raw stereo window only. It measures the absolute
peak and the largest absolute normalized lag correlation at one and two stereo frames. A window
can raise gain when it contains at least 32 stereo frames, its peak is at least `0.0005`, and its correlation is at least `0.20`.
This small structure gate rejects digital silence and stationary uncorrelated low-level noise in
the controlled fixtures. It does not learn a noise floor, so a sustained quiet tone cannot be
reclassified as noise over time. Correlation is only a practical structure hint. It cannot
perfectly distinguish wanted sound from ambient noise, and coherent hum can pass the gate.
No measurement changes or filters PCM.

The raw protection envelope attacks at once and releases exponentially with a `0.75 s` time
constant. Every update clamps elapsed time to `0..0.1 s`. The neutral target is `0.80` times
`auto_frame_scale`, so the allowed preference range `0.25..1.125` gives `0.20..0.90` target
fill. XY45 and rotating XY multiply the target by `1/sqrt(2)` because rotation can combine both channel peaks.
This retains breathing room at every allowed preference without changing raw PCM or manual gain.
Automatic effective gain is bounded to `0.1..256.0`. A current nonzero peak clamps gain
down to its target before that frame renders. Eligible quiet structure raises gain toward the
envelope target with a `0.35 s` exponential time constant. Empty presentation drains retain
bounded elapsed time for the next real input batch. A gap reaching 100 ms discards that rise budget.
Inactive, manual, remote geometry, source epoch and suspended/held presentation rebase the budget.
Empty, invalid, silent or unstructured frames never raise gain. The envelope can still release during those frames, so
later quiet structure recovers without a fabricated source or track reset.

`auto_frame_scale` is a global portable preference. It defaults and resets to `1.0` and is
clamped to `0.25..1.125` at the gesture and native boundaries. AUTO-on local 2D pinch and
one-finger zoom edit it. AUTO-off gestures keep the manual `0.1..7.0` gain path. Remote
geometry keeps its existing desktop gain owner and does not apply this local preference.

Live edits show pending save state. Completed stage gestures and RESET flush one checked preference commit.
A failed write keeps the live value pending, shows the failure, and offers RETRY FRAMING SAVE.
Import retires older pending writes. No synchronous disk write occurs on each pointer sample.
An unfinished gesture can still be lost if the process dies before its completion or idle flush.

## Stereo-first input

[Ben's stereo-first decision](../decisions/2026-09-13-stereo-first-input.md) applies to this slice.
Microphone negotiation tries stereo across at most four viable rates before mono fallback, within twelve total format opens.
Keep the selected device and AudioSource.MIC. Report recorder client and platform device formats separately.
A mono client is duplicated only as the existing visual transport fallback, never advertised as independent stereo.
A 500 ms owner-local paired aggregate may show difference RMS and identical non-silent pairs without saving PCM.
Silence, correlation and format counts alone do not establish physical channel independence.

## Verification

- Controller fixtures: zero, nonfinite/empty input, stationary low noise, quiet continuous and pulsed structure, ordinary sound, impulses, sustained loud and quiet-loud-quiet.
- Include amplitudes below and above 0.02, both channels, and equivalent elapsed results at 60/90/120 Hz.
- Prove hard bounds, stable silence/noise, quiet enlargement above 20x and first-loud-frame protection.
- Run shared DSP modes at the new upper range. Assert finite geometry, bounded segment counts and no panic.
- Gesture tests cover AUTO/manual separation, slow pinch, one-finger direction, slider takeover, reset and all existing ownership exclusions.
- Persistence checks cover defaults, malformed local state, archive round trips and rejection, old archives, preset preservation and restart.
- Keep source-switch, capture epoch and mic handoff tests. Gain work cannot weaken stale-reader rejection.
- Root runs the project Gradle wrapper, locked native tests and relevant boundary checks. No competing build owner.
- ASUS: exact installed APK/hash/signer, actual selected built-in microphone, route/rate truth, measured quiet/loud behavior, real slow/ordinary pinch and one-finger zoom.
- ASUS: AUTO stays on, default/reset/restart/manual mode work, source switches and dead zones remain usable, and audio levels stay unchanged.
- Preserve current permissions. Existing permission grants are not proof of fresh consent UI.
- Separate synthetic fixtures, real microphone evidence, screenshot geometry and Ben's physical feedback. Never substitute one for another.

## Recovery and completion

Retain the pre-change APK, signer, settings/app-state archive, permission and device-state receipt before installation.
Restore temporary test settings without undoing the accepted application update or overwriting new user edits.
No uninstall or data clear. Keep test audio at MUSIC <=1/30 and PC silent.
Publish source/receipts only to the existing private repository. No production signing or public release.
End with exact build identity, changed behavior, three things for Ben to try, known limits and rollback.
If blocked, report the exact condition, evidence, useful verified partial and smallest next action.
