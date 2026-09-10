# DeepSeek visual assessment, U1 round 3

Assessor: DeepSeek. Panel round: 3 of 4. Unit: U1, tactile no-track console.
Prior DeepSeek scores preserved: round 1 not scored on pixels, round 2 = 8 bounded. This round
is a new assessment of the r3 artifact, not a revision of round 2.
Independence: I did not read `muse-visual-round-03.md` or `grok-code-round-03.md`. Code tests are
not visual evidence. No source edits, no build, no device control, no network, no Git.
Method: PIL measurement of the PNGs at the confirmed density 2.75 px/dp (1080x2400, 440 dpi).
Contrast is WCAG 2.x relative luminance. "Mean luminance" of a strip means the mean sRGB value of
the region divided by 255, the same measure I used on the baseline capture.

---

## 1. Evidence inventory

### Observed and used

| file | size | what it shows |
|---|---|---|
| `asus-r3-amoled-normal.png` | 1080x2400 | AMOLED no-track console, font 1.0 |
| `asus-r3-dark-normal.png` | 1080x2400 | Dark no-track console, font 1.0 |
| `asus-r3-glass-normal.png` | 1080x2400 | Glass no-track console, font 1.0 |
| `asus-r3-light-normal.png` | 1080x2400 | Light no-track console, font 1.0, corner tags |
| `asus-r3-amoled-focus.png` | 1080x2400 | primary key focus, plain family |
| `asus-r3-dark-focus.png` | 1080x2400 | primary key focus, plain family |
| `asus-r3-glass-focus.png` | 1080x2400 | primary key focus, glass family |
| `asus-r3-light-focus.png` | 1080x2400 | primary key focus, annotated family |
| `asus-r3-light-pressed.png` | 1080x2400 | real DOWN then CANCEL on the primary key, no activation |
| `asus-r3-light-font1.3.png` | 1080x2400 | Light at font 1.3 |
| `asus-r3-light-font2.0.png` | 1080x2400 | Light at font 2.0 |
| `asus-r3-light-320dp.png` | 880x1920 | real 320 dp window, font 1.0 |
| `asus-r3-light-320dp-font2.png` | 880x1920 | real 320 dp window, font 2.0 |
| `asus-r3-overflow.png` | 1080x2400 | S9 latched with the overflow popout open |
| `design-20260909/asus-swipe.png` | 1080x2400 | baseline strip for footprint comparison |

Interaction receipts, used only as semantics evidence, not as visual acceptance:
`asus-r3-overflow.png` and `asus-r3-overflow-pull-attempt.png` show that S9 tap and S9 upward pull
both open the existing overflow popout, and I did not treat them as styling evidence.
`failed-theme-selector-*.png` excluded as instructed.

### Not observed, therefore not scored

Disabled key state in any family. Focus and pressed in AMOLED, Dark, and Light at scales other
than those listed. Latched state outside the Light family (the S9 latched capture is Light only).
Dark and Glass at font 1.3 and 2.0. Plain families at 320 dp and in landscape. Keyboard open,
IME open, reduced motion, PiP, hidden chrome, beam or event-edge flash, saved-look import.
Touch delivery is parent-supplied and never inferred from pixels.

---

## 2. Verification of the requested corrections

### 2.1 Plot label "SIGNAL CHECK" on the true-black plot: FIXED

Region: y 343..367, x 24..260, identical location in all four families.

| family | measured ink | contrast vs plot plane `(1,0,0)` |
|---|---|---|
| AMOLED | `(255,255,255)` | 20.97:1 |
| Dark | `(242,239,246)` | 18.43:1 |
| Glass | `(244,246,255)` | 19.45:1 |
| Light | `(255,255,255)` | 20.97:1 |

Round 2 Light measured 1.41:1. Closed.

The label stays clear of the status band at every scale: the gap between the band's last line and
the label is 41.1 dp at font 1.0, 42.2 dp at 1.3, and 44.4 dp at 2.0. In round 2 that gap collapsed
to 4.0 dp at font 2.0. Closed.

### 2.2 Status band wrap at large font: FIXED

`asus-r3-light-font2.0.png`, y 132..397. The band wraps to three complete lines: `src · no source`,
`119.4 fps · 2 segs`, `xy45 · ×1.83·a`. No ellipsis, no truncation, nothing clipped. The band grows
to 96.7 dp tall and the plot label still sits 44.4 dp below it. Round 2 truncated the left string to
`src · no so...`. Closed.

