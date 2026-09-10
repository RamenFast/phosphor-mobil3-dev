# Mobile expansion execution ledger

Canonical plan: [MOBILE-EXPANSION-PLAN.md](../../../MOBILE-EXPANSION-PLAN.md).
Active contract: [EXPANSION.md](../../../spec/EXPANSION.md).
Authority: [2026-09-08 execution decision](../../../decisions/2026-09-08-mobile-expansion-execution.md).

## Current checkpoint

- Execution approved 2026-09-08 at 00:18:19 UTC.
- At 02:47 UTC Ben explicitly made `/system`, including `/system/bin`, `/vendor`, and boot/vbmeta partitions read-only. Raw block aliases and both slots are included. Reads remain allowed. Existing no-remount, no-flash and no-root-configuration-change boundaries remain in force.
- Coordinator: current Jcode root session. Ben's human continuation at 01:11:40 UTC confirms the proposed routing after the required ritual.
- One beacon at 00:19:40 UTC reported `visible:true` and expired. No repeat beacon was used. The later human confirmation, not expiry or automatic notifications, opened worker routing.
- Confirmed mapping: `openai-oauth:gpt-6-astra`, explicit `high`, for audio, presentation, experience, and independent critique. The route and enforced model pin were rechecked before spawning.
- At most two live workers, one implementation writer. Coordinator owns shared files, real device actions, verification, commits, and cleanup.
- Section 1 is accepted at `3e5e00e`. [Round 1](critiques/section-01-round-01.md) and [round 2](critiques/section-01-round-02.md) each scored 7/10. After both corrections, independent [round 3](critiques/section-01-round-03.md) scored 9/10 with no material findings. Stop corrective review early. No expanded runtime feature or release is accepted yet.
- The round 1 critic, two root researchers, and section 1 correction worker are stopped after retained handoffs. The correction worker completed its eight-file patch and released ownership at 01:50 UTC. Coordinator verification precedes the correction commit and independent round 2. Next implementation scope is the fixed packaged root-audio feasibility helper, not product acceptance.
- At 02:08 UTC `session_skunk_1788833286980_595eb5eac5d15851` became sole implementation writer for the fixed debug helper. It also owns the narrow SDK correction subset. The coordinator does not edit those files. Section 1 reviewer `session_hedgehog_1788833218692_8aebcc68229bc347` finished and was stopped at 02:15 UTC.
- The helper worker released all implementation files at 02:35 UTC and was stopped at 02:42 UTC. The coordinator now owns all source integration and the only Gradle build slot. Its released 17-file receipt is `/home/ben/.jcode/scratch/section2-helper-source.sha256`, hash `be2e1f71175fa741a82188273d05a1c4ed6bf9d84a69b9d2af663fbdcb376305`. That receipt predates coordinator corrections and is not the current source/APK identity.
- At 03:28 UTC installed clean source `a93db64` passed three real root PCM captures, same-process repeat, real app-parent death cleanup and fresh-process recovery. [Exact trial evidence](section-02-helper-trials.md) supersedes prior startup failures and installed hashes. Full R01 remains in progress. All workers are stopped. The coordinator owns source, the build slot and device actions until the next explicit assignment.
- At 03:33 UTC `session_octopus_1788838399073_f4efc740327eaecf` became sole R01 product writer on confirmed Astra/high. It owns the fixed helper, variant packaging, root-specific main/debug/test integration, root contract and narrow post-JNI test observation. It has no Gradle, device or Git authority. At 03:34 UTC `session_scorpion_1788838484448_ab469c39ab0716af` began a read-only audit of base7f8b1d3 lifecycle seams. This is not a scored full-section review. The coordinator owns receipts, source gates, all builds and device actions. [Product acceptance checklist](section-02-root-acceptance.md) records the required observations.
- The read-only auditor completed at 03:46 UTC and was stopped after its [full source-backed lifecycle map](section-02-lifecycle-audit.md) was retained. It found the source-sheet pre-consent gates, three PlaybackService stop routes, shared owner/retirement requirements, mic's dual acknowledgement and an unused untagged metadata-stop handler. These went to the sole writer before final integration. No runtime or R15 score was claimed. The coordinator reserved a third fixed playback-only tone action for actual UI acceptance without creating another helper.

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

At02:56 the exact clean `4f8e5ec` app APK was installed and readback-verified. Both debug APKs were built together. [Helper trial1](section-02-helper-trials.md) proved the installed native PIE obtained UID0 with unchanged mount namespace. ART then aborted before READY because its fixed environment lacked boot classpaths. No tone or policy started, no helper remained, and preferences stayed byte-identical. The read-only platform-export correction passes16native tests and awaits its exact Android build. At02:54 the user-selected route was idle Bluetooth A2DP at MUSIC7/15, not the earlier muted speaker. Preserve that current state.

## Section and requirement matrix

Statuses are `pending`, `in progress`, `implemented`, `accepted`, or `blocked`. A planned owner is not an active worker.

| Section | Requirement | Planned owner | Status | Required evidence | Critique |
|---|---|---|---|---|---|
| 1 | Baseline/contracts | Coordinator + boundary corrections | accepted | Coordinator and independent real-format gates passed, exact backup/history hashes preserved | Rounds 1/2: 7/10. Round 3: 9/10 |
| 2 | R01 root | Coordinator + audio writer | in progress | Exact a93db64: three real PCM passes, live render/loopback, repeat, parent death and recovery, unchanged preferences. Product streaming/service/UI and broader failure acceptance remain open | Full section not started. Separate bounded helper safety review retained |
| 3 | R09 mic/mix | Audio | pending | Actual accessory route, two signals, rate/drift/buffer/clipping and partial-loss checks | Not started |
| 4 | R02 HUD | Coordinator, released Poodle/Nautilus writers | implemented | Frozen source:505 Android unit tests, lint, dual APKs, engine,79 native tests, source boundary12. Hardware transparency, touches, handoff and teardown still open | Round1:6/10. Round2:8/10. Separate callback addendum closes source defect |
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
| All | R15 critique | Independent critic | in progress | Astra route/effort, pinned section, score/round/evidence, four-round cap | Section 1 accepted at round 3: 9/10. Later sections pending |
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

## Checkpoint history

### Section 1 correction verification, 02:05 UTC

The coordinator inspected the eight-file correction and independently ran `scripts/test-play-boundary.sh`, shellcheck, Bash syntax checks, the source gate, `git diff --check`, exact generated-APK hashes, and protected-file hashes. Task `055179nzvw` exited 0 in 77.48 seconds. The suite passed 102 parser cases and 67 packaged/public CLI cases, including actual debug APK rejection with unrelated production XML, real AAPT2 APK and bundletool AAB positives, all C0 bytes, and compilerless Java exit 2. Generated APK SHA-256 remained `31d90be7dd58522148397302f61686806b19e842c4507cfc0d878e02601249d3`. No app build, signing, install or device action occurred in this gate. Raw output is `section1-corrections-coordinator.log` in the private recovery directory. Independent round 2 remains required.

### Section 1 SDK correction verification, 02:20 UTC

The coordinator independently verified the five-file SDK correction after source ownership was released. Task `960508e89v` passed 107 parser cases, 70 real-format/public CLI cases, the original boundary fixtures, shellcheck, source checks, diff checks and exact generated-APK preservation in 83.65 seconds. Missing SDK in actual APK/AAB and duplicate SDK in an actual APK now return exit 4. Source omission remains valid. The artifact hash is unchanged. No app build or install occurred. Raw output is `section1-sdk-coordinator.log` in private recovery. Round 3 must review the correction commit independently.

1. Preserve accepted section 1 gates and regressions. No fourth review is needed for its `3e5e00e` checkpoint.
2. Keep normal root-off startup inert and avoid system/kernel/security changes.
3. Exact section 1 Android unit/lint/build/checkEngine passed at `283a017`, task `509140jwt7`, 55 tasks, exit 0. Retained log and built APK hash are in the private recovery directory.
4. After source ownership returns, implement the fixed packaged AudioPolicy feasibility helper. Keep one implementation writer and at most two live workers.
5. Verify PCM against controlled Phosphor-UID fixtures only. Never capture actual calls or private messages during testing. The phone was in user-selected Telegram at 01:34 UTC, so do not restore the launcher over the user's active UI.
6. Retain the source-backed AOSP finding: privileged loopback-with-render is limited to 16 kHz mono PCM16 and bypasses projection opt-out, not NO_SYSTEM_CAPTURE. Normalized stereo does not create stereo input. Capture quality and exclusions must be truthful.

## Blocked outcome and release boundary

