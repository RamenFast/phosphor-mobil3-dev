# Phase 12: portable settings and accepted clean defaults

Date: 2026-09-06. Card: B17. Refs #5.

**Status: source corrections and frozen host gates passed. Device acceptance remains open.**

**B17: OPEN.** No clean-emulator app defaults or physical same-package update preservation have been accepted. No production package was uninstalled and no signed-production archive transfer is claimed.

## Source identity and scope

Mobile parent: `7a85e33fe2a4af259b9e6c48b8eba42ff5f303d0`.
Shared dependency remains `aa09b8e14f8b8912b125e3312b4ddeec09404089`, unchanged by this phase.

The final reviewed source inventory contains ten mobile production, test and spec paths. Existing MainActivity, PlaybackService, LegacySettingsMigration, SettingsArchive and ScopeUiState remain the owners. No defaults module, settings bus, migration framework, native/JNI change, dependency or production filename was added. LIGHT already supplied the legal LEG range and photosensitivity confirmation, so it was retained.

| Requirement | Actual change and check | Remaining boundary |
|---|---|---|
| Accepted absent tuning without replacing saved values | Activity and state fallbacks align AMOLED, mode 1, beam 7, manual gain 1.8332275, auto gain/fullscreen on, grid off, HUD/BAND AUTO, focus 0.3, geometry 0.6, BEAM range 6..20, LEG 3 and scope lock. KnownDefaults exercises actual state/policy constructors and labels Activity/JNI checks source-only. | Real Application/Activity startup, visible controls and native delivery require emulator and phone observations. |
| Startup HUD AUTO with legacy preservation | Application already commits the migration resolver before Activity restore. Its final no-valid-value fallback now returns AUTO. Valid portable, causal and legacy Boolean branches remain unchanged. Actual resolver/archive tests cover empty stores, repeated resolution, explicit modes and precedence. | Tests do not instantiate Application or SharedPreferences. |
| Legacy envelope preservation remains distinguishable from fallback | The existing actual resolver test checks ON/AUTO/OFF in both supported checksum schemas and each store. Twelve assertions include ON/OFF results unequal to the AUTO fallback. Malformed/tampered envelope rejection remains tested. | This proves the resolver, not Android migration execution. |
| Local tuning never becomes relay display state | Save reads the stored local auto setting or true, not live ui.autoGain. The existing healthy relay command uses absent true/manual1.8332275. Explicit local false, apply-once timing and command formatting are preserved. Source-only tests trace service/display/save/restore owners. | Real first-session relay, lifecycle save and local restoration remain unmeasured. |
| Independent five-key matrix and old archive merge | PiP true, controls false, grid data false, double tap true and linger false retain typed state, persistence, UI and provided-key archive merge. Archive tests cover all 32 combinations across four metadata pairs and missing older keys. | Actual Android picker/import rollback and in-app surface refresh remain open. |
| Legal LEG and strict portable values | Archive accepts finite 0.1..60 including endpoints and rejects next-outside floats. RGB requires exactly nine finite legal components. Ranges require exact finite ordered tokens. Tests cover valid-checksum invalid imports, all nine RGB positions and private-field exclusion. | Real import/JNI behavior is not inferred from JVM checks. |
| No guessed custom palette | Restore keeps valid inactive RGB slots, uses custom_count0 for disabled/invalid input and the existing native custom reset. It does not persist illustrative colors. Focus and cycle publish through existing setters immediately on restore. | Source-linked checks are not native surface or visual acceptance. |

## Review corrections and frozen gates

Initial attempt01 passed 381 JVM tests but independent reviews found two actual owner defects: Application migration seeded HUD OFF, and a first relay session could persist its auto-gain display as a missing local preference. Those passing tests did not close B17. Original blocked reviews and evidence remain immutable.

Root correction01 changed six paths in an explicitly expanded ten-path scope. Attempt02 passed 386 JVM tests. Independent source and intent correction reviews closed both concrete source contradictions. The intent addendum acknowledges its initial map missed the relay chain. Its source-first ordering disclosure remains: a production JNI self-test API declaration/comment and normative gate command prelude were visible before the map, but no test implementation or retained result was read before evidence release. This is not an exposure-free review claim.

