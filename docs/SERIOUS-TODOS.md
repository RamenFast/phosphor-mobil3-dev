# Serious todos

## Current complete-baseline campaign, 2026-09-10

Goal: tested, stable, fully featured, polished Phosphor mobile baseline in
`/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`. Root capture stays deferred.
GPT-6/Astra later review: top-level `GPT6-REVIEW-LATER.md`.

Temporary routing until GPT-6 usage resets: Grok 4.6 high implements and does 3D.
Muse/DeepSeek own UI design and visual critique. Grok still scores code only.
Pass >=8/10, max four attempts, old scores immutable.

- [x] Deferred-root stub `b222818` (Grok 8 then 9; bounded ASUS).
- [x] R06 brightness + Android-compatible archive checksum `b74beb2` (Grok blocked/8/8; 921 tests; bounded ASUS).
- [x] U1 tactile no-track console `7204b91` (Grok 8/8/8, Muse 5/7/8, DeepSeek 8/8; 933 tests; bounded ASUS).
- [x] Jcode fresh-path null-bool activation `d4b6aab0` (Grok 8 then 9). Huge-history transport remains open.
- [x] R09 source: service-owned mic, route proof, visualization mixer. 946 tests, Grok code 7 then 8. Not device-accepted.
- [x] R09 ASUS bounded built-in start/stop at 48 kHz mono on NAAIB70036673ZC. Prefs restored, RECORD_AUDIO revoked.
- [x] R09 ASUS PiP identity: same pid and MicCaptureService across HOME PiP. Prefs restored.
- [x] R09 PiP return: native segs resume after surface rebind 9ff96bd (Grok 8). Prefs restored.
- [x] Duplicate built-in SRC rows: distinct `(id)` labels; both ASUS bottom/back start. Muse 9. Prefs restored.
- [x] R09 linger: local wav + linger_background on, HOME keeps PlaybackService foreground with media focus. Prefs restored, force-stop.
- [x] R09 HUD overlay: appops SYSTEM_ALERT_WINDOW allow + SHOW FLOATING HUD presents TYPE_APPLICATION_OVERLAY over home (`hud-over-home.png`). Revoke ignore closes HUD. Restored appops default. dumpsys permission still granted=false; appops is the live gate.
- [ ] R09 remaining: accessory USB/BT (none connected; bonded but disconnected, not taken over), playback+mic mix (needs MediaProjection consent).
- [x] HDR request path source: `hdr_requested` off by default, selector, pipeline rebuild, HOLD transfer 2.0. Grok 7 then 8. Sibling `3171e0f`.
- [ ] HDR device proof: Vulkan identity, dataspace after present, matching metadata, honest SDR fallback on ASUS. Screenshots are not nits.
- [x] R14 startup source: default none, process-death fresh, import unconfirmed. Grok 7 then 8.
- [x] R14 ASUS matrix: last-used is not default; unconfirmed mic inert; confirmed mic auto-starts; process-death fresh; capture not prompt-free. Prefs restored.
- [ ] R14 remaining: rotation/same-process recreate (configChanges skips orientation; CLEAR_TASK stopped mic). Root auto-start stays deferred.
- [x] Tactile track well source: look 2 stays tactile with transport. Grok 7 then 8. `0d9d751`.
- [x] Muse visual of tactile track console with a real track: look 1 was 7; look 2 **9/10 PASS**. Prefs restored.
- [x] Console/status loud-frame flatten source: Grok 8. `bd35b08`. ASUS no-track shot taken.
- [x] Muse visual of flattened chrome: 8/10. Idle SIGNAL CHECK overlay accepted as quiet on that shot.
- [x] Muse pixel score of a real track: look 2 **9/10**.
- [x] Sheet B1 source: vector close, checked-row tick, manual inline clear. Muse code 9. Device pixels open.
- [x] Sheet B2 source: opaque non-Glass plates, LinkCard prose `open`. Muse code 9.
- [x] Sheet B2b: hide StatusBand while a sheet is open. Muse code 9.
- [x] Muse visual of sheet plates + LinkCard: **9/10**. Prefs restored.
- [x] Sheet B2c source: trailing-action rail drop at <340dp or fontScale ≥1.3. Muse code 9.
- [x] Muse visual of SOURCE trailing-action drop: **9/10**. Prefs restored.
- [x] ChipCell latch source: surface2 + 1dp sink when active. Muse code 9.
- [x] Muse visual of ChipCell latch: **8/10**. Prefs restored.
- [x] Edge flash source: 80ms tactile HIGH bevel pulse, gated, luminance ≤0.60. Muse code 9.
- [ ] Edge flash ASUS still-frame: 80ms pulse not captured this run (look 2 APPLY path). Code accepted. Prefs restored.
- [ ] Remaining visual: non-AMOLED recipes unshot; two press languages on look 1 StoneKey (accepted watch).
- [x] Fresh-install tactile default: empty prefs seed AMOLED look 2. Upgrade-like prefs stay look 1. Grok 8.
- [x] ASUS empty-install tactile default look 2 vs upgrade-like prefs stay look 1. Prefs restored.
- [x] Partial ASUS regressions 2026-09-10: SIGNAL CHECK, LIGHT preset latch, HOLD pause/return, settings, SRC, ChipCell, HUD, linger. Prefs restored.
- [ ] ASUS regressions still open: landscape (rotation lock), TalkBack (not toggled), dashed disabled play, named saved-look APPLY.
- [x] Five ASUS lifecycle cycles (start/Home/return/recreate/stop). Prefs restored.
- [ ] ~~30-minute soak~~ Ben 2026-09-10: do not soak. Idle soak cancelled. Prefs restored after ~4.5 min. Do not resume.
- [ ] Keep `GPT6-REVIEW-LATER.md` current when a unit lands so Astra can review after GPT-6 usage resets.

