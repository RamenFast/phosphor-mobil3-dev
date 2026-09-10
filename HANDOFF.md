# Handoff: mobile expansion execution

## Latest Prime checkpoint, 2026-09-10 ~08:00 UTC

Sheet B1 source-accepted: Muse code **9/10**. Vector close, checked tick, inline search clear. 956 tests. Device pixels not yet shot. Prefs still baseline.

## Latest Prime checkpoint, 2026-09-10 ~07:40 UTC

Tactile track console look 2 Muse visual **9/10 PASS**. Round 1 7 was look 1 FlatKey. Skip vectors at PLAY weight. Prefs restored. See [round 2](docs/plans/mobile-expansion/critiques/tactile-track-muse-visual-round-02.md). Next: sheet/manual hierarchy.

## Latest Prime checkpoint, 2026-09-10 ~07:25 UTC

Muse track visual 7/10 was look 1 FlatKey skip marks, not tactile vectors. Baseline omits `look_version`. Key-face `drawConsoleVector` is in source for look 2. Device F1 needs a look-2 shot. Prefs restored. See [F1 checkpoint](docs/plans/mobile-expansion/tactile-track-f1-asus-checkpoint.md).

## Previous Prime checkpoint, 2026-09-10 ~07:10 UTC

Tactile track Muse visual round 1 **7/10** (F1 prev/next weight). Skip-pair source `15742d2`/`4bb2c0f`. ASUS pixels still tiny skip marks after install; PREV label probe did not appear. F1 not closed. Prefs restored. See [F1 checkpoint](docs/plans/mobile-expansion/tactile-track-f1-asus-checkpoint.md). Do not soak.

## Latest Prime checkpoint, 2026-09-10 ~07:40 UTC

Mic-label source+ASUS: colliding built-in SRC rows suffix `(id)` on display only. Grok 7 then Muse 9. 956 JVM tests, lint, dual APK. Installed `08923e33…`. Bottom (19) and back (21) both start `MicCaptureService` at 48 kHz mono. Stored key unsuffixed. Prefs restored, RECORD_AUDIO revoked. See [mic-label ASUS checkpoint](docs/plans/mobile-expansion/mic-label-asus-checkpoint.md). Accessories/mix/HUD/linger still open. Do not soak.

## Latest Prime checkpoint, 2026-09-10 ~04:45 UTC

R14 ASUS matrix: last-used is not default. Unconfirmed default mic inert. Confirmed mic + RECORD_AUDIO auto-starts on fresh launch and process-death. Capture with popup off does not start MediaProjection. Prefs restored, RECORD_AUDIO revoked. CLEAR_TASK in-process is not rotation and stopped mic. See [R14 ASUS checkpoint](docs/plans/mobile-expansion/r14-asus-checkpoint.md). Soak cancelled. Do not soak.

## Previous Prime checkpoint, 2026-09-10 ~04:27 UTC

Fresh tactile default source-accepted: empty install AMOLED look_version 2. Upgrade-like prefs stay look 1. Grok OAuth 8/10. Existing appearance bytes unchanged.

## Previous Prime checkpoint, 2026-09-10 ~04:17 UTC

R14 startup source-accepted: Grok OAuth 7 then 8. Default none. Last-used is not a default. See [R14 checkpoint](docs/plans/mobile-expansion/r14-source-checkpoint.md). Device launch matrix unproven.

## Previous Prime checkpoint, 2026-09-10 ~03:37 UTC

HDR request path source-accepted: Grok OAuth 7 then 8. Request default off. Format change rebuilds composite/HOLD. No active-HDR claim. Sibling render-gpu `3171e0f`. See [R05 source checkpoint](docs/plans/mobile-expansion/r05-source-checkpoint.md). Device dataspace/nits unproven.

R09 source remains `8bed59a` with bounded ASUS built-in mic.

## Previous Prime checkpoint, 2026-09-10 ~02:58 UTC

R09 source accepted: 946 JVM tests, lint, dual APK, checkEngine. Grok OAuth code 7 then 8. F1 epoch rewind closed. APK `4e436e263443c1d9b4f4ef572b7f08251b7f93b6e1971ad60708e6008debcfd8`. See [R09 source checkpoint](docs/plans/mobile-expansion/r09-source-checkpoint.md). Not device-accepted. Next: bounded ASUS mic/route/mix evidence, then HDR from `dev/scratch/r05-current-handoff.md`.

GPT-6 later review remains `GPT6-REVIEW-LATER.md`. Grok only via grok-oauth, never OpenRouter.

## Previous Prime checkpoint, 2026-09-10 ~02:10 UTC

GPT-6 usage exhausted mid-R09. Later Astra review brief is top-level
`GPT6-REVIEW-LATER.md`. Temporary implementation/3D owner is Grok 4.6 high.
Muse/DeepSeek still own UI. Grok still scores code only.

HEAD remains `7204b91` (accepted bounded U1). R09 is dirty, unreleased, and
not accepted. Astra writer `astra-r09-grounding` went idle mid-edit. HDR
research is released at `dev/scratch/r05-current-handoff.md` and must wait for
R09 source release. ASUS `NAAIB70036673ZC` is idle: font 1.0, physical
1080x2400, MUSIC 1/30, no Phosphor services listed.

