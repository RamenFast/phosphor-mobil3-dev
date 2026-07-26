# Phase 03 inert causal contracts receipt

**Date:** 2026-07-26
**Behavior changed:** No
**Privileged behavior activated:** No
**Scope:** Android-free state, action, provenance, delta, acknowledgement, refusal, audit, idempotency, capture-truth, geometry, and current-control catalog contracts with a test-only reference oracle

## Result

Phase 03 host validation passes for inert contracts only. No production store, reducer, dispatcher, persistence adapter, observer, renderer command path, Binder service, tailnet transport, CLI projector, or effect runner is introduced or referenced by the existing app.

The contracts compile into both Play and Fortress because both distributions must eventually project the same causal model. They do not grant either build new authority.

## Delivered contracts

Production contracts are split by responsibility under `app/src/main/kotlin/dev/phosphor/mobil3/state/`:

- `FrozenCollections.kt`: defensively copied, read-only list/set/map values used anywhere a durable contract carries a collection;
- `StatePrimitives.kt`: schema version, distribution/build identity, principals, transports, capabilities, session liveness, availability, authority, semantically blocker-typed fixes and refusals, provenance, and evidence-qualified capture truth;
- `StateSnapshot.kt`: desired/effective settings, field-specific typed state values, complete field keys, exact availability/authority/fix/provenance planes, compile-time distribution/build identity pairing, session/liveness agreement, capabilities, capture truth, and initial snapshot factories;
- `Actions.kt`: one sealed Android-free action family, structural canonical payloads, payload validation, requested capabilities, affected fields, direct typed state-intent projections, and explicit human-flow metadata;
- `ActionCatalog.kt`: exact projection of all 57 current `ScopeActions` methods to 48 typed action contracts or 9 reason-bearing non-agent exceptions, with a compiled erased-JVM-signature oracle that rejects overload and parameter/return-type drift;
- `SettingsContracts.kt`: the nine intention groups, exactly one row per current state field, direct typed actions, field-typed desired/effective values, exact availability/authority/fix projection, and exact-reference visible writer provenance;
- `ProtocolRecords.kt`: requests, field-typed ordered deltas, action-bound acknowledgements, inert effect descriptions, typed refusals, timestamped audit records plus bounded count/age index policy, a structural `AcceptedActionRecord` that couples the typed action and exact previous-to-post snapshot diff to one authored provenance object across acknowledgement/delta/audit/snapshot/settings/effects, successful effect-only human-flow requests, and bounded 24-hour idempotency records/index policy;
- `GeometryContracts.kt`: bounded geometry frames with source/clock identity, normalized beam segments, energy, signal state, explicit true-beam/Phosphor-geometry/ProjectM layer state, ProjectM timing/parameters/features/live-or-still truth, minimum true-beam visibility, decorative-before-beam load shedding, honest drop count, and beam-bound quality reporting.

The catalog is bound by a JVM test that reflects the compiled `ScopeActions` interface directly. Adding, removing, renaming, overloading, or changing a parameter/return type without updating the causal contract fails the Phase 03 suite.

## Truth and safety properties

