#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PM3="$ROOT/dev/pm3"
TMP="${JCODE_SCRATCH_DIR:-/tmp}/pm3-test-$$"
BIN="$TMP/bin"
LOG="$TMP/calls.log"
BLOCKERS="$TMP/blockers.log"
SCHEMA_OUT=""
ORIGINALS="$TMP/originals"
mkdir -p "$BIN" "$ORIGINALS"
: >"$LOG"
: >"$BLOCKERS"

REPO_ARTIFACTS=(
  "$ROOT/app/build/outputs/apk/play/debug/app-play-debug.apk"
  "$ROOT/app/build/outputs/apk/play/release/app-play-release.apk"
  "$ROOT/app/build/outputs/apk/fortress/debug/app-fortress-debug.apk"
  "$ROOT/app/build/outputs/apk/fortress/release/app-fortress-release.apk"
  "$ROOT/app/build/reports/pm3/build-play-debug.log"
  "$ROOT/app/build/reports/pm3/build-play-release.log"
  "$ROOT/app/build/reports/pm3/build-fortress-debug.log"
  "$ROOT/app/build/reports/pm3/build-fortress-release.log"
)
ORIGINAL_PRESENT=()

preserve_repo_artifacts() {
  local index path
  for index in "${!REPO_ARTIFACTS[@]}"; do
    path="${REPO_ARTIFACTS[$index]}"
    if [ -e "$path" ]; then
      [ -f "$path" ] || fail "refusing to replace non-file fixture target: $path"
      ORIGINAL_PRESENT[index]=1
      cp -a -- "$path" "$ORIGINALS/$index"
    else
      ORIGINAL_PRESENT[index]=0
    fi
  done
}

restore_repo_artifacts() {
  local index path
  for index in "${!REPO_ARTIFACTS[@]}"; do
    path="${REPO_ARTIFACTS[$index]}"
    rm -f -- "$path"
    if [ "${ORIGINAL_PRESENT[$index]:-0}" -eq 1 ]; then
      mkdir -p "$(dirname "$path")"
      cp -a -- "$ORIGINALS/$index" "$path"
    fi
  done
}

