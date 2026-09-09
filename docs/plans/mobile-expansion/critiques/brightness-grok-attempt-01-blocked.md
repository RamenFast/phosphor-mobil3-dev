# R06 brightness pin review attempt 1 of 4: build-blocked, unscored

- Unit freeze: `dev/scratch/brightness-pin-20260909/source.sha256` against `b222818e86f210fd0b02f9621e61427022ebc63e`.
- Freeze hashes matched the 12 listed files at read time.
- Reviewer: Grok 4.6 high. Stopped before a scored round.
- Score: none. This attempt does not accept or reject source quality.
- Budget: attempt 1 of max 4. Do not reset the budget. Next scored review is attempt 2.

Coordinator `compileDebugKotlin` failed. An incomplete noncompiling candidate is not scored.

## Verified compile break

`MainActivity.kt:72` implements `ScopeActions`.
`MainActivity.kt:1491` declares `override fun setPinScreenBrightness(on: Boolean)`.
`PhosphorScreen.kt:136-180` `interface ScopeActions` has no `setPinScreenBrightness`.

Kotlin therefore rejects the override. Coordinator report: `MainActivity.kt:1491 setPinScreenBrightness overrides nothing`.

## Additional concrete wiring gap (source, not compiled)

`Sheets.kt:1238-1240` ChipCell calls `actions.setPinScreenBrightness`.
`Sheets.kt:1553` `SheetActions` has `fun setPinScreenBrightness(on: Boolean) {}` with an empty default.
`PhosphorScreen.kt:349-385` `sheetActions` object implements `SheetActions` and does not override `setPinScreenBrightness`.
No `setPin` string exists in `PhosphorScreen.kt`.

Consequence if only the ScopeActions override is added on MainActivity: Display-sheet taps still hit the empty `SheetActions` default and never reach `MainActivity.setPinScreenBrightness`.

Smallest correction: declare `setPinScreenBrightness` on `ScopeActions`, override it on the `sheetActions` wrapper to `actions.setPinScreenBrightness`, keep the MainActivity implementation. Regression: compileDebugKotlin plus a source or host test that the Display ChipCell path is not the empty default.

## What this attempt did not do

No full lifecycle, persistence, or test-strength critic was completed. Writer `tests-r2.log` shows `OK (67 tests)` as inherited focused evidence. `tests.log` recorded an earlier archive-schema failure that is not treated as the current scored result. No device, design, or numeric acceptance is claimed.

## Release

This reviewer holds no source lock, Git hold, or freeze file. Read-only inspection of the brightness-pin freeze is released. The working tree is left unchanged.
