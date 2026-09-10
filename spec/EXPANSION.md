# Expanded mobile instrument contracts

spec-version: mobile-expansion-2
drift: 17
compile-count: 0

This source implements Ben's accepted R01–R17 direction. The canonical execution order remains in `MOBILE-EXPANSION-PLAN.md`. The execution ledger records implementation, real acceptance, and critiques separately. Drift counts unaccepted R requirements, not test failures. Do not lower it from a build or review score alone.

## Next-release override: deferred root capture, 2026-09-09

Ben deferred root capture from the next release and requested an encouraging stub.
This overrides operational root activation below, not the retained research contract.
The hidden bestiary offers ROOT CAPTURE · COMING LATER as an informational button.
It opens local, dismissible help only. It does not authorize, open a root manager,
change source selection, write root preferences, or start a helper.

A fixed product policy disables root selection and authorization for both app build types.
Legacy enabled/acknowledged preferences remain inert and are not erased.
Ordinary Everything playing and explicit standard capture keep real Android consent.
Direct product root-service starts fail with a deferred status before helper creation.
Only the existing debug-only controlled check entry remains available for research.
No root research, protocol evidence, or cleanup behavior is removed.

Acceptance requires tests for all legacy flag combinations, inert button callbacks,
authorization before side effects, and service rejection before helper creation.
Host/source assertions are not Android execution evidence. Root stereo, SoundCloud,
audibility, and latency remain deferred and unaccepted.

## Vision

The same truthful beam moves between the full app, PiP, and a floating HUD. Ben can inspect a paused image, recall an instrument setup, and understand a dark beam without turning the instrument into a dashboard. Added power remains opt-in, visible while active, private, and recoverable.

## Ownership

- One logical source publishes visual samples. Local playback retains the existing sample-locked audible path.
- Standard and root capture are alternative everything-playing backends, not separate product flavors.
- Playback-plus-microphone has two readers, bounded input buffers, and one mixer producer. Mixing changes visualization only.
- Stop acknowledgement precedes source replacement. Reader, task, source, and helper generations reject retired callbacks.
- Root capture preserves real stereo and minimizes added latency. Measure against normal playback on the same output and verify beam-to-audible alignment. Timestamp or buffer estimates must be labeled separately from end-to-end measurements. Do not hide delay through sample drops or claim reconstructed rate creates missing bandwidth.
- One active presentation surface belongs to full app, PiP, or HUD. Surface destruction rechecks owner/generation before touching the native surface.
- Source transport, user display pause, and lifecycle render suspension are separate states.
- A surviving real service can be rebound. Activity recreation does not create a new source, projection token, or permission chain.

## Settings and migration

Use direct typed persistence rather than a command bus. Keep the accepted PiP, linger, rotation, gain, grid, beam-energy, and other defaults unless explicitly changed here.

Existing `hud_mode` controls in-app information presentation and retains its accepted AUTO default. The new floating HUD is a distinct off-by-default feature. Do not repurpose `hud_mode` or treat theme glass as overlay access.

| Setting | Default and domain | Storage |
|---|---|---|
| Pause presentation | HOLD or BLACK, default HOLD | Portable |
| Default source | NONE until explicitly configured | Portable source kind, private target stays local |
| Automatic permission popup | Off | Portable, imported activation needs one local confirmation |
| Root capture | Hidden and off | Local opt-in, never an imported authorization |
| Floating HUD | Off | Preference only, not a grant or running service |
| HUD background | Explicit SOLID or TRANSPARENT | Portable |
| Include microphone | Off | Preference only, not a recording grant |
| Microphone input | Built-in by default, explicit accessory when selected | Private device identity stays local |
| Mix levels | Independently bounded playback and mic visualization levels | Portable, no speaker-volume effect |
| HDR | Off | Requested preference, active capability is runtime-only |
| Foreground brightness pin | Off; Boolean `pin_screen_brightness` | Schema /2 only; no global brightness write |
| Custom colors | Up to six RGB slots, independent selected membership | Portable |
| Color order | Ordered unless shuffle is enabled | Portable |
| Generated color / random interval | Independent off-by-default modes | Portable |
| Cycle | Preserve three-second TIMER default, 0.1–60 seconds | Portable |
| Appearance | AMOLED on clean install, preserve existing saved look | Portable |
| Instrument presets | Named explicit beam-setting records | Portable and versioned |
| Held image / inspection | No persisted image or transform | Transient bounded memory |
| Signal check | On demand, closed initially | Transient observations only |

