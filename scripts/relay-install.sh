#!/usr/bin/env bash
# relay-install.sh — build and install phosphor-relay v2 as a user systemd
# service so it survives logout and restarts on failure.
#
#   local  (default)   : build here → ~/.local/bin → enable the user service here
#   remote (--host H)   : build here → scp the x86_64 binary to H → install +
#                         enable the user service on H over ssh
#
# Idempotent: re-running upgrades the binary and unit in place. The only thing it
# ever removes is a *stale v1* left at /tmp/phosphor-relay on a remote target.
# Exit codes follow the workspace CLI standard: 0 ok · 2 missing tool · 3 bad
# args · 4 runtime failure.
set -euo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELAY_DIR="$(cd "$SELF_DIR/../relay" && pwd)"
BIN_NAME="phosphor-relay"
UNIT_NAME="phosphor-relay.service"

HOST=""
PORT=""
PLAYER=""

say()  { printf '\033[36m▸\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$*"; }
die()  { printf '\033[31m✗\033[0m %s\n' "$1" >&2; [ -n "${2:-}" ] && printf '  fix: %s\n' "$2" >&2; exit "${3:-4}"; }

usage() {
  cat <<EOF
usage: relay-install.sh [--host HOST] [--port N] [--player NAME]

  --host HOST     install on a remote tailnet node over ssh (default: this box)
  --port N        serve on port N (default: 45777, from config)
  --player NAME   MPRIS player id (default: spotify, from config)
  -h, --help      this help

Builds the release binary from ../relay, installs it to ~/.local/bin, writes a
user systemd unit, enables it now, and turns on linger so it runs while logged
out. Re-run any time to upgrade.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --host)   HOST="${2:-}"; shift 2 || die "--host needs a value" "relay-install.sh --host <tailscale-host>" 3 ;;
    --port)   PORT="${2:-}"; shift 2 || die "--port needs a value" "relay-install.sh --port 45777" 3 ;;
    --player) PLAYER="${2:-}"; shift 2 || die "--player needs a value" "relay-install.sh --player spotify" 3 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" "run: relay-install.sh --help" 3 ;;
  esac
done

# ExecStart honours the spec baseline (`serve`) plus any explicit overrides.
EXEC="%h/.local/bin/${BIN_NAME} serve"
[ -n "$PORT" ]   && EXEC="$EXEC --port $PORT"
[ -n "$PLAYER" ] && EXEC="$EXEC --player $PLAYER"

# The unit text — generated once, used for both local and remote installs.
unit_text() {
  cat <<EOF
[Unit]
Description=phosphor-relay v2 — desktop→phone audio + scope + player bridge
After=network.target pipewire.service
Wants=pipewire.service

[Service]
Type=simple
ExecStart=${EXEC}
Restart=on-failure
RestartSec=5

[Install]
WantedBy=default.target
EOF
}

command -v cargo >/dev/null 2>&1 || die "cargo not found" "install rust: https://rustup.rs" 2

say "building ${BIN_NAME} (release) from ${RELAY_DIR}"
cargo build --release --manifest-path "${RELAY_DIR}/Cargo.toml" >/dev/null || die "release build failed" "run 'cargo build --release' in ${RELAY_DIR} to see the error" 4
BIN="${RELAY_DIR}/target/release/${BIN_NAME}"
[ -x "$BIN" ] || die "built binary missing at ${BIN}" "check the build output" 4
ok "built $(du -h "$BIN" | cut -f1) binary"

# ── local install ────────────────────────────────────────────────────────────
if [ -z "$HOST" ]; then
  command -v systemctl >/dev/null 2>&1 || die "systemctl not found" "this installer targets systemd user services" 2
  BIN_DIR="${HOME}/.local/bin"
  UNIT_DIR="${HOME}/.config/systemd/user"
  say "installing to ${BIN_DIR}"
  install -Dm755 "$BIN" "${BIN_DIR}/${BIN_NAME}"
  mkdir -p "$UNIT_DIR"
  unit_text > "${UNIT_DIR}/${UNIT_NAME}"
  say "enabling the user service"
  systemctl --user daemon-reload
  systemctl --user enable --now "${UNIT_NAME}"
  loginctl enable-linger "${USER}" >/dev/null 2>&1 || true
  ok "installed + enabled — check: systemctl --user status ${UNIT_NAME}"
  ok "receipt:  ${BIN_DIR}/${BIN_NAME} doctor"
  exit 0
fi

# ── remote install ───────────────────────────────────────────────────────────
command -v ssh >/dev/null 2>&1 || die "ssh not found" "install openssh-client" 2
command -v scp >/dev/null 2>&1 || die "scp not found" "install openssh-client" 2

say "staging binary + unit on ${HOST}"
scp -q "$BIN" "${HOST}:/tmp/${BIN_NAME}.new" || die "scp binary to ${HOST} failed" "check: ssh ${HOST} (is it reachable on the tailnet?)" 4
# fixed literal remote path (no client-side var expansion) — write the unit there
unit_text | ssh "$HOST" 'cat > /tmp/phosphor-relay.service.new' || die "scp unit to ${HOST} failed" "check ssh access to ${HOST}" 4

say "installing on ${HOST} over ssh"
# The remote block is single-quoted (no local expansion); it exports the user
# runtime dir so `systemctl --user` works over a non-login ssh session, clears a
# stale v1, then installs and enables the service.
ssh "$HOST" 'bash -s' <<'REMOTE' || die "remote install on the target failed" "ssh in and inspect: systemctl --user status phosphor-relay.service" 4
set -euo pipefail
export XDG_RUNTIME_DIR="/run/user/$(id -u)"
BIN_NAME="phosphor-relay"
UNIT_NAME="phosphor-relay.service"

# stop + remove a stale v1 relay if one is lingering at /tmp
pkill -f "/tmp/${BIN_NAME}" 2>/dev/null || true
rm -f "/tmp/${BIN_NAME}" 2>/dev/null || true

mkdir -p "${HOME}/.local/bin" "${HOME}/.config/systemd/user"
install -Dm755 "/tmp/${BIN_NAME}.new" "${HOME}/.local/bin/${BIN_NAME}"
mv -f "/tmp/${UNIT_NAME}.new" "${HOME}/.config/systemd/user/${UNIT_NAME}"
rm -f "/tmp/${BIN_NAME}.new" 2>/dev/null || true

systemctl --user daemon-reload
systemctl --user enable --now "${UNIT_NAME}"
loginctl enable-linger "${USER}" >/dev/null 2>&1 || true
echo "  remote: enabled; $("${HOME}/.local/bin/${BIN_NAME}" --version)"
REMOTE

ok "installed + enabled on ${HOST}"
ok "receipt:  ssh ${HOST} ~/.local/bin/${BIN_NAME} doctor"
exit 0