Live remaining work: finish R09, HDR, startup, remaining visual/3D,
fresh tactile default, full ASUS regression and soak. Root stays deferred.
Heartbeat stays on.

## Previous Prime checkpoint,2026-09-10 00:49UTC

U1 tactile no-track console now has bounded source and visual acceptance:933 JVM tests/lint/dualAPK pass; Grokcode8/8/8,Musevisual5/7/8,DeepSeekvisualround2/3=8/8. FinalAPK installed/readback tested on ASUS; four themes/focus/largefont/narrow-window evidence retained. Original preferences restored exactly,app idle,font1,wmreset,owned fixtures removed. See [U1 receipt and remaining full-baseline work](docs/plans/mobile-expansion/visual-u1-asus-checkpoint.md). R06+checksum is b74beb2 and rootstub b222818.

Next R09 service-owned microphone/accessory routing and standard-only visualization mixer, grounded handoff `dev/scratch/r09-current-handoff.md`. Root stays deferred. Remaining HDR,startup,full visual craft/fresh-look default,regression/soak are NOT complete. Jcode fresh text/tool paths are repaired and verified on immutable d4b6aab0 build; old huge-history transport remains unproven. Persistent goal87579191 and owned heartbeat continue until the full requested baseline is truly complete.

## Current Prime checkpoint,2026-09-09 23:42 UTC

R06 brightness and deterministic Android-compatible archive checksums are committed `b74beb2`.921 JVM tests/lint/dualAPK passed; Grok code attempts blocked/8/8 preserved. Exact final APK is installed/readback verified on ASUS; lifecycle/PiP/HUD and repaired SAF on/off imports passed bounded checks. Preferences restored exactly,app idle,pin off,MUSIC1/30. See [R06 receipt and limits](docs/plans/mobile-expansion/brightness-asus-checkpoint.md).

Astra medium is sole U1 writer: tactile vector no-track console with opt-in versioned look, preserving saved appearance bytes and playback/gesture truth. Independent Muse direct-high and DeepSeek v4.1-flash xhigh design briefs plus source corrections are retained in ignored `dev/scratch/design-20260909/`; Grok reviews code only. Current pass threshold >=8/10,max4 attempts. Root owns builds/Git/ASUS. Complete polished baseline goal87579191 remains active; remaining mic/mixer,HDR,startup,visual implementation and full regression/soak are not complete. Jcode isolated null-bool candidate passes38 tests and builds; independent review pending,live unchanged. Its built-in maintainer feedback is queued,not a confirmed delivery. Heartbeat remains on until everything is truly done.

## Prime continuation, 2026-09-09 22:49 UTC

This checkpoint supersedes the older pending-stub and unavailable-ASUS claims below. The honest deferred-root stub is implemented, reviewed by Grok 4.6 high (8/10 then 9/10), installed and readback-verified on dedicated non-root ASUS NAAIB70036673ZC. Astra medium writes; current acceptance is strictly above 8, max four rounds. 909 JVM tests, lint/dual APKs and 126 native tests passed. Real preview open/close, Android standard-capture consent and legacy-root inertness passed bounded checks. Preferences restored byte-for-byte, app stopped, MUSIC 1/30. S25 remains excluded. See [receipt and exact limits](docs/plans/mobile-expansion/root-stub-asus-checkpoint.md).

Next: R06 foreground-only brightness pin under its existing contract, then remaining expansion outcomes. Jcode is not fixed: short HTTPS text works, but BashTool null Boolean parsing fails and large-history transport cause remains open. No push, signing, publication, root-audio or full expansion acceptance. Build backup: `/media/ben/Mass storage/agenticTinkering/claude/phosphor-mobile/root-stub-20260909/`.

## Start here: recovered checkpoint, 2026-09-09 04:16 UTC

This checkpoint supersedes conflicting current-state and worker-ownership claims below.
It is a continuation receipt, not a replacement expansion plan or a new acceptance score.
Ben requested this comprehensive repository handoff at 03:29 UTC because the original chat was failing.

### Current source and last successful work

- Mobile implementation: `2997afe1aa0ff4a4b230ae4675a7532b51ea9d42`, `fix: complete practical manual and readable pause status`.
- Shared engine: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Both working trees were clean before this documentation-only recovery. No implementation, device, or shared-engine change occurred during recovery.
- The last successful original-session tool committed the manual, readable pause status, correction receipts, and HDR research at 03:40:58 UTC.
- Original session: `session_parrot_1788825216343_301349c073c5faad`. Its saved JSON parses and contains 3,899 messages.
- Subsequent OpenAI requests repeatedly hit WebSocket connection timeouts, 180-second stream inactivity, and request-send failures. Automatic todo continuation then retried.
- A fresh minimal OpenAI OAuth diagnostic passed at approximately 04:14 UTC, including credential refresh and `AUTH_TEST_OK`. The original large request was not replayed. This does not prove the original session is fixed.
- The saved session was left unchanged. Recovery copy: `/home/ben/.jcode/scratch/session-recovery/parrot.original.json`. Both copies matched SHA256 `a4042b3aee7919ab5f0bb3654f53a4721e1dd660a6d3dbe4e0bcd4d324ee2a56`.
- Its compaction covers 3,680 messages and has an OpenAI-encrypted payload with no plaintext summary. A different provider cannot use that encrypted summary. Use this handoff and the repository receipts for continuity rather than assuming a provider switch preserves that context.

