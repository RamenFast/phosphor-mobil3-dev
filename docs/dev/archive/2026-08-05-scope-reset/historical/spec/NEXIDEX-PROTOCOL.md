# Nexidex first-party protocol

**Status:** Binding Phosphor 2.0 protocol
**Protocol version:** `phosphor.nexus/2`
**Nexus rulings:** facts #716 and #717 plus the dated 2026-07-25 refresh in `../docs/dev/receipts/phosphor-2.0/nexus-consultation-2026-07-25.md`

## 1. Purpose

Nexidex must be able to observe, configure, drive, and understand Fortress Phosphor comprehensively without a private UI automation layer. Human and agent access are two faces of the same typed actions and state.

The full agent surface is P0 in the current in-development Fortress build. The first public Play release keeps the shared causal store and schemas but exports no agent entry. A later Play-signed subset may use this same protocol after an explicit product/policy decision.

The protocol is custom for Phosphor and Nexus, but its command-line face MUST comply with the workspace Agent CLI Standard.

## 2. Two reach paths, one causal store

There are exactly two legitimate Nexus reach paths. They are projections of one action schema, one causal store, one revision order, one provenance grammar, one refusal grammar, and one audit log. Neither path may mutate a preference, renderer, service, or native object by a private side route.

### 2.1 Existing desktop host adapter

```text
Nexus phone or trusted agent
  -> nexus-relay, the one watched tailnet gate
  -> nexus-mobile/relay/src/phosphor.rs
  -> /usr/bin/phosphor
  -> desktop Phosphor runtime control socket
  -> desktop Phosphor causal store
```

The public adapter surface remains exactly seven argv-fixed verbs:

```text
status  play  pause  xy  waveform  snapshot  schema
```

The broker may use an internal liveness probe, but `probe` is not an eighth public control verb. There is no generic `ctl`, arbitrary argv, shell, RPC, or passthrough escape. The seven verbs are a strict compatibility subset of the complete Phosphor action model and return the same effective state, revisions, provenance, and fix-bearing refusals as the full protocol.

### 2.2 Full mobile protocol

#### 2.2.1 Remote mobile

```text
Phosphor mobile app
  -> outbound authenticated session
  -> Nexidex tailnet gate
  -> trusted agent requests over the established session
  -> Phosphor causal store
```

Phosphor MUST dial the trusted endpoint. Phosphor MUST NOT listen on Wi-Fi, mobile, tailnet, localhost, or a public interface.

Tailscale placement is not sufficient authentication. The outbound session MUST additionally verify a pinned endpoint identity and complete a nonce-bound application handshake carrying protocol version, principal identity, token identity, requested capabilities, and fresh session ID. Tokens are revocable, never logged, and never accepted as authority for a capability they do not contain.

#### 2.2.2 Same phone

```text
Nexidex Android app
  -> real bound Binder/AIDL service
  -> same protocol dispatcher
  -> same causal store
```

Binder is not a network port and is allowed as a P0 same-phone transport. A localhost socket is a second gate and is forbidden.

The service returns a real `IBinder`. A started service whose `onBind()` returns `null` does not satisfy this contract.

### 2.3 Shared dispatcher law

The full mobile Binder and tailnet transports terminate at the same protocol dispatcher. The desktop seven-verb adapter terminates at a compatibility projection over the same action/state grammar. All three faces use the same authentication concepts, capability grants, actions, state schema, provenance, consent, revoke behavior, idempotency rules, and audit records.

A contract test MUST execute every overlapping action through UI, `pm3`, Binder, authenticated tailnet, and the seven-verb adapter where applicable, then compare effective state, revision, provenance, acknowledgement, and refusal.

## 3. Trust identities

Trust is based on a tuple, not a package-name claim:

```text
package name
current signing certificate SHA-256
accepted signing lineage
build profile = play | fortress | nexus | local_dev
protocol version
```

The Phosphor 2.0 identity table is:

| Identity | Package | Required signing identity | Agent-entry status |
|---|---|---|---|
| Play | `dev.phosphor.mobil3` | Google Play App Signing certificate, recorded after enrollment | No exported entry in the first public release |
| Fortress | `dev.phosphor.mobil3.fortress` | RamenFast estate certificate SHA-256 `e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00` | Full protocol after activation gates |
| Nexus production | `dev.nexus.mobile` | RamenFast estate identity or a later explicitly accepted signing lineage | May cross the production signature-permission wall |
| Local development | package-specific | Explicitly enrolled development certificate | Development profile only |

The trust model MUST distinguish these identities even when one has no enabled agent transport yet:

- Play-signed Phosphor, modeled for future compatibility but without an exported first-release agent entry;
- Ben-signed Fortress Phosphor;
- Ben/Nexidex estate-signed Nexidex;
- local development identities explicitly enrolled for testing.

