# R15 independent R02 floating HUD source review

## Decision

**Current released source implementation: 6/10. Not accepted for release. Hardware acceptance is unassessed.**

The implementation has a coherent single presentation owner, real GPU alpha wiring, explicit activation, and mostly separate audio ownership. Four source defects remain: an unresolved Kotlin property, a native retirement barrier that can time out without retiring, mutable track identity that can advance twice, and an uncancelled pending controller connection.

This score assesses the frozen implementation, not a corrected coordinator tree. The score weights contract wiring 3/3, lifecycle and failure safety 1/3, source and metadata continuity 1/2, and presentation/settings integration 1/2. Source evidence cannot establish Android composition, lifecycle delivery, or hardware behavior.

## Provenance and boundaries

- Review began 2026-09-08 06:16:37 UTC with a ten-minute limit and no workers.
- Repository: `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`.
- Baseline HEAD: `8990d023f3d65371863d06d7e0c32ed55dbe1365`.
- Released manifest: `/home/ben/.jcode/scratch/r02-hud-source-20260908T0602.sha256`.
- Manifest SHA256: `56b4b970234e28ce1dbb638ed689b7cb23b04ede7857776785ca397d26632e89`.
- All 19 live source hashes matched before copying. All 19 copied source hashes matched after copying.
- Immutable evidence directory: `/home/ben/.jcode/scratch/r15-hud-review-20260908T0617`.
- `source/` contains the exact released files. `SOURCE.sha256` records their hashes. `released.diff` records the released tracked changes against HEAD.
- Live files were released to the coordinator at 06:17:50 UTC after sealing the snapshot. The assessment of the 19 released files stayed on `source/`. Incidental live search results were not substituted for the frozen baseline.
- Necessary adjacent lifecycle/player and shared GPU files were copied separately into `adjacent/`. `ADJACENT.sha256` records these separately observed dependencies. They are not claimed as part of the original 19-file release.
- `REPORT.sha256` records this report's exact hash. A report cannot contain its own ordinary SHA256 without a circular hash dependency.
- Binding governance and `ben-context-standards` were read first. No product source edits, Git mutations, compilation, installation, device actions, GUI, network, or service operations occurred.
- Only private scratch evidence was written. No R01 root implementation was reviewed. HOLD and HDR remain outside this review.

All relative source paths below refer to the repository unless a sibling path is explicitly named. Released line numbers refer to the immutable snapshot.

## Findings

### F1. High: the frozen Android source cannot compile

**Location:** `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt:854`.

The Show guard reads `micHandoff.active != null`. Its declared type is `MicHandoffPolicy` at MainActivity lines 108-127. The adjacent policy exposes `isPending`, not `active`, in `app/src/main/kotlin/dev/phosphor/mobil3/MicHandoffPolicy.kt:20-21`.

**Source reproduction:** resolve the property against that class. No `active` property exists. The authored wiring test at `app/src/test/kotlin/dev/phosphor/mobil3/FloatingHudPolicyTest.kt:145` explicitly requires the same invalid expression, so that string assertion does not validate Kotlin integration.

**Independent external evidence:** at 06:17:07 UTC the coordinator reported an actual Android compiler failure at line 854. This reviewer did not run compilation or inspect a raw compiler log. The coordinator's initial message called the receiver `micController`. The frozen source establishes the exact receiver as `micHandoff`.

**Effect:** the released baseline cannot produce the intended Android application. This blocks release regardless of otherwise sound policy models.

**Finite obligation:** correct the real pending-microphone interface and its fixture, retain this baseline, and attach the coordinator's Android compile/test result to a new source receipt. The coordinator reported a later `isPending` correction. That correction is outside this report's assessed baseline.

### F2. High: native timeout does not complete or invalidate presentation retirement

**Locations:**

