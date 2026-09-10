# DeepSeek visual assessment, U1 round 2

Assessor: DeepSeek. Panel round: 2 of 4. Unit: U1, tactile no-track console.
Scope: the no-track console only, as instructed. Whole-app styling is not scored.
Independence: I did not read `muse-visual-round-02.md` or `grok-code-round-*.md`. Code quality
is not design evidence. No source edits, no build, no device control, no network.
Method: PIL measurement of the PNGs at actual density 2.75 px/dp (440 dpi), as stated in
`r2-visual-context.txt`. Contrast is WCAG 2.x relative luminance. "Mean luminance" of a strip means
the mean sRGB value of the region divided by 255, the same measure used on the baseline capture.
All values below are measured from pixels unless marked as a computed target.

---

## 1. Evidence inventory

### Observed, used

| file | captured | what it shows |
|---|---|---|
| `asus-r2-amoled-normal.png` | 17:12:34 | AMOLED no-track console, font 1.0 |
| `asus-r2-amoled-font1.3.png` | 17:12:49 | same, font 1.3 |
| `asus-r2-amoled-font2.0.png` | 17:12:53 | same, font 2.0 |
| `asus-r2-light-normal.png` | 17:14:40 | Light family no-track console, font 1.0, designators |
| `asus-r2-dark-normal.png` | 17:17:01 | Dark family, font 1.0 |
| `asus-r2-glass-normal.png` | 17:17:47 | Glass family, font 1.0, radius 0 |
| `asus-r2-glass-key-focus.png` | 17:18:38 | second TAB focus on the play key, no activation |
| `asus-r2-glass-pressed.png` | 17:18:59 | real MotionEvent DOWN on the play key, CANCEL after |
| `asus-r2-glass-mic-live.png` | 17:19:32 | source `mic` live, primary key reads HOLD |
| `asus-r2-glass-held.png` | 17:19:57 | after HOLD, primary key reads LIVE |
| `design-20260909/asus-swipe.png` | baseline | pre-U1 no-source console for footprint and look comparison |

`asus-r2-glass-focus.png` (17:18:09) is pixel-identical to `asus-r2-glass-normal.png` outside the
status band. I treat it as a capture without a visible focus target and did not use it as focus
evidence. `failed-theme-selector-*.png` were excluded as instructed. Older unlabeled PNGs
(`asus-amoled-*.png`, `asus-font2-pinned-controls.png`) were not used.

### Not observed, so not scored

Latched or selected state, disabled state, focus or pressed in Dark, Light, or AMOLED,
Light or Dark at font 1.3 and 2.0, landscape, 320 dp narrow window, keyboard or IME open,
reduced-motion and PiP frame pairs, hidden-chrome frames, beam or event-edge flash, saved-look
import, TalkBack announcement. Touch delivery is parent-supplied, not inferred from pixels.

---

## 2. Measured geometry, all four families

Card: 958 x 238 px (348 x 86.5 dp), x 61..1018, y 2108..2345 in AMOLED, Dark, and Glass.
Light measures y 2104..2342 (see D8).

| element | measured | in dp | target | verdict |
|---|---|---|---|---|
| primary key | 154 x 154 px | 56 x 56 dp | 56 dp | exact |
| MODE key | 176 x 132 px | 64 x 48 dp | 48 dp min | pass |
| SRC key | 154 x 132 px | 56 x 48 dp | 48 dp min | pass |
| overflow key | 132 x 132 px | 48 x 48 dp | 48 dp min | pass |
| key centerline | all keys center y = 2226.5 | | one centerline | pass |
| primary to MODE gap | 33 px | 12.0 dp | 12 dp transport gap | exact |
| MODE to SRC gap | 22 px | 8.0 dp | 8 dp control gap | exact |
| well | 856 x 170 px, x 112..967, y 2142..2311 | 311 x 61.8 dp | spans inner width | pass |
| well to key padding | 8 px | 2.9 dp | seated | pass |
| SRC to overflow | 169 px flexible | 61.5 dp | spacer | same as baseline |

Baseline card is 223 px (81.1 dp). New card is 238 px (86.5 dp). The one-row footprint grew
15 px, or 6.8 percent. The trace region above the card loses 15 px of about 1800 px, under 1 percent. This is inside the
"same silhouette, seated well" intent and is not a defect.

