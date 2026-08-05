#!/usr/bin/env bash
set -uo pipefail
export LC_ALL=C

TOOL="package-release"
TOOL_VERSION="1.0.0"
MODE="run"
JSON=0
SCRIPT_REPO="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
REPO="$SCRIPT_REPO"
DEPENDENCY_REPO=""
APK=""
AAB=""
OUTPUT=""
OUTPUT_EXPLICIT=0

json_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }
want_json() { [ "$JSON" -eq 1 ] || [ ! -t 1 ]; }

error() {
  local exit_code="$1" code="$2" message="$3" fix="$4"
  if want_json; then
    printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"%s","message":"%s","fix":"%s"}\n' \
      "$TOOL" "$TOOL_VERSION" "$(ts)" "$code" "$(json_escape "$message")" "$(json_escape "$fix")"
  else
    printf 'error: %s\nmessage: %s\nfix: %s\n' "$code" "$message" "$fix" >&2
  fi
  exit "$exit_code"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    schema) MODE="schema"; shift ;;
    -h|--help) MODE="help"; shift ;;
    --json) JSON=1; shift ;;
    --repo|--dependency-repo|--apk|--aab|--output)
      option="$1"
      [ "$#" -ge 2 ] || error 3 bad_input "$option requires a value" "run scripts/package-release.sh schema"
      case "$option" in
        --repo) REPO="$2" ;;
        --dependency-repo) DEPENDENCY_REPO="$2" ;;
        --apk) APK="$2" ;;
        --aab) AAB="$2" ;;
        --output) OUTPUT="$2"; OUTPUT_EXPLICIT=1 ;;
      esac
      shift 2 ;;
    *) error 3 bad_input "unknown argument: $1" "run scripts/package-release.sh schema" ;;
  esac
done

[ -t 1 ] || JSON=1

if [ "$MODE" = "help" ]; then
  cat <<'USAGE'
package-release: verify and package canonical Phosphor Android release artifacts

  scripts/package-release.sh [--repo PATH] [--dependency-repo PATH]
      [--apk PATH] [--aab PATH] [--output PATH] [--json]
  scripts/package-release.sh schema
USAGE
  exit 0
fi

if [ "$MODE" = "schema" ]; then
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","data":{"schema":{"arguments":["--repo PATH","--dependency-repo PATH","--apk PATH","--aab PATH","--output PATH","--json","schema"],"outputs":["production-signed APK","Play-upload-signed AAB","combined exact-source archive","BUILD-MANIFEST.json","SHA256SUMS"],"checks":["clean exact-tag provenance","APK uses RELEASE_CERT_SHA256","AAB uses PLAY_UPLOAD_CERT_SHA256","bundletool validation","package and version identity","embedded commit identity","APK 16 KiB ZIP alignment","every packaged ELF has 16 KiB LOAD alignment","checksums verify"],"exit_codes":{"0":"release package verified","2":"required release evidence unavailable or invalid","3":"bad input","4":"tool failure"}}}}\n' \
    "$TOOL" "$TOOL_VERSION" "$(ts)"
  exit 0
fi

REPO=$(cd "$REPO" 2>/dev/null && pwd) || \
  error 2 repository_unavailable "release repository is unavailable: $REPO" "provide a readable release checkout"
[ -n "$DEPENDENCY_REPO" ] || DEPENDENCY_REPO="$REPO/../phosphor"
DEPENDENCY_REPO=$(cd "$DEPENDENCY_REPO" 2>/dev/null && pwd) || \
  error 2 dependency_unavailable "path dependency repository is unavailable: $DEPENDENCY_REPO" "provide the exact sibling engine checkout"

