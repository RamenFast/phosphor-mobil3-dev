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
