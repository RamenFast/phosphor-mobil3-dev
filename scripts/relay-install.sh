#!/usr/bin/env bash
# Build and install the relay as a local or remote systemd user service.
set -Eeuo pipefail

TOOL=relay-install
VERSION=2.2.0
HOST='' PORT='' PLAYER='' VERB=install
JSON=0
DOCTOR_ALL_OK=false
LINGER=false
USAGE='relay-install.sh [install|schema|help] [--host HOST] [--port N] [--player NAME] [--json]'
# Detect --json even when an earlier argument is invalid.
for arg in "$@"; do [[ $arg != --json ]] || JSON=1; done
want_json() { [[ $JSON == 1 || ! -t 1 ]]; }
# shellcheck disable=SC1003
json_string() {
  local LC_ALL=C value=$1 char code i
  printf '"'
  for ((i=0; i<${#value}; i++)); do
    char=${value:i:1}
    case $char in
      '"') printf '\\"' ;;
      \\) printf '\\\\' ;;
      *) printf -v code '%d' "'$char"
         if ((code < 32)); then printf '\\u%04x' "$code"; else printf '%s' "$char"; fi ;;
    esac
  done
  printf '"'
}
# Bash's clock keeps help/schema independent of external commands and the repo.
envelope() {
  local ts
  printf -v ts '%(%Y-%m-%dT%H:%M:%S%z)T' -1
  ts=${ts:0:22}:${ts:22:2}
  printf '{"status":"%s","tool":"%s","version":"%s","ts":"%s"' "$1" "$TOOL" "$VERSION" "$ts"
}
fail() {
  trap - ERR
  if want_json; then
    envelope error
    printf ',"error":'; json_string "$2"
    printf ',"fix":'; json_string "$3"
    printf '}\n'
  else
    printf 'error: %s\nfix: %s\n' "$2" "$3" >&2
  fi
  exit "$1"
}
trap 'fail 4 "Installer operation failed at line $LINENO" "Inspect stderr, repair the failed operation, then rerun. Existing config is unchanged."' ERR
say() { printf '%s\n' "$*" >&2; }
need() { command -v "$1" >/dev/null 2>&1 || fail 2 "$1 not found" "$2"; }
# Validate the complete cli.rs doctor shape, not an all_ok substring in malformed JSON.
# Checks contain only string, boolean, or null scalars. serde_json sorts object keys.
doctor_value() {
  local string='"([^"\\[:cntrl:]]|\\(["\\/bfnrt]|u[[:xdigit:]]{4}))*"'
  local scalar pair object pattern
  scalar="($string|true|false|null)"
  pair="$string:$scalar"
  object="\\{($pair(,$pair)*)?\\}"
  pattern="^\\{\"all_ok\":(true|false),\"checks\":\\[($object(,$object)*)?\\],\"status\":\"ok\",\"tool\":\"phosphor-relay\",\"ts\":$string,\"version\":$string\\}$"
  [[ $1 =~ $pattern ]] || return 1
  [[ $1 =~ ^\{\"all_ok\":(true|false), ]] || return 1
  printf '%s' "${BASH_REMATCH[1]}"
}
usage_error() { fail 3 "$1" "usage: $USAGE"; }

seen_verb=0
while (($#)); do
  case $1 in
    install|schema|--schema|help|-h|--help)
      [[ $seen_verb == 0 ]] || usage_error 'Use one command only'
      seen_verb=1
      case $1 in --schema) VERB=schema ;; -h|--help) VERB=help ;; *) VERB=$1 ;; esac ;;
    --json) JSON=1 ;;
    --host|--port|--player)
      flag=$1
      (($# >= 2)) || usage_error "$flag needs a value"
      [[ -n $2 && $2 != -* ]] || usage_error "$flag needs a nonempty value"
      case $flag in
        --host) [[ -z $HOST ]] || usage_error 'Duplicate --host'; HOST=$2 ;;
        --port) [[ -z $PORT ]] || usage_error 'Duplicate --port'; PORT=$2 ;;
        --player) [[ -z $PLAYER ]] || usage_error 'Duplicate --player'; PLAYER=$2 ;;
      esac
      shift ;;
    *) usage_error "Unknown argument: $1" ;;
  esac
  shift
