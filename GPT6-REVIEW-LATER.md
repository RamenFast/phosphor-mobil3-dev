# GPT-6 / Astra review later

Written 2026-09-10 by Prime after GPT-6 usage ran out mid-baseline.
This file is a later-pass brief for Astra / GPT-6. It is not an acceptance claim.

When GPT-6 usage resets, Astra should re-read this file, current Git, `HANDOFF.md`,
`docs/plans/mobile-expansion/EXECUTION.md`, and the evidence directories below.
Then review the work Grok 4.6 high continues from this interruption, plus the
unreleased R09 tree Astra itself left dirty.

Do not reopen accepted bounded units unless a later change regresses them.
Do not treat this document as source of truth over current Git or device receipts.

## Why this file exists

Ben asked for a top-level later-review note after GPT-6 usage exhausted.
Astra implemented or grounded every accepted unit in this campaign.
Grok 4.6 high is the temporary substitute for implementation and 3D until GPT-6
returns. Independent critic roles stay the same.

## Model routing

Standing law, still in force:

- Implementation owner: Astra medium. Temporary substitute: Grok 4.6 high on dedicated OAuth only
  (`grok-oauth/grok-4.6`). NEVER OpenRouter `openrouter/x-ai/grok-*`.
- 3D modeling: Grok 4.6 high. This replaces the earlier Astra medium/high 3D
  assignment.
- UI / visual design and visual critique: Muse 1.3 Contributor direct high
  (`meta/muse-spark-1.3-contributor`) or DeepSeek. Preferred DeepSeek selector
  while it lasts: `deepseek/deepseek-v4.1-flash-expires-on-0910` at xhigh.
  Fallback: `deepseek/deepseek-v4-pro`.
- Code-quality critique only: Grok 4.6 high/xhigh. Grok does not score visual
  taste. Pass is >=8/10. Maximum four attempts per unit. Preserve every old
  score, including failed and blocked rounds.
- Parent Prime owns orchestration, Gradle/full gates, Git, and dedicated ASUS
  device work.

When GPT-6 returns, Astra should:

1. Review Grok's R09 (and later) source against the ratified contracts.
2. Review any 3D or other Grok implementation for completeness, not taste.
3. Leave visual scoring to Muse/DeepSeek.
4. Leave code scoring to an independent Grok session if Astra is the author of
   a later patch. If Grok authored the patch, Astra's later review is the
   missing independent implementation review.

## Accepted bounded units (do not reopen unless regression)

| Unit | Commit | Independent scores | Device |
|---|---|---|---|
| Deferred-root stub | `b222818` | Grok code 8 then 9 | ASUS preview, consent, legacy-root inert. Receipt: `docs/plans/mobile-expansion/root-stub-asus-checkpoint.md` |
| R06 brightness + Android-compatible archive checksum | `b74beb2` | Grok code blocked / 8 / 8 | ASUS focus/Home/PiP/HUD/pause/recreation/import. 921 tests. Receipt: `docs/plans/mobile-expansion/brightness-asus-checkpoint.md` |
| U1 tactile no-track console | `7204b91` | Grok code 8/8/8. Muse visual 5/7/8. DeepSeek visual round2/3 = 8/8 | ASUS four themes, focus, Light font 1/1.3/2, pressed, 320 dp, S9/SRC gestures. 933 tests. Receipt: `docs/plans/mobile-expansion/visual-u1-asus-checkpoint.md` |

U1 final installed/readback APK SHA256:

`a2a3cfc78db9a6aae87f6c9c911a0518476b128881d40afa5602245b4a763485`

Signer:

`f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`

Historical visual defects that were closed on U1, and must stay closed:

- SIGNAL CHECK readable on actual black plot
- measured status wrap and label placement at large font
- 2 dp focus ring with clearance from tray line
- Light top-left tags and independently centered legends
- compact one-row geometry and original S9/play/MODE/SRC semantics

U1 remaining non-blockers, still full-baseline work:

- inherited loud outer card frame
- plane-relative rest-luminance metric (do not blacken authored Dark/Glass cards)
- fresh-install tactile default is still unproven
- landscape, TalkBack, disabled-state, full motion/PiP, saved-look round-trip

## Jcode (separate from mobile, already activated)

