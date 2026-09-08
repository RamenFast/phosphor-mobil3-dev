# R15 section 5 / R13 independent integrated-source critique

## Result and immutable identity

**Score: 6/10. R13 is not accepted.** The retained-image mechanism substantially follows the intended design. Fresh resume and source retirement have material gaps. Actual Android acceptance remains unrun.

Reviewer: Jcode worker `session_macaque_1788853377867_c83af5a4929e73cd`, independent of the implementer. Coordinator-confirmed route: `openai-oauth:gpt-6-astra/high`. No workers were started. The coordinator completed the routing ritual. This is one review of the specified integrated baseline, not approval of an earlier source map or a later correction. The coordinator owns the cumulative section-round count.

Review began 2026-09-08 07:42:57 UTC. Report prepared within the 15-minute worker budget.

| Identity | Exact object |
|---|---|
| Mobile commit | `44172dcc1ea8d91ac1fe8783cbeb68f5c3eb3d68` |
| Mobile tree | `436bdbef0a4640b6801dce862881a4b61f23eb2b` |
| Shared commit | `084f5d9612f7f699bb80b08607845982d1579861` |
| Shared tree | `6cf0a199f4afdf0521b6fdff4fc79a193644cdef` |
| Mobile runtime base | `a3223b87c8d15fc0da691cfcfe0963ba464d1e92` |
| Shared runtime base | `7729990bb29f0167ef906d0fbdb44e1e91206955` |

Product reads used Git objects, not mutable working-tree content. The read receipt records 39 target blobs, 17 baseline blobs inspected through diffs, and three external governance files. Whole-blob hashing is not a claim that every line received a full review. Selected control-flow regions, complete small ownership modules, relevant diffs, and focused test bodies supplied the evidence below.

Receipt: `/home/ben/.jcode/scratch/R15-section05-macaque-20260908T074257Z/READSET.sha256.txt`

Receipt SHA256: `3de32012651e162e29997988d7e51ffe0612c998a70c6fd0cc04fad70aef639f`

The completion message supplies this report's SHA256. This original report and receipt remain unchanged. Any later correction assessment must use a separate artifact.

Path notation below: `K/` means mobile `app/src/main/kotlin/dev/phosphor/mobil3/`. `G/` means shared `crates/phosphor-render-gpu/src/`. Unprefixed `rust/` paths belong to mobile.

## Prioritized findings

### F1. P1 acceptance gap: capture reads can cross resume and publish old samples

**Requirement:** canonical section 5 fresh current-timeline resume, spec R13 visual-only stale-ingress retirement. This is the disclosed producer gap, independently confirmed, not a new discovery or a passed requirement.

**Source:** `K/MicController.kt:181-191`, `K/CaptureService.kt:269-270,342-344`, `rust/src/deck.rs:38-39`, `rust/src/render.rs:172-179`, `rust/src/pause.rs:57-76`.

Finite trace:

1. A capture read starts while the display is held and obtains old samples.
2. Resume increments VISUAL_EPOCH and clears the visual ring.
3. The old read returns. Its source remains running, so readSourceSamples calls push.
4. push_capture publishes samples without a read epoch or sample timestamp.
5. The next live render consumes those old samples as current input.

Root PCM publication has the same samples-only seam. Remote receive-side epoch capture occurs after payload reading at `rust/src/remote.rs:1401-1415`. It therefore does not prove the age of bytes already buffered before decode. The callback epoch improvements are useful, but they do not close this distinct source-time obligation.

**Smallest correction:** add a producer token before each capture read and validate it at visual publication under the existing ring guard. Define and test the treatment of already-buffered source data. Do not flush, seek, or clear audible playback queues to solve a display problem.

**Finite check:** hold a producer immediately before publication, resume, then release it. The old chunk must be rejected and a new marker accepted. Add separate source-time evidence for buffering before the read/decode fence.

### F2. P2: source retirement can recreate a held image containing retired-source GPU energy

**Requirement:** source changes and explicit stop invalidate old held history and clear retained images on source retirement.

