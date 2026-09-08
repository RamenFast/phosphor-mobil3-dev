# R15 section 5 / R13 independent correction review, round 3

## Verdict and immutable identity

**Score: 7/10. R13 is not accepted.** Correction02 fixes the specific round2 retained-history/reset-publication failures and the capture-controller baseline defect at source. Freshness is still materially incomplete. Root publication and actual buffered-source age remain open. An old, already-drained render can still make its first application submission after a completed resume or retirement, although its retained commit is now correctly rejected.

This is the third section-level review, not a revision of earlier scores. The original round1 remains 6/10 and immutable round2 remains 7/10. The coordinator owns the maximum of four total rounds. No later source or unrelated color work is scored here.

Route and effort: coordinator-confirmed `openai-oauth:gpt-6-astra`, `high`. The routing ritual and Ben approval preceded this worker. The independent reviewer did not implement these corrections, repeat the ritual, start workers, or execute the product.

Review began 2026-09-08 08:19:56 UTC. Finite source analysis ended at 08:24:59 UTC. Report preparation and sealing followed within the 15-minute limit.

| Identity | Exact object |
|---|---|
| Reviewed mobile commit | `0591d7345cdbc701dd2549ef62037959c1fbdaca` |
| Reviewed mobile tree | `309d81671a186dd88a3be77bedc924c9d369913a` |
| Reviewed shared commit | `0ffd658d7f19e68180c2720e0500b23644619e90` |
| Reviewed shared tree | `51f828e60c5bbfd7003694bf28b7455daf07db1e` |
| Prior reviewed mobile | `35d8c98e0c3dbaab1f10dcfb841a87792ba6a260` |
| Prior reviewed shared | Same `0ffd658d7f19e68180c2720e0500b23644619e90` |

Product evidence came only from these Git objects and the requested prior reports. The mobile comparison was `35d8..0591`. The shared GPU operation is unchanged from round2. Whole-blob hashing identifies exact inputs, not a claim to have reviewed every line. The receipt identifies full small modules, selected regions, diff inputs and discovery-only scope. Truncated large outputs were followed by focused reads of relied-on regions.

Private report directory: `/home/ben/.jcode/scratch/R15-section05-round03-20260908T081956Z/`.

Readset: `READSET.sha256.txt`. SHA256: `cfd10527fd07f6ab414c4871ac9a9ea63dfe77692628864624ad70bf1402bf63`.

Prior round2 report SHA256 independently checked: `e0d96fcbc7556c20528074112adc01903a7341f8947153bf47db16689c285f3b`. Its receipt SHA256: `a4db91a5590834a532806a1da7d7548871838d269af22c6bb71db32dc96dd38d`. Neither file was changed. The original round1 was read from its blob in mobile0591.

Path shorthand below: `K/` is mobile `app/src/main/kotlin/dev/phosphor/mobil3/`. `T/` is mobile `app/src/test/kotlin/dev/phosphor/mobil3/`. `G/` is shared `crates/phosphor-render-gpu/`. Other `rust/` paths are mobile. All line references use the reviewed commit for their repository.

## Finding dispositions

| Existing finding | Round3 source disposition | Remaining boundary |
|---|---|---|
| F1 source-time freshness | App pre-read owner/epoch fence remains supported and compatible with the new transaction | Root remains samples-only. App AudioFlinger and pre-fence network age are not established. |
| F2-R1 split LIVE/reset publication | Specific race corrected | LIVE and its visual revision now share History publication. No delayed reset boolean remains. |
| F2-R2 old retained candidate after HOLD/LIVE | Specific accepted-HOLD contamination corrected | Commit validates generation and visual revision. Old application submission can still occur before rejection, detailed below. |
| F2-R3 local deposits after the only retirement clear | Specific persistent-residue trace corrected | Final invalidation follows decoder join and inactive publication. Already-drained render work still has the application-submission boundary below. |
| F3 stale remote M changes replacement | Round2 correction preserved in inspected interactions | Native predicate remains inside ring, meter, History transaction. Full Android callback/retarget execution is not proven. |
| F4 replacement initial PAUSED inherits A's baseline | Specific A-to-B trace corrected at source | Real Android controller callback delivery remains an acceptance check. |
| F5 independent HUD LIVE | Round2 source correction preserved | Actual touch/accessibility and audio/position continuity remain unrun on Android. |

