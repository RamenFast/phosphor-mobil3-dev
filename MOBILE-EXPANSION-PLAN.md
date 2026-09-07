# Phosphor Mobile: expanded instrument plan

**Status:** approved feature direction, comprehensively recompiled on 2026-09-07 from Ben's requests through 23:15 UTC, including explicit approval of instrument presets and signal check. Plan only. No feature implementation or device activation is performed by this update.

**Canonical plan:** `MOBILE-EXPANSION-PLAN.md` at the repository root. This replaces the earlier accumulated versions in this same file. Git retains their history. Do not create a competing expansion plan.

## Goal

Phosphor turns sound into a truthful, immediate beam. The signal owns the screen. Controls explain the instrument without becoming a dashboard over it. Expansion must preserve that purpose, privacy, existing source behavior, and the retro flat/sleek character Ben wants.

Deliver every approved outcome below, including persistence, recovery, documentation, device acceptance, and verified installation. A selectable toggle, a build, or a review score alone does not prove completion.

| ID | Approved outcome | Implementation section |
|---|---|---|
| R01 | Secret opt-in root Android audio capture, without LSPosed or writable system/boot directories | 2 |
| R02 | Floating HUD above other apps, with solid or genuinely transparent backgrounds | 4 |
| R03 | Smiling, tailed manual turtle and a much more comprehensive, meme-rich terminal-style manual | 11 |
| R04 | Meaningful animated glyphs and better screen-space use, rendering only on visible foreground surfaces | 10 |
| R05 | Optional genuine HDR scope output | 12 |
| R06 | Optional full screen-brightness pin while the full app is foreground | 13 |
| R07 | Random beam color and random cycle interval controls | 6 |
| R08 | Up to six custom beam colors, selected for ordered or shuffled cycling | 6 |
| R09 | External/Bluetooth mic selection and optional mic inclusion in everything-playing capture | 3 |
| R10 | More resistant settings pull-down dismissal, with opening behavior unchanged | 9 |
| R11 | Expandable settings sections inspired by Codex tool-call views | 9 |
| R12 | Consolidated Light, Dark, Glass, and AMOLED themes with shared customization and meaningful design | 10 |
| R13 | Default pause holds the rendered frame for inspection, with a settings choice to go black instead | 5 |
| R14 | Configurable default source and automatic startup permission requests, with prompt-free already-authorized working root capture | 14 |
| R15 | Independent GPT Astra section critiques, scored 1–10, up to four rounds when below 8 | Critique protocol |
| R16 | Named instrument presets for complete beam setups, distinct from appearance presets and source settings | 7 |
| R17 | On-demand signal check explaining the actual input, signal health, and reasons for a dark beam | 8 |

### Approval boundaries

- This request recompiles documentation. Do not begin feature implementation as a side effect. At execution start, create an executable todo list from R01–R17 and the ordered sections.
- Original vision and specs remain source documents, but Ben's newer approved direction supersedes their old root, overlay, and startup restrictions. Update them before implementation so they no longer contradict this plan.
- Keep one Android product with debug/release builds. No silent flavor split, renderer fork, root service installed in system, runtime administration API, analytics, or unrelated relay redesign.
- Preserve local sample-locked playback, relay behavior, standard MediaProjection capture, source truth, accepted beam-energy fixes, settings survival, and opening gestures.
- Existing theme appearances must survive consolidation. The four-preset picker replaces duplicated presentation choices, not the user's saved look.
- Instrument presets and signal check are explicitly approved. Calibration playground, beam snapshot export, and listening-session timer remain **unapproved** and outside this plan.

## Scope / affected areas

### Grounded source observations

Observed on 2026-09-06 and rechecked in relevant areas on 2026-09-07. Re-read current source and git state before execution.

| Area | Current seam and constraint |
|---|---|
| Android host | `MainActivity.kt` owns SurfaceView creation, settings wiring, source selection, and render pause/resume. Its lifecycle render pause is not a user-facing freeze/inspect state. |
| Startup | `onResume()` opportunistically restores some passive sources using `last_source` and `lastSourceReopened`. It is not an explicit default-source startup policy. Capture consent callbacks already use selection/task fencing. |
| Transport | `togglePlay()` uses the Media3 controller or native deck. Preserve source-specific audible pause and captured-session truth while adding pause presentation. |
| Capture | `CaptureService.kt` owns a MediaProjection session and stereo AudioRecord. Reuse its proven reader-stop and source-retirement contracts, not its projection identity for root capture. |
| Mic | `MicController.kt` is activity-owned, opens default MIC at 48 kHz stereo, and feeds the native capture input. Mixing needs a new composite owner, not two readers interleaving samples into one input. |
| UI | `Sheets.kt`, `Console.kt`, `Controls.kt`, `Glyphs.kt`, `Motion.kt`, `Gestures.kt`, and `Dimens.kt` own presentation and gestures. The terminal is a styled manual/bestiary in `ManualSheet.kt`, not a shell. |
| Dismissal | SheetDismissState follows downward drag 1:1 and accepts 72 dp travel OR 920 dp/s velocity. Nested scroll can pass leftover downward movement to sheet dismissal. |
| Themes | `Palette.kt` has 13 rooms. `RoomStyle.kt` already separates palette from character, motion, density, panel alpha, and overrides. Consolidate these seams. |
| Light | Kotlin state, settings archive, JNI, and `render.rs` assume three custom colors. Archive validation expects nine RGB components. All these limits must change together. |
| Renderer | `rust/src/render.rs` prefers SDR sRGB. The shared GPU shader supports premultiplied transparency. Installed wgpu 27 supports an FP16 linear-scRGB Vulkan surface when the device exposes it. |
| Contracts | Active vision/specs and release scanners explicitly exclude root and overlays. Replace obsolete prohibitions with exact permitted-component/permission checks. |

