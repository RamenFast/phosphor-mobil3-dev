# Transpose · design direction

Prime · creative director · 2026-09-24. Built from `../inventory.md`, `../phase0-device-notes.md`,
real test-bed captures of both builds, and Ben's house design rules. Binding for Phases 1-4.
Prime may refine details on the device. Changes to anything below are recorded here first.

## One idea

**The beam is the instrument. The chrome is a quiet set of stone keys you reach for.**
The S25 felt good because it had one row, one hero key, flat quiet siblings, sharp corners,
instant accent feedback, color where color matters, and a quiet stage. We keep that grammar
and give every post-S25 capability exactly one calm home.

## Stage

- Default: nothing but the beam. No readouts, no plates, no floating buttons.
- Tap: the console rises. One quiet line sits above it: what is playing
  (`microphone`, `everything playing`, the track title, or the relay name). Nothing else.
- No `fps`, `segs`, `×gain·a`, dBFS or bridge counters outside the developer view.
- No stage SIGNAL CHECK button. If there is no sound, the quiet line says `no sound yet`.
  Tapping that line opens SRC.
- HOLD shows as a state of the play key (pause glyph with a hold bar), not a prose plate.
- Gesture feedback: see *Size and AUTO* below. Glow and mode gestures show a short word
  (`brighter`, `dimmer`, the new mode name), never a number.

## Console

`[◂◂] [▶] [▸▸] · [MODE] [SRC] ········ [⋯]` in one row, always.

- **One hero key.** PLAY is the only dimensional key: square 56dp stone, catch-light and shadow,
  sinks 1dp on press. It changes glyph (▶, ❚❚, hold), never shape or size.
- **Flat siblings.** Every other key: 48dp tall, hairline border, 0dp corners, mono label.
  MODE and SRC keep their words. ◂◂ ▸▸ are 48dp squares and appear only with a track or queue.
- **One state language** (for keys, rows and chips everywhere):
  - pressed: 10% accent tint, instant;
  - selected or on: accent rim + accent text + a `✓` or filled mark, so hue is never the only signal;
  - disabled: 40% ink, hairline at 40%, no rim.
- **No reflow.** At large font scales, MODE and SRC become 48dp glyph keys with TalkBack labels.
  The row never splits into 2 or 4 rows. No edge flash.
- Track title marquee and seek rule sit above the row only while a track is present (as S25).

## ⋯ popout

A 2×2 grid of large cells, one quick toggle, nothing else:

| LIGHT | LOOK |
|---|---|
| **PiP** | **SETTINGS** |

Quick toggle under the grid: `GRID`. Frame rate, stats HUD and AUTO PiP leave the popout
(they live once, in Settings or the developer view).

## Sheets: one anatomy

- Header: glyph + TITLE (mono, 14sp caps) + ✕ (48dp). No subtitle, no disclaimer under the title.
- Sections: mono eyebrow (12sp caps, 0.08em tracking, ink2), 24dp above, 8dp below.
- **One button family: `Key`.** Hairline rectangle, 0dp corners, 48dp minimum height, 16dp side
  padding, mono 15sp label, optional leading glyph, optional trailing value in ink2.
  Variants are only width (row = full width, chip = grid cell) and weight
  (**stone** for the one primary action in a sheet, flat for everything else).
  States follow the console state language above.
- Help text: at most one line, 13sp humanist, ink2, and only where a control would confuse
  a first-time user. Longer explanations go to the manual.
- Slider: label left, value word right (`soft`, `bright`, `3 s`), 48dp touch height.
  Numbers appear only where the unit is natural (seconds, Hz, frames per second).
- Sheets open over the stage; the beam stays visible above them (sheet max 85% height).

## Information architecture: one home per control

| Control | Home | Verdict |
|---|---|---|
| Open file, open folder, queue list | SRC › LIBRARY | keep |
| Everything playing (capture) | SRC › OTHER APPS | keep, one line of consent help on first use only |
| Include mic with capture | SRC › OTHER APPS, one toggle row under capture | simplify |
| Track names permission | SRC › OTHER APPS, one row `track names · allow` only while not granted | simplify |
| Microphone start/stop | SRC › MICROPHONE: one row; tap starts, tap the active row stops | simplify |
| Mic input choice | SRC › MICROPHONE sub-rows only when >1 input, human names (`built-in`, `built-in · second`, `USB`, `headset`) | simplify |
| Relays, add relay, LIVE | SRC › REMOTE | keep |
| Relay latency | SETTINGS › REMOTE (only when a relay exists) | move |
| Modes, random, ban faces, geometry | MODE | keep, cut prose |
| Beam color presets | LIGHT › 3×3 swatch grid | restore S25 |
| Six saved colors | LIGHT › SAVED: six swatch squares + `+`; tap = wear, long-press = edit/delete | port |
| Color cycle (off/timer/track, leg time) | LIGHT › CYCLE, only with ≥2 saved colors | port |
| Random color (roll, automatic) | LIGHT › CYCLE as `⚄ roll` key + `auto` toggle | simplify |
| Photosensitivity guard | LIGHT, one line only when a fast cycle is chosen | simplify |
| Themes (rooms + appearance looks) | LOOK › tiles as live previews | merge the two systems |
| FEEL, MOTION, LABELS | LOOK › STYLE (3 chips) | keep; CORNERS follows the look |
| Appearance hex editor | developer view | cut from users |
| Size (AUTO), manual gain, VIEW LOCK, reset size | SETTINGS › SOUND & VIEW | rework (below) |
| Focus, beam, glow, ⚄ vary-per-track | SETTINGS › SOUND & VIEW | keep, ranges collapse under `vary per track` |
| Default source on launch | SETTINGS › STARTUP: none · microphone · everything playing | simplify; permission popup folds in |
| Frame rate, keep screen bright, HDR, fullscreen, rotation, UI placement, controls always visible, double-tap play | SETTINGS › SCREEN | keep, no prose |
| AUTO PiP, floating HUD (+ background), background linger, pause display | SETTINGS › PiP & HUD | keep once |
| Instrument setups (apply, save, rename, delete) | SETTINGS › SETUPS | simplify |
| Settings export/import, setup documents | SETTINGS › SETUPS › backup | merge |
| Manual | SETTINGS › ABOUT and `?` in each sheet header | one home + contextual link |
| Signal check, stats HUD, band, beam rate, grid data, HDR status, raw gain, retry framing save, root | SETTINGS › DEVELOPER (appears after 7 taps on the version) | move |