Light family: curated Glass radius 0 is confirmed on the Glass keys. The top border of each
Glass key runs the full 154 or 132 px width with no corner inset, so the keys are square, as the
source correction requires.

---

## 3. Criterion results

### 3.1 Top-left lamp and pressed inversion

Normal state, all four families: lit band on top and left, dark step on bottom and right.
AMOLED column x=196: `#5A5A5A` outline, then 5 px `#7E7E7E` lit band, then `#141414` face. The
bottom is a 5 px black step plus outline. Dark: outline `(90,92,99)`, lit `(144,141,147)`, face
`(43,38,49)`. Glass: outline `(90,92,99)`, lit `(127,130,137)`, face `(21,26,38)`.

Pressed state, Glass play key: the lit band moves to the bottom and right, the top and left
become the dark step, the face darkens from `(21,26,38)` to `(12,16,24)`, and the glyph sinks
+1 px right and +3 px down. The key outline stays `(90,92,99)`. The glyph stays a play triangle,
so the capture does not claim playback. This is a correct momentary inversion.

### 3.2 Continuous boundary and contrast

| pair | AMOLED | Dark | Glass | floor | verdict |
|---|---|---|---|---|---|
| key outline vs well fill | 3.04:1 | 3.04:1 | 3.02:1 | 3:1 | pass, thin margin |
| lit band vs face | 4.54:1 | 4.51:1 | 4.52:1 | 1.6:1 target | pass, strong |
| label vs face | 18.42:1 | 12.96:1 | 16.12:1 | 4.5:1 | pass |
| well step vs well fill | 3.04:1 | 3.04:1 | 3.02:1 | 3:1 | pass |
| Light label vs face | 11.1:1 | | | 4.5:1 | pass |

The key outlines are continuous on all four sides. The corner join is closed: the top line starts
at x=120 and the left line starts at y=2150, and they meet.

### 3.3 Vector symbols

Play glyph: 36 x 46 px (13.1 x 16.7 dp), filled, sharp vertices, straight edges, no stroke, no
rounding. Bounding-box center sits 2 px right of the key center, which is correct optical
placement for a right-pointing triangle (mass is on the left).

Overflow glyph: 38 x 39 px (13.8 x 14.2 dp), a horizontal rail with a short centre stem and three
square dots below. Stroke weight is consistent, ends are square. This is the pull-handle geometry
the parent describes as intentional.

No font glyph supplies a transport or overflow mark. At font 2.0 the symbols keep their dp size
and stay centered in the full-width keys.

### 3.4 Optical center, gaps, alignment

AMOLED 1.0: MODE label center (394.0, 2226.5) against key center (394.5, 2226.5). SRC label
center (583.0, 2226.5) against key center (581.5, 2226.5). Both within 1.5 px. Play glyph center
y = 2226.5, exact. Overflow glyph center y = 2222.0, which is 4.5 px (1.6 dp) above the key
center. See D5.

Light family: MODE label center y = 2244.5, which is 18 px (6.5 dp) below the key center, and
the play glyph center y = 2239.5, 13 px (4.7 dp) below center. See D3.

### 3.5 Focus state, Glass play key

The focus ring is a 2 px (0.73 dp) blue-tinted line, measured `(77,93,118)`, drawn just inside the
well step line with a 3 px (1.09 dp) gap to the key outline. It is present on all four sides and
is not clipped by the well or the card.

| pair | value | floor | verdict |
|---|---|---|---|
| ring vs well fill `(5,7,12)` | 3.02:1 | 3:1 | passes by 0.02 |
| ring vs card surface `(16,19,28)` | 2.78:1 | 3:1 | below floor on this side |
| ring vs adjacent well step line `(90,92,99)` | 1.00:1 | 3:1 | identical luminance |

Greyscale values: ring 91, well step line 92. See D2.

### 3.6 Displayed state clarity, mic

`mic` live: primary key reads HOLD, 87 x 28 px (31.6 x 10.2 dp), centered at (198.0, 2226.5),
color `(244,246,255)` on face `(21,26,38)` = 16.12:1. Status band reads `src · mic` and
`119.3 fps · 960 segs`.

