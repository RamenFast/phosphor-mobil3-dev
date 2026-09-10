# Tactile default code review, attempt 1 of 4

- Tree: `phosphor-mobil3` HEAD `ca0c63d1d8ce0795afbde933839955d1eb8c90ae` plus dirty AppearanceMigration and tests.
- Freeze file: `dev/scratch/tactile-default-20260910/source.sha256`.
- Freeze hash matched `AppearanceMigration.kt` (`c5c60d310742eca750821b115eef45a3fa1c1980302cc493a453b0fe64f6cb41`). This reviewer holds no freeze.
- Dirty wiring outside the freeze: `AppearanceMigrationTest.kt`, `ConsoleTactileTest.kt`, `AppearanceWorkflowTest.kt`.
- Reviewer: Grok code quality only. No visual-taste or device judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. Attempt 1 accepts the bounded source.
- Historical: none. This is round 1 of 4.

This is source acceptance of the fresh-install tactile seed. It is not device, screenshot, or beauty acceptance. U1 scores stay immutable.

## Diff under review

Four files, +17 / -4. Production change is six lines in `AppearanceMigration.initial`:

- New private `userSettingKeys`: `mode`, `gain`, `fps`, `oversample`, `auto_gain`, `default_source`.
- When `appearance_state` is absent and no legacy room keys exist, empty-of-those-keys seeds `CuratedAppearances.amoled.copy(lookVersion = 2)` with `activeId = "curated:amoled"`.
- Upgrade-like keys keep catalog AMOLED at look version 1.
- `stored()` still returns first. Existing appearance bytes are not re-encoded.

No Console, codec, catalog, or workflow owner changes.

## Evidence classes

Verified by reading the freeze file after hash match, plus dirty tests as wiring evidence, plus callees needed to judge the seed:

- Empty map: `initial` returns look version 2, `activeId` `curated:amoled`. `emptyInstallSeedsTactileAmoledWithoutRewritingUpgradePrefs` and `ConsoleTactileTest` lock it.
- Gain present, no `appearance_state`: look version 1 and `CuratedAppearances.amoled` equality. Same test plus `cleanInstallUsesCuratedAmoledAndRetainsAllActualLegacyRows`.
- Legacy room key: still the legacy resolver, look version 1. `ConsoleTactileTest` `room=glass` and existing override/sentinel tests.
- `AppearanceWorkflow.load` on empty prefs commits the version-2 document before publish. `cleanStartupCommitsActualMigrationBeforePublishingAndRetainsThirteenRows`.
- `AppearanceWorkflow.load` with `appearance_state` present publishes decode and does not write, including surrounding whitespace. `existingValidBytesWinWithoutWriteIncludingWhitespace`.
- Codec still omits `look_version` at 1 and writes integer 2 only. Curated/legacy catalog records stay version 1. Console still branches on `ConsoleKeybedPolicy.tactile(style.lookVersion, hasTransport)`.
- `restoreTuning` reads gain/mode/fps with in-memory defaults and does not write them before `initializeAppearance()`. `saveTuning` runs from `onStop` and export, after the first appearance commit.
- `PhosphorApplication` writes `hud_mode` on every process start. That key is not in `userSettingKeys` or `legacyKeys`, so a real first launch still takes the tactile branch.

Inherited, not rerun: parent full JVM tests just passed. No Gradle, Git, or device command ran here.

Unobserved: ASUS/S25 empty-prefs install, appearance-sheet copy, TalkBack, and screenshots.

## Requirements that hold

These are required behavior. They are not defects.

- Empty prefs seed curated AMOLED look version 2.
- Upgrade-like prefs (`gain` / `mode` / `fps` / legacy room keys) stay look version 1. The implementation also treats `oversample`, `auto_gain`, and `default_source` as upgrade-like.
- Existing `appearance_state` bytes win without rewrite.
- Catalog AMOLED, curated records, and encoded version-1 hashes stay unchanged.
- Reset still applies catalog `curated:amoled` (look version 1) and keeps named looks, matching the U1 catalog contract.

## Material findings

None that break the three stated invariants on the inspected call graph.

## Residual notes

TD-N1. The seed stores `activeId = "curated:amoled"` with `active = amoled.copy(lookVersion = 2)`. `AppearanceCollection.modified` is therefore true for the life of that default. The appearance summary reads `AMOLED · modified` until the user saves a named look, applies a catalog record, or resets. Reset and `repairAppearance()` apply catalog version 1 and drop tactile. That is honest against an immutable catalog, but the new factory default never presents as `current`.

TD-N2. Production first launch is not `emptyMap()`. Application startup writes `hud_mode` before appearance load. Tests do not lock `hud_mode` (or other non-upgrade keys) as a non-blocking key. If that key later joins `userSettingKeys`, JVM tests would still pass and a real empty install would stay version 1.

TD-N3. Named upgrade keys `mode`, `fps`, `oversample`, `auto_gain`, and `default_source` are in the detector. Only `gain` and legacy `room` are asserted. A later deletion from the set would not fail current tests.

TD-N4. `cleanInstallUsesCuratedAmoledAndRetainsAllActualLegacyRows` still uses `gain=2f`. That is now the upgrade-like path. The new empty-install test carries the tactile assertion. The old name is stale, not wrong.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| Empty prefs → look version 2, `curated:amoled` | Executed `initial` | Pass in `emptyInstallSeedsTactileAmoledWithoutRewritingUpgradePrefs` and `ConsoleTactileTest` |
| `gain` without appearance_state → look version 1 | Executed `initial` | Pass; equals catalog AMOLED |
| Legacy `room` → look version 1 | Executed `initial` | Pass |
| Empty workflow load commits version 2 | Executed `AppearanceWorkflow` | Pass; 13 legacy rows retained |
| Existing `appearance_state` bytes, including whitespace | Executed load | No write |
| Stored document wins over unrepresentable legacy | Executed `initial` | Unchanged |
| Opt-in preview/cancel/apply/save/reset/restart | Executed workflow with `gain` seed | Pass; reset still version 1 |
| `hud_mode`-only first launch | Source read / untested | Detector allows tactile; no lock |
| `mode`/`fps`/`default_source` upgrade keys | Source set / untested | Present, not asserted |
| Appearance summary `modified` on empty seed | Source read / untested | True for `copy(lookVersion = 2)` vs catalog |

## Limits

No source, build, device, or Git mutation. Freeze not held. Visual U1 contract still says fresh tactile default was deferred; this slice is the deferred seam, not a catalog change. Device proof of an actual empty SharedPreferences install remains parent-owned.

## Disposition

Accept tactile-default source at 8/10. Parent owns the full gate, Git, and device empty-install proof. Residuals TD-N1–N4 are not fails.
