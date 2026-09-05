# Phase 4: B2 capture-to-microphone handoff

- Date: 2026-09-05, UTC.
- Initial checkpoint: B2 OPEN. Offline implementation and gate evidence only. Five-cycle phone acceptance was pending.
- Current phase status: **B2: PASS 5/5** on the exact retained APK. See the 16:33 UTC live receipt below. Final regression remains open.
- Private issue: #3.
- Mobile base: `2401239e3c7a644f7d9c598d2a43ae761464b4c1`.
- Implementation commit: `47ff7cdc71c3b8e4315d265844fdfbfcfeb2136c`.
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
- Clean-source commit and retained APK: complete at `47ff7cd`. Both final reviews pass their bounded source contracts.
- Exact APK installation and hash/signer readback: deferred by the quiet-work boundary.
- Five capture-to-mic cycles and B4 transition regression: pending.

## Exact retained debug APK, not installed

The root built commit `47ff7cd` from clean mobile and sibling trees, then rechecked both identities and the frozen source manifest.
The build completed at 10:50:55 UTC. Generated `BUILD_COMMIT` is `47ff7cdc71c3`, without a dirty suffix.
The exact APK passed 11 source and 5 packaged-artifact boundary checks. The pinned NDK runtime exemption remained the only exemption.

- Retained file: `dev/scratch/pre-v2-20260829T072841Z/phase-04/candidate-47ff7cdc71c3/phase-04-47ff7cdc71c3.apk`.
- APK SHA-256: `ad9a726eb510d3c5ecd066af7218fbc95fdbbef137acc7603ed5771118d42993`.
- Debug signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- Signature verification: APK v2 scheme passed.

No device command or installation occurred. Use this retained APK for the later B2 check, rather than silently rebuilding from a newer documentation commit.

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

## Live acceptance, 2026-09-05 16:33 through 16:38 UTC

Ben authorized the real phone check at 16:30 UTC. Root used the explicitly selected Galaxy S25, SM-S931U, API 36.
The device was unlocked. No keyguard, screen timeout, screensaver, permission or volume setting changed.
Private evidence is in `dev/scratch/pre-v2-20260829T072841Z/phase-04/live-20260905T1632/`.

### Exact installation

Before installation, the retained candidate passed offline hash, package, debuggable flag, version code and signer checks.
Root backed up the currently installed debug APK and fresh preferences, then used pm3 installation with readback.
Local and installed SHA-256 both equal `ad9a726eb510d3c5ecd066af7218fbc95fdbbef137acc7603ed5771118d42993`.
The debug signer matched the pinned value above. Version code is 2000000, version name is `2.0.0-debug`.
The reviewed source identities and unchanged sibling commit matched before the run. No rebuild or runtime source edit occurred.

### Five consecutive switches

Microphone permission was already granted. Each cycle used Android's actual capture-consent dialog and the app's built-in mic selection.
Every capture had an active projection before switching. Every mic snapshot showed projection `null` afterward.

| Cycle | Capture-stop log to mic-running log | AudioFlinger server frames, first to second snapshot | Bright beam pixels changed |
|---|---:|---:|---:|
| 1 | 96.787 ms | 40,320 to 130,560 | 1,875 |
| 2 | 90.756 ms | 27,840 to 106,560 | 7,602 |
| 3 | 82.442 ms | 29,760 to 113,280 | 42,372 |
| 4 | 80.302 ms | 30,720 to 115,200 | 33,847 |
| 5 | 75.337 ms | 30,720 to 121,920 | 65,096 |

These durations describe observed log intervals, not a guaranteed deadline or a timer-based startup rule.
Beam measurements compare a 400x400 center region, excluding controls, with a 10% grayscale threshold to reject dark background dithering.
Root inspected the final screenshot: `src · mic`, the microphone privacy indicator and a lit trace were present.

| Requirement | Actual result |
|---|---|
| Capture cleanup precedes mic startup | All five ordered capture stopped, ring inactive, ring active, then mic running. |
| Exactly one start per request | Exactly five mic-running records occurred before the five-cycle end marker. Each of ten recorder snapshots had one active app MIC track. |
| Real microphone input advances | Every track was unsilenced, stereo, 48 kHz, and its server frame count increased between snapshots. |
| Beam is live rather than a static success face | All five bright-trace comparisons changed. The final source face identified mic. |
| Newer source retains ownership | A rapid mic selection followed by local selection ended in local PLAYING state with no app recorder or retained projection. |
| Narrow B4 transition regression | Mic-to-local and capture-to-local both disabled the reader ring before native local open. Both published local PLAYING and retained no app recorder. |
| Artifact and user state survive | Exact package/hash/signer readback passed. Tuning, runtime preferences and saved hosts were restored byte-for-byte. |

The local checks used a task-owned valid MP3 through the existing open path.
They do not repeat recursive-provider acceptance or establish audible output at the unchanged media volume.
The rapid selection check proves the resulting owner. It does not manufacture a stuck reader or guarantee hitting every pending-start race.

### Complete log window and restoration

The all-PID main/system/crash reader completed its 300-second deadline with 69,626 records.
The exact host reader replayed all 69,626 records. Captured and replayed text matched byte-for-byte.
Start, five-cycle completion, regression and final markers fall inside this window.
The scoped fatal-exception, fatal-signal, ANR, native-panic and mic-stop/start-error scan found zero candidates.

- Raw SHA-256: `aced7e0750bf8eaa51d1bb0151a44f14e7a30e62f5a6b5c70739fdd91e34c6e6`.
- Captured and replayed text SHA-256: `1269932f91015d5513089687322e8a67812c19c7489d16e48320d96b0b5ea9ec`.

Root stopped only the debug app, restored the original three user preference files and verified their exact bytes.
The task-owned MP3 and device diagnostic directory were removed after retaining and hashing the evidence.
Screen timeout, screensaver and stay-awake values remained unchanged. Capture projection was null at closeout.
The exact B2 debug APK remains installed. The production package was not changed.

An early host assertion expected `(null)` instead of Android's actual standalone `null` projection text.
Root corrected the parser and reran the check across all five cycles. This was not a retained-projection failure.
The initial launch-transition screenshot was excluded from acceptance. Earlier slow source-menu taps only revealed controls.
The subsequent wake-then-select gestures opened the real source sheet. No failed attempt was counted as a successful cycle.

Phase 4 task 4.4 passes. B2 retains its final-regression and human acceptance boundaries.
Hardware failure, delayed reader death, capture-face callbacks and release-error retry remain the previously recorded limits, not forced-device PASS claims.
The overall B1-B21 drift counter remains 21. The next implementation phase is Phase 5, not the future roadmap.