done
[[ -z $HOST || $HOST =~ ^([a-zA-Z0-9_][a-zA-Z0-9_.-]*@)?[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || usage_error 'Invalid --host: use an SSH alias, hostname, or IPv4 address, optionally user@host'
[[ -z $PLAYER || $PLAYER =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]*$ ]] || usage_error 'Invalid --player: use an MPRIS identifier without spaces or shell syntax'
if [[ -n $PORT ]]; then
  [[ $PORT =~ ^[0-9]{1,5}$ ]] || usage_error 'Invalid --port: use an integer from 1 through 65535'
  PORT=$((10#$PORT))
  ((PORT >= 1 && PORT <= 65535)) || usage_error 'Invalid --port: use an integer from 1 through 65535'
fi
[[ $VERB == install || -z $HOST$PORT$PLAYER ]] || usage_error 'Install options require the install command'

help_text() {
  printf '%s\n' "usage: $USAGE" \
    'Default command: install locally. --host uses SSH and SCP on the selected host.' \
    '--port overrides serve only. --player overrides the configured MPRIS player.' \
    'Defaults: config port 45777 and player spotify. Existing config is never rewritten.' \
    'Builds with cargo --locked, installs ~/.local/bin/phosphor-relay and its user unit.' \
    'Runs installed schema, config and doctor before enable --now. Does not restart an active service.' \
    'Linger is best effort. Diagnostics and child output go to stderr.' \
    'schema/--schema always emits JSON. help/-h/--help is inert.'
}
if [[ $VERB == help ]]; then
  if want_json; then
    envelope ok; printf ',"command":"help","usage":'; json_string "$(help_text)"; printf '}\n'
  else help_text; fi
  exit 0
fi
if [[ $VERB == schema ]]; then
  envelope ok
  # Output schemas are strict. The schema response refers to this same contract.
  # shellcheck disable=SC2016
  printf '%s\n' ',"command":"schema","schema":{
    "$schema":"https://json-schema.org/draft/2020-12/schema",
    "$id":"urn:phosphor:relay-install:2.2.0",
    "description":"relay-install.sh [install|schema|help] [--host HOST] [--port N] [--player NAME] [--json]. No verb means install. --schema aliases schema. -h and --help alias help. Schema is always JSON. Other commands use JSON when stdout is not a TTY or --json is present. Exactly one result on stdout. Child output and progress use stderr. Install builds with cargo --locked, installs the binary and unit, checks installed schema/config/doctor before enable --now, and attempts linger. Existing config is preserved. Active services are not restarted. Doctor reports the config port, not --port. Doctor all_ok is reported literally, not inferred from its exit code. An unsuccessful check is not proof of readiness. Exits: 0 completed, 2 unavailable dependency, 3 invalid arguments, 4 runtime failure. No streams. Help and schema are inert and need no checkout or external command.",
    "oneOf":[{"$ref":"#/$defs/install"},{"$ref":"#/$defs/help"},{"$ref":"#/$defs/discovery"},{"$ref":"#/$defs/error"}],
    "$defs":{
      "args":{"type":"object","additionalProperties":false,"properties":{
        "command":{"enum":["install","schema","help"],"default":"install"},
        "--host":{"type":"string","pattern":"^([a-zA-Z0-9_][a-zA-Z0-9_.-]*@)?[a-zA-Z0-9][a-zA-Z0-9_.-]*$","description":"Install only. SSH alias, hostname or IPv4, optionally user@host. Deployment must remain protected by Tailscale and firewall rules. Default local."},
        "--port":{"type":"integer","minimum":1,"maximum":65535,"description":"Install only. Serve override, not a config mutation or doctor override. Leading decimal zeroes accepted."},
        "--player":{"type":"string","pattern":"^[a-zA-Z0-9_][a-zA-Z0-9_.-]*$","description":"Install only. MPRIS player identifier. Default from config."},
        "--json":{"type":"boolean","default":false}
      },"description":"One command only. Duplicate value options, unknown arguments, absent values and install options on discovery commands exit 3."},
      "install":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","command","host","port","player","doctor_all_ok","linger","restarted"],"properties":{
        "status":{"const":"ok"},"tool":{"const":"relay-install"},"version":{"const":"2.2.0"},"ts":{"type":"string","format":"date-time"},"command":{"const":"install"},
        "host":{"type":"string","description":"Empty means local."},"port":{"type":["integer","null"],"minimum":1,"maximum":65535,"description":"Explicit serve override, null means config."},"player":{"type":["string","null"],"description":"Explicit serve override, null means config."},
        "doctor_all_ok":{"type":"boolean","description":"Actual pre-activation doctor field. False reports unmet checks, not a successful environment audit. Read stderr for details."},"linger":{"type":"boolean","description":"Whether enable-linger succeeded."},"restarted":{"const":false}
      }},
      "help":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","command","usage"],"properties":{
        "status":{"const":"ok"},"tool":{"const":"relay-install"},"version":{"const":"2.2.0"},"ts":{"type":"string","format":"date-time"},"command":{"const":"help"},"usage":{"type":"string"}
      }},
      "discovery":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","command","schema"],"properties":{
        "status":{"const":"ok"},"tool":{"const":"relay-install"},"version":{"const":"2.2.0"},"ts":{"type":"string","format":"date-time"},"command":{"const":"schema"},"schema":{"$ref":"https://json-schema.org/draft/2020-12/schema","description":"This complete contract, including strict result schemas and argument descriptions."}
      }},
      "error":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","error","fix"],"properties":{
        "status":{"const":"error"},"tool":{"const":"relay-install"},"version":{"const":"2.2.0"},"ts":{"type":"string","format":"date-time"},"error":{"type":"string"},"fix":{"type":"string"}
      }}
    }
  }}'
  exit 0
