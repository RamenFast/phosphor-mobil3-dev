#!/usr/bin/env bash
# check-play-boundary.sh — prove that the compiled Play surface contains no Fortress/private edge.
set -euo pipefail

TOOL="check-play-boundary"
VERSION="2.0.0"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="all"
ARTIFACT="$REPO/app/build/outputs/bundle/playRelease/app-play-release.aab"
MANIFEST=""
DEPENDENCIES=""
JSON_FORCE=0

now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
escape() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr -d '\n'; }
want_json() { [ "$JSON_FORCE" -eq 1 ] || [ ! -t 1 ]; }
fail() {
  local code="$1" error="$2" message="$3" fix="$4"
  if want_json; then
    printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"%s","message":"%s","fix":"%s"}\n' \
      "$TOOL" "$VERSION" "$(now)" "$(escape "$error")" "$(escape "$message")" "$(escape "$fix")"
  else
    printf 'error: %s\nmessage: %s\nfix: %s\n' "$error" "$message" "$fix" >&2
  fi
  exit "$code"
}
ok() {
  local data="$1" human="$2"
  if want_json; then
    printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","data":%s}\n' \
      "$TOOL" "$VERSION" "$(now)" "$data"
  else
    printf '%s\n' "$human"
  fi
}

while [ $# -gt 0 ]; do
  case "$1" in
    schema|source|artifact|all) MODE="$1" ;;
    --artifact) shift; [ $# -gt 0 ] || fail 3 bad_input "--artifact needs a path" "pass --artifact <play.apk|play.aab>"; ARTIFACT="$1" ;;
    --manifest) shift; [ $# -gt 0 ] || fail 3 bad_input "--manifest needs a path" "pass --manifest <merged-AndroidManifest.xml>"; MANIFEST="$1" ;;
    --dependencies) shift; [ $# -gt 0 ] || fail 3 bad_input "--dependencies needs a path" "pass --dependencies <play-runtime-classpath.txt>"; DEPENDENCIES="$1" ;;
    --json) JSON_FORCE=1 ;;
    -h|--help) MODE="schema" ;;
    *) fail 3 bad_input "unknown argument '$1'" "use schema, source, artifact, or all; optional --artifact/--manifest/--dependencies" ;;
  esac
  shift
done

if [ "$MODE" = schema ]; then
  ok '{"usage":"check-play-boundary.sh [schema|source|artifact|all] [--artifact PATH] [--manifest PATH] [--dependencies PATH] [--json]","modes":{"source":"checks source-set and build-graph boundaries","artifact":"checks compiled archive, merged manifest, and runtime classpath","all":"runs source and artifact checks"},"exits":{"0":"success","2":"required artifact/evidence unavailable","3":"bad input","4":"boundary violation or runtime failure"},"forbidden_classes":["Shizuku/ADB sidecar/shell capture","Fortress Binder/overlay/private Nexus entry"],"fix":"build bundlePlayRelease, then run all"}' \
    'check-play-boundary: schema printed'
  exit 0
fi

SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
WORK="$(mktemp -d "$SCRATCH_ROOT/phosphor-play-boundary.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
HITS="$WORK/hits.txt"
: > "$HITS"
SOURCE_CHECKS=0
ARTIFACT_CHECKS=0
TRUSTED_RUNTIME_EXEMPTIONS=0

record_hits() {
  local label="$1"
  shift
  local output status
  set +e
  output=$("$@" 2>&1)
  status=$?
  set -e
  if [ "$status" -eq 0 ]; then
    if [ -n "$output" ]; then
      printf '[%s]\n%s\n' "$label" "$output" >> "$HITS"
    fi
    return
  fi
  if [ "$status" -eq 1 ] && [ "$(basename "$1")" = grep ]; then
    return
  fi
  fail 4 scanner_command_failed "Boundary check '$label' could not run: $output" "repair the named evidence path or scanner dependency and retry"
}

check_source() {
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  [ -d "$REPO/app/src/play" ] || fail 4 play_source_missing "Play source set is missing" "create app/src/play and keep first-release agent entries absent"
  [ -d "$REPO/app/src/fortress" ] || fail 4 fortress_source_missing "Fortress source set is missing" "create app/src/fortress for elevated implementation"
  grep -q 'create("play")' "$REPO/app/build.gradle.kts" || fail 4 flavor_missing "Play flavor is not declared" "declare distribution flavors in app/build.gradle.kts"
  grep -q 'create("fortress")' "$REPO/app/build.gradle.kts" || fail 4 flavor_missing "Fortress flavor is not declared" "declare distribution flavors in app/build.gradle.kts"
  grep -q 'applicationId = "dev.phosphor.mobil3.fortress"' "$REPO/app/build.gradle.kts" || \
    fail 4 fortress_package_wrong "Fortress package identity is not fixed" "use dev.phosphor.mobil3.fortress"

  local source_regex='rikka[.]shizuku|moe[.]shizuku|Shizuku|SYSTEM_ALERT_WINDOW|[/]data[/]local[/]tmp|dev[.]nexus[.]mobile|NexusControlService|NexidexBinder|CAPTURE_AUDIO_OUTPUT'
  record_hits source_private_marker grep -RInE "$source_regex" "$REPO/app/src/main" "$REPO/app/src/play"
  record_hits play_symlink find "$REPO/app/src/play" -type l -print
  SOURCE_CHECKS=$((SOURCE_CHECKS + 5))
}