### Latest decisions override historical instructions

1. Root capture is deferred from the next release. At 03:16 UTC Ben explicitly requested an encouraging stub button instead.
2. Preserve root research and recovery evidence. Do not treat this deferral as permission to discard it or claim stereo, SoundCloud, or latency acceptance.
3. The current manual still exposes operational root controls. Converting the release surface to the requested honest stub remains work, not a completed change.
4. Keep the S25 undisturbed under the latest device instruction. ASUS was absent at the last recorded host inventory, 01:35 UTC. Recovery did not probe either phone.
5. Preserve the ASUS MUSIC ceiling of 1/30 and silent PC audio. Use an explicit authorized serial for any future device action.
6. System, vendor, boot, and vbmeta writes remain forbidden, including raw-device aliases and both slots. No remount, flash, or bootloader change.
7. No push, publication, signing, store submission, or unrelated relay redesign is authorized by this recovery.

### Provider and worker routing

Ben requested Grok 4.6 with max reasoning for future verification at 03:16 UTC.
That is a preference to fulfill after authentication and exact capability checks, not a verified available route.
At this checkpoint, the Jcode-managed Grok Build backend is installed and xAI device authorization awaits Ben.
The separate `xai` provider uses an API key. `grok-build` uses subscription authentication and ACP.
The inspected Grok backend handles tools internally. Do not assume parity with Jcode's native tools or reasoning-effort controls.

The running shared Jcode server is `7c82a17`, while PATH resolves `88cffeb`.
An installed CLI capability does not prove the running server has reloaded that capability.
Do not restart a shared server with active sessions as a shortcut.
Verify authentication, advertised model IDs, supported effort, a short response, and tool behavior before assigning review work.
No worker was launched during this recovery. Historical worker ownership windows below are not current leases.
A new root session follows the current once-per-session routing ritual. Do not reuse expired ownership or assume a prior session waived that ritual.
Use at most two live workers and one implementation writer. The coordinator owns the sole full build slot, integration, device actions, and Git.

### Evidence at the current implementation

The [manual integration receipt](docs/plans/mobile-expansion/section-11-correction-01.md) records frozen gate `339523o4jw`:
901 JVM tests across 74 suites, 126 native tests, three offscreen GPU cases, Android lint, both debug APKs, and engine/helper/source-boundary checks.
Complete mobile/shared before-and-after source inventories matched.
These test results are inherited evidence, not rerun by the recovery session.
The retained result JSON explicitly reports `installed=false` and `android_device_acceptance=false`.

Retained prefix: `dev/scratch/mobile-expansion-20260908T001819Z/manual-pause-integration-r2`.
Recovery independently rehashed these three retained files and matched the committed receipt:

| Artifact suffix | SHA256 |
| --- | --- |
| `-app.apk` | `8661645ee51f5b0d6eefdee42ba94433aec7c6b1710b9b67907fef6e47c2e79c` |
| `-androidTest.apk` | `5d2eaaae346d7a7a51345a4d2aef6ef3cc8ce76ce1236396bbb3b8e1be27bf97` |
| `-jvm.tar.gz` | `6b97dda8e4635ff5cc5bfe2c97abab7d4cb39feeafe630e43d0337f6278f84b5` |

This is integrated working-source evidence. It is not a final reviewed release freeze, installation, or Android acceptance.
The manual now has 35 chapters. Its correction receipt distinguishes executed content tests, source assertions, and unobserved Compose/device schedules.

### Complete remaining-outcome map

