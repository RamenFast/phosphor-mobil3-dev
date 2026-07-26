# Public release divergence ledger

**Status:** Append-only
**Rule:** Never edit or delete a historical entry. Add a later entry with `supersedes` when facts or policy change.

This ledger records only differences required to publish the Play distribution. It is not permission to weaken, redefine, or remove Fortress behavior.

## Entry schema

Each entry contains:

- `id`
- `date`
- `capability`
- `fortress_behavior`
- `play_blocker`
- `policy_evidence`
- `required_public_change`
- `owner`
- `receipt`
- `supersedes`

---

## PRD-0001

- **date:** 2026-07-25
- **capability:** First-party Nexus control entry
- **fortress_behavior:** Full same-phone Binder and authenticated tailnet control projects the shared causal store, actions, provenance, capability grants, revocation, liveness, HUD, and receipts.
- **play_blocker:** The first Play release has no approved exported first-party agent-control product surface. Shipping a hidden or undocumented control entry would violate the ratified distribution boundary and increase review/security risk.
- **policy_evidence:** `vision/PHOSPHOR-LIVING-INSTRUMENT.md`; `spec/NEXIDEX-PROTOCOL.md`; `spec/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`. Official Google policy must be rechecked immediately before submission.
- **required_public_change:** Exclude Binder service, remote agent transport, private endpoint configuration, private trust pins, and Nexus activation UI from the Play source/dependency graph, manifest, and AAB. Retain inert shared state/action schemas only.
- **owner:** Nexus/control phase
- **receipt:** Pending `A-02`, `C-06`, and public sanitizer receipt.
- **supersedes:** None

## PRD-0002

- **date:** 2026-07-25
- **capability:** Shizuku, shell audio, and ADB sidecar
- **fortress_behavior:** Fortress may run bounded UID 2000 capture experiments and, if proven safe, a Shizuku UserService or authenticated shell-owned sidecar with truthful lifecycle and revocation.
- **play_blocker:** These are privileged developer mechanisms outside the first Play product boundary and must not be present merely disabled.
- **policy_evidence:** `spec/AUDIO-CONNECTIVITY-AND-PROJECTM.md`; `spec/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`. Current Play policy evidence is pending the pre-submission review.
- **required_public_change:** Exclude APIs, dependencies, metadata, manifests, strings, setup flows, sidecar binaries, and private protocol details from Play source and artifacts.
- **owner:** Audio/distribution phases
- **receipt:** Pending `A-02`, `K-05`, `K-06`, boundary scanner, and sanitizer receipt.
- **supersedes:** None

## PRD-0003

- **date:** 2026-07-25
- **capability:** Transparent application overlay
- **fortress_behavior:** True transparent `SYSTEM_ALERT_WINDOW` overlay with disclosure, persistent recovery controls, click-through option, sizing, center fade, frame cap, and burn-in shift.
- **play_blocker:** Special-access policy and review evidence have not yet approved this exact use for the first Play release.
- **policy_evidence:** `spec/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md` section 7. Official policy and disclosure requirements must be rechecked immediately before submission.
- **required_public_change:** Omit the overlay permission, service, implementation, and user flow from the initial Play graph and AAB. Keep PiP as the honest fallback and do not call it transparent.
- **owner:** Display/distribution phases
- **receipt:** Pending `A-02`, `J-03`, `J-04`, and Play manifest scan.
- **supersedes:** None

## PRD-0004

- **date:** 2026-07-25
- **capability:** Signing and package identity
- **fortress_behavior:** Ben-signed `dev.phosphor.mobil3.fortress`, co-installable with the preserved debug app, using explicit Fortress trust and signing lineage.
- **play_blocker:** Play App Signing uses an independently governed installed-app identity and cannot silently replace a differently signed local package.
- **policy_evidence:** `spec/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`; Android package signature enforcement; Play Console evidence pending enrollment.
- **required_public_change:** Build Play as `dev.phosphor.mobil3`, fail closed when release signing is unavailable, provide signed settings/theme export-import, and document whether Play begins a new lineage or preserves an accepted one.
- **owner:** Distribution/release phases
- **receipt:** Pending certificate report, export/import test, and side-by-side installation receipt.
- **supersedes:** None

## PRD-0005

- **date:** 2026-07-25
- **capability:** Private endpoints, trust pins, receipts, and privileged protocol detail
- **fortress_behavior:** Private repository contains the complete estate integration, private endpoints, certificate allowlists, authorization receipts, and developer diagnostics.
- **play_blocker:** Publishing private trust material or implementation detail would expand attack surface and violate the sanitized-source boundary.
- **policy_evidence:** Workspace Fortress posture; `spec/NEXIDEX-PROTOCOL.md`; Ben's 2026-07-25 release instruction.
- **required_public_change:** Public publisher stages only an explicit allowlist, rejects private markers, emits a complete diff and sanitizer receipt, and builds the public checkout independently.
- **owner:** Public publisher/release phases
- **receipt:** Pending public allowlist, secret scan, independent build, and downloaded-asset checksum verification.
- **supersedes:** None

## PRD-0006

- **date:** 2026-07-25
- **capability:** Ratified signing lineages and data-bearing migration
- **fortress_behavior:** `dev.phosphor.mobil3.fortress` uses the Ben-controlled RamenFast estate certificate `e4d14c...d9b00`, remains co-installable, and does not disturb the installed debug app or its data.
- **play_blocker:** Installed Play builds use a separate Google Play App Signing identity. They cannot update or coexist under `dev.phosphor.mobil3` with the currently installed unrelated debug signer.
- **policy_evidence:** Ben's 2026-07-25 instruction; `decisions/2026-07-25-phosphor-2.0-release-contracts.md`; Android package signature enforcement; Play Console certificate evidence pending enrollment.
- **required_public_change:** Publish Play as `dev.phosphor.mobil3`; exclude the Fortress keystore, certificate trust implementation, private receipts, and suffix activation paths; provide versioned settings/theme export-import and document the explicit debug-to-Play migration.
- **owner:** Distribution/release phases
- **receipt:** `docs/dev/receipts/phosphor-2.0/phase-01-contracts-and-signing.md`; artifact and migration tests pending.
- **supersedes:** PRD-0004

## PRD-0007

- **date:** 2026-07-26
- **capability:** Proven compile-time distribution, signing, and migration boundary
- **fortress_behavior:** `dev.phosphor.mobil3.fortress` compiles from its own source set, accepts private endpoint seeding, signs only with the pinned RamenFast estate certificate, and remains ready for later privileged implementation without claiming that implementation is active.
- **play_blocker:** The initial Play graph must contain no Fortress package, Binder/Nexus control, Shizuku, ADB-sidecar, shell-capture, overlay, private endpoint, or private trust implementation. Google Play App Signing identity is still unknown before enrollment.
- **policy_evidence:** `decisions/2026-07-25-phosphor-2.0-release-contracts.md`; `spec/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`; official Play policy still requires a fresh pre-submission review.
- **required_public_change:** Build `dev.phosphor.mobil3` from the Play source set; require complete external upload-signing inputs; compile an unconditionally empty seeded-host list; run the Gradle-owned source/graph/manifest/archive boundary gate; expose only inert, allowlisted settings migration.
- **owner:** Distribution/release phases
- **receipt:** `docs/dev/receipts/phosphor-2.0/phase-02-distributions-signing-migration.md`; S25 co-install, signed rollback export, Play enrollment, and public sanitizer remain pending.
- **supersedes:** PRD-0006
