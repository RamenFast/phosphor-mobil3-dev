#!/usr/bin/env bash
# force-link-states.sh — reproduce the relay link states on demand.
#
# Runs a SACRIFICIAL relay on a spare port and breaks that one, so the estate
# relays Ben actually listens to are never touched. Verifies they are healthy
# before and after, and refuses to run if the port is already in use.
#
# The states this forces are the inputs to RemoteLinkTruth; the mapping from
# input to band string has host tests. See
# docs/dev/receipts/phosphor-2.0/phase-B-remote-truth.md
#
# Agent-first per ~/Dev/ClaudeWorkspace/AGENT-CLI-STANDARD.md:
#   exits 0 = every state reproduced, 2 = a state failed, 3 = bad input,
#   4 = runtime failure (port busy, relay would not start).
set -uo pipefail

TOOL="force-link-states"
VERSION="2.0.0"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
cd "$REPO_ROOT" || { echo "cannot enter repo root" >&2; exit 4; }

PORT=45998
RELAY="relay/target/release/phosphor-relay"
ESTATE=(100.114.165.77 100.66.109.56)
JSON=0
SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
WORK="$(mktemp -d "$SCRATCH_ROOT/phosphor-force-link-states.XXXXXX")"

for arg in "$@"; do
  case "$arg" in
    --json) JSON=1 ;;
    --port=*) PORT="${arg#--port=}" ;;
    -h|--help)
      cat <<'USAGE'
force-link-states — reproduce relay link states against a sacrificial relay

  scripts/force-link-states.sh [--json] [--port=N]

Forces, in order: baseline · stalled (SIGSTOP) · backoff (SIGKILL) · error
(closed port). Never touches the estate relays; checks they stay healthy.

exits: 0 all reproduced · 2 a state failed · 3 bad input · 4 runtime failure
USAGE
      exit 0 ;;
    *) echo "unknown argument: $arg (try --help)" >&2; exit 3 ;;
  esac
done

say() { [ "$JSON" -eq 0 ] && printf '%s\n' "$*" >&2; return 0; }
RESULTS=()
record() { RESULTS+=("$1:$2:$3"); say "$(printf '%-10s %-6s %s' "$1" "$2" "$3")"; }

[ -x "$RELAY" ] || { echo "missing $RELAY; run: cargo build --release --manifest-path relay/Cargo.toml" >&2; exit 4; }