| Section / requirement | Current result | Remaining action |
| --- | --- | --- |
| 1 / artifact boundary | Accepted source review 9/10. | Preserve boundary and historical evidence during later changes. |
| 2 / R01 root | Limited feasibility and unsuccessful stereo trials retained. | Ship the requested honest stub. Real root stereo, SoundCloud, audibility, and added latency remain deferred and unaccepted. |
| 3 / R09 routing/mixer | Research exists, no accepted mixer. | Finish external/Bluetooth mic selection and visualization-only mixing without presenting deferred root capture as working. Establish format/clock ownership before implementation. |
| 4 / R02 HUD | Source review 8 plus callback addendum, integrated. | Actual transparency, touch, owner handoff, and surface continuity on an authorized target. |
| 5 / R13 HOLD | Epochs, retained images, and application-present acknowledgement integrated. Original 6/7/7 plus narrow addenda retained. | Buffered source age, actual Android callbacks, retained output, and display acceptance. |
| 6 / R07/R08 colors | Six slots and cycle controls integrated. Original 7/7, narrow correction 8. | Actual persistence, UI selection/cycling, and track handoff. |
| 7 / R16 instruments | Full reviews 7/7/7/8, corrections integrated. | Real SAF/storage/native/UI acceptance. Full-review cap reached. |
| 8 / R17 signal check | Full reviews 7/7/8, corrections integrated. | Real D1-D10 Android observations. Preserve unavailable measurements as unavailable. |
| 9 / R10/R11 settings | Full reviews 7/7/7. Terminal reversal correction06 passed focused tests and later full integration. | Fourth and final full review, then actual input/focus/large-font/queue acceptance. |
| 10 / R04/R12 appearance | Full reviews 7/7. Round2 pause-label contrast finding corrected in `2997afe`. | Next full independent review and actual four-theme, migration, authoring, visibility, and lifecycle acceptance. |
| 11 / R03 manual | Original full review 7. Five corrections and 35 chapters integrated in `2997afe`. | Update root help/control behavior for the new stub. Full correction review and navigation/search/IME/accessibility/pixel/link-recovery acceptance. |
| 12 / R05 HDR | Detailed source feasibility handoff committed, not implementation. | Implement genuine negotiated HDR and truthful SDR fallback, then actual compositor/panel evidence. |
| 13 / R06 brightness | Foreground-only contract authored, not implementation. | Integrate persisted default-off window policy, restoration, UI/manual, and foreground/PiP/HUD acceptance. |
| 14 / R14 startup | Explicit coordinator pending. | Implement default-source/permission/lifecycle behavior while keeping deferred root startup unavailable. |
| Critique / R15 | Original reports remain immutable. | Stop full corrective rounds at 8 or after four total. Separate narrow addenda from full rounds. Verify the new requested Grok route before use. |
| 15 / regression/release | Host gates and tooling fixtures passed. No new installation. | Complete requirement-linked regression, five cycles, 30-minute soak, exact reviewed dual-APK freeze, authorized installation, readback, restoration, and cleanup. |

The saved todo list had 15 incomplete outcomes. Some descriptions still named released workers or earlier test counts.
Recreate the outcomes from this map, not stale worker activity. Root capture's release deferral is newer than that list.

### Ordered continuation

1. Read machine governance, relevant skills, `docs/AGENTS.md`, the canonical plan, `spec/EXPANSION.md`, and the execution ledger.
2. Recheck live Git state and ownership. Preserve unrelated changes and all original review reports.
3. Record the root-stub release behavior in the canonical plan and active contracts before changing runtime or manual behavior.
4. Implement and verify that stub without deleting research or claiming capture acceptance.
5. Verify provider capabilities and obtain the required worker routing confirmation before independent reviews.
6. Finish the remaining feature contracts and implementations in small owned units. Keep the single full build slot.
7. Run the missing full settings, appearance, and manual reviews within their remaining budgets. Preserve original scores.
8. Use an authorized available target for actual acceptance. Do not substitute S25 access for the ASUS absence.
9. Freeze exact reviewed source and both APKs. Complete the final regression, installation/readback, restoration, and release gates.

Key next-feature references:
[HDR source handoff](docs/plans/mobile-expansion/section-12-hdr-source-handoff.md),
[brightness contract](docs/plans/mobile-expansion/section-13-brightness-contract.md),
[appearance correction03](docs/plans/mobile-expansion/section-10-correction-03.md),
[appearance round2](docs/plans/mobile-expansion/section-10-round-02.md),
[manual correction/integration](docs/plans/mobile-expansion/section-11-correction-01.md), and
[routing research](docs/plans/mobile-expansion/section-03-routing-research.md).

Use `scripts/env.sh` and the Gradle wrapper for Android builds. Use locked Cargo resolution.
`docs/AGENTS.md` owns command examples, and the retained frozen runner owns the prior full-gate recipe.
Do not reconstruct a successful gate from test counts alone or build while another writer owns the source.

**Blocked outcome:** name the exact absent provider capability, device, approval, or reproducible failure.
Keep the useful source and receipts, record the smallest resolving action, and continue independent authorized work.
Do not turn missing hardware, authentication, or a review budget into fabricated completion.

## Historical checkpoints

**Updated:** 2026-09-08,15:45UTC. **Current status:** Combined gate405519lhyk passed871 JVM/126 native/three offscreen GPU tests, Android compilation/lint/dual APK and engine/source checks. Integrated485bc9f, no installation. Tooling fixture suites7293643sv6 also passed. Settings round3=7 found terminal-only reversal, now corrected with58 actual host tests; Android integration of that correction is pending. Appearance round1=7 found four finite consumer defects, assigned to Cat (Astra/high) through16:15UTC. Chicken independently reviews immutable485bc9f manual through16:06. Both prior reviewers are stopped. No Gradle while Cat owns source. Root is LOW, workers HIGH by Ben's15:14 direction. Last host inventory15:14 found no ASUS. S25 remains undisturbed. Root stereo/SoundCloud/audibility/latency, buffered source age and device acceptance remain open.

**Historical checkpoint below, superseded where the current status differs:** Latest full frozen gate5457814owf passed803 JVM/126 native/three offscreen GPU tests, lint, dual APKs and engine/source boundary checks with unchanged inventories. Both APKs were retained and rehashed before any later build. Subsequent Settings child-input correction d8760e5 passed54 pure tests. Manual cca1e8d adds26 chapters and passed12 pure/source-linked tests. These were subsequently compiled in405519lhyk.

