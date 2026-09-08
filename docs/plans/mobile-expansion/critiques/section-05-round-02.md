# R15 section 5 / R13 independent correction review, round 2

## Result and immutable identity

**Score: 7/10. R13 is not accepted.** The five corrections improve the baseline. App capture read ordering, the public GPU clear, native remote observation fencing, fresh-History initialization, and independent HUD LIVE are substantive improvements. Material integration gaps remain in render/reset publication, local source retirement, and capture-controller initialization. Root read ordering and actual source-time freshness remain explicitly open.

This is one separate correction report. It does not change the original round-1 report or its 6/10 score at mobile44172/shared084f. The coordinator owns the section's cumulative round count and any next correction. Route and effort: coordinator-confirmed `openai-oauth:gpt-6-astra`, `high`. The root completed the routing ritual and approval. This independent reviewer did not repeat it or start workers.

Review began 2026-09-08 08:02:49 UTC. Finite product source analysis ended at 08:11:12 UTC. Artifact preparation and reporting remained within the 15-minute limit.

| Identity | Exact object |
|---|---|
| Reviewed mobile commit | `35d8c98e0c3dbaab1f10dcfb841a87792ba6a260` |
| Reviewed mobile tree | `7620ef661c152a3282acfb875630ab6b3dfbb706` |
| Reviewed shared commit | `0ffd658d7f19e68180c2720e0500b23644619e90` |
| Reviewed shared tree | `51f828e60c5bbfd7003694bf28b7455daf07db1e` |
| Baseline mobile commit | `44172dcc1ea8d91ac1fe8783cbeb68f5c3eb3d68` |
| Baseline shared commit | `084f5d9612f7f699bb80b08607845982d1579861` |
| Preserved baseline report blob in reviewed mobile | `36d473fea39c3b60af878810afd3f871d5961ef9` |

All product reads used these Git objects, not live source files. Only this report and its private readset receipt were written. The receipt records exact blob/SHA256 identities, selected regions, baseline-diff inputs, and discovery-only tree scope. Whole-blob hashing does not claim whole-file review. Large truncated outputs were followed by focused reads of the relied-on regions.

Receipt: `/home/ben/.jcode/scratch/R15-section05-round02-20260908T080249Z/READSET.sha256.txt`

Receipt SHA256: `a4db91a5590834a532806a1da7d7548871838d269af22c6bb71db32dc96dd38d`

Path shorthand: `K/` is mobile `app/src/main/kotlin/dev/phosphor/mobil3/`. `T/` is mobile `app/src/test/kotlin/dev/phosphor/mobil3/`. `G/` is shared `crates/phosphor-render-gpu/`. Other `rust/` paths are mobile. Shared audio paths are written in full. Every line reference below is at the reviewed commit for that repository.

## Per-finding disposition

| Baseline finding | Correction disposition | Remaining requirement gap |
|---|---|---|
| F1 capture read crosses resume | App AudioRecord/mic producer-read seam corrected at source with production-primitive tests | Root producer epoch/acknowledgement, AudioFlinger age and buffered network age remain open. Render-side stale candidates are separate F2 integration gaps. |
| F2 retired source GPU energy | Public clear operation and sequential pixel regression supported. Integrated requirement remains unresolved | Clear can precede local producer retirement. LIVE visibility and in-flight render candidates are not ordered with the visual reset. |
| F3 old remote M pauses replacement | Exact baseline remote retarget/disconnect trace corrected at source | Tests cover History predicates and source wiring, not the full threaded runtime barrier. Reset completion still has F2 races. |
| F4 initial PAUSED manufactures HOLD | Correct for fresh native History. Partial across actual transport-owner replacement | Capture controller B's initial snapshot can inherit controller A's transport baseline and manufacture an edge. |
| F5 HUD lacks independent LIVE | Source correction supported | Physical target geometry, accessibility and unchanged audio/position need Android acceptance. |

## F1: app pre-read owner and epoch fence

`K/CaptureReadFence.kt:12-16` samples the epoch before the blocking read and retains it with the entire returned batch. CaptureService retains its activation token at `255` and passes it with the sampled epoch at `267-271`. MicController does the same at `75-99`. Neither caller samples a replacement owner during publication.