- `phosphor.state/2` snapshots contain desired, effective, availability, authority, fixes, capabilities, provenance, liveness, distribution/build identity, capture truth, ordered revision, and sequence planes.
- Every collection-valued durable contract defensively copies caller inputs into read-only values, preventing later alias mutation of state, canonical payload, delta, geometry, audit, or idempotency truth.
- Every current state field has exactly one row in the nine binding settings intention groups. A row's desired/effective values must match the field type, its repair must exactly project availability/authority, and its visible last-writer stamp cannot diverge from provenance.
- Safety confirmation is a typed human-only action. Nexus attempts refuse with `permission_requires_human`; the human test path accepts it through the same request/provenance/revision grammar.
- Deltas reject duplicate fields, unchanged values, and values of the wrong field type. Changed acknowledgements reject undeclared fields and require an exact effective value for single-change actions; zero/multi-change acknowledgements cannot publish one ambiguous value. Accepted records name the exact previous snapshot and require the delta keys and old/new values to equal the complete previous-to-post desired/effective diff, including non-direct and partially changing multi-field actions.
- Canonical action payloads are structural, argument-sorted, locale-independent, and bit-exact for floats.
- Every mutating request carries principal, idempotency key, expected revision, reason, requested capability, transport, and optional session.
- Changed acknowledgements require a delta. Ordinary state no-ops forbid deltas and effects. Typed human-flow requests may retain the exact state snapshot while emitting only provenance-bound `human_flow.open` descriptions and an action audit; they cannot forge protected consent or claim an effective state value.
- `AcceptedActionRecord` structurally requires the exact same accepted `ProvenanceStamp` object in acknowledgement, delta changes, audit, changed snapshot fields, settings projections, visible writer, and inert HUD/persistence effects. It also preserves exact provenance on unchanged fields, forbids action-driven changes to distribution/session/capability/capture/authority planes, and rejects equal-but-copied stamps.
- Idempotency is principal-plus-key scoped. Same-key/same-payload replay returns the original acknowledgement without advancing revision or rerunning effects. Same-key/different-payload returns `idempotency_conflict` with the original receipt ID.
- Snapshot constructors reject Play/Fortress/build-profile contradictions, session/liveness disagreement, incomplete field planes, semantically mismatched blocker/fix codes, and any fix plane that does not exactly project availability before authority.
- A bounded idempotency index refuses new keys when full rather than evicting an unexpired key. Records cannot expire before 24 hours. Every audit kind carries an event wall clock, and `AuditIndexSnapshot` independently enforces positive count/age bounds, no future records, and maximum age.
- Every refusal is typed and fix-bearing. Availability and authority blocker states accept only semantically compatible repair codes. Validation, capability, liveness, availability, authority, revision, idempotency conflict, and protected-index capacity failures occur before state mutation or effect descriptions. The test oracle rejects backward accepted time and preflights receipt/revision/sequence/expiry/ordinal arithmetic before committing state, audit, or idempotency.
- All four non-none capture causes require a matching dated evidence receipt observed at or after the qualified transition. Retry metadata is legal only in `retrying`; the next retry must equal transition time plus a positive backoff capped at five minutes. Unavailable capture must expose the exact blocking-authority fix.
- Geometry frames carry all three explicit layers, keep the true beam above a visibility floor, bind reported frame quality to true-beam quality, and require ProjectM/Phosphor decorative quality to shed before the beam. ProjectM identity, clock, preset, parameters, features, and live/still truth must agree; no-signal states cannot imply live beam energy or a moving ProjectM field.

## Requirement conformance