- `rust/src/jni_glue.rs:46-57`: enqueue `SurfaceCreated`, wait two seconds, and return `-1` without cancelling the command.
- `rust/src/jni_glue.rs:67-78`: enqueue `SurfaceDestroyed`, wait two seconds, then log and return without a success result.
- `rust/src/render.rs:319-320,359-368`: complete bring-up, install `Active`, then ignore acknowledgement-send failure.
- `app/src/main/kotlin/dev/phosphor/mobil3/SurfaceOwner.kt:9-11,19-23`: regard detach return as completion, or clear current before detach.
- `app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt:41-45,60-64,92-95`: failure and destruction use this void detach barrier.
- `app/src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt:290-292`: close the host and remove the overlay window afterward.

**Source reproduction trace, not an executed GPU fault injection:**

1. HUD attach enqueues `SurfaceCreated` and the render thread stalls inside bring-up for more than four seconds.
2. At two seconds, the JNI wait returns `-1`. The queued or running command remains valid.
3. SurfaceHost releases its lease. It queues pause and destruction, then waits another two seconds.
4. The destruction wait expires. JNI logs failure but returns normally, so Kotlin treats retirement as complete.
5. HUD teardown may remove the SurfaceView window. A visible activity may claim a successor lease.
6. The render thread can later finish the old bring-up, install `Active`, and ignore the failed acknowledgement because its receiver already timed out.
7. Queued pause/destruction can eventually retire that surface, but they do not establish retirement before Java window destruction or successor grant.

FIFO command order helps eventual cleanup. It does not make the timeout a completed barrier. The defect does not require claiming two simultaneous native `Active` values. It is a broken window-lifetime and ownership-completion guarantee.

**Provenance:** HEAD already had the two-second destroy log-and-return behavior. R02 introduces the timed synchronous attach acknowledgement and relies on that inherited destroy API as the new lease-transfer barrier. This is an R02 contract failure, not a claim that R02 introduced every relevant line.

**Effect:** slow or unhealthy GPU work can outlive Java retirement and contradict the explicit detach-before-replacement contract. Actual crash behavior is unmeasured.

**Finite obligation:** exercise a delayed queued attach and a delayed in-flight attach, followed by close and return. Assert that timed-out attachment never becomes an accepted presentation, failed retirement cannot grant a successor, and native surface/window use cannot continue after Java destruction. A cancellation token plus a sticky unavailable owner addresses stale publication and successor grant, but does not alone prove the last lifetime condition. Do not treat a boolean failure report followed by unconditional window removal as complete safety evidence.

### F3. Medium: mutable display metadata is treated as a new track identity

**Locations:**

- `app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt:69-75` includes title, artist, and album in the identity sent to the gate.
- `app/src/main/kotlin/dev/phosphor/mobil3/SurfaceOwner.kt:28-33` advances whenever that list differs.
- `app/src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt:268-273` and MainActivity lines 540-545 forward metadata changes.
- Adjacent `app/src/main/kotlin/dev/phosphor/mobil3/PhosphorPlayer.kt:101-106,174-182,144-156` publishes queue title first and resolved tags later.
- `rust/src/render.rs:504-506` advances the per-track color leg for every such command.

**Source reproduction trace:**

1. Enable TRACK cycling with at least two selected colors.
2. Advance to a local queue entry whose queue title is `file.flac` and whose resolved title is `Song` with artist `Artist`.
3. `advanceIfPossible` changes the index, clears loaded metadata, and invalidates player state before asynchronous staging finishes.
4. `itemData` publishes media ID `q1`, index `1`, title `file.flac`, and no artist or album.
5. The current SurfaceHost accepts this changed list and calls `cycleAdvance`.
6. `onTrackMetadata` later publishes the same media ID and index with title `Song` and artist `Artist`.
7. The list differs again, so the same track causes a second `cycleAdvance`.

This is a direct branch trace through actual producers and consumers. Android callback scheduling was not executed here. A delayed metadata publication makes the two observations independently testable.

