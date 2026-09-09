# Deferred-root stub independent review, round 1 of 4

- Unit: uncommitted 13-file stub against `9b99c5e46a312440cf06a5c56f260b7f686969b0`.
- Freeze: `dev/scratch/root-stub-20260909/source.sha256`. All 13 hashes matched the files read for this review.
- Reviewer: Grok 4.6 high, independent of the writer and of the coordinator build.
- Score: **8/10**.
- Acceptance gate: strictly above 8/10. This round does not accept.
- Historical Grok stub scores: none. This is round 1. Original Section 2 research and stereo scores are untouched.

This is a local source preview score. It is not Android, device, or release acceptance.

## Evidence classes

Observed by this reviewer:

- Current git status and diff against `9b99c5e` (13 paths: 12 modified, 1 untracked).
- Exact current source of those 13 files plus actual callers outside the unit.
- Freeze SHA-256 match for every listed path.
- Existing receipts already on disk: writer `tests.log` (`OK (26 tests)`), coordinator `android-gate.log` (`BUILD SUCCESSFUL`), and Gradle `testDebugUnitTest` HTML/XML (906 tests, 0 failures, including 5 `RootCaptureDeferredTest` cases). Those runs were not executed by this reviewer.

Source inference, not runtime:

- Call graphs from `MainActivity`, `CaptureService`, `Sheets`, `PhosphorScreen`, debug `SelfTestReceiver` / `RootCaptureChecks`, and manifests.
- Accessibility of `ManualKey` from its modifiers, not from TalkBack.

Device evidence: none. This reviewer did not build, install, or operate a phone.

## Winning condition used

Honest encouraging local preview only. No normal UI, startup, or legacy-pref root authorization or capture. Standard capture remains usable. Explicit debug research stays bounded. Release activation stays absent.

## What holds

`RootCapturePolicy.PRODUCT_AVAILABLE` is `false` for both build types. Executed JVM policy, not a comment, forces every legacy enabled/ack/standard combination onto `CaptureBackend.STANDARD` and denies `mayAuthorize`. `mayStart` is true only for `debug && controlledCheck`.

`RootCaptureSettings.enabled()` uses that policy, so a stored `root_capture_enabled` flag does not select root. `enable()` returns before `prefs()` or `RootCaptureSession(` when the product is unavailable. `setRootCapture` and `openRootManager` return before source change, preference writes, or a manager intent.

Bestiary UI in `ManualSheet.kt` is a local `rootDisclosure` preview. The visible actions are `ROOT CAPTURE · COMING LATER` and `GOT IT · CLOSE PREVIEW`. The sheet source no longer contains `onRootCapture(true|false)`, `onRootManager()`, or `ManualRootToggle`. `ManualKey` keeps 48 dp, focus border, wrapping text, and `Role.Button`.

`CaptureService.startRoot()` rejects product starts with the deferred status before `startForeground` and before `RootCaptureSession(`. The debug check reservation remains. `SelfTestReceiver` and `RootCaptureChecks` stay in the debug source set, DUMP-protected, and absent from the main manifest. `RootCaptureService` stays `exported=false`.

Ordinary `startCapture()` still chooses STANDARD when `enabled()` is false, then follows `RECORD_AUDIO` and MediaProjection consent. Startup `last_source=capture` uses that same function. The helper, protocol tests, and cleanup code remain in tree.

Contracts in `MOBILE-EXPANSION-PLAN.md`, `spec/EXPANSION.md`, and `section-02-root-product.md` state the override and refuse stereo/SoundCloud/latency claims.

## Findings

### F1. Normal SRC sheet still presents root as a current product path

**Concrete failure:** the honest-stub surface is incomplete on the ordinary source sheet. Operational widgets and present-tense capability copy remain live Compose, not historical docs.

**Exact location:** `app/src/main/kotlin/dev/phosphor/mobil3/ui/Sheets.kt`

- 743-753: `if (state.rootCaptureEnabled || state.captureRoot)` still renders root capability prose, `use standard capture · Android consent`, `RETRY ROOT`, and `ROOT MANAGER`.
- 771-773: always-visible copy says authorized root capture uses its private helper.
- 803-806: always-visible copy says root capture can include `BY_SYSTEM` audio.

**Why it matters:** 771-773 and 803-806 are not behind the enabled/captureRoot gate. Every SRC user still reads root as a current authorized alternative. That fights the requested honest preview.

