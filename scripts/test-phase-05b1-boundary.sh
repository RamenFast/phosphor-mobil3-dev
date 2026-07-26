#!/usr/bin/env bash
# Fixture checks for the Phase 05b-1 observation-only boundary gate.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GATE="$ROOT/scripts/check-phase-05b1-boundary.sh"

pass() { printf 'ok - %s\n' "$1"; }
fail() { printf 'not ok - %s\n' "$1" >&2; exit 1; }

bash -n "$GATE" "$0" || fail "bash syntax"
pass "bash syntax"

if command -v shellcheck >/dev/null 2>&1; then
  shellcheck "$GATE" "$0" || fail "shellcheck"
  pass "shellcheck"
fi

schema="$($GATE schema)" || fail "schema command"
printf '%s' "$schema" | jq -e '
  .status == "ok" and
  .tool == "check-phase-05b1-boundary" and
  (.version | type == "string") and
  (.ts | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-][0-9]{2}:[0-9]{2}$")) and
  .data.schema["$schema"] == "https://json-schema.org/draft/2020-12/schema" and
  .data.schema.additionalProperties == false and
  (.data.schema.properties.verbs.properties | keys == ["all","artifact","schema","source"]) and
  (.data.schema.properties.exits.properties | keys == ["0","2","3","4"])
' >/dev/null || fail "strict enveloped schema"
pass "strict enveloped schema"

set +e
bad="$($GATE impossible 2>/dev/null)"
bad_rc=$?
set -e
[ "$bad_rc" -eq 3 ] || fail "unknown verb exit 3"
printf '%s' "$bad" | jq -e '.status == "error" and .error == "bad_input" and (.fix | length > 0)' >/dev/null || \
  fail "unknown verb error envelope"
pass "unknown verb exit and fix"

for invalid_mode in schema source; do
  set +e
  invalid_option="$($GATE "$invalid_mode" --artifact /does/not/apply.aab 2>/dev/null)"
  invalid_option_rc=$?
  set -e
  [ "$invalid_option_rc" -eq 3 ] || fail "$invalid_mode rejects --artifact with exit 3"
  printf '%s' "$invalid_option" | jq -s -e '
    length == 1 and
    .[0].status == "error" and
    .[0].error == "bad_input" and
    (.[0].fix | length > 0)
  ' >/dev/null || fail "$invalid_mode rejects --artifact with one valid envelope"
done
pass "published option contracts are enforced"

for ((control_code = 1; control_code <= 31; control_code++)); do
  printf -v control_octal '%03o' "$control_code"
  printf -v control_character '%b' "\\$control_octal"
  controlled_input="bad${control_character}verb"
  expected_message="unknown verb '$controlled_input'"
  set +e
  controlled="$($GATE "$controlled_input" 2>/dev/null)"
  controlled_rc=$?
  set -e
  [ "$controlled_rc" -eq 3 ] || fail "control $control_code unknown verb exit 3"
  printf '%s' "$controlled" | jq -s -e \
    --arg expected "$expected_message" '
      length == 1 and
      .[0].status == "error" and
      .[0].tool == "check-phase-05b1-boundary" and
      .[0].version == "2.0.0" and
      (.[0].ts | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-][0-9]{2}:[0-9]{2}$")) and
      .[0].error == "bad_input" and
      .[0].message == $expected and
      (.[0].fix | length > 0)
    ' >/dev/null || fail "control $control_code valid JSON envelope"
  if printf '%s' "$controlled" | LC_ALL=C grep -q $'[\001-\037]'; then
    fail "control $control_code escaped bytes"
  fi
done
pass "all representable JSON control characters are escaped in error JSON"

(
  scratch_root="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
  minimal_path="$(mktemp -d "$scratch_root/phase05b1-no-jq.XXXXXX")"
  trap 'rm -rf "$minimal_path"' EXIT
  ln -s "$(command -v date)" "$minimal_path/date"
  ln -s "$(command -v dirname)" "$minimal_path/dirname"
  set +e
  missing_jq="$(PATH="$minimal_path" /bin/bash "$GATE" schema 2>/dev/null)"
  missing_jq_rc=$?
  set -e
  [ "$missing_jq_rc" -eq 2 ] || fail "missing jq exit 2"
  printf '%s' "$missing_jq" | jq -e '.status == "error" and .error == "dependency_unavailable" and (.fix | length > 0)' >/dev/null || \
    fail "missing jq valid error envelope"
  printf '%s' "$schema" | jq -e --argjson exit_code "$missing_jq_rc" \
    '.data.schema["$defs"].schemaVerb.properties.exits.const | index($exit_code) != null' >/dev/null || \
    fail "schema contract declares missing jq exit"
)
pass "missing jq is a valid unavailable envelope"

