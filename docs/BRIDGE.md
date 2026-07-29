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
| `K` 0x4B | JSON `{ts_ms, tx_a, tx_g, dropped_a, rms?, rms_peak?}` every 1 s |

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

`K.rms`/`K.rms_peak` (added 2026-07-29, both **optional**) report the loudness of the
audio written to the wire since the previous `K`, normalised 0.0–1.0, and are present only
while the `audio` stream is on. They exist so a client can tell **a silent source from a
dead link** — both draw nothing, but only one is still receiving frames. Each `K` drains
its own window, so a source that just went quiet reads quiet immediately rather than being
masked by a session-long average. A client that predates the fields simply skips them, and
one that receives them absent must treat that as "cannot tell", never as measured silence.

## Lifecycle & hardening (2026-07-18, the service-bench session — relay 2.1.0)

The codex audit's 13 findings (docs/dev/codex-bridge-audit-2026-07-18.md) are all
addressed; the load-bearing invariants, so nobody "simplifies" them away:

**Phone:** ONE long-lived control thread owns every session sequentially (overlap
impossible by construction); JNI verbs are pure intent-writers (atomics + trip + mailbox,
O(µs), never socket I/O); a dedicated writer thread owns the write half (K self-generated
on its own 2 s cadence — unstarvable; H coalesced via dirty flag — unlosable; any write
error trips the session); Session RAII tears down in a documented order (cancel → FIN →
writer join → ring close+clear → reader → audio → supervisor BEFORE slot clear → drop
stream outside the lock), bounded joins detach-and-count (`leaked_threads` in
remoteStatus must stay 0); the oboe supervisor is token-cancelled with a bounded reopen
ladder (exhaustion trips the session — never silence behind "streaming"); mute is
playback POLICY, reset on connect (BUGLOG #5). Monotonic clocks for all liveness.

**Audio path:** lock-free SPSC ring (rust/src/spsc.rs, first implementation of desktop's
SPSC-RING-DESIGN) — the RT callback does no alloc/lock/syscall/log. Latency constants
(field-tuned with Ben's ears): ~400 ms elastic ring · catch-up only after ~250 ms
SUSTAINED above 350 ms, cutting to 250 ms · audio channel 32×10 ms. Jitter soaks in
silently; runaway lag dies in one cut; `audio_buf_ms/skips/skip_ms/a_drops` live in
remoteStatus + the Nerd HUD bridge line.

**Relay:** every external command runs under `util::run_cancellable` (hard deadline +
25 ms-polled cancel flag flipped by watchdog AND client-disconnect — no child holds the
session hostage); browse + Drive fetch are supersede-tokened JOBS (control loop stays
inside the phone's 3 s stall window; capture keeps playing through a download); EOF
events carry pump ids (stale deaths ignored); CapturePump/FilePump/Geometry/FileSession/
SessionState are Drop-RAII + a RunningGuard (panic unwind cleans the whole graph;
catch_unwind logs only); tx_a counts wire writes; audio queue 32 frames (~320 ms max
hoard); monotonic liveness.

## Deployment state (2026-07-18, evening)

- interserve-linux: **relay 2.1.0 LIVE** (systemd user service).
- thinkcenter: **relay 2.1.0 LIVE** (systemd user service; v1 hotfix era over).
- rclone: installed on interserve-linux with `gdrive:` OAuth done; Drive root live in its
  config. thinkcenter local roots only.

## v1 (historical)

`A`(s16le bursty)/`M`(title,artist,album,playing)/`T`(bare strings) only, no handshake,
no liveness — the burstiness + zombie bugs are BUGLOG #1/#2. Superseded above.
