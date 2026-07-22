# Phosphor specification cabinet

**Status:** Source of truth from 2026-07-22 onward

This directory is the normative Ti specification for the next Phosphor Mobile build. Read the Fi source first:

1. [`../vision/PHOSPHOR-LIVING-INSTRUMENT.md`](../vision/PHOSPHOR-LIVING-INSTRUMENT.md)
2. [`PRODUCT.md`](PRODUCT.md)
3. [`EXPERIENCE.md`](EXPERIENCE.md)
4. [`NEXIDEX-PROTOCOL.md`](NEXIDEX-PROTOCOL.md)
5. [`NEXUS-RULINGS-2026-07-22.md`](NEXUS-RULINGS-2026-07-22.md)
6. [`AUDIO-CONNECTIVITY-AND-PROJECTM.md`](AUDIO-CONNECTIVITY-AND-PROJECTM.md)
7. [`DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`](DISTRIBUTION-PERMISSIONS-AND-SIGNING.md)
8. [`ACCEPTANCE.md`](ACCEPTANCE.md)
9. [`../docs/dev/PHOSPHOR-NEXIDEX-IMPLEMENTATION-HANDOFF.md`](../docs/dev/PHOSPHOR-NEXIDEX-IMPLEMENTATION-HANDOFF.md)

## Authority order

When documents disagree, use this order:

1. Ben's latest explicit instruction.
2. The Fi vision.
3. This spec cabinet.
4. The ratified decision record in `decisions/`.
5. The implementation handoff.
6. Existing `docs/UX-SPEC.md`, `docs/ARCHITECTURE.md`, and `docs/BRIDGE.md` where not superseded.
7. Current implementation behavior.

Current behavior is evidence, not authority, when this cabinet deliberately changes it.

## Requirement labels

- **MUST:** release or architecture invariant.
- **SHOULD:** expected unless a documented measurement or policy fact justifies deviation.
- **MAY:** compatible option.
- **PLAY:** applies to the Google Play artifact.
- **FORTRESS:** applies to the Ben-signed development artifact.
- **BOTH:** applies to both artifacts.
- **SPIKE:** must be empirically proven before implementation is treated as available.
- **LATER:** designed now, not required to reach the first Play release.

## Definition of done for a feature

A feature is not complete until it has:

1. one causal state model;
2. a human surface;
3. an agent surface where applicable;
4. provenance for mutations;
5. an honest unavailable/error state with a fix;
6. reduced-motion and accessibility behavior;
7. performance and lifecycle tests;
8. an acceptance receipt named in `ACCEPTANCE.md`.

## No-code status of this cabinet

This cabinet was authored as a specification and handoff pass. It does not claim that the described functionality is implemented. The implementation baseline remains commit `383472b` until a future executor changes it.
