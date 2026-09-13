# ASKS

| date | ask | status |
|---|---|---|
| 2026-08-05 | Preserve the PC relay and defer its polish to a later networking stage | active |
| 2026-08-05 | Remove analytics, behavior tracking, usage tracking, and automatic reporting | implemented |
| 2026-08-05 | Keep `dev/pm3` only as local developer tooling | implemented |
| 2026-08-05 | Consolidate Android builds onto one debug/release product and one Gradle authority | implemented |
| 2026-08-05 | Support the lowest Android release that preserves future audio development | API 29 implemented; device evidence pending |
| 2026-08-05 | Default playback-capture consent to the full display rather than one selected app | implemented on API 34+; device evidence pending |
| 2026-08-05 | Clean code, comments, docs, branches, artifacts, and release state before new core features | in progress |
| 2026-08-05 | Create the final `v2.0.0` tag only after explicit approval of the committed release source | approval pending |
| 2026-08-05 | Preserve wanted Fortress settings, then uninstall `dev.phosphor.mobil3.fortress` only with explicit data-loss approval | approval pending |
| 2026-08-05 | Provision the approved production APK signer and Play-upload AAB signer outside the repository | blocked on external signing inputs |
| 2026-08-05 | Add two major core features after the cleanup stage | waiting for feature brief |
| 2026-08-26 | B1: Add chrome-adjacent scope deadzones and the exact 333ms settle delay | open · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B2: Make capture-to-built-in-microphone switching start a live beam reliably | Phase 4 live PASS 5/5 on exact APK; final regression remains open · [receipt](dev/receipts/pre-v2-b1-b21/phase-04-b2-mic.md) · [#3](https://github.com/RamenFast/phosphor-mobil3-dev/issues/3) |
| 2026-08-26 | B3: Prove regular-app Tailscale relay, fresh-Linux setup, direct files, and whole recursive folders | verify · offline folder/UI repair, independent intent review and scoped gates passed; exact artifact and live Linux/Tailscale acceptance pending · [receipt](dev/receipts/pre-v2-b1-b21/phase-07-b3-relay-matrix.md) · [#3](https://github.com/RamenFast/phosphor-mobil3-dev/issues/3) |
| 2026-08-01 | B4: Make recursive local folder playback survive nested and invalid entries without freezing or going dark | verify · Phase 3 live acceptance passed, final regression remains · [receipt](dev/receipts/pre-v2-b1-b21/phase-03-b4-folder-tree.md) · [#1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) |
| 2026-08-26 | B5: Keep deposited scope brightness stable through settings and chrome cycles | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B6: Restore honest local and captured title, artist, and captured transport controls | offline implementation/reviews passed; live acceptance open · [receipt](dev/receipts/pre-v2-b1-b21/phase-05-b6-b7-b8-media-truth.md) · [#2](https://github.com/RamenFast/phosphor-mobil3-dev/issues/2) |
| 2026-08-26 | B7: Restore in-app captured-media seek only when the active session can seek | offline capability/routing checks passed; live acceptance open · [receipt](dev/receipts/pre-v2-b1-b21/phase-05-b6-b7-b8-media-truth.md) · [#2](https://github.com/RamenFast/phosphor-mobil3-dev/issues/2) |
| 2026-08-26 | B8: Make the captured-media play/pause glyph match the audible state | offline state/glyph checks passed; live acceptance open · [receipt](dev/receipts/pre-v2-b1-b21/phase-05-b6-b7-b8-media-truth.md) · [#2](https://github.com/RamenFast/phosphor-mobil3-dev/issues/2) |
| 2026-08-26 | B9: Keep local seek and navigation from freezing the app and sound | verify · Phase 2 logged live acceptance passed; final regression remains · [receipt](dev/receipts/pre-v2-b1-b21/phase-02-b9-seek-stress.md) · [#1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) |
| 2026-08-26 | B10: Preview FEEL, MOTION, CORNERS, and LABELS changes immediately | offline source/reviews passed; live acceptance open · [receipt](dev/receipts/pre-v2-b1-b21/phase-09-ui.md) · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B11: Keep the display awake while a playback or capture source is live | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B12: Add synchronized quick and full controls for PiP auto-entry | offline source/reviews passed; live acceptance open · [receipt](dev/receipts/pre-v2-b1-b21/phase-09-ui.md) · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B13: Keep queue in SOURCE, remove DECK and console volume, and add always-visible controls | offline source/reviews passed; live acceptance open · [receipt](dev/receipts/pre-v2-b1-b21/phase-09-ui.md) · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B14: Make seek, tuning and range sliders easier to adjust. Volume feature removed by September 6 override | offline source/reviews passed; live acceptance open · [receipt](dev/receipts/pre-v2-b1-b21/phase-09-ui.md) · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B15: Make the grid visible and add real left/right amplitude and absolute dBFS data | open · raw data and ownership host checks passed; three matched phone captures passed the contrast target after the installed correction; Ben confirmation pending · [receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md) · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B16: Rotate the grid with the Xy45 goniometer trace and restore it elsewhere | open · shared CPU/GPU mechanism and independent reviews passed; actual phone mode matrix pending · [receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md) · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B17: Preserve settings across versions and seed the accepted clean-install defaults | open · corrected source/reviews and frozen host gates passed; S25 byte-preservation and restoration verified; emulator defaults still pending · [receipt](dev/receipts/pre-v2-b1-b21/phase-12-b17-settings.md) · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B18: Add a dedicated portable toggle for double-tap playback | open · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B19: Protect scope gain and orbit from Android bottom-edge gesture overshoot | open · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B20: Keep auto-gain stable across silent gaps without changing its never-cutoff limits | open · gain hold and current-item reset host checks passed; one actual loud/silence/loud fixture passed; full source/item matrix pending · [receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md) · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B21: Stop sources on recents removal by default and make background linger optional | verify · offline implementation, separate reviews, 225-test gates and exact debug artifact passed; installation and Android acceptance pending · [receipt](dev/receipts/pre-v2-b1-b21/phase-06-b21-lifecycle.md) · [#1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) |
| 2026-09-05 | Recover the existing handoff, check wireless S25 readiness and disk capacity, then confirm before app changes | completed; latest stop was Phase 2 task 2.5 |
| 2026-09-05 | Resume the pinned B9 device test and preserve settings | Phase 2 PASS with replay-verified logs in [receipt](dev/receipts/pre-v2-b1-b21/phase-02-b9-seek-stress.md); settings restored; final regression remains open |
| 2026-09-05 | Test captured Spotify seek and play/pause separately from the local freeze, with permission prompts approved | reproduced and recovered by notification access; [receipt](dev/receipts/pre-v2-b1-b21/spotify-permission-recheck-2026-09-05.md); Phase 5 remains open |
| 2026-09-05 | Monitor disk and clear only stale or rebuildable artifacts if needed | 19 GiB available during device tests; no cleanup needed |
| 2026-09-05 | Use only approved Astra/Grok workers with explicit supported high-to-max effort and bounded concurrency | global preference saved; project ceiling remains two workers; dated execution override records exact ranges and route limitations |
| 2026-09-05 | Continue autonomous work without interrupting YouTube or music | active quiet-work boundary from 10:32 UTC; B2 offline delivery complete; roadmap authored without device, visible GUI, audio or active-service changes |
| 2026-09-05 | Write a comprehensive self-contained mobile codebase plan, using subfolders as needed and choosing the roadmap priorities | completed in [mobile-next](plans/mobile-next/README.md). Nine numbered phases and 21 tasks include contained context, source inventory, verification and rollback. Independent plan review PASS and root document/guard checks passed. Future implementation and publication remain unapproved |
| 2026-09-05 | Compare the reposted original bugs with the B1-B21 plan and verified progress | audited all 21 original requests against raw report, plan and acceptance matrix; B2/B4/B9 phase live checks passed, final regression open; remaining 18 cards open; [audit](FEEDBACK.md#2026-09-05-1721-utc-original-report-audit) |
| 2026-09-05 | Continue the original repairs to completion without interrupting a podcast | active from 17:45 UTC; Phase 5 and Phase 6 offline code/reviews/exact artifacts verified; Phase 7 implementation active; no phone/playback/visible GUI/active-service changes; actual live acceptance stays deferred |

| 2026-09-05 | Compress eligible agent-facing context without information loss. Exclude the separately governed dwelling and its exclusive files, as defined in [workspace governance](../../AGENTS.md#1--scope--neighbors). Preserve existing references and conditions. | documentation-only review and hash-verified integration |
| 2026-09-06 | Clean ephemeral files, install needed tools without extra prompts, and continue the original repairs | removed six obsolete daemon logs and eight temporary directories, 423579 bytes; retained rollback, evidence and reusable build inputs; Phase10 continues offline |

## 2026-09-06 phone acceptance updates

- Resume comprehensive S25 testing with existing exact artifacts and private backups. Active.
- Remove the console volume slider and percentage without restyling remaining controls. Completed and installed at 9b3cc62, included in final 6565585.
- Prioritize the repeated B8 Spotify capture play/pause symbol report. Real button and source-state checks required.
- Phone test volume must remain at most 15 percent while Ben sleeps. Current MUSIC index is 2 of 15. Do not restore a higher prior volume. Keep PC audio silent.

## 2026-09-06 device checkpoint

[Current phone receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-s25-2026-09-06.md) maps all B cards to actual observations and remaining limits. Three corrections are installed: console volume removal, measured grid visibility and the discovered SOURCE queue command. B8 stays open. No overall B1-B21 PASS is claimed.

## 2026-09-06 20:24 UTC: fix the remaining issues

Ben requested fixing the remaining failures, not merely listing them.
The [gesture recovery receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-gesture-recovery-2026-09-06.md) records a repaired Android16 test driver, four actual multi-pointer checks and proven native surface recreation.
B8, exact physical settle timing, emulator rendering and the remaining matrix stay open. No new app fix is claimed.

## 2026-09-06 replacement test phone and additional repairs

Ben supplied the ASUS_AI2202 and requested that testing move off his S25. The S25 is now excluded from further tests.
The [ASUS receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-asus-2026-09-06.md) records clean native rendering and two newly found, fixed and installed bugs: fast TIMER confirmation bypass and incomplete untouched-control exports.
Actual warning/recovery and a full 41-key defaults mutation/import passed. B8 and the remaining acceptance gaps stay open.

## 2026-09-06 23:31 UTC: restore USB and keep the ASUS alive

Ben fixed the cable and requested phone settings that keep testing connected.
ASUS USB authorization is working. Plugged-in stay-awake is 7, the screensaver is disabled, and Android confirms Awake with StayOn true.
The unplugged timeout and lock/security policy are unchanged. These requested settings remain enabled after cleanup.
The [continuation receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-asus-recreation-2026-09-06.md) records resumed moving-trace and gesture checks.

## 2026-09-07 01:11 UTC: approved remaining acceptance plan

Ben approved finishing the existing ASUS acceptance gaps before the separate mobile expansion.
Priority is synchronized Spotify truth, physical gesture settling, rotation/import variants and auto-gain item ownership.
Small evidence-led corrections are authorized. Preserve the UI, muted ASUS, excluded S25 and requested keep-awake settings.
Debug-only opt-in timeline observations may close measurement gaps without adding runtime administration.
Workers and release operations retain their separate approval gates.

## 2026-09-07 01:18 UTC: updated ASUS audio ceiling

Ben changed the ASUS volume and asked that testing never raise it further.
The explicit ASUS readback at 01:19 UTC is MUSIC 1/30 (3.3 percent).
Keep that level, do not restore the earlier 0/30 baseline or raise above 1/30.
PC audio remains silent and the S25 remains excluded.

## 2026-09-07 02:25 UTC: dedicated ASUS autonomy

Ben clarified that the ASUS is an old test phone he does not actively use. Jcode is its only operator.
Retake control after unexplained UI changes instead of pausing over suspected user activity.
This supersedes the earlier availability hold, not the volume1/30 ceiling, S25 exclusion or release boundaries.

## 2026-09-07 02:55 UTC: final-result acceptance audit

The automatic continuation requested real observations for the whole result and a requirement-to-check map, without a user interruption.
The [audit receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-final-artifact-audit-2026-09-07.md) records final-artifact behavior, prior same-source checks and explicit unobserved paths separately.
The final regression and exact restoration were completed. This does not convert remaining B1-B21 or release gates into passes.

## 2026-09-08 00:18 UTC: approved mobile expansion execution

Ben approved the complete [canonical expansion plan](../MOBILE-EXPANSION-PLAN.md) and autonomous reversible S25 work. This supersedes the older S25 exclusion for this task. The [execution ledger](plans/mobile-expansion/EXECUTION.md) owns per-section state and receipts. No new feature is accepted from planning or a baseline build.

| ID | Unique approved outcome | Status |
|---|---|---|
| R01 | Hidden opt-in root audio capture without LSPosed or system/boot writes | open |
| R02 | Floating HUD with solid and genuine transparent presentation | open |
| R03 | Smiling tailed turtle and indexed, comprehensive, meme-rich terminal manual | open |
| R04 | Meaningful animated glyphs, improved layout, and visible-only presentation work | open |
| R05 | Optional genuine HDR scope output | open |
| R06 | Optional full-foreground-app brightness pin without global changes | open |
| R07 | Independent generated-color and random cycle-interval controls | open |
| R08 | Six custom color slots with selected ordered/shuffled cycling | open |
| R09 | Actual external/Bluetooth mic selection and visualization-only capture mixing | open |
| R10 | More deliberate downward settings dismissal with opening unchanged | open |
| R11 | Expandable settings sections with truthful summaries and retained state | open |
| R12 | Light, Dark, Glass, AMOLED and shared customization preserving saved looks | open |
| R13 | Default HOLD pause with inspection and optional BLACK presentation | open |
| R14 | Configured default startup, contextual automatic permission requests, direct authorized root start | open |
| R15 | Independent GPT Astra section critiques, up to four total rounds below 8 | pending required routing confirmation |
| R16 | Named portable instrument presets distinct from appearance and source settings | open |
| R17 | Read-only on-demand signal check showing measured input health and dark-beam causes | open |

All worker workstreams use proposed Astra/high reasoning, at most two live workers and one implementation writer. The required routing beacon appeared once and expired without a new confirmation. Do not infer approval from that expiry or repeat the same unanswered notification. Publication, signing, irreversible changes, and unrelated relay work keep their existing boundaries.

## 2026-09-08 execution clarifications

- At 01:11:40 UTC Ben confirmed continuation after the routing question. Astra/high routing is now confirmed, not inferred from beacon expiry. The execution ledger owns current worker state.
- At 02:47:30 UTC Ben required read-only access to `/system`, including `/system/bin`, `/vendor`, and boot/vbmeta partitions. This applies through raw block-device aliases and both slots. Reads are allowed. The active expansion contract and helper contract now state this boundary explicitly. No remount, flash or bootloader change is authorized.
- At04:17:59 UTC Ben clarified that R01's purpose is true app audio in the vectorscope from any app, specifically SoundCloud. SoundCloud has not been tested and is not proven blocked. Investigate actual opt-out behavior and fuller-fidelity capture paths within the existing no-write boundary. Limited16kHz mono AudioPolicy feasibility alone does not complete this outcome.
- At04:21:19 UTC Ben explicitly required real stereo. Duplicated mono is not acceptable vectorscope input. Ben reports standard capture works with Spotify but the installed SoundCloud app produces no scope input, making SoundCloud the root smoke test. Preserve sample-rate fidelity and ideally honor the selected rate. The existing48/96/192kHz option currently controls beam reconstruction over a48kHz native input, not recorder negotiation. Report requested reconstruction and actual captured rate separately.
- At04:38:43 UTC Ben required testing and optimization for minimal added capture latency. The scope must remain aligned with the music heard on the current output. Compare the candidate against normal playback on the same output. Separate added software delay, existing route latency and measured display alignment. No numerical latency promise or acceptance follows from a moving beam or successful PCM writes.
- At04:56:09 UTC Ben authorized a conditional fallback if full root capture remains unsolved: leave a root-capable entry point with encouraging words for future work, then continue the remaining expansion. This does not require stopping the current bounded stereo experiment. A fallback must distinguish working root authorization from unavailable stereo capture, retain evidence and recovery, and never present duplicated mono or unmeasured latency as the requested result.
- At09:00:39 UTC Ben offered an unrooted ASUS phone for non-root acceptance while he sleeps. Leave the rooted S25 undisturbed. At09:07 the ADB device list contained only the wireless S25. At09:09 USB enumeration contained no phone. No ASUS command, install or acceptance occurred. Continue offline corrections until the offered device appears, without waking Ben or changing the S25.

## 2026-09-09 recovered release and continuity requests

- At03:16 UTC Ben deferred root capture from the next release and requested an encouraging stub button. This supersedes older instructions to finish working root capture for that release. The current implementation still exposes operational controls. Stub implementation and matching plan/spec/manual changes remain open.
- At03:16 UTC Ben requested Grok 4.6 with max reasoning for subsequent independent verification and offered OAuth sign-in. At04:06 UTC he requested session diagnosis and xAI OAuth setup. The Grok Build backend is provisioned, with device authorization pending at the recovery checkpoint. Exact model and effort support remain unverified. Existing defaults were preserved.
- At03:29 UTC Ben requested a comprehensive repository handoff because the chat was failing. The 04:16 UTC recovery checkpoint in [HANDOFF.md](../HANDOFF.md) supplies source pins, inherited and freshly checked evidence, all remaining outcomes, latest boundaries, and ordered continuation. The original saved session remains unchanged and backed up.


## 2026-09-09 Prime continuation and current TODOs

This current-status entry supersedes old pending-stub/provider/device claims above, without changing historical receipts.

- Done: deferred-root stub b222818. Grok source scores 8 then9/10. Installed/readback verified on ASUS; local preview, standard consent and legacy-root inertness checked. Preferences restored, app stopped, MUSIC1/30.
- Active: R06 brightness source authored; 67 focused tests pass. Full Android build, code review and real ASUS acceptance remain.
- Active: independent visual proposals, Muse1.3 Contributor direct high (Ben-approved fallback) and DeepSeek v4.1 flash `deepseek-v4.1-flash-expires-on-0910` xhigh. OpenRouter Muse attempt returned no output.
- New design ask: preserve playback controls and AOSP4.4 KitKat character; improve layout/readability/personal craft. Tactile early2000s game buttons, original vector symbols, skeuomorphism, wireframes, restrained crackling light, fluid animation and extensive theming. Genuine HDR where supported, truthful SDR fallback. Astra medium/high handles3D and implementation.
- Current review rule: **8/10 or above passes**, max4 rounds. Preserve old scores. Detailed custom critics must inspect actual evidence and distinct interaction/theme states. Grok reviews code quality, never visual taste. Muse/DeepSeek own visual design and critique.
- Remaining implementations: external/Bluetooth mic and visualization-only mixing; negotiated HDR; explicit default-source/startup coordinator. Root audio remains deferred.
- Remaining acceptance: settings, appearance/manual, HUD/HOLD/colors/presets/signal-check device regressions; review remaining budgets without rewriting prior reports; five lifecycle cycles,30-minute soak, exact reviewed APK freeze and final installation/restoration.
- Separate open repair: Jcode BashTool null Boolean input bug, plus unresolved large-history transport stalls. Short native HTTPS Astra text works; full tool smoke does not.
- Boundaries: dedicated non-root ASUS NAAIB70036673ZC, MUSIC<=1/30, PC silent; S25 untouched; no system/vendor/boot/vbmeta writes, push, signing, publication or store submission.

Detailed evidence and next step remain in `docs/plans/mobile-expansion/EXECUTION.md`, the canonical expansion plan and HANDOFF.md.

- At23:08 UTC Ben requested persistent completion, periodic TODO checks, and disabling the heartbeat only when all work is genuinely complete. Goal87579191-0c7b-4e71-be25-44fabd0a0040 is active; no arbitrary token budget.

## 2026-09-09 23:26 UTC verification update

R06 source review attempt2 scored8/10 and passes; attempt1 remains build-blocked/unscored. Android918 tests/lint/dualAPK passed. ASUS actual brightness/wake transitions passed for no-source, display pause, Home/focus, PiP/HUD, disable and process recreation. Global slider60/auto1 unchanged. Real SAF import exposed existing cross-runtime checksum quoting divergence, confirmed bidirectionally with native Android and desktop exports. Astra owns a narrow deterministic Android-compatible canonicalizer repair before final acceptance. Checksum validation is not weakened. Both preference XML files are restored exactly; app idle, pin off; overlay permission back to default.

Jcode maintainer_feedback was invoked once with sanitized facts. Tool reports queued, not confirmed delivery/read. Isolated sender cleaned up. Exact-source null-bool candidate passes4 added regressions; full BashTool module tests continue with existing test-environment issues isolated. Live channels unchanged.

-23:36 R06 implemented and bounded-tested; final921 tests,Grok8,ASUS SAF on/off passed after deterministic checksum repair. Restored preferences,pin off,no services. Full baseline acceptance remains active; next tactile U1 console.

-2026-09-10 00:06 Jcode null-bool repair activated and verified: current/shared-server exact immutable d4b6aab0 binary,stable/config preserved.38BashTool tests,Grok8then9,default native text+tool and public fresh run passed with actual notify:null,wake:null. Original huge-history transport stalls remain unproven; old chats untouched. Receipt in Mass storage Jcode prime-null-bool-receipts-20260909/ACTIVATION-RECEIPT.json.

-2026-09-10 00:22 U1 correction2 build929 tests/Grok8 passed. Muse visual history5->7FAIL; DeepSeek first rendered assessment8 bounded. Actual all4themes,font1/1.3/2,Glasspress/focus/HOLD-LIVE captures retained. Astra correction3 active: Light plot-text contrast, large-font status reflow/placement, focus-ring clearance/contrast, centered designator labels. ASUS original prefs restored exactly,font1,no services. U1 not visually accepted yet; full baseline goal remains active.

-2026-09-10 00:49 U1 bounded visualPASS: Muse8/DeepSeek8 after preserved failed rounds,full933tests,Grok8. Finalcandidate installed/readback and actual theme/largefont/focus/narrowwindow checks; ASUS restored exactly. Next R09; whole polished baseline not complete.

- 2026-09-10 ~02:10 GPT-6 usage exhausted. Prime wrote top-level GPT6-REVIEW-LATER.md for Astra later review. Temporary implementation and 3D: Grok 4.6 high. UI design/critique remain Muse/DeepSeek. Code critique remains Grok. R09 still dirty/unreleased after Astra interruption. Continue complete baseline; heartbeat on.


## 2026-09-12 recovery-only planning

Ben requests recovery, a complete original-plus-expansion checklist, and a collaboratively
chosen game plan before further implementation. Ordinary playback-plus-mic mixing on ASUS
is provisional first work, not permission to start during this recovery pass.
Atomic current UI/behavior feedback and the read-only S25 build identity live in
[the recovery feedback](FEEDBACK.md#2026-09-12-recovery-feedback-plan-before-implementation).
Compare the preferred S25 checkpoint with the current ASUS development build and explain
consolidated change trajectories. Do not build, install, reset, publish or resume a soak.

- 2026-09-12: Write a dated Ben + Prime direction-recovery brief and propose clarifying
  questions. [Brief created](2026-09-12-ben-prime-direction-recovery.md). ASUS confirmed
  as recovery target; implementation and later-session prompt remain pending discussion.

- 2026-09-12: Preserve Chapel's raw ASUS field feedback, prioritize direct microphone
  usability and first-class pinch, retain Ben's S25 alignment/Glass/color anchors,
  and clarify recording, zoom, tutorial, three-band scopes, stereo mic and demo needs.
  [Recorded with open scope questions](2026-09-12-ben-prime-direction-recovery.md#chapel-field-feedback-and-bens-answers-2026-09-12).
  Planning only; no implementation or purchase authorized by this entry.

- 2026-09-12: Recording scope clarified as efficient compressed audio only, with a
  Captures entry under Sources near file/folder choices. Modern-phone codec/hardware
  assumptions apply to format research, not a silent app compatibility change.
  [Decisions and remaining questions](2026-09-12-ben-prime-direction-recovery.md#recording-and-gain-clarification-2026-09-12).

- 2026-09-12: Record any selected supported source to a dedicated folder, with simple
  recording control, optional estimated-size disclosure/dismissal memory, custom
  file-size limit and red HUD warning. Default stop on screen-off; nearby opt-in
  allows screen-off recording. Visible HUD/PiP recording continues and both need
  quick-settings controls. Auto-enabled pinch changes preferred framing, persisted
  across restarts. [Full decisions and unresolved edges](2026-09-12-ben-prime-direction-recovery.md#recording-controls-and-remembered-auto-framing-2026-09-12).

- 2026-09-12: Offer mutually exclusive stop/save or new-file actions at the recording
  size limit. Source switch stops/saves by default, with continue-REC opt-in. Screen
  off obeys its checkbox even after HUD/PiP use. Prioritize normal recording controls
  visually; deeper settings remain discoverable. [Clarification](2026-09-12-ben-prime-direction-recovery.md#recording-limits-source-changes-and-control-hierarchy-2026-09-12).

- 2026-09-12: Continue-on-source-switch keeps the same recording file. Default toggle-off still stops and saves; size-limit behavior remains separate. Recorded in the recovery brief.

- 2026-09-12: Ben chooses current-player following, delegates quiet-signal auto-framing
  tuning with slight breathing room, and confirms visual zoom must not affect saved
  audio levels. [Convergence decisions](2026-09-12-ben-prime-direction-recovery.md#convergence-decisions-2026-09-12).

- 2026-09-12: Ben requests consolidation with a clear style and next-session handoff.
  [Bring the instrument home](plans/ben-prime-recovery/README.md) is the forward work
  map; its prompt executes Unit 1 only when later invoked. Conversation preserved,
  engineering defaults labeled, independent document review requested. No app changes.

- 2026-09-13: Ben approves Unit 1 through ASUS verification and his feedback test card, with Git/GitHub work included. Scope remains microphone, pinch and remembered AUTO framing. [Active contract](../spec/UNIT1-AUTO-FRAMING.md). No later unit or public release follows from this approval.

- 2026-09-13: Ben requires stereo-first input defaults across Phosphor development devices. Preserve independent left/right content and label mono limitations honestly. [Decision](../decisions/2026-09-13-stereo-first-input.md). Included in Unit 1 negotiation and verification.
