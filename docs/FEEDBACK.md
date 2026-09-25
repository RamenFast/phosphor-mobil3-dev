# FEEDBACK — Ben's live feedback ledger (phosphor-mobil3)

spec-version: pre-v2-b1-b21
drift: 22

*One row per unique piece of feedback. `×N` = times Ben has raised it (a repeat means
the fix missed or the receipt never reached him — treat repeats as priority signals).
Status: open · shipped · verify (shipped, awaiting Ben's receipt) · retracted.
Maintained per the `mobile-feedback` repo skill; update IN THE SAME PASS as the fix.*

## Pre-v2 B1-B21 raw human report

These lines remain the authority. Only the B labels were added.

> **B1:** Zoom in/out scope needs deadzone by controls, and *1/3 second delay* before finger can move it
> **B2:** "Built in mic" doesn't work, when switching sources.
> **B3:** Need to verify Tailscail is working, and that new Linux setups work right. Last I tried it didn't work.
> **B4:** Report 2026-8-1, local file playback through folders crashes the app and scope shows no light for local music playback specifically. 3:00PM, pointed it at music folder. Local playback through direct file works.
> **B5:** Brightness inconsistencies with the app, sometimes resets when opening setting and closing again, UI glitch. Scope looks faded then bright when interacting with ui
> **B6:** Spotify track name surface in notification tray looks great, but there is no track name/artist metadata when playing local audio or capturing everything. It should display the track name and artist, was previously working
> **B7:** add in app track seeking. Feature used to be there for everything playing. Everything playing doesn't show it working anymore, only local phone music file playback.
> **B8:** play/pause is flipped (UI is lying, opposite symbol) when playing everything/Spotify. Local music playback doesn't have this issue.
> **B9:** When navigating/seeking in a local audio file, the whole app hard froze, had to restart app.
> **B10:** when configuring UI there is no real time visual change for the 4 options below the main color scheme, needs verified working again (immediately working/showing visual difference/feedback)
> **B11:** The display still falls asleep after periods of inactivity sometimes, even when playing music. I think it could be related to brightness issue, thoroughly investigate and resolve.
> **B12:** Picture in picture behavior tweaking. Needs to be easily toggle le surface setting clearly visible/toggleable in quick settings and main settings
> **B13:** "deck" feature needs to be removed from settings/quick settings and replaced with "controls always visible" setting in full settings
> **B14:** The sliders should be wider/easier to adjust, make sure UI is consistent with new styling you apply (and that beam colors still shows in bar surface)
> **B15:** Grid is too dark to be seen, also, amplitude? Include basic *real* (left right) amplitude/DB range information in scope when "grid data" is toggled.
> **B16:** Grid needs to change orientation to goniometer position when selected and turn back when not
> **B17:** My settings need to be saved cross version, and be the app default.
> **B18:** Double tap to play/pause needs to be a dedicated toggle in settings
> **B19:** Add more deadzone to be bottom of the screen so swiping up to get android controls doesn't accidentally adjust scope view, very easy to overshoot and accidentally adjust the zoom level of scope
> **B20:** auto gain works, but it seems inconsistent. Some songs have lots of dead space, others it's perfect. Note, I've never had auto gain cut the scope off
> **B21:** When the app is swiped/closed as a background task, it shouldn't then linger in the background by default. Background lingering should be an option in settings for those that want to see/approve less frequently "share this screen" message prompts

## Ledger

| first | × | feedback (Ben's words distilled) | status | resolution / receipt |
|---|---|---|---|---|
| 07-18 | 3 | Album art not displayed/transferred (remote) | verify | ×1 code (fresh-connect art-id bug) · ×2 receipts showed working · ×3 root cause = thinkcenter relay service never restarted onto 2.2.0 (ops); restarted 21:09 — awaiting Ben's eyes |
| 07-18 | 2 | S25 corners cut off / chrome fights the curved glass | shipped | ×1 RoundedCorner inset root fix · ×2 Ben's design: floating card chrome + scroll-curl (approved: "card edges look good") |
| 07-18 | 3 | Icons: custom settings icons → launcher waveforms → category icons everywhere (deck/light/room/settings rows + sheet headers, all themes) | verify | 8-glyph set + wrapper (4 characters) · launcher hero+supports+foreground band · popout rows + sheet headers now carry glyphs (pass 4) |
| 07-18 | 2 | Scope accuracy: "2-3 circles out of sync", "doesn't look 120fps" (recalibrated: subtler than first stated) | shipped | window-slicing root fix → real polyphase reconstruction; ONE clean circle receipt on device |
| 07-18 | 3 | Swipe-up for settings (ask → "better, but could it be a bit better?") · B19 bottom-edge overshoot still changes scope view | open | v1 threshold door → v2 finger-tracked + flick · v3 bottom-band arbitration · pass 4: top dead-band (56dp) so bar-summons never zoom · ×3 B19 requires any-pointer protection in the existing 88dp bottom band |
| 07-18 | 1 | Audio lags the desktop | verify | adaptive jitter buffer tight/balanced/safe + A/V-sync consumer tap; full-song verdict pending |
| 07-18 | 1 | Output switch on ThinkCentre: UI stale + audio dies | shipped | relay 2.2.0: real output move + honest S echo; live round-trip receipt |
| 07-18 | 1 | Grid should zoom with pinch; gain to 7× | shipped | grid_spacing rides effective gain; 7× everywhere incl. desktop 4.7.2 |
| 07-18 | 1 | Popout: animate + center above play card; can't slide it down (→ fixed); "looks good though~!" | shipped | animated/centered/measured anchor + drag-down close (pass 3) |
| 07-18 | 1 | Quick settings in popout: FPS/nerd/GRID with matching icons | verify | shipped pass 3 (3 new glyphs); Ben eyeball pending |
| 07-18 | 1 | Dedicated SET key on console | retracted | Ben: "nvm, not enough room when media keys show" |
| 07-18 | 3 | Rotation: locks + icons face the viewer (×3: mis-rotation → upside-down/wrong-side + landscape gaps) | verify | v4: quadrant SIGN flipped per Ben's receipt (deviceQ−displayQ); landscape chrome receipted live (width-capped console, band split); settings = two-column landscape anchored to the reach side per rotation; Ben re-verifies directions |
| 07-18 | 1 | Storage: 514MB — keep settings, auto-clean the rest, check leaks | shipped | staged-audio leak (441MB) found; self-cleaning staging; verified live 431MB→347KB |
| 07-18 | 3 | Notifications allowed but capture metadata dark · B6 captured title/artist regressed | open | ×1 two-switch confusion, deep-link added · ×2 access was still ungranted (repro proved it); granted via adb 22:36; VERIFIED: mirror carries the captured app's live track name · ×3 B6 requires a new Spotify and tagged-folder truth receipt |
| 07-18 | 1 | Auto-gain should lock the viewport w/ finger message | shipped | "auto · view locked" ribbon; settings GAIN rule = manual takeover |
| 07-18 | 1 | Lock a particular zoom level | verify | VIEW LOCK chip (SIGNAL section), persists, "view locked" ribbon (pass 4) |
| 07-18 | 2 | Not all top text disappears when set to auto/off | shipped | repro'd on Ben's phone: HUD·auto + BAND·on — two chips, one mental model. Quick-settings toggle is now TOP (drives band+HUD lockstep); receipt toptext-off-receipt.png = pure scope. Fine-grained pair stays in SETTINGS |
| 07-18 | 2 | LOCAL CAPTURE works (Spotify)! — wants track name/art there · B6 asks for restored title/artist | open | ×1 VERIFIED live on SoundCloud: session face mirrors the captured track (still→growing followed a skip) · ×2 B6 regression requires current local and Spotify receipts; artwork is not gating |
| 07-18 | 1 | Nerd toggle + fade "back" | shipped | never missing — HUD on/auto/off chip in PERFORMANCE (auto = fade); also now in quick settings |
| 07-18 | 2 | Save user settings between test cycles · B17 requires cross-version preservation and accepted defaults | open | ×1 snapshot/restore protocol + memory; custom-light persistence gap also closed · ×2 B17 requires same-package update, archive, and clean-install receipts with unknown RGB held |
| 07-18 | 1 | Fullscreen togglable | verify | DISPLAY chip; off-state receipt pending |
| 07-18 | 1 | Full functionality test pass | open | pass-4 sweep started; continue next session (verify list in HANDOFF) |
| 07-18 | 2 | Next/back dead in player + missing from notification (capture source) · B6 captured controls regressed | open | ×1 root cause: no external controller (access ungranted) → mirror honestly advertised nothing; now transport is ALWAYS offered on capture (controller-precise, system-media-key fallback needs no permission) + access granted; VERIFIED: MEDIA_NEXT advanced SoundCloud still→growing, session actions=1018 · ×2 B6 requires current captured-control truth without fabricating unsupported actions |
| 07-18 | 1 | Quick settings has a ton of dead space | shipped | 2×2 icon grid (180dp card), hairline rule, SpaceEvenly toggles — receipted live in landscape (popout-grid-receipt.png) |
| 07-18 | 1 | Grid pref needed a re-toggle to apply between versions | shipped | root CLASS fixed: SetGrid/SetGlow/SetFocus/beam-theme arriving pre-surface were dropped; renderer creation now applies persisted mirrors (grid, glow, focus, beam preset) |
| 07-18 | 1 | Settings portrait-only in landscape — should be a real landscape view per side | shipped | SettingsSheet: two-column landscape (SIGNAL+DISPLAY / PERFORMANCE→ABOUT) in one scroll (curl+dismiss intact); all sheets anchor to the reach side by display rotation |
| 07-18 | — | LIGHT beam-cycle parity (queue item, not feedback) | shipped | slots 1·2·3 (2/3 remember), per-slot picker, gradient ring, LEG 0.1–60s, TIMER/TRACK; sub-1s behind the existing photosensitivity card (TRACK exempt — one fade per song isn't a strobe) |
| 07-19 | 3 | Randomizer buttons w/ user-selectable range for beam/glow (×2 "checkboxes aren't there" · ×3 "can't toggle them off") | verify | ×1 label-tap affordance invisible · ×2 real checkbox added (1.0.1) — but tap only ever ARMED · ×3 true toggle: uncheck disarms, last roll stays (1.0.2); verified live both directions (checked ×19 roll → unchecked, value held); receipts randrange-checkbox-{armed,toggleoff}.png |
| 07-19 | 1 | Ban scope views from RANDOM ⚄ rotation | verify | BAN FACES editor in MODE·AUTOMATIC; ≥2 faces guard; persists; receipt ban-faces-receipt.png (dots+spec struck, count·2) |
| 07-19 | 1 | "Super awesome visualizers — MANIPULATE GEOMETRY" | verify | GEOMETRY FX stage rides every mode: kaleido/spin/tunnel/pulse + AMOUNT; live receipt geomfx-kaleido-live.png (4-fold mandala on Ben's Spotify capture); tunnel breathes ~17s (still shots undersell it — judge live) |
| 07-19 | 1 | Capture consent needs context ("share your screen" = share audio, no data taken, source public) + built-in manual w/ cute ascii + link cards opening the user's browser | verify | consent card rewritten (system wording decoded, no-data pledge, source LinkCard); MANUAL sheet in SETTINGS·ABOUT: ascii tube welcome + 7 sections + CARDS (source/releases/desktop/license → ACTION_VIEW, verified into Chrome and back); receipts manual-{welcome-art,cards,screenshare-section}.png; note: Ben's stored consent skips the in-app card — fresh installs see it before the system dialog |
| 07-19 | 1 | Permissions explainer under "everything playing" (short + manual link) + SOURCE picker gets its own themed icons (rotation-safe) | verify | explainer row (share-screen = AUDIO, nothing leaves) + manual… key routed from SOURCE (returns to SOURCE on close); 4 new hairline glyphs (file page-w/-trace, folder-w/-wave, radiating app window, mic) + Remote masts on host rows — same 24-unit law as every other legend; receipt source-icons-explainer.png; 1.0.4 |
| 07-19 | 1 | Easter egg from the field notes ("little characters, big implications") + OTHER APPS logo strip (Spotify/SoundCloud/Apple Music/VLC/mpv + …) w/ "local music playback" in the title | verify | strip: 5 brand marks in the 24-unit hairline right of OTHER APPS · LOCAL MUSIC PLAYBACK (receipt otherapps-logo-strip.png) · the egg: it's in the manual, the tube knows a number — found is forever, and the CAL sticker learns something (receipt bestiary-found.png, persistence force-stop-verified); 1.0.5 |
| 07-19 | 1 | View lock working too well — can't zoom even after toggling settings | shipped | root: gainLocked = autoGain OR viewLock, so AUTO-GAIN refused gestures regardless of the chip; now only VIEW LOCK refuses — a pinch while auto is armed is a manual takeover (disarms auto, desktop law); 1.0.6 |
| 07-19 | 5 | Rotations messed up when controls locked (settings) — ×4 flat-desk ghost rotation (fixed, Ben: "rotate is the correct way now") · ×5 settings shows portrait not the side view when UI locked vertical | verify | two roots: Android's orientation listener confidently reports quadrants from a nearly-flat phone (desk phone rotated its chrome) → replaced w/ gravity listener + ~20° flatness gate + ±30° cardinal hysteresis; and sheet headers rotated alone while bodies stayed pinned → sheets are one coherent pinned document now; Ben's physical re-verify wanted (device tilt can't be faked over adb); 1.0.6 |
| 07-19 | 1 | App should reopen its last source (capture → re-prompt share screen) | shipped | last_source pref (capture/mic; files+networks stay deliberate); reopens once per process at resume, capture re-raises the system dialog only if in-app consent was ever given; VERIFIED live: install→relaunch→capture prompted→Ben shared→"src · capture" with his track playing |
| 07-19 | 2 | Seek bar color synced to scope color — "and other parts of the ui, if it fits" · B14 requires the beam color to remain on wider controls | open | ×1 every room's palette now carries beamAccent (2 Hz live poll, desktop 82% blend law); value-indicating elements draw it — seek played-portion, DragRule fills, RangeDragRule kept spans — structural accents stay the room's own; visible in settings-beamtint receipt (violet fills matching the violet trace) · ×2 B14 keeps `liveAccent` while widening seek, tuning, range, and volume hit lanes |
| 07-19 | — | Two fresh repo screenshots (source picker w/ selection, settings) — Ben ok'd remote labels visible | shipped | docs/screenshots/{source-picker,settings-sheet}.png + README grid rework; captured live during Ben's own listening session (✓ everything playing + beam-tinted rules) |
| 08-26 | 1 | B1: Scope zoom needs a deadzone beside controls and a 1/3-second settle delay | open | Contract compiled · [issue #4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) · automated and live receipt pending |
| 08-26 | 1 | B2: Built-in microphone can stay dark after switching from capture | open | Contract compiled · [issue #3](https://github.com/RamenFast/phosphor-mobil3-dev/issues/3) · automated and live receipt pending |
| 08-26 | 1 | B3: Tailscale and fresh-Linux relay need regular-app proof for live audio, direct files, and recursive folders | open | Contract compiled · [issue #3](https://github.com/RamenFast/phosphor-mobil3-dev/issues/3) · automated and live receipt pending |
| 08-01 | 1 | B4: Local folder playback can freeze and leave a dark scope while a direct file works | open | Reported at 3:00PM · contract compiled · [issue #1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) · automated and live receipt pending |
| 08-26 | 1 | B5: Scope brightness fades and brightens across UI and settings cycles | open | Contract compiled · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) · automated and live receipt pending |
| 08-26 | 1 | B7: In-app captured-media seek is missing while the platform seek control works | open | Contract compiled · [issue #2](https://github.com/RamenFast/phosphor-mobil3-dev/issues/2) · automated and live receipt pending |
| 08-26 | 1 | B8: Captured-media play/pause is flipped while local playback is correct | open | Contract compiled · [issue #2](https://github.com/RamenFast/phosphor-mobil3-dev/issues/2) · automated and live receipt pending |
| 08-26 | 1 | B9: Seeking or navigating a local file can freeze the app and audio | open | Contract compiled · [issue #1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) · automated and live receipt pending |
| 08-26 | 1 | B10: FEEL, MOTION, CORNERS, and LABELS do not preview a visible change | open | Contract compiled · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) · automated and live receipt pending |
| 08-26 | 1 | B11: The display sometimes sleeps while music is still playing | open | Contract compiled · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) · automated and live receipt pending |
| 08-26 | 1 | B12: PiP auto-entry needs visible quick and full settings controls | open | Contract compiled · [issue #4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) · automated and live receipt pending |
| 08-26 | 1 | B13: Remove DECK after queue and volume move, and add always-visible controls | open | Contract compiled · [issue #4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) · automated and live receipt pending |
| 08-26 | 1 | B15: The grid is too dark and needs real left/right amplitude and dBFS data | open | Raw stereo data and source-boundary host gates passed. Physical coefficient experiment and Ben visibility confirmation remain open · [receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md) · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 08-26 | 1 | B16: The grid must rotate with the Xy45 goniometer trace | open | Shared centered rotation, zero reset and offscreen parity passed. Actual phone transitions remain open · [receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md) · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 08-26 | 1 | B18: Double-tap playback needs a dedicated settings toggle | open | Contract compiled · [issue #4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) · automated and live receipt pending |
| 08-26 | 1 | B20: Auto-gain pumps after dead space but has not cut the scope off | open | Sounding-only gain release and exact new-item identity gates passed. Actual silent-gap experience remains open · [receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md) · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) |
| 08-26 | 1 | B21: Recents removal must stop sources by default; background linger must be optional | open | Contract compiled · [issue #1](https://github.com/RamenFast/phosphor-mobil3-dev/issues/1) · automated and live receipt pending |

## 2026-09-05 device follow-through

Ben confirmed that the local freeze and captured Spotify transport faults are separate reports.
The B9 pinned build survived seven effective paused seeks and eight next/next/previous batches on the S25.
The [B9 receipt](dev/receipts/pre-v2-b1-b21/phase-02-b9-seek-stress.md) remains VERIFY because logcat evidence and final regression are pending.

B6/B7/B8 reproduced with notification access disabled. The approved permission restored title, artist, seek, and matching play/pause behavior without code changes.
The [Spotify receipt](dev/receipts/pre-v2-b1-b21/spotify-permission-recheck-2026-09-05.md) records matching session positions after pause and seek.
The fallback still lies about playback state without permission. Phase 5 remains open, including its reattachment and capability checks.

The service-release crash already assigned to Phase 6 occurred before B9 stress. User tuning and saved relay preferences survived the update and tests.
No card has its final regression receipt yet. Keep `drift: 21`.

## 2026-09-05 execution routing update

Ben replaced the old Sol/GLM/Grok/Muse mapping with Astra high-to-max or Grok 4.6 xhigh-to-max, at supported levels only.
The plan records that override. The project keeps its two-worker ceiling, separate reviews, and root-owned device tests.
Astra-high answered through the configured route. Grok 4.6 availability and max effort remain unverified. No product behavior changed.

## 2026-09-05 B9 logged acceptance

The root recovered Android log evidence without changing system files or privileges.
After rejecting a stale-coordinate attempt, the UI-pinned rerun completed eight paused seeks, a five-drag latest-request burst, and eight queue batches.
All expected final positions and queue items appeared. The process survived, the final trace was lit, and raw log replay matched 77,993 records exactly.
The root restored UI placement and verified unchanged tuning and saved-relay hashes, then removed the temporary device diagnostic.
Phase 2 task 2.5 passes. B9 stays verify until final regression. The separate paused-position, source-label, capture fallback, and service-release issues remain open.

## 2026-09-05 B4 implementation checkpoint

Shared decoder preflight is committed in sibling `2a45b0f`, with 27 passing tests and independent source review.
The mobile checkpoint passed 139 Android tests, lint and Android native compilation. It is not the final frozen candidate.
Independent review is closing newer transport intent, partial source-stop failure and destructive-supersession ownership cases.
The [B4 receipt](dev/receipts/pre-v2-b1-b21/phase-03-b4-folder-tree.md) keeps exact APK installation and direct/recursive phone acceptance pending.
B4 remains open. B2 mic-start success, B6/B7/B8 capture truth and final regressions remain in their planned phases.

### B4 frozen-source gate, 2026-09-05 09:08 UTC

Final gate 3 passed 162 Android, 39 mobile native and 27 shared-audio tests, with no failures or skips.
Lint, Android native compilation, mobile formatting, protected hashes and before/after source identity checks passed.
This snapshot includes persistent source-loss reconciliation and ownership-guarded idle cleanup.
Independent final reviews and exact-APK phone acceptance remain pending. Compilation does not close B4.

### B4 exact debug installation, 2026-09-05 09:20 UTC

Implementation checkpoint `9bf8527` passed independent scoped source review, then built from clean mobile and sibling trees.
The exact debug APK passed packaged boundary checks and pm3 hash/signer readback. Tuning and saved-relay bytes stayed unchanged across installation.
The app launched, but keyguard still blocks real picker, audio and scope checks. Final independent intent review also remains pending.
B4 stays open. The [receipt](dev/receipts/pre-v2-b1-b21/phase-03-b4-folder-tree.md) records the exact source, APK and signer identities without a phone PASS.

### B4 final intent review, 2026-09-05 09:24 UTC

The separate final human-intent review passed for the frozen source. The root verified all 30 reviewed source identities against the installed candidate's unchanged source.
Both independent reviews now pass their limited source contracts. B4 remains open because keyguard blocks the required direct/tree playback, output, scope and source-transition observations.

### B4 live acceptance, 2026-09-05 10:09 UTC

Phase 3 task 3.5 passes on the installed `9bf8527` candidate. B4 is now verify, pending final regression.
Direct and nested controls lit the scope. Invalid entries skipped in both navigation directions. The corrupt tail stopped without automatic retry.
A 132-entry tree reached the same supported MP3 used directly, with matching metadata and position progression. Ben confirmed audible output.
Pending pause, rotated UI seeking, mic/capture handoffs and the narrow remote/local transition passed their observed checks.
The complete normal-workflow log replay matched 435,373 records, with no crash or ANR candidate and unchanged app exit history.

A separate 20,001-file stress fixture caused Android's storage provider to ANR before the app received its tree grant.
The later mic selection superseded the tree without stale local publication, but took about ten seconds to start. This is a recorded limit, not an arbitrary-size PASS.
The root removed the fixture and verified provider recovery. Its separate 79,427-record log also replayed exactly.
Unsafe or unreproduced destructive-loss branches retain production-linked tests, not invented phone observations.

The root restored original runtime preferences and media volume. Tuning and saved hosts stayed byte-identical.
All task-owned device fixtures and diagnostics were removed. The [receipt](dev/receipts/pre-v2-b1-b21/phase-03-b4-folder-tree.md) contains scope, hashes and limitations.
Keep `drift: 21` until the complete matrix and final regressions pass. Phase 4 B2 is next.

### B2 offline source checkpoint, 2026-09-05 10:49 UTC

The candidate keeps activity-owned mic startup and existing B4 reader release.
It requires later request-correlated capture idle, completed source release and successful recording before publishing a live mic face.
Independent source review reproduced an overlapping-STOP snapshot overwrite and rejected the first candidate.
The root corrected publication with one latest-stop token while preserving every request-specific B4 acknowledgement.
Separate corrected-source and human-intent reviews now pass on matching file identities.

The corrected frozen gates passed 15 mic tests and 177 Android tests, with no failures, errors or skips.
Lint passed with zero errors and 15 warnings. Native/shared/relay tests, strict local Rust lint and source/boundary gates passed.
Protected archive hashes and source manifests remained unchanged across root gates.

Ben requested quiet autonomous work at 10:32 UTC. No device or audio action followed that boundary.
Exact installation, five capture-to-mic cycles and narrow B4 transition regression remain pending.
The receipt retains legacy capture-face, release-error retry and post-start reader limits. This is not a phone PASS.
Keep B2 open and `drift: 21` unchanged. The [receipt](dev/receipts/pre-v2-b1-b21/phase-04-b2-mic.md) contains the evidence.

## 2026-09-05: contained mobile roadmap, planning only

Ben delegated mobile roadmap priorities and requested a context that a later executor could use without the conversation.
The [mobile-next plan](plans/mobile-next/README.md) now contains nine numbered phases, 21 tasks, source identities, acceptance checks and rollback.
Three isolated candidates and a separate critic selected evidence-first changes over a broad app rewrite.
The plan preserves Activity-owned mic, existing services, shared Rust, instrument style and the unfinished B1-B21 sequence.

Independent final plan review passed after five command/test-scope corrections.
Root checked 63 source hashes, task coverage, file owners, local links and Bash syntax.
Offline APK guards and 18 synthetic manifest/runner cases passed without a device operation.
The [planning receipt](plans/mobile-next/verification/PLAN-REVIEW.md) preserves failed-review history, exact identities and evidence limits.

No future runtime implementation, phone action, visible GUI, audio change or active-service operation occurred during planning.
B2's exact debug APK remains retained, not installed. Device acceptance still waits for Ben's quiet boundary to change.
New roadmap execution and publication remain unapproved. Keep `spec-version: pre-v2-b1-b21` and `drift: 21` unchanged.

## 2026-09-05 16:38 UTC: B2 exact-artifact phone check

Ben authorized phone acceptance at 16:30 UTC. The retained `47ff7cd` debug APK passed offline package guards and installed hash/signer readback.
All five capture-to-mic cycles passed. Every cycle released capture before one unsilenced mic recorder started, advanced sample frames and changed the beam.
Observed capture-stop to mic-running intervals were 75.337 to 96.787 ms. These are measurements, not a timeout guarantee.
Mic/local and capture/local transitions passed their narrow ownership checks. Rapid mic-then-local selection left local ownership without another mic start.
The complete 300-second log had 69,626 records and replayed byte-for-byte. The scoped failure scan found no candidates.
Root restored tuning, runtime preferences and saved hosts byte-for-byte, stopped task audio and removed only task fixtures and diagnostics.
The exact B2 debug APK remains installed. No production package, system setting, desktop media, source code or sibling change occurred.
The [B2 receipt](dev/receipts/pre-v2-b1-b21/phase-04-b2-mic.md) maps each requirement to measured evidence and keeps fault-path limits explicit.
Phase 4 task 4.4 passes. Final regression and human acceptance remain open. The next implementation phase is Phase 5, not the future roadmap.
Keep `spec-version: pre-v2-b1-b21` and `drift: 21` unchanged.

## 2026-09-05 17:21 UTC: original report audit

Ben reposted the original 21 requests for comparison. This is a coverage check, not a report that every bug recurred.
Each request remains in the raw report above, the execution plan's traceability table and `spec/ACCEPTANCE.md`.
No original request is missing from those records. Coverage does not mean implementation or acceptance.

| Original cards | Current evidence and remaining work |
|---|---|
| B2, built-in mic | Phase 4 passed five exact-APK phone cycles. Final regression remains open. |
| B4, recursive local folders and dark scope | Phase 3 passed direct/tree playback, nested music, invalid entries and a lit trace. Final regression remains open. |
| B9, local seek/navigation freeze | Phase 2 passed logged paused-ring seeking and navigation stress. Final regression remains open. |
| B6/B7/B8, metadata, captured seek and play/pause | Notification access restored the observed Spotify path. Missing-access truth, reattachment, buffering and the full matrix remain Phase 5 work. |
| B21, default stop and optional linger | Open, Phase 6. PiP auto-entry remains a separate setting. |
| B3, Tailscale and fresh Linux | Open, Phase 7. Existing-relay audio frames do not establish fresh-install or whole-folder relay acceptance. |
| B1/B19/B18, control deadzones, Android bottom edge and double tap | Open, Phase 8. Preserve the 333ms rule and dedicated playback toggle. |
| B14/B13/B12/B10, sliders, DECK replacement, PiP and four style previews | Open, Phase 9. Preserve beam-colored sliders and move queue/volume before removing DECK. |
| B5/B11, brightness and unwanted sleep | Open, Phase 10. Fixed-brightness and live-source wake tests remain separate required observations. |
| B15/B16/B20, visible stereo grid data, orientation and auto-gain | Open, Phase 11. Require real left/right dBFS, mode-linked grid rotation and preserved no-cutoff limits. |
| B17, settings and defaults | Open, Phase 12. Test update preservation, archive round-trip and accepted clean-install defaults. Do not invent missing custom RGB values. |

Three cards have phase-specific live PASS evidence. Eighteen remain open, including the three with partial Spotify recovery.
The B4 receipt retains the separate oversized Android-provider ANR and delayed-cancellation limit. Normal-folder acceptance does not erase that result.
All cards retain their final-regression boundaries. Visual acceptance still requires Ben's judgment.
The audit corrected stale B9 ask status and added the latest B2 progress above the execution plan's historical checkpoints.
The future roadmap remains a frozen planning baseline, not authority to skip unfinished original repairs.
No runtime source, phone, service, permission, setting, acceptance checkbox or drift counter changed during this audit.

### Reader entry-point follow-through

Following the roadmap README's actual start-here links exposed a remaining integration defect: its current-repairs page still directed readers to the completed B2 test.
The README and current resume point now report B2's live PASS and direct readers to inherited Phase 5 and the exact receipt.
The old baseline and procedure remain explicitly historical. Their source hashes and artifact identities remain unchanged.
This correction changes documentation navigation only. It does not accept more repairs or authorize roadmap implementation.

## 2026-09-05 17:45 UTC: quiet continuation

Ben requested completion of the original repairs while listening to a podcast, without interruptions.
Phase 5 resumes from mobile `d1877aa` and sibling `2a45b0f`. Existing B2/B4/B9 phase evidence remains valid within its recorded scope.
Root keeps sequential code phases and separate approved reviews. Phone activity, playback changes, visible windows and active-service operations remain deferred.
Offline tests, retained builds and local commits may proceed. No live PASS, human acceptance, publication or future roadmap implementation follows from this boundary.

## 2026-09-05 18:35 UTC: B6/B7/B8 offline truth repair

The existing decoder event now drives local tag publication through the service's sole serial watcher.
Exact open/request identity rejects stale metadata, including same-path seeks and failed replacements.
Captured metadata and controls now use observed session identity, state, duration and supported actions.
Buffering retains the pause action without claiming audible output. Local and remote retain their existing playing semantics.
Unsupported captured play/skip controls are hidden, while the source/access remedy remains available.

Separate source and human-intent reviews passed against the frozen 14-file manifest.
Root independently reran 198 Android tests. Native 41, shared audio 30 and relay 26 tests passed.
CLI, boundary, provenance-fixture, scope/privacy and Rust lint checks passed. Protected archive and all relevant source hashes remained unchanged.
The first root scope check exposed ambiguous brief-owner wording, corrected in `1f94a4d` without weakening the gate.
The next overall command stopped on whitespace in concurrent unrelated sibling desktop work. That work remains untouched.
Clean-sibling provenance and the retained clean candidate stay pending. No failed aggregate run is reported as a full PASS.

The [Phase 5 receipt](dev/receipts/pre-v2-b1-b21/phase-05-b6-b7-b8-media-truth.md) maps each outcome to evidence and later live checks.
B6/B7/B8 stay VERIFY pending actual Android title/artist, seek, glyph and notification acceptance. Drift remains 21.
No phone, playback, permission, visible GUI, active relay or production state changed. Quiet sequential Phase 6 work follows the frozen behavior commit.

## 2026-09-05 18:58 UTC: Phase 5 clean artifact evidence

The root retained a debug APK from clean detached local snapshots of mobile `5215120` and shared source `4dc0f2c`.
Every reviewed input matched before and after. Original concurrent desktop edits remained untouched.
All 55 Android build tasks executed. Tests passed 198/198. Lint, JNI, signer, 11 source checks and 5 artifact checks passed.
The [Phase 5 receipt](dev/receipts/pre-v2-b1-b21/phase-05-b6-b7-b8-media-truth.md) records the exact APK, source and signer hashes.
This closes the retained-artifact blocker, not B6/B7/B8 acceptance. No phone, playback, GUI, active service or publication action occurred.
Phase 6 continues with separate implementation and source review. Final live receipts and drift 21 remain open.

## 2026-09-05 19:36 UTC: B21 offline ownership and shutdown repair

Default task removal now has one stop path to the real local, relay, capture and Activity microphone owners.
One false-default full-settings option preserves only existing service-owned sources. PiP remains separate.
Late task, consent, save and controller callbacks are fenced. Capture cleanup cannot recreate PlaybackService or clear a newer mirror.
Ordinary service destruction preserves separate readers. A recreated playback owner restores surviving capture without another projection prompt.
Native teardown stays serial. Reader timeout remains an error, with explicit stop retry available after owner destruction.
Local, capture and relay face release handlers are immediate and do not issue duplicate native or external transport stops.

Worker and root each executed 225 Android tests. Native 41, shared audio 30 and relay 26 tests passed.
Lint, JNI, CLI/boundary fixtures, scope/privacy and archive checks passed at their recorded component levels.
The root aggregate first failed on a concurrent documentation name collision. Correction `c147ff7` preserved the exclusion through canonical governance without weakening the detector.
Source and separate source-first human-intent reviews passed the same 15-file freeze after concrete ownership and recovery defects were corrected.
The [Phase 6 receipt](dev/receipts/pre-v2-b1-b21/phase-06-b21-lifecycle.md) preserves failed attempts, exact hashes and the later Android matrix.

B21 remains VERIFY. Actual callback delivery, task membership, public Media3 release and microphone-only/no-service teardown are not proven by host tests.
No phone, playback, permission, GUI, active relay or publication action occurred. All original live cards and drift 21 remain open.

## 2026-09-05 21:31 UTC: B3 offline relay folders and Linux setup checks

Relay folder Play now retains the complete recursive queue in the existing FileSession path. Direct files keep their sorted sibling behavior.
One browser action plays the current folder. Directory rows remain browse. Peer/request/revision checks reject stale cached listings and retained callbacks.
Literal backslashes remain valid names. Separate source review found and closed both the remote-name regression and stale-peer folder exposure.

The canonical installer now exposes inert discovery and structured normal/error results. Its confined local/remote fixture checks exact production shell behavior.
False doctor reports remain explicit warnings, not environment-readiness claims. Existing configuration is preserved and an active service is not reported as restarted.
Root observed 14 fixture groups and 7 actual read-only relay configuration/library-list checks, including errors and recovery.
Root's scoped gate passed 241 Android tests, 43 relay tests, 40 selected native tests and 30 shared-audio tests with identical source inventories.
The corrected folder-focused gate passed 17 tests through finite real decoding and production EOF handlers, not physical audio output.

The runtime worker's earlier full native suite created a self-only socket fixture despite its no-sockets boundary. That disclosed violation remains in its report.
Root excludes that exact test without changing it. Developer CLI and release fixtures remain NOT RUN in this lane because their execution paths need separate confinement.
Root gate 1 failed on a test-local name shadow after other components passed. The two-line correction and passing gate 2 do not relabel that failed command.

The [Phase 7 receipt](dev/receipts/pre-v2-b1-b21/phase-07-b3-relay-matrix.md) maps each supported outcome to concrete evidence and the remaining live matrix.
Separate human-intent review and exact clean debug artifact follow-through remain pending. No original live checkbox or drift counter changed.
Actual Tailscale reachability, Linux activation, phone UI, audible/beam output and reconnect acceptance remain open. No live service or device operation was performed.

## 2026-09-05 22:00 UTC: B3 callback authority correction and bounded intent closeout

Independent human-intent review found retained root-selector callbacks bypassed the new request guard, and full source-sheet dismissal did not retire every action.
The existing request now guards root selection and retires before Play, on replacement, dismissal and disposal. Retained and reentrant callbacks become inert.
Root gate 3 passed 246 Android tests, including 21 folder-action cases and all five added regressions. Relay 43, selected native 40 and shared audio 30 also passed.
Seven actual read-only relay CLI checks and fourteen confined installer groups passed again. The complete input inventory stayed unchanged.
The independent correction03 receipt inspected the exact source, XML and logs and closed both bounded findings. Original review and failed attempts remain intact.
Source-string wiring and production-helper tests do not execute Compose. Exact retained debug artifact follow-through remains next.
Live B3, all original final cards and drift 21 remain open. The quiet-lane exclusions, prior socket-test disclosure and NOT RUN fixtures remain unchanged.

## 2026-09-05 23:42 UTC: gesture ownership and live seek publication

Phase8 now uses the existing stage owner for transformed24dp chrome margins, through333ms settling, physical88dp bottom rejection and preserved one-finger console pull. The dedicated double-tap setting defaults true and supplies a literal null handler when disabled. Focused37 and full271 Android tests passed against unchanged source. Independent review found no concrete source defect but kept actual Compose/phone acceptance open.

Authorized phone checks on the exact installed Phase7 APK verified preference preservation, local direct/folder metadata and beam, microphone handoff, and local/mic recents source cleanup. The marked420-second local log replay matched58579 records with no crash/ANR/panic candidates. Complete lifecycle, captured transport, relay/Linux and later UI/render/default matrices remain open.

A real paused seek exposed stale media-session position despite a native reopen at the requested offset. The existing successful latest-request publication now emits actual native position as a consumed Media3 discontinuity. Root full gate272 passed unchanged inputs. This source correction still needs exact installed recovery evidence. No original final card or drift21 count is closed by these partial results. See the Phase7 live checkpoint and Phase8 receipt.

## 2026-09-06 00:05 UTC: exact Phase 8 package retained

The clean committed-source Phase 8 build completed successfully with 272 tests, debug signer verification and 11 source plus 5 artifact boundary checks. Root independently verified all six retained receipt hashes. The [Phase 8 receipt](dev/receipts/pre-v2-b1-b21/phase-08-gestures.md) records the exact source, APK and evidence identities.

The authorized phone's USB route disappeared. Read-only wireless checks identified the same S25 with another media app in the foreground. Root left that playback undisturbed. No Phase 8 installation, fresh preference backup or input occurred. Prior backups remain intact. Paused-seek recovery, live gestures and all original final cards remain open, with drift 21 unchanged.

Sequential Phase 9 controls work continues offline through the confirmed Astra high route. This does not accept any pending phone, relay, render or settings behavior.

## 2026-09-06 01:24 UTC: controls source and independent correction reviews

Phase 9 now shares 44dp slider lanes and live beam tint, moves queue to SOURCE and volume to console, removes only DECK UI, and persists independent controls/PiP options. Effective style defaults and explicit overrides reach the existing chrome and one live sample.

Separate source and source-first intent reviews exposed unreachable short-layout controls, stale relocated volume and an inner/outer height mismatch. Bounded corrections now share existing scroll/refresh owners and report the actual occupied console height. Earlier blocked reviews remain intact.

The final frozen gate passed 308 tests with zero failures, errors or skips. Lint retained 15 existing warnings. Full input inventories matched. Independent correction addenda closed the identified source findings without claiming Compose or Android execution. The [Phase 9 receipt](dev/receipts/pre-v2-b1-b21/phase-09-ui.md) preserves exact hashes, requirement-linked checks and live gaps.

No device, playback, permission, visible GUI or live-service change occurred in this phase. Exact committed packaging is next. Phone layout/touch, system volume, PiP, settings restoration, paused seek and final regression remain open. Drift remains 21.

## 2026-09-06 01:30 UTC: exact Phase 9 debug artifact retained

Reviewed behavior commit `992c08a` now has an exact clean-snapshot debug APK. The isolated build passed 308 tests, lint, engine, two scope gates and 11 source plus 5 artifact boundary checks. All six artifact receipt hashes independently matched. The signer matches the existing debug lineage. The Phase 9 receipt records full source, APK and evidence identities.

No APK was installed and no phone interaction occurred. Phase10 brightness/wake source work may continue offline. Live controls, paused seek, gesture and all final regression gates remain open. Existing backups, unrelated work and drift 21 are unchanged.

## 2026-09-06 01:41 UTC: requested ephemeral cleanup

Ben requested cleanup of ephemeral files and continued implementation, with needed tool installations allowed without additional prompts. Root removed six obsolete Kotlin daemon logs and eight task-owned temporary directories, totaling 423579 bytes. Every removed file matched the expected name and type, and no inspected process descriptor held a target open.

All twelve retained Phase8/9 artifact checksums and the protected archive hashes passed after cleanup. Rollback APKs, preference backups, review and build receipts, source snapshots, and reusable build caches remain intact. The ignored Phase10 cleanup receipt records each removed path, size and hash. No tool installation was needed for this step, and no phone or live-service action occurred.

Phase10 source work and its prepared frozen gate continue. This cleanup does not close any B-card acceptance or change drift 21.

## 2026-09-06 03:48 UTC: B5/B11 source-owned display repair

The complete beam-breath driver is retired without adding a beam-energy field or changing the shared 0.40 scrim. Existing reveal and scroll owners remain. The actual source owners now control wake, including current local drained/error publication, per-session remote media freshness and guarded microphone failure publication after cancelled source requests.

Frozen attempt06 passed 348 Android unit tests and 33 selected native cases. Lint reports 16 warnings and zero errors. All 823 full input rows stayed identical. Separate source review and a genuinely isolated source-first intent assessment found no remaining concrete blocker in their bounded scope. The independent map was saved before retained tests, results and prior reports. Earlier failed preparation and invalid-ordering reviews remain preserved, not relabelled as passes.

The [Phase 10 receipt](dev/receipts/pre-v2-b1-b21/phase-10-display.md) maps actual producers to checks and open Android acceptance. Real PowerManager, main/PiP timeout, AudioRecord/Oboe/Media3 callbacks and settled GPU luminance remain unmeasured. Chosen beam tuning survives recreation, but different-size GPU energy textures are replaced. Exact committed packaging follows next. No phone, display setting, permission or service changed. B5/B11 and drift 21 remain open.

### Phase 10 packaging-boundary correction, 03:51 UTC

A test method name collided with the unchanged privacy scanner. Root renamed only that declaration, proved exact reverse-hash equivalence, and reran the full frozen gate plus source boundary. Attempt07 passed 348 Android and 33 native cases. No production/spec behavior or scanner changed. Earlier independent source/intent approvals remain bounded to identical production, with this root-verified test-name-only correction documented in the Phase 10 receipt.

## 2026-09-06 04:00 UTC: exact Phase 10 debug artifact retained

Behavior commit `7f70e29` now has an exact clean-snapshot debug APK. Root independently verified 360 mobile and 53 pinned shared inputs, identical before/after source receipts and all six artifact checksums. The isolated build passed 348 Android cases, native packaging, lint, engine checks, 2/2 scope checks and 11+5 boundary checks. The debug signer matches the retained lineage. Artifact lint reports nine warnings with zero errors. Its dependency advisories differ from the root gate's 16-warning report, not its code warnings.

The [Phase 10 receipt](dev/receipts/pre-v2-b1-b21/phase-10-display.md) records exact source, APK and evidence identities. No APK was installed and no phone, preference or live-service state changed. Original Phase 11 can continue offline. B5/B11 live acceptance, final regression and drift 21 remain open.

## 2026-09-06 07:52 UTC: Phase11 source and host review closure

The single raw stereo drain now supplies independent L/R amplitude and absolute dBFS under portable GRID DATA. A bounded meter window and exact per-attempt remote lease prevent stale source publication. Shared centered grid rotation preserves zero-angle output. Auto-gain holds silent gaps and resets tracked peak only after a proven current new local item.

Frozen attempt04 passed 362 JVM, 50 selected native and 26 shared renderer tests. Lint reports 16 warnings and zero errors. Separate source and genuinely source-first intent reviews found no remaining concrete blocker in their bounded mechanisms. All 27 reviewed paths and 825 gate inputs matched. Failed runs and invalid-ordering reviews remain preserved.

The [Phase11 receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md) records exact hashes and requirement-linked limits. B15 physical visibility, B16 device mode transitions, B20 actual silent-gap behavior and the local enqueue-versus-callback timing contract remain open. The shared implementation is committed as `aa09b8e14f8b8912b125e3312b4ddeec09404089`. Exact mobile packaging follows. No phone or active-media state changed, and drift remains 21.

### Phase11 identifier-only boundary correction, 07:58 UTC

The unchanged privacy scanner matched two private DSP identifier names before the mobile commit. Root renamed only those locals, proved exact reverse-hash equivalence, and preserved the failure. The full frozen attempt05 passed 362 JVM, 50 selected native, 26 shared renderer and 11 source-boundary checks, with all 827 input rows unchanged and lint 16 warnings/zero errors. This is a root-verified naming correction, not new Android or independent-review acceptance.

## 2026-09-06 08:02 UTC: exact Phase11 debug artifact retained

Behavior commit `80eeb55` and shared `aa09b8e` now have an exact clean-snapshot APK. Root verified all 27 final reviewed blobs against the commits and all 362 mobile plus 459 shared committed inputs before/after build. The isolated build passed 362 JVM cases, native packaging, checkEngine, 2/2 scope checks and 11+5 boundary checks. Six artifact checksums and the retained debug signer matched. Artifact lint has nine warnings and zero errors, with dependency/plugin advisory differences from the root gate.

The [Phase11 receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md) retains the exact APK hash and full provenance. No installation, preference update or device acceptance occurred. B15/B16/B20, the callback-timing contract and drift 21 remain open. Phase12 settings/defaults is next.

## 2026-09-06 09:17 UTC: Phase12 settings source and host closure

Accepted clean defaults and strict portable tuning now follow the existing owners. Independent review caught Application startup seeding HUD OFF and a first relay session leaking its display auto-gain into an absent local preference. Root corrected both owners without replacing valid tuning. A final test-only correction makes ON/OFF checksum envelopes distinguishable from the AUTO fallback across both schemas and stores.

The frozen final gate passed 386 JVM, 50 selected native and 26 shared renderer tests, with 11 lint warnings and zero errors. All 828 input hashes stayed unchanged. Separate source and intent addenda verified the corrected paths and named envelope test. Source-only Application/service assertions remain source-only, not Android execution. The [Phase12 receipt](dev/receipts/pre-v2-b1-b21/phase-12-b17-settings.md) maps requirements, exact evidence and recovery.

B17 remains open. No Phase12 APK has yet been installed, and clean-emulator app defaults, same-package S25 preservation, real import delivery and the separate system-rotation authority gap remain unaccepted. The existing emulator only passed isolated boot/identity preflight. No physical phone or private preference changed. Drift remains 21.

The [Phase11 receipt](dev/receipts/pre-v2-b1-b21/phase-11-render.md#0917-utc-callback-wording-disposition) now appends a transparent wording correction: the specification requires the same sample stream, not execution inside the output callback. The observed enqueue path supports that source relationship. Physical sample-lock and timing remain open, with no normative or historical review text replaced.

## 2026-09-06: console volume removed by user direction

Ben rejected the console volume feature during S25 acceptance. The current experience and acceptance contracts now require its removal, without replacement or reserved row. Android retains volume ownership. This supersedes the older B13/B14 inline-volume requirements.

The repeated B8 report concerns Spotify capture, not the already-correct local-file glyph. It remains open pending real source-state and visible-button agreement. Testing must keep phone MUSIC volume at or below 15 percent and leave PC audio silent.

## 2026-09-06 S25 physical feedback and installed correction

Ben repeated that the grid was unreadable. Three matched old/new captures at fixed low brightness now show minor-line contrast 8 to 43, with background unchanged. Shared 297e88b and mobile 6565585 are installed with verified bytes and signer. No user visibility approval is claimed.

Actual SOURCE queue taps exposed a missing Media3 seek-to-item capability. The narrow correction passed Android tests and the real nested left-track selection. Paused seek position and last-item replay remain follow-ups. Stable-session Spotify checks matched their symbols, but the repeated B8 report remains open. The [phone receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-s25-2026-09-06.md) records the final session-loss observation and all acceptance limits.

419 JVM tests and 25 relevant shared renderer tests passed. Phone state was restored, except retaining the already-enabled grid and the new 2/15 music-volume ceiling. No PC audio, desktop service change or publication occurred. Drift remains 21.

## 2026-09-06 continued phone acceptance: final-item replay

The final-item replay failure was reproduced on installed 6565585 with a 15-second right-only fixture: the duration watcher paused before native drain and retained READY output, so Play resumed the tail and immediately paused again. The duration heuristic now only selects a successor. The final item drains and publishes native ENDED, allowing the existing replay path to restart at zero. The corrected physical APK reached STOPPED at 15000ms, then actual Play showed the pause glyph and advancing playback near the beginning. 419 JVM cases, lint, engine check and assembly passed. Paused seek on this candidate reached 9744ms without playback. This is a narrow repair, not full B9 closure.

On the preceding final candidate, relaunching Spotify restored its external session. Three settled real capture toggles matched both platform sessions and the glyph (PAUSED, PLAYING, PAUSED). Immediate taps while chrome was hiding did not activate transport and are not counted as routing failures. B8 still lacks buffering coverage and reproduction of Ben's reported inversion.

## 2026-09-06 18:49 UTC: continued physical receipt

Installed 63fc7ef retains the four demonstrated corrections. Additional real checks covered right-only dBFS, paused seeking, four style overrides and persistence, manual/automatic PiP toggles, shortened-timeout main/PiP wake, and local recents removal. A provisional PiP-return hypothesis was rejected after the unchanged control build passed explicit tap timing. Its experimental code was removed, not shipped. No B8 inversion fix or comprehensive acceptance is claimed. Original tuning, hosts, timeout and PiP denial were restored and verified, test fixtures removed, and phone left no-source at MUSIC 2/15. See the continuation receipt. Drift stays 21.

## 2026-09-06 19:28 UTC: expanded phone test pass

No new product patch was justified. Actual Spotify next-track buffering propagated to the mirror, stable glyph transitions matched, double-tap on/off worked, and all 42 exported keys round-tripped through Android pickers. Invalid-checksum import left preferences byte-identical. Local/relay/capture linger survived real task removal and stopped after disabling linger; microphone stopped even with linger. Fixed-brightness circle trace samples after modal dismissal and PiP reconfiguration overlapped baseline variation. The exact two-pointer driver was killed before event injection. Existing emulator renderer failures and full release/visual gates remain open. Original tuning and quiet phone state were restored. See the final pass receipt. B8 is still unreproduced, not declared fixed. Drift remains 21.

## 2026-09-06 20:43 UTC: gesture driver recovered

Exit137 was traced to an InputManager initialization exception, not an established external kill.
The corrected InputManagerGlobal fixture passed a no-input probe and four real two-pointer cases with actual saved-gain checks.
Native logs now prove full surface teardown and recreation. Moving-trace stability through that sequence and exact physical333ms timing remain open.
The [receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-gesture-recovery-2026-09-06.md) retains evidence and limits. Original tuning and hosts match exactly. APK unchanged, MUSIC2/15, no source. Drift21.

## 2026-09-06 21:05 UTC: ASUS fast-cycle guard bypass

Testing moved to Ben's ASUS_AI2202, Android14, with the S25 excluded from further device actions.
The exact prior APK rendered through Adreno730 Vulkan at about119FPS from empty app data.
Clean-default export and visible controls matched the accepted defaults. Actual LEG endpoints0.1 and60 seconds worked.
TRACK0.1 then TIMER bypassed the photosensitivity confirmation. Native logs showed `beam cycle: 0.1s per_track=false` without acknowledgment.
The test immediately returned to TRACK with no audio source active.

LIGHT now routes slider and both clock-mode controls through one guard.
An unacknowledged fast TIMER request publishes1 second to UI and native state before displaying the existing warning.
Five executable regressions cover ordering, TRACK exemption, acknowledgment and safe boundaries. Updated source checks require all three control routes.
All424 Android tests, lintDebug and assembleDebug passed. An earlier full run failed only on the old source-string assertion, which was updated rather than removed.
ASUS installation and actual warning/recovery verification are next. No B8 fix, independent review or S25 installation is claimed. Drift remains21.

## 2026-09-06 21:14 UTC: untouched-control export repair

Guard fix 1a0f6b9 passed actual ASUS warning, native one-second timer, KEEP 1 s and dismiss-without-acknowledgment checks.
The real clean-default archive round-trip then exposed a separate omission: the 36-key export did not snapshot untouched linger, view lock, custom count or cycle settings.
After setting custom count 2 and a one-second cycle, importing that archive left both active. Native logs confirmed the stale custom cycle.

Partial imports intentionally preserve omitted keys, including older archives. That contract remains unchanged.
The existing snapshot writer now saves the five effective control values before export, without seeding RGB values.
Source-wiring and actual export/decode regressions were added. All 426 Android tests, lintDebug and assembleDebug passed.
The next gate is a new clean-data ASUS export followed by real mutation/import and native readback. No blanket B17 or release acceptance is claimed.

## 2026-09-06 21:23 UTC: both ASUS repairs delivered

The final installed APK is 6183aa8 with SHA256 `5d30d7e62d47ebdb7e67ef94bd71f7a2455fd524197699e2b3dc1626bc2e5dc3`.
The new clean export contained all five previously missing controls and no invented RGB. Actual UI mutations changed all five, then real import restored all 41 exported values.
Native readback reset custom colors to zero and the cycle to a three-second TIMER. Test files and test-only inactive colors were cleaned up.
ASUS remains installed, muted, no-source and stopped. S25 was not touched after the handoff. See the [ASUS receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-asus-2026-09-06.md) for remaining gates. Drift stays 21.

## 2026-09-06 23:48 UTC: restored USB and remaining physical checks

Ben restored the ASUS cable and asked to keep the phone alive. Authorized plugged-in wake and disabled screensaver settings were applied and read back.
Two full native surface teardown/recreation cycles now have moving-trace measurements: −0.54% and +1.16% mean changes across 18 screenshots.
The test fixture now takes explicit surface dimensions, validates all coordinates before input and runs seven invalid-coordinate regressions.
The actual ASUS free-stage, bottom-band latch, held-margin and adjacent-stage comparisons passed. The 17 host timing tests also passed.
An initial margin trajectory left the protected margin, so it did not establish held-margin rejection. The corrected fixed-height comparison did.
No app-runtime change or installation was needed. Tuning/runtime preferences were restored byte-for-byte, audio stayed muted and owned files were removed.
Exact physical 333ms, B8 and broader acceptance remain open. The [receipt](dev/receipts/pre-v2-b1-b21/phase-15-16-asus-recreation-2026-09-06.md) records limits and restoration. Drift stays 21.

## 2026-09-07 00:03 UTC: eleven-mode grid and asymmetric trace checks

All eleven modes passed actual grid-orientation measurements: only Xy45 was diagonal, with the other ten Cartesian.
Left/right PCM fixtures produced measured axes 0°/90° in XY and −45°/+45° in Xy45.
The real raw-meter rows labeled the active channel 0.100/−20.0dBFS and the silent channel 0.000/−∞.
GRID DATA off removed both rows. GRID off left a clear screenshot region at maximum red intensity1.
An initial verifier incorrectly treated onStop preferences as immediate mode state. Actual live HUD labels supplied the correct evidence.
Both app preference files were restored byte-for-byte. Audio stayed muted, test files were removed and requested wake settings remain enabled.
This narrows the render matrix, not B8, exact physical timing or all release gates. See the [mode receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-mode-grid-2026-09-07.md). Drift stays21.

## 2026-09-07 00:18 UTC: real ASUS Spotify capture

Spotify was logged in and explicitly routed to This phone. Muted real playback, the initial Pause and four further capture button transitions matched source state, mirror state and visible glyph.
Before notification access the console withheld unsupported transport. After confirmed revocation it returned to the generic capture face.
The ASUS secure listener cache lagged authoritative revocation. Both the original cache bytes and authoritative four-component set were restored.
The temporary audio grant and ONE_TIME flag required a normal while-in-use grant, cancelled projection and targeted revoke to restore the original denied flags.
No global permission reset or account change occurred. Phosphor preferences were restored byte-for-byte, projection stopped and Spotify left paused.
B8 still did not reproduce. No speculative patch or overall acceptance is claimed. See the [capture receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-spotify-2026-09-07.md). Drift stays21.

## 2026-09-07 B8 prediction race

The [synchronized ASUS receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-capture-race-2026-09-07.md) supersedes “unreproduced” for the observed transient case.
Media3 prediction caused five premature visible glyph changes before source callbacks. The observed-state correction produced zero premature changes across seven actual routed commands.
Other original variants remain open. No blanket B8 or release PASS is claimed. Keep drift21.

## 2026-09-07 slow-pinch correction

Dense physical input exposed discarded sub-threshold pinch movement. The accumulator now retains it until the existing threshold is crossed.
Eight ASUS runs passed per-card guard checks and produced gain growth. Exact 333ms samples stayed blocked, with no deferred jump after rebase.
The analyzer accounts for renewed console movement and exact native milli-unit telemetry truncation, not a generic tolerance.
438 Android tests, lint, debug assembly and engine checks passed. See the [receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-slow-pinch-2026-09-07.md).
Other card/orientation variants and final closeout remain open. Drift stays21.

## 2026-09-07 Activity sensor retention

Same-process ASUS Activity replacements left one, two, then three active gravity listeners after UI settling.
Destruction now clears ownership and unregisters the listener. Queued callbacks and rotation actions reject retired owners.
440 Android tests and lint passed. Three actual replacements now retain one listener with the same process.
See the [sensor receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-sensor-retirement-2026-09-07.md). Drift stays21.

## 2026-09-07 exact installed continuation checkpoint

The [final receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-final-checkpoint-2026-09-07.md) records actual local gain reset, silence hold and paused-seek publication.
Android lock disabled both dependent controls, held portrait through a five-setting landscape import, and applied landscape after unlock.
Exact implementation743d8bb is installed with readback-verified hash/signature.440 Android tests and lint pass.
Both original preference files and permissions are restored, owned artifacts removed, and the app is stopped at MUSIC1/30.
Remaining capture scenarios, physical poses, broader gesture timing and human/Linux/release gates remain open. Drift stays21.

## 2026-09-07 final-artifact feedback-loop audit

The [requirement matrix](dev/receipts/pre-v2-b1-b21/phase-16-asus-final-artifact-audit-2026-09-07.md) records actual743d8bb workflows, not aggregate test claims.
Ten capture commands produced ten glyph changes with no premature transitions. Listener loss and recovery updated actual transport availability.
SOURCE dismissal sampled332ms blocked and340ms rebased, then slow gain rose1.833 to2.005. Final listener replacements retained one registration.
Local paused seek held10848ms. VIEW LOCK held1.83. Delayed slow 3D pinches changed perspective reversibly without changing gain.
An incorrect uniform-shrink hypothesis failed and is retained as excluded evidence. The camera controls perspective, not gain scaling.
The input fixture now supports a bounded post-tap delay and structured argument errors. Both invalid gaps returned exit2 without input.
Whole-result Android440, native73, relay43 and developer/privacy/release fixture gates passed. Exact phone state is restored. Drift stays21.

## 2026-09-07 atomic traceability follow-through

The [atomic inventory](dev/receipts/pre-v2-b1-b21/phase-16-asus-atomic-traceability-2026-09-07.md) maps 47 explicit requirements and changed outputs.
Thirty named JUnit cases were checked individually against their passing XML. Every named private evidence file exists and was hashed.
The corrected VERBOSE-capable reader observed its marker and zero diagnostics during an actual default-off launch and swipe.
The real fixture on host Android stubs returned the expected input_failed envelope and exit3. This is not a device injection-failure claim.
Nine diagnostic event types were observed live. The tenth, capture_binding, has a source check only.
Both phone preference files were restored and compared again. MUSIC remains1/30. No production change or new release claim. Drift stays21.

## 2026-09-07 post-mapping execution

The complete Android gate reran with all55 tasks executed and440 passing cases. Native73,relay43 and CLI/privacy/release fixtures passed again.
The new actual sheet sequence changed gain1.833 to2.0023825,rebase338ms,without a deferred jump. VIEW LOCK retained1.83.
Three fresh Activity replacements retained one gravity listener. Default-off emitted zero events with a verified VERBOSE-capable reader.
Fresh fixture probe and both invalid gaps passed. Host input-manager failure returned its expected envelope and exit3.
Fresh captured transport is blocked by the actual excluded-device Spotify Connect route, not relabeled as a pass.
Every mapped ID now records whether its post-mapping result is fresh execution,prior-evidence revalidation or an exact blocker. Drift stays21.

## 2026-09-07 fresh local acceptance after continuation

The [new local receipt](dev/receipts/pre-v2-b1-b21/phase-16-asus-local-replay-2026-09-07.md) closes prior-only evidence for the measured gain,seek,camera and rotation-import workflows.
Actual gain settled1.15 for loud and6.00 for quiet. Silence held6.00 through advancing time and EOF. Paused seek held9000ms in two system dumps.
Actual slow3D pinches changed perspective and reversed at fixed gain1.83. Both disabled rotation controls rejected taps.
Five landscape settings imported while portrait remained held. Android unlock produced landscape. Both original preferences and all URI grants were restored exactly.
No production code changed. Spotify remained remote and was not controlled. Drift stays21.


## 2026-09-12 recovery feedback: plan before implementation

Source: Ben's direct messages in Prime session `01a097bf-7afe-766a-8686-5d48ad63cbb1`.
This session is recovery and planning only. These reports are not permission to implement,
reset a tree, replace an installed build, or start autonomous backlog execution.
Earlier receipts remain historical. A reviewer score does not override Ben's lived feedback.

| ID | Feedback | Status / next evidence |
|---|---|---|
| REC01 | Expanded settings sections are comprehensive. Smaller dropdowns are appropriate. Keep that useful structure. | User-approved direction; not blanket behavior acceptance |
| REC02 | Controls inside expanded sections need cleanup and easier interaction. Put frequently changed settings near the top. | Open design requirement; choose ordering together |
| REC03 | Accidental dismissal protection works, but after scrolling Ben cannot pull down to dismiss and must use X. | Reported regression; reproduce before assigning cause |
| REC04 | Check consistency across every screen/drawer that previously supported swipe dismissal. | Open inventory and interaction check |
| REC05 | Add liquid-glass-like visual feedback for pull resistance and the cards' weight/gravity. | Open design direction; no chosen implementation |
| REC06 | Theme settings are confusing and may not work correctly. Debug, test and verify their effects. | Open; related to earlier B10, not proof of the same root cause |
| REC07 | Manual is liked, but lacks a clear Back button/swipe-back flow. System Back closes it instead of navigating within it. | Reported navigation problem; verify current source and device behavior |
| REC08 | Everyday manual should focus on reading/using the scope, basics and fun architecture facts, with less information overload. | New content direction supersedes maximal everyday detail |
| REC09 | Put the deeper manual behind a secret developer setting unlocked by repeated app-build-number taps. | Open design requirement; tap count not specified |
| REC10 | Restore the old visible, selectable colors and interactive visual design, then extend it. Do not replace it with uniform colorless controls. | Open recovery requirement; related to earlier beam-color/UI preservation requests |
| REC11 | Features exist but feel undercooked; UI/UX direction drifted and more bugs may exist. | User assessment; compare complete workflows, not isolated scores |
| REC12 | Use the currently installed S25 version as the preferred comparison reference and explain what changed afterward. | Live APK SHA256 matches retained source 06f84e2; no launch or mutation |

Read-only identity: S25 debug package reports `2.0.0-debug`, code `2000000`.
Installed APK SHA256: `4a370170cda5410caf8abc82d8cd9a997fd0777c718cbd1ba31c2cd3aa9127fa`.
This matches `docs/plans/mobile-expansion/section-02-stereo-trials.md` and source
`06f84e2eb7da46c758e9f8b388c3537e6c67905d`. Current mobile HEAD before this note
is `591e2bb`, 117 descendant commits later. It is a debug checkpoint, not a new release.
Ordinary playback-plus-microphone mixing remains the provisional first implementation
unit only after the recovery discussion. Preserve the S25 reference build.

2026-09-12 clarification: Ben confirms the reported recovery problems concern the latest
ASUS build. He used/tested it live and received direct user feedback today. The S25
remains the preferred design reference. He requests detailed questions before a later
implementation session and will ask for its prompt afterward. Consolidated situation,
feedback and open questions: [Ben + Prime direction recovery](2026-09-12-ben-prime-direction-recovery.md).

## 2026-09-12 Chapel field report follow-up

Ben supplied direct field feedback from today's ASUS use and clarified his frequent
actions and S25 design anchors. The verbatim report and atomic issue map are preserved
in [the dated recovery brief](2026-09-12-ben-prime-direction-recovery.md#chapel-field-feedback-and-bens-answers-2026-09-12).
B5 brightness, B20 auto-gain, B6/B7/B8 transport truth and B11/B21 lifecycle symptom
families are repeated signals, not proof of repeated root causes. Their current
reported behavior remains open; historical receipts do not close these reports.
New recording, tutorial, three-band scope, mic recommendation and demo-material
requests remain planning input. No runtime change or new acceptance is claimed.

2026-09-12 gain clarification: Chapel's trace was simply too small. Auto-gain stays
too zoomed out as sound becomes subtle, despite visible shape/structure. This
refines today's B20-related report, not a separate reproduced bug or diagnosed
root cause. Audio-only recording and Captures navigation are recorded in the
[recovery brief](2026-09-12-ben-prime-direction-recovery.md#recording-and-gain-clarification-2026-09-12).

2026-09-12: Ben confirms auto-gain pinch must adjust remembered preferred framing,
not disable auto-gain. This supersedes the older manual-takeover gesture direction
for this recovery. All-source recording, screen-off choice, size limit and HUD/PiP
controls are captured in the dated brief. They remain design requirements, not
implemented capabilities or authority to begin building.

## 2026-09-13 stereo-first correction

Ben clarified that mono input is not the desired Phosphor development default. Distinct left/right sound supplies the intended scope shape. [Stereo-first input](../decisions/2026-09-13-stereo-first-input.md) owns this requirement and its honest hardware-fallback boundary. The baseline ASUS 48 kHz mono observation remains a limitation, not accepted stereo.

## 2026-09-13 Unit 1 delivered for feedback

Status: **verify**, not user-accepted shipped. Source `9d5d77e` is installed on ASUS. Stereo-first input, fresh microphone diagnostics, quiet AUTO framing, remembered stage zoom and rotation margin have source and device evidence. [Receipt](plans/ben-prime-recovery/UNIT1-RECEIPT.md). Ben tests the three-action card before any next slice. Earlier recovery feedback and inherited gaps remain open.

## 2026-09-13/24 Unit 1 field result · Ben

Ben tested the installed Unit 1 build. Status: **open, failed Ben's lived test**. The earlier `verify` is withdrawn.

Ben's words, lightly trimmed:

> i just, I can't even test this dude...
> 1. The mic works. But it glitches out all the time.
> 2. Zoom is fucked. Yeah, pinch works, but the zoom measurement rates make no sense, and what is with x63.69a in the corner?
> 3. Not bothering
> Settings and UI are unusable, gpt model doesn't know shit about design, and it shows.
> Honestly? I don't know if this is salvagable, grok might have fucked it.
> This was originally a fable project ... this is never coming out on the play store looking/behaving like this...
> Most of your test code is garbage, I would throw it mostly out and replace it with actual UX/UI testing and staying aligned

Repeat signals: auto-gain/zoom (B20, Chapel report) ×2 after this attempt. Settings/UI usability (REC01-REC11) is a repeat.
Prime's miss: source tests, numeric logs and screenshots were treated as acceptance. They did not show whether the instrument feels usable.
The `×63.69·a` label is a raw internal gain readout plus an AUTO marker. It carries no meaning for a user.
No further implementation follows from this entry. Direction and salvage approach need Ben's decision.

## 2026-09-24 Transpose Phase 1 · verify (Prime)

- Mic "glitches all the time": AUTO instant-drop replaced by a gliding controller (clicks ignored, loud settles in ~0.3 s, quiet regrows gently). Status: **verify**.
- `×63.69·a` and fps/segs on stage: moved to the developer view; stage shows one quiet line in words. Status: **verify**.
- Zoom numbers: replaced by a closer/farther rail; AUTO and manual share one size scale; leaving AUTO keeps the size. Status: **verify**.
- Console: S25 one-row key language for every look; ⋯ popout trimmed to 4 destinations + GRID. Status: **verify**.
Test card: `docs/plans/transpose/TEST-CARD-1.md`.

## 2026-09-25 Ben on Phase 1

> okay! Now that's what I'm talking about with zoom ... pinch and one finger zoom *look* good 👍
> It feels calm on the main screen, screen is still a bit laggy, but settings are still a cluster I can't wrap my head around, but yes far far better :)

Asks, handled the same pass (verify): ×multiplier beside the zoom rail and on the SIZE slider; fps and segs back under the STATS HUD setting (gain tag stays hidden). Lag: Vulkan validation turned off on device and engine crates built at opt-level 3 in debug APKs (verify). Open: AUTO PiP must stay a setting Ben can disable (Phase 3). Settings clarity (Phase 3, repeat).
