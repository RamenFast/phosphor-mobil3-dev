# Phosphor Mobil3 — Improvement Plan

*Compiled 2026-08-03 at `2493ce1` from two independent high-reasoning reviews:
Fable 5 and Opus 5, both read-only, `build/` skipped, pre-existing
uncommitted files left untouched. Merged by the coordinating agent; cited
files/lines spot-checked. Nothing here is implemented yet.*

**Both models converged on one diagnosis:** the four detent defects share one
shape — *a correct component reached through wiring nobody tested*. 338 host
tests cover components exhaustively; wiring is covered by three source-grep
tests written after each incident. Items 1, 2, 12, 13 are four framings of
that gap; item 12 dissolves it. Items unique to one model marked `[F5]` /
`[O5]`.

---

## P1

### 1. Three of the four detent defects have no guard at all *(both)*
`RotationDetentReachabilityTest` (74 lines) guards defect #1 only. Commit
`2493ce1` fixed three more and added **zero** tests (`git show --stat`:
`MainActivity.kt` only). The unguarded three `[O5]` mapped precisely:
- `applyScopeRotationPreference()` no-lock branch (fixed at
  `MainActivity.kt:1496-1503`; **four** call sites — `:349`, `:434`, `:1303`,
  `:1456` — three on paths running long after the detent commits);
- `routeOrientation` reading `display.rotation` (no-lock early branch at
  `:1574-1580`; `:1582` still reads it for locked paths, one careless merge
  from regression);
- sensor callback gating on cardinal not raw degree (`:1549`).
`[F5]` adds: the existing reachability test is itself fragile — it greps exact
declarations like `private fun updateOrientationSensor()`, so a rename makes
it pass vacuously.
- **Fix:** extend the reachability test with three assertions in the same
  blunt style (the repo already defends the trade at
  `ApplicationStartupOrderTest.kt:18-21`), and make the anchors fail loudly
  when missing.
- **Verify:** each new assertion proven *failable* by reverting the
  corresponding hunk of `2493ce1` in a scratch worktree.

### 2. No gate proves a build ever ran on a phone *(both)*
All 13 `scripts/ship-check.sh` gates (`:97-250`) are host-side —
`grep 'serial\|device\|logcat'` returns nothing. "13/13 green" is compatible
with an APK never installed, the exact state that produced "what even
changed???". `dev/pm3` already has every primitive (`install`, `smoke` with
SELFTEST→`selftest.json`+PNG at `dev/pm3:590-618`, `logcat`, `fps`) and
correctly refuses implicit device selection (`dev/pm3:138-140`).
- **Fix:** gate 14 `device.smoke` — skip-when-no-`PM3_SERIAL` (the `skip`
  state at `ship-check.sh:54` exists), green only on
  `pm3 --serial "$PM3_SERIAL" install && … smoke` exit 0, serial recorded in
  the gate detail. `[F5]` alternative/extension: emulator sensor injection +
  `dumpsys` orientation check (system images for API 36 are present).
- **Verify:** `PM3_SERIAL=100.102.2.83:5555 ship-check.sh` goes green through
  the device gate; unset → reports `skip`, not `green`.

### 3. The final detent fix is still unwitnessed on a turning phone *(both)*
Commit `2493ce1` admits it; the receipts say "cannot finish verifying …
needs to be picked up" with no follow-up. Ledger row 46 claims `works` citing
the 9 arithmetic tests — the exact evidence the saga proved insufficient,
written before the discovery (`V2-FEATURE-LEDGER.md:46`; last ledger commit
`7521228` predates the fixes).
- **Fix:** run the S25 through all four cardinals; capture `PhosphorRotation`
  logcat lines (`MainActivity.kt:1637-1640`) + `dumpsys` requestedOrientation
  as the receipt. Then re-cite row 46 to the reachability test + the device
  receipt, and `[O5]` add a corrections-table row: *"9 passing unit tests
  cited as evidence a feature worked — it never ran"* (the most valuable row
  the file could carry).
- **Verify:** every citation in row 46 resolves to a file:line and a
  re-runnable command.