JNI types agree at the inspected seam: `K/PhosphorNative.kt:101-109` uses Long owner/epoch and a Long activation result. `rust/src/jni_glue.rs:360-395` uses jlong, copies only a valid positive count within the Java array, and delegates to the production acceptance primitive. Copying before locking is acceptable because validation follows copying. The owner and epoch casts preserve their bit patterns.

`rust/src/engine.rs:115-143` locks ring then meter, checks nonzero owner, exact capture owner, active state and original read epoch, then pushes without releasing either guard. Activation installs a unique nonzero token at `73-78`. Generic StereoWindow replacement clears that token. Measurement-only reset at `81-84` preserves it. Counter exhaustion rejects publication with owner zero instead of reusing an owner. `rust/src/deck.rs:42-55` installs source state under the same ring/meter order. Publication cannot activate a source.

Finite corrected trace: read begins at epoch 4 with owner A, resume changes epoch to 5 under the publication lock, read returns, publication rejects epoch 4. A new epoch-5 read from A is accepted. Replacing A with B without changing epoch independently rejects A.

Tests read: `rust/src/engine.rs:153-238` has four production-primitive tests for held-read resume, same-epoch owner replacement, inactive/noncapture rejection, and publication waiting behind the ring guard. `T/CaptureReadFenceTest.kt:12-91` runs the production pure loop with a real held thread, preserves the whole 960-sample batch's epoch, and checks zero reads, stop-during-read, negative and thrown terminal failures. Five existing WakeOwnershipTest adapters change signature only. These are useful host checks, not AudioRecord or JNI runtime execution by this reviewer.

Root remains samples-only at `K/CaptureService.kt:343-346`, `rust/src/jni_glue.rs:345-356`, and `rust/src/deck.rs:38-39`. The pinned handoff `section-05-capture-fence-handoff.md:54-60` explicitly requires producer coordination and acknowledgement. This review neither tags receipt time as source time nor claims a root protocol correction. App read ordering cannot establish the age of already-buffered AudioFlinger samples. Remote receive fences remain after payload reading at `rust/src/remote.rs:1401-1415`, so buffered network source age is also unproven.

## F2: correct clear primitive, incomplete boundary transaction

### Supported correction

`G/src/lib.rs:815-835` clears both live energy views using render-pass clears, submits in the renderer queue, and resets the current ping-pong side. It allocates no replacement energy textures, performs no CPU readback or wait, and does not mutate separately retained resources. `rust/src/render.rs:664-683` calls it before fresh-path DSP reset and sample consumption. HOLD returns before this branch at `606-662`.

`G/tests/retained_frame.rs:207-230` starts with nonzero emission, demonstrates that empty advance alone preserves it, clears both live sides, checks opaque black through three subsequent reads/empty advances, and checks byte-identical retained pixels throughout. Fixture construction uses a real 64x48 Rgba8Unorm offscreen renderer at `17-46`. This is strong evidence for the operation when correctly sequenced. `T/HoldRenderBoundaryTest.kt:22-33` checks source wiring, not scheduler behavior.

### F2-R1, P2: LIVE becomes visible before the clear request

Source: `rust/src/pause.rs:96-111`, `rust/src/render.rs:180-182,591-600,664-671,938-959`, `rust/src/pause.rs:41-46`.

1. HOLD owns a retained image and old live energy remains allocated.
2. Resume changes History.paused to false and clears visual ingress while holding ring, meter and DISPLAY.
3. Those locks are released before `finish_visual_reset` stores VISUAL_FRESH.
4. Render observes LIVE and consumes a false VISUAL_FRESH in that interval.
5. It advances the uncleared energy, presents, and commits under the unchanged History generation.
6. Resume publishes its delayed clear request. Before another live render consumes it, a subsequent pause can pin the stale resumed commit.

This is a source-permitted interleaving, not an observed phone failure. Queue ordering inside clear_energy cannot repair a clear that the render never requested for this frame.

### F2-R2, P2: an in-flight old candidate survives a complete HOLD/LIVE cycle

Source: `rust/src/render.rs:591-600,683-693,814-916,938-959`, `rust/src/pause.rs:41-46,96-109`.

