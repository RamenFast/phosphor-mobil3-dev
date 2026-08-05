#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PM3="$ROOT/dev/pm3"
SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
TMP="$(mktemp -d "$SCRATCH_ROOT/pm3-test.XXXXXX")"
BIN="$TMP/bin"
CALLS="$TMP/calls.log"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
ORIGINAL="$TMP/original-app-debug.apk"
mkdir -p "$BIN"
: >"$CALLS"

HAD_APK=0
if [ -f "$APK" ]; then
  HAD_APK=1
  cp -a "$APK" "$ORIGINAL"
fi
cleanup() {
  rm -f "$APK"
  if [ "$HAD_APK" -eq 1 ]; then
    mkdir -p "$(dirname "$APK")"
    cp -a "$ORIGINAL" "$APK"
  fi
  rm -rf "$TMP"
}
trap cleanup EXIT

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'ok - %s\n' "$*"; }

cat >"$BIN/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail
printf '%q ' "$@" >>"$PM3_TEST_CALLS"
printf '\n' >>"$PM3_TEST_CALLS"
if [ "${1:-}" = "--version" ]; then
  printf 'Android Debug Bridge version 1.0.41\n'
  exit 0
fi
if [ "${1:-}" = "pair" ]; then
  printf 'Successfully paired to %s\n' "$2"
  exit 0
fi
if [ "${1:-}" = "connect" ]; then
  printf 'connected to %s\n' "$2"
  exit 0
fi
[ "${1:-}" = "-s" ] || exit 9
shift 2
case "${1:-}" in
  install) printf 'Performing Streamed Install\nSuccess\n' ;;
  exec-out) printf '\211PNG\r\n\032\nfixture' ;;
  pull)
    if [[ "${2:-}" = */base.apk ]]; then cp "$PM3_TEST_APK" "$3"; else printf 'fixture-mp4' >"$3"; fi
    ;;
  logcat) printf 'first line\nsecond line\n' ;;
  shell)
    shift
    case "${1:-} ${2:-} ${3:-}" in
      "dumpsys package dev.phosphor.mobil3.debug"|"dumpsys package dev.phosphor.mobil3")
        printf 'versionName=2.0.0-debug\n'
        ;;
      "pm path dev.phosphor.mobil3.debug"|"pm path dev.phosphor.mobil3")
        printf 'package:/data/app/fixture/base.apk\n'
        ;;
      "am start -n") printf 'Starting: Intent\n' ;;
      "pidof dev.phosphor.mobil3.debug "|"pidof dev.phosphor.mobil3 ") printf '1234\n' ;;
      "dumpsys media_session ") printf 'dev.phosphor.mobil3.debug playing\n' ;;
      "dumpsys audio ") printf 'dev.phosphor.mobil3.debug active\n' ;;
      "dumpsys gfxinfo dev.phosphor.mobil3.debug") printf 'Total frames rendered: 42\nJanky frames: 0\n' ;;
      "pm dump dev.phosphor.mobil3.debug") printf 'pkgFlags=[ DEBUGGABLE HAS_CODE ]\n' ;;
      "am broadcast -a") printf 'Broadcast completed: result=0\n' ;;
      "run-as dev.phosphor.mobil3.debug cat")
        if [ "${4:-}" = "files/selftest.json" ]; then
          printf '{"ok":true}'
        else
          printf '\211PNG\r\n\032\nselftest'
        fi
        ;;
      "screenrecord --time-limit "*|"rm /sdcard/pm3-rec.mp4 ") ;;
      *) exit 8 ;;
    esac
    ;;
  *) exit 7 ;;
esac
ADB
chmod +x "$BIN/adb"

cat >"$BIN/gradle" <<'GRADLE'
#!/usr/bin/env bash
set -euo pipefail
printf '%q ' "$@" >>"$PM3_TEST_CALLS"
printf '\n' >>"$PM3_TEST_CALLS"
case "${1:-}" in
  assembleDebug)
    mkdir -p app/build/outputs/apk/debug
    printf 'debug-apk' >app/build/outputs/apk/debug/app-debug.apk
    ;;
  assembleRelease)
    mkdir -p app/build/outputs/apk/release
    printf 'release-apk' >app/build/outputs/apk/release/app-release.apk
    ;;
  *) exit 6 ;;
