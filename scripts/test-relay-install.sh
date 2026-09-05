#!/usr/bin/env bash
# Confined production-script acceptance. Read the confinement report before authorization.
set -Eeuo pipefail
TOOL=test-relay-install
VERSION=1.0.0
PINNED_SOURCE=e585f2c5fb2107e9dab79586947a549b32335baa7fdff96d089172f839d108d8
PINNED_REMOTE=7841a87828e687f8a51673e37ddf31db35b48d2321b559b8fbb8782b6b2f684e
usage='scripts/test-relay-install.sh [run|schema|help] [--json] [--approved-source SHA256]'
CASE='' WORK=''
stamp() { printf '%(%Y-%m-%dT%H:%M:%S+00:00)T' -1; }
export TZ=UTC
fail() {
  trap - ERR
  if [[ -n ${WORK:-} && ${CASE:-} == "$WORK"/case-* && -d $CASE ]]; then
    printf 'Failed case: %s\n' "$CASE" >&2
    for evidence in calls out err; do
      if [[ -f $CASE/$evidence ]]; then
        printf '\nCase %s:\n' "$evidence" >&2
        /usr/bin/cat "$CASE/$evidence" >&2
      fi
    done
  fi
  printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"%s","fix":"%s"}\n' "$TOOL" "$VERSION" "$(stamp)" "$2" "$3"
  exit "$1"
}
MODE=run
SEEN_MODE=0
APPROVED=''
while (($#)); do
  case $1 in
    run|schema|--schema|help|--help|-h)
      [[ $SEEN_MODE == 0 ]] || fail 3 'Use one fixture command' "$usage"
      MODE=schema; [[ $1 != run ]] || MODE=run
      SEEN_MODE=1 ;;
    --json) : ;;
    --approved-source)
      [[ $# -ge 2 && -z $APPROVED && $2 =~ ^[a-f0-9]{64}$ ]] || fail 3 'Expected one SHA256 value' "$usage"
      APPROVED=$2; shift ;;
    *) fail 3 'Unknown fixture argument' "$usage" ;;
  esac
  shift
done
[[ $MODE == run || -z $APPROVED ]] || fail 3 'Source override requires the default run command' "$usage"
if [[ $MODE == schema ]]; then
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","schema":' "$TOOL" "$VERSION" "$(stamp)"
  # shellcheck disable=SC2016
  printf '%s\n' '{
    "$schema":"https://json-schema.org/draft/2020-12/schema",
    "$id":"urn:phosphor:test-relay-install:1.0.0",
    "description":"scripts/test-relay-install.sh [run|schema|help] [--json] [--approved-source SHA256]. Default command: run. schema/--schema and help/--help/-h are inert discovery aliases. Always JSON, including on terminals. No streams. One envelope on stdout, evidence on stderr. Source and remote heredoc must match embedded reviewed hashes. --approved-source optionally confirms the source hash, it is not required. Uses installed Bash, jq, coreutils and util-linux script, never installs dependencies. Runs only disposable host mocks and inert private PTY help. No live acceptance. Exits 0 complete, 2 missing prerequisite, 3 arguments or changed source pin, 4 failed check. Writes only a disposable scratch tree and deletes it after printing failure evidence.",
    "oneOf":[{"$ref":"#/$defs/result"},{"$ref":"#/$defs/discovery"},{"$ref":"#/$defs/error"}],
    "$defs":{
      "args":{"type":"object","additionalProperties":false,"properties":{"command":{"enum":["run","schema","help"],"default":"run"},"--json":{"type":"boolean","default":true},"--approved-source":{"type":"string","pattern":"^[a-f0-9]{64}$","description":"Run only. Optional assertion against the embedded source pin. Duplicate value options or commands are errors."}}},
      "result":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","source_sha256","passed"],"properties":{"status":{"const":"ok"},"tool":{"const":"test-relay-install"},"version":{"const":"1.0.0"},"ts":{"type":"string","format":"date-time"},"source_sha256":{"type":"string","pattern":"^[a-f0-9]{64}$"},"passed":{"type":"array","items":{"type":"string"}}}},
      "discovery":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","schema"],"properties":{"status":{"const":"ok"},"tool":{"const":"test-relay-install"},"version":{"const":"1.0.0"},"ts":{"type":"string","format":"date-time"},"schema":{"$ref":"https://json-schema.org/draft/2020-12/schema"}}},
      "error":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","error","fix"],"properties":{"status":{"const":"error"},"tool":{"const":"test-relay-install"},"version":{"const":"1.0.0"},"ts":{"type":"string","format":"date-time"},"error":{"type":"string"},"fix":{"type":"string"}}}
    }
  }}'
  exit 0
