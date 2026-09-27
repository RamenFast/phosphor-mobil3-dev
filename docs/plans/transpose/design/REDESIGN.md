# Transpose · whole-app redesign

Designer · 2026-09-25 · for Prime (creative director) and Ben. Builds on [DIRECTION.md](DIRECTION.md).
Where the two differ, this file is newer and wins. Each decision says why in one line.

Ben today: settings look off, too many submenus, drop the collapsible parts, keep the swipe feel,
keep every capability. Answer: **one flat scroll per sheet, no drill-in, no sub-sheets, one type scale.**

## 1. Navigation map

```
L0  STAGE            beam only · tap = console · pinch/drag = closer/farther
 │                   2-finger sideways = mode · 2-finger up/down = glow · double tap = play (setting)
 │                   bottom-edge pull (console hidden) = console, keep pulling = SETTINGS
L1  CONSOLE          [◂◂] [▶] [▸▸] · [MODE] [SRC] ···· [⋯]      swipe up on console = SETTINGS
 │   └ ⋯ popout      LIGHT · LOOK · PiP · SETTINGS  +  GRID toggle   (part of the console layer)
L2  SHEETS           MODE · SRC · LIGHT · LOOK · SETTINGS           one flat scroll each
L3  MANUAL           only from SETTINGS › ABOUT (chapters are content, not a sheet)
```

- **Every sheet is one tap from the console layer.** The ⋯ popout is the console's own drawer,
  not a menu of menus (it holds four destinations and one toggle, law 4).
- **Levels: 3 for everything, 4 only for the manual.** Stage → console → sheet. Manual chapters sit
  inside the manual sheet, so the manual adds one level and no more.
- **Cut sub-sheets:** INSTRUMENT (saved setups) becomes a group inside SETTINGS. LIGHT loses
  "recall instrument" (duplicate). Settings pages (index → page) are gone.
  SIGNAL CHECK stays a developer-only sheet (stage key in the developer view only).
- **System Back peels one layer:** popout → sheet → console. Manual: chapter → index → back to
  SETTINGS (the sheet it came from). Why: Back must never skip a layer the person can see.

## 2. Sheets and their sections

One heading per group. No group opens or collapses. Rows that only matter in a state appear in
that state (for example range sliders under "vary per track"); that is context, not a submenu.

**SETTINGS** (six groups, fixed order, most-changed first)

1. SOUND & VIEW · auto size · size (closer/farther, ×) · reset size (only when moved) · view lock ·
   focus · beam · glow · vary beam per track (+ range) · vary glow per track (+ range)
2. SCREEN · frame rate · fps line · keep screen bright · HDR · fullscreen · keys always visible ·
   double tap to play · when paused · lock scope rotation · lock key placement
3. PiP & BACKGROUND · auto PiP · floating HUD (+ background, show/hide) · keep playing in background
4. SOURCES · on launch · ask for permission at launch · relay latency (only with a saved relay)
5. SETUPS · saved and starter setups (tap = select; selected shows apply · update · rename ·
   duplicate · export · delete) · save current as… · export settings · import settings ·
   import setups · export all setups
6. ABOUT · manual › · version (7 taps = developer) · CAL line (Annotated looks only)
7. DEVELOPER (after 7 taps) · signal check · status band · beam rate · grid data · pause display
   only · reset inspection · HDR / HUD status · appearance values editor

**SRC** (as built, slice d) · LIBRARY (open file · open folder · queue rows) → OTHER APPS
(everything playing · include mic + playback/mic levels · track names `allow` only while not granted)
→ MICROPHONE → REMOTE (relay rows with an `edit` key, `add relay`, and when connected: music ·
desktop visualizer · desktop sources · desktop library · disconnect).
Active source rows say `stop` and stop on tap. The ⏻ LIVE stone is gone (it duplicated the rows).
Microphone rows use kind names (`built-in`, `built-in · second`, `USB headset`, `Bluetooth`) and read
`microphone` when there is only one. One tap starts any idle input (slice i: `chooseAndStartMicrophone`
reuses the existing start paths). The active row says `stop` and stops. Tapping another input while one
runs moves the running microphone to it.
Status and consent lines appear only when they carry live state; the relay empty state is one line.

