# R15 ADDENDUM-02: narrow R02 correction review

## Decision

**Corrected source implementation: 8/10. One failure-recovery blocker remains. Hardware acceptance is unassessed.**

F1 is corrected at source. F2 now waits for actual native retirement, with sound `Active` field order, but its Android adapter lets failed-retirement exceptions escape lifecycle callbacks. F3's local metadata-enrichment defect is corrected. F4 now owns pending and completed controller connections through one release path. The lint callback factoring is corrected at source.

This updates the source assessment only. It does not replace round one's findings, erase its baseline, or establish Android build/device acceptance. The score reflects substantial recovery of native lifetime safety, local track identity, and controller ownership, with incomplete error adaptation and unexecuted Android integration.

## Exact baselines and scope

- Request received 2026-09-08 06:41:34 UTC. Review limit: eight minutes, no workers.
- Round-one immutable report: `/home/ben/.jcode/scratch/r15-hud-review-20260908T0617/REPORT.md`.
- Round-one report SHA256 remains `308852afaa8ee8950f7d85c11483abeccca8fb196c5dd78ef839c9d5cf53c3c5`, independently rechecked during this review.
- Coordinator reports that exact report retained in commit `db19cc4`.
- Round-two manifest: `/home/ben/.jcode/scratch/r02-corrections-20260908T0639.sha256`.
- Manifest SHA256: `aa12a2b83183fcb905d5b426fca3ef5dc8985955c6e567dc29eddf12021ba719`.
- Observed repository HEAD: `fbbcfb6f2b2f77023b28ea8bd22ab6f4c3ecfb6d`.
- All 14 listed files matched the supplied hashes and were copied to `/home/ben/.jcode/scratch/r15-hud-review-20260908T0641/source/` before assessment.
- MainActivity's coordinator-owned correction is outside the 14-path receipt. Its separately copied SHA256 is `0d51e28caffa406fea607f2072482afa2908ef0a0a9a9561ca70a436920b0256`.
- `COORDINATOR.sha256` separately records the interface dependency and supplemental source-linked test snapshots. These are not represented as part of the writer's 14-file manifest.
- Source paths below are repository-relative. Line numbers use this round's snapshots unless explicitly identified as round one.

Only F1-F4 and the callback lint correction were assessed. No full re-review, source edits, Git mutation, Gradle/Android compilation, device action, GUI, network, installation, or workers occurred. The sole permitted compilation was the exact pure `surface_lifecycle.rs` host test crate in private scratch.

## F1: resolved at source, Android gate remains coordinator-owned

`app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt:854` now reads `micHandoff.isPending`. The actual policy exposes that Boolean at `MicHandoffPolicy.kt:20-21`. The active-microphone and pending-permission guards remain alongside it.

The corrected source-linked assertion at `app/src/test/kotlin/dev/phosphor/mobil3/FloatingHudPolicyTest.kt:223` requires the real interface rather than round one's nonexistent `.active` property.

The coordinator-owned visibility and metadata assertions were inspected as narrow Git diffs. They account for the visible-only heartbeat/sensor guards and cycle routing through SurfaceHost. These remain source-string checks, not Android lifecycle execution. No current-round Kotlin test result is claimed by this reviewer.

**Disposition:** the exact round-one unresolved-symbol defect is closed by inspection. Final compile/test/lint receipts must come from the coordinator's Android gate.

## F2: native lifetime repair accepted by source inspection, adapter failure remains open

### Actual native resource order

`rust/src/render.rs:184-190` declares `Active.surface` before `Active._window: Retiring<SendWindow>`. Rust drops struct fields in declaration order. Therefore the owned wgpu Surface drops before the retiring window wrapper.

`rust/src/surface_lifecycle.rs:69-74` explicitly drops its contained value before setting `dropped=true` and notifying waiters. The signal therefore follows release of the owned NativeWindow reference, rather than merely observing command receipt.

The relevant source traces are:

| Path | Resource order and result |
|---|---|
| Normal replacement or destroy | `render.rs:323,388-390` drops `Active`. Surface drops first, then window wrapper, then the destruction acknowledgement is sent. |
| Successful construction rejected by cancellation or receiver loss | `render.rs:371-376` installs Active only if publication succeeds. Otherwise the local `a` drops with the same field order. |
| Queued attach cancelled | `render.rs:318-320` skips GPU work. Dropping the queued command's wrapper signals after its owned window reference drops. No Surface was created for that command. |
| Attach command send failure | `jni_glue.rs:72-79` drops the failed-send result and its command payload. Again no Surface exists for that command. The retained token records actual payload drop. |
| Bring-up error or ordinary thread unwind | In `render.rs:905-953`, a locally created Surface is scoped inside the function body, while the retiring window is a function parameter. Locals drop before parameters on return/unwind. After construction, `Active` uses its explicit field order. |
| Render receiver loss | `jni_glue.rs:12-25` treats channel loss as false acknowledgement, but still waits for each retained resource token. `surface_lifecycle.rs:45-48` does not equate disconnection with resource release. |

This resolves the coordinator's specific field-order concern in the inspected source. The pure host tests do not instantiate real `Active`, wgpu, or an Android window. Destructor hangs, process abort, and driver behavior are not experimentally proved by this review.

### Cancellation and real retirement

`jni_glue.rs:64-70` retains every unretired window token. `surface_lifecycle.rs:26-36` cancels failed/timed-out attachment. `render.rs:318-320` checks queued cancellation, and publication checks cancellation and receiver availability at `surface_lifecycle.rs:22-24`.

Crucially, `jni_glue.rs:80-86` calls `retire_surface()` before a failed attachment returns. `retire_surface()` waits for command acknowledgement or disconnection and all retained drop signals. There is no longer a two-second fabricated destruction completion.

A publication/deadline race can still produce a transient native candidate before cancellation acquires the mutex. That does not authorize a failed Java attachment: the failure path still waits for actual retirement before returning. The resource barrier, not instantaneous cancellation of driver work, closes the original lifetime defect.

The Boolean JNI destruction signature matches `PhosphorNative.kt:20`. `SurfaceOwner.kt:9-29` makes a failed detach sticky-unavailable, so old or successor leases are not accepted afterward.

**Driver-stall uncertainty:** the two-second attach deadline is not a bound on JNI callback duration. `Retirement.wait()` and destruction acknowledgement can wait indefinitely while a driver stalls. Android callback delay or ANR remains possible. This is explicitly documented in the correction contract at `docs/plans/mobile-expansion/section-04-hud-contract.md:32`. No finite GPU-completion, watchdog, forced-exit, or phone-responsiveness guarantee is claimed.

### F2-R: High remaining blocker, native failure escapes Android callbacks

**Exact locations:**

- `SurfaceHost.kt:111-114` turns a false native retirement acknowledgement into `IllegalStateException` with `check(...)`.
- `SurfaceHost.kt:69-73` calls `owner.release(lease)` directly from `SurfaceHolder.Callback.surfaceDestroyed`.
- `SurfaceHost.kt:50-54` releases directly in failed attach before the `failed()` callback.
- `SurfaceHost.kt:121-130` also releases directly from visibility handling.
- `SurfaceOwner.kt:10-14` deliberately leaves the owner unavailable when detach throws.

**Concrete source reproduction, not executed Android fault injection:**

1. Hold a live presentation lease, then let the render thread disconnect after dropping its owned resources.
2. Android invokes `SurfaceHost.surfaceDestroyed`.
3. Native `retire_surface()` observes channel failure, confirms the retained resource drop, and truthfully returns false.
4. `check(PhosphorNative.surfaceDestroyed())` throws.
5. `SurfaceOwner.retire()` preserves sticky unavailability, which is correct for ownership safety.
6. No catch exists in the SurfaceHolder callback. The exception escapes instead of becoming unavailable status and controlled presentation cleanup.

The framework callback path is outside `FloatingHudService.onStartCommand`'s construction `runCatching`. Catching `host.close()` in service retirement does not catch this separate callback. On the failed-attach branch, the same exception can prevent `lease=null` and `failed()` from running.

**Impact:** an unhealthy renderer can turn into an uncaught main-thread exception, risking process termination and interruption of otherwise service-owned audio. This is a controlled-failure contract defect, not evidence that native windows are still retired early.

`FloatingHudPolicyTest.kt:70-76` correctly expects an exception from the pure owner. It does not test adaptation at an Android callback boundary.

**Smallest acceptance obligation:** inject a false native destruction result after confirmed resource release into the real SurfaceHost adapter. Require no escaping callback exception, sticky owner unavailability, cleared host references/listeners, visible unavailable/restart status, and no successor attach. Then cover failed attach and hidden/visibility retirement through the same boundary. Do not weaken the actual resource barrier or convert false acknowledgement to success.