| Binding clause | Production contract | JVM observation | Phase 03 result |
|---|---|---|---|
| `PRODUCT.md` 3.1, one causal store planes | `PhosphorStateSnapshot`, `StateField`, `StateValue`, `ProvenanceStamp` | `snapshotCarriesEveryRequiredPlaneAndFieldContract` | Contract-ready only; no store exists |
| `PRODUCT.md` 3.1 and Fact 717, one authored provenance stamp | `AcceptedActionRecord` couples `StateDelta`, `ActionAcknowledgement`, `AuditRecord`, snapshot/settings provenance, and `InertEffectDescription` by object identity | `acceptedMutationReusesOneProvenanceInstanceAndReceiptEverywhere`; `nexusThemeSliceReusesTheAcceptedStampForStateDeltaHudPersistenceAckAndAudit` | Equal-but-copied provenance is structurally rejected; no production dispatcher exists |
| `PRODUCT.md` 3.2 and acceptance B-01, control parity | `ActionType`, concrete `PhosphorAction` classes, direct state intent, and `StateActionCatalog` | `catalogCoversAllScopeActionsExactlyOnceWithTypedActionsOrExceptions`; `safetyConfirmationIsTypedHumanOnlyAndCannotBeForgedByNexus`; `humanFlowRequestsHaveAProvenanceBoundEffectOnlyAcceptance` | All current methods accounted for; protected human flows are requestable but not forgeable; adapters remain Phase 04+ |
| `PRODUCT.md` 4, settings intention groups and row truth | `SettingsGroup`, `SettingsRowSchema`, `SettingsRowProjection`, `SettingsSchema` | `settingsSchemaCoversEveryFieldAndNineIntentionGroupsWithVisibleWriterTruth` | Contract-ready only; no production settings projector |
| `NEXIDEX-PROTOCOL.md` 6, ordered snapshot and delta | `PhosphorStateSnapshot`, `StateDelta`, `StateChange`, `AcceptedActionRecord.previousSnapshot` | exact previous-to-post diff, detached snapshot, hidden multi-field mutation, and exact-provenance tests | Contract-ready only |
| `NEXIDEX-PROTOCOL.md` 7, actions/revisions/idempotency | `ActionRequest`, structural `CanonicalPayload`, `IdempotencyRecord` | replay, conflict, no-op, stale revision, and protected-capacity tests | Test-oracle semantics proven; no dispatcher/persistence |
| `NEXIDEX-PROTOCOL.md` 8, typed fix-bearing refusals | `RefusalCode`, `Refusal`, `Fix`, blocker-specific allowed fix codes | validation, semantically mismatched fix, and pre-effect refusal matrix | Contract-ready only |
| `NEXIDEX-PROTOCOL.md` 9 and acceptance B-05, latest revision wins | revision/sequence/provenance/contested field contracts | `latestAcceptedRevisionWinsContentionAndBothActionsRemainInAudit` | Test-oracle semantics proven |
| `NEXIDEX-PROTOCOL.md` 10, bounded geometry stream | `GeometryFrame`, `GeometryLoadSheddingPolicy`, `BeamSegment`, `ProjectMFieldMetadata` | `geometryContractKeepsTrueBeamSourceClockLayersAndHonestDropCount` | Visibility, stillness, source/clock, and beam-first degradation schema only; no producer or transport |
| `NEXIDEX-PROTOCOL.md` 11, truthful capture states | `CaptureTruth`, `CaptureEvidence`, bounded retry metadata | `captureTruthAvailabilityAuthorityAndProtocolRecordsFailClosed` | Schema validation proven; no new capture path |
| `NEXIDEX-PROTOCOL.md` 14, bounded append-only audit grammar | `AuditKind`, timestamped `AuditRecord`, `AuditRetentionPolicy`, `AuditIndexSnapshot`, `AcceptedActionRecord` | action, no-op, human-flow, replay, conflict, refusal, contention, count/age/future-clock, copy-rejection, and atomic-overflow observations | Test-oracle semantics only |
| Execution-spine Phase 03 activation boundary | state package plus test-only `ReferenceHarness` | prohibited scans for Android/runtime imports, production dispatch/store/reducer/persistence/observer/transport symbols, and existing app references | Pass |

## Acceptance status

Phase 03 does **not** mark B-01 through B-05 complete. It establishes compile-time contracts and test-oracle semantics:

- **B-01:** contract-ready for all 57 current `ScopeActions` methods, with 48 typed mutations/human flows and 9 justified read/system exceptions;
- **B-02:** human HUD provenance/revision/persistence-description contract-ready, with no production UI/store/persistence path;
- **B-03:** Nexus theme provenance/HUD-description contract-ready, with no live Nexus authority or UI animation path;
- **B-04:** no-op and replay semantics proven only in the test oracle;
- **B-05:** contention semantics proven only in the test oracle;
- **B-06:** remains pending the operational session/revocation phases.

## Defects caught before checkpoint

Independent orchestration review rejected the initial dense prototype because:

1. its catalog omitted every `ScopeActions` method after `remoteHosts`;
2. most catalog rows were strings rather than concrete typed actions;
3. its bounded idempotency oracle evicted unexpired records before the required 24-hour retention;
4. its deltas and acknowledgements used unbounded `Any` values;
5. it lacked the binding bounded geometry-frame schema;
6. protected/DRM capture causes did not require an evidence receipt.