The final freeze builds the application and androidTest APKs together from the same reviewed clean source. Hash both artifacts and the source before installation. A skipped androidTest task or missing APK is not dual-artifact proof. The helper candidate build now produces both APKs, but the source has no handwritten instrumentation tests yet. A generated test APK is not device-test coverage. One explicit build-slot owner runs all Gradle tasks. Source/test preparation may proceed during an isolated build, but no second Gradle invocation starts until that slot is released.

Record cause, evidence, alternatives tested, useful verified partial result, and smallest next step. Continue independent work while keeping the requirement open. Four sub-8 critiques do not waive correctness/privacy/device gates. Signing inputs, exact release-tag approval, store evidence, publication, and irreversible operations remain separate gates.

### R01 product handoff and coordinator gate, 04:13 UTC

Octopus released all source ownership at04:11:40. Its38-path receipt SHA-256 is `f804921958b1664e4ddcf154e35b5f56906235c4e5fced5e11dbe76b5055ff00`. The coordinator verified every present/deleted path before further edits. The worker reported26 locked native tests and207 Java assertions passed, with13 additional Android-unit cases authored but unrun. Its private report is `r01-product-writer-handoff.md` in the Jcode scratch directory. The writer was stopped at04:13.

The coordinator inspected session framing, READY/current-owner fencing, observer-specific test cancellation, root/standard stop routing, actual FGS roles and variant identity inputs. It formatted the native sources and corrected standard-only consent and opt-out help text. Task `7960916xk9` now independently runs host tests, Android unit/lint/engine checks, both debug APK builds and both generated helper variants. The coordinator holds the only Gradle slot. No protocol2 APK has been installed or accepted yet. Installed runtime remains exacta93 protocol1 feasibility.

Zebra is the sole remaining worker, Astra/high, with a read-only R09 routing and service/mixer facts task. It reads baseline089382d through Git objects, writes only a private report, and has no source, build, device or Git authority. R01 remains the active implementation section. No later runtime section has started.

### R01 independent build and stereo clarification, 04:28 UTC

The coordinator independently passed all 26 native tests and 207 Java assertions. Android gate1 exposed an invalid primitive-array `mapNotNull` test call. Gate2 compiled both APKs but found two existing rotation-first heartbeat assertions. The coordinator corrected the test receiver conversion and restored the established heartbeat order. Gate3, task `036677fj3w`, passed all 477 Android unit tests with zero failures/errors/skips, lint, engine checks, both debug APK tasks and the release-helper generation task. No regression assertion was removed. The source boundary passed12 checks and all five protected hashes matched.

Generated helper inspection used actual AGP-managed asset/JNI directories, not their pre-registration conventions. Both PIEs are AArch64 with16KiB LOAD alignment. Cargo dep-info records the exact variant package, build identity, debug-mode flag and matching DEX digest. A failed raw-string search was an inadequate optimized-binary inspection method, not proof of a digest mismatch. Full release signing/packaging remains untested and its guards unchanged.

At04:15 the S25 still had the original preference archive SHA-256 `1f3fcf264ead0a0b3823a2c31f8c512842c9e25501c082ce659ed2b2fa3d1a72`, no active player, no capture policy and no projection. The user's TikTok activity remained focused. No install, launch, media control, volume or route change occurred. Installed app remains the a93 feasibility artifact.

Ben clarified at04:17 and04:21 that R01 requires actual stereo, meaningful kHz fidelity and a SoundCloud smoke test. He reports SoundCloud is silent under standard capture. These are acceptance requirements, not optional improvements. The current16kHz mono implementation is only a limited checkpoint. Stallion now researches stereo paths read-only. Zebra's bounded R09 handoff is retained and its worker stopped. No live audioserver mutation, injection, hook, restart or policy bypass experiment has run.

### Clean product checkpoint and latency priority, 04:40 UTC

Task `769513tatl` committed `ccee7c827da44d4f7dc2268eecbe6619685594a6` and built both debug APKs together from that clean source. Source archive SHA-256 is `0ff550ab606b09f7ebc54993543f425712fb64b4de30d7dc1e0718c0815badea`. App SHA-256 is `e4c125e8ba4d5cc5ffa246d358941ea58e1b93c1d454b78c9cd7960359684e77`. Companion test SHA-256 is `1876caa79b7d30678641419bd5ab5a0a150082b964531a451f4166061bf24515`. Helper identity is `c6054daf4440ef4cbbb28d4a4dba715028ac56d60008f31423e2edcf1a365c91`, with DEX SHA-256 `69446d7e2b10b810a9a69f7c00743d0f865486386c830470eebe0c7f1c3fa266`. Retained copies and logs live in the existing private recovery directory. These are build receipts, not product or stereo acceptance.

Read-only inspection of SoundCloud2026.08.13-release, code368060, found `allowAudioPlaybackCapture="false"` in its installed base APK. APK SHA-256 is `7d2b4a238b63e3d374b45f795e3246d84afb749750e9c304cbb8a5a665bcbf13`. Runtime capture flags, offload behavior and actual root support remain unmeasured.

Stallion completed its source-only stereo report and stopped. Dromedary is the sole implementation writer, Astra/high, for fixed debug-only own-UID48kHz stereo LOOP_BACK plus distinct-UID physical monitoring. Existing product modes and mono wire format remain unchanged pending feasibility. Hog is the second worker, Astra/high, read-only, investigating direct-copy ownership and cleanup to avoid re-render latency. Neither worker has build, device or Git authority. The coordinator owns the only Gradle slot and all phone actions.

Ben added minimal-latency and audible alignment acceptance at04:38:43. The proposed re-render delay is unmeasured. Actual input/monitor timestamp pairs, buffer occupancy and frame mapping will be retained as estimates, not acoustic proof. Direct-copy Binder mutation remains unexecuted while lifetime and unrelated-output preservation are unresolved. Protected phone paths and partitions remain read-only.

### Exact product trials, 04:48 UTC

The coordinator installed the retained `ccee7c827da4` app with `dev/pm3`. Installed readback and signer matched the clean-build receipt. Preference archives before and after installation matched the original byte-for-byte. Task `5093544ubs` failed only in its final shellcheck invocation because external source following was omitted. The install itself succeeded. Re-running shellcheck with `-x` passed without changing the script's assertions.

The first actual product SYSTEM trial, task `539528x6c7`, reached the real specialUse service, tagged policy, root recorder and progressing own-UID BY_SYSTEM player. AudioFlinger showed an unsilenced16000Hz client and nonzero input-HAL signal. The fixture then failed its combined PCM count/frequency/RMS assertion. Its old error path discarded aggregate values, so the exact failed subcondition remains unknown. Helper read progress reached20 and source head76394. Independent helper/policy/service cleanup, byte-identical preferences and volume preservation passed. The coordinator changed the debug receipt to retain signal and post-JNI aggregates on failures as well as successes. No pass is inferred from the live HAL signal.

The NONE trial, task `719361eh1j`, returned healthy silence: read progress20, PCM80128frames, nonzero0, normalized240384frames, post-JNI ringRMS0 and source head74443. This directly proves a finite-mode frame-bound defect: the pre-read limit check allowed one partial block beyond80000. The writer is correcting the requested read length and adding a partial-bound regression. This is a plausible cause of the prior SYSTEM failure, not a confirmed diagnosis of its missing metrics.

NONE's helper, policy, service and preference cleanup passed. Its overall preservation guard failed because Bluetooth media volume changed from9 to7 during capture. AudioService recorded two `com.android.bluetooth.setA2dpDeviceVolume` events fromUID1002 at21:45:23.787 and21:45:24.270. The coordinator made no volume-setting calls and did not restore the earlier value. Further audible trials wait for a new quiet preflight. The current user-selected level remains intact. Stereo, SoundCloud and latency acceptance remain open.

### Bounded stereo increment and independent correction review, 05:19 UTC

Dromedary released15files and stopped. The coordinator independently passed264Java checks,27native tests and485Android unit tests, lint, both debug APKs, engine validation and release helper compilation in task562669fo4k. The later source-head/FINAL corrections passed487Android tests and the same debug gates in188210wydg. These are successive source gates, not one final frozen artifact.

The [immutable source review](section-02-stereo-review.md), SHA256`afb7399b6d863a6334810574607af263af37bc588e379f36c60520cabadf6eb0`, found rejected FINAL retention, native-filtered protocol rejection losing its cleanup latch, and a wallclock source cutoff that could truncate startup-delayed playback. The coordinator updated the contract before each correction. The [separate correction addendum](section-02-stereo-review-addendum-01.md), SHA256`f059a483f689da9961d7099d878545a5e930632a5d419d642a54343b00c7a890`, found those exact corrections resolved at source level. Both coordinator and reviewer passed30locked native host tests. The latest Android-specific native change still needs the clean dual-artifact build. This review is not a full R15 score or hardware acceptance. Kitten is stopped. All source/build/device ownership is back with the coordinator.