**Effect:** two selected colors can advance twice and return to the original color for one track. With more colors, tracks can skip a color. The existing fixture at `FloatingHudPolicyTest.kt:8-26` covers identical repeated lists, not metadata enrichment for one track.

**Finite obligation:** use stable source/item identity for track boundaries where available. Test one placeholder-to-resolved publication, repeated metadata, artwork-only updates, identical-label distinct items, and app/HUD/app handoff. Require exactly one advance for each actual track boundary and no extra advance for enrichment or handoff.

### F4. Medium: retirement cannot cancel the pending controller connection

**Locations:** `app/src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt:255-274,282-294`.

`connectController` stores its build future only in a local variable. `retire` increments `controllerGeneration`, removes Handler callbacks, and releases the completed `controller` field. It neither retains nor cancels/releases the pending connection future.

**Source reproduction trace:** start a HUD and delay controller connection completion. Close the HUD before the future completes. Retirement sees `controller == null`, so it has no connection handle to retire. The future's completion listener remains registered and captures the service. If it later succeeds, the generation/retired guard releases the result, which is a useful safeguard.

**Effect:** the source does not satisfy the contract's immediate cancellation of pending connection work during close, lock, revoke, or failed construction. The pending binding can outlive HUD retirement until resolution. This review does not claim a permanent leak, a second audio reader, or a missing Media3 internal timeout.

**Finite obligation:** retain and retire the pending connection using the supported Media3 lifetime API. Delay both success and failure completion across close and assert no surviving pending binding or listener, no controller resurrection, and exactly one eventual release.

## Requirement-to-evidence coverage