assert_repo_artifacts_restored() {
  local index path
  for index in "${!REPO_ARTIFACTS[@]}"; do
    path="${REPO_ARTIFACTS[$index]}"
    if [ "${ORIGINAL_PRESENT[$index]:-0}" -eq 1 ]; then
      [ -f "$path" ] || fail "pre-existing artifact was not restored: $path"
      cmp -s -- "$ORIGINALS/$index" "$path" || fail "pre-existing artifact changed: $path"
    else
      [ ! -e "$path" ] || fail "fixture artifact survived cleanup: $path"
    fi
  done
}
cleanup_all() {
  restore_repo_artifacts
  rm -rf "$TMP"
}
trap cleanup_all EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "ok - $*"; }
blocker() { printf '%s\n' "$*" >>"$BLOCKERS"; echo "BLOCKER: $*" >&2; }
run_check() {
  local label="$1"
  shift
  if "$@"; then pass "$label"; else blocker "$label"; fi
}
run_capture() {
  set +e
  OUT="$($PM3 "$@" 2>"$TMP/err")"
  RC=$?
  ERR="$(cat "$TMP/err")"
  set -e
}
assert_rc() { [ "$RC" -eq "$1" ] || fail "exit $RC != $1 for $*; out=$OUT err=$ERR"; }
assert_one_line_json() {
  [ "$(printf '%s\n' "$OUT" | wc -l)" -eq 1 ] || fail "not one line: $OUT"
  printf '%s' "$OUT" | jq -e . >/dev/null || fail "invalid JSON: $OUT"
  printf '%s' "$OUT" | jq -e '
    def success_root: ["data","status","tool","ts","version"];
    def error_root: ["data","error","fix","message","status","tool","ts","version"];
    (.tool == "pm3") and (.version == "0.2.0")
    and (.ts | type == "string" and length > 0
      and test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-][0-9]{2}:[0-9]{2}$"))
    and (.data | type == "object")
    and if .status == "ok" then (keys | sort) == success_root
        elif .status == "error" then
          ((keys | sort) == error_root)
          and (.error | type == "string" and length > 0)
          and (.message | type == "string" and length > 0)
          and (.fix | type == "string" and length > 0)
        else false end
  ' >/dev/null || fail "bad exact one-shot envelope: $OUT"
}
assert_contract_shape() {
  local verb="$1" shape="$2" json="$3"
  [ -n "$SCHEMA_OUT" ] || fail "schema must be captured before contract shape assertions"
  printf '%s' "$json" | jq -e --argjson contract "$SCHEMA_OUT" --arg verb "$verb" --arg shape "$shape" '
    def deref($schema; $decl):
      if $decl["$ref"] == "#" then $schema
      elif $decl["$ref"] then $schema["$defs"][$decl["$ref"] | split("/")[-1]]
      else $decl end;
    def type_ok($schema; $value; $raw):
      (deref($schema; $raw)) as $decl
      | if $decl.const != null then $value == $decl.const
        elif $decl.type == "array" then ($value | type) == "array"
        elif $decl.type == "string" then ($value | type) == "string"
        elif $decl.type == "integer" then ($value | type) == "number" and (($value % 1) == 0)
        elif $decl.type == "boolean" then ($value | type) == "boolean"
        elif $decl.type == "object" then ($value | type) == "object"
        else false end;
    . as $actual
    | ($contract.data.schema) as $schema
    | ($schema.properties.verbs.properties[$verb].properties.success["$ref"] | split("/")[-1]) as $envelope_name
    | ($schema["$defs"][$envelope_name]) as $envelope
    | ($envelope.properties.data["$ref"] | split("/")[-1]) as $data_name
    | ($schema["$defs"][$data_name]) as $data_schema
    | ($shape == "output")
      and (($actual | keys | sort) == ($envelope.required | sort))
      and (($actual.data | keys | sort) == ($data_schema.required | sort))
      and all($data_schema.required[]; . as $key | type_ok($schema; $actual.data[$key]; $data_schema.properties[$key]))
  ' >/dev/null || fail "$verb $shape did not match its declared nested data contract: $json"
}
assert_logcat_event_shape() {
  local event="$1" json="$2"
  [ -n "$SCHEMA_OUT" ] || fail "schema must be captured before logcat event assertions"
  printf '%s' "$json" | jq -e --argjson contract "$SCHEMA_OUT" --arg event "$event" '
    def deref($schema; $decl):
      if $decl["$ref"] == "#" then $schema
      elif $decl["$ref"] then $schema["$defs"][$decl["$ref"] | split("/")[-1]]
      else $decl end;
    def type_ok($schema; $value; $raw):
      (deref($schema; $raw)) as $decl
      | if $decl.const != null then $value == $decl.const
        elif $decl.type == "string" then ($value | type) == "string"
        else false end;
    . as $actual
    | ($contract.data.schema) as $schema
    | (if $event == "logcat" then $schema["$defs"].logcatEvent else $schema["$defs"].streamError end) as $decl
    | (($actual | keys | sort) == ($decl.required | sort))
      and all($decl.required[]; . as $key | type_ok($schema; $actual[$key]; $decl.properties[$key]))
  ' >/dev/null || fail "logcat $event did not match declared contract: $json"
}
assert_json_error_has_fix() {
  assert_one_line_json
  printf '%s' "$OUT" | jq -e '.status == "error" and (.data | type) == "object"' >/dev/null ||
    fail "expected error one-shot with data object: $OUT"
  if [ -n "$SCHEMA_OUT" ]; then
    printf '%s' "$OUT" | jq -e --argjson contract "$SCHEMA_OUT" '
      .error as $error | ($contract.data.schema["$defs"].stableError.enum | index($error)) != null
    ' >/dev/null || fail "error code is not declared stable: $OUT"
  fi
}
assert_error_code() {
  printf '%s' "$OUT" | jq -e --arg code "$1" '.error == $code' >/dev/null || fail "error code != $1: $OUT"
}
assert_data_keys() {
  local expected="$1"
  printf '%s' "$OUT" | jq -e --argjson expected "$expected" '(.data | keys | sort) == ($expected | sort)' >/dev/null ||
    fail "data keys did not equal $expected: $OUT"
}
assert_contains() { printf '%s' "$1" | grep -Fq -- "$2" || fail "missing [$2] in [$1]"; }
assert_not_contains() { ! printf '%s' "$1" | grep -Fq -- "$2" || fail "unexpected [$2] in [$1]"; }
assert_no_file() { [ ! -e "$1" ] || fail "unexpected file exists: $1"; }
assert_log_count() { local n; n="$(grep -cF -- "$2" "$1" || true)"; [ "$n" -eq "$3" ] || fail "log count for [$2] was $n not $3 in $1"; }

preserve_repo_artifacts

