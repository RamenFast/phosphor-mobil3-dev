# Decision record: 2026-07-26 Phase 05b-1 observation-only Nexus projection

**Status:** Ratified for implementation
**Implementation in this checkpoint:** Atomic observation only
**Prior checkpoint:** `checkpoint/phosphor-2.0.0-phase-05a` at `36c9d43de6241af5284aaa45cfe538b6a6960775`

## Context

Phase 05a established inert Fortress-only Nexus trust, token, grant, session, lifecycle, authorization-candidate, and projection contracts. It deliberately did not establish a shared-store commit permit or a runtime Android transport.

A mutating Nexus HUD adapter is not safe as the next step:

1. `PhosphorStateStore.dispatch` is the sole existing mutation linearization point. It serializes reduction, synchronous persistence, image publication, counters, audit, and idempotency under the store monitor.
2. `DisplayHudReducer` accepts only the declared local human and migration identities and rejects Nexus principals, Binder/tailnet transports, and session-bearing requests.
3. The private HUD persistence slice rejects Nexus principals, Binder/tailnet provenance, non-null session identifiers, and Nexus lifecycle audit material. A nominal Nexus acceptance would fail persistence and degrade the store rather than publish a truthful mutation.
4. `NexusAuthorizationCandidate` is explicitly a snapshot candidate, not a commit permit or a currentness proof. Pre-authorizing outside the store monitor leaves a revoke, heartbeat, grant, token, and generation race.
5. No single persisted owner yet contains the HUD value together with current Nexus token, grant, generation, heartbeat, revocation, lifecycle, and driving-presence state.

The smallest dependency-correct advance is therefore an authorized, observation-only projection over one atomic main-store read.

## Decisions

1. **One atomic store observation:** Main source gains one immutable `PhosphorStoreObservation` containing the exact current `PhosphorStateSnapshot`, immutable audit records, and `StoreHealth`, captured together while holding the existing `PhosphorStateStore` monitor.
2. **No secondary state authority:** Observation reads the existing store image. It does not create another state store, reducer, writer, persistence port, counter, audit log, or revision clock.
3. **Fortress-only current authority:** Fortress source may add an in-memory observation authority that owns the current Nexus session, token, grant ledger, and trust policy behind one monitor. It accepts only opaque lifecycle and token-revocation results that match its exact current state.
4. **Currentness at observation time:** The authority revalidates the candidate's exact session ID, token ID, trust tuple, generation, capability, lifecycle, reach path, grant, revocation state, token validity, and remote heartbeat status while holding its monitor through the atomic store observation.
5. **Linearized revoke versus observe:** An observation that acquires the authority monitor first may return the pre-revoke store observation. A revoke or closure that acquires it first causes the later observation to return only a typed no-disclosure refusal. No observation may return state after detecting stale authority.
6. **Least-disclosure results:**
   - `OBSERVE_STATE` may return the exact state snapshot and store health.
   - `OBSERVE_AUDIT` may return immutable audit records and store health without adding an independent state payload.
   - `OBSERVE_GEOMETRY` returns a typed unavailable result with a repair instruction because high-rate geometry is not owned by the causal store.
   - Authorization failures expose only the established `NexusPreAuthRefusal` code and fix.
7. **Observation is causally inert:** An observation never dispatches an action, changes revision or sequence, persists, consumes idempotency, appends audit, changes HUD presence, or transitions a session to `DRIVING`.
8. **No runtime activation:** This checkpoint adds no Android service, AIDL, manifest component, signature permission, Binder call, socket, tailnet listener or dialer, UI, `MainActivity` bootstrap, live `pm3` verb, S25 action, signer enrollment, or desktop-adapter change.
9. **Play exclusion:** All Nexus observation authority and projection implementation remains under `app/src/fortress`. Play must contain no Nexus classes. The generic atomic store observation is Nexus-free main source.
10. **Mutation remains blocked:** Phase 05b mutation cannot begin until one shared persisted owner and one generic main-store commit-authorization fence can atomically revalidate current session, token, trust, generation, transport, capability, grant, revocation, heartbeat, and driving state while the existing store monitor remains held through reducer execution and `port.save`.

## Required adversarial evidence

The checkpoint is not complete unless deterministic tests prove:

- state, audit, and health are captured from one store critical section;
- observation cannot see an accepted image before persistence succeeds;
- a save failure preserves the prior snapshot and reports the current read-only health together;
- valid state observation returns the exact store snapshot object;
- valid audit observation does not add a state disclosure;
- stale generation, wrong trust tuple, wrong session, wrong token, missing grant, revoked token, disconnect, Binder death, explicit revoke, and expired remote heartbeat all refuse without state or audit data;
- concurrent observation and revoke have only the two permitted linearized outcomes;
- geometry returns typed unavailable rather than fabricated evidence;
- observations do not change snapshot identity, revision, sequence, audit count, idempotency count, or persistence writes;
- Play cannot load the Fortress observation classes;
- source and artifact gates find no runtime activation or Python dependency.

## Explicitly unchanged paths

This checkpoint must not change:

- `app/src/main/kotlin/dev/phosphor/mobil3/store/DisplayHudReducer.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/settings/HudCausalEnvelopeCodec.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`
- Android manifests, Gradle configuration, AIDL, services, renderer/JNI, UI control routing, or live transport code
- the desktop Nexus adapter
- the protected untracked `Phosphor build.md`

## Rollback

The Phase 05b-1 checkpoint must be one independently revertible signed commit and annotated signed tag. Reverting it must reproduce the exact Phase 05a tree at `36c9d43de6241af5284aaa45cfe538b6a6960775`.

No runtime uninstall, device action, signer migration, preference migration, or transport cleanup is required because this checkpoint activates none of them.