New debug modes4/5 use only originalUID MEDIA/GAME,48kHz PCM16 stereo LOOP_BACK and a separately verified monitor player UID. They emit bounded aggregates, not product PCM. Product mode3 now respects its exact80000-frame read cap, and failed product checks retain their measured PCM/ring aggregates. The [finite contract](section-02-stereo-probe.md) preserves exact source counts, source/record/monitor clock evidence, queue limits, quiet tone ramps and conservative cleanup. No stereo phone trial has run.

The coordinator prepared but did not execute `dev/scratch/mobile-expansion-20260908T001819Z/run-root-stereo-check.sh`, SHA256`82eabce0ccd0052e93df3c25a728ad58112ec3f360a72fa637d75e8870cac107`. Its Bash syntax and shellcheck-x pass. It requires a reviewed clean dual-artifact freeze, verifies source/archive/APK identity, checks idle preflight, runs only a fixed DUMP action and checks independent cleanup/preferences/physical-volume/fixed-volume/route restoration. The independent reviewer did not review this runner. Debug manifest SHA256 is`2f8ed062c6c875e3e7eb851aa52f43000f668a1149438c54d926008f8e9388cc`. Source boundary12 and all5protected hashes pass. Latest read-only S25 preflight shows normal mode, no active playback or policy mix, and only the unrelated old sulogd process.

Hog's [direct tee audit](section-02-direct-tee-audit.md) and [native policy addendum](section-02-native-policy-audit.md) are retained byte-identically. Direct tee replaces foreign output lists without ownership/CAS and leaves surviving source references after sink closure. Native registration has no inspected universal fidelity cap, but automatic policy-mix cleanup ownership remains unestablished. Neither path was executed. Do not broaden the controlled probe or write protected system/partition paths.

At04:56/04:58 Ben authorized an encouraging root-ready authorization surface if bounded stereo work remains blocked. That fallback must keep the authorization check real, label missing stereo honestly and preserve standard capture. Continue independent expansion work rather than repeating uninformative root experiments. SoundCloud, full stereo product, selected-rate negotiation, physical gain/audibility and minimal added latency remain open.

### Reviewed stereo freeze and quiet-window deferral, 05:37 UTC

The coordinator corrected the source-completion threshold, sticky app protocol rejection and native terminal-cleanup evidence. Kitten preserved its original report and supplied a separate correction addendum. Both immutable reports are tracked. Independent source review accepted the narrow corrections for the normal Android gate and one bounded own-UID trial. It did not accept hardware behavior or independently review the host runner.

Task `809210e4xz` committed clean `06f84e2eb7da46c758e9f8b388c3537e6c67905d` and built both debug APKs together. All487 Android unit tests, lint, engine checks and both native helper variants passed. The coordinator and critic each passed30 native tests. Coordinator Java checks passed264 assertions, with57 stereo assertions independently repeated. The [stereo trial receipt](section-02-stereo-trials.md) records exact source, app, companion, manifest and report hashes. Private retained APKs remain unchanged.

USB disappeared before installation. The existing wireless endpoint identified the same hardware and build. A fresh preflight then found an active external MEDIA player and exited2 before installation or test audio. Installed code remains `ccee7c8`. No stereo trial, physical audibility, gain, latency or SoundCloud claim follows from these host checks. Current playback remains untouched. A later quiet preflight can use the retained reviewed candidate without rebuilding another worker's changing source.

Poodle now owns only R02 floating HUD implementation, Astra/high. Its contract precedes runtime edits and requires inert defaults, generation-owned presentation, actual transparency, truthful controls and owned cleanup. Active activity-owned microphone transfer stays blocked without stopping the microphone until R09 supplies the service owner. Hare independently audits R13 HOLD seams through frozen Git objects and writes only a private report. Neither worker may run Gradle, operate a device or mutate Git. The coordinator retains the only build slot and all installation, integration and commit authority. R09 input mixing remains open pending format and capture-clock evidence.

### Stereo buffer correction and HUD integration, 06:24 UTC

Task `9872769kyw` installed the reviewed `06f84e2` app after a fresh quiet preflight. Installed readback and signer matched. The first SYSTEM stereo trial, `015380v664`, initialized actual48kHz PCM16 with two channels, own-UID LOOP_BACK and a distinct-UID physical monitor. It stopped cleanly at the unchanged4800-frame monitor queue limit after4992 read/written frames. Independent process/policy cleanup and preferences/volume/route preservation passed. No completed stereo window, physical audibility, latency or SoundCloud acceptance follows. Exact artifact and receipt hashes are in [stereo trials](section-02-stereo-trials.md).

The monitor's measured capacity was8793frames. Pinned platform source establishes that capacity can set the default startup threshold. The correction requests and reads back960 effective buffer frames and480 startup frames before playback and after route selection. It does not increase the100ms queue limit. The threshold hypothesis remains unproven on this phone until a new exact-artifact trial.

Vole's immutable [ADDENDUM-02](section-02-stereo-review-addendum-02.md) accepted the narrow buffer source delta but found missing terminal UTF-8 byte budgeting and unbounded host transport. The separate [ADDENDUM-03](section-02-stereo-review-addendum-03.md), SHA256`25e95b0a1736bd31014908274f97f7a9da9ee6c50521a2871d9209f6a19770b2`, independently accepted both corrections at source/host scope. Full diagnostics either fit the existing frame or become a fixed error receipt preserving identity and cleanup truth. The new runner bounds every ADB operation and attempts independent postflight after handled launch failures. Five fake-ADB fixtures passed independently. No reviewer ran the actual runner or phone. The old reports and runners remain unchanged. Coordinator host checks passed207 core/normalizer,75 stereo and154 terminal assertions.

Poodle released R02's19-file source receipt at06:03 with75 host Rust tests. All19 hashes matched before integration. Its worker is stopped. The coordinator's first frozen Android gate found an undefined `micHandoff.active`; the existing `isPending` property corrected it. The second gate compiled Android but exposed five source-linked tests that still expected activity-only cycle calls and ungated chrome ticks. Updated assertions now require shared lease-gated metadata, hidden-owner guards, and removal before each tick restart. The third gate passed all498 Android unit tests, including nine HUD fixtures, but failed lint on a synthetic `surfaceChanged` call with invalid format0. The overall gate is not accepted. All three source manifests matched before/after their gates.

Dragon independently reviews the immutable released HUD source. Early source findings include incomplete native timeout retirement, mutable metadata driving duplicate cycles and an uncancelled pending controller future. These are not phone observations. HUD source remains uncommitted and uninstalled pending correction and independent assessment. The coordinator owns the only build slot and all device/Git actions. Stereo/SoundCloud/latency and R02 device acceptance remain open. Protected phone paths and partitions remain read-only.

### Isolated stereo freeze, 06:39 UTC

To avoid coupling a phone experiment to changing HUD code, the coordinator built committed stereo source`15bcbe5` and the exact sibling engine from separate clean local checkouts. Task`217974blpq` passed487Android unit tests, lint, both debug APKs, engine checks and both helper variants. Complete source hashes and clean Git state matched before and after. The retained [stereo freeze](section-02-stereo-trials.md) records exact source, app, test, helper, signer and review hashes. No worker ran Gradle. The initial setup attempt stopped before build because a toolchain directory symlink was not ignored. Replacing that scratch-only link with an ignored directory of toolchain links restored genuinely clean source before the successful gate.

The S25 had unrelated active MEDIA playback at06:34. The candidate was retained but not installed. No new tone, source control, volume, route or permission operation occurred. Installed code remains`06f84e2` and preferences match baseline. Nautilus owns a bounded HUD F2/F3/F4 correction window. Dragon is idle pending the exact released delta for a separate round2 assessment. Its immutable score6/10 baseline remains in`db19cc4` and is not rewritten.

## 06:58 UTC: HUD source integrated and independent correction review closed

Poodle's original HUD source and Nautilus's native lifetime, stable-item and controller-future corrections were integrated by the coordinator. Both writers stopped after their exact receipts were retained. Dragon's immutable round1 scored6/10. Round2 scored8/10 and accepted those corrections while identifying a callback failure adapter defect. The separate callback addendum closes that defect at source without changing the score or claiming Android callback execution. All reports are retained byte-for-byte under `critiques/section-04-*`.

Coordinator frozen gate `329223ctm4` passed505 Android unit tests, lint, both debug APK builds, engine integration and both helper variants. All captured source hashes matched afterward. Independent locked/offline Rust1.96.0 tests passed79/79. The production source boundary passed12 checks. Exact logs, hashes and remaining device obligations are in [HUD validation](section-04-hud-validation.md). This working-tree gate is not a clean committed install freeze.