Native changes belong in `rust/src/{render,jni_glue,deck,engine}.rs` and focused mixer/cycle/presentation policy modules. Shared GPU changes belong in sibling `phosphor/crates/phosphor-render-gpu`, never a copied engine. Read sibling governance before editing it.

### State and default contracts

Use typed persistence and explicit migrations. The following are behavior contracts, with final key names defined in the active spec before code changes.

| Setting/state | Default and persistence |
|---|---|
| Pause presentation | `HOLD` by default, `BLACK` alternative. Portable user setting. Held image, inspect transform, and source identity are transient, not exported recordings. |
| Default source | `NONE` until configured. Allow everything playing, mic, and explicitly configured local/relay targets. Private target/device identities stay outside portable archives. |
| Automatic permission popup | Off initially. When enabled, a fresh user launch can request missing permissions for the chosen default source without opening the source picker. It does not grant permissions itself. |
| Root capture | Hidden, off initially. Once enabled, use the authorized root backend for everything playing. Root grants and measured backend health are runtime/local facts, not portable settings or proof supplied by an archive. |
| HUD, include mic, HDR, brightness pin | Off initially. Save user preferences, but permission grants and service state are not settings. |
| Light cycle | Preserve accepted defaults and 0.1–60 second range. Ordered selected colors unless shuffle is enabled. Random color, random interval, and shuffle are independent modes with explicit precedence. |
| Appearance | Keep AMOLED as the accepted clean-install default. Preserve existing user's palette/style on update. Four curated presets share one customization model. |
| Instrument presets | Named portable beam-setting records with a versioned schema. No source targets, permission grants, startup actions, theme colors, HDR, or window-brightness overrides. Saving/importing a preset does not apply it. |
| Signal check | On-demand read-only view of current source and measured signal health. Transient bounded measurements only, no recording history, automatic export, or new permission request just to inspect state. |
| Existing settings | Preserve PiP, linger, rotation, beam-energy, gain, and other accepted defaults unless this plan explicitly changes them. |

Imports are inert: they never start capture, request root, open permission dialogs, dial a relay, or activate a HUD. Portable startup preferences do not carry grants or private targets. Imported automatic-start behavior requires local confirmation before its first activation. A confirmed local configuration then works without repeated app-owned confirmations.

## Approach

Execute sections in order. Investigate hardware-dependent feasibility early, then integrate proven paths. A blocked section retains its evidence while independent work continues. Re-read changing shared files before each edit and commit only owned changes.

### 1. Establish contracts, baseline, and execution tracking

- Record R01–R17 in the project asks ledger and map each to the validation matrix below.
- Refresh vision, active product/audio/experience/distribution/acceptance specs, and decisions to describe this expanded scope. Preserve historical decisions and protected archives.
- Reconcile the existing mobile-next roadmap without making unrelated roadmap completion a prerequisite.
- Capture exact mobile/sibling commits, dirty files, toolchains, existing defaults, and regression receipts. Do not overwrite another session's work.
- Specify source ownership, startup decisions, pause presentation, new settings schema, migration, and permission boundaries before runtime changes.
- Replace obsolete root/overlay scanner bans with exact permission, service, IPC, dependency, and artifact checks. Retain privacy and package-boundary checks.
- Maintain one execution ledger with requirement, owner, status, test receipts, critique score/round, and unresolved gaps. Git commits provide reversible checkpoints.

### 2. Prove and integrate root capture (R01)

- Inspect the target Android build, root manager, SELinux context, audio policy, routes, and existing authorization. Do not infer backend support merely from `su` succeeding.
- Prototype a packaged fixed-command helper launched with su/app_process from writable app/data storage. Require no LSPosed, system/boot writes, remount, boot patch, reboot persistence, or global SELinux disable.
- Test privileged AudioPolicy loopback-with-render first, keeping audio audible on the phone. Investigate actual policy options against ordinary and opted-out applications and the OEM implementation.
- Treat REMOTE_SUBMIX as a distinct candidate. Upstream scrcpy documents that it may divert audio away from the phone. If needed, prove a feedback-free local monitoring route and full route restoration before accepting it.
- Normalize PCM and transfer through private IPC to the existing scope input. Package and version the helper with the app. No exported control endpoint, arbitrary shell command interface, unsolicited network traffic, or disk audio recording.
- Integrate generation-tagged source changes, metadata mirroring, reader failures, helper death, cancellation, denial/revocation, bounded shutdown, and acknowledgement before replacement.
- Declare the actual foreground-service role and its Android-version requirements. A root capture session must not pretend it owns MediaProjection consent.
- Reveal the root switch through the existing hidden bestiary. Explain and obtain root authorization when the user enables it. Do not probe/request root on normal startup when the feature is off.
- When previously authorized root capture is enabled and working, starting everything playing uses it directly without MediaProjection or redundant app permission dialogs. Section 14 governs startup and failure behavior.
- Keep standard playback capture available. Report actual unsupported/offloaded/protected/OEM cases without claiming universal capture. Unsupported labels alone do not complete target-device root acceptance.

