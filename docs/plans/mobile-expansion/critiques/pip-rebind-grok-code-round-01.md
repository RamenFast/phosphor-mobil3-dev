# PiP surface rebind code review, attempt 1 of 4

- Tree: `phosphor-mobil3` HEAD `6e775fa1c124f7e8652ba38b041ba20ce6103674` plus dirty PiP rebind sources.
- Freeze file: `dev/scratch/pip-rebind-20260910/source.sha256`.
- All 3 freeze hashes matched the working tree. This reviewer holds no freeze.
- Dirty wiring outside the freeze: `PictureInPicturePolicyTest.kt`, `ControlsVisibilityPolicyTest.kt`.
- Reviewer: Grok code quality only. No visual-taste or device judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. Attempt 1 accepts the bounded source.
- Historical: none. This is round 1 of 4.

This is source acceptance of the leave-PiP activity-surface rebind. It is not device, screenshot, or beauty acceptance. It does not close the ASUS HOME PiP visualization stall by itself.

## Diff under review

Five files, +30 / -1. Production change is fifteen lines:

- `PictureInPicturePolicy.shouldRebindSurface(leavingPip, activityStarted)` is `leavingPip && activityStarted`.
- `SurfaceHost.rebindActivity()` sets `activityVisible = true`, then `activity?.retire()` then `present()`.
- `MainActivity.onPictureInPictureModeChanged` snapshots `leaving = ui.pip && !isInPictureInPictureMode` before `ui.pip = isInPictureInPictureMode`. If the policy is true it rebinds and sets `ui.presentationVisible = true`. Existing `uiTick` post is unchanged: `if (!ui.pip && activityStarted && ui.presentationVisible) tick.post(uiTick)`.
- Tests: `leavingPipRebindsStartedActivitySurfaceOnly` plus an adjacent-string split in `ControlsVisibilityPolicyTest`.

No MicCaptureService, CaptureService, or native JNI owner changes.

## Evidence classes

Verified by reading the freeze files after hash match, plus dirty tests as wiring evidence, plus callees needed to judge the rebind:

- Leave snapshot is the previous `ui.pip` and the new `isInPictureInPictureMode`. Enter (`isInPictureInPictureMode == true`) cannot rebind. A second false callback cannot rebind. `leavingPipRebindsStartedActivitySurfaceOnly` locks the three non-trivial boolean rows and the Activity/SurfaceHost strings.
- `activityStarted` is set true only in `onStart` and false only in `onStop`. `onStop` already calls `SurfaceHost.activityVisible(false)`, which retires the activity host. The stale-lease case (native gone, SurfaceView still `ready`) is the path where `onStop` did not run, so `activityStarted` is still true. Dismissing PiP after `onStop` skips rebind.
- `present()` attaches only when `ready && !closed && !owner.accepts(lease)`. After `retire()`, `lease` is null, so a still-ready SurfaceView reclaims and calls `PhosphorNative.attachSurface`. `attachSurface` success unpauses render. That is the 0-segs repair: `uiTick` reads `scopeStats().segs` and is skipped while `ui.pip`.
- `SurfaceOwner` stays the single native authority. MainActivity still does not call `setRenderPaused` or `surfaceDestroyed`. `FloatingHudPolicyTest.actualWiringKeepsImportSourceAndNativeOwnershipSeparate` still holds those strings.
- `uiTick` post/remove counts are unchanged (3 posts, 4 removes). Rebind sits between the existing remove and the existing leave post.

Inherited, not rerun: parent 956 JVM tests just passed. No Gradle, Git, or device command ran here.

Unobserved: ASUS HOME PiP return to fullscreen, `surfaceChanged` vs `onPictureInPictureModeChanged` order, TalkBack.

## Requirements that hold

These are required behavior. They are not defects.

- Rebind only when leaving PiP with a started activity.
- Leave detection uses the previous `ui.pip` bit, not the updated one.
- Rebind is retire then present on the activity `SurfaceHost`, not a second native owner.
- Mic / capture identity is not touched. Checkpoint already held MicCaptureService across HOME PiP.
- Overlay HUD is not the ASUS path (`SYSTEM_ALERT_WINDOW` denied in `r09-asus-pip-checkpoint.md`). `preferred()` is the activity host.
- Existing leave `uiTick` restart remains. Forcing `presentationVisible = true` only runs when the rebind gate is true.

## Material findings

None that break the stated leave-PiP surface repair on the inspected call graph.

## Residual notes

These are not attempt-1 fails.

PR-N1. `leavingPipRebindsStartedActivitySurfaceOnly` locks `fun rebindActivity()` by presence, not the body. An empty function would still pass. Production body is retire then present.

PR-N2. The same test does not lock `ui.presentationVisible = true` inside the PiP callback. On the no-`onStop` path that assignment is already true. It matters only if presentation was cleared while still started (floating HUD).

PR-N3. Policy does not assert `shouldRebindSurface(false, false)`. Trivial.

PR-N4. `PhosphorScreen` early-returns a separate `AndroidView(factory = makeSurface)` when `state.pip`, and the fullscreen tree has another factory. Setting `ui.pip = false` schedules that switch. `rebindActivity()` runs on the still-composed host in the callback, before the next Compose frame. If Compose then disposes and `makeSurface()` builds a new host, the new surface attaches through `surfaceCreated` / `surfaceChanged`. If the SurfaceView remains, retire-then-present is the repair. Both depend on a later valid attach. This split is pre-existing.

PR-N5. `present()` uses `surfaceFrame` width/height. After retire, a 0-size frame skips `attach` and leaves native paused until `surfaceChanged`. Unlikely while `ready` is true (last PiP size should remain). If `surfaceChanged` is the missing callback, 0-size would miss the repair.

PR-N6. `attachSurface` `< 0` poisons that `SurfaceHost` (`failure` is sticky). Same path as ordinary `surfaceChanged`. Harmless if Compose recreates via `makeSurface()`. Sticky if the SurfaceView remains.

PR-N7. `ControlsVisibilityPolicyTest` no longer requires `removeCallbacks` immediately adjacent to the leave `tick.post`. The PiP-callback substring test now carries that order.

PR-N8. ASUS return-to-fullscreen is unobserved here. Checkpoint still open until a device pass shows non-zero segs with the same MicCaptureService identity.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| `shouldRebindSurface(true, true)` | Executed policy | Pass |
| `shouldRebindSurface(true, false)` / `(false, true)` | Executed policy | Pass |
| Leave snapshot before `ui.pip` assign | Source string in PiP callback | Pass |
| Callback calls `SurfaceHost.rebindActivity()` | Source string in PiP callback | Pass |
| `rebindActivity()` exists | Source string in `SurfaceHost.kt` | Pass; body not locked |
| Leave `uiTick` post still gated | Source string | Pass; counts unchanged |
| Retire then present body | Source read | Present; untested as a unit |
| Mic/capture owners unchanged | Source read | Pass |
| ASUS HOME PiP return segs | Device | Unobserved |

## Disposition

Accept at 8/10. Release the bounded source. No freeze held. Device still has to prove non-zero segs after ASUS HOME PiP return.