The candidate design corrects all six defects. Independent adversarial reviews then found and drove additional corrections for collection aliases; field-specific typed values; typed action/canonical-payload/direct-intent binding; exact previous-to-post delta coverage for direct, relative, generated, and multi-field action shapes; distribution/session/fix-plane contradictions; semantically blocker-typed repairs; structural end-to-end provenance identity; effect-only human-flow acceptance without forged consent; all-cause capture evidence timing and exact retry scheduling; human-only safety confirmation; ProjectM visibility/stillness/load-shedding truth; timestamped bounded audit and idempotency policies; exact compiled `ScopeActions` signatures; and monotonic, overflow-atomic test-oracle ordering. Fresh independent reviews of the corrected tree passed with zero blockers; the phase remains unsealed only until the signed checkpoint and exact revert rehearsal succeed.

## Validation

Commands:

```bash
./gradlew \
  :app:testPlayDebugUnitTest \
  :app:testFortressDebugUnitTest \
  :app:lintPlayDebug \
  :app:lintFortressDebug \
  --console=plain

git diff --check
git ls-files '*.py'
# Explicit scans over app/src/main/kotlin/dev/phosphor/mobil3/state:
# - no Android/AndroidX/coroutine/network/file imports
# - no production store/reducer/dispatcher/persistence/effect runner
# - no mutable state declarations
# - no reference from existing production app sources
```

Observed results:

- complete Play debug unit suite: 30 tests, 0 skipped, 0 failures, 0 errors;
- complete Fortress debug unit suite: 30 tests, 0 skipped, 0 failures, 0 errors;
- Phase 03 contract class: 20 tests per distribution, 0 skipped, 0 failures, 0 errors;
- Play debug lint: 0 errors, 48 visible warnings, 31 visible hints;
- Fortress debug lint: 0 errors, 48 visible warnings, 31 visible hints;
- whitespace check: pass;
- tracked Python: empty;
- Android/runtime import scan: pass;
- production operational-symbol scan: pass;
- existing-production-reference scan: pass;
- `concourse doctor --json`: 64 pass, 3 warnings, 1 unrelated estate failure in the `nexus-model` probe (`AttributeError` while parsing a string as a model object); full output and classification are preserved, and no Phase 03 repository/source/test/signing/rollback gate failed.

No lint baseline was created. No device test applies because this phase intentionally has no active reader, writer, transport, renderer, or UI adapter.

## Independent review

Two independent read-only reviews passed on the final corrected tree with no remaining Phase 03 blocker:

- specification/architecture release-gate review against Product 3.1/3.2/4, Nexidex 6-11/14, acceptance B-01..B-05, Fact 717, and the inert activation boundary;
- adversarial Kotlin/test-semantics review covering aliases, canonical and direct intent, exact previous-to-post diffs, idempotency retention, timestamped audit/provenance identity, protected human flows, typed repairs, catalog drift, capture truth, geometry truth, and accidental runtime behavior.

Both reviewers independently forced the 20-test contract class in Play and Fortress after the last source change. Their final reports record zero blockers and are archived beside the validation logs.

## Durable evidence

The final test logs, lint reports, source/prohibited scans, independent review reports, changed-file hashes, and manifest are archived under:

```text
/media/ben/Mass storage/agenticTinkering/Codex/phosphor-2.0/phase-03-host-validation-20260726T0129Z
```

The final evidence payload manifest is `corrected-final/PAYLOAD-SHA256SUMS` with SHA-256 `7784866b078e52e0ec05bffe55c80d9eb63a9702247905024fab23702f8e4d45` and 36 entries. To avoid a circular checksum, that payload manifest excludes itself and the archived copy of this receipt; the receipt is instead protected by the signed Git checkpoint and copied beside the manifest after this hash was recorded.

## Rollback and next activation gate

The commit carrying this receipt is the sole intended target of the SSH-signed annotated tag `checkpoint/phosphor-2.0.0-phase-03`. Treat Phase 03 as sealed only after the tag resolves locally and remotely.

Once sealed, exact rollback is:

```bash
git revert checkpoint/phosphor-2.0.0-phase-03
```

Phase 04 may add the first operational store/reducer/persistence slice. It must keep every migration slice independently revertible and may not activate Binder, tailnet authority, privileged capture, overlay, or other Fortress control surfaces.