After HOLD: primary key reads LIVE, 85 x 28 px, centered at (197.0, 2227.5), 17.66:1 on face
`(12,16,24)`. Status band reads `src · mic` and `0.0 fps · 0 segs`.

The label, the source string, and the fps and segment counters agree. The held state also darkens
the key face to the pressed tone, so held versus live is distinguishable by structure and text, not
by hue. The primary key keeps its full bevel in both word-label states. I do not read HOLD as an
audio play or pause claim.

### 3.7 Signal dominance and footprint

| region | measured mean luminance |
|---|---|
| AMOLED strip | 23.85/255 = 9.35 percent |
| Dark strip | 46.97/255 = 18.42 percent |
| Glass strip | 34.32/255 = 13.46 percent |
| Light strip | 213.92/255 = 83.89 percent |
| Light full screen | 27.18/255 = 10.66 percent |

The plot region contains no chrome in any capture. The AMOLED strip meets the resolved rest cap of
10 percent with 0.65 points of headroom. Dark and Glass exceed it. See D1.

The lit bands occupy 1.32 percent of the AMOLED card area, so even at the 0.45 luminance clamp the
strip mean would rise only to about 9.6 percent, still under the rest cap.

### 3.8 Font scale 1.3 and 2.0, AMOLED

Font 1.3: card height unchanged at 238 px. Labels scale, no clip. MODE label grows to 117 x 39 px
inside a key that grows to 184 px (66.9 dp), and the play key narrows to 146 px (53.1 dp). See D6.
Font 2.0: the key bed reflows to four full-width stacked keys, 840 px (305.5 dp) wide, with 8.7 dp
gaps. Play stays 56 dp. MODE and SRC measure 50.5 dp, and overflow 48 dp. No label clips. Card height
259 dp, which is 29.7 percent of the 872 dp screen, and the trace keeps 65 percent. All labels are
centered within 1.5 px of their key centers.

---

## 4. Defects, each with region, consequence, and correction

### D1. Strip mean luminance exceeds the rest cap on Dark and Glass (material, metric)

Region: console card, `asus-r2-dark-normal.png` y 2108..2345 and `asus-r2-glass-normal.png`
same band. Measured 18.42 percent (Dark) and 13.46 percent (Glass) against the resolved rest cap
of 10 percent.

Consequence: the acceptance metric fails on two of four families. The cause is partly the palette: the Dark card fill alone measures `(32,32,38)`, so its sRGB value
is 12.5 percent of the scale before any edge or label is drawn. A 10 percent mean is not reachable
on a card fill that light.

Correction: redefine the cap relative to the plane, for example "strip mean luminance at or below
plane mean plus 10 points", and keep the absolute 10 percent figure for AMOLED only. No pixel change
is needed for Dark or Glass if the metric is corrected. If the parent wants the absolute cap on all
families, the card fill must drop to near black, which would flatten the card against the plane.

### D2. Focus ring is at the floor and merges in greyscale (material, accessibility)

Region: `asus-r2-glass-key-focus.png`, play key perimeter, x 111..115 and y 2140..2144 (left and top
edges). The ring measures `(77,93,118)`, luminance 0.1071. The adjacent well step line measures
`(90,92,99)`, luminance 0.1073. Their contrast against each other is 1.00:1 and their greyscale
values are 91 and 92.

Consequence: against the well fill the ring clears the 3:1 floor by 0.02 (3.02:1), and against the
card surface it is 2.78:1, below the floor. In greyscale the ring and the well step line merge into
one thicker tray edge, so the focus indicator is distinguished by hue and by a 2 px thickness change
only. A user who cannot separate the blue tint from neutral grey sees no new indicator, just a
slightly thicker line.

Correction: raise the ring to at least 4.5:1 against the well fill and make it at least 2 dp thick.
The full Glass accent `(127,184,255)` measures 9.79:1 against the well fill and would be unmistakable.
If the low-alpha look must stay, add a second channel: draw the ring outside the key silhouette with a
2 dp plane gap, so the ring sits on the card surface rather than inside the tray line.

### D3. Light family legend and glyph sit off the key center (material, cross-family)