Heartbeat `eae54bc2-7c95-4bd5-94f2-4745e05d2a78` stays on until the list above is
genuinely complete or honestly blocked. Historical items below are not the live
baseline order.

## Active mobile expansion, 2026-09-08

- [ ] Finish section 1's exact artifact boundary after [independent round 1, 7/10](plans/mobile-expansion/critiques/section-01-round-01.md). The original 91 parser checks passed, but real debug APK plus unrelated production XML escaped the archive gate. Correct actual packaged-manifest binding and two error contracts, then independently review round 2.
- [x] Obtain the required once-per-session Astra/high routing confirmation before workers. Ben confirmed continuation at 01:11:40 UTC after the single beacon. Section 1 independent round 1 is running, with no score assigned yet.
- [ ] Prove the fixed packaged helper and non-diverting PCM. App-origin KernelSU UID 0 is verified on exact ff7067e with unchanged settings. The existing provider's successful identity command is not a PCM receipt or a portable shipping backend. Preserve the observed manager/driver mismatch without automatic kernel/system repair.
- [ ] Complete all R01–R17 feature and integration evidence in [the expansion execution ledger](plans/mobile-expansion/EXECUTION.md). No new feature is delivered by the initial baseline/contracts checkpoint.

Ben's 2026-09-08 approval permits reversible S25 expansion work and supersedes older S25 exclusions below for that task. Historical B-card evidence and unrelated release/irreversible-action boundaries remain intact.

## Primetime cleanup

