# Phase 00: close inherited repairs and recompile this plan

Entry: Ben authorizes this roadmap's implementation. The inherited B1-B21 program has completed behavior closeout through Phase 18.
Phase 19 publication and production signing remain separate external gates.
Read [EXECUTION](../EXECUTION.md) and [current repairs](../context/CURRENT-REPAIRS.md). Use their exact `M`, `E`, `RUN` aliases.

## ▸ 00.1 Freeze the accepted behavior baseline

📁 Existing: `M/PRE-V2-B1-B21-EXECUTION-PLAN.md`, `M/spec/ACCEPTANCE.md`, `M/docs/ASKS.md`, `M/docs/FEEDBACK.md`, `M/docs/SERIOUS-TODOS.md`.
NEW: `M/docs/dev/receipts/mobile-next/00-baseline.md` and `M/docs/dev/receipts/mobile-next/README.md`.

Root records all 21 B IDs with their exact source, APK, receipt hash and required live/visual result.
Distinguish per-phase live checks from final regression. Reconcile the ledgers without rewriting failed history.
Record both clean repository commits, branch arrangement, toolchains, locks, rollback APK/signer and a private settings recovery copy.
No unchecked, missing, duplicated or VERIFY row qualifies as accepted behavior.

✅ Run and inspect the full receipt, not only the checkbox search:

```bash
cd "$M"
git status --porcelain=v1
git -C "$E" status --porcelain=v1
git rev-parse HEAD
git -C "$E" rev-parse HEAD
cat spec/ACCEPTANCE.md docs/dev/receipts/mobile-next/00-baseline.md
(cd docs/dev/archive/2026-08-05-scope-reset/protected && sha256sum -c SHA256SUMS)
```

Expected: stable attributed trees, 21 distinct accepted rows with matching evidence, protected files all OK.
Root and an independent reviewer sign the requirement-to-receipt map. Today's B2 state fails this entry gate deliberately.

## ▸ 00.2 Recompile future tasks against that exact tree

📁 Existing: `M/docs/plans/mobile-next/`, all owners in [source inventory](../context/SOURCE-INVENTORY.md).
NEW: `M/docs/dev/receipts/mobile-next/00-plan-rebase.md`.

Re-read changed owners and accepted B6/B8/B17/B21 contracts. Retire tasks already satisfied with explicit evidence.
Resolve proposed filenames and test seams before handing each phase to an executor. Preserve retired task IDs in the rebase receipt.
Update this plan's manifests, dependencies, exact command interfaces and rollback pair. Do not paste B2-era implementation bodies over accepted repairs.
This is a bounded plan compilation step, not permission to improvise while activating source.

✅ Run:

```bash
cd "$M"
sha256sum -c docs/plans/mobile-next/context/SOURCE-SHA256SUMS
git diff --check
cat docs/dev/receipts/mobile-next/00-plan-rebase.md
```

Expected: refreshed manifest matches the accepted tree. Every retained task has verified owners, named cases, one gate and a rollback.
Independent review records PASS for the rebased document identity before Phase 01 starts.
Keep the original historical inventory in Git. Do not replace its hashes merely to silence an unexplained mismatch.

↩ Rollback: revert only the owned Phase 00 documentation commit with `git revert --no-edit "$PHASE_COMMIT"`.
The phase receipt defines `PHASE_COMMIT` before this command. Preserve inherited receipts and accepted source.
⛔ No app activation, new signer, network, phone or runtime implementation belongs to this phase.