source_result="$($GATE source)" || fail "source boundary"
printf '%s' "$source_result" | jq -e '
  .status == "ok" and
  .data.baseline == "36c9d43de6241af5284aaa45cfe538b6a6960775" and
  .data.source_checks >= 12 and
  .data.artifact_checks == 0 and
  .data.observation_only == true and
  .data.python_free == true
' >/dev/null || fail "source result contract"
pass "source boundary and result contract"

(
  baseline="36c9d43de6241af5284aaa45cfe538b6a6960775"
  scratch_root="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
  mkdir -p "$scratch_root"
  worktree="$(mktemp -d "$scratch_root/phase05b1-gate-fixture.XXXXXX")"
  patch_file="$worktree.candidate.patch"
  # Called indirectly by the subshell EXIT trap.
  # shellcheck disable=SC2317
  cleanup_fixture() {
    git -C "$ROOT" worktree remove --force "$worktree" >/dev/null 2>&1 || true
    rm -f "$patch_file"
  }
  trap cleanup_fixture EXIT

  git -C "$ROOT" diff --binary "$baseline" -- . ':(exclude)Phosphor build.md' > "$patch_file"
  rm -rf "$worktree"
  git -C "$ROOT" worktree add --detach "$worktree" "$baseline" >/dev/null
  git -C "$worktree" apply "$patch_file"
  for path in \
    app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt \
    app/src/testFortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcherTest.kt \
    decisions/2026-07-26-phase-05b1-observation-only.md \
    docs/dev/receipts/phosphor-2.0/phase-05b1-observation-only.md \
    scripts/check-phase-05b1-boundary.sh \
    scripts/test-phase-05b1-boundary.sh; do
    mkdir -p "$worktree/$(dirname "$path")"
    cp "$ROOT/$path" "$worktree/$path"
  done
  chmod +x "$worktree/scripts/check-phase-05b1-boundary.sh"

  printf '\nval forbiddenDatagramSocket = java.nio.channels.DatagramChannel.open()\n' \
    >> "$worktree/app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt"
  set +e
  counterexample="$("$worktree/scripts/check-phase-05b1-boundary.sh" source 2>/dev/null)"
  counterexample_rc=$?
  set -e
  [ "$counterexample_rc" -eq 4 ] || fail "runtime activation counterexample exit"
  printf '%s' "$counterexample" | jq -e '.error == "runtime_activation_detected"' >/dev/null || \
    fail "runtime activation counterexample result"

  cp "$ROOT/app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt" \
    "$worktree/app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt"
  for python_command in \
    'python3 -c "print(1)"' \
    '/usr/bin/python3 -c "print(1)"' \
    '"/usr/bin/python3" -c "print(1)"' \
    "'/usr/bin/python3' -c \"print(1)\"" \
    'command -- /usr/bin/python3 -c "print(1)"' \
    $'/usr/bin/python3\\\n -c "print(1)"'; do
    cp "$ROOT/scripts/test-phase-05b1-boundary.sh" "$worktree/scripts/test-phase-05b1-boundary.sh"
    printf '\n%s\n' "$python_command" >> "$worktree/scripts/test-phase-05b1-boundary.sh"
    set +e
    counterexample="$("$worktree/scripts/check-phase-05b1-boundary.sh" source 2>/dev/null)"
    counterexample_rc=$?
    set -e
    [ "$counterexample_rc" -eq 4 ] || fail "Python counterexample exit"
    printf '%s' "$counterexample" | jq -e '.error == "python_dependency_detected"' >/dev/null || \
      fail "Python counterexample result"
  done

  cp "$ROOT/scripts/test-phase-05b1-boundary.sh" "$worktree/scripts/test-phase-05b1-boundary.sh"
  printf '\n// prohibited bootstrap probe\n' >> "$worktree/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt"
  set +e
  counterexample="$("$worktree/scripts/check-phase-05b1-boundary.sh" source 2>/dev/null)"
  counterexample_rc=$?
  set -e
  [ "$counterexample_rc" -eq 4 ] || fail "prohibited path counterexample exit"
  printf '%s' "$counterexample" | jq -e '.error == "unexpected_change"' >/dev/null || \
    fail "prohibited path counterexample result"
)
pass "activation, Python, and prohibited-path counterexamples fail closed"