A certificate allowlist is required. Rotation uses Android signing lineage where available and requires both current-certificate and lineage checks. Revocation immediately closes active sessions and clears transient grants.

The same-phone service MUST be protected first by an Android signature permission and then by an inner package/certificate/capability check. This proof is the first Binder implementation gate.

The currently installed Nexus package is debug-signed. Explicit development enrollment does not bypass Android's production signature permission. Until Nexus is backed up, migrated, signed with the accepted estate identity, reinstalled, restored, and independently tested, Ben-signed Fortress same-phone Binder control remains truthfully unavailable. A debug-signed Phosphor/Nexus pair may exercise the local-development profile. Authenticated tailnet development access may enroll the debug identity separately without pretending signer parity.

Session establishment requires: package or node principal, current certificate or pinned node identity, accepted lineage where applicable, build profile, protocol version, fresh nonces, session ID, token ID, and requested capability set. Any mismatch fails before state disclosure beyond the minimum typed refusal and fix.

## 4. Capabilities

Capabilities are independently grantable:

- `observe.state`
- `observe.geometry`
- `observe.audit`
- `control.settings`
- `control.transport`
- `control.scope`
- `control.display`
- `control.themes`
- `request.permissions`
- `control.fortress`

Observe does not imply drive. Drive does not imply permission approval. Geometry streaming does not imply state mutation.

The user can grant, inspect, and revoke each capability. The Fortress master control may select a preset bundle, but every grant remains visible.

Persistent grants are scoped to the complete trusted principal tuple. Transient grants are scoped to one session and are cleared on revoke, Binder death, token revocation, protocol downgrade, package replacement, signer change, or process restart unless a separately receipted persistence rule says otherwise. A capability removed while an action is in flight prevents commit and returns `capability_revoked`.

## 5. Session liveness and presence

A session has explicit states:

```text
absent -> authenticating -> observing -> driving -> observing -> closing -> absent
```

The human HUD shows:

- an eye only while `observing` or `driving`;
- a hand only while an accepted action is actively being applied or visibly settled;
- the affected control and `NEXUS` provenance when driving;
- no ambient presence after disconnect.

Presence is a pure function of a real attached session, accepted command timing, acknowledgements, and real beam state. It cannot be manually faked by a theme.

The default heartbeat interval is 2 seconds and is negotiated only within 1 to 5 seconds. An explicit disconnect or revoke closes authority immediately and removes HUD presence no later than one negotiated heartbeat. Binder death closes immediately. Unexpected remote loss enters `closing` after three missed heartbeats, refuses further actions, clears transient grants, and then becomes `absent`. Heartbeat receipt timestamps use a monotonic clock for deadlines and wall-clock RFC 3339 timestamps for audit.

## 6. State snapshot and deltas

A snapshot contains:

```json
{
  "schema": "phosphor.state/2",
  "session": "...",
  "revision": 42,
  "ts": "...",
  "distribution": "fortress",
  "desired": {},
  "effective": {},
  "availability": {},
  "authority": {},
  "fixes": {},
  "capabilities": {},
  "provenance": {},
  "liveness": {}
}
```

Every delta contains:

```json
{
  "event": "state.delta",
  "revision": 43,
  "changes": [
    {
      "field": "display.hud",
      "old": "auto",
      "new": "on",
      "provenance": {
        "by": "nexus",
        "because": "user asked Nexus to pin the HUD",
        "transport": "binder",
        "session": "...",
        "ts": "..."
      }
    }
  ]
}
```

Deltas are ordered by revision and sequence. A gap requires a fresh snapshot. Consumers do not invent intermediate state.

## 7. Action model

There is one named action for every human control. Representative namespaces:

```text
scope.mode.set
scope.gain.set
scope.view_lock.set
scope.color.set
scope.projectm.set
source.select
source.capture.request
transport.play
transport.pause
display.pip.set
display.overlay.set
display.hud.set
rotation.scope_lock.set
rotation.ui_lock.set
theme.install
theme.preview
theme.keep
theme.revert
permission.open
permission.request
fortress.master.set
oobe.start
oobe.reset
```

Every mutating action carries an idempotency key, expected revision, reason, requested capability, and one provenance stamp created at acceptance. UI actions pass through the same dispatcher and fields even when the UI supplies them internally.

Idempotency is scoped to trusted principal plus key and survives reconnect/process restart for at least 24 hours in a bounded receipt index. Repeating the same key and canonical payload returns the original acknowledgement without rerunning effects or advancing revision. Reusing the key with a different payload refuses with `idempotency_conflict` and the original receipt ID.

An accepted action returns `changed`, new revision, effective value, and the original provenance stamp. A no-op returns `changed:false` and does not advance revision. A stale expected revision refuses before effects with `revision_conflict`, the current revision, and a refresh/retry fix. The action, state delta, HUD response, acknowledgement, and audit record all reuse the same provenance object and receipt ID.