PRODUCT_VERSION=$(sed -n 's/^val appVersion = "\([^"]*\)"/\1/p' "$REPO/app/build.gradle.kts" | head -1)
[ -n "$PRODUCT_VERSION" ] || error 4 version_parse_failed "could not read appVersion from app/build.gradle.kts" "restore the canonical Gradle version declaration"
TAG="v${PRODUCT_VERSION}"
PROVENANCE_JSON=$(
  "$SCRIPT_REPO/scripts/check-release-provenance.sh" \
    --repo "$REPO" --version "$PRODUCT_VERSION" --dependency-repo "$DEPENDENCY_REPO" --json
) || exit $?
printf '%s\n' "$PROVENANCE_JSON" | jq -e '.status == "ok"' >/dev/null 2>&1 || \
  error 4 provenance_protocol_error "release provenance returned an invalid envelope" "run scripts/check-release-provenance.sh directly"
COMMIT=$(printf '%s\n' "$PROVENANCE_JSON" | jq -r '.data.commit')
SHORT_COMMIT=$(printf '%s\n' "$PROVENANCE_JSON" | jq -r '.data.short_commit')
DEPENDENCY_COMMIT=$(printf '%s\n' "$PROVENANCE_JSON" | jq -r '.data.path_dependency.commit')
DEPENDENCY_SHORT=${DEPENDENCY_COMMIT:0:12}

[ -n "$APK" ] || APK="$REPO/app/build/outputs/apk/release/app-release.apk"
[ -n "$AAB" ] || AAB="$REPO/app/build/outputs/bundle/release/app-release.aab"
[ -s "$APK" ] || error 2 artifact_unavailable "signed release APK is missing: $APK" "run ./gradlew :app:assembleRelease"
[ -s "$AAB" ] || error 2 artifact_unavailable "signed release AAB is missing: $AAB" "run ./gradlew :app:bundleRelease"

