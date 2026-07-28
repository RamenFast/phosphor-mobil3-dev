#!/usr/bin/env bash
# ship-check.sh — the one scoreboard for Phosphor Mobile v2.
#
# Answers a single question with a number: how many of the fixed release gates
# are green right now? The gate list is FIXED so the denominator cannot drift to
# flatter the result. Every gate names the exact command that establishes it, so
# a green here is a fact you can re-run, not a claim.
#
# Agent-first per ~/Dev/ClaudeWorkspace/AGENT-CLI-STANDARD.md:
#   one-shot envelope {status,tool,version,ts,data}; errors carry `fix`;
#   exits 0 = all green, 2 = some gate red, 3 = bad input, 4 = runtime failure.
set -uo pipefail

TOOL="ship-check"
VERSION="2.0.0"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
cd "$REPO_ROOT" || { echo "cannot enter repo root $REPO_ROOT" >&2; exit 4; }

# The protected note is NEVER opened. Only its hash is ever computed.
PROTECTED_NOTE="Phosphor build.md"
PROTECTED_BASELINE_FILE="docs/dev/receipts/phosphor-2.0/protected-note.sha256"

JSON=0
QUICK=0
ONLY=""
for arg in "$@"; do
  case "$arg" in
    --json) JSON=1 ;;
    --quick) QUICK=1 ;;
    --only=*) ONLY="${arg#--only=}" ;;
    -h|--help)
      cat <<'USAGE'
ship-check — release gate scoreboard for Phosphor Mobile v2

  scripts/ship-check.sh [--json] [--quick] [--only=PREFIX]

  --json         emit the one-shot envelope instead of the human table
  --quick        skip gates that compile or assemble (fast signal only)
  --only=PREFIX  run just the gates whose id starts with PREFIX (e.g. --only=boundary)

exits: 0 all gates green · 2 a gate is red · 3 bad input · 4 runtime failure
USAGE
      exit 0 ;;
    *)
      printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"unknown argument: %s","fix":"run scripts/ship-check.sh --help for accepted flags"}\n' \
        "$TOOL" "$VERSION" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$arg"
      exit 3 ;;
  esac
done

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }

GATE_IDS=(); GATE_STATES=(); GATE_DETAILS=(); GATE_FIXES=()
record() { # id state detail fix
  GATE_IDS+=("$1"); GATE_STATES+=("$2"); GATE_DETAILS+=("$3"); GATE_FIXES+=("${4:-}")
  if [ "$JSON" -eq 0 ]; then
    local mark
    case "$2" in
      green) mark=" ok " ;;
      red)   mark="FAIL" ;;
      *)     mark="skip" ;;
    esac
    printf '%s  %-28s %s\n' "$mark" "$1" "$3" >&2
  fi
}

selected() { [ -z "$ONLY" ] && return 0; case "$1" in "$ONLY"*) return 0 ;; *) return 1 ;; esac; }

# ---- environment -------------------------------------------------------------
if [ -f scripts/env.sh ]; then
  # shellcheck disable=SC1091
  . scripts/env.sh
fi
GRADLE_LOG="$(mktemp -t ship-check-gradle.XXXXXX)"
trap 'rm -f "$GRADLE_LOG"' EXIT

gradle_run() { ./gradlew "$@" --no-daemon >"$GRADLE_LOG" 2>&1; }

