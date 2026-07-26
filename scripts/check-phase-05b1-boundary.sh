#!/usr/bin/env bash
# check-phase-05b1-boundary.sh — prove the Phase 05b-1 checkpoint stays observation-only.
set -euo pipefail

TOOL="check-phase-05b1-boundary"
VERSION="2.0.0"
BASELINE="36c9d43de6241af5284aaa45cfe538b6a6960775"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-schema}"
PLAY_ARTIFACT="$REPO/app/build/outputs/bundle/playRelease/app-play-release.aab"
ARTIFACT_OPTION_SET=false
DESKTOP_ADAPTER="${PHOSPHOR_DESKTOP_ADAPTER:-/home/ben/Dev/ClaudeWorkspace/nexus-mobile/relay/src/phosphor.rs}"
DESKTOP_ADAPTER_SHA256="66569383773d747ecffdcfe596a4a21b414bbd045e777a9d5e85ece045c91104"

now() { date +%Y-%m-%dT%H:%M:%S%:z; }
escape() {
  local value="$1" code octal control encoded
  value=${value//\\/\\\\}
  value=${value//\"/\\\"}
  # argv cannot contain NUL. Encode every other JSON control character so the
  # dependency-error path remains valid JSON even before jq is available.
  for ((code = 1; code <= 31; code++)); do
    printf -v octal '%03o' "$code"
    printf -v control '%b' "\\$octal"
    printf -v encoded '\\u%04x' "$code"
    value=${value//"$control"/"$encoded"}
  done
  printf '%s' "$value"
}
fail() {
  local code="$1" error="$2" message="$3" fix="$4"
  printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"%s","message":"%s","fix":"%s"}\n' \
    "$TOOL" "$VERSION" "$(now)" "$(escape "$error")" "$(escape "$message")" "$(escape "$fix")"
  exit "$code"
}
ok() {
  local data="$1"
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","data":%s}\n' \
    "$TOOL" "$VERSION" "$(now)" "$data"
}
need() {
  command -v "$1" >/dev/null 2>&1 || fail 2 dependency_unavailable "$1 is unavailable" "install $1 and retry"
}

if [ "$MODE" = "--json" ]; then
  shift
  MODE="${1:-schema}"
fi
shift $(( $# > 0 ? 1 : 0 )) || true
while [ $# -gt 0 ]; do
  case "$1" in
    --artifact)
      shift
      [ $# -gt 0 ] || fail 3 bad_input "--artifact needs a path" "pass --artifact <play.apk|play.aab>"
      PLAY_ARTIFACT="$1"
      ARTIFACT_OPTION_SET=true
      ;;
    --json) ;;
    *) fail 3 bad_input "unknown argument '$1'" "use schema, source, artifact, or all; optional --artifact PATH" ;;
  esac
  shift
done

# The JSON Schema keyword is intentionally literal inside this single-quoted document.
# shellcheck disable=SC2016
schema_data='{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "$id":"phosphor://schemas/check-phase-05b1-boundary/2.0.0",
  "title":"Phase 05b-1 observation-only boundary gate",
  "description":"Strict command, output, error, and exit contract for the internal Phase 05b-1 source and Play-artifact release gate.",
  "type":"object",
  "additionalProperties":false,
  "required":["usage","verbs","exits","always_json"],
  "properties":{
    "usage":{"const":"check-phase-05b1-boundary.sh [schema|source|artifact|all] [--artifact PATH] [--json]"},
    "always_json":{"const":true},
    "verbs":{
      "type":"object",
      "additionalProperties":false,
      "required":["schema","source","artifact","all"],
      "properties":{
        "schema":{"$ref":"#/$defs/schemaVerb"},
        "source":{"$ref":"#/$defs/sourceVerb"},
        "artifact":{"$ref":"#/$defs/artifactVerb"},
        "all":{"$ref":"#/$defs/allVerb"}
      }
    },
    "exits":{"type":"object","additionalProperties":false,"required":["0","2","3","4"],"properties":{"0":{"const":"success"},"2":{"const":"required dependency, baseline, or artifact unavailable"},"3":{"const":"bad input or unknown verb"},"4":{"const":"boundary violation or scanner runtime failure"}}}
  },
  "$defs":{
    "timestamp":{"type":"string","format":"date-time","minLength":1},
    "emptyOptions":{"type":"array","const":[]},
    "artifactOptions":{"type":"array","const":["--artifact PATH","--json"]},
    "schemaVerb":{"type":"object","additionalProperties":false,"required":["command","description","options","success","errors","exits"],"properties":{"command":{"const":"check-phase-05b1-boundary.sh schema"},"description":{"const":"Return this complete contract at data.schema."},"options":{"type":"array","const":["--json"]},"success":{"$ref":"#/$defs/schemaEnvelope"},"errors":{"$ref":"#/$defs/errorEnvelope"},"exits":{"const":[0,2,3]}}},
    "sourceVerb":{"type":"object","additionalProperties":false,"required":["command","description","options","success","errors","exits"],"properties":{"command":{"const":"check-phase-05b1-boundary.sh source"},"description":{"const":"Check the exact Phase 05a diff allowlist, prohibited paths, activation symbols, Python absence, Play source separation, inert pm3 verb, and desktop-adapter identity."},"options":{"type":"array","const":["--json"]},"success":{"$ref":"#/$defs/resultEnvelope"},"errors":{"$ref":"#/$defs/errorEnvelope"},"exits":{"const":[0,2,3,4]}}},
    "artifactVerb":{"type":"object","additionalProperties":false,"required":["command","description","options","success","errors","exits"],"properties":{"command":{"const":"check-phase-05b1-boundary.sh artifact [--artifact PATH]"},"description":{"const":"Verify and check the signed Play release AAB for the existing Play boundary, Python material, and Fortress Nexus observation classes."},"options":{"$ref":"#/$defs/artifactOptions"},"success":{"$ref":"#/$defs/resultEnvelope"},"errors":{"$ref":"#/$defs/errorEnvelope"},"exits":{"const":[0,2,3,4]}}},
    "allVerb":{"type":"object","additionalProperties":false,"required":["command","description","options","success","errors","exits"],"properties":{"command":{"const":"check-phase-05b1-boundary.sh all [--artifact PATH]"},"description":{"const":"Run source and artifact checks in one invocation."},"options":{"$ref":"#/$defs/artifactOptions"},"success":{"$ref":"#/$defs/resultEnvelope"},"errors":{"$ref":"#/$defs/errorEnvelope"},"exits":{"const":[0,2,3,4]}}},
    "resultData":{"type":"object","additionalProperties":false,"required":["baseline","source_checks","artifact_checks","artifact","observation_only","python_free"],"properties":{"baseline":{"const":"36c9d43de6241af5284aaa45cfe538b6a6960775"},"source_checks":{"type":"integer","minimum":0},"artifact_checks":{"type":"integer","minimum":0},"artifact":{"type":"string"},"observation_only":{"const":true},"python_free":{"const":true}}},
    "schemaData":{"type":"object","additionalProperties":false,"required":["schema"],"properties":{"schema":{"$ref":"#"}}},
    "resultEnvelope":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","data"],"properties":{"status":{"const":"ok"},"tool":{"const":"check-phase-05b1-boundary"},"version":{"const":"2.0.0"},"ts":{"$ref":"#/$defs/timestamp"},"data":{"$ref":"#/$defs/resultData"}}},
    "schemaEnvelope":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","data"],"properties":{"status":{"const":"ok"},"tool":{"const":"check-phase-05b1-boundary"},"version":{"const":"2.0.0"},"ts":{"$ref":"#/$defs/timestamp"},"data":{"$ref":"#/$defs/schemaData"}}},
    "errorEnvelope":{"type":"object","additionalProperties":false,"required":["status","tool","version","ts","error","message","fix"],"properties":{"status":{"const":"error"},"tool":{"const":"check-phase-05b1-boundary"},"version":{"const":"2.0.0"},"ts":{"$ref":"#/$defs/timestamp"},"error":{"type":"string","minLength":1},"message":{"type":"string","minLength":1},"fix":{"type":"string","minLength":1}}}
  }
}'

case "$MODE" in
  schema|source|artifact|all) ;;
  *) fail 3 bad_input "unknown verb '$MODE'" "use schema, source, artifact, or all" ;;