The expanded archive uses `phosphor.settings/2` and retains a strict `phosphor.settings/1` reader. Preserve the existing one-MiB input limit, checksum contract, omitted-key semantics, and whole-payload validation. Old three-color arrays migrate losslessly. Count, RGB components, and selected membership validate together.

Imports never start capture, request root, activate HUD, open a permission dialog, or dial a relay. They carry no grants, projection tokens, runtime health, private device/host/media identities, or inspection images. Imported automatic-start behavior requires one local confirmation before its first activation, not repeated confirmations after local approval.

Before implementing each schema owner, record its exact field names, legal ranges, migration, and test fixtures here. Do not serialize all preferences into presets or copy runtime state into archives.

## Root playback capture [R01]

Ben's04:17:59 UTC clarification keeps true any-app audio, specifically SoundCloud, as the acceptance target. The current16kHz mono path is a measured implementation limit, not a reduced product goal or proof that another app is unsupported. Test actual app behavior and investigate fuller-fidelity alternatives without system/vendor/boot/vbmeta writes. Preserve actual channel and format truth. Do not accept duplicated mono as original stereo or infer universal capture from root credentials.

At04:21:19 Ben explicitly required real stereo and meaningful sample-rate fidelity. SoundCloud, reported silent with standard capture, is the root smoke test. Existing48/96/192kHz settings are beam reconstruction factors over48kHz input. Preserve their operation, distinguish requested and actual capture rates, and negotiate higher input fidelity where supported rather than relabeling upsampled16kHz mono as success. Verify independent left/right content and audible output through the real app path.

The hidden bestiary reveals the root switch. Ordinary startup with root off neither probes nor requests root. Initial enablement explains existing root-manager authorization, then verifies the actual supported grant without fabricating a manager prompt.

The exact R01 product contract is [section-02-root-product.md](../docs/plans/mobile-expansion/section-02-root-product.md). Local runtime keys `root_capture_enabled` and `root_capture_profile_ack` are Boolean false by default and excluded from archives and backup. Authorization and availability remain transient. Everything playing selects enabled root directly without RECORD_AUDIO or MediaProjection. Standard capture is an explicit alternative. Failures never silently fall back.

The R13 [root producing-read correction](../docs/plans/mobile-expansion/section-05-correction-03.md) adds bounded epoch controls, acknowledgement and tagged mode2/3 PCM. It preserves recorder and policy lifetime. Original-owner native publication and epoch-scoped normalization reject queued old reads without pretending to prove buffered-source age. Modes4/5 stereo behavior remains unchanged.

The [application-present correction](../docs/plans/mobile-expansion/section-05-correction-04.md) separates nonblocking LIVE requests from current-token present completion. Pending is visible until the sole render owner acknowledges the new application image. This does not certify buffered source age or physical scanout.

Protocol2 fixes modes, build/package identity, positive generations, PCM16 blocks, sequence and format validation, renewable lease/watchdog deadlines, and confirmed retirement before replacement. The real nonexported RootCaptureService uses specialUse on API34+, sharing CaptureService retirement and metadata ownership without claiming projection ownership. Actual input remains16kHz mono, normalized to48kHz duplicated-mono float. No stereo recovery is claimed.