cat >"$BIN/adb" <<'ADB'
#!/usr/bin/env bash
mode="${PM3_FAKE_ADB_MODE:-ok}"
printf 'adb %s\n' "$*" >>"$PM3_FIXTURE_LOG"
case "$mode" in
  unavailable) echo "fixture adb should not have been called in unavailable mode" >&2; exit 127 ;;
  version_fail) [ "${1:-}" = --version ] && { echo "fixture adb version failure" >&2; exit 8; } ;;
  connect_zero_bad) [ "${1:-}" = connect ] && { echo "failed to connect to ${2:-}: Connection refused"; exit 0; } ;;
  logcat_fail) if [ "${1:-}" = -s ] && [ "${3:-}" = logcat ]; then echo "logcat fixture failure" >&2; exit 9; fi ;;
  screencap_fail) if [ "${1:-}" = -s ] && [ "${3:-}" = exec-out ]; then printf 'PARTIALPNG'; exit 8; fi ;;
  pull_fail) if [ "${1:-}" = -s ] && [ "${3:-}" = pull ]; then printf 'PARTIALMP4' >"${5:-/dev/null}"; echo "pull failed" >&2; exit 8; fi ;;
  dumpsys_fail) if [ "${1:-}" = -s ] && [ "${3:-}" = shell ] && [[ " $* " == *" dumpsys "* ]]; then echo "fixture dumpsys failure" >&2; exit 8; fi ;;
esac
case "${1:-}" in
  --version) echo "Android Debug Bridge fixture" ;;
  devices) echo "List of devices attached"; echo "FIRSTDEVICE device" ;;
  pair) echo "Successfully paired to $2" ;;
  connect) echo "connected to $2" ;;
  -s)
    serial="$2"; shift 2
    case "${1:-}" in
      install) echo "Success" ;;
      shell)
        shift
        case "$*" in
          *"dumpsys package"*) echo "versionName=9.9.9" ;;
          *"am start"*) echo "Starting" ;;
          *"pidof"*) echo "1234" ;;
          *"dumpsys gfxinfo"*) echo "Total frames rendered: 12" ;;
          *"dumpsys media_session"*) echo "fixture media for $serial" ;;
          *"dumpsys audio"*) echo "fixture audio for $serial" ;;
          *"pm dump"*) echo "pkgFlags=[ DEBUGGABLE ]" ;;
          *"am broadcast"*) echo "Broadcast completed" ;;
          *"run-as"*"selftest.json"*) echo '{"ok":true}' ;;
          *"run-as"*"selftest.png"*) printf 'png' ;;
          *"screenrecord"*) echo recorded ;;
          *"rm /sdcard/pm3-rec.mp4"*) echo removed ;;
          *) echo "shell:$*" ;;
        esac ;;
      exec-out) printf 'PNGDATA' ;;
      pull) printf 'MP4DATA' >"$3"; echo pulled ;;
      logcat) echo "07-26 10:00:00.000 I phosphor-mobil3: hello"; echo "07-26 10:00:00.001 E AndroidRuntime: boom" ;;
      *) echo "fixture adb unknown $*" ;;
    esac ;;
  *) echo "fixture adb unknown $*" ;;
esac
ADB
chmod +x "$BIN/adb"

cat >"$BIN/gradle" <<'GRADLE'
#!/usr/bin/env bash
printf 'gradle %s\n' "$*" >>"$PM3_FIXTURE_LOG"
echo "pm3 fake gradle log: $*"
task="${1:-}"
case "$task" in
  assemblePlayDebug) path="app/build/outputs/apk/play/debug/app-play-debug.apk" ;;
  assemblePlayRelease) path="app/build/outputs/apk/play/release/app-play-release.apk" ;;
  assembleFortressDebug) path="app/build/outputs/apk/fortress/debug/app-fortress-debug.apk" ;;
  assembleFortressRelease) path="app/build/outputs/apk/fortress/release/app-fortress-release.apk" ;;
  *) echo "bad task $task" >&2; exit 7 ;;
esac
mkdir -p "$(dirname "$path")"
printf 'apk:%s' "$task" >"$path"
GRADLE
chmod +x "$BIN/gradle"

cat >"$BIN/keytool" <<'KEYTOOL'
#!/usr/bin/env bash
printf 'keytool %s\n' "$*" >>"$PM3_FIXTURE_LOG"
case "${PM3_FAKE_KEYTOOL_MODE:-good}" in
  good) printf 'fixture-play-certificate-der' ;;
  fail) echo "fixture keystore failure" >&2; exit 8 ;;
  *) echo "unknown keytool fixture mode" >&2; exit 9 ;;