| Requirement | Source evidence and assessment | Remaining uncertainty / acceptance |
|---|---|---|
| One owner across app/PiP/HUD | Shared companion owner in SurfaceHost 90-113. Lease fencing in SurfaceOwner 9-23. App/PiP use MainActivity.makeSurface 889-898. | Normal source path is coherent. F2 prevents unconditional lifecycle compliance. Repeat real transfers and stale callbacks. |
| Stale surface callbacks | `preferred()` and lease checks at SurfaceHost 33-36,60-64 fence retired hosts. | Framework callback ordering, same-view recreation, and native delayed work remain unexecuted. |
| Native attach failure and destruction | Explicit `-1` handling and cleanup at SurfaceHost 41-45. Renderer only reports mode after configuration and renderer availability. | F2 is a material failure-path defect. No timeout injection was run. |
| GPU format continuity | `render.rs:869-873,925-940` pins the retained format and refuses unsupported replacements. Renderer lives outside `Active` at 228-231. | Android-only code was not compiled here. Surface capability changes and device loss need coordinator checks. |
| True transparency | SurfaceHost 22-24 requests translucent pixels and top Z order. render 359-368 sets actual scope alpha. surface_policy selects PreMultiplied only when supported. | Source chain exists. Actual Android compositor and colored-background pixels remain unproved. |
| GPU shader alpha | Sibling `phosphor/crates/phosphor-render-gpu/src/lib.rs:429-432,684-703,769-799` writes alpha without blending. `shaders.wgsl:213-221` emits premultiplied RGB and calculated alpha. | The black attachment clear is overwritten by the full-screen composite draw, so it is not itself an opaque-backplate defect. Hardware/compositor acceptance is still required. |
| Honest fallback | SurfaceHost 47-55 reports unsupported premultiplication, requests opaque format recreation, and leaves `scope_alpha=1`. A negative attach reports unavailable. | Exercise unsupported alpha and failed opaque recreation. Confirm no stale active-transparent status. |
| Tuning, dimensions, density | render 323-350,364-366 resizes the retained renderer and refreshes density without resetting the retained tuning. | Energy textures can be replaced by resize. No held-frame continuity claim belongs to R02. |
| Active microphone refusal | MainActivity 851-869 checks ownership and pending permission before consent/start. HudPolicy 21-26 gives a concrete refusal. | F1 breaks the pending-handoff interface. After correction, test an active reader and pending handoff independently. |
| Source identity continuity | HUD creates a MediaController, not an audio reader. MainActivity onStop 560-575 releases presentation/controller, not service-owned readers. `selectSource` 818-822 only retires activity requests. | Reader counts, source identity, position, and wake state need real repeated handoffs. No inference from absence of direct reader calls alone. |
| Existing session attachment | Adjacent PlaybackService 220-268 constructs session/player faces and restores an existing capture mirror. No local deck or relay is opened in this entry path. | F4 concerns the pending connection lifetime, not proven audio duplication. |
| Track deduplication | Shared in-memory gate and lease routing cover repeated identical metadata and handoff gaps. | F3 defeats actual track identity under metadata enrichment. Same-label item transitions also require an event-routing fixture. |
| Transport acknowledgement | HUD 150-156,241-253 checks available commands and derives labels from controller state. CaptureMirrorPolicy 18-27 uses observed platform state. PlaybackService 1536-1544 maps actual skip capabilities. | The HUD does not optimistically flip its own label. Local/relay/capture acknowledgement behavior remains device acceptance. |
| Inert defaults and archives | HudPolicy 9-19 defaults off/SOLID/320. SettingsArchive 92-95 allows only typed HUD fields. MainActivity import 262-291 restores preferences without Show or consent. | Existing `hud_mode` remains separate. Test absent fields and enabled+transparent import without access. |
| Consent return and PiP precedence | MainActivity 182-186 clears consent state without activation. 447,454,461 suppress PiP during consent or active/pending HUD. | Deny/grant return, cancellation, and process recreation need Android checks. No automatic Show is present in the inspected return path. |
| No hidden chrome/render clock | PhosphorScreen 220 returns before chrome effects. MainActivity 78-89 and 560-568 stop heartbeat. SurfaceHost 102-112 releases hidden leases. render 291-295 blocks without a surface or when paused. | Protected-screen visibility delivery, initial hidden overlay construction, lock ordering, and frame counters are unmeasured. |
| Close/lock/revoke/partial failure | HUD 63-71 observes screen/app-op changes. 84-176 catches construction failure. 282-308 independently attempts cleanup and reports named failures. | F2 and F4 remain. Pure cleanup-loop fixtures do not execute Android resource cleanup. |
| Task removal and process death | HUD 300-308 uses existing task retirement and then closes. Adjacent BackgroundLifecyclePolicy 130-157 applies existing linger/source policy. START_NOT_STICKY and in-memory session prevent preference-driven restart. | Observe linger on/off, process kill, stale callbacks, and task membership timing on Android. No device action was taken. |
| Return and source controls | HUD 276-279 launches the app, then retires. MainActivity 704-718 hides HUD and opens source picker when requested. | Verify failed return launch keeps a usable HUD and that SRC never starts a new input. |
| Geometry and outside touch | HUD 103-108 sets nonfocusable/non-touch-modal flags. 189-194 sizes controls to 48 dp. 226-240 clamps to usable insets and handles configuration changes. | Small displays, large font scale, density changes, all edges, protected screens, and actual outside touches require device checks. No brightness/lock-screen override was introduced in HUD source. |

## Evidence quality and uncertainty

The all-19 checksum verification is executed evidence. Git HEAD and diff inspection establish the released source baseline and inherited destroy-timeout provenance. Finding reproductions above are source-level traces, not executed Kotlin, native, Media3, or Android lifecycle tests.

The release contract reports 75 passing host Rust tests. That is prior worker evidence, not a test run by this reviewer. The released Android fixtures were authored but had not run in that worker. `FloatingHudPolicyTest` mainly exercises pure models and source-string presence. It does not exercise a real Service, WindowManager, SurfaceHolder, MediaController, GPU, or JNI timeout. Its line 145 even requires the F1 invalid property expression.

