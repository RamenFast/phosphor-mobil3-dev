# ADDENDUM: section05 correction04 application-present acknowledgement

## Narrow disposition

**Completed, source-supported with explicit semantic qualifications.** Correction04 supplies an honest asynchronous application-present acknowledgement while LIVE remains requested. Stale, superseded, paused, failed-acquire, failed-retention and no-present work cannot acknowledge the current token. The inspected single render owner cannot first-submit an earlier candidate after acknowledging the current token.

This does **not** eliminate old first submissions after the CPU request. It exposes a later completion boundary while allowing those submissions during pending. It does not establish physical scanout, successful GPU execution, Android callback delivery or captured-source age. **R13 is not accepted or rescored here.** Original scores 6/7/7 remain unchanged. This addendum does not consume the reserved fourth full review.

Important qualification: the exported pending bit means **LIVE requested and its token not acknowledged**, not a durable completion record independent of HOLD. Entering HOLD makes pending false without acknowledging the interrupted LIVE request. The private `presented` token remains unchanged and HOLD pins the last committed image. This matches correction04's intervening-HOLD check, but an unqualified claim that “only present clears the bit” would be false.

## Immutable identity and method

Route requested and enforced by the task: `openai-oauth:gpt-6-astra`, explicit `high`. No workers, new routing ritual, product edits or Git mutation occurred.

| Input | Exact identity |
|---|---|
| Mobile | `73e13eaf6a65df94557f8eb8107e5a44252a13bd` |
| Mobile tree | `2bb54a5395a19da65817c4fbeecafd25a57b4c8c` |
| Shared | `0ffd658d7f19e68180c2720e0500b23644619e90` |
| Shared tree | `51f828e60c5bbfd7003694bf28b7455daf07db1e` |
| Compare mobile | `79af079dc5e4ef3c70f2349c692ecfaa6a86ee88` |
| Compare tree | `e09f37a8d10fc4f0f07487758a89c566c2b636df` |

Review started 2026-09-08T09:53:22Z. Finite product source analysis ended 09:58:49Z. Only immutable Git objects and private sealed snapshots supplied product bodies. Whole-blob hashes identify inputs, not a whole-file review. `READSET-SCOPE.md` records relied-on regions and discovery-only inputs. The correction-only comparison confirms `render.rs` is byte-identical to the compare revision. Its actual unchanged ordering, not a proposed design, carries the new acknowledgement.

Private directory: `/home/ben/.jcode/scratch/section05-correction04-addendum-20260908T095322Z/`.

`READSET.sha256.txt` SHA256: `217920d32de3a0ea46ecc45cd330419db0252a7b9fe318f4cf04f29939d5e5f0`. It covers exact source copies, Git-blob/SHA256 tables, comparison, scope and both extraction runners. All 41 entries passed `sha256sum -c` before this addendum was written.

| Runner or original evidence | SHA256 |
|---|---|
| Private snapshot-runner.sh | `722681cd1823d72ebdda1e00bf08cb2061b530ee50ef9766e30924c53e075a4a` |
| Private extra-snapshot-runner.sh | `5e2d16c8e687ab395171d431cff067aa1feb923fde3e81d3e5c5c82d2dc22f96` |
| Original private round3 report | `3b520a8e1cb964474891cbe5ca25e272e3f69dcf2eee5dc994c7a533e63685b5` |
| Original private round3 readset | `cfd10527fd07f6ab414c4871ac9a9ea63dfe77692628864624ad70bf1402bf63` |
| Inherited correction gate runner, documentary only | `a8281f4730be9b3f50bc59fef587c50f69a9df191380cf4dc358cd1ed4fe36e8` |

The original private artifacts were mode444 before copying and hashing. They were not edited. The committed round3 report, its validation subsection, disposition, correction04 contract and correction validation were read. No raw inherited gate runner or mutable gate source was executed or re-audited.

## Finite findings and requirement checks

Line numbers below refer to the exact pinned repository. `K/` means mobile Kotlin package `dev/phosphor/mobil3`. `G/` means shared `crates/phosphor-render-gpu/src`.

### A1. Current LIVE acknowledgement is token-exact

