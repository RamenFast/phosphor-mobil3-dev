# Phase 05b-1 observation-only Nexus projection receipt

**Date:** 2026-07-26
**Behavior changed:** Yes, in-process observation only
**Privileged behavior activated:** No
**Prior checkpoint:** `checkpoint/phosphor-2.0.0-phase-05a` at `36c9d43de6241af5284aaa45cfe538b6a6960775`
**Scope:** One Nexus-free atomic main-store observation, one Fortress-only in-memory authorized observation projector, exact trust-policy revocation lineage, adversarial tests, Play classpath absence proof, and deterministic source/artifact activation-boundary gates

## Result

Phase 05b-1 adds an authorized observation path without adding a second state authority or a mutation path. `PhosphorStateStore.observe()` captures the exact current snapshot, an immutable audit copy, and current store health in one critical section under the same monitor that serializes reducer execution, synchronous persistence, image publication, audit, counters, and idempotency.

The Fortress-only `NexusObservationDispatcher` owns the current session, token, grant ledger, and trust policy behind one monitor. It revalidates the exact observation candidate while retaining that monitor through the atomic store observation. State, audit, and geometry results obey least disclosure. Observation never dispatches an action or changes causal state.

## Delivered slice

- `app/src/main/kotlin/dev/phosphor/mobil3/store/PhosphorStateStore.kt` adds immutable `PhosphorStoreObservation` and synchronized `observe()`.
- `app/src/test/kotlin/dev/phosphor/mobil3/store/PhosphorStateStoreTest.kt` proves one-section snapshot/audit/health capture, save-before-publication, old-state plus degraded-health coherence after save failure, exact snapshot identity, immutable audit isolation, and no idempotency exposure.
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt` adds the Fortress-only in-memory observation authority and typed least-disclosure results.
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusContracts.kt` binds each opaque token-revocation result to the exact previous `NexusTrustPolicy` object. The dispatcher rejects stale or unrelated policy artifacts, so applying a revocation cannot replace the current policy lineage or discard earlier revocations.
- `app/src/testFortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcherTest.kt` exercises valid state/audit projections, typed geometry unavailability, stale generation, wrong trust, wrong session, wrong token, grant removal, explicit revoke, token revoke, disconnect, Binder death, heartbeat expiry, backward observation time, stale-policy replacement, and both permitted revoke/observe linearization orders.
- `app/src/test/kotlin/dev/phosphor/mobil3/distribution/DistributionCapabilitiesTest.kt` proves the Fortress observation classes are loadable only in Fortress and absent from Play.
- `decisions/2026-07-26-phase-05b1-observation-only.md` is the binding scope and rollback decision.
- `scripts/check-phase-05b1-boundary.sh` provides an always-JSON, self-describing Bash gate for the exact Phase 05a diff allowlist, prohibited source paths, broad Java/Android/network runtime-activation symbols, executable Python tooling and build/runtime Python dependency markers, Play source separation, inert `pm3`, mandatory desktop-adapter identity, repository-absolute Play artifact verification, signed Play artifact contents, and Fortress observation-class absence from Play DEX.
- `scripts/test-phase-05b1-boundary.sh` validates the gate's strict schema, declared/runtime exit alignment, enforced per-verb option contract, exit/error contract, escaping of every argv-representable JSON control character, truthful missing-`jq` response, source and artifact results, and isolated fail-closed counterexamples for NIO network activation, bare, path-qualified, quoted, command-wrapped, or backslash-continued executable Python, prohibited-path drift, missing desktop-adapter proof, working-directory helper substitution, and signed Python-bearing AAB content.

## Authority and disclosure truth

- `OBSERVE_STATE` returns only the exact `PhosphorStateSnapshot` and `StoreHealth`.
- `OBSERVE_AUDIT` returns only immutable audit records and `StoreHealth`; it does not add a state payload.
- `OBSERVE_GEOMETRY` returns a typed unavailable result with a repair instruction because geometry is not owned by the causal store.
- Authorization failures return only `NexusPreAuthRefusal.code` and `.fix`.
- A valid observation may linearize before a concurrent revoke. If revoke or closure linearizes first, the later observation refuses without state or audit disclosure.
- Observation does not change snapshot identity, revision, sequence, audit count, idempotency count, persistence writes, HUD presence, or session driving state.

## Final host validation

The corrected integrated focused gate passed:

```bash
./gradlew --no-daemon --rerun-tasks \
  :app:testPlayDebugUnitTest :app:testFortressDebugUnitTest \
  --tests 'dev.phosphor.mobil3.store.PhosphorStateStoreTest' \
  --tests 'dev.phosphor.mobil3.nexus.NexusContractsTest' \
  --tests 'dev.phosphor.mobil3.nexus.NexusObservationDispatcherTest' \
  --tests 'dev.phosphor.mobil3.distribution.DistributionCapabilitiesTest' \
  --console=plain
```