## Remaining material findings

### F1, P1 acceptance gap: producer/source-time freshness is still open

**Requirement:** canonical `MOBILE-EXPANSION-PLAN.md:152`, current-source-timeline resume without accumulated capture backlog. `spec/EXPANSION.md:124-126` preserves, rather than narrows, that requirement.

**Root seam:** `K/CaptureService.kt:343-346` calls samples-only `pushCaptureSamples`. `rust/src/jni_glue.rs:345-356` copies and delegates to `rust/src/deck.rs:38-39`, which pushes under the ring lock with no read epoch or producer token validation. Root session ownership at the Kotlin callback does not identify when its samples were produced.

Finite root trace, unchanged by correction02:

1. The root producer obtains an old marker A while display HOLD is active. A remains in the producer/pipe/app delivery path.
2. Resume runs `pause.rs:132-141`. It increments visual revision, publishes the epoch and clears pending visual samples under the ring guard.
3. The same still-live root session delivers A afterward. Kotlin's running/session/owner predicate passes.
4. The samples-only native path pushes A after that clear. It has no old read epoch to reject.
5. A new-token render clears GPU energy and then drains A at `render.rs:672-682`. Its token is current, so the later retained commit can accept this genuinely old ingress.

This is distinct from F2's already-drained render. The new render token cannot establish a sample's producer time.

**App seam is corrected, but narrower:** `K/CaptureReadFence.kt:12-16` samples the epoch before each read and carries it with the whole batch. CaptureService uses the original activation owner at `255,267-271`. MicController does the same at `75-99`. JNI passes both values at `jni_glue.rs:368-384`. The production predicate and push are one guarded operation at `engine.rs:127-144`.

Finite supported app trace: read starts with epoch4/ownerA, resume publishes epoch5 under ring/meter/History, then publication of the held epoch4 batch fails. A fresh epoch5 batch from ownerA can pass. `clear_visual_measurement` at `engine.rs:81-84` leaves ownership intact, so changing the visual revision does not disable the continuing capture producer.

A separate finite source-age possibility remains: AudioFlinger holds samples produced before resume, a read begins after resume with epoch5, and that read returns the buffered samples. The app fence correctly proves call ordering, but it has no production timestamp that distinguishes this case from current input. This review does not assert a measured buffer age or demand physically zero transport latency. It preserves the canonical backlog/current-timeline obligation as unverified.

`remote.rs:1401-1415` similarly reads the whole payload before sampling its visual epoch and display generation. A same-session payload already buffered before resume can receive a new epoch afterward. Exact owner fencing does not prove that payload's source age. No audible queue flush or relay redesign is implied by this finding.

**Smallest follow-up:** retain the bounded producer-coordinated root epoch/acknowledgement and epoch-bearing PCM work described in `section-05-capture-fence-handoff.md:54-60`. Then establish actual buffered-source timeline evidence separately for root/app/network paths. Receipt timestamps are not a substitute. Keep visual recovery separate from audible queues, local position and remote jitter.

**Required finite checks:** hold root production/delivery across resume and reject the old marker while accepting the next coordinated marker. Separately distinguish genuinely pre-boundary buffered samples from post-boundary samples using source-time evidence. The inherited host gate does not perform these checks.

### F2-R4, P2: an old render can first submit and present after the new boundary

**Requirement:** canonical fresh resume/source retirement at `MOBILE-EXPANSION-PLAN.md:152`. The application-present identity is explicitly distinguished from physical scanout at `spec/EXPANSION.md:116` and `section-05-hold-contract.md:13-15`.

**Source:** `render.rs:579-588,652-682,803-818,863-903,927-948`, `pause.rs:76-81,106-109,132-158`. Shared `G/src/lib.rs:528-546,672` shows that `advance` itself submits GPU work. This is not only a later retained-texture copy.

