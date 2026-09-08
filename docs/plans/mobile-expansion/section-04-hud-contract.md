# Section 04: floating HUD contract [R02]

## Vision

The same beam stays useful above another app without interrupting its music. The HUD owns presentation, never a second source.

## Settings and migration

| Field | Type / default | Meaning |
|---|---|---|
| `floating_hud_enabled` | Boolean / false | Saved preference. Only an explicit Show action activates a session. |
| `floating_hud_background` | `SOLID` or `TRANSPARENT` / `SOLID` | Requested presentation, not capability evidence. |
| `floating_hud_width_dp` | integer 240–640 / 320 | Preferred width, clamped to the current display. |
| `floating_hud_height_dp` | integer 240–640 / 320 | Preferred height, clamped to the current display. |

Absent fields take these defaults. Existing `hud_mode` remains the in-app information setting with AUTO default. Archives validate typed fields and never carry window position, grants, service state, surface generation, or source identities. Import and restoration only update preferences. They never show the HUD or open consent. A saved enabled preference still requires Show after restart.

## Ownership and order

One process-local SurfaceOwner grants monotonically increasing leases. Full app and PiP use the activity host. FloatingHudService uses the same SurfaceHost implementation. Native detach completes before replacement attach. Every callback checks its lease, so stale change/destroy callbacks cannot touch a successor. Surface loss retires presentation only. Render tuning, source selection, and the existing source readers remain intact.

Show is a visible-activity action. Without overlay access, one explicit action opens Android overlay settings. Return from settings does not show or reopen consent automatically. With access and an unlocked display, start the exact nonexported `FloatingHudService`, add its window, then claim presentation. Failed or partial construction removes only its own views/listeners and leaves or restores the activity host. HUD suppresses automatic and manual PiP while pending or visible.

Hide, close, lock, revocation, and task removal cancel pending callbacks, release the HUD lease, remove the owned view, release its MediaController and listeners, and stop the foreground service. A visible activity may then reclaim its still-valid SurfaceView. Return launches the full activity and retires HUD ownership. Lock and revocation never automatically restart on unlock or grant. Service restart is NOT_STICKY. Process death carries no running preference.

The service observes screen lock, app-op permission changes, display configuration, and view visibility. No hidden presentation owns a render clock. Hidden activity chrome is not composed or ticked while HUD owns presentation. The foreground activity restores its chrome and lease when HUD ends. No view changes system brightness or lock-screen visibility.

Audio remains owned by existing PlaybackService/CaptureService. An active activity-owned microphone blocks HUD transfer before consent or service start. The restriction preserves its current reader and says to keep microphone presentation in the app until R09 supplies a real microphone service owner. It never stops or seizes that reader to make Show succeed. Closing HUD does not stop an existing service source. Task removal uses existing source/linger retirement. The HUD does not start or restart mic/capture in background. Source selection returns to visible app controls.

## Correction contract: native retirement, item identity, controller connection

An attach deadline cancels its request. Queued creation checks cancellation before GPU work. In-flight creation cannot publish after cancellation or receiver loss. Every request retains a retirement signal until its surface and native window have actually dropped. JNI waits for this signal before failed attach or destruction returns. Destruction also waits synchronously for the render command acknowledgement. Channel loss is not a clean acknowledgement and makes the shared owner unavailable to successors. A driver stall can delay this required barrier indefinitely. No bounded GPU completion, watchdog, or forced process exit is promised.

Android surface and visibility callbacks contain retirement failures instead of throwing through the framework. One production callback adapter reports the first failure and requests HUD cleanup independently. Later teardown operations still run. A disconnected render channel keeps the shared owner unavailable and tells the user to restart Phosphor. Closing listeners, removing the owned overlay, and stopping its foreground service do not turn a lost acknowledgement into success. Host fixtures exercise this adapter with the actual lease owner. Android callback recovery remains a separate device check.

Local item identity includes a process-local queue generation and item index, stable through filename-to-tag enrichment and artwork updates. A shared gate survives app/HUD/app handoff. Each host observes item events as well as metadata. RemotePlayer publishes `remote:now` and CapturePlayer publishes `capture:now`, not actual item IDs. Their host/package plus title, artist, and album remain a best-effort fallback. Artwork does not define a boundary. Capture's platform media ID is not exposed by its player face. Same-label distinct external items cannot be distinguished with the available metadata. No source lifecycle rewrite belongs here.