1. A live render snapshots source generation G and drains old samples A.
2. It is delayed before completing its advance/present.
3. Another thread completes HOLD then LIVE. Resume advances VISUAL_EPOCH, clears ingress and publishes VISUAL_FRESH, but does not change History.generation.
4. The old render completes using A and calls commit(G).
5. History is now LIVE at generation G, so commit accepts this obsolete candidate.

The candidate carries only source_generation, not the visual epoch of its drained work. Moving the fresh flag inside the lock alone does not address this trace. The next frame may clear live energy, but that does not prevent the stale first resumed present or a pause pinning its commit. This is not a demand for physical panel timing. It concerns application present and retained ownership.

### F2-R3, P2: explicit local stop permits deposits after its only clear

Source: `rust/src/deck.rs:180-192,327-342,47-55`, shared `crates/phosphor-audio/src/playback.rs:727-758`, `rust/src/pause.rs:115-125`, `rust/src/render.rs:664-693,938-959`.

1. Local source A is playing. deck::close calls invalidate before close_session stops the producer.
2. Invalidate publishes the fresh request and replaces History. Suspend the stop thread before close_inner finishes producer retirement.
3. Render consumes the request and clears live GPU energy.
4. The still-live PlayerSession publishes another A chunk to the same scope ring. Its scope push follows audible push and has no visual-retirement token.
5. Render deposits A and can commit it under the already-new History generation.
6. Complete producer stop and set_ring_state(false). That final state replacement requests no second GPU clear.
7. A subsequent no-source advance can retain residual A energy, and HOLD can pin it.

This trace does not require an illicit late app AudioRecord callback. It uses the actual local producer connection. The earlier split inside invalidate, which calls fresh_visual_ingress before replacing History, further shows that the fresh request and History generation are not one publication. A rejected old-generation commit alone also cannot erase pixels already deposited into live energy.

**Smallest corrective requirement:** make producer retirement, visual reset publication and eligible retained-frame identity one coherent boundary. Revalidate the visual identity of in-flight work, and ensure that obsolete deposits cannot survive into a later accepted candidate. Keep GPU work outside CPU ownership locks. Keep audible queues unchanged by display-only resume. A source-only counter or a flag reorder alone does not satisfy all three traces.

**Finite missing checks:** hold render after drain and around fresh-flag publication, complete HOLD/LIVE, then release it. Separately suspend local stop after invalidation while a final producer chunk arrives. Assert obsolete candidates are not committed and their energy cannot reappear after the first new boundary frame. Preserve the standalone pixel test as a separate operation-level check.

## F3: native remote observation fencing

`rust/src/remote.rs:603-609,618-626` advances remote intent generation and trips the current session before invalidating History. `1415` captures History generation before M parsing. `1456-1467` supplies that generation and the exact SessionShared liveness closure to observe_transport_from. The liveness check includes cancellation, quit and matching session generation at `198-203`.

`rust/src/pause.rs:96-105,212-213` applies the generation/liveness predicate, transport observation and display transition while holding ring, meter, then DISPLAY. `History::transition:55-62` rejects obsolete predicates before mutating the baseline. A retarget that wins first makes the old callback fail. An observation that wins first can modify only the old History, which later invalidation discards. It cannot adopt the replacement History through the old baseline trace.

`K/RemotePlayer.kt:240-245` no longer repeats the native display observation from its tokenless metadata callback. Metadata UI provenance outside that specific display write was not redesigned or scored as corrected.

No reversed DISPLAY-to-ring acquisition was found in the inspected pause, render, JNI and source-publication sites. The remote closure reads atomics rather than acquiring a second pointer lock. Retired Arc ownership leaves the transaction before destruction. GPU work and transport dispatch do not run under DISPLAY. Added ring/meter contention remains CPU work, not a demonstrated Android latency bound.

Tests: `rust/src/pause.rs:152-198` directly exercise History initialization, edges, obsolete generation/retired-session booleans and preservation of explicit pause. They do not execute apply_transition, a real SessionShared, retarget, or a threaded runtime barrier. `T/HoldRenderBoundaryTest.kt:36-48` is a textual source assertion. F3's specific old-M-to-new-History mutation is source-corrected, while F2's post-lock reset ordering remains open.