**MODE** · AUTOMATIC: random (⚄) → XY · 3D · TIME · SPECTRUM mode rows → GEOMETRY chips + amount
(amount only when a geometry is on) → SKIP ON ⚄: the 11 face chips, always visible at the end, so
bans can be set before the first roll. No prose. Why at the end: the modes stay the first thing.

**LIGHT** · COLORS: 3×3 real-colour swatches (first thing, as on the S25) → SAVED: six squares + `+`
→ CYCLE (only with ≥ 2 saved) → RANDOM: `⚄ roll` key + `new color each cycle` toggle.
Details in §6.

**LOOK** (as built, slices c/c') · LOOKS: Glass, AMOLED, Dark, Light, then the distinct rooms, then
saved looks (a saved name that matches a built-in one reads `name · saved`). CLASSIC: the older
versions of the curated four, together at the end. Exactly one tile is marked; a custom unsaved look
gets its own `current` tile first. Tiles are live previews. → STYLE: feel · motion · corners (sharp ·
soft · round) · labels, each changing only its field. Choices use one row, else a 2-column grid, else
one column, whichever first fits every label on one line. Raw values stay in DEVELOPER.

## 3. One type scale

| Token | Size | Face | Case | Use |
|---|---|---|---|---|
| `Type.title` | 14 sp | mono | CAPS | sheet title in the header |
| `Type.eyebrow` | 12 sp, +0.08 em | mono | CAPS | group headings |
| `Type.label` | 15 sp | mono | lowercase words | every row label and key label |
| `Type.value` | 14 sp | mono | lowercase | trailing values, chip text, `on`/`off` |
| `Type.hint` | 13 sp | humanist | lowercase | the one optional line under a label |

Why lowercase labels: CAPS now mean structure only (titles, group heads, console keys). A row reads
as a word you can change. Before, three sizes of CAPS competed and the eye had no anchor.

## 4. Row anatomy (the one family)

- Sheet padding 20 dp on both sides; content starts at the same x in every row, heading and hint.
  No inner card, no inset plate. (The Glass look drew a second, narrower panel. That is gone.)
- Header: glyph + TITLE left, ✕ right, both centred on one 48 dp line. Content starts 8 dp below.
- Group heading: 28 dp above, 8 dp below, ink2, no rule under it.
- Row: min 56 dp, 12 dp vertical padding, hairline divider between rows (not after the last row of
  a group). Label left, trailing part right, both centred vertically.
  - toggle: `label [hint]` · `on`/`off` · 20 dp square mark (filled 10 dp square when on).
  - choice: label on top, cells below; cells share the width, 48 dp high, 8 dp gap; chosen =
    accent rim + filled mark + accent text; cells stack vertically at font scale ≥ 1.3.
  - slider: `label` left, value right on one line; the 48 dp track below spans the full width.
  - action: `label` left, `›` or a value word right.
  - source row: glyph · label · trailing state (`stop`, `edit`).
- Key (a button that is not a row): hairline rectangle, 48 dp, 0 dp corners, `Type.label`.
  One stone per sheet at most (the primary action). Pressed = 10 % accent tint, instant.
- Selected is never hue alone: rim + mark. Disabled = 45 % alpha, no rim.

## 5. Swipe behavior

- **Open:** swipe up on the console → SETTINGS follows the finger (existing PullRevealState, kept).
  Bottom-edge pull with the console hidden: console first, then SETTINGS. Other sheets open by tap.
- **Dismiss, every sheet, one mechanism** (the S25-style nested scroll, now shared):
  - Drag the header down, or drag down in content. Content scrolls first. When content reaches its
    top, the rest of the same downward drag pulls the sheet, with soft resistance.
    This works after scrolling: scroll back to the top and keep pulling in one motion.
  - Only a finger can pull the sheet. A fling that reaches the top stops there (the accidental-
    dismiss protection Ben liked).
  - Release closes when pulled ≥ 96 dp, or ≥ 32 dp with a downward flick ≥ 700 dp/s
    (tuned on Ben's phone: real thumbs flick short). Otherwise it glides home (160 ms, or a cut
    with reduced motion). A cancelled pull always goes home.
  - Travel is the plain sum of finger deltas (they are already finger motion). Release speed is
    measured from that travel over the last 100 ms, aged at release: flick, hold, release = still.
    Local pointer positions are never used for speed; they drift with the moving card.
  - Both nested-scroll phases react only to UserInput.
  - Scrim tap, ✕ and Back also close.
- **Horizontal:** none inside sheets. Sliders own sideways drags. Locked landscape still slides
  sheets in from the edge; they leave downward after a drag.
- Why one mechanism: Settings had its own 660-line adapter and it was the only sheet that could not
  be pulled down after scrolling (REC03). The shared path already worked in the other sheets.

## 6. LIGHT behavior (as built, slice b)

- **COLORS** tap = wear that preset. It clears the saved-colour selection and auto color.
- **CYCLE** appears whenever it has an effect: ≥ 2 saved colours, or auto color on.
  Choices `off · timer · each track` (auto color: `timer · each track`, it always moves).
  Timer: `every` on a log rail 0.1–60 s, or with `random timing` a two-thumb range (shortest–longest).
  `order · saved / shuffled` while saved colours cycle. Turning a cycle on puts every saved colour
  in the ring; `off` keeps the first ring colour worn alone.
- **RANDOM:** `⚄ roll` wears one generated colour now (temporary). `auto color` = generated auto.
- **Guard:** only when timing below 1 s is requested: one line and `keep safe` / `allow faster`,
  directly under the cycle choice. Safe timing stays active until the person allows faster.

**Saved-colour gesture map** (every baseline state stays reachable):

| Want | Gesture |
|---|---|
| Save the colour worn now | tap `+` (it is worn at once; it joins the ring if a cycle runs) |
| Wear one saved colour | cycle `off`, tap the square |
| Wear a preset while saved colours exist | tap the preset (saved selection becomes empty) |
| Cycle through some saved colours | cycle `timer` or `each track` (all join), then tap squares to remove or add (ring keeps ≥ 2) |
| Edit a colour | long-press the square, or TalkBack action "edit": HSV square, hue / saturation / brightness sliders (keyboard and TalkBack ranges), #hex readout, `done` |
| Delete a colour | `delete` in the editor, or TalkBack action "delete" |
| Auto color with saved colours kept | toggle `auto color`; saved ring is kept and returns when a square is tapped |

Deliberate changes from the baseline (Auditor L7): `+` saves the displayed colour, not always the
preset; with cycle off a tap wears one colour instead of toggling membership; a running ring keeps at
least two colours (a one-colour ring is the same state as cycle off).

## 7. Functionality retention

Nothing below is dropped. "same" = same action, new surface.

| Control today | Where today | New home |
|---|---|---|
| Open file · open folder · queue rows | SRC | SRC › LIBRARY (same) |
| Everything playing + consent card | SRC | SRC › OTHER APPS (same consent logic) |
| Include mic · playback level · mic level | SRC | SRC › OTHER APPS toggle + two sliders when on |
| Track names access (grant) | SRC prose + key | SRC › OTHER APPS `track names · allow` while not granted |
| Capture status / fix | SRC prose | one hint line under everything playing |
| Root capture keys (flag-gated, off) | SRC | same place, same flag |
| "manual…" beside capture prose | SRC | SETTINGS › ABOUT › manual (one home) |
| Start selected mic · input rows · retry | SRC | SRC › MICROPHONE: tap a row = choose + start; tap the active row = stop; `retry` key only when the status shows a failure |
| Stop microphone | SRC row | tap the active mic row |
| Bluetooth mic confirm | SRC | same, two keys inline |
| Mic status | SRC prose | hint line under the rows only when not idle |
| ⏻ LIVE stone (stop / start capture) | SRC | stop = active row `stop`; start = everything playing |
| Relays: connect, edit, add, remove | SRC | SRC › REMOTE (same editor) |
| Music / visualizer streams, desktop sources, browse library, disconnect | SRC | SRC › REMOTE when connected |
| Remote failure message | SRC | SRC › REMOTE (same) |
| Mode rows, random, ban faces | MODE | MODE (ban chips always visible in SKIP ON ⚄) |
| Geometry + amount | MODE | MODE |
| 9 presets | LIGHT text rows | LIGHT › COLORS 3×3 swatches |
| Saved colours: select, edit (HSV + RGB), delete, add | LIGHT | LIGHT › SAVED squares, gesture map §6. RGB rules → HSV square + hue/saturation/brightness range sliders + #hex |
| Random roll · automatic generated colour | LIGHT | LIGHT › RANDOM |
| Shuffle order ("Le random order") | LIGHT | LIGHT › CYCLE `order` |
| Timer / track · leg seconds · random interval min/max | LIGHT | LIGHT › CYCLE |
| Photosensitivity guard | LIGHT | LIGHT, one line when relevant |
| Recall instrument | LIGHT | SETTINGS › SETUPS (duplicate removed) |
| Legacy rooms · appearance looks | ROOM + Settings | LOOK tile grid (merged) |
| FEEL · MOTION · LABELS | ROOM | LOOK › STYLE |
| CORNERS chip | ROOM | LOOK › STYLE corners · sharp / soft / round (0 / 8 / 12) |
| Live style sample | ROOM | cut (tiles preview the look) |
| Appearance editor (hex, preview, save, rename, delete, repair) | Settings › DEVELOPER | same |
| Auto size, size, reset, view lock, focus, beam, glow, vary + ranges | Settings page | SETTINGS › SOUND & VIEW |
| Size not saved · retry | Settings page | same group, only on failure |
| Frame rate, fps line, bright, HDR, fullscreen, keys visible, double tap, when paused, rotation locks | Settings page | SETTINGS › SCREEN |
| Brightness pin error | Settings page | hint line under keep screen bright |
| Auto PiP, floating HUD, HUD background, show/hide HUD, keep playing | Settings page | SETTINGS › PiP & BACKGROUND |
| On launch, ask permission, relay latency | Settings page | SETTINGS › SOURCES |
| Saved setups sheet (curated, apply, duplicate, update, rename, export, delete, save, import preview, undo, cancel, retry, rapid guard, source controls) | INSTRUMENT sheet | SETTINGS › SETUPS inline, same actions |
| Export / import settings + transfer status | Settings page | SETTINGS › SETUPS |
| Manual · version · developer unlock · CAL line | Settings page | SETTINGS › ABOUT |
| Signal check, status band, beam rate, grid data, pause display only, reset inspection, HDR/HUD status | DEVELOPER page | SETTINGS › DEVELOPER |
| Stage SIGNAL CHECK · pause label | stage (developer view) | same, developer view only |
| GRID | ⋯ | ⋯ (only home) |
| Enter PiP | ⋯ | ⋯ (only home) |
| Manual search, chapters, previous/next, bestiary, link cards | MANUAL | MANUAL (Back fixed) |
| Transport: play/pause/hold, previous/next, seek rule, track title | console | unchanged |
| Stage gestures: tap, double tap, pinch/drag size, 2-finger mode and glow, bottom-edge pull | stage | unchanged |
| 3D orbit (one-finger drag) and dolly (pinch) | stage, 3D modes | unchanged |
| Held-image pan / pinch while paused | stage, HOLD | unchanged |
| Floating HUD: drag, resize, ↗, ×, FIT, LIVE, SRC, ‹ ▶ › | floating HUD | unchanged |
| Notification / media-session transport, mic stop action | notification | unchanged |
| Setup import decisions (add, skip, replace, as copy), cancel file, cancel preview, restore / retry save | INSTRUMENT | SETTINGS › SETUPS, all four decisions kept |
| Appearance recovery, repair, reset | DEVELOPER editor | unchanged |
| Signal check OPEN SOURCES | SIGNAL CHECK | unchanged (developer) |
| Remote library roots, play folder → queue | SRC › REMOTE | unchanged, every root shown |

## 8. Build order

a. SETTINGS flat (+ a' fixes) · b. LIGHT · a2. shared dismissal · c. LOOK · d. SRC · e. MODE · f. Manual Back.
Each slice: compile, unit tests, debug APK, then Prime's device screenshots decide.
