# Phosphor Mobile v2 — feature ledger

**Purpose:** one place holding the *true* state of every feature, so nobody rebuilds
something that works or ships something that only looks finished.

**The law of this file:** a state is set from a **real check**, never inferred from a
commit message, a plan, or another document. Every row names the command or file:line
that established it. A row without a citation is a defect, not a row.

**States:** `works` (verified running) · `partial` (real but incomplete) ·
`stub` (shape exists, no behavior) · `absent` (not built)

**Why this exists:** `docs/PLAN-GAP.md` diagnosed the problem precisely — *"a plan
written from commit messages inherits their optimism."* Several documents in this repo
had already drifted from the code. Three examples caught on 2026-07-28 alone are in the
"corrections" section below.

Last full verification: **2026-07-28**, via `scripts/ship-check.sh` (13/13 gates green).

---

## Corrections made 2026-07-28 (docs that were wrong)

These mattered enough to name. Each cost, or would have cost, real time.

| Claim | Where | Reality | Command that proved it |
|---|---|---|---|
| "126 lint errors, including 125 Media3 opt-in errors" is a release blocker | `docs/dev/GOOGLE-PLAY-PUBLISHING-PLAN.md` §4.2 | **0 errors**, 48 warnings, 30 hints. Already fixed by `@OptIn` annotations on `master`. | `./gradlew :app:lintPlayRelease` then read `app/build/reports/lint-results-playRelease.txt` |
| "Release signing silently falls back to the debug key" | same, §4.2 | Already fails closed. The task refuses to run without complete inputs. | `./gradlew :app:lintPlayRelease` fails at `:app:verifyPlayReleaseSigning` |
| "Resting-beam dot not yet on mobile (M5)" | `docs/SERIOUS-TODOS.md` | Implemented and wired. | `ui/ScopeUiState.kt:79`, `ui/Console.kt:93` |
| "M4 remainder: mic source" pending | `docs/SERIOUS-TODOS.md` | Implemented via `startMic()` and a permission flow. | `MainActivity.kt:797-804` |
| ">120 oversampling lever" needs wiring | `docs/SERIOUS-TODOS.md` | Shipped; the live device prefs show `oversample=2`. | `MainActivity.kt:1089`, device `phosphor.prefs.xml` |

---

## Core instrument (the v1 product)

| Feature | State | Evidence |
|---|---|---|
| Beam renderer, scope modes, GPU path | `works` | v1.0.7 released and in daily use; 32/32 Rust tests via `cargo test` in `rust/` |
| Local file/folder playback deck | `works` | `PhosphorPlayer.kt`; shipped since 1.0.0 |
| MediaProjection playback capture | `works` | `CaptureService.kt`; consent flow at `MainActivity.kt:806-812` |
| Microphone source | `works` | `MainActivity.kt:797`; corrects the stale TODO above |
| Themes / rooms / palettes | `works` | `ui/Palette.kt`, `ui/LightSheet.kt`; device prefs show `room=dark` |
| Rotation + UI placement lock | `works` | 1.0.7 release notes; device prefs `ui_locked_orientation=1` |
| Manual / bestiary / OOBE-ish surfaces | `works` | `ui/ManualSheet.kt`; device prefs `bestiary_found=true` |
| PiP | `works` | `MainActivity.kt:336,418,445` implement and update PiP params |
| Resting beam / no-signal truth | `works` | `ui/Console.kt:93` |

## Tailscale relay link (v2 focus)