### 3. Route microphones and mix capture (R09)

- Enumerate available built-in, wired, USB, Bluetooth SCO, and BLE inputs with AudioManager and observe device changes.
- Negotiate supported channels, format, and rate. Convert mono/other rates to the native stereo format. Do not require every accessory to support 48 kHz float stereo.
- Request Bluetooth permissions only when needed. Prefer AudioRecord device selection, using communication routing only where required. Verify the actual route after recording starts.
- Explain Bluetooth quality/output-route changes before initial activation. Restore temporary routing/mode changes on stop. On explicit input loss, report the missing device instead of silently selecting a different microphone.
- Add INCLUDE MIC, input choice, and mix-level controls to everything playing for standard and root backends. Mixing affects visualization only, not speaker output.
- Represent playback-plus-mic as one composite source with two readers and one mixer producer. Use bounded per-input buffers, timestamp alignment, rate/drift correction, and clipping protection.
- Give mic capture a real service owner for HUD/PiP use, with correct microphone foreground-service type/permission, recording indication, and while-in-use startup rules.
- Handle permission denial, privacy mute, competing recording, disconnect, partial-input loss, and retries. If mic fails, playback capture can continue with an explicit “mic unavailable” state, not a false claim that both inputs flow.
- Preserve source retirement and task-removal/linger rules. No old reader or stop callback may affect a new source. Update lifecycle policies to describe the new service-owned microphone honestly.

### 4. Establish visible-surface ownership and floating HUD (R02)

- Extract the narrow activity-only surface controller into a reusable host. Initially use one active native presentation surface: full app, PiP, or HUD.
- Tag callbacks with surface owner/generation. An old destroy callback cannot tear down a replacement. Transfer presentation without duplicating audio capture or native render loops.
- Add SYSTEM_ALERT_WINDOW consent and a user-started overlay service. Provide drag, resize, close, return-to-app, and compact source/transport controls.
- Add HUD enablement and SOLID / TRANSPARENT background controls. Use real SurfaceView transparency, supported premultiplied swapchain alpha, and shader scope_alpha. Fading an opaque black rectangle is not transparency.
- Keep touches outside the HUD available to other apps. Respect protected screens and platform overlay restrictions. Give HUD priority over automatic PiP while HUD mode is active.
- Stop hidden rendering/animation work. A visible HUD counts as foreground presentation, not permission to animate hidden activity chrome.
- Lock or overlay-permission loss removes presentation. User dismissal stops the HUD owner. Retain or stop audio according to explicit source/linger policy and restore full-app ownership cleanly when appropriate.

### 5. Make hold/inspect the default pause surface (R13)

- Separate source transport state, display pause state, and lifecycle render suspension. Do not reuse `setRenderPaused` as all three authorities.
- Add PAUSE DISPLAY: HOLD FRAME / BLACK in Display settings, default HOLD FRAME. Apply the selected behavior to the app's pause surface and confirmed transport pause observations.
- Preserve existing source-specific transport semantics: pausing local playback still pauses audio, and capture/relay transport commands reflect actual supported controls and acknowledgements. Do not silently redefine every pause as “audio keeps playing.”
- For a live source without controllable transport, provide a clearly labeled display pause. Show “display held · source live” rather than pretending the source stopped. Source interruption, silence, buffering, and errors remain distinct from an intentional pause.
- On HOLD, retain the last fully presented beam frame before pause. Freeze persistence decay, light-cycle appearance, geometry animation, and deposited samples for that image. Do not freeze a later empty buffer after transport has stopped.
- Allow pan/zoom inspection of the held image with reset and a clear return-to-live action. These gestures change only the transient inspection transform, not live gain, focus, rotation settings, or remote controls. Preserve settings opening gestures.
- On BLACK, clear the scope to true black once while keeping controls and resume affordances available. A transparent HUD should show a black scope region for this explicit mode, not silently reinterpret black as transparency.
- Changing HOLD/BLACK while paused updates presentation without changing transport. Retain the held image in memory for that pause session so switching back to HOLD can recover it.
- Retain the paused image across surface recreation, rotation, and app/HUD/PiP handoff with bounded GPU resources and correct resize behavior. On process death or no available frame, show black plus an honest “no held frame” state, not invented history.
- Resume joins the current source timeline without dumping an accumulated capture backlog into the scope. Source changes and explicit stop invalidate the old held image. Background lifecycle suspension alone does not create or clear a user pause.
- Redraw held presentation only for inspection, chrome changes, or surface events. Do not continuously deposit or rerender an unchanged frozen frame. Keep retained samples/images in memory only and clear them on source retirement.

### 6. Expand custom beam and random cycling (R07–R08)

