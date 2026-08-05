# Phosphor and Nexidex implementation handoff

**Audience:** zero-context executor/orchestrator
**Baseline:** `383472b`
**Source authority:** `vision/` and `spec/`
**This handoff's status:** planning only, no app implementation included

## Legend

- ▸ task
- 📁 exact file or directory
- ✅ verification
- ↩ rollback
- ⛔ constraint
- ⚠ gotcha
- ❓ human decision

## Executor start contract

Read in order:

1. `AGENTS.md` and `/home/ben/Dev/ClaudeWorkspace/AGENTS.md`
2. `vision/PHOSPHOR-LIVING-INSTRUMENT.md`
3. every file in `spec/`
4. `decisions/2026-07-22-product-alignment.md`
5. existing `docs/ARCHITECTURE.md`, `docs/UX-SPEC.md`, `docs/BRIDGE.md`, and `docs/AGENTS.md`
6. Nexidex `AGENTS.md`, `docs/plans/UI-HONESTY-SPINE.md`, and IPC/settings/HUD implementations

Before changing code:

```bash
git status --short
git rev-parse --short HEAD
./gradlew :app:assembleRelease
(cd rust && cargo test --release)
(cd relay && cargo test)
```

Record the current commands that actually exist. The baseline has no `:app:testReleaseUnitTest`, so do not treat that missing task as a regression.

⛔ Do not implement the whole plan in one commit.
⛔ Do not edit dirty sibling repositories without isolating and confirming ownership.
⛔ Do not introduce Python.
⛔ Do not use a localhost network socket for Nexidex same-phone control.
⛔ Do not place Fortress implementation behind only a runtime flag in the Play graph.
⛔ Do not claim shell capture or ProjectM integration before their spikes pass.

Each phase ends in a focused commit and named receipts. If a phase gate fails, fix it or revert that phase before advancing.

---

# Phase 0: lock schemas and feedback loops

## ▸ 0.1 Add machine-readable state and action schemas before implementations

📁 Create:

- `app/src/main/kotlin/dev/phosphor/mobil3/state/PhosphorState.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/state/PhosphorAction.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/state/Provenance.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/state/Capability.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/state/ActionResult.kt`
- `docs/dev/state-schema.json`
- `docs/dev/action-schema.json`

Start as inert data types and serializers. Do not wire UI yet.

Required roots:

```text
scope, source, transport, display, rotation, light, theme,
permissions, nexus, oobe, commerce, diagnostics, runtime
```

Every mutable field has desired/effective/availability where authority can diverge. Provenance is a first-class value, not metadata reconstructed by the UI.

✅ Add JVM/Kotlin tests for serialization, revision ordering, idempotency, and provenance identity. Run the exact generated unit-test task discovered with:

```bash
./gradlew :app:tasks --all | grep -E 'test.*UnitTest|lint|assemble'
```

↩ Revert the Phase 0 commit. No production behavior should have changed.

## ▸ 0.2 Add a requirement-to-test ledger

📁 Create `docs/dev/REQUIREMENT-TRACEABILITY.md` mapping every `spec/ACCEPTANCE.md` ID to owner files, test type, and receipt path.

✅ Script-free check:

```bash
comm -23 \
  <(grep -oE '\*\*[A-O]-[0-9]{2}' spec/ACCEPTANCE.md | tr -d '*' | sort -u) \
  <(grep -oE '[A-O]-[0-9]{2}' docs/dev/REQUIREMENT-TRACEABILITY.md | sort -u)
```

Expected output is empty.

↩ Revert only the traceability commit.

---

# Phase 1: compile-time Play/Fortress seam

## ▸ 1.1 Create distribution flavors

