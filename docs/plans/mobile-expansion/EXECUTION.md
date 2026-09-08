# Mobile expansion execution ledger

Canonical plan: [MOBILE-EXPANSION-PLAN.md](../../../MOBILE-EXPANSION-PLAN.md).
Active contract: [EXPANSION.md](../../../spec/EXPANSION.md).
Authority: [2026-09-08 execution decision](../../../decisions/2026-09-08-mobile-expansion-execution.md).

## Current checkpoint

- Execution approved 2026-09-08 at 00:18:19 UTC.
- Coordinator: current Jcode root session. Ben's human continuation at 01:11:40 UTC confirms the proposed routing after the required ritual.
- One beacon at 00:19:40 UTC reported `visible:true` and expired. No repeat beacon was used. The later human confirmation, not expiry or automatic notifications, opened worker routing.
- Confirmed mapping: `openai-oauth:gpt-6-astra`, explicit `high`, for audio, presentation, experience, and independent critique. The route and enforced model pin were rechecked before spawning.
- At most two live workers, one implementation writer. Coordinator owns shared files, real device actions, verification, commits, and cleanup.
- Section 1 [round 1](critiques/section-01-round-01.md) and [round 2](critiques/section-01-round-02.md) each scored 7/10. Round 2 confirmed all first-round corrections, then reproduced missing/duplicate SDK declarations passing artifact checks. The narrow SDK correction and round 3 follow. No expanded app feature is accepted yet.
- The round 1 critic, two root researchers, and section 1 correction worker are stopped after retained handoffs. The correction worker completed its eight-file patch and released ownership at 01:50 UTC. Coordinator verification precedes the correction commit and independent round 2. Next implementation scope is the fixed packaged root-audio feasibility helper, not product acceptance.
- At 02:08 UTC `session_skunk_1788833286980_595eb5eac5d15851` became sole implementation writer for the fixed debug helper. It also owns the narrow SDK correction subset. The coordinator does not edit those files. Section 1 reviewer `session_hedgehog_1788833218692_8aebcc68229bc347` finished and was stopped at 02:15 UTC.

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
- Recovery also preserves original installed APK, signer/package details, device settings, and protected-file hashes. A same-package debug probe update is now installed, detailed below. No settings mutation occurred.
- Storage baseline: 17 GiB free on the project filesystem. Bound retained artifacts and monitor before large builds.

Before rollback, recheck package/signer and compare the exact backup hashes. A same-package reinstall preserves data. Restore the matching private state only when the coordinator has established ownership and stopped the app. Do not clear data or uninstall. Revert only owned source changes. Do not blindly roll back another session's commits or user tuning.

## Observed capability evidence

The KernelSU home screen reports `Working [Jailbreak mode]`, `LKM`, driver `32525-2`. Manager `v3.3.0 (32601-2)` reports a version mismatch. The screen also reports Permissive SELinux and disabled seccomp. No security setting was changed. The shell lacks visible su and cannot inspect /data/adb. These facts establish a running reported driver, not app/helper authorization or usable capture.

At 01:10 UTC the Superuser list and Phosphor profile show an existing enabled ROOT grant, Default profile, UID 10401, package `dev.phosphor.mobil3.debug`. No grant changed. `run-as` still lacks visible su, but its `runas_app` context is not the normal app process. A fixed debug-only normal-process authorization probe is the next discriminating check.

At 01:21:45 UTC the exact `bf9b8f2` debug probe ran in the normal `untrusted_app` process, UID 10401. Every fixed standard su location returned ENOENT. Cleanup was confirmed after ten milliseconds. Installed/readback SHA-256 is `1ae6992d0f80667676c2d54715c047a2fb049a6adcb00552c56bb5d90653a520`, signer unchanged. Preferences remain byte-identical through install and probe. [Authorization receipt](section-02-authorization.md) records the failure and the independently inspected, separately invoked KernelSU 3.2.5 compatibility test. No PCM is claimed.

At 01:32:35 UTC the exact `ff7067e` compatibility probe returned UID 0, exit 0, through the existing KernelSU 3.2.5 provider, initiated by normal app PID 22323/UID 10401. It completed in 210 ms with confirmed pipe/child cleanup, no recording or projection, and unchanged preferences. Installed/readback SHA-256 is `31d90be7dd58522148397302f61686806b19e842c4507cfc0d878e02601249d3`, signer unchanged. All 17 probe tests and full Android gates passed. Existing `logcat sulogd` PID 10693 is 15 days old and was not touched. Root command authorization is now proven, but the packaged production helper and PCM are not.

