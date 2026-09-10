# Muse visual critique — U1 round 2/4 (resting no-track console)

Independent assessment from pixels only. Round-1 report preserved unchanged.
Grok code scores are not design evidence and were not used. No source edits,
builds, device, or network. Density 2.75 (440dpi). filenames give font_scale.

Shots: `asus-r2-amoled-normal.png`, `asus-r2-amoled-font1.3.png`,
`asus-r2-amoled-font2.0.png`, `asus-r2-light-normal.png` vs baseline
`design-20260909/asus-swipe.png`. Context: `r2-visual-context.txt`,
`resolved-design.md`, `source-corrections.md` (curated Glass radius 0, 4 families
+ AMOLED default, HOLD/LIVE distinctness, S9 gesture ownership, 80–200ms fades,
no periodic samplers, multi-open settings). Dark/Glass shots not yet captured —
labeled UNOBSERVED throughout. Scope: tactile no-track console only.

## Measured geometry (px/2.75 = dp)

| Shot | Card | Bed | Verdict |
|---|---|---|---|
| amoled-normal | y2108–2345, x61–1018 → 348.4 × 86.5dp | play x120–273 (~56dp), MODE x307–480 (~63dp), SRC x505–658 (~56dp), spacer, S9 x828–959 (~48dp), one shared centerline (glyph/label centers ≈ y2226) | PASS — round-1 defect 1 (full-width play bar, 143.6dp card) FIXED; footprint matches baseline 84.6dp within 2dp |
| amoled-font1.3 | same 86.5dp card, one row, labels fit with air | one-row preserved | PASS — no clip, no wrap needed |
| amoled-font2.0 | card 259.6dp; four full-width rows 58.9 / 50.5 / 50.5 / 50.9dp; labels centered, unclipped (MODE x458–619, SRC x481–602, S9 glyph x521–558) | vertical stack | PASS — round-1 A1 (rows dipping to 40dp) FIXED; all rows ≥48dp; stacking is the correct 2.0 fallback |
| light-normal | y2104–2342, x64–1015 → 346.2 × 86.9dp | same one-row bed as AMOLED | PASS — identical footprint across families |

## Measured light and contrast (WCAG pairs, sampled)

- AMOLED labels white on #141414 = 18.4:1 (n=1096). PASS (≥4.5).
- AMOLED silhouette: outer edges 90-gray = 3.04:1 vs black; play left edge 126-gray
  = 5.17:1. PASS (≥3.0), thin margin on the outer line as in round 1.
- AMOLED play glyph 16.4dp vector triangle, centered; S9 glyph bright (230/199).
  Closed, stroked, optical center holds. PASS — original-vector, early-2000s pop.
- AMOLED strip mean 9.4% of panel max. PASS (≤10% rest; beam dominant; plot clean,
  idle diamond only, status band unchanged).
- AMOLED bevel polarity: top = bottom = 90-gray symmetric, left brightest (126).
  Left-lit ✓, top not distinguished from bottom — top-left lamp PARTIAL. Minor;
  resting pixels cannot prove pressed inversion (see gaps).
- LIGHT labels dark (med 40) on stone face (med 221) = 10.9:1. PASS.
- LIGHT status band cream, text 17.4:1. PASS. Designators (S1/V2/J1/S9) present
  and legible. Bevels read (light-carrier shaded edge visible).
- LIGHT strip is necessarily bright (83.9%) — the ≤10% budget is an AMOLED number;
  light-family dominance needs a beam-on shot to judge. Verification gap, not a defect.

## Defects (screenshot / region / consequence / fix)

1. `asus-r2-light-normal.png`, stage `SIGNAL CHECK` label (≈ y300, on black plot):
   peaks at 39-gray = 1.41:1 vs plot. Consequence: the stage label is invisible in
   the light family — below every floor (4.5 text, 3.0 graphic). It sits outside
   the strict U1 console but inside every light screenshot, and it blocks any
   light-family acceptance. Fix: stage label must use a readable-on-plot color
   (near-white as in AMOLED), never room ink; verify with a sampled pair ≥4.5:1.
2. Bevel lamp (both normals): bottom edge meters equal to top (90/90). Consequence:
   the top-left lamp is asserted, not proven, and pressed inversion is entirely
   unobserved. Fix: one finger-down pressed capture per family (or a stated
   static-press proof) showing inverted polarity + 1dp sink before acceptance.
3. Round-1 items 2–4 (48dp accounting, continuous ≥90-gray outline) are
   measurement-ambiguous on these shots and remain open pending a pressed/focus
   capture with stated border accounting.

## Expressly unobserved — no claim, blocking for acceptance

Dark + Glass families; pressed / latched / disabled / focus states (structural
distinction unverified); playing / paused / HOLD-LIVE displayOnly truth; seek and
title states; sheets; overflow popout; S9 gestures; reduced motion; PiP; landscape;
narrow window; migration/legacy rendering.

