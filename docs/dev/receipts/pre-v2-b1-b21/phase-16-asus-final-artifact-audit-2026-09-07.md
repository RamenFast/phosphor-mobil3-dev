# ASUS final-artifact regression audit

2026-09-07. This audit follows the request to verify the whole result through actual app paths.
It closes the measured capture, slow-pinch and listener-retirement corrections, not every B1-B21 scenario.
No production code changed during this audit. The retained input fixture gained a validated post-tap delay.

## Identity and boundaries

- Device: ASUS_AI2202, Android 14, portrait 1080 by 2400. ADB identity: [redacted].
- Installed implementation: `743d8bb`, including capture truth `61e0630` and slow pinch `18569be`.
- Package: `dev.phosphor.mobil3.debug`, version `2.0.0-debug`.
- Installed APK SHA-256: `eba2a0bc433055dec0a01650e49e5df0c4e8318fc1b227d4c50f8d933a3b57e5`.
- Debug signer: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- The supported `dev/pm3 install` had verified installed bytes and signer. This audit rechecked the installed base APK hash after cleanup.
- Root-only ASUS testing. S25 excluded. MUSIC stayed 1/30. No PC audio, deployment, signing, tag, push or publication.

Raw evidence remains in ignored `dev/scratch/asus-timeline-20260907T0113Z/`.
Names below are relative to that directory. Recordings and personal source metadata remain private.
The adjacent [SHA-256 manifest](phase-16-asus-final-artifact-audit-2026-09-07.sha256) pins twelve principal evidence files.

## Requirement-to-observation matrix

| Requirement or changed output | Actual check and observed result | Evidence and limits |
|---|---|---|
| Capture glyph follows observed source state, not Media3 predictions | Real Spotify routed to this phone. Ten physical console taps produced ten routed commands and ten glyph changes, with zero premature changes. Before the fix, five commands produced five premature changes. | `audit-final-capture-analysis.json`, `audit-final-capture.log`, `audit-final-capture.mp4`. Draw-time observation, not panel scanout or source internals. |
| Capture toggle uses the observed state | Commands alternated play and pause against actual source callbacks. Source and mirror ended PAUSED at 92309ms after the final explicit pause. | Same timeline plus `audit-capture-paused*`. No synthetic media-session provider. |
| Activity reconnect retains current capture truth | Home, foreground re-entry and actual console interaction showed the current source and correct Pause glyph. The subsequent pause showed Play. | `audit-capture-reconnected*`, `audit-capture-paused*`. Queued stale callbacks are separately covered by binding tests, not artificially injected here. |
| Lost capture access removes stale transport authority, recovery restores it | Notification-listener access removal showed generic capture text without transport keys. Restoring access recovered the current paused item with Play. | `audit-capture-unavailable.xml`, `audit-capture-recovered.xml`, corresponding system dumps. This is a real permission transition. |
| Noncapture playback still publishes actual local title, duration and state | Actual SOURCE picker, persisted tree grant and WAV decode produced a moving helix, item title, 1:30 duration, advancing time and Pause. EOF showed 1:30 and Play. | `audit-helix-playing.xml`, `audit-dolly-delayed-after.xml`, perspective screenshots and XML. The WAV is controlled content decoded by the real app, not a renderer stub. |
| Paused local seeking does not resume or drift | Actual console swipe while paused moved to 10848ms. Two system dumps retained PAUSED, speed 0 and update timestamp 2785746. UI showed 0:10 and Play. | `audit-final-paused-seek.xml`, `audit-final-paused-seek-first.txt`, `audit-final-paused-seek-held.txt`. |
| Dense slow pinch accumulates movement | Actual SOURCE close followed by a 300ms requested gap and 240 slow moves changed gain 1.833 to 2.0050182. UI showed 2.01. Old per-frame rejection had discarded this movement. | `audit-sheet-gap300-input.jsonl`, `audit-sheet-gap300.log`, `audit-sheet-gap300-analysis.json`, `audit-sheet-gap300-after.xml`. |
| Chrome guard discards blocked travel and rebases before applying | The same run recorded 241 decisions: 22 Blocked, one Rebase, 218 Apply. An observed age of 332ms remained blocked. Rebase occurred at 340ms. Blocked gain and the first post-rebase sample stayed unchanged. | Same sheet evidence. Exact 333ms was not sampled here. Earlier eight overflow runs sampled the inclusive 333ms boundary. Every observed card movement restarts the guard. |
| VIEW LOCK retains gain during broad pinch | With actual locked preferences loaded, a physical 380-to-780px pinch left the displayed gain at 1.83. | `audit-final-viewlock-input.jsonl`, `audit-final-viewlock.xml`, `view-lock-test-prefs.xml`. This checks gain lock, not all camera-lock combinations. |
| Shared slow-pinch sampling still drives the 3D camera rather than gain | Four delayed slow outward pinches changed the actual helix perspective. Four inverse pinches returned its bounds close to baseline. Gain remained 1.83 and playback advanced from 0:21 to 0:40. | `audit-dolly-perspective-{before,changed,return}.png`, matching XML and input logs. Upper trace edge 789 to 773 to 787px, left edge 25 to 11 to 26px, lower edge 1633 to 1612 to 1634px. Controlled periodic source, not exact camera-coordinate telemetry. |
| Destroyed Activities retire their gravity listener | On the exact final APK, three same-process Activity replacements retained one active listener after settling. Before the fix, settled counts grew 1, 2, 3. | `audit-final-sensor-result.txt`, `audit-sensor-final-*`. Final process stayed 30018. A bounded settle check excludes normal destruction overlap. |
| Local auto-gain resets across items and holds silence | Earlier same-implementation actual queue replay observed loud gain 1.15, quiet 4.51 then 6.00, and silence holding 6.00 while time advanced. | [Installed checkpoint](phase-16-asus-final-checkpoint-2026-09-07.md). Not rerun with all three files in this audit. No gain implementation changed afterward. |
| Android rotation authority survives saved landscape import and unlock | Earlier same-implementation real picker imported five settings. Android lock kept portrait and disabled dependent controls. Enabling Android rotation applied landscape without new gravity motion. | Same checkpoint. Final-artifact lifecycle was rerun above. Physical cardinal poses were not reproduced. |
| Diagnostic fixture does not inject malformed delayed gestures | Version 4 compiled through Gradle and passed the existing seven invalid coordinate/bounds cases. Device gaps -1 and 1001 returned parsed error envelopes, exit 2 and no input events. The final probe reported no injection. | `audit-driver-errors-build.log`, `audit-driver-final-errors.json`, `audit-driver-final-probe.jsonl`. Initial uncaught errors printed `Killed`. A small top-level exception handler corrected that tool defect, and the same invalid inputs were rerun. |
| Privacy, no new runtime administration and exact restoration | Original two preference files and four baseline setting lines compared byte-for-byte. RECORD_AUDIO is denied with baseline flags. Four original notification listeners remain enabled. Only the owned tree grant disappeared. Installed APK hash is unchanged. | `audit-restored-*`, `audit-cleanup-*`, `audit-final-state.txt`, `audit-final-volume.txt`. See restoration details below. |

