# Remaining mobile visual — next-pass design brief (Muse, overnight)

2026-09-10. UI design only. No source, build, Git, or device work.
Scope: everything visual EXCEPT the accepted U1 no-track tactile console
(Muse 8 / DeepSeek 8 / Grok 8 — closed, do not reopen) and EXCEPT 3D
wireframe craft (Grok's job — not touched here).

Read: `docs/plans/mobile-expansion/visual-u1-asus-checkpoint.md`,
`dev/scratch/design-20260909/resolved-design.md`,
`dev/scratch/design-20260909/source-corrections.md`,
`ui/TactileConsole.kt`, `ui/PhosphorScreen.kt` (call sites),
`ui/Console.kt`, `ui/Sheets.kt`, `ui/Controls.kt`, `ui/ManualSheet.kt`,
`ui/LightSheet.kt`, `ui/Type.kt`, `ui/Dimens.kt`, `ui/Motion.kt`,
`ui/RoomStyle.kt`, `ui/ConsoleTactileTokens.kt`, `ui/SliderGeometry.kt`
(bounded reads; no edits made).

House laws (unchanged): KitKat / AOSP 4.4 calm, bench instrument.
Beam is the world, chrome is the bench. Sharp corners everywhere —
Glass alone keeps 12dp. No pills, no info cards. Two tiers only:
raised state-changing keys, flat reporting info. Depth = brightness
(bevel polarity + 1dp sink), never shape change. Mono data, humanist
prose (line-printer mono prose in Annotated rooms). AMOLED is the
clean-install default; the 13 old rooms are migration inputs, never
picker entries. Motion 80–200ms (room crossfade 240 only),
reduced-motion cuts to stills.

## 1. What to leave alone

- U1 no-track keybed exactly as accepted: well + key bed, play 56dp /
  rest 48dp, one centerline, pressed = inverted bevel + 1dp sink,
  latched = sunk + 2dp accent bar + 1px accent tick, disabled = flat +
  dashed edgeQuiet + announced reason, focus = 2px ring + 1px gap,
  vector PLAY/PAUSE/OVERFLOW set, S9 gesture ownership, responsive
  1/2/4-row policy. Token layer (`ConsoleTactileTokens`: `#141414`
  AMOLED face, edgeQuiet ≥3:1, per-pair measured floors) stays.
- Gesture truth: S9 tap/pull owner, play-bar swipe-up to settings,
  seek scrub lane, dismiss, MODE random-arm, PiP. No gesture changes
  in this pass.
- Saved-look byte-exactness (`lookVersion` 1 renders old look
  byte-for-byte; look 2 opt-in). No serialization changes.
- Glass 12dp + 0.62 alpha + specular rim stays the only rounding and
  the only gloss. No new gloss anywhere else.
- Rejected ideas stay rejected: bench POST ritual, new lamps/needles/
  VU, 8Hz energy sampler, one-open accordion, full glossy skin.
  Only existing signals may surface.
- 3D wireframe hero / mode-change nudge: Grok's track. This brief
  specifies nothing there; flat fallback remains accepted.

## 2. What to change

### A. Track/title/seek state (the biggest gap)

Today `ConsoleKeybedPolicy.tactile()` returns true ONLY when there is
no transport (`lookVersion == 2 && !hasTransport`), so the moment a
track is present the console falls back to the legacy row (StoneKey +
FlatKey, font glyphs `◂◂` / `▸▸`). The accepted tactile unit and the
playing unit are two different instruments. Next pass:

- Build the track console as the resolved 3-row structure inside the
  same well + key bed: title rail (~20dp, fixed) / seek 48dp lane /
  56dp key bed. Same silhouette as no-track, same lit top edge,
  same seated well — the strip must read as one object whether or
  not a track is loaded.
- Extend the vector symbol set to prev/next (closed silhouettes,
  same 26dp box and stroke law as PLAY/PAUSE). Delete the `◂◂`/`▸▸`
  font glyphs from the track row. Display-only HOLD/LIVE keeps its
  real label + action truth (source-corrections rule) — only the
  previous/next marks change.
- Title rail: keep observed order, keep marquee-only-on-overflow with
  existing delay/velocity, keep Mono dataLg ink. Fix the rail height
  so marquee on/off does not reflow the key bed. Long
  `title — artist` strings wrap policy: rail is one line, marquee on
  overflow; never push the seek lane down.
- Seek rule rebuild: raise the hit lane from 44dp
  (`SliderGeometry.HIT_LANE_DP`) to the 48dp floor without widening
  the visible track (2dp hairline) or the 8dp square thumb — touch
  grows, picture does not. Timestamps stay mono at the ends but must
  clear their contrast floor per pair after composite (muted-on-
  surface at dataXs is the suspect pair — re-measure, blend ink
  toward plane to the floor, never a global gray). Keep square thumb,
  accent span from track start (existing law).
- Source-without-seek keeps the 2-row variant (title rail + key bed,
  no seek lane, no gap left behind).
- Prev/next keys join the tactile five-state language
  (pressed/latched/focus/disabled identical to MODE/SRC). Disabled
  capture gates (`captureCanPrevious/Next/Play`) render as flat +
  dashed edgeQuiet with announced reason — same as U1 disabled.

### B. Sheet / manual hierarchy

Sheets keep observed order (SOURCE groups, SETTINGS groups with live
summaries, MANUAL index + search + rows). Keep the chamfered sheet
binding geometry (`sheetCardShape` per character) and multi-open
settings accordion. Changes:

- Sheet header: replace the `✕` font-glyph close mark with the
  closed vector close symbol at fixed dp size (fontScale 1.3/2.0
  safe). 48dp target already exists — keep it, only the mark
  changes. Same for the `✓` prefix in `SheetRow` (checked state must
  read in greyscale: keep the accent border AND add a non-hue mark —
  sunk face or tick — per the adopted selection-hierarchy fix) and
  the `open ↗` arrow in `LinkCard` (drawn vector or plain prose
  label, no font-glyph arrow).
- Manual search field: fold to the resolved recipe — 48dp field with
  vector search symbol + placeholder + inline clear key. Delete the
  separate CLEAR SEARCH row (it costs a full row for a key that
  belongs inside the field). Keep mono 14sp entry text, accent
  cursor, `surface2` fill with `lineStrong` edge.
- Right-rail pattern (`manual…/grant…`-style keys sharing one
  column): single column ≥340dp width and fontScale <1.3, drop below
  prose under 340dp or at 1.3+. Audit every sheet row with a
  trailing action against this rule.
- Sheet legibility: plates go opaque OR the status rail is suppressed
  while any sheet is open (kills ghost text past ~1.1:1). Current
  `sheetAlpha` 0.94 over a live trace plus scrim 0.40 is the suspect
  stack — pick one of the two resolved options, do not invent a
  third (no blur, no shadow/elevation language). Glass keeps its
  existing opaque readability surface + translucent outer material.
- Queue rows: keep hairline + accent-border active law, 48dp min,
  wrap-not-ellipsis at large fonts. No change beyond the ✓-prefix
  rule above.
- `ChipCell`/`SheetRow`/`FlatKey` keep hairline + accent-when-active
  law; audit that active never rests on hue alone (accent border
  counts as non-hue shape change — keep it, add sunk face where the
  cell is a latch: DEFAULT/MODE-adjacent toggles, TIMER/TRACK,
  shuffle, auto-gain, view lock).

### C. Inherited loud frame

The console card currently stacks surface-fill + hairline + plane +
surface (three fills, two paddings) inside 12dp/10dp outer margins;
`consoleAlpha` 0.86 lets the trace ghost through the chrome. It reads
louder than the bench law allows (chrome luminance cap 0.60). Next
pass:

- One surface fill, one hairline, one padding step per card. Drop the
  plane sandwich. Keep sharp corners, keep the hairline (decor) /
  edgeQuiet ≥3:1 (silhouette) / edgeKey ≥4.5:1 (primary, latched,
  focus-adjacent) ladder — computed per room, never one global gray.
- Tighten outer margins only to the point where the card still floats
  clear of the panel glass; do not dock it. StatusBand gets the same
  single-fill treatment. No forced black palettes — AMOLED keeps its
  bounded bevel (`#141414` face, 1–2px lit top/left ~1.6:1, dark
  bottom/right step); quiet comes from fewer layers, not darker
  colors.
- `consoleAlpha`/`sheetAlpha`/`scrimAlpha` re-verified against the
  0.60 cap after the flattening; trace ghosting through chrome must
  not carry information (decorative bleed ≤10% luminance, ≤16% while
  the event-driven edge light is live — per resolved §9 checklist).

### D. Animation / theming polish

- Press-language parity: U1 tactile keys are static-drawn (content
  offsets 1dp when sunk — good, keep). Legacy StoneKey animates face
  color + sink over 80ms. When the track row joins the tactile bed
  (§A), all keys share ONE press law: inverted bevel + 1dp sink,
  ≤80ms, content follows the face. No color-only press anywhere.
- Keep the house motion table verbatim (press 80, summon 120,
  settle 160 + 8dp, sheet 200, room 240 smoothstep, pull-reveal
  thresholds as coded; CLOCK_TICK press, CONTEXT_CLICK latch,
  double-tick postcard, disabled silent; nothing runs hidden/in
  PiP). Burn-in walk (±1px, 60s orbit) stays on chrome only.
- Event-driven edge light (press 80ms flash, mode/source/room
  change; gated visible && !pip && !reduced-motion; clamped under
  the 0.60 cap; never on labels, accent bars, focus rings) is still
  unbuilt — it is the last open motion item. Build it as a finite
  event flash on the existing 1px lit edge, no sampler, no
  brightness writer (source-corrections rule).
- Theming: four curated families (Light / Dark / Glass / AMOLED),
  AMOLED clean-install default. No renames, no new palettes, no
  default flip, no Light/Dark/Glass claims without fresh device
  captures — all non-AMOLED recipes remain computed targets until
  re-shot. `Spacing via style.space()`, `panelAlphaScale` (glass
  0.62), character/motion/density couplings stay.
- Type: Mono data everywhere in chrome, Prose for gentle notes;
  labels wrap, containers grow, no ellipsis; verify 1.3/2.0,
  portrait/landscape/320dp, keyboard open. Touch ≥48dp everywhere
  (close key, search field, accordion headers, lanes, overflow,
  seek lane per §A).

## 3. Scores — remaining surfaces only (0–10, evidence only)

U1 no-track is accepted and unscored here. 3D is Grok's and unscored.

| Surface | Score | Why |
|---|---|---|
| Track / title / seek (track present) | 4 | Falls back to legacy row: font glyphs `◂◂`/`▸▸`, no well/bed, no 3-row rail, 44dp seek lane under the 48dp floor, muted dataXs timestamps unmeasured. Marquee gating and seek gestures are correct — the structure around them is not. |
| Sheet hierarchy (SOURCE/SETTINGS/LIGHT) | 5 | Order, accordion, chamfer geometry, 48dp rows all correct. Loses points: `✕`/`✓`/`↗` font glyphs, missing inline search-clear, translucent plate stack (ghost-text risk), hue-only active states on latch cells, sub-48dp slider lanes outside accessibility mode. |
| Manual hierarchy | 5 | Index + search + rows + chapter nav + cards all sound; search field already 48dp. Loses points: no symbol/placeholder/inline-clear recipe, separate CLEAR SEARCH row, right-rail rule unaudited, LinkCard arrow glyph, deferred-root rail reads as inert rail correctly (keep). |
| Inherited loud frame | 4 | Triple-fill card stack + 0.86 ghost alpha + full margins reads louder than the 0.60 cap law; plane-relative metric still uncleaned. No shadow language (good). Flattening path is clear and small. |
| Animation / theming polish | 6 | Table, gates, haptics, burn-in, reduced-motion all correct; U1 five states done. Loses points: two press languages (static tactile vs animated stone), event-driven edge flash unbuilt, non-AMOLED recipes unshot. |

No surface passes at 8 yet except the closed U1 unit. Highest
leverage first: §A track-row unification (it is the daily-visible
surface), then §C frame flattening (it quiets everything at once),
then §B glyph/hierarchy sweep, then §D edge flash.

## 4. Acceptance sketch (for the implementing pass, not this brief)

Shots: playing + paused + disabled-capture (AMOLED + one light +
Glass), finger-down pressed frame on track row, sheet title rail,
search field with inline clear, 1.3/2.0 portrait + landscape + 320dp,
reduced-motion + PiP pairs. Checklist per resolved §9 (a)–(k):
transport truth incl. displayOnly/capture gates; pressed/latched
silhouette unchanged by overlay; strip rest ≤10% luminance (16% edge
light live); all pairs measured ≥ floor; greyscale latch/disabled/
focus distinct; 48dp targets, no pills outside Glass; gestures
unchanged; opaque sheets (no ghost >1.1:1); no idle delta hidden/PiP;
saved-look import byte-identical; no HDR/4K strings. Score 0–10 on
evidence only; ≥8 passes.