## Score: 7/10 — CONDITIONAL PASS on resting console, NO full U1 acceptance

Resting no-track console passes in AMOLED (1.0/1.3/2.0) and Light (1.0) with
compact footprint, measured contrast, and genuine tactile craft. Withheld points:
defect 1 (light stage label), unverified pressed inversion and state distinction,
unobserved Dark/Glass. Next: fix defect 1, add pressed + focus captures, then
Dark/Glass normals.

## Addendum B — Dark + Glass normals (font 1.0, via LOAD/DRAFTTACTILE/APPLY)

Failed-theme-selector captures were NOT used, per instruction. Score below is
unchanged: 7/10 stands and 7 is a FAIL under the >=8 bar. This addendum only
narrows the unobserved set.

- `asus-r2-dark-normal.png`: card 86.5dp, one-row bed, purple-tinted faces
  (face rgb 43,38,49). Labels 12.6:1 (med 242 vs face 43). Edges ~99-103 gray,
  left brightest (144). SIGNAL CHECK bright (peak 246). PASS for resting state.
- `asus-r2-glass-normal.png`: card 86.5dp, one-row bed. Outer border runs to the
  exact corner pixel (x61/y2108 fully bright, no rounding) — curated Glass
  radius 0 PRESERVED per source corrections. Labels 16.0:1. Edges 90-gray with
  brighter left (127). PASS for resting state.
- Observation (not defect): resting strip luminance scales with family face —
  AMOLED 9.4%, Glass 13.5%, Dark 18.4%. The ≤10% budget is an AMOLED number;
  family dominance needs beam-on shots to judge. Open gap.
- Resting console now passes in all four families at font 1.0 (+AMOLED 1.3/2.0).
  Still blocking acceptance: light SIGNAL CHECK fix, pressed/focus captures,
  beam-on dominance, playing/HOLD-LIVE truth, gestures, reduced motion.

## Addendum C — Glass focus + pressed states (observed state evidence only)

Shots: `asus-r2-glass-key-focus.png` (2nd-TAB focus on play, no activation),
`asus-r2-glass-pressed.png` (MOTION DOWN captured, CANCEL after, exit 0, no
playback). TalkBack behavior NOT inferred. Score unchanged: 7/10 FAIL stands.

- PRESSED: play face median darkens 28.3 → 17.3 (walk-tolerant region median)
  and glyph center sinks 2226.5 → 2229.5 (+3px ≈ 1.1dp, above the ±1.5px
  burn-in-walk noise, matching the 1dp sink spec). No playback fired, glyph and
  label intact, silhouette held. PASS for pressed depth on Glass.
- FOCUS: a bright line above the play key (row 2140, peak 90/92/99) exists in
  focus and not in normal (16/19/28); left-side pixels carry an accent blue
  lift (118 vs 99 in blue channel). Ring is present, outside the silhouette,
  accent-tinted. But sampled ring pixels meter ≈2.9–3.0 vs plane — borderline
  against the ≥3:1 ring floor, and continuity around all four sides is not
  proven from these crops. PARTIAL: ring exists; require a dedicated ring
  contrast/continuity measurement before acceptance.
- Method note: burn-in walk shifts chrome ~1–2px between captures, so raw
  pixel-diffs (28–38k changed px card-wide) are noise; only region medians and
  glyph-centroid shifts were used as evidence above.
- Still open: pressed/focus on other families, latched/disabled distinction,
  playing/HOLD-LIVE truth, beam-on dominance, reduced motion, gestures.

## Addendum D — Glass mic HOLD / held-LIVE states (displayed-state clarity)

Shots: `asus-r2-glass-mic-live.png` (mic live; stone reads HOLD), `asus-r2-glass-held.png`
(after HOLD; stone reads LIVE; mic later force-stopped). Near-silent ambient input:
tiny beam only — NOT beam-dominance proof. No audio play/pause inferred from HOLD.

- Display-only distinctness PRESERVED per source corrections: the stone shows text
  HOLD / LIVE, never the ▶ transport glyph. Display-only and real-play semantics
  are visually separate channels. PASS.
- Label contrast: HOLD 16.0:1, LIVE 17.8:1 vs key face. PASS (≥4.5).
- Latched distinction beyond the label: LIVE stone face meters darker (17.3 vs
  resting 28.3, region medians) — the held key sits sunk/dark, structurally
  distinct from resting, not hue-only. PASS (partial: accent-bar/tick detail
  below pixel-resolve from these crops; accepted as consistent with latched spec).
- Source truth: `src · mic` + live fps/segs while mic live; `0.0 fps · 0 segs`
  after HOLD — honest idle, no theatre. PASS.
- Score unchanged: 7/10 FAIL stands. Remaining for acceptance: light SIGNAL
  CHECK fix, ring continuity measure, beam-on dominance, reduced motion,
  gestures, and any still-unobserved states.
