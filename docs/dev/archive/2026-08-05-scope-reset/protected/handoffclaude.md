# Phosphor 2.0 Phase 05 Runtime Handoff

**Created:** 2026-07-26

**Primary repository:** `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`

**Companion repository:** `/home/ben/Dev/ClaudeWorkspace/nexus-mobile`

**Current branch:** `release/phosphor-2.0.0`

**Current sealed base commit:** `62d7d5921080510bf530b3afbff0a8f89968527e` (`phase 05b-1: seal observation-only Nexus projection`)

**Current sealed base tag:** `checkpoint/phosphor-2.0.0-phase-05b1`

## Purpose and intended end state

The immediate job is to finish and seal the active Phase 05 runtime, not merely make the candidate compile.

Phase 05 must end with all of the following true at the same time:

1. `PhosphorStateStore` is the one durable and causal authority for Phosphor state and Nexus authority.
2. Every accepted Nexus mutation, whether received over Binder or tailnet, revalidates the exact current session, generation, token, grant, revocation, requested capability, idempotency key, and expected revision while the store monitor remains held through synchronous persistence and publication.
3. Fortress has a secure local Binder transport with exact caller and signing-lineage verification, bounded typed JSON transactions, single-use challenges, replay protection, death handling, and no pre-auth disclosure.
4. Fortress has an explicit outbound-only tailnet runtime with pinned mutual authentication, bounded P frames, generation fencing, shared authority, correct heartbeat decay, typed replies, reconnect handling, and no listener or discovery path.
5. Nexus-mobile speaks the exact Binder protocol and its relay exposes the exact authenticated Phosphor P-frame lane.
6. `pm3` safely administers live status, grants, revocations, actions, audit, and tailnet configuration using explicit device selection and agent-first envelopes.
7. Play contains no Binder service, AIDL, provider, private Nexus runtime, tailnet runtime, or Fortress-only implementation in its source or built artifacts.
8. Host tests, built-artifact scans, live S25 adversarial tests, independent reviews, receipts, a signed checkpoint, and an exact rollback rehearsal all pass.

The ratified execution spine has **8 remaining stage groups** from the sealed Phase 05b-1 checkpoint: finish Phase 05, then Phases 06 through 12. Do not begin Phase 06 until the Phase 05 runtime described here is fully sealed.

## Non-negotiable safeguards

- **Never open, read, modify, stage, delete, or commit** the untracked file `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/Phosphor build.md`.
- Hash-only integrity checks of that protected file are acceptable if needed.
- Do not touch the unrelated Nexus-mobile untracked receipt `docs/receipts/screen-20260724-214558.png`.
- Do not use Python.
- Use explicit S25 serial `100.102.2.83:5555` for every ADB action. Never select the first device implicitly.
- Read and follow the `/android-adb` skill before any phone action.
- If root is needed on the workstation, use `sudoplz sudo ...`. Never use plain `sudo`.
- Do not push `main` or `master` without explicit user confirmation.
- Preserve the sealed Phase 05b-1 commit and tag as the rollback base.
- There is substantial uncommitted and untracked work in both repositories. Do not reset, clean, checkout over, or broadly restore the working trees.
- No current Phase 05b-2 work has been committed.

## Architecture that must not regress

### One shared durable authority

`PhosphorStoreImage.authorityPlane` is the sole durable Nexus authority source. There must not be a parallel authority preference, file, port, in-memory acknowledgement plane, or transport-owned dispatcher.

Durable authority includes the typed, checksummed authority envelope and the durable fields needed for restart safety, including persistent grants, revoked token IDs, consumed nonces, and consumed idempotency information.

Transient session and liveness state may be published at runtime in `PhosphorStateSnapshot`, but persisted store images must normalize runtime Nexus session, Nexus capabilities, and liveness back to absent. Active sessions and transient grants must not survive process restart.

### Exact mutation linearization

A volatile monitor around `store.dispatch` is not enough. Revocation can otherwise race between authorization and `port.save`.

The required path is:

1. Transport authenticates and constructs a typed projection.
2. `NexusObservationDispatcher` holds its authority monitor.
3. It enters `PhosphorStateStore.dispatchAuthorized`.
4. `PhosphorStateStore` holds its existing store monitor.
5. The authorization fence revalidates exact currentness while both relevant monitors remain held.
6. The reducer, idempotency handling, audit, counter updates, `port.save(nextImage)`, image publication, and listener enqueue occur in the existing shared-store order.
7. No acknowledgement is returned unless the durable save succeeded.
8. Save failure publishes read-only health and fails closed.

