# U1 code review, attempt 1 of 4

- Freeze: `dev/scratch/visual-u1-20260909/source.sha256` against `b74beb278b298bdc9b7c614339ce4e9a1a373009`.
- All 14 freeze hashes matched.
- Reviewer: Grok 4.6 high. No visual-taste judgment. Muse and DeepSeek own design.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. This round accepts the bounded U1 source.
- R06 scores remain immutable (attempt 1 unscored, attempts 2 and 3 = 8).

This is source acceptance. It is not device, screenshot, or beauty acceptance.

## Evidence classes

Verified by reading freeze files and their direct callees:

- `lookVersion` default 1, init 1..2.
- Encode omits `look_version` unless the value is 2.
- Decode allows the extra key only as integer 2. Explicit 1, other numbers, `2.0`, `2e0`, booleans, strings, and unknown keys fail before persistence.
- Checksum is of the constructed document, so invalid `look_version` fails as `invalid_value` or `wrong_type`, not a silent hash skip.
- Editor toggles only `draft.lookVersion`. Workflow preview, cancel, apply, save, reset, and reload are exercised by `ConsoleTactileTest.workflowPreviewCancelApplySaveResetAndRestartAreLossless` on real `AppearanceWorkflow`.
- `RoomStyle.overridden` copies `this.lookVersion` onto FEEL bases. Tests cover every `ChromeCharacter`.
- `ConsoleKeybedPolicy.tactile` is `lookVersion == 2 && !hasTransport`. `Console.kt` uses `hasTransport = trackTitle != null || remote`.
- Tactile play gate and HOLD/LIVE labels duplicate the legacy `PauseDisplayPolicy` inputs. Capture without `captureCanPlay` or `live` omits the play key.
- S9 keeps `overflowHandleGesture`. Keyboard Enter/Space and semantic `onClick` do not add a clickable pointer owner.
- Tokens use existing WCAG luminance. Opaque `rgb()` faces. Authored colors are not written back (`assertEquals(before, value)`).
- AMOLED well 0, raised face `0x141414`. Curated Glass radius 0, legacy Glass 12.
- Missing `appearance_state` and unrelated prefs stay look version 1.

Inherited, not rerun: writer `tests-r4.log` `OK (208 tests)`, parent Android gate 927/0. Compose compiled in the writer production command.

Unobserved: 48dp/56dp laid-out pixels, TalkBack, physical press, and screenshots.

## Material findings

None that break the authored contract on the inspected call graph.

## Residual notes

U1-N1. `ConsoleTactileTest` does not source-assert `Console.kt` wiring. The no-track branch is verified by reading `Console.kt:203,263-264` and the tactile file, not by a string lock on that call.

U1-N2. Play/HOLD/LIVE logic is duplicated between the legacy `Row` and `TactileConsoleKeybed`. A later capture-gate change could drift.

U1-N3. Focus ring is a 2dp stroke outside the face. The draw inset is 4dp for the key body and 1dp for the ring path, so the plane gap is about 2dp, not the contract 1dp. Functionally outside the key. Pixel check belongs to visual review.

U1-N4. Disabled dashed rendering exists but the keybed never passes `enabled = false`. Capture-unavailable play stays omitted, matching the parent checklist.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| Legacy curated hashes, omit `look_version` | Executed codec | 4 golden SHA-256 in `legacyCanonicalHashesStayExactAndExtensionIsStrict` |
| Strict optional field | Executed decode | Bad 1/0/3/2.0/2e0/true/null/"2", unknown key |
| Preview/cancel/apply/save/reset/restart | Executed workflow | Pass in written test |
| FEEL/editor/archive/merge/initial v1 | Executed | Pass |
| Contrast floors, AMOLED face, Glass radii | Executed token math | Pass, no authored mutation |
| tactile(2,false) only | Executed policy | Pass |
| Console.kt dispatch/S9/48dp layout | Source read / unobserved pixels | Call graph matches, no UI test |

## Limits

The brightness manual string in this freeze is R06 wording, not U1 behavior. `HANDOFF.md` is dirty and outside the freeze. No source, build, device, Git, or network work. No freeze is held.

## Disposition

Accept U1 source at 8/10. Device and visual rounds remain parent-owned.