At06:54 a bounded read-only S25 snapshot still found unrelated active MEDIA playback, no Audio Policy Mix, and baseline-identical preferences. No install, audio, route, volume, grant, security or protected-partition write occurred. Reviewed stereo-only candidate15bcbe5 remains retained but not installed. R01 hardware testing and R02 physical acceptance await a quiet boundary. Quiet deferral is not a technical failure. R09 remains dependent on accepted input/clock evidence. R13 may proceed against the now-reviewed presentation owner.

## 07:02 UTC: clean HUD artifacts retained and next source window released

Clean dual-artifact gate810364pegz passed on mobilea3223b8 and engine7729990 with both sources unchanged. The [HUD validation receipt](section-04-hud-validation.md) records source, app, test, signer, actual packaged manifest, helper and freeze hashes. These artifacts are retained but uninstalled. Current phone playback and installed06f remain untouched.

Hatchling (`session_hatchling_1788850916645_0936b9dc7b70d2b5`), confirmed Astra/high, became the only R13 implementation writer at07:02. Its25-minute bounded source window covers the exact pause/inspection contract and additive shared GPU retention. The agreed mechanism retains immutable energy plus frozen composite uniforms outside Active, commits successful-present identity, and lets the destination own background opacity. No opaque-image fade masquerades as transparent HUD. Physical scanout and unsolicited external pause timing remain explicit limits. Worker uses private host build targets and has no Android, device, Git or runtime authority.

Wyvern (`session_wyvern_1788851255827_314313334fccf04b`), Astra/high, began an eight-minute read-only audit at07:07. It inspects fixeda322/772 objects to close the retained audit's pause-entry and visual-ingress freshness gaps. It has no editing, compilation, device or Git mutation authority and provides no R15 score. Coordinator owns receipts and the shared FEEDBACK entry, all integration and the single Gradle slot.

## 07:42 UTC: HOLD integration gate passed, source review still pending

Hatchling released its25-path source and stopped. Wyvern and Calf also stopped after their bounded read-only reports. The original writer handoff and next-section color map are retained unchanged. No worker retains source or build ownership.

Coordinator gate5737253okz passed83 native tests and two actual offscreen GPU pixel tests, but failed two of505 Android unit tests. The coordinator corrected display-versus-transport telemetry and selected-source-versus-live-reader truth, after clarifying the contract. Seven production-policy tests and two archive cases were added without dropping earlier transport assertions.

Frozen correction gate966199akcz passed514 Android tests with no failures, errors or skips,83 native tests, two real GPU pixel tests, lint, both debug artifacts, engine integration, release helper and source boundary. Complete mobile and engine manifests stayed unchanged. [Exact receipt and acceptance gaps](section-05-hold-validation.md) separate those checks from unrun Android/device behavior. Shared renderer source is committed at084f5d9612f7f699bb80b08607845982d1579861. The working-tree artifacts are not a final clean installation freeze.

R13 independent critique and capture producer read-epoch freshness remain open. Last07:19 read-only phone preflight found unrelated active media and baseline-identical preferences. No installation, audio control or protected phone write occurred. Reviewed stereo15bc and HUDa322 candidates remain retained but uninstalled. Root stereo separation, audible monitoring, added latency, SoundCloud and full expansion acceptance remain open.

## 08:02 UTC: first HOLD critique retained and correction gate passed

Macaque's immutable exact44172/084f review scored6/10 and identified five concrete gaps. Tigress released the app AudioRecord producer-token/read-epoch correction with scoped host evidence. Both workers stopped, all11 released writer hashes matched, and their reports are retained unchanged. Root helper PCM still lacks producer read-epoch evidence and has not been mislabeled fresh at receipt.

Coordinator corrections clear both live GPU energy sides, reject stale remote transport observations under the pause transaction, seed initial transport observations without manufacturing HOLD, and expose an independent HUD LIVE action even with controllable audio. Frozen gate4104942tt5 passed521 Android unit tests,91 native tests,three actual offscreen GPU tests,lint,dual debug build,engine/release-helper integration and production source boundary. Both full source manifests stayed identical. [Correction receipt](section-05-correction-01.md) records hashes and remaining limits. Independent round2 and Android behavior remain open. No phone operations occurred. The single build slot is released.

## 08:22 UTC: immutable second critique and committed correction02 gate

Puppy's exact35d8/0ffd review scored7/10, with concrete split-reset, in-flight candidate, local producer retirement and capture-controller initialization traces. The report and receipt were sealed444, verified, retained unchanged, then the owned worker stopped. Coordinator authored correction02 before code: History visual revision plus source generation fences retained commits, renderer reset is token-based rather than a delayed boolean, local retirement follows producer join, and each capture-controller binding seeds its own pause observation without manufacturing a transition.

Mobile0591d73/shared0ffd658 passed isolated clean gate514919zi9y with526 JVM tests,94 native and three actual offscreen GPU tests, lint, dual debug artifacts, engine/release helper and production boundary. Both complete source sets remained unchanged. [Correction02](section-05-correction-02.md) records exact source/artifact/runner/log hashes. No installation or Android acceptance occurred. Mizaru, confirmed Astra/high, began a15-minute exact-object round3 at08:19:56. Duckling, also Astra/high, remains the sole six-color source writer under its30-minute window ending08:34. The isolated gate did not include or alter that writer's source. Gradle slot was explicitly released after the gate.

The08:04 bounded read-only S25 preflight still found unrelated active MEDIA playback, no AudioPolicy mix and byte-identical baseline preferences. No phone mutations followed. Original stereo, audibility, minimal added latency, SoundCloud, root source-time freshness and full Android acceptance remain open. The next independent section continues without relabeling these gaps as delivered.

## 08:44 UTC: released color integration passes the full frozen gate

Duckling released all15 paths, their receipt matched, and the worker stopped. The first full gate failed three stale source-linked import/restore assertions. Coordinator updated their exact adapter anchors after inspecting boolean and orientation preservation. A public archive-merge regression now checks those unrelated fields directly. Gate942127xywq passed539 JVM tests,104 native tests,three offscreen GPU tests,lint,both debug APKs,engine/release-helper integration and source boundary. Both complete source manifests stayed unchanged. The [color validation receipt](section-06-color-validation.md) distinguishes this working-source gate from a reviewed clean installation freeze and actual Android acceptance.

Mizaru's immutable R13 round3 scored7/10 and is retained unchanged. Its separate [disposition](section-05-round-03-disposition.md) carries root/source-time freshness and post-boundary submission gaps. The next root protocol research is read-only and bounded. Section6 independent round1 follows the committed checkpoint. Section7's typed instrument contract was committed39ba before runtime work. No phone mutation occurred. The08:24 read-only phone observation still showed unrelated active music and baseline-identical preferences. Stereo15bc and HUDa322 remain uninstalled.

## 11:10 UTC: preset workflow integrated, full section review follows

Since the08:44 checkpoint, color correction02 received an independent8/10 narrow source/host assessment, retained unchanged. R13's original6/7/7 scores remain intact. Its separately retained correction04 source audit supports exact-token application-present acknowledgement, not buffered source age or phone behavior. Root producing-read epochs are committed79af, with their separate protocol and host evidence.

Preset core/native commit019c0da passed587 JVM,121 native andthree offscreen GPU tests. Microbe then implemented the complete Activity/SAF/persistence/UI workflow under the approved Astra/high route. It explicitly released fourteen paths at11:05. The coordinator verified its immutable manifest and86-test host receipt, stopped the worker and acquired the sole Gradle slot.

Full frozen gate580618965w passed624 JVM tests across57 suites,121 native tests,three offscreen GPU tests,lint,both debug APKs,engine/release-helper integration and production source boundary. Both complete source manifests stayed unchanged. [Workflow validation](section-07-workflow-validation.md) records exact artifact and runner hashes, production-path checks and unaccepted Android behavior. Independent full-section R16 critique follows the committed checkpoint. No installation occurred.

Mushroom's bounded R17 source audit is verified and retained unchanged. The signal-check observation contract preceded runtime implementation. R17 remains pending implementation rather than being accepted from an evidence map.

Ben's09:05 sleep handoff remains binding. The S25 has been undisturbed. Host-only USB/ADB enumeration at11:00 found no ASUS, and local ADB mDNS enumeration at11:03 found no service. Original stereo, SoundCloud, audibility, low added latency, actual Android persistence/SAF/HOLD/accessibility, and the whole expansion remain open. Fifteen executable milestones are still incomplete.

## 12:07 UTC: corrected preset adapters and signal observations pass integration