Package and version one fixed-purpose audio helper. Its framework DEX lives sealed and read-only in private app data. Its native bootstrap executes from the installed, app-nonwritable native-library directory, not writable app home. Android's API 29 execution restriction makes this distinction necessary. Use private PCM/status IPC. Do not expose arbitrary shell execution, control endpoints, unsolicited networking, or audio recordings. Treat `/system`, including `/system/bin`, `/vendor`, and every boot/vbmeta partition as read-only. This includes raw block devices, aliases and both slots. Reads are allowed. Do not patch boot, flash, remount, disable SELinux, install a persistent root service, or use LSPosed. Keep task writes in installed app code, private app data and explicitly owned test artifacts.

The normal app never becomes root. A session-scoped native supervisor acquires only the original app UID's existing supported provider grant. It launches one fixed framework helper, owns privileged termination and reap, and exits with that session. Neither process daemonizes. Match exact protocol/build/identity, bound all pipes and deadlines, and stop on owner EOF, heartbeat loss, revocation or helper failure. Cleanup uncertainty prevents replacement. Do not rely on the unprivileged app killing an already-root child.

The initial KernelSU trial requires the already-observed Default profile with inherited namespaces. Provider grant itself applies the profile's namespace choice. Do not change the profile or infer no-remount merely from Phosphor containing no mount call. Unsupported provider/profile state is a concrete compatibility failure, not a reason to modify security configuration.

Prefer AudioPolicy loopback-with-render. If REMOTE_SUBMIX is needed, prove local monitoring, no feedback, and complete route restoration. Root success and manager installation are not audio acceptance. Test real PCM and audible output on the target, including ordinary and opted-out sources. Label unsupported/offloaded/protected/OEM cases without claiming universal capture.

The inspected API 29–36 privileged playback flag permits at most 16 kHz mono public linear PCM with at most two bytes per sample. The initial implementation uses PCM16. It can bypass projection/manifest opt-out, but not `NO_SYSTEM_CAPTURE` or `ALLOW_CAPTURE_BY_NONE`. Report actual input format separately from normalized 48 kHz stereo transport. Duplicated mono is not recovered stereo. Use the registered tagged mix with explicit `LOOP_BACK | RENDER`, not an untagged global submix. Initial tests match only the original Phosphor UID and controlled MEDIA/GAME fixtures, never actual calls or private messages. Suspend testing on communication mode.

Model denial, revocation, helper death, unsupported routing, cancellation, and bounded shutdown. A root session declares its actual foreground-service role rather than claiming projection consent. A healthy already-authorized root playback-only start needs no projection or redundant app recording dialog. Failure offers retry or an explicit standard-capture choice, never silent fallback.

### Authorization feasibility seam

The existing debug-only self-test receiver accepts the explicit `dev.phosphor.mobil3.ROOT_AUTH_PROBE` action. Restrict the receiver to callers holding Android `DUMP`. Unknown actions do nothing. Production declares neither the receiver nor this action. Ordinary activity startup never calls the probe.

The probe runs in the normal package process, not `run-as`. It tries only fixed standard `su` locations with the fixed command `/system/bin/id -u`. There is no command/path extra, shell console, audio capture, permission mutation, or settings write. Stop after the first successfully launched candidate, even if authorization fails. Missing executable candidates may advance to the next fixed location. A root claim requires exit zero, complete bounded output equal to `0`, and confirmed child/reader termination. A timeout, oversized output, malformed identity, or incomplete cleanup is an explicit failure, never authorization evidence.

Write one atomic private `root-authorization.json` receipt with the developer envelope `{status,tool,version,ts}`, package/build/app UID, attempted fixed locations, elapsed time, and observed outcome. Errors carry a concrete `fix`. Allow one probe at a time. Bound the launched command to three seconds, each output to four KiB, and teardown to one second. This feasibility receipt proves only command authorization. AudioPolicy registration, PCM, audible output, opt-out capture, service ownership, and user opt-in remain separate acceptance gates.