Settings has 7 short sections: SOUND & VIEW · STARTUP · SCREEN · PiP & HUD · REMOTE · SETUPS · ABOUT.
Section summaries use words (`auto size · closer`), never raw multipliers.

## Size and AUTO (the zoom Ben could not read)

- **One size scale** for AUTO and manual. Turning AUTO off keeps the current size. No jump.
- **AUTO** keeps sound comfortably in view. It glides both ways: fast enough to stop clipping
  (about 80 ms attack), slow and smooth on release (about 1.5 s). No instant collapse.
  It does not grow noise: below the learned room floor it holds size.
- **Pinch and one-finger drag** with AUTO on set *closer* or *farther* around AUTO.
  The range is wide enough to matter (about ¼× to 4×). With AUTO off, they set size directly.
- **Feedback while touching:** a thin vertical rail at the screen edge with a marker,
  a small tick for "AUTO's choice", and the words `closer` / `farther` at its ends.
  It fades 600 ms after release. No numbers.
- Settings shows `AUTO SIZE · on`, a `size` slider with the same rail, and `reset size`.

## Type, color and space

- Mono (JetBrains Mono or the platform mono) for labels, keys and values. Humanist sans for help.
- Scale: 12 eyebrow · 13 help · 15 key · 16 sheet title rows · 20 empty states.
- Spacing grid: 4dp. Sheet side padding 20dp. Key gap 8dp.
- Every look defines ink, ink2, line, surface, accent. Text contrast ≥ 4.5:1,
  control boundaries ≥ 3:1, checked for AMOLED, Glass, Light and Blossom.
- Selection is never color only: rim + mark. Color swatches carry their names for TalkBack.

## Motion

- Console rise 160 ms ease-out; sheet slide 200 ms ease-out; popout fade 120 ms.
- Press feedback is instant. No bounce, no flashes.
- Reduced motion: fades only, 0-80 ms. The beam is the only thing that moves all the time.

## Accessibility floor

- 48dp targets everywhere; the hero key is 56dp.
- Every control has a TalkBack label and state (`on`, `selected`, `disabled`).
- Font scale 2.0 must not truncate or overlap anything; rows grow in height.
- Landscape: sheets become a side panel; the console stays one row.
- System Back closes the top layer only (manual chapter → index → sheet → console).

## What we took, changed and cut

- **Took from S25:** one row, one stone key, flat hairline siblings, instant accent rim,
  the swatch grid, room tiles as previews, the quiet stage.
- **Changed:** 48dp instead of 44dp keys; a check mark with every selected state; one theme
  system; one size scale; words instead of numbers.
- **Cut:** status plate, stage readouts, SIGNAL CHECK on stage, 16 → 1 button family,
  17 duplicate paths, the hex editor, root prose, multi-row console reflow, edge flash.

## As built · Settings (2026-09-25)

- Settings is an index of topics; each opens a page with "‹ all settings" and system Back to return.
- Topics: SOUND & VIEW · SCREEN · PiP & BACKGROUND · SOURCES · SETUPS & BACKUP · ABOUT (+ DEVELOPER).
  STARTUP and REMOTE merged into SOURCES (law 4: no menu under three choices).
- GRID lives only in the ⋯ popout. LIGHT and LOOK live only in ⋯. The raw appearance editor is in DEVELOPER.
- Row family: toggle (label, optional hint, on/off word, filled-square mark), choice (hairline cells,
  chosen = accent rim + filled square; stacks vertically at large font), slider (name left, value right),
  action (label, value or ›). Hints only where a label is jargon.
- SIZE follows AUTO: closer/farther around AUTO when on, direct size when off (same as the stage gestures).
- Font scale 2.0 checked on device: no clipped or mid-word-broken text in the index, SCREEN and SOURCES.

## As built · Redesign (2026-09-25, Designer)

The index + pages Settings above is superseded. Settings, LIGHT, LOOK, SRC and MODE are now one flat
scroll each, with one type scale, one row family and one shared dismissal. Source of truth:
[REDESIGN.md](REDESIGN.md). Commits `29d6587` … (Prime) carry slices a, a', b, a2, c, c', d, e, f.
