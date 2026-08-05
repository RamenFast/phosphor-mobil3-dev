#!/usr/bin/env bash
# Prove that the production artifact contains only the public Phosphor product surface.
set -euo pipefail

TOOL="check-play-boundary"
VERSION="3.1.0"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="all"
ARTIFACT="$REPO/app/build/outputs/bundle/release/app-release.aab"
MANIFEST=""
DEPENDENCIES=""
JSON_FORCE=0

now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
escape() {
  local value="${1//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/}"
  printf '%s' "$value"
}
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

while [ "$#" -gt 0 ]; do
  case "$1" in
    schema|source|artifact|all) MODE="$1" ;;
    --artifact) shift; [ "$#" -gt 0 ] || fail 3 bad_input "--artifact needs a path" "pass --artifact <app.apk|app.aab>"; ARTIFACT="$1" ;;
    --manifest) shift; [ "$#" -gt 0 ] || fail 3 bad_input "--manifest needs a path" "pass --manifest <merged-AndroidManifest.xml>"; MANIFEST="$1" ;;
    --dependencies) shift; [ "$#" -gt 0 ] || fail 3 bad_input "--dependencies needs a path" "pass --dependencies <release-runtime-classpath.txt>"; DEPENDENCIES="$1" ;;
    --json) JSON_FORCE=1 ;;
    -h|--help) MODE="schema" ;;
    *) fail 3 bad_input "unknown argument '$1'" "use schema, source, artifact, or all; optional --artifact/--manifest/--dependencies" ;;
  esac
  shift
done

if [ "$MODE" = schema ]; then
  ok '{"usage":"check-play-boundary.sh [schema|source|artifact|all] [--artifact PATH] [--manifest PATH] [--dependencies PATH] [--json]","modes":{"source":"checks the single-product source and build graph","artifact":"checks the compiled archive, merged manifest, and runtime classpath","all":"runs source and artifact checks"},"exits":{"0":"success","2":"required evidence unavailable","3":"bad input","4":"boundary violation or runtime failure"},"fix":"build bundleRelease, then run all"}' \
    'check-play-boundary: schema printed'
  exit 0
fi

SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
WORK="$(mktemp -d "$SCRATCH_ROOT/phosphor-play-boundary.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
HITS="$WORK/hits.txt"
: >"$HITS"
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
    [ -z "$output" ] || printf '[%s]\n%s\n' "$label" "$output" >>"$HITS"
    return
  fi
  if [ "$status" -eq 1 ] && [ "$(basename "$1")" = grep ]; then return; fi
  fail 4 scanner_command_failed "Boundary check '$label' could not run: $output" "repair the named evidence path or scanner dependency"
}

check_source() {
  [ ! -e "$REPO/app/src/play" ] || fail 4 legacy_source_set "The retired Play source set remains" "merge public code into main and remove app/src/play"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  [ ! -e "$REPO/app/src/fortress" ] || fail 4 legacy_source_set "The retired Fortress source set remains" "remove the retired elevated product source set"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  ! grep -q 'productFlavors' "$REPO/app/build.gradle.kts" || fail 4 flavor_graph_present "Product flavors remain in the Android build" "use the single debug/release product graph"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  grep -q 'applicationId = "dev.phosphor.mobil3"' "$REPO/app/build.gradle.kts" || fail 4 package_identity_wrong "Production package identity is not fixed" "use dev.phosphor.mobil3"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  grep -q 'applicationIdSuffix = ".debug"' "$REPO/app/build.gradle.kts" || fail 4 debug_identity_wrong "Debug package is not isolated" "give debug builds a .debug application ID suffix"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  grep -Eq 'minSdk = 29|minsdk[[:space:]]*=[[:space:]]*29' "$REPO/app/build.gradle.kts" || fail 4 minimum_sdk_wrong "Android 10 is not the declared compatibility floor" "set minSdk to API 29"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  [ ! -d "$REPO/app/src/main/jniLibs" ] || fail 4 stale_native_tree "An unmanaged main/jniLibs tree remains" "build native libraries only through Gradle cargo tasks"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))

  local retired_regex='Nexus|Nexidex|dev[.]nexus[.]mobile|Pm3AdminProvider|NEXUS_BINDER|NexusBinder|nexus-status|nexus-grant|nexus-revoke|state-get|state-watch|action-run|audit-list|audit-export|rikka[.]shizuku|moe[.]shizuku|SYSTEM_ALERT_WINDOW|CAPTURE_AUDIO_OUTPUT|POST_NOTIFICATIONS'
  local tracking_regex='firebase[-.:/]analytics|crashlytics|sentry[-.:/]|appsflyer|mixpanel|amplitude|datadog|newrelic|appcenter|advertising[_.-]?id|installation[_.-]?id'
  record_hits retired_product_surface grep -RInE "$retired_regex" "$REPO/app/src/main" "$REPO/app/src/debug" "$REPO/app/build.gradle.kts"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  record_hits tracking_surface grep -RInEi "$tracking_regex" "$REPO/app/src" "$REPO/app/build.gradle.kts" "$REPO/gradle/libs.versions.toml"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  local forced_routing_regex='bindProcessToNetwork|requestNetwork|setRemoteNetworkMode|networkMode|ACTION_REMOTE_POLICY_CHANGED|ACCESS_NETWORK_STATE|CHANGE_NETWORK_STATE'
  record_hits forced_physical_routing grep -RInE "$forced_routing_regex" "$REPO/app/src/main" "$REPO/app/src/debug"
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
  record_hits source_symlink find "$REPO/app/src" -type l -print
  SOURCE_CHECKS=$((SOURCE_CHECKS + 1))
}