fi
[[ -z $APPROVED || $APPROVED == "$PINNED_SOURCE" ]] || fail 3 'Explicit source hash differs from the embedded pin' 'Review the changed source and update the reviewed pin.'
SOURCE_DIR=$(cd -- "${BASH_SOURCE[0]%/*}" && pwd)
SOURCE=$SOURCE_DIR/relay-install.sh
SOURCE_HASH=$(/usr/bin/sha256sum "$SOURCE")
[[ ${SOURCE_HASH%% *} == "$PINNED_SOURCE" ]] || fail 3 'Installer differs from the confinement-reviewed source' 'Review changed source and update both reviewed pins before execution.'
for tool in /usr/bin/jq /usr/bin/sha256sum /usr/bin/cmp /usr/bin/env /usr/bin/install /usr/bin/realpath /usr/bin/mktemp /usr/bin/mkdir /usr/bin/rm /usr/bin/mv /usr/bin/cat /usr/bin/script /bin/bash; do
  [[ -x $tool ]] || fail 2 'A fixture tool is unavailable' 'Provide the preexisting Bash, jq and coreutils tools. Do not install dependencies implicitly.'
done
SCRATCH=${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}
[[ -d $SCRATCH ]] || fail 2 'Scratch directory does not exist' 'Select an existing private JCODE_SCRATCH_DIR.'
SCRATCH=$(/usr/bin/realpath -e "$SCRATCH")
WORK=$(/usr/bin/mktemp -d "$SCRATCH/relay-install.fixture.XXXXXXXX")
cleanup() {
  [[ $WORK == "$SCRATCH"/relay-install.fixture.* && -d $WORK && ! -L $WORK ]] || return 1
  [[ $(/usr/bin/realpath -e "$WORK") == "$WORK" ]] || return 1
  /usr/bin/rm -rf -- "$WORK"
}
trap cleanup EXIT
trap 'fail 4 "Confined fixture assertion failed" "Inspect stderr and repair the named case. No live installer was used."' ERR
printf 'Confined work directory: %s\n' "$WORK" >&2
/usr/bin/mkdir -p "$WORK/repo/scripts" "$WORK/repo/relay/target/release"
/usr/bin/install -m644 "$SOURCE" "$WORK/repo/scripts/relay-install.sh"
/usr/bin/cmp "$SOURCE" "$WORK/repo/scripts/relay-install.sh"
printf '[package]\nname="fixture-only"\nversion="0.0.0"\n' > "$WORK/repo/relay/Cargo.toml"