The HUD retains its connection future until retirement, including after success. One ownership primitive invokes Media3 `releaseFuture` exactly once. Its completion callback checks generation and retirement, but never separately releases the same result. Pending cancellation and completed release therefore use the same supported owner. Fixtures control completion, failure, and close around this production primitive.

## Controls and presentation

Sharp, legible 48 dp controls provide drag, bounded resize, close, return, source, previous, play/pause, and next. Window bounds remain inside current usable display bounds. `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCH_MODAL` preserve outside touches. Protected-screen overlay hiding is respected.

Transport uses a MediaController to the existing session and checks available commands. Labels derive from session acknowledgements, including CaptureMirrorPolicy observed playback. Unsupported transport says unavailable rather than inventing play state. Source selection returns to the app without starting an input.

SOLID uses an opaque SurfaceView and scope_alpha=1. TRANSPARENT requests translucent SurfaceView pixels, Vulkan PreMultiplied swapchain alpha, and shared renderer scope_alpha=0. Only the successful native configuration reports active transparent. Without PreMultiplied support, report requested TRANSPARENT / active SOLID with a concrete reason, use alpha=1, and recreate opaque SurfaceView presentation. No window fade simulates transparency. A failed surface reports unavailable, not active solid.

Surface transfers refresh density and dimensions without restoring stale tuning. Format stays compatible with the retained shared renderer. Future R13 HOLD/BLACK and R05 HDR remain separate authorities. R13 must provide opaque black for explicit BLACK even on transparent HUD. R05 must independently prove HDR plus transparency. This section adds neither held-frame nor HDR claims.

## Verification map

| Requirement | Source check | Coordinator acceptance |
|---|---|---|
| One generation owner | Pure lease transfer/stale callback fixtures | Repeat app/PiP/HUD transfers and rotation, inspect surface lifecycle logs |
| Failure and cleanup | Lease/show failure, lock/revoke policy fixtures and service wiring | Deny/revoke access, lock, close, remove task, kill process, force addView failure |
| No hidden work | Owner visibility fixtures and lifecycle wiring | Observe no render frames or chrome heartbeat when hidden/locked |
| Real transparency | Native alpha selection fixtures and pixel-format wiring | Show over another app with colored content, compare SOLID and TRANSPARENT |
| Source continuity | Service has controller only, no reader start | Verify unchanged source identity and exactly one reader during repeated handoff |
| Mic dependency | Explicit active-mic refusal fixture and Activity ownsSource guard | Start mic in app, tap Show, verify no consent/service/reader change. R09 owns future background-mic acceptance |
| Truthful transport | Capability/observed-state fixtures | Exercise local, relay, capture with and without transport capability |
| Touch and geometry | Bounds fixtures | Tap outside, drag/resize at every edge, rotate and change display size |
| Inert import/defaults | Typed archive and absent-key fixtures | Import enabled+transparent while access absent, verify no service or consent |
| Recovery | Owned teardown and activity fallback wiring | Return-to-app and failed-show restore beam, tuning, density and controls |

Device acceptance is deferred while the phone plays music. Worker evidence is source and permitted host tests only. Coordinator owns Android compilation and independent review.

## Implementation notes and acceptance procedure

Show checks active and pending activity microphone ownership before opening consent or starting the HUD. Successful native presentation acknowledges the swapchain mode before the activity moves behind the other app. A failed initial attach leaves the app available. The HUD uses a display-specific overlay window context on API 30+, explicit full-display coordinates, and system-bar/cutout bounds. API 29 reclamps when stable insets arrive.

The shared renderer and GPU remain outside the native Active surface. Surface transfer refreshes dimensions and density. The GPU pins the established color format, refusing an incompatible replacement instead of using a mismatched composite pipeline. Shared renderer resize still replaces energy textures. R13 owns retained-frame behavior and must not infer held-image continuity from this R02 handoff.

Track-driven light callbacks pass through the current SurfaceHost lease. An in-memory media identity gate deduplicates the same track across activity/HUD callbacks. Controller and surface attachment recheck the latest metadata, covering the handoff gap without a second transport owner. The focused track fixture exercises stale owners, repeated metadata, and return-to-app deduplication.

Cleanup steps run independently after the native window-lifetime barrier. A failed listener, surface, window, controller, or notification cleanup does not skip the remaining steps. Failures name the incomplete resource in status and logcat. A closing service cannot accept another Show until destruction finishes. SurfaceHost clears only its own host references even when native detach fails.

