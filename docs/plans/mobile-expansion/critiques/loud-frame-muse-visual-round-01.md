# Muse visual critique — loud-frame round 01 (chrome flatten only)

Scope: `dev/scratch/loud-frame-20260910/asus-no-track.png` (1080x2400) versus
the inherited loud-frame brief (triple-fill card + 0.86 ghost alpha).
Grok code 8 (bd35b08) is NOT visual evidence and was not used.
DeepSeek off — no DeepSeek report read.
U1 no-track tactile well is accepted and out of scope — not reopened, not scored.
No source / build / Git / device work. Pixel reads only.

## Artifact

- One shot: ASUS no-track idle, AMOLED-dark room, font 1.0.
- StatusBand top, SIGNAL CHECK mid-plot, console card bottom.
- Attached full frame viewed; pixel probes at native PNG resolution.

## Flatten verification (measured)

StatusBand (top):
- Vertical probe x=540: border 149-gray at y138-140, pure black 0,0,0 from
  y141 through y232, border 149-gray at y233-235, then black.
- Single top edge + single bottom edge, 3px each. No second inner line at
  +2dp. No plane step. One opaque fill (black) + one hairline.
- Side probe y=170: interior black, right border 149-gray at x1035.
- Border contrast 149-gray on black = 7.0:1. Decor-bright, unbroken rectangle.
- PASS: reads as one surface + hairline. Sandwich gone.

Console card (bottom):
- Vertical probe x=540: single top border 88-gray at y2085, then pure black
  interior (y2090-2205 read 0,0,0). No second fill line, no plane sandwich.
- One fill + one hairline + one content step. Same single-frame construction
  as the band, dimmer line (88-gray = 2.9:1 on black).
- Horizontal probe y=2140 across x30-1040: interior black; key silhouettes
  only (77-gray edges, 2.5:1) — keys are U1, not scored here.
- Card floats clear of the panel glass / nav bar (black gap y2210-2250
  before system nav). Not docked. Sharp corners preserved, no pills, no gloss.
- PASS: plane sandwich gone. No trace ghosting through the fill in this
  idle shot (interior 0,0,0).

## SIGNAL CHECK overlay (remaining loud item)

- Region y340-460 x0-400: mean ~6/255, max 149, only 4.5% pixels >20.
  Small left-aligned mono label on pure black. No box, no fill, no backplate.
- Reads quiet, not loud. No bleed, no ghost text, no second overlay layer.
- The empty black field dominates — bench law holds (beam off, chrome dim).
- PASS: nothing loud remains in this state.

## Craft notes

- Chrome rest is near-black: status row frac>20 only 6.9%, console 2.8%.
  Well under the quiet-bench spirit; no 0.60-cap violation visible here.
- Minor, non-failing: band hairline (149 / 7.0:1) is brighter than console
  card hairline (88 / 2.9:1). Consistent with band-as-readable-exception
  (opaque) vs card-as-quiet-frame, so not scored as a defect. Flagged only
  for the alpha re-verify pass (Grok LF-N1: translucent outer fill over a
  live trace still needs its 0.60-cap / bleed measurement on a beam-on shot).
- U1 well / keybed / pressed / focus / 320dp / font-scale / sheets / track
  row: explicitly unobserved and unscored in this round.

## Score: 8/10 — PASS (chrome flatten + idle overlay only)

Single-fill + single-hairline holds on both cards on measured pixels;
idle SIGNAL CHECK overlay is quiet. No new criteria invented.
Full-app and beam-on-trace acceptance NOT claimed.
