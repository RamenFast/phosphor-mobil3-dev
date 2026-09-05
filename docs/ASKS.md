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
| 2026-08-26 | B3: Prove regular-app Tailscale relay, fresh-Linux setup, direct files, and whole recursive folders | open · [#3](https://github.com/RamenFast/phosphor-mobil3-dev/issues/3) |
| 2026-08-01 | B4: Make recursive local folder playback survive nested and invalid entries without freezing or going dark | verify · Phase 3 live acceptance passed, final regression remains · [receipt](dev/receipts/pre-v2-b1-b21/phase-03-b4-folder-tree.md) · [#1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) |
| 2026-08-26 | B5: Keep deposited scope brightness stable through settings and chrome cycles | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B6: Restore honest local and captured title, artist, and captured transport controls | open · [#2](https://github.com/RamenFast/phosphor-mobil3-dev/issues/2) |
| 2026-08-26 | B7: Restore in-app captured-media seek only when the active session can seek | open · [#2](https://github.com/RamenFast/phosphor-mobil3-dev/issues/2) |
| 2026-08-26 | B8: Make the captured-media play/pause glyph match the audible state | open · [#2](https://github.com/RamenFast/phosphor-mobil3-dev/issues/2) |
| 2026-08-26 | B9: Keep local seek and navigation from freezing the app and sound | verify · Phase 2 logged live acceptance passed; final regression remains · [receipt](dev/receipts/pre-v2-b1-b21/phase-02-b9-seek-stress.md) · [#1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) |
| 2026-08-26 | B10: Preview FEEL, MOTION, CORNERS, and LABELS changes immediately | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B11: Keep the display awake while a playback or capture source is live | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B12: Add synchronized quick and full controls for PiP auto-entry | open · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B13: Remove DECK after moving queue and volume, then add always-visible controls | open · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B14: Make seek, tuning, range, and volume sliders wider and easier to adjust | open · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B15: Make the grid visible and add real left/right amplitude and absolute dBFS data | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B16: Rotate the grid with the Xy45 goniometer trace and restore it elsewhere | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B17: Preserve settings across versions and seed the accepted clean-install defaults | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B18: Add a dedicated portable toggle for double-tap playback | open · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B19: Protect scope gain and orbit from Android bottom-edge gesture overshoot | open · [#4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) |
| 2026-08-26 | B20: Keep auto-gain stable across silent gaps without changing its never-cutoff limits | open · [#5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 2026-08-26 | B21: Stop sources on recents removal by default and make background linger optional | open · [#1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) |
| 2026-09-05 | Recover the existing handoff, check wireless S25 readiness and disk capacity, then confirm before app changes | completed; latest stop was Phase 2 task 2.5 |
| 2026-09-05 | Resume the pinned B9 device test and preserve settings | Phase 2 PASS with replay-verified logs in [receipt](dev/receipts/pre-v2-b1-b21/phase-02-b9-seek-stress.md); settings restored; final regression remains open |
| 2026-09-05 | Test captured Spotify seek and play/pause separately from the local freeze, with permission prompts approved | reproduced and recovered by notification access; [receipt](dev/receipts/pre-v2-b1-b21/spotify-permission-recheck-2026-09-05.md); Phase 5 remains open |
| 2026-09-05 | Monitor disk and clear only stale or rebuildable artifacts if needed | 19 GiB available during device tests; no cleanup needed |
| 2026-09-05 | Use only approved Astra/Grok workers with explicit supported high-to-max effort and bounded concurrency | global preference saved; project ceiling remains two workers; dated execution override records exact ranges and route limitations |
| 2026-09-05 | Continue autonomous work without interrupting YouTube or music | active quiet-work boundary from 10:32 UTC; B2 offline delivery complete; roadmap authored without device, visible GUI, audio or active-service changes |
| 2026-09-05 | Write a comprehensive self-contained mobile codebase plan, using subfolders as needed and choosing the roadmap priorities | completed in [mobile-next](plans/mobile-next/README.md). Nine numbered phases and 21 tasks include contained context, source inventory, verification and rollback. Independent plan review PASS and root document/guard checks passed. Future implementation and publication remain unapproved |
| 2026-09-05 | Compare the reposted original bugs with the B1-B21 plan and verified progress | audited all 21 original requests against raw report, plan and acceptance matrix; B2/B4/B9 phase live checks passed, final regression open; remaining 18 cards open; [audit](FEEDBACK.md#2026-09-05-1721-utc-original-report-audit) |
