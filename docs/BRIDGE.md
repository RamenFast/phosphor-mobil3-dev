# The Tailscale bridge — protocol v2 reference

**v2 (2026-07-18, Fable session).** The desktop/laptop relay captures audio (whole output
OR one app, read-only), streams it to the phone with metadata/art/position, forwards
desktop-scope geometry, serves the machine's music library (local dirs + rclone/Drive),
and takes transport — all over one TCP connection on the tailnet. The phone plays it,
scopes it, and fronts it as a native media app (see Act III residency).

Topology: `thinkcenter` laptop 100.66.109.56 · `interserve-linux` PC 100.114.165.77
(both run `phosphor-relay` as a systemd user service, port 45777) · s25 phone
100.102.2.83. Source of truth for the relay: `relay/` (10 modules, serde-only).
Install/upgrade: `scripts/relay-install.sh [--host <h>]`. Ops receipts:
`phosphor-relay doctor|sources|probe --host <h> --rms|schema`.

## Framing

`[1-byte type][4-byte BE u32 payload length][payload]` — one TCP conn. Caps: client→server
64 KiB (violation → `E` + close), server→client 8 MiB. **Unknown types are read-and-
skipped by both sides** (forward compatibility). Connect: relay sends `W` immediately;
client sends `H` immediately (no round-trip stall); `K`+`M` flow after `H`, `A`/`G` per
toggles. v1 detection: first byte `A`/`M` instead of `W` → the peer is v1.

## Server → client

| tag | payload |
|---|---|
| `W` 0x57 | JSON `{proto:2, tool, version, host, caps:{audio,geometry,per_app,library,art}, selected, libraries:[{id,label,path}]}` |
| `A` 0x41 | raw s16le stereo 48 kHz PCM, **fixed 1920 B = 10 ms, real-time ~100/s** (short final chunk allowed) — capture AND file playback |
| `G` 0x47 | `phosphor tap` frame JSON verbatim (`polyline` ≤64 pts, `trace_size`, `peak`…), ≤60 fps latest-wins |
| `M` 0x4D | JSON `{title,artist,album,playing, position_ms, duration_ms\|null, can_seek, source:"player"\|"file", art_id\|null, path\|null}` — on change + 1 Hz while playing |
| `S` 0x53 | JSON `{sources:[{id,kind:"app"\|"monitor",label,available}], selected}` — reply to `Q`, pushed on vanish-fallback |
| `L` 0x4C | JSON `{root, path, dirs:[], files:[{name,size}]}` |
| `R` 0x52 | `[u16 BE header_len][{id,mime} JSON][raw image bytes]` |
| `E` 0x45 | JSON `{error, fix, context}` — every error carries `fix` |
| `K` 0x4B | JSON `{ts_ms, tx_a, tx_g, dropped_a}` every 1 s |

## Client → server

| tag | payload |
|---|---|
| `H` 0x48 | JSON `{proto:2, client, audio:bool, geometry:bool, geometry_fps}` — resendable; relay diffs and starts/stops pumps |
| `T` 0x54 | JSON `{cmd:"playpause"\|"play"\|"pause"\|"next"\|"prev"\|"seek", ms?}` — bare v1 strings also accepted |
| `Q` 0x51 | empty → `S` |
| `C` 0x43 | JSON `{id}` — switch capture target (kills pump, spawns new; exits file mode) |
| `B` 0x42 | JSON `{root, path}` (relative, jailed) → `L` |
| `P` 0x50 | JSON `{root, path}` play file · `{action:"stop"}` back to capture |
| `R` 0x52 | JSON `{id}` → `R` reply (ask when `M.art_id` changes) |
| `K` 0x4B | JSON `{ts_ms}` — **client MUST ping every 2 s; 8 s of silence tears the session down** (kills all relay children — the zombie law) |

Notes: `art_id` = first 16 hex of sha256(artUrl). File `position_ms` = consumed bytes/192
+ seek base; pause = stop-reading backpressure (sample-exact resume); seek preserves
pause state; EOF auto-advances through the sorted directory. Source ids mirror desktop
targets.rs: `device:<node.name>.monitor`, `app:<application.name>` (+`+` dedup). The
`audio` toggle governs live capture; `P` file playback streams regardless. Bandwidth:
A ≈ 1.54 Mb/s · G ≈ 0.5–1 Mb/s · M+K ≈ 100 B/s.

## Deployment state (2026-07-18)

- interserve-linux: **v2 systemd user service LIVE** (probe: 97 A/s, all caps true).
- thinkcenter: **v1 + PULSE_LATENCY_MSEC=20 hotfix** — stays until the phone speaks v2
  (clean-break discipline), then `scripts/relay-install.sh --host thinkcenter`.
- rclone: not yet installed anywhere — Drive roots activate after `rclone config` (Ben's
  one OAuth) + a `{"id":"drive0","label":"google drive","rclone":"gdrive:"}` config root.

## v1 (historical)

`A`(s16le bursty)/`M`(title,artist,album,playing)/`T`(bare strings) only, no handshake,
no liveness — the burstiness + zombie bugs are BUGLOG #1/#2. Superseded above.