- Immutable installed binary SHA256 `d4b6aab0420644391d8c8ff40dc841e80cb36eee67cf03573b349a4385fca8e9`
- Version `a7246b4-bash-null-d4b6aab0`
- Current + shared-server selected; stable, config, and old chats preserved
- 38/38 BashTool tests; Grok 8 then 9; default native text/tool and public `jcode run` passed with actual `notify:null,wake:null`
- Original ~1.45–1.83M-character history transport stalls remain unresolved and unreplayed
- Maintainer feedback queued only
- Receipts: `/media/ben/Mass storage/agenticTinkering/claude/jcode-loop-fix-2026-09-05/prime-null-bool-receipts-20260909/`

Astra later: do not treat huge-history repair as done. Fresh-path activation is done.

## Interrupted R09 state (the first thing to review)

HEAD is still `7204b9112804f5626bae7b22593ad0b63d7e9a8f`.
R09 is **not committed, not released, not full-gated, not code-reviewed, not
device-accepted**.

Astra (`astra-r09-grounding`, `sub-2fd049a5`) was the sole writer. The session
went idle mid-edit after a focused kotlinc of existing tests failed. Last
runtime edit: `MicCaptureService.kt`. Last visible compile failure was
`phase9Source` visibility when tests were compiled outside Gradle, plus
unfinished source-string test updates. 12 isolated `CaptureMixPolicyTest`
methods had passed earlier (`dev/scratch/r09-implementation-20260910/policy-tests.log`).

Ratified contract: `docs/plans/mobile-expansion/section-03-r09-contract.md`
Grounded research: `dev/scratch/r09-current-handoff.md`
Scratch compile/test logs: `dev/scratch/r09-implementation-20260910/`

Dirty worktree at interruption (20 modified, 8 untracked):

Modified:

- `app/src/main/AndroidManifest.xml`
- `BackgroundLifecyclePolicy.kt`, `CaptureService.kt`, `MainActivity.kt`, `MicController.kt`
- `SourceWakeLock.kt`, `SourceWakePolicy.kt`
- `settings/SettingsArchive.kt`
- `ui/ManualContent.kt`, `ui/PhosphorScreen.kt`, `ui/ScopeUiState.kt`, `ui/Sheets.kt`
- tests: `BackgroundLifecyclePolicyTest.kt`, `ForegroundBrightnessPolicyTest.kt`, `WakeOwnershipTest.kt`
- `docs/plans/mobile-expansion/EXECUTION.md`
- `scripts/lib/ManifestBoundary.java`
- `spec/AUDIO-AND-CONNECTIVITY.md`, `spec/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`, `spec/EXPANSION.md`

Untracked:

- `CaptureMixPolicy.kt`, `CaptureMixSession.kt`, `MicCaptureService.kt`
- `MicrophoneRoutePolicy.kt`, `MicrophoneRoutes.kt`
- `ui/MicrophoneControls.kt`
- `app/src/test/kotlin/dev/phosphor/mobil3/CaptureMixPolicyTest.kt`
- `docs/plans/mobile-expansion/section-03-r09-contract.md`

Required R09 behavior, still the winning condition:

- Visible-activity admission and RECORD_AUDIO
- Real mic FGS, notification Stop, generation-tagged lifecycle
- Actual device+format route verified before PCM
- Explicit selected-input loss, no silent fallback
- Owned Bluetooth communication/SCO leases only, restored on exit
- Standard playback + optional mic, one composite owner/mixer, sole visual publisher
- Pre-read visual epochs through queues/resampling/filter history
- Mixed PCM never reaches audible output
- Root helpers stay inert; no root+mic startup
- Strict portable keys, private local device identity
- Preserve existing local/relay/HOLD/BLACK/source-handoff
- Tests for invalid frames, bounds, epoch reset, stop orders, permission denial,
  failing routes, device loss, missing timestamps
- Parent full Gradle/lint/dual APK/checkEngine, independent Grok code >=8 max 4,
  then ASUS built-in plus each available accessory

Grok 4.6 high continues this implementation until release.

R09 review status at 2026-09-10 ~02:52 UTC:
- Attempt 1: 7/10 FAIL. F1 CaptureMixSession.offer rewound mixer epoch. Report: docs/plans/mobile-expansion/critiques/r09-grok-code-round-01.md
- Correction applied: offer no longer calls core.epoch. 946 tests pass. Independent attempt 2 on grok-oauth/grok-4.6.
- Not committed. Not device-accepted. Astra later reviews
that result against the contract and against this dirty tree, not against a
remembered plan.

## HDR research, ready but unimplemented

Read-only handoff: `dev/scratch/r05-current-handoff.md`
Evidence: `dev/scratch/r05-grounding/`
Pins at research time: mobile `7204b91`, shared engine `0ffd658d7f19e68180c2720e0500b23644619e90`