resolve_artifact_evidence() {
  [ -f "$ARTIFACT" ] || fail 2 artifact_unavailable "Production artifact not found at $ARTIFACT" "build it with ./gradlew :app:bundleRelease and the selected signing inputs"
  command -v unzip >/dev/null || fail 2 dependency_unavailable "unzip is unavailable" "install unzip"
  command -v zipinfo >/dev/null || fail 2 dependency_unavailable "zipinfo is unavailable" "install zipinfo"
  command -v strings >/dev/null || fail 2 dependency_unavailable "strings is unavailable" "install binutils"

  if [ -z "$MANIFEST" ]; then
    MANIFEST=$(find "$REPO/app/build/intermediates" -path '*release*' -name AndroidManifest.xml -type f 2>/dev/null | sort | tail -n1 || true)
  fi
  if [ -z "$MANIFEST" ] || [ ! -f "$MANIFEST" ]; then
    fail 2 manifest_unavailable "Merged release manifest is unavailable" "run :app:processReleaseMainManifest or pass --manifest"
  fi

  if [ -z "$DEPENDENCIES" ]; then
    DEPENDENCIES="$WORK/releaseRuntimeClasspath.txt"
    if ! (cd "$REPO" && ./gradlew :app:dependencies --configuration releaseRuntimeClasspath --console=plain) >"$DEPENDENCIES" 2>&1; then
      fail 4 dependency_report_failed "Could not resolve the release runtime classpath" "read $DEPENDENCIES and repair dependency resolution"
    fi
  fi
  [ -f "$DEPENDENCIES" ] || fail 2 dependencies_unavailable "Release dependency report is unavailable" "pass --dependencies or allow the scanner to run Gradle"
}