**Evidence:** `rust/src/pause.rs:28-87,105-115,138-164`.

`presented` starts at None. Resume advances `visual_revision` under History before publishing the visual epoch and clearing visual ingress under the existing ring/meter transaction. Retirement replaces History with a new generation/revision and no acknowledgement. While LIVE, `present_pending()` compares the full current FrameToken against `presented`.

`commit` rejects a mismatched token or paused History before changing serial, `presented` or committed ownership. The only inspected production assignment of an acknowledged token is this successful commit. Immutable search finds its production caller at `render.rs:884-889`.

Finite supersession trace: T is acknowledged, HOLD/LIVE creates U, retirement creates V, U's later completion is rejected, pending remains true for V, and a successful V commit alone acknowledges V. A stale T/U commit after V cannot replace V or reopen pending. These facts establish identity, not source timestamps.

### A2. Late admitted old work is permitted before completion, not after it

**Evidence:** `render.rs:164-175,230-240,290-309,545-554,618-648,744-758,848-890`. Shared `G/lib.rs:528-546,672,813-835`.

One OnceLock initializes one render thread. Its frame token, clear, drain, DSP/advance, acquire, retained copy, composition, submit, present and commit run sequentially on that owner. Driver work occurs after the short History snapshot guard has ended.

Finite old-admission trace:

1. The owner snapshots T and drains old samples at 638-648, then stalls before advance.
2. Another thread completes HOLD/LIVE or retirement, publishing U and making LIVE pending.
3. The owner resumes T. `advance` submits old deposits, then final composition may submit and `present()` may return.
4. T's History commit is rejected. U remains pending.
5. The same owner begins U, clears both live-energy sides before consuming samples, advances, retains/composes, submits and calls `present()`.
6. If U is still current and LIVE, its commit acknowledges U. Otherwise it rejects and the newer request remains pending.

After step6, no earlier candidate remains parked on a second render owner or frame-work queue to make its first submission. An earlier loop iteration already finished or abandoned its present path before U could run. The command queue is not a second queue of executable drained frames. Queue ordering places old deposits before the new clear and subsequent work.

This supports correction04's changed observable completion boundary. **The original round3 strict CPU-boundary freshness schedule still exists through step3.** It must not be described as eliminated or as merely previously submitted panel work.

### A3. No-present and known failure paths do not manufacture completion

**Evidence:** `render.rs:296-309,536-554,560-625,848-889`, shared `G/retained_frame.rs:69-147`.

No active surface, lifecycle-paused rendering, failed surface setup, failed acquisition and failed retained allocation do not reach the LIVE commit. Acquisition errors continue at 850-857. Retention errors continue at 870-873 before final composition/present. Energy clear alone updates only the render owner's EnergyEpoch, not History's `presented`. HOLD redraw submits and presents at 612-613 but returns without a LIVE commit.

An acquire/retention failure can follow an energy advance. That does not acknowledge the request. A later successful current-token image can acknowledge it. No timeout sets pending false for LIVE. A hidden or failed surface can therefore remain pending indefinitely.

Here “successful application present” means the inspected path reached normal return from `present()` and then accepted the exact token. `present()` supplies no success Result at this call site. This is not an independent acknowledgement of GPU completion, asynchronous driver health or visible pixels.

### A4. Intervening HOLD preserves committed identity, not unfinished LIVE

**Evidence:** `pause.rs:57-58,75-87,108-115,340-364`.

Finite trace: committed image A belongs to T, LIVE U is pending, then HOLD arrives before U commits. `pin()` clones A. U's later commit is rejected because paused is true. Pending reads false while HOLD, but `presented` still identifies T. A subsequent LIVE creates a new token and becomes pending again.

This is honest image ownership and matches the checked-in production History test. It is not a sticky pending flag. Consumers must interpret bits together. `paused=false && pending=false` is the relevant completed-LIVE state. BLACK and inspection do not fabricate a new acknowledged token.

### A5. Foreground Activity and HUD expose the narrow boundary without changing audio authority

**Evidence:** `jni_glue.rs:848-909`, `K/MainActivity.kt:631-718,936-963`, `K/ui/ScopeUiState.kt:28-40`, `K/ui/PhosphorScreen.kt:651-654`, full `K/ui/PauseDisplayPolicy.kt`, `K/FloatingHudService.kt:143-152,174-185,273-303,332-360`.

