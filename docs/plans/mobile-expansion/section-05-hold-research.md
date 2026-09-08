# SECTION5 R13 HOLD / BLACK: immutable source-audit handoff

2026-09-08. Independent read-only audit. Requested route: openai-oauth:gpt-6-astra high. No workers, implementation, R15 score or approval.

## Source identity and actual checks

Mobile: `06f84e2eb7da46c758e9f8b388c3537e6c67905d`. Shared GPU: `7729990bb29f0167ef906d0fbdb44e1e91206955`. Both exact commit objects resolved successfully. Product source was inspected only through git show at these objects. No claim about shared worktree cleanliness or installed APK identity.

Governance read: machine `/home/ben/Dev/ClaudeWorkspace/AGENTS.md`, ben-context-standards, folder-management-user-preferences, pinned mobile docs/AGENTS.md, sibling AGENTS.md. Explicit no-edit/no-Python scope precluded ledger writes and Python prose linting.

Read-set, including targeted excerpts and symbol searches rather than a claim of exhaustive file review:
- Mobile canonical MOBILE-EXPANSION-PLAN.md Section5 lines141–153 and spec/EXPANSION.md R13 lines100–110, plus ownership/settings contracts.
- Mobile rust/src/{render.rs,engine.rs,jni_glue.rs,lib.rs}.
- app/src/main/kotlin/dev/phosphor/mobil3/{PhosphorNative.kt,MainActivity.kt,PlaybackService.kt,ui/ScopeModel.kt,ui/ScopeUiState.kt}.
- app/src/test/kotlin/dev/phosphor/mobil3/{PlaybackTruthTest.kt,CaptureMirrorPolicyTest.kt,LocalPlaybackPolicyTest.kt,BackgroundLifecyclePolicyTest.kt,PictureInPicturePolicyTest.kt,LocalOwnershipRegressionTest.kt,ui/ScopeUiStateTest.kt,ui/StagePinchScaleTest.kt}.
- Shared crates/phosphor-render-gpu/src/{lib.rs,shaders.wgsl}.

Every path above is identified by its repository commit hash. No changing implementation worktree files were read. Seven grouped finite source-shape checks passed: renderer outside surface ownership, blocking idle wait, attachment-only surface, bindable/noncopyable energy, destructive changed-size resize, transport-first toggle, and absent user-pause render barrier. These were shell assertions on pinned git-show text. They are not runtime tests. No Gradle, Cargo, build, device, GUI, ADB, audio, service, setting, root, network or Git mutation occurred. An initial malformed tool call and a computed-path scratch-write gate failed before execution.

## Answer and existing resource seams

Real GPU-only retention is feasible independently of the Android Surface and source transport. Current source does not implement HOLD. References below use R=mobile render.rs, E=engine.rs, J=jni_glue.rs, A=MainActivity, P=PlaybackService, G=shared renderer lib.rs, S=shared shaders.wgsl.

|Resource|Observed source and consequence|
|---|---|
|Device/surface lifetime|R177–191,229–233 keep Gpu and GpuRenderer outside Active. R374–377 drops only Active. Retained image belongs beside device/renderer, not inside Activity, SurfaceView or Active.|
|Surface teardown|J69–84 waits up to two seconds for render-thread destroy acknowledgement. Preserve that barrier and R02 owner/generation fencing. A stale destroy must not retire a successor surface.|
|Energy textures|G151–175: D2, one mip/sample, RENDER_ATTACHMENT and TEXTURE_BINDING only. G224–232 chooses Rg16Float or Rgba16Float fallback. G33–35 resources/current index are private. Binding is supported, raw copy is not permitted by existing usages. Float energy does not prove HDR output.|
|Mutation|G517–529,661–675 advance submits decay/deposit and flips current even with empty segments. S23–28 also subtracts a floor. advance([]) and high persistence are not HOLD.|
|Resize|G804–835 changed-size resize replaces both energy textures and resets current. Same-size returns. R324/368 call it on surface creation/change. Existing survival is not general rotation/resize retention.|
|Private output|G265–277 output_texture uses composite_format, RENDER_ATTACHMENT and COPY_SRC, not TEXTURE_BINDING. G735–759 uses it for offscreen composition. Mobile does not present that texture. It is private, not exposed by composite_submit, and resize does not resize it.|
|Public composite seam|G769–799 composite_into accepts a caller-owned compatible TextureView. G411–433 fixes its target format and blend=None. Its energy must match viewport dimensions. A different viewport is not a texture inspection transform.|
|Swapchain|R861–876 selects sRGB when offered and requests only RENDER_ATTACHMENT. Existing surface cannot be copied by assumption. COPY_SRC would need real capability/configuration validation.|
|Color/alpha|G214 sets hardware_encodes from format. S203–205 manually gamma-encodes otherwise. S213–221 outputs premultiplied alpha. Preserve format/transfer/alpha interpretation and avoid double encoding or premultiplication.|
|Presented identity|R687–770 advances BEFORE R774 acquires surface texture. Lost/Outdated/error can skip presentation after energy changed. R794–801 composites, submits and calls present. Current energy is not necessarily last presented energy.|

