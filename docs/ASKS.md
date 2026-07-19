# ASKS

| date | ask | status |
|---|---|---|
| 2026-07-18 | Android port: reimagined UI, deep media integration, keep-awake, PiP nice-to-have, S25, no Python | M0–M5 pass 1 shipped |
| 2026-07-18 | FPS options incl. above 120 | shipped (60/90/120/uncapped + beam-rate 120/240/480; >120 display impossible on Android, done honestly) |
| 2026-07-18 | Move toolchain out of home folder | shipped (in-repo `.toolchain/`) |
| 2026-07-18 | GitHub: my call, 1 branch, merge asap | shipped (both repos on RamenFast, master only) |
| 2026-07-18 | Tailscale remote-scope bridge: scope a desktop's audio (Spotify) on the phone + audio pass-through + transport | working MVP shipped; toggles + source-picker queued |
| 2026-07-18 | Bridge: independent toggles to stream visualizer and/or music (bandwidth) | shipped (v2 `H` hello toggles, SOURCE▸REMOTE flow) |
| 2026-07-18 | Bridge: pick desktop source — primary output OR a specific app — like desktop phosphor, non-disruptive (don't touch PC's sound/phosphor) | shipped (pw-record TARGET_OBJECT per-app picker) |
| 2026-07-18 | **Remote music file-browser: browse the PC's music dir (main drive Music) + Mass storage music over the bridge, pick a file, stream + scope it** | shipped (library browser, both machines' roots) |
| 2026-07-18 | **Revalidate audio output on the phone + confirm the remote scope draws with real audio on the source machine** | shipped (v2 10 ms framing killed the burstiness; residency killed background-mute) |
| 2026-07-18 | **App icon — Android adaptive launcher icon** | shipped (adaptive multi-figure icon) |
| 2026-07-18 | **Re-run the ENTIRE M5 UI with Fable — full quality-pass/redo of the Compose chrome against UX-SPEC + ben-ui-design, on fresh context** | shipped (the Fable redo, Acts 0–V) |
| 2026-07-18 | **Native audio residency for the remote source — its own media notification + lock-screen player, notification transport drives the laptop** | shipped (one MediaSession, RemotePlayer, lock-screen card) |
| 2026-07-18 | **Full settings port (desktop Settings surface) + a bridge host field** | mostly shipped (grouped settings live; REMOTE HOSTS add/edit UI still queued — this session's stretch) |
| 2026-07-18 | **3 themes of VERY DIFFERENT UI STYLE, all mobile-friendly (not just palette swaps)** | 2 of 3 shipped (Blossom Dark carved + AMOLED Void engraved); **CRT Amber service bench lands this session** |
| 2026-07-18 | **Google Drive music: browse + play (incl. skip/back) over the bridge** | shipped (rclone backend, cache-then-play, OAuth done) |
| 2026-07-18 | **Pinch to zoom in/out = gain on the mobile app** | shipped (pinch=gain/dolly, drag=gain/orbit, ×ribbon) |
| 2026-07-18 | **Remote-render control: while streaming VISUALIZER, phone settings drive the mainline desktop phosphor; patch + redeploy desktop on the laptop if needed (authorized)** | **in progress this session** (Act V — relay speaks the ctl socket; `ctl gain` 4.7.1 patch) |
| 2026-07-18 | **Audio still glitchy / lagging behind sometimes — improve the transport through Tailscale** | **in progress this session** (Acts II–III: lock-free RT ring + drop-oldest queues + catch-up; audit findings 10+12) |
| 2026-07-18 | **UI more dynamic/responsive + customizable UX/UI elements** | **in progress this session** (Act VI responsiveness pass; Act VIII custom style editor) |
| 2026-07-18 | **macOS liquid-glass / iPhone iOS-6 style blended theme** | **in progress this session** (Act VIII — the Liquid Glass room, 4th soul) |
| 2026-07-18 | **Window fades: hitting ✕ should be more expressive of what it's doing** | **in progress this session** (Act VI — expressive dismiss, motion shows where the sheet goes) |
| 2026-07-18 | **UX honesty: desktop autogain + streaming shows a stale local multiplier — show "autogain on" truth instead** | **in progress this session** (Act V — `auto · pc` band + live effective gain from the desktop probe) |
| 2026-07-18 | **Fullscreen by default + toggle to hide the status info (not by default) OR auto-hide like the player buttons** | **in progress this session** (Act VI — immersive default + BAND on/auto/off) |
| 2026-07-18 | Keep the narration/meme rig alive + turn the creative dial up (session conduct) | **in progress this session** (rig live; CRT Amber POST readout, CAL stamp, bridge-health HUD, self-portrait tiles) |