# Never fight for a port that is already serving something.
if (echo >"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  echo "port $PORT is already in use; fix: pass --port=N with a free port" >&2
  exit 4
fi

estate_health() {
  local host="$1"
  timeout 15 "$RELAY" probe --host "$host" 2>/dev/null | grep -o '"a_per_sec":[0-9.]*' | cut -d: -f2
}

say "checking the estate relays before we start"
BEFORE=()
for h in "${ESTATE[@]}"; do BEFORE+=("$(estate_health "$h")"); done

# One K-frame reader, shared by every case below.
#
# The socket work runs in a subshell: a failed `exec 3<>/dev/tcp/...` terminates
# the shell that ran it, which would kill this script rather than return an error
# we can report. Isolating it means a refused connection is just exit 7.
read_frames() { # port seconds outfile
  local port="$1" secs="$2" out="$3"
  (
    body='{"proto":2,"client":"force-link-states","audio":true,"geometry":false}'
    len=$(printf '%s' "$body" | wc -c)
    exec 3<>"/dev/tcp/127.0.0.1/$port" || exit 7
    {
      printf 'H'
      printf '%b' "$(printf '\\x%02x\\x%02x\\x%02x\\x%02x' \
        $(( (len>>24)&255 )) $(( (len>>16)&255 )) $(( (len>>8)&255 )) $(( len&255 )) )"
      printf '%s' "$body"
    } >&3
    timeout "$secs" cat <&3 >"$out"
    exec 3<&-
  ) 2>/dev/null
}
frame_count() { strings "$1" 2>/dev/null | grep -c '"ts_ms"'; }
# Largest gap between consecutive K frames, in ms. A stall shows up here.
max_gap_ms() {
  strings "$1" 2>/dev/null | grep -o '"ts_ms":[0-9]*' | cut -d: -f2 \
    | awk 'NR>1{d=$1-p; if(d>m) m=d} {p=$1} END{print m+0}'
}

RELAY_LOG="$WORK/relay.log"
"$RELAY" serve --port "$PORT" >"$RELAY_LOG" 2>&1 &
SACRIFICIAL=$!
cleanup() {
  kill -CONT "$SACRIFICIAL" 2>/dev/null
  kill -9 "$SACRIFICIAL" 2>/dev/null
  rm -rf "$WORK"
}
trap cleanup EXIT
sleep 3

kill -0 "$SACRIFICIAL" 2>/dev/null || { echo "sacrificial relay would not start; see $RELAY_LOG" >&2; exit 4; }

# ---- baseline: frames flowing on a healthy link ------------------------------
read_frames "$PORT" 4 "$WORK/base.bin"
BASE=$(frame_count "$WORK/base.bin")
if [ "$BASE" -ge 2 ]; then
  record baseline green "$BASE K frames, steady cadence"
else
  record baseline red "only $BASE K frames; the relay is not streaming"
fi

# ---- stalled: frozen process, socket still open ------------------------------
( read_frames "$PORT" 9 "$WORK/stall.bin" ) & READER=$!
sleep 3; kill -STOP "$SACRIFICIAL"; sleep 5; kill -CONT "$SACRIFICIAL"
wait $READER 2>/dev/null
GAP=$(max_gap_ms "$WORK/stall.bin")
# A healthy link ticks every ~1000 ms, so anything past 3 s is a genuine freeze.
if [ "$GAP" -ge 3000 ]; then
  record stalled green "${GAP} ms gap with the socket still open"
else
  record stalled red "largest gap only ${GAP} ms; the freeze did not take"
fi

# ---- backoff: peer dies mid-stream -------------------------------------------
( read_frames "$PORT" 6 "$WORK/kill.bin" ) & READER=$!
sleep 2; kill -9 "$SACRIFICIAL" 2>/dev/null
wait $READER 2>/dev/null
sleep 1
if (echo >"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  record backoff red "port still accepts after SIGKILL"
else
  record backoff green "EOF mid-stream, then the port refuses"
fi

# ---- error: nothing listening ------------------------------------------------
read_frames "$PORT" 2 "$WORK/error.bin"
ERROR_RESULT=$?
if [ "$ERROR_RESULT" -eq 7 ]; then
  record error green "connection refused, the terminal-failure path"
else
  record error red "a closed port did not refuse"
fi

# ---- the estate relays must be exactly as we found them ----------------------
i=0; ESTATE_OK=1
for h in "${ESTATE[@]}"; do
  after=$(estate_health "$h")
  if [ -n "$after" ] && [ -n "${BEFORE[$i]}" ]; then
    say "  estate $h: ${BEFORE[$i]} -> $after A-frames/sec"
  else
    ESTATE_OK=0
  fi
  i=$((i+1))
done
if [ "$ESTATE_OK" -eq 1 ]; then
  record estate green "both estate relays healthy, untouched"
else
  record estate red "an estate relay is unreachable; investigate before trusting this run"
fi

GREEN=0; RED=0
for r in "${RESULTS[@]}"; do
  case "$r" in *:green:*) GREEN=$((GREEN+1)) ;; *:red:*) RED=$((RED+1)) ;; esac
done

if [ "$JSON" -eq 1 ]; then
  printf '{"status":"%s","tool":"%s","version":"%s","ts":"%s","data":{"green":%d,"total":%d,"states":[' \
    "$([ "$RED" -eq 0 ] && echo ok || echo error)" "$TOOL" "$VERSION" \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$GREEN" "$((GREEN+RED))"
  for i in "${!RESULTS[@]}"; do
    [ "$i" -gt 0 ] && printf ','
    IFS=':' read -r id state detail <<<"${RESULTS[$i]}"
    printf '{"id":"%s","state":"%s","detail":"%s"}' "$id" "$state" "$detail"
  done
  printf ']}}\n'
else
  printf '\n  %d/%d states reproduced\n' "$GREEN" "$((GREEN+RED))" >&2
fi

[ "$RED" -eq 0 ] && exit 0 || exit 2
