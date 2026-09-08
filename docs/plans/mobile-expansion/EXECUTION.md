# Mobile expansion execution ledger

Canonical plan: [MOBILE-EXPANSION-PLAN.md](../../../MOBILE-EXPANSION-PLAN.md).
Active contract: [EXPANSION.md](../../../spec/EXPANSION.md).
Authority: [2026-09-08 execution decision](../../../decisions/2026-09-08-mobile-expansion-execution.md).

## Current checkpoint

- Execution approved 2026-09-08 at 00:18:19 UTC.
- Coordinator: current Jcode root session. No workers have started.
- Required worker routing confirmation remains pending. One beacon at 00:19:40 UTC reported `visible:true` and expired. Expiry is not approval. Do not repeat it.
- Proposed mapping: `openai-oauth:gpt-6-astra`, explicit `high`, for audio, presentation, experience, and independent critique.
- At most two live workers, one implementation writer. Coordinator owns shared files, real device actions, verification, commits, and cleanup.
- Section 1 contracts and exact permission-scanner implementation are checked. [Boundary receipt](section-01-boundary.md) records 91 parser cases and the public CLI/retained release gates. [Independent critique](critiques/section-01-route-blocker.md) is still blocked, with no score or round used. No expanded app feature is accepted yet.

## Baseline and recovery

- Mobile baseline: `c8a2370b958e26dc429f258d9ce9284c52ba9f40`.
- Shared-engine baseline: `7729990bb29f0167ef906d0fbdb44e1e91206955`.
- Both trees were clean at execution start.
- S25 USB `R3CY90HEZ3M` is primary. Wireless `100.102.2.83:5555` was checked against the same hardware serial. Do not operate them concurrently.
- Package: `dev.phosphor.mobil3.debug`, version `2.0.0-debug`, code `2000000`.
- Installed APK SHA-256: `f5afd99617e7a1abf21246c612590212adeed3a82b588c6235e8448a68b48834`.
- Installed signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- Private recovery directory: ignored `dev/scratch/mobile-expansion-20260908T001819Z/`, mode 0700.
- `baseline-preferences.tar`: `1f3fcf264ead0a0b3823a2c31f8c512842c9e25501c082ce659ed2b2fa3d1a72`.
- `baseline-appstate.tar`: `f70efe963071886acb46cf9243b27bf79632bfb658d8d9edb537622dfbd6e135`, includes shared preferences and app files.
- Recovery also preserves installed APK, signer/package details, device settings, and protected-file hashes. No app replacement or settings mutation has occurred.
- Storage baseline: 17 GiB free on the project filesystem. Bound retained artifacts and monitor before large builds.

Before rollback, recheck package/signer and compare the exact backup hashes. A same-package reinstall preserves data. Restore the matching private state only when the coordinator has established ownership and stopped the app. Do not clear data or uninstall. Revert only owned source changes. Do not blindly roll back another session's commits or user tuning.

## Observed capability evidence

The KernelSU home screen reports `Working [Jailbreak mode]`, `LKM`, driver `32525-2`. Manager `v3.3.0 (32601-2)` reports a version mismatch. The screen also reports Permissive SELinux and disabled seccomp. No security setting was changed. The shell lacks visible su and cannot inspect /data/adb. These facts establish a running reported driver, not app/helper authorization or usable capture.

Display metadata advertises HDR10, HLG, and HDR10+. Actual FP16/scRGB application presentation, transparent HDR, and panel luminance remain unproven. Ordinary operation with root disabled on this modified device is not an unmodified non-root OS receipt.

The current logcat executable has the previously documented override. Reuse the noninvasive ignored logd diagnostic if needed. Do not replace or reinterpret the system executable as an authorized root entry point.

## Section and requirement matrix

Statuses are `pending`, `in progress`, `implemented`, `accepted`, or `blocked`. A planned owner is not an active worker.