The target's measured standard-su failure permits a separate debug-only `dev.phosphor.mobil3.KSU_AUTH_PROBE` action. It is not an automatic fallback or a product dependency on the altered logcat path. The coordinator independently identified the existing `/system/bin/logcat` as KernelSU 3.2.5 through its version, help and kernel-info commands, and checked the matching upstream `cli.rs`, `su.rs` and `ksucalls.rs`. First require its exact `ksud 3.2.5` version output under the app UID. Then invoke only `debug su`, without global-mount mode, and supply only `exec /system/bin/id -u` through a closed private stdin pipe. The upstream command requests the current UID's existing KernelSU grant and execs a shell. No grant/profile change, namespace switch, install, module operation or system write is permitted. Each stage retains the same output/runtime/cleanup bounds. A version or authorization failure stops. Report the provider explicitly. This compatibility probe cannot establish a general shipping backend.

## Microphone and mixing [R09]

The standard-only implementation contract is [section-03-r09-contract.md](../docs/plans/mobile-expansion/section-03-r09-contract.md). It defines service, route, clock, epoch, settings and failure ownership. Root remains deferred.

Enumerate available built-in, wired, USB, SCO, and BLE inputs. Negotiate supported formats and convert mono/rates to the native stereo contract. Verify the actual routed device after start. Contextual Bluetooth permissions and communication routing cannot become unconditional startup actions.

Explain quality/output changes before initial Bluetooth activation. Restore temporary audio mode/routes after stop. Losing an explicitly selected device reports that device unavailable, not a silent switch to a different microphone.

Ongoing microphone input has a real service owner, recording indication, correct FGS type/permission, and while-in-use startup behavior. The composite mixer aligns timestamps, corrects rate/drift, bounds latency/buffering, and protects against clipping. A failed optional mic leaves healthy playback capture flowing with an explicit partial-input state. Mixing never feeds the speaker.

## Presentation, HUD, pause, HDR, and brightness [R02, R05, R06, R13]

### Floating HUD [R02]

The user-started HUD provides drag, resize, close, return-to-app, and compact source/transport actions. `floating_hud_enabled` defaults false and is preference only. `floating_hud_background` defaults SOLID and accepts SOLID or TRANSPARENT. Width and height preferences default 320 dp, accept 240–640 dp, and clamp to usable display bounds. Existing `hud_mode` remains unchanged. Imports and restoration never show a HUD or open consent, even when the imported preference is enabled.

An explicit visible-activity Show action requests overlay access once when absent. Return from consent never automatically shows or repeats consent. After successful window addition, one shared SurfaceHost transfers a generation lease from the activity/PiP to FloatingHudService. Stale change/destroy callbacks cannot detach a successor. Pending or visible HUD takes precedence over PiP. Lock, revocation, dismissal, task removal and partial failure retire only owned presentation, listeners and controller. A visible activity reclaims presentation. No automatic service restart occurs.

Native retirement waits for actual surface/window drop, even beyond the attach deadline. Cancelled creation cannot publish. Channel loss blocks successor ownership. Driver stalls can delay this barrier. Stable local queue/item IDs ignore metadata enrichment. Remote/capture placeholder IDs use documented label fallback. Pending controller futures retire exactly once through Media3 releaseFuture.

TRANSPARENT requires translucent SurfaceView pixels, a supported Vulkan PreMultiplied swapchain, and shared-renderer scope_alpha=0. SOLID uses opaque pixels and scope_alpha=1. Report requested versus active mode. Unsupported premultiplied alpha falls back to opaque SOLID with a reason. Fading an opaque rectangle is not transparency. Hidden surfaces/chrome stop render and animation work. Size and density update without changing tuning or source.