Clover released22 R17 paths at11:51, their hashes matched, and the worker stopped. Coordinator integrated the R16 typed rollback adapters, automatic persistence guard, pre-picker authored ticket through whole-settings decoding, and compact LIGHT recall. Gate783238n3mp passed653 JVM tests across58 suites,125 native tests,three offscreen GPU tests,lint,engine/release-helper checks,source boundary and both APK builds. Complete mobile/shared before/after source manifests matched. Both artifacts and XML/lint evidence are retained in the [combined receipt](section-07-08-integration-validation.md), not installed.

The predecessor gate failed six stale source assertions. Corrected anchors preserve source ordering, exact local identity, no-mic-stop and gain obligations. No runtime code changed between those two gates. R16 original full round1 remains7/10, with corrected full round2 next. R17 full round1 is next. ASUS was absent at11:41 host enumeration. No phone action occurred. Root stereo, buffered source age, Android behavior and whole-expansion acceptance remain open. Section9's complete control inventory and settings-only dismissal contract precede runtime work.

## 12:35 UTC: startup recovery and observation provenance corrections gated

Sunflower's immutable R16 round2 and Hibiscus's immutable R17 round1 each scored7/10. Both reports are retained unchanged, and both workers stopped. Their finite findings led to written correction contracts before code. Startup recovery now exists before restore-time light transactions. Signal corrections preserve conservative local observation ages, both wire PCM16 rails, honest root observation units, unavailable unowned display taps and typed picker selection. Foreign transport details cannot label a missing or mismatched input.

Full frozen gate4931158kjm passed675 JVM tests across59 suites,126 native tests,three offscreen GPU tests,lint,dual APKs,engine/release-helper integration and production boundary. Complete source manifests matched before and after. Both APKs and actual XML/lint evidence are retained in the [correction receipt](section-07-08-correction-validation.md). This is working-source integration, not a reviewed clean installation freeze or Android acceptance. The failed1224 expected-exception test receipt and successful1226 predecessor remain preserved.

Section9 pure gesture/presentation owners were committedf504b3f with15 actual-source tests. Its settings-only Compose adapter and six grouped sections are next. Corrected full R16 round3 and R17 round2 remain required. At12:28 host-only USB/ADB enumeration again found no ASUS. No phone action occurred, and the S25 remains undisturbed. Fifteen whole-outcome milestones remain incomplete.

## 12:59 UTC: distinct startup authority gap corrected, settings writer continues

Blossom completed full R16 round3 at6355074 with7/10. The [original report](critiques/section-07-round-03.md), SHA256 `b9cb262b1efcc114b6adce76db6ab55d015753b3dcc6788adfe42d5cfd166f59`, is retained unchanged and its inventory verifies. The reviewer stopped. A retained renderer can differ from fresh Activity defaults after failed startup light restore. Commit914f585 adds explicit native reconciliation before saving that unconfirmed target. Its [correction contract](section-07-correction-03-design.md) records91 passing actual-source pure tests. Known-active persistence-only retry remains unchanged. Android integration awaits the settings writer's release, and the fourth full R16 review remains reserved.

Daisy owns only section9 UI adapters and related tests through13:16. No Gradle runs during that writer window. Tulip reviews full R17 round2 from immutable6355074 through13:18. Both use approved Astra/high. Root separately committed4375041, the four appearance design contracts and eight tested pure value/contrast/motion cases. That does not implement theme migration, editor or Android motion. Root stereo, source age, all phone acceptance and fifteen outcome milestones remain open.

## 13:19 UTC: settings integrated and second signal critique corrected

Daisy explicitly released all seven settings paths at13:05. All hashes matched, the writer stopped and root acquired the sole build slot. Gate788107bhla passed711 JVM/126 native/three offscreen GPU tests, lint, dual APK builds, engine and source-boundary checks. Both complete source sets remained unchanged. Commit374326f contains the settings-only deliberate dismissal and six retained expandable groups. The [integration receipt](section-09-integration-validation.md) retains both APKs and XML evidence, not Android touch acceptance.

Tulip's full R17 round2 at6355074 scored7/10. Its [original report](critiques/section-08-round-02.md), SHA256 `2a2e111c194bab960fbe6edd5fd7e585404b3876a33f8966102d852151174ac4`, is preserved unchanged. It confirms the five original fixes and identifies two finite health defects. Commita0d3c12 publishes diagnostic retirement before asynchronous capture cleanup and expires old zero-read progress. The [separate correction receipt](section-08-correction-02-design.md) records full gate2634988b58:716 JVM/126 native/three offscreen GPU tests, lint, dual APKs, engine and unchanged source manifests. Actual service callbacks remain untested. Tulip stopped after verified custody.

Rose, approved Astra/high, reviews full R16 round4 on374326f through13:33. Original7/7/7 scores remain unchanged. Maple, also Astra/high, reviews full section9 round1 on the same pin through13:40. Both review immutable Git objects and cannot build or touch devices. Root owns all mutable source and integration. R17 full round3 is still required, not replaced by the coordinator's gate.

No phone operations occurred. The ASUS was absent at the last12:28 host enumeration. S25 remains undisturbed. All new APKs are retained, not installed. Original stereo, SoundCloud, audibility, added latency, source age and fifteen outcome milestones remain open.

## 13:52 UTC: settings access corrections and final preset critique retained

Rose's fourth/final full R16 review at374326f scored8/10. Its [original report](critiques/section-07-round-04.md), SHA256 `db99e93c9fb8ba59cbd5a9df3cd03fb4914d5fe3420f57bd0c8607313b03e60c`, is preserved unchanged. Original7/7/7 remain unchanged. No fifth full review will run. The small remaining source-navigation mismatch received a separate typed recovery flag and [follow-up gate](section-07-final-review-followup.md). Commit3f2c198 passed736 JVM/126 native/three offscreen GPU tests, lint, both APKs and unchanged source/boundary checks. Real preset and storage/SAF acceptance remain open.

Maple's full section9 round1 at374326f scored7/10. Its [original report](critiques/section-09-round-01.md), SHA256 `175f9457fe60157af1dd227c31c1f5b3c33237ad393e5e5323d50983e2b378bb`, remains immutable. Commit4a0e8b6 corrects short upward-release flick classification and screen-owned settings state above hidden/PiP branches. Gate3294511d5q passed726 JVM tests and full integration. Commitf9a1106 adds SETTINGS-only range/key/selected semantics, visible keyboard focus, wrapping/growing controls and48dp lanes. Final gate1246216zt4 passed733 JVM tests and full integration, including actual Compose semantic-node execution. The preceding expected source-string failure is retained separately. Original action inventory38/42 remains unchanged. Neither host node execution nor compilation accepts actual TalkBack, key dispatch, narrow/large-font pixels or nested callback order.

Appearance855ae09 passed722 JVM tests including64,350 exact legacy override combinations. It preserves actual authored palettes/styles and raw missing/sentinel/unknown provenance. No running migration or editor is claimed.

Rose and Maple released all holds and stopped. Seedling reviews full R17 round3 at a0d3c12 through14:00. Evergreen reviews full settings round2 at a38f39e through14:12. Both use approved Astra/high, immutable source and no target execution. Root owns mutable source and integration. Host-only ADB/USB inventory at13:51 still found no ASUS. No phone action occurred. S25 remains undisturbed. Fifteen complete-outcome milestones remain open.

## 2026-09-08 14:02UTC: signal review custody and appearance core ownership

- Seedling completed full R17 round3 at pinned a0d3c128fe65bceb3302f70e50a6d7fcaecf59ca/shared0ffd658. Score8/10, no new material source defect. Original7/7 reports remain unchanged. Retained report SHA25696f51d29b6c0c0aa3068d48a33fa9800a8463eec9f06f9a8c70af725742f1c2f is byte-identical in critiques/section-08-round-03.md, committed10f10a5. All55 snapshot hashes and42 reviewed production/test comparisons against the inherited716 gate passed. No behavioral tests or device actions ran in the review. Worker stopped after custody. Stop corrective full review rounds at this threshold.
- The same commit records section-10-document-contract.md before pure codec implementation. Palmtree session session_palmtree_1788876103807_22fb83cfa33456e3 uses confirmed Astra/high and owns only new AppearanceDocument/Codec/Collection, their new tests and a separate handoff. Explicit release deadline14:31UTC. No Gradle/device/Git/Python/shared actions permitted. Root retains all runtime integration and full-gate ownership.
- Evergreen remains the other worker, read-only full settings round2 at a38f39e through14:12. No active build. Full live-tree gates wait for appearance source release. Latest full gate remains736 JVM/126 native/three offscreen GPU, not installed. Fifteen outcome milestones remain incomplete. Latest host inventory13:51 showed no ASUS. S25 stays undisturbed.

### Authoritative continuation checkpoint,15:04UTC

This append supersedes the early section matrix and historical writer/build statements above. It does not rewrite their evidence.

