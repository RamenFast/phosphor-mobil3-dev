# Atomic requirement and changed-output traceability

**04:40 continuation:** The [fresh local replay](phase-16-asus-local-replay-2026-09-07.md) supersedes prior-only device evidence for A1, C6, P2/P7 and the measured L3 import/authority workflow. Earlier rows retain their timestamped status. Other physical and capture/release limits remain unchanged.

2026-09-07. Remaining-fixes continuation: capture61e0630,pinch18569be,listener743d8bb,fixture2367df3 and their guardrails. Not all historical B1-B21 acceptance.

This is a mapping of observed checks, not a blanket end-to-end pass. The check level is explicit in every row.
Policy and source results do not close the live gaps named in the last column. U IDs are user boundaries, not historical B cards.

All named test cases were resolved individually in the passing JUnit XML. Every named raw evidence file exists and was hashed.
The private verified inventory and generator are retained as `audit-atomic-traceability.json` and `build-atomic-traceability.rb` in the audit scratch directory.
The [final-artifact receipt](phase-16-asus-final-artifact-audit-2026-09-07.md) supplies device/artifact identity and earlier before/after comparisons.

| ID and explicit requirement/output | Concrete check and observed result | Evidence level and remaining limit |
|---|---|---|
| **C1** Capture glyph and toggle ignore optimistic Media3 predictions | 10 commands,10 glyph transitions,0 premature transitions against real source callbacks Check: `audit-final-capture-analysis.json` / `CaptureMirrorPolicyTest.optimisticControllerPredictionsCannotFlipTheCaptureGlyph`: passed | actual_app. No panel-scanout or source-internal latency claim. |
| **C2** Observed state travels through existing session extras and clears to NONE on retirement | Source publication/clear checks passed. Actual notification-access loss removed stale transport. Check: `audit-capture-unavailable.xml` / `CaptureMirrorPolicyTest.observedStateTravelsThroughSessionExtrasWithActivityOwnershipChecks`: passed | source_and_actual_app. No additional limitation beyond the stated check level. |
| **C3** Activity reconnect resynchronizes current capture truth | Foreground re-entry showed current item and correct glyph, then explicit pause showed Play. Check: `audit-capture-reconnected.xml` / `audit-capture-paused.xml` / `CaptureMirrorPolicyTest.activeMirrorReattachesItsExactPlayerWithoutResettingIt`: passed | actual_app. No additional limitation beyond the stated check level. |
| **C4** Missing observed state cannot mean predicted playback | NONE and ERROR return false despite optimistic true. Actual access removal showed unavailable transport. Check: `audit-capture-unavailable.xml` / `CaptureMirrorPolicyTest.optimisticControllerPredictionsCannotFlipTheCaptureGlyph`: passed / `CaptureMirrorPolicyTest.missingNotificationAccessCannotReproduceOptimisticInvertedToggle`: passed | policy_and_actual_app. No additional limitation beyond the stated check level. |
| **C5** Retired Activity/controller bindings cannot publish current truth | Binding revisions rejected old, replaced, null and same-controller reattachment callbacks. Check: `CaptureMirrorPolicyTest.staleCallbacksLoseAuthorityIncludingSameControllerReattachment`: passed / `CaptureMirrorPolicyTest.observedStateTravelsThroughSessionExtrasWithActivityOwnershipChecks`: passed | source_and_policy. Queued stale callbacks were not injected into the live Activity. |
| **C6** Local playback retains its existing truth | Actual WAV title/duration/time/play state and paused seek held10848ms with speed0 in two dumps. Check: `audit-final-paused-seek.xml` / `audit-final-paused-seek-first.txt` / `audit-final-paused-seek-held.txt` / `CaptureMirrorPolicyTest.captureBufferingToPausedLocalOrRemoteUsesUnchangedNoncaptureTruth`: passed | actual_app. No additional limitation beyond the stated check level. |
| **C7** Remote playback retains its existing truth | Noncapture true/false playback states remain independent of capture PLAYING/PAUSED extras. Check: `CaptureMirrorPolicyTest.captureBufferingToPausedLocalOrRemoteUsesUnchangedNoncaptureTruth`: passed | policy. No live remote audio replay under the no-PC-audio boundary. |
| **C8** Playing,buffering,connecting,paused and resumed capture states use the defined glyph policy | All named active/inactive states passed policy assertions. Live finite replay emitted PLAYING/PAUSED only. Check: `audit-final-capture-analysis.json` / `CaptureMirrorPolicyTest.playingPausedBufferingAndEveryActiveTransitionMatchTheCaptureGlyph`: passed / `CaptureMirrorPolicyTest.routeWaitsForObservedStateAndBufferingCanPause`: passed | policy_and_partial_actual_app. BUFFERING/CONNECTING and sustained inversion variants remain unobserved end to end. |
| **P1** Sub0.001 samples retain the last applied reference and accumulate deliberate outward movement | Slow source-sheet pinch changed gain1.833 to2.0050182 after its guard. Check: `audit-sheet-gap300-analysis.json` / `ui.StagePinchScaleTest.slowOutwardMovementAccumulatesInsteadOfDisappearingEachFrame`: passed / `ui.StagePinchScaleTest.cumulativeScaleIsIndependentOfSamplingRate`: passed | policy_and_actual_app. No additional limitation beyond the stated check level. |
| **P2** Slow inward movement also accumulates | Inward sampling passed and actual inverse3D gestures restored perspective edges near baseline. Check: `audit-dolly-perspective-pixels.json` / `ui.StagePinchScaleTest.slowInwardMovementAlsoAccumulates`: passed | policy_and_actual_app. 3D measurement is perspective response, not exact camera-coordinate readback. |
| **P3** Stationary jitter does not drift and a new sequence discards old distance | 100 alternating subthreshold jitter pairs emitted no scale. New sequence established its own reference. Check: `ui.StagePinchScaleTest.stationaryJitterDoesNotDriftAndNewSequenceDoesNotReuseDistance`: passed | policy. No additional limitation beyond the stated check level. |
| **P4** Blocked and rebase events replace the reference so excluded travel never jumps later | 22 Blocked,1 Rebase,218 Apply.332ms blocked,340ms rebase. Blocked and first post-rebase gain unchanged. Check: `audit-sheet-gap300-analysis.json` / `audit-sheet-gap300.log` / `ui.StagePinchScaleTest.guardAndRebaseDiscardBlockedTravelBeforeAnyGainChange`: passed / `ui.StageGesturePolicyTest.blockedToClearFrameIsBaselineOnlyThenMotionCanApply`: passed | policy_and_actual_app. No additional limitation beyond the stated check level. |
| **P5** Zero,negative and nonfinite distances reset without publishing scale | Zero,-1,NaN and infinity each reset, then a new valid baseline produced the correct ratio. Check: `ui.StagePinchScaleTest.invalidDistanceResetsWithoutApplyingAnInvalidScale`: passed | policy. No additional limitation beyond the stated check level. |
| **P6** Gain limits and VIEW LOCK remain unchanged | Production pinch still clamps0.1..7. Actual locked broad pinch retained displayed1.83. Check: `audit-final-viewlock.xml` / `audit-final-viewlock-input.jsonl` | source_and_actual_app. Clamp extremes were inspected, not reached in the final physical replay. |
| **P7** Shared sampler drives3D dolly rather than changing gain | Trace top edge789→773→787,left25→11→26,bottom1633→1612→1634. Displayed gain1.83 throughout. Check: `audit-dolly-perspective-pixels.json` / `audit-dolly-perspective-changed.xml` / `audit-dolly-perspective-return.xml` | actual_app. No additional limitation beyond the stated check level. |
| **P8** Existing chrome333ms guard,physical margins,bottom-edge rejection and mode6 pull remain intact | Named boundary/mapping tests passed. Actual source-sheet guard observed332ms blocked and340ms rebased. Check: `audit-sheet-gap300-analysis.json` / `ui.StageGesturePolicyTest.movementBlocksThrough332And333ButNot334Milliseconds`: passed / `ui.StageGesturePolicyTest.marginUsesPhysicalPixelsAtSeveralDensitiesAndInclusiveEdges`: passed / `ui.StageGesturePolicyTest.bottomBoundaryAndOvershootAreInclusiveAtAllDensities`: passed / `ui.StageGesturePolicyTest.mode6PrecedesScopeGuardAndMultitouchRetiresWithoutTakeover`: passed / `ui.StageGesturePolicyTest.soleCallerMapsAllPressedPointersAndGuardsEveryScopeMutation`: passed | policy_and_partial_actual_app. Every card/orientation and exact final-sheet333ms were not physically sampled. |
| **L1** Destruction clears gravity-listener ownership before unregistering | Exact final APK retained1 listener through3 same-process replacements. Old implementation grew1,2,3. Check: `audit-final-sensor-result.txt` / `ui.RotationDetentReachabilityTest.sourceOnlyDestructionClearsListenerOwnershipBeforeUnregistering`: passed | source_and_actual_app. No additional limitation beyond the stated check level. |
| **L2** Retired task,Activity and listener identities cannot register or mutate rotation | All three production ownership guards passed the named source check. Check: `ui.RotationDetentReachabilityTest.sourceOnlyRetiredActivityCannotRegisterOrMutateRotation`: passed | source. No queued stale native orientation mutation was injected. |
| **L3** Existing Android rotation authority and detent ownership remain intact | System hold,unlock refresh,disabled controls and import source checks passed. Earlier same-implementation actual import held portrait then unlocked landscape. Check: `ui.RotationDetentReachabilityTest.sourceOnlySystemLockHoldsObservedActivityWithoutOverwritingStoredChoicesOrPresentation`: passed / `ui.RotationDetentReachabilityTest.sourceOnlySteadySensorUnlockUsesExistingTickAndLifecycleToResumeBothOwners`: passed / `ui.RotationDetentReachabilityTest.sourceOnlyFullSheetDisablesBothDependentControlsAndExplainsAndroidAuthority`: passed / `ui.RotationDetentReachabilityTest.sourceOnlyImportRestoresSavedChoicesWithoutReplacingHeldPresentation`: passed | source_and_prior_actual_app. Actual prior check is recorded in phase-16-asus-final-checkpoint-2026-09-07.md. Physical cardinal poses remain open. |
| **D1** Diagnostics default off and evaluate fields only after explicit opt-in | Empty property,actual cold launch and swipe,VERBOSE-capable reader observed its marker but0 diagnostic events. Check: `audit-default-off-result.json` / `audit-default-off-verbose-reader.log` / `AcceptanceTraceTest.debugFieldsAreEvaluatedOnlyAfterExplicitLogOptIn`: passed | source_and_actual_app. No additional limitation beyond the stated check level. |
| **D2** Release diagnostics are no-op without logger or field evaluation | Release implementation is inline Unit,no android logger and no fields() call. Check: `AcceptanceTraceTest.releaseHasNoLoggingOrFieldEvaluation`: passed | source. No installed release acceptance. Release signing/provenance boundaries remain intact. |
| **D3** Glyph observation uses the exact drawn snapshot and follows drawContent | Same-snapshot source test passed.60 draw observations in the recorded10-command timeline match the actual drawn label snapshot. Check: `audit-final-capture.log` / `audit-final-capture.mp4` / `AcceptanceTraceTest.glyphObservationUsesTheSameSnapshotAsTheDrawnLabel`: passed | source_and_actual_app. Draw callback and recording do not measure panel scanout. |
| **D4** Chrome observation is read-only and cannot extend guard timestamps | Repeated observation calls retained the final timestamp and333ms boundary.242 actual chrome observations were captured. Check: `audit-sheet-gap300.log` / `ui.StageGesturePolicyTest.observationPreservesFinalMotionTimestampAndNeverExtendsTheGuard`: passed | policy_and_actual_app. No additional limitation beyond the stated check level. |
| **D5** Timing uses monotonic Compose evaluation and discloses measurement overhead | Actual input/evaluation timestamps analyzed. Maximum observed input-to-evaluation lag12ms in the source-sheet run. Check: `audit-sheet-gap300-analysis.json` / `AcceptanceTraceTest.debugFieldsAreEvaluatedOnlyAfterExplicitLogOptIn`: passed | source_and_actual_app. Instrumented lag is reported. Incremental observer overhead was not isolated experimentally. |
| **O-activity_player** Changed diagnostic output activity_player | 30 events. Fields: uptime_ms, event, capture, state, ready, playing, ui. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **O-capture_binding** Changed diagnostic output capture_binding | Call site emits only old/new controller identity hashes. No binding event appears in the finite final timeline. Check: Direct check described in the observed result. See the retained generator and source inventory. | source. Publication observed by source check only, not live event receipt. |
| **O-capture_callback** Changed diagnostic output capture_callback | 80 events. Fields: uptime_ms, event, owner, current, state. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **O-capture_glyph_draw** Changed diagnostic output capture_glyph_draw | 60 events. Fields: uptime_ms, event, playing. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **O-capture_mirror** Changed diagnostic output capture_mirror | 80 events. Fields: uptime_ms, event, state, playing, actions. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **O-capture_observed_ui** Changed diagnostic output capture_observed_ui | 10 events. Fields: uptime_ms, event, state, ui. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **O-capture_publish** Changed diagnostic output capture_publish | 80 events. Fields: uptime_ms, event, owner, state, actions. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **O-capture_request** Changed diagnostic output capture_request | 10 events. Fields: uptime_ms, event, play. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **O-chrome** Changed diagnostic output chrome | 242 events. Fields: uptime_ms, event, evaluated_ms, blocked, Console, Sheet, Overflow. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **O-gesture** Changed diagnostic output gesture | 241 events. Fields: uptime_ms, event, evaluated_ms, input_ms, pointers, decision, gain, locked, mode. Check: `audit-observed-event-schema.json` | actual_app. No additional limitation beyond the stated check level. |
| **D6** Do not record metadata,audio,accounts or relay endpoints and add no runtime administration endpoint | All10 call sites inventoried. Observed9 event schemas contain timing,state,identity,geometry,gesture fields only. Source boundary gate passed11 checks. Check: `audit-observed-event-schema.json` | source_and_actual_app. Finite captured logs cannot prove absence of all possible future private data. Source inspection covers the unobserved binding fields. |
| **F1** Delayed fixture command accepts bounded post-tap gap and preserves physical input reporting | 300ms and700ms delayed commands emitted accepted input records and successful version4 completion through real Android input. Check: `audit-sheet-gap300-input.jsonl` / `audit-dolly-perspective-out.jsonl` | actual_app. No additional limitation beyond the stated check level. |
| **F2** Malformed delayed arguments fail before input with actionable errors | Device gaps-1 and1001 each returned parsed error envelope,invalid_arguments,exit2,no input. Check: `audit-driver-final-errors.json` | actual_fixture_cli. No additional limitation beyond the stated check level. |
| **F3** Probe initializes input manager without input and reports readiness | Actual phone probe reported ready,input_injected=false and successful version4 completion. Check: `audit-driver-final-probe.jsonl` | actual_fixture_cli. No additional limitation beyond the stated check level. |
| **F4** Input-manager failure returns actionable error and exit3 | Real compiled fixture returned input_failed,error.fix and exit3 when its host input-manager probe could not initialize. Check: `audit-driver-input-failure.json` | host_cli_with_android_stubs. Host failure-path observation,not real device injection rejection. |
| **A1** Local auto-gain resets between loud and quiet items and holds silent playback | Actual queue observed loud1.15,quiet4.51 then6.00,silence6.00 with advancing playback. Check: `gain-loud-stable.xml` / `gain-quiet-start.xml` / `gain-quiet-stable.xml` / `gain-silence-start.xml` / `gain-silence-held.xml` | prior_actual_app. Same implementation,earlier candidate artifact. Not all source/error combinations. |
| **U1** Only ASUS may be targeted and volume must not rise above1/30 | Explicit ASUS identity used throughout. Supported final volume getter returned1 in0..30. Check: `audit-final-volume.txt` | actual_device. S25 was excluded. No PC audio was initiated. |
| **U2** Restore original preferences and temporary system settings exactly | Both original preference byte comparisons and four-line settings comparison passed. Post-default-off check repeated both preference comparisons. Check: `audit-restored-prefs.xml` / `audit-restored-runtime.xml` / `audit-restored-settings.txt` | actual_device. No additional limitation beyond the stated check level. |
| **U3** Preserve notification permissions,audio denial and unrelated URI grants | Original four notification listeners and audio denial flags verified. URI set changed75→74 with only owned tree removed. Check: `audit-cleanup-notification.txt` / `audit-cleanup-package.txt` / `audit-cleanup-grants-before.txt` / `audit-cleanup-grants-after.txt` | actual_device. No additional limitation beyond the stated check level. |
| **U4** Preserve requested stay-awake7,screensaver0 and unplugged timeout600000 | All three getters matched requested values. Check: `audit-final-state.txt` | actual_device. No additional limitation beyond the stated check level. |
| **U5** Stop test sources,clear projection and remove only owned phone fixtures | No app process or source services,null projection,two baseline preference files only,owned fixture folder/JAR absent. Check: `audit-final-state.txt` | actual_device. No additional limitation beyond the stated check level. |
| **U6** Install exact verified implementation and preserve rollback | Supported install verified hash/signer. Final base APK still matched retained743d8bb SHA256. Baseline and candidate APKs retained privately. Check: `final-743d8bb.apk` / `baseline.apk` | actual_device. No additional limitation beyond the stated check level. |
| **U7** No workers,push,release signing,publication or Linux deployment under this authorization | Root-only commits on existing release branch. No merge,push,tag,signing or Linux service action occurred. Check: Direct check described in the observed result. See the retained generator and source inventory. | workflow_record. Authorization boundary,not a release acceptance result. |

