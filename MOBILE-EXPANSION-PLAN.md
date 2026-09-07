# Phosphor Mobile: expanded light, capture, and floating scope

Approved by Ben on 2026-09-07. Saved at the repository root at Ben's request.

Updated on 2026-09-07 with Ben's confirmed settings-dismissal, expandable-settings, and theme-consolidation additions. Opening gestures already work and must stay unchanged.

This document preserves the approved plan. Updating it does not start implementation. Source observations describe the 2026-09-06 investigation, with settings/theme checks on 2026-09-07, and must be rechecked before execution.

## Goal

Deliver all twelve requested additions as working, documented features:
1. Hidden, opt-in root Android audio capture.
2. A floating HUD above other apps, with solid or transparent backgrounds.
3. A smiling, tailed turtle and a substantially richer terminal-style manual.
4. Animated glyphs and better use of screen space while preserving the core design and opening swipes.
5. Optional real HDR scope output.
6. Optional maximum screen brightness while Phosphor is foreground.
7. Random beam color and random cycle interval controls.
8. Up to six custom beam colors, selectable for ordered or shuffled cycling.
9. External/Bluetooth microphone selection and optional microphone mixing with “everything playing.”
10. More resistant, deliberate pull-down dismissal of settings without changing how settings opens.
11. Expandable settings sections inspired by Codex tool-call views, with a retro, flat, sleek instrument presentation.
12. A consolidated, customizable theme system with meaningful Light, Dark, Glass, and AMOLED presets.

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
- SheetDismissState in Sheets.kt follows downward drag 1:1. It dismisses at 72 dp travel OR 920 dp/s downward velocity. Nested scrolling can hand leftover movement to dismissal.
- Palette.kt lists 13 rooms. RoomStyle.kt already separates palette from character, motion, density, panel alpha, and overrides. Consolidate these existing mechanisms rather than create another theme engine.

Affected code:
- Android source services, lifecycle policies, manifest, settings, Compose controls and manual.
- Settings dismissal and presentation in Sheets.kt, Motion.kt, Dimens.kt, and their gesture policies/tests. Theme consolidation in Palette.kt, RoomStyle.kt, settings persistence, and SettingsArchive.kt.
- rust/src/{render,jni_glue,deck,engine}.rs and focused new mixer/cycle policy modules.
- Shared GPU changes in sibling phosphor/crates/phosphor-render-gpu, never a copied engine.
- Specs, acceptance tests, privacy/distribution docs, release checks, and developer receipts.

Preserve existing audio sources, sample-locked local playback, relay behavior, PiP, opening gestures, gesture ownership, and accepted brightness fixes. Consolidate the theme picker without losing saved appearances. Re-read live git state before implementation because other sessions may have changed these surfaces.

## Approach

### 1. Establish the expanded contract and acceptance ledger

- Record all twelve asks and the current source baseline.
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

### 6. Redesign controls, consolidate themes, and expand the terminal-style manual

- Retain the room/palette language, hairline glyphs, beam-first hierarchy, and existing opening swipes. Prefer retro flat/sleek surfaces, with restrained depth only where it explains interaction or hierarchy.
- Rework spacing and adaptive layouts around actual phone, landscape, multi-window, and HUD dimensions.
- Use at least 48 dp touch targets, labels/accessibility semantics, and no overlapping gesture owners.
- Animate existing vector/Canvas glyphs with restrained rotation, trace, and state transitions.
- Gate animation clocks by lifecycle, surface visibility, component visibility, and reduced-motion settings.
- Hidden sheets and background activities must not keep infinite transitions or polling alive.
- Give the turtle a clearly visible smile and tail while preserving the other bestiary characters.
- Expand the manual into indexed/searchable terminal-style chapters covering every source, gesture, light control, display setting, permission, and recovery path.
- Add a substantial meme/bestiary collection, targeting at least 24 distinct entries or contextual responses without burying practical help.
- Preserve the secret bestiary interaction. Do not introduce an executable shell or runtime administration API.

#### 6.1 Make settings dismissal deliberate, not slippery

- Leave settings opening thresholds, velocity handling, and finger-tracked opening behavior unchanged. Do not require a faster upward swipe.
- Add progressive resistance to downward sheet displacement and a stable return when the user releases below the dismissal threshold.
- Separate scrolling from dismissal. Only deliberate direct downward dragging at the content's top boundary, or on the header, can arm dismissal.
- Do not let leftover fling momentum or programmatic scrolling dismiss settings. Reversing direction must cancel dismissal predictably.
- Require minimum intentional travel before accepting a fast downward flick. Support a deliberate longer slow pull as an accessible alternative.
- Tune resistance and distance/velocity thresholds on-device against accidental-scroll and intentional-dismissal cases. Do not claim one universal numerical UI standard.
- Keep close and Android Back available with accessible labels and adequate targets. Preserve other dismissal paths unless separately approved.
- Scope this change to settings initially. Shared SheetHost changes must not silently alter source, manual, or light-sheet gestures.

#### 6.2 Reimagine settings as expandable instrument sections

- Use compact full-width section rows: meaningful glyph, heading, current-value summary, and expansion chevron. Reveal controls inline, following the useful interaction of Codex tool-call views rather than copying its branding.
- Group by user intent: Signal, Beam & Light, Display & HUD, Motion & Performance, Appearance, and About & Manual. Keep source-specific controls near their source.
- Work one example through the full design: collapsed “Display & HUD” reports “HDR off · HUD off · brightness system”. Expansion exposes those settings, their actual capability states, and explanations.
- Allow multiple sections open. Preserve expansion state and scroll position through theme changes, rotation, and returning to settings. Expansion itself never changes a setting.
- Keep rows flat and aligned, with hairline separators and clear text hierarchy. Avoid a stack of decorative boxes or icon-only mystery controls.
- Distinguish expansion, toggles, navigation, actions, and sliders by stable forms and semantics. Status color alone must not communicate state.
- Give TalkBack and switch/keyboard navigation explicit expanded/collapsed states, logical focus order, and announcements. Hidden controls must leave the focus tree.
- Animate expansion only while visible, honor reduced motion, and keep the touched header anchored to avoid scroll jumps.

