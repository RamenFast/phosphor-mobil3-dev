#!/usr/bin/env bash
# Fixed release-gate scoreboard for the consolidated Phosphor Mobile product.
set -uo pipefail

TOOL="ship-check"
VERSION="3.1.0"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
cd "$REPO" || exit 4
JSON=0
QUICK=0
ONLY=""
MODE="run"

for arg in "$@"; do
  case "$arg" in
    schema) MODE="schema" ;;
    --json) JSON=1 ;;
    --quick) QUICK=1 ;;
    --only=*) ONLY="${arg#--only=}" ;;
    -h|--help) MODE="help" ;;
    *)
      printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"bad_input","message":"unknown argument: %s","fix":"run scripts/ship-check.sh schema"}\n' \
        "$TOOL" "$VERSION" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$arg"
      exit 3
      ;;
  esac
done

[ -t 1 ] || JSON=1

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }

if [ "$MODE" = help ]; then
  cat <<'USAGE'
ship-check: release-gate scoreboard for Phosphor Mobile

  scripts/ship-check.sh [--json] [--quick] [--only=PREFIX]
  scripts/ship-check.sh schema

--quick skips compile, test, lint, native, relay, and release-artifact gates.
--only runs gates whose ID begins with PREFIX.
USAGE
  exit 0
fi

if [ "$MODE" = schema ]; then
  # shellcheck disable=SC2016 # $schema is a literal JSON Schema property name.
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","data":{"schema":{"$schema":"https://json-schema.org/draft/2020-12/schema","title":"ship-check contract","type":"object","additionalProperties":false,"properties":{"arguments":{"const":["--json","--quick","--only=PREFIX","schema"]},"gate_states":{"const":["green","red","skip"]},"exit_codes":{"const":{"0":"all non-skipped gates green; skips are allowed only for an unfiltered quick run","2":"one or more selected gates are red or an explicitly selected gate was skipped","3":"bad input","4":"runtime failure"}},"gate_ids":{"const":["android.compile","android.tests","android.lint","android.assemble","native.tests","native.lint","relay.tests","relay.lint","pm3.fixtures","boundary.fixtures","release.fixtures","boundary.source","scope.docs","scope.source","privacy.tracking","protected.archive","nopython","antistub","signing.release","provenance.release","release.bundle"]}}}}}\n' \
    "$TOOL" "$VERSION" "$(ts)"
  exit 0
fi

# shellcheck disable=SC1091
[ -f scripts/env.sh ] && source scripts/env.sh
SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
GRADLE_LOG="$(mktemp "$SCRATCH_ROOT/ship-check-gradle.XXXXXX")"
PACKAGE_LOG="$(mktemp "$SCRATCH_ROOT/ship-check-package.XXXXXX")"

GATE_IDS=()
GATE_STATES=()
GATE_DETAILS=()
GATE_FIXES=()
record() {
  GATE_IDS+=("$1")
  GATE_STATES+=("$2")
  GATE_DETAILS+=("$3")
  GATE_FIXES+=("${4:-}")
  if [ "$JSON" -eq 0 ]; then
    local mark
    case "$2" in green) mark=" ok " ;; red) mark="FAIL" ;; *) mark="skip" ;; esac
    printf '%s  %-24s %s\n' "$mark" "$1" "$3" >&2
  fi
}
selected() { [ -z "$ONLY" ] && return 0; case "$1" in "$ONLY"*) return 0 ;; *) return 1 ;; esac; }
gradle_run() { ./gradlew --no-daemon "$@" >"$GRADLE_LOG" 2>&1; }
gradle_profile_run() {
  local profile="$1"
  shift
  PHOSPHOR_SIGNING_PROFILE="$profile" ./gradlew --no-daemon "$@" >"$GRADLE_LOG" 2>&1
}

if selected android.compile; then
  if [ "$QUICK" -eq 1 ]; then
    record android.compile skip "skipped (--quick)"
  elif gradle_run :app:compileDebugKotlin :app:compileDebugUnitTestKotlin; then
    record android.compile green "debug Kotlin and tests compile at minSdk 29"
  else
    record android.compile red "Android compilation failed" "read $GRADLE_LOG; run ./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin"
  fi
fi