# shellcheck disable=SC1091
[ -f "$SCRIPT_REPO/scripts/env.sh" ] && source "$SCRIPT_REPO/scripts/env.sh"
BUILD_TOOLS="${ANDROID_HOME:-$REPO/.toolchain/Sdk}/build-tools/36.0.0"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"
AAPT2="$BUILD_TOOLS/aapt2"
for tool in "$APKSIGNER" "$ZIPALIGN" "$AAPT2" keytool jarsigner readelf unzip strings tar gzip jq sha256sum; do
  if [[ "$tool" = */* ]]; then
    [ -x "$tool" ] || error 2 tool_unavailable "required release tool is unavailable: $tool" "run scripts/bootstrap-android.sh"
  else
    command -v "$tool" >/dev/null 2>&1 || error 2 tool_unavailable "required release tool is unavailable: $tool" "install $tool and rerun the release gate"
  fi
done

SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
WORK=$(mktemp -d "$SCRATCH_ROOT/phosphor-release-package.XXXXXX") || \
  error 4 scratch_failure "could not create release scratch directory" "check JCODE_SCRATCH_DIR permissions"
trap 'rm -rf "$WORK"' EXIT

APK_VERIFY="$WORK/apksigner.txt"
"$APKSIGNER" verify --verbose --print-certs "$APK" >"$APK_VERIFY" 2>&1 || \
  error 2 apk_signature_invalid "release APK signature verification failed" "inspect $APK_VERIFY and rebuild with the approved signer"
APK_CERT=$(awk -F': ' '/certificate SHA-256 digest:/{print tolower($2); exit}' "$APK_VERIFY" | tr -d ':[:space:]')
[ -n "$APK_CERT" ] || error 4 signer_parse_failed "could not read the APK signer fingerprint" "inspect $APK_VERIFY"
EXPECTED_APK_CERT=$(printf '%s' "${RELEASE_CERT_SHA256:-}" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')
[[ "$EXPECTED_APK_CERT" =~ ^[0-9a-f]{64}$ ]] || \
  error 2 signing_evidence_unavailable "RELEASE_CERT_SHA256 is missing or invalid" "provide the approved Ben-controlled direct APK certificate fingerprint"
[ "$APK_CERT" = "$EXPECTED_APK_CERT" ] || \
  error 2 apk_signer_mismatch "APK signer $APK_CERT differs from RELEASE_CERT_SHA256 $EXPECTED_APK_CERT" "build the direct APK with PHOSPHOR_SIGNING_PROFILE=production"

AAB_VERIFY="$WORK/jarsigner.txt"
jarsigner -verify -verbose -certs "$AAB" >"$AAB_VERIFY" 2>&1 || \
  error 2 aab_signature_invalid "release AAB signature verification failed" "inspect $AAB_VERIFY and rebuild with the approved signer"
grep -F 'jar verified.' "$AAB_VERIFY" >/dev/null || \
  error 2 aab_signature_invalid "release AAB is not verifiably signed" "inspect $AAB_VERIFY and rebuild with the approved signer"
AAB_CERT=$(keytool -printcert -jarfile "$AAB" 2>/dev/null | awk -F': ' '/SHA256:/{print tolower($2); exit}' | tr -d ':[:space:]')
[ -n "$AAB_CERT" ] || error 2 aab_signature_invalid "could not read the AAB signer fingerprint" "run keytool -printcert -jarfile on the AAB"
EXPECTED_AAB_CERT=$(printf '%s' "${PLAY_UPLOAD_CERT_SHA256:-}" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')
[[ "$EXPECTED_AAB_CERT" =~ ^[0-9a-f]{64}$ ]] || \
  error 2 signing_evidence_unavailable "PLAY_UPLOAD_CERT_SHA256 is missing or invalid" "provide the approved Google Play upload certificate fingerprint"
[ "$AAB_CERT" = "$EXPECTED_AAB_CERT" ] || \
  error 2 aab_signer_mismatch "AAB signer $AAB_CERT differs from PLAY_UPLOAD_CERT_SHA256 $EXPECTED_AAB_CERT" "build the Play bundle with PHOSPHOR_SIGNING_PROFILE=play-upload"

"$ZIPALIGN" -c -P 16 -v 4 "$APK" >"$WORK/zipalign.txt" 2>&1 || \
  error 2 zip_alignment_invalid "release APK does not satisfy 16 KiB ZIP alignment" "rebuild with the pinned Android Gradle Plugin and inspect $WORK/zipalign.txt"

mkdir -p "$WORK/apk" "$WORK/aab"
unzip -q "$APK" -d "$WORK/apk" || error 4 archive_failure "could not unpack the release APK" "rebuild the APK"
unzip -q "$AAB" -d "$WORK/aab" || error 4 archive_failure "could not unpack the release AAB" "rebuild the AAB"
ELF_COUNT=0
while IFS= read -r -d '' library; do
  ELF_COUNT=$((ELF_COUNT + 1))
  LOAD_COUNT=0
  while IFS= read -r alignment; do
    LOAD_COUNT=$((LOAD_COUNT + 1))
    alignment_value=$((alignment))
    [ "$alignment_value" -ge 16384 ] || \
      error 2 elf_alignment_invalid "$(basename "$library") has LOAD alignment $alignment" "rebuild every native library for 16 KiB pages"
  done < <(readelf -lW "$library" | awk '$1 == "LOAD" {print $NF}')
  [ "$LOAD_COUNT" -gt 0 ] || error 2 elf_invalid "$(basename "$library") has no ELF LOAD segments" "rebuild the native library"
done < <(find "$WORK/apk/lib" -type f -name '*.so' -print0 2>/dev/null)
[ "$ELF_COUNT" -gt 0 ] || error 2 native_evidence_missing "release APK contains no native libraries" "inspect the Gradle JNI source-set configuration"

BADGING=$("$AAPT2" dump badging "$APK" 2>/dev/null) || \
  error 4 manifest_parse_failed "could not read APK package metadata" "inspect the release APK with aapt2"
PACKAGE_NAME=$(printf '%s\n' "$BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)
VERSION_CODE=$(printf '%s\n' "$BADGING" | sed -n "s/^package:.* versionCode='\([^']*\)'.*/\1/p" | head -1)
VERSION_NAME=$(printf '%s\n' "$BADGING" | sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" | head -1)
[ "$PACKAGE_NAME" = "dev.phosphor.mobil3" ] || \
  error 2 package_mismatch "release APK package is $PACKAGE_NAME" "build the canonical production application ID"
[ "$VERSION_NAME" = "$PRODUCT_VERSION" ] || \
  error 2 version_mismatch "release APK version $VERSION_NAME differs from tag $TAG" "align appVersion, the Git tag, and artifact metadata"

find "$WORK/apk" -type f -name '*.dex' -print0 | xargs -0 strings >"$WORK/apk-dex-strings.txt"
find "$WORK/aab" -type f -name '*.dex' -print0 | xargs -0 strings >"$WORK/aab-dex-strings.txt"
grep -F "$SHORT_COMMIT" "$WORK/apk-dex-strings.txt" >/dev/null || \
  error 2 embedded_commit_missing "release APK does not identify commit $SHORT_COMMIT" "retain BuildConfig.BUILD_COMMIT in packaged runtime metadata"
grep -F "$SHORT_COMMIT" "$WORK/aab-dex-strings.txt" >/dev/null || \
  error 2 embedded_commit_missing "release AAB does not identify commit $SHORT_COMMIT" "retain BuildConfig.BUILD_COMMIT in packaged runtime metadata"

"$REPO/gradlew" --no-daemon :app:validateBundle "-PphosphorBundle=$AAB" >"$WORK/bundletool.txt" 2>&1 || \
  error 2 bundle_validation_failed "bundletool rejected the release AAB" "inspect $WORK/bundletool.txt and rebuild the bundle"

[ -n "$OUTPUT" ] || OUTPUT="$REPO/app/build/outputs/release-package/$TAG"
if [ -e "$OUTPUT" ] && [ -n "$(find "$OUTPUT" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]; then
  if [ "$OUTPUT_EXPLICIT" -eq 1 ]; then
    error 2 output_not_empty "release output already contains files: $OUTPUT" "move the prior package aside or select a new --output directory"
  fi
  rm -rf "$OUTPUT" || error 4 output_failure "could not replace generated release output: $OUTPUT" "check build-directory permissions"
fi
mkdir -p "$OUTPUT" || error 4 output_failure "could not create release output: $OUTPUT" "choose a writable output directory"

APK_NAME="phosphor-mobil3-${PRODUCT_VERSION}.apk"
AAB_NAME="phosphor-mobil3-${PRODUCT_VERSION}.aab"
SOURCE_NAME="phosphor-mobil3-${PRODUCT_VERSION}-source.tar.gz"
install -m 0644 "$APK" "$OUTPUT/$APK_NAME"
install -m 0644 "$AAB" "$OUTPUT/$AAB_NAME"

SOURCE_ROOT="$WORK/source"
mkdir -p "$SOURCE_ROOT/phosphor-mobil3-$PRODUCT_VERSION" "$SOURCE_ROOT/phosphor-engine-$DEPENDENCY_SHORT"
git -C "$REPO" archive "$TAG" | tar -x -C "$SOURCE_ROOT/phosphor-mobil3-$PRODUCT_VERSION" || \
  error 4 source_archive_failed "could not export mobile source from $TAG" "verify the release tag"
git -C "$DEPENDENCY_REPO" archive "$DEPENDENCY_COMMIT" | tar -x -C "$SOURCE_ROOT/phosphor-engine-$DEPENDENCY_SHORT" || \
  error 4 source_archive_failed "could not export engine source from $DEPENDENCY_COMMIT" "verify the path dependency commit"
SOURCE_DATE_EPOCH=$(git -C "$REPO" show -s --format=%ct "$COMMIT")
tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner \
  -C "$SOURCE_ROOT" -cf - . | gzip -n >"$OUTPUT/$SOURCE_NAME" || \
  error 4 source_archive_failed "could not create the combined source archive" "check tar and gzip"

APK_SHA=$(sha256sum "$OUTPUT/$APK_NAME" | awk '{print $1}')
AAB_SHA=$(sha256sum "$OUTPUT/$AAB_NAME" | awk '{print $1}')
SOURCE_SHA=$(sha256sum "$OUTPUT/$SOURCE_NAME" | awk '{print $1}')
GRADLE_VERSION=$(sed -n 's#.*gradle-\([0-9][0-9.]*\)-bin.zip#\1#p' "$REPO/gradle/wrapper/gradle-wrapper.properties")
AGP_VERSION=$(awk -F'"' '$1 ~ /^agp / {print $2}' "$REPO/gradle/libs.versions.toml")
KOTLIN_VERSION=$(awk -F'"' '$1 ~ /^kotlin / {print $2}' "$REPO/gradle/libs.versions.toml")
NDK_VERSION=$(awk -F'"' '$1 ~ /^ndk / {print $2}' "$REPO/gradle/libs.versions.toml")
RUST_VERSION=$(cd "$REPO/rust" && rustc --version)
GENERATED_AT=$(ts)

jq -n \
  --arg schema "phosphor.release-manifest/1" \
  --arg generated_at "$GENERATED_AT" \
  --arg tag "$TAG" \
  --arg version "$PRODUCT_VERSION" \
  --arg package "$PACKAGE_NAME" \
  --arg version_code "$VERSION_CODE" \
  --arg commit "$COMMIT" \
  --arg engine_commit "$DEPENDENCY_COMMIT" \
  --arg apk_signer_sha256 "$APK_CERT" \
  --arg aab_signer_sha256 "$AAB_CERT" \
  --arg gradle "$GRADLE_VERSION" \
  --arg agp "$AGP_VERSION" \
  --arg kotlin "$KOTLIN_VERSION" \
  --arg ndk "$NDK_VERSION" \
  --arg rust "$RUST_VERSION" \
  --arg apk "$APK_NAME" --arg apk_sha "$APK_SHA" \
  --arg aab "$AAB_NAME" --arg aab_sha "$AAB_SHA" \
  --arg source "$SOURCE_NAME" --arg source_sha "$SOURCE_SHA" \
  '{schema:$schema,generated_at:$generated_at,release:{tag:$tag,version:$version,package:$package,version_code:$version_code,commit:$commit,path_dependency_commit:$engine_commit,apk_signer_sha256:$apk_signer_sha256,aab_upload_signer_sha256:$aab_signer_sha256},toolchains:{gradle:$gradle,android_gradle_plugin:$agp,kotlin:$kotlin,ndk:$ndk,rust:$rust},artifacts:{apk:{file:$apk,sha256:$apk_sha},aab:{file:$aab,sha256:$aab_sha},source:{file:$source,sha256:$source_sha}}}' \
  >"$OUTPUT/BUILD-MANIFEST.json" || error 4 manifest_write_failed "could not write BUILD-MANIFEST.json" "check jq and output permissions"

(
  cd "$OUTPUT" || exit 1
  sha256sum "$APK_NAME" "$AAB_NAME" "$SOURCE_NAME" BUILD-MANIFEST.json >SHA256SUMS
  sha256sum -c SHA256SUMS >/dev/null
) || error 4 checksum_failure "release checksum generation or verification failed" "inspect $OUTPUT/SHA256SUMS"

if want_json; then
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","data":{"output":"%s","tag":"%s","commit":"%s","path_dependency_commit":"%s","package":"%s","product_version":"%s","version_code":"%s","apk_signer_sha256":"%s","aab_upload_signer_sha256":"%s","native_libraries":%d,"artifacts":["%s","%s","%s","BUILD-MANIFEST.json","SHA256SUMS"]}}\n' \
    "$TOOL" "$TOOL_VERSION" "$(ts)" "$(json_escape "$OUTPUT")" "$TAG" "$COMMIT" "$DEPENDENCY_COMMIT" \
    "$PACKAGE_NAME" "$PRODUCT_VERSION" "$VERSION_CODE" "$APK_CERT" "$AAB_CERT" "$ELF_COUNT" "$APK_NAME" "$AAB_NAME" "$SOURCE_NAME"
else
  printf 'release package verified\n  output: %s\n  tag: %s\n  commit: %s\n  APK signer: %s\n  AAB upload signer: %s\n' \
    "$OUTPUT" "$TAG" "$COMMIT" "$APK_CERT" "$AAB_CERT"
fi