The coordinator reported an actual Android compile failure during this review. At 06:25:06 UTC the coordinator reported 498 passing unit tests on a corrected tree. The coordinator also reported an Android lint failure: SurfaceHost.present manually invokes surfaceChanged with dummy format 0. Frozen SurfaceHost line 67 confirms that call. These are attributed integration updates, not reviewer-run checks or acceptance of this baseline. The coordinator owns the callback-refactor lint fix and corrected receipt. No compilation or phone acceptance is inferred from policy tests, source strings, or shader inspection.

Uncertainty is concentrated at Android integration boundaries: callback ordering, overlay visibility, Media3 pending connection disposal, JNI/GPU lifetime under stalls, source reader counts, and true compositor pixels. No extra research outside these boundaries was performed.

## Finite coordinator acceptance obligations

These are coordinator-owned follow-through, not instructions for Ben. Do not extend into HOLD, BLACK, HDR, or R01 implementation.

1. **Corrected build receipt:** close F1, update the invalid wiring assertion, run the owned Android compile and unit gate, and record changed source hashes.
2. **Native failure barrier:** inject queued and in-flight attach delays beyond two seconds. Close, return, and recreate presentation. Require the F2 lifetime and successor assertions, not just a logged timeout.
3. **Controller retirement:** delay connection success and failure across close, lock, revoke, and construction failure. Require retirement of pending connection work and one release for any late successful controller.
4. **Track boundaries:** run F3's staged-to-resolved case plus same-label distinct items and app/HUD/app transfers. Count native cycle advances against actual source/item boundaries.
5. **Activation and microphone:** test clean defaults, old archive, enabled+transparent import without access, deny/grant consent return, active mic, and pending mic. Require no automatic Show, reader change, consent loop, or PiP while pending.
6. **Presentation and continuity:** record source identity, reader count, position, tuning, and wake state. Perform ten app/PiP/HUD transfers across local, relay, and capture. Require one surface owner and unchanged service source except explicit transport actions.
7. **Pixels and fallback:** compare SOLID and TRANSPARENT above colored content. Record actual swapchain alpha/format and status. Force unsupported premultiplication and failed fallback attach. Require opaque fallback with reason or unavailable, never fake transparency.
8. **Cleanup and hidden work:** close, lock/unlock, revoke/regrant, remove task with linger off/on, and kill process. Inject addView failure and individual cleanup failures. Require no orphan overlay, notification, listener, controller, hidden render/chrome clock, or automatic restart.
9. **Controls and geometry:** test acknowledged previous/play/pause/next for all three service sources, including unavailable capture controls. Test SRC/return, failed return launch, outside touches, all edges, two rotations, and changed display/font size. Require reachable controls and no new source from SRC.

Acceptance remains blocked until source defects are closed and the applicable coordinator checks have receipts. The smallest next check is the corrected Android compile result already owned by the coordinator, followed by the native delayed-retirement fixture. Preserve this report. Later evidence belongs in a narrow addendum with its own hashes, not a replacement report.

## Exact source manifests

The following are the exact released and adjacent manifests preserved with this report.

### SOURCE.sha256