The inspected present() call establishes application submission, not physical scanout. No inspected API identifies the last image actually scanned onto the panel. That stronger meaning of fully presented remains unverified.

## Smallest public-API-supported mechanism

Own a committed last-presented composite texture on the render thread, outside Active. Tag it with source generation, presentation serial, original extent, format, transfer and alpha convention. Allocate with the compatible composite format and RENDER_ATTACHMENT | TEXTURE_BINDING, checking format support and allocation failure. No copy flags or CPU readback are needed for render-to-texture followed by a texture draw.

Acquire a surface before overwriting committed image content. Run existing advance exactly once for a live frame. Use existing composite_into to draw into the owned image, then a small new shared texture-presentation pass to draw that exact image into the surface. Publish the serial only when submitted to present(). Failed acquisition leaves the committed image intact. Retain the previous allocation through changed-size allocation until the new-size candidate is presented. Bound ownership to committed/candidate, not an image history.

Pause pins committed and stops writing it. HOLD samples it on explicit presentation invalidations. BLACK preserves it and issues an opaque black clear without normal composite. Resume releases the pin and rejoins live input. Inspection uses normalized image coordinates, aspect-preserving fit, bounded pan/zoom and clipped addressing. Reset changes only inspection. Keep uncovered-region clearing explicit.

This adds one full-size color texture and a live texture draw. Resize can temporarily need two color allocations. Nominal RGBA8/BGRA8 storage is 4*w*h bytes per image, excluding driver overhead. Set fixed count and checked byte limits against admitted dimensions. On allocation failure preserve the prior valid image, otherwise report no held frame. Exact S25 memory/performance limits are unmeasured.

This is a supported implementation seam, not existing functionality or accepted runtime evidence. The inspection draw is new. Do not fork the beam renderer or rewrite DSP/decay.

### Concrete alternatives and traps

Retained energy plus frozen composite parameters is possible through a new shared API because energy is already bindable. Retain owned textures/views and snapshot ALL composite parameters. Prevent ping-pong from writing the held texture by detaching/reallocating or protecting its slot. A retained handle alone does not freeze content. Add transformed energy sampling for inspection. This supports destination-specific alpha/transfer recomposition, but changes more shared internals than color retention.

Copying energy requires adding and validating COPY_SRC/COPY_DST on same-format, same-sample-count endpoints. Existing allocations cannot do this. It still requires frozen appearance and last-present identity. Freezing the whole GpuRenderer and compositing it again works only with unchanged dimensions/format, frozen uniforms and known presented energy. Current resize and advance-before-acquire defeat full R13.

Private offscreen output and swapchain copying are not exposed supported retention paths today. Screenshot/readback, last nonempty samples, reconstructed geometry and an empty-history fallback are not proof of rendered-image HOLD.

### Alpha and future HDR

An opaque retained full-app composite remains opaque when replayed into transparent HUD. Reducing whole-image alpha fades the beam and is not equivalent to transparent background. Decide whether held handoff preserves captured background or requires destination-owned background. The latter favors retained energy plus frozen parameters or deliberately factored emission. No current public API exposes those independently. Do not destroy retention during the R02 handoff to avoid this decision.

BLACK is unambiguous: RGBA=(0,0,0,1) in the scope region, even on transparent HUD, with controls separate. A black clear followed by ordinary composite reintroduces grid/beam/background and is not BLACK. Clear once per relevant surface/damage generation, not once forever across recreated swapchains.

R05 requires real FP16 linear-scRGB presentation and HDR/SDR held-image transfer. Keep that named requirement. Do not infer HDR from Rg16Float energy. Current pipeline format is fixed at creation and hardware_encodes=false invokes SDR gamma. Future HDR needs explicit transfer state, compatible pipelines, tested conversion and compositor evidence. An 8-bit retained image cannot recover unstored HDR emission. Transparent HDR is unverified, not approved or ruled out.