esac
GRADLE
chmod +x "$BIN/gradle"

cat >"$BIN/apksigner" <<'APKSIGNER'
#!/usr/bin/env bash
set -euo pipefail
printf 'Signer #1 certificate SHA-256 digest: aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899\n'
APKSIGNER
chmod +x "$BIN/apksigner"

export PM3_ADB="$BIN/adb"
export PM3_GRADLE="$BIN/gradle"
export PM3_APKSIGNER="$BIN/apksigner"
export PM3_RECEIPTS_DIR="$TMP/receipts"
export PM3_TEST_CALLS="$CALLS"
export PM3_TEST_APK="$APK"

run_capture() {
  set +e
  OUT="$($PM3 --json "$@" 2>"$TMP/stderr")"
  RC=$?
  set -e
}

assert_envelope() {
  printf '%s' "$OUT" | jq -e '
    .tool == "pm3" and .version == "1.0.0"
    and (.ts | type == "string" and length > 0)
    and if .status == "ok" then (.data | type == "object")
        elif .status == "error" then
          (.error | type == "string" and length > 0)
          and (.message | type == "string" and length > 0)
          and (.fix | type == "string" and length > 0)
          and (.data | type == "object")
        else false end
  ' >/dev/null || fail "bad envelope: $OUT"
}

SCHEMA="$($PM3 schema)"
printf '%s' "$SCHEMA" | jq -e '
  .status == "ok"
  and .data.schema.properties.defaults.const.profile == "debug"
  and .data.schema.properties.mappings.const.debug.package_id == "dev.phosphor.mobil3.debug"
  and .data.schema.properties.mappings.const.release.package_id == "dev.phosphor.mobil3"
  and (.data.schema.properties.verbs.required | sort) == (["build","connect","doctor","fps","help","install","logcat","media","pair","record","run","schema","screenshot","smoke"] | sort)
  and .data.schema.properties.exit_codes.const == {"0":"success","2":"dependency or capability unavailable","3":"bad input or usage","4":"runtime failure"}
' >/dev/null || fail "schema contract mismatch"
printf '%s' "$SCHEMA" | grep -Eqi 'nexus|nexidex|state-watch|action-run|audit-export|tailnet-secret|fortress' && fail "retired product surface remains in schema"
pass "schema describes only the developer surface"

run_capture help
[ "$RC" -eq 0 ] || fail "help exit $RC"
assert_envelope
printf '%s' "$OUT" | jq -e '.data.verbs | index("build") and (index("state-get") | not)' >/dev/null
pass "help uses the v1 structured envelope"

run_capture --profile invalid doctor
[ "$RC" -eq 3 ] || fail "invalid profile exit $RC"
assert_envelope
printf '%s' "$OUT" | jq -e '.error == "invalid_profile" and (.data.usage | length > 0)' >/dev/null
pass "bad usage exits 3 with a fix and usage"

run_capture --distribution play doctor
[ "$RC" -eq 3 ] || fail "retired distribution flag exit $RC"
printf '%s' "$OUT" | jq -e '.error == "unknown_argument"' >/dev/null
pass "retired distribution selection is rejected"

for verb in state-get state-watch nexus-status action-run audit-export tailnet-secret; do
  run_capture "$verb"
  [ "$RC" -eq 3 ] || fail "$verb exit $RC"
  printf '%s' "$OUT" | jq -e '.error == "unknown_verb"' >/dev/null
 done
pass "retired product verbs are absent"

run_capture doctor
[ "$RC" -eq 0 ] || fail "doctor exit $RC: $OUT"
printf '%s' "$OUT" | jq -e '
  .data.profile == "debug"
  and .data.package_id == "dev.phosphor.mobil3.debug"
  and .data.gradle_task == "assembleDebug"
  and (.data.apk | endswith("app/build/outputs/apk/debug/app-debug.apk"))
  and .data.all_ok == true
' >/dev/null
pass "doctor reports the single debug product"

run_capture build
[ "$RC" -eq 0 ] || fail "build exit $RC: $OUT"
printf '%s' "$OUT" | jq -e '
  .data.profile == "debug"
  and .data.package_id == "dev.phosphor.mobil3.debug"
  and .data.gradle_task == "assembleDebug"
  and (.data.sha256 | test("^[0-9a-f]{64}$"))
