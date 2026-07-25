# Nexus consultation receipt, 2026-07-25

**Purpose:** Preserve the operational rulings Nexus returned after reading the current private Phosphor and Nexus trees.
**Repository mutation by consultation:** None
**Status:** Binding planning input, independently checked against current source before activation

## Source identity

The consultation was recovered from the dated Nexus working record that existed as the untracked path `20260725_160417_e66673` at baseline capture.

- Original path SHA-256 captured before it disappeared from the live Nexus worktree: `9403ba569d5f3d98ffe58a676798b37069e90da4f380824bc906cc11d3abc524`. The durable baseline metadata preserves its name and metadata.
- Focused consultation extract SHA-256: `2d63ea2e25ca4de5e9090e8997afb8c9caa247031a66fb6dd9f7d5866f9a266b`
- Analyst read-result SHA-256: `537eb6d2db8cd99c6aba8fa13bdddef50ad25e631ad40904c89e18e144001034`
- Durable copies: `baseline-20260725T2311Z/consultation/` beneath the Phase 00 archive.

The missing live path is not treated as proof of implementation. This receipt records only the recovered dated answer and current-tree checks.

## Rulings that still stand

1. One causal store and one provenance stamp project through UI, renderer, persistence, CLI, HUD, Binder, tailnet, and receipts.
2. Phosphor remains outbound-dialing. It must not expose a generic network listener or localhost authorization gate.
3. Nexus's existing desktop Phosphor hand is the exact argv-fixed seven-verb subset: `status`, `play`, `pause`, `xy`, `waveform`, `snapshot`, `schema`. There is no generic passthrough.
4. The full mobile observe-and-drive protocol uses same-phone Binder or authenticated tailnet. Both reach paths project the same store, provenance, refusal grammar, and revision order.
5. Same-phone access starts with an Android `signature` permission, then independently checks package, current certificate or accepted signing lineage, explicit enrollment/token, protocol compatibility, and capabilities.
6. Ben/Fortress and Google Play signing identities are distinct and must never be conflated.
7. Capabilities are independently inspectable, grantable, and revocable. Observe never implies drive. Geometry never implies mutation.
8. Liveness is visible state. `absent`, `authenticating`, `observing`, `driving`, and `closing` are represented rather than rendered as dead controls.
9. The HUD eye appears only during real observation. The hand appears only after accepted control. Both use the same session and provenance IDs and disappear within one heartbeat after disconnect or revoke.
10. Revocation closes active sessions and clears transient grants immediately. It is active authority behavior, not a read-only informational projection.
11. The first Play release exports no agent-control entry point.
12. Source and tests remain inert until the authorization, revocation, liveness, receipt, and real Android gates pass independently.

## Required corrections and guards

- CLI exits are binding: `0` success, `2` unavailable, `3` bad input, `4` runtime failure. The existing protocol text that assigned `2` to usage and `4` to unavailable is wrong and would split liveness semantics.
- The seven-verb desktop adapter and full mobile protocol must be named as two reach paths over one store. A separate mutation path is forbidden split state.
- The same-phone Binder service is greenfield in Phosphor. `CaptureService.onBind()` currently returns `null`; the promoted path must return a real `IBinder`, be protected by a signature permission and the inner trust wall, and have a negative test proving no localhost/TCP listener.
- Nexus current code is precedent, not automatically sufficient implementation: its live relay was independently found disk-aligned at v0.2.0 but unauthenticated on `0.0.0.0`, and its current app trust edge uses runtime certificate comparison rather than the required manifest-declared signature permission. Neither seam may be copied into Phosphor as-is.

## Phase projection

- Phase 01 fixes the protocol exits and names both reach paths, identities, liveness, grant, revoke, conflict, and rollback contracts.
- Phase 03 introduces the inert shared causal schemas.
- Phase 05 implements and independently proves the signature-permission Binder wall, inner authorization, full protocol, seven-verb compatibility projection, heartbeat decay, revocation, and receipts before activation.

## Rollback

This receipt changes documentation only and is reverted with the Phase 00 documentation commit. The sealed source extracts remain retained as historical evidence.
