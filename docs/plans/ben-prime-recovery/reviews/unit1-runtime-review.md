# Runtime correction review

## Verdict: BLOCKED on one native mode-transition ordering defect

The microphone getter correction is source-clear. The rotation factor is correct for settled modes, but its render-loop placement misses the animated switch frame.

Scope: read-only review of commit `23541ca` and the current `rust/src/engine.rs` and `rust/src/render.rs` diff. Read `docs/AGENTS.md` and `spec/UNIT1-AUTO-FRAMING.md`. No builds, tests, device actions, network calls, source edits or Git writes were performed. Earlier persistence review and unrelated features remain closed.

## Blocker and smallest fix

`rust/src/render.rs:811-815` sets `view_scale` from `computer.mode` and updates gain. The tube-flip then changes `computer.mode` at lines 859-869. DSP computation uses that new mode at line 963.

An animated transition from XY or waveform into XY45 or rotating XY therefore renders its first switched frame with the old, larger framing target. A settled 0.005-peak input at maximum preference can retain approximately 180x instead of the rotation-safe 127.28x on that frame. Equal or opposite channel extrema can exceed the horizontal viewport. Tube-flip collapse scales Y only, so it does not protect X. A delayed frame can also land after the bloom interval.

Smallest fix: move the existing guarded AUTO update block after the tube-flip mode assignment and before grid setup and DSP computation. Preserve one update per live frame, its elapsed-time accounting, and the existing source/remote guards. Do not perform a second update with the same samples. Reduced-motion and no-surface mode changes already occur before the current update and are not the failing path.

Add a regression that reproduces the production order: settle maximum AUTO in an unrotated mode, land the animated switch, then check the very first rotated frame. Cover XY45 and rotating XY with equal/opposite channel extrema. Also verify the return to an unrotated mode restores its target through the existing upward settling policy, not an immediate upward jump.

## Clear findings

- `AutoGain::set_mode` uses `1/sqrt(2)` only for `Xy45` and `XySwirl`. Other modes restore `1.0`. This bounds the rotation of a raw stereo peak box without changing channel samples.
- For a settled rotated 0.005-peak fixture, the neutral and maximum targets imply approximately 113.14x and 127.28x. These remain meaningful magnification, well above 20x, inside the existing 0.1..256 bound.
- `AutoGain::update` incorporates the current raw peak before computing its target and clamps downward immediately. The new multiplier makes settled rotated protection more conservative. It does not hide raw full-scale samples or repair clipped input.
- The structure gate, 0.35 s rise, 0.75 s envelope release, empty-drain budget and proven local-item reset are unchanged. Silence and unstructured input cannot raise gain. Coherent noise can still pass the documented correlation heuristic.
- Manual gain uses its existing 0.1..7.0 path. The new field affects only AUTO target calculation. Remote geometry skips local AUTO. HOLD returns through retained-image presentation before the new code. Source epochs and held presentation retain their existing time rebases. There is still one raw stereo drain. No PCM, shared-engine or ownership change appears in this diff.

## Live microphone getter

`MicCaptureService.kt:160-166` reads the current recorder rather than the startup cache. It checks owner, recorder identity, stopping and destroyed state after taking the snapshot. Retiring owners use the existing cached lifecycle observation. A current owner without a recorder returns null rather than the old owner's snapshot.

`MicController.observation()` reads the owner-local diagnostic snapshot. It does not call AudioRecord or start a reader. `SignalRecorderWindow` captures one volatile descriptor/meter state and rejects writes through stale meter tokens. Recorder lifecycle flags are volatile and contribution also checks the session attachment.

The inspected caller is the Activity refresh path. Service lifecycle mutations run on the main thread. These checks support that existing threading contract, not a general atomic getter for arbitrary concurrent callers. Service `stopping`, `destroyed` and `recorder` fields are not volatile. The supplemental getter test checks source wiring, not an adversarial retirement schedule. No new stale-reader or PCM ownership path was found.

## Evidence and remaining verification

The supplied `final-stereo-signal-1.xml` through `-3.xml` show the ASUS built-in route, client and platform device formats at 48 kHz stereo PCM16, and completed reads increasing from 2078 to 2331 to 2582. Paired-window difference RMS changes from 0.00673 to 0.00390 to 0.00722. Most non-silent pairs are not identical. This establishes fresh device-session measurements and non-identical delivered channels, not physical microphone independence or acoustic channel separation.

The new extrema test covers settled XY45/rotating XY at maximum preference and reconstruction factors 1/2/4/8. It does not execute the production animated-switch order. It warms gain on a tone, then computes extrema without a controller update for those extrema. Its 20 rotating frames are not an exhaustive phase sweep. It also lacks an explicit nonempty-geometry assertion.

Existing tests cover quiet usefulness, cadence, silence/noise, first-loud-frame protection, raw-meter separation, manual ownership and finite all-mode output. Source inspection is not a test pass. Native tests were reported running by the parent and their final result was not verified here. After the ordering fix, the build owner should record completed native results and fresh ASUS default/maximum XY45 geometry on the exact updated APK. Pre-correction pixel evidence does not verify the new margin.

Review complete. Reviewer released with no continuing work.