esac
if [ "$ARTIFACT_OPTION_SET" = true ] && [ "$MODE" != artifact ] && [ "$MODE" != all ]; then
  fail 3 bad_input "--artifact is not valid for $MODE" "use --artifact only with artifact or all"
fi

need jq
if [ "$MODE" = schema ]; then
  ok "$(jq -cn --argjson schema "$schema_data" '{schema:$schema}')"
  exit 0
fi

need git
need grep
need sha256sum
need sort
need comm
need sed

SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
WORK="$(mktemp -d "$SCRATCH_ROOT/phosphor-phase05b1-boundary.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
SOURCE_CHECKS=0
ARTIFACT_CHECKS=0

check_source() {
  cd "$REPO"
  git cat-file -e "$BASELINE^{commit}" 2>/dev/null || \
    fail 2 baseline_unavailable "Phase 05a baseline $BASELINE is unavailable" "fetch checkpoint/phosphor-2.0.0-phase-05a and retry"

  cat > "$WORK/allowed-paths.txt" <<'PATHS'
HANDOFF.md
app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusContracts.kt
app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt
app/src/main/kotlin/dev/phosphor/mobil3/store/PhosphorStateStore.kt
app/src/test/kotlin/dev/phosphor/mobil3/distribution/DistributionCapabilitiesTest.kt
app/src/test/kotlin/dev/phosphor/mobil3/store/PhosphorStateStoreTest.kt
app/src/testFortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcherTest.kt
decisions/2026-07-26-phase-05b1-observation-only.md
docs/ASKS.md
docs/dev/PHOSPHOR-2.0-RELEASE-EXECUTION.md
docs/dev/REQUIREMENT-TRACEABILITY.md
docs/dev/receipts/phosphor-2.0/phase-05b1-observation-only.md
scripts/check-phase-05b1-boundary.sh
scripts/test-phase-05b1-boundary.sh
PATHS
  sort -o "$WORK/allowed-paths.txt" "$WORK/allowed-paths.txt"
  {
    git diff --name-only "$BASELINE" -- .
    git ls-files --others --exclude-standard
  } | grep -vxF 'Phosphor build.md' | sort -u > "$WORK/changed-paths.txt"
  comm -23 "$WORK/changed-paths.txt" "$WORK/allowed-paths.txt" > "$WORK/unexpected-paths.txt"
  if [ -s "$WORK/unexpected-paths.txt" ]; then
    fail 4 unexpected_change "Phase 05b-1 changes paths outside its ratified slice: $(paste -sd, "$WORK/unexpected-paths.txt")" "revert or move the named paths to a later checkpoint"
  fi
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))

  local required
  for required in \
    app/src/main/kotlin/dev/phosphor/mobil3/store/PhosphorStateStore.kt \
    app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt \
    app/src/testFortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcherTest.kt \
    decisions/2026-07-26-phase-05b1-observation-only.md; do
    grep -qxF "$required" "$WORK/changed-paths.txt" || \
      fail 4 required_change_missing "Required Phase 05b-1 path is absent: $required" "restore the ratified observation-only implementation"
  done
  SOURCE_CHECKS=$((SOURCE_CHECKS + 4))

  local unchanged_paths=(
    app/build.gradle.kts build.gradle.kts settings.gradle.kts gradle.properties gradle
    app/src/main/AndroidManifest.xml app/src/play/AndroidManifest.xml app/src/fortress/AndroidManifest.xml
    app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt
    app/src/main/kotlin/dev/phosphor/mobil3/store/DisplayHudReducer.kt
    app/src/main/kotlin/dev/phosphor/mobil3/settings/HudCausalEnvelopeCodec.kt
    app/src/main/kotlin/dev/phosphor/mobil3/ui
    app/src/main/aidl app/src/main/kotlin/dev/phosphor/mobil3/service
    app/src/main/cpp app/src/main/jni
    relay dev/pm3
  )
  if ! git diff --quiet "$BASELINE" -- "${unchanged_paths[@]}"; then
    git diff --name-only "$BASELINE" -- "${unchanged_paths[@]}" > "$WORK/prohibited-changes.txt"
    fail 4 activation_boundary_changed "Prohibited activation paths changed: $(paste -sd, "$WORK/prohibited-changes.txt")" "revert these paths; Phase 05b-1 is observation-only"
  fi
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))

  git diff -U0 "$BASELINE" -- \
    app/src/main/kotlin/dev/phosphor/mobil3/store/PhosphorStateStore.kt \
    app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusContracts.kt \
    app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt \
    | grep '^+' | grep -v '^+++' > "$WORK/added-production.txt" || true
  # git diff does not include a new untracked file before the checkpoint commit.
  # Scan the entire new dispatcher as well, so the pre-commit gate cannot miss activation code.
  cat app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt \
    >> "$WORK/added-production.txt"
  local activation_regex='(^|[^A-Za-z])(android[.]|androidx[.]|IBinder|Binder[.]|ServiceConnection|ServerSocket|DatagramSocket|Socket[(]|java[.]net|java[.]nio[.]channels[.](DatagramChannel|SocketChannel|ServerSocketChannel|AsynchronousSocketChannel|AsynchronousServerSocketChannel)|DatagramChannel[.]open|SocketChannel[.]open|ServerSocketChannel[.]open|AsynchronousSocketChannel[.]open|AsynchronousServerSocketChannel[.]open|io[.]ktor|okhttp3|MainActivity|store[.]dispatch[(]|SetDisplayHud|DisplayHudReducer|HudCausalEnvelopeCodec|System[.]loadLibrary|external[[:space:]]+fun|native[[:space:]])'
  if grep -En "$activation_regex" "$WORK/added-production.txt" > "$WORK/activation-hits.txt"; then
    fail 4 runtime_activation_detected "New production lines contain runtime activation symbols: $(paste -sd';' "$WORK/activation-hits.txt")" "remove the activation code and retain pure observation contracts only"
  fi
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))

  if [ -n "$(git ls-files '*.py')" ]; then
    fail 4 python_dependency_detected "Tracked Python files exist in the project" "remove Python from the self-contained Phosphor project"
  fi
  if grep -RInEi --include='*.kt' --include='*.kts' '(^|[^A-Za-z0-9_])(chaquopy|libpython|org[.]python)([^A-Za-z0-9_]|$)' \
      app/src/main app/src/play app/src/fortress app/build.gradle.kts build.gradle.kts settings.gradle.kts gradle \
      > "$WORK/python-hits.txt"; then
    fail 4 python_dependency_detected "Runtime/build source references Python: $(paste -sd';' "$WORK/python-hits.txt")" "remove the Python dependency or invocation"
  fi
  local command_prefix='(command[[:space:]]+(--[[:space:]]+)?|exec[[:space:]]+(--[[:space:]]+)?|env[[:space:]]+(--[[:space:]]+)?([^[:space:]=]+=[^[:space:]]+[[:space:]]+)*)?'
  local python_command_regex="(^|[;&|][[:space:]]+|[\$][(][[:space:]]*)${command_prefix}([^[:space:]]*/)?(python([0-9.]*)?|pypy([0-9.]*)?)([[:space:]]|\$)"
  local python_quoted_command_regex="(^|[;&|][[:space:]]+|[\$][(][[:space:]]*)${command_prefix}[\"']([^\"']*/)?(python([0-9.]*)?|pypy([0-9.]*)?)[\"']([[:space:]]|\$)"
  : > "$WORK/python-command-hits.txt"
  local script_file
  while IFS= read -r -d '' script_file; do
    # Shell removes a backslash-newline pair before tokenization. Mirror that
    # normalization so a continued interpreter path cannot evade the scan.
    if sed ':join; /\\$/ { N; s/\\\n//; b join; }' "$script_file" \
        | grep -nE "($python_command_regex)|($python_quoted_command_regex)" \
        | sed "s#^#$script_file:#" >> "$WORK/python-command-hits.txt"; then
      :
    fi
  done < <(find scripts dev -type f -name '*.sh' -print0)
  if [ -s "$WORK/python-command-hits.txt" ]; then
    fail 4 python_dependency_detected "Project scripts invoke Python: $(paste -sd';' "$WORK/python-command-hits.txt")" "replace the Python invocation with self-contained Bash or project-native tooling"
  fi
  SOURCE_CHECKS=$((SOURCE_CHECKS + 2))

  if find app/src/main app/src/play -type f \( -name '*.kt' -o -name '*.java' -o -name '*.aidl' \) -print0 \
      | xargs -0 grep -IlE 'NexusObservationDispatcher|NexusObservationResult' > "$WORK/play-source-observation.txt"; then
    if [ -s "$WORK/play-source-observation.txt" ]; then
      fail 4 play_source_leak "Play/main source references Fortress observation classes" "move all Nexus observation authority to app/src/fortress"
    fi
  fi
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))

  local play_gate
  play_gate=$(cd "$REPO" && "$REPO/scripts/check-play-boundary.sh" source --json) || \
    fail 4 play_source_gate_failed "Play source boundary failed: $play_gate" "repair the Play boundary and retry"
  printf '%s' "$play_gate" | jq -e \
    '.status == "ok" and .tool == "check-play-boundary" and .version == "2.0.0" and .data.source_checks > 0' \
    >/dev/null || fail 4 play_source_gate_failed "Play source boundary returned an invalid result" "repair the canonical Play boundary helper and retry"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))

  local pm3_output pm3_status
  set +e
  pm3_output=$(dev/pm3 --json nexus-status 2>/dev/null)
  pm3_status=$?
  set -e
  [ "$pm3_status" -eq 2 ] || fail 4 pm3_activation_detected "pm3 nexus-status exit was $pm3_status, expected inert exit 2" "restore the side-effect-free Phase 05 unavailable stub"
  if [ "$(printf '%s' "$pm3_output" | jq -r '.status')" != error ] || \
      [ "$(printf '%s' "$pm3_output" | jq -r '.error')" != phase_unavailable ]; then
    fail 4 pm3_activation_detected "pm3 nexus-status no longer reports phase_unavailable" "restore the inert Phase 05 stub"
  fi
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))

  [ -f "$DESKTOP_ADAPTER" ] || \
    fail 2 desktop_adapter_unavailable "Desktop Nexus adapter is unavailable at $DESKTOP_ADAPTER" "restore the Phase 05a desktop adapter path or pass PHOSPHOR_DESKTOP_ADAPTER"
  local actual_desktop_sha
  actual_desktop_sha=$(sha256sum "$DESKTOP_ADAPTER" | awk '{print $1}')
  [ "$actual_desktop_sha" = "$DESKTOP_ADAPTER_SHA256" ] || \
    fail 4 desktop_adapter_changed "Desktop Nexus adapter hash changed to $actual_desktop_sha" "restore the Phase 05a desktop adapter before sealing Phase 05b-1"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
}