check_artifact() {
  resolve_artifact_evidence
  if ! unzip -Z1 "$ARTIFACT" >"$WORK/archive-list.txt" 2>"$WORK/archive-list-error.txt"; then
    fail 4 archive_inspection_failed "Archive member listing failed: $(cat "$WORK/archive-list-error.txt")" "provide a valid APK or AAB"
  fi
  if grep -Eq '(^/|(^|/)\.\.(/|$))' "$WORK/archive-list.txt"; then
    fail 4 archive_path_escape "Archive contains an absolute or parent-traversing entry" "repair packaging inputs before publication"
  fi
  if ! zipinfo -l "$ARTIFACT" >"$WORK/archive-details.txt" 2>"$WORK/archive-details-error.txt"; then
    fail 4 archive_inspection_failed "Archive mode inspection failed: $(cat "$WORK/archive-details-error.txt")" "provide a valid APK or AAB"
  fi
  if grep -Eq '^l[rwx-]{9}[[:space:]]' "$WORK/archive-details.txt"; then
    fail 4 archive_symlink "Archive contains a symbolic-link entry" "remove archive symlinks"
  fi
  mkdir -p "$WORK/archive"
  if ! unzip -qq "$ARTIFACT" -d "$WORK/archive" 2>"$WORK/archive-extract-error.txt"; then
    fail 4 archive_extraction_failed "Archive extraction failed: $(cat "$WORK/archive-extract-error.txt")" "repair the archive"
  fi
  if find "$WORK/archive" -type l -print -quit | grep -q .; then
    fail 4 archive_symlink "Archive extracted a symbolic link" "remove archive symlinks"
  fi

  : >"$WORK/trusted-runtime-paths.txt"
  local runtime_candidates=(
    "$WORK/archive/base/lib/arm64-v8a/libc++_shared.so:strip-unneeded"
    "$WORK/archive/lib/arm64-v8a/libc++_shared.so:strip-unneeded"
    "$WORK/archive/BUNDLE-METADATA/com.android.tools.build.debugsymbols/arm64-v8a/libc++_shared.so.sym:strip-debug"
  )
  local has_runtime=0 candidate
  for candidate in "${runtime_candidates[@]}"; do [ -f "${candidate%%:*}" ] && has_runtime=1; done
  if [ "$has_runtime" -eq 1 ]; then
    local ndk_root="${ANDROID_NDK_HOME:-}"
    if [ -z "$ndk_root" ]; then
      local sdk_root="${ANDROID_HOME:-}"
      if [ -z "$sdk_root" ] && [ -f "$REPO/local.properties" ]; then sdk_root=$(sed -n 's/^sdk\.dir=//p' "$REPO/local.properties" | tail -n1); fi
      local ndk_version
      ndk_version=$(sed -n 's/^ndk = "\([^"]*\)"/\1/p' "$REPO/gradle/libs.versions.toml" | head -n1)
      [ -n "$sdk_root" ] && [ -n "$ndk_version" ] && ndk_root="$sdk_root/ndk/$ndk_version"
    fi
    local ndk_lib="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
    local llvm_strip="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
    if [ ! -f "$ndk_lib" ] || [ ! -x "$llvm_strip" ]; then
      fail 2 ndk_runtime_unavailable "Pinned NDK libc++ is unavailable" "install the pinned NDK or set ANDROID_NDK_HOME"
    fi
    local artifact transform expected
    for candidate in "${runtime_candidates[@]}"; do
      artifact="${candidate%%:*}"
      transform="${candidate##*:}"
      [ -f "$artifact" ] || continue
      expected="$WORK/trusted-$transform.so"
      cp "$ndk_lib" "$expected" || fail 4 runtime_derivation_failed "Could not stage pinned libc++" "check scratch storage"
      "$llvm_strip" "--$transform" "$expected" || fail 4 runtime_derivation_failed "Could not derive pinned libc++ output" "repair the pinned NDK"
      cmp -s "$expected" "$artifact" || fail 4 untrusted_android_runtime "Packaged libc++ does not match the pinned NDK" "remove substituted native runtimes and rebuild"
      printf '%s\n' "$artifact" >>"$WORK/trusted-runtime-paths.txt"
      TRUSTED_RUNTIME_EXEMPTIONS=$((TRUSTED_RUNTIME_EXEMPTIONS + 1))
    done
  fi

  : >"$WORK/archive-strings.txt"
  while IFS= read -r -d '' candidate; do
    if ! grep -Fxq "$candidate" "$WORK/trusted-runtime-paths.txt"; then
      strings -a -f "$candidate" >>"$WORK/archive-strings.txt" || fail 4 archive_string_scan_failed "Could not scan $candidate" "repair binutils or the archive member"
    fi
  done < <(find "$WORK/archive" -type f -print0)

  # DEX files contain third-party data tables and framework constants. Match
  # retired implementation identities here, not generic words such as
  # "nexus", "analytics", or permission names. Permissions remain a strict
  # merged-manifest check below.
  local archive_regex='dev[./]phosphor[./]mobil3[./](fortress|nexus|distribution|entitlement)|dev[.]phosphor[.]mobil3[.](fortress|nexus|distribution|entitlement)|dev[.]nexus[.]mobile|Pm3AdminProvider|NexusBinder(Service|Runtime|Protocol|Bootstrap|DispatcherOwner)|Nexus(RuntimeManager|ObservationDispatcher|AuthorityPersistence|SessionContracts)|NEXUS_BINDER|rikka[./]shizuku|moe[./]shizuku|nexus-status|state-watch|action-run|audit-export'
  local manifest_regex='SYSTEM_ALERT_WINDOW|CAPTURE_AUDIO_OUTPUT|POST_NOTIFICATIONS|dev[.]phosphor[.]mobil3[.]fortress|dev[.]nexus[.]mobile|Nexus|Nexidex|shizuku'
  local dependency_regex='firebase[-.:/]analytics|crashlytics|sentry|appsflyer|mixpanel|amplitude|datadog|newrelic|appcenter|shizuku|rikka|adb[-_. ]sidecar'
  local reporting_regex='firebaseio[.]com|appcenter[.]ms|sentry[.]io|datadoghq[.]com|newrelic[.]com|amplitude[.]com|mixpanel[.]com'
  local seeded_endpoint_regex='100[.](66|102|114)[.]|thinkcenter|interserve|2bmillerb'

  record_hits archive_forbidden_marker grep -InEi "$archive_regex" "$WORK/archive-list.txt" "$WORK/archive-strings.txt"
  record_hits merged_manifest_forbidden_marker grep -InEi "$manifest_regex" "$MANIFEST"
  record_hits runtime_dependency_forbidden_marker grep -InEi "$dependency_regex" "$DEPENDENCIES"
  record_hits reporting_endpoint grep -InEi "$reporting_regex" "$WORK/archive-strings.txt"
  record_hits seeded_private_endpoint grep -InEi "$seeded_endpoint_regex" "$WORK/archive-strings.txt"
  ARTIFACT_CHECKS=$((ARTIFACT_CHECKS + 5))
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
  fail 4 play_boundary_violation "$COUNT production-boundary categories failed" "remove the named private, control, tracking, or elevated surface and rebuild"
fi

ok "{\"mode\":\"$MODE\",\"source_checks\":$SOURCE_CHECKS,\"artifact_checks\":$ARTIFACT_CHECKS,\"trusted_runtime_exemptions\":$TRUSTED_RUNTIME_EXEMPTIONS,\"artifact\":\"$(escape "$ARTIFACT")\",\"manifest\":\"$(escape "$MANIFEST")\"}" \
  "Production boundary clean ($SOURCE_CHECKS source checks, $ARTIFACT_CHECKS artifact checks)"