resolve_artifact_evidence() {
  [ -f "$ARTIFACT" ] || fail 2 artifact_unavailable "Play artifact not found at $ARTIFACT" "build it with ./gradlew :app:bundlePlayRelease and explicit PLAY_UPLOAD_* signing inputs"
  command -v unzip >/dev/null || fail 2 dependency_unavailable "unzip is unavailable" "install unzip and retry"
  command -v zipinfo >/dev/null || fail 2 dependency_unavailable "zipinfo is unavailable" "install unzip with zipinfo support and retry"
  command -v strings >/dev/null || fail 2 dependency_unavailable "strings is unavailable" "install binutils and retry"

  if [ -z "$MANIFEST" ]; then
    MANIFEST=$(find "$REPO/app/build/intermediates" -path '*playRelease*' -name AndroidManifest.xml -type f 2>/dev/null | sort | tail -n1 || true)
  fi
  if [ -z "$MANIFEST" ] || [ ! -f "$MANIFEST" ]; then
    fail 2 manifest_unavailable "Merged Play manifest is unavailable" "run :app:processPlayReleaseMainManifest or pass --manifest"
  fi

  if [ -z "$DEPENDENCIES" ]; then
    DEPENDENCIES="$WORK/playReleaseRuntimeClasspath.txt"
    if ! (cd "$REPO" && ./gradlew :app:dependencies --configuration playReleaseRuntimeClasspath --console=plain) > "$DEPENDENCIES" 2>&1; then
      fail 4 dependency_report_failed "Could not resolve the Play runtime classpath" "read $DEPENDENCIES and repair Gradle dependency resolution"
    fi
  fi
  [ -f "$DEPENDENCIES" ] || fail 2 dependencies_unavailable "Play runtime dependency report is unavailable" "pass --dependencies or allow the scanner to run Gradle"
}

