# Phase 04 operational HUD store receipt

**Date:** 2026-07-26
**Behavior changed:** Yes, for local `display.hud` only
**Privileged behavior activated:** No
**Scope:** One production reducer/store/persistence/UI projection slice for the existing HUD on/auto/off control

## Result

Phase 04 makes `display.hud` the first operational projection of the sealed `phosphor.state/2` contracts. The visible HUD setting, rendered HUD visibility, accepted snapshot, delta, acknowledgement, settings projection, audit record, idempotency record, persisted causal image, rollback mirror, and user feedback all originate from one `PhosphorStateStore` action path.

This receipt does not claim that the remaining 39 state fields, renderer mutations, Nexus, Binder, tailnet, CLI, permissions, capture, overlay, PiP, or ProjectM are operational through the store. They remain later reversible slices.

## Delivered slice

- `store/DisplayHudReducer.kt` accepts only `SetDisplayHud` for local HUMAN/UI or MIGRATION/MIGRATION principals holding `control.display`.
- `store/PhosphorStateStore.kt` owns the published snapshot, bounded audit/idempotency indexes, receipt counters, persistence ordering, listeners, and fix-bearing health state.
- `store/PhosphorStorePersistence.kt` defines the atomic image and persistence port without Android, Binder, transport, renderer, or effect-runner dependencies.
- `settings/CausalStatePreferences.kt` attempts one synchronous SharedPreferences commit for the private causal envelope and rollback-compatible `hud_mode`; if Android reports a disk failure after mutating its in-memory map, a compensating editor restores the exact prior in-process values and the store remains on its prior published image.
- `settings/HudCausalEnvelopeCodec.kt` binds schema, checksum, compiled identity, HUD value, revision, sequence, wall clock, retained receipt/idempotency counters, audit/refusal records, idempotency acknowledgements/deltas, and a receipt-keyed provenance pool. It recursively sorts every JSON object key, preserves array order, hashes the explicit canonical payload bytes, and rejects any root byte stream that is not the exact canonical re-encoding.
- `ui/ScopeUiState.kt` exposes HUD as a read-only Compose projection of the store snapshot. It contains no mutable HUD value.
- `MainActivity.kt` constructs the correct local-development, Play-release, or Fortress-release base identity; grants only local HUMAN and MIGRATION `control.display`; dispatches UI changes; and reconciles verified archive HUD values through a MIGRATION action.
- `ui/Controls.kt` and `ui/Sheets.kt` disable the HUD control and show the repair when the store becomes read-only.

## Ordering and provenance

For a changed HUD request:

1. clocks and receipt capacity are checked, then authorization, validation, availability, authority, and the current principal/capability grant are checked before an idempotency replay can be returned;
2. an existing same-principal/same-key request replays or conflicts before the new-key index-capacity and expected-revision gates; a new accepted request must still reserve its idempotency ordinal without overflow;
3. one `ProvenanceStamp` is authored;
4. desired/effective HUD, revision, sequence, field provenance, delta, acknowledgement, audit, settings projection, and idempotency record reuse that stamp;
5. the complete `PhosphorStoreImage` is encoded and synchronously committed;
6. only after a successful commit does the store publish the snapshot to Compose listeners.

A failed commit retains the exact prior snapshot, audit, idempotency index, and UI HUD value, restores the prior SharedPreferences in-memory projection after Android's fail-after-memory-mutation behavior, then publishes a fix-bearing read-only health state. A no-op persists its receipt and idempotency record without changing snapshot identity, revision, sequence, delta, or effects. A same-key/same-payload replay returns the original acknowledgement and writes only a replay audit. A same-key/different-payload request returns a conflict containing the original receipt.

## Persistence and migration truth

- New installs use HUD `off`, revision `0`, no invented provenance, then persist that baseline.
- Legacy `hud_mode` values map exactly: `0=on`, `1=auto`, `2=off`.
- Only when `hud_mode` is absent does `nerd_hud=true/false` map to `on/off`.
- Malformed present legacy values do not clamp or fall through; the store becomes read-only with a repair.
- A valid private causal envelope always wins over legacy mirrors.
- Checksum, schema, compiled distribution/build mismatch, malformed nested content, invalid counters, missing provenance, re-derived provenance, and unsupported HUD-slice records fail closed as `CorruptEnvelope`; there is no silent legacy fallback.
- Settings archives omit both the private causal envelope and the private pending-import marker. Imported `hud_mode` is excluded from the generic preference commit. Before dispatch, Phosphor durably records a pending operation keyed by archive digest, HUD value, and a random operation identifier. A live Activity applies it only while lifecycle-started. A replacement Activity resumes a marker found during foreground entry, and a lifecycle-owned local SharedPreferences listener wakes that foreground Activity if the marker is committed after it already resumed. The originating Activity suppresses its own marker wake while its import callback is still in flight. Every marker read is type-aware and fails closed. Generic import `commit(false)` restores the exact prior typed in-memory values and reports whether compensation itself committed. HUD application preserves the original idempotency key after process death or a lost acknowledgement. Marker write, compare/remove, compensation, and post-remove observation share one process-wide transaction lock, so a replacement writer cannot be deleted by an older cleanup. Cleanup returns and surfaces exact changed-marker, remove-failure, restore-failure, and post-observation repair text; the marker clears only after accepted/replayed application, a successful cleanup commit, and observed absence. A later fully cleared intentional import receives a fresh operation.
- Debug variants use `local_dev/local_development`; release variants use the matching Play or Fortress identity.

## Validation

