#!/usr/bin/env bash
# release-stable.sh: publish a tagged dev commit to the stable repo and attach the APK.
#   scripts/release-stable.sh <tag> [apk]
# Stable gets the full dev history up to <tag> (Ben: "don't make it clean"). The GitHub
# Release on stable carries the APK and its SHA256, so the Releases tab works for
# Obtainium, F-Droid users, and anyone who clicks through from the store listing.
# JSON envelope on stdout. Exits: 0 ok, 2 bad usage, 3 gate failed, 4 environment.
set -euo pipefail
TOOL="release-stable"; VERSION="1.0.0"
ts() { date -Iseconds; }
die() { printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"%s","fix":"%s"}\n' "$TOOL" "$VERSION" "$(ts)" "$2" "$3"; exit "$1"; }

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STABLE="${PHOSPHOR_STABLE_REMOTE:-https://github.com/RamenFast/phosphor-mobil3.git}"
STABLE_SLUG="RamenFast/phosphor-mobil3"
TAG="${1:-}"; APK="${2:-}"
[ "$TAG" = schema ] && { printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","schema":{"usage":"release-stable.sh <tag> [apk]","effect":"push tag + history to stable, create GitHub Release with APK and sha256","env":{"PHOSPHOR_STABLE_REMOTE":"override stable remote"}}}\n' "$TOOL" "$VERSION" "$(ts)"; exit 0; }
[ -n "$TAG" ] || die 2 "tag required" "release-stable.sh v2.0.0"
cd "$REPO"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || die 2 "tag $TAG does not exist" "git tag -a $TAG -m 'release $TAG' <commit>"
command -v gh >/dev/null || die 4 "gh missing" "install GitHub CLI and gh auth login"

SHA=$(git rev-list -n1 "$TAG")
if [ -z "$APK" ]; then
  APK="$REPO/app/build/outputs/apk/release/app-release.apk"
fi
[ -f "$APK" ] || die 3 "APK not found at $APK" "source scripts/env.sh && ./gradlew assembleRelease, or pass the APK path"

# The APK must be built from the tagged commit. package-release.sh embeds provenance; check the cheap thing here.
APK_VER=$("$ANDROID_HOME/build-tools/"*/aapt2 dump badging "$APK" 2>/dev/null | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1 || true)
[ -n "$APK_VER" ] || die 3 "could not read versionName from APK" "source scripts/env.sh so aapt2 is on ANDROID_HOME"
case "$TAG" in v"$APK_VER"|"$APK_VER") ;; *) die 3 "APK versionName $APK_VER does not match tag $TAG" "rebuild from the tagged commit";; esac

SUM=$(sha256sum "$APK" | cut -d' ' -f1)
OUT="$(mktemp -d)"; ASSET="$OUT/phosphor-mobil3-$TAG.apk"; cp "$APK" "$ASSET"; echo "$SUM  phosphor-mobil3-$TAG.apk" > "$ASSET.sha256"

# Full history, not a squash. Stable's master = dev history up to the tag, plus one commit that
# swaps in the store-facing README (docs/STABLE-README.md). That top commit is regenerated each
# release, so the push uses --force-with-lease; everything under it is the untouched dev history.
git remote get-url stable >/dev/null 2>&1 || git remote add stable "$STABLE"
git fetch -q stable master 2>/dev/null || true
WT="$OUT/stable-wt"; git worktree add -q --detach "$WT" "$SHA"
( cd "$WT" && cp docs/STABLE-README.md README.md && git add README.md \
  && git -c user.name="release-stable" -c user.email="release@phosphor.local" commit -q -m "Stable README for $TAG" )
STABLE_SHA=$(git -C "$WT" rev-parse HEAD)
git push --force-with-lease=master stable "$STABLE_SHA:refs/heads/master" 2>&1 | tail -1 >&2
git push stable "refs/tags/$TAG" 2>&1 | tail -1 >&2
git worktree remove --force "$WT"

# Release notes: commits since the previous tag on stable, or since the previous local tag.
PREV=$(git describe --tags --abbrev=0 "$TAG^" 2>/dev/null || true)
NOTES="$OUT/notes.md"
{
  echo "## Phosphor Mobile $TAG"
  echo
  echo "Built from \`$SHA\`. SHA256 of the APK is in the attached \`.sha256\` file."
  echo
  echo "Install: download the APK below, or add this repo to Obtainium. Android 10 or newer, arm64."
  echo
  if [ -n "$PREV" ]; then echo "### Changes since $PREV"; echo; git log --format='- %s' "$PREV..$TAG" | head -60; fi
  echo
  echo "Development happens in [phosphor-mobil3-dev](https://github.com/RamenFast/phosphor-mobil3-dev). Bugs in this build go [here](https://github.com/$STABLE_SLUG/issues)."
} > "$NOTES"

if gh release view "$TAG" -R "$STABLE_SLUG" >/dev/null 2>&1; then
  gh release upload "$TAG" -R "$STABLE_SLUG" --clobber "$ASSET" "$ASSET.sha256" >/dev/null
else
  gh release create "$TAG" -R "$STABLE_SLUG" --title "Phosphor Mobile $TAG" --notes-file "$NOTES" "$ASSET" "$ASSET.sha256" >/dev/null
fi
URL="https://github.com/$STABLE_SLUG/releases/tag/$TAG"
printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","tag":"%s","commit":"%s","apk_version":"%s","sha256":"%s","release":"%s"}\n' "$TOOL" "$VERSION" "$(ts)" "$TAG" "$SHA" "$APK_VER" "$SUM" "$URL"