- Replace all three-slot assumptions across state, UI, typed settings, archives, JNI, and Rust with validated support for six custom color slots.
- Allow add/edit/remove and per-slot cycle selection without deleting unselected colors. One selected color is solid. Zero selected custom colors returns to an explicit preset state.
- Add RANDOM COLOR with roll-now and automatic generated-color modes. Add RANDOM INTERVAL with minimum/maximum bounds within 0.1–60 seconds.
- Add “Le random order” for selected saved colors using a shuffle bag, with no immediate repeat when at least two distinct slots are selected.
- Keep generated colors distinct from shuffled saved colors. When automatic generated color is active, saved-slot order controls are inactive but retained. The UI explains which mode owns color selection.
- Rust remains the sole cycle clock and randomization authority. Draw randomness at leg/track boundaries, never every frame. Use deterministic seeded tests without exporting runtime RNG state.
- Preserve TIMER interpolation and TRACK stepping. Random interval chooses each TIMER leg duration. Disable interval controls in TRACK mode. HOLD pause freezes the displayed result without corrupting source/track state.
- Preserve the rapid-cycle acknowledgement across min/max edits, imports, enabling randomness, and TIMER/TRACK switches. Reject invalid ranges before publishing settings to the renderer.
- Migrate old three-color preferences and nine-component archives without loss. Validate count/selection/components together. Retain current accepted default values.

### 7. Save and recall complete instrument presets (R16)

- Add an INSTRUMENT PRESETS surface near Beam & Light and a compact recall action. Keep it visually and semantically separate from Appearance presets and whole-app settings archives.
- Define a typed, versioned preset for scope mode, geometry/effect amount, gain/auto-gain, focus, beam energy, glow, custom colors and selected slots, color-cycle/randomization settings, and related beam controls. Enumerate exact fields in the active spec rather than serializing all preferences.
- Exclude audio sources, transport, microphone mixing, permissions, root activation, startup choices, relay hosts, appearance, HDR, brightness pin, and live inspection transforms. Applying a beam setup must not start recording, change volume, or alter the selected source.
- Provide create from current setup, name, save/update, duplicate, rename, delete, and explicit apply. Preserve saved records when current controls are edited and show when the active setup has diverged from the recalled preset.
- Include a small curated starting set such as “Clean XY”, “Spectral bench”, and “Ambient”, with their purpose explained and exact parameters validated against the supported modes. Do not promise numerical calibration or use unusually rapid/high-energy defaults.
- Validate the entire preset before applying it. Publish a coherent settings snapshot to Kotlin/native ownership, with no partially applied preset on validation or renderer failure. Preserve the prior setup for immediate undo of a preset application.
- Apply presets without interrupting source audio or stealing gesture focus. If the source's remote geometry owns a setting, report that capability boundary rather than falsely claiming the remote renderer changed. Use only existing supported relay controls, with no speculative protocol redesign.
- While HOLD is active, preserve the held image. Preset application changes the live setup used on resume and is labeled accordingly. It does not erase the image or mutate the inspect transform.
- Reuse rapid-cycle acknowledgement and all six-color/range validation. A preset or imported archive cannot bypass the light guard. No permission or root authorization material enters the preset schema.
- Persist user presets through updates and provide versioned import/export through the existing settings archive mechanism or a clearly identified preset document. Import validates without applying, handles duplicate names explicitly, and preserves existing presets on failure.

### 8. Explain the actual signal with signal check (R17)

- Add an expandable SIGNAL CHECK view under Signal & Startup, reachable from a no-signal/error message without leaving the instrument. Keep the usual scope screen quiet when it is closed.
- Show selected source and actual active owner/backend, desired versus routed microphone device, negotiated rate/channels/format, and whether playback, microphone, or both currently contribute to the scope.
- Present measured input level, peak/clipping indication, recent sample-flow freshness, and reader/link state. Read bounded observations from existing source owners and the audio path without consuming audio twice or adding work that blocks realtime readers.
- Label observations precisely: waiting for consent, starting, flowing, measured silence, stalled reader, disconnected, unavailable input, display held, or display black-on-pause. Do not diagnose a source-app opt-out or DRM from silence alone.
- Distinguish silence from a healthy held/black pause surface. Show that input can still flow while the display is intentionally held, and that an external transport can be paused separately.
- Worked example: “Everything playing › root · 48 kHz stereo · samples arriving · display held”. Another: “Playback capture › consent needed”, with the existing explicit grant action. Never show guessed sample rates or fabricated zero readings when measurement is unavailable.
- For mic mixing, show each input's health and its contribution separately. A missing mic must not make a healthy playback input look disconnected.
- Provide plain-language next actions using existing retry, permission, route-selection, and source controls. Opening diagnostics itself never switches sources, asks for access, launches another recording, or dials a relay.
- Refresh only while the view is visible and at a bounded UI rate. Retain no behavioral/audio history and expose no new network endpoint. Private device/host details stay local and out of portable settings.
- Test exact status precedence and stale-owner fencing with fixture states, then prove real silence, signal, clipping, denial, route loss, root failure, and hold/resume on-device. Signal check must describe the same reality as the normal source face.

### 9. Reimagine settings and strengthen dismissal (R10–R11)