# All PATH commands are copies of this dispatcher. No symlink and no fallback PATH.
/usr/bin/cat > "$WORK/mock" <<'MOCK'
#!/bin/bash
set -Eeuo pipefail
name=${0##*/}
reject() { printf '%s\n' "$name rejected: $*" >> "$WORK/violations"; exit 97; }
[[ $WORK == /*/relay-install.fixture.* && $CASE == "$WORK"/case-* ]] || exit 97
{ printf '%s' "$name"; printf ' <%s>' "$@"; printf '\n'; } >> "$CASE/calls"
# Only the three legacy literal staging paths may be mapped. All other paths
# must resolve under WORK without traversing a symlink or an outside ancestor.
map_path() {
  local path=$1 canonical
  case $path in
    /tmp/phosphor-relay|/tmp/phosphor-relay.new|/tmp/phosphor-relay.service.new)
      [[ $MOCK_SIDE == remote ]] || reject 'local literal /tmp path'
      path=$CASE/stage/${path##*/} ;;
  esac
  [[ $path == "$WORK"/* && $path != *'/../'* && $path != */.. ]] || reject "outside path $path"
  canonical=$(/usr/bin/realpath -m -- "$path")
  [[ $canonical == "$path" ]] || reject "noncanonical or symlink path $path"
  MAPPED=$path
}
case $name in
  cargo)
    [[ $# == 7 && $1 == build && $2 == --locked && $3 == --release && $4 == --manifest-path && $5 == "$WORK/repo/relay/Cargo.toml" && $6 == --target-dir && $7 == "$WORK/repo/relay/target" ]] || reject 'cargo argv'
    [[ $MOCK_FAIL != cargo ]] || exit 1
    printf 'mock cargo build output\n'
    if [[ $MOCK_FAIL != missing-binary ]]; then
      /usr/bin/install -m755 "$WORK/relay-stub" "$WORK/repo/relay/target/release/phosphor-relay"
    fi ;;
  install)
    [[ $# == 3 && $1 == -Dm755 ]] || reject 'install argv'
    map_path "$2"; src=$MAPPED
    map_path "$3"; dst=$MAPPED
    [[ $src == "$WORK/repo/relay/target/release/phosphor-relay" || $src == "$CASE/stage/phosphor-relay.new" ]] || reject 'install source'
    [[ $dst == "$HOME/.local/bin/phosphor-relay" ]] || reject 'install destination'
    [[ $MOCK_FAIL != install ]] || exit 1
    /usr/bin/install -Dm755 -- "$src" "$dst" ;;
  mkdir)
    [[ $1 == -p && $# -ge 2 && $# -le 3 ]] || reject 'mkdir argv'
    shift
    paths=()
    for path in "$@"; do
      map_path "$path"
      [[ $MAPPED == "$HOME/.local/bin" || $MAPPED == "$HOME/.config/systemd/user" ]] || reject 'mkdir destination'
      paths+=("$MAPPED")
    done
    /usr/bin/mkdir -p -- "${paths[@]}" ;;
  mv)
    [[ $# == 3 && $1 == -f && $2 == /tmp/phosphor-relay.service.new && $3 == "$HOME/.config/systemd/user/phosphor-relay.service" ]] || reject 'mv argv'
    map_path "$2"; src=$MAPPED
    map_path "$3"; dst=$MAPPED
    /usr/bin/mv -f -- "$src" "$dst" ;;
  rm)
    [[ $# == 2 && $1 == -f && ( $2 == /tmp/phosphor-relay || $2 == /tmp/phosphor-relay.new ) ]] || reject 'rm argv'
    map_path "$2"
    /usr/bin/rm -f -- "$MAPPED" ;;
  systemctl)
    [[ $MOCK_SIDE != remote || ${XDG_RUNTIME_DIR:-} == /run/user/424242 ]] || reject 'remote runtime uid'
    [[ $MOCK_SIDE != local || -z ${XDG_RUNTIME_DIR:-} ]] || reject 'inherited real runtime'
    if [[ $# == 2 && $1 == --user && $2 == daemon-reload ]]; then
      [[ $MOCK_FAIL != reload ]] || exit 1
    elif [[ $# == 4 && $1 == --user && $2 == enable && $3 == --now && $4 == phosphor-relay.service ]]; then
      [[ $MOCK_FAIL != enable ]] || exit 1
    else reject 'systemctl argv'; fi
    printf 'mock systemctl output\n' ;;
  loginctl)
    [[ $# == 2 && $1 == enable-linger && $2 == fixture-user && $USER == fixture-user ]] || reject 'loginctl argv or user'
    [[ $MOCK_FAIL != linger ]] || exit 1
    printf 'mock loginctl output\n' ;;
  pkill)
    [[ $MOCK_SIDE == remote && $# == 2 && $1 == -f && $2 == /tmp/phosphor-relay ]] || reject 'pkill argv'
    exit 1 ;;
  id)
    [[ $MOCK_SIDE == remote && $# == 1 && $1 == -u ]] || reject 'id argv'
    printf '424242\n' ;;
  scp)
    [[ $MOCK_SIDE == local && $# == 3 && $1 == -q && $2 == "$WORK/repo/relay/target/release/phosphor-relay" && $3 == fixture-host:/tmp/phosphor-relay.new ]] || reject 'scp argv'
    [[ $MOCK_FAIL != scp ]] || exit 1
    /usr/bin/install -m755 "$2" "$CASE/stage/phosphor-relay.new" ;;
  ssh)
    [[ $MOCK_SIDE == local && $# == 2 && $1 == fixture-host ]] || reject 'ssh argv'
    case $2 in
      'cat > /tmp/phosphor-relay.service.new')
        [[ $MOCK_FAIL != ssh-stage ]] || exit 1
        /usr/bin/cat > "$CASE/stage/phosphor-relay.service.new" ;;
      'bash -s')
        /usr/bin/cat > "$CASE/received-remote.sh"
        hash=$(/usr/bin/sha256sum "$CASE/received-remote.sh")
        [[ ${hash%% *} == "$PINNED_REMOTE" ]] || reject 'remote heredoc differs from reviewed bytes'
        [[ $MOCK_FAIL != ssh-run ]] || exit 1
        /usr/bin/env -i HOME="$CASE/remote" USER=fixture-user TMPDIR="$CASE/remote-tmp" \
          PATH="$CASE/bin" LC_ALL=C TZ=UTC WORK="$WORK" CASE="$CASE" \
          MOCK_SIDE=remote MOCK_FAIL="$MOCK_FAIL" DOCTOR_MODE="$DOCTOR_MODE" \
          /bin/bash "$CASE/received-remote.sh" ;;
      *) reject 'unknown SSH command string' ;;
    esac ;;
  *) reject 'unknown command' ;;
esac
MOCK

/usr/bin/cat > "$WORK/relay-stub" <<'STUB'
#!/bin/bash
set -Eeuo pipefail
[[ $0 == "$HOME/.local/bin/phosphor-relay" ]] || exit 97
printf 'relay <%s> <%s>\n' "${1:-}" "${2:-}" >> "$CASE/calls"
[[ $# == 2 && $2 == --json ]] || { printf 'unexpected relay argv\n' >> "$WORK/violations"; exit 97; }
case $1 in
  schema)
    [[ $MOCK_FAIL != schema ]] || exit 4
    printf '{"status":"ok","tool":"phosphor-relay","version":"2.2.0","ts":"2026-09-05T00:00:00+00:00","commands":["library"]}\n' ;;
  config)
    [[ $MOCK_FAIL != config ]] || exit 4
    if [[ -f $HOME/.config/phosphor-relay/config.json ]]; then
      /usr/bin/cat "$HOME/.config/phosphor-relay/config.json"
    elif [[ -d $HOME/Music ]]; then
      printf '{"port":45777,"player":"spotify","libraries":[{"id":"music0"}]}\n'
    else printf '{"port":45777,"player":"spotify","libraries":[]}\n'; fi ;;
  doctor)
    [[ $MOCK_FAIL != doctor ]] || exit 4
    # The fixture writes the selected port to config. Doctor gets no --port flag.
    case $DOCTOR_MODE in
      true) checks='[{"check":"port","detail":"configured fixture port is free","ok":true}]'; value=true ;;
      false) checks='[{"check":"rclone","fix":"install rclone","ok":false},{"check":"port","fix":"configured port is busy, owner unknown","ok":false}]'; value=false ;;
      escapes) checks='[{"check":"root:music0","detail":"quote\" slash\\ tab\t ctrl\u0001","ok":true}]'; value=true ;;
      malformed) printf '{"all_ok":true,"checks":[broken}\n'; exit 0 ;;
      missing) printf '{"checks":[],"status":"ok"}\n'; exit 0 ;;
      *) printf 'unknown doctor fixture\n' >> "$WORK/violations"; exit 97 ;;
    esac
    printf '{"all_ok":%s,"checks":%s,"status":"ok","tool":"phosphor-relay","ts":"2026-09-05T00:00:00+00:00","version":"2.2.0"}\n' "$value" "$checks" ;;
  *) printf 'unexpected relay command\n' >> "$WORK/violations"; exit 97 ;;
esac
STUB

PASSED=()
prepare() {
  CASE=$WORK/case-$1
  /usr/bin/mkdir -p "$CASE/bin" "$CASE/local" "$CASE/remote" "$CASE/local-tmp" "$CASE/remote-tmp" "$CASE/stage"
  for cmd in cargo install mkdir mv rm systemctl loginctl pkill id scp ssh; do
    /usr/bin/install -m755 "$WORK/mock" "$CASE/bin/$cmd"
  done
  : > "$CASE/calls"
  MOCK_FAIL=none DOCTOR_MODE=true
  /usr/bin/rm -f -- "$WORK/repo/relay/target/release/phosphor-relay"
}
run() {
  local expected=$1 result
  shift
  if /usr/bin/env -i HOME="$CASE/local" USER=fixture-user TMPDIR="$CASE/local-tmp" \
    PATH="$CASE/bin" LC_ALL=C TZ=UTC WORK="$WORK" CASE="$CASE" \
    MOCK_SIDE=local MOCK_FAIL="$MOCK_FAIL" DOCTOR_MODE="$DOCTOR_MODE" PINNED_REMOTE="$PINNED_REMOTE" \
    /bin/bash "$WORK/repo/scripts/relay-install.sh" "$@" > "$CASE/out" 2> "$CASE/err"; then
    result=0
  else result=$?; fi
  [[ $result == "$expected" ]] || { /usr/bin/cat "$CASE/err" >&2; printf 'case=%s expected=%s actual=%s\n' "${CASE##*/}" "$expected" "$result" >&2; return 1; }
  /usr/bin/jq -es 'length == 1 and (.[0] | .tool == "relay-install" and .version == "2.2.0" and (.ts | test("[+-][0-9]{2}:[0-9]{2}$")))' "$CASE/out" >/dev/null
  if [[ $expected == 0 ]]; then
    /usr/bin/jq -e '.status == "ok"' "$CASE/out" >/dev/null
  else
    /usr/bin/jq -e '.status == "error" and (.error|length)>0 and (.fix|length)>0' "$CASE/out" >/dev/null
  fi
  [[ ! -s $WORK/violations ]]
  /usr/bin/cmp "$SOURCE" "$WORK/repo/scripts/relay-install.sh"
}
check() { /usr/bin/jq -e "$1" "$CASE/out" >/dev/null; }
contains() { [[ $(<"$1") == *"$2"* ]]; }
absent() { [[ $(<"$CASE/calls") != *"$1"* ]]; }
pass() { PASSED+=("$1"); printf 'PASS %s\n' "$1" >&2; }
check_install() {
  local home=$1 exec=$2 calls
  /usr/bin/cmp "$WORK/relay-stub" "$home/.local/bin/phosphor-relay"
  [[ -x $home/.local/bin/phosphor-relay ]]
  contains "$home/.config/systemd/user/phosphor-relay.service" "ExecStart=$exec"
  contains "$home/.config/systemd/user/phosphor-relay.service" 'Restart=on-failure'
  calls=$(<"$CASE/calls")
  [[ $calls == *'relay <schema> <--json>'*'relay <config> <--json>'*'relay <doctor> <--json>'*'systemctl <--user> <daemon-reload>'*'systemctl <--user> <enable> <--now> <phosphor-relay.service>'* ]]
  absent restart
}

# Inert discovery is exercised through the exact copied public script and empty PATH.
prepare discovery
/usr/bin/rm -f -- "$CASE/bin/cargo"
run 0 schema
# shellcheck disable=SC2016
check '.schema."$defs" | all(.[]; .additionalProperties == false)'
SCHEMA=$WORK/schema.json
/usr/bin/install -m644 "$CASE/out" "$SCHEMA"
[[ ! -s $CASE/calls ]]
run 0 --schema --json
run 0 --help
check '.command == "help" and (.usage | contains("--host"))'
[[ ! -s $CASE/calls ]]
pass 'I1 inert schema aliases and piped help'

# A private PTY, not a desktop terminal. Both commands are literal and inert.
prepare tty
# shellcheck disable=SC2016
for command in '/bin/bash "$WORK/repo/scripts/relay-install.sh" --help' '/bin/bash "$WORK/repo/scripts/relay-install.sh" --help --json'; do
  /usr/bin/env -i HOME="$CASE/local" USER=fixture-user TMPDIR="$CASE/local-tmp" \
    PATH="$CASE/bin" SHELL=/bin/bash LC_ALL=C TZ=UTC WORK="$WORK" \
    /usr/bin/script --quiet --return --command "$command" "$CASE/tty.typescript" \
    </dev/null > "$CASE/tty.out" 2> "$CASE/tty.err"
  if [[ $command == *--json ]]; then
    /usr/bin/jq -e '.command == "help" and .status == "ok"' "$CASE/tty.out" >/dev/null
  else contains "$CASE/tty.out" 'usage: relay-install.sh'; fi
done
[[ ! -s $CASE/calls ]]
pass 'I1b real isatty human help and forced JSON on a private PTY'

for flag in --host --port --player; do
  prepare "missing-${flag#--}"
  run 3 "$flag"
  [[ ! -s $CASE/calls ]]
  run 3 "$flag" ''
  [[ ! -s $CASE/calls ]]
done
prepare args
for port in 0 65536 9999999999999999999999999999 1x -1; do run 3 --port "$port"; done
run 3 --host 'host;touch nope'
run 3 --player 'spotify %n'
run 3 --port 42 --port 43
run 3 schema --host fixture-host
run 3 help install
[[ ! -s $CASE/calls ]]
pass 'I2 argument rejection before commands'
prepare escapes
chars=$'quote" backslash\\ newline\n tab\t return\r utf8-\303\251 '
for ((i=1; i<32; i++)); do printf -v char '\\%03o' "$i"; printf -v char '%b' "$char"; chars+=$char; done
run 3 "$chars" --json
/usr/bin/jq -e --arg value "Unknown argument: $chars" '.error == $value' "$CASE/out" >/dev/null
[[ ! -s $CASE/calls ]]
pass 'I3 full Bash JSON escaping and explicit JSON errors'

for dependency in cargo systemctl install mkdir ssh scp; do
  prepare "missing-$dependency"
  /usr/bin/rm -f -- "$CASE/bin/$dependency"
  if [[ $dependency == ssh || $dependency == scp ]]; then run 2 --host fixture-host; else run 2; fi
  [[ ! -s $CASE/calls ]]
done
pass 'I4 dependency preflight before build'

prepare local
run 0
check '.host == "" and .port == null and .player == null and .doctor_all_ok and .linger and (.restarted == false)'
check_install "$CASE/local" '%h/.local/bin/phosphor-relay serve'
contains "$CASE/err" '"libraries":[]'
pass 'I5 default local binary unit doctor ordering and no Music'

prepare 'home space'
run 0
check_install "$CASE/local" '%h/.local/bin/phosphor-relay serve'
run 0 --host fixture-host
check_install "$CASE/remote" '%h/.local/bin/phosphor-relay serve'
pass 'I5b local and remote HOME paths containing spaces'

prepare unit-write
/usr/bin/mkdir -p "$CASE/local/.config/systemd/user/phosphor-relay.service"
run 4
absent systemctl
absent 'relay <schema>'
/usr/bin/rm -rf -- "$CASE/local/.config/systemd/user/phosphor-relay.service"
run 0
check_install "$CASE/local" '%h/.local/bin/phosphor-relay serve'
pass 'I5c actual unit redirection failure and recovery'

prepare configured
/usr/bin/mkdir -p "$CASE/local/.config/phosphor-relay" "$CASE/local/Music"
printf '{"port":47888,"player":"vlc","libraries":[{"id":"keep","path":"%s/Music"}]}\n' "$CASE/local" > "$CASE/local/.config/phosphor-relay/config.json"
/usr/bin/install -m644 "$CASE/local/.config/phosphor-relay/config.json" "$CASE/config-before"
run 0 install --port 47888 --player vlc --json
check_install "$CASE/local" '%h/.local/bin/phosphor-relay serve --port 47888 --player vlc'
/usr/bin/cmp "$CASE/config-before" "$CASE/local/.config/phosphor-relay/config.json"
run 0 --port 47888 --player vlc
/usr/bin/cmp "$CASE/config-before" "$CASE/local/.config/phosphor-relay/config.json"
pass 'I6 overrides repeat upgrade and byte-preserved config'

prepare music
/usr/bin/mkdir -p "$CASE/local/Music"
run 0
contains "$CASE/err" '"id":"music0"'
[[ ! -e $CASE/local/.config/phosphor-relay/config.json ]]
pass 'I7 existing Music default without config creation'

prepare remote
/usr/bin/mkdir -p "$CASE/remote/.config/phosphor-relay"
printf '{"port":47888,"player":"vlc","libraries":[]}\n' > "$CASE/remote/.config/phosphor-relay/config.json"
/usr/bin/install -m644 "$CASE/remote/.config/phosphor-relay/config.json" "$CASE/config-before"
printf 'stale fixture only\n' > "$CASE/stage/phosphor-relay"
run 0 --host fixture-host --port 47888 --player vlc
check_install "$CASE/remote" '%h/.local/bin/phosphor-relay serve --port 47888 --player vlc'
/usr/bin/cmp "$CASE/config-before" "$CASE/remote/.config/phosphor-relay/config.json"
[[ ! -e $CASE/stage/phosphor-relay && ! -e $CASE/stage/phosphor-relay.new && ! -e $CASE/stage/phosphor-relay.service.new ]]
contains "$CASE/calls" 'pkill <-f> </tmp/phosphor-relay>'
contains "$CASE/calls" 'id <-u>'
run 0 --host fixture-host --port 47888 --player vlc
/usr/bin/cmp "$CASE/config-before" "$CASE/remote/.config/phosphor-relay/config.json"
pass 'I8 byte-identical remote heredoc mapped staging cleanup and repeat install'

for side in local remote; do
  args=(); [[ $side != remote ]] || args=(--host fixture-host)
  for mode in false escapes malformed missing; do
    prepare "$side-doctor-$mode"
    DOCTOR_MODE=$mode
    if [[ $mode == malformed || $mode == missing ]]; then
      run 4 "${args[@]}"; absent systemctl
    else
      run 0 "${args[@]}"
      if [[ $mode == false ]]; then
        check '.doctor_all_ok == false'
        contains "$CASE/err" 'owner unknown'
        contains "$CASE/err" 'Warning: doctor reports unmet checks'
      else check '.doctor_all_ok == true'; fi
    fi
  done
  for fault in cargo missing-binary install schema config doctor reload enable; do
    prepare "$side-fail-$fault"
    MOCK_FAIL=$fault
    run 4 "${args[@]}"
    case $fault in cargo|missing-binary|install|schema|config|doctor) absent systemctl ;; esac
    MOCK_FAIL=none
    run 0 "${args[@]}"
  done
  prepare "$side-linger"
  MOCK_FAIL=linger
  run 0 "${args[@]}"
  check '.linger == false'
  contains "$CASE/err" 'linger was not enabled'
done
pass 'I9 true false malformed doctor and error recovery both branches'
for fault in scp ssh-stage ssh-run; do
  prepare "transport-$fault"
  MOCK_FAIL=$fault
  run 4 --host fixture-host
  absent systemctl
  MOCK_FAIL=none
  run 0 --host fixture-host
done
pass 'I10 transport failure and recovery'

# Validate each result property against the published strict shape without a new framework.
# The schema itself remains JSON Schema, not this test helper's format.
prepare final
run 0 --port 00080
check '.port == 80'
/usr/bin/jq -e --slurpfile contract "$SCHEMA" '
  . as $result | $contract[0].schema."$defs".install as $shape |
  (($result|keys) - ($shape.properties|keys) | length) == 0 and
  ($shape.required - ($result|keys) | length) == 0
' "$CASE/out" >/dev/null
[[ ! -s $WORK/violations ]]
AFTER=$(/usr/bin/sha256sum "$SOURCE")
[[ $SOURCE_HASH == "$AFTER" ]]
/usr/bin/cmp "$SOURCE" "$WORK/repo/scripts/relay-install.sh"
pass 'I11 strict output keys decimal port and unchanged source hashes'
printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","source_sha256":"%s","passed":' "$TOOL" "$VERSION" "$(stamp)" "$PINNED_SOURCE"
printf '%s\n' "${PASSED[@]}" | /usr/bin/jq -Rsc 'split("\n")[:-1]'
printf '}\n'
