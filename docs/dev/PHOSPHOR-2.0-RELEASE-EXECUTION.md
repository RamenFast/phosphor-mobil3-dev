# Phosphor 2.0 release execution spine

**Status:** Active
**Branch:** `release/phosphor-2.0.0`
**Started:** 2026-07-25
**Release target:** `v2.0.0`
**Authority:** Ben's 2026-07-25 release instruction, `vision/`, `spec/`, `decisions/2026-07-22-product-alignment.md`, Nexus consultation records, workspace governance, and device evidence

## Non-negotiable boundaries

- Do not modify either untracked user-owned `Phosphor build.md` file.
- Do not weaken Fortress permissions, silent first-party control, Tailscale behavior, Nexus parity, or privileged experiments to satisfy Play policy.
- Play and Fortress are compile-time distributions. Private implementation must be absent from the Play graph and artifact.
- Do not claim universal capture, silent MediaProjection renewal, transparent PiP, or live Nexus authority without direct evidence.
- Do not introduce Python.
- Phosphor dials outbound. Do not add a generic network listener or localhost authorization gate.
- Use explicit ADB serial `100.102.2.83:5555`. Never select the first `adb devices` row.
- Push release branches and prerelease checkpoints as authorized. Do not merge or push master/main without Ben's final confirmation.

## Durable evidence set

- Requirement matrix: `docs/dev/REQUIREMENT-TRACEABILITY.md`
- Append-only Play/Fortress ledger: `docs/dev/PUBLIC-RELEASE-DIVERGENCE.md`
- Baseline receipt: `docs/dev/receipts/phosphor-2.0/phase-00-baseline.md`
- Nexus consultation receipt: `docs/dev/receipts/phosphor-2.0/nexus-consultation-2026-07-25.md`
- Phase receipts: `docs/dev/receipts/phosphor-2.0/phase-NN-*`
- Binding release gates: `spec/ACCEPTANCE.md`
- Living session front door: `HANDOFF.md`
- Durable binary/device archive: `/media/ben/Mass storage/agenticTinkering/Codex/phosphor-2.0/`

## Phase protocol

Every phase follows this exact loop:

1. Update the applicable source in `vision/`, `spec/`, or `decisions/` before changing behavior.
2. Record intended files, tests, artifact checks, device checks, prohibited-behavior checks, and rollback command in the requirement matrix.
3. Keep new active behavior inert until its authorization and failure-path tests pass.
4. Build and test the phase independently.
5. Create named receipts with exact commands and results.
6. Confirm `Phosphor build.md` SHA-256 remains unchanged.
7. Commit only the phase.
8. Record `git revert <phase-commit>` as the rollback before enabling the next active capability.

A phase cannot advance with an unexplained missing receipt. A hardware or policy limit may close as `UNSUPPORTED` or `BLOCKED` only with dated evidence, a truthful product state, and a fix or fallback.

## Planned reversible phases

| Phase | Scope | Activation rule | Rollback |
|---|---|---|---|
| 00 | Baseline, asks, traceability, divergence, handoff | Documentation only | Revert phase-00 commit |
| 01 | Protocol, identity, signing, audio-truth, rollback contracts | Documentation and inert schemas only | Revert phase-01 commit |
| 02 | Play/Fortress flavors, package IDs, fail-closed signing, boundary scanner | No privileged implementation yet | Revert phase-02 commits before adding Fortress features |
| 03 | Inert causal state/action/provenance schemas and tests | No production readers/writers | Revert phase-03 commit |
| 04 | Store/reducer/persistence migration by functional slice | One slice at a time | Revert latest slice commit |
| 05 | Fortress Binder/tailnet session security and complete `pm3` projection | Transport stays inert until auth/revoke/receipt suite passes | Revert transport activation commit |
| 06 | Rotation, sheets, gestures, liveness, accessibility | Per-slice UI receipts | Revert latest UI slice |
| 07 | Themes, grid, beam, sounds, OOBE | Import remains inert and sandboxed | Revert latest instrument slice |
| 08 | PiP and Fortress overlay | Overlay stays unavailable until special-access recovery tests pass | Revert overlay activation commit |
| 09 | Standard audio matrix, shell experiment, Shizuku/sidecar decision, relay | Privileged capture only after S25 proof | Revert privileged implementation, preserve matrix |
| 10 | ProjectM field and beam-first load shedding | Field off by default until source/clock/performance receipts pass | Revert ProjectM activation |
| 11 | Play commerce, privacy, current policy evidence, public sanitizer | No public publish until clean independent build | Revert publication commit and retain divergence ledger |
| 12 | Signed artifacts, GitHub prerelease, S25 side-by-side activation, rollback rehearsal | Preserve debug package and data | Uninstall suffix package, reinstall archive if needed, import signed export |

## Orchestration law

At most three delegated workers run beside the primary orchestrator. Delegated work must be disjoint. The primary orchestrator owns:

- the causal model and integration order;
- independent verification and matrix status;
- signing and authorization activation;
- physical S25 changes and rollback;
- release and publication truth.

## Current checkpoint

Phase 00 is sealed and pushed at `70c8e2860bfe22310dd41bb341b19202580fef56`; rollback is `git revert 70c8e2860bfe22310dd41bb341b19202580fef56`. Phase 01 is active: protocol exits, reach paths, package/signing identities, grants, liveness, idempotency, audio truth, migration, and rollback are being made binding before behavior changes. Application behavior remains 1.0.7.