The intent reviewer also identified inadequate valid-envelope discrimination because AUTO had become the fallback. Root correction02 expanded only the existing test method. The other nine reviewed paths and all text outside that method remained byte-identical. Attempt03 reran the unchanged complete gate.

Final attempt03 completed at09:09:09 UTC, exit0:

- 34 JVM suites: **386 tests, zero failures, errors or skips**. KnownDefaults 10, LegacySettingsMigration 9, SettingsArchive 33 and ScopeUiState 2.
- Selected native: **50 passed**, comprising engine 27, terminal 9, activation 4, close 1, remote media 8 and watchdog 1.
- Shared beam/CPU/GPU: **26 passed**, including offscreen readback without an adapter skip. This is not Android surface acceptance.
- Shared workspace consumer check and scoped renderer Clippy at `-D warnings` passed.
- Native format, Android packaging/checkEngine, source privacy 11 and protected archive 3 passed.
- Lint: **11 warnings, zero errors**. Differences from Phase11 advisories do not represent product warning fixes.
- All **828** gate inputs matched before and after. Root independently rehashed them after completion.

Evidence is retained under ignored `dev/scratch/pre-v2-20260829T072841Z/phase-12/`:

| Evidence | SHA256 |
|---|---|
| `combined03-inputs.sha256` | `a603f4fb8dc0555bb90dafc31f2295cd03e958e972b1d2d5fc0bff87103a5b45` |
| `root-correction02-pre-gate.md` | `bbd9c8a87f3a19523a5d2dab0e64d90b87a1519629a6989613d190b86ed36042` |
| `implementation-attempt03-before.sha256` and `-after.sha256` | `6db77585c6ad2f186dad79378206a3103ef8d2b85f2d04c513885587d2763ae3` |
| `root-attempt03-evidence.sha256`, 59 files | `804680c3ac04012d8b7d760b903cf5aff48d16b0d91110df4bdb492722976d03` |
| Original `review-source01-astra.md` | `47e771324a12a37468eb0a111fb01b3fbb7d773bbd6a543f67752126046a81c2` |
| Original `review-intent-isolated01-astra.md` | `cca94fd7fe3fe823e410d1d3465edabc7a521b0756d51c030532b53aea089223` |
| `review-source-correction01-astra-terminal.md` | `2b7ff66256a7c8a6cd9f26c2de6b52174453873493d26c78a5da7c4f11a371fa` |
| `review-intent-correction01-astra.md` | `464a1a38d357fc718631567a0fd28d9e14e0a2dc460d083912856df218fc1f69` |

Final test-only SOURCE addendum `review-source-correction02-astra-terminal.md` has SHA256 `a5d30ed950aad4db573fbb6963be56def6d9f0476bf02ab89e3290780f62d074`. Bounded intent addendum `review-intent-correction02-astra.md` has SHA256 `b3f30bc20fa2fc7e953eefcc06b6314daa11ff2fd4caaa5ecebedec1c3c50fab`. Both independently rechecked the named passing method, ten current paths, the 828-input inventories and retained evidence. Both preserve Android limits and found no new blocker in the single-method correction.

## Remaining acceptance and recovery

The separately confirmed system-rotation authority gap remains open. Locked Activity and chrome/beam routing can bypass the OS lock, and dependent settings are not yet disabled with an explanation. This phase does not claim that gap is fixed merely because scope lock has the accepted default.

An isolated emulator boot preflight passed using the existing project AVD, a read-only overlay, no window/audio/camera and explicit emulator route. Its API36 x86_64 guest advertises arm64 translation. All seven original config/image hashes survived and the owned emulator process group stopped. No Phosphor package was installed or launched in that preflight. Guest ABI advertisement is not proof of native runtime compatibility.

Next acceptance requires the exact committed debug artifact, actual empty-data Application startup and visible defaults on the emulator, actual import/restore delivery and same-package S25 preference preservation. Use fresh stable private backups, retain Phase0 comparison and never clear the physical app. The current receipt does not claim installed bytes or any phone state change. Drift remains21 and the B17 ask remains open.

Rollback uses a new revert commit for this scoped change, the retained exact Phase11 APK, and private preference restoration only if an update changed values. Do not overwrite newer user tuning or reset shared history.