- Leave settings opening thresholds, velocity handling, and finger-tracked opening behavior unchanged. Ben explicitly says opening works well. Do not require a faster upward swipe.
- Add progressive resistance to downward sheet displacement and a firm return below the dismissal threshold.
- Arm dismissal only through deliberate direct dragging at the content's top boundary or on its header. Leftover fling momentum, programmatic scrolling, slider gestures, and expansion animations must not dismiss the sheet.
- Require minimum intentional travel before accepting a fast downward flick. Keep a longer slow pull as an accessible alternative. Reversal/cancellation returns control predictably.
- Tune distance/velocity/resistance on-device against observed accidental and deliberate gestures. There is no universal numerical “best UX” threshold. Preserve close, Back, other approved dismissal paths, and other sheets' behavior.
- Present settings as compact full-width expandable rows: meaningful glyph, heading, current-value summary, and chevron. Reveal controls inline, inspired by Codex tool-call interaction rather than copying its branding.
- Group by intent: Signal & Startup, Beam & Light, Display & HUD, Motion & Performance, Appearance, and About & Manual. Source-specific controls remain near their source.
- Worked example: collapsed Display & HUD shows “Hold on pause · HDR off · HUD off · brightness system”. Expansion exposes those settings and actual capability explanations.
- Allow several sections open. Preserve expansion/scroll position across returning to settings, theme changes, and rotation. Expansion never changes a setting and should keep the touched header anchored.
- Use flat aligned rows, hairline separators, clear typography, and stable distinct forms for expansion, toggles, actions, navigation, and sliders. Avoid decorative boxes and unexplained icon-only controls.
- Provide at least 48 dp targets, meaningful labels, expanded/collapsed semantics, logical TalkBack/switch/keyboard focus, and readable large-font layouts. Collapsed controls leave the focus tree.

### 10. Consolidate meaningful themes and animated controls (R04, R12)

- Reuse Palette/RoomStyle into one theme model. Offer Light, Dark, Glass, and AMOLED as curated entry points, not four separately implemented UIs.
- Provide one expandable Appearance editor for background/surface/text/accent colors, beam-following accents, opacity, density, and motion treatment, with a live preview.
- Support save/rename/apply/reset/delete of user appearance presets. These are theme presets, separate from the approved instrument presets in section 7. Reset appearance without resetting audio, sources, or beam controls.
- Preserve all legacy room IDs and explicit overrides as equivalent saved appearances. Retire duplicate picker entries only after migration and old/new archive round trips pass. Do not silently map an existing theme to its nearest replacement.
- Preserve AMOLED clean-install default and the current user's theme. Theme glass is separate from HUD transparency and cannot grant permissions or start a source.
- Rework spacing around actual portrait, landscape, multi-window, and HUD dimensions while retaining the beam-first hierarchy, retro flat/sleek character, hairline glyphs, and existing opening swipes. Reserve subtle depth for meaningful interaction/importance.

| Preset | Meaning | Concrete language |
|---|---|---|
| Light | Daylight service manual and calibrated bench | Warm light surfaces, dark ink, crisp scales, compact mono labels, flat dividers. Accent identifies the editable/active parameter. Shallow pressed feedback distinguishes actions. |
| Dark | Night-time instrument panel | Charcoal layers retain separation without glare. Quiet context text and restrained accent distinguish controls from signal. Short eased transitions explain state changes. |
| Glass | Controls on a transparent instrument cover | Translucent surfaces preserve the beam below. Edge lines and readable text backplates explain layers. Opacity never substitutes for disabled-state meaning. Supply an opaque fallback when readability or compositor support requires it. |
| AMOLED | The beam in darkness | True-black base, unfilled controls, sparse boundaries, limited accent emission. No background glow or elevated grey slabs by default. Static/brief transitions keep attention on the signal. |

- Define shared semantic roles across presets: live signal, editable control, selected state, context, unavailable capability, and error. Change palette/material, not meaning.
- Animate existing vector/Canvas glyphs with purpose: chevron rotation means expansion, a rotating progress symbol means real ongoing work, and a trace means actual signal. Do not portray idle controls as busy.
- Gate all animation clocks and polling by lifecycle, surface visibility, component visibility, and reduced motion. Hidden sheets, background activities, and obscured chrome must not keep infinite transitions alive.
- Target at least 4.5:1 normal text contrast and 3:1 large text/essential control boundaries. Test Glass over bright/dark moving content. Validate custom combinations and offer readable corrections without silently altering saved values.
- Before restyling all settings, produce the four design contracts and representative collapsed/expanded layouts. Review by observable meaning, accessibility, and real-device behavior, not decoration count.

### 11. Expand manual, terminal art, and memes (R03)

- Give the turtle a clearly legible smile and tail while preserving the other bestiary characters and five-tap discovery interaction. Include the root feature's secret entry without making active recording secret.
- Make the terminal-style manual indexed/searchable, with chapters for every source, gesture, light setting, theme, instrument preset, signal-check reading, startup policy, pause inspection mode, display feature, permission, and recovery path.
- Add at least 24 distinct meme/bestiary entries or contextual responses without obscuring practical instructions. Keep art legible across themes and large fonts.
- Explain source/transport pause versus display hold, genuine HDR versus brightness/beam gain, normal versus root permission flow, and Bluetooth route limitations.
- Do not introduce an executable shell, network-fetched meme feed, behavioral telemetry, or app administration protocol.

### 12. Add real HDR scope output (R05)

- Probe display HDR capabilities, actual surface formats, and available headroom. Use wgpu's FP16 linear-scRGB Vulkan path where supported.
- Add explicit output-mode handling in the shared renderer. HDR output stays linear and bypasses the existing non-sRGB gamma-encoding branch.
- Map beam emission above SDR reference white while preserving black, controlled glow, and readable SDR chrome. Keep SDR output unchanged when HDR is off.
- Reconfigure safely on toggles, display changes, pause/held-image presentation, and app/PiP/HUD transitions. Retained HDR images must not become washed out when presented through SDR fallback.
- Show requested versus active HDR state with a concrete unsupported reason. Test transparent HUD plus HDR independently, not as an assumed compositor capability.
- If FP16 fails on the target, investigate a bounded native presentation alternative before declaring it unsupported. A brighter SDR beam is not a substitute for real HDR acceptance.

