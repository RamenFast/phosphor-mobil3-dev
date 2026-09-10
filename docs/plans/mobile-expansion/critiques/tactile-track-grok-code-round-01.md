# Tactile track console code review, attempt 1 of 4

- Tree: `phosphor-mobil3` HEAD `88695bb0700b3106b1c6fc834074603dc55c6767` plus dirty Console, TactileConsole, ConsoleTactileTokens, and tests.
- Freeze file: `dev/scratch/tactile-track-20260910/source.sha256`.
- All 3 freeze hashes matched the working tree. This reviewer holds no freeze.
- Dirty wiring outside the freeze: `ConsoleTactileTest.kt`.
- Reviewer: Grok code quality only. No visual-taste or device judgment.
- Score: **7/10**.
- Gate: Ben accepts >= 8/10. Attempt 1 does not pass. One layout-owner correction is required, then independent round 2.
- Historical: none. This is round 1 of 4.

This is source acceptance of the track/title/seek well unification. It is not device, screenshot, or beauty acceptance. U1 no-track scores stay immutable.

## Diff under review

Four files, +117 / -46. Production change is the tactile transport well:

- `ConsoleKeybedPolicy.tactile` is now `lookVersion == 2`. `hasTransport` stays in the signature and is unused.
- `Console.kt` still draws title, SeekRule, and the StoneKey / FlatKey `◂◂` / `▸▸` row when not tactile. When tactile, those chrome pieces are not drawn outside the well. `TactileConsoleKeybed` receives palette, reduced-motion, prev, next, and seek.
- `TactileConsoleKeybed` puts a 20dp title rail and the existing `SeekRule` inside the well, then the key bed. Prev/next are `ConsoleVector.PREV` / `NEXT` closed filled paths in the same 26dp canvas as PLAY.
- Tests flip `tactile(2, true)` to true and string-lock `PREV` / `NEXT` plus the new `tactile()` source line.

No codec, catalog, gesture-owner, or SeekRule geometry changes.

## Evidence classes

Verified by reading the freeze files after hash match, plus dirty tests as wiring evidence, plus callees needed to judge the well:

- `tactile(1, false)` stays false. `tactile(2, true)` and `tactile(2, false)` are true. `deviceAndNarrowWindowLayoutsHaveFixedPhysicalWidthVectors` locks that.
- Look 1 still composes `StoneKey` and `FlatKey("◂◂")` / `FlatKey("▸▸")` in `Console.kt`. Capture skip strings `if (hasTransport && (!capture || state.captureCanPrevious))` remain on that branch, which is why `CaptureMirrorPolicyTest` still matches.
- Look 2 with `appearanceValue != null` calls `TactileConsoleKeybed` and skips the outer title / SeekRule / legacy row.
- No-track layout vectors at 306dp and 234dp are unchanged. The extra Column wrapper has one child when there is no title and no seek, so U1 key sizes stay.
- Title rail is `Box(Modifier.fillMaxWidth().height(20.dp))` with the same `title  —  artist` join, `Type.dataLg`, delay 2200ms, velocity 24.dp. SeekRule is composed only when `seekable && durationMs > 0`.
- Prev/next use `TactileConsoleKey` at 48dp, `enabled = showPrev/showNext`, dashed disabled face, announced reason. Capture hide-vs-disable differs from look 1 on purpose (U1 disabled language).
- PLAY/PAUSE/OVERFLOW paths are untouched. PREV/NEXT call `close()` on a three-point path and fill, not a font glyph.
- `SeekRule` still uses `SliderGeometry.HIT_LANE_DP` 44, 2dp track, 8dp thumb, `consoleSeekGesture`. `SliderGeometryTest` still locks that body.

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

## Material findings

### F1. One-row policy ignores prev/next width, so ASUS default inner well overflows

**Priority: high. Contract:** wrap rows when the inner well cannot hold the composed keys. Evidence: source arithmetic against the U1 306dp inner width, high confidence.

`ConsoleKeybedPolicy.layout` still wraps 1-row vs 2-row with the no-transport budget:

`primary + mode + source + 48 + 28` = 252dp at font 1.

The 1-row branch now also composes prev 48 + 8 + next 48 + 8 in front of that row. Fixed children become 364dp:

`48 + 8 + 56 + 8 + 48 + 12 + 64 + 8 + 56 + 8 + 48`.

U1 recorded ASUS inner well as about 306dp after frame/padding. 306 >= 252, so `layout` still returns `Layout(1, 56, 64, 56, 48)`. 364 > 306, so the Row overflows by 58dp. The well Box does not clip. The console card does (`clip(cardShape)`), after 12dp tactile pad plus the 2+2 plane sandwich. The right-side overflow handle is the first 48dp past the well. On the daily portrait playing-track case, S9 can clip off the card.

2-row and 4-row already put prev / play / next on their own weighted row (168dp minimum). 234dp windows already take 2-row and are fine. The miss is only the 1-row band, which is the ASUS default.

`hasTransport` was removed from the tactile gate and was not added to `layout()`. Tests still assert 306dp / font 1 is 1-row, with no transport-width case.

**Smallest correction:** thread `hasTransport` into `layout()` and add the composed extra (112dp) to the 1-row budget, or never take the 1-row branch when transport is present. Keep no-transport 306dp as 1-row. Lock a 306dp + transport vector that expects 2 rows.

## Residual notes

These are not attempt-1 fails.

TT-N1. The 20dp title rail does not grow with fontScale. `Type.dataLg` is 14.sp. At fontScale 2 that is 28dp of type in a 20dp Box that does not clip, so the line can paint into the seek lane. Muse asked for a fixed rail so marquee on/off does not reflow, not so fontScale 2 becomes unreadable. Round 2 may keep 20dp at font 1 and still raise the min with fontScale.

TT-N2. SeekRule stays 44dp hit / 2dp hairline / 8dp thumb / `p.muted` timestamps. Muse asked to raise the hit lane to 48dp without widening the picture, and to re-measure muted-on-well. This slice was told to put SeekRule inside the well, not to rebuild it.

TT-N3. New tests only string-lock `ConsoleVector.PREV` / `NEXT` and the `tactile()` source line. They do not lock the 20.dp rail, SeekRule inside `TactileConsole.kt`, Console.kt moving title/seek, closed `Path.close()`, or layout with transport. F1 would not have failed JVM.

TT-N4. Tactile marquee drops `chromeVisible`. Console AnimatedVisibility uses the same predicate as `chromeVisible`, so this only differs during the hide animation.

TT-N5. When `!showPlay`, `primary(Modifier.weight(1f))` drops the weight because the empty lambda never attaches it. Prev/next then sit left with a 16dp gap instead of a reserved play slot. Matches U1 omit-play, looks uneven.

TT-N6. PREV is a left triangle. NEXT is a right triangle almost equal to PLAY (0.22/0.72 vs 0.28/0.78). Closed silhouettes are satisfied. Whether they read as skip-back/forward is visual.

TT-N7. File header still says no transport dispatch. The keybed now takes `onPrev` / `onNext` / `onSeek`.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| `tactile(1, *)` false, `tactile(2, true/false)` true | Executed policy | Pass |
| No-track 306/234 layout vectors | Executed `layout()` | Pass, unchanged |
| PREV/NEXT enum names | Source string | Pass |
| `tactile()` source is `lookVersion == 2` | Source string | Pass |
| Title 20dp + SeekRule inside well | Source read | Present, untested as a unit |
| Look 1 StoneKey / `◂◂` / `▸▸` | Source read | Present |
| Closed PREV/NEXT paths | Source read | `close()` + fill |
| Capture prev/next disabled, not hidden | Source read | `enabled = showPrev/showNext` |
| Source without seek omits SeekRule | Source read | Guard unchanged |
| 306dp + transport is 1-row | Executed `layout()` / arithmetic | Fail: 364dp of keys in 306dp |
| Seek hit lane 48dp | Source / existing tests | Unchanged 44dp, out of slice |
| ASUS playing-track well | Device | Unobserved |

## Limits

No source, build, device, or Git mutation. This critique file is the only write. Freeze not held. Visual U1 no-track remains closed. Device proof of a playing track in the well remains parent-owned after the wrap fix.

## Disposition

Reject attempt 1 at **7/10**. Release: fail. Round 2 reviews the F1 wrap correction only. The listed musts that already hold still have to hold.