Finite same-source resume trace:

1. A live frame snapshots token `(G,V)` at `render.rs:579-588`. Its energy token already matches, so it needs no clear.
2. The renderer drains old samples A at `672-682`. Suspend that thread immediately after the drain, before DSP/advance. No driver operation or queue submission for this frame has happened yet.
3. Another thread completes HOLD then LIVE through `pause.rs:132-145`. History is now LIVE at `(G,V+1)`, the producer epoch is published, visual ingress is cleared, locks are released, and the call returns.
4. Release the renderer. It computes A and calls `advance` at `863-903`. Shared `advance` submits those old deposits at `G/src/lib.rs:672`.
5. It retains/composites A and calls `queue.submit` then `present()` at `render.rs:927-942`. Neither operation revalidates the frame token or serializes submission with boundary completion.
6. Only afterward does `History::commit` reject `(G,V)` at `render.rs:944-947` and `pause.rs:76-78`.
7. The next new-token live frame requires a clear at `render.rs:652-659`. Thus the stale candidate does not become accepted new HOLD history, and its live energy does not survive the first new-token clear.

Finite local retirement variant: suspend step2, run `deck::close_inner` through actual producer join, `set_ring_state(false)` and final `pause::invalidate`, then release the renderer. The joined decoder cannot publish anymore, but previously drained render samples still reach a new application submission. Their commit is rejected under the new generation/revision. The next eligible frame clears them.

**What this finding does not claim:** it does not revive round2's stale accepted retained-image trace. The new commit token prevents that. It does not claim old GPU energy survives the first correctly sequenced new-token frame. It does not demand observable physical scanout or forbid the explicitly disclosed brief in-flight image on entry to HOLD.

The narrower mismatch is that work can be submitted for the first time after resume or retirement has completed at the application's CPU boundary. It is not merely a previously submitted image arriving at panel scanout. The unchanged HOLD contract admits a concurrent in-flight image before pinned redraw at line15. That disclosure should not be presented as proof of fresh-resume application ordering. Correction02's reference to physical scanout at line13 does not close this distinct trace.

The source establishes that this schedule is permitted. It does not establish how often Android schedules it, how long the old image remains visible, or actual panel behavior. This is a transient application-output defect, not a durable retained-history defect.

**Smallest corrective requirement:** establish a render-owner submission boundary for fresh LIVE/retirement, with an honest distinction between a requested CPU state change and completed fresh presentation. Validate eligible work as part of that ownership protocol. Do not hold DISPLAY, ring or meter across GPU submission, driver calls or present. Do not delay urgent audible pause for GPU work. A standalone token check before submit, outside a coordinated ownership protocol, only moves the check-to-submit race.

**Finite missing check:** hold the actual render/submission path after its drain but before its first advance submission. Complete resume or final retirement, release it, and record application submission eligibility separately from retained commit acceptance. Keep the existing offscreen clear test and History barrier tests as distinct checks. No such integrated test was run by this reviewer or established by the inherited counts.

## Supported correction traces

### F2-R1: LIVE and reset identity are now one publication

`pause.rs:132-141` acquires ring then meter through `engine.rs:115-123`, then History. `History::transition` changes LIVE and visual revision under that History lock. Before the outer guards are released, the same transaction publishes `VISUAL_EPOCH`, clears pending visual samples and clears measurements. Retirement performs the corresponding generation/revision replacement at `pause.rs:148-158`.

The renderer takes display state and `FrameToken` from one History snapshot at `render.rs:579-588`. It compares that token against its own `EnergyEpoch` at `652`, calls `clear_energy` at `653`, and records acknowledgement only at `659`. There is no separate delayed VISUAL_FRESH flag to miss.

Finite lock ordering: if render snapshots first, it carries the old token and cannot later commit into changed History. If transition publishes first, render observes LIVE with the new revision. It cannot observe new LIVE with the old revision from the split round2 publication. Sample draining waits for the ring guard, so it cannot bypass an unfinished visual ingress clear. GPU work remains outside the ownership locks.

