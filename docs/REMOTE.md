# PC relay setup

Phosphor Mobile can act as a remote head for a Linux PC. The PC runs `phosphor-relay`; the phone receives audio or geometry and sends playback commands over Tailscale.

The protocol has no application-layer login or encryption. Tailscale is the identity and encryption boundary. Do not expose relay port 45777 to the public internet.

## Desktop setup

The relay lives under `relay/` and requires a PipeWire or PulseAudio Linux machine.

```bash
scripts/relay-install.sh --host <ssh-host>
ssh <ssh-host> systemctl --user status phosphor-relay
```

Installing a new binary does not restart an already-running service. Restart deliberately after an upgrade:

```bash
ssh <ssh-host> systemctl --user restart phosphor-relay
```

The relay currently listens on `0.0.0.0:45777`. Use the host firewall and Tailscale access controls to prevent non-tailnet access. Binding to an explicit Tailscale address is deferred to the networking polish stage.

Useful checks:

```bash
phosphor-relay doctor
phosphor-relay sources
phosphor-relay schema
```

## Phone setup

1. Install and connect Tailscale on the phone and PC.
2. Open **SOURCE**, then **REMOTE**.
3. Add the PC with one of these host forms:
   - a single-label Tailscale MagicDNS name, such as `studio-pc`
   - a full name ending in `.ts.net`
   - a Tailscale IPv4 address in `100.64.0.0/10`
4. Keep the default port `45777` unless the relay was configured differently.
5. Select the saved host.

Phosphor rejects public DNS names, private-LAN IPv4 addresses, and IPv4 addresses outside the Tailscale range. Legacy `.tailnet` names remain accepted for existing installations.

A fresh installation contains no saved host and makes no relay connection. Hosts are stored only after the user saves them.

## Streams and controls

- **AUDIO** receives PC audio for local playback and visualization.
- **VISUALIZER** receives desktop scope geometry.
- **LATENCY** selects tight, balanced, or safe buffering.
- **NETWORK** controls Android network preference. It does not replace the Tailscale endpoint check.
- Playback controls can play, pause, seek, move between tracks, and select relay sources.

When remote geometry is active, the desktop owns the beam shape. The phone reports the desktop mode and source instead of applying local geometry effects.

## Honest limits

- The scope follows audio after the jitter buffer, not packet arrival.
- Silence and a dead link are separate states when the relay supplies RMS evidence.
- Artwork or metadata can remain stale when a relay service was upgraded but not restarted.
- Long-session recovery, discovery, bind-address hardening, and latency tuning are deferred.
