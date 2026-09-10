# Sheet B2 Muse code — 9/10 PASS

2026-09-10. Muse Spark 1.3 Contributor. Source only. HEAD pending parent commit.

Grok chose opaque non-Glass plates (not status-rail suppress). LinkCard `open ↗` → prose `open`.

Hashes at score:
- Sheets.kt `e9e8cac7f76ae62b11091d7436044703e01ade8587f34915401f7654a37fd8ea`
- ManualSheet.kt `83561198c3968c22d3596d3e85530fa558a683eefa608d2cf8ae4b69f1cc70c1`
- Dimens.kt / PhosphorScreen.kt / Controls.kt hash-match freeze.

## Pass
- Non-Glass plate fill is opaque `p.surface`. Ghost status through 0.94 is gone by construction.
- Glass keeps dual layer: outer `sheetAlpha * panelAlphaScale`, inner opaque after pad. No blur/shadow.
- Scrim stays 0.40. Status rail still paints (overdraw only).
- LinkCard: 48dp, hairline, sharp corners, no font-glyph arrow, Controls.kt untouched.
- B1 close/tick/search, gestures, accordion, chamfer not in the diff.

## Watches (pixels later, not fails)
- Glass 16dp pad gutter between translucent outer and inner opaque — faint rim possible.
- Status rail still drawn under plates.
- RoomSheet tile Glass alphas live inside the opaque sheet.

Proceed to ASUS pixels.
