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

## Decider run · AUTO on vs off (20 s each, same session, fresh data)

| Setting | Height px min / median / max | CV | Jumps >30% | Stalled runs |
|---|---|---|---|---|
| AUTO on | 81 / 198 / 253 | 0.152 | 3 | [(np.float64(0.7), 14)] |
| AUTO off, manual ×7 | 35 / 47 / 64 | 0.099 | 1 | none |

- With AUTO off, the trace is as steady as the S25 build (CV 0.099 vs 0.097), with no stalls or jumps.
- With AUTO on, size swings and a stall appear again. This supports the cartographer's first cause:
  AUTO's instant drop and regrow (`engine.rs:818-823`), not audio starvation.
- Manual gain maxes at ×7, while AUTO climbs to ×63. Turning AUTO off drops the view from ×63 to ×1.83.
  One gain scale must serve both modes.
- Limit: one quiet room, one phone, video-based measurement. A host test on recorded mic PCM should confirm.

## Phase 1 AUTO controller · first device check (same room, 15 s, gain log at 2 Hz)

| Build | Gain min / median / max | CV |
|---|---|---|
| Old `9d5d77e` | 20.3 / 28.0 / 32.4 | 0.109 |
| New controller | 28.2 / 32.2 / 37.0 | 0.065 |

- The new 20 s screen recording had no collapse runs (old: 0.4-0.5 s collapses).
- The 2 Hz log can miss sub-second collapses. Host tests cover clicks, speech-like sound and loud entrances.
- Remaining size variation on screen is the room noise itself, not the controller.