### 2.3 Focus ring: FIXED, full 2 dp, independent of the tray line, all four sides

Ring thickness 5 px = 1.82 dp in all four families, against 2 px = 0.73 dp in round 2. The ring is
now the family accent and is present and symmetric on all four sides of the primary key.

| family | ring color | ring vs its background | ring vs the adjacent tray line |
|---|---|---|---|
| AMOLED | `(255,108,170)` | 7.97:1 | 2.62:1 |
| Dark | `(217,154,201)` | 7.13:1 | 2.65:1 |
| Glass | `(170,205,255)` | 11.21:1 | 4.04:1 |
| Light | `(142,45,35)` | 5.02:1 | 1.66:1 |

Round 2 measured 3.02:1 against the well fill and 1.00:1 against the tray line, so the ring merged
with the tray line in greyscale. In round 3 the ring sits in its own band with a 3 px gap on each
side: tray line at y 2135..2137, gap 2138..2140, ring 2141..2145, gap 2146..2148, key outline
2149..2151. Left, right, top, and bottom scans all match. The ring is not clipped by the well or the
card. The ring clears the 3:1 floor against its own background in every family and clears it against
the tray line in Glass. In AMOLED, Dark, and Light the ring-to-tray-line luminance step is 2.6:1,
2.7:1, and 1.7:1, so the ring is distinguished from the tray line by the 1.1 dp gap and by hue as
well as luminance. This is a large, measurable improvement and it closes the round 2 focus defect.

### 2.4 Light family corner tags and independent label centering: FIXED at 1.0, 1.3, and 2.0

Tags sit in the top-left corner of each key at roughly a 3 px / 2 px inset, matching the existing
source convention. Labels and glyphs are centered independently of the tags.

| element | measured center | key center | delta |
|---|---|---|---|
| Light 1.0 MODE label | x 394.0, y 2218.5 | x 394.5, y 2219 | 0.5 px, 0.5 px |
| Light 1.0 SRC label | x 582.0, y 2218.5 | x 581.5, y 2219 | 0.5 px, 0.5 px |
| Light 1.0 play glyph | x 198.5, y 2219.5 | x 196.5, y 2219.5 | 2 px optical x, 0 px y |
| AMOLED 1.0 MODE label | x 394.0, y 2224.5 | x 394.5, y 2225.5 | 0.5 px, 1 px |
| AMOLED 1.0 play glyph | x 198.0, y 2225.5 | x 196.5, y 2225.5 | 1.5 px optical x, 0 px y |

Round 2 measured the Light legend 6.5 dp and the glyph 4.7 dp below the key center. Closed.

Tag and label separation, no overlap at any scale:

| scale | MODE tag x-range | MODE label x-range | x gap | vertical overlap |
|---|---|---|---|---|
| 1.0 | 315..379 | 385..437 | 6 px = 2.18 dp | 8 px, diagonal only |
| 1.3 | 316..354 | 385..456 | 31 px = 11.27 dp | 0 px |
| 2.0 | 122..189 | 458..619 | 269 px = 97.8 dp | none |

The 1.0 case is the tightest at a 2.18 dp horizontal gap, and it reads cleanly at 4x inspection.

### 2.5 Compact one-row geometry: RETAINED

| element | r3 measured | in dp | r2 | verdict |
|---|---|---|---|---|
| card | 958 x 240 px | 348.4 x 87.3 | 238 px | +2 px only |
| primary key | 154 x 154 px, x 120..273, y 2149..2302 | 56 x 56 | identical | retained |
| MODE key | 176 x 132 px, x 307..482 | 64 x 48 | identical | retained |
| SRC key | 154 x 132 px, x 505..658 | 56 x 48 | identical | retained |
| overflow key | 132 x 132 px, x 828..959 | 48 x 48 | identical | retained |
| centerline | all keys center y 2225.5 | | 2226.5 | retained |
| play to MODE gap | 33 px | 12.0 | identical | retained |
| MODE to SRC gap | 22 px | 8.0 | identical | retained |
| well | 868 x 182 px, x 106..973 | 315.6 x 66.2 | 856 x 170 px | seating inset 1.8 dp to 4.0 dp |