Authority-only changes use `PhosphorStateStore.commitAuthorityPlane`, which also saves and publishes under the same store monitor. Its current early no-op return intentionally avoids saving unchanged images and avoids poisoning health when a rejected or replayed authority update changes nothing.

### Binder boundary

- Fortress production owns the Binder service through `PhosphorApplication.causalStore` and `NexusRuntimeManagers.forStore(store)`.
- `NexusBinderRuntimeRegistry` is only an override seam.
- AIDL is exactly:

  `String transact(String requestJson, IBinder clientToken)`

- Protocol is `phosphor.nexus/2`.
- Every request carries `protocol`, `verb`, fresh `request_id`, fresh `nonce`, wall-clock `issued_at_millis`, and `payload`.
- Signature permission is checked before JSON parsing or disclosure.
- Then verify UID, package, current signer, full accepted signing lineage, profile, protocol, challenge binding, token, generation, and capability.
- Challenge disclosure before authentication is minimal: challenge ID, server nonce, protocol, and server-clock expiry.
- Authentication must use a fresh outer request ID and nonce. It binds the original challenge request ID, original client nonce, server nonce, caller UID, package, signing lineage, challenge ID, and trust tuple.
- Requested capabilities are intersected with the configured capability ceiling and exact durable persistent grants. No auto-grant is allowed.
- Binder heartbeat is only a non-mutating currentness proof. Binder liveness belongs to death-recipient and explicit disconnect, not tailnet heartbeat persistence.
- Binder death and explicit disconnect must durably close the shared authority before local transient state is cleared.
- Replayed action dispatch is a successful typed replay of the original acknowledgement, not a generic Binder replay error.
- Setup cancellation is bound to the exact caller and exact `IBinder` token. A late authentication completion after cancellation must immediately close its shared authority.

### Tailnet boundary

- Fortress only initiates an outbound TCP connection to one explicit configured host and port.
- No listener, bind, accept, UDP discovery, multicast, network-interface discovery, localhost fallback, or implicit endpoint exists in production tailnet code.
- Framing is `[tag][u32 big-endian JSON length][UTF-8 JSON]`.
- Welcome tag is `0x57`. Phosphor RPC tag is `0x50`.
- Protocol is `phosphor.tailnet.rpc/1`.
- Endpoint identity is pinned and mutually authenticated.
- Client proof domain is `phosphor.tailnet.rpc.client-auth/v1`.
- Server challenge proof domain is `phosphor.tailnet.rpc.server-challenge/v1`.
- The proof tuple is SHA-256 over each UTF-8 part followed by NUL: domain, protocol, principal, pinned endpoint identity, token ID, decimal generation, client nonce, server nonce, secret.
- The production adapter must use `NexusRuntimeManagers.forStore(store)` and the shared dispatcher. It must not construct its own store, dispatcher, token authority, or nonce ledger.
- Requested capabilities must all exist as durable persistent grants for the exact tailnet trust tuple.
- Heartbeat policy is three missed acknowledgements to CLOSING and four to ABSENT.
- A late acknowledgement after CLOSING must not revive authority.
- CLOSING retains enough exact shared state to durably reach ABSENT on the fourth miss.
- Local state clears only after the shared durable transition succeeds.
- Reconnect advances generation and stale frames cannot affect the new generation.

## Current candidate state

The working trees contain the full uncommitted Phase 05b-2 candidate across Phosphor, Nexus-mobile, and the relay.

### Important Phosphor paths