### 4. The gravity sensor is registered and never unregistered *(both)*
`grep -rn unregisterListener app/src` → nothing. `updateOrientationSensor()`
registers at `MainActivity.kt:1553-1555` (SENSOR_DELAY_UI); `onStop()`
(`:551-566`) tears down five other things but not the accelerometer. Newly
load-bearing: before `3cf145e` the listener existed only under a lock; now it
is always-on — battery drain while backgrounded on an app that already holds
`FLAG_KEEP_SCREEN_ON` (`:425`). `[O5]`: the vestigial `run { }` wrapper at
`:1513-1562` should go with the same edit.
- **Fix:** unregister in `onStop()` beside the other teardowns; re-register
  from `onStart()`/`onResume()` (`updateOrientationSensor()` is idempotent via
  the `== null` guard at `:1514`). Drop the `run {` wrapper.
- **Verify:** `adb shell dumpsys sensorservice | grep -i phosphor`
  before/after backgrounding — registration disappears; rotation still
  detents on re-open.

### 5. Detent state is process-local — every cold start begins undecided `[O5]`
`committedCardinal` assigned only at declaration (`:121`) and from the sensor
(`:1542`), never persisted. Cold start flat on a desk → `NONE` →
`UNSPECIFIED` (`:1631`) → pre-fix twitchy behaviour indefinitely. A plausible
shape for the next "it's still sensitive" report.
- **Fix:** persist alongside `scope_locked_orientation` (`:1046-1053`), seed
  in `restoreTuning()` before the `:1054` sensor call; keep the system
  auto-rotate check (`:1616-1625`) first.
- **Verify:** rotate landscape → force-stop → relaunch flat: logcat +
  `dumpsys` show landscape immediately.

## P2

### 6. HANDOFF.md is stale by 15 commits and states the opposite of reality *(both)*
`HANDOFF.md:3`: "at `586f5c7`, working tree clean, 13/13 green". HEAD is
`2493ce1`, 15 commits later, tree not clean, and the detent saga — the single
most important thing a next reader needs — is absent. The file's own closing
advice (`HANDOFF.md:66`) indicts it.
- **Fix:** make the state line generated, not typed — cite `ship-check.sh`
  output; add a "known-live defect class" section naming the reachability
  lesson.
- **Verify:** `git rev-parse --short HEAD` + `git status --short` match the
  file after any handoff edit.

### 7. Two handoff files, contradictory truths *(both)*
`handoffclaude.md` (699 lines, untracked, 2026-07-26) vs `HANDOFF.md`: they
disagree on the sealed rollback base (`phase-05b1` vs `phase-05b2` at
`handoffclaude.md:11-13` vs `HANDOFF.md:4`), and `handoffclaude.md:44` claims
no 05b-2 work is committed while tag `checkpoint/phosphor-2.0.0-phase-05b2`
(`4c416e6`) exists. The rollback base is the one fact you cannot afford to
have wrong. `[O5]`: handoffclaude.md is not disposable — it carries
architecture invariants (single durable authority, mutation linearization)
that exist nowhere in `docs/`.
- **Fix:** split by kind, not author — promote durable invariants into
  `docs/ARCHITECTURE.md` or `decisions/`; keep HANDOFF.md as the single
  ephemeral front door; archive the rest. Do **not** just commit
  handoffclaude.md (two tracked front doors).
- **Verify:** `ls *.md` at root shows one handoff; every invariant resolves
  to a tracked path.

### 8. Signing passwords in cleartext at mode 664 *(both — and both confirm .gitignore is already correct)*
`build-debug.log` and `local.properties` are properly ignored (`.gitignore:9`,
`:3`) and never tracked — **no ignore change needed** (the brief's question
answers itself). The real issue: `local.properties` holds
`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_PASSWORD` in cleartext, world-readable,
inside a directory agents routinely read (both reviewers had the password
enter their transcripts). `app/build.gradle.kts:43-45` already prefers env
vars — the safer path exists and is unused.
- **Fix:** `chmod 600` at minimum; better, move both passwords to the
  environment or `~/.secrets/` (where the JKS already lives per
  `HANDOFF.md:32`), leave only `sdk.dir`; `[F5]` rotate the exposed password.
- **Verify:** `stat -c '%a' local.properties` → 600;
  `./gradlew :app:verifyFortressReleaseSigning` passes from env.