## Measurement corrections and restoration

The first default-off reader filtered INFO and higher, so its silence did not establish absence of VERBOSE events.
The corrected reader accepted VERBOSE, observed an explicit marker and recorded zero diagnostics during actual cold launch and physical swipe.
Both preference files were restored and compared byte-for-byte afterward. The app is stopped with no source services or projection.
The log property remains empty and MUSIC remains 1/30. No fixture or production code changed in this traceability continuation.

The failure-envelope check runs the real compiled fixture on the host against Android stubs. It proves exit3 and its error schema only.
It does not simulate a real device rejecting an injected gesture. The separate malformed-gap checks ran on the actual phone.

The final live logs contain nine of the ten diagnostic event types. `capture_binding` is explicitly source-checked, not claimed as live-observed.
Actual BUFFERING/CONNECTING, sustained variants, every physical orientation and deadline, live remote audio and release acceptance remain open.


## Post-mapping rerun, 03:35-03:41 UTC

The full Android gate ran with `--rerun-tasks`: all55 tasks executed,440 tests passed, and lint/assembly/checkEngine passed.
Native73,relay43 and developer/privacy/release fixtures passed again. All30 named cases in this inventory passed in fresh XML.
Each row below distinguishes fresh execution from retained-evidence revalidation. These are not interchangeable.

