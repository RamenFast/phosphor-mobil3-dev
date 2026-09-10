# U1 code review, attempt 3 of 4

- Runtime freeze: `dev/scratch/visual-u1-20260909/source-visual-r3.sha256` (19 paths) against `b74beb278b298bdc9b7c614339ce4e9a1a373009`.
- Test-only additions: `r3-source-assertion-repair/source-addition.sha256` (`GridDataTest.kt`, `StageGesturePolicyTest.kt`).
- All 19 runtime hashes and both test hashes matched after fullgate r3b.
- Reviewer: Grok 4.6 high. No visual-taste judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. This round accepts the bounded r3 source.
- Historical: code attempts 1 and 2 = **8/10**, unchanged. Muse visual 5 then 7 and Deep 8 are separate. R06 scores unchanged.

This is source acceptance. It is not device or beauty acceptance.

## Scope

New or changed versus attempt 2: `StageReadability.kt`, `PhosphorScreen.kt`, `Console.kt` StatusBand and tactile card pad, `TactileConsole.kt` legend layout and 5.dp bed pad, `ConsoleTactileTokens.kt` focus 4.6, presentation/stage tests, contract, execution, SERIOUS-TODOS. look_version wire and play/MODE/SRC/S9 owners are unchanged.

## Runtime, verified by reading

Plot ink: `StageReadability.plotInk` composites onto black locally. PhosphorScreen copies `p.ink` only for SIGNAL CHECK. Curated values are not written. `StageReadabilityTest.fourFamiliesKeepAuthoredBytesAndReadableBlackPlotText` executes that.

Status band: `rememberTextMeasurer` takes unwrapped 1-line widths. `stackStatus` uses long addition. Stacked rows set `maxLines = Int.MAX_VALUE`. Unstacked left stays 1 line only after the unwrapped widths already fit. `onHeightChanged` reports box height. `statusBandHeightPx` feeds `signalTopDp` only. StatusBand does not read that px. One extra compose, then stable.

Signal label: `maxOf(88f, bandHeightDp + 8f)` while the band is shown, else 88. Independent of StatusBand internals.

Focus: tokens keep authored accent at >= 4.6 against the well, else blend. Ring is still a 2.dp stroke outside the clickable box (`Offset(-2.dp)`). Bed padding is 5.dp. Console tactile card pad is 12.dp / 6.dp versus legacy 14/10. Hit layout remains the key box.

Tags: custom `Layout` measures main content and the designator separately. Tag places at top-left inset. Main stays centered. `legendHeight` grows only when the tag box would intersect the centered main. Tests use measured-size fixtures, including a font-2-ish 67dp key.

No new dispatch, timers, or saved-palette writes.

## Fullgate JVM fails, then test-only repair

r3 compile passed with 933 tests and 2 source-string failures. Those tests are outside the runtime freeze.

GridDataTest expected an inline band `if`. Runtime extracted the same predicate to `bandShown` and still opens StatusBand with GridData ungated by hud. The repair now requires `val bandShown = ...` before `if (bandShown)` and StatusBand. Same owners.

StageGesturePolicyTest looked for contiguous `Dim.consolePadH` inner pad. `StageChromeBounds.Card.Console` still precedes `.padding(style.space(2.dp))`. Inner pad is tactile 12.dp else `Dim.consolePadH`. The repair keeps transform-before-padding and names that split. Not a gesture-owner regression.

r3b: 933 tests, 0 failures. Inherited, not rerun here. Writer 214 focused tests also inherited.

## Material findings

None on the frozen runtime. The two JVM fails were stale source-string seams plus the intended tactile pad split.

## Residual notes

U3-N1. Status stack policy is tested with injected pixel widths, not a live TextMeasurer/font2 Compose run.

U3-N2. Exterior focus still depends on drawing outside the key node plus padding. Clip proof is visual/device.

U3-N3. `androidDisplayDensityKeepsPhysicalMinimumsAtEveryFontScale` still uses stock `Density`, not the production `LocalView` override.

## Disposition

Accept r3 at 8/10. Preserve attempts 1 and 2 = 8. No freeze held.