check_artifact() {
  need unzip
  need strings
  need jarsigner
  [ -f "$PLAY_ARTIFACT" ] || \
    fail 2 artifact_unavailable "Play artifact not found at $PLAY_ARTIFACT" "build :app:bundlePlayRelease with explicit release signing inputs"
  case "$PLAY_ARTIFACT" in
    *.aab) ;;
    *) fail 3 bad_input "Phase 05b-1 artifact evidence must be a signed Play AAB" "pass --artifact app/build/outputs/bundle/playRelease/app-play-release.aab" ;;
  esac
  if ! jarsigner -verify "$PLAY_ARTIFACT" > "$WORK/play-aab-signature.txt" 2>&1 || \
      ! grep -q '^jar verified[.]$' "$WORK/play-aab-signature.txt"; then
    fail 4 artifact_signature_invalid "Play AAB signature verification failed" "rebuild the signed Play release bundle and retry"
  fi

  local play_gate
  play_gate=$(cd "$REPO" && "$REPO/scripts/check-play-boundary.sh" artifact --artifact "$PLAY_ARTIFACT" --json) || \
    fail 4 play_artifact_gate_failed "Play artifact boundary failed: $play_gate" "repair the Play artifact boundary and rebuild"
  printf '%s' "$play_gate" | jq -e \
    '.status == "ok" and .tool == "check-play-boundary" and .version == "2.0.0" and .data.artifact_checks > 0' \
    >/dev/null || fail 4 play_artifact_gate_failed "Play artifact boundary returned an invalid result" "repair the canonical Play boundary helper and rebuild"
  ARTIFACT_CHECKS=$((ARTIFACT_CHECKS + 1))

  unzip -Z1 "$PLAY_ARTIFACT" > "$WORK/play-archive-list.txt" || \
    fail 4 archive_inspection_failed "Could not list the Play artifact" "provide a valid APK or AAB"
  if grep -Ei '(^|/)(python([0-9.]*)?|libpython[^/]*|site-packages|chaquopy)(/|$)|python[^/]*[.](zip|pyz)$|[.](py|pyc|pyo|pyz)$' "$WORK/play-archive-list.txt" > "$WORK/artifact-python.txt"; then
    fail 4 python_artifact_detected "Play artifact contains Python material: $(paste -sd, "$WORK/artifact-python.txt")" "remove Python packaging from the Android artifact"
  fi
  unzip -p "$PLAY_ARTIFACT" | strings > "$WORK/play-archive-strings.txt"
  if grep -Ei 'Py_Initialize|PyRun_|libpython|site-packages|chaquopy|python[^[:space:]/]*[.](zip|pyz)' \
      "$WORK/play-archive-strings.txt" > "$WORK/artifact-python-strings.txt"; then
    fail 4 python_artifact_detected "Play artifact payload contains Python runtime material" "remove the embedded Python runtime and rebuild"
  fi
  ARTIFACT_CHECKS=$((ARTIFACT_CHECKS + 1))

  mkdir -p "$WORK/play-artifact"
  unzip -qq "$PLAY_ARTIFACT" 'base/dex/*' 'classes*.dex' -d "$WORK/play-artifact" 2>/dev/null || true
  find "$WORK/play-artifact" -type f -print0 | xargs -0 -r strings > "$WORK/play-dex-strings.txt"
  if grep -E 'NexusObservationDispatcher|NexusObservationResult' "$WORK/play-dex-strings.txt" > "$WORK/play-observation-classes.txt"; then
    fail 4 play_class_leak "Play artifact contains Fortress Nexus observation classes" "restore source-set separation and rebuild the Play artifact"
  fi
  ARTIFACT_CHECKS=$((ARTIFACT_CHECKS + 1))
}

case "$MODE" in
  source) check_source ;;
  artifact) check_artifact ;;
  all) check_source; check_artifact ;;
esac

ok "$(jq -cn \
  --arg baseline "$BASELINE" \
  --arg artifact "$PLAY_ARTIFACT" \
  --argjson source_checks "$SOURCE_CHECKS" \
  --argjson artifact_checks "$ARTIFACT_CHECKS" \
  '{baseline:$baseline,source_checks:$source_checks,artifact_checks:$artifact_checks,artifact:$artifact,observation_only:true,python_free:true}')"