Region: `asus-r2-light-normal.png`, console, MODE key x 307..481 and play key x 120..273. The MODE
and SRC legends measure center y = 2244.5, which is 18 px (6.5 dp) below the key center 2226.5. The
play glyph center is 13 px (4.7 dp) below center. The V2 designator sits at y 2191..2204, 29 px
(10.5 dp) above center.

Consequence: in the Annotated family the legend and the transport glyph no longer share the optical
centerline that AMOLED, Dark, and Glass use. The strip's content baseline differs by 6.5 dp between
families for the same component. The stacked designator also makes the key a two-line layout, and the
Light family at font 1.3 and 2.0 is not captured, so the growth behavior is unverified.

Correction: either move the designator to the top-left corner at 3 dp inset, which is where the
existing `designators` code places it and where silk-screen part numbers belong, or keep the stack
and rebalance so the legend lands within 1 dp of the key center. Capture Light at 1.3 and 2.0 either
way.

### D4. Card frame is the loudest structure in the strip (material, hierarchy, inherited)

Region: all four captures, card border at x 61..1018, y 2108..2110 and 2343..2345. The border
measures `(255,255,255)` in AMOLED, Dark, and Glass, which is 20.97:1 against the plane.

Consequence: the decorative frame outranks every control edge in the strip. The key outlines measure
3.04:1 and the lit bands 4.54:1. The frame is also the largest static bright area in the console, so
it is the biggest burn-in contributor there. This frame is inherited from the baseline and is not a U1
regression, but it weakens the U1 goal that important surfaces read as the dimensional ones.

Correction: draw the card frame with the decor hairline (AMOLED `#2E2E2E`, 1.66:1) or at most
`edgeQuiet` (3.04:1), so the lit key edges are the brightest chrome in the strip. If the frame change
is deferred to the baseline pass, record it as a known hierarchy inversion.

### D5. Overflow glyph sits 1.6 dp above the key center (minor)

Region: `asus-r2-amoled-normal.png`, overflow key x 828..959. Glyph bbox (875,2203)-(912,2241),
center y 2222.0 against key center 2226.5.

Consequence: in the same row, the play glyph and the MODE and SRC legends are optically centered
while the overflow mark sits high, so the row's visual baseline wobbles by 1.6 dp. The pull-handle
intent explains the internal asymmetry, but the bounding box can still be centered.

Correction: center the glyph bounding box on the key center and move the rail and stem inside it.

### D6. Primary key shrinks at font 1.3 (minor)

Region: `asus-r2-amoled-font1.3.png`, play key top border x 120..265, 146 px (53.1 dp), against
154 px (56 dp) at font 1.0. MODE grows from 176 px to 184 px in the same capture.

Consequence: the 56 dp primary promise is not held at 1.3, though the primary remains the largest
key and stays above the 48 dp floor. The row is width constrained, so MODE's wider label squeezes
the play key.

Correction: reserve the primary key's 56 dp width and let the flexible spacer absorb the change, or
let the row wrap earlier.

### D7. Pressed sink is asymmetric (minor)

Region: `asus-r2-glass-pressed.png` play glyph bbox (182,2207)-(216,2252) against normal
(181,2204)-(215,2249). Offset is +1 px right and +3 px down, which is 0.36 dp and 1.09 dp.

Consequence: the design states a 1 dp sink on both axes. The rendered sink is one third of that on x.
The eye reads the downward move, so the effect is not broken, but the token and the pixels disagree.

Correction: use a single sink value for both axes, or document the deliberate vertical bias.

### D8. Light family card sits 4 px higher and 3 px shorter (minor)

Region: `asus-r2-light-normal.png` card outer bounds y 2104..2342 against 2108..2345 in the other
three families. The Light card also has no white frame, and its edge measures `(215,211,203)`.

Consequence: the strip's bottom gap to the gesture band differs by about 1 dp between families.
Likely a consequence of a different border treatment for a light card, not a layout error.

Correction: confirm whether the Light card intentionally omits the outer frame, and if so, align the
card's outer box to the same 86.5 dp height and bottom inset as the other families.

### D9. Play glyph proportion is narrow (polish)

Region: all captures, play glyph 36 x 46 px, aspect 0.78 (13.1 x 16.7 dp). A classic transport
triangle is usually 0.9 to 1.1. The mark is clean and sharp, so this is taste, not a fault.