## Exact pause/transport hazard and needed ordering

A841–847 calls c.pause() first, or deckToggle without a controller. There is no retain request or acknowledgement. J176–181 calls deck::set_paused directly while J89–94 separately sends asynchronous lifecycle Paused. This is not an ordered transaction.

As transport stops producing samples, R539–548 drains input, R569–578 handles empty input, and R687–770 continues decay/deposit or eventually a resting point. Capturing after a later UI callback can therefore freeze a later empty/decayed image.

Capture sequence is concrete: P668–686 checks actual platform actions and sends play/pause. P481–488 later receives playback state. P588–610 publishes mirror and session extras. A478–507 updates UI. None pins an image. P1682–1688's immediate command future is not external acknowledgement. updatePlayback/observed extras, not optimistic playWhenReady, carry capture truth.

For app-originated controllable pause, pin the committed presented image and acknowledge source/pause token before issuing transport. Put the seam at the common transport owner so notification/earbuds/focus/noisy routes cannot bypass it. Show pending or display held/source live until acknowledgement. Rejection cannot fabricate stopped transport. Do not delay urgent audible route-loss pause behind an unbounded GPU wait. The pin should be a short CPU ownership operation, not readback or GPU idle.

Exact clearing inside deck::set_paused/PhosphorPlayer and relay acknowledgement inside RemotePlayer/remote.rs are outside allowed source. They were not read. We can prove the missing barrier and continued empty-frame rendering, not a particular local ring-clear line. Smallest next read is those pinned handlers before implementing the common barrier.

Precise external-observation blocker: another player may stop before this app receives its callback. A one-image cache overwritten during that interval cannot recover the image before the unseen stop. Current samples/presentation records have no exact common stop fence. Pre-pin all app-originated commands, pin unsolicited pause at earliest authoritative observation, and do not call that a proven pre-external-stop guarantee. The stronger requirement needs authoritative timing with bounded history or an explicit observation-boundary contract. Silence and last-nonempty fallback do not close it.

## State, lifecycle, inspect and fresh resume

Keep transport observation, intentional display pause and lifecycle suspension separate. ScopeUiState has playing/live/noSignal and capture capabilities but no held identity or inspect transform. Default portable preference is HOLD. Image/source identity/transform remain transient.

A474 resumes lifecycle rendering and A546–558 suspends/releases controller. Neither may create/clear intentional pause. R02 must derive rendering visibility from active full-app/PiP/HUD owner, not an old Activity's stop. P1375–1412 governs real task/source retirement, which invalidates held image by source generation. Failed preflight is not proven source replacement. Live uncontrollable input needs explicit display pause and display held · source live. STATE_NONE, ERROR, STOPPED and buffering are not all intentional pause.

Branch HOLD/BLACK before R539, excluding gain/meter-driven geometry, resting/warmup/flip, cycle computation, DSP and advance. R498–501 track events may update source state but cannot change pinned appearance. Define resumed live cycle phase without replaying missed displayed frames. Rebase frame-time deltas. Live tuning can change for resume while the frozen image remains unchanged.

R292 blocks on recv only without surface or under lifecycle pause. Held presentation must wait for dirty events too. GeometryFrame arrives through an unbounded channel and R525 replaces the latest value. No draw does not mean no busy work. Coalesce geometry with bounded latest storage and avoid waking frozen presentation on every source packet. Live producer ingestion may continue, but must not dirty HOLD. Surface errors need bounded surface-recovery retries, not an 8ms frozen loop.

Redraw only inspect/reset, mode switch, necessary visible chrome damage, attach/resize/format/owner events. Compose-only chrome changes need not redraw native scope. No visible surface means no GPU presentation.

E100–107 provides ring-then-meter lock order. E151/185 show clear_pending at real source boundaries. A display-resume visual flush can use that lock to discard pending capture windows without changing DECK_ACTIVE, retiring the source or replacing remote lease identity. J347 setRingActive is a source API, not a display pause API. Clear/coalesce queued geometry. Excluded ingress code must confirm bounded queues and reject stale decoded batches crossing resume. A bounded ring alone does not prove freshness. Never flush audible local playback or seek it to wall-clock time. Preserve sample-locked local pause.