The definitive clean host gate then passed with:

- **144 Play debug JVM tests** and **188 Fortress debug JVM tests**, zero failures and errors;
- `lintPlayDebug`, `lintFortressDebug`, `lintPlayRelease`, and `lintFortressRelease`;
- signed Play and Fortress release APK and AAB builds;
- Play release signing fail-closed proof with every `PLAY_UPLOAD_*` input absent;
- a disposable external non-release Play fixture certificate, SHA-256 `10ffa59fe4cb846142981503186edcbac492ee865e0033b6411ca8c83c602dd1`;
- exact Fortress signer certificate SHA-256 `e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00`;
- exact application identities `dev.phosphor.mobil3` / `Phosphor` and `dev.phosphor.mobil3.fortress` / `Phosphor Fortress`;
- Play source/artifact boundary and Phase 05b-1 source/artifact boundary passes;
- Play DEX absence of `NexusObservationDispatcher` and `NexusObservationResult`;
- protected-note SHA-256 `beb8ae3a05b903580f70fb41bf5de3db99cfadae103ae0e929c19d8b406a3583`;
- zero tracked Python files and no Android build/runtime Python dependency;
- unchanged desktop adapter SHA-256 `66569383773d747ecffdcfe596a4a21b414bbd045e777a9d5e85ece045c91104`.

Final artifact SHA-256 values:

| Artifact | SHA-256 |
|---|---|
| Play release APK | `f4a86812e037cc1104bbed41846a9c9e82f844636052a2acff1c4f8924cdf363` |
| Play release AAB | `690219afbbf3d1f036d5d18b26a7c7dd03c6ba35d26d1f808fb09e988cf59a85` |
| Fortress release APK | `9a9e86c13d5c406ea67b256021ed54e1cb317d20c6f3f7a52c243abe803cb681` |
| Fortress release AAB | `fb7cf8e0da932bdeae3429c412e3f3fc35ee7c0139e9115346bb23700763a7fe` |

The durable evidence archive is:

```text
/media/ben/Mass storage/agenticTinkering/Codex/phosphor-2.0/phase-05b1-host-validation-20260726T191433Z
```

The dedicated observation-only boundary fixture also passed:

```bash
bash -n scripts/check-phase-05b1-boundary.sh scripts/test-phase-05b1-boundary.sh
shellcheck scripts/check-phase-05b1-boundary.sh scripts/test-phase-05b1-boundary.sh
scripts/test-phase-05b1-boundary.sh
```

It deterministically checks the source and current signed Play artifact, plus isolated fail-closed counterexamples for NIO network activation, bare, path-qualified, quoted, command-wrapped, and backslash-continued executable Python tooling, prohibited-path changes, absent desktop-adapter proof, working-directory helper substitution, signed Python-bearing AAB content, missing `jq`, invalid per-verb options, and every argv-representable JSON control character. This closes the decision record's runtime-activation, Python-dependency, artifact-helper integrity, desktop identity, and machine-envelope evidence requirements.

## Explicitly not proved or activated here

- No Nexus action dispatch, mutating shared-store fence, persisted Nexus provenance, runtime `DRIVING` transition, or HUD presence mutation.
- No Android signature permission, AIDL/Binder service, caller certificate check, production signer enrollment, process-death Binder receipt, or manifest component.
- No outbound tailnet client, endpoint connection, heartbeat from a real network, or listener of any kind.
- No live `pm3` protocol verb, S25 install or action, UI/bootstrap path, renderer/JNI change, permission, overlay, privileged capture, or desktop-adapter change.
- No durable Nexus authority database, durable authority audit, HUD decay after authority loss, or UI/Binder/tailnet/pm3 mutation parity.
- No master/main promotion. That remains human-gated.

Phase 05b mutation remains blocked until one persisted owner and one generic main-store commit-authorization fence atomically revalidate current session, token, trust, generation, transport, capability, grant, revocation, heartbeat, and driving state while the existing store monitor remains held through the reducer and `port.save`.

## Rollback

The commit carrying this receipt is the sole intended target of `checkpoint/phosphor-2.0.0-phase-05b1`. Treat the phase as sealed only after the SSH-signed commit and annotated signed tag resolve locally and remotely and an isolated checkout proves:

```text
git revert checkpoint/phosphor-2.0.0-phase-05b1
```

reproduces the exact Phase 05a tree at `36c9d43de6241af5284aaa45cfe538b6a6960775`. No runtime uninstall, device action, signer migration, preference migration, or transport cleanup is required because this checkpoint activates none of them.

Before the signed checkpoint commit, an isolated temporary candidate commit and `git revert` produced the exact Phase 05a tree `ce4c5b8fe4c7ee04e67528b2b5adf806d550e757`. The durable archive retains that rehearsal; the final signed commit is rechecked with the same exact-tree comparison before push.