JNI reads desired pause, BLACK, pinned availability and pending bit8 in one History guard. Activity forwards bit8 into observable UI state. Its normal visible, non-PiP 500ms heartbeat refreshes that state, and display toggles refresh immediately. The status ribbon appears for HOLD or pending. Policy says `waiting for new frame` for pending LIVE, otherwise it preserves held/black/no-frame text and the observed-source-live suffix.

HUD display LIVE calls only native display resume and sync. `syncTransport` reads bit8, removes a prior pending callback, and schedules another at 250ms only while pending, surface-ready, shown and unlocked. Its transport glyphs and command availability still use actual controller/source state, not pending. The display-only fallback remains separate from controller play/pause.

`SurfaceHost.kt:46-68` reveals an important naming limit: its `presented` callback acknowledges successful surface attach and unpauses rendering. It is **not** the per-frame application-present acknowledgement. This callback merely marks HUD readiness and starts sync. Native bit8, not the callback name, controls completion text. Visibility changes also resync at HUD115-125. HUD retirement sets `retired` before removing all Handler callbacks, then releases controller, surface and window. A queued refresh checks retirement again. Activity stops its heartbeat on stop and rejects hidden/destroyed/PiP ticks at 633.

UI observation is sampled, not instantaneous. A short pending interval may complete between refreshes. This source review does not establish a scheduler latency bound, every external event's delivery, PiP status presentation or actual Android accessibility behavior. It supports the wired normal foreground/HUD adapters without promoting callback source assertions to execution evidence.

### A6. Lock and authority boundaries remain intact in the correction

**Evidence:** correction-only `DELTA.diff`, `pause.rs:132-164`, `engine.rs:81-84,115-123`, `render.rs:545-554,638-648,882-889`, `jni_glue.rs:866-872`, HUD276-303.

The pending addition writes CPU token state and reads a CPU predicate. No surface, queue, GPU allocation, poll or driver operation moves under DISPLAY or ring/meter locks. Retired History and replaced pinned images leave their transaction before destruction. The render commit guard ends before spare-frame unwrapping/drop. Existing visual ingress clearing preserves capture ownership and is not an audible queue flush. No new recorder, render thread, input queue, source command or endpoint appears in the correction-only diff.

These are source-boundary checks of this delta, not a fresh audit of all audio/remote ownership or new R16 work.

## Validation and uncertainty

No optional host test was run. Read-only analysis was sufficient for this bounded request. Existing `pause.rs:251-365` tests use real threads/barriers with production History and EnergyEpoch primitives. Their no-acquire check represents absence of commit, not actual Surface failure. UI tests exercise policy and include source-text wiring assertions. They do not execute the Android render loop, callbacks or a drain-to-first-submit barrier.

Inherited gate `315155k95w`, reported completed 2026-09-08 09:39:49 UTC with exit0: 557 JVM tests, 111 native tests, three offscreen GPU tests, lint, dual APK and source checks passed on the frozen delta. Its immutable validation document explicitly says working-source integration gate, not clean-commit artifact freeze. No APK was installed. These results are inherited, not rerun, independently raw-audited or promoted to device acceptance.

The useful completed result is a finite, source-supported application-present acknowledgement boundary with HOLD and observation qualifications. Buffered AudioRecord source age remains open. Root-producing-read correction03, all-source freshness, device scheduling, GPU execution/scanout, actual audio continuity and whole R13 acceptance remain outside this addendum. No claim is made about the live writer's R16 core or the root's disjoint integration.

Smallest remaining acceptance work belongs to root: preserve the controlled buffered-source-time experiment and any actual render-owner/Android behavioral check in their separate gates. Neither extra design nor a fourth full review is required to state this addendum's narrow result.

## Release

Only this private ADDENDUM and its private snapshot/readset artifacts were created. Original reports, scores and product files remain unchanged. The addendum and readset are sealed mode444, with SHA256 supplied in the completion report. **All read, testing and review holds are released. No test or background job is running. Work stops after the required completion report. Root retains integration, build and stop ownership.**