Coordinator procedure after Android compilation and independent source review:

1. Verify clean defaults and an old archive. Confirm in-app HUD AUTO, floating HUD off, PiP unchanged, and SOLID requested.
2. Import enabled plus TRANSPARENT without overlay access. Confirm no service, permission screen, source change, or connection starts.
3. Tap Show without access. Deny once, return, and confirm no loop or PiP. Grant once and confirm another Show is required.
4. Start an existing service-owned source. Record its owner identity, reader count, track position, tuning, and source wake state.
5. Show SOLID over a second app with bright content. Confirm a black scope backplate, usable outside touches, and no new source reader.
6. Return, choose TRANSPARENT, and Show again. Confirm the second app remains visible between beam strokes. Record active alpha status and Vulkan logs.
7. Exercise previous/play/pause/next on local, relay, and capture sources. Confirm labels change only from session observations and unsupported commands stay unavailable.
8. Tap SRC and confirm the visible source picker opens without starting mic, capture, or a relay. Repeat app/PiP/HUD handoff ten times.
9. Drag and resize at every edge. Rotate twice and change font/display size. Confirm all controls remain reachable and dimensions/density track the display.
10. Lock and unlock. Revoke access while visible. Close the HUD and remove the task with linger both off and on. Confirm no automatic restart, orphan view, duplicate reader, or hidden render/chrome clock.
11. Start an activity microphone and tap Show. Confirm the explicit R09 restriction, unchanged reader, no consent, and no HUD service.
12. Exercise addView and native attach failure with a test fixture. Confirm the app can reclaim presentation and every cleanup step runs after an injected failure.

Authored Android unit evidence: `FloatingHudPolicyTest` covers leases, stale callbacks, show/native failure models, close/death generations, cleanup exceptions, imports/permissions, hidden work, geometry bounds, and real source wiring. Two `SettingsArchiveTest` fixtures cover typed HUD round-trip and rejection. These fixtures require the coordinator's Gradle slot and have not run in the worker.

Native worker checks use Rust 1.96.0, locked offline resolution, and private target `/home/ben/.jcode/scratch/r02-cargo-target`. The final full host suite passed 75 tests, including alpha selection and scope_alpha fixtures. `rustfmt --check` passed for render.rs and surface_policy.rs. `rustfmt --emit stdout` parsed jni_glue.rs without edits. `git diff --check` passed. Native host checks do not compile Android-only render/JNI code or prove device transparency. The pre-existing RootCaptureChecks formatting line remains unchanged.

## Correction evidence, 2026-09-08

The immutable round-01 critique remains unchanged at SHA256 `308852afaa8ee8950f7d85c11483abeccca8fb196c5dd78ef839c9d5cf53c3c5`. This correction does not replace its assessment.

The corrected production lifecycle primitive passed four controlled host fixtures: queued attach deadline, in-flight attach deadline, lost attach receiver, and clean versus disconnected retirement with actual resource-drop ordering. The full locked offline Cargo 1.96.0 suite passed 79 tests in private target `/home/ben/.jcode/scratch/r02-correction-cargo-target`. JNI retains every unretired window token across replacement, including channel-loss paths, and prunes already-dropped tokens.

The actual `FloatingHudPolicyTest` class passed all 13 fixtures using the cached Kotlin 2.4.10 compiler and JUnit 4.13.2, without Gradle. This compiled production HudPolicy and SurfaceOwner with a scratch-only source-file reader. It did not compile Android SurfaceHost, PhosphorPlayer, or FloatingHudService. The new PlaybackTruthTest producer-wiring fixture is authored but unrun.

Pinned Media3 1.10.1 runtime bytecode confirms `releaseFuture(Future)` calls `cancel(false)`, or releases the completed controller when cancellation fails. The HUD uses that API once for its retained future. Host fixtures exercise the production ownership primitive with controlled futures and a disposal hook. They do not execute a real Media3 binding.

Rustfmt checks passed for surface_lifecycle.rs, render.rs, and lib.rs with child traversal disabled. JNI parsed through rustfmt stdout. Its pre-existing RootCaptureChecks formatting line was restored exactly rather than rewritten. Owned-path diff-check passed. Android compilation, JNI/GPU execution, actual delayed driver retirement, Media3 binding disposal, compositor pixels, and device handoffs remain coordinator acceptance. No worker Gradle, device, install, or Git mutation occurred.
