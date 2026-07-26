# Phase 05a inert Nexus and pm3 contracts receipt

**Date:** 2026-07-26
**Behavior changed:** Yes for the host `dev/pm3` interface; no mobile runtime authority changed
**Privileged behavior activated:** No
**Scope:** Fortress-only pure Kotlin Nexus trust/session/authorization-projection contracts, Fortress-only adversarial tests, Play classpath boundary proof, and a Bash-only agent-first host CLI

## Result

Phase 05a establishes the inert contract boundary that Phase 05b must implement without inventing a second state writer. Fortress now has typed Nexus protocol, identity, enrollment, challenge, token, grant, session, revoke, heartbeat, observation, and authorization-candidate contracts. Play has none of those classes. `dev/pm3` is a strict, self-describing host interface whose currently implemented build/device verbs are covered by fixtures and whose Phase 05 runtime protocol verbs fail explicitly with exit `2`, stable error data, and a fix.

This receipt does not claim a live Nexus connection, real Binder/AIDL service, tailnet client, shared-store commit permit, runtime `DRIVING` state, signer enrollment, persistent grant database, S25 behavior, renderer/native mutation, or desktop/mobile parity. Those remain later reversible slices.

## Delivered contract slice

- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusContracts.kt` defines protocol/build/reach identity, canonical Android signing lineage and pinned tailnet identity, explicit production/development enrollment, pre-auth non-disclosure refusals, server-owned nonce issuance, token validation/revocation, and authenticated identity results.
- `NexusNonceLedger.ServerIssuer` is a private trusted-server seam with a private constructor and private issue method. Ordinary public Kotlin, Java, or JVM reflection callers cannot create an initial ledger, mint a `NexusChallenge`, or construct the private issuance implementation. The inert contract does not claim resistance to hostile `setAccessible` code already running inside the trusted app process. Authentication consumes the exact issuance-bound challenge and ledger, rejects stale/not-yet-valid/replayed challenge, session, server nonce, and client nonce tuples, and never accepts a peer-selected reusable session identity.
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusSessionContracts.kt` defines capability ceilings, persistent/transient grant scopes, lifecycle generation, heartbeat deadlines, explicit disconnect/Binder-death/token-revoke closure, transient cleanup, read-only hand/eye presence, and authorization candidates. Opaque transition and revocation results cannot be copied or proved by peers.
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusDispatcherContracts.kt` exposes observation and future commit requirements only. Phase 05a intentionally contains no store-origin commit permit, fresh-commit evidence, dispatcher implementation, effect runner, or public `beginDriving` path.
- `app/src/testFortress/kotlin/dev/phosphor/mobil3/nexus/NexusContractsTest.kt` exercises the valid contract path and adversarial signer, protocol, nonce, replay, grant, heartbeat, revoke, stale-generation, copied-proof, session reuse, and false-driving counterexamples.
- `app/src/test/kotlin/dev/phosphor/mobil3/distribution/DistributionCapabilitiesTest.kt` proves the Nexus contract classes are loadable in Fortress and absent from Play.
- Nexus production contracts and their behavioral test suite exist only under `app/src/fortress` and `app/src/testFortress`; the shared distribution test named above is the deliberate classpath-boundary assertion. No manifest, Gradle, service, provider, receiver, socket, persistence, UI, renderer, JNI, permission, or overlay file changes belong to this phase.

## pm3 interface

- `dev/pm3` remains Bash-only and exposes a flat verb set with exact nested `{status,tool,version,ts,data}` one-shot envelopes.
- `schema` returns a recursive draft 2020-12 contract with strict objects, exact per-verb data schemas, explicit error/exit contracts, and distinct logcat event/error record schemas.
- Streams are canonical NDJSON with `event`; diagnostics stay on stderr; errors carry stable `error`, `message`, and `fix` fields; exits remain `0` success, `2` unavailable, `3` usage, and `4` runtime failure.
- Device verbs require `--serial` or `PM3_SERIAL` before adb discovery and always invoke adb with `-s`. Missing serial wins over missing/broken adb, so the CLI never silently selects the first device row.
- Play/Fortress and debug/release task, package, and APK mappings are explicit. Release signing discovery is fail closed and supports test overrides without bypassing certificate verification.
- Screenshot and screen-record writes are transactional. A failed capture, pull, cleanup, or save does not replace a pre-existing destination. The fixture Gradle path does not overwrite or delete pre-existing fixed-path build outputs.
- `state-get`, `state-watch`, `nexus-status`, `nexus-grant`, `nexus-revoke`, `action-run`, `audit-list`, and `audit-export` are declared side-effect-free Phase 05 placeholders and return exit `2` with an exact fix until the authenticated shared-store projection exists.
- `scripts/test-pm3.sh` covers syntax, shellcheck, strict schemas, every implemented one-shot contract, representative errors and exits, NDJSON records, explicit serial selection, device/Gradle/keytool fixtures, signing failures, transactional success/failure, fixed artifact preservation, and unavailable Phase 05 verbs.

## Validation

The definitive Phase 05a host archive and its manifest verify successfully:

```text
/media/ben/Mass storage/agenticTinkering/Codex/phosphor-2.0/phase-05a-host-validation-20260726T171833Z
manifest SHA-256: 8054517a9ca00a98c3faec7261ac177926a311c66cc95fe7258bb5ff11a0280b
```

The forced full JVM suites passed with `141` Play tests and `170` Fortress tests, with zero failures, errors, or skips. The isolated Fortress Nexus contract suite and both Play/Fortress class-boundary tests passed. All four Play/Fortress debug/release lint reports contain zero fatal issues, errors, warnings, or other findings. `scripts/test-pm3.sh`, Play source fixtures, Play source boundary, and Play AAB artifact boundary all passed.

The clean signed artifacts are:

```text
16fc82bf50350ce2b9ef07baced891de1d36435386771d7546e4a019dfb7e41b  app-play-release.apk
8365434f756e1cdc2e116532ff6b418b15aba7635dcd2ac5d159e02532ae02cb  app-play-release.aab
a2d342003faa446e988799d502f11c80570f4fed29482725359a572c958541df  app-fortress-release.apk
```

Play identity is `dev.phosphor.mobil3` / `Phosphor` / `2.0.0`; Fortress identity is `dev.phosphor.mobil3.fortress` / `Phosphor Fortress` / `2.0.0`. The disposable non-release Play fixture certificate is `0c2a2ab2efeac3e5b44330fc39ba8317c5a1e8fbbddc8ff0f386c5105de5d64f`; the Fortress certificate is `e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00`. Play release signing also failed closed when its external credentials were removed.

The Play APK and AAB contain zero Nexus classes; the Fortress APK contains the Nexus contracts. The ordinary public JVM issuer probe reports `private_server_issuer=true` and `public_jvm_challenge_mint_surface=0`. Every Phase 05 runtime `pm3` placeholder exits `2`. The protected note hash remained `beb8ae3a05b903580f70fb41bf5de3db99cfadae103ae0e929c19d8b406a3583`, tracked Python remained zero, and the desktop adapter remained unchanged at `66569383773d747ecffdcfe596a4a21b414bbd045e777a9d5e85ece045c91104`.

`concourse doctor` returned its known unrelated estate status: `64` pass, `3` warn, `1` fail, exit `4`. The sole failure is the pre-existing `nexus-model` probe assuming an object where its registry value is a string. It does not inspect or execute this Phosphor candidate and is retained honestly in the archive.

## Explicitly not proved here

- Android signature permission, AIDL/Binder service registration, caller certificate verification, Binder death from a real process, or live Nexus signer migration.
- An outbound tailnet transport, endpoint pinning on a real connection, heartbeat/network-loss behavior, or a generic listener of any kind.
- Atomic shared-store authorization/currentness fencing, a genuine store-origin fresh-commit receipt, durable one-use receipt consumption, or the only legal runtime transition to `DRIVING`.
- Live UI/Binder/tailnet/pm3 equality for effective state, revision, provenance, acknowledgement, refusal, audit, and idempotency behavior.
- S25 installation, explicit-serial device mutation, process death, network loss, or physical observation.
- Desktop seven-verb adapter parity. The existing adapter remains unchanged and must be parity-tested against the later mobile projection.
- Production Google Play installed-app signing identity, Play Console enrollment, public publication, renderer/native settings migration, privileged capture, overlay, PiP, or ProjectM.

## Rollback

The commit carrying this receipt is the sole intended target of `checkpoint/phosphor-2.0.0-phase-05a`. Treat the phase as sealed only after the signed annotated tag resolves locally and remotely and an isolated checkout proves:

```text
git revert checkpoint/phosphor-2.0.0-phase-05a
```

reproduces the exact Phase 04 tree at `9020c21dc8669b938d2f096ba042ad6b2631ba7c`. Phase 05b must not activate a Binder/tailnet/shared-store transport until that rollback proof and its own authorization/currentness tests pass. Master/main promotion remains human-gated.
