# FEEDBACK — Ben's live feedback ledger (phosphor-mobil3)

*One row per unique piece of feedback. `×N` = times Ben has raised it (a repeat means
the fix missed or the receipt never reached him — treat repeats as priority signals).
Status: open · shipped · verify (shipped, awaiting Ben's receipt) · retracted.
Maintained per the `mobile-feedback` repo skill; update IN THE SAME PASS as the fix.*

| first | × | feedback (Ben's words distilled) | status | resolution / receipt |
|---|---|---|---|---|
| 07-18 | 3 | Album art not displayed/transferred (remote) | verify | ×1 code (fresh-connect art-id bug) · ×2 receipts showed working · ×3 root cause = thinkcenter relay service never restarted onto 2.2.0 (ops); restarted 21:09 — awaiting Ben's eyes |
| 07-18 | 2 | S25 corners cut off / chrome fights the curved glass | shipped | ×1 RoundedCorner inset root fix · ×2 Ben's design: floating card chrome + scroll-curl (approved: "card edges look good") |
| 07-18 | 3 | Icons: custom settings icons → launcher waveforms → category icons everywhere (deck/light/room/settings rows + sheet headers, all themes) | verify | 8-glyph set + wrapper (4 characters) · launcher hero+supports+foreground band · popout rows + sheet headers now carry glyphs (pass 4) |
| 07-18 | 2 | Scope accuracy: "2-3 circles out of sync", "doesn't look 120fps" (recalibrated: subtler than first stated) | shipped | window-slicing root fix → real polyphase reconstruction; ONE clean circle receipt on device |
| 07-18 | 2 | Swipe-up for settings (ask → "better, but could it be a bit better?") | verify | v1 threshold door → v2 finger-tracked + flick · v3 bottom-band arbitration · pass 4: top dead-band (56dp) so bar-summons never zoom |
| 07-18 | 1 | Audio lags the desktop | verify | adaptive jitter buffer tight/balanced/safe + A/V-sync consumer tap; full-song verdict pending |
| 07-18 | 1 | Output switch on ThinkCentre: UI stale + audio dies | shipped | relay 2.2.0: real output move + honest S echo; live round-trip receipt |
| 07-18 | 1 | Grid should zoom with pinch; gain to 7× | shipped | grid_spacing rides effective gain; 7× everywhere incl. desktop 4.7.2 |
| 07-18 | 1 | Popout: animate + center above play card; can't slide it down (→ fixed); "looks good though~!" | shipped | animated/centered/measured anchor + drag-down close (pass 3) |
| 07-18 | 1 | Quick settings in popout: FPS/nerd/GRID with matching icons | verify | shipped pass 3 (3 new glyphs); Ben eyeball pending |
| 07-18 | 1 | Dedicated SET key on console | retracted | Ben: "nvm, not enough room when media keys show" |
| 07-18 | 2 | Rotation: locks + icons face the viewer (×2: quick-settings mis-rotation, UI-locked position + upright elements, scope-locked+UI-follow missing) | verify | v3 (Opus agent): proper rotate-layout primitive (bbox swap, no clipping — receipt in popout screenshot), full 4-mode matrix incl. chrome-to-gravity for scope-locked+follow, sensor hysteresis ±30°. Physical-rotation receipts = Ben |
| 07-18 | 1 | Storage: 514MB — keep settings, auto-clean the rest, check leaks | shipped | staged-audio leak (441MB) found; self-cleaning staging; verified live 431MB→347KB |
| 07-18 | 2 | Notifications allowed but capture metadata dark | shipped | ×1 two-switch confusion, deep-link added · ×2 access was still ungranted (repro proved it); granted via adb 22:36; VERIFIED: mirror carries the captured app's live track name |
| 07-18 | 1 | Auto-gain should lock the viewport w/ finger message | shipped | "auto · view locked" ribbon; settings GAIN rule = manual takeover |
| 07-18 | 1 | Lock a particular zoom level | verify | VIEW LOCK chip (SIGNAL section), persists, "view locked" ribbon (pass 4) |
| 07-18 | 2 | Not all top text disappears when set to auto/off | shipped | repro'd on Ben's phone: HUD·auto + BAND·on — two chips, one mental model. Quick-settings toggle is now TOP (drives band+HUD lockstep); receipt toptext-off-receipt.png = pure scope. Fine-grained pair stays in SETTINGS |
| 07-18 | 1 | LOCAL CAPTURE works (Spotify)! — wants track name/art there | shipped | VERIFIED live on SoundCloud: session face mirrors the captured track (still→growing followed a skip) |
| 07-18 | 1 | Nerd toggle + fade "back" | shipped | never missing — HUD on/auto/off chip in PERFORMANCE (auto = fade); also now in quick settings |
| 07-18 | 1 | Save user settings between test cycles | shipped | snapshot/restore protocol + memory; custom-light persistence gap also closed |
| 07-18 | 1 | Fullscreen togglable | verify | DISPLAY chip; off-state receipt pending |
| 07-18 | 1 | Full functionality test pass | open | pass-4 sweep started; continue next session (verify list in HANDOFF) |
| 07-18 | 1 | Next/back dead in player + missing from notification (capture source) | shipped | root cause: no external controller (access ungranted) → mirror honestly advertised nothing; now transport is ALWAYS offered on capture (controller-precise, system-media-key fallback needs no permission) + access granted; VERIFIED: MEDIA_NEXT advanced SoundCloud still→growing, session actions=1018 |