- `app/build.gradle.kts`
- `app/src/fortress/AndroidManifest.xml`
- `app/src/fortress/aidl/dev/phosphor/mobil3/nexus/binder/IPhosphorNexusBinder.aidl`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusAuthorityPersistence.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusObservationDispatcher.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusRuntimeManager.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusSessionContracts.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/admin/Pm3AdminProvider.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/binder/*`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/nexus/tailnet/*`
- `app/src/main/kotlin/dev/phosphor/mobil3/PhosphorApplication.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/store/CommitAuthorization.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/store/PhosphorStateStore.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/store/PhosphorStorePersistence.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/settings/HudCausalEnvelopeCodec.kt`
- focused Play and Fortress tests under `app/src/test` and `app/src/testFortress`
- `dev/pm3`
- `scripts/test-pm3.sh`
- `decisions/2026-07-26-phase-05b2-nexus-mutation.md`
- `docs/dev/receipts/phosphor-2.0/phase-05-pm3-live-operator.md`

### Important Nexus-mobile paths

- `app/src/main/kotlin/dev/nexus/mobile/phosphor/PhosphorBinderClient.kt`
- `app/src/main/kotlin/dev/nexus/mobile/phosphor/PhosphorRuntime.kt`
- `app/src/main/kotlin/dev/nexus/mobile/phosphor/PhosphorSecurity.kt`
- `app/src/test/kotlin/dev/nexus/mobile/phosphor/PhosphorSecurityTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/dev/nexus/mobile/CoreService.kt`
- `relay/src/phosphor.rs`
- `relay/src/proto.rs`
- `relay/src/rpc.rs`
- `relay/src/session.rs`
- generated capability projections under `dev/capability/generated/v2`

### Last correction made before this handoff

A real tailnet lifecycle defect was found and corrected:

- CLOSING advanced the shared Nexus session generation.
- The production adapter still matched the original wire identity against the advanced session generation.
- The manager also retained stale session state while the dispatcher advanced.
- As a result, the fourth missed heartbeat could fail to durably transition CLOSING to ABSENT, and later disconnect could fail exact dispatcher identity checks.

The correction added exact shared-manager ownership for tailnet heartbeat and liveness transitions, kept the manager's session object identical to the dispatcher's current session object, retained the original wire identity separately, made local clearing conditional on durable transition success, carried the truthful configured endpoint into authentication, and prevented late acknowledgements from resetting misses after CLOSING.

A regression test now proves heartbeat refresh, CLOSING at the third miss, refusal of late heartbeat revival, ABSENT at the fourth miss, absent snapshot projection, and dispatcher removal.

### Validation already green

These passed before the handoff:

1. Focused Phosphor authority, shared runtime manager, and tailnet tests after the final lifecycle correction:

   ```bash
   ./gradlew \
     :app:compileFortressDebugKotlin \
     :app:compileFortressDebugUnitTestKotlin \
     :app:testFortressDebugUnitTest \
     --tests 'dev.phosphor.mobil3.nexus.NexusObservationDispatcherTest' \
     --tests 'dev.phosphor.mobil3.nexus.NexusRuntimeManagerTest' \
     --tests 'dev.phosphor.mobil3.nexus.tailnet.*' \
     --no-daemon --rerun-tasks
   ```

   Result: `BUILD SUCCESSFUL`, 21 seconds.

2. Nexus-mobile focused Phosphor client compile and unit tests:

   ```bash
   ./gradlew \
     :app:compileDebugKotlin \
     :app:compileDebugUnitTestKotlin \
     :app:testDebugUnitTest \
     --tests 'dev.nexus.mobile.phosphor.*' \
     --no-daemon --rerun-tasks
   ```

   Result: `BUILD SUCCESSFUL`.

3. Nexus relay formatting, all Rust tests, and capability catalog:

   ```bash
   cargo fmt --manifest-path relay/Cargo.toml -- --check
   cargo test --manifest-path relay/Cargo.toml -- --nocapture
   dev/capability/project-catalog.sh check v2 --json
   ```

   Result: 192 Rust unit tests and 5 e2e tests passed. Capability catalog reported `drift:false`.

4. Complete pm3 fixture gate:

   ```bash
   ./scripts/test-pm3.sh
   ```

   Result: all syntax, shellcheck, schema, exit-code, explicit-serial, live-provider fixture, secret-boundary, authority-transaction, artifact-restoration, and Play-absence checks passed.

5. Play focused store and settings tests had passed earlier, including the authority-plane schema v2 and verified legacy v1 migration tests.

### Validation not yet green or not yet rerun

A full dual-flavor matrix was run before the final authority test corrections and tailnet lifecycle correction. It failed on seven Fortress authority tests. Those seven focused tests were corrected and now pass in the focused suite, but the full matrix has **not** been rerun after the final changes.

No final post-change APK boundary scan, live S25 validation, independent final review, commit, tag, push, or rollback rehearsal has been performed.

## Ordered work to complete next

### 1. Re-establish integrity without altering the tree

1. Confirm `HEAD` remains `62d7d5921080510bf530b3afbff0a8f89968527e`.
2. Confirm the base tag still resolves to that commit.
3. Record `git status --short` in both repositories while excluding the protected note and unrelated Nexus receipt from any operation.
4. Confirm no unexpected agent is still editing the files.
5. Review the complete diff in logical groups. Do not use a broad reset or checkout.
6. Hash the protected note only if an integrity comparison is needed. Never read it.
7. Confirm there are no tracked Python files or newly introduced Python invocations.

### 2. Review the final tailnet lifecycle correction before expanding work

The last patch is focused-test green but late in the integration cycle. Review these exact symbols together:

- `NexusObservationDispatcher.recordHeartbeatSession`
- `NexusRuntimeManager.recordTailnetHeartbeat`
- `NexusRuntimeManager.evaluateTailnetLiveness`
- `ProductionTailnetSharedRuntimePort.recordHeartbeat`
- `ProductionTailnetSharedRuntimePort.transitionRemoteLiveness`
- `ProductionTailnetSharedRuntimePort.closeIfMatched`
- `PhosphorTailnetRuntime.pump`

Verify:

1. The manager and dispatcher always retain the exact same current `NexusSession` object where identity checks use `!==`.
2. A save failure leaves the manager, adapter, and dispatcher on the prior exact state.
3. CLOSING is persisted once at the third missed acknowledgement.
4. A late acknowledgement after CLOSING does not reset misses or call heartbeat successfully.
5. ABSENT is persisted at the fourth miss even though the shared session generation advanced at CLOSING.
6. Connection-finally disconnect cannot overwrite or fake a prior failed close.
7. Local adapter state clears only after a durable close or absent transition succeeds.
8. Reconnect can neither replace nor conceal an authority state whose durable close failed.

Add deterministic tests for save failure during CLOSING, save failure during ABSENT, save failure during ordinary connection-close, and reconnect after each failure. Existing tests cover the successful path, but these failure schedules are the highest-risk remaining seam.

### 3. Finish tailnet response fidelity and runtime truthfulness

The tailnet lane works and is tested, but its production response payloads are not yet at the same fidelity as Binder.

1. Make `state.get` return the full typed snapshot and health contract, including liveness, capabilities, provenance, availability, authority, fixes, and all intended settings fields.
2. Make `audit.list` return bounded structured records with count, health, provenance, and refusal fields. Honor any declared `limit` argument rather than silently returning the complete audit.
3. Make accepted and replayed `display.hud.set` replies carry the full original `ActionAcknowledgement`, including disposition, changed, revision, sequence, effective value, receipt ID, provenance, delta, effects, and nested acknowledgement.
4. Make refusals carry typed code, fix, current revision, and original receipt ID where available.
5. Ensure adapter-thrown errors are converted to a stable typed tailnet refusal rather than free-form `error` text.
6. Add serialization contract tests. Replayed dispatch must reproduce the original acknowledgement exactly.
7. Add an actual socket-to-production-adapter integration test. Current socket tests use a recording authority, and manager tests exercise shared state separately. There is no single deterministic test that spans wire, production adapter, shared manager, dispatcher, store save, and typed response.
8. Review or remove compatibility helpers `authDigest` and `parseSeededRemoteHosts`. They currently encode legacy or default identities and should not become an alternate production auth/config path.

### 4. Add a truthful tailnet bootstrap status surface

`PhosphorTailnetRuntimeBootstrap` currently exposes install, start, and shutdown only. `pm3 tailnet-status` reports stored preferences, not actual runtime state.

Implement a small, thread-safe status snapshot that can report at least:

- installed or not installed
- running or stopped
- configured endpoint host and port without secrets
- current generation if authenticated
- current presence or connection state
- last failure category or redacted fix
- whether shutdown completed

Do not expose token secret, proof, nonce, or full pinned identity in logs.

Then update pm3 so:

- `tailnet-status` distinguishes configured, installed, running, connected/authenticated, closing, absent, and stopped.
- `tailnet-start` refuses truthfully if no complete config and encrypted secret exist.
- `tailnet-start` reports actual start state rather than appending `started:true` unconditionally.
- `tailnet-stop` reports actual shutdown state.
- `tailnet-configure` reports whether a restart was actually attempted and whether it succeeded.
- provider bootstrap reports a redacted failure rather than silently swallowing every exception.

Add provider and CLI fixture coverage for these states.

### 5. Close remaining pm3 data-contract gaps

1. `audit-export` currently accepts `from_revision` but appears to export all records. Either implement the declared filtering semantics or remove the misleading input from the schema and command.
2. Expand pm3 acknowledgement JSON beyond `effectiveValue.toString()` so it is typed and causally useful.
3. Expand audit JSON beyond receipt ID, kind, and wall time if the CLI claims a full audit surface.
4. Verify the SharedPreferences rollback helper handles a failed rollback commit. A second `commit(false)` must not leave the process believing the refused configuration is authoritative.
5. Review the encrypted-secret path. The input byte array and ciphertext bytes are zeroed, but decryption currently creates an immutable `String` that cannot be zeroed.
6. `openFile` writer-thread failures cannot be returned as the normal typed provider response. Ensure oversized or persistence-failed secret ingestion becomes a detectable nonzero CLI outcome and cannot be reported as success.
7. Decide whether clearing tailnet configuration should also remove the Android Keystore key. It is safe to retain only if no ciphertext remains and the intended lifecycle is documented.
8. Rerun `./scripts/test-pm3.sh` after every provider or bootstrap change.

### 6. Re-run and harden shared authority tests

Run the complete `NexusObservationDispatcherTest`, `NexusRuntimeManagerTest`, Binder package tests, and tailnet package tests together after the previous changes.

Preserve the corrected authority-test semantics:

- An absent durable authority plane refuses attach without attempting a save. It should not manufacture a storage failure.
- A persistence failure after an active authority existed makes subsequent observation fail closed. It must not continue disclosing state from a now-unwritable authority.
- A corrupt authority plane fails closed without constructing an invalid projection first.
- A loaded durable plane does not merge caller-supplied persistent grants. The constructor returns a refused attach result rather than necessarily throwing.
- Nonce consumption must be seeded before constructing an admitted dispatcher and must survive later heartbeat/session saves and restart.
- Concurrency tests must block the intended save, not constructor authentication persistence.

Add or confirm tests for:

1. Grant save failure.
2. Revoke save failure.
3. Nonce save failure.
4. Revocation racing authorization and durable action save.
5. Revoke during authority save.
6. Replay after restart.
7. Corrupt authority envelope.
8. Transient grant removal after restart.
9. Persistent grant survival after restart.
10. Constructor fail-closed behavior.
11. Lifecycle audit and receipt counter atomicity.
12. No-op authority transactions performing no save, publication, audit, or health poisoning.

### 7. Finish Binder host validation

Run:

```bash
./gradlew \
  :app:compileFortressDebugKotlin \
  :app:compileFortressDebugUnitTestKotlin \
  :app:testFortressDebugUnitTest \
  --tests 'dev.phosphor.mobil3.nexus.binder.*' \
  --tests 'dev.phosphor.mobil3.nexus.NexusRuntimeManagerTest' \
  --no-daemon --rerun-tasks
```

Confirm source and tests still prove:

1. One-method AIDL only.
2. Signature permission before JSON handling.
3. UID, package, current signer, and full signing-lineage checks before disclosure.
4. Minimum challenge disclosure.
5. Server wall-clock challenge lifetime.
6. Fresh outer request ID and nonce for auth.
7. Exact caller and challenge tuple binding.
8. Durable nonce consumption and replay refusal after restart.
9. Requested capability ceiling intersected with exact durable grants.
10. No auto-grant.
11. Exact action object `{ "type": "display.hud.set", "mode": "auto|on|off" }` only.
12. Bounded request and response behavior.
13. Full typed observe, audit, accepted, replayed, refused, and failed replies.
14. Binder heartbeat does not call tailnet heartbeat mutation.
15. Exact-token setup cancellation and late-auth closure.
16. Token replacement refusal.
17. Binder death and explicit disconnect use the shared manager and clear local state only after durable closure.

### 8. Re-run Nexus-mobile and relay gates after any protocol change

If the server wire changes, update the Nexus-mobile client and relay in the same iteration.

Nexus-mobile Binder rules to retain:

- explicit `ComponentName` binding
- package visibility for the Fortress service
- caller-side package and signing-lineage verification
- bounded transactions, including setup
- fresh outer request IDs and nonces
- challenge and auth fields exactly matching the server contract
- death and bounded reconnect handling
- setup cancellation with a fresh typed disconnect
- no secret logging

Run:

```bash
cd /home/ben/Dev/ClaudeWorkspace/nexus-mobile
./gradlew \
  :app:compileDebugKotlin \
  :app:compileDebugUnitTestKotlin \
  :app:testDebugUnitTest \
  --tests 'dev.nexus.mobile.phosphor.*' \
  --no-daemon --rerun-tasks

cargo fmt --manifest-path relay/Cargo.toml -- --check
cargo test --manifest-path relay/Cargo.toml -- --nocapture
dev/capability/project-catalog.sh check v2 --json
```

The relay full suite passed once after an earlier single flaky connection-reset failure. Continue treating the e2e lane as potentially flaky until repeated full-suite runs remain green.

### 9. Run the complete Phosphor dual-flavor host matrix

This is the next mandatory broad gate after code completion:

```bash
cd /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3
./gradlew \
  :app:compilePlayDebugKotlin \
  :app:compileFortressDebugKotlin \
  :app:compileFortressDebugUnitTestKotlin \
  :app:testPlayDebugUnitTest \
  :app:testFortressDebugUnitTest \
  :app:assemblePlayDebug \
  :app:assembleFortressDebug \
  --no-daemon --rerun-tasks
```

Do not accept a focused-only green result as completion.

After this passes, run all project lint variants and the signed release artifact tasks that the prior sealed phases used. Preserve the exact build identity behavior:

- debug builds use `local_dev`
- Play release uses Play identity
- Fortress release uses Fortress and Nexus identity
- store bootstrap accepts a compiled-identity initial snapshot and never hardcodes Play

### 10. Prove Play source and artifact absence

Run both source and built-artifact scans.

Source requirements:

- no import or reference to `dev.phosphor.mobil3.nexus` from `app/src/main` or Play source
- no Fortress Binder, AIDL, provider, tailnet, or private runtime source in the Play source graph
- neutral distribution strings are not themselves a failure
- main `PhosphorApplication` may select an initial snapshot from compiled identity but must not import Fortress-only classes

Built Play APK requirements:

- no `IPhosphorNexusBinder`
- no Binder service class
- no private signature permission
- no `Pm3AdminProvider`
- no tailnet classes
- no Fortress-only Nexus implementation classes
- no Fortress provider or service manifest entry

Built Fortress APK requirements:

- exact one-method AIDL exists
- expected signature permission exists
- Binder service exists
- pm3 provider exists and is protected correctly
- production signer defaults and capability ceiling are as intended

Review `app/build.gradle.kts`: the current default Binder capability ceiling was expanded to `observe.state,observe.audit,control.display`. This remains durable-grant gated, but release policy should explicitly approve the default.

### 11. Perform live S25 validation

Use `/android-adb` and explicit serial `100.102.2.83:5555`.

Important signing fact:

- Installed Nexus-mobile debug certificate: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`
- Production Phosphor Fortress estate certificate: `e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00`

Android signature permission will correctly reject the installed debug Nexus app against a production-signed Fortress build. Do not weaken the production permission to make the test pass.

Use two explicit live cases:

1. **Production denial case**
   - Install the production-signed Fortress artifact.
   - Confirm the debug-signed Nexus app cannot use the signature-protected Binder service.
   - Confirm there is no pre-auth package, signer, ceiling, snapshot, audit, or authority disclosure.

2. **Explicit local-dev acceptance case**
   - Build a Fortress debug/local-dev artifact with the debug signer supplied explicitly through the supported build property or environment override.
   - Confirm the requested package and signing lineage exactly match the installed Nexus app.
   - Install both debug artifacts.
   - Use pm3 to create exact persistent grants for the Android trust tuple.
   - Exercise challenge, auth, observe, audit, heartbeat-currentness, HUD action, replay, stale revision, revoke, explicit disconnect, token replacement, process restart, and Binder death.
   - Confirm the visible and persisted store state, acknowledgement, idempotency, audit, and restart behavior.

Do not guess or request the device PIN. Use the established ADB and Shizuku workflows if the relevant skill requires them.

### 12. Perform live tailnet and relay validation

Use an explicit configured relay endpoint and the real relay P-frame lane.

1. Configure the relay only with the approved pinned endpoint identity, token ID, token secret, and principal.
2. Configure Fortress through pm3. Secret must enter through stdin and the write-only provider URI, never argv, environment, Bundle, logs, or receipts.
3. Create exact persistent grants for the tailnet trust tuple.
4. Start the runtime and confirm outbound-only connection behavior.
5. Verify mutual proofs and exact pinned identity.
6. Exercise state, audit, HUD action, accepted reply, exact replay reply, typed refusal, stale generation, oversize frame rejection, and unsupported operation.
7. Kill or isolate the relay network path.
8. Prove third missed acknowledgement produces durable CLOSING and clears transient grants.
9. Prove fourth missed acknowledgement produces durable ABSENT.
10. Send a late acknowledgement after CLOSING and prove it cannot revive authority.
11. Restore the network and prove generation advances and the new session must authenticate and revalidate grants.
12. Restart the app and prove no active session or transient grant survives while persistent grants, revocations, nonces, and idempotency do survive.

### 13. Run live pm3 validation on the S25

For every device command use explicit serial selection.

Exercise and receipt:

- `schema`
- `doctor`
- state get
- bounded state watch NDJSON
- Nexus status
- exact Android grant
- exact tailnet grant
- grant refusal
- revoke
- action run accepted
- action replay
- stale revision refusal
- audit list
- transactional audit export and checksum verification
- tailnet status before config
- tailnet configure
- secret ingestion from stdin
- start
- live status
- stop
- clear
- Play-distribution refusal
- ADB failure exits

Require one-shot envelopes with `status`, `tool`, `version`, and `ts`; stream events with canonical `event`; errors with a usable `fix`; and exit classes 0, 2, 3, and 4 exactly as declared by the CLI standard.

### 14. Obtain independent blocker-first reviews

Use at least three disjoint read-only reviews after the final code and test run:

1. Shared-store and authority reviewer:
   - monitor ordering
   - save-before-publish
   - currentness fence
   - nonce/idempotency/revocation persistence
   - restart normalization
   - save-failure schedules

2. Binder and Play-boundary reviewer:
   - permission and signer checks
   - protocol and challenge binding
   - death/cancellation/replay behavior
   - typed serialization
   - Play source and APK absence

3. Tailnet, relay, and pm3 reviewer:
   - outbound-only transport
   - mutual authentication
   - frame bounds
   - generation and heartbeat semantics
   - runtime status truthfulness
   - secret handling
   - CLI schema and exits

Do not checkpoint with an unresolved blocker or an unaddressed low-confidence review.

### 15. Update final records and traceability

Update the Phase 05b-2 decision and receipt documents only after the implementation and live evidence are final.

The documentation must distinguish:

- what is host-tested
- what is artifact-inspected
- what is proven live on the S25
- production denial versus explicit local-dev acceptance
- Binder versus tailnet liveness semantics
- persistent versus transient authority
- known compatibility surfaces retained or removed
- exact rollback base
- remaining work in Phases 06 through 12

Do not claim desktop parity, renderer activation, production Nexus enrollment, or later-stage capabilities that were not actually demonstrated.

### 16. Create and verify the Phase 05 checkpoint

Only after every gate above is green:

1. Confirm the protected note is still untracked and untouched.
2. Confirm the unrelated Nexus receipt is untouched.
3. Confirm no Python was introduced.
4. Stage only intended Phase 05 files.
5. Review the staged diff and staged file list.
6. Create an SSH-signed commit with a receipts-bearing message.
7. Create a signed Phase 05 completion tag.
8. Verify commit and tag signatures locally.
9. Push the release branch and tag only if allowed by the current release workflow. Never push main or master without explicit confirmation.
10. Verify remote branch and peeled tag refs equal the local commit.
11. Archive exact evidence hashes.

### 17. Rehearse exact rollback

In an isolated worktree or other safe rollback environment:

1. Start from the new Phase 05 completion commit.
2. Revert the Phase 05b-2 commit or commits.
3. Confirm the resulting tracked tree exactly equals `62d7d5921080510bf530b3afbff0a8f89968527e`.
4. Confirm the protected untracked note was never involved.
5. Preserve the rollback receipt.

### 18. Continue the release spine only after Phase 05 is sealed

After the Phase 05 completion commit, signed tag, remote verification, and rollback proof are complete, resume the ratified execution plan at Phase 06 and continue through Phase 12 in order. Re-read the authoritative tracked planning and governance sources at that time. Do not infer or begin later activation work from this handoff alone.

## Known unfinished, stubbed, or least-confident areas

This section is intentionally candid. These are the first places a new agent should pressure-test.

### Highest confidence risk: tailnet lifecycle save failures

The successful CLOSING-to-ABSENT path now has focused coverage, but save-failure schedules around CLOSING, ABSENT, connection-finally disconnect, and reconnect are not yet comprehensively tested. The manager, dispatcher, and adapter use exact object identity in several places. Any mismatch can strand durable authority or clear local state too early.

### Tailnet response payloads are less complete than Binder

The production tailnet adapter currently returns a typed but partial snapshot and partial audit records. Accepted and replayed dispatch currently expose little more than status, replay flag, and revision. This does not yet match the rich Binder acknowledgement and refusal contract.

### No end-to-end production-adapter socket test

The socket runtime tests use a recording authority. The shared manager tests use a real store and dispatcher. A single deterministic test spanning actual wire frames through the production adapter into the shared store does not yet exist.

### Tailnet bootstrap has no truthful runtime status API

pm3 currently knows stored configuration, not authoritative runtime connection state. Start and stop verbs append success fields too optimistically. Provider bootstrap uses a broad `runCatching`, so startup failure can be silent.

### Compatibility helpers may be dangerous alternate paths

`PhosphorTailnetRuntime.authDigest` and `parseSeededRemoteHosts` carry legacy or default identity behavior. Review whether any production caller can reach them. Remove or confine them to tests if they are no longer part of the ratified protocol.

### Manager replacement rollback deserves adversarial review

`attachSharedLocked` snapshots manager fields, attempts to close an existing session, and restores prior manager fields if replacement attach throws. If the prior close already persisted successfully, restoring the old manager fields could point at a dispatcher whose internal session is already closed. Existing tests cover failure before replacement in some schedules, not every close-succeeds/new-attach-fails schedule.

### SharedPreferences rollback is not fully failure-proof

The pm3 helper restores the prior in-memory preference image after `commit(false)`, which corrects the known Android in-memory exposure issue. However, it does not currently treat rollback `commit(false)` as a second fatal state with an explicit unusable configuration marker.

### Secret lifecycle is improved but not perfect

Secret input bytes and ciphertext bytes are zeroed, and the CLI avoids argv, environment, Bundle, and log disclosure. Decryption currently materializes an immutable Kotlin `String`. Writer-thread persistence errors also need a robust way to become a nonzero CLI result.

### pm3 audit and acknowledgement schemas are minimal

`audit-export` accepts `from_revision` but currently appears not to filter by it. Audit records and action acknowledgements omit causal fields that Binder already exposes. The CLI contract and provider implementation should be brought into exact agreement.

### Binder is host-tested, not live-IPC proven

Host tests cover protocol, replay, cancellation, typed replies, and artifact boundaries, but real Android `PackageManager` signing lineage, signature permission behavior, death-recipient IPC, and setup cancellation have not been exercised on the S25 for this candidate.

### Production signer mismatch is expected and must remain fail-closed

The installed Nexus debug app and production Fortress app use different signing certificates. Production Binder success is impossible without a correctly signed Nexus build. Use an explicit local-dev signer override for acceptance testing rather than weakening release security.

### Relay lane lacks full Android-to-relay live proof

Relay Rust tests and e2e socket tests pass. The actual Android Fortress runtime has not yet authenticated to the real relay and completed state, audit, mutation, heartbeat-loss, reconnect, and restart scenarios.

### Full Phosphor matrix has not been rerun after the final patch

The earlier broad run failed seven authority tests. Focused corrections now pass, but the final broad Play/Fortress test and assembly matrix is still mandatory.

### Post-change artifact separation is unproven

Play compiled successfully during the earlier broad attempt, but Play and Fortress APKs have not been rescanned after the final lifecycle patch and all concurrent integration changes.

### No final lint, release artifact, review, checkpoint, push, or rollback proof

Nothing in the current Phase 05b-2 working tree is committed. No signed completion tag exists. No final independent review set or exact rollback receipt exists.

### Working-tree provenance is complex

Several agents edited overlapping files during the integration. The current code compiles in the focused suite, but a new agent should review the final diff by subsystem rather than trusting ownership reports. In particular, inspect shared files changed by authority, Binder, tailnet, and pm3 work together:

- `NexusObservationDispatcher.kt`
- `NexusRuntimeManager.kt`
- `NexusSessionContracts.kt`
- `PhosphorStateStore.kt`
- `PhosphorStorePersistence.kt`
- `Pm3AdminProvider.kt`
- `PhosphorApplication.kt`
- `app/build.gradle.kts`

### Later release phases remain untouched

Phases 06 through 12 remain after Phase 05. Their exact work should be taken from the authoritative tracked execution spine and governance material after this phase is sealed. This file intentionally does not invent their implementation details.