```text
f7d10bc5bfc2eff95971ffe8e6288609d4133471e66c5feb6957fa3bccc297b4  docs/plans/mobile-expansion/section-04-hud-contract.md
c299f45a466675ca090a5945a0a1583e30a862bd3565519ab79367beb7090a45  spec/EXPANSION.md
f1b1d201c393ba5d945a6e10d04f584a571ff951d84c0d5f793514bfdab078a4  app/src/main/AndroidManifest.xml
79b843205f9182f3c66de8376b0b123f41a84e22e7b98542c036030f4e121d60  app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt
8b6cda2caa80dee7ba897f7419365e16061b89f62c04c7180d203a8b7e9f189d  app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt
b09304ffe0c41239ad47f463d1f85cb459220025bd87654ec545636488603fff  app/src/main/kotlin/dev/phosphor/mobil3/HudPolicy.kt
ba3e1ee0876f83896df394dad7ce3c2b7b1d79e93c34e50dcc582a8935188366  app/src/main/kotlin/dev/phosphor/mobil3/SurfaceOwner.kt
c66e6049381ce86488d580f237dd90a8a25bf7a6fd1633de19f277c8f51027a3  app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt
103ddce3054a83d76edd785ba25c2f71b30853dc5176b46d1dae9d65399cc75c  app/src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt
e7b5956b222da55aad136c6484d7ebf2b6e18999ee77e36e2c9393ae21d66ca8  app/src/main/kotlin/dev/phosphor/mobil3/settings/SettingsArchive.kt
7d41e332e50de84c076100b03382ce3c323c56745aa937ddd372169074df48ef  app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt
5acd2da57da1f453628a80fa060cac81c73a70a2b022b780f6f03de94714dbfe  app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt
8bb84eb1d5ffc8652e35d5b0012bbe36451b4e7669300e26b9025d57268daef6  app/src/main/kotlin/dev/phosphor/mobil3/ui/Sheets.kt
9e118c48e98e2a8c49329c558d1988e59d51a502d0e2edf9086437e0b7f4c6c0  app/src/test/kotlin/dev/phosphor/mobil3/FloatingHudPolicyTest.kt
a2412358f83e47d95c3a3f3522e51631c1f5f1274dd21e527c67cb7c3f42a650  app/src/test/kotlin/dev/phosphor/mobil3/settings/SettingsArchiveTest.kt
860b919ecdabdd8f1a7330644efccc0f39ee04299faad3e8f82a773ce1d5adba  rust/src/render.rs
1b2c72af62ff6865a5672e913dd009419f608bae637b6b0f0578e416ba3f871b  rust/src/jni_glue.rs
c4a4f4a64716c5ac509ef1a09efca5a3a3ab34f09afd98c7bc5b73d1b5705f65  rust/src/lib.rs
f600992e49f7a4e7e8bea607e46863c95ddc1761bae43595f6bacf7416160cd8  rust/src/surface_policy.rs
```

### ADJACENT.sha256

```text
0e2bcbd986a9d17917026c41bf17cc2373e067ab31ad6b3d48e67bd52e4afa3b  adjacent/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/MicHandoffPolicy.kt
8f08040691b5d39f5233fd088d4afd7cb1289e8edd58dc11999a2b5f46e40a6c  adjacent/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorPlayer.kt
db89a1a54ecc65f2b8a95eebc0e04eb487fc2d89721b8bba99453fab744e3e44  adjacent/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/PlaybackService.kt
d07fbabc47281517cfed4e0c7f082a3ffac3a6634676fd0d5ec536427196271c  adjacent/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/CaptureMirrorPolicy.kt
d3c743fda7c7b1891d788bbaee2998fd914a7f0b6094b1423b93ec5de02bc4ac  adjacent/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/BackgroundLifecyclePolicy.kt
5ab8dba12e4544830826e9bd51da84eee62258b4cb0165596b88cb72c9d2dbc9  adjacent/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/RemotePlayer.kt
41307781dc028ffb57409a3a5fb871a3345ffe0ab4da119ad0bd6581e63000dc  adjacent/phosphor-mobil3/rust/Cargo.toml
e25c22944f8a9250ea88f2757e25938b05375df4e774bc5e8e3cb7456db0983a  adjacent/phosphor/crates/phosphor-render-gpu/src/lib.rs
65b004c826704101833128b2fef82f32d446f566be297222f0495e69ae7d428c  adjacent/phosphor/crates/phosphor-render-gpu/src/shaders.wgsl
```

Report finalized 2026-09-08 before the 06:26:37 UTC review deadline. No further source investigation is included.