set +e
missing_desktop="$(PHOSPHOR_DESKTOP_ADAPTER="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}/missing-phase05b1-adapter" "$GATE" source 2>/dev/null)"
missing_desktop_rc=$?
set -e
[ "$missing_desktop_rc" -eq 2 ] || fail "missing desktop adapter exit 2"
printf '%s' "$missing_desktop" | jq -e '.error == "desktop_adapter_unavailable" and (.fix | length > 0)' >/dev/null || \
  fail "missing desktop adapter result"
pass "desktop adapter proof cannot be bypassed"

artifact="${PHASE05B1_PLAY_ARTIFACT:-$ROOT/app/build/outputs/bundle/playRelease/app-play-release.aab}"
if [ -f "$artifact" ]; then
  artifact_result="$($GATE artifact --artifact "$artifact")" || fail "artifact boundary"
  printf '%s' "$artifact_result" | jq -e '
    .status == "ok" and
    .data.source_checks == 0 and
    .data.artifact_checks == 3 and
    .data.observation_only == true and
    .data.python_free == true
  ' >/dev/null || fail "artifact result contract"
  pass "artifact boundary and result contract"

  (
    scratch_root="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
    fake_cwd="$(mktemp -d "$scratch_root/phase05b1-cwd-helper.XXXXXX")"
    trap 'rm -rf "$fake_cwd"' EXIT
    mkdir -p "$fake_cwd/scripts"
    cat > "$fake_cwd/scripts/check-play-boundary.sh" <<'FAKE'
#!/usr/bin/env bash
printf '{"status":"ok","tool":"wrong-helper","version":"999","ts":"2026-01-01T00:00:00+00:00","data":{"artifact_checks":999}}\n'
FAKE
    chmod +x "$fake_cwd/scripts/check-play-boundary.sh"
    cwd_result="$(cd "$fake_cwd" && "$GATE" artifact --artifact "$artifact")" || fail "artifact gate from foreign CWD"
    printf '%s' "$cwd_result" | jq -e '.status == "ok" and .tool == "check-phase-05b1-boundary" and .data.artifact_checks == 3' >/dev/null || \
      fail "artifact helper cannot be CWD-substituted"
  )
  pass "artifact helper is repository-absolute and identity-checked"

  if command -v zip >/dev/null 2>&1 && command -v keytool >/dev/null 2>&1 && command -v jarsigner >/dev/null 2>&1; then
    (
      scratch_root="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
      artifact_fixture="$(mktemp -d "$scratch_root/phase05b1-python-aab.XXXXXX")"
      trap 'rm -rf "$artifact_fixture"' EXIT
      mutant="$artifact_fixture/play-python-runtime.aab"
      cp "$artifact" "$mutant"
      mkdir -p "$artifact_fixture/payload/assets"
      printf 'Py_Initialize\n' > "$artifact_fixture/payload/assets/python311.zip"
      printf 'embedded runtime\n' > "$artifact_fixture/payload/assets/runtime.pyz"
      (cd "$artifact_fixture/payload" && zip -q "$mutant" assets/python311.zip assets/runtime.pyz)
      zip -qd "$mutant" 'META-INF/*' || true
      keytool -genkeypair -noprompt -storetype PKCS12 \
        -keystore "$artifact_fixture/fixture.p12" -storepass phase05b1 -keypass phase05b1 \
        -alias fixture -keyalg RSA -keysize 2048 -validity 1 \
        -dname 'CN=Phase 05b-1 Artifact Fixture,O=Phosphor,C=US' >/dev/null 2>&1
      jarsigner -keystore "$artifact_fixture/fixture.p12" -storepass phase05b1 -keypass phase05b1 \
        "$mutant" fixture >/dev/null 2>&1
      set +e
      python_artifact="$($GATE artifact --artifact "$mutant" 2>/dev/null)"
      python_artifact_rc=$?
      set -e
      [ "$python_artifact_rc" -eq 4 ] || fail "Python artifact counterexample exit 4"
      printf '%s' "$python_artifact" | jq -e '.error == "python_artifact_detected"' >/dev/null || \
        fail "Python artifact counterexample result"
    )
    pass "signed Python-bearing AAB counterexample fails closed"
  fi
else
  set +e
  unavailable="$($GATE artifact --artifact "$artifact" 2>/dev/null)"
  unavailable_rc=$?
  set -e
  [ "$unavailable_rc" -eq 2 ] || fail "missing artifact exit 2"
  printf '%s' "$unavailable" | jq -e '.status == "error" and .error == "artifact_unavailable" and (.fix | length > 0)' >/dev/null || \
    fail "missing artifact error envelope"
  pass "missing artifact is truthful unavailable"
fi

printf 'phase 05b-1 boundary fixtures passed\n'