| Feature | State | Evidence |
|---|---|---|
| Relay protocol v2 (audio/geometry/meta/transport) | `works` | Both relays live at 2.2.0; `phosphor-relay probe` shows `a_per_sec` 87-94, full caps |
| Relay crate | `works` | 15/15 tests, `cargo test` in `relay/` |
| Phone-side remote engine (SPSC ring, supervisor) | `works` | `rust/src/remote.rs`; 32/32 Rust tests |
| Fortress seeded hosts | `works` | Fortress dex contains 2 private host strings; `ship-check.sh --only=boundary.endpoints` |
| **Play host list** | `works` | `RemoteHostStore` + `+ ADD RELAY` UI. Play seeds empty, Fortress seeds from BuildConfig, and the Play dex still contains 0 private host strings. Proven live on device: `docs/dev/receipts/phosphor-2.0/v2/remote-sheet-with-add-relay.png` |
| User-managed host store | `works` | 13 unit tests: parse, malformed-skip, empty-Play-seed, one-shot seeding, add/edit/remove round-trip, restore-after-process-death, every refusal path, failed-write reporting, delimiter-corruption resistance, trim-on-save |
| Connect to a relay | `works` | Live: `remote-connected-interserve.png`, band reads `src · remote · interserve-linux`, relay serving 97.67 A-frames/sec |
| Link state: dialing / greeted / connected | `works` | `RemoteLinkTruth` + 9 tests. `greeted` is new and stops the app claiming a live link before any frame arrives |
| Link state: stalled | `works` | Own state, no longer masquerading as `reconnecting`. Proven on the S25: `kill -STOP` the relay gives `signal stalled · interserve-linux`, `kill -CONT` recovers. |
| Link state: backoff / error | `works` | Proven on the S25: pointing at a closed port gives `reconnecting · deadport`. Reproducible via `scripts/force-link-states.sh`. |
| Link state: silent | `works` | Relay K frame now carries `rms`/`rms_peak`; engine reports `remote_rms` (null when the relay cannot say); band reads `remote · <host> · no sound`. Proven against a live relay: silence 0.0, sine 0.565686 vs theoretical 0.565685. 12 tests across relay and app, both verified failable. **Live on `interserve-linux` since 2026-07-29** (raw K frames verified). `thinkcenter` still runs the older relay and omits the field, which the app correctly reads as "cannot tell" rather than silence. |
| Link state: authenticated | `not applicable` | Protocol v2 has no authentication. L-04 borrowed the word from the Nexus session plane. **Ask: amend L-04.** |
| Error `fix` text surfaced | `works` | Engine and relay both guarantee a fix; it was built and discarded. Now shown in the REMOTE sheet |

## Causal store / Nexus machinery (v2 additions)

| Feature | State | Evidence |
|---|---|---|
| Causal store, HUD slice, provenance/audit | `works` | 149 Play + 250 Fortress tests, 0 failures |
| v1→v2 settings migration | `works` | `CausalStatePreferencesTest` covers legacy v1 envelope, tamper refusal, commit failure, rollback restore. Same prefs file `phosphor.prefs`, so v1 settings are read in place |
| Fortress Binder / AIDL / tailnet RPC / pm3 provider | `works (inert)` | Compiles, 250 tests pass, manifest is bind-on-demand only. **Not activated in any shipped build.** |
| Nexus-mobile integration | `absent` — **deliberately** | The companion app is being rewritten from scratch. No integration work will be done against the current one. |

### Gates on the inert Nexus code

If this is ever activated, these must be satisfied first. They are recorded because
choosing not to do work is only honest when the choice is written down.

1. **Tailnet save-failure tests are NOT written.** The handoff
   (`handoffclaude.md` §"Highest confidence risk") names save-failure schedules around
   CLOSING/ABSENT/connection-close/reconnect as the highest remaining risk. They were
   deliberately skipped this session because the code cannot run in a shipped build, so
   the tests would guard nothing today. **Gate: write them before any activation.**
2. **No end-to-end socket→adapter→store test exists.** Socket tests use a recording
   authority. Same reasoning, same gate.
3. **Tailnet bootstrap has no truthful runtime status API.** `pm3 tailnet-status`
   reports stored preferences, not live state. Gate before operator use.
4. **Binder is host-tested, not live-IPC proven.** No real `PackageManager` signing
   lineage or death-recipient behavior has been exercised on device.

### Independent seal review, 2026-07-28 (Opus 5, read-only)

The review's verdict was that the Nexus machinery **is genuinely inert and safe to seal**.
It confirmed: Play dispatch behavior is unchanged (the NEXUS branch always refuses because
`authorizationFence` is null), no new thread/listener/socket on the normal path, the
Fortress `NEXUS_*` BuildConfig fields are correctly flavor-scoped, and the Binder service
is true bind-on-demand behind a signature permission with no auto-start.

It also found two blockers, neither in the Nexus code. Both are resolved:

| id | finding | state |
|---|---|---|
| **F1** | `HudCausalEnvelopeCodec.SCHEMA` bumped to `/2` while `migrateLegacyV1` was called from **nothing in production**. Any device holding a v1 envelope loaded `CorruptEnvelope`, so the store latched read-only permanently and the HUD silently fell back, unrecoverable short of clearing app data. | **fixed.** `CausalStatePreferences.load()` now upgrades in place. Proven by disabling the branch and watching `loadUpgradesALegacyV1EnvelopeInPlaceInsteadOfLatchingReadOnly` fail, then pass when restored. Ben's v1.0.7 install was never at risk: it has no envelope (verified on device) and takes the unchanged `LegacyBootstrap` path. |
| **F2** | The working tree was being edited concurrently, so "the Phase 05b-2 diff" is no longer a clean unit; it now also carries a prefs-file split, backup rules, RemoteHostStore, and the remote editor. | **accepted as scope.** These are this session's intended v2 work, not accidental drift. The commit will stage by explicit pathspec and the seal commit message will name exactly what it covers. |

Three further findings are **gates, not blockers**, recorded here rather than fixed:

5. **`ScopeUiState` leaks two store listeners per Activity recreate** now that the store
   is application-scoped and outlives the Activity. It holds `artwork: ByteArray?`, so the
   growth is real, though it needs repeated recreates to matter.
   **Gate: fix before any long-soak or ProjectM work.**
6. **`Pm3AdminProvider.onCreate` runs on every Fortress process start** and will latch the
   tailnet client permanently once an operator has configured it. It is a no-op by default
   because no secret exists. This is the one honest exception to "bind-on-demand".
   **Gate: revisit if tailnet is ever activated.**
7. **`pm3 action-run` sends `Transport.CLI` with a HUMAN principal, which
   `DisplayHudReducer` always refuses.** The passing pm3 fixture is a canned adb stub, so
   the green test is not evidence that the verb works.
   **Gate: prove `action-run` against a real device before claiming pm3 operator parity.**

## Google Play readiness

| Requirement | State | Evidence |
|---|---|---|
| targetSdk 36 / minSdk 35 / arm64 | `works` | `app/build.gradle.kts:120-127` |
| Lint clean (0 errors) | `works` | `lint-results-playRelease.txt` |
| Release signing fails closed | `works` | `verifyPlayReleaseSigning` blocks the build without inputs |
| Play/Fortress boundary enforced | `works` | `check-play-boundary.sh all` exits 0; Play dex has 0 private strings |
| Backup policy (`dataExtractionRules`) | `works` | `data_extraction_rules.xml` + `backup_rules.xml`. Causal envelope, runtime state and `remote_hosts` excluded from cloud backup and device transfer; genuine settings still restore. Lint `DataExtractionRules` gone |
| Lint dispositions | `works` | 48 → 41 warnings; every survivor has a written disposition in `docs/dev/LINT-DISPOSITIONS.md` |
| **Play upload keystore** | **`absent`** | `~/.secrets/` holds only the Fortress JKS. **Ben must mint this.** Blocks the Play AAB only, not testing. |
| **Privacy policy URL** | **`absent`** | Play-required. **Ben must publish it.** |
| **Billing / Pro unlock** | **`absent`** | No `BillingClient` anywhere. See cost estimate below. |
| **7-day trial state machine** | **`absent`** | No trial code exists. |
| First-run commercial/privacy setup | `absent` | No OOBE flow found |

### Cost estimate for the Play commerce work (not started)

Sequenced *after* the Fortress prerelease, per Ben's 2026-07-28 decision.

| Piece | Shape | Rough size |
|---|---|---|
| Entitlement state machine | Pure Kotlin, no Android deps, exhaustively unit-tested. States per publishing plan §5.1. Must handle clock rollback, offline, refund, revocation. | ~300 LOC + ~400 LOC tests |
| Billing integration | `BillingClient` wiring behind the state machine: connect, query, purchase, acknowledge, restore, pending. | ~400 LOC |
| Trial storage | Dedicated no-backup prefs; wall-clock + elapsed-realtime anchor to survive reboot without punishing travel. | ~150 LOC |
| Paywall + first-run sheets | In Phosphor's existing sheet language. No dark patterns (publishing plan §5.3). | ~500 LOC |
| Play Console setup | Human gate: account, product `phosphor_pro`, Data safety, content rating. | Ben |

**Hard prerequisites:** the upload keystore and the privacy policy URL. Both are Ben's.

---

## How to re-verify this whole file

```bash
cd /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3
scripts/ship-check.sh            # the 13 release gates
scripts/ship-check.sh --json     # same, machine-readable
```

Any row above whose evidence no longer reproduces is a defect in this file. Fix the
row, do not quietly soften the claim.