### 9. Gates that exist in prose but not in ship-check `[O5]`
- **Lint:** `docs/dev/LINT-DISPOSITIONS.md:7-9` maintains a baseline no gate
  defends (no `lint {}` block, no lint gate). Add
  `lint { baseline; abortOnError = true }` + a gate.
- **Rust engine:** ledger cites "32/32 cargo tests" twice (lines 40, 66) but
  nothing runs them; `checkEngine` (`app/build.gradle.kts:289-293`) is called
  by no script, while `:219` makes `../phosphor/crates` a live input — a real
  seam to the sibling repo.
- **Fix:** gates `lint`, `engine.check`, `engine.tests` (skip-if-missing).
- **Verify:** `ship-check.sh --only=engine` goes red when a sibling crate
  breaks; deliberate lint error exits nonzero.

### 10. Stale jniLibs and root clutter *(both)*
16MB of dead `.so` files in `app/src/main/jniLibs/` (build now generates
elsewhere; could shadow) `[F5]`; stale `build-debug.log` (Jul 19);
`NEXUS-FEEDBACK.md` tracked at mode 600 (inconsistent); untracked
`Phosphor build.md` at root.
- **Fix:** delete stale artifacts, normalize permissions, fold stray root
  files into `docs/` or delete once superseded.
- **Verify:** `git status` clean; no unexpected root files.

## P3

### 11. Extract an `OrientationController` from the 1752-line MainActivity *(both — the durable fix)*
The file mixes lifecycle, sensors, prefs, media session, capture consent,
PiP, HUD, ~40 bridge overrides. Defect #1 was invisible in a file this size,
trivial in a 150-line controller. Extract just the orientation subsystem
(`:102-122`, `:1481-1658`) behind a small interface; the source-grep guards
of item 1 then become real behavioural tests with a fake sensor.
(`Sheets.kt` 1685 and `PlaybackService.kt` 1169 are the same shape — later.)

### 12. The instrumented-test deferral condition has fired `[O5]`
`docs/SERIOUS-TODOS.md:4`: "deferred — revisit if regressions slip past."
Four slipped past in one night. No `androidTest` source set exists. Options:
Robolectric for lifecycle/wiring on host, or adopt item 2's device gate as
the deliberate substitute — pick one and write it down; the current state is
"deferred" with no replacement.

### 13. `pm3 schema` under-declares its verb list `[O5]`
`dev/pm3:214` requires 22 verbs, omitting the six `tailnet-*` verbs that
`help` (`:386`) advertises and `:622-626` implement.
`additionalProperties:false` makes this a contract error.
- **Fix:** add the six + a fixture asserting schema ≡ help ≡ case labels.

### 14. Small build items `[O5]`
- `kotlin-android` declared in `gradle/libs.versions.toml:23` but never
  applied (only `kotlin.compose`) — remove or document.
- `[F5]` minSdk 35 excludes most devices for the Play flavor; if intentional,
  it deserves a `decisions/` entry (none exists).
- `[F5]` reachability test resolves resources by walking up four directories
  from CWD — works under gradle, fragile elsewhere.

---

## Verified healthy (both reviewers)
`decisions/` (4 records) match code exactly; the Play/Fortress boundary is
defended by three gates + `NexusBinderArtifactBoundaryTest` — "genuinely well
done". Signing fails closed with named-input `fix:` errors, no debug-key
fallback. Gradle hygiene clean (`FAIL_ON_PROJECT_REPOS`, pinned wrapper 9.3.1,
version catalog). `dev/pm3` conforms to the agent-CLI standard (envelope,
fix-on-error, 0/2/3/4, explicit-serial refusal, NDJSON logcat) apart from
item 13. `.gitignore` correct as-is. The honesty culture
(`V2-FEATURE-LEDGER.md:6-8` "a row without a citation is a defect") is
stronger than most production repos — the ledger's current inaccuracy (item
3) matters *because* the culture is real.

## Suggested order
3 first (device witness — it gates everything else's honesty) → 1 (guards,
proven failable) → 4 (leak) → 2 (device gate, so 3 never recurs) → 5 → 8
(secrets; includes rotation) → 6+7 (one handoff consolidation) → 9, 10 →
11/12 (the durable wiring-test story — decide Robolectric vs device gate) →
13, 14.