check_artifact() {
  resolve_artifact_evidence
  if ! unzip -Z1 "$ARTIFACT" > "$WORK/archive-list.txt" 2> "$WORK/archive-list-error.txt"; then
    fail 4 archive_inspection_failed "Play archive member listing failed: $(cat "$WORK/archive-list-error.txt")" "provide a valid APK or AAB and retry"
  fi
  if grep -Eq '(^/|(^|/)\.\.(/|$))' "$WORK/archive-list.txt"; then
    fail 4 archive_path_escape "Play archive contains an absolute or parent-traversing entry" "repair the packaging inputs before inspecting or publishing the archive"
  fi
  if ! zipinfo -l "$ARTIFACT" > "$WORK/archive-details.txt" 2> "$WORK/archive-details-error.txt"; then
    fail 4 archive_inspection_failed "Play archive mode inspection failed: $(cat "$WORK/archive-details-error.txt")" "provide a valid APK or AAB and retry"
  fi
  if grep -Eq '^l[rwx-]{9}[[:space:]]' "$WORK/archive-details.txt"; then
    fail 4 archive_symlink "Play archive contains a symbolic-link entry" "remove archive symlinks; Play artifacts must contain only self-contained regular files and directories"
  fi
  mkdir -p "$WORK/archive"
  if ! unzip -qq "$ARTIFACT" -d "$WORK/archive" 2> "$WORK/archive-extract-error.txt"; then
    fail 4 archive_extraction_failed "Play archive extraction failed: $(cat "$WORK/archive-extract-error.txt")" "repair the archive and retry before publication"
  fi
  if find "$WORK/archive" -type l -print -quit | grep -q .; then
    fail 4 archive_symlink "Play archive extracted a symbolic link" "remove archive symlinks; Play artifacts must contain only self-contained regular files and directories"
  fi
  : > "$WORK/trusted-runtime-paths.txt"

  # Android's official libc++ runtime contains `/data/local/tmp` as an internal
  # test/runtime string. Exempt only byte-for-byte outputs derived from this
  # build's pinned NDK, never arbitrary files sharing the runtime filename.
  local runtime_candidates=(
    "$WORK/archive/base/lib/arm64-v8a/libc++_shared.so:strip-unneeded"
    "$WORK/archive/lib/arm64-v8a/libc++_shared.so:strip-unneeded"
    "$WORK/archive/BUNDLE-METADATA/com.android.tools.build.debugsymbols/arm64-v8a/libc++_shared.so.sym:strip-debug"
  )
  local has_runtime=0 candidate
  for candidate in "${runtime_candidates[@]}"; do
    [ -f "${candidate%%:*}" ] && has_runtime=1
  done
  if [ "$has_runtime" -eq 1 ]; then
    local ndk_root="${ANDROID_NDK_HOME:-}"
    if [ -z "$ndk_root" ]; then
      local sdk_root="${ANDROID_HOME:-}"
      if [ -z "$sdk_root" ] && [ -f "$REPO/local.properties" ]; then
        sdk_root=$(sed -n 's/^sdk\.dir=//p' "$REPO/local.properties" | tail -n1)
      fi
      local ndk_version
      ndk_version=$(sed -n 's/^ndk = "\([^"]*\)"/\1/p' "$REPO/gradle/libs.versions.toml" | head -n1)
      [ -n "$sdk_root" ] && [ -n "$ndk_version" ] && ndk_root="$sdk_root/ndk/$ndk_version"
    fi
    local ndk_lib="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
    local llvm_strip="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
    if [ ! -f "$ndk_lib" ] || [ ! -x "$llvm_strip" ]; then
      fail 2 ndk_runtime_unavailable "Pinned NDK libc++ is unavailable for Play runtime verification" "install the version pinned in gradle/libs.versions.toml or set ANDROID_NDK_HOME"
    fi
    local artifact transform expected
    for candidate in "${runtime_candidates[@]}"; do
      artifact="${candidate%%:*}"
      transform="${candidate##*:}"
      [ -f "$artifact" ] || continue
      expected="$WORK/trusted-$transform.so"
      cp "$ndk_lib" "$expected" || fail 4 runtime_derivation_failed "Could not stage pinned NDK libc++ for comparison" "check scratch storage and retry"
      "$llvm_strip" "--$transform" "$expected" || fail 4 runtime_derivation_failed "Could not derive the pinned NDK libc++ $transform output" "repair the pinned NDK installation and retry"
      if ! cmp -s "$expected" "$artifact"; then
        fail 4 untrusted_android_runtime "Packaged libc++ does not match the pinned NDK's $transform output" "remove substituted native runtimes and rebuild from the pinned NDK"
      fi
      printf '%s\n' "$artifact" >> "$WORK/trusted-runtime-paths.txt"
      TRUSTED_RUNTIME_EXEMPTIONS=$((TRUSTED_RUNTIME_EXEMPTIONS + 1))
    done
  fi
  # Scan every member independently. Concatenating `unzip -p` output can join
  # the tail of one member to the head of the next and invent forbidden paths.
  : > "$WORK/archive-strings.txt"
  while IFS= read -r -d '' candidate; do
    if ! grep -Fxq "$candidate" "$WORK/trusted-runtime-paths.txt"; then
      if ! strings -a -f "$candidate" >> "$WORK/archive-strings.txt"; then
        fail 4 archive_string_scan_failed "Could not scan archive member $candidate" "repair binutils or the archive member and retry"
      fi
    fi
  done < <(find "$WORK/archive" -type f -print0)

  local archive_regex='dev[./]phosphor[./]mobil3[./]fortress|dev[.]phosphor[.]mobil3[.]fortress|rikka[./]shizuku|moe[./]shizuku|Shizuku|[/]data[/]local[/]tmp|dev[.]nexus[.]mobile|NexusControlService|NexidexBinder|CAPTURE_AUDIO_OUTPUT|SYSTEM_ALERT_WINDOW'
  local manifest_regex='SYSTEM_ALERT_WINDOW|dev[.]phosphor[.]mobil3[.]fortress|dev[.]nexus[.]mobile|NexusControlService|NexidexBinder|shizuku'
  local dependency_regex='shizuku|rikka|adb[-_. ]sidecar|nexus[-_. ]mobile'
  local private_endpoint_regex='100[.](66|102|114)[.]|thinkcenter|interserve|2bmillerb'

  record_hits archive_private_marker grep -InE "$archive_regex" "$WORK/archive-list.txt" "$WORK/archive-strings.txt"
  record_hits merged_manifest_private_marker grep -InE "$manifest_regex" "$MANIFEST"
  record_hits runtime_dependency_private_marker grep -InE "$dependency_regex" "$DEPENDENCIES"
  record_hits private_endpoint grep -InE "$private_endpoint_regex" "$WORK/archive-strings.txt"
  ARTIFACT_CHECKS=$((ARTIFACT_CHECKS + 4))
}

case "$MODE" in
  source) check_source ;;
  artifact) check_artifact ;;
  all) check_source; check_artifact ;;
  *) fail 3 bad_input "unsupported mode '$MODE'" "use schema, source, artifact, or all" ;;
esac

if [ -s "$HITS" ]; then
  cat "$HITS" >&2
  COUNT=$(grep -c '^\[' "$HITS")
  fail 4 play_boundary_violation "$COUNT Play boundary categories failed" "move elevated/private code into app/src/fortress or a Fortress-only module, rebuild Play, and retry"
fi

ok "{\"mode\":\"$MODE\",\"source_checks\":$SOURCE_CHECKS,\"artifact_checks\":$ARTIFACT_CHECKS,\"trusted_runtime_exemptions\":$TRUSTED_RUNTIME_EXEMPTIONS,\"artifact\":\"$(escape "$ARTIFACT")\",\"manifest\":\"$(escape "$MANIFEST")\"}" \
  "Play boundary clean ($SOURCE_CHECKS source checks, $ARTIFACT_CHECKS artifact checks)"
