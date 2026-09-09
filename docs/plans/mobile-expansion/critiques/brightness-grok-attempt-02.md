# R06 brightness pin independent review, attempt 2 of 4

- Unit freeze: `dev/scratch/brightness-pin-20260909/source-r2.sha256` against `b222818e86f210fd0b02f9621e61427022ebc63e`.
- All 13 freeze hashes matched the files read for this review.
- Reviewer: Grok 4.6 high. Independent of the writer and of the coordinator build.
- Score: **8/10**.
- Acceptance gate: Ben accepts >= 8/10. This round accepts the bounded source stub.
- Historical: attempt 1 remains build-blocked and unscored. That budget slot is not reset. This is attempt 2. No prior scored brightness-pin Grok result exists.

This is source acceptance only. It is not device, TalkBack, or visual-design acceptance. Muse and DeepSeek own design.

## Evidence classes

Verified by this reviewer:

- Exact freeze files and `git diff` against `b222818`.
- Policy functions executed in the written tests (256-mask plus malformed-off).
- Archive decode/merge tests as written.
- Attempt 1 compile hole is gone: `ScopeActions` and `SheetActions` both declare `setPinScreenBrightness(on: Boolean)` with no empty default. `PhosphorScreen.kt:392` forwards it. `withSheetRouting` uses `SheetActions by base`.

Inherited, not rerun here:

- Writer `tests-r3.log` `OK (68 tests)`.
- Coordinator Android gate `android-gate-r2.log` `BUILD SUCCESSFUL`, 918 unit tests, 0 failures, lint, dual APK, checkEngine.

Inferred from call paths, not run on a device:

- Lifecycle ordering in `MainActivity`.
- HUD `presenting` vs `active`.
- Window attribute writes.

Unobserved: actual `WindowManager.LayoutParams.screenBrightness`, keep-awake, auto-brightness, PiP, HUD, import, and recreation on a phone.

## Attempt 1 disposition

Attempt 1 stopped on `compileDebugKotlin`: `MainActivity.kt:1491` overrode a missing `ScopeActions` member, and `sheetActions` did not forward the setter. Those two holes plus the `SheetActions` empty default are corrected and pinned by `ForegroundBrightnessPolicyTest.bothActualInterfacesRequireTheSetterAndTheSheetAdapterForwardsIt`.

## What holds

`ForegroundBrightnessPolicy.active` requires requested, started, resumed, focused, current, and not destroyed, PiP, or HUD. Executed mask 31 is the only true case. Brightness is `1f` or `-1f`. `awake` is `sourceAwake || active`, so a pin keeps the window awake with pause or no source, and an inactive pin leaves `SourceWakePolicy.visible(started && sourceLive)` unchanged.

`MainActivity.applyBrightnessPin` writes `window.attributes.screenBrightness` only when the current attribute differs, then reuses `reassertSourceWake` for `keepScreenOn` and `FLAG_KEEP_SCREEN_ON`. The `uiTick` path still calls `reassertSourceWake` and does not contain `screenBrightness` or `applyBrightnessPin`. No `Settings.System.put` or `SCREEN_BRIGHTNESS` write exists in the adapter.

Event order from source:

- `onPause` / `onStop` / `onDestroy` assign `activityResumed` or `activityDestroyed` before `applyBrightnessPin`.
- `onWindowFocusChanged` assigns `activityFocused` before apply. Permission overlays and split-screen focus loss therefore restore `-1f` without waiting for pause.
- `onPictureInPictureModeChanged` sets `ui.pip` then apply. `brightnessPinActive` also ORs `isInPictureInPictureMode`, which covers PiP-while-resumed.
- HUD: `hudChanged` applies before `moveTaskToBack`. `hud` is `FloatingHudService.presenting`.
- `makeSurface` clears the old view `keepScreenOn`, assigns `scopeSurface`, then apply.
- `setPinScreenBrightness` fences `taskIsCurrent`, applies immediately, then `commit()`. Failure keeps the local choice and shows retry copy.
- `acceptSettingsArchive` commits the whole write owner before `restoreTuning`. Omitted `/2` keys are not in `merge` output. `/1` skips `pin_screen_brightness`. Bad `/2` types throw `invalid_setting_type`. Instrument and appearance sources do not mention the key.

Default-off is `requested()` as Boolean-or-false. Manual copy names DISPLAY & HUD, pending device acceptance, and no global slider write.

## Material findings

None that reproduce a contract break on the inspected source.

## Residual notes, not blockers

R2-N1. Activity adapter tests are source-indexOf, not instantiated Android. Policy and archive tests run real functions. This caps confidence, not a named false write.

R2-N2. HUD displacement uses `presenting` (`added && surfaceReady`). `show()` publishes `HUD opening` while `presenting` is still false, so the pin can remain on Phosphor's own window until the overlay is actually up. Restore still happens before `moveTaskToBack` once presenting is true. This does not write another app's window.

R2-N3. `activityFocused` is not cleared on pause or destroy. Inactivity still follows `resumed` / `destroyed`. OEM focus-callback gaps stay unobserved.

## Test map

| Check | Kind | Result used |
| --- | --- | --- |
| Default-off, malformed local, 256-mask active/awake | Executed policy | Pass in written tests |
| Pause/no-source still wakes when requested | Executed policy | Pass |
| `/2` bool, `/1` skip, omit-merge, explicit false, bad types | Executed archive | Pass |
| Lifecycle order, one attribute writer, no ticker brightness, interface forward | Source-read | Strings match current files |
| Manual DISPLAY & HUD copy | Executed content plus source | Pass |
| Window attributes on device | Unobserved | Coordinator device pending |

## Limits

No visual redesign comments. `docs/ASKS.md` is dirty and outside the freeze. No source edits, builds, Git mutations, or device work. Coordinator APKs are not treated as installed evidence.

## Disposition

Accept the bounded source at 8/10. Preserve attempt 1 as unscored. Device, panel luminance, auto-brightness, PiP/HUD handoff, and recreation remain coordinator-owned. This reviewer holds no freeze. Working tree unchanged.