esac
KEYTOOL
chmod +x "$BIN/keytool"

export PATH="$BIN:$PATH" PM3_FIXTURE_LOG="$LOG" PM3_GRADLE="$BIN/gradle" PM3_ADB="$BIN/adb" PM3_KEYTOOL="$BIN/keytool" PM3_FAKE_ADB_MODE=ok
unset PLAY_UPLOAD_STORE_FILE PLAY_UPLOAD_STORE_PASSWORD PLAY_UPLOAD_KEY_ALIAS PLAY_UPLOAD_KEY_PASSWORD PLAY_UPLOAD_CERT_SHA256
unset RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD RELEASE_CERT_SHA256

bash -n "$PM3"; pass "pm3 syntax"
bash -n "$0"; pass "test syntax"
if command -v shellcheck >/dev/null 2>&1; then
  shellcheck "$0"; pass "test shellcheck"
  shellcheck "$PM3"; pass "pm3 shellcheck"
else
  echo "skip - shellcheck unavailable" >&2
fi

run_capture --json help; assert_rc 0; assert_one_line_json; assert_data_keys '["usage","verbs"]'; pass "help exact nested JSON envelope"
run_capture --help; assert_rc 0; assert_one_line_json; assert_data_keys '["usage","verbs"]'; pass "help works under pipe auto-json"
run_capture --json wat; assert_rc 3; assert_json_error_has_fix; assert_error_code unknown_verb; assert_data_keys '["usage"]'; pass "unknown verb exit3 with stable error and usage data"
run_capture --json doctor --bogus; assert_rc 3; assert_json_error_has_fix; assert_error_code unknown_argument; assert_data_keys '["usage"]'; pass "unknown global arg exit3 with stable error and usage data"

run_capture --json schema; assert_rc 0; assert_one_line_json; assert_contains "$OUT" 'assemblePlayRelease'
printf '%s' "$OUT" | jq -e '
  ["help","schema","doctor","pair","connect","build","install","run","logcat","screenshot","record","media","fps","smoke","state-get","state-watch","nexus-status","nexus-grant","nexus-revoke","action-run","audit-list","audit-export"] as $verbs
  | ($verbs | sort) as $sorted
  | (.data.schema) as $schema
  | ((keys | sort) == ["data","status","tool","ts","version"])
    and ((.data | keys) == ["schema"])
    and ($schema["$schema"] == "https://json-schema.org/draft/2020-12/schema")
    and ($schema.type == "object")
    and ($schema.additionalProperties == false)
    and (($schema.properties.verbs.required | sort) == $sorted)
    and (($schema.properties.verbs.properties | keys) == $sorted)
    and ([$schema | .. | objects | select(.type? == "object") | .additionalProperties == false] | all)
    and ($schema["$defs"].schemaData.properties.schema["$ref"] == "#")
    and ($schema["$defs"].schemaEnvelope.properties.data["$ref"] == "#/$defs/schemaData")
    and ($schema["$defs"].errorEnvelopeEmpty.required == ["status","tool","version","ts","error","message","fix","data"])
    and ($schema["$defs"].errorEnvelopeEmpty.properties.data["$ref"] == "#/$defs/emptyData")
    and ($schema["$defs"].errorEnvelopeUsage.properties.data["$ref"] == "#/$defs/usageData")
    and ($schema["$defs"].streamError.required | index("fix"))
    and ($schema.properties.verbs.properties.help.properties.success["$ref"] == "#/$defs/helpEnvelope")
    and ($schema["$defs"].helpEnvelope.properties.data["$ref"] == "#/$defs/helpData")
    and (["help","schema","doctor","pair","connect","build","install","run","screenshot","record","media","fps","smoke"]
      | all(.[]; . as $verb
        | ($schema.properties.verbs.properties[$verb].properties.success["$ref"] | split("/")[-1]) as $envelope_name
        | ($schema["$defs"][$envelope_name]) as $envelope
        | ($envelope.properties.data["$ref"] | split("/")[-1]) as $data_name
        | ($schema["$defs"][$data_name]) as $data_schema
        | (($envelope.required | sort) == ["data","status","tool","ts","version"])
          and ($envelope.additionalProperties == false)
          and ($data_schema.type == "object")
          and ($data_schema.additionalProperties == false)
          and (($data_schema.required | sort) == ($data_schema.properties | keys | sort))))
    and ([$schema | .. | objects | .["$ref"]? | select(type == "string" and startswith("#/$defs/")) | split("/")[-1]]
      | all(.[]; . as $name | $schema["$defs"] | has($name)))
    and ($schema.properties.signing_inputs.properties.play_release.properties.required_env.const | index("PLAY_UPLOAD_CERT_SHA256"))
    and ($schema.properties.signing_inputs.properties.fortress_release.properties.expected_cert_sha256.const == "e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00")
    and (($schema.properties.exit_codes.properties | keys) == ["0","2","3","4"])