## F4: initialization is not yet per actual transport owner

Fresh History is corrected. `rust/src/pause.rs:58-62` stores an initial observation without pausing or resuming. It suppresses duplicate observations and applies later edges. Explicit pause is unchanged. The four History tests at `152-198` check both initial states, later transitions, predicate rejection and initial observation during explicit display pause.

### F4-R1, P2: capture-controller replacement reuses the old baseline

Source: `K/PlaybackService.kt:438-512,515-525,590-596`, `K/CaptureMirrorPolicy.kt:36-45`, `rust/src/pause.rs:58-68,202-203`.

1. Capture mirrors controller A while A is PLAYING. Native transport_paused becomes Some(false).
2. A is destroyed. Its callback clears the binding, publishes null state and refreshes controllers.
3. Clearing CaptureControllerBinding changes only its current reference and callback generation. Null playback publication sends no native observation or baseline reset.
4. Refresh selects an already-PAUSED controller B and publishes B's initial snapshot at PlaybackService512.
5. Native History still carries A's Some(false). B's initial true observation is treated as a PLAYING-to-PAUSED edge and pins the display.

No pause transition from B is required. The Kotlin exact-binding guard protects stale callbacks but does not seed native transport state for the replacement controller. This is a finite continuation of the requested initialization/source-replacement obligation, not a speculative rewrite of capture routing.

**Smallest correction:** seed transport observation for a new exact binding without creating or clearing intentional display pause. Preserve subsequent authoritative edges and explicit pre-command pinning. Add a production binding-path test with A PLAYING, A removed, initial B PAUSED, duplicate B PAUSED, B PLAYING then B PAUSED. A pure fresh-History test alone does not cover that path.

## F5: independent HUD LIVE and target dimensions

`K/FloatingHudService.kt:169-173` places FIT and LIVE in a separate 48dp inspection row. The remaining transport row has four equal-width controls at `174-188`, rather than adding a sixth control to the old row. At the intended 240dp minimum width, inspection controls receive 120dp and transport controls 60dp. Button defaults at `218-223` supply 48dp minimum width and 48dp height. This supports the requested minimum dimensions for an available area that can fit the configured HUD.

LIVE calls only setDisplayPaused(false) and syncTransport at `273-276`. Its enabled state depends solely on display pause at `281-282`, so it works for HOLD or BLACK regardless of source transport capability. The transport button retains controller play/pause at `177-183`. Content descriptions distinguish display-only action from source controls.

JNI setDisplayPaused dispatches only pause::set_paused at `rust/src/jni_glue.rs:854-859`. The downstream visual reset does not seek or clear audible queues. `T/HoldRenderBoundaryTest.kt:8-19` checks that wiring and the separate row. It does not measure actual Android targets or perform a click. The added row consumes 48dp of vertical scope area, which leaves approximately 48dp for the scope at the nominal 240dp total height with four fixed rows. Large fonts, smaller usable bounds, gesture continuity, accessibility focus and real audio/position continuity remain device checks, not established regressions.

## Requirement coverage and evidence boundaries

| Canonical section 5 requirement | Correction evidence and remaining disposition |
|---|---|
| Separate lifecycle, display, transport, line143 | Existing separation preserved. F3 predicate transaction improved. F4 transport-owner seed remains incomplete. |
| HOLD default and BLACK preference, line144 | Unchanged by correction. Baseline policy/archive evidence inherited. Install/migration/import behavior not newly verified. |
| Transport meaning and real acknowledgement, line145 | Display LIVE is separate. Pre-command semantics unchanged by inspected delta. Remote command rejection and Android notification/earbud paths remain unrun. |
| Truthful live-source state, line146 | HUD uses actual observed playback/active capture inputs at FloatingHudService283-293. Controller initialization has F4-R1. No new actual-device truth claim. |
| Retain last presented frozen image, line147 | Shared retention design unchanged. Clear preserves retained pixels in inherited synthetic evidence. In-flight resumed candidate eligibility has F2-R2. |
| Inspection/reset/return LIVE, line148 | Independent HUD LIVE corrected, with 48dp source geometry and isolated action. Actual touch/accessibility/settings-gesture checks remain open. |
| BLACK once and controls, line149 | Inspected render606-662 still uses held dirty branch and opaque BLACK. Actual transparent Android HUD behavior is inherited acceptance work. |
| HOLD/BLACK keeps image without transport, line150 | Inspected JNI885-886 changes mode and dirty event only. Baseline retention evidence unchanged. No new Android round trip. |
| Recreation, bounds, no history, line151 | Core retained ownership is unchanged from baseline. Correction does not establish allocation failure, rotation or app/PiP/HUD behavior. |
| Current-timeline resume and invalidation, line152 | App read fence improves F1. Root/source-time obligations and F2-R1/R2/R3 remain material. |
| Event-driven HOLD and transient retirement, line153 | HOLD still returns before live clear/DSP. Clear adds no product readback/export. Retired local emission can persist under F2-R3. Device idle/resource evidence remains open. |