- [x] Preserve the dirty starting material and create a rollback tag.
- [x] Reset active vision, specification, and decision authority.
- [x] Remove the dormant runtime administration and audit graph.
- [x] Preserve the user-facing HUD value, then scrub obsolete private state.
- [x] Collapse Android packaging to one debug/release product.
- [x] Make the Gradle wrapper the sole Android build authority.
- [x] Set the compatibility floor to Android 10, API 29.
- [x] Request microphone permission before projection when playback capture needs it.
- [x] Request full-display projection by default on Android 14 and newer.
- [x] Limit saved PC relay hosts to Tailscale endpoints.
- [x] Remove process-wide Wi-Fi/mobile routing that could bypass Tailscale.
- [x] Add and expose a privacy policy.
- [x] Make dirty, untagged, mismatched, and unknown release provenance fail closed.
- [x] Add canonical APK/AAB, bundletool, signer, 16 KiB, source, manifest, and checksum gates.
- [x] Make device installation verify exact APK bytes and signer.
- [x] Finish comment cleanup and active-document reconciliation.
- [x] Run all Android, Rust, relay, CLI, boundary, and shell gates.
- [x] Build and install the exact current implementation APK on the Galaxy S25 and verify installed bytes and signer.
- [x] Complete the S25 capture, denial, process-death, rotation, true multi-window, relay, disconnect, and dormant-network matrix.
- [x] Reconcile local branch ancestry without pushing protected branches.
- [ ] Exercise the true screen-lock callback after explicit approval to manipulate the PIN-protected keyguard state.
- [ ] Provision the approved direct-APK and Play-upload signers and build the final APK/AAB.
- [ ] Verify the production signer, manifest, archive boundary, and 16 KiB native alignment. The same flow passes with an ephemeral fixture signer.
- [ ] Install the exact packaged production APK on the phone with `dev/pm3 --profile release --serial <serial> install <APK>`.
- [ ] Preserve wanted Fortress settings and obtain explicit approval before uninstalling `dev.phosphor.mobil3.fortress`.

## Pre-v2 B1-B21 lived repairs

