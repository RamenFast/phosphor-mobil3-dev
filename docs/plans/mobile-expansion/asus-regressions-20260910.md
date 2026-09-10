# ASUS requirement-linked regressions — 2026-09-10 partial

Device `NAAIB70036673ZC` only. Prefs restored to baseline hashes after.
HEAD `c6a6f96`. No soak. S25 listed unused.

## Proven this run
- SIGNAL CHECK sheet: no source, honest Unavailable rows. `reg-signal-check.png`
- LIGHT presets: Solar Gold → P7 Green latch. `reg-light-p7.png`
- HOLD: PAUSE DISPLAY ONLY → RETURN DISPLAY TO LIVE, copy "display held". `reg-hold-paused.png`
- Settings / SRC / ChipCell / HUD / linger: earlier this campaign

## Not this run (honest)
- TalkBack: not toggled. `enabled_accessibility_services` was `null`. System-wide, would steal Ben's input.
- Landscape: `wm user-rotation lock 1` then launch. Shot 2400x1080, console as a bottom strip, status full width (`reg-landscape.png`). Restored `lock 0` and portrait home. Note: `scope_rotation_locked` true still followed the launch-time landscape (lock-to-current).
- Named saved-look APPLY: appearance editor field hits are unreliable; prefs restore is the reversible round trip used instead.
- No-source PLAY still draws an enabled triangle (not a dashed disabled face). Watch.
- Mix / USB-BT still blocked.
