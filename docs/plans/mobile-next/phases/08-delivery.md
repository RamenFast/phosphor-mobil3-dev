# Phase 08: deliver exact, recoverable and honestly described artifacts

Entry: Phases 00-07 pass their required host, Android and visual checks.
Outcome: source can be rebuilt in its documented layout, public output excludes private evidence, and activation names exact bytes.
Production signing and publication remain separate from this engineering closeout.

## ▸ 08.1 Test source export and rebuild before release activation

📁 Existing: `M/scripts/package-release.sh`, `M/scripts/check-release-provenance.sh`, `M/scripts/test-release-gates.sh`, `M/scripts/check-play-boundary.sh`, `M/scripts/publish-public.sh`, `M/README.md`, `M/spec/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`.
NEW: `M/docs/dev/receipts/mobile-next/SOURCE-EXPORT.md`.

⚠ Root inspected `package-release.sh:201-211`. Mobile and engine use exact Git archives, but the exported engine directory differs from the relative dependency layout.
The public-tree publisher and canonical package-source archive are separate paths. Fixing one does not prove privacy or rebuildability of the other.

Extend existing release fixtures, not a new product CLI. Extract into a fresh ignored directory and reject traversal or symlinks escaping it.
Stage mobile as `phosphor-mobil3` and engine as sibling `phosphor`, preserving all build-source bytes and relative dependencies.
Record both source commits and per-file hashes. A source-only build may use verified manifest identity when VCS metadata is absent.
Never invent a clean Git tag or disable release provenance. Source-build identity is explicitly distinct from an approved signed release.
Run the documented Gradle debug build from the extracted tree. Missing toolchain/dependency caches are unavailable, not rebuild PASS.

Maintain two explicit inventories: complete private provenance, and public distributed-source content with enumerated documentation omissions.
The public bundle preserves every build input, dependency lock, license and regeneration input. Omit private plans, receipts and estate details.
Record omissions openly. Do not label a sanitized tree byte-identical to the entire private Git tree.
Update the distribution spec before changing exporter semantics. Preserve protected historical files in the repository byte-for-byte.
If an omitted file is a build input, stop and separate the private data from that input with a reviewed source change.

Negative fixtures cover dirty/tag/engine mismatch, unlisted JNI, missing license, archive traversal, private receipt leakage and malformed source manifest.
Run two source-package builds to compare deterministic tar metadata and file manifests. Signed binary byte equality remains a separate measured claim.

✅ Run fixture-only gates and the staging rebuild cases added to the existing suite:

```bash
cd "$M"
scripts/test-release-gates.sh
scripts/test-play-boundary.sh
scripts/package-release.sh schema --json
```

Expected: rejection fixtures actually fail, staged source rebuild passes, distributed code hashes match, and public omissions are reviewed.
No real signer, release tag, active production install or upload occurs in this task.

## ▸ 08.2 Seal acceptance, retain artifacts and hand off

📁 Existing: `M/scripts/ship-check.sh`, `M/spec/ACCEPTANCE.md`, `M/docs/ASKS.md`, `M/docs/FEEDBACK.md`, `M/docs/SERIOUS-TODOS.md`, `M/README.md`, `M/HANDOFF.md`.
NEW: `M/docs/dev/receipts/mobile-next/08-closeout.md` and `M/docs/dev/receipts/mobile-next/SHA256SUMS`.

Run the full [evidence floor](../verification/EVIDENCE.md), new tests and D1-D9 on one final reviewed source/artifact pair.
Add new gates without deleting or weakening the canonical fixed gate IDs. Keep fixture tests for exact red/skip semantics.
Record every required case, API branch, failed attempt, corrected retry, setting restoration and reviewer identity.

Retain exact APK/test APK, signer hashes, dependency manifest, source hashes and compatible rollback artifact.
Do not rebuild from a later docs commit and silently install different bytes.
Update spec and feedback from observed behavior. Link unresolved structural risk instead of pretending the roadmap eliminates every possible failure.

Keep README evergreen, with current product truth and imagery from this exact build, not a planning journal.
Update HANDOFF, asks and local project memory. Issues describe local delivery honestly until a separately approved push lands.

✅ Run this pre-release-only scoreboard command and inspect the sealed receipt:

```bash
cd "$M"
set +e
scripts/ship-check.sh --json > "$RUN/ship-check.json"
rc=$?
set -e
test "$rc" -eq 2
jq -e '([.data.gates[] | select(.state == "skip")] | length) == 0 and ([.data.gates[] | select(.state == "red") | .id] | sort) == (["signing.release","provenance.release","release.bundle"] | sort)' "$RUN/ship-check.json"
(cd docs/dev/archive/2026-08-05-scope-reset/protected && sha256sum -c SHA256SUMS)
cat docs/dev/receipts/mobile-next/08-closeout.md
```

Expected before approved release inputs: zero skips, exactly the three external reds, all behavior/roadmap gates green, protected hashes OK.
Do not use this exact-red command for an approved release. That separate lane requires exit 0 and all gates green under its release procedure.
All required Android rows need measured evidence. A scoreboard alone cannot close a missing manual or visual observation.

↩ Rollback: retain prior release assets unchanged. Revert only owned failing packaging/gate commits and restore compatible exact debug artifact if necessary.
Production recovery may require a forward version. Never force-push, retag or remove failed-release evidence to hide a regression.

## External release lane, not executed by this roadmap

❓ Ben owns approval for push, merge, final tag, production signer use, public publication and Play submission.
The executor owns preparing exact artifacts, evidence and a concise approval request when the relevant boundary is reached.
Approved shipping uses canonical Gradle packaging, separate APK/upload signers, AAB validation, 16 KiB native alignment and checksum verification.
Install the exact packaged artifact, preserve settings and verify package/version/signer on the device before claiming delivery.
Keep any unapproved or unavailable step BLOCKED. No new approval is required merely to finish the planning document.