' >/dev/null
grep -q 'assembleDebug' "$CALLS" || fail "debug Gradle task not called"
pass "build uses the consolidated Gradle variant"

run_capture --profile release build
[ "$RC" -eq 2 ] || fail "unsigned release exit $RC: $OUT"
printf '%s' "$OUT" | jq -e '.error == "signing_inputs_missing" and (.fix | length > 0)' >/dev/null
pass "release build fails closed before Gradle without signing inputs"

run_capture install
[ "$RC" -eq 3 ] || fail "missing serial exit $RC"
printf '%s' "$OUT" | jq -e '.error == "serial_required"' >/dev/null
pass "device operations require an explicit serial"

run_capture --serial serial-1 install
[ "$RC" -eq 0 ] || fail "install exit $RC: $OUT"
printf '%s' "$OUT" | jq -e '
  .data.serial == "serial-1"
  and .data.package_id == "dev.phosphor.mobil3.debug"
  and .data.local_sha256 == .data.installed_sha256
  and (.data.signer_sha256 | test("^[0-9a-f]{64}$"))
' >/dev/null
pass "install targets the debug-suffixed package and verifies exact bytes and signer"

run_capture --profile release --serial serial-1 install "$APK"
[ "$RC" -eq 0 ] || fail "explicit release install exit $RC: $OUT"
printf '%s' "$OUT" | jq -e --arg apk "$APK" '
  .data.package_id == "dev.phosphor.mobil3"
  and .data.apk == $apk
  and .data.local_sha256 == .data.installed_sha256
' >/dev/null
pass "install accepts the exact canonical APK path for the production package"

run_capture --serial serial-1 run
[ "$RC" -eq 0 ] || fail "run exit $RC: $OUT"
printf '%s' "$OUT" | jq -e '.data.pids == "1234"' >/dev/null
grep -Fq -- '-s serial-1 shell am start -n dev.phosphor.mobil3.debug/dev.phosphor.mobil3.MainActivity' "$CALLS" || \
  fail "debug launch component did not preserve the application namespace"
pass "run verifies the selected process"

SHOT="$TMP/shot.png"
run_capture --serial serial-1 screenshot "$SHOT"
if [ "$RC" -ne 0 ] || [ ! -s "$SHOT" ]; then fail "screenshot failed: $OUT"; fi
pass "screenshot writes a non-empty PNG transactionally"

VIDEO="$TMP/record.mp4"
run_capture --serial serial-1 record 1 "$VIDEO"
if [ "$RC" -ne 0 ] || [ ! -s "$VIDEO" ]; then fail "record failed: $OUT"; fi
pass "record writes a non-empty MP4 transactionally"

run_capture --serial serial-1 media
[ "$RC" -eq 0 ] || fail "media exit $RC: $OUT"
run_capture --serial serial-1 fps
[ "$RC" -eq 0 ] || fail "fps exit $RC: $OUT"
run_capture --serial serial-1 smoke
[ "$RC" -eq 0 ] || fail "smoke exit $RC: $OUT"
grep -Fq -- '-s serial-1 shell am broadcast -a dev.phosphor.mobil3.SELFTEST -n dev.phosphor.mobil3.debug/dev.phosphor.mobil3.SelfTestReceiver' "$CALLS" || \
  fail "debug self-test receiver did not preserve the application namespace"
[ "$(find "$PM3_RECEIPTS_DIR" -maxdepth 1 -name 'selftest-*.png' -type f | wc -l)" -eq 1 ] || fail "smoke receipt isolation"
pass "device diagnostics and debug self-test retain their developer role"

LOGCAT="$($PM3 --serial serial-1 logcat 2>"$TMP/logcat.err")"
[ "$(printf '%s\n' "$LOGCAT" | wc -l)" -eq 2 ] || fail "logcat line count"
printf '%s\n' "$LOGCAT" | jq -e '.event == "logcat" and .package_id == "dev.phosphor.mobil3.debug"' >/dev/null
pass "logcat is canonical NDJSON"

printf 'pm3 developer CLI fixtures passed\n'
