#!/usr/bin/env bash
set -euo pipefail
unset GIT_COMMIT

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRATCH_ROOT="${JCODE_SCRATCH_DIR:-$HOME/.jcode/scratch}"
mkdir -p "$SCRATCH_ROOT"
WORK=$(mktemp -d "$SCRATCH_ROOT/phosphor-release-gates-test.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

make_repo() {
  local path="$1" name="$2"
  mkdir -p "$path"
  git -C "$path" init -q
  git -C "$path" config user.name "Phosphor fixture"
  git -C "$path" config user.email "fixture@invalid"
  printf '%s\n' "$name" >"$path/source.txt"
  mkdir -p "$path/app"
  printf 'val appVersion = "2.0.0"\n' >"$path/app/build.gradle.kts"
  git -C "$path" add source.txt app/build.gradle.kts
  git -C "$path" commit -q -m "fixture"
}

expect_error() {
  local expected_exit="$1" expected_error="$2"
  shift 2
  set +e
  local output
  output=$("$@" 2>/dev/null)
  local actual_exit=$?
  set -e
  [ "$actual_exit" -eq "$expected_exit" ]
  printf '%s\n' "$output" | jq -e --arg error "$expected_error" \
    '.status == "error" and .error == $error and (.fix | length > 0)' >/dev/null
}

MOBILE="$WORK/mobile"
ENGINE="$WORK/engine"
make_repo "$MOBILE" mobile
make_repo "$ENGINE" engine
git -C "$MOBILE" tag v2.0.0
MOBILE_HEAD=$(git -C "$MOBILE" rev-parse HEAD)

"$REPO/scripts/check-release-provenance.sh" schema --json | \
  jq -e '.status == "ok" and .data.schema.exit_codes["2"]' >/dev/null
"$REPO/scripts/package-release.sh" schema --json | \
  jq -e '.status == "ok" and (.data.schema.outputs | index("SHA256SUMS"))' >/dev/null

"$REPO/scripts/check-release-provenance.sh" \
  --repo "$MOBILE" --version 2.0.0 --commit "$MOBILE_HEAD" \
  --dependency-repo "$ENGINE" --json | \
  jq -e '.status == "ok" and .data.clean == true and .data.path_dependency.clean == true' >/dev/null

touch "$MOBILE/untracked"
expect_error 2 dirty_release_tree \
  "$REPO/scripts/check-release-provenance.sh" --repo "$MOBILE" --version 2.0.0 --json
rm "$MOBILE/untracked"

git -C "$MOBILE" tag -d v2.0.0 >/dev/null
expect_error 2 release_tag_missing \
  "$REPO/scripts/check-release-provenance.sh" --repo "$MOBILE" --version 2.0.0 --json
git -C "$MOBILE" tag v2.0.0

expect_error 2 asserted_commit_invalid \
  "$REPO/scripts/check-release-provenance.sh" --repo "$MOBILE" --version 2.0.0 --commit "${MOBILE_HEAD:0:12}" --json
expect_error 2 asserted_commit_mismatch \
  "$REPO/scripts/check-release-provenance.sh" --repo "$MOBILE" --version 2.0.0 \
  --commit 0000000000000000000000000000000000000000 --json

touch "$ENGINE/untracked"
expect_error 2 dirty_dependency_tree \
  "$REPO/scripts/check-release-provenance.sh" --repo "$MOBILE" --version 2.0.0 \
  --dependency-repo "$ENGINE" --json
rm "$ENGINE/untracked"

expect_error 2 artifact_unavailable \
  "$REPO/scripts/package-release.sh" --repo "$MOBILE" --dependency-repo "$ENGINE" \
  --apk "$WORK/missing.apk" --aab "$WORK/missing.aab" --json
printf 'not an apk\n' >"$WORK/present.apk"
expect_error 2 artifact_unavailable \
  "$REPO/scripts/package-release.sh" --repo "$MOBILE" --dependency-repo "$ENGINE" \
  --apk "$WORK/present.apk" --aab "$WORK/missing.aab" --json

set +e
SHIP_JSON=$(env \
  -u RELEASE_STORE_FILE -u RELEASE_STORE_PASSWORD -u RELEASE_KEY_ALIAS \
  -u RELEASE_KEY_PASSWORD -u RELEASE_CERT_SHA256 \
  -u PLAY_UPLOAD_STORE_FILE -u PLAY_UPLOAD_STORE_PASSWORD -u PLAY_UPLOAD_KEY_ALIAS \
  -u PLAY_UPLOAD_KEY_PASSWORD -u PLAY_UPLOAD_CERT_SHA256 \
  "$REPO/scripts/ship-check.sh" --json --only=release.bundle 2>/dev/null)
SHIP_EXIT=$?
set -e
[ "$SHIP_EXIT" -eq 2 ]
printf '%s\n' "$SHIP_JSON" | jq -e \
  '.status == "error" and (.data.gates[] | select(.id == "release.bundle" and .state == "red"))' >/dev/null

printf 'ok - release provenance rejects dirty, untagged, mismatched, and incomplete evidence\n'
printf 'ok - selected release artifact gate cannot pass without signing evidence\n'
