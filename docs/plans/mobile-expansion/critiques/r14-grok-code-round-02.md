# R14 code review, attempt 2 of 4

- Tree: `phosphor-mobil3` HEAD `bb3044d389a84f5673e5cc53e9110553a046d4db` plus dirty unreleased R14 sources after the F1–F3 corrections.
- Freeze file: `dev/scratch/r14-startup-20260910/source.sha256`.
- All 6 freeze hashes matched the working tree. This reviewer holds no freeze.
- Dirty wiring outside the freeze: `StartupCoordinatorPolicyTest.kt`. Other dirty files (`MicrophoneRoutePolicy.kt`, `MicrophoneRoutes.kt`, `CaptureMixPolicyTest.kt`) are not this slice.
- Reviewer: Grok code quality only. No visual-taste or device judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. Attempt 2 passes. F1–F3 are closed.
- Historical: attempt 1 = **7/10**, unchanged.

This is source acceptance of the three named corrections. It is not device, accessory, or beauty acceptance.

## Diff versus attempt 1

Unchanged seams: default `none` and popup missing-key off; `last_source` is still written and is not read for reopen; root is not in `allowed` or the settings row; `RootCapturePolicy.PRODUCT_AVAILABLE` stays false; schema `/2` specs add `default_source` and `automatic_permission_popup` as `v2Only`; import writes prefs and `restoreTuning` and does not call `maybeStartConfiguredDefault`. Those must-nots still hold. Residuals R2–R5 are unchanged and are not fails.

Changed:

- Capture auto-start no longer treats `consent_seen` as prompt-free authorization. `promptFreeCapture()` is false. Popup off writes inline status and does not call `startCapture()`.
- Fresh launch is a process one-shot: companion `processStartupConsumed` plus `processFreshLaunch(alreadyConsumed)`. Process death resets the static and counts. Same-process `onResume`, unlock, permission/picker return, and HUD `onNewIntent` do not.
- Auto-start requires runtime `default_source_confirmed`. Only local `setDefaultSource` writes it. Import does not.

New policy tests lock the three gates. Parent inherited tests+lint just passed; not rerun here.

## F1 closed

Attempt 1 failed because `!captureConsentNeeded()` called `startCapture()` with popup off. `consent_seen` is not a live MediaProjection token, so the system dialog (and missing `RECORD_AUDIO`) could appear.

Current `maybeStartConfiguredDefault`:

```
"mic" -> if (promptFreeMic(granted) || ui.automaticPermissionPopup) startMic()
    else ui.microphoneStatus = "Default microphone is waiting. Open SRC to grant and start."
"capture" -> if (ui.automaticPermissionPopup) startCapture()
    else ui.captureStatus = "Default capture is waiting. Open SRC to approve Android consent."
```

`startCaptureBackend` for STANDARD still requests `RECORD_AUDIO` if missing, then always `captureConsent.launch(screenCaptureIntent(...))`. That path runs from auto-start only when popup is on. Popup off never reaches it. Mic auto-starts only when `RECORD_AUDIO` is granted or popup is on.

## F2 closed

Attempt 1 failed because production passed `freshLaunch(firstCreate = savedInstanceState == null, configChange = false)`. A non-null `savedInstanceState` after process death was classified as not fresh.

Current `onCreate`:

```
pendingFreshStartup = StartupCoordinatorPolicy.processFreshLaunch(processStartupConsumed)
processStartupConsumed = true
```

`processFreshLaunch` is `!alreadyConsumed`. The companion static dies with the process, so recents restore after kill is fresh. Later same-process recreates, living `onResume`, unlock, permission/picker return, and HUD `onNewIntent` do not set the pending bit. Auto-start still runs from `onResume` only when that bit is set, then clears it. Rotation remains `configChanges` and does not recreate.

## F3 closed

Attempt 1 failed because imported `default_source` / `automatic_permission_popup` entered ordinary prefs and `ui`, so the next accepted fresh launch treated them as a local DEFAULT chip tap.

Current `shouldAutoStart` requires `confirmed`. `locallyConfirmed` reads runtime `default_source_confirmed`, default false. `setDefaultSource` is the only writer: `runtimePrefs().edit { putBoolean(CONFIRMED, value != "none") }`. `acceptSettingsArchive` still does not call `setDefaultSource` or `maybeStartConfiguredDefault`. The key is not in archive specs, so decode skips it. It lives in `phosphor.runtime`, which backup rules already exclude.