**Disposition:** round one's native lifetime defect is corrected in source. F2 remains open only for this concrete Android failure-adapter issue and its missing integration evidence.

## F3: local metadata enrichment resolved

`PhosphorPlayer.kt:28-32,105-110,163-171` assigns each installed queue a process-local generation and publishes stable `local:<queueId>:<index>` media IDs. `onTrackMetadata` does not change that ID. Ordinary track advance changes the index, not the queue generation.

`SurfaceOwner.kt:37-40` uses stable item identity rather than mutable title/artist/album when available. `SurfaceHost.kt:21-25,78-91` observes player events as well as metadata changes, covering distinct items with equal labels. The listener is removed at close, lines 94-98.

Round one's trace now produces the same identity for `file.flac` and later resolved `Song / Artist`, so the second publication does not advance the color cycle. A new local item or installed queue yields a distinct identity. The shared gate still survives app/HUD/app handoff.

Authored fixtures at `FloatingHudPolicyTest.kt:29-55` cover enrichment, artwork exclusion, repeated metadata, equal-label distinct items, queue replacement, stale ownership, and return. `PlaybackTruthTest.kt:9-19` checks producer wiring. These fixtures were inspected, not run here.

**Explicit limitation:** `remote:now` and `capture:now` are placeholder IDs. Host/package plus labels remain best-effort fallback, as documented and tested at `FloatingHudPolicyTest.kt:57-67`. Equal-label distinct external tracks remain indistinguishable with this player metadata. F3's concrete local defect is closed. No universal external track-identity claim is granted.

## F4: pending and completed connection ownership resolved at source

`FloatingHudService.kt:52-54` supplies Media3 `releaseFuture` to `HudConnection`. `connectController` retains the future before installing its completion listener at lines 265-267. The listener checks retirement, generation, and future ownership before adopting a result.

`HudPolicy.kt:77-90` retains one future, closes idempotently, clears ownership before release, and calls the disposal hook once. `FloatingHudService.kt:286-292` invalidates generation and closes that owner before other teardown. It no longer separately releases a late controller result, avoiding competing disposal paths.

The source covers completion before close, close before completion, connection failure, absent future, and repeated close. `FloatingHudPolicyTest.kt:79-104` exercises that production ownership primitive with CompletableFuture and a disposal hook.

The correction contract reports prior inspection of pinned Media3 1.10.1 bytecode, where releaseFuture cancels pending work or releases a completed controller. That is attributed writer evidence, not a bytecode check rerun by this reviewer. The authored fixture is not a real Media3 binding test.

**Disposition:** F4's missing retained future is corrected. Actual pending-binding disposal and one-release behavior remain coordinator Android acceptance.

## Lint callback correction

`SurfaceHost.surfaceChanged` delegates to `attach(holder,width,height)` at lines 39-42. `present()` calls the same helper at lines 75-76. It no longer fabricates a SurfaceHolder callback with format `0`. No lint suppression was added for this correction.

**Disposition:** the precise invalid-format callback source is removed. Android lint was not run by this reviewer.

## Executed evidence

The exact copied `rust/src/surface_lifecycle.rs`, SHA256 `56a48eaa135f45a05a59e60d0cb6f0ce5307159ac41838d17c5db1d8a11075a2`, was compiled and run once with:

```text
rustc 1.96.0 (ac68faa20 2026-05-25)
rustc --edition=2024 --test rust/src/surface_lifecycle.rs -o /home/ben/.jcode/scratch/r15-hud-review-20260908T0641/surface_lifecycle_tests
/home/ben/.jcode/scratch/r15-hud-review-20260908T0641/surface_lifecycle_tests --nocapture
```

**Result: 4 passed, 0 failed.** Tests covered queued cancellation, in-flight cancellation, lost attach receiver, and acknowledged/disconnected barriers that wait for actual wrapped-resource drop. They did not execute JNI, wgpu, real Active field destruction, Kotlin, Media3, or Android callbacks.

All 14 correction hashes and the supplied manifest hash were verified. Supplemental snapshots have their own hashes. Round one's report hash was reverified unchanged. Final source rechecks and this addendum's hash are preserved alongside the report.

## Finite remaining obligations and release

