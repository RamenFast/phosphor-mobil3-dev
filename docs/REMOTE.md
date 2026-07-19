# REMOTE — the phone as a head for desktop phosphor

The phone can visualize a desktop machine's audio live: the desktop runs
`phosphor-relay`, the phone connects over your own network (a VPN/overlay like
Tailscale or WireGuard works great — the relay binds loopback-adjacent and is meant
to stay inside your walls, never the open internet).

## Desktop side

The relay lives in this repo (`relay/`) and installs with one script on any
PipeWire/PulseAudio Linux box that already runs desktop
[phosphor](https://github.com/RamenFast/phosphor):

```
scripts/relay-install.sh <host>      # builds + installs ~/.local/bin/phosphor-relay + systemd user unit
ssh <host> systemctl --user status phosphor-relay
```

Law worth knowing: **installing a new relay binary does not restart the running
service** — `systemctl --user restart phosphor-relay` is a separate, deliberate step.

The relay serves protocol v2 on port `45777`: audio (Opus), scope geometry, track
metadata + album art, transport control, and source/output switching, all over one
connection.

## Phone side

Seed your hosts at build time in `local.properties`
(`phosphor.remoteHosts=label:host:port,label:host:port`), then:

SOURCE → REMOTE → pick a host. Toggles for AUDIO / VISUALIZER streams, a LATENCY
mode (tight ~80 ms · balanced ~150 ms · safe — underruns widen the jitter buffer,
clean minutes shrink it), and a NETWORK selector (auto / Wi-Fi / mobile).

While VISUALIZER is on, the desktop owns the beam: the status band shows the
desktop's truth (`swirl · auto · pc`) and the phone's geometry FX stage leaves the
remote picture untouched.

## Honesty notes

- The scope draws what the ear hears: A/V sync is tapped at the audio consumer,
  after the jitter buffer, not at packet receive.
- The phone's SOURCE picker can switch the desktop's audio output (e.g. HDMI ↔
  analog); the relay moves the sink and echoes the real result back.
