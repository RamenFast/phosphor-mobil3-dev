# Serious todos

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