## 8. Refusals and fixes

Every refusal is typed and fix-bearing. Examples:

- `system_rotation_locked`: Open Android rotation settings, then retry.
- `permission_requires_human`: Open the Android consent surface.
- `capability_not_granted`: Ask the user to grant `control.display`.
- `distribution_unavailable`: Install or connect to the Fortress build.
- `capture_source_opted_out`: Choose the deck, microphone, remote relay, or Fortress shell capture if available.
- `revision_conflict`: Refresh state and retry against revision N.
- `idempotency_conflict`: Generate a new key or replay the original payload.
- `capability_revoked`: Re-establish a session and request the capability again.
- `signer_migration_required`: Complete the signed Nexus backup/reinstall/import gate or use the authenticated development route.

No action silently falls back to a different behavior.

## 9. Contention and last writer

The accepted latest mutation wins, ordered by store revision. There is no hidden priority that lets Nexus continuously fight the human.

If human and agent touch the same control in a short contention window:

- the most recent accepted action is effective;
- the UI identifies the current writer;
- the audit stream records both actions;
- an optional `contested:true` indicator persists briefly;
- continuous agent automation must yield while the human is actively manipulating that control unless the user explicitly selected an automation mode.

## 10. Beam and geometry stream

Geometry is a separate bounded stream, provisionally `G` frames, containing enough information for Nexus to understand the live instrument without scraping pixels.

It MUST include:

- source and clock identity;
- frame sequence and monotonic timestamp;
- scope mode;
- normalized beam segments or sampled geometry;
- beam energy and signal state;
- layer identity for true beam, Phosphor geometry effects, and ProjectM field metadata;
- drop count and quality tier.

The ProjectM field itself need not stream full pixels. Its preset, parameters, timing, feature values, and layer state are agent-readable. The true beam geometry remains available.

Streaming is backpressured and rate-limited. Dropped geometry increments an honest counter rather than blocking audio or rendering.

## 11. Capture truth model

The protocol, renderer, UI, HUD, relay, and CLI share these canonical capture states:

- `unavailable`
- `permission_needed`
- `starting`
- `present_silent_or_opted_out`
- `connected_no_signal`
- `flowing`
- `stalled`
- `retrying`
- `stopped`
- `error`

`present_silent_or_opted_out` MUST not be collapsed into `connected_no_signal`. Its `cause` is one of `source_opt_out`, `protected_or_drm`, `silent_content`, or `unknown`, and only evidence may select the first two. `unavailable` includes an authority and fix. `retrying` includes attempt, next retry timestamp, and bounded backoff.

## 12. Theme protocol

Required verbs:

```text
pm3 theme schema
pm3 theme validate <path>
pm3 theme add <path>
pm3 theme list
pm3 theme show <id>
pm3 theme preview <id>
pm3 theme keep
pm3 theme revert
pm3 theme export <id> --output <path>
pm3 theme remove <id>
```

Preview is non-destructive and self-reverts on timeout, background/resume boundary, session loss, or explicit revert. The human UI shows `KEEP` and `REVERT` while a preview is active.

## 13. CLI contract

`dev/pm3` remains the command-line face. One-shot output uses:

```json
{"status":"ok","tool":"pm3","version":"...","ts":"...","data":{}}
```

Errors include `error`, `message`, and `fix`. Exit codes are binding across `pm3`, the desktop adapter, and all projectors: `0` success, `2` unavailable dependency/capability, `3` bad input or usage, and `4` runtime failure. Streams are NDJSON with a canonical `event` field. `pm3 schema` self-describes commands, fields, enums, capabilities, formats, exits, and device-selection rules.

Every one-shot has non-empty RFC 3339 `ts` and a `data` object. Every device operation requires `--serial <serial>` or `PM3_SERIAL`; it never silently chooses the first `adb devices` row. If a discovery verb offers candidates, it returns all candidates and requires an explicit subsequent selection.

No CLI verb may depend on Python.

## 14. Audit and receipts

Audit records are append-only within a bounded local retention policy. They include authentication, heartbeat expiry, Binder death, grant/revoke, idempotent replay/conflict, actions, refusals, capture transitions, theme mutations, and elevated operations.

Sensitive material such as purchase tokens, raw audio, private file paths, and authorization tokens is redacted by default.

The human can export a reviewed receipt. The agent can request the same structured receipt if granted `observe.audit`.

## 15. Cross-app respondent

Nexidex's HUD and Phosphor's HUD respond to the same session state:

- Nexus shows Phosphor attached and whether it is observing or being driven.
- Phosphor shows Nexus's eye/hand and the affected surface.
- Both decay after explicit disconnect/revoke within one negotiated heartbeat, immediately on Binder death, and after the declared missed-heartbeat timeout on unexpected network loss.
- Both report identical session and provenance IDs.

The animation communicates a real relationship. It is not an idle mascot loop.