Ben went to bed at09:05 and offered the ASUS for non-root work. Leave the S25 undisturbed. Host-only ADB/USB enumeration at13:51 again found no ASUS connection. Historical ASUS identity is `NAAIB70036673ZC`, model ASUS_AI2202, with MUSIC at1/30 or lower and no increase. These facts are not a fresh device baseline.

Microbe explicitly released fourteen R16 workflow paths at11:05 and stopped. Its86 host tests and source receipt were verified before the coordinator's full gate. Mushroom's R17 audit is verified and retained in [signal observations](docs/plans/mobile-expansion/section-08-signal-observations.md), with a pre-runtime [signal check contract](docs/plans/mobile-expansion/section-08-signal-check-contract.md). Both workers are stopped. Coordinator owns integration, commits, device actions and the sole Gradle slot. No fourth full R13 review has been consumed.

**Current workstreams,15:04UTC:** R16 full reviews7/7/7/8 and R17 full reviews7/7/8 stop at the accepted source-review threshold. Settings originals7/7 remain unchanged. Herb's exact Compose queue audit is retained5d4d19d, and Bat's bounded correction04 investigation is retained byte-identically with d8760e5. Both investigators are stopped. No bounded addendum consumes another full-review round. Palmtree's core/migration is committed7fdab21 and gated. Ant, Astra/high, retains the appearance runtime/editor writer slot until explicit release, due by15:10. It reports116 actual-source host tests and syntax-only checks, not Android integration. The coordinator owns the released Settings/manual paths and commits. No Gradle may run until Ant releases. The next gate combines released runtime, Settings provider/editor hooks and manual, including updated source assertions. New artifacts remain uninstalled. Host inventory14:13 showed no ASUS, and the S25 stays undisturbed.

## Read first

- Current checkpoints: [color/present correction validation](docs/plans/mobile-expansion/section-05-06-correction-validation.md), [color correction02 assessment](docs/plans/mobile-expansion/critiques/section-06-round-02-addendum.md), [application-present source addendum](docs/plans/mobile-expansion/critiques/section-05-correction-04-addendum.md), and [preset core integration](docs/plans/mobile-expansion/section-07-core-validation.md). Original color and R13 review scores remain unchanged in their original reports. The [instrument preset contract](docs/plans/mobile-expansion/section-07-instrument-contract.md) predates implementation. Historical writer/build statements below describe prior windows.