Inspection must bypass A1415–1431 setGainAbsolute: it changes tuning, disables/persists auto-gain and sends remoteScopeCtl. A1771–1772 changes camera. Add transient inspect actions. Preserve settings-opening gesture priority and reset in-flight gesture sequences when pause mode changes.

## Finite requirement-to-test map

All checks below are proposed future validation, not tests executed here.

|Plan line / requirement|Source seam|Concrete validation|
|---|---|---|
|143 three authorities|R233/379,A474/552,ScopeUiState|T1 state table separates transport/display/lifecycle. stop/start never clears intentional pause. Reject old source/surface tokens.|
|144 default HOLD and observations|ScopeUiState,A478–539,P588–610|T2 default/missing-key migration HOLD, BLACK portable round-trip, no image/transform export. Optimistic callbacks cannot confirm transport.|
|145 audible/acknowledged transport|A841,P233/668,J176|T3 event trace pins before app pause, including notification/noisy/focus. Local audio pauses. Unsupported/rejected external actions never report stopped.|
|146 live display pause/distinctions|ScopeUiState27–32/59,CaptureMirrorPolicyTest|T4 ingress continues under display held/source live. Silence, buffering, disconnect, absent access and error do not invent intentional pause.|
|147 exact image, frozen evolution|R539–801,G517/769,S23–28|T5 present identifiable A, pin, stop input, inject empties/color/geometry. Pixels/serial remain A, zero advance/DSP calls. Failed acquisition of B retains A.|
|148 inspect/reset no tuning writes|A1415/1771,StagePinchScaleTest|T6 pan/zoom corner markers with aspect/bounds. Reset restores A. Spy zero gain/focus/rotation/camera/remote/prefs writes. Existing opening gesture wins.|
|149 BLACK and controls|R794,G769,S213–221|T7 GPU output RGB zero/alpha one. Transparent HUD over contrasting content stays black, controls/resume interactive. No composite after clear.|
|150 HOLD/BLACK recoverability|new pause record at R229|T8 HOLD A→BLACK→HOLD preserves serial/image/inspect, zero transport actions. Pause initially in BLACK still retains A.|
|151 recreation/handoff/no history/bounds|R310–377,G804–835|T9 rotate/full-app/PiP/HUD, callback permutations, bounded allocations. Surviving device retains A. Process/device loss/no frame gives black/no held frame. Check alpha/format policy separately.|
|152 fresh timeline/retirement|E100–187,P756/1196/1385|T10 timestamp old/new windows around resume. No old pending window in first new deposit. Local audio position preserved. Stop/replacement drops A, rejected selection/lifecycle-only suspension retains it. Reject stale ingress/stop.|
|153 event-only/private resources|R288–305/525|T11 counters during stationary HOLD/BLACK, source flood, no surface, inspect/resize. No periodic GPU/deposit work. Only named redraws submit. Retirement frees images, no disk image write.|
|spec110 HDR interaction|G214/411,S203–205|T12 future SDR/HDR and solid/transparent transfers check gamma once, alpha, compatible pipeline and truthful active/fallback status. Compositor evidence separate.|

Existing test anchors were read, not run: CaptureMirrorPolicyTest covers observed truth/action gating/stale bindings. LocalPlaybackPolicyTest covers transport intent/focus. LocalOwnershipRegressionTest covers publication and stale release. BackgroundLifecyclePolicyTest covers linger/retirement/callbacks. PictureInPicturePolicyTest covers entry policy, not retention. PlaybackTruthTest covers publication identity. StagePinchScaleTest covers gesture accumulation, not inspection. ScopeUiStateTest covers HUD defaults, not pause. E tests cover stereo/remote source locking. G tests972/992 cover uniform layout, not retained images. rust/src/lib.rs16–26 gates render/JNI to Android, so pure host tests cannot validate their actual execution.

## Disposition and no-code rollback

Audit completed with precise implementation blockers: missing retained texture draw/state, excluded local/relay transport and ingress ordering, unsolicited external-stop timing, opaque-to-transparent held-background contract, and unverified future HDR transfer. Best current mechanism is caller-owned composite retention through existing composite_into. If destination alpha/transfer recomposition is mandatory, use a narrow retained-energy/frozen-parameters API instead of claiming opaque pixels can become transparent.

Early source findings were delivered to coordinator parrot for Poodle. No implementation or R15 approval was given. Only this private scratch report is created and sealed read-only. No repo/Git/device/playback state requires rollback. Future corrections belong in a separate addendum. This audit releases its R13 source-audit ownership on completed report and owns no implementation files.
