# Decision record: 2026-07-26 Phase 05b-2 shared-store Nexus mutation

**Status:** Ratified for implementation
**Implementation in this checkpoint:** Fortress-only Nexus mutation of the currently operational `display.hud` causal slice
**Prior checkpoint:** `checkpoint/phosphor-2.0.0-phase-05b1` at `62d7d5921080510bf530b3afbff0a8f89968527e`

## Context

Phase 05b-1 proved atomic authorized observation while deliberately blocking mutation. The current production causal store has exactly one operational typed action, `SetDisplayHud`. Every other action remains schema-only or retains its pre-causal path until its later functional phase. Claiming Nexus mutation parity for theme, transport, scope, rotation, overlay, capture, or permissions now would bypass the ratified release ordering.

The active HUD slice already provides the required mutation semantics: typed action and request, exact revision and sequence, no-op behavior, 24-hour idempotency, conflict and replay records, one provenance object, bounded audit, synchronous persistence before publication, listener ordering, and read-only degradation after save failure. Nexus mutation must enter this existing linearization point rather than add another reducer, writer, persistence port, counter, audit log, or preference path.

## Decisions

1. **Current operational action only.** Phase 05b-2 activates Nexus mutation only for `SetDisplayHud`. The dispatcher refuses every action whose production reducer is not yet operational. Later phases extend the same fence and dispatcher when their reducers become real.
2. **One store mutation authority.** `PhosphorStateStore` remains the sole owner of revision, sequence, receipt ordinals, idempotency ordinals, reduction, persistence, image publication, audit, and listeners.
3. **Generic main-store fence.** Main source gains a Nexus-free `CommitAuthorizationFence`. `dispatchAuthorized` invokes it while holding the existing store monitor, validates that any authorization exactly matches the typed action, principal, session, transport, capability, and current snapshot, and then executes the existing reducer and `port.save` before releasing the store monitor.
4. **Authority monitor remains held through save.** Fortress `NexusRuntimeDispatcher` acquires its authority monitor first, revalidates current session, token, trust policy, generation, reach path, grant, revocation state, token expiry, and heartbeat, and retains that monitor while calling `PhosphorStateStore.dispatchAuthorized`. Every session transition, token revoke, capability revoke, heartbeat update, and close uses the same authority monitor. The global lock order is authority monitor, then store monitor.
5. **No forged direct Nexus dispatch.** The ordinary `PhosphorStateStore.dispatch` path never authorizes a Nexus principal. A Nexus request without the store-invoked fence refuses and is not accepted through local HUMAN or MIGRATION rules.
6. **Authorization precedes replay.** Current authority and capability are revalidated before the reducer performs idempotency replay/conflict lookup. A matching replay may ignore stale `expectedRevision`, but never current revoke, session, token, grant, transport, capability, or heartbeat failure.
7. **Persisted causal truth.** Accepted Nexus HUD actions persist the same `PhosphorStoreImage` shape as local actions, including the Nexus principal, Binder or tailnet transport, non-null session ID, acknowledgement, delta, audit, idempotency record, and one shared provenance stamp. The private HUD codec accepts exactly those Nexus forms and continues rejecting unrelated principals, transports, missing sessions, non-HUD actions, effects, and non-HUD snapshot drift.
8. **Transient authority is not resurrected.** Token, session, heartbeat, and transient grant authority remain runtime state and are never reconstructed as active after process restart. Persisted action receipts remain replay-reserved, but a replay still requires a newly authenticated current session.
9. **Observation and mutation share authority.** The existing observation dispatcher is evolved into one Fortress runtime dispatcher. Observation, mutation, heartbeat, transition, and revoke use one monitor and one current authority aggregate.
10. **No Android transport activation in this checkpoint.** Phase 05b-2 adds no manifest component, signature permission, AIDL, Binder service, socket, tailnet dialer, runtime bootstrap, UI control, device action, live `pm3` verb, or desktop-adapter change. Binder and tailnet activate in their following independently revertible checkpoints.
11. **Play exclusion.** The generic main-store fence is Android-free and Nexus-free. Nexus authorization implementation stays under `app/src/fortress`; Play contains no Fortress dispatcher or Nexus runtime class.
12. **No Python.** Implementation, tests, gates, and receipts remain Kotlin, Bash, Gradle, or existing Rust only.

## Required adversarial evidence

The checkpoint is incomplete unless deterministic tests prove:

- ordinary dispatch cannot authorize a Nexus principal;
- an exact current Binder candidate can change HUD through the existing store and return the complete accepted record;
- an exact current tailnet candidate has identical causal results except transport/session identity;
- accepted Nexus state, delta, acknowledgement, audit, idempotency, persistence, and settings projection reuse one provenance object and receipt ID;
- Nexus no-op does not advance revision or sequence;
- authorized matching replay returns the original acknowledgement without rerunning or advancing revision even when `expectedRevision` is stale;
- revoked token, capability removal, wrong session, wrong token, wrong trust tuple, stale generation, wrong reach path, expired token, and expired heartbeat refuse before replay or persistence;
- revoke and mutation races have exactly two outcomes: mutation fully persists first, or revoke linearizes first and mutation refuses with no candidate publication;
- save failure preserves the previous image and reports read-only health;
- Nexus principal/session/transport receipts round-trip canonically across restart;
- malformed Nexus provenance, missing sessions, unsupported transports, unrelated action records, and cross-principal idempotency aliases fail closed;
- Play cannot load or archive the Fortress runtime dispatcher;
- no Binder, tailnet, manifest, service, listener, UI, renderer/JNI, desktop-adapter, or Python activation is introduced.

## Intended implementation paths

- `app/src/main/kotlin/dev/phosphor/mobil3/store/PhosphorStateStore.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/store/DisplayHudReducer.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/settings/HudCausalEnvelopeCodec.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusDispatcherContracts.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt`
- corresponding main, settings, store, Fortress Nexus, and distribution tests
- Phase 05b-2 boundary script, fixture, receipt, traceability, execution spine, asks, and handoff

## Rollback

Phase 05b-2 is one SSH-signed commit and one annotated signed tag, `checkpoint/phosphor-2.0.0-phase-05b2`. Reverting it must reproduce the exact Phase 05b-1 tree at `62d7d5921080510bf530b3afbff0a8f89968527e`.

No application uninstall, signer migration, endpoint cleanup, or device action is required because this checkpoint activates no Android transport.
