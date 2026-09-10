# R14 code review, attempt 1 of 4

- Tree: `phosphor-mobil3` HEAD `bb3044d389a84f5673e5cc53e9110553a046d4db` plus dirty unreleased R14 sources.
- Freeze file: `dev/scratch/r14-startup-20260910/source.sha256`.
- All 6 freeze hashes matched the working tree. This reviewer holds no freeze.
- Dirty wiring outside the freeze: `StartupCoordinatorPolicyTest.kt`.
- Reviewer: Grok code quality only. No visual-taste or device judgment.
- Score: **7/10**.
- Gate: Ben accepts >= 8/10. Attempt 1 does not pass. Classification and prompt-free start need a correction, then independent round 2.

This is source code-quality review of the named startup slice. It is not device, accessory, or beauty acceptance.

## Evidence classes

Verified by reading freeze files after hash match, plus dirty tests/docs only as wiring evidence:

- `StartupCoordinatorPolicy` defaults are `none` and popup off. `allowed` is `none|mic|capture`. Invalid or missing `default_source` becomes `none`. `shouldAutoStart("none", false, false)` is false.
- `MainActivity` no longer reads `last_source` for reopen. Runtime still writes it. Archive tests already keep it out of portable schema.
- Auto-start runs from `onResume` only when `pendingFreshStartup` is set in `onCreate`. Surviving mic/capture/playback wake blocks restart. Root is not an allowed default. `RootCapturePolicy.PRODUCT_AVAILABLE` remains false, so `startCapture()` from this path cannot select root.
- Schema `/2` specs add `default_source` and `automatic_permission_popup`. Both are `v2Only`. Import writes prefs and `restoreTuning`; it does not call `maybeStartConfiguredDefault`.
- Settings chips are none/mic/capture. Copy says last-used is not a default. No root chip.

Inherited, not rerun: parent 952 JVM tests 0 fail/0 error/0 skip, lint, assemble.

Unobserved: ASUS fresh-launch, process-death restore, MediaProjection prompt, imported confirmation, and TalkBack.

## Requirements that hold

These are required behavior. They are not defects.

- Default none does not auto-start. Popup missing-key is off.
- `last_source` is not treated as the configured default. The old once-per-process reopen block is gone.
- Root auto-start stays deferred: not in `allowed`, not in the settings row, product backend stays STANDARD.
- Archive rejects unknown source strings through `StartupCoordinatorPolicy.allowed`. Legacy schema skips the new keys.

## Material findings

### F1. Capture auto-start can present permission UI while popup is off

**Priority: high. Contract:** default authorized starts directly; missing consent with popup off shows an inline grant/start action; popup on runs one serialized chain after visibility. Evidence: source, high confidence.

`maybeStartConfiguredDefault` treats `!captureConsentNeeded()` as prompt-free authorization:

```
"capture" -> if (!captureConsentNeeded()) startCapture()
    else if (ui.automaticPermissionPopup) startCapture()
```

`captureConsentNeeded()` is `!RootCaptureSettings.enabled(this) && !consent_seen`. `markConsentSeen()` runs in `launchCaptureConsent()` before the Activity result, not after a grant. `startCaptureBackend` for STANDARD still requests `RECORD_AUDIO` if missing, then always `captureConsent.launch(screenCaptureIntent(...))`.

Reachable path: user sets DEFAULT · capture, leaves AUTOMATIC PERMISSION POPUP off, has `consent_seen` from an earlier prompt, no surviving capture owner, cold `savedInstanceState == null` launch. `shouldAutoStart` is true. The system MediaProjection dialog appears. If `RECORD_AUDIO` was later revoked, the microphone permission dialog appears first.

Mic is fenced (`PERMISSION_GRANTED` or popup). Capture is not. There is no inline grant/start action on the scope face when popup is off and consent is missing; the function either pops the platform chain or returns.

**Smallest correction:** auto-start capture only when the start is prompt-free. A stored `consent_seen` flag is not a live projection token. Popup off must not call `startCapture()` / `micPermission.launch`. Missing consent stays inline. Keep surviving-owner rebind.

