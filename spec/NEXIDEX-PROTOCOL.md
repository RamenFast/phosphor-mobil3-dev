# Nexidex first-party protocol

**Status:** Binding protocol design
**Nexus rulings:** facts #716 and #717, preserved with raw fact/session pointers in `NEXUS-RULINGS-2026-07-22.md`

## 1. Purpose

Nexidex must be able to observe, configure, drive, and understand Fortress Phosphor comprehensively without a private UI automation layer. Human and agent access are two faces of the same typed actions and state.

The full agent surface is P0 in the current in-development Fortress build. The first public Play release keeps the shared causal store and schemas but exports no agent entry. A later Play-signed subset may use this same protocol after an explicit product/policy decision.

The protocol is custom for Phosphor and Nexus, but its command-line face MUST comply with the workspace Agent CLI Standard.

## 2. One-gate transport law

### 2.1 Remote

```text
Agent on trusted computer
  -> Nexidex tailnet gate
  -> authenticated Phosphor client session
  -> Phosphor causal store
```

Phosphor MUST dial the trusted endpoint. Phosphor MUST NOT listen on Wi-Fi, mobile, tailnet, localhost, or a public interface.

### 2.2 Same phone

```text
Nexidex Android app
  -> real bound Binder/AIDL service
  -> same protocol dispatcher
  -> same causal store
```

Binder is not a network port and is allowed as a P0 same-phone transport. A localhost socket is a second gate and is forbidden.

Both transports use the same authentication concepts, capability grants, actions, state schema, provenance, consent, revoke behavior, and audit log.

## 3. Trust identities

Trust is based on a tuple, not a package-name claim:

```text
package name
current signing certificate SHA-256
accepted signing lineage
build profile = play | fortress | nexus
protocol version
```

The trust model MUST distinguish these identities even when one has no enabled agent transport yet:

- Play-signed Phosphor, modeled for future compatibility but without an exported first-release agent entry;
- Ben-signed Fortress Phosphor;
- Ben/Nexidex estate-signed Nexidex;
- local development identities explicitly enrolled for testing.

A certificate allowlist is required. Rotation uses Android signing lineage where available. Revocation immediately closes active sessions and clears transient grants.

The same-phone service MUST be protected first by an Android signature permission and then by an inner package/certificate/capability check. This proof is the first Binder implementation gate.

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

## 6. State snapshot and deltas

A snapshot contains:

```json
{
  "schema": "phosphor.state/1",
  "session": "...",
  "revision": 42,
  "ts": "...",
  "distribution": "fortress",
  "state": {},
  "effective": {},
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

Actions carry an idempotency key, expected revision where mutation safety matters, reason, and requested capability.

An accepted action returns `changed`, new revision, effective value, and the original provenance stamp. A no-op returns `changed:false`. A conflict returns the current revision and a fix.

## 8. Refusals and fixes

Every refusal is typed and fix-bearing. Examples:

- `system_rotation_locked`: Open Android rotation settings, then retry.
- `permission_requires_human`: Open the Android consent surface.
- `capability_not_granted`: Ask the user to grant `control.display`.
- `distribution_unavailable`: Install or connect to the Fortress build.
- `capture_source_opted_out`: Choose the deck, microphone, remote relay, or Fortress shell capture if available.
- `revision_conflict`: Refresh state and retry against revision N.

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

The protocol and HUD share these states:

- `no_capability`
- `permission_needed`
- `starting`
- `present_silent_or_opted_out`
- `connected_no_signal`
- `flowing`
- `stalled`
- `backoff`
- `stopped`
- `error`

`present_silent_or_opted_out` MUST not be collapsed into `no_signal`. It tells the user and agent that capture exists but the upstream app may be refusing it.

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

Errors include `error`, `message`, and `fix`. Exit codes are 0 success, 2 usage, 3 operational or validation failure, and 4 unavailable dependency/capability. Streams are NDJSON with a canonical `event` field. `pm3 schema` self-describes commands, fields, enums, capabilities, and formats.

No CLI verb may depend on Python.

## 14. Audit and receipts

Audit records are append-only within a bounded local retention policy. They include authentication, grant/revoke, actions, refusals, capture transitions, theme mutations, and elevated operations.

Sensitive material such as purchase tokens, raw audio, private file paths, and authorization tokens is redacted by default.

The human can export a reviewed receipt. The agent can request the same structured receipt if granted `observe.audit`.

## 15. Cross-app respondent

Nexidex's HUD and Phosphor's HUD respond to the same session state:

- Nexus shows Phosphor attached and whether it is observing or being driven.
- Phosphor shows Nexus's eye/hand and the affected surface.
- Both decay on disconnect within one poll/heartbeat window.
- Both report identical session and provenance IDs.

The animation communicates a real relationship. It is not an idle mascot loop.