The final host evidence archive is:

```text
/media/ben/Mass storage/agenticTinkering/Codex/phosphor-2.0/phase-04-host-validation-20260726T095903Z
manifest SHA-256: 73fddc9e80215c6331f7de10d6833c80ed415b2e40bdd1d58c578dd97d4954e8
```

The clean gate first proved that release signing fails closed when `PLAY_UPLOAD_*` inputs are absent, then created a disposable external non-release Play fixture and ran:

```text
./gradlew --no-daemon --rerun-tasks :app:testPlayDebugUnitTest
./gradlew --no-daemon --rerun-tasks :app:testFortressDebugUnitTest
./gradlew --no-daemon --rerun-tasks \
  :app:lintPlayDebug :app:lintFortressDebug

./gradlew --no-daemon --rerun-tasks \
  :app:testPlayDebugUnitTest \
  --tests 'dev.phosphor.mobil3.store.DisplayHudReducerTest' \
  --tests 'dev.phosphor.mobil3.store.PhosphorStateStoreTest' \
  --tests 'dev.phosphor.mobil3.settings.CausalStatePreferencesTest' \
  --tests 'dev.phosphor.mobil3.ui.ScopeUiStateHudProjectionTest' \
  --tests 'dev.phosphor.mobil3.ui.HudUiIntegrationHelpersTest'

./gradlew --no-daemon --rerun-tasks \
  :app:testFortressDebugUnitTest \
  --tests 'dev.phosphor.mobil3.store.DisplayHudReducerTest' \
  --tests 'dev.phosphor.mobil3.store.PhosphorStateStoreTest' \
  --tests 'dev.phosphor.mobil3.settings.CausalStatePreferencesTest' \
  --tests 'dev.phosphor.mobil3.ui.ScopeUiStateHudProjectionTest' \
  --tests 'dev.phosphor.mobil3.ui.HudUiIntegrationHelpersTest'
```

All isolated commands passed from the corrected tree. The complete copied JUnit receipts report **140 Play tests and 140 Fortress tests**, with zero failures, errors, or skips. Both debug lints passed with zero errors. The state-space coverage includes changed acceptance, no-op, replay/conflict/revocation, stale revisions, principal/capability refusal, idempotency expiry/full-index/counter exhaustion, audit age and clock rollback, persistence failure compensation, listener failure/reentrancy/concurrency and registration ordering, corrupt-envelope lockout, exact JSON key and scalar typing, recursive key-sorted canonical JSON bytes, exact-root re-encoding, encoder/decoder cap symmetry, HUD-only retained grammar, snapshot/replay ordering, provenance identity/orphan rejection, legacy precedence and malformed lockout, archive/pending-marker exclusion, process-death and late-marker recovery, same-Activity wake suppression, lifecycle refusal, wrong-type markers, transaction-locked cleanup/replacement ordering, cleanup post-observation and restore failure status, honest import counts, trusted export, disabled degraded controls, and Compose projection.

A subsequent clean build produced and inspected:

```text
f9eeb39d4fc6fba839964d9ce39d590ca23e22ecd88ad32d2c7910440b0ccc79  app-play-release.apk
36332583ddc62c22ea58b7a01764611472ecc8e7b7ea077ce20b36ea5efb098b  app-play-release.aab
29c04a1ddf26edb71b87871225b55dbc75fd96ee13a7ad7bc8cec8fbae748d19  app-fortress-release.apk
```

The Play fixture APK is `dev.phosphor.mobil3`, version `2.0.0`, label `Phosphor`, certificate SHA-256 `c09de60c2b13009e020c8c0a56ffa0840b3541e0f37f36f63c03c7617d3e1543`. The Fortress APK is `dev.phosphor.mobil3.fortress`, version `2.0.0`, label `Phosphor Fortress`, and is signed by the ratified RamenFast estate certificate `e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00`.

Play boundary fixtures, source scan, and final AAB scan passed. `git diff --check` passed. The protected `Phosphor build.md` SHA-256 remained `beb8ae3a05b903580f70fb41bf5de3db99cfadae103ae0e929c19d8b406a3583`. `git ls-files '*.py'` remained empty. Static gates found no direct `hud_mode` writer outside the persistence adapter, no mutable HUD copy, and no privileged activation marker in the new slice.

`concourse doctor --json` remained honestly non-green with exit `4`: 64 pass, 3 warnings, and 1 known unrelated `nexus-model` probe failure caused by that tool treating a string as a model object. The full doctor output is retained in the archive and is not represented as a Phosphor Phase 04 failure.

## Explicitly not proved here

- Physical S25 tap, process-death, SharedPreferences disk-failure, or frame-time evidence for the HUD row.
- Binder, tailnet, CLI, or live Nexus observation/control.
- Renderer/native migration for any setting.
- Operational coverage for any state field other than `display.hud`.
- Production Google Play signing identity, Play Console submission, or final S25 installation. The host gate used a clearly named disposable Play fixture; Fortress used the ratified estate certificate.
- Universal/protected audio capture, transparent overlay, or ProjectM behavior.

## Rollback

The commit carrying this receipt is the sole intended target of `checkpoint/phosphor-2.0.0-phase-04`. Treat the phase as sealed only after the signed annotated tag resolves locally and remotely and an isolated checkout proves:

```text
git revert checkpoint/phosphor-2.0.0-phase-04
```

reproduces the exact Phase 03 tree. The rollback restores the legacy direct HUD path while retaining the rollback-compatible `hud_mode` mirror. Master/main promotion remains human-gated.