**Source:** `rust/src/pause.rs:82-92`, `rust/src/render.rs:598-602,661-667,827-861,934-955`, `G/lib.rs:528-540,584-585`, `G/shaders.wgsl:23-28`.

invalidate replaces CPU History and clears visual ingress. The render-side fresh branch clears geometry and resets Computer and clocks. It does not clear the live GPU energy textures. The shared decay shader computes `max(old_energy * keep - 0.0004, 0)`, not a source-generation reset.

Finite trace:

1. Present a bright noncentral marker A from source A with nonzero persistence.
2. Stop A or replace it with a silent source B without resizing the renderer.
3. Invalidation clears committed and pinned History and increments generation.
4. The next live advance retains nonzero A energy, then adds either no new signal or a resting dot.
5. retain_frame copies that energy and commit accepts it under the new generation.
6. Pause now. The newly pinned image still contains A pixels despite source retirement.

This finding concerns explicit retirement, not an assertion that ordinary same-source phosphor trails must disappear on every resume. Source arithmetic and the absence of a GPU clear establish the trace. Android pixels were not observed here.

**Smallest correction:** clear live energy at the source-generation boundary before any new-generation advance or commit. Keep pinned immutable resources untouched during ordinary HOLD and surface transfer. A source-only generation counter cannot erase pixels already deposited in GPU history.

**Finite check:** extend the synthetic pixel fixture with A, retirement, silent B, first live present, then HOLD. The old off-center A marker must be absent. Keep the existing A/acquire-failed-B/pin-A check separate.

### F3. P2: a retired remote M observation can pause the replacement source

**Requirement:** source retirement and exact-session fencing must prevent stale source callbacks from changing current display state.

**Source:** `rust/src/remote.rs:198-202,591-608,616-625,1455-1466`, `rust/src/pause.rs:121-128`.

The native M path checks scope_live before parsing JSON, then calls a global observation API without a source token. The ownership check and History mutation are not one guarded publication. connect/disconnect also invalidate History before advancing remote generation.

Finite interleaving:

1. Old reader passes scope_live for M `{playing:false}` and is suspended before observe_transport.
2. Retarget or disconnect invalidates History and retires the old remote session.
3. Old reader resumes and calls observe_transport(true).
4. The call sees fresh global History and pins or pauses the replacement source.

The audio/geometry epoch checks do not protect this separate transport-state write. This is a source-proven race possibility, not an observed Android incident.

**Smallest correction:** carry an exact source/session token into transport observation and validate it at the same guarded point that changes History. Coordinate source invalidation with token retirement. Merely checking scope_live a second time outside that guard leaves the race.

**Finite check:** use a barrier after the old session check, invalidate/retarget, release the old observation, and assert that replacement History stays live and unchanged.

### F4. P2: an initial PAUSED snapshot manufactures intentional display HOLD

**Requirement:** the refined section-05 contract says transport initialization does not create intentional pause. Silence, buffering, and errors must also stay distinct.

**Source:** `rust/src/pause.rs:22-28,121-128`, `K/PlaybackService.kt:511-512,590-596`, `rust/src/remote.rs:1455-1462`.

History starts with transport_paused=None. observe_transport(true) calculates a change because `None != Some(true)` and `paused` is true. It calls set_paused(true). PlaybackService feeds the initial bound controller's current snapshot through this method, not only later transitions.

Finite trace: start a fresh capture mirror while the chosen external player is already PAUSED. No user pause or PLAYING-to-PAUSED transition is required. The initial snapshot freezes the display, possibly with no held frame. A remote initial M with playing=false follows the same rule.

**Smallest correction:** seed transport observation per source without generating intentional display pause. Preserve explicit pre-command pinning and subsequent authoritative pause transitions. Test initial PAUSED, PLAYING-to-PAUSED, duplicate PAUSED, and source replacement separately.

### F5. P2 UX gap: HUD has no display-only return-to-live for controllable sources

**Requirement:** inspection has a clear return-to-live action, and independent display actions do not change transport.