' >/dev/null || fail "schema is not recursively strict or its flat verb set drifted"
SCHEMA_OUT="$OUT"
assert_contract_shape schema output "$OUT"
pass "schema is enveloped at data.schema, recursively strict, self-referential, and exact"

run_capture --json help; assert_rc 0; assert_contract_shape help output "$OUT"; pass "help output matches declared contract"

check_missing_serial_logcat() {
  : >"$LOG"
  run_capture --json logcat
  assert_rc 3
  assert_json_error_has_fix
  assert_error_code serial_required
  assert_data_keys '["usage"]'
  assert_not_contains "$OUT" '"event"'
  assert_log_count "$LOG" "adb " 0
}
run_check "missing-serial logcat exactly one exit3 object and no adb call" check_missing_serial_logcat

check_missing_serial_precedes_missing_adb() {
  local save_adb="$PM3_ADB"
  PM3_ADB="$TMP/no-such-adb-command"
  run_capture --json logcat
  PM3_ADB="$save_adb"
  export PM3_ADB
  assert_rc 3
  assert_json_error_has_fix
  assert_error_code serial_required
  assert_data_keys '["usage"]'
}
run_check "missing serial remains usage exit3 when adb is unavailable" check_missing_serial_precedes_missing_adb

check_doctor_adb_unavailable() {
  local save_adb="$PM3_ADB"
  PM3_ADB="$TMP/no-such-adb-command"
  run_capture --json doctor
  PM3_ADB="$save_adb"
  export PM3_ADB
  assert_rc 2
  assert_json_error_has_fix
  assert_error_code prerequisites_unavailable
  assert_data_keys '["all_ok","distribution","profile","package_id","gradle_task","apk","checks"]'
  assert_contains "$OUT" '"check":"adb","ok":false'
}
run_check "doctor exits 2 when adb is unavailable" check_doctor_adb_unavailable

check_doctor_adb_runtime_failure() {
  PM3_FAKE_ADB_MODE=version_fail
  export PM3_FAKE_ADB_MODE
  run_capture --json doctor
  PM3_FAKE_ADB_MODE=ok
  export PM3_FAKE_ADB_MODE
  assert_rc 2
  assert_json_error_has_fix
  assert_error_code prerequisites_unavailable
  assert_data_keys '["all_ok","distribution","profile","package_id","gradle_task","apk","checks"]'
  assert_contains "$OUT" 'adb --version failed'
}
run_check "doctor treats executable-but-broken adb as unavailable" check_doctor_adb_runtime_failure

run_capture --json --distribution play --profile debug doctor; assert_rc 0; assert_contains "$OUT" '"package_id":"dev.phosphor.mobil3"'; assert_contains "$OUT" '"gradle_task":"assemblePlayDebug"'; assert_contains "$OUT" 'app/build/outputs/apk/play/debug/app-play-debug.apk'; assert_contract_shape doctor output "$OUT"; pass "play debug mapping"
run_capture --json --distribution fortress --profile release doctor; assert_rc 0; assert_contains "$OUT" '"package_id":"dev.phosphor.mobil3.fortress"'; assert_contains "$OUT" '"gradle_task":"assembleFortressRelease"'; assert_contains "$OUT" 'app/build/outputs/apk/fortress/release/app-fortress-release.apk'; pass "fortress release mapping"

run_capture --json --distribution play --profile release build; assert_rc 2; assert_json_error_has_fix; assert_error_code signing_inputs_missing; assert_data_keys '[]'; assert_contains "$OUT" "PLAY_UPLOAD_STORE_FILE"; assert_contains "$OUT" "signing input"; pass "play release signing unavailable truthful"
run_capture --json --distribution fortress --profile release build; assert_rc 2; assert_json_error_has_fix; assert_error_code signing_inputs_missing; assert_data_keys '[]'; assert_contains "$OUT" "RELEASE_STORE_FILE"; assert_contains "$OUT" "signing input"; pass "fortress release signing unavailable truthful"

