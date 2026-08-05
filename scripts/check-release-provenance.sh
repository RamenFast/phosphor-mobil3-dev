#!/usr/bin/env bash
set -uo pipefail

TOOL="release-provenance"
VERSION="1.0.0"
MODE="run"
JSON=0
REPO=""
PRODUCT_VERSION=""
ASSERTED_COMMIT="${GIT_COMMIT:-}"
DEPENDENCY_REPO=""

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
      "$TOOL" "$VERSION" "$(ts)" "$code" "$(json_escape "$message")" "$(json_escape "$fix")"
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
    --repo)
      [ "$#" -ge 2 ] || error 3 bad_input "--repo requires a path" "run scripts/check-release-provenance.sh schema"
      REPO="$2"; shift 2 ;;
    --version)
      [ "$#" -ge 2 ] || error 3 bad_input "--version requires a value" "run scripts/check-release-provenance.sh schema"
      PRODUCT_VERSION="$2"; shift 2 ;;
    --commit)
      [ "$#" -ge 2 ] || error 3 bad_input "--commit requires a full Git SHA" "run scripts/check-release-provenance.sh schema"
      ASSERTED_COMMIT="$2"; shift 2 ;;
    --dependency-repo)
      [ "$#" -ge 2 ] || error 3 bad_input "--dependency-repo requires a path" "run scripts/check-release-provenance.sh schema"
      DEPENDENCY_REPO="$2"; shift 2 ;;
    *) error 3 bad_input "unknown argument: $1" "run scripts/check-release-provenance.sh schema" ;;
  esac
done

[ -t 1 ] || JSON=1

if [ "$MODE" = "help" ]; then
  cat <<'USAGE'
release-provenance: verify exact, clean, tagged release source

  scripts/check-release-provenance.sh --repo PATH --version X.Y.Z [--commit FULL_SHA] [--dependency-repo PATH] [--json]
  scripts/check-release-provenance.sh schema
USAGE
  exit 0
fi

if [ "$MODE" = "schema" ]; then
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","data":{"schema":{"arguments":["--repo PATH","--version X.Y.Z","--commit FULL_SHA","--dependency-repo PATH","--json","schema"],"checks":["git repository available","HEAD is a full commit","working tree and index are clean","vVERSION points exactly at HEAD","optional asserted commit is full length and equals HEAD","path dependency repository is clean and commit-identifiable"],"exit_codes":{"0":"provenance verified","2":"required provenance evidence unavailable or invalid","3":"bad input","4":"tool failure"}}}}\n' \
    "$TOOL" "$VERSION" "$(ts)"
  exit 0
fi