fi

# Everything above this point is builtin-only and has no filesystem side effects.
[[ -n ${HOME:-} && $HOME == /* && -n ${USER:-} ]] || fail 2 'HOME and USER are required' 'Set an absolute HOME and the target login USER before installing.'
SOURCE_PATH=${BASH_SOURCE[0]}
[[ $SOURCE_PATH == */* ]] || SOURCE_PATH=./$SOURCE_PATH
SELF_DIR=$(cd -- "${SOURCE_PATH%/*}" && pwd)
RELAY_DIR=$(cd -- "$SELF_DIR/../relay" && pwd) || fail 2 'Sibling relay checkout is missing' 'Run the installer from a complete Phosphor checkout.'
BIN_NAME=phosphor-relay
UNIT_NAME=phosphor-relay.service
need cargo 'Install Rust and Cargo, then rerun the installer.'
if [[ -z $HOST ]]; then
  need systemctl 'Use a Linux host with systemd user services.'
  need install 'Install coreutils on the target host.'
  need mkdir 'Install coreutils on the target host.'
else
  need ssh 'Install openssh-client, then rerun.'
  need scp 'Install openssh-client, then rerun.'
fi
EXEC="%h/.local/bin/$BIN_NAME serve"
[[ -z $PORT ]] || EXEC+=" --port $PORT"
[[ -z $PLAYER ]] || EXEC+=" --player $PLAYER"
unit_text() {
  printf '%s\n' '[Unit]' 'Description=phosphor-relay v2 desktop to phone bridge' \
    'After=network.target pipewire.service' 'Wants=pipewire.service' '' '[Service]' \
    'Type=simple' "ExecStart=$EXEC" 'Restart=on-failure' 'RestartSec=5' '' \
    '[Install]' 'WantedBy=default.target'
}
# Fix the target explicitly so a caller Cargo target setting cannot select stale bytes.
say "Building $BIN_NAME from $RELAY_DIR"
cargo build --locked --release --manifest-path "$RELAY_DIR/Cargo.toml" --target-dir "$RELAY_DIR/target" >&2 || fail 4 'Release build failed' "Run cargo build --locked --release --manifest-path $RELAY_DIR/Cargo.toml --target-dir $RELAY_DIR/target and inspect stderr."
BIN=$RELAY_DIR/target/release/$BIN_NAME
[[ -x $BIN ]] || fail 4 "Built binary missing at $BIN" 'Inspect Cargo output and repair the build target.'

if [[ -z $HOST ]]; then
  BIN_DIR=$HOME/.local/bin
  UNIT_DIR=$HOME/.config/systemd/user
  install -Dm755 "$BIN" "$BIN_DIR/$BIN_NAME" >&2
  mkdir -p "$UNIT_DIR" >&2
  unit_text > "$UNIT_DIR/$UNIT_NAME"
  "$BIN_DIR/$BIN_NAME" schema --json >&2 || fail 4 'Installed schema failed' 'Inspect the installed binary and rebuild before activation.'
  "$BIN_DIR/$BIN_NAME" config --json >&2 || fail 4 'Installed config check failed' 'Repair ~/.config/phosphor-relay/config.json and rerun. The installer did not change it.'
  doctor=$("$BIN_DIR/$BIN_NAME" doctor --json) || fail 4 'Installed doctor failed' 'Inspect stderr and repair the installed relay before activation.'
  say "$doctor"
  DOCTOR_ALL_OK=$(doctor_value "$doctor") || fail 4 'Doctor report is invalid' 'Inspect the doctor JSON contract before activation.'
  [[ $DOCTOR_ALL_OK == true ]] || say 'Warning: doctor reports unmet checks. Installation is not proof of environment readiness. Inspect its fixes above.'
  [[ -z $PORT ]] || say 'Note: doctor checked the config port, not the explicit serve --port override.'
  systemctl --user daemon-reload >&2
  systemctl --user enable --now "$UNIT_NAME" >&2
  if loginctl enable-linger "$USER" >&2; then LINGER=true; else say 'Warning: linger was not enabled. The relay may stop after logout.'; fi