**Source:** `K/ui/Sheets.kt:1159-1169`, `K/FloatingHudService.kt:168-181,278-283`.

The app exposes PAUSE DISPLAY ONLY even for a controllable local or remote source. The HUD exposes FIT and transport. Its display toggle is only the fallback when COMMAND_PLAY_PAUSE is unavailable.

Finite user path:

1. Play a local track.
2. Use app settings to pause the display only. Audio continues.
3. Transfer the held image to the HUD.
4. The HUD button remains Pause source, not Return display to live.
5. Pressing it pauses audio and retains HOLD. There is no HUD display-only resume action.

Returning to the app can recover the display, so this is not a permanent trap. It is a missing inspection control on the handoff surface.

**Smallest correction:** expose a separate held-display LIVE action while retaining truthful transport controls. Verify that this action changes no audio state or local position.

## What the source does support

### Presented identity and pause lock

`History::pin` clones committed ownership. commit rejects candidates while paused and rejects obsolete generations. In `render.rs:914-955`, acquisition precedes retention, and commit follows present. Therefore A committed, B acquire fails, then pause retains A. B's failed acquire may advance live energy, but it cannot overwrite A's independent retained texture.

The pause-true path does no GPU allocation, submit, poll, driver wait, or transport action under DISPLAY. Dropped pinned/history resources leave the lock before destruction. Common local, remote, and supported capture pause owners pin before audible pause or command dispatch. The inherited source assertions check call order, not Android callback execution.

The identity remains the CPU-committed application-present boundary. Physical scanout is not observable. A concurrent in-flight present may appear before the pinned redraw, as the contract discloses. This review does not strengthen that into exact panel timing or pre-observation external-stop proof.

### Frozen image and destination transparency

`G/retained_frame.rs:71-147` copies same-format energy and composite uniforms. Exclusive ownership controls reuse. The retained image does not own a Surface. `present_retained:225-228` changes destination alpha and SDR encoding, not source appearance. The additive shader fits the original extent and bounds image addressing.

BLACK is a clear-only `wgpu::Color::BLACK` branch at `render.rs:640-653`, including alpha one. Mode changes preserve pinned ownership and inspection. Missing pinned history uses the same black fallback and production UI reports no held frame.

### Resource and idle discipline

Checked dimensions, device limits, arithmetic, and a 128 MiB per-snapshot cap exist. Committed, spare/candidate, and pinned ownership is bounded by the inspected loop. Allocation errors leave committed History unchanged. Allocation, pipeline creation, present, and driver failure behavior still need fault injection. An error outside the CPU lock can still stall a driver.

Visible HOLD branches before sample drain, DSP, geometry, decay, deposit, resting beam, and cycle evolution. An unchanged held display waits on the command channel. Geometry packets use a latest mailbox and do not themselves enqueue a dirty command. Hidden/lifecycle-paused drawing is gated before the presentation branch. This supports the intended event-driven source path, not measured zero GPU work across every Android callback or configuration event.

### UI defaults and isolation

PauseDisplayPolicy defaults missing or malformed local values to HOLD. The archive admits only HOLD or BLACK and has focused round-trip/type fixtures. Inspection and images are not portable settings. App inspection dispatch occurs after settings-door and chrome arbitration and before live tuning verbs. JNI inspection changes only bounded transient Inspection. App reset and mode controls exist. HUD gesture continuity, touch cancellation, accessibility, and F5 still require actual UI checks.

## Requirement coverage and remaining acceptance

