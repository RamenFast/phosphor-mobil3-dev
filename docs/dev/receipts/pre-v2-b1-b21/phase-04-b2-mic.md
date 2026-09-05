# Phase 4: B2 capture-to-microphone handoff

- Date: 2026-09-05, UTC.
- Status: B2 OPEN. Offline implementation and gate evidence only. Five-cycle phone acceptance is pending.
- Private issue: #3.
- Mobile base: `2401239e3c7a644f7d9c598d2a43ae761464b4c1`.
- Unchanged sibling: `2a45b0f4d05696efe98970f51ac5358c052b565f`.
- Rollback APK: Phase 3 debug candidate, SHA-256 `98523b2eadd1ea2807ccd9f6ebcfc0850d6d3ec430c8d744ad330c7ca2d8be0a`.

## Human outcome and boundary

Selecting built-in mic after capture must release capture before microphone startup and show live state only after successful recording.
The latest source selection wins. No timer may authorize startup.
The activity retains microphone ownership. Existing B4 reader completion and source-loss reconciliation remain intact.

At 10:32 UTC Ben requested autonomous work without interrupting YouTube or music.
The root stopped device work and deferred installation, playback changes, alerts and live source-switching tests.
Quiet source review, tests and retained builds remain allowed. This receipt does not claim device acceptance.

## Implementation and reviewed correction

The pending mic request records the current capture-status sequence and a unique request token.
PlaybackService carries that token through its existing serial release and CaptureService STOP path.
Startup requires both a later matching idle observation and the successful B4 source-release reply.
The reply carries its publication revision, which the activity rechecks before starting mic.

MicController checks initialization, starts recording, checks recording state, arms the scope and starts its reader before reporting success.
Failure remains non-live with retry text. An uninitialized mic never clears another owner's ring.
New source selection cancels pending policy state and deferred mic startup.

Independent source review rejected the initial candidate with a reproduced overlapping-STOP defect.
Two callbacks on one completion could publish the newest idle first, then overwrite it with the old request's idle.
If the activity missed those broadcasts, its snapshot reread could leave the newest mic request pending indefinitely.
The reproduction used compiled production policies through JShell. It was not an Android lifecycle test.

The root added one latest STOP token per CaptureService instance.
Every STOP selects that token, including non-mic STOPs. Only the latest nonnull token may publish correlated status.
Every request still sends its own B4 stop reply, preserving late source-loss reconciliation.
Tests cover both callback orders, both cleanup/destruction orders, retry completion replacement and non-mic supersession.
No status history, new reader owner, startup timer or second stop path was added.

## Frozen automated evidence

Private working receipts live under `dev/scratch/pre-v2-20260829T072841Z/phase-04/`.
Root gate 1 completed at 10:37:43 UTC with identical before/after source manifests.
The final manifest SHA-256 is `6006f9e0333ec22388c8c5b99478f8d2ec30e3afbc0948e64423afbc1565c38f`.

| Check | Observed result | Limit |
|---|---|---|
| Targeted MicHandoffPolicyTest after correction | 15 tests passed, no failures, errors or skips | Production-linked JVM policies plus source-wiring assertions |
| Full Android unit suite | 177 tests passed across 19 suites, no failures, errors or skips | Does not execute Android hardware or service lifecycle |
| Debug lint | Passed, zero errors and 15 warnings | Not a warning-free claim |
| `:app:checkEngine` | Passed | Native Android compile, not phone execution |
| Mobile locked Cargo suite | 39 passed | No Rust source changed in B2 |
| Retained relay locked Cargo suite | 26 passed | No relay source or active service changed |
| Shared audio locked Cargo suite | 27 passed | Sibling source unchanged |
| pm3, boundary and release-rejection fixtures | Passed | No real device activation or release signing |
| Source boundary scanner | Passed | Artifact scan is a later build check |
| Mobile rustfmt and both repository diff checks | Passed | Scoped formatting floor |
| Protected archive | All recorded hashes passed | Archive remains unchanged |
| Selected native/relay strict lint, scope, tracking, no-Python and antistub gates | Passed | Only non-release prefixes ran. No signer or device gate ran. |

Gradle used the repository wrapper after `source scripts/env.sh`.
The corrected root run used low-priority CPU and I/O scheduling, with no device command.
The root retained XML results, lint XML, command logs and source manifests in the private phase-04 directory.

## Review and delivery state

- Implementation handoff: scoped initial implementation PASS, with its original identities retained.
- Independent source review: initial FAIL retained. Corrected-source PASS includes an independent compiled-policy reproduction with both destruction orders.
  Root rechecked all 12 reviewed source identities.
- Independent human-intent review: PASS on the corrected candidate. Root checked its 11 listed source/spec/plan identities.
- Clean-source commit and retained APK: next. Both final reviews now pass their bounded source contracts.
- Exact APK installation and hash/signer readback: deferred by the quiet-work boundary.
- Five capture-to-mic cycles and B4 transition regression: pending.

## Historical pre-fix comparison

Before the quiet-work boundary, one granted-permission capture-to-mic cycle succeeded on Phase 3 source `9bf8527`.
Its bounded 150-second all-PID log contains 17,453 records and start, switch and end markers.
The host reader replayed all records and `cmp` confirmed byte-identical captured and replayed text.
Tuning, saved hosts and runtime preferences matched the pre-test archive after restoration.
This fast pre-fix cycle does not establish that the race was absent and does not accept B2.

## Remaining runtime checks

The intent review identified separate legacy capture-session callbacks that can still publish a capture-labelled live face during pending mic handoff.
This does not publish premature mic-labelled success. Preserve the observation for transition testing and the planned B6/B8 truth phase.
Some service-release failures leave policy state pending until retry or a newer selection. Startup stays blocked and the service reports remedy text.
Observe that recovery path on the phone. Neither review claims globally race-free metadata or automatic recovery from every reader error.

After the quiet-work boundary changes, install the exact reviewed debug APK through pm3 and retain its readback proof.
Record five real capture-to-mic switches with permission already granted, request ordering, one active mic recorder and sample progression.
Check safe source supersession and narrow B4 local/capture transitions without forcing hardware failure.
Restore test settings and remove only task-owned fixtures. Preserve every failed attempt and evidence limit.
Keep B2 open and retain final regression. Do not push, merge, tag, uninstall production or publish a release.