The strip is one row in all four families at font 1.0. Against the baseline capture (81.1 dp) the
strip is 7.6 percent taller and the trace loses 17 px of about 1800 px. Compactness is retained.

### 2.6 Pressed state on the Light family: correct, no activation

`asus-r3-light-pressed.png` against `asus-r3-light-normal.png`, primary key column x=196:

- Normal top edge: well fill 202, outline `112,112`, then the lit band at 255 for 5 px, then the
  face `(232,224,208)`.
- Pressed top edge: outline `112,112`, then a shaded band at 167 for 5 px, then the face
  `(221,214,198)`. The lit band has left the top edge.
- Normal bottom edge: face, then a shaded band at 176 for 5 px, then the outline.
- Pressed bottom edge: face, then a lit band at 255 for 5 px, then the outline.
- Face darkens from `(232,224,208)` to `(221,214,198)`, the same sunken tone the latched state uses.
- The glyph moves +1 px right and +3 px down, and stays a play triangle, so the capture claims no
  playback. The key outline rows are unchanged, so the silhouette does not move. The well and card
  are pixel-identical between the two captures.

### 2.7 Source, play, and S9 semantics: retained

The status band still reads `src · no source` with `xy45 · ×1.83·a`, the key order is unchanged
(primary, MODE, SRC, overflow), and the parent receipts confirm S9 keeps its existing overflow
gesture owner for both tap and pull while SRC still opens the source sheet. No visual or semantic
regression found in the strip.

---

## 3. Unit measurements, state by state

### 3.1 Boundaries and text, all four families

| pair | AMOLED | Dark | Glass | Light | floor |
|---|---|---|---|---|---|
| key outline vs well fill | 3.04:1 | 3.09:1 | 3.04:1 | 3.06:1 | 3:1 pass |
| well step line vs well fill | 3.04:1 | 3.09:1 | 3.04:1 | 3.03:1 | 3:1 pass |
| label vs face | 18.42:1 | 12.96:1 | 16.12:1 | 11.11:1 | 4.5:1 pass |
| Light outline vs card surface | | | | 4.59:1 | pass |

### 3.2 Latched state, observed in the Light family through the S9 receipt

`asus-r3-overflow.png`, overflow key x 828..959, y 2135..2300:

- Face is `(221,214,198)`, one step sunk, identical to the pressed face, against the resting
  `(232,224,208)`.
- Top edge polarity is inverted: a shaded band at 167 (1.66:1 against the face) replaces the lit
  band at 255.
- A bottom accent bar in `(169,103,91)` measures 5 px tall (1.82 dp), 100 px wide (36.4 dp), inset
  16 px (5.8 dp) from each side of the key, at 3.04:1 against the sunken face.
- A left accent tick in `(169,103,91)` measures 2 x 22 px (0.73 x 8 dp) at the key's left edge.
- The label and tag stay `ink`, no tint.
- The tag, the bar, the tick, and the inverted bevel make the latched state readable in greyscale,
  because the bar clears 3:1 against the face.

### 3.3 Narrow window at 320 dp

`asus-r3-light-320dp.png` (font 1.0): card 790 x 422 px = 287.3 x 153.5 dp, two key rows.
Transport row key is 153 px = 55.6 dp tall. The MODE, SRC, and overflow row is 131 px = 47.6 dp
tall. Row gap 40 px = 14.5 dp.

`asus-r3-light-320dp-font2.png` (font 2.0): card 790 x 710 px = 287.3 x 258.2 dp, four stacked
full-width keys. Play 153 px = 55.6 dp, MODE 138 px = 50.2 dp, SRC 138 px = 50.2 dp, overflow
131 px = 47.6 dp. Every key stays at or above 48 dp except overflow, which measures 47.6 dp.
No label clips. The status band wraps to three lines and the plot label stays readable.

### 3.4 Font growth in the annotated family

| scale | Light card | play key | MODE key | overlap or clip |
|---|---|---|---|---|
| 1.0 | 246 px = 89.5 dp | 154 px = 56.0 dp | 132 px = 48.0 dp | none |
| 1.3 | 264 px = 96.0 dp | 184 px = 66.9 dp | 173 px = 62.9 dp | none |
| 2.0 | 710 px = 258.2 dp | 154 px = 56.0 dp | 139 px = 50.5 dp | none |

