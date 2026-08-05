# Primetime cleanup phase 01 authority reset

**Recorded:** 2026-08-05 UTC

## Change

The active vision and specification now define one Play-safe app, Android 10 support, no analytics or behavior tracking, no Nexus or product-agent control, and retained local, capture, and PC relay sources.

`decisions/2026-08-05-product-scope-reset.md` supersedes conflicting active clauses in the July product decisions. The July decisions remain immutable history.

Obsolete planning and protocol documents moved under `docs/dev/archive/2026-08-05-scope-reset/historical/`. Their bodies were moved through Git without content edits.

The living handoff, agent guide, asks ledger, and serious backlog now follow the reset.

## Active source

- `vision/PHOSPHOR-LIVING-INSTRUMENT.md`
- `spec/README.md`
- `spec/PRODUCT.md`
- `spec/EXPERIENCE.md`
- `spec/AUDIO-AND-CONNECTIVITY.md`
- `spec/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`
- `spec/ACCEPTANCE.md`

## Checks

- `git diff --check` passed.
- Every required historical archive target exists and is non-empty.
- The active specification index references only current files.
- The privacy contract explicitly rejects analytics, telemetry, behavior history, advertising identifiers, installation identifiers, and silent reporting.

## Follow-through

`README.md`, `docs/ARCHITECTURE.md`, and `docs/plans/V2-FEATURE-LEDGER.md` still describe the pre-cleanup implementation. They will be rewritten after the code and build compile from the new source, so their public claims remain measured rather than aspirational.

## Rollback

Revert the phase commit. The moved files return to their original paths and the active source returns to its previous bodies.