A fresh captured-transport attempt reached generic capture with no local transport: zero routed requests and zero glyph changes.
Opening Spotify on ASUS then showed active Spotify Connect playback on the excluded S25.
No pause,play or device-transfer command was sent from Spotify. Taking over that route would cross the explicit exclusion.
The earlier successful10-command final-artifact recording remains valid evidence, but is not relabeled as a successful fresh replay.

| ID | Post-mapping check result |
|---|---|
| C1 | Fresh capture replay blocked by active Spotify Connect on excluded S25.0 local routed requests. Earlier10/10/0 oracle reran and passed. |
| C2 | Fresh named source/policy cases passed. Actual generic capture showed no transport controls. Access was revoked and restored to baseline afterward. |
| C3 | Fresh capture replay blocked by active Spotify Connect on excluded S25.0 local routed requests. Earlier10/10/0 oracle reran and passed. |
| C4 | Fresh named source/policy cases passed. Actual generic capture showed no transport controls. Access was revoked and restored to baseline afterward. |
| C5 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| C6 | Fresh noncapture policy case passed. Prior real paused-seek dumps were rechecked:PAUSED,10848ms,same timestamp. No new local decode run. |
| C7 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| C8 | Fresh capture replay blocked by active Spotify Connect on excluded S25.0 local routed requests. Earlier10/10/0 oracle reran and passed. |
| P1 | Fresh policy cases passed. Actual241-sample sheet pinch changed gain1.833 to2.0023825,rebase338ms,no deferred jump,max input lag12ms. |
| P2 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| P3 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| P4 | Fresh policy cases passed. Actual241-sample sheet pinch changed gain1.833 to2.0023825,rebase338ms,no deferred jump,max input lag12ms. |
| P5 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| P6 | Fresh actual broad VIEW LOCK pinch retained1.83. Source clamp remains0.1..7. |
| P7 | Prior actual pixel oracle reran:perspective edge changed at least10px,returned within3px. No new3D source run. |
| P8 | Fresh policy cases passed. Actual241-sample sheet pinch changed gain1.833 to2.0023825,rebase338ms,no deferred jump,max input lag12ms. |
| L1 | Fresh actual same-process replacements0/1/2/3 each retained1 listener,PID1741. Named source test passed. |
| L2 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| L3 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| D1 | Fresh actual cold launch and swipe with VERBOSE-capable reader:marker observed,0 diagnostic events. Named source test passed. |
| D2 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| D3 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| D4 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| D5 | Fresh named JUnit cases passed. Linked earlier device evidence revalidated, not necessarily replayed. |
| O-activity_player | Fresh live observation:2 events in postmap-sheet/capture logs. Generic capture has no routed transport. |
| O-capture_binding | Direct source assertion reran successfully. No live event claimed. |
| O-capture_callback | Referenced evidence exists and hashes were rechecked. Earlier observation retained, not a fresh physical replay. |
| O-capture_glyph_draw | Referenced evidence exists and hashes were rechecked. Earlier observation retained, not a fresh physical replay. |
| O-capture_mirror | Fresh live observation:1 events in postmap-sheet/capture logs. Generic capture has no routed transport. |
| O-capture_observed_ui | Fresh live observation:1 events in postmap-sheet/capture logs. Generic capture has no routed transport. |
| O-capture_publish | Fresh live observation:1 events in postmap-sheet/capture logs. Generic capture has no routed transport. |
| O-capture_request | Referenced evidence exists and hashes were rechecked. Earlier observation retained, not a fresh physical replay. |
| O-chrome | Fresh live observation:242 events in postmap-sheet/capture logs. Generic capture has no routed transport. |
| O-gesture | Fresh live observation:241 events in postmap-sheet/capture logs. Generic capture has no routed transport. |
| D6 | Referenced evidence exists and hashes were rechecked. Earlier observation retained, not a fresh physical replay. |
| F1 | Fresh delayed physical fixture command completed with241 observed app gesture samples and gain change. |
| F2 | Fresh device low/high invalid gaps returned parsed error envelopes,exit2 and0 input events. |
| F3 | Fresh actual device probe returned ready,no input and exit0. |
| F4 | Fresh real host CLI against Android stubs returned input_failed envelope and exit3. |
| A1 | Referenced evidence exists and hashes were rechecked. Earlier observation retained, not a fresh physical replay. |
| U1 | Fresh supported volume getter reports1/30. Excluded-device Spotify route was not taken over. |
| U2 | Both preference files compared byte-for-byte after final launch. Four-line settings comparison passed. |
| U3 | Fresh package dump:audio denied with baseline flags. Original4 notification listeners enabled. No URI grant added in this pass. |
| U4 | Fresh getters confirmed stay-awake7,screensaver0,timeout600000ms. |
| U5 | Asynchronous listener retirement settled,then actual source services were absent,projection null and only2 baseline preference files remained. |
| U6 | Fresh installed base APK hash matches exact743d8bb artifact eba2a0bc. Rollback remains retained. |
| U7 | Referenced evidence exists and hashes were rechecked. Earlier observation retained, not a fresh physical replay. |

Fresh raw results: `postmap-android.log`, `postmap-native.log`, `postmap-relay.log`, `postmap-cli-results.json`,
`postmap-sheet-analysis.json`, `postmap-sensor-result.txt`, `postmap-default-off.log`, `postmap-capture-analysis.json`,
`postmap-restoration-settled.txt`, `postmap-oracle-results.json` and `postmap-atomic-checks.json`.
The private `postmap-spotify.xml` establishes the active excluded-device route. Do not expose its personal media metadata.

The whole-result automated rerun and named fresh physical checks passed. The broader fresh live matrix is not complete.
Resume captured-transport testing only when Spotify is local to ASUS without commandeering playback on the excluded device.
Prior local-gain/import/dolly observations remain labeled prior evidence. Physical gravity and release gates remain separate.
