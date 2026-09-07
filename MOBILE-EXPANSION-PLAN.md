# Phosphor Mobile: expanded light, capture, and floating scope

Approved by Ben on 2026-09-07. Saved at the repository root at Ben's request.

This document preserves the approved plan. Saving it does not start implementation. Source observations describe the 2026-09-06 investigation and must be rechecked before execution.

## Goal

Deliver all nine requested additions as working, documented features:
1. Hidden, opt-in root Android audio capture.
2. A floating HUD above other apps, with solid or transparent backgrounds.
3. A smiling, tailed turtle and a substantially richer terminal-style manual.
4. Animated glyphs and better use of screen space without changing the core design or swipes.
5. Optional real HDR scope output.
6. Optional maximum screen brightness while Phosphor is foreground.
7. Random beam color and random cycle interval controls.
8. Up to six custom beam colors, selectable for ordered or shuffled cycling.
9. External/Bluetooth microphone selection and optional microphone mixing with “everything playing.”

Complete means implementation, persistence, error recovery, device acceptance, documentation, and verified installation. A toggle or successful build alone is not completion.

When execution begins, create an executable todo list from this plan. Keep each requirement open until its acceptance evidence exists.

## Scope / affected areas

Observed implementation:
- MainActivity.kt owns the SurfaceView, UI state wiring, settings, and render pause/resume.
- CaptureService.kt uses MediaProjection and a stereo AudioRecord.
- MicController.kt is activity-owned, assumes 48 kHz stereo, and selects the default MIC.
- Both capture readers currently feed the same native capture input. Concurrent writes would interleave audio, not mix it.
- ManualSheet.kt contains the manual and five-tap hidden bestiary. Console.kt is playback chrome, not a shell.
- LightSheet.kt, ScopeUiState.kt, MainActivity.kt, JNI, and render.rs all assume at most three custom colors.
- SettingsArchive.kt permits three colors and exactly nine RGB components.
- The current surface prefers SDR sRGB. The shared GPU shader already supports premultiplied transparency.
- Installed wgpu 27 supports an FP16 linear-scRGB Vulkan surface, subject to device capabilities.
- Active specs and release scanners explicitly exclude root and overlay access.

Affected code:
- Android source services, lifecycle policies, manifest, settings, Compose controls and manual.
- rust/src/{render,jni_glue,deck,engine}.rs and focused new mixer/cycle policy modules.
- Shared GPU changes in sibling phosphor/crates/phosphor-render-gpu, never a copied engine.
- Specs, acceptance tests, privacy/distribution docs, release checks, and developer receipts.

Preserve existing audio sources, sample-locked local playback, relay behavior, PiP, themes, gesture ownership, and accepted brightness fixes. Re-read live git state before implementation because another session has been editing the light controls.

## Approach

### 1. Establish the expanded contract and acceptance ledger

- Record all nine asks and the current source baseline.
- Update active specs to explicitly authorize root capture, overlays, microphone mixing, six-color cycling, and HDR.
- Reconcile the existing mobile-next roadmap without making unrelated roadmap completion a prerequisite.
- Replace obsolete scanner bans with precise permission, component, dependency, and artifact checks.
- Keep one Android product with debug/release builds. Do not silently introduce a privileged flavor.
- Define settings defaults, migrations, surface ownership, and source-stop behavior before changing runtime code.
- Retain current defaults. New privileged, overlay, mixing, HDR, and brightness features start off.

### 2. Prove root capture on the target device, then integrate it

- Inspect the actual Android version, root manager, SELinux context, audio policies, and available routes.
- Prototype a packaged helper launched through su/app_process from writable app/data storage.
- Require no LSPosed, boot patch, system installation, partition remount, reboot persistence, or global SELinux disable.
- Test privileged AudioPolicy loopback-with-render first, preserving playback on the phone.
- Investigate privileged capture policy options against actual opted-out audio and OEM behavior.
- Treat REMOTE_SUBMIX as a separate candidate, not an automatic solution: upstream scrcpy documents that it can divert playback away from the phone.
- If a diversion-based route is necessary, prove a feedback-free local monitoring path and route restoration before accepting it.
- Feed normalized PCM into the existing scope path through private IPC. Keep helper commands fixed and endpoints non-exported.
- Integrate source ownership, metadata mirroring, cancellation, reader failure, root denial/revocation, helper death, and stop acknowledgements.
- Use a truthful foreground-service declaration. Root capture must not pretend to own a MediaProjection session.
- Add the secret entry through the existing bestiary, followed by an explicit “ROOT CAPTURE” switch and root consent.
- Never request root on ordinary startup or silently fall back to microphone capture.
- Keep standard MediaProjection capture available. Report protected/offloaded/OEM limitations without claiming universal capture.

### 3. Add microphone routing and playback-plus-microphone mixing

