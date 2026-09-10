# Tactile track console code review, attempt 2 of 4

- Tree: `phosphor-mobil3` HEAD `88695bb0700b3106b1c6fc834074603dc55c6767` plus dirty Console, TactileConsole, ConsoleTactileTokens, and tests.
- Freeze file: `dev/scratch/tactile-track-20260910/source.sha256` (updated for this correction).
- All 3 freeze hashes matched the working tree. This reviewer holds no freeze.
- Dirty wiring outside the freeze: `ConsoleTactileTest.kt`.
- Reviewer: Grok code quality only. No visual-taste or device judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. Attempt 2 passes. F1 is closed.
- Historical: attempt 1 = **7/10**, unchanged.

This is source acceptance of the F1 wrap correction only. It is not device, screenshot, or beauty acceptance. U1 no-track scores stay immutable.

## Diff versus attempt 1

Title rail, SeekRule-in-well, look-2 tactile with a track, closed PREV/NEXT paths, look-1 StoneKey/`◂◂`/`▸▸`, capture hide-vs-disable on look 1, HOLD/LIVE, S9 overflow, and play-bar swipe-up are unchanged. Those musts still hold. They are not fails.

The F1 owner did change. `ConsoleKeybedPolicy.layout` now takes `hasTransport` (default false). The 1-row budget adds `skip = 112f` when transport is present (`prev 48 + gap 8 + gap 8 + next 48`). 4-row still uses `mode + source + 48 + 16`. No-transport vectors are the same six rows as U1.

`TactileConsoleKeybed` passes `hasTransport` into `layout(maxWidth.value, fontScale, displayOnly, hasTransport)`. 2-row and 4-row transport rows are `Row(Modifier.fillMaxWidth())` with `primary(Modifier.weight(1f))` between 48dp prev/next. 1-row still uses fixed 56dp primary plus the 112dp skip pair.

Tests keep the no-transport 306/234 table, then lock `layout(306f, 1f, hasTransport = true)` as 2-row and `layout(380f, 1f, hasTransport = true)` as 1-row.

## F1 closed

Attempt 1 failed because `layout` wrapped 1-row vs 2-row on the no-transport budget `primary + mode + source + 48 + 28` = 252dp at font 1. The 1-row branch also composed prev/next, so fixed children were 364dp in a 306dp ASUS inner well. `layout` still returned 1-row. The overflow handle sat past the well and could clip off the card.

Current 1-row predicate:

```
val skip = if (hasTransport) 112f else 0f
widthDp < primary + skip + mode + source + 48f + 28f -> 2
```

At font 1, transport 1-row budget is 364dp. 306 < 364, so ASUS default inner well is 2-row: `Layout(2, 306, 64, 56, 48)`. 380 >= 364, so 380 stays 1-row: `Layout(1, 56, 64, 56, 48)`. Exact 364 is 1-row and fits with zero leftover. 363 wraps.

Composed 1-row with transport matches that budget:

`48 + 8 + 56 + 8 + 48 + 12 + 64 + 8 + 56 + 8 + 48` = 364.

2-row transport row minimum is 168dp (`48 + 8 + 56 + 8 + 48`). Secondary is 184dp (`64 + 8 + 56 + 8 + 48`). Both fit in 306. `fillMaxWidth` gives the weighted primary a bounded parent, so play sits between prev and next instead of shrinking the row to wrap-content.

No-transport 306/font 1 stays 1-row 56/64/56/48. 234 stays 2-row. Font 2 stays 4-row. `layout(width, font)` still defaults `hasTransport = false`, so the existing inequality on the no-transport table is unchanged.

## Evidence classes

Verified by reading the freeze files after hash match, plus dirty tests as wiring evidence, plus the same callees needed to judge the well:

- `tactile(1, false)` stays false. `tactile(2, true)` and `tactile(2, false)` stay true.
- No-track 306/234 vectors match attempt 1 and U1. The test still calls `layout(width, font)` without transport.
- `layout(306f, 1f, hasTransport = true)` is 2-row. `layout(380f, 1f, hasTransport = true)` is 1-row. JVM parent already passed those rows.
- Production call is `layout(maxWidth.value, fontScale, displayOnly, hasTransport)` inside the padded well `BoxWithConstraints`.
- 2-row and 4-row transport rows both use `Modifier.fillMaxWidth()` and `primary(Modifier.weight(1f))`. No-transport branches still pass `Modifier.width(layout.primaryWidth.dp)`.
- Look 1 still composes `StoneKey` and `FlatKey("◂◂")` / `FlatKey("▸▸")`. Capture skip strings `if (hasTransport && (!capture || state.captureCanPrevious))` remain on that branch.
- Look 2 still skips outer title / SeekRule / legacy row. Title rail is still `Box(Modifier.fillMaxWidth().height(20.dp))`. SeekRule still composes only when `seekable && durationMs > 0`.
- PREV/NEXT still `close()` a three-point path and fill. PLAY/PAUSE/OVERFLOW paths are untouched.
- `SeekRule` still uses `SliderGeometry.HIT_LANE_DP` 44. HOLD/LIVE, capture play omit, S9, and play-bar swipe-up are not in this diff versus attempt 1.