### 13. Pin foreground screen brightness (R06)

- Add PIN SCREEN BRIGHTNESS using `WindowManager.LayoutParams.screenBrightness = 1.0`, separate from beam energy, auto-gain, HDR, and retired brightness modulation.
- Apply while the full app is foreground, including paused/no-source use, and keep that window awake. Reassert on recreation, resume, import, and relevant window changes.
- Restore BRIGHTNESS_OVERRIDE_NONE when disabled or leaving full-app foreground. Do not brighten unrelated apps through the HUD or write global brightness/auto-brightness settings.
- Confirm automatic brightness cannot lower the foreground window under ordinary operation. Do not use a polling loop to fight the system.
- Respect panel, thermal, battery, and accessibility dimming constraints. Warn plainly about sustained brightness/static-image exposure without blocking every use. Do not claim a backlight override bypasses hardware protection.

### 14. Default-source startup and automatic permissions (R14)

- Replace opportunistic last-source reopening with one explicit startup coordinator. Keep manual source selection authoritative and distinguish a chosen default from whichever source happened to run last.
- Add DEFAULT SOURCE and AUTOMATIC PERMISSION POPUP in Signal & Startup. Selecting a default authorizes its configured startup action on future fresh user launches. No default preserves manual behavior.
- Define fresh launch as a new user-initiated app/task startup, including launch after process death, not every onResume, rotation, permission return, app/HUD switch, unlock, or return from a picker.
- Run at most one startup attempt/permission chain per launch generation. Fence all asynchronous callbacks by task, launch, and source selection. Resume a surviving real service instead of restarting it.
- Start an already-authorized configured source directly. With automatic popup off, missing consent leaves an inline start/grant action. With it on, request only missing permissions necessary for that default source after the activity is visible.
- For standard everything playing, sequence required RECORD_AUDIO permission and fresh MediaProjection consent correctly. API 34+ projection tokens remain single-use. Never auto-accept or fake a system consent result.
- For mic/external input, request necessary recording/Bluetooth access contextually, then start the actual selected route. Handle unavailable devices honestly.
- Local/relay defaults require a previously selected valid file/folder/host. Keep private identities local, revalidate access, and report missing targets rather than opening unsolicited pickers or choosing a random host. Explicit default configuration permits that selected relay's startup connection, not network activity on an unconfigured install.
- Optional metadata/listener, overlay, and other special access use their real platform flows only when required by the configured startup feature. Do not dump all optional permission screens onto launch. Requests remain serialized, never overlapping.

#### Startup decision table

| Condition | Required behavior |
|---|---|
| No default configured | No automatic source selection, permission dialogs, root request, or network connection. |
| Default source already active under a surviving owner | Rebind and show its actual state. Do not restart or request another projection token. |
| Default authorized, normal path | Start directly regardless of the automatic-popup switch. |
| Default needs consent, automatic popup on | Begin the relevant system request chain once for this fresh launch, then start on approval. |
| Default needs consent, automatic popup off | Show what is needed inline and wait for an explicit start/grant action. |
| Everything playing, root enabled, authorization valid and raw backend working | Start root capture directly. No MediaProjection, redundant RECORD_AUDIO request for playback-only root capture, or app-owned confirmation. |
| Root unavailable, grant lost, helper fails, or route unsupported | Report root failure with retry/explicit standard-capture choice. Do not silently launch projection permission UI or change source. |
| User denies/cancels permission or changes source | Stop the startup chain for this launch. No repeated popup loop or stale callback takeover. |

- Root-manager authorization is separate from Android capture consent. First-time/revoked root may require the root manager's own prompt, which the app cannot promise to suppress. During normal authorized operation add no app-owned resistance. Do not retrigger authorization repeatedly on failure.
- Root playback does not grant unrelated microphone/Bluetooth/overlay access. If optional mic mixing is already authorized, include it directly. If it is not authorized on an otherwise working root startup, start playback-only, show mic unavailable inline, and provide explicit mic enablement without an unsolicited popup. Required overlay access remains an honest platform requirement.
- Preserve default choice across update and process death, never permission tokens. Cancellation is respected for the current launch. A later fresh launch can retry according to the user's setting, subject to Android denial rules.
- Explain in settings that automatic startup can activate recording or begin the configured playback/relay connection. Retain platform recording indicators, foreground-service notification obligations, and a visible stop action. Prompt-free is not covert capture.

### 15. Integrate, document, and deliver

- Run requirement-linked tests after each reversible change and commit only owned work with receipts. Update specs and feedback when implementation findings refine the contracts.
- Integrate combinations: root/standard capture plus mic, HUD plus hold/black, HDR plus transparency/paused frames, randomized light plus hold/resume, presets plus held frames/remote geometry, signal check plus partial-input failure, and startup plus surviving services/permission return.
- Update README, manual, privacy/distribution guidance, settings archive docs, source recovery text, and measured device limitations.
- Build through the Gradle wrapper and install through `dev/pm3` with explicit device serials. Verify installed APK hash, signer, package, version, exact source commit, and same-package settings survival.
- Run existing unit/lint/engine checks, locked Cargo tests, revised permission/boundary gates, and developer/release script tests. A release claim requires the existing exact-source, signing, provenance, packaging, and installed-artifact gates.
- Preserve rollback points and a final requirement-to-evidence summary. Do not mark an unavailable hardware-dependent feature as delivered merely because its UI has a fallback.
- No push, store submission, publication, destructive operation, or unrelated machine/service change follows implicitly. Respect existing explicit release gates.

