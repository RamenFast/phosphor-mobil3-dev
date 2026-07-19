#!/usr/bin/env bash
# publish-public.sh — stage the sanitized public tree of phosphor-mobil3 and (optionally)
# push it. The private repo keeps everything; the public view says nothing it shouldn't.
# Agent CLI contract: JSON envelope on stdout, errors carry fix, exits 0/2/3/4.
set -euo pipefail

TOOL="publish-public"; VERSION="1.0.0"
now() { date -Iseconds; }
die() { # $1 exit code, $2 error, $3 fix
  printf '{"status":"error","tool":"%s","version":"%s","ts":"%s","error":"%s","fix":"%s"}\n' \
    "$TOOL" "$VERSION" "$(now)" "$2" "$3"; exit "$1"; }

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PUB="${PHOSPHOR_PUBLIC_CHECKOUT:-$REPO/../phosphor-mobil3-public}"

case "${1:-stage}" in
  schema)
    printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","schema":{"usage":"publish-public.sh [stage|push|schema]","stage":"rsync allowlist into %s, transform docs, run sanitization gate","push":"stage + commit + push the public checkout","gate_tokens":"tailnet IPs, private hostnames, agent-ecosystem names","exits":{"0":"ok","2":"bad usage","3":"sanitization gate tripped","4":"environment missing"}}}\n' "$TOOL" "$VERSION" "$(now)" "$PUB"
    exit 0 ;;
  stage|push) MODE="$1" ;;
  *) die 2 "unknown subcommand: $1" "use stage, push, or schema" ;;
esac

[ -d "$PUB/.git" ] || die 4 "public checkout missing at $PUB" \
  "git clone git@github.com:RamenFast/phosphor-mobil3.git '$PUB' (or set PHOSPHOR_PUBLIC_CHECKOUT)"

# --- allowlist copy (delete strays so removals propagate) ---
rsync -a --delete --exclude build --exclude 'src/main/jniLibs' "$REPO/app/"   "$PUB/app/"
rsync -a --delete --exclude target                             "$REPO/rust/"  "$PUB/rust/"
rsync -a --delete --exclude target                             "$REPO/relay/" "$PUB/relay/"
rsync -a --delete                                              "$REPO/scripts/" "$PUB/scripts/"
rsync -a --delete                                              "$REPO/dev/"    "$PUB/dev/"
rsync -a --delete                                              "$REPO/gradle/" "$PUB/gradle/"
rsync -a "$REPO/build.gradle.kts" "$REPO/settings.gradle.kts" "$REPO/gradle.properties" \
  "$REPO/gradlew" "$REPO/gradlew.bat" "$REPO/LICENSE" "$REPO/README.md" "$REPO/.gitignore" "$PUB/"
mkdir -p "$PUB/docs"
rsync -a --delete "$REPO/docs/screenshots" "$PUB/docs/"
rsync -a "$REPO/docs/UX-SPEC.md" "$REPO/docs/REMOTE.md" "$PUB/docs/"
# ARCHITECTURE ships minus its private-governance pointer line.
sed '/docs\/AGENTS\.md/d' "$REPO/docs/ARCHITECTURE.md" > "$PUB/docs/ARCHITECTURE.md"
# The publisher itself stays private-side only: it names the private hosts in its gate.
rm -f "$PUB/scripts/publish-public.sh"

# --- sanitization gate: any hit aborts; receipts name the exact lines ---
GATE='100\.66\.|100\.114\.|100\.102\.|thinkcenter|interserve|Nexus|codex|2bmillerb|AGENTS\.md|NEXUS-FEEDBACK|HANDOFF'
if HITS=$(grep -rInE "$GATE" "$PUB" --exclude-dir=.git 2>/dev/null); then
  printf '%s\n' "$HITS" >&2
  die 3 "sanitization gate tripped ($(printf '%s\n' "$HITS" | wc -l) hits, listed on stderr)" \
    "scrub the offending lines in the PRIVATE repo (or extend the transform step), then re-run"
fi

STAGED=$(cd "$PUB" && git status --porcelain | wc -l)
if [ "$MODE" = "push" ]; then
  ( cd "$PUB" && git add -A && git commit -q -m "sync from private tree $(cd "$REPO" && git rev-parse --short HEAD)" && git push -q )
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","action":"push","changed_files":%s,"public":"%s"}\n' \
    "$TOOL" "$VERSION" "$(now)" "$STAGED" "$PUB"
else
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","action":"stage","changed_files":%s,"public":"%s","note":"gate clean; run with push to publish"}\n' \
    "$TOOL" "$VERSION" "$(now)" "$STAGED" "$PUB"
fi