Inherited, not rerun: parent JVM tests just passed. No Gradle, Git, or device command ran here.

Unobserved: ASUS/S25 playing-track layout, fontScale 2 title pixels, TalkBack, and screenshots.

## Requirements that hold

These are required behavior. They are not defects.

- Track title and SeekRule live inside the tactile well for look version 2.
- U1 no-track key bed stays on the `!hasTransport` branches, with the same 1/2/4-row vectors.
- `lookVersion == 2` stays tactile when a track or remote is present.
- Prev/next in the tactile bed are closed vector silhouettes, not `◂◂` / `▸▸`.
- Title rail height is a fixed 20dp so marquee on/off cannot reflow the key bed.
- Look version 1 keeps the legacy StoneKey path, including font glyphs and outer title/seek.
- HOLD/LIVE, capture play omit, S9 overflowHandleGesture, and play-bar swipe-up are unchanged.
- 1-row with transport wraps when the inner well is under 364dp at font 1. 380dp stays 1-row.

## Material findings

None on the inspected F1 correction source.

## Residual notes

These are not attempt-2 fails. TT-N1, N2, N4–N7 are carried from attempt 1.

TT-N1. The 20dp title rail does not grow with fontScale. `Type.dataLg` is 14.sp. At fontScale 2 that is 28dp of type in a 20dp Box that does not clip.

TT-N2. SeekRule stays 44dp hit / 2dp hairline / 8dp thumb / `p.muted` timestamps. Out of this slice.

TT-N3. Tests now lock 306dp+transport → 2-row and 380dp+transport → 1-row. They still do not lock `fillMaxWidth`, the `layout(..., hasTransport)` call site, `112f`, `Path.close()`, or the 20.dp rail. `Layout` does not store skip, so any skip in about 55–128 would still satisfy those two asserts. The 306 wrap is the real F1 lock.

TT-N4. Tactile marquee drops `chromeVisible`. Console AnimatedVisibility uses the same predicate as `chromeVisible`, so this only differs during the hide animation.

TT-N5. When `!showPlay`, `primary(Modifier.weight(1f))` still drops the weight because the empty lambda never attaches it. `fillMaxWidth` keeps the row the well width. Prev/next sit left with a 16dp gap. Matches U1 omit-play.

TT-N6. PREV is a left triangle. NEXT is a right triangle almost equal to PLAY (0.22/0.72 vs 0.28/0.78). Closed silhouettes are satisfied. Whether they read as skip-back/forward is visual.

TT-N7. File header still says no transport dispatch. The keybed takes `onPrev` / `onNext` / `onSeek`.

TT-N8. `112f` is a literal, not `48+8+8+48`. If prev/next width changes, the 1-row budget will drift. 2-row and 4-row copy the same transport `Row` block.

TT-N9. 4-row still gates at 184dp (`mode + source + 48 + 16`). The transport row needs 168dp. Inner wells between 160dp and 167dp would take 4-row and overflow that row by a few dp. Not the ASUS 306 default. Not F1.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| `tactile(1, *)` false, `tactile(2, true/false)` true | Executed policy | Pass |
| No-track 306/234 layout vectors | Executed `layout()` | Pass, unchanged |
| 306dp + transport, font 1 | Executed `layout(hasTransport=true)` | Pass, 2-row |
| 380dp + transport, font 1 | Executed `layout(hasTransport=true)` | Pass, 1-row |
| PREV/NEXT enum names | Source string | Pass |
| `tactile()` source is `lookVersion == 2` | Source string | Pass |
| `layout` 1-row budget includes 112dp skip | Source read + arithmetic | Pass; 364dp matches composed row |
| Production `layout(..., hasTransport)` | Source read | Present; untested as a string |
| 2-row/4-row `Row(Modifier.fillMaxWidth())` | Source read | Present; untested as a string |
| Title 20dp + SeekRule inside well | Source read | Present, untested as a unit |
| Look 1 StoneKey / `◂◂` / `▸▸` | Source read | Present |
| Closed PREV/NEXT paths | Source read | `close()` + fill |
| Capture prev/next disabled, not hidden | Source read | `enabled = showPrev/showNext` |
| Source without seek omits SeekRule | Source read | Guard unchanged |
| Seek hit lane 48dp | Source / existing tests | Unchanged 44dp, out of slice |
| ASUS playing-track well | Device | Unobserved |

## Limits

No source, build, device, or Git mutation. This critique file is the only write. Freeze not held. Visual U1 no-track remains closed. Device proof of a playing track in the well remains parent-owned after this wrap fix.

## Disposition

**8/10. Pass.** Preserve attempt 1 = 7. Source-accept the F1 wrap correction. Do not treat this as device or screenshot acceptance.