- Start with the canonical [MOBILE-EXPANSION-PLAN.md](MOBILE-EXPANSION-PLAN.md), [active expansion contract](spec/EXPANSION.md), and [single execution ledger](docs/plans/mobile-expansion/EXECUTION.md).
- Ben approved implementation and reversible S25 work at 00:18:19 UTC. This supersedes the prior S25 exclusion for this task. Preserve unrelated ASUS volume, PC-audio, signing, publication, and irreversible-action boundaries.
- Ben's 02:47 UTC boundary forbids writes to `/system`, including `/system/bin`, `/vendor`, and boot/vbmeta partitions through any path. Reads are allowed. No remount, flash or bootloader change. Use only installed app code and owned private data for the helper.
- Installed app is clean `06f84e2eb7da46c758e9f8b388c3537e6c67905d`, APK SHA256 `4a370170cda5410caf8abc82d8cd9a997fd0777c718cbd1ba31c2cd3aa9127fa`. Both app and androidTest APKs were built together and hashed before installation. At05:39 installed readback and signer matched. Preferences remained byte-identical to baseline. The first stereo SYSTEM trial stopped cleanly at its100ms monitor queue bound. Its two-channel initialization is not independent stereo acceptance. See [exact stereo receipts](docs/plans/mobile-expansion/section-02-stereo-trials.md). Historical `ccee7c8` trials below are superseded as installed state only.
- The installed PIE obtains existing KernelSU authority, keeps the mount namespace, reads trusted platform classpaths and loads sealed fd6 DEX. [Earlier a93 feasibility](docs/plans/mobile-expansion/section-02-helper-trials.md) passed three997Hz captures, same-process repeat, actual parent-death cleanup and recovery. Those exact protocol1 receipts do not accept the new product. Physical audibility remains unproven.
- Ben clarified at04:17 and04:21 that true stereo is required and SoundCloud is the smoke test. He reports SoundCloud fails standard capture while Spotify works. Do not accept duplicated mono or generic unsupported labels. Preserve sample-rate fidelity. Existing48/96/192kHz settings are beam reconstruction over48kHz input, not capture-rate negotiation. Report actual source and reconstruction rates separately.
- The protocol2 product uses shared root/standard retirement, actual specialUse FGS, local-only authorization and fixed debug SYSTEM/NONE/TONE checks. Its clean build passed477 Android tests,26 native tests,207 Java assertions, lint and engine checks. Actual SYSTEM failed its PCM assertion, with cleanup/preservation passing. NONE measured80128silentframes, exposing the finite-mode overrun, and passed helper/policy/service cleanup. Its overall guard failed on the external volume change. The reviewed stereo checkpoint corrected failure-aggregate retention and exact finite read bounds, without weakening assertions. Those corrections are installed in`06f84e2`, but their product checks have not been repeated.
- Ben confirmed Astra/high routing at01:11:40. Section1 reviews scored7/7/9. Poodle and Nautilus released HUD implementation and correction receipts, then stopped. The coordinator independently passed the [HUD integration gate](docs/plans/mobile-expansion/section-04-hud-validation.md). Dragon's immutable reviews scored6/8 and separately assess the final callback correction. Hare's [R13 HOLD audit](docs/plans/mobile-expansion/section-05-hold-research.md) and Vole's [stereo correction addendum](docs/plans/mobile-expansion/section-02-stereo-review-addendum-03.md) remain unchanged. Coordinator owns device actions, the only Gradle slot, integration and commits. Two live workers maximum, one implementation writer. Do not repeat the beacon.
- Ben required minimal added latency at04:38. Measure against normal playback on the same output and verify alignment with heard music. Current re-render latency is unmeasured. The [stereo research](docs/plans/mobile-expansion/section-02-stereo-research.md) distinguishes combined-route restrictions from a scoped LOOP_BACK monitor candidate. Direct tee mutation remains unexecuted while safe ownership is unresolved. The SoundCloud installed manifest explicitly opts out of projection capture, but actual root stereo support remains untested.
- At04:56 and04:58 Ben authorized an honest root-ready authorization surface if bounded stereo investigation remains blocked. Keep encouraging copy and working authorization, without claiming stereo audio. Continue independent expansion work. The [finite stereo contract](docs/plans/mobile-expansion/section-02-stereo-probe.md), [direct tee audit](docs/plans/mobile-expansion/section-02-direct-tee-audit.md) and [native policy addendum](docs/plans/mobile-expansion/section-02-native-policy-audit.md) preserve current evidence and limits.
- [Exact stereo freeze and trials](docs/plans/mobile-expansion/section-02-stereo-trials.md) records the installed experiment and the newer clean `15bcbe5` dual-artifact candidate. App SHA256 is `4708959a55ef2c3ccac0e3ea97c29ce2b8db7cf2a7294f39c8ebf6913c7c2e86`. Companion SHA256 is `611000a6516dbdb76ada7f984e43ea2d1297dec94d9496ffc8009545d2829903`. That reviewed stereo-only candidate is retained, not installed. At06:54 bounded read-only preflight still found active unrelated MEDIA playback, no policy mix, and byte-identical baseline preferences. Do not interrupt it. After a fresh quiet preflight, use the exact retained candidate and reviewed v2 SYSTEM runner. R09 awaits accepted input/clock evidence. R02 has a separate [clean dual-APK freeze](docs/plans/mobile-expansion/section-04-hud-validation.md) at `a3223b8`, also uninstalled. Its original UI/native source is reviewed, not hardware-accepted. At07:10 another read-only snapshot still showed unrelated MEDIA playback and baseline-identical preferences. Hatchling owns R13 source until its bounded handoff around07:27. Wyvern audits fixed pre-R13 source without edits. Coordinator owns only handoff/receipts/shared FEEDBACK until writer release. Do not run Gradle over changing source.
- Historical muted-speaker receipts are superseded by the current Bluetooth route above. Existing `no_backup` is Android-managed 0771 and must not be chmodded. New helper directories alone use 0700. Preference archives still match the original baseline byte-for-byte.
- The [fixed debug authorization probe](docs/plans/mobile-expansion/section-02-authorization.md) distinguishes normal-app root execution from the observed manager grant and failed `run-as` lookup. It cannot execute caller commands or capture audio.

## Previous repair receipts

- **Fresh local continuation:** [04:40 ASUS local/rotation replay](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-local-replay-2026-09-07.md). Exact installed743d8bb passed actual gain reset, silence/EOF, paused seek, reversible3D perspective, disabled controls and held import/unlock. These now have fresh post-mapping device evidence. Phone state is restored. Spotify stayed remote and was not controlled.

- **Post-mapping rerun:** The atomic receipt now reports every ID after a forced full regression and new physical checks. Slow gain again rose1.833 to2.0023825 without deferred travel. Three Activity replacements again retained one listener. Fresh Spotify transport replay is blocked: its actual Connect route is playing on the excluded S25. Do not transfer or pause that playback. Earlier capture evidence remains valid, not a fresh pass. ASUS test state is restored.

- **Atomic traceability:** [47 requirements and changed outputs](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-atomic-traceability-2026-09-07.md). Each has a concrete check and observed result. The additional real-app default-off check passed with a VERBOSE reader. Host fixture failure returned its expected error envelope. Phone preferences were restored again. Source-only and unobserved live paths remain labeled.

- **Final-artifact audit:** [Requirement-level real-path checks and exact cleanup](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-final-artifact-audit-2026-09-07.md). The installed743d8bb replay produced ten capture commands, ten glyph changes and zero premature changes. Source-sheet timing, slow gain, 3D perspective, VIEW LOCK, paused seek and listener retirement were checked on that artifact. Remaining unobserved paths are explicit.

- **Final installed checkpoint:** [Local gain, paused seek, held rotation import and exact restoration](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-final-checkpoint-2026-09-07.md). Source743d8bb is installed and readback verified. Temporary phone state is restored. Remaining scenario and human/release gates are explicit.