Verified, not accepted:

- wgpu-hal 27.0.4 still pairs Vulkan FP16 with `EXTENDED_SRGB_LINEAR_EXT`
- Android fingerprints compile Vulkan+GLES; actual adapter must be runtime-checked
- `ndk` needs existing feature `nativewindow` for `buffers_data_space()`; not enabled yet
- Dataspace readback is not active-HDR proof and not physical luminance
- Permanent `Gpu.format` and once-cached retained presenter must become per-destination
- `presented()` is metadata-only, not first-GPU-present
- Same-host resize does not bump Java lease generation; add `presentationRevision`
- HOLD SDR->HDR must preserve old SDR clipping, not reveal latent >1 energy
- Failed configure can destroy the old swapchain; explicit SDR reconfigure, no rollback illusion
- API 34 is the first candidate for the preferred metadata path on ASUS Android 14

Do not implement HDR while R09 still owns the mutable app tree.

## Remaining complete-baseline scope

This is the live goal. Heartbeat stays on until every item is genuinely done
or honestly blocked with evidence.

1. Finish R09 source, focused tests, parent full gate, Grok code review, ASUS mic/route/mix acceptance.
2. Implement genuine linear HDR with truthful SDR fallback from the R05 handoff. Runtime Vulkan identity, matching metadata/buffer ordering, HOLD transfer matrix, and instrumented luminance remain proof obligations. Screenshots are not nits.
3. Default-source startup coordinator (R14), including consent-chain rules and inert imports. Root auto-start stays deferred.
4. Remaining visual craft beyond bounded U1: track/title/seek, broader sheet/manual layout, inherited frame hierarchy, animation, theming polish. Muse/DeepSeek own design. Grok may implement 3D wireframes.
5. Proven fresh-install tactile default without breaking existing saved looks.
6. Requirement-linked device regressions: settings, appearance/manual, HUD, HOLD, colors, presets, signal check, landscape, TalkBack, disabled state, saved-look round trip.
7. Five lifecycle cycles and 30-minute soak on the exact accepted APK.
8. Exact reviewed APK freeze, install, and original-state restoration on ASUS `NAAIB70036673ZC`.

Explicitly deferred or out of this baseline:

- Root PCM, root stereo, SoundCloud root, root+mic
- Push, signing, store submission, publication
- S25 work
- Production Fortress uninstall
- Historical B-card / v2-release matrix, except where a current unit actually regresses it
- Jcode huge-history transport

## Device and process law

- Dedicated non-root ASUS Zenfone 9 serial `NAAIB70036673ZC`
- MUSIC <= 1/30, currently 1/30 at this note
- PC silent; S25 excluded
- No system/vendor/boot/vbmeta writes
- No push/sign/publication
- Maximum two live workers; one app source writer
- Parent owns builds, Git, and device
- ADB here rejects global `-n`; redirect stdin
- Preserve original two preference XML files; restore byte-for-byte after device work
- Baseline backups: `dev/scratch/root-stub-20260909/asus-baseline/`
- External verified backups: `/media/ben/Mass storage/agenticTinkering/claude/phosphor-mobile/`

At this note: app services not listed, font_scale 1.0, wm physical 1080x2400.

## What Astra should actually do later

1. Diff HEAD after Grok's R09 release against `7204b91` and this interrupted tree.
2. Check the R09 contract, not the writer's summary, especially mixed-PCM isolation, route proof before publication, epoch-before-read, and stop/generation fencing.
3. Check that U1 tactile geometry, Appearance `look_version`, and R06 brightness/checksum were not collateral damage.
4. Check HDR only after it exists, using the R05 handoff's proof obligations.
5. Write an evidence-backed review in `docs/plans/mobile-expansion/critiques/` and leave visual scoring to Muse/DeepSeek.
6. If Astra then authors a correction, Grok still does the independent code-quality score.

If GPT-6 is still unavailable, leave this file in place and keep Grok inside the same laws.

## Later units after this brief was first written
- Mic-label `14dac99` Grok 7 then Muse 9. ASUS SRC `(19)`/`(21)` both start. Prefs restored.


- HDR dataspace observe `bb3044d` and ASUS REQUEST HDR vs SDR dataspace (not nits).
- R14 source `ca0c63d` Grok 7 then 8, then ASUS matrix receipt `docs/plans/mobile-expansion/r14-asus-checkpoint.md`.
- Fresh tactile default source `1921b38` Grok 8. Device empty-install proof still open.
- Idle 30-minute soak aborted by Ben 2026-09-10. Do not resume.
