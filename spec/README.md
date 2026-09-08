# Phosphor specification cabinet

The vision and specification are the product source. The implementation compiles from them and must not introduce a hidden product decision.

## Authority order

Use the first applicable source:

1. Ben's current direction.
2. The newest accepted file in `decisions/`.
3. `vision/PHOSPHOR-LIVING-INSTRUMENT.md`.
4. The active files in `spec/`.
5. `README.md`, `docs/ARCHITECTURE.md`, `docs/REMOTE.md`, and `docs/BRIDGE.md`.
6. Living ledgers in `docs/`.
7. Implementation and tests.
8. Historical documents under `docs/dev/archive/`, old decisions, and receipts.

Historical material can explain why code exists. It cannot restore removed scope.

## Active specifications

- `PRODUCT.md`: product boundary, state, privacy, and supported capabilities.
- `EXPERIENCE.md`: interaction, motion, rotation, and visible-state rules.
- `AUDIO-AND-CONNECTIVITY.md`: local, capture, microphone, and PC relay contracts.
- `DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`: package, build, permissions, signing, and release identity.
- `EXPANSION.md`: approved R01–R17 state, ownership, migration, and interaction contracts.
- `ACCEPTANCE.md`: observable release gates.

## Requirement terms

- **Must:** release-blocking invariant.
- **Should:** expected behavior that needs a recorded disposition if absent.
- **May:** optional behavior that cannot weaken a must-level rule.
- **Deferred:** excluded from this development stage and not promised by the active product.

## Definition of done

A change is done when:

1. The active source documents describe the behavior.
2. The implementation contains no conflicting legacy path.
3. Automated tests cover deterministic contracts.
4. Device checks cover Android-owned consent and lifecycle behavior.
5. Privacy, package, artifact, and network scans pass.
6. The result has a rollback point and a receipt.
7. Public documentation describes only measured product truth.

Compilation alone is not acceptance.