## Measurement corrections, not hidden passes

An initial 3D check expected a greater-than-10-percent uniform shrink. The measured height did not shrink, so that assertion failed.
Its shell pipeline initially exposed only the successful `tee` exit. The failure text and raw pixel result are retained in `audit-helix-pixels.json` and the session log.
No shrink claim relies on that command. Later commands use `set -euo pipefail`.

The camera uses focal perspective, not uniform gain scaling. Immediate tap-plus-pinch also did not establish stage ownership.
A live reader with a 700ms post-tap gap observed 241 actual stage decisions in `audit-dolly-routing-live.log`.
The final perspective sequence used that delay and a playing source throughout. Its reversible edge movement supports camera response, not uniform shrinking.
One intervening attempt reached EOF before its final screenshot. That attempt is excluded from visual comparison.

The earlier source-sheet close consumed pointers before the stage received them.
Version 4 supplies an explicit 0..1000ms delay without changing the app. The actual card timestamps still decide acceptance.

## Named regression coverage and limits

The whole-result host rerun is recorded in `audit-whole-result-*.log`.
It includes the project Android gate, fixture compilation, locked/offline native and relay suites, developer CLI fixtures and privacy/release-gate fixtures.
Observed results: 440 Android cases, lint, assembly and checkEngine passed. Native 73 and relay 43 cases passed.
Developer CLI, production-boundary and isolated release-gate fixtures passed. The source boundary checker passed all 11 checks.
The later fixture error-handler change passed a separate Gradle rebuild and the actual no-input device checks above.
`git diff 743d8bb -- app/src rust relay` was empty. The host rerun did not replace the installed exact APK.
Host policy tests supplement the real paths above. They do not replace them.

- `CaptureMirrorPolicyTest.optimisticControllerPredictionsCannotFlipTheCaptureGlyph` exercises prediction, rollback and observed callback sequences.
- `observedStateTravelsThroughSessionExtrasWithActivityOwnershipChecks` checks publication, retirement-to-NONE and Activity binding guards in production sources.
- `playingPausedBufferingAndEveryActiveTransitionMatchTheCaptureGlyph` and `routeWaitsForObservedStateAndBufferingCanPause` cover transient policy states.
- `staleCallbacksLoseAuthorityIncludingSameControllerReattachment` checks binding revisions. The actual reconnect and permission recovery rows exercise their public path.
- The six `StagePinchScaleTest` cases cover inward/outward accumulation, sample density, guard reset, stationary jitter and invalid distances.
- `RotationDetentReachabilityTest.sourceOnlyDestructionClearsListenerOwnershipBeforeUnregistering` and `sourceOnlyRetiredActivityCannotRegisterOrMutateRotation` supplement the actual before/after listener count.

Actual Spotify emitted PLAYING and PAUSED in the finite final timeline, not BUFFERING or CONNECTING.
Those transient public states remain policy-tested but not observed end to end. Sustained inversion variants remain unproven.
The no-PC-audio boundary excludes live remote audio acceptance. Its unchanged truth path has policy/native/relay regression, not a new live relay claim.
Physical gravity poses, every card/orientation deadline, queued stale sensor-event injection, compositor scanout and human design approval remain open.
Release-only no-op diagnostics have source checks, not an installed release proof. Signing and release provenance gates remain intact.

## Restoration after this audit

The hardcoded app-UID cleanup fixture released only the owned test tree. The system URI list changed from 75 entries to 74.
The exact set difference was that one owned URI. All three older app grants and all unrelated grants remained present.
The authoritative notification list contains the original four components. RECORD_AUDIO remains denied with the original sensitivity flags.

Both baseline preference files compare exactly after force-stop and restore. The empty generated host-preferences file was removed.
Rotation is 0/0 and opt-in logging is empty, matching the four-line baseline file exactly.
Stay-awake 7, screensaver 0 and unplugged timeout 600000ms remain as requested.
The supported `cmd media_session volume --stream 3 --get` reports 1 in range 0..30.
An initial unsupported `media` command did not produce volume evidence and is not used for this claim.

Only owned phone WAV, recording, XML and JAR files were removed. The empty owned directory was removed without recursion.
The app is stopped, its source services are absent and media projection is null. Private host receipts and rollback APKs remain available.
The installed base APK hash still equals the exact artifact above. No implementation or installation changed during this audit.