- **Latest continuation:** [Activity sensor leak reproduced and corrected](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-sensor-retirement-2026-09-07.md). That tested candidate is superseded by the exact installed build below.440 Android tests pass. Ben confirmed exclusive ASUS ownership and autonomous takeover.

- **2026-09-07 slow-pinch checkpoint:** [Eight measured runs and correction](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-slow-pinch-2026-09-07.md). Candidate SHA40f1a847 is installed, with capture fix61e0630 and slow-pinch accumulation. 438 tests and lint pass. Temporary state and remaining acceptance are not closed.

- **2026-09-07 continuation:** [Capture prediction race reproduced and corrected](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-capture-race-2026-09-07.md). Five premature glyph flips before the fix, zero across seven routed commands afterward. This supersedes the blanket “unreproduced” description for that transient failure only. Gesture and other acceptance work is ongoing.
- **Current volume override:** Ben set ASUS MUSIC to **1/30** and asked that it not be raised. Do not restore historical 0/30. S25 exclusion and requested keep-awake remain binding.

- [Real ASUS Spotify capture and permission restoration](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-spotify-2026-09-07.md). Repeated source/mirror/glyph agreement passed. B8 remains unreproduced, not fixed.
- [All eleven grid modes, asymmetric trace angles and raw channels](docs/dev/receipts/pre-v2-b1-b21/phase-16-asus-mode-grid-2026-09-07.md). Pixel-based mode checks and left/right labels passed on ASUS, with exact restoration.
- [ASUS moving-trace recreation and version 2 physical gestures](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-asus-recreation-2026-09-06.md). Two complete surface cycles and held-margin checks passed. Exact physical 333ms remains open.
- [ASUS clean-start acceptance and two installed repairs](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-asus-2026-09-06.md).
- [Gesture-driver repair and actual native surface recreation](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-gesture-recovery-2026-09-06.md).
- [Final expanded phone pass and exact remaining gates](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-final-pass-2026-09-06.md).
- [Earlier continuation and its limits](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-continuation-2026-09-06.md).
- [Earlier phone receipt and per-card gaps](docs/dev/receipts/pre-v2-b1-b21/phase-15-16-s25-2026-09-06.md).
- [Original execution plan](PRE-V2-B1-B21-EXECUTION-PLAN.md), especially Phases 15-17. Its old volume requirements are superseded.
- [Receipt index](docs/dev/receipts/pre-v2-b1-b21/README.md).

## Previous ASUS session boundaries

The following records the earlier ASUS repair session. Its S25 exclusion is superseded only for the approved expansion above. The historical device receipts remain evidence for their exact artifacts, not new expansion acceptance.

**The S25 is excluded from further testing.** Ben supplied an ASUS_AI2202 Android14 replacement. The current verified USB serial is `NAAIB70036673ZC`. ASUS MUSIC is **1/30**, superseding the older muted baseline. No Phosphor package existed before this testing session, but the debug package is now installed.

Keep ASUS MUSIC at **1/30 or lower**, with no increase. The excluded S25 was left at 2/15. Keep PC audio silent. Do not manipulate the PIN/keyguard, publish, push, sign a release or deploy/restart Linux services under this phone-testing authorization.

USB access resumed at 23:32 UTC. Ben explicitly requested persistent plugged-in wake settings.
ASUS now has `stay_on_while_plugged_in=7`, `screensaver_enabled=0` and its unchanged 600000ms unplugged timeout.
Do not restore those two requested settings as if they were temporary tests.
The current continuation restored both original preference files byte-for-byte and stopped the app. Debug observations are disabled and owned test artifacts are removed.
Ben resolved the ownership question at02:25UTC: the ASUS is a dedicated test phone and Jcode is its only operator. Retake control autonomously. Exact installation and temporary-state restoration are complete for this checkpoint.
Older S25 narratives below are historical. Their moving-trace recreation gap is superseded by the latest ASUS receipt.

The console volume slider is unwanted. It is removed, not moved elsewhere. Preserve the remaining UI design. The repeated Spotify play/pause-symbol and grid-visibility reports are priority requirements, not requests Ben should repeat.

## Installed now on ASUS

- Implementation `743d8bb`, including observed capture truth `61e0630` and slow pinch `18569be`.
- Package `dev.phosphor.mobil3.debug`, version `2.0.0-debug`.
- Installed APK SHA256: `eba2a0bc433055dec0a01650e49e5df0c4e8318fc1b227d4c50f8d933a3b57e5`.
- Exact retained APK: `dev/scratch/asus-timeline-20260907T0113Z/final-743d8bb.apk`.
- `dev/pm3 install` verified matching installed bytes and signer. Exact-source Android gate passed 440 tests, lint, assembly and checkEngine.
- Earlier host-native 73, relay 43, mocked pm3, privacy and isolated release-gate fixtures passed. No deployment or release occurred.
- Real source-item gain, silence hold, paused seek, disabled rotation controls, held import and unlock passed their named checks.
- Final state: both baseline preference files restored exactly, app stopped, no source services/projection, MUSIC 1/30, requested wake settings preserved.
- Owned test-tree permission, phone fixtures and opt-in diagnostics are removed. Three older URI grants remain unchanged.
- Raw evidence and rollback stay private. This is not all B1-B21 acceptance or release approval.