📁 Modify:

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`

📁 Create:

- `app/src/play/AndroidManifest.xml`
- `app/src/fortress/AndroidManifest.xml`
- `app/src/play/kotlin/dev/phosphor/mobil3/distribution/DistributionCapabilities.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/distribution/DistributionCapabilities.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/distribution/Distribution.kt`

Use flavor dimension `distribution`. Keep Play package `dev.phosphor.mobil3`. Use `dev.phosphor.mobil3.fortress` for Fortress unless Ben changes it before implementation.

Move distribution-specific manifest entries out of `main` as features land. The Play manifest begins smaller, not larger.

✅ Verify:

```bash
./gradlew :app:assemblePlayRelease :app:assembleFortressRelease
apkanalyzer manifest application-id app/build/outputs/apk/play/release/*.apk
apkanalyzer manifest application-id app/build/outputs/apk/fortress/release/*.apk
```

Install both on an emulator or S25 only when Ben's device-use rules are followed.

## ▸ 1.2 Add artifact exclusion checks

📁 Create `scripts/check-play-boundary.sh` using POSIX shell, `unzip`, `grep`, and Android tools. It checks Play outputs for forbidden classes, dependencies, metadata, components, and strings such as Shizuku package names and sidecar verbs.

📁 Wire a Gradle verification task, for example `checkPlayBoundary`, in `app/build.gradle.kts` or root build logic.

✅ Verify:

```bash
./gradlew :app:bundlePlayRelease checkPlayBoundary
```

Introduce a temporary forbidden marker in a test fixture and prove the checker fails, then remove it.

↩ Revert Phase 1 commits. This restores the original single artifact and must occur before any Fortress-only feature exists.

⚠ Current release signing configuration may assume one release identity. Do not put signing passwords or private keys in Gradle files or git.

---

# Phase 2: one causal store and migration

## ▸ 2.1 Implement the reducer/store

📁 Create:

- `app/src/main/kotlin/dev/phosphor/mobil3/state/PhosphorStore.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/state/PhosphorReducer.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/state/StatePersistence.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/state/StateMigration.kt`
- `app/src/test/kotlin/dev/phosphor/mobil3/state/`

Use a single-threaded mutation boundary. Publish immutable snapshots. Actions return `changed`, revision, effective value, and the exact accepted provenance object.

⛔ Do not use mutable Compose state as the source of truth.
⛔ Do not put high-rate PCM or per-frame geometry in this store. Store status and configuration only.

## ▸ 2.2 Migrate existing UI state in slices

📁 Refactor:

- `app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt`
- renderer command dispatch around `PhosphorNative.kt`

Migration order:

1. HUD and display preferences
2. rotation desired/effective state
3. room/theme and light preferences
4. scope mode/gain/lock
5. source and remote state
6. permissions, OOBE, diagnostics, commerce

For each slice, remove the old independent writer after every reader uses the store.

✅ For each slice, test human action, restart persistence, engine event, and no-op. Finish Phase 2 with:

```bash
./gradlew :app:assemblePlayRelease :app:assembleFortressRelease :app:lintPlayRelease :app:lintFortressRelease
(cd rust && cargo test --release)
(cd relay && cargo test)
```

↩ Revert the latest slice commit, not the entire store, if one migration destabilizes the app.

⚠ `MainActivity.kt` currently owns many direct mutations and lifecycle decisions. Do not create a second shadow controller while migrating.

---

# Phase 3: Nexidex protocol and same-phone Binder

## ▸ 3.1 Implement protocol dispatcher independently of transport

📁 Create:

- `app/src/main/kotlin/dev/phosphor/mobil3/nexus/ProtocolModels.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/nexus/ProtocolDispatcher.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/nexus/SessionRegistry.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/nexus/TrustIdentity.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/nexus/AuditLog.kt`
- protocol tests under `app/src/test/.../nexus/`

Dispatcher inputs are authenticated session, action, idempotency key, expected revision, and reason. It invokes the same store actions used by UI.

✅ Golden tests cover snapshots, deltas, no-op, revision conflict, missing capability, human-only permission flows, and revoke.

## ▸ 3.2 Add signature-protected bound service

📁 Create:

- `app/src/main/aidl/dev/phosphor/mobil3/nexus/IPhosphorAgent.aidl`
- `app/src/main/kotlin/dev/phosphor/mobil3/nexus/PhosphorAgentService.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/nexus/TwoWalls.kt`

📁 Modify distribution manifests so the service is exported only with a declared signature permission and code-level package/certificate/capability checks.

Use signing certificate history. Fail closed when the allowlist is empty. Keep placeholder cert values outside source control through generated/local configuration.

✅ Instrumentation tests install:

- trusted signed test peer;
- untrusted peer with same package intent;
- observe-only peer;
- revoked session.

Prove C-01 through C-05.

⚠ Nexus fact #717 says the Binder guard is the first proof, not cleanup after a permissive prototype.

## ▸ 3.3 Extend `pm3`

📁 Modify `dev/pm3` and its existing implementation without introducing Python.

Add:

```text
schema
state get/watch
nexus status/grant/revoke
action run
audit list/export
doctor bijection
```

Use standard envelope, NDJSON events, fix-bearing errors, and exit codes 0/2/3/4.

✅ Run the repository's Agent CLI conformance and `concourse doctor` when available. Capture schema and error receipts.

## ▸ 3.4 Add remote transport through the one gate

📁 Extend existing bridge/relay protocol docs and Rust/Kotlin modules only after dispatcher and Binder pass.

Potential files after source inspection:

- `docs/BRIDGE.md`
- `rust/src/bridge_core.rs`
- `rust/src/remote.rs`
- `relay/src/proto.rs`
- `relay/src/session.rs`
- Kotlin remote controller in `MainActivity.kt` or extracted service

Reuse the existing outbound Phosphor connection in Fortress. Add authenticated typed control/state channels without opening a listener. Preserve existing audio and geometry framing compatibility or version cleanly. The first public Play release may retain ordinary audio relay behavior but does not expose the Nexidex agent-control channel.

✅ Network inspection shows outbound connection only. Binder and remote actions produce identical store results and provenance shape.

↩ Revert the specific transport commit. Dispatcher and Binder remain usable.

❓ Cross-repo Nexus support will eventually require changes in `/home/ben/Dev/ClaudeWorkspace/nexus-mobile`. That repo was dirty during specification. Create a clean worktree or wait until ownership is clear before editing it.

---

# Phase 4: motion, rotation, and gestures

## ▸ 4.1 Make direct sheet tracking synchronous

📁 Modify:

- `app/src/main/kotlin/dev/phosphor/mobil3/ui/Motion.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/ui/Sheets.kt`

Separate `dragOffset` raw state from post-release animation state. Remove per-delta coroutine launch/snap behavior. Keep room motion personality only in settle.

✅ Compose/instrumentation drag robot samples finger and sheet positions. Pass E-01, E-02, E-09.

## ▸ 4.2 Canonical landscape side

📁 Modify `Sheets.kt` and `Insets.kt`.

Replace physical-rotation-side selection with screen-relative right placement. Animate horizontal translation, not bottom translation. Transform safe insets after any rotated chrome coordinate system.

✅ Physical S25 receipts in ROTATION_90 and ROTATION_270. Pass D-05 and E-03.

## ▸ 4.3 Android rotation authority

📁 Create `app/src/main/kotlin/dev/phosphor/mobil3/system/RotationAuthority.kt`.

📁 Modify:

- `MainActivity.kt`
- `ScopeUiState.kt` or new store selectors
- `Sheets.kt` settings rows

Observe system rotation setting through public readable state/ContentObserver where permitted. When locked, freeze current effective orientation, stop app sensor overrides, disable settings, and expose fix. Retain desired app prefs.

✅ Pass D-01 through D-04 with physical rotation video and state snapshots.

⚠ Do not request `WRITE_SETTINGS` to defeat system authority.

## ▸ 4.4 Global gesture arbiter and 1.5 second arm

📁 Refactor:

- `app/src/main/kotlin/dev/phosphor/mobil3/ui/Gestures.kt`
- call sites in `PhosphorScreen.kt`, `Sheets.kt`, and compose surface

📁 Create `app/src/main/kotlin/dev/phosphor/mobil3/ui/GestureArbiter.kt` and `ScopeArmIndicator.kt`.

Make ownership explicit. Do not mutate one-finger gain before arm. Normalize sensitivity by stage dimension/density. Preserve immediate pinch.

✅ Pass E-04 through E-08. Add misinput soak traces.

↩ Each subtask is a separate commit. Revert the failed gesture commit without undoing sheet latency fixes.

---

# Phase 5: theme engine, grid, and beam colors

## ▸ 5.1 Define theme pack schema and loader

📁 Create:

- `docs/theme-authoring.md`
- `docs/dev/theme-schema.json`
- `app/src/main/kotlin/dev/phosphor/mobil3/theme/ThemePack.kt`
- `ThemePackLoader.kt`
- `ThemeValidator.kt`
- `ThemeRepository.kt`
- test fixtures under `app/src/test/resources/themes/`

Use `.phostheme` ZIP plus `theme.json`, and `.phosroom` JSON export. Parse inert data only. Enforce traversal, size, asset, workload, visibility, protected-token, and version rules.

✅ Fuzz/property tests for malformed archives and schema values. Pass F-04 and F-09.

## ▸ 5.2 Unify built-in rooms and packs

📁 Refactor:

- `app/src/main/kotlin/dev/phosphor/mobil3/ui/Palette.kt`
- `RoomStyle.kt`
- theme selection in `Sheets.kt`
- renderer theme command/state in Kotlin and Rust

Built-ins and packs compile to one room model. Curated membership is data. Migrate non-curated built-ins to a maintained legacy pack without losing selected IDs.

✅ Pass F-01 through F-03 and built-in export/reimport round trips.

## ▸ 5.3 Add human and agent authoring surfaces

📁 Create `app/src/main/kotlin/dev/phosphor/mobil3/ui/ThemePackSheet.kt` and preview controls. Extend `pm3 theme` verbs from protocol spec.

✅ Pass F-05 through F-08 and F-10.

## ▸ 5.4 Grid/ground controls

📁 Modify:

- LIGHT/settings UI, currently `LightSheet.kt` and/or `Sheets.kt`
- `ScopeUiState.kt` migration/store schema
- `PhosphorNative.kt`
- `rust/src/jni_glue.rs`
- `rust/src/render.rs`

Use renderer state already supporting grid/ground where possible. Distinguish room defaults from user override.

✅ Pass G-01 and G-02 on S25.

## ▸ 5.5 Multi-stop/random/spectrum-glide beam

📁 Modify:

- light UI and state
- Rust renderer/beam configuration
- persistence migration
- agent schema

Replace fixed 3-color representation with bounded vector, minimum 16. Add random constraints and hue-space full-spectrum interpolation. Preserve v1 settings exactly through migration.

✅ Pass G-03 through G-06, including 1000-roll deterministic test.

↩ Revert each feature commit independently. Theme loader should remain inert until the UI/repository is ready.

---

# Phase 6: liveness, HUD respondent, UI sounds, OOBE

## ▸ 6.1 One capture/link liveness model

📁 Create `app/src/main/kotlin/dev/phosphor/mobil3/runtime/Liveness.kt` and selectors. Feed real engine/relay/capture events into it.

📁 Modify renderer no-signal/resting-beam behavior in Rust and HUD rendering in Compose.

✅ Pass H-01 through H-06. Prove stale audio cannot keep chrome breathing.

## ▸ 6.2 Nexidex presence respondent

📁 Create `app/src/main/kotlin/dev/phosphor/mobil3/ui/NexusRespondent.kt`. Integrate with existing Nerd HUD and the session registry.

Animate eye only for real observation, hand only for accepted active control. Display exact provenance. No theme can forge it.

✅ Binder and tailnet sessions produce identical respondent states. Disconnect decay is within one heartbeat.

## ▸ 6.3 UI sound engine

📁 Create:

- `app/src/main/kotlin/dev/phosphor/mobil3/sound/UiSoundEngine.kt`
- `UiSoundCategory.kt`
- `app/src/main/res/raw/` assets with source/license ledger

Trigger sounds from accepted discrete actions, not arbitrary Composable recompositions. Use sonification audio attributes and preloaded SoundPool. Ensure app output capture exclusion/analysis routing.

✅ Pass I-01 through I-04.

## ▸ 6.4 OOBE

📁 Create:

- `app/src/main/kotlin/dev/phosphor/mobil3/oobe/OobeState.kt`
- `app/src/main/kotlin/dev/phosphor/mobil3/ui/OobeOverlay.kt`
- safe stereo demo and short caption-matched narration under `app/src/main/res/raw/`

Integrate trial/diagnostics setup without lengthening the core vectorscope lesson beyond 30 seconds. The narration starts conservatively, mutes immediately, and the beam/geometry acts as Phosphor's mouth for the explained L/R example. Defer system permissions until feature choice. Add Settings relaunch.

✅ Pass I-05 through I-09 in airplane mode and reduced motion.

---

# Phase 7: PiP and Fortress overlay

## ▸ 7.1 Make PiP explicit

📁 Modify `MainActivity.kt` PiP parameter and lifecycle code. Add store fields and settings UI.

Conditionalize auto-enter on user choice and eligible source state. Add action(s) where source transport supports play/pause.

✅ Pass J-01 and J-02.

## ▸ 7.2 Build one render-target coordinator

📁 Extract current SurfaceView/render lifecycle into a coordinator capable of retargeting the authoritative renderer without starting a second capture/audio pipeline.

Potential files:

- `app/src/main/kotlin/dev/phosphor/mobil3/render/RenderTargetCoordinator.kt`
- `PhosphorScreen.kt`
- `MainActivity.kt`
- Rust surface lifecycle entry points

✅ Instrument thread/capture counts. Pass J-06 before overlay implementation.

## ▸ 7.3 Fortress transparent overlay

📁 Fortress-only:

- `app/src/fortress/kotlin/dev/phosphor/mobil3/overlay/ScopeOverlayService.kt`
- overlay window/composable files
- `app/src/fortress/AndroidManifest.xml`

Add explicit special-access flow, notification escape actions, click-through/interactivity, center vignette, resize/drag, quiet-state rate, burn-in shift, and no-signal face.

✅ Pass J-03 through J-07. Then rerun `checkPlayBoundary` and confirm no overlay permission/component in Play unless a later decision explicitly changes the policy.

↩ Revert Fortress overlay commit. PiP remains.

---

# Phase 8: Tailscale setup and relay parity

## ▸ 8.1 Add in-app setup/help

📁 Create `app/src/main/kotlin/dev/phosphor/mobil3/ui/RemoteSetupSheet.kt` and `docs/remote-setup.md`. Link GitHub/manual from a real UI button.

Show hosts, route, link state, test, and fix. Keep auto network default. Explain forced-route tailnet risk.

✅ A new tester follows only the in-app concise path plus linked manual to connect both hosts. Pass L-01 through L-03.

## ▸ 8.2 Unify human/agent relay status

📁 Refactor existing remote status object so HUD and protocol read the same instance. Ensure five or more explicit phases and real counters.

✅ Fault-inject relay loss, error frame, audio silence, and reconnect. Pass L-04 and L-05.

---

# Phase 9: audio compatibility and privileged capture P0

The public capture path is hardened first. The privileged route is a gated experiment, not a promise.

## ▸ 9.0 Reproduce and harden standard Android capture

📁 Audit and instrument:

- `app/src/main/kotlin/dev/phosphor/mobil3/CaptureService.kt`
- source/consent handling in `MainActivity.kt`
- `PhosphorNative.kt`
- `rust/src/jni_glue.rs`
- Rust capture ring and liveness path

Compare a known-working Spotify session with SoundCloud app and browser sessions. Record capture configuration, usages, format, channel mask, sample rate, buffer cadence, nonzero PCM, JNI/ring acceptance, energy threshold, MediaProjection callback, and lock/background lifecycle. Separate these outcomes:

```text
Android/source opt-out or protected path
valid capture delivering zero PCM
PCM delivered but rejected/dropped in Phosphor
PCM flowing but liveness/renderer classified it as silent
projection/session stopped
```

Fix general pipeline defects. Do not add brand-name special cases that claim a service is capturable when Android is supplying silence.

✅ Pass K-01 through K-04 and update the compatibility matrix before beginning the shell route.

↩ Revert only the public-capture hardening commit if it regresses the known-working Spotify/deck paths.

## ▸ 9.1 Ground-truth shell output capture

Use `/android-adb` skill and Ben's device-use rules. Do not touch the phone while another app has focus. Establish baseline with scrcpy audio-output capture or equivalent on the S25.

📁 Record in:

- `docs/dev/audio-capture-spike/README.md`
- `docs/dev/audio-capture-spike/matrix.csv`
- captured metadata/checksums, not copyrighted full tracks

Test apps/routes from `spec/AUDIO-CONNECTIVITY-AND-PROJECTM.md`.

✅ K-05 closes success or conclusive failure with raw evidence and exact commands.

↩ Stop all sidecars, restore route/state, remove temporary device files, and document cleanup.

## ▸ 9.2 Shizuku UserService prototype, only if 9.1 succeeds

📁 Fortress-only module/source set. Prefer native/JVM code running under UID 2000, with shared-memory/bounded IPC into the app.

No high-rate JSON. No TCP listener. Explicit start/stop/liveness. Validate actual UID and granted capabilities at runtime.

✅ K-06 and K-07. Compare PCM to heard output and measure latency/drop.

## ▸ 9.3 ADB sidecar fallback, only if necessary

Use Rust/Kotlin/C/C++ as appropriate, never Python. Place build artifacts under project build/scratch conventions, not repository root. Authenticate local IPC and clean up on stop.

✅ K-07/K-08 plus Play-boundary scan.

⛔ Do not attempt to capture calls/private communications as part of the music success criterion.

---

# Phase 10: ProjectM scope-view spike and implementation

## ▸ 10.1 License and upstream lock

📁 Create:

- `third_party/projectm/README.md`
- `third_party/projectm/LICENSES/`
- `docs/dev/projectm-license-ledger.md`
- `docs/dev/projectm-preset-ledger.md`

Pin upstream version/commit. Record LGPL obligations and preset licenses before shipping assets.

✅ License inventory has no unknown entry for any Play-bound preset.

## ▸ 10.2 Actual libprojectM Android spike

Build a minimal arm64 shared-library integration in an isolated feature/source directory. Feed known stereo PCM and render to a GLES SurfaceTexture/TextureView beneath/with the wgpu beam.

Likely new areas:

- `app/src/main/cpp/projectm/` or a dedicated native module
- `app/src/main/kotlin/dev/phosphor/mobil3/projectm/`
- Gradle/CMake integration isolated from the existing Rust build

Do not rewrite the core renderer in this spike.

✅ M-01, basic frame output, lifecycle, context loss, and no crash. If impossible, write a decision record comparing a native wgpu alternative with measurements.

## ▸ 10.3 One source/clock fan-out

📁 Extend the audio sample bus so true beam and ProjectM receive timestamped data from the same drained source. Exclude UI sound.

✅ M-03 and M-07 with synthetic tones and timestamps.

## ▸ 10.4 Productize the scope view

📁 Add:

- scope-view state/action schema;
- ProjectM controls sheet;
- renderer layer coordinator;
- quality/thermal controller;
- preset pack repository;
- agent snapshot/delta fields.

Enforce true-beam opacity floor and degradation ladder.

✅ M-02 through M-09 plus O-05 and O-06.

↩ Disable/remove the ProjectM field feature commit while retaining generic scope-view and fan-out infrastructure if the library is unstable.

---

# Phase 11: Play commerce, diagnostics, and publication reconciliation

## ▸ 11.1 Implement trial and Billing only in Play

Follow `docs/dev/GOOGLE-PLAY-PUBLISHING-PLAN.md`. Place Billing dependency and code in Play source/dependency graph only where practical.

📁 Expected new areas:

- `app/src/play/kotlin/dev/phosphor/mobil3/commerce/`
- shared entitlement interface in main
- Fortress entitlement implementation in fortress

Test purchase acknowledgement, restore, pending, cancellation, refund/revocation, offline grace, and trial clock protections. Preserve user state after expiry.

✅ N-01 through N-03 with Play license testers.

## ▸ 11.2 Minimal diagnostics

📁 Add local structured crash/support report, first-run choice, Settings toggle, redaction, preview/export, and optional minimal uploader only if approved.

Android vitals is aggregated support evidence, not guaranteed per-user reporting.

✅ N-04/N-05 plus network capture proving off means no diagnostics upload.

## ▸ 11.3 Final Play boundary and declarations

Reconcile manifest, Data Safety, privacy policy, store copy, special-access status, signing cert, asset licenses, and target requirements against the exact AAB.

✅ N-06/N-07 and every publication checklist gate.

❓ Ben submits legal, financial, price, declarations, and production rollout. AI prepares but does not autonomously accept agreements or publish.

---

# Phase 12: final integrated verification

## ▸ 12.1 Automated suite

```bash
./gradlew :app:assemblePlayRelease :app:assembleFortressRelease
./gradlew :app:lintPlayRelease :app:lintFortressRelease
./gradlew checkPlayBoundary
(cd rust && cargo test --release)
(cd relay && cargo test)
git diff --check
```

Run discovered unit/instrumentation/macrobenchmark tasks. Do not invent nonexistent task names.

## ▸ 12.2 S25 state-space matrix

Exercise:

```text
4 physical orientations
Android rotation lock on/off
scope lock on/off
UI lock on/off
classic/ProjectM
stage/sheet/PiP/overlay
Play/Fortress
local/capture/mic/relay/shell source where available
speaker/BT/USB
live/silent/stalled/error
reduced motion/sound on/off
Nexus detached/observe/drive/revoked
```

Use pairwise reduction for routine runs and full targeted matrices for rotation, capture, signing, and surface lifecycle.

## ▸ 12.3 Receipts and honest ledger

📁 Update:

- `spec/ACCEPTANCE.md` only if a ratified requirement changed;
- `docs/dev/REQUIREMENT-TRACEABILITY.md` with receipt links;
- `docs/BUGLOG.md` and `docs/ASKS.md`;
- `HANDOFF.md` with exact remaining gates;
- release/publication docs.

No acceptance ID is marked complete without a receipt. Failed spikes are recorded as failures with fallbacks, not disappeared.

## ▸ 12.4 Commit and rollback law

Each phase commit message states:

```text
intent
source requirements
files/symbols
validation
known honest limits
receipt paths
```

Never use destructive reset in a shared/dirty workspace. Roll back with a focused fix or `git revert <phase-commit>` after checking other agents' work.

---

# Open decisions and stop-and-ask rules

The executor may continue with placeholders for signing certs, store IDs, and final room IDs. Stop and ask Ben only when one of these becomes immediately blocking:

1. choosing the existing `dev.phosphor.mobil3` signing/update lineage before Play enrollment or data-bearing migration;
2. creating or rotating the real Fortress private signing key;
3. entering Play Console legal/financial agreements;
4. selecting unclear-license ProjectM presets for public distribution;
5. deciding to include overlay in the first Play submission after current policy review;
6. any experiment that may capture private communications or materially disrupt device audio;
7. destructive changes to Ben's phone or sibling dirty repositories.

Do not stop for ordinary naming, file placement, test design, or implementation details already decided by the specs.

# Completion definition

The product line is complete only when:

- Play and Fortress artifacts are genuinely separated and signed;
- every applicable acceptance ID has a receipt;
- Nexidex and human surfaces demonstrably share one causal store;
- all Nexus changes have visible provenance;
- the S25 audio spike has an honest result;
- ProjectM is a real scope view with beam priority and licensing receipts;
- UI direct manipulation is measurably eager;
- Android authority and permission limits are represented truthfully;
- the Play commercial/privacy promises remain intact;
- no Python has entered the project.