HUD uses the existing MediaSession and acknowledged source capabilities for transport. Source selection and new permission-bound input starts return to the visible app. It creates no audio reader. Until R09 supplies a microphone service owner, an active activity-owned microphone prevents HUD transfer without stopping or seizing that reader. Close leaves existing service sources intact. Task removal obeys existing source/linger policy. Keep outside touches usable and respect protected-screen restrictions. Detailed order and checks live in `docs/plans/mobile-expansion/section-04-hud-contract.md`.

### Pause [R13]

HOLD is the portable `pause_display` default. BLACK is the alternative. Lifecycle suspension, intentional display pause, and observed transport remain separate authorities. HOLD pins the last completed application present submission before controllable transport retirement. Physical panel scanout is not observable through this API.

The public shared `RetainedFrame` path stores the exact GPU energy and frozen composite uniforms used for that submission. It does not reconstruct beams from samples or read pixels to the CPU. Acquisition or allocation failure preserves committed history. Pinned textures remain immutable and live outside surface ownership and destructive resize. Checked dimensions and bytes bound a committed/candidate/pinned set. The detailed mechanism and checks live in `docs/plans/mobile-expansion/section-05-hold-contract.md`.

Freeze deposited energy, decay, displayed cycle, geometry and appearance. Destination-owned scope_alpha recomposes frozen beam/grid/theme with true premultiplied transparency on supported HUD surfaces. It never fades an opaque retained rectangle. Original extent, format and SDR transfer provenance remain attached to the frame. Future HDR conversion belongs to R05.

Pan/zoom/reset affect transient inspection only, with aspect-preserving fit and bounded image addressing. Preserve settings-opening gestures. Live uncontrollable inputs show `display held · source live`. Local pause still pauses audio. Common transport owners pin with a short CPU ownership operation before app, notification or earbud pause. No driver wait delays urgent audible pause. External commands still require real capability and acknowledgement. Unsolicited external PAUSED pins at the earliest authoritative observation, which cannot prove a frame before an unseen external stop. Silence, buffering and errors do not create intentional pause.

BLACK clears the scope once per dirty presentation, including opaque black on transparent HUD. Controls remain available. Switching HOLD/BLACK retains image and inspection without changing transport. Rotation and app/PiP/HUD transfers preserve bounded history. Process death or absent history shows black and `no held frame`. Source stop/replacement invalidates history. Resume clears only stale visual ingress and rebases display timing, never seeking or flushing audible local playback. Unchanged HOLD/BLACK waits for actual dirty events, not geometry packets. Hidden surfaces do no GPU work.

App AudioRecord capture and mic retain a runtime visual epoch sampled before each read with the whole returned batch. Native publication validates that epoch, active state and the original producer token under the visual ring lock. Source replacement invalidates the token independently of resume. This proves app read ordering, not AudioFlinger sample time. Root PCM requires producer-coordinated epoch metadata and acknowledgement, not an app receipt timestamp. The bounded capture-fence contract records this still-open root requirement without narrowing canonical R13 acceptance.

### HDR and brightness

HDR requires real FP16 linear-scRGB presentation and compositor evidence. Linear output bypasses manual SDR gamma encoding. Keep black, controlled emission, readable SDR chrome, and the unchanged SDR path. Reconfigure safely across owner/display changes and HDR/SDR held-image transfers. Report requested versus active HDR and a concrete fallback reason. Advertised capabilities and screenshots alone do not prove panel luminance or transparent-HDR support.

Brightness pin uses only the full foreground app window's `screenBrightness=1.0` and keep-awake flag, including pause/no-source. The exact focused-foreground condition, event ordering, and schema /2 key are in [section13](../docs/plans/mobile-expansion/section-13-brightness-contract.md). Restore `BRIGHTNESS_OVERRIDE_NONE` outside that ownership or when disabled. Never change global brightness/auto-brightness or brighten another app through HUD/PiP. Respect thermal, battery, dimming, and static-image exposure limits. Do not poll to fight the system.

## Light and instrument presets [R07, R08, R16]

