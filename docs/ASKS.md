# ASKS

| date | ask | status |
|---|---|---|
| 2026-07-18 | Android port: reimagined UI, deep media integration, keep-awake, PiP nice-to-have, S25, no Python | M0–M5 pass 1 shipped |
| 2026-07-18 | FPS options incl. above 120 | shipped (60/90/120/uncapped + beam-rate 120/240/480; >120 display impossible on Android, done honestly) |
| 2026-07-18 | Move toolchain out of home folder | shipped (in-repo `.toolchain/`) |
| 2026-07-18 | GitHub: my call, 1 branch, merge asap | shipped (both repos on RamenFast, master only) |
| 2026-07-18 | Tailscale remote-scope bridge: scope a desktop's audio (Spotify) on the phone + audio pass-through + transport | working MVP shipped; toggles + source-picker queued |
| 2026-07-18 | Bridge: independent toggles to stream visualizer and/or music (bandwidth) | **queued — next session** (design in docs/BRIDGE.md) |
| 2026-07-18 | Bridge: pick desktop source — primary output OR a specific app — like desktop phosphor, non-disruptive (don't touch PC's sound/phosphor) | **queued — next session** (design in docs/BRIDGE.md) |
| 2026-07-18 | **Remote music file-browser: browse the PC's music dir (main drive Music) + Mass storage music over the bridge, pick a file, stream + scope it** | **queued — next session** (see HANDOFF "Remote library") |
| 2026-07-18 | **Revalidate audio output on the phone (worked on speakers earlier) + confirm the remote scope draws with real audio on the source machine** | **queued — next session** (note: AirPods → Spotify Connect auto-moved playback to the phone, silencing the laptop source) |
| 2026-07-18 | **App icon — Android adaptive launcher icon (currently the default Android icon; M0 stripped the missing `@mipmap/ic_launcher` ref)** | **queued — next session** |
| 2026-07-18 | **Re-run the ENTIRE M5 UI with Fable — Ben observed the model may have dropped off Fable ~1/4 into M5; full quality-pass/redo of the Compose chrome against UX-SPEC + ben-ui-design, on fresh context** | **queued — TOP priority next session** (HANDOFF §0) |
| 2026-07-18 | **Native audio residency for the remote source — its own media notification + lock-screen player so the Tailscale audio plays "as if native" (route to earbuds/speaker); MediaSessionService for remote, notification transport drives the laptop** | **queued — next session** (HANDOFF A″; also fixes phone-audio muting) |
| 2026-07-18 | **Full settings port (desktop Settings surface) + a bridge host field** | **queued — next session** (HANDOFF B) |
| 2026-07-18 | **3 themes of VERY DIFFERENT UI STYLE, all mobile-friendly (not just palette swaps)** | **queued — next session, with the §0 UI redo** (HANDOFF B) |
| 2026-07-18 | **Google Drive music: browse + play (incl. skip/back) over the bridge** | **in progress this session** (Act IV — rclone backend on the relay, cache-then-play; needs one rclone OAuth from Ben) |
| 2026-07-18 | **Pinch to zoom in/out = gain on the mobile app** | **in progress this session** (promoted into the UI redo, Act I.13 — pinch=gain/dolly + drag=gain/orbit + readout ribbon) |
