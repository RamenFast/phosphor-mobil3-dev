# Muse visual critique — U1 round 1/4 (AMOLED no-track only)

Scope: `asus-amoled-tactile.png` (1080x2400, actual density 2.75, font 1.0)
versus baseline `design-20260909/asus-swipe.png`. Code score (code8/927 tests)
is NOT visual evidence and was not used. No source edits, no builds, no device.

## Measured layout (density 2.75, px/2.75 = dp)

| Item | New shot | Baseline / target | Verdict |
|---|---|---|---|
| Console card | x61–1018, y1951–2345 → 348 × 143.6dp | 348 × 84.6dp, one row | FAIL — 59dp taller |
| Play key | x129–950, y1991–2139 → ~298 × 54dp full-width bar | 56dp stone in one-row bed | FAIL — wrapped to own row |
| MODE / SRC / overflow | ~90dp wide × 45.8dp tall each (y2179–2305 incl. borders) | 48dp min each, one shared centerline | FAIL (marginal) — 2dp under floor by inclusive measure; Astra to confirm interior vs border accounting |
| Gaps | pure black (0,0,0), well = plane | dark-room well recipe | PASS |
| Side/bottom insets | 22.2 / 19.6dp | ~22 / ~20dp | PASS |

## Measured light and contrast (WCAG pairs, sampled)

- Labels: white text on #141414 face = 18.4:1 (n=797 bright px, median 255). PASS (≥4.5).
- Silhouette outer edges (#5A5A5A=90) vs black = 3.04:1. PASS but thin margin above the 3.0 floor.
- Inner bevel steps (#4F4F4F=79) vs black = 2.56:1. Sub-floor — acceptable ONLY as decoration, never as the silhouette; the outer 90-gray line must remain the unbroken outline on all four sides.
- Face #141414 vs black plane = 1.14:1 — faces intentionally melt into the field; all boundary work rides on edges. Consistent with the bounded-depth brief.
- Strip mean luminance 8.3% of panel max. PASS (≤10% rest budget, beam dominant).
- Play vector triangle 16dp tall, centered, crisp white. PASS — pops without shouting.
- Status band, SIGNAL CHECK label, idle diamond, plot blackness: unchanged. PASS.

## Defects (exact region → consequence)

1. `asus-amoled-tactile.png`, console card y1951–2345 — play occupies a full-width
   row, card grows 84.6 → 143.6dp. Consequence: ~59dp of signal area lost in the
   no-track state, the exact state whose compactness the brief protects; U1 done
   criterion ("same silhouette with raised face") fails. Minimal fix: return play
   to the one-row bed (56dp stone + MODE/SRC/overflow 48dp, 12dp transport gap,
   8dp group gap, spacer before overflow), card back to ~85dp tall.
2. Same shot, row-2 keys y2179–2305 = 45.8dp inclusive. Consequence: if inclusive,
   the 48dp floor is missed on the three most-tapped keys. Minimal fix: add 6px
   (2.2dp) interior height or show border-exclusive measure proving 48dp.
3. Same shot, play bottom band (y2137–2139: 94/113/90) meters brighter at its core
   (4.3:1) than the top edge (3.0:1). Consequence: the top-left lamp reads
   inconsistent — the key looks bottom-lit. Minimal fix: verify bevel polarity in
   source (lit = top+left brightest) and re-shoot; if the bright bottom line is
   the adjacent well-step rather than the key edge, say so with a crop.
4. Inner step pixels at 79-gray (2.56:1) sit on MODE/SRC vertical edges (right edge
   sample 43-gray = 1.48:1, left edge 79). Consequence: right/bottom outlines risk
   falling below the silhouette floor where the outer bright line breaks.
   Minimal fix: guarantee one continuous ≥90-gray outer outline per key.

## What passes and must not regress

Tactile pop (bounded depth on true black), vector glyphs, white card hairline +
inner frame, black well gaps, 18:1 labels, 8.3% strip luminance, untouched status
band and idle truth, sharp corners, no pills.

## Not captured — no claim made

Pressed/latched/disabled/focus states, playing/paused/displayOnly truth, seek/title
states, sheets, popout, other families, font 1.3/2.0, landscape, narrow window,
reduced motion, PiP, gestures (S9 tap/pull). Full U1 visual acceptance is NOT granted.

## Score: 5/10 — FAIL (round 1 of 4)

Craft passes; layout fails the core U1 silhouette criterion. Code test results did
not influence this score. Next: minimal correction (§defect 1, then 2–4) and one
re-shoot of AMOLED no-track at font 1.0.

## Addendum A — font-2.0 pinned-controls shot (`asus-font2-pinned-controls.png`)

Stress case only (fontScale 2.0, controls pinned visible; font since restored).
Density 2.75 confirmed for both shots; the old 2.625 inference is retired.

- Card grows to 257.1dp tall with FOUR full-width stacked rows: play 48.0dp,
  MODE 42.5dp, SRC 42.5dp, S9 40.0dp. Labels unclipped, centered, no ellipsis
  (MODE bbox x458–619 inside its row; SRC x481–602; S9 glyph x521–558).
- Direction check: vertical stacking at 2.0 is the CORRECT fallback — one-row bed
  cannot fit 28sp labels, and full-width rows beat truncation. No layout surprise.
- Defect A1: rows 2–4 fall below the 48dp floor (42.5/42.5/40.0dp). Consequence:
  touch targets shrink exactly when users need them largest. Minimal fix: enforce
  48dp-min rows at any fontScale (card grows; signal yields — the accepted trade).
- Top status band shows overlap/truncation (`src · no …` + `xy45` collision).
  Logged as PRE-EXISTING shared baseline TODO, out of U1 scope, not charged here;
  confirm against baseline font-2.0 capture when available.
- Score unchanged: 5/10 FAIL round 1. Normal-font one-row restoration stays the
  required correction; A1 rides with it.
