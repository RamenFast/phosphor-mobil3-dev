# Phase 01 contracts and signing receipt

**Date:** 2026-07-25
**Behavior changed:** No
**Scope:** Protocol, package identity, signing custody, audio truth, migration, and rollback contracts

## Binding corrections

- Release target fixed at `2.0.0`.
- Play package fixed at `dev.phosphor.mobil3`.
- Fortress package fixed at `dev.phosphor.mobil3.fortress`.
- Existing seven-verb desktop adapter and full mobile Binder/authenticated-tailnet protocol named as strict projections of one store.
- CLI exits corrected to `0` success, `2` unavailable, `3` bad input, `4` runtime failure.
- Non-empty RFC 3339 timestamps, `data` envelopes, NDJSON `event`, schema self-description, and explicit ADB serial selection are binding.
- Trust tuple, signing lineages, capability grant/revoke, heartbeat, Binder death, idempotency, stale-revision conflict, provenance reuse, and audit rules are explicit.
- Audio states distinguish unavailable, permission needed, starting, opted-out/protected/silent, connected without signal, flowing, stalled, retrying, stopped, and error.
- Settings/theme migration and full release rollback contents are explicit.

## Signing custody check

The repository-local property file contains all four signing input names without exposing their values. The referenced keystore is outside the repository at `/home/ben/.secrets/phosphor-mobil3-release.jks` with mode `0600`.

`keytool` verified:

- alias: `phosphor-mobil3`;
- entry: `PrivateKeyEntry`;
- subject/issuer: `CN=phosphor mobil3, OU=RamenFast, O=RamenFast, C=US`;
- validity: 2026-07-19 through 2056-07-11;
- certificate SHA-256: `e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00`.

Repository-owned Android build-tools `apksigner` verified the baseline release APK uses that same certificate and a valid APK Signature Scheme v2 signature. This proves present key custody and artifact identity, not reproducibility or Phosphor 2.0 release readiness.

The current Gradle file still falls back from missing release signing to the debug signing config. That is an open implementation defect for Phase 02. Phase 01 makes the fail-closed behavior binding and adds negative acceptance gates; it does not claim the defect is fixed.

## Current identity truth

- Installed Phosphor debug: Android Debug certificate `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- Installed Nexus debug: the same development certificate.
- Fortress production target: RamenFast estate certificate `e4d14c...d9b00`.
- Play installed-app certificate: unknown until Play App Signing enrollment.

The current debug Nexus cannot cross a production Fortress signature permission. Production same-phone activation remains gated on Nexus backup, signed migration, import, negative tests, and rollback. Development enrollment is not signer parity.

## Validation commands

```bash
keytool -list -v -keystore <external-keystore> -alias <redacted> -storepass <redacted> -keypass <redacted>
.toolchain/Sdk/build-tools/36.0.0/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
git diff --check
git ls-files '*.py'
sha256sum 'Phosphor build.md'
```

No secret values are stored in this receipt.

## Prior checkpoint and rollback

Phase 00 commit and remote release-branch checkpoint:

```text
70c8e2860bfe22310dd41bb341b19202580fef56
```

Exact Phase 00 rollback:

```bash
git revert 70c8e2860bfe22310dd41bb341b19202580fef56
```

Phase 01 is documentation-only. After this receipt commits, annotated tag `checkpoint/phosphor-2.0.0-phase-01` is created at that phase commit before Phase 02 begins. Its exact rollback is:

```bash
git revert checkpoint/phosphor-2.0.0-phase-01
```