| Section | Current evidence and remaining outcome |
| --- | --- |
| 1 | Accepted9/10, original boundary history preserved. |
| 2–3 | Original root stereo, SoundCloud, acoustic latency and mixer remain unaccepted. Historical16kmono feasibility remains a limited real result. S25 undisturbed. |
| 4 | HUD source review8 and callback addendum retained. Device transparency/touch/surface continuity remain open. |
| 5 | HOLD epochs, committed images and application-present acknowledgement gated. Original scores6/7/7 and narrow addenda remain separate. Source age, Android callbacks and scanout remain open. |
| 6 | Six colors/randomization implemented and gated, original7/7 plus narrow correction8. Real persistence/UI/track handoff remain open. |
| 7 | Instrument source review7/7/7/8, stop full corrective reviews. Recovery changes gated. Real SAF/storage/JNI/UI acceptance open. |
| 8 | Signal source reviews7/7/8. Real D1–D10 Android observations remain open. Unavailable measurements remain labeled. |
| 9 | Settings original7/7.803JVM gate covers focus/core changes. Exact Compose queue audit retained5d4d19d. Child exclusion d8760e5 passes54 pure checks, not Android input. Two bounded addenda are not extra full rounds. |
| 10 | Core/migration7fdab21 passed803JVM/126native/3offscreenGPU/lint/dualAPK/engine/boundary and unchanged source inventories. Ant reports116 pure runtime/editor tests and syntax checks, still awaiting explicit source release before Android integration. |
| 11 | cca1e8d implements26 indexed chapters/responses, bounded search/history and revised turtle with preserved discovery/root/link callbacks.12 actual pure/source-linked checks passed. Android/pixels/navigation and independent review open. |
| 12–14 | HDR, window brightness and launch coordinator remain pending. Manual labels them planned. |
| 15 | No new install. Five-cycle, soak, exact reviewed dualAPK freeze, restoration and hardware acceptance remain open. |

Latest full gate5457814owf is working-source integration evidence, not final clean reviewed acceptance. Its exact APK pair was copied before another build and SHA-256 reverified at15:02UTC: app27fedae3277a1370df3fde0fdd3dbbafa51c677ad3d1e1dab8e088696cc48f6c, androidTest82c7b7eac2d06772002ba72dcb161c56a960e1c8597ef6b49306dba2456bf466. Private prefix `appearance-core-migration-r01` retains the pair, runner, inventories, logs and JVM archive. No device action occurred. Last14:13 host inventory had no ASUS. Ant is the only live writer, deadline15:10UTC. No Gradle starts before explicit release.

## 2026-09-08 15:45 UTC continuation

- Combined appearance/manual/settings gate405519lhyk passed871 JVM,126 native and3 offscreen GPU cases, Android compilation/lint/dual APK/source checks. Integrated485bc9f. Exact receipt: section-10-11-integration.md. No install.
- Developer CLI, release provenance and packaged boundary fixture suites7293643sv6 passed. These are fixtures, not real installation/signing/release acceptance. Both retained debug artifact hashes stayed unchanged. Logs: `expansion-1519-{pm3,release-fixtures,boundary-fixtures}.log` in the private task directory.
- Independent Settings round3 at485bc9f is7/10, original7/7 unchanged. Report retained byte-identically as section-09-round-03.md, SHA2566de3739c16ebffb4b6e4f71936a54d8a39a562212292c98d05fef3264892b96b. Terminal-only reversal correction06 passed58 host cases196243bjpx; combined gate remains pending.
- Independent appearance round1 at485bc9f is7/10. Report retained byte-identically as section-10-round-01.md, SHA256508f838b8c795cea9a480955f0be9737bb901f852dbe47cbae873ebe10ab6e6b. Cat, Astra/high, owns only the four finite correction seams through16:15 UTC. Chicken independently reviews immutable manual SECTION11 through16:06. No Gradle during the writer window.
- Ben requested root LOW and workers HIGH at15:14, then300K compression at15:39. Native threshold changed200000 to300000 in Jcode config and the harness confirmed live application. Model routes were not changed.
- Brightness contract is pre-code only. HDR, startup coordinator, mixing and final device acceptance remain open. Last host inventory15:14 found no ASUS. S25 remains undisturbed.

## 2026-09-09 01:33 UTC recovery and integration

The session resumed with mobile82f3f98 and Cat's uncommitted released files intact. The old worker was no longer in the active swarm. Its immutable report explicitly released source at2026-09-08T16:00:24Z; all private manifest and18 live-source hashes verified before using the build slot. No elapsed-time assumption substituted for release.

Gate267547pfgo failed one of891 JVM tests: the stage geometry source assertion still searched for old unscaled padding. Root verified actual report-before-gutters-and-inner-padding ordering, retained the failed XML archive, and strengthened the assertion to require all markers and ordering. Full gate409985gj6g then passed891 JVM/126 native/3offscreen GPU plus lint, dual APK, engine/helper/boundary/source inventories. Exact retained hashes are in section-10-correction-02.md. Working-source success is not finalcleanreviewfreeze or device acceptance. Commit4f285a6 integrates the corrections and original manual round1 report unchanged.

Chick, Astra/high, now owns only manual F1-F5 correction paths and the narrow browser error adapter until02:03UTC. Chipmunk independently reviews immutable4f285a6 full appearance ROUND2 until01:59UTC. No Gradle during writer ownership. Root remainsLOW. Device state has not been probed or changed in this continuation. Fifteen outcome milestones remain incomplete, and source/build success is not hardware acceptance.

Host-only device inventory at2026-09-09 01:35UTC still lists only the S25 wireless transport. USB inventory has no ASUS/Android handset. No shell, UI, connection restart or setting command was sent to either phone. ASUS device acceptance remains unavailable, not attempted through the excluded S25.

## 2026-09-09 04:16 UTC: session recovery and missing handoff completed

Current implementation is `2997afe1aa0ff4a4b230ae4675a7532b51ea9d42`, shared engine `0ffd658d7f19e68180c2720e0500b23644619e90`. Both trees were clean before documentation recovery. Released manual and pause-label corrections are committed, along with the HDR source handoff. Historical worker deadlines above are not current ownership.

The [current handoff](../../../HANDOFF.md) records all remaining outcomes and supersedes stale current-state descriptions. Gate `339523o4jw` is inherited evidence for 901 JVM/126 native/three GPU tests and full Android integration. Recovery rehashed the retained app APK, companion APK, and JVM archive against the committed manual receipt. All matched. No tests, builds, device operations, or installation were rerun by recovery.

Ben's03:16 UTC direction defers root capture from the next release and requires an honest stub. That implementation and its plan/spec/manual changes remain pending. His requested Grok4.6/max verifier requires authenticated catalog and effort verification first. No new worker ran.

The original session parses with3,899 messages. Repeated OpenAI transport failures and automatic todo retries explain its stalled tail. A fresh minimal OAuth probe now passes, but the original large request was not retried. The unchanged session has a hash-matched private backup. Its encrypted OpenAI compaction has no plaintext summary, so the repository handoff is the portable continuation entry.

## Prime continuation, 2026-09-09

Ben requested continuation from Parrot and Seedling after Jcode stalled.
The source checkpoint is clean `9b99c5e`, implementation `2997afe`.
Prime owns integration, full builds, Git, and device work. One bounded writer owns
only the deferred-root stub and its contracts/tests. At most two workers may run.
Ben confirmed Astra medium for implementation and Grok high for independent review.
Acceptance is strictly above 8/10, with at most four full rounds. Preserve original
scores and reports. This supersedes older threshold and routing instructions.

Ben reports the ASUS Zenfone 9 is connected and dedicated to non-root testing.
This entry records his authorization, not a fresh device inventory check.
The coordinator alone operates it. Keep MUSIC at or below 1/30 and PC audio silent.
Leave the S25 undisturbed. Existing system/vendor/boot/vbmeta and publication limits hold.

The next-release root contract is updated before runtime edits. The stub must not
request authorization or capture, including through legacy saved enablement.
Standard capture remains available. Preserve controlled debug research and receipts.
No build, device acceptance, independent review, or installation is claimed here.

The bounded stub implementation passed 26 focused JVM tests on actual policy/manual
production sources, including five deferred-root tests and 21 manual tests.
Evidence: `dev/scratch/root-stub-20260909/{command.txt,compile.log,tests.log}`.
Cached Kotlin 2.4.10 and JUnit 4.13.2 ran through the project JDK, without Gradle.
The existing ManualNavigation copy-visibility warning remains. Android/UI files were
not compiled by this focused check. Full integration and Grok review belong to Prime.