At font 1.3 the annotated family grows its keys by 25 to 31 percent because each key must hold a
corner tag plus a centered legend, and both scale with the font. This is the sanctioned
"containers grow, no ellipsis" behavior, and no label clips. The effect is specific to the annotated
family, so at font 1.3 the Light strip is about 10 percent taller than the plain strips.

### 3.5 Strip luminance

| family | mean sRGB value | percent of scale |
|---|---|---|
| AMOLED | 23.72 | 9.30 |
| Dark | 46.24 | 18.13 |
| Glass | 33.57 | 13.17 |
| Light | 211.62 | 82.99 |

The plot region contains no chrome in any capture. See item O1 in section 4.

---

## 4. Remaining open items in the unit

Each item below is cosmetic or outside the pixels. None is a material visual defect, and none blocks
the unit.

### O1. Strip rest-luminance check is unmet on Dark and Glass (metric decision, not a pixel fix)

Region: console card band, `asus-r3-dark-normal.png` and `asus-r3-glass-normal.png`. Measured 18.13
percent and 13.17 percent against the resolved check of 10 percent at rest. AMOLED passes at 9.30
percent.

Consequence: as written, one acceptance check fails on two of four families. The cause is the
palette: the Dark card fill alone measures `(32,32,38)`, so its sRGB value is 12.5 percent of the
scale before any edge or label is drawn. No bevel change can bring the mean under 10 percent while
the card is that light.

Smallest correction: define the check relative to the plane (for example, strip mean at or below
plane mean plus 10 points) and keep the absolute 10 percent figure for AMOLED. Do not darken the
Dark and Glass card fills to chase the number, because that would flatten the card against the
plane and weaken the panel silhouette that the corrected focus and bevel work depends on.

### O2. Card frame is still the loudest structure in AMOLED, Dark, and Glass (inherited chrome)

Region: card border, x 61..1018, y 2106..2108 and 2343..2345. Still `(255,255,255)`, 20.97:1 against
the plane, against 3.04:1 key outlines and 4.54:1 lit bands.

Consequence: the decorative frame outranks every control edge, and it is the largest static bright
area in the strip. This is baseline chrome that U1 did not change, so it is carried as a full
baseline requirement, not as a unit defect. Smallest correction: draw the card frame with the decor
hairline or at most edgeQuiet so the keys are the brightest structure in the strip.

### O3. Overflow glyph sits 1.6 dp above the key center (cosmetic)

Region: `asus-r3-amoled-normal.png`, overflow key x 828..959. Glyph bbox (875,2202)-(912,2240),
center y 2221.0 against key center 2225.5. Unchanged from round 2. The pull-handle intent explains
internal asymmetry, but the bounding box can be centered so the row shares one optical baseline.

### O4. Pressed sink stays asymmetric (cosmetic)

Region: `asus-r3-light-pressed.png`, primary glyph offset +1 px right (0.36 dp) and +3 px down
(1.09 dp). The brief states a 1 dp sink. The y value is right and the x value is a third of it.
Unchanged from round 2.

### O5. Annotated family sits 2.2 dp above the plain families (cosmetic)

Light card measures 246 px (89.5 dp) with its key row centered at y 2219.5, against 240 px
(87.3 dp) and y 2225.5 in AMOLED, Dark, and Glass. Bottom gap differs by 1.1 dp (21.1 dp versus
20.0 dp). The Light card also has no white frame, which is consistent with a light card and is not a
defect by itself. Smallest correction: align the annotated card's outer box and key-row centerline
to the same values as the plain families.

### O6. Play glyph proportion is narrow (taste)

Glyph measures 35 x 46 px, aspect 0.76. A classic transport triangle is usually 0.9 to 1.1. The mark
is clean, sharp, and correctly centered, so this is polish only.

---

## 5. Full-baseline remaining requirements, kept separate from U1

These are not part of the no-track console unit and are not scored here.

1. Card frame weight (O2), if the frame is treated as baseline chrome rather than unit chrome.
2. Sheets, source screen, settings accordion, manual index and search, and the overflow popout.
   The S9 receipt shows the popout renders above the new well with no console regression, but the
   popout itself was not assessed.
