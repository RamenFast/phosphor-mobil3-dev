# Deferred-root stub independent review, round 2 of 4

- Unit: uncommitted 15-file stub against `9b99c5e46a312440cf06a5c56f260b7f686969b0`.
- Freeze: `dev/scratch/root-stub-20260909/source-r2.sha256`. All 15 hashes matched the files read for this review, including after the coordinator Android gate.
- Reviewer: Grok 4.6 high, independent of the writer and of the coordinator build.
- Score: **9/10**.
- Acceptance gate: strictly above 8/10. This round accepts the bounded source stub.
- Historical Grok stub scores: round 1 = **8/10**, unchanged. Original Section 2 research and stereo scores are untouched.

This is a local source preview score. It is not Android device or release acceptance.

## Evidence classes

Observed by this reviewer:

- Current git status and diff against `9b99c5e` (15 paths: 14 modified, 1 untracked).
- Exact current source of the freeze set plus remaining root callers.
- Freeze SHA-256 match for every listed path.
- Existing receipts already on disk: writer `tests-r2.log` (`OK (29 tests)`), coordinator `android-gate-r2.log` (`BUILD SUCCESSFUL`), and coordinator report of 909 unit tests with 0 failures. Those runs were not executed by this reviewer.

Source inference, not runtime:

- `PRODUCT_AVAILABLE` is a `const val false`, so SRC operational widgets behind that gate cannot compose.
- Call graphs from `MainActivity`, `CaptureService`, `Sheets`, `PhosphorScreen`, debug `SelfTestReceiver` / `RootCaptureChecks`, and manifests.
- Accessibility of `ManualKey` from its modifiers, not from TalkBack.

Device evidence: none. This reviewer did not build, install, or operate a phone. Coordinator states no device acceptance yet.

## Round 1 findings, reassessed

Round 1 scored 8/10 and did not accept. That score stays 8.

| ID | Round 1 defect | Current evidence | Disposition |
| --- | --- | --- | --- |
| F1 | Ordinary SRC sheet advertised live root capture. Operational widgets keyed on `rootCaptureEnabled \|\| captureRoot`. | `Sheets.kt:743` now requires `RootCapturePolicy.PRODUCT_AVAILABLE` before RETRY ROOT, ROOT MANAGER, and root-input copy. `Sheets.kt:771-773` and `803-806` state deferral and a hidden preview, not a helper path. `RootCaptureDeferredTest.sourceSheetCannotAdvertiseOperationalRootEvenForAStaleRootStatus` pins the gate and forbids the old sentences. | Corrected on inspected source. |
| F2 | `disable()` wrote `ENABLED=false` with no product guard. | `RootCaptureSettings.kt:49-50` returns before revision, prefs, authorization, helper stop, or message writes. `deferredDisableReturnsBeforeAnyMutationOrCleanup` asserts that prefix. | Corrected on inspected source. |
| F3 | Manual sheet still accepted unused operational callbacks. PhosphorScreen still passed them. | `ManualSheet.kt` no longer has `rootEnabled`, `rootBusy`, `rootStatus`, `onRootCapture`, or `onRootManager`. `PhosphorScreen.kt:854-859` passes discovery and links only. `manualApiAndCallerContainNoOperationalRootPlumbing` covers both sides. | Corrected on inspected source. |

## What holds

The round 1 kill-switch still holds. Executed JVM policy forces every legacy enabled/ack/standard combination onto STANDARD and denies `mayAuthorize`. `mayStart` is true only for `debug && controlledCheck`. `enable()` and `disable()` return before preference or helper work. `setRootCapture` and `openRootManager` still return before source change or a manager intent.

Bestiary UI remains a local `rootDisclosure` preview with `ROOT CAPTURE · COMING LATER` and `GOT IT · CLOSE PREVIEW`. `ManualKey` keeps 48 dp, focus border, wrapping text, and `Role.Button`.

`CaptureService.startRoot()` still rejects product starts before `startForeground` and `RootCaptureSession(`. Debug DUMP checks remain in the debug source set. `RootCaptureService` stays `exported=false`. Ordinary Everything playing still uses `startCapture()` which follows STANDARD when `enabled()` is false, then `RECORD_AUDIO` and MediaProjection consent.

Contracts still refuse stereo, SoundCloud, audibility, and latency claims.

## Residual notes, not new blockers

R2-N1. `ScopeActions` in `PhosphorScreen.kt:147-148,367-368` and `Sheets.kt:1559-1560` still declare and forward `setRootCapture` / `openRootManager`. The only Compose call of `openRootManager()` is inside the `PRODUCT_AVAILABLE` block. Activity guards remain. This is leftover API, not a reachable grant.

R2-N2. Operational widget source at `Sheets.kt:743-753` is retained behind a compile-time false gate. That is useful if the product flag flips later. It is not current UI.

R2-N3. Writer-focused JUnit still compiled only `RootCapturePolicy`, `ManualContent`, and the two test classes. Coordinator Gradle compiled the adapters and reported 909 passing unit tests. This reviewer did not rerun either.

No new material authorization, capture, or honesty defect was found in the bounded unit.

## Verification gaps

These are missing checks, not proof that the named behavior fails.

1. No Compose, TalkBack, or pixel run of the preview or SRC sheet.
2. `RootCaptureDeferredTest` source-reads Sheets, settings, activity, and service. It does not construct Android objects or write SharedPreferences.
3. No device evidence that Everything playing still gets MediaProjection consent, or that a legacy `root_capture_enabled=true` install stays inert.

## Limits

This review stayed on the stub unit and its live callers. It did not re-score historical stereo, SoundCloud, helper-protocol, or latency research. Coordinator `buildReleaseRootAudioLauncher` builds the preserved helper. That is not treated as product activation.

No model or tool blocker. No source edits, builds, Git mutations, credentials, or unrelated sessions were used.

## Disposition

Accept the bounded source stub at 9/10. Preserve round 1 = 8/10. Do not treat this as device, TalkBack, or release acceptance. Remaining Android consent, installed-legacy, and accessibility checks belong to coordinator-owned device work, not another source-correction round unless a later gate finds a new finite defect.