fixture_store="$TMP/play-fixture.jks"
: >"$fixture_store"
export PLAY_UPLOAD_STORE_FILE="$fixture_store" PLAY_UPLOAD_STORE_PASSWORD=fixture-store-pass \
  PLAY_UPLOAD_KEY_ALIAS=fixture-alias PLAY_UPLOAD_KEY_PASSWORD=fixture-key-pass
PLAY_UPLOAD_CERT_SHA256="$(printf 'fixture-play-certificate-der' | sha256sum | cut -d' ' -f1)"
export PLAY_UPLOAD_CERT_SHA256
PM3_FAKE_KEYTOOL_MODE=fail; export PM3_FAKE_KEYTOOL_MODE
run_capture --json --distribution play --profile release build; assert_rc 2; assert_json_error_has_fix; assert_error_code signing_verification_failed; assert_data_keys '[]'; assert_contains "$OUT" 'keystore or alias verification failed'; pass "play release corrupt signer exits unavailable"
PM3_FAKE_KEYTOOL_MODE=good; export PM3_FAKE_KEYTOOL_MODE
PLAY_UPLOAD_CERT_SHA256="0${PLAY_UPLOAD_CERT_SHA256:1}"; export PLAY_UPLOAD_CERT_SHA256
run_capture --json --distribution play --profile release build; assert_rc 2; assert_json_error_has_fix; assert_error_code signing_certificate_mismatch; assert_data_keys '[]'; assert_contains "$OUT" 'certificate mismatch'; pass "play release foreign signer exits unavailable"
PLAY_UPLOAD_CERT_SHA256="$(printf 'fixture-play-certificate-der' | sha256sum | cut -d' ' -f1)"; export PLAY_UPLOAD_CERT_SHA256
run_capture --json --distribution play --profile release build; assert_rc 0; assert_contains "$OUT" '"gradle_task":"assemblePlayRelease"'; assert_contract_shape build output "$OUT"; pass "play release verified fixture signer builds"
unset PLAY_UPLOAD_STORE_FILE PLAY_UPLOAD_STORE_PASSWORD PLAY_UPLOAD_KEY_ALIAS PLAY_UPLOAD_KEY_PASSWORD PLAY_UPLOAD_CERT_SHA256
run_capture --json --distribution fortress --profile debug build; assert_rc 0; assert_contains "$OUT" '"gradle_task":"assembleFortressDebug"'; assert_contains "$(cat "$LOG")" "gradle assembleFortressDebug"; assert_contract_shape build output "$OUT"; pass "fixture gradle build task"
if [ ! -f "$ROOT/app/build/reports/pm3/build-fortress-debug.log" ] || ! grep -q '^pm3 fake gradle log:' "$ROOT/app/build/reports/pm3/build-fortress-debug.log"; then
  fail "fixture report log missing marker"
fi

: >"$LOG"
run_capture --json --serial SERIAL123 --distribution fortress --profile debug install; assert_rc 0; assert_contains "$(cat "$LOG")" "adb -s SERIAL123 install"; assert_not_contains "$(cat "$LOG")" "adb devices"; assert_contract_shape install output "$OUT"; pass "adb install always -s"

: >"$LOG"
export PM3_SERIAL=ENV123
run_capture --json run
unset PM3_SERIAL
assert_rc 0
assert_contains "$(cat "$LOG")" "adb -s ENV123 shell am start"
assert_not_contains "$(cat "$LOG")" "adb devices"
assert_contract_shape run output "$OUT"
pass "PM3_SERIAL is explicit and never triggers first-device discovery"

set +e
OUT="$($PM3 --json --serial SERIAL123 logcat 2>"$TMP/logcat.err")"
RC=$?
ERR="$(cat "$TMP/logcat.err")"
set -e
[ "$RC" -eq 0 ] || fail "logcat exit $RC"
[ "$(printf '%s\n' "$OUT" | grep -c '"event":"logcat"')" -eq 2 ] || fail "logcat did not emit two event lines: $OUT"
printf '%s\n' "$OUT" | while IFS= read -r line; do
  printf '%s' "$line" | jq -e . >/dev/null || fail "invalid logcat NDJSON line: $line"
  assert_logcat_event_shape logcat "$line"
done
assert_contains "$ERR" "streaming adb logcat as NDJSON"
pass "logcat NDJSON and stderr diagnostics"