else
  say "Staging binary and unit on $HOST"
  scp -q "$BIN" "$HOST:/tmp/$BIN_NAME.new" >&2 || fail 4 'Remote binary staging failed' 'Check SSH access to the tailnet host and rerun.'
  unit_text | ssh "$HOST" 'cat > /tmp/phosphor-relay.service.new' >&2 || fail 4 'Remote unit staging failed' 'Check SSH access and remote staging space, then rerun.'
  # Fixed command strings, not interpolated shell input. The remote result is private
  # protocol data. All remote child output still goes to the caller stderr.
  remote_result=$(ssh "$HOST" 'bash -s' <<'REMOTE'
set -euo pipefail
XDG_RUNTIME_DIR="/run/user/$(id -u)"
export XDG_RUNTIME_DIR
BIN_NAME=phosphor-relay
UNIT_NAME=phosphor-relay.service
doctor_value() {
  local string='"([^"\\[:cntrl:]]|\\(["\\/bfnrt]|u[[:xdigit:]]{4}))*"'
  local scalar pair object pattern
  scalar="($string|true|false|null)"
  pair="$string:$scalar"
  object="\\{($pair(,$pair)*)?\\}"
  pattern="^\\{\"all_ok\":(true|false),\"checks\":\\[($object(,$object)*)?\\],\"status\":\"ok\",\"tool\":\"phosphor-relay\",\"ts\":$string,\"version\":$string\\}$"
  [[ $1 =~ $pattern ]] || return 1
  local value=${1#*:}
  printf '%s' "${value%%,*}"
}
for tool in systemctl install mkdir mv rm; do
  command -v "$tool" >/dev/null 2>&1 || { printf 'Missing remote tool: %s\n' "$tool" >&2; exit 2; }
done
pkill -f "/tmp/${BIN_NAME}" 2>/dev/null || true
rm -f "/tmp/${BIN_NAME}" 2>/dev/null || true
mkdir -p "$HOME/.local/bin" "$HOME/.config/systemd/user" >&2
install -Dm755 "/tmp/${BIN_NAME}.new" "$HOME/.local/bin/${BIN_NAME}" >&2
mv -f "/tmp/${UNIT_NAME}.new" "$HOME/.config/systemd/user/${UNIT_NAME}" >&2
rm -f "/tmp/${BIN_NAME}.new" >&2
"$HOME/.local/bin/$BIN_NAME" schema --json >&2
"$HOME/.local/bin/$BIN_NAME" config --json >&2
doctor=$("$HOME/.local/bin/$BIN_NAME" doctor --json)
printf '%s\n' "$doctor" >&2
doctor_all_ok=$(doctor_value "$doctor") || { printf 'Doctor report is invalid. Inspect its JSON before activation.\n' >&2; exit 4; }
[[ $doctor_all_ok == true ]] || printf '%s\n' 'Warning: doctor reports unmet checks. Installation is not proof of environment readiness. Inspect its fixes above.' >&2
systemctl --user daemon-reload >&2
systemctl --user enable --now "$UNIT_NAME" >&2
linger=false
if loginctl enable-linger "$USER" >&2; then linger=true; else printf '%s\n' 'Warning: linger was not enabled. The relay may stop after logout.' >&2; fi
printf '%s %s\n' "$doctor_all_ok" "$linger"
REMOTE
  ) || {
    code=$?
    [[ $code != 2 ]] || fail 2 'A remote prerequisite is unavailable' 'Inspect remote stderr and install the missing tool before retrying.'
    fail 4 'Remote installation failed' 'Inspect remote stderr, repair the failed operation and rerun. Existing config is unchanged.'
  }
  [[ $remote_result =~ ^(true|false)' '(true|false)$ ]] || fail 4 'Unexpected remote installation receipt' 'Inspect SSH startup output and the remote installer before retrying.'
  DOCTOR_ALL_OK=${BASH_REMATCH[1]}
  LINGER=${BASH_REMATCH[2]}
  [[ -z $PORT ]] || say 'Note: doctor checked the config port, not the explicit serve --port override.'
fi
if want_json; then
  envelope ok
  printf ',"command":"install","host":'; json_string "$HOST"
  printf ',"port":%s,"player":' "${PORT:-null}"
  if [[ -n $PLAYER ]]; then json_string "$PLAYER"; else printf null; fi
  printf ',"doctor_all_ok":%s,"linger":%s,"restarted":false}\n' "$DOCTOR_ALL_OK" "$LINGER"
else
  printf 'Installed and enabled phosphor-relay%s. Existing active service was not restarted.\n' "${HOST:+ on $HOST}"
  printf 'Doctor all_ok: %s. Linger enabled: %s.\n' "$DOCTOR_ALL_OK" "$LINGER"
fi