1. Close F2-R with a callback-level false-ack fixture and controlled unavailable status, without weakening retirement safety.
2. Obtain the coordinator's corrected Android compile, unit, and lint receipts for these exact or superseding hashes.
3. Exercise delayed real native retirement and receiver loss under the coordinator's Android gate. Distinguish safe resource order from unbounded driver wait.
4. Execute the authored local-identity cases and a real delayed Media3 connection close. Require one cycle per actual local item and one connection disposal.
5. Retain round one's finite runtime acceptance obligations for compositor pixels, source continuity, activation, cleanup, controls, and geometry. This narrow review neither reran nor expanded them.

The corrected snapshot is released after this report. No further source reads or tests are needed for this round. Preserve ADDENDUM-02 and round one's REPORT byte-for-byte. Later changes require another narrow receipt/addendum.

## Exact manifests

### SOURCE.sha256

```text
b48d2e88b3bf636f694b41850e3a934a3a6e148d595d50cded0c15427b96922d  docs/plans/mobile-expansion/section-04-hud-contract.md
f271f417e7425e89fd2d5fe38df716a4fa5a9a9d94cf4990dd646ee33aba4dee  spec/EXPANSION.md
96a331091e480cca0323c6a851bd6e4c36aa67bc246a69b644d159f5ca74a0f9  app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt
fa387af13bcb23c78ac2e4d47e345179f3ffc3d38942104cc9bb9a0bb65ad1c5  app/src/main/kotlin/dev/phosphor/mobil3/SurfaceOwner.kt
b97344d191afa74c1bc6c28e65c9d34eb03ad655be496fb1f8c2e99be5a4e912  app/src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt
b19daaa81240cb34b27a45f9a73e6aa18462e404eda3bc8d3d3c3012e1a9fcc9  app/src/main/kotlin/dev/phosphor/mobil3/HudPolicy.kt
5265f9cb4a57fc790e00d582fdae0aa402b80f61a35e77ddb1a627c06936b241  app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt
72bf1ffc13994252b7a46b3dc90e4c669617503b0e78aaf83c8461333ef7d5fc  app/src/main/kotlin/dev/phosphor/mobil3/PhosphorPlayer.kt
ea923d90162a8247d6abac68fbf9d0bd368893f904db79b891e01c6a815e91b6  app/src/test/kotlin/dev/phosphor/mobil3/FloatingHudPolicyTest.kt
c3d282dde9e868aa172711706a411d2626dd751aed679af031544e7c9e7c4319  app/src/test/kotlin/dev/phosphor/mobil3/PlaybackTruthTest.kt
e45707189f6f8e2e3890b84dea725d9911f1306eafaaf936a0d550da1ceece17  rust/src/jni_glue.rs
d2fcec760cadc255a4e0129f7d3a665a2d7005f14e69c21b1eca32ef2063522c  rust/src/render.rs
4d53745c3dd99c7fb3f9495e2329b6e1152476ff3d500b785a7aa48237446b6f  rust/src/lib.rs
56a48eaa135f45a05a59e60d0cb6f0ce5307159ac41838d17c5db1d8a11075a2  rust/src/surface_lifecycle.rs
```

### COORDINATOR.sha256

```text
0d51e28caffa406fea607f2072482afa2908ef0a0a9a9561ca70a436920b0256  coordinator/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt
0e2bcbd986a9d17917026c41bf17cc2373e067ab31ad6b3d48e67bd52e4afa3b  coordinator/app/src/main/kotlin/dev/phosphor/mobil3/MicHandoffPolicy.kt
c3d282dde9e868aa172711706a411d2626dd751aed679af031544e7c9e7c4319  coordinator/app/src/test/kotlin/dev/phosphor/mobil3/PlaybackTruthTest.kt
a2412358f83e47d95c3a3f3522e51631c1f5f1274dd21e527c67cb7c3f42a650  coordinator/app/src/test/kotlin/dev/phosphor/mobil3/settings/SettingsArchiveTest.kt
f2a6822667f13e8676c64064cd9adb00040b754062f02544dc4eb313364045cf  coordinator/app/src/test/kotlin/dev/phosphor/mobil3/ui/ControlsVisibilityPolicyTest.kt
bae297b95d2de626f1cc5d4a1aa1ce80ba468183c44619d1be861842b257c369  coordinator/app/src/test/kotlin/dev/phosphor/mobil3/ui/RotationDetentReachabilityTest.kt
```

Finalized 2026-09-08 within the eight-minute round-two budget. The exact addendum hash is stored in `ADDENDUM-02.sha256`.