Correction: if a wider read is wanted, widen the triangle to about 1.0 aspect and keep the optical
offset.

---

## 5. Outside U1, tracked, not scored

1. Light `SIGNAL CHECK` measures `(39,39,39)` on black, which is 1.41:1. The parent has confirmed
   this is a full-baseline defect and will fix it. Region: `asus-r2-light-normal.png`, y 285..320.
2. Font 2.0 status band truncates the left string to `src · no so...` and the right string starts
   immediately after it, and the second line truncates to `119.3 fps · ...`. The gap between the
   second line and `SIGNAL CHECK` collapses from 27.6 dp at font 1.0 to 4.0 dp at 2.0. No overlap in
   this capture. Region: `asus-r2-amoled-font2.0.png`, y 160..350. Parent-tracked baseline TODO.
3. The status band reads `×0.10` with no `·a` suffix. Context states gain 0.10 is a temporary test
   gesture change, so I do not treat it as a U1 regression.

---

## 6. Verification gaps

The score below covers only observed states. These remain unverified and could change it:

1. Latched or selected state, and disabled state, in any family. Two of the five U1 states.
2. Focus and pressed in Dark, Light, and AMOLED. Only Glass was captured.
3. Light family at font 1.3 and 2.0. This is the family with a two-line key layout, so it carries the
   highest clip risk.
4. Landscape and 320 dp narrow window, keyboard open.
5. Reduced-motion, PiP, and hidden-chrome frame pairs, to prove no idle delta.
6. Event-driven edge flash and beam state. The parent states this branch has no autonomous motion or
   edge flash, so the beam-lit edge recipe is not exercised.
7. Saved-look export and import round trip. Not visual, so not scored here.
8. TalkBack announcement and keyboard traversal order. Touch delivery is parent-supplied.

---

## 7. Score

**8 / 10. Pass on the evidence available, with bounds.**

Why 8 and not lower: every measurable floor that U1 set is met. Geometry is exact to the pixel and
identical across four families (56 dp primary, 48 dp secondaries, one centerline, 12 and 8 dp gaps,
seated well). The tactile read is real and measured: lit band 4.5:1 against the face, dark bottom
step, correct top-left lamp, and a correct pressed inversion with a 1.09 dp sink. Control silhouettes
clear 3:1 in all four families. Labels measure 11.1:1 to 18.4:1. No label clips at font 1.0, 1.3, or
2.0, and every target stays at or above 48 dp. The vector symbols are original, closed, sharp, and
truthful, and the mic HOLD and LIVE states agree with the source string and the counters. The
one-row footprint grows only 6.8 percent over baseline. AMOLED keeps signal dominance at 9.35 percent
strip luminance.

Why not higher: D2 (focus ring at the floor, greyscale merge) and D3 (Light legend 6.5 dp off the
cross-family centerline) are real in-scope quality misses, and D1 shows the rest-luminance acceptance
metric fails on two families as written. D4 is an inherited hierarchy inversion that keeps the
decorative frame louder than the keys.

What would move this to 9 or 10: fix D2 so focus is distinguishable without hue and at least 4.5:1
against its background. Fix D3 so the legend returns to the key optical center in the Annotated
family and capture Light at 1.3 and 2.0, settle D1's metric definition, and quiet the card frame
(D4). Adding the unobserved states would then complete the unit.

What would drop this below 8: a latched or disabled state that is indistinguishable in greyscale, a
Light clip at font 1.3 or 2.0, or a pressed state that moves the key silhouette.

Bounds on the pass: this score covers normal, pressed, focus, and the mic live and held states, in
the families and scales listed in section 1. It is not acceptance of the two unobserved states
(latched, disabled), of non-Glass interaction states, or of Light at large font. I make no code
acceptance claim, and no claim about touch delivery.

---

## 8. One-page summary for Astra

Keep: geometry, well, lamp, lit band strength, pressed inversion, vector symbols, word labels for
mic states, font 2.0 reflow.

Fix in order: D2 focus ring distinctness, D3 Light legend centering plus Light 1.3 and 2.0 captures,
D4 card frame weight, D1 metric definition, then D5 to D9 polish.

Do not change: the 56 and 48 dp targets, the shared centerline, the 12 and 8 dp gaps, the label
colors, or the playback and source truth logic.
