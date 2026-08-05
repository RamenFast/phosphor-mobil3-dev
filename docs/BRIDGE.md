# Tailscale bridge protocol v2

The PC relay captures desktop audio or plays a selected local file, streams optional scope geometry, sends metadata and artwork, and accepts playback controls. The phone connects over one TCP session on the user's tailnet.

Default port: `45777`.

Security boundary: protocol v2 is not independently authenticated or encrypted. The mobile host store accepts only Tailscale-style endpoints. The relay host must use Tailscale access controls or a firewall and must not expose the port to the public internet.

## Framing

Each frame is:

```text
[1-byte type][4-byte big-endian payload length][payload]
```

Client-to-server payload cap: 64 KiB. Server-to-client payload cap: 8 MiB. Unknown frame types are read and skipped for forward compatibility.

On connect, the relay sends `W` and the client sends `H`. A first server byte of `A` or `M` indicates the historical v1 protocol.

## Server to client

| tag | payload |
|---|---|
| `W` | JSON welcome with protocol, relay version, capabilities, selected source, and library roots |
| `A` | s16le stereo 48 kHz PCM, normally 1920 bytes per 10 ms frame |
| `G` | Desktop scope geometry JSON, latest frame wins |
| `M` | Title, artist, album, playback state, position, duration, seek support, source, artwork ID, and path |
| `S` | Available capture sources and selected source |
| `L` | Jailed library directory listing |
| `R` | Artwork header and bytes |
| `E` | JSON error with a required `fix` field |
| `K` | One-second counters and optional RMS evidence |

## Client to server

| tag | payload |
|---|---|
| `H` | Requested audio and geometry streams plus geometry rate |
| `T` | Play, pause, play/pause, next, previous, or seek command |
| `Q` | Request source list |
| `C` | Select capture target |
| `B` | Browse a jailed library path |
| `P` | Play a library file or stop file mode |
| `R` | Request artwork by ID |
| `K` | Client heartbeat; send every two seconds |

Eight seconds without a client heartbeat terminates the session and its relay children.

## Audio and liveness

- Audio bandwidth is about 1.54 Mb/s.
- Geometry is normally below 1 Mb/s.
- The phone's lock-free ring feeds the real-time callback without allocation, locks, logging, or syscalls.
- Tight, balanced, and safe modes control jitter-buffer targets.
- A sustained runaway buffer performs one bounded catch-up cut rather than allowing latency to grow without limit.
- Audio status is measured at the consumer so displayed scope state follows what the user hears.
- Optional `K.rms` and `K.rms_peak` values distinguish a live silent source from a dead link.

## Lifecycle invariants

- One phone control thread owns one session at a time.
- One writer owns the TCP write half.
- JNI calls publish intent and do not perform socket I/O.
- Any writer, reader, audio, or supervisor failure trips the session.
- Shutdown cancels work, closes queues, joins bounded workers, clears audio, and then releases the session slot.
- Relay child processes run with cancellation and deadlines.
- Browse and remote-file jobs are supersedable so they cannot block the control loop.
- Stale pump completion events cannot replace the current source state.

The protocol implementation is in `relay/src/`, `rust/src/remote.rs`, and `rust/src/bridge_core.rs`. Embedded Rust tests are the executable reference.