- Enumerate actual input devices with AudioManager and observe connection changes.
- Offer built-in, wired, USB, Bluetooth SCO, and BLE inputs only when available.
- Negotiate supported channel counts, encoding, and sample rates. Convert mono and other rates to the native stereo format.
- Request Bluetooth permissions contextually. Prefer recorder routing, using communication routing only when the device requires it.
- Verify the actual routed device after recording starts. A preferred-device request is not proof of routing.
- Show Bluetooth quality/output-route changes before activation and restore temporary routing on stop.
- Add “INCLUDE MIC” to everything-playing capture, with input selection and mix-level controls.
- Treat playback-plus-mic as one composite source with two readers and one mixer output.
- Use bounded per-input buffering, timestamp alignment, resampling/drift correction, and clipping protection.
- Send only the mixed stream to the scope. Do not play microphone audio through speakers.
- Give microphone capture a real service owner for HUD/PiP operation, with microphone foreground-service permissions and visible recording state.
- Handle microphone denial, privacy mute, disconnect, competing recording, and partial-input failure without hiding which input stopped.

### 4. Introduce explicit visible-surface ownership and the floating HUD

- Extract the narrow activity-only render-surface controller into a reusable host.
- Keep one active native presentation surface initially: full app, PiP, or floating HUD.
- Fence surface callbacks by owner/generation so an old destroy callback cannot tear down the replacement.
- Add SYSTEM_ALERT_WINDOW consent and a user-started overlay service.
- Provide drag, resize, close, return-to-app, and compact source/transport controls.
- Add settings for HUD enablement and SOLID / TRANSPARENT background.
- Wire actual SurfaceView transparency, supported premultiplied swapchain alpha, and the existing shader’s scope_alpha.
- Do not simulate transparency by fading an opaque black rectangle.
- Give floating HUD priority over automatic PiP while HUD mode is active. Prevent duplicate windows and duplicate capture owners.
- Keep touches outside the HUD available to other apps. Respect Android’s protected screens and overlay restrictions.
- Pause hidden rendering and animations. Stop or retain audio according to explicit source/linger policy.
- Stop overlay presentation on lock, permission revocation, or user dismissal and cleanly restore full-app ownership.

### 5. Expand beam-light controls and persistence

- Replace fixed three-slot assumptions across UI, storage, JNI, and Rust with a validated six-slot model.
- Allow add, edit, remove, and per-slot cycle selection without erasing unselected colors.
- Add RANDOM COLOR with a roll-now action and an automatic mode.
- Add RANDOM INTERVAL with minimum/maximum controls inside the existing 0.1–60 second range.
- Add “Le random order” for selected colors, using a shuffle bag with no immediate repeat when multiple colors are selected.
- Separate generated random colors from shuffling saved colors.
- Keep one cycle authority in Rust. Draw randomness at cycle boundaries, never every frame.
- Preserve TIMER interpolation and TRACK stepping. Disable interval controls when TRACK mode owns advancement.
- Preserve and extend the current rapid-cycle acknowledgement across edits, imports, mode changes, and random interval bounds.
- Migrate existing three-color preferences and archives without loss. Validate malformed, partial, and out-of-range settings.
- Keep runtime random state separate from portable settings.

### 6. Redesign controls and expand the terminal-style manual

- Retain the room/palette language, hairline glyphs, tactile controls, beam-first hierarchy, and existing swipes.
- Rework spacing and adaptive layouts around actual phone, landscape, multi-window, and HUD dimensions.
- Use at least 48 dp touch targets, labels/accessibility semantics, and no overlapping gesture owners.
- Animate existing vector/Canvas glyphs with restrained rotation, trace, and state transitions.
- Gate animation clocks by lifecycle, surface visibility, component visibility, and reduced-motion settings.
- Hidden sheets and background activities must not keep infinite transitions or polling alive.
- Give the turtle a clearly visible smile and tail while preserving the other bestiary characters.
- Expand the manual into indexed/searchable terminal-style chapters covering every source, gesture, light control, display setting, permission, and recovery path.
- Add a substantial meme/bestiary collection, targeting at least 24 distinct entries or contextual responses without burying practical help.
- Preserve the secret bestiary interaction. Do not introduce an executable shell or runtime administration API.

### 7. Add genuine HDR scope output

- Probe display HDR capabilities and actual surface formats/headroom.
- Use the existing wgpu FP16 linear-scRGB path where supported.
- Add an explicit shared-renderer output mode so HDR stays linear and bypasses the current non-sRGB gamma-encoding branch.
- Map beam emission above SDR reference white while preserving black background, controlled glow, and readable SDR chrome.
- Reconfigure surfaces safely when HDR changes or the app moves between displays/presentation modes.
- Keep SDR rendering unchanged when HDR is off.
- Show requested versus active HDR state and a concrete unsupported reason.
- Test transparent HUD plus HDR separately. Do not assume compositor support.
- If FP16 presentation fails on the target, investigate a bounded native presentation alternative before declaring the device unsupported.

### 8. Add foreground maximum-brightness pinning