Canonical anchors read: `MOBILE-EXPANSION-PLAN.md:141-153,294-310`. R13 spec read: `spec/EXPANSION.md:114-126`. Acceptance row remains unchecked at `spec/ACCEPTANCE.md:168`. The correction contract does not narrow canonical freshness or substitute source review for Android acceptance.

## Checks performed versus inherited

**This reviewer performed:** finite Git-object source and focused test-body inspection, baseline-to-target diffs, exact commit/tree/blob identity reads, SHA256 receipt generation, lock-order analysis, and finite failure/interleaving traces. Concrete F2 and F4 traces were sent early to the coordinator. No implementation was edited or executed.

**Inherited coordinator gate `4104942tt5`:** 521 Android unit tests with no failures/errors/skips, 91 locked native tests, three actual offscreen GPU tests, lint, dual debug APKs, engine integration, release helper and source-boundary checks. Both source manifests and file sets were unchanged. The pinned correction note at `35-50` carries the gate identifiers and hashes. Raw logs were not independently rerun or re-audited here. These are inherited host/synthetic evidence, not this reviewer's tests or Android acceptance.

The GPU pixel test does not execute Android Surface acquisition, invalidation scheduling, local producer retirement or render candidate commit. History tests do not implement the full remote threaded barrier. Kotlin source assertions do not prove target geometry or callback delivery. The held-reader fixtures exercise useful production primitives but not AudioFlinger sample timestamps.

Read-only baseline-to-target path comparison found no changes to root-helper, RootCaptureSession.kt or the canonical plan. No root protocol change is claimed. The reviewer made no product, tracked-file or Git mutation. No builds, tests, target execution, Android/device/GPU/GUI/network/audio/service or worker operations occurred. No protected system, vendor, boot or security surface was written.

## Score, uncertainty and bounded next step

R15's 7/10 anchor fits a mostly implemented feature with material gaps. Intent fidelity improves through real capture fences and independent LIVE. Functional accuracy improves for the GPU operation and remote observation predicate, but reset/retirement ordering and replacement initialization remain source-proven gaps. UX source geometry improves without actual accessibility acceptance. Lifecycle/performance/privacy structure remains promising rather than fully verified. A score of 8 would incorrectly describe these remaining gaps as minor polish. A score of 9 or 10 lacks the required integration evidence.

Largest intent mismatch: a supposedly fresh or retired visual boundary can still admit old energy into an accepted retained image. The source-time root gap is separately open and not excused by the renderer correction.

**Blocked:** whole R13 acceptance lacks coherent render/reset/retirement publication, exact transport-binding initialization, root producer-read coordination, true buffered-source freshness evidence and exact Android behavior.

**Evidence:** F2-R1/R2/R3 and F4-R1 provide line-backed finite traces. F1 and the acceptance table identify the explicit unproved source-time and device boundaries.

**Best current result:** corrected app capture publication, a validated-inherited GPU clear primitive, corrected specific remote stale-M fencing, correct fresh-History seed behavior and independent HUD LIVE. This report preserves the baseline and provides an exact separate readset.

**Next smallest step:** the coordinator owns focused reset/candidate and binding-seed corrections with finite barrier regressions, then a new immutable integration freeze if another review round is used. Preserve the source-time/root limitation and actual Android acceptance as separate obligations. Do not edit this report to score later code. All source reads and review ownership are released on completion. No live-source ownership remains necessary.