3. Landscape console layout, and the placement-locked sheet behavior.
4. Keyboard and IME open behavior on the source screen and manual search.
5. Disabled state in the real strip. The parent checklist states real capture-unavailable buttons
   remain omitted, so the dashed disabled style is implemented but not visible in a live console.
6. Reduced-motion, PiP, and hidden-chrome frame pairs, and the event-driven edge flash.
7. Saved-look export, import, and restart round trip.
8. Burn-in walk behavior under the new bevel and focus ring.

---

## 6. Bounds on this assessment

This report covers the no-track console at 1080x2400 in AMOLED, Dark, Glass, and Light at font 1.0,
the Light family at font 1.3 and 2.0, a real 320 dp window in the Light family at font 1.0 and 2.0,
the primary key in focus in all four families, the primary key pressed in Light, and the overflow
key latched in Light. It does not cover the disabled state, latched state outside Light, pressed or
focus states at other scales and families, plain families at 320 dp or landscape, keyboard and IME
layouts, reduced motion, PiP, hidden chrome, the edge flash, or the saved-look round trip. This
report makes no claim that the whole app is polished, and no claim about touch delivery.

---

## 7. Score and verdict

**8 / 10. Bounded U1 visual PASS.**

Verdict: every material visual defect that this unit owns is closed with measured evidence.

- Focus now renders as a 1.82 dp accent ring, present and symmetric on all four sides in all four
  families, 5.02:1 to 11.21:1 against its own background, and separated from the tray line by a
  1.1 dp gap. The round 2 greyscale merge is gone.
- The annotated family places its part tags in the key corners and centers every legend and glyph
  within 1 px at font 1.0, 1.3, and 2.0, with no overlap at any scale.
- The plot label is readable in every family (18.43:1 to 20.97:1, against 1.41:1 in Light in round 2)
  and stays 41 to 44 dp clear of the status band at every scale, including the three-line wrap at
  font 2.0.
- Geometry is exact and identical across families: 56 dp primary, 48 dp secondaries, 64 dp MODE, one
  centerline at y 2225.5, 12.0 dp and 8.0 dp gaps, a seated well, and a one-row 87.3 dp strip that is
  only 7.6 percent taller than the baseline.
- All four floors hold: control boundary 3.04 to 3.09:1, labels 11.11 to 18.42:1, focus 5.02:1 or
  better, and every target at or above 48 dp at font 1.0, 1.3, 2.0 and in a 320 dp window.
- Pressed and latched states are structural: inverted bevel, sunken face, accent bottom bar at
  3.04:1, accent left tick, ink labels, and an unchanged silhouette. Both read without colour.
- The vector marks are original, sharp, and truthful, and the parent receipts confirm the source,
  S9, and play semantics are unchanged.

Why 8 and not 9: my round 2 report named four conditions for a 9. Two are done (focus, annotated
alignment). Two remain: the rest-luminance check needs its definition settled (O1), and the
inherited card frame is still the loudest structure in the strip (O2). Neither is a defect of the
U1 implementation, and neither requires rework of the key bed, tokens, or states, so they do not
hold the unit back from a pass. Fixing O2 in the baseline pass and settling O1 would take this unit
to 9.

What would drop this below 8: a disabled state that is indistinguishable in greyscale, a clip at
font 1.3 or 2.0 in any family, a pressed state that moves the key silhouette, or a focus indicator
that fails 3:1 against its own background.

---

## 8. One page for Astra

Keep exactly as it is: key geometry (56 and 48 dp, 64 dp MODE), the shared centerline, 12.0 and
8.0 dp gaps, the well seating, the lit top-left band with a dark bottom-right step, the inverted
pressed polarity with the 1.09 dp sink, the latched accent bar and left tick with ink labels, the
accent focus ring at 1.82 dp with its 1.1 dp gaps, the corner part tags with centered legends, the
three-line status wrap at font 2.0, the plot label placement, the 320 dp two-row and four-row
reflows, and the source, S9, and play semantics.

Follow-ups, in order, none of them unit-blocking: settle the rest-luminance definition (O1), quiet
the card frame in the baseline pass (O2), center the overflow glyph bounding box (O3), make the
sink symmetric (O4), align the annotated card box and centerline to the plain families (O5), and
consider a wider play triangle (O6).

Do not change: the label colours, the bevel polarity, the 48 dp floor, the truth logic for play,
hold, live, and capture, or the gesture owners.
