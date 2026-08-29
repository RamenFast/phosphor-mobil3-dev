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