### F2-R2: stale retained commits and persistent energy contamination are rejected

`pause.rs:76-81` validates both token fields and paused state under History. Resume increments visual revision at `106`, while retirement increments both fields at `61-68`. The render thread is the sole inspected owner of its EnergyEpoch and GPU advance/clear sequence.

Finite trace: render holds old token T, boundary produces T2, old render deposits and tries to commit T, commit rejects T, then the next live frame observes T2 and clears before advancing. If the display is paused instead, the held branch returns before live advance and cannot accept T. No second concurrent renderer can insert an old-token deposit after this thread's T2 clear. This closes the specific old-image-to-new-HOLD and residual-energy traces without claiming the transient submission behavior above is fixed.

Geometry remains a bounded latest mailbox. `render.rs:164-169,661-670` checks current visual epoch and exact live owner at publication/consumption. Removing the old mailbox clear does not turn an old-epoch packet into an eligible new-epoch one. An old render that already passed these checks has the same explicitly separate in-flight submission boundary.

### F2-R3: final local invalidation follows actual producer retirement

`deck.rs:327-342` now calls `close_inner(true)`, removes the deck, calls `close_session`, publishes inactive ring state, then invalidates History. `deck_close.rs:3-13` closes audible input, sends Stop, stops the stream and joins the actual producing thread. The shared producer's scope push at `crates/phosphor-audio/src/playback.rs:749-754` occurs before that thread can finish and be joined.

Finite ordering: a decoder chunk already past audible push can still reach the scope ring while close is joining. Close cannot publish its final revision until the thread is finished. Final invalidation then clears any still-pending chunk. A renderer that already drained that chunk carries an older token, is rejected for retained commit, and is cleared before a later new-token advance. This fixes the old schedule where the only clear occurred before the producer's last chunk and no later reset existed.

The existing same-item path at `deck.rs:173` still passes `!same_item` into close. The correction does not add invalidation to the explicit preserve-history seek path or change audible pause into a visual-only action.

### F4: initial observation is now scoped to the exact capture binding

`K/CapturePauseObservation.kt:8-17` resets only its baseline on bind, rejects null/stale owners and unknown states, seeds the first known state without emitting an action, and emits only later changes.

Production wiring matters here. `K/PlaybackService.kt:474-476` binds the observation adapter alongside `CaptureControllerBinding`. Callback registration uses `main` at `504`. The callback checks `captureActive && isCurrent()` before `observeCapturePause` at `487-490`. Observation precedes metadata on that callback path. Both adapters clear at `518-519`, before unregistering the old callback. Register failure and session destruction also call this clear path at `494-506`.

`CaptureControllerBinding` uses both exact object identity and a per-bind generation at `K/CaptureMirrorPolicy.kt:36-45`. A stale callback from an earlier bind of the same object fails that generation guard. The observer receives the current controller at `PlaybackService.kt:598`, but only after this callback guard on the callback path. Snapshot publication uses the current binding at `512-513`. The repeated observation inside `publishCapturePlayback:606` is a duplicate and produces no second action.

Finite corrected A/B trace:

1. Bind A and observe PLAYING. The adapter seeds false without changing display pause.
2. A is destroyed. The callback guard passes for A, then both bindings are cleared.
3. Bind already-PAUSED B. Its initial true observation seeds B's baseline and performs no native pause.
4. B PLAYING emits false, then B PAUSED emits true through `setDisplayPaused`.
5. A's queued callback fails the old binding generation. B duplicates and unknown states emit no extra action.

This corrects the precise round2 replacement-baseline defect without resetting audio, source identity or explicit user HOLD. No Android callback execution is claimed.

### F3 and F5: preserved material interactions

Remote connect/disconnect still advance intent generation and trip the old session before invalidation at `remote.rs:603-609,618-626`. M records display generation before parsing at `1415`, and passes it with the exact liveness closure at `1463-1467`. `pause.rs:339-340,132-140` runs the predicate/transition inside the same ordered transaction. An old observation that wins first affects only old History, which retirement discards. An invalidation that wins first causes the old generation/liveness predicate to reject before changing the observation baseline. The new visual revision does not weaken this source-generation fence.