- [ ] Close the B1-B21 lived-repair matrix before the v2 release path continues. Every card needs its active-spec contract, automated gate, honest live receipt, ledger update, and rollback point. Keep `spec-version: pre-v2-b1-b21` and `drift: 21` until all 21 receipts pass. Track the umbrella in [issue #7](https://github.com/RamenFast/phosphor-mobil3-dev/issues/7).

## Deferred networking work

- [ ] Bind the PC relay to an explicit Tailscale interface or address.
- [ ] Add protocol-level peer authentication only if Tailscale identity is no longer the boundary.
- [ ] Polish discovery, connection recovery, latency, and long-session behavior.
- [ ] Re-run remote artwork, output switching, file playback, and link-state receipts.

## Post-v2 structure debt

These items remain outside the B1-B21 behavior scope:

- [ ] Reconcile capture-reader lifecycle and raw reader-thread ownership beyond the narrow B2 microphone handoff.
- [ ] Split oversized Activity, playback/capture service, and RemoteFlow responsibilities only with a separate accepted plan.
- [ ] Reconcile render, session, and serve-client teardown ownership.
- [ ] Rework release scoreboard and envelope debt without weakening the exact release reds or CLI contract.
- [ ] Define the full native playback-event ownership policy after the narrow B6 metadata consumer lands.
- [ ] Remove duplicated default authority and wake ownership after behavior receipts prove the current contracts.

## B2 verification limits recorded during source review, 2026-09-05

- [ ] Complete the exact-candidate five-cycle microphone matrix after Ben's quiet-work boundary changes. Include ordinary permission retry and safe source supersession. Do not claim JVM coverage proves Android consent or Activity recreation.
- [ ] Keep post-start microphone read-error recovery in the separate reader-lifecycle work. The existing loop can remain nonresponsive after a successful startup without updating the live face. B2 changes startup and handoff only.

## Deferred product work

The next two major core features are intentionally absent from this backlog until Ben provides the feature brief. Historical plans are not active todos.

## 2026-09-05 device findings

- [ ] Phase 6 task 6.3: fix the reproduced `Missing implementation to handle COMMAND_RELEASE` service-shutdown crash. The B9 receipt contains its DropBox evidence.
- [ ] Phase 5: retain the notification-access recovery and stop publishing an inverted capture state when that permission is absent.
- [ ] Check the capture-to-local queue transition: the band can say `no source` while the local queue plays and the trace is lit.
- [ ] Check paused local seek position reporting: the mirror reads zero until playback resumes at the requested destination.
- [x] Resolve the S25 logcat evidence gap. A temporary read-only logd client captured the complete B9 rerun. Raw replay matched exactly, and the helper was removed. No system file or mount changed. See the Phase 2 receipt addendum.
- [ ] Investigate oversized-provider cancellation separately from the passed representative B4 path. A 20,001-file stress fixture caused an external-storage-provider ANR before app grant. Mic eventually won without stale local publication, but started about ten seconds after selection. The fixture was removed and normal picker recovery passed. Before another large stress run, assess cancellable provider queries and a bounded fixture. See the Phase 3 receipt.

## Rotation and Android acceptance limits, 2026-09-06

- [ ] Exercise system hold, stale setters, import while held, stationary unlock, all cardinal/app-lock combinations, local beam sign, asymmetric corners/RTL and short windows on the exact committed S25 candidate. The rotation receipt records source/host checks only.
- [ ] Exercise real SheetHost partial drag followed by Back/close and late end/cancel/reversal/fling. Verify the committed base, continuation and exactly one completion across Compose scheduling/disposal. Actual state and Animatable host checks pass, but they do not deliver Android pointers.
- [ ] Reconcile Activity recreation and retired gravity-listener authority. The current listener lifecycle can outlive an Activity, while applied presentation is transient. Keep this distinct from the bounded system-lock and dismissal corrections.
- [ ] Disposition remote-geometry rotation separately. Remote geometry bypasses the local native view-rotation owner. Local beam checks and symmetric resting dots do not prove remote cardinal behavior.
- [ ] Verify or correct actual header release velocity and cancellation semantics. Header velocity remains zero and cancellation uses the existing distance-based settle path. The committed-displacement fix does not close these precommit behaviors.
- [ ] Distinguish an unreadable OS rotation setting from a known lock in user-facing status. Current fail-closed behavior is preserved, but its diagnostic text conflates the two states.
- [ ] Complete B17 visible/native clean defaults on a supported Android runtime. The isolated translated emulator proves selected startup/persistence and PiP observations, not native rendering. Five backend attempts and their failures are preserved in the settings receipt.

## Full-gate preflight limits, 2026-09-06

- [ ] Resolve whole-sibling formatting drift across53 files before claiming the required workspace format gate. Keep the pinned shared implementation unchanged until the scope is explicitly dispositioned. The host-preflight receipt preserves all observed formatter differences.
- [ ] Complete the planned public-publisher sanitization/confinement fixture and private scratch/estate-script omission proof before invoking publication or the force-link-state harness. Existing publisher code remains untouched and the fixture is absent. This is a release-path blocker, not permission to skip the gate.

## 2026-09-06 observed phone follow-ups

- [ ] Reproduce B8 against a stable live Spotify session with synchronized visible-symbol and source-state evidence. Stable checks passed, but the user-reported inversion is not resolved by this session.
- [ ] Diagnose final-session disappearance after the Spotify Play request. Concurrent package-change callbacks were observed, not a proven cause.
- [ ] Fix paused local seek publication: real drags sometimes report position zero until resume. Also recheck the captured source/mirror position disagreement.
- [ ] Fix/retest replay at the last local item's end. One real Play attempt emitted immediate native unpause/pause and stayed at the end.
- [ ] Finish the exact open B-card cases in the September 6 phone receipt. Do not replace real gesture, timeout, style, source/linger or emulator acceptance with host assertions.

## 2026-09-06 continued phone checkpoint

Final-item replay is repaired in 63fc7ef with real before/after proof. Four later paused seeks were correct but do not erase the prior intermittent-zero report. PiP manual/auto controls and accelerated main/PiP wake passed bounded checks. The alleged PiP-return chrome defect did not survive matched-control timing checks, so no speculative patch remains. B8 inversion/buffering, exact gesture timing and the other gaps in the [continuation receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-continuation-2026-09-06.md) remain open. The scratch UI helper must wait for settled chrome rather than interpreting a missing SRC node as a product failure.

## 2026-09-06 final test-pass disposition

The [final pass receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-final-pass-2026-09-06.md) supersedes stale coverage gaps: real buffering propagation, double-tap on/off, slider-lane samples, 42-key physical import/rollback, all-source linger/removal, and bounded trace stability are now observed. These are not blanket card closures. Remaining: unreproduced B8 inversion, exact physical multi-pointer/333ms tests (finite shell driver killed with exit137), clean-emulator native rendering (five prior failures), complete surface destruction/recreation, exhaustive LEG/default/mode/auto-gain-reset combinations and fresh-Linux release acceptance. Android currently appends .json to exported .phossettings filenames; round-trip works, but clarify the intended filename/MIME contract in follow-up. Do not repeat the same failed driver/emulator runs without a new hypothesis.

## 2026-09-06 20:43 UTC: narrower remaining gates

The driver initialization failure is repaired and four physical two-pointer cases passed.
Full native surface destruction/recreation is now observed in replay-verified logs.
These supersede those two earlier blockers only. Exact physical333ms timing, moving-trace recreation, B8, native emulator rendering and exhaustive variants remain open.
See the [gesture recovery receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-gesture-recovery-2026-09-06.md). No independent worker review occurred while routing confirmation remains unanswered.

## 2026-09-06 ASUS acceptance and additional fixes

ASUS physical clean-start native rendering and default controls now pass, replacing the missing clean-device evidence but not repairing emulator translation.
The fast-TIMER guard bypass and omitted untouched export controls are fixed and installed in `6183aa8`. Actual warning/recovery and all 41 exported values passed device checks.
Remaining B8, synchronized physical 333 ms timing, moving-trace recreation, exhaustive variants and human/release gates are listed in the [ASUS receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-asus-2026-09-06.md).
Do not use the S25 for further tests.

## 2026-09-06 23:48 UTC: ASUS recreation and gesture evidence

Two complete native surface teardown/recreation cycles now preserve the measured moving trace.
Eighteen screenshots show mean bright-trace changes of −0.54% and +1.16% with overlapping ranges.
The ASUS-adapted finite driver passed a no-input probe, free pinch, latched bottom-band rejection,
held console-margin rejection and an adjacent allowed-stage comparison. Seventeen host timing tests passed.
The exact physical 333ms timestamp remains unobserved, not a failed product check or a completed test.
B8, exhaustive variants and human/release gates remain open. See the [new receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-asus-recreation-2026-09-06.md).

## 2026-09-07 00:03 UTC: narrower render variants

The full eleven-mode grid matrix, asymmetric XY/Xy45 trace rotation, both raw channel labels and GRID/GRID DATA opt-outs now pass on ASUS.
These supersede those specific untested variants. System-lock/import/cardinal presentation, broader source/item auto-gain resets,
translated-emulator failures, exact physical333ms, B8 and human/release gates remain open.
The [mode receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-mode-grid-2026-09-07.md) records pixel measurements and test assumptions without declaring complete B20 or visual signoff.

## 2026-09-07 00:18 UTC: ASUS capture reproduction result

Real logged-in Spotify capture, the initial Pause and four later transitions matched actual source/mirror states and UI glyphs.
No-listener fallback and eventual post-revocation fallback also appeared. Revocation latency was not bounded.
B8 remains unreproduced, with no speculative fix. See the [capture receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-spotify-2026-09-07.md).
The original permission states, listener membership/cache and app tuning were restored. Spotify remains paused on the same track.

## 2026-09-07 observed capture race correction

The [new ASUS receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-capture-race-2026-09-07.md) records a reproduced transient B8 race and an installed candidate correction.
The earlier “unreproduced” wording is no longer accurate for that specific failure. Real buffering and sustained-inversion variants remain open.
A dense physical pinch also exposed discarded sub-threshold scale movement. The 333ms guard blocked correctly at observed 332/333ms and rebased at334ms, but slow pinch movement needs correction and revalidation before closing the fixture pass.

## 2026-09-07 physical slow-pinch checkpoint

The discarded slow movement is corrected and verified across eight ASUS overflow-dismissal runs.
The [receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-slow-pinch-2026-09-07.md) narrows the exact333ms gap to untested hosts/orientations and panel scanout.
One-pixel console movements restart the global stage guard as specified. Their ergonomics are not independently signed off.
A later Settings/gain7 observation occurred outside the fixture window. Preserve possible newer user tuning until ownership is resolved.
Rotation listener retirement remains under investigation. One live registration is observed, but recreation leakage is not yet reproduced.

## 2026-09-07 sensor retirement and autonomy update

The retained sensor-listener concern is reproduced and corrected. Settled connections grew1/2/3 before,
and stayed1 through three Activity replacements afterward. Physical gravity poses and import presentation remain separate.
Ben explicitly confirmed exclusive ASUS test ownership. The suspected-user-activity hold is resolved.

## 2026-09-07 remaining acceptance after installed checkpoint

Passed: actual loud-to-quiet local gain adaptation, silence hold, paused seek at9000ms, disabled Android-dependent controls,
held landscape import in portrait, unlock-to-landscape, sensor listener retirement and exact final state restoration.
See the [checkpoint](dev/receipts/pre-v2-b1-b21/phase-16-asus-final-checkpoint-2026-09-07.md).

Still open: actual capture BUFFERING/CONNECTING and every sustained inversion variant, physical gravity/cardinal poses,
exact sheet/cardinal gesture deadlines and compositor scanout, human grid/design approval, and separately authorized Linux/release gates.
The sheet-close fixture delivered no stage gesture callbacks, so its stable gain is not an exact333ms measurement.
No current permission or cleanup blocker remains. Ben's dedicated-ASUS takeover authorization remains in force.

## 2026-09-07 final-artifact audit limits

The [new matrix](dev/receipts/pre-v2-b1-b21/phase-16-asus-final-artifact-audit-2026-09-07.md) supersedes the sheet-callback gap above for one controlled portrait run.
The source-sheet run now observed332ms blocked and340ms rebase. It did not sample exactly333ms or every orientation.
The final APK passed real capture replay/recovery, local paused seek, slow gain, VIEW LOCK, camera perspective and same-process listener replacement checks.
Actual capture BUFFERING/CONNECTING, sustained variants, physical gravity poses, broader timing, panel scanout and human design approval remain unobserved.
Remote audio and release acceptance retain their explicit authorization boundaries. No branch merge, push, publication or deployment was performed.
The malformed-input fixture error was corrected and verified on-device. No current audit cleanup blocker remains.

## 2026-09-07 03:38 UTC: fresh capture replay boundary

The post-mapping real attempt reached generic capture with zero routed commands. It is not a transport pass.
The actual Spotify screen on ASUS showed active Connect playback on the excluded S25.
No Spotify play,pause or route-transfer command was sent. Do not take over excluded-device playback for an acceptance test.
Repeat captured transport only when the source is local to ASUS without disturbing that route.
Forced whole-result regression and fresh slow-pinch,VIEW LOCK,listener-retirement,default-off and fixture CLI checks passed.
The [atomic receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-atomic-traceability-2026-09-07.md) distinguishes each fresh result from revalidated prior evidence.


## 2026-09-10: shared status band at font scale2

Muse U1 visual round1 addendum reports src/mode overlap or truncation in the shared
status band. This is a pre-existing baseline TODO, outside the tactile key-bed unit.
Confirm against a baseline font2 capture before changing shared layout.
Evidence: `dev/scratch/visual-u1-20260909/muse-visual-round-01.md`, Addendum A.
Temporary gain changes from coordinator gestures do not establish a source defect.


## U1 visual round2 follow-up boundaries

The shared font2 status issue now has measured-width reflow and measured band clearance
for SIGNAL CHECK. Source tests pass only after their command is recorded in the U1 ledger.
Actual font2/landscape captures remain required; this is not device closure of the TODO.
DeepSeek D4 identifies the inherited outer console frame as brighter than essential key edges.
Keep that baseline hierarchy issue deferred. Do not repaint authored Dark/Glass backgrounds
to meet an absolute AMOLED-only mean-brightness metric.
