# U1 code review, attempt 2 of 4

- Freeze: `dev/scratch/visual-u1-20260909/source-visual-r2.sha256` against `b74beb278b298bdc9b7c614339ce4e9a1a373009`.
- All 15 freeze hashes matched.
- Reviewer: Grok 4.6 high. No visual-taste judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. This round accepts the bounded correction source.
- Historical: attempt 1 = **8/10**, unchanged. R06 scores unchanged.

This is source acceptance of the numeric layout, density, hit/focus, and bevel correction. It is not device or beauty acceptance.

## Diff versus attempt 1

Unchanged hashes: appearance value/codec, palette, RoomStyle, editor, manual, spec. Wire, look_version, workflow, and dispatch owners are the same call graph.

Changed: `Console.kt` tactile vertical pad 8.dp, `TactileConsole.kt`, `ConsoleTactileTokens.kt` layout and raised-high floor, tests, contract, execution, SERIOUS-TODOS.

## What the correction does

`TactileConsoleKeybed` installs a local `Density` for the key bed only. `density` is `LocalView.resources.displayMetrics.density`. `fontScale` and `TextUnit.toDp` / `Dp.toSp` come from the inherited Compose density, so the nonlinear font converter stays. `Dp.toPx` then uses the resource density. 48.dp and 56.dp hit minima are physical display dp.

`ConsoleKeybedPolicy.layout` uses inner-well width, not a 340dp screen guess. Executed vectors:

- 306dp font 1 -> 1 row 56/64/56/48
- 306dp font 1.3 -> 1 row 56/67/56/48
- 306dp font 2 -> 4 full-width rows
- 234dp font 1 and 1.3 -> 2 rows
- HOLD/LIVE primary grows to 67dp at font 1.3

Gaps 12/8/8 fit the 1-row budget (`+ 28f`). Overflow stays 48dp on the trailing edge.

Focus stroke is drawn at `-2.dp` with size `+4.dp`, outside the clickable box. Bed `padding(3.dp)` reserves that ring. Bevel `gap` is 0. The outer silhouette is the full hit box. Bevel lines sit at `1.dp + depth/2`, so a 2dp primary stroke stays inside the layout. Raised high/left vs low/right, reversed when sunk. No uniform inner bright border.

Play/HOLD/LIVE, capture omit, MODE random-arm, SRC, and S9 `overflowHandleGesture` are unchanged.

## Evidence

Verified by reading the changed files and recomputing the layout table. Inherited writer `visual-r2-correction/tests-r2.log` `OK (210 tests)` and parent gate 929/0, not rerun here.

Unobserved: whether `drawWithContent` paints the exterior ring without parent clip on device, and whether `displayMetrics.density` equals the inherited Compose density on ASUS.

## Material findings

None on the inspected correction source.

## Residual notes

U2-N1. `androidDisplayDensityKeepsPhysicalMinimumsAtEveryFontScale` uses stock `Density(2.75f, font)`. It proves Compose `Dp.toPx` ignores fontScale. It does not construct the production `LocalView` override. The override is verified by reading `TactileConsole.kt:41-50`.

U2-N2. Wrapping still uses linear `9f * fontScale` for label width while text conversion is nonlinear. Font 2 is forced to 4 rows by `fontScale >= 1.8f`.

U2-N3. Exterior focus still depends on drawing outside the key node plus 3.dp bed padding. Pixel proof is visual/device work.

## Disposition

Accept the correction at 8/10. Preserve attempt 1 = 8. No freeze held.
