#!/usr/bin/env bash
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
WORK="$(mktemp -d "$SCRATCH_ROOT/phosphor-play-boundary-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

"$REPO/scripts/check-play-boundary.sh" schema --json | jq -e '.status == "ok" and .data.exits["2"] == "required evidence unavailable"' >/dev/null
"$REPO/scripts/check-play-boundary.sh" source --json | jq -e '.status == "ok" and .data.source_checks == 11' >/dev/null

printf '<manifest package="dev.phosphor.mobil3"><application/></manifest>\n' > "$WORK/AndroidManifest.xml"
printf 'releaseRuntimeClasspath\n+--- androidx.core:core-ktx\n' > "$WORK/dependencies.txt"
mkdir -p "$WORK/clean" "$WORK/split" "$WORK/bad" "$WORK/symlink"
# These strings legitimately occur in dependency data tables and framework
# constants. The artifact gate must reject concrete retired identities without
# treating generic vocabulary as shipped product surface.
printf 'clean play payload nexus analytics android.permission.POST_NOTIFICATIONS\n' > "$WORK/clean/classes.dex"
printf '/data/local' > "$WORK/split/first.bin"
printf '/tmp' > "$WORK/split/second.bin"
printf 'dev.phosphor.mobil3.fortress Shizuku /data/local/tmp\n' > "$WORK/bad/classes.dex"
printf 'dev.phosphor.mobil3.fortress Shizuku /data/local/tmp\n' > "$WORK/symlink/forbidden-target"
ln -s forbidden-target "$WORK/symlink/classes.dex"
printf 'not a zip archive\n' > "$WORK/corrupt.aab"
(cd "$WORK/clean" && zip -q "$WORK/clean.aab" classes.dex)
(cd "$WORK/split" && zip -q "$WORK/split.aab" first.bin second.bin)
(cd "$WORK/bad" && zip -q "$WORK/bad.aab" classes.dex)
(cd "$WORK/symlink" && zip -y -q "$WORK/symlink.aab" classes.dex)
(cd "$WORK/clean" && zip -q "$WORK/parent-path.aab" classes.dex)
printf '@ classes.dex\n@=../escape\n' | zipnote -w "$WORK/parent-path.aab"
(cd "$WORK/clean" && zip -q "$WORK/absolute-path.aab" classes.dex)
printf '@ classes.dex\n@=/absolute\n' | zipnote -w "$WORK/absolute-path.aab"

"$REPO/scripts/check-play-boundary.sh" artifact --json \
  --artifact "$WORK/clean.aab" --manifest "$WORK/AndroidManifest.xml" --dependencies "$WORK/dependencies.txt" \
  | jq -e '.status == "ok" and .data.artifact_checks == 5' >/dev/null

"$REPO/scripts/check-play-boundary.sh" artifact --json \
  --artifact "$WORK/split.aab" --manifest "$WORK/AndroidManifest.xml" --dependencies "$WORK/dependencies.txt" \
  | jq -e '.status == "ok" and .data.artifact_checks == 5' >/dev/null

set +e
BAD_JSON=$("$REPO/scripts/check-play-boundary.sh" artifact --json \
  --artifact "$WORK/bad.aab" --manifest "$WORK/AndroidManifest.xml" --dependencies "$WORK/dependencies.txt" 2>/dev/null)
BAD_EXIT=$?
set -e
[ "$BAD_EXIT" -eq 4 ]
printf '%s\n' "$BAD_JSON" | jq -e '.status == "error" and .error == "play_boundary_violation" and (.fix | length > 0)' >/dev/null

set +e
SYMLINK_JSON=$("$REPO/scripts/check-play-boundary.sh" artifact --json \
  --artifact "$WORK/symlink.aab" --manifest "$WORK/AndroidManifest.xml" --dependencies "$WORK/dependencies.txt" 2>/dev/null)
SYMLINK_EXIT=$?
set -e
[ "$SYMLINK_EXIT" -eq 4 ]
printf '%s\n' "$SYMLINK_JSON" | jq -e '.status == "error" and .error == "archive_symlink" and (.fix | length > 0)' >/dev/null

set +e
CORRUPT_JSON=$("$REPO/scripts/check-play-boundary.sh" artifact --json \
  --artifact "$WORK/corrupt.aab" --manifest "$WORK/AndroidManifest.xml" --dependencies "$WORK/dependencies.txt" 2>/dev/null)
CORRUPT_EXIT=$?
set -e
[ "$CORRUPT_EXIT" -eq 4 ]
printf '%s\n' "$CORRUPT_JSON" | jq -e '.status == "error" and .error == "archive_inspection_failed" and (.fix | length > 0)' >/dev/null

for ESCAPE_ARTIFACT in "$WORK/parent-path.aab" "$WORK/absolute-path.aab"; do
  set +e
  ESCAPE_JSON=$("$REPO/scripts/check-play-boundary.sh" artifact --json \
    --artifact "$ESCAPE_ARTIFACT" --manifest "$WORK/AndroidManifest.xml" --dependencies "$WORK/dependencies.txt" 2>/dev/null)
  ESCAPE_EXIT=$?
  set -e
  [ "$ESCAPE_EXIT" -eq 4 ]
  printf '%s\n' "$ESCAPE_JSON" | jq -e '.status == "error" and .error == "archive_path_escape" and (.fix | length > 0)' >/dev/null
done

set +e
MISSING_JSON=$("$REPO/scripts/check-play-boundary.sh" artifact --json \
  --artifact "$WORK/missing.aab" --manifest "$WORK/AndroidManifest.xml" --dependencies "$WORK/dependencies.txt" 2>/dev/null)
MISSING_EXIT=$?
set -e
[ "$MISSING_EXIT" -eq 2 ]
printf '%s\n' "$MISSING_JSON" | jq -e '.status == "error" and .error == "artifact_unavailable" and (.fix | length > 0)' >/dev/null

set +e
BAD_INPUT_JSON=$("$REPO/scripts/check-play-boundary.sh" --json unsupported 2>/dev/null)
BAD_INPUT_EXIT=$?
set -e
[ "$BAD_INPUT_EXIT" -eq 3 ]
printf '%s\n' "$BAD_INPUT_JSON" | jq -e '.status == "error" and .error == "bad_input" and (.fix | length > 0)' >/dev/null

printf 'Production boundary fixtures passed\n'
