# Executor contract

This is a future execution procedure. Writing or validating this plan does not execute its commands.
Read [the product boundary](context/VISION-AND-BOUNDARIES.md) and [current repair state](context/CURRENT-REPAIRS.md) first.

## Authority and roles

Ben delegated roadmap design choices. The final decision record settles the technical choices rather than asking him to design the code.
New roadmap implementation still needs his execution authorization. Existing repair work retains its earlier authorization.
Device, audio, active-relay and visible-GUI actions also need a permitted work window.
A source mismatch triggers a plan refresh, not speculative adaptation by an executor.

The root owns integration, exact-source gates, commits and activation.
An implementer authors one bounded change. A separate verifier checks its actual source and evidence.
A separate intent review checks the changed human behavior before a phase closes.
One agent must not approve its own implementation. Workers do not spawn workers.

Use at most two project workers at once. Only independent read-only reviews or disjoint authoring owners may overlap.
Production source phases remain sequential. Do not edit a shared owner from two workers at once.
For this planning session, the approved route was Astra high. Grok 4.6 did not work through the configured route.
A future interactive root follows Ben's once-per-session swarm routing ritual before spawning workers.
Do not edit saved routes, silently substitute an older model or claim an unsupported effort level works.

## Stable symbols

- ▸ names one discrete task.
- 📁 names its exact existing or proposed file owners.
- ✅ gives its verification command and expected observation.
- ↩ gives the phase rollback.
- ⛔ identifies a protected boundary.
- ⚠ identifies a checked baseline trap or evidence limit.
- ❓ identifies an external decision or permission, with its owner.

A proposed path is not an existing implementation. Every phase marks new files explicitly.
A command naming a proposed test becomes runnable only after that phase authors the test and its configuration.
A successful empty test filter is not a PASS. Inspect test counts and require the named cases.

## Shell bootstrap for authorized execution

Use Bash. Keep one timestamp for the entire run and store it in the receipt.
Do not run device commands from this bootstrap.

```bash
set -euo pipefail
export M=/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3
export E=/home/ben/Dev/ClaudeWorkspace/phosphor
cd "$M"
source scripts/env.sh
export TS="$(date -u +%Y%m%dT%H%M%SZ)"
export RUN="$M/dev/scratch/mobile-next-$TS"
umask 077
mkdir -p "$RUN"
export PM3_DEVICE_WINDOW_OK=no
export B2_APK="$M/dev/scratch/pre-v2-20260829T072841Z/phase-04/candidate-47ff7cdc71c3/phase-04-47ff7cdc71c3.apk"
printf 'started=%s\nmobile=%s\nengine=%s\n' "$TS" "$(git rev-parse HEAD)" "$(git -C "$E" rev-parse HEAD)" > "$RUN/start.txt"
git status --short
git -C "$E" status --short
```

Do not discard another worker's uncommitted changes to make this check clean.
Attribute changes first. Start a source phase only after its expected baseline and owners are stable.
Keep source receipts relative to the repository. Do not commit private runtime archives or generated binaries.

## Per-phase loop

1. Check entry conditions and the prior phase's real acceptance receipt.
2. Read the named source files, active spec and accumulated feedback.
3. Record both repository commits, branch names and complete hashes of the owners the phase can change.
4. Preserve the prior accepted APK and its hash. Record the phase's start commit.
5. Write or update the human-readable module contract before changing implementation.
6. Add failing requirement-linked tests against the current production boundary.
7. Author inert code or adapters first. Run their gates before replacing the active call site.
8. Switch one bounded ownership seam. Do not leave two runtime authorities for rollback convenience.
9. Freeze source. Run independent source and intent review on complete-file identities.
10. Root reruns gates against those identities, checks privacy and protected surfaces, then commits only owned changes.
11. Build the exact committed candidate from a clean tree and recheck source identity after the build.
12. During a permitted device window, apply DEVICE.md's offline package/version/signer guard, then install through pm3 and capture hash/signer readback.
13. Run the named real scenario checks. Restore settings and remove only task-owned fixtures.
14. Fold measured behavior and failures into spec, feedback, issue and receipt records.

One failed gate earns diagnosis and a corrected retry, not repeated blind execution.
If the corrected gate fails again, preserve the failing evidence and the last verified state.
A valid blocked report names cause, evidence, useful partial result and the smallest resolving action.
Do not interrupt Ben's quiet period merely because a device gate is waiting.

## Quiet versus active execution

Low-priority local compilation can use:

```bash
nice -n 10 ionice -c 3 ./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest :app:lintDebug :app:checkEngine
```

Do not mistake low-priority scheduling for permission to run GPU stress, audible fixtures or device tests during media use.
Root sets `PM3_DEVICE_WINDOW_OK=yes` only after an actual permitted window and an explicit selected-device identity.
The variable records permission; it does not create permission.

Guard every device command with both conditions:

```bash
test "${PM3_DEVICE_WINDOW_OK:-no}" = yes
: "${PM3_SERIAL:?Pin the explicitly verified device serial before device work}"
dev/pm3 --serial "$PM3_SERIAL" --profile debug smoke
```

The default `no` deliberately prevents accidental activation when someone pastes a plan command during quiet work.
Never use bare adb device selection, force app permissions as a substitute for consent, or silently install into production.

## Commits and external activation

Keep source work on the permitted branch arrangement, with at most one branch besides master/main.
Do not create a second work branch or a worktree for convenience.
Each phase receipt records its exact commit list and rollback artifact, not a movable branch name alone.
A later documentation commit does not change the identity of an already retained APK.

Publication is separate from technical acceptance. Pushes, tags, merges, signer use and store submission require their own approval.
Do not close issues as though a local commit has reached the default branch.
A frozen release candidate can remain pinned to its commit and artifact while a later plan is considered.
If the branch lifecycle conflicts with an approved release freeze, resolve that external gate before adding source commits.