HUD FIT and independent LIVE remain in the separate 48dp row at `K/FloatingHudService.kt:169-173`. LIVE calls only `setDisplayPaused(false)` and refreshes chrome at `273-275`. Enablement depends on display pause at `281-282`, not transport controllability. JNI at `jni_glue.rs:854-859` dispatches only the visual pause transition. The revised transition preserves capture ownership and does not flush audible queues. Physical target dimensions, accessibility focus and real audio/position continuity remain device acceptance, not source-proven measurements.

## Tests inspected and evidence limits

- `pause.rs:245-303` uses real host threads and channels around the actual generic History snapshot/commit methods. It checks stale commit rejection after HOLD/LIVE and retirement. It then separately exercises the actual EnergyEpoch primitive. It does not run the Android render loop, sample drain, `apply_transition`, GPU advance or application present.
- `pause.rs:305-324` checks repeated resume/retirement reset-token behavior. Existing tests at `196-242` preserve initial observation, real edges, rejected old predicates and explicit pause semantics.
- `deck_close.rs:24-61` exercises actual close_session with a real thread blocked on an AudibleRing push. It proves close-before-join behavior. It does not instantiate Android deck.rs and a concurrent renderer through final invalidation.
- `T/CapturePauseObservationTest.kt:10-45` has three production-adapter behavior tests. Its fourth test at `48-56` is source wiring. It does not instantiate PlaybackService or deliver Android callbacks.
- `T/HoldRenderBoundaryTest.kt:8-60` has four source-text assertions for HUD, clear ordering, remote wiring and local close ordering. Those are integration wiring checks, not scheduler or Android behavior proofs.
- `engine.rs:153-238` uses the production app capture acceptance primitive for held-read, owner replacement, inactive rejection and ring-lock recheck. Its boundary fixture changes the supplied epoch under the shared ring guard. It does not timestamp AudioFlinger samples.
- `G/tests/retained_frame.rs:17-46,207-230` exercises the real GPU operation on a synthetic 64x48 Rgba8Unorm offscreen fixture. It demonstrates that empty advance preserves old emission, clear removes both live sides, later empty advances do not revive it, and retained pixels stay byte-identical. This is unchanged from round2 and does not execute Android Surface/pause/retirement scheduling.

### Inherited coordinator gate, not reviewer execution

At 08:21:51 UTC the coordinator reported exact-target gate `514919zi9y` completed at 08:21:27 with exit0. Both source manifests were unchanged before/after and the isolated exact Git pair was clean. Reported results: **526 JVM tests across 46 suites, zero failures/errors/skips, 94 native tests, three actual offscreen GPU tests, lint, both debug APKs together, checkEngine, release-helper and source-boundary checks passed.**

This supersedes the initial pending-gate status for host integration only. The earlier 94-native/eight-cached-JVM snapshot was also supplied, not executed here. Raw gate files were not independently read or re-audited. Supplied location: `/home/ben/.jcode/scratch/hold-freeze-0591d73-0817`.

| Coordinator-supplied artifact | SHA256 |
|---|---|
| Source archive | `382cbcc3284a924602f9caf23bc3bc57fc0cc676928efe463d4576ecb7d58234` |
| App debug APK | `dee32897b98e61f23d995b345d38bd35ab893b6ee963bb4050a2966ae4fe9977` |
| Test debug APK | `f0f741092e6a077f949ddc4a56d12e2ed9162257fb6867968be1b46806527a1d` |

These are inherited host/synthetic checks and compilation artifacts. They are not an install, device acceptance, Android callback execution or fresh-source-time proof. This reviewer ran no builds, tests, target program, Android/ADB, GUI, GPU, audio, network, service or worker operation.

## Canonical requirement coverage

