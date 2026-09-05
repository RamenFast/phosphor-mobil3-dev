# FEEDBACK — Ben's live feedback ledger (phosphor-mobil3)

spec-version: pre-v2-b1-b21
drift: 21

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
| 08-26 | 1 | B15: The grid is too dark and needs real left/right amplitude and dBFS data | open | Contract compiled · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) · automated and live receipt pending |
| 08-26 | 1 | B16: The grid must rotate with the Xy45 goniometer trace | open | Contract compiled · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) · automated and live receipt pending |
| 08-26 | 1 | B18: Double-tap playback needs a dedicated settings toggle | open | Contract compiled · [issue #4](https://github.com/RamenFast/phosphor-mobil3-dev/issues/4) · automated and live receipt pending |
| 08-26 | 1 | B20: Auto-gain pumps after dead space but has not cut the scope off | open | Contract compiled · [issue #5](https://github.com/RamenFast/phosphor-mobil3-dev/issues/5) · automated and live receipt pending |
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