| Canonical section 5 obligation | Evidence and disposition |
|---|---|
| Separate lifecycle, display, transport | Separate state exists. F3/F4 leave source-authority defects. |
| HOLD default and BLACK preference | Production policy, archive field and focused fixtures read. Device clean-install, migration, import application unrun. |
| Preserve controllable transport semantics | Common-owner pre-pin ordering traced. Remote still has optimistic play state and later M reconciliation, not per-command acknowledgement. Actual rejection, notification and earbud paths unrun. |
| Truthful live-source labels | Production PauseDisplayPolicy and activity reader/observed-state inputs read. Selected capture metadata alone is not the live suffix. Android lifecycle truth unrun. |
| Last presented frozen appearance | Source supports immutable energy/uniform retention and A/failed-B/pin-A. Synthetic pixel evidence inherited. Driver failure and panel timing unproven. |
| Pan, zoom, reset, return live, settings gestures | App branch isolation and reset exist. F5 affects HUD return live. Actual gesture/accessibility checks unrun. |
| BLACK once with controls | Dirty held branch and opaque clear inspected. Actual transparent Android HUD and PiP controls unrun. |
| Change mode without transport | Mode setter changes black state and dirty flag only. History/inspection retained. Device round trip unrun. |
| Recreation, resize, bounded resources, missing frame | Retained ownership lives outside Active. Bounds and fallback inspected. Synthetic destructive resize inherited. Android rotation, PiP/HUD transfers and allocation failures unrun. |
| Fresh resume and source invalidation | Visual-only ring/geometry flush and remote chunk epochs improve source. F1/F2/F3 remain material. Audible queues are not cleared by fresh_visual_ingress. |
| Event-driven HOLD, hidden surfaces, transient privacy | Branch ordering and latest mailbox support idle behavior. No new product CPU image readback or image export found. Driver counters, source flood, memory, and F2 retirement residue remain open. |

## Checks actually performed versus inherited evidence

**This reviewer:** read-only Git object inspection, base-to-target content diffs, exact tree/blob identity and SHA256 receipt generation, control-flow and finite failure/interleaving traces. Read focused native History, epoch/lease, retained bounds/shader tests, JVM pause-policy/archive tests, and the two synthetic GPU fixture bodies. No host test was rerun. No copied or mock implementation was executed. There is no reviewer-generated runtime pass claim.

**Inherited coordinator gate `966199akcz`:** 514 JVM Android tests, 83 native tests, two actual synthetic offscreen GPU pixel tests, lint, dual debug build, engine and source-boundary checks, with both source manifests unchanged. These results are supplied by the coordinator and recorded in the pinned validation document. Raw gate logs were not independently rerun or re-audited here.

The synthetic GPU fixture exercises 64x48 offscreen Rgba8Unorm targets. It checks retained/live equality, live mutation and resize isolation, inspection/reset pixels, and premultiplied destination opacity. It does not exercise Android Surface acquisition, BLACK's Android render branch, callback ownership, source-stop energy clearing, transport initialization, or stale M interleavings. JVM source assertions do not establish notification or earbud delivery.

No Gradle, Cargo, Android/JNI execution, GPU, device, GUI, audio, relay, network, service, install, or worker operation occurred in this review. No product/source/Git state or existing report was edited. Only this private report and its immutable read-set receipt were written.

## Score rationale and bounded outcome

Intent fidelity is substantial in the core frozen-image design. Functional accuracy loses ground at source boundaries and initialization. UX has the HUD display-resume gap. Lifecycle/performance/privacy source structure is promising, but actual Android behavior and source-retirement residue remain unresolved. The inherited main-path tests do not cover these material traces. Under R15's anchors, 6/10 means mostly implemented with material gaps, not verified acceptance. A score of 8 would be unsupported by the present source and evidence.

**Blocked:** full R13 acceptance lacks producer source-time freshness, corrected retirement/observation behavior, and a clean exact Android install plus behavioral evidence.

**Evidence:** F1 through F5 and the requirement table identify concrete source traces and missing observations. The frozen gate is not Android callback/device proof. No final clean install freeze exists in the supplied evidence.

**Best current result:** an independently reviewed immutable retained-image implementation with inherited host/synthetic-pixel main-path evidence, five finite findings, and an exact read receipt.

**Next smallest step:** the coordinator can add the F4 initial-observation and F3 stale-observation barrier tests first, then correct the ownership seam. Add the F2 retirement pixel check and F1 delayed-producer check before the next corrected-source freeze. Keep device acceptance separate and respect any existing four-round section limit. Do not amend this report to score later source.