## Evidence classes

Verified by reading freeze files after hash match, plus dirty tests/docs only as wiring evidence:

- `StartupCoordinatorPolicy` defaults are `none` and popup off. `allowed` is `none|mic|capture`. Invalid or missing `default_source` becomes `none`. `shouldAutoStart("none", false, false, true)` is false. `promptFreeCapture()` is false. `promptFreeMic` is the live `RECORD_AUDIO` grant.
- `MainActivity` no longer reads `last_source` for reopen. Runtime still writes it. Archive tests already keep it out of portable schema.
- Auto-start runs from `onResume` only when `pendingFreshStartup` is set from the process one-shot in `onCreate`. Surviving mic/capture/playback wake blocks restart. Root is not an allowed default. Product backend stays STANDARD.
- Schema `/2` specs add `default_source` and `automatic_permission_popup`. Both are `v2Only`. Import writes prefs and `restoreTuning`; it does not confirm or start.
- Settings chips are none/mic/capture. Copy says last-used is not a default. No root chip.

Inherited, not rerun: parent tests+lint just passed.

Unobserved: ASUS fresh-launch, process-death restore, MediaProjection prompt, imported confirmation, and TalkBack.

## Material findings

None on the inspected correction source.

## Residual notes

These are not attempt-2 fails.

R1. Policy tests now lock process-death freshness, the confirm gate, and prompt-free mic/capture. They still do not lock `defaultOf("last_source")`, archive v2Only round-trip, or MainActivity's `onCreate` / `startCapture` seam. Brightness already has that archive pattern.

R2. Settings chip ids duplicate `allowed` as a literal list. Drift would compile.

R3. Bluetooth mic auto-start can return from `explainMicrophone` with `micBluetoothExplain` set and no scope-face action. The explain rows live in SRC.

R4. `handleIntent("open"|"capture")` still runs in `onCreate` before auto-start. Manifest has no VIEW filter; residual unless those extras become a user path.

R5. `setDefaultSource` / `setAutomaticPermissionPopup` update UI even when portable `commit()` fails. Confirmation uses runtime `edit { }` (`apply`). Same pattern as several other toggles.

R6. Confirmation is a boolean latch, not a stored source identity. After one local non-`none` chip tap, a later import of a different allowed default can auto-start on the next fresh launch without retapping. Spec says not to re-ask after local approval. Import still cannot set the latch.

R7. Popup-off waiting copy is `microphoneStatus` / `captureStatus`. Those strings render in SRC, not as a new scope-face grant/start control. SRC already has start-mic and everything-playing rows. Spec asked for an inline action; this round's correction was inline status.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| Missing default is none; no auto-start; popup off | Executed policy | Pass in `missingDefaultIsNoneAndDoesNotAutoStart` |
| Process death is fresh; same-process consume is not | Executed policy | Pass in `processDeathIsFreshAndRotationInSameProcessIsNot` |
| Surviving/live/`file` block restart; mic can start | Executed policy | Pass |
| Imported default needs local confirm | Executed policy | Pass in `importedDefaultDoesNotAutoStartUntilLocalConfirm` |
| Capture is never prompt-free | Executed policy + source | Pass. `promptFreeCapture()` false; popup off does not call `startCapture()` |
| Mic prompt-free is live RECORD_AUDIO | Executed policy + source | Pass |
| `last_source` not read for startup | Source | No `getString("last_source"` in `MainActivity` |
| Root not a default | Source | `allowed` and chips omit root; product backend STANDARD |
| Archive v2Only keys exist | Source | Spec + `v2Only`; no dedicated archive test |
| Import does not start capture or confirm | Source | `acceptSettingsArchive` has no `maybeStartConfiguredDefault` / `setDefaultSource` |
| Popup-off capture is prompt-free | Source | **Pass. F1 closed** |
| Process death is fresh | Source | **Pass. F2 closed** |
| Imported default needs local confirm | Source | **Pass. F3 closed** |

## Limits

No product source, build, device, Git, or network work. This critique file is the only write. No freeze is held.

## Disposition

**8/10. Pass.** Preserve attempt 1 = 7. Source-accept the F1–F3 corrections. Do not treat this as device or APK acceptance.
