# Phase 0 · device notes (test bed)

Test bed: ASUS_AI2202 `NAAIB700B7373PZ`, Android 14. Fresh app data for each build. Quiet room, built-in mic, XY45, AUTO on.
Captures: `build/transpose/captures/s25/` and `build/transpose/captures/current/` (screens, UI trees, 12 s mic recordings).

## Mic behavior, 12 s screen recording at 30 fps

| Build | Trace height px (min / median / max) | Size variation (CV) | Jumps >30% | Collapses ≥3 frames |
|---|---|---|---|---|
| S25 `06f84e2` | 29 / 37 / 50 | 0.097 | 1 | none |
| Current `9d5d77e` | 21 / 187 / 246 | 0.3 | 10 | 1.03s×3, 4.2s×13, 11.5s×14 |

- S25: stable but tiny. Quiet sound never fills the view. This matches Ben's original complaint.
- Current: fills more of the view, but pumps constantly and collapses to a small blob or the resting dot
  for about half a second with no input. This matches "glitches out all the time".
- Status band in current showed `480 segs` and then `0 segs` about 1 s apart while the mic was live.
  Some frames get no audio. Cause under investigation in `inventory.md` section 5.
- logcat: wgpu Vulkan validation warnings (VKDBGUTILWARN003) print every frame in the debug build.

## Visible UI regressions from S25 to current

- LIGHT lost its colored swatch grid; presets are now a plain text list.
- SETTINGS: the DEFAULT row overflows and prints vertically down the right edge (`current/07-settings.png`).
- SRC: buttons in three different sizes, prose between buttons, raw device IDs (`built-in (19)`, `(21)`).
- ROOM: TalkBack labels say "Apply legacy room …".
- Stage: a boxed status band with fps, segment count and `×gain·a` is shown on every console reveal.
  S25 showed the same readouts unboxed. Both are engineering output, not user information.
- Current adds a floating "SIGNAL CHECK" label on the stage at launch.