check_logcat_adb_failure() {
  PM3_FAKE_ADB_MODE=logcat_fail
  export PM3_FAKE_ADB_MODE
  run_capture --json --serial SERIAL123 logcat
  PM3_FAKE_ADB_MODE=ok
  export PM3_FAKE_ADB_MODE
  assert_rc 4
  [ "$(printf '%s\n' "$OUT" | wc -l)" -eq 1 ] || fail "stream error was not one NDJSON record: $OUT"
  printf '%s' "$OUT" | jq -e '.event == "stream_error" and .error == "logcat_failed" and (.message | length > 0) and (.fix | length > 0)' >/dev/null ||
    fail "terminal stream error missing event/error/message/fix: $OUT"
  assert_logcat_event_shape stream_error "$OUT"
}
run_check "logcat adb failure emits terminal stream_error exit4" check_logcat_adb_failure

check_device_query_runtime_failure() {
  PM3_FAKE_ADB_MODE=dumpsys_fail
  export PM3_FAKE_ADB_MODE
  run_capture --json --serial SERIAL123 media
  assert_rc 4
  assert_json_error_has_fix
  assert_error_code media_query_failed
  assert_data_keys '[]'
  assert_contains "$OUT" 'media-session query failed'
  run_capture --json --serial SERIAL123 fps
  assert_rc 4
  assert_json_error_has_fix
  assert_error_code gfxinfo_query_failed
  assert_data_keys '[]'
  assert_contains "$OUT" 'gfxinfo query failed'
  PM3_FAKE_ADB_MODE=ok
  export PM3_FAKE_ADB_MODE
}
run_check "device query adb failures are structured exit4" check_device_query_runtime_failure

run_capture --json --serial SERIAL123 media; assert_rc 0; assert_contract_shape media output "$OUT"; pass "media nested data contract"
run_capture --json --serial SERIAL123 fps; assert_rc 0; assert_contract_shape fps output "$OUT"; pass "fps nested data contract"

run_capture --json --schema junk; assert_rc 3; assert_json_error_has_fix; assert_error_code unexpected_argument; assert_data_keys '["usage"]'; pass "schema rejects trailing positional input with usage data"
run_capture --json --help junk; assert_rc 3; assert_json_error_has_fix; assert_error_code unexpected_argument; assert_data_keys '["usage"]'; pass "help rejects trailing positional input with usage data"

check_screenshot_missing_serial_no_file() {
  local outp="$TMP/missing-serial.png"
  run_capture --json screenshot "$outp"
  assert_rc 3
  assert_json_error_has_fix
  assert_error_code serial_required
  assert_data_keys '["usage"]'
  assert_no_file "$outp"
}
run_check "screenshot missing serial creates no file" check_screenshot_missing_serial_no_file

check_screenshot_adb_failure_removes_partial() {
	local outp="$TMP/partial.png"
  PM3_FAKE_ADB_MODE=screencap_fail
  export PM3_FAKE_ADB_MODE
  run_capture --json --serial SERIAL123 screenshot "$outp"
  PM3_FAKE_ADB_MODE=ok
  export PM3_FAKE_ADB_MODE
  assert_rc 4
  assert_json_error_has_fix
  assert_error_code screenshot_capture_failed
  assert_data_keys '[]'
  assert_no_file "$outp"
}
run_check "screenshot adb failure removes partial" check_screenshot_adb_failure_removes_partial

check_screenshot_failure_preserves_existing_output() {
	local outp="$TMP/existing.png"
	printf 'sentinel-png' >"$outp"
	PM3_FAKE_ADB_MODE=screencap_fail
	export PM3_FAKE_ADB_MODE
	run_capture --json --serial SERIAL123 screenshot "$outp"
	PM3_FAKE_ADB_MODE=ok
	export PM3_FAKE_ADB_MODE
	assert_rc 4
	assert_json_error_has_fix
	assert_error_code screenshot_capture_failed
	assert_data_keys '[]'
	[ "$(cat "$outp")" = sentinel-png ] || fail "screenshot failure changed pre-existing output"
}
run_check "screenshot adb failure preserves pre-existing output" check_screenshot_failure_preserves_existing_output

check_screenshot_success_is_one_shot() {
  local outp="$TMP/success.png"
  run_capture --json --serial SERIAL123 screenshot "$outp"
  assert_rc 0
  assert_one_line_json
  assert_contract_shape screenshot output "$OUT"
  [ -s "$outp" ] || fail "successful screenshot did not create output"
}
run_check "screenshot success emits exact nested envelope" check_screenshot_success_is_one_shot