The coordinator subsequently confirmed ASUS serial `NAAIB70036673ZC`, model
`ASUS_AI2202`, installed debug package, and speaker MUSIC 1/30 without device changes.
This is a coordinator observation, not a device check performed by the writer.

### Deferred-root correction after Grok round 1

Grok round 1 scored 8/10. The strictly-above-8 gate did not accept it.
The immutable report remains `dev/scratch/root-stub-20260909/grok-round-01.md`.
F1: SRC operational root controls now require product availability, independently
of legacy flags or stale root status. Ordinary SRC copy states the deferral.
F2: deferred disable returns before preference, revision, message, or cleanup changes.
F3: the manual API and its caller no longer carry root state or operational callbacks.
Controlled debug research, standard capture, and retained helper code are unchanged.

The correction passed 29 focused JVM tests: eight deferred-root policy/source tests
and 21 manual tests. Commands and logs use `command-r2.txt`, `compile-r2.log`, and
`tests-r2.log` in the same evidence directory. Android adapters are source assertions,
not instantiated Android tests. Full gate and independent round 2 remain pending.


## Prime root-stub ASUS checkpoint, 2026-09-09

Grok rounds 8/10 then 9/10 accept the bounded source stub. 909 JVM and 126 native tests passed. Reviewed APK installed/readback verified on ASUS; local preview, real standard-consent path and legacy-root inertness observed. Preferences restored exactly; ASUS idle at MUSIC 1/30. See [full receipt and limits](root-stub-asus-checkpoint.md). R06 is next; no full-expansion or release acceptance.

## R06 foreground brightness implementation, 2026-09-09

Prime assigned the sole bounded writer after deferred-root acceptance at `b222818`.
Before code, section13 fixed `pin_screen_brightness` as Boolean, default off, schema /2 only.
The pin requires a started, resumed, focused, current Activity outside PiP/HUD/destruction.
The Activity event adapter writes 1.0/-1.0 only when window attributes differ.
The existing source-wake ticker remains brightness-write-free. Its original wake decision
is combined with the active pin. No source, global setting, service, root or engine changed.
Local disable applies before persistence. A saving failure remains visible with a retry.
Imports retain omitted keys and activate this window choice only after valid persistence.
The existing-style settings control and manual distinguish requests from measured luminance.

Ben corrected the review threshold to at least 8/10, with four full rounds maximum.
That supersedes earlier strictly-above-8 entries without changing their original reports.
Grok now reviews code quality, not visual design. This unit does not redesign the UI.
Ben assigns visual choices to Muse1.3-contributor/xhigh and exact DeepSeek v4.1 Flash/xhigh,
subject to route verification. Astra medium/high owns 3D. Playback controls and the
AOSP4.4 KitKat character remain protected by the current design direction.

Focused check: 67 JVM tests passed across ForegroundBrightnessPolicyTest (6),
SettingsArchiveTest (40), and ManualContentTest (21). Actual changed policy, archive,
and manual sources compiled with cached Kotlin2.4.10 using the project JDK.
Unchanged dependencies came from the prior coordinator-compiled debug classes and
cached jars. No mock Android framework, Gradle build or device check ran in this unit.
Lifecycle/UI checks are source assertions plus pure policy schedules, not Android execution.
The first run correctly skipped an invalid new key in a legacy-schema fixture.
The test was corrected to use a valid-checksum schema /2 fixture. All original logs remain.
Evidence: `dev/scratch/brightness-pin-20260909/{command-r2.txt,compile-r2.log,tests-r2.log}`.
The existing ManualNavigation copy-visibility warning remains. Full Android integration,
Grok review and ASUS window/global-state restoration checks belong to the coordinator.

### R06 adapter correction before review attempt 2

The coordinator Android gate failed: MainActivity.setPinScreenBrightness overrode
nothing. The writer had added only SheetActions, not ScopeActions or its real adapter.
Grok released attempt 1 as build-blocked and unscored. That report is preserved.
ScopeActions now declares the setter. SheetActions requires it without a no-op default.
PhosphorScreen's actual SheetActions object delegates directly to its ScopeActions host.
The Android compiler must now check both implementations. A source-linked regression
checks the declarations and real delegation, but does not replace compiler/device proof.

The corrected source passed 68 focused tests and git diff --check. Evidence uses
`command-r3.txt`, `compile-r3.log`, `tests-r3.log`, and `source-r2.sha256` in the R06
receipt directory. The earlier command/log/freeze remain unchanged. The full Android
rerun, Grok attempt 2, and actual ASUS UI/window check remain coordinator-owned.

### R06 SAF check exposed cross-runtime archive canonicalization

The ASUS rejected a host-native Boolean archive with checksum_mismatch, despite
matching transferred bytes. Its own export reimported successfully. Native reflection
showed JSON-java omitted the schema slash escape while Android included it.
The Android-export checksum was reproduced exactly by the slash-corrected canonical
bytes. The coordinator's diagnostic OFF import then passed and released brightness.
This was a pre-existing checksum algorithm portability defect, not an R06 Boolean error.
Original diagnostics and fixtures remain in `canonical-probe/` and the R06 receipt root.

SettingsArchive checksum strings now use explicit Android JSONStringer quoting.
The Android10/API29 and Android14 reference string bodies are identical; Android13
was also inspected. Quote, slash, backslash, short controls and lowercase other-control
escapes match. Non-control Unicode stays unchanged. The parser, output serializer,
number normalization and strict checksum comparison are unchanged. No host-hash fallback
was added: prior host-only fixtures were never compatible Android exports.

Focused tests passed 86 cases across policy, manual, settings, light, appearance and
canonicalization suites. Golden vectors cover all32 controls, slash/quote/backslash,
non-ASCII text, supplementary Unicode and U+0085/U+2028/U+2029. Fixed independently
hashed minimal fixtures preserve actual ASUS metadata and the diagnostic OFF checksum.
Existing type-validation fixture builders now use the deterministic quote spelling;
the fixed golden vectors, rather than those builders, independently test checksum bytes.
The initial run caught an expected-vector typo for form feed; the Android-compatible
production output was correct and the vector now expects the short escape.

Evidence: `dev/scratch/brightness-pin-20260909/canonical-repair/` retains command-r2.txt,
compile-r2.log and tests-r2.log. Current native production is compiled from source,
with unchanged prior Android-built dependency classes. No Gradle/device action ran
in this correction. Parent owns full integration and Grok attempt3 of4.
The earlier source-review8 remains historical, not acceptance of this correction.

The repaired native decoder also accepted the exact full ASUS export: its computed
SHA matched stored `ba75b1f9732926475ffd027ba9bd50e7a8dffdcb89f527cb8c30feab73b16905`.
Only hashes and the pin Boolean were logged, not appearance content.
New native-exported ON/OFF fixtures are under `canonical-repair/fixtures/`; original
incompatible host fixtures remain unchanged for the regression record.


## Prime R06 ASUS checkpoint,23:36 UTC

Final921-test/lint/dualAPK gate and Grok attempt3=8 passed, preserving attempt2=8 and blocked attempt1. Deterministic Android checksum repair passed actual SAF on/off imports. Brightness lifecycle,PiP/HUD and restoration observed on ASUS. Final preferences restored exactly, app idle,pin off,MUSIC1/30. See [receipt and remaining limits](brightness-asus-checkpoint.md). Next U1 tactile console; full remaining baseline remains active.


### U1 opt-in tactile console

Source contract: `docs/plans/mobile-expansion/visual-u1-contract.md`.
Implement an authored optional look_version=2 and console-only no-track key bed.
Version1 omits the field exactly. Saved looks, transport truth and gestures remain unchanged.
Fresh tactile default is deferred until a preinit meaningful-settings baseline is proven.
Parent owns full builds, device evidence and acceptance. No visual acceptance is implied.


U1 implementation receipt (2026-09-09): authored lookVersion defaults1. Only integer2
is accepted as the optional look_version wire field. Legacy omitted-field output matches
four hashes captured from the prior compiled codec and the existing independent vectors.
Curated/legacy records and fresh defaults remain unchanged. The AppearanceEditor draft
has a CONSOLE KEYS toggle, using the existing preview/apply/save owners.

RoomStyle retains the choice through coupled FEEL edits. No-track-only Console branching
leaves the original row and transport callbacks intact. The separate TactileConsole surface
has opaque authored-only tokens, fixed Canvas glyphs, state-truth selection, press sink,
focus ring and disabled rendering. Capture-unavailable controls remain omitted under the
original gate; no extra unavailable action was invented. S9 keeps overflowHandleGesture.
It gains semantic/keyboard activation without adding another pointer handler.

The well and faces are opaque, including Glass, so token ratios use their actual backgrounds.
The author's ink remains unchanged when it passes. The shared contrast palette is not rewritten.
AMOLED well is0 and raised face141414. There is no edge flash, timer, beam sampler or new
visibility writer. Existing burn-in walk and the original console swipe remain in their owners.