The exact R07/R08 field, migration, publication, timing and verification contract is [section-06-color-contract.md](../docs/plans/mobile-expansion/section-06-color-contract.md). Saved storage is zero to six RGB triples with independent membership, not a selected prefix. The coordinator-owned decisions there preserve legacy values and the runtime-only rapid-cycle acknowledgement. Implementation must validate the whole effective tuple before persistence and native activation.

[Correction 01](../docs/plans/mobile-expansion/section-06-correction-01.md) requires one UI write owner, truthful rollback, idempotent publication, exact deletion lineage and complete-tuple repair. Its original independent critique remains immutable.
[Correction 02](../docs/plans/mobile-expansion/section-06-correction-02.md) preserves active color state when inactive saved storage changes. Exact unselected deletion remaps identities and bag positions without fabricating a TRACK event.

Each saved color can be edited or removed independently from selection. One selected color is solid. Zero selected colors returns to explicit preset mode. Shuffle uses selected saved slots without immediate repetition when at least two distinct slots are selected.

Generated colors, saved-slot order, and random TIMER duration are separate controls. Generated color owns color selection while enabled but retains the inactive saved order. Rust owns the clock and random decisions at leg/track boundaries, never per frame. TIMER interpolates, TRACK steps, and interval controls are inactive in TRACK. Preserve the rapid-cycle acknowledgement across range edits, imports, presets, and mode changes.

Instrument presets include enumerated mode, geometry, gain/auto-gain, focus, beam energy, glow, color slots/selection, cycle/randomization, and related beam settings. The exact version1 field set, curated values, cancellation transaction, record/document bounds and checks are in [section-07-instrument-contract.md](../docs/plans/mobile-expansion/section-07-instrument-contract.md), authored before runtime implementation. They exclude source/transport, mic routing/mixing, grants, root/startup, private targets, appearance, HDR, brightness, and inspection transforms. Use versioned records rather than arbitrary preference maps.

Provide create/save/update/duplicate/rename/delete and explicit apply. Editing current tuning does not mutate saved records. Show divergence from a recalled setup. Validate a whole snapshot before applying it and retain immediate undo. Failure cannot partially change the instrument. Import is inert and handles duplicate names explicitly. Curated setups have validated, restrained defaults. HOLD keeps its image while a preset changes the live setup used on resume. Remote-owned controls remain honestly unavailable unless the existing protocol supports them.

## Signal check [R17]

Opening signal check observes existing owners without starting readers, requesting permissions, changing source, or dialing a relay. Show selected/actual source/backend, requested/routed mic, negotiated rate/channels/format, per-input contribution, measured level/peak/clipping, sample freshness, and reader/link state. The pre-implementation [observation contract](../docs/plans/mobile-expansion/section-08-signal-check-contract.md) defines provenance, status precedence, non-destructive measurement and visible-only refresh. Owner adapter fields remain evidence-dependent.

Distinguish missing measurement from measured zero, consent/startup from flow, silence from stall, and intentional display pause from transport pause. Never infer DRM or opt-out from silence alone. Existing retry/grant/route/source actions provide recovery. Refresh at a bounded visible-only UI rate, with no audio/behavior history.

Worked example: `Everything playing › root · 16 kHz mono input · 48 kHz duplicated-mono transport · samples arriving · display held`. Every numerical label must come from actual negotiation or measurement.

## Settings, themes, motion, and manual [R03, R04, R10, R11, R12]

Opening gestures remain unchanged. Downward dismissal has progressive resistance and minimum deliberate travel before a fast flick qualifies. Only intentional header/top-boundary dragging arms dismissal. Leftover flings, programmatic scroll, sliders, and expansion animations do not. Preserve slow accessible dismissal, reversal/cancellation, Back/close, and other sheets.

