# Loud console frame flatten code review, attempt 1 of 4

- Tree: `phosphor-mobil3` HEAD `53d647904e46545ca49ca1b11d9f8850b2ea4692` plus dirty Console.kt and two tests.
- Freeze file: `dev/scratch/loud-frame-20260910/source.sha256`.
- Freeze hash matched `Console.kt` (`6d2ee6cf66c4cdabf8e158ffcf34a0c656fc00e10f862049b6c5da7ab16408b0`). This reviewer holds no freeze.
- Dirty wiring outside the freeze: `AppearancePresentationTest.kt`, `StageGesturePolicyTest.kt`.
- Reviewer: Grok code quality only. No visual-taste or device judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. Attempt 1 accepts the bounded sandwich drop.
- Historical: none. This is round 1 of 4.

This is source acceptance of Muse remaining-visual C frame flatten only. It is not device, screenshot, or beauty acceptance. U1 no-track scores stay immutable. Console alpha / 0.60-cap re-verify is not closed here.

## Diff under review

Three files, +11 / -10. Production change is ten lines in `Console.kt`:

- Console card drops the inner `padding(2.dp) + background(plane) + padding(2.dp) + background(surface)` sandwich. Remaining chain is `clip(cardShape)` → one `p.surface.copy(alpha = Dim.consoleAlpha * style.panelAlphaScale)` fill → one `Dim.hairline` / `p.line` border → one content padding.
- StatusBand switches `readableOn(p.plane)` / `background(p.plane)` to `readableOn(p.surface)` / `background(p.surface)` and adds `.border(Dim.hairline, p.line)` before the existing `padding(style.space(4.dp))`. No `copy(alpha)` on the band.
- Overflow popout, BenchPost, tactile 12/6 pads, look-1 `Dim.consolePadH/V`, outer `Dim.cardMarginH/Bottom`, gestures, and keybed policy are untouched.

Tests retarget StatusBand to surface + hairline and lock the console card against the removed sandwich pads and plane fill.

No codec, catalog, TactileConsole, Dimens, or gesture-owner changes.

## Evidence classes

Verified by reading the freeze file after hash match, plus dirty tests as wiring evidence, plus callees needed to judge the frame:

- Working `Console.kt` SHA-256 matches the freeze. HEAD is `53d6479`. Dirty set is exactly Console.kt plus the two named tests.
- Console() from `fun Console(` to `OverflowHandleKey` has one `.background(`, one `.border(Dim.hairline, p.line, cardShape)`, and one content `.padding(horizontal = style.space(`. No `.padding(style.space(2.dp))`. No `.background(p.plane)`.
- Tactile pads remain `12.dp` / `6.dp` behind `ConsoleKeybedPolicy.tactile(style.lookVersion, hasTransport)`. Look 1 still uses `Dim.consolePadH` / `Dim.consolePadV`. `playBarSwipeUp` still sits after that padding. `StageChromeBounds.Card.Console` still reports after transform and before padding.
- StatusBand substring to `SeekRule` contains `val p = p.readableOn(p.surface)`, `.background(p.surface)`, `.border(Dim.hairline, p.line)`, and the 4.dp inner pad. It does not contain `.background(p.plane)` or `copy(alpha`. `Palette.readableOn` sets `surface = background.copy(alpha = 1f)` and recomputes ink / ink2 / muted at 4.5 and line at 3.0 against that RGB.
- OverflowPopout still paints `p.surface.copy(alpha = Dim.sheetAlpha * style.panelAlphaScale)` plus `p.lineStrong` hairline and `Dim.popoutPad`. BenchPost still uses `readableOn(p.plane)` and `.background(p.plane)`.
- `actualConsumersUseOpaqueReadableRolesAndDensityWithoutShrinkingTargets` locks the new StatusBand strings and still requires some `.background(p.plane)` in Console.kt (BenchPost). `realCardsReportAfterTheirTransformsAndBeforePadding` forbids the sandwich on the console card only.

Inherited, not rerun: parent 956 JVM tests just passed. No Gradle, Git, or device command ran here.

Unobserved: ASUS/S25 luminance after flatten, Glass 0.62 panel scale over a live trace, TalkBack, and screenshots.

## Requirements that hold

These are required behavior. They are not defects.

- Console card is one surface fill, one hairline, one padding step. The plane sandwich is gone.
- StatusBand is opaque surface + hairline, with `readableOn(surface)`.
- Overflow popout is not flattened. U1 tactile paddings stay 12/6. Look-1 console pads stay 14/10. Outer 12/10 card margins still float the card off the glass.
- Sharp corners stay room-owned (`cardShape = RoundedCornerShape(style.cornerRadius)`; StatusBand stays a rectangle). Glass still keeps 12.dp on the console card only.
- S9 overflow handle, play-bar swipe-up, seek, capture skip strings, HOLD/LIVE, and tactile well drawing are outside this diff.
- `lookVersion` 1 still composes StoneKey / FlatKey outside the well. Look 2 still calls `TactileConsoleKeybed`.

## Material findings

None that break the stated sandwich drop on the inspected call graph.

The remaining console fill is still `Dim.consoleAlpha` 0.86 times `panelAlphaScale`. That is the pre-existing outer fill, now the only fill. Muse C asked for alpha re-verify against the 0.60 cap after flattening. This attempt does not change `Dimens.kt`. That is residual, not a sandwich-contract fail.

## Residual notes

These are not attempt-1 fails.

LF-N1. Dropping the inner opaque `background(p.surface)` means look-1 keys and the tactile well now sit on the translucent outer fill. Trace ghosting through the console card can only increase until `consoleAlpha` / Glass `panelAlphaScale` 0.62 are re-measured against decorative-bleed ≤10% (16% with edge light live). StatusBand is the opaque exception, by construction of `readableOn`.

LF-N2. Removing 2.dp + 2.dp sandwich insets widens the inner well by 8.dp total. Wrap policy is unchanged. ASUS ~360.dp minus 12.dp side margins and 12.dp tactile pads is still ~312.dp, under the transport 1-row budget 364.dp. `layout(306f, …)` tests still pass a literal width, not the measured well.

LF-N3. `lightStatusTextHasOpaquePlaneBackplateOverBlackAndBrightScope` still proves `readableOn(p.plane)` against black and white scopes. StatusBand no longer consumes that path. Pause-label and BenchPost still do. Surface-role opacity for the band is locked by wiring (`readableOn(surface)` + `background(p.surface)` + no `copy(alpha)`) plus `readableOn`'s forced opaque surface field, not by that Light-plane over-test.

LF-N4. Parent asked that overflow still use plane. Production OverflowPopout already used `p.surface` at `sheetAlpha`, not `p.plane`. This diff does not touch it. BenchPost remains the Console.kt `background(p.plane)` consumer.

LF-N5. Card tests lock absence of the sandwich, not presence of the remaining `p.surface.copy(alpha = Dim.consoleAlpha * style.panelAlphaScale)` expression. A second non-plane fill without a 2.dp pad would still pass `StageGesturePolicyTest`.

LF-N6. Outer `Dim.cardMarginH` 12.dp / `cardMarginBottom` 10.dp are unchanged. Muse C allowed tightening only until the card still floats. No dock. No change required for this flatten.

## Gate

Attempt 1 **passes** at 8/10. Source-accept the plane-sandwich drop. Do not treat this as quiet-frame or 0.60-cap acceptance.