# Parse the JUnit XML rather than trusting gradle's summary line: an UP-TO-DATE
# task prints success without having run anything.
test_failures() { # results-dir
  local dir="$1"
  [ -d "$dir" ] || { echo "missing"; return; }
  local n
  n=$(grep -ho 'failures="[0-9]*"' "$dir"/*.xml 2>/dev/null | awk -F'"' '{s+=$2} END {print s+0}')
  local e
  e=$(grep -ho 'errors="[0-9]*"' "$dir"/*.xml 2>/dev/null | awk -F'"' '{s+=$2} END {print s+0}')
  echo $((n + e))
}
test_count() {
  local dir="$1"
  [ -d "$dir" ] || { echo 0; return; }
  grep -ho 'tests="[0-9]*"' "$dir"/*.xml 2>/dev/null | awk -F'"' '{s+=$2} END {print s+0}'
}

# ---- 1..2 compile ------------------------------------------------------------
if [ "$QUICK" -eq 0 ]; then
  if selected compile.play; then
    if gradle_run :app:compilePlayDebugKotlin; then
      record "compile.play" green "playDebug Kotlin compiles"
    else
      record "compile.play" red "compile failed" "read $GRADLE_LOG; run ./gradlew :app:compilePlayDebugKotlin"
    fi
  fi
  if selected compile.fortress; then
    if gradle_run :app:compileFortressDebugKotlin :app:compileFortressDebugUnitTestKotlin; then
      record "compile.fortress" green "fortressDebug + tests compile"
    else
      record "compile.fortress" red "compile failed" "read $GRADLE_LOG; run ./gradlew :app:compileFortressDebugKotlin"
    fi
  fi
else
  selected compile.play && record "compile.play" skip "skipped (--quick)"
  selected compile.fortress && record "compile.fortress" skip "skipped (--quick)"
fi

# ---- 3..4 unit tests ---------------------------------------------------------
for flavor in Play Fortress; do
  id="tests.$(echo "$flavor" | tr '[:upper:]' '[:lower:]')"
  selected "$id" || continue
  if [ "$QUICK" -eq 1 ]; then record "$id" skip "skipped (--quick)"; continue; fi
  dir="app/build/test-results/test${flavor}DebugUnitTest"
  if gradle_run ":app:test${flavor}DebugUnitTest"; then
    fails=$(test_failures "$dir"); total=$(test_count "$dir")
    if [ "$fails" = "missing" ]; then
      record "$id" red "no XML results produced" "check ${dir}"
    elif [ "$fails" -eq 0 ] && [ "$total" -gt 0 ]; then
      record "$id" green "${total} tests, 0 failures"
    else
      record "$id" red "${total} tests, ${fails} failing" "./gradlew :app:test${flavor}DebugUnitTest"
    fi
  else
    record "$id" red "gradle task failed" "read $GRADLE_LOG"
  fi
done

# ---- 5..6 assemble -----------------------------------------------------------
if [ "$QUICK" -eq 0 ]; then
  for flavor in play fortress; do
    id="assemble.$flavor"
    selected "$id" || continue
    Cap="$(tr '[:lower:]' '[:upper:]' <<<"${flavor:0:1}")${flavor:1}"
    apk="app/build/outputs/apk/$flavor/debug/app-$flavor-debug.apk"
    if gradle_run ":app:assemble${Cap}Debug" && [ -f "$apk" ]; then
      record "$id" green "$(du -h "$apk" | cut -f1) apk"
    else
      record "$id" red "no apk produced" "read $GRADLE_LOG"
    fi
  done
else
  selected assemble.play && record "assemble.play" skip "skipped (--quick)"
  selected assemble.fortress && record "assemble.fortress" skip "skipped (--quick)"
fi

# ---- 7 pm3 fixtures ----------------------------------------------------------
if selected pm3; then
  if [ -x scripts/test-pm3.sh ]; then
    if scripts/test-pm3.sh >/dev/null 2>&1; then
      record "pm3.fixtures" green "all pm3 fixture tests pass"
    else
      record "pm3.fixtures" red "fixture failure" "run scripts/test-pm3.sh to see which"
    fi
  else
    record "pm3.fixtures" red "scripts/test-pm3.sh missing or not executable" "restore the script"
  fi
fi

# ---- 8..9 Play boundary ------------------------------------------------------
if selected boundary.source; then
  if scripts/check-play-boundary.sh source --json >/dev/null 2>&1; then
    record "boundary.source" green "no Fortress/private code in the Play source graph"
  else
    record "boundary.source" red "Play source boundary violated" "scripts/check-play-boundary.sh source --json"
  fi
fi
if selected boundary.artifact; then
  play_apk="app/build/outputs/apk/play/debug/app-play-debug.apk"
  if [ ! -f "$play_apk" ]; then
    record "boundary.artifact" skip "no Play apk built yet"
  elif scripts/check-play-boundary.sh all --artifact "$play_apk" --json >/dev/null 2>&1; then
    record "boundary.artifact" green "Play apk carries no Fortress implementation"
  else
    record "boundary.artifact" red "Play artifact boundary violated" "scripts/check-play-boundary.sh all --artifact $play_apk --json"
  fi
fi

# ---- 10 private endpoints stay out of Play -----------------------------------
# One command proves BOTH halves: the seeded estate endpoints are compiled into
# Fortress (so the remote feature is really there) and absent from Play.
if selected boundary.endpoints; then
  PRIVATE_RE='thinkcenter|interserve|100\.66\.109|100\.114\.165'
  count_hosts() {
    [ -f "$1" ] || { echo -1; return; }
    unzip -p "$1" 'classes*.dex' 2>/dev/null | strings | grep -cE "$PRIVATE_RE" || true
  }
  p=$(count_hosts app/build/outputs/apk/play/debug/app-play-debug.apk)
  f=$(count_hosts app/build/outputs/apk/fortress/debug/app-fortress-debug.apk)
  if [ "$p" -lt 0 ] || [ "$f" -lt 0 ]; then
    record "boundary.endpoints" skip "apks not built yet"
  elif [ "$p" -eq 0 ] && [ "$f" -gt 0 ]; then
    record "boundary.endpoints" green "play=0 fortress=$f private host strings"
  elif [ "$p" -gt 0 ]; then
    record "boundary.endpoints" red "play apk leaks $p private host string(s)" \
      "private endpoints must never compile into Play; check DistributionCapabilities + build.gradle.kts REMOTE_HOSTS"
  else
    record "boundary.endpoints" red "fortress apk has no seeded hosts (expected >0)" \
      "set phosphor.remoteHosts in local.properties or PHOSPHOR_REMOTE_HOSTS"
  fi
fi

# ---- 11 protected note untouched (hash only, never read) ---------------------
if selected protected; then
  if [ ! -f "$PROTECTED_NOTE" ]; then
    record "protected.note" skip "protected note not present"
  else
    actual=$(sha256sum "$PROTECTED_NOTE" | cut -d' ' -f1)
    tracked=$(git ls-files --error-unmatch "$PROTECTED_NOTE" 2>/dev/null || true)
    if [ -n "$tracked" ]; then
      record "protected.note" red "protected note became git-tracked" "git rm --cached '$PROTECTED_NOTE'"
    elif [ -f "$PROTECTED_BASELINE_FILE" ]; then
      expected=$(cut -d' ' -f1 <"$PROTECTED_BASELINE_FILE")
      if [ "$actual" = "$expected" ]; then
        record "protected.note" green "untracked, hash unchanged"
      else
        record "protected.note" red "protected note CHANGED" "restore it; this file is user-owned and must never be edited"
      fi
    else
      mkdir -p "$(dirname "$PROTECTED_BASELINE_FILE")"
      printf '%s  %s\n' "$actual" "$PROTECTED_NOTE" >"$PROTECTED_BASELINE_FILE"
      record "protected.note" green "untracked, baseline hash recorded"
    fi
  fi
fi

# ---- 12 no Python ------------------------------------------------------------
# The repo forbids Python. Note scripts/test-phase-05b1-boundary.sh legitimately
# contains python3 strings as NEGATIVE test fixtures - it exists to prove the
# boundary scanner catches python. Detecting that as a violation would be the
# detector failing to distinguish use from mention, so it is excluded by name.
if selected nopython; then
  py_tracked=$(git ls-files '*.py' | head -5)
  py_called=$(grep -rlE '(^|[^a-zA-Z_])python[23]?[ "'"'"']' \
    --exclude='test-phase-05b1-boundary.sh' --exclude='ship-check.sh' \
    scripts/ app/build.gradle.kts build.gradle.kts 2>/dev/null | head -5 || true)
  if [ -z "$py_tracked" ] && [ -z "$py_called" ]; then
    record "nopython" green "no tracked .py and no python invocation"
  else
    record "nopython" red "python present: ${py_tracked}${py_called}" "this project forbids Python; use Bash or Kotlin"
  fi
fi

# ---- 13 anti-stub sweep over changed files -----------------------------------
# Ben's explicit ask: work must not be reported done while it is really a stub.
# The scanner excludes itself - a tool that names the markers it hunts would
# otherwise always flag itself, which would train everyone to ignore the gate.
if selected antistub; then
  changed=$(git status --porcelain | awk '{print $NF}' \
    | grep -E '\.(kt|kts|rs|sh)$' | grep -v 'scripts/ship-check.sh' || true)
  hits=""
  if [ -n "$changed" ]; then
    while IFS= read -r f; do
      [ -f "$f" ] || continue
      m=$(grep -nE '(TODO|FIXME|XXX)([^A-Za-z]|$)|not implemented|NotImplementedError' "$f" 2>/dev/null | head -3 || true)
      [ -n "$m" ] && hits="${hits}${f}: ${m}\n"
    done <<<"$changed"
  fi
  if [ -z "$hits" ]; then
    record "antistub" green "no TODO/FIXME/stub markers in changed code"
  else
    record "antistub" red "stub markers found" "finish the work or record it in docs/plans/V2-FEATURE-LEDGER.md with a gate"
  fi
fi

# ---- verdict -----------------------------------------------------------------
green=0; red=0; skipped=0
for s in "${GATE_STATES[@]}"; do
  case "$s" in green) green=$((green+1)) ;; red) red=$((red+1)) ;; *) skipped=$((skipped+1)) ;; esac
done
total=$((green + red))

if [ "$JSON" -eq 1 ]; then
  printf '{"status":"%s","tool":"%s","version":"%s","ts":"%s","data":{"gates_green":%d,"gates_total":%d,"gates_skipped":%d,"gates":[' \
    "$([ "$red" -eq 0 ] && echo ok || echo error)" "$TOOL" "$VERSION" "$(ts)" "$green" "$total" "$skipped"
  for i in "${!GATE_IDS[@]}"; do
    [ "$i" -gt 0 ] && printf ','
    printf '{"id":"%s","state":"%s","detail":"%s"' \
      "${GATE_IDS[$i]}" "${GATE_STATES[$i]}" "$(printf '%s' "${GATE_DETAILS[$i]}" | sed 's/"/\\"/g')"
    [ -n "${GATE_FIXES[$i]}" ] && printf ',"fix":"%s"' "$(printf '%s' "${GATE_FIXES[$i]}" | sed 's/"/\\"/g')"
    printf '}'
  done
  printf ']}}\n'
else
  printf '\n  %d/%d gates green' "$green" "$total" >&2
  [ "$skipped" -gt 0 ] && printf ' (%d skipped)' "$skipped" >&2
  printf '\n' >&2
fi

[ "$red" -eq 0 ] && exit 0 || exit 2
