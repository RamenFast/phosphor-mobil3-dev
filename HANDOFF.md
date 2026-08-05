# Handoff: primetime cleanup in progress

**Scope decision:** `decisions/2026-08-05-product-scope-reset.md`

**Rollback tag:** `checkpoint/phosphor-2.0.0-pre-primetime-cleanup`

**Protected baseline receipt:** `docs/dev/receipts/primetime-cleanup/phase-00-baseline.md`

## Current state

Ben approved a repository and product reset on 2026-08-05.

The active target is one Play-safe Android app with Android 10 support. Nexus, product-agent control, tracking-like audit state, Fortress distribution, privileged capture research, ProjectM promises, and dormant commerce scaffolding leave the active product.

Local playback, microphone and MediaProjection capture, the existing scope interface, Tailscale connectivity, and PC relay playback remain.

The three pre-existing dirty files are preserved byte-for-byte under `docs/dev/archive/2026-08-05-scope-reset/protected/`. The active working tree no longer depends on them.

## Execution order

1. Replace the active product authority and archive obsolete planning documents.
2. Remove Nexus, causal authority, audit, and dormant entitlement code.
3. Collapse Play and Fortress into one debug/release build.
4. Lower `minSdk` to 29 and add platform compatibility adapters.
5. Harden capture permission ordering and preserve relay behavior.
6. Run the complete Android, Rust, relay, shell, privacy, and artifact gates.
7. Reconcile Git history into `main`.
8. Build, verify, and install the canonical release.

## Current release truth

No existing `2.0.0` APK or AAB is a release candidate. Several differently hashed artifacts share the same version identity.

The next release must be rebuilt from the final tagged source. Its build manifest must bind the commit, package, version, signer, and hashes.

## Human gates

- Push to private or public `main`.
- Production signing-key custody and Play upload-key provisioning.
- Publishing the HTTPS privacy policy.
- Any uninstall required by an incompatible production signer.
- Final GitHub publication and Play Console submission.

## Durable boundaries

- Do not author or invoke Python.
- Use explicit S25 serial `100.102.2.83:5555` for every ADB action.
- Keep protocol v2 inside a trusted local network or Tailscale.
- Do not redesign relay authentication during this cleanup.
- Do not infer the next two core features from archived plans.