## Critique protocol (R15)

**Reviewer: GPT Astra (`gpt-6-astra`) only, independent of the implementer.** Ben explicitly selected this model on 2026-09-07. Preferred known route is `openai-oauth:gpt-6-astra`, subject to live catalog verification. Do not substitute Grok or another model for critique without Ben's approval.

- Review at the end of each numbered implementation section, not every small edit. Instrument presets, signal check, settings, and themes each receive a section-level critique. Final integration receives its own critique.
- The critic reads source, tests, and real receipts but does not edit the work it scores. The coordinator/implementer addresses findings and obtains the next critique.
- Before the first worker in an execution session, follow `/home/ben/.jcode/SWARM-ROUTES.md` and the attention-beacon routing ritual. Astra is already the chosen reviewer model. Confirm supported effort and any implementation-worker mapping rather than reopening that model choice. Proposed review effort is `high`, with `xhigh`/`max` only when supported and confirmed.
- Verify exact model/provider/effort support and respect the harness pin. If Astra is unavailable, record the precise review blocker. Continue independent work where useful, but do not invent a review score or silently replace the reviewer.
- Use at most two live workers by default. The root owns shared-state changes, device actions, integration, verification, and cleanup. No recursive/unbounded worker expansion.
- Judge originating intent against the living-instrument vision, active specs, Ben's latest requests, and this plan. New approved behavior supersedes old root/overlay exclusions. Ask whether the feature strengthens a truthful, immediate, private, beam-first instrument.
- Give one overall 1–10 rating supported by intent fidelity, functional accuracy, UX/accessibility, and lifecycle/performance/privacy evidence where applicable. Mark inapplicable dimensions honestly. Scores are not arithmetic substitutes for acceptance.
- Anchors: 1–3 broken or substantially misses intent, 4–5 partial with major gaps, 6–7 mostly works with material gaps, 8 meets intent with verified main paths and minor polish remaining, 9 has strong edge/integration evidence, 10 has no material gap found within the tested scope. A 10 is not proof of perfection.
- Each review names the largest intent mismatch, exact affected requirement/source, observed evidence, and prioritized corrective work. Compilation alone cannot justify functional success.
- Run **up to four critique rounds total** per section. If a round is below 8, address findings and rerun checks before the next review. Stop early at 8 or higher. Four means four total review passes, not an initial pass plus four retries.
- After round four below 8, **continue to the next section**, retaining the score, attempted fixes, unresolved findings, and evidence. Do not reset an endless loop or quietly mark full acceptance.
- Moving on does not waive correctness/privacy/release gates or required device evidence. Final integration reports all carried gaps and separates implementation progress from accepted delivery.
- During execution, save compact receipts under `docs/plans/mobile-expansion/critiques/`: section, commit, Astra route/effort, round, score, intent comparison, evidence, prioritized findings, and disposition. Keep this root file the canonical plan, with one execution ledger linked from it when created.

## Validation

These are future implementation checks, not claims that this planning session ran device tests.