Display metadata advertises HDR10, HLG, and HDR10+. Actual FP16/scRGB application presentation, transparent HDR, and panel luminance remain unproven. Ordinary operation with root disabled on this modified device is not an unmodified non-root OS receipt.

At 02:11 UTC read-only audio preflight showed MODE_NORMAL, speaker selection, no registered Audio Policy Mix, and speaker MUSIC volume 0/15. This newer user state supersedes the old 2/15 receipt. Preserve it. The first muted fixture can prove PCM/routing and teardown, not physical audibility. Baseline output threads were in standby. Both USB and wireless still identified the same S25. No other app or volume was controlled.

The current logcat executable has the previously documented override. Its separately verified compatibility identity probe establishes authorization only. Reuse the noninvasive ignored logd diagnostic for logs. Do not replace the executable or depend on its override in the product.

## Section and requirement matrix

Statuses are `pending`, `in progress`, `implemented`, `accepted`, or `blocked`. A planned owner is not an active worker.

| Section | Requirement | Planned owner | Status | Required evidence | Critique |
|---|---|---|---|---|---|
| 1 | Baseline/contracts | Coordinator + boundary corrections | in progress | Round 1 found actual packaged-manifest binding and error-contract gaps. Corrections active | Round 1: 7/10, round 2 pending |
| 2 | R01 root | Coordinator | in progress | App-origin UID 0 proven on exact ff7067e, unchanged settings. Fixed helper and non-diverting PCM still unproven | Not started |
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
| All | R15 critique | Independent critic | in progress | Astra route/effort, pinned section, score/round/evidence, four-round cap | Section 1 round 1: 7/10 |
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

### Section 1 correction verification, 02:05 UTC

The coordinator inspected the eight-file correction and independently ran `scripts/test-play-boundary.sh`, shellcheck, Bash syntax checks, the source gate, `git diff --check`, exact generated-APK hashes, and protected-file hashes. Task `055179nzvw` exited 0 in 77.48 seconds. The suite passed 102 parser cases and 67 packaged/public CLI cases, including actual debug APK rejection with unrelated production XML, real AAPT2 APK and bundletool AAB positives, all C0 bytes, and compilerless Java exit 2. Generated APK SHA-256 remained `31d90be7dd58522148397302f61686806b19e842c4507cfc0d878e02601249d3`. No app build, signing, install or device action occurred in this gate. Raw output is `section1-corrections-coordinator.log` in the private recovery directory. Independent round 2 remains required.

### Section 1 SDK correction verification, 02:20 UTC

The coordinator independently verified the five-file SDK correction after source ownership was released. Task `960508e89v` passed 107 parser cases, 70 real-format/public CLI cases, the original boundary fixtures, shellcheck, source checks, diff checks and exact generated-APK preservation in 83.65 seconds. Missing SDK in actual APK/AAB and duplicate SDK in an actual APK now return exit 4. Source omission remains valid. The artifact hash is unchanged. No app build or install occurred. Raw output is `section1-sdk-coordinator.log` in private recovery. Round 3 must review the correction commit independently.

1. Finish and independently review section 1 corrections. Preserve the real debug-APK/mismatched-XML regression.
2. Keep normal root-off startup inert and avoid system/kernel/security changes.
3. Exact section 1 Android unit/lint/build/checkEngine passed at `283a017`, task `509140jwt7`, 55 tasks, exit 0. Retained log and built APK hash are in the private recovery directory.
4. After source ownership returns, implement the fixed packaged AudioPolicy feasibility helper. Keep one implementation writer and at most two live workers.
5. Verify PCM against controlled Phosphor-UID fixtures only. Never capture actual calls or private messages during testing. The phone was in user-selected Telegram at 01:34 UTC, so do not restore the launcher over the user's active UI.
6. Retain the source-backed AOSP finding: privileged loopback-with-render is limited to 16 kHz mono PCM16 and bypasses projection opt-out, not NO_SYSTEM_CAPTURE. Normalized stereo does not create stereo input. Capture quality and exclusions must be truthful.

## Blocked outcome and release boundary

Record cause, evidence, alternatives tested, useful verified partial result, and smallest next step. Continue independent work while keeping the requirement open. Four sub-8 critiques do not waive correctness/privacy/device gates. Signing inputs, exact release-tag approval, store evidence, publication, and irreversible operations remain separate gates.
