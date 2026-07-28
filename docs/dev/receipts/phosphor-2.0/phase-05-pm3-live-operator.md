# Phase 05 pm3 live operator surface

This slice adds the Fortress-only `content://${applicationId}.pm3` operator provider and wires `dev/pm3` to it.

## Provider contract

- Component: `dev.phosphor.mobil3.nexus.admin.Pm3AdminProvider` in the Fortress source set only.
- Manifest: exported provider, authority `${applicationId}.pm3`, protected by `android.permission.DUMP`.
- Runtime guard: `call()` also rejects callers except Android shell/root UID.
- Method: `pm3`.
- Request Bundle key: `request`, a base64url JSON object `{"verb":"...","args":{...}}`.
- Response Bundle key: `response`, a base64url JSON object with `ok`, `protocol`, `verb`, and `data`, or a refusal object with `fix`.

## Live verbs

- `state-get`
- `state-watch`, bounded polling only
- `nexus-status`
- `nexus-grant`, exact persistent trust tuple grant
- `nexus-revoke`, exact trust tuple/capability revoke
- `action-run` for `display.hud.set`, via `Transport.CLI` and local-human principal through the store reducer
- `audit-list`
- `audit-export`, transactional snapshot payload with base64url bytes and SHA-256

## Boundaries

The provider uses the single `PhosphorApplication.causalStore`. It creates no store, server, localhost socket, network client, secret surface, or Play provider. The CLI remains shell-only, JSON one-shot by default, and emits canonical root `event` for `state-watch` NDJSON.

## Shared API follow-up

Owl's current durable grant/revoke helpers are methods on `NexusObservationDispatcher`, which requires an already-authenticated runtime session. pm3 needs a bootstrap operator path. The clean shared signature needed is a store-scoped durable authority administrator, for example:

```kotlin
internal object NexusDurableAuthorityAdmin {
    fun grantPersistent(store: PhosphorStateStore, grant: NexusCapabilityGrant, wallTimeMillis: Long): NexusAuthorityMutationResult
    fun revokePersistent(store: PhosphorStateStore, trustKey: NexusTrustKey, capability: Capability, wallTimeMillis: Long): NexusAuthorityMutationResult
}
```

That would let Binder/tailnet and pm3 share exactly one implementation while keeping the authority plane inside the causal store.