### F2. `freshLaunch(savedInstanceState == null)` drops process-death restores

**Priority: high. Contract:** a fresh launch is a new user app/task startup, including after process death; it is not resume, rotation, unlock, permission return, picker return, or HUD handoff. Evidence: source, high confidence.

Production always passes `configChange = false`:

```
pendingFreshStartup = StartupCoordinatorPolicy.freshLaunch(
    firstCreate = savedInstanceState == null,
    configChange = false,
)
```

The activity already lists `configChanges` for orientation/size, so rotation does not recreate. The remaining non-null `savedInstanceState` case is process-death (and other unlisted recreates). Spec includes process death. The policy test names `firstCreate = false` as resume, so the wrong classification is locked.

A process-level one-shot (the old `lastSourceReopened` shape) would fire after death and not on living `onResume`. `savedInstanceState == null` does the opposite for recents restore after kill: no configured default starts, even with granted mic and no surviving owner.

**Smallest correction:** treat cold start (`savedInstanceState == null`) and first create in a new process (process-death restore) as fresh. Keep later same-process recreates, `onResume`, unlock, permission/picker return, and HUD `onNewIntent` inert. Stop hardcoding `configChange = false` while testing the unused branch.

### F3. Portable auto-start keys activate without local confirmation

**Priority: high. Contract:** imported automatic-start behavior needs one local confirmation before its first activation. Imports never open a permission dialog. Evidence: source, high confidence.

`acceptSettingsArchive` writes `default_source` and `automatic_permission_popup` into ordinary prefs and `restoreTuning` publishes them into `ui`. There is no device-local confirmation latch distinct from the portable keys. The next accepted fresh launch uses the imported default as if the user had tapped DEFAULT · mic/capture here.

Import itself does not call `maybeStartConfiguredDefault`. The miss is the first activation after import, not an immediate start on decode.

**Smallest correction:** keep the keys portable and inert on import. Require one local confirmation (settings chip or an explicit confirm) before `shouldAutoStart` may fire. Do not re-ask after that local approval.

## Residual notes

R1. `StartupCoordinatorPolicyTest` does not lock `defaultOf("last_source")`, archive v2Only round-trip, or MainActivity's `savedInstanceState` / `startCapture` seam. Brightness already has that archive pattern.

R2. Settings chip ids duplicate `allowed` as a literal list. Drift would compile.

R3. Bluetooth mic auto-start can return from `explainMicrophone` with no inline action.

R4. `handleIntent("open"|"capture")` still runs in `onCreate` before auto-start. Manifest has no VIEW filter; residual unless those extras become a user path.

R5. `setDefaultSource` / `setAutomaticPermissionPopup` update UI even when `commit()` fails. Same pattern as several other toggles.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| Missing default is none; no auto-start; popup off | Executed policy | Pass in `missingDefaultIsNoneAndDoesNotAutoStart` |
| Resume/configChange not fresh | Executed policy | Pass; encodes F2's wrong restore class |
| Surviving/live/`file` block restart; mic can start | Executed policy | Pass |
| `last_source` not read for startup | Source | No `getString("last_source"` in `MainActivity` |
| Root not a default | Source | `allowed` and chips omit root; product backend STANDARD |
| Archive v2Only keys exist | Source | Spec + `v2Only`; no dedicated archive test |
| Import does not start capture | Source | `acceptSettingsArchive` has no `maybeStartConfiguredDefault` |
| Popup-off capture is prompt-free | Source | Fail F1 |
| Process death is fresh | Source | Fail F2 |
| Imported default needs local confirm | Source | Fail F3 |

## Limits

No product source, build, device, Git, or network work. This critique file is the only write. No freeze is held.

## Disposition

Reject attempt 1 at **7/10**. Release: fail. Round 2 reviews F1–F3. The three named must-nots still have to hold: no auto-start when default is none, `last_source` is not the default, root auto-start stays deferred.