check_connect_zero_exit_failure_text() {
  PM3_FAKE_ADB_MODE=connect_zero_bad
  export PM3_FAKE_ADB_MODE
  run_capture --json connect 127.0.0.1:34567
  PM3_FAKE_ADB_MODE=ok
  export PM3_FAKE_ADB_MODE
  assert_rc 4
  assert_json_error_has_fix
  assert_error_code connect_failed
  assert_data_keys '[]'
  assert_contains "$OUT" "failed to connect"
}
run_check "connect zero-exit failure text exits 4" check_connect_zero_exit_failure_text

check_record_pull_failure_removes_output() {
  local outp="$TMP/partial.mp4"
  PM3_FAKE_ADB_MODE=pull_fail
  export PM3_FAKE_ADB_MODE
  run_capture --json --serial SERIAL123 record 1 "$outp"
  PM3_FAKE_ADB_MODE=ok
  export PM3_FAKE_ADB_MODE
  assert_rc 4
  assert_json_error_has_fix
  assert_error_code record_pull_failed
  assert_data_keys '[]'
  assert_no_file "$outp"
}
run_check "record pull failure removes output exit4" check_record_pull_failure_removes_output

check_record_pull_failure_preserves_existing_output() {
	local outp="$TMP/existing.mp4"
	printf 'sentinel-mp4' >"$outp"
	PM3_FAKE_ADB_MODE=pull_fail
	export PM3_FAKE_ADB_MODE
	run_capture --json --serial SERIAL123 record 1 "$outp"
	PM3_FAKE_ADB_MODE=ok
	export PM3_FAKE_ADB_MODE
	assert_rc 4
	assert_json_error_has_fix
	assert_error_code record_pull_failed
	assert_data_keys '[]'
	[ "$(cat "$outp")" = sentinel-mp4 ] || fail "record failure changed pre-existing output"
}
run_check "record pull failure preserves pre-existing output" check_record_pull_failure_preserves_existing_output

check_record_success_is_one_shot() {
  local outp="$TMP/success.mp4"
  run_capture --json --serial SERIAL123 record 1 "$outp"
  assert_rc 0
	assert_one_line_json
	assert_contract_shape record output "$OUT"
	[ -s "$outp" ] || fail "successful record did not create output"
}
run_check "record success emits exactly one envelope without adb stdout leakage" check_record_success_is_one_shot

check_tty_one_shots_are_json() {
  command -v script >/dev/null 2>&1 || return 0
  local out rc
  set +e
  out="$(script -q -e -c "$PM3 wat" /dev/null 2>&1)"
  rc=$?
  set -e
  [ "$rc" -eq 3 ] || fail "tty rc $rc out=$out"
  OUT="${out//$'\r'/}"
  ERR=""
  assert_json_error_has_fix
  assert_error_code unknown_verb
  assert_data_keys '["usage"]'

  set +e
  out="$(script -q -e -c "$PM3 schema" /dev/null 2>&1)"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] || fail "tty schema rc $rc out=$out"
  OUT="${out//$'\r'/}"
  assert_one_line_json
  assert_data_keys '["schema"]'
}
run_check "TTY one-shots remain exact JSON objects" check_tty_one_shots_are_json

phase05_check() {
  local cmd="$1" before after
  before="$(wc -l <"$LOG")"
  run_capture --json "$cmd"
  after="$(wc -l <"$LOG")"
  assert_rc 2 "$cmd"
  assert_json_error_has_fix
  assert_error_code phase_unavailable
  assert_data_keys '[]'
  assert_contains "$OUT" "authenticated Binder/tailnet client"
  [ "$before" = "$after" ] || fail "Phase 05 side effect for $cmd"
}
for cmd in state-get state-watch nexus-status nexus-grant nexus-revoke action-run audit-list audit-export; do
  run_check "Phase 05 unavailable flat verb: $cmd" phase05_check "$cmd"
done
pass "Phase 05 flat verbs are explicit exit-2 side-effect-free stubs"

run_capture --json pair 127.0.0.1:12345 999999; assert_rc 0; assert_contract_shape pair output "$OUT"; pass "pair preserved with exact nested data"
run_capture --json connect 127.0.0.1:34567; assert_rc 0; assert_contract_shape connect output "$OUT"; pass "connect preserved with exact nested data"

restore_repo_artifacts
assert_repo_artifacts_restored
pass "fixture build outputs restored byte-for-byte without deleting real artifacts"

if [ -s "$BLOCKERS" ]; then
  echo "pm3 fixture tests completed with implementation blockers:" >&2
  sed 's/^/- /' "$BLOCKERS" >&2
  exit 1
else
  echo "pm3 fixture tests passed"
fi