- Add a separate “PIN SCREEN BRIGHTNESS” setting using WindowManager.LayoutParams.screenBrightness = 1.0.
- Keep it independent of beam energy, auto-gain, HDR, and the retired brightness modulation.
- Apply it while the full app is foreground, including paused/no-source use, and keep that window awake.
- Reassert it after recreation, settings import, resume, and relevant window changes.
- Restore BRIGHTNESS_OVERRIDE_NONE when disabled or leaving full-app foreground.
- Do not write global brightness or auto-brightness settings and do not fight the system with a polling loop.
- Confirm automatic brightness cannot lower the foreground window under ordinary operation.
- Respect thermal, battery, accessibility dimming, and panel limits. Do not claim a brightness override bypasses hardware protections.

### 9. Integrate, document, and deliver

- Run requirement-linked tests after each reversible change and commit only owned work with receipts.
- Update manual, README, privacy, settings archive documentation, permission explanations, and known device limits.
- Validate the complete feature combinations, not just isolated toggles.
- Build and install through Gradle and dev/pm3 with explicit device serials.
- Verify installed APK hash, signer, package, version, source commit, and settings survival.
- Keep publication, store submission, destructive operations, and shared-branch pushes behind their existing explicit gates.
- If workers are useful, follow the model/effort confirmation ritual first. Keep shared device actions and integration with the root coordinator.

## Validation

| Requirement | Required evidence |
|---|---|
| Root capture | Real PCM and visible scope from ordinary and opted-out test apps, preserved audible output, speaker/wired/Bluetooth routes, denied/revoked root, helper crash, repeated start/stop, unchanged system/boot files. |
| External/Bluetooth mic | Actual routed-device observation and distinguishable physical input on each available accessory, mono/rate conversion, unplug/reconnect, permission denial, and restored routing. |
| Playback + mic | Two distinguishable test signals visible together, mic-off removes only mic, bounded delay/drift, no clipping or speaker feedback, partial-input failure truth. |
| Floating HUD | Real overlay above other apps, genuinely transparent background, drag/resize/touch isolation, rotation, PiP arbitration, return-to-app, lock/unlock, revocation, and no orphan service. |
| Six colors and randomness | Old archive migration, six-color round trip, selection persistence, deterministic seeded tests for shuffle membership/no-repeat and interval bounds, TIMER/TRACK behavior, rapid-cycle guard. |
| Animated redesign | Portrait/landscape/large-font screenshots and interaction tests, preserved swipe fixtures, accessibility semantics, reduced-motion behavior, frame/jank measurements, zero continuing hidden animation callbacks. |
| Manual and turtle | Complete feature-to-chapter coverage, search/navigation checks, legible smile/tail, meme inventory, no clipping in every room and large font. |
| HDR | FP16/scRGB surface and compositor evidence, linear-output tests, SDR regression images, real-panel HDR/SDR comparison. Screenshots alone do not prove luminance. |
| Brightness pin | Auto-brightness enabled during light changes, brightness slider changes, recreation/resume, exit/crash, and unchanged global settings. Record thermal/system constraints separately. |
| Integration | Existing source/gesture/lifecycle tests, Gradle unit tests/lint/build/checkEngine, locked Cargo tests, revised boundary/release checks, same-package update, and exact installed-artifact verification. |

Use a rooted handset for root acceptance, the non-root S25 for normal-path regression, and API 29 coverage for guarded fallbacks. Confirm actual device availability before activation.

Run a sustained HUD + capture + mic + random-light session, then repeat with HDR and brightness pin where applicable. Check audio continuity, thermal behavior, memory growth, frame pacing, and teardown.

## Open questions / decisions

Recommended defaults, resolved by plan approval:
- “Terminal” means the expanded terminal-style manual/bestiary, not an OS shell.
- “Six custom beams” means six saved beam-color slots, not six complete renderer presets.
- Random color generates a new color. Random order shuffles selected saved colors. Random interval chooses each TIMER leg duration.
- Mic mixing affects visualization only and starts off.
- “Foreground-only animation” includes the visible floating scope, but excludes hidden full-app chrome.
- Brightness pin applies to the foreground full app, not a HUD that would brighten unrelated apps.
- Root discovery can remain playful, but active root capture and microphone use remain explicit.
- Root and HDR support depend on measured device capabilities. No fallback or unavailable label counts as completed target-device acceptance.

Remaining engineering uncertainties:
- Whether the target OEM permits a non-diverting privileged audio mix under the stated root constraints.
- Which Bluetooth inputs are physically available and whether they change playback quality.
- Whether the target compositor supports HDR and transparency together.
- Current store eligibility for the expanded root/overlay/service behavior requires a separate distribution review, not an assumed “Play-safe” claim.

Blocked outcome format:
State the exact failing capability, commands and observations proving it, alternatives tested, useful partial result, and smallest next step. Keep that requirement open while completing every independent feature.
