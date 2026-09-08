# R15 ADDENDUM-03: F2-R callback correction only

## Disposition

**F2-R is resolved at source for the inspected claim/release failure paths.** The prior **8/10** assessment remains unchanged. No broad review, new score, or new acceptance obligations are introduced.

The correction contains failed native retirement at the Android adapter boundary, keeps the owner unavailable, continues teardown, and preserves failure text. This is source-only disposition, not Android callback or device acceptance. Native F2 lifetime conclusions and the accepted F3/F4 corrections remain untouched.

## Frozen source identity

Request received 2026-09-08 06:53:16 UTC, with a five-minute limit. The coordinator stated that source was frozen during task `329223ctm4`.

Before inspection, exactly these six current files were copied into `/home/ben/.jcode/scratch/r15-hud-review-20260908T0653/source/`:

- `app/src/main/kotlin/dev/phosphor/mobil3/SurfaceOwner.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`
- `app/src/test/kotlin/dev/phosphor/mobil3/FloatingHudPolicyTest.kt`
- `docs/plans/mobile-expansion/section-04-hud-contract.md`

The reviewer-created manifest SHA256 is `0d1f7c3410a4427fba15490db340f86e86d0a0a11923d135072227880d55ca67`. Full source hashes appear below. Unlike round two, this request supplied no external hash manifest. These hashes identify the exact observed frozen source, not a separately supplied writer receipt.

Previous reports were read only to verify preservation:

- REPORT SHA256: `308852afaa8ee8950f7d85c11483abeccca8fb196c5dd78ef839c9d5cf53c3c5`.
- ADDENDUM-02 SHA256: `ccab0099329b0413afcc883045a5803a6874cb1fb32e3282b9f650202ecebd6b`.

Both remain unchanged. All source line citations below use the six new snapshots.

## Same failure traces, corrected outcome

### Callback containment and sticky unavailability

`SurfaceOwner.kt:9-29` retains the prior unavailable-owner behavior. A failed native detach still throws inside the pure owner and does not authorize a successor. The correction does not reinterpret failed retirement as success.

`SurfaceCallbacks`, at `SurfaceOwner.kt:34-46`, catches an action failure. It records the first failure before invoking observers. Status delivery and the failure/cleanup request have separate `runCatching` calls, so an exception from one does not suppress the other. Subsequent actions still execute even when a failure has already been recorded.

### Destroy or hidden presentation with a disconnected renderer

The formerly uncaught trace now follows:

1. `SurfaceHost.surfaceDestroyed`, lines 73-76, or a visibility path, lines 125-135, calls `retire()`.
2. `retire()`, lines 78-81, saves the retiring lease and clears the host's lease reference first.
3. The native false-acknowledgement check at lines 115-117 still makes the pure owner unavailable.
4. `callbacks.run` catches that exception and records `Surface unavailable. Restart Phosphor before retrying`.
5. The specified claim/release exception no longer escapes the Android callback.

The same helper serves activity-hidden and HUD-hidden retirement. `attach()`, lines 46-49, refuses a failed host before claiming another lease. A later preferred-host attempt also places `owner.claim` inside `callbacks.run`, so unavailable-owner rejection is contained.

### Failed attach

`SurfaceHost.kt:48-71` contains claim, attach, and result handling inside the adapter. For a negative native attach result, lines 55-57 retire the lease and then report failure.

If retirement itself fails, its inner adapter records the restart-required reason and returns normally. The later generic attach-failure report does not overwrite that first reason. If retirement succeeds, the generic unavailable/retry reason is reported. The contained lease-owner exception does not skip the independent failure request.

### Disconnect during close and later teardown

`SurfaceHost.close()`, lines 102-111, marks the host closed and independently attempts player-listener removal, lease retirement, and holder-callback removal. It clears its references afterward. There are no deferred `getOrThrow()` calls that can rethrow the earlier retirement failure after cleanup.

Because `SurfaceCallbacks.run` continues to execute later actions, an earlier failure does not prevent these subsequent attempts. A fallback host cannot bypass sticky owner unavailability, and the failed host cannot auto-attach.

### HUD cleanup and final reason

`FloatingHudService.kt:140-144` updates `presentationStatus` and separately posts HUD cleanup on failure. During existing retirement, lines 286-299, the service still attempts controller, listeners, surface, window, and foreground-notification cleanup.

Lines 295-304 retain `closingHost` long enough to choose `closingHost.failure` over the original close message. A disconnect discovered while closing therefore does not end as misleading `HUD off` text. Existing cleanup-failure details are appended when applicable.

The service's failed callback checks `retired` before starting another close. A failure detected during an already-running close does not need a second teardown pass because the current pass continues independently.

### Activity-visible failure

`MainActivity.kt:889-892` now writes surface failure text to `ui.floatingHudStatus` and displays that text in a long Toast through the independent failure callback. The source no longer silently discards the activity host's unavailable/restart message.

The correction contract at `section-04-hud-contract.md:34` matches these adapter semantics and continues to distinguish host fixtures from Android callback recovery.

## Authored fixture evidence

`FloatingHudPolicyTest.kt:79-98` uses the production `SurfaceCallbacks` with the actual `SurfaceOwner`. It injects disconnected retirement, asserts containment, rejects successor ownership, runs later cleanup actions, and requires one retained failure report.

The loop labels cover destroy, failed attach, activity hidden, HUD hidden, and close. Those labels do not instantiate five Android lifecycle paths. The source trace above establishes their adapter wiring.

Lines 101-109 inject failing status and cleanup observers. They check that status failure cannot suppress the independent cleanup request, that later actions still execute, and that later messages do not overwrite the first failure. Source-linked assertions at lines 279-282 check the shared retirement route and absence of `getOrThrow()`.

These fixtures were **inspected only**. The coordinator reported that Gradle was running them. This reviewer did not run or claim a Gradle, Kotlin, Android, native, or device test result in this round.

## Scope and release

Only F2-R correction source and its authored fixture were inspected. No native F2 re-review, F3/F4 re-review, product edit, Git mutation, Android/Gradle command, ADB/device action, GUI, network, or worker was used.

The narrow source defect is closed. The previously assigned coordinator gate and callback-runtime evidence remain the same obligations, not new ones. Native driver-stall uncertainty remains as recorded in ADDENDUM-02. This source review supplies no hardware claim.

Source is released upon delivery of this addendum. Preserve all three reports byte-for-byte. `ADDENDUM-03.sha256` records this document's exact hash.

## Exact six-file manifest

```text
fe3e03491d10b24faf5157404e41334ae13fc85c82d97b8f4796260138f4fd9b  app/src/main/kotlin/dev/phosphor/mobil3/SurfaceOwner.kt
2fddde401479ef8e5624460fa859448501c873281c2efaae07602f281e3e8fbc  app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt
8f2e4fa6430916a6f1aed7a261f0806791b699e3a8003eddb47df9c21352e700  app/src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt
71eb4fd9627d0d651f7f448f876a05741fff2d83aa59a21e3c5546e9d010f7b6  app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt
3767f43131044285b80f2235adff6c4246299e05d9c596bcf77236367756c249  app/src/test/kotlin/dev/phosphor/mobil3/FloatingHudPolicyTest.kt
00a4b556d39b64e32c21bab39666830db6f9e5bd7b903e56e10fe04e91a148f1  docs/plans/mobile-expansion/section-04-hud-contract.md
```