#### 6.3 Consolidate theme presets and customization

- Use one theme model with four curated entry points: Light, Dark, Glass, and AMOLED. Presets initialize shared tokens and style settings, not separate UI implementations.
- Provide one Appearance editor for background/surface/text/accent colors, beam-following accents, surface opacity, density, and motion treatment. Keep advanced controls expandable and show the effective result in a live preview.
- Support save, rename, apply, reset, and delete for user appearance presets. Reset appearance without resetting audio, beam, or source settings. Keep whole-app settings archives distinct from appearance presets.
- Keep AMOLED as the accepted clean-install default. Do not switch the user's existing appearance during consolidation.
- Migrate every legacy room ID and explicit override. Preserve a retired room as an equivalent saved appearance instead of mapping it silently to the nearest new preset.
- Retire duplicate picker entries only after migration and archive round-trip tests pass. Keep legacy readers as needed for old exports, not parallel runtime theme systems.
- Separate theme glass treatment from floating-HUD transparency. Neither theme selection nor a preset import grants permissions or starts root, microphone, or overlay features.

Each preset needs a design contract, not just different colors:

| Preset | Meaning | Concrete visual and interaction language |
|---|---|---|
| Light | Daylight service manual and calibrated bench | Warm light surfaces, dark ink, crisp scale marks, compact mono labels, and flat section dividers. Accent marks the active/editable parameter. Shallow pressed feedback distinguishes actions without decorative bevels. |
| Dark | Night-time instrument panel | Charcoal surfaces retain separation without glare. Quiet secondary text and restrained accent illumination distinguish available controls from the live signal. Short eased transitions explain state changes. |
| Glass | Controls on a transparent instrument cover | Translucent control surfaces preserve the beam beneath, with clear edge lines and readable text backplates. Opacity communicates layering, not disabled state. Use restrained transitions and an opaque fallback where contrast or compositor support requires it. |
| AMOLED | The beam in darkness | True-black base, unfilled control areas, sparse hairline boundaries, and limited accent emission. No background glow or elevated grey slabs by default. Reduced chrome and static/brief transitions keep attention on the signal. |

- Define shared semantic roles across all presets: live signal, editable control, selected state, secondary context, unavailable capability, and error. Vary palette/material, never the meaning of a control.
- Motion must explain operation: chevron rotation means expansion, a rotating indicator means ongoing work, and a live trace means actual signal. Do not use busy animation for idle controls.
- Provide at least 4.5:1 contrast for normal text and 3:1 for large text and essential control boundaries. Test Glass against bright and dark moving content. Validate custom combinations and offer a readable correction without silently changing saved values.
- Write the four design contracts and representative collapsed/expanded screen examples before restyling all settings. Review them against the contracts and device checks, not subjective decoration counts.

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
| Settings dismissal | Opening regression fixtures remain unchanged. Top-of-list flings and ordinary scrolls do not close settings. Short fast accidental movements do not dismiss. Deliberate long slow pulls and qualified fast pulls dismiss. Reversal, cancellation, TalkBack, close, Back, and other sheets remain correct. |
| Expandable settings | Multiple sections expand independently, summaries reflect real settings/capabilities, expansion changes no settings, state/scroll survive rotation and theme changes, focus excludes collapsed controls, and large-font layouts stay readable. |
| Consolidated themes | Four preset design-contract reviews, contrast checks including moving Glass backgrounds, true-black AMOLED base, legacy-room/override migration, user-preset CRUD/reset, old/new archive round trips, and no appearance action changes audio or activates privileged features. |
| Manual and turtle | Complete feature-to-chapter coverage, search/navigation checks, legible smile/tail, meme inventory, no clipping in every room and large font. |
| HDR | FP16/scRGB surface and compositor evidence, linear-output tests, SDR regression images, real-panel HDR/SDR comparison. Screenshots alone do not prove luminance. |
| Brightness pin | Auto-brightness enabled during light changes, brightness slider changes, recreation/resume, exit/crash, and unchanged global settings. Record thermal/system constraints separately. |
| Integration | Existing source/gesture/lifecycle tests, Gradle unit tests/lint/build/checkEngine, locked Cargo tests, revised boundary/release checks, same-package update, and exact installed-artifact verification. |

Use a rooted handset for root acceptance, the non-root S25 for normal-path regression, and API 29 coverage for guarded fallbacks. Confirm actual device availability before activation.

Run a sustained HUD + capture + mic + random-light session, then repeat with HDR and brightness pin where applicable. Check audio continuity, thermal behavior, memory growth, frame pacing, and teardown.

## Open questions / decisions

Recommended defaults, resolved by plan approval:
- Ben confirmed that opening works well. Only pull-down settings dismissal gets more resistance, not upward opening.
- Expandable settings use the Codex tool-call interaction as inspiration, with Phosphor's retro flat/sleek visual language and meaningful summaries.
- Consolidation is required. The planned implementation combines four curated theme presets with one customization editor, rather than forcing a choice between presets and customization.
- Theme meaning follows the four design contracts above. Exact colors and resistance constants remain device-validated design work, not arbitrary promises.
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