Expandable full-width settings rows use meaningful glyphs, headings, live summaries, and chevrons. Group Signal & Startup, Beam & Light, Display & HUD, Motion & Performance, Appearance, and About & Manual. Multiple sections can remain open. Preserve scroll, header anchoring, expansion, and focus across theme/rotation. Collapsed controls leave accessibility focus.

Light, Dark, Glass, and AMOLED are curated appearances over one model/editor, not separate UIs. Preserve every legacy room and explicit override as an equivalent saved appearance. Appearance CRUD/reset affects no source or beam setting. Keep AMOLED clean defaults and true black. Glass uses legible backplates and an opaque fallback when needed. Theme glass is not HUD transparency.

Use flat aligned rows, sharp forms, hairline separators, meaningful depth, and beam-first layouts. Provide at least 48 dp targets, 4.5:1 normal-text contrast, and 3:1 large-text/essential-boundary contrast. Check portrait, landscape, multi-window, HUD, and large fonts. Animation depicts actual state and obeys lifecycle, surface/component visibility, and reduced motion.

The local terminal-style manual is indexed/searchable and covers every source, feature, consent, and recovery. Preserve five-tap discovery and bestiary characters. The turtle has a legible smile and tail. Include at least 24 distinct meme/bestiary entries or contextual responses. No shell, remote feed, telemetry, or administration protocol is added.

## Default-source startup [R14]

A configured default is not last-used source. A fresh launch is a new user app/task startup, including after process death, not resume, rotation, unlock, permission return, picker return, or HUD handoff.

| Condition | Behavior |
|---|---|
| No configured default | No automatic source, access request, root probe, or network connection |
| Real source owner survives | Rebind without restart or new projection token |
| Default authorized | Start directly regardless of automatic-popup setting |
| Missing required consent, popup off | Show inline grant/start action |
| Missing required consent, popup on | One serialized relevant request chain after Activity visibility |
| Healthy authorized root everything-playing default | Direct root start without projection or redundant recording dialogs |
| Root failure | Explicit error, retry, or standard-capture choice without silent fallback |
| Denial/cancellation/source replacement | Retire this launch chain and reject delayed callbacks |

Fence callbacks by task, launch, and source generation. Revalidate local file/folder/relay access without unsolicited pickers or random fallback hosts. Keep targets private. A surviving service remains the owner.

Root-manager consent cannot be fabricated or promised away. Optional ungranted mic mixing does not block healthy root playback: start playback-only and show mic unavailable with explicit enablement. Other required special access uses its real platform flow. Retain recording indicators, service obligations, and a visible stop action. Prompt-free is not covert capture.

## Acceptance and critique [R15]

`spec/ACCEPTANCE.md` and the execution ledger map every requirement to host and exact-device evidence. Each numbered implementation section receives an independent GPT Astra critique with route, effort, source identity, round, score, evidence, and disposition. Four rounds means four total reviews. A score of 8 ends corrective review early but never replaces missing functional/privacy/device evidence.

Blocked work states the exact bottleneck, evidence, alternatives tested, useful verified result, and smallest next step. Continue independent work without claiming the missing outcome.

Ben's04:56:09 fallback permits an encouraging root-ready surface if full-fidelity low-latency capture remains unresolved after bounded experiments. Its working action may verify actual root authorization, but must not pretend to start accepted stereo capture. Keep ordinary standard capture usable, preserve root-off inertness, name the remaining limitation, and retain the implementation evidence for future work. Continue the remaining expansion. This fallback is not evidence that stereo, SoundCloud or latency acceptance passed.


### U1 opt-in tactile console

Source contract: `docs/plans/mobile-expansion/visual-u1-contract.md`.
Implement an authored optional look_version=2 and console-only no-track key bed.
Version1 omits the field exactly. Saved looks, transport truth and gestures remain unchanged.
Fresh tactile default is deferred until a preinit meaningful-settings baseline is proven.
Parent owns full builds, device evidence and acceptance. No visual acceptance is implied.