| Section5 line and requirement | Evidence and honest disposition |
|---|---|
| 143, separate lifecycle/display/transport | Separate render pause, History and transport adapter remain. Corrected F4 seed and preserved F3 fence support source semantics. Actual Android owner transitions unrun. |
| 144, HOLD default and BLACK setting | Unchanged correction scope. Original policy/archive review inherited. No new install/migration/import acceptance. |
| 145, source-specific transport and acknowledgement | Visual transaction changes no audible queue or command. Local close stop/join ordering inspected. Rejected external commands, notification and earbud delivery remain unrun. |
| 146, truthful live source and distinct interruption | F4 no longer makes B's initial PAUSED into an edge. HUD reads observed state/active capture at FHUD283-293. No device truth claim. |
| 147, last presented frozen frame | Token-checked History and immutable shared clear support retained identity. F2 accepted-old-candidate trace closed. Transient application submission boundary remains separate. |
| 148, pan/zoom/reset/LIVE without live tuning changes | HUD LIVE and JNI inspection isolation at jni_glue898-913 inspected. Original app gesture evidence inherited. Physical gestures/accessibility/settings priority remain open. |
| 149, BLACK once with controls | Held dirty branch at render594-650 clears opaque black at632-645 and returns before live work. Android transparent HUD pixels/control availability remain acceptance work. |
| 150, mode change preserves image and transport | JNI885-886 changes black and dirty event only. New revision changes do not make mode switching a transport action. Device round trip unrun. |
| 151, recreation/bounds/no history | Retained ownership and surface paths unchanged in the focused delta. Original bounds/recreation review inherited. Android rotation/PiP/HUD, allocation failures and process death not newly verified. |
| 152, current timeline and source retirement | Specific reset/retained contamination and local final retirement corrected. F1 and F2-R4 remain material. No device current-timeline acceptance. |
| 153, event-driven HOLD and transient retirement | Render307-320 and594-650 keep unchanged HOLD out of live drain/advance. Single-owner clear removes retired energy before new-token advance. Driver idle/resource timing and complete source-time boundaries remain open. |

`spec/ACCEPTANCE.md:168` remains unchecked. Every canonical section5 obligation is mapped here, but several use explicitly inherited source coverage or unrun acceptance checks. Aggregate passing tests are not complete requirement verification.

## Score rationale, next step and release

R15 at `MOBILE-EXPANSION-PLAN.md:294-310` requires one overall rating, not an arithmetic test-count score. **7/10** fits a mostly implemented feature with material gaps. Intent fidelity improves through coherent visual revision and exact controller initialization. Functional accuracy improves for retained identity and producer retirement, but canonical freshness and post-boundary application output remain incomplete. UX/source controls are preserved without actual accessibility acceptance. Lifecycle/performance/privacy structure remains bounded and avoids new image readback, export or audible-queue changes, but actual Android behavior is not established. A score of8 would mislabel these gaps as minor polish.

Largest intent mismatch: returning LIVE still cannot guarantee joining the current source timeline. Old root/buffered-source samples can be accepted as new ingress, and old already-drained render work can still be newly submitted after the CPU boundary. These are separate paths and need separate evidence.

**Blocked:** whole R13 acceptance lacks root producer coordination, actual buffered-source timing, corrected/verified fresh application-submission ordering and exact Android behavioral acceptance.

**Best current result:** source-supported closure of round2 F2-R1/R2/R3 retained/reset defects and F4 replacement initialization, preserved F3/F5 corrections, inherited clean exact host integration, and a separate immutable report with exact readset.

**Smallest next step:** the coordinator owns any bounded fresh-submission correction and finite render-owner barrier check. Keep root/source-time and actual Android acceptance as explicit separate obligations. If a fourth critique is used, review a new immutable pair after checks. Four rounds means four total, and moving on after the cap does not mark these gaps accepted. Do not amend this report or earlier reports to score later source.

Only this new private report and its readset were written. Both are sealed mode444 and their exact hashes are supplied on completion. No product or Git changes were made. No live source ownership was taken from the color writer. **All source reads and review ownership are released. The coordinator may stop this worker.**