| Requirement | Required observable evidence |
|---|---|
| R01 Root | Real PCM and visible scope from ordinary and opted-out test apps, audible output preserved, speaker/wired/Bluetooth routes, denied/revoked root, helper crash, repeat start/stop, private IPC, unchanged system/boot files. |
| R02 HUD | Real overlay above other apps, actual transparency versus solid black, drag/resize/touch isolation, orientation, PiP arbitration, return-to-app, lock/revocation/dismissal, no duplicate source/surface or orphan service. |
| R03 Manual | Every approved feature maps to a chapter/recovery path. Search/navigation, at least 24 meme entries/responses, visible turtle smile/tail, preserved bestiary discovery, no theme/large-font clipping. |
| R04 Motion/layout | Portrait/landscape/multi-window screenshots and interaction tests, adequate targets, unchanged opening gesture fixtures, reduced motion, frame/jank measurements, zero ongoing hidden animation callbacks. |
| R05 HDR | Actual FP16/scRGB surface and compositor evidence, correct linear output, SDR regression images, real-panel SDR/HDR comparison, hold/black and transparent-HUD combinations. Screenshots alone do not prove luminance. |
| R06 Brightness | Auto brightness enabled during ambient-light and brightness-slider changes, pause/no-source, recreation/resume, exit/crash, unchanged global settings, no HUD takeover of another app's brightness. Record thermal/system limits. |
| R07 Randomness | Seeded tests prove random color ownership, interval bounds, draw-at-boundary behavior, TIMER/TRACK precedence, rapid-cycle guard through imports/toggles, and predictable hold/resume. |
| R08 Six colors | Full six-slot edit/select/delete/shuffle flow, membership/no-repeat tests, 0/1/6-selected behavior, old three-color archive migration, six-color round trip, malformed input rejection, unselected colors preserved. |
| R09 Mic/mix | Actual routed accessory and physical-input proof, mono/rate conversion, unplug/reconnect, denial/privacy mute, route restoration. Two distinguishable signals in the mixed scope, mic-off removes only mic, bounded drift/latency and clipping, no speaker feedback. |
| R10 Dismissal | Opening fixtures unchanged. Ordinary scroll/top flings/short accidental motion do not close settings. Deliberate slow and qualified fast pulls close. Reversal, cancellation, sliders, expansion, Back, close, and other sheets remain correct. |
| R11 Expandable settings | Multiple sections expand independently, truthful live summaries, no setting mutation on expansion, scroll/focus/state survive rotation/theme changes, collapsed controls leave accessibility focus, large fonts remain readable. |
| R12 Themes | Four meaning-contract reviews, text/control contrast including Glass over moving content, true-black AMOLED, all old room/override migrations, appearance preset CRUD/reset/archive round trips, no source/permission side effects. |
| R13 Pause | HOLD is default on clean install and migration. Last presented frame remains stable without decay while paused, inspect pan/zoom/reset changes no live tuning, BLACK clears correctly, switch modes while paused, resume current timeline without backlog, truthful controllable/live-source labels, source-stop invalidation, rotation/HUD/PiP/HDR recreation. |
| R14 Startup | Fresh configured launch reaches the source without picker. One consent chain when enabled, no chain when disabled, no repeat on resume/rotation/permission return. Already-authorized root starts with zero app/projection dialogs. Denial, revocation, root failure, missing target/input, surviving service, source-change races, and inert imports follow the decision table. |
| R15 Critique | Every substantial section has an independent GPT Astra evidence-backed rating or explicit route blocker. Sub-8 work gets corrective review up to four total rounds. Remaining gaps carry forward honestly. |
| R16 Instrument presets | Named create/save/update/duplicate/rename/delete/recall, coherent application and undo, modified-state indication, curated setups match their purpose, six-color/random guard preservation, versioned round trips and malformed/duplicate imports, no source/theme/permission side effects, held-image preservation and truthful remote-owned controls. |
| R17 Signal check | Real owner/route/rate/channel observations, measured flow/silence/clipping versus unavailable data, distinct consent/failure/paused-display states, mixed-input health, stale-callback rejection, existing recovery actions, no permission/source side effect on open, bounded visible-only refresh and no stored audio/behavior history. |
| Integration | Existing source/gesture/lifecycle regression tests, Gradle unit/lint/build/checkEngine, locked Cargo tests, revised boundary/release checks, exact installed APK verification, same-package update/defaults/archive survival. |

Use a rooted target for root acceptance, the non-root Galaxy S25 for ordinary-path regression, API 29 for version fallbacks, and actual Bluetooth/USB accessories for route claims. Confirm live availability before activating anything. Current handset capabilities cannot be inferred from old receipts.

Run sustained HUD + capture + mic + random-light sessions, with periodic pause/inspect/resume and route changes. Repeat HDR and brightness combinations where supported. Measure audio continuity, memory growth, frame pacing, thermal behavior, battery implications, and resource teardown.

## Decisions, proposals, and remaining uncertainty

### Settled interpretation

- Settings opening remains unchanged. Only downward dismissal gets more resistance.
- Terminal means a comprehensive terminal-style manual/bestiary, not a shell.
- Six custom beams means six color slots, not complete renderer presets.
- Random generated color, shuffled saved-color order, and random TIMER duration are separate controls with explicit ownership.
- Freeze/inspect is the default pause presentation, not a new requirement to keep local audio playing when paused. BLACK is the user-selectable alternative.
- Root mode is a backend for everything playing. Enabling it does not override an explicitly selected microphone/file/relay default. With default everything playing and working authorized root, fresh startup is direct and prompt-free for that playback-only capture path.
- Automatic permission popup controls missing-consent requests, not whether an already-authorized default can start. Fresh install with no configured default remains quiet.
- Root grants do not grant microphone, overlay, or Bluetooth permissions. Root-manager first authorization/revocation and platform special-access dialogs cannot be promised away.
- Themes combine four curated presets with one editor. Exact colors and gesture constants are device-validated design work. Appearance presets are approved and distinct from the instrument-preset proposal.
- Brightness pin affects only the foreground full app. HDR, brightness pin, and beam energy remain distinct.

### Approved additions and remaining proposals

- **Instrument presets, approved at 23:15 UTC:** saved complete beam setups such as mode, geometry, gain, focus, glow, and colors. Recall “Clean XY” without changing UI theme or source. Section 7 owns the full feature and its portable data contract.
- **Signal check, approved at 23:15 UTC:** an on-demand source-health view showing actual input/route, sample rate/channels, clipping, and whether a dark beam means silence, lost source, denied capture, or an intentionally paused display. Section 8 owns this dedicated diagnostic surface.
- Calibration playground, image export, and session timer also remain suggestions only. Do not let a critic introduce them as missing requirements.

### Hardware and distribution uncertainties

- Whether the rooted OEM permits non-diverting raw capture under the fixed no-system/boot-write constraints, including opted-out/offloaded audio.
- Which Bluetooth microphones are physically available, what formats they support, and whether their input route changes playback quality.
- Whether the target compositor supports FP16 HDR, transparency, and held-frame handoff together with useful luminance headroom.
- The supported foreground-service declaration/distribution treatment for root and overlay behavior requires measured platform checks and current store review. Do not assert expanded artifacts are Play-safe without evidence.

**Valid blocked outcome:** identify the exact capability that failed, commands/observations proving it, alternatives tested, useful partial result, and smallest next step. Keep that requirement open, continue independent work, and never convert missing evidence into a completion claim.