[ -n "$REPO" ] || error 3 bad_input "--repo is required" "pass the release repository path"
[ -n "$PRODUCT_VERSION" ] || error 3 bad_input "--version is required" "pass the product version without a v prefix"
[[ "$PRODUCT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] || \
  error 3 bad_input "invalid product version: $PRODUCT_VERSION" "use a semantic version such as 2.0.0"
REPO=$(cd "$REPO" 2>/dev/null && pwd) || \
  error 2 provenance_unavailable "release repository is unavailable: $REPO" "provide a readable repository checkout"

git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 || \
  error 2 provenance_unavailable "release source is not a Git working tree" "build releases from the exact tagged Git checkout"

HEAD_COMMIT=$(git -C "$REPO" rev-parse --verify HEAD 2>/dev/null) || \
  error 2 provenance_unavailable "HEAD cannot be resolved" "check out the intended release commit"
[[ "$HEAD_COMMIT" =~ ^[0-9a-f]{40}$ ]] || \
  error 4 git_failure "Git returned an invalid HEAD commit: $HEAD_COMMIT" "repair the repository and rerun the provenance gate"

STATUS=$(git -C "$REPO" status --porcelain --untracked-files=normal 2>/dev/null) || \
  error 4 git_failure "Git status failed" "repair the repository and rerun the provenance gate"
[ -z "$STATUS" ] || \
  error 2 dirty_release_tree "release working tree or index is dirty" "commit or remove every intended change, then rerun from a clean tree"

TAG="v${PRODUCT_VERSION}"
TAG_COMMIT=$(git -C "$REPO" rev-parse --verify "refs/tags/${TAG}^{commit}" 2>/dev/null) || \
  error 2 release_tag_missing "required release tag $TAG does not exist" "after final approval, create $TAG on the exact release commit"
[ "$TAG_COMMIT" = "$HEAD_COMMIT" ] || \
  error 2 release_tag_mismatch "tag $TAG points to $TAG_COMMIT, not HEAD $HEAD_COMMIT" "check out the tagged commit or move the unshipped tag only with explicit approval"

if [ -n "$ASSERTED_COMMIT" ]; then
  ASSERTED_COMMIT=${ASSERTED_COMMIT,,}
  [[ "$ASSERTED_COMMIT" =~ ^[0-9a-f]{40}$ ]] || \
    error 2 asserted_commit_invalid "GIT_COMMIT or --commit must be a full 40-character Git SHA" "set it to the exact HEAD commit or remove the override"
  [ "$ASSERTED_COMMIT" = "$HEAD_COMMIT" ] || \
    error 2 asserted_commit_mismatch "asserted commit $ASSERTED_COMMIT does not equal HEAD $HEAD_COMMIT" "use the exact checked-out release commit"
fi

DEPENDENCY_JSON="null"
if [ -n "$DEPENDENCY_REPO" ]; then
  DEPENDENCY_REPO=$(cd "$DEPENDENCY_REPO" 2>/dev/null && pwd) || \
    error 2 dependency_provenance_unavailable "path dependency repository is unavailable: $DEPENDENCY_REPO" "provide the exact sibling engine checkout"
  git -C "$DEPENDENCY_REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 || \
    error 2 dependency_provenance_unavailable "path dependency source is not a Git working tree" "build with the exact sibling engine checkout"
  DEPENDENCY_COMMIT=$(git -C "$DEPENDENCY_REPO" rev-parse --verify HEAD 2>/dev/null) || \
    error 2 dependency_provenance_unavailable "path dependency HEAD cannot be resolved" "check out the intended engine commit"
  [[ "$DEPENDENCY_COMMIT" =~ ^[0-9a-f]{40}$ ]] || \
    error 4 git_failure "Git returned an invalid dependency commit: $DEPENDENCY_COMMIT" "repair the dependency repository"
  DEPENDENCY_STATUS=$(git -C "$DEPENDENCY_REPO" status --porcelain --untracked-files=normal 2>/dev/null) || \
    error 4 git_failure "path dependency Git status failed" "repair the dependency repository"
  [ -z "$DEPENDENCY_STATUS" ] || \
    error 2 dirty_dependency_tree "path dependency working tree or index is dirty" "commit or remove every intended engine change before release"
  DEPENDENCY_JSON=$(printf '{"repository":"%s","commit":"%s","short_commit":"%s","clean":true}' \
    "$(json_escape "$DEPENDENCY_REPO")" "$DEPENDENCY_COMMIT" "${DEPENDENCY_COMMIT:0:12}")
fi

if want_json; then
  printf '{"status":"ok","tool":"%s","version":"%s","ts":"%s","data":{"repository":"%s","product_version":"%s","tag":"%s","commit":"%s","short_commit":"%s","clean":true,"path_dependency":%s}}\n' \
    "$TOOL" "$VERSION" "$(ts)" "$(json_escape "$REPO")" "$(json_escape "$PRODUCT_VERSION")" \
    "$TAG" "$HEAD_COMMIT" "${HEAD_COMMIT:0:12}" "$DEPENDENCY_JSON"
else
  printf 'release provenance verified\n  tag: %s\n  commit: %s\n' "$TAG" "$HEAD_COMMIT"
  [ "$DEPENDENCY_JSON" = null ] || printf '  path dependency: %s\n' "$DEPENDENCY_COMMIT"
fi