## Previous ASUS checkpoint, superseded above

- Implementation `6183aa8`, including `1a0f6b9`. These add the LIGHT fast-TIMER guard repair and complete untouched-control exports.
- Debug package `dev.phosphor.mobil3.debug`, version `2.0.0-debug`.
- APK SHA256 `5d30d7e62d47ebdb7e67ef94bd71f7a2455fd524197699e2b3dc1626bc2e5dc3`.
- Exact retained APK: `dev/scratch/asus-acceptance-20260906T2051Z/snapshot-committed.apk`.
- All 426 Android tests and lint passed. Actual warning/recovery and 41-key mutation/import passed. Native logs confirmed safe timer behavior and custom-color reset.
- Final ASUS state: clean effective defaults, no source, app stopped, MUSIC 0/30. Private backups and receipts retained.

## Previous S25 installation, unchanged

- Mobile implementation `63fc7ef`, including final-item replay repair, volume removal `9b3cc62` and grid contract `9f65bd1`.
- Shared implementation `297e88b`.
- Debug package `dev.phosphor.mobil3.debug`, version `2.0.0-debug` (`2000000`).
- APK SHA256 `f5afd99617e7a1abf21246c612590212adeed3a82b588c6235e8448a68b48834`.
- Installed APK bytes and signer were verified through `dev/pm3 install`.
- Exact APK and original installed rollback are retained under ignored `dev/scratch/phone-acceptance-20260906T1720Z/`.

Four corrections are installed:
1. No console VOL row, percentage, spare row or app-owned volume adapter. The console contracts without restyling its other controls.
2. Shared minor/axis grid coefficients are 0.035/0.08. Three matched low-brightness captures improved minor-line contrast from 8 to 43 sRGB code values while the background stayed at 1. This is measured improvement, not Ben's visual acceptance.
3. SOURCE queue taps now reach the existing track-switch handler through the previously missing Media3 command. Actual selection of the nested left-only fixture passed.
4. Final-item playback now drains to native ENDED instead of pausing its tail early. Actual Play after completion restarts the item near zero.

## Verified and still open

419 Android JVM tests, lint, builds, 15 renderer library cases, 10 actual GPU integration cases, selected strict Clippy, source privacy and protected archive checks passed. Actual phone evidence covers five earlier-candidate mic transitions, local seek stress, nested/corrupt file handling, fixed queue selection, left/stereo dBFS, a silent-gap gain hold, stable-session Spotify control/reattachment, permission recovery, temporary-allowed automatic PiP, existing relay connect/disconnect, and capture/mic recents removal.

**B8 is not closed.** Stable Spotify checks matched the icon to the session, but they did not reproduce Ben's reported inversion. A later final-candidate attempt lost the external Spotify session. No speculative glyph fix was made. Later next-track testing observed real Spotify and mirrored BUFFERING then PLAYING. Stable controls still matched. The original inversion remains unconfirmed, not fixed.

Paused local seek position had an intermittent zero observation, although four later paused seeks published correct positions. Final-item replay is now repaired. Additional right-only dBFS, style persistence, PiP on/off/manual entry, local recents removal, and main/PiP wake beyond a shortened timeout passed bounded checks. A suspected PiP-return defect failed matched-control diagnosis, so the experimental patch was discarded. The later pass verified double-tap on/off, slider-lane samples, all 42 exported settings through real import and checksum rejection, all-source linger/removal, and bounded trace stability. Exact multi-pointer timing, full surface recreation, exhaustive variants, clean-emulator defaults and fresh-Linux acceptance remain open. The input driver was killed before injection; prior emulator renderer failures remain documented. The latest receipt names each limitation. Keep `drift: 21`.

## Restoration

Original tuning was restored byte-for-byte except retaining the grid's already-enabled state. Saved relay hosts are byte-identical. Test-only controls, double-tap, PiP auto-entry, grid-data and mode changes were restored. Android's original PiP denial and notification-listener membership were restored. Automatic brightness, timeout, screensaver and rotation policy were restored. Automatic brightness may adjust its live numeric value.

Phone fixture files and owned diagnostic processes/files are cleaned up. The app was verified in no-source state and left in the background without PiP. MUSIC remains 2/15. Backups and raw evidence are private and retained. The final 240-second diagnostic window replayed 82,861 records exactly, with no fatal/ANR/panic candidates. Earlier failed/truncated logging attempts are explicitly not counted as complete evidence.

## Release boundaries unchanged

The 20:27 UTC continuation diagnosed the input-driver exit137 as an Android16 InputManager initialization exception.
Its corrected retained fixture passed a no-input probe and four physical two-pointer checks.
A complete native surface destruction/recreation sequence now has replay-verified logs, but moving-trace luminance through that sequence remains unproven.
The exact 333ms physical boundary, B8 inversion and emulator failures remain open. No app runtime patch or new APK was needed for these checks.
Original tuning and hosts were restored byte-for-byte. No workers ran because routing confirmation remains unanswered.

Approved signing inputs, final-tag approval and the dependent release bundle remain external blockers. No push, tag, signing, publication, production/Fortress removal or Play submission occurred. Historical August release evidence remains in `docs/dev/receipts/primetime-cleanup/`.