if selected android.tests; then
  if [ "$QUICK" -eq 1 ]; then
    record android.tests skip "skipped (--quick)"
  elif gradle_run :app:testDebugUnitTest; then
    results="app/build/test-results/testDebugUnitTest"
    total=$(grep -ho 'tests="[0-9]*"' "$results"/*.xml 2>/dev/null | awk -F'"' '{sum += $2} END {print sum + 0}')
    failures=$(grep -hoE '(failures|errors)="[0-9]*"' "$results"/*.xml 2>/dev/null | awk -F'"' '{sum += $2} END {print sum + 0}')
    if [ "$total" -gt 0 ] && [ "$failures" -eq 0 ]; then
      record android.tests green "$total tests, zero failures"
    else
      record android.tests red "$total tests, $failures failures" "inspect $results"
    fi
  else
    record android.tests red "Android unit suite failed" "read $GRADLE_LOG; run ./gradlew :app:testDebugUnitTest"
  fi
fi

if selected android.lint; then
  if [ "$QUICK" -eq 1 ]; then
    record android.lint skip "skipped (--quick)"
  elif gradle_run :app:lintDebug; then
    record android.lint green "Android lint passes at API 29"
  else
    record android.lint red "Android lint failed" "read $GRADLE_LOG; inspect app/build/reports/lint-results-debug.html"
  fi
fi

if selected android.assemble; then
  if [ "$QUICK" -eq 1 ]; then
    record android.assemble skip "skipped (--quick)"
  elif gradle_run :app:assembleDebug && [ -s app/build/outputs/apk/debug/app-debug.apk ]; then
    record android.assemble green "$(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1) debug APK"
  else
    record android.assemble red "debug APK was not produced" "read $GRADLE_LOG; run ./gradlew :app:assembleDebug"
  fi
fi

if selected native.tests; then
  if [ "$QUICK" -eq 1 ]; then
    record native.tests skip "skipped (--quick)"
  elif cargo test --manifest-path rust/Cargo.toml --locked >/dev/null 2>&1; then
    record native.tests green "mobile Rust tests pass with the lockfile"
  else
    record native.tests red "mobile Rust tests failed" "run cargo test --manifest-path rust/Cargo.toml --locked"
  fi
fi

if selected native.lint; then
  if [ "$QUICK" -eq 1 ]; then
    record native.lint skip "skipped (--quick)"
  elif cargo fmt --manifest-path rust/Cargo.toml --check >/dev/null 2>&1 &&
       cargo clippy --manifest-path rust/Cargo.toml --locked --all-targets -- -D warnings >/dev/null 2>&1; then
    record native.lint green "mobile Rust formatting and Clippy pass"
  else
    record native.lint red "mobile Rust lint failed" "run cargo fmt --manifest-path rust/Cargo.toml --check && cargo clippy --manifest-path rust/Cargo.toml --locked --all-targets -- -D warnings"
  fi
fi

if selected relay.tests; then
  if [ "$QUICK" -eq 1 ]; then
    record relay.tests skip "skipped (--quick)"
  elif cargo test --manifest-path relay/Cargo.toml --locked >/dev/null 2>&1; then
    record relay.tests green "PC relay tests pass with the lockfile"
  else
    record relay.tests red "PC relay tests failed" "run cargo test --manifest-path relay/Cargo.toml --locked"
  fi
fi

if selected relay.lint; then
  if [ "$QUICK" -eq 1 ]; then
    record relay.lint skip "skipped (--quick)"
  elif cargo fmt --manifest-path relay/Cargo.toml --check >/dev/null 2>&1 &&
       cargo clippy --manifest-path relay/Cargo.toml --locked --all-targets -- -D warnings >/dev/null 2>&1; then
    record relay.lint green "PC relay formatting and Clippy pass"
  else
    record relay.lint red "PC relay lint failed" "run cargo fmt --manifest-path relay/Cargo.toml --check && cargo clippy --manifest-path relay/Cargo.toml --locked --all-targets -- -D warnings"
  fi
fi

if selected pm3.fixtures; then
  if scripts/test-pm3.sh >/dev/null 2>&1; then
    record pm3.fixtures green "developer-only pm3 contract passes"
  else
    record pm3.fixtures red "pm3 fixture failure" "run scripts/test-pm3.sh"
  fi
fi

if selected boundary.fixtures; then
  if scripts/test-play-boundary.sh >/dev/null 2>&1; then
    record boundary.fixtures green "production-boundary fixtures pass"
  else
    record boundary.fixtures red "production-boundary fixture failure" "run scripts/test-play-boundary.sh"
  fi
fi

if selected release.fixtures; then
  if scripts/test-release-gates.sh >"$PACKAGE_LOG" 2>&1; then
    record release.fixtures green "release provenance, omission, and false-green regressions pass"
  else
    record release.fixtures red "release gate fixtures failed" "read $PACKAGE_LOG; run scripts/test-release-gates.sh"
  fi
fi

if selected boundary.source; then
  if scripts/check-play-boundary.sh source --json >/dev/null 2>&1; then
    record boundary.source green "single-product source boundary is clean"
  else
    record boundary.source red "source boundary violated" "run scripts/check-play-boundary.sh source --json"
  fi
fi

if selected scope.docs; then
  docs_hits=$(grep -RInE 'Nexus|Nexidex|phosphor[.]nexus|dev[.]nexus[.]mobile|NEXUS_BINDER|agent surface|agent harness|agent entry|agent control' \
    README.md HANDOFF.md vision spec docs \
    --exclude-dir=dev --exclude='PUBLIC-RELEASE-DIVERGENCE.md' 2>/dev/null | head -20 || true)
  if [ -z "$docs_hits" ]; then
    record scope.docs green "active product docs make no retired integration promises"
  else
    record scope.docs red "retired product language remains in active docs" "remove or historicize: $docs_hits"
  fi
fi

if selected scope.source; then
  source_hits=$(grep -RInE 'Pm3AdminProvider|NexusBinder|Nexus.*Runtime|NEXUS_BINDER|nexus-status|nexus-grant|nexus-revoke|state-get|state-watch|action-run|audit-list|audit-export' \
    app/src/main app/src/debug dev/pm3 app/build.gradle.kts 2>/dev/null | head -20 || true)
  if [ -z "$source_hits" ]; then
    record scope.source green "runtime and developer CLI contain no retired product control surface"
  else
    record scope.source red "retired control symbols remain" "remove: $source_hits"
  fi
fi

if selected privacy.tracking; then
  tracking_hits=$(grep -RInEi 'firebase|crashlytics|sentry|analytics|telemetry|datadog|newrelic|appcenter|advertising[_.-]?id|installation[_.-]?id' \
    app/src app/build.gradle.kts gradle/libs.versions.toml rust/Cargo.toml relay/Cargo.toml 2>/dev/null | head -20 || true)
  if [ -z "$tracking_hits" ]; then
    record privacy.tracking green "no analytics, behavior tracking, advertising ID, or reporting SDK"
  else
    record privacy.tracking red "tracking or reporting marker found" "inspect and remove: $tracking_hits"
  fi
fi

if selected protected.archive; then
  protected_dir="docs/dev/archive/2026-08-05-scope-reset/protected"
  if [ -f "$protected_dir/SHA256SUMS" ] && (cd "$protected_dir" && sha256sum -c SHA256SUMS >/dev/null 2>&1); then
    record protected.archive green "all protected inputs remain byte-identical"
  else
    record protected.archive red "protected archive hash mismatch" "restore the protected archive from phase 00"
  fi
fi

if selected nopython; then
  py_tracked=$(git ls-files '*.py' | head -5)
  py_called=$(grep -RIlE '(^|[^A-Za-z_])python(2|3)?([[:space:]]|$)' scripts app/build.gradle.kts build.gradle.kts \
    --exclude='ship-check.sh' 2>/dev/null | head -5 || true)
  if [ -z "$py_tracked" ] && [ -z "$py_called" ]; then
    record nopython green "no tracked Python and no Python invocation"
  else
    record nopython red "Python remains: ${py_tracked}${py_called}" "use Bash, Kotlin, or Rust"
  fi
fi

if selected antistub; then
  changed=$(git status --porcelain | sed 's/^...//' | grep -E '\.(kt|kts|rs|sh)$' | grep -v '^scripts/ship-check.sh$' || true)
  hits=""
  if [ -n "$changed" ]; then
    while IFS= read -r file; do
      [ -f "$file" ] || continue
      marker=$(grep -nE '(^|[^A-Za-z])(TODO|FIXME|XXX)([^A-Za-z]|$)|not implemented|NotImplementedError' "$file" 2>/dev/null | head -3 || true)
      [ -z "$marker" ] || hits="${hits}${file}: ${marker} "
    done <<<"$changed"
  fi
  if [ -z "$hits" ]; then
    record antistub green "no stub markers in changed implementation files"
  else
    record antistub red "stub markers found" "finish or remove: $hits"
  fi
fi

SIGNING_GREEN=0
if selected signing.release || selected release.bundle; then
  missing=""
  for prefix in RELEASE PLAY_UPLOAD; do
    for suffix in STORE_FILE STORE_PASSWORD KEY_ALIAS KEY_PASSWORD CERT_SHA256; do
      name="${prefix}_${suffix}"
      [ -n "${!name:-}" ] || missing="${missing}${missing:+,}$name"
    done
  done
  if [ -z "$missing" ] && \
      gradle_profile_run production :app:verifyProductionSigning :app:verifyPlayUploadSigning; then
    SIGNING_GREEN=1
  fi
fi

PROVENANCE_GREEN=0
if selected provenance.release || selected release.bundle; then
  if gradle_run :app:verifyReleaseProvenance; then PROVENANCE_GREEN=1; fi
fi

if selected signing.release; then
  if [ "$SIGNING_GREEN" -eq 1 ]; then
    record signing.release green "direct APK and Play upload signing identities verify independently"
  else
    record signing.release red "release signing is unavailable or unverified: $missing" "provide every approved RELEASE_* and PLAY_UPLOAD_* input, then run ./gradlew :app:verifyProductionSigning :app:verifyPlayUploadSigning"
  fi
fi

if selected provenance.release; then
  if [ "$PROVENANCE_GREEN" -eq 1 ]; then
    record provenance.release green "release source and sibling engine are clean and commit-identical to v2.0.0"
  else
    record provenance.release red "release provenance is unavailable or invalid" "read $GRADLE_LOG; commit the final tree, obtain tag approval, then run ./gradlew :app:verifyReleaseProvenance"
  fi
fi

if selected release.bundle; then
  if [ "$QUICK" -eq 1 ]; then
    record release.bundle skip "skipped (--quick)"
  elif [ "$SIGNING_GREEN" -ne 1 ] || [ "$PROVENANCE_GREEN" -ne 1 ]; then
    blockers=""
    [ "$SIGNING_GREEN" -eq 1 ] || blockers="signing.release"
    if [ "$PROVENANCE_GREEN" -ne 1 ]; then
      blockers="${blockers}${blockers:+, }provenance.release"
    fi
    record release.bundle red "canonical release package blocked by $blockers" "satisfy every named prerequisite; a required artifact gate cannot be skipped"
  else
    release_stage="$(mktemp -d "$SCRATCH_ROOT/phosphor-ship-release.XXXXXX")"
    production_apk="$release_stage/app-production.apk"
    play_aab="$release_stage/app-play-upload.aab"
    if gradle_profile_run production clean :app:assembleRelease && \
        cp app/build/outputs/apk/release/app-release.apk "$production_apk" && \
        gradle_profile_run play-upload clean :app:bundleRelease :app:validateReleaseBundle :app:checkPlayBoundary && \
        cp app/build/outputs/bundle/release/app-release.aab "$play_aab"; then
      release_manifest=$(find app/build/intermediates -path '*release*' -name AndroidManifest.xml -type f 2>/dev/null | sort | tail -n1 || true)
      release_dependencies="app/build/reports/play-boundary/releaseRuntimeClasspath.txt"
      if [ -n "$release_manifest" ] && [ -f "$release_dependencies" ] && \
          scripts/check-play-boundary.sh artifact --json \
            --artifact "$production_apk" --manifest "$release_manifest" --dependencies "$release_dependencies" \
            >"$PACKAGE_LOG" 2>&1 && \
          PHOSPHOR_SIGNING_PROFILE=production scripts/package-release.sh --json \
            --apk "$production_apk" --aab "$play_aab" >>"$PACKAGE_LOG" 2>&1; then
        record release.bundle green "production APK and Play-upload AAB, bundle validation, 16 KiB alignment, exact sources, manifest, and SHA256SUMS pass"
      else
        record release.bundle red "canonical dual-signer release package failed" "read $GRADLE_LOG and $PACKAGE_LOG; inspect staged evidence in $release_stage"
      fi
    else
      record release.bundle red "canonical dual-signer release build failed" "read $GRADLE_LOG; inspect staged evidence in $release_stage"
    fi
  fi
fi

green=0
red=0
skipped=0
for state in "${GATE_STATES[@]}"; do
  case "$state" in green) green=$((green + 1)) ;; red) red=$((red + 1)) ;; *) skipped=$((skipped + 1)) ;; esac
done
total=$((green + red))

json_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

explicit_skip=0
if [ -n "$ONLY" ] && [ "$skipped" -gt 0 ]; then explicit_skip=$skipped; fi
failed=$((red + explicit_skip))

if [ "$JSON" -eq 1 ]; then
  if [ "$failed" -eq 0 ]; then
    printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","data":' "$TOOL" "$VERSION" "$(ts)"
  else
    printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"release_gates_failed","message":"%d selected release gates are red or incomplete","fix":"inspect data.gates and run each named fix","data":' "$TOOL" "$VERSION" "$(ts)" "$failed"
  fi
  printf '{"gates_green":%d,"gates_total":%d,"gates_skipped":%d,"gates":[' "$green" "$total" "$skipped"
  for index in "${!GATE_IDS[@]}"; do
    [ "$index" -eq 0 ] || printf ','
    printf '{"id":"%s","state":"%s","detail":"%s"' \
      "${GATE_IDS[$index]}" "${GATE_STATES[$index]}" "$(json_escape "${GATE_DETAILS[$index]}")"
    [ -z "${GATE_FIXES[$index]}" ] || printf ',"fix":"%s"' "$(json_escape "${GATE_FIXES[$index]}")"
    printf '}'
  done
  printf ']}}\n'
else
  printf '\n%d/%d selected gates green' "$green" "$total" >&2
  [ "$skipped" -eq 0 ] || printf ' (%d skipped)' "$skipped" >&2
  printf '\n' >&2
fi

[ "$failed" -eq 0 ] && exit 0 || exit 2
