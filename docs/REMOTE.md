# PC relay setup

Phosphor Mobile can act as a remote head for a Linux PC. The PC runs `phosphor-relay`; the phone receives audio or geometry and sends playback commands over Tailscale.

The protocol has no application-layer login or encryption. Tailscale is the identity and encryption boundary. Do not expose relay port 45777 to the public internet.

## Desktop setup

The relay lives under `relay/` and requires a PipeWire or PulseAudio Linux machine.

```bash
scripts/relay-install.sh schema
scripts/relay-install.sh --help
scripts/relay-install.sh --json
scripts/relay-install.sh --host <ssh-host>
ssh <ssh-host> systemctl --user status phosphor-relay
```

The default invocation installs locally. The optional flat `install` command does the same work.
`--host` selects an SSH alias, hostname, or IPv4 address, optionally `user@host`.
`--port` accepts decimal ports from 1 through 65535. `--player` accepts an MPRIS identifier.
Neither option rewrites the existing config file.

The installer builds with `cargo build --locked --release` into the checkout's explicit relay target directory.
It installs `~/.local/bin/phosphor-relay` and `~/.config/systemd/user/phosphor-relay.service`.
The unit uses `ExecStart=%h/.local/bin/phosphor-relay serve`, followed by explicit port and player overrides.
Remote installation retains its fixed `/tmp/phosphor-relay*` staging paths and stale-v1 cleanup.

Before `systemctl --user enable --now`, the installer runs the installed binary's `schema`, `config`, and `doctor` commands.
The config check must succeed. A failed doctor invocation or invalid doctor report stops activation.
Doctor's `all_ok:false` remains a report, not a new mandatory-all-tools installation gate.
The installer preserves that value as `doctor_all_ok:false`, prints the checks, and warns on stderr.
Optional `rclone` or desktop `phosphor` absence does not newly prevent installation.
An unknown busy-port owner is not an expected or successful check.

Doctor currently checks the config port and ignores `--port` overrides.
An installer port override therefore does not prove that port is free.
Doctor tests directory existence, not readable library content or remote connectivity.
The default library is `~/Music` only when that directory already exists.
Without an existing Music directory or configured roots, the default library list is empty.
The installer neither creates Music nor changes library configuration.

Linger remains best effort. Its outcome is reported as `linger:true` or `linger:false`.
If linger fails, the service may stop after logout.

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

### Installer agent contract

`schema` and `--schema` always return one enveloped JSON object with strict result schemas.
They and `help`, `-h`, and `--help` exit before checkout lookup or external commands.
Other commands use human output on a terminal and JSON when piped. `--json` forces JSON.
Progress and child output go to stderr, never into the result object stream.

Every JSON result includes `status`, `tool`, `version`, and an ISO-8601 `ts` with UTC offset.
Successful installation also reports `command`, `host`, `port`, `player`, `doctor_all_ok`, `linger`, and `restarted:false`.
An empty host means local. Null port or player means no explicit serve override.
Errors include `error` and `fix`. Exit codes are 0 completed, 2 unavailable prerequisite, 3 arguments, and 4 runtime failure.
Completion means installation operations completed, not that every doctor check or live connection passed.

### Offline installer fixture

`scripts/test-relay-install.sh` copies the production installer unchanged into a disposable mock repository.
The canonical no-argument command runs the fixture. Optional `--approved-source SHA256` asserts its reviewed source pin.
The source pin and remote-heredoc pin reject unreviewed script changes before execution.
Both installation paths use private HOME, USER, TMPDIR, and an allowlisted mock PATH under `env -i`.
SSH, SCP, process and service operations are strict mocks with no real-tool fallback.
Only exact legacy `/tmp` names map to fixture files. No host `/tmp` path is written.

The fixture covers schema and arguments, JSON escaping, installed bytes and unit content, config preservation,
doctor ordering and false reports, unavailable tools, runtime failures, and retries.
Mock config and doctor outputs prove installer behavior only. They do not verify the real relay's dependency or library checks.
This is isolated host-fixture coverage, not live fresh-user or Tailscale acceptance. See the [Phase 7 receipt](dev/receipts/pre-v2-b1-b21/phase-07-b3-relay-matrix.md) for executed checks and remaining limits.

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
