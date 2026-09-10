# Sheet B1 code critique — round 01 (code quality only)

- Reviewer: Muse (independent critic, attempt 1 of 4)
- Freeze: repo HEAD `3090779` plus dirty `Sheets.kt`, `ManualSheet.kt`, `Controls.kt`
- Hashes verified before reading (all match):
  - `8be92e12…` Sheets.kt, `6dfa797c…` ManualSheet.kt, `24cc34e9…` Controls.kt
- Scope: CODE QUALITY only. No visual taste, no product edits, no Gradle/Git/adb.
- Test state per parent (not rerun): `testDebugUnitTest` 956/82 pass, `assembleDebug` clean.

## Score: 9/10 — PASS (>= 8)

All four musts hold on source read, the `SheetEntryPolicyTest` close-wiring
strings are intact, and the diff is tight (101 insertions, 9 deletions, only the
three frozen files).

## Must-by-must evidence

1. Header close is a closed vector, not font — HOLD.
   - `Sheets.kt` header is now a 48dp `Box` (`Modifier.size(48.dp)`,
     `contentAlignment = Alignment.Center`) with `.clickable(onClick = dismiss)`.
   - Both strings remain: `contentDescription = "Close settings"` and
     `contentDescription = "Close $title"` (also asserted by
     `sourceOnlyHeaderNestedAndExplicitClosesKeepTheirExistingOwners`).
   - Mark is `SheetChromeMark(SheetChromeVector.Close, p.ink2, size = 18.dp)`,
     a `Canvas` `drawPath` polygon closed with `close()` — no `"✕"` Mono left
     (zero `✕`/`✓` matches in all three files; the one comment line was
     reworded to "close").
   - Scrim-tap `detectTapGestures(onTap = { dismiss() })`, `BackHandler`,
     drag/settle wiring all still present as the test asserts.
2. Checked SheetRow has accent border AND non-hue mark — HOLD.
   - `Controls.kt` `SheetRow(checked)`: border switches to `p.accent`
     (hue), background sinks to `p.surface2` (luminance/face, non-hue), and a
     14dp `SheetChromeVector.Tick` vector mark prefixes the row (shape,
     non-hue). Font `"✓ "` prefix is gone.
   - Tick renders in `p.ink` rather than `p.accent`, so the shape cue survives
     even where accent hue is unreadable — correct choice.
3. Manual search is a 48dp field with vector search, placeholder, inline 48dp
   clear, no CLEAR SEARCH row — HOLD.
   - Field keeps `heightIn(min = 48.dp)`, mono `14.sp`, `SolidColor(p.accent)`
     cursor, `contentDescription = "Search manual chapters"`.
   - `decorationBox` Row: 18dp `Search` vector + weighted text box with
     `"source, control, or symptom"` placeholder when empty + inline 48dp
     `Box` (`contentDescription = "Clear search"`, 16dp `Close` vector) when
     non-empty; `Spacer(12.dp)` preserves trailing padding when empty.
   - Zero `CLEAR SEARCH` matches; the old `ManualKey("CLEAR SEARCH")` line is
     deleted. The single remaining `"Clear search"` is the inline button's
     accessibility label, which is expected.
4. Gestures, sheet order, accordion, chamfer, console, mic, HDR untouched —
   HOLD as far as this slice shows.
   - `git diff --stat` touches only the three frozen files; `Sheets.kt` diff
     is the header block plus the comment reword. Close-wiring strings the
     test pins (`BackHandler`, scrim tap, `clickable(onClick = dismiss)`,
     `onDragEnd`, `settleDismiss`, single `onDismiss()`) are all present.

## What is done well (code quality)

- One shared `SheetChromeMark` helper owns all three silhouettes (Close, Tick,
  Search) instead of three one-off canvases. Closed paths throughout;
  doc comment states the font/fontScale-independence contract.
- Search icon ring uses `PathFillType.EvenOdd` with two ovals — the standard
  ring construction, no stroke-width/font dependence.
- `SheetRow` composes three independent checked cues (border hue + sunk face +
  tick shape) without changing its public signature — all existing call sites
  keep working.
- Manual `decorationBox` follows the standard overlay pattern (placeholder
  under, `inner()` over) and keeps the clear target inside the field.
- Imports added are exactly the used ones (`Rect`, `Size`, `Path`,
  `PathFillType`, `Dp`, `Box`, `size`); `Canvas` was already imported.

## Findings (code quality only)

- F1 (minor, no point deduction beyond the 1): checked state is still not
  machine-readable. `SheetRow`'s root is a plain `clickable` Box with no
  `Role.Checkbox`/`stateDescription`; the old `"✓ "` prefix and the new tick
  are both visual-only. Pre-existing gap, not a regression — but the slice
  touched exactly this cue and could have closed it.
- F2 (note, out of slice — do NOT expand this slice to fix): `RangeDragRule`'s
  armed box (`drawRect(p.accent)`) remains a hue-only fill with no shape cue,
  inconsistent with the new SheetRow rule. Candidate for a later slice, not
  this one.

## Blocked

- Bottleneck: none for this slice — musts hold, wiring strings hold.
- Evidence: source read + hash match + `git diff` scope above.
- Best current result: this implementation (9/10, pass).
- Smallest next action: accept slice B1; file F1/F2 as follow-ups for a later
  sheet-hardening slice.
