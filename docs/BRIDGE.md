# The Tailscale remote-scope bridge

**Ben's idea (2026-07-18), greenlit:** the phone can't capture Spotify (Android role-locks
`CAPTURE_AUDIO_OUTPUT`), but a Linux desktop/laptop CAN — via PipeWire — and it's already on
the tailnet. So the desktop becomes a **capture + control proxy**: it streams the audio it's
playing to the phone, the phone scopes AND plays it, and the phone's transport buttons drive
the desktop's player over MPRIS. Pure fortress: Ben's metal, Tailscale, no cloud.

Ben's requirement: **remote scope AND audio pass-through** (hear it on the phone), plus
next / back / play-pause control.

## Topology (all tailnet nodes, verified reachable)

```
thinkcenter (laptop, 100.66.109.56)          s25 (phone, 100.102.2.83)
  Spotify → default sink                        phosphor-mobil3 "remote" source
  phosphor-relay  ── TCP over Tailscale ──►      oboe playback + scope (SampleRing)
  playerctl -p spotify  ◄── transport ──         next / back / play-pause buttons
```

Dev box: interserve-linux (desktop, 100.114.165.77) — builds the relay (laptop has no
cargo), scp's the x86_64 binary to the laptop, and drives the phone over adb.

## Wire protocol (one TCP connection, framed)

Frame = `[1 byte type][4 byte BE u32 length][payload]`.

Server → client:
- `A` (0x41): raw PCM, **s16le interleaved stereo 48000 Hz** (the audio).
- `M` (0x4D): UTF-8 JSON `{title, artist, album, playing}` (metadata, ~1 Hz).

Client → server:
- `T` (0x54): UTF-8 transport command — `next` | `prev` | `playpause`.

Audio chunks ~20 ms (3840 bytes). Bandwidth ≈ 1.5 Mbps — trivial for Tailscale.

## Relay (laptop side — `relay/`, standalone x86_64 Rust binary)

- Captures the default sink monitor with `parec --format=s16le --rate=48000 --channels=2`
  (everything Spotify plays). Monitor auto-detected from `pactl get-default-sink` + `.monitor`.
- Polls `playerctl -p spotify metadata`/`status` at 1 Hz → `M` frames.
- Reads `T` frames → `playerctl -p spotify next|previous|play-pause`.
- Binds TCP (default 45777). Deployed: `cargo build --release` here → scp to laptop → run.

## Phone client (`RemoteSource`)

- Connects to `<laptop-tailscale-ip>:45777`.
- `A` → push PCM into an AudibleRing (oboe plays it, Ben hears it) AND the scope SampleRing
  (the beam draws it) — reuses the exact rings the deck/capture already use.
- `M` → update the MediaSession + console title/artist.
- Transport buttons → `T` frames.
- Surfaced in the SOURCE sheet as "remote · <host>".

## Status

- [x] Feasibility confirmed; laptop reachable, tools present, Spotify live.
- [x] Relay binary (`relay/`, built x86_64, scp'd to laptop `/tmp/phosphor-relay`).
- [x] Phone RemoteSource — connects, plays PCM via oboe, scopes it, metadata, transport.
- [x] End-to-end proven: laptop test tone → 960 segs on the phone; metadata + transport live.
- Field note: **Spotify Connect moves playback to the phone when buds connect** — the laptop
  then outputs silence and the bridge captures silence. Keep Spotify's device = the laptop.

## Planned next (Ben's asks, 2026-07-18)

### 1. Two independent stream toggles (bandwidth control)
Client sends a config frame on connect: `{audio: bool, geometry: bool}`.
- **Music (audio)** on → relay streams PCM (`A`); phone plays it AND scopes locally (full
  fidelity). ~1.5 Mbps.
- **Visualizer (geometry)** on → relay runs `phosphor tap` and forwards each frame's
  `polyline` as `G` frames; phone draws the desktop's exact (decimated) beam without needing
  audio. Lower fidelity, but works with music off for a low-bandwidth visual.
- Render precedence on the phone: geometry stream if on, else local scope from audio.
- `phosphor tap` frame shape (verified): `{event:"frame", segments:N, polyline:[…], peak,
  bbox, centroid, trace_size:[w,h]}` + a `hello` line first + `tick` when quiet. The phone
  parses `polyline` → segments → `GpuRenderer::advance`, bypassing the DSP.

### 2. Source selection (mirror desktop phosphor's picker, non-disruptively)
The phone picks WHICH desktop source to scope: **primary output** (default sink monitor) or a
**specific application** (Spotify, browser, …) — like desktop phosphor, without changing the
PC's own audio routing or its phosphor.
- Relay adds a verb/frame: enumerate sources — `pactl list sinks short` (outputs) +
  `pactl list sink-inputs` (apps: index, `application.name`, the Sink they feed).
- Client picks one; relay captures it:
  - **primary output** → `parec -d <default-sink>.monitor` (current behavior).
  - **an app** → the honest non-disruptive path is PipeWire: `pw-record --target <node-id>`
    of the app's output node, OR capture the monitor of the sink the app feeds (catches all
    apps on that sink — simpler but not solo). True per-app solo = link a capture stream to
    the app's node like desktop phosphor's `mirror.rs`/`targets.rs` do; port that logic into
    the relay. MUST NOT move the app's stream (no vacuum) — read-only tap only.
- Phone UI: the SOURCE sheet's "remote" row opens a sub-list of the desktop's sources
  (fetched from the relay), plus the two stream toggles and a host field.