| Section | Requirement | Planned owner | Status | Required evidence | Critique |
|---|---|---|---|---|---|
| 1 | Baseline/contracts | Coordinator | implemented | Verified backups, active specs, 91 manifest cases, public CLI and retained gate checks in section-01-boundary.md | Blocked on routing, 0 rounds |
| 2 | R01 root | Audio | pending | App-authorized PCM, audible output, opt-out test, helper/route recovery, no system writes | Not started |
| 3 | R09 mic/mix | Audio | pending | Actual accessory route, two signals, rate/drift/buffer/clipping and partial-loss checks | Not started |
| 4 | R02 HUD | Presentation | pending | Real transparency, touch isolation, owner transfer, teardown and no duplicate source | Not started |
| 5 | R13 pause | Presentation | pending | Last-frame hold, BLACK, inspect/reset, no transport lie/backlog, recreation | Not started |
| 6 | R07 randomness | Presentation | pending | Seeded boundaries/precedence, guard, timer/track, hold/resume | Not started |
| 6 | R08 six colors | Presentation | pending | 0/1/6 selection, edit/delete/shuffle, legacy migration and malformed archives | Not started |
| 7 | R16 presets | Experience | pending | CRUD, atomic apply/undo, divergence, import, held/remote/source boundaries | Not started |
| 8 | R17 signal check | Experience | pending | Actual flow/silence/clip/route states, per-input health, visible-only read-only behavior | Not started |
| 9 | R10 dismissal | Experience | pending | Unchanged opening, real accidental/slow/fast/reversal/slider pointer cases | Not started |
| 9 | R11 expansion | Experience | pending | Live summaries, multi-open, scroll/focus restoration and no mutation | Not started |
| 10 | R04 motion/layout | Experience | pending | Visible-only clocks, reduced motion, layouts, real frame pacing | Not started |
| 10 | R12 themes | Experience | pending | Four meaning contracts, contrast, 13-room migration, appearance CRUD | Not started |
| 11 | R03 manual | Experience | pending | Indexed feature/recovery coverage, smile/tail, 24 entries, large fonts | Not started |
| 12 | R05 HDR | Presentation | pending | Real FP16/compositor/panel evidence, SDR and transparency/hold regression | Not started |
| 13 | R06 brightness | Presentation | pending | Full-app-only window override, pause/no-source, restoration, global settings unchanged | Not started |
| 14 | R14 startup | Audio + coordinator | pending | Launch decision table, consent sequence, stale callbacks, root direct start, inert imports | Not started |
| All | R15 critique | Independent critic | blocked | Astra route/effort, pinned section, score/round/evidence, four-round cap | Routing confirmation pending |
| 15 | Integration/install | Coordinator | pending | Full gates, five-cycle owners, 30-minute soak, exact installed bytes/settings, cleanup | Not started |

## Verified baseline checks

Unmodified baseline commands ran before source edits. Raw logs stay in the private recovery directory.

| Command | Observed result |
|---|---|
| `source scripts/env.sh` then `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:checkEngine` | Exit 0, 440 Android unit tests, BUILD SUCCESSFUL, 55 tasks |
| `cargo test --manifest-path rust/Cargo.toml --locked` | Exit 0, 73 tests passed |
| `cargo test --manifest-path relay/Cargo.toml --locked` | Exit 0, 43 tests passed |
| Installed APK readback and `apksigner verify --print-certs` | Verified installed artifact and signer above |
| `tar -tf` on preferences/app-state recovery | Both archives readable |
| `scripts/test-pm3.sh` | Exit 0, developer CLI fixtures passed. The script temporarily swaps a generated APK, so it ran with no concurrent build/install. Exact generated APK SHA-256 matched afterward. |
| `scripts/test-play-boundary.sh` | Exit 0, existing production-boundary fixtures passed |
| `scripts/test-release-gates.sh` | Exit 0, isolated provenance/signing-rejection fixtures passed. No real release tag or production signer was created. |
| `scripts/check-play-boundary.sh source --json` | Exit 0, 11 existing source checks. Old permission bans remain to be replaced. |
| Protected-file SHA-256 manifest, local document links, `git diff --check` | All passed after contract edits |
| Fresh S25 preference archive hash after preflight | Exactly matches the preserved baseline. No settings changed. |

These checks do not accept any expansion feature or production release. New artifact/source identity must be recorded after each implementation checkpoint.

## Next actions

1. Continue section 2 with actual app/helper authorization proof, then bounded non-diverting AudioPolicy capture feasibility.
2. Keep normal root-off startup inert and avoid system/kernel/security changes.
3. Re-run the Android build/gates after the section 1 checkpoint and retain exact source/artifact identity.
4. After routing confirmation, independently critique section 1 and assign bounded root/audio work. No confirmation means no worker spawn, not a blanket stop on coordinator implementation.
5. Fold actual backend/service findings into the active spec before integrating them.

## Blocked outcome and release boundary

Record cause, evidence, alternatives tested, useful verified partial result, and smallest next step. Continue independent work while keeping the requirement open. Four sub-8 critiques do not waive correctness/privacy/device gates. Signing inputs, exact release-tag approval, store evidence, publication, and irreversible operations remain separate gates.