Focused native compilation uses the cached Kotlin Compose plugin and Android36 APIs.
The broader208-case native run passed before the final narrow wrapping/import assertions.
Early test harness repairs used AppearanceException.code and added the cached AndroidX
collection-jvm dependency. Original failing logs remain. Current-source final test results
and dependency/source hashes live under `dev/scratch/visual-u1-20260909/`.
The existing ManualNavigation constructor-copy warning remains unchanged.

R06 brightness manual wording now names the bounded ASUS focus/Home/PiP/HUD/restart/import
checks and leaves other devices and physical luminance unverified. No R06 runtime changed.
Fresh tactile default remains an explicit deferred seam, not inferred from missing appearance_state.
No Gradle, Git, device, engine, title, seek, sheet or source-authority action ran in U1.
Parent owns full compilation, screenshots, real layout/gestures and independent acceptance.

Final focused U1 run:208 tests passed, including the final width policy and legacy-import
preservation assertions. Exact command: visual-u1-20260909/tests-command-r4.txt.
Logs: test-compile-r4.log and tests-r4.log. Source is released for the parent full gate.


U1 visual round1 correction: Muse5/10 remains unchanged, separate from Grok source8.
The compactness defect came from applying340dp to the inner well rather than screen width.
Current ASUS inner306dp now selects one56/64/56/48dp row. A320dp window's inner234dp
selects two rows. Font2 selects full-width rows with no height ceiling.
Fixed expected row/width vectors cover306/234dp at font1/1.3/2 and HOLD text at1.3.

Source inspection found no app density override. The old internal4dp focus gutters explain
why48dp layout targets looked40dp tall. The corrected silhouette fills its measured hit layout.
New-keybed density comes from the Android view resources. The existing font-scale converter
is preserved, including Android14 nonlinear scaling. No global/legacy density changes occur.
At440dpi,48dp is132px and56dp is154px, independently asserted through native Compose units.
Only tactile-console vertical padding changes to8dp, yielding a nominal86dp compact card.
This is a geometry prediction; parent device evidence must verify actual bounds.

Raised keys now have a continuous1dp outer contrast-safe silhouette and no uniformly bright
inner border. Top/left get the high step and bottom/right the low step. The high step exceeds
the AMOLED outline. Press/selection intentionally reverse polarity; labels and authored colors
remain unchanged. Exterior focus rings have a1dp gap and reserved well/inter-key space.

Baseline TODO: font2 shared status-band overlap/truncation remains outside U1. Muse's addendum
records the src/mode collision. Confirm against a baseline font2 shot before a later correction.
Do not infer a gain bug from temporary gesture-driven screenshot state.
Evidence remains in visual-u1-20260909/visual-r2-correction. Prior screenshots/logs/freeze unchanged.

Final native correction run:210 tests passed, including actual Compose source compilation.
Exact command/log: visual-r2-correction/command-r2.txt, compile-r2.log, tests-r2.log.
The shared status TODO is also indexed in docs/SERIOUS-TODOS.md. Parent owns re-shoot/acceptance.


U1 next visual correction batch: Muse round2 remains7FAIL; DeepSeek independent round2
remains8 bounded/provisional. AddendaC/D and DeepSeek D2/D3 informed this batch only.
The four resting key geometries and glyphs remain approved evidence, not a new acceptance.

SIGNAL CHECK now resolves a local ink against native black rather than a light room's ink.
All four family colors pass4.5 against that plot in unit checks, without changing stored colors.
The shared status band measures actual heading widths using TextMeasurer, the current font
resolver and scale. Overwide headings stack; data wraps rather than disappears. The stage
SIGNAL CHECK action is placed below the measured band height with8dp clearance. Data selection,
source actions, band/HUD visibility and audio state are unchanged. Font2 status screenshots are
still required: pure measured-width tests are not Android font or pixel evidence.

Focus now keeps the authored accent when it passes4.6 against the well; otherwise it derives
a local color. The2dp source ring had met the old3dp gutter and tray stroke, explaining partial
overpaint. The gutter is5dp; new-only exterior console padding shrinks2dp on each axis to retain
key positions and card footprint. The ring has room for its full stroke and a distinct plane gap.
No change was made to hit bounds, core outlines, glyphs or the approved resting bevel.

Designators move to the3dp top-left corner. A custom measured layout centers the main legend
independently. It grows a key only when measured designator/main boxes would overlap; full-width
font2 rows therefore do not grow merely because a small designator exists. Normal/1.3/2 box
vectors test centering and separation. Actual Light screenshots remain parent-owned evidence.

D4 inherited loud outer frame is deferred as a baseline hierarchy issue. D1 dark/glass mean
brightness concerns the metric definition, not permission to repaint authored families.
Minor D5-D9 received no speculative polishing. Source/visual scores and prior freezes remain intact.

Final focused batch:214 tests passed with actual modified Compose sources, including
PhosphorScreen and measured key/status layouts. Evidence: visual-r3-correction/command-r3.txt,
compile-r3.log, tests-r3.log. Earlier failures were stale supplementary source-string assertions,
updated to assert the new wrapping and unchanged visibility/padding fallbacks. Logs remain intact.
Source is released for parent full gate and visual round3 of4. No device acceptance is claimed.


## U1 bounded visual acceptance,2026-09-10 00:49UTC

Code8/8/8, Muse5/7/8 and DeepSeekround2/3=8/8 preserve history.933-test/lint/dualAPK gate and real ASUS four-theme/focus/Lightlargefont/320dp evidence pass boundedU1. Exact prefs restored,app idle,font1,wmreset,owned Downloadfixtures removed. [Receipt and still-open full-baseline gates](visual-u1-asus-checkpoint.md). Next R09 microphone/service/composite vertical unit.

## R09 standard-only implementation admission, 2026-09-10

Coordinator assigned sole app writer after U1 commit7204b91. [R09 contract](section-03-r09-contract.md) ratifies service-owned mic, explicit routes and one bounded standard-only visualization mixer. Source implementation and focused tests are in progress. No Gradle, Git or device actions by the writer. Coordinator owns full gates, review and ASUS acceptance. S25 excluded, root deferred. No R09 acceptance claimed.


## R09 parent gate, 2026-09-10 ~02:35UTC

Dirty unreleased tree: 945 JVM tests, 0 fail/error/skip; lintDebug; assembleDebug; assembleDebugAndroidTest; checkEngine. APK SHA256 `d466bd0e471ae942d20efbdd6b571d83de8056ce1aa6521f5fed15b3c27b587f`. Independent Grok OAuth code review attempt 1 running. Not committed, not device-accepted. Mix offer uses pre-read epoch. HUD pending-only. Manual mix chapter is route-dependent, not Planned.


## Lifecycle cycle 1, 2026-09-10 ~04:32UTC

APK `4de177797c38cbcda07f3a4c9ab4696eb77b241f0262e7f1cd9fcff7726409bd` (HEAD 1921b38) on NAAIB70036673ZC. Start, Home, return, `am start -S` recreate, force-stop. PlaybackService bound while visible. No Phosphor services after stop. Prefs restored byte-for-byte. MUSIC 1/30. Not five cycles. Not a 30-minute soak.


## Lifecycle cycles 2–5, 2026-09-10 ~04:36UTC

Same installed APK as cycle 1 on NAAIB70036673ZC. Each cycle: start, Home, return, `am start -S` recreate. Then force-stop. No Phosphor services after stop. Prefs restored byte-for-byte. MUSIC 1/30. Five cycles now recorded. Not a 30-minute soak. Not accessory/PiP/HDR-nits proof.


## Soak aborted, 2026-09-10 ~04:41UTC

Ben: do not soak. Stopped after ~4.5 min wall-clock. Force-stop. Prefs restored. MUSIC 1/30. Do not resume idle soak.


## R14 ASUS matrix, 2026-09-10 ~04:45UTC

See [r14-asus-checkpoint.md](r14-asus-checkpoint.md). Last-used is not default. Confirmed mic auto-starts. Capture not prompt-free. Prefs restored. Rotation analogue not proven. Soak cancelled.


## R09 ASUS PiP identity, 2026-09-10 ~04:47UTC

See [r09-asus-pip-checkpoint.md](r09-asus-pip-checkpoint.md). MicCaptureService identity held in PiP. Mix/accessories/HUD/linger/SRC-row honesty not proven. Prefs restored.


## R09 PiP segs rebind, 2026-09-10 ~05:07UTC

See [r09-asus-pip-rebind-checkpoint.md](r09-asus-pip-rebind-checkpoint.md). Native 480 segs after PiP return. Prefs restored. Mix/accessories still open.