743-753 is inert for a normal or legacy-pref user only because `RootCaptureSettings.enabled()` now stays false. The widgets are not hidden by `PRODUCT_AVAILABLE`. They return if `ui.captureRoot` becomes true.

**Coupling:** `CaptureService.kt:482-484` stamps every published status with the service `backend`. `RootCaptureService` is `CaptureBackend.ROOT`. A deferred product reject would still publish a ROOT error, and `MainActivity.kt:1759` would set `ui.captureRoot = true`, which reopens 743-753. That start is currently unreachable from `startCaptureBackend` because `enabled()` is false. It is a latent reopen, not a demonstrated normal-UI start.

**Fix:** gate or rewrite SRC root copy and widgets on `PRODUCT_AVAILABLE` (or remove them). Do not key leftover operational UI on `captureRoot`. Keep debug dump checks as the research entry.

### F2. `disable()` still erases the legacy enabled flag

**Concrete failure:** the contract says leftover enabled/ack preferences stay inert and are not erased. `enable()` honors that. `disable()` does not.

**Exact location:** `app/src/main/kotlin/dev/phosphor/mobil3/RootCaptureSettings.kt:49-61`

`disable()` still runs `prefs(context).edit { putBoolean(RootCapturePolicy.ENABLED, false) }`, stops a helper, and rewrites `message`. There is no `PRODUCT_AVAILABLE` guard.

**Reachability:** the only production caller is `MainActivity.kt:1701`, after the 1696-1698 early return. Source inference: current UI does not call it. The deferred tests assert the `enable()` guard and never assert `disable()`.

**Fix:** return from `disable()` without `prefs().edit` when the product is unavailable, or document an explicit wipe. Add a test that the disable body cannot write `ENABLED` while `PRODUCT_AVAILABLE` is false.

### F3. Operational callbacks remain plumbed beside an inert bestiary

**Concrete failure:** the preview is inert, but the old authorization API is still wired through the manual sheet.

**Exact location:**

- `ManualSheet.kt:154-158` still takes `rootEnabled`, `rootBusy`, `rootStatus`, `onRootCapture`, `onRootManager` and does not use them.
- `PhosphorScreen.kt:857-861` still passes `actions::setRootCapture` and `actions::openRootManager`.

`setRootCapture` now toasts `DEFERRED` instead of authorizing. That is a side effect, not a grant. It is also not the local dismissible preview the bestiary uses.

**Fix:** drop the unused parameters in the stub unit, or stop passing live authorization lambdas. If a stale callback must exist, keep the activity guard and do not toast from a preview tap.

## Verification gaps

These are missing checks, not proof that the named behavior fails.

1. No Compose or accessibility run of the preview. `ManualContentTest.rootPreviewIsLocalDismissibleAndHasNoOperationalCallbacks` reads `ManualSheet.kt` as text. Its `Role.Button` check uses the generic `ManualKey` helper, not a unique root-row semantic tree.
2. No test that `Sheets.kt` hides operational root controls when the product is deferred. The 13-file tests never open that file.
3. `RootCaptureDeferredTest` source-reads `startRoot` / `setRootCapture` / `enable()`. It does not construct `CaptureService`, `MainActivity`, or SharedPreferences.
4. Writer-focused JUnit compiled only `RootCapturePolicy`, `ManualContent`, and the two test classes. It did not compile `ManualSheet`, `MainActivity`, or `CaptureService`. Coordinator Gradle later compiled and ran 906 unit tests. This reviewer did not rerun them.
5. No device, TalkBack, or installed-APK evidence that Everything playing still gets MediaProjection consent, or that a legacy `root_capture_enabled=true` install stays inert.

## Limits

This review stayed on the stub unit and its live callers. It did not re-score historical stereo, SoundCloud, helper-protocol, or latency research. `buildReleaseRootAudioLauncher` in the coordinator gate builds the preserved helper. That is not treated as product activation.

No model or tool blocker. No source edits, builds, Git mutations, credentials, or unrelated sessions were used.

## Disposition

Do not accept at this gate. The policy kill-switch, bestiary preview, service reject-before-helper, and debug-only check entry are real and mostly consistent. Round 2 should close F1 and F2 with tests that fail if SRC operational root UI or `disable()` preference writes return while the product is deferred.
