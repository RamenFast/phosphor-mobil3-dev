# Phase 11: raw stereo data, grid angle and silent-gap gain

Date: 2026-09-06. Cards: B15, B16, B20. Refs #5.

**Status: bounded source and host gates passed. Exact artifact and Android acceptance are not yet established by this receipt.**

- B15: OPEN. Raw-channel mechanism verified on host. Physical grid visibility and Ben confirmation remain required.
- B16: OPEN. Shared CPU/GPU mechanism verified offscreen. Actual device mode transitions remain required.
- B20: OPEN. Gain hold and current-item identity verified on host. Actual loud/silence/loud behavior remains required.

## Source identity and change

Mobile parent: `157ea5b05fd70cf4989ac337eea4a5490fdd343f`.
Exact shared dependency: `aa09b8e14f8b8912b125e3312b4ddeec09404089`.
That shared commit preserves the separately merged desktop baseline `70a31090299cdad928efcd12fca2f08deb69adc4`. Its public receipt is `docs/dev/GRID-ANGLE-2026-09-06.md` in the sibling repository. Neither master branch was changed by these scoped commits.

The reviewed inventory contains 21 mobile production/test/spec paths and six shared renderer/test paths. Documentation added after review does not replace the frozen source identities.

| Human requirement | Actual production owner and change | Observed checks |
|---|---|---|
| Independent real L/R amplitude and absolute dBFS | Existing single scope-ring drain computes finite complete-pair raw peaks before gain. The same vector feeds DSP and its maximum feeds AutoGain. A consumed, bounded 500ms window distinguishes silence, missing data and expiry. | Actual SampleRing, StereoPeak and window tests cover unequal channels, half-frame completion, malformed pairs, zero, empty, expiry and logarithmic dBFS. |
| Optional data without a second poller | Existing scopeStats/JNI and foreground uiTick publish when HUD or GRID DATA needs data. GRID DATA defaults false, uses the existing BAND visibility policy and typed preference/archive paths. | GridDataTest 5 and SettingsArchiveTest 22 passed. UI/heartbeat adapters include source assertions, not Compose execution. |
| No stale old-source readings | Ring-before-meter synchronization plus a unique per-attempt RemoteScopeLease guards setup, both real remote PCM adapters and retirement. Generic boundaries invalidate the lease. Retirement clears only its own published source before FIN/joins. | Seven behavioral lease regressions plus adapter source corroboration cover delayed writers, same-generation replacement, cancelled setup, generic replacement and late cleanup. |
| Grid follows actual Xy45 | Render uses actual post-flip mode and current-session remote K mode. Shared CPU/GPU rotate only centered grid coordinates. Zero preserves original arithmetic. GPU reuses byte 100 of its 112-byte uniform. | Local/known-remote mode cases and seven shared tests cover packing, axes, exact zero reset, parity and compositor origin/crop. |
| Stable silent gaps without cutoff | AutoGain holds below raw 0.02. Sounding frames retain 0.999 release, 0.05 glide, 0.92 headroom and 6.0 auto clamp. Manual 7 remains supported. Toggles preserve tracked peak. | Engine gain tests include 10000 silent/invalid inputs, exact threshold, unchanged effective-gain origin and sounding convergence. |
| Only a proven new local item resets tracked peak | Existing request slot, exact PlaybackTruth Open, positive native open identity, successful main publication and TrackStarted authorize one reset. Render rechecks the published native owner. Seek, metadata, capture and mic do not create this boundary. | PlaybackTruthTest 21 and native identity/event tests cover stale publication, same-path replay, seek, failed/unpublished opens and duplicate commands. |

## Frozen gate and independent review

Root attempt04 finished at 07:19:50 UTC with exit 0. Its runner uses offline locked Cargo resolution, two build jobs, two Rayon threads and serial shared GPU tests without visible display variables.

- 33 JVM suites: **362 tests, zero failures, errors or skips**.
- Selected native tests: **50 passed**, comprising engine 27, terminal 9, activation 4, close 1, remote media 8 and watchdog table 1.
- Shared beam/CPU/GPU: **26 passed**, including actual offscreen compositor readback. No adapter skip occurred.
- Shared workspace/all-targets consumer check and selected renderer Clippy at `-D warnings` passed.
- Android native build/JNI packaging, checkEngine, native format, both whitespace checks and all three protected archive checks passed.
- Root lint: **16 warnings, zero errors**. Lint report generation was up-to-date. This is not warning-free lint.

The CPU timing stress case reported 8153.88 ms/frame and the GPU log contains a RADV conformance warning. A passing test is not a mobile performance or physical display acceptance claim.

All 825 gate inputs matched before and after. Separate read-only SOURCE review found the prior remote ownership defect closed. A genuinely isolated intent reviewer froze its substantive production map before receiving tests, retained results or other reports. Both reviews found no remaining concrete blocker within their bounded mechanisms.

Evidence is retained under ignored `dev/scratch/pre-v2-20260829T072841Z/phase-11/`:

| Evidence | SHA256 |
|---|---|
| `combined04-inputs.sha256` | `c96e4b09c1e0f9a6461e786afdadacf6b52d18dbfd58b9e2221f905c7ac9aa4e` |
| `implementation-attempt04-before.sha256` and `-after.sha256` | `f7316498908624d06363a95bd4623ab45ba3e087bb7b50f36db211f7718a88cd` |
| `root-correction04-pre-gate.md` | `7acedcce2ddf2644fd2c4d802598f5d456a25b1ad55dce77a9b21b0e9bc75821` |
| `root-render-correction03-gates.sh` | `2008b9214eb1c4e979f6b86f9188c0bd807d13d31a73c861029ee4f6cdd35aff` |
| `review-source-correction04-astra.md` | `8320f96b2ad02b0488af4302bed89a0fda23aa245590b4ea70c0de54cb10ce8b` |
| `review-intent-isolated04-astra-map.md` | `9010d805ad8089e596cde6966c8121074a2a66be9b34c107ab6a27fe786fc99a` |
| `review-intent-isolated04-astra.md` | `a7df7bf80a41b2ca97897d91316441d121b65b11eee17cb839711abc1204f242` |

Earlier failed gates and invalid-ordering reviews remain unchanged. Attempt03 failed one whitespace-sensitive adapter assertion after rustfmt while all seven new behavioral lease tests passed. Root proved attempt04 changed only that test's whitespace normalization. Production and the other 824 gate inputs stayed byte-identical. The retry passed. Attempt03 remains failed, not retrospectively green.

## Separate open source-contract disposition

Local shared playback mirrors decoded/resampled chunks after audible enqueue, before DeckOutput consumes them. The inspected audible ring has roughly 0.1-second capacity. This is the same sample stream, not literally an output-callback tap. AUDIO section 2's callback-tap wording and exact physical-output timing are therefore not accepted by the current tests. Keep this discrepancy open for a separate owner-linked disposition. Do not silently rewrite the requirement or treat source review as callback timing proof.

Audible remote scope uses post-jitter/post-mute/post-zero-fill output. With remote audio disabled and geometry inactive, the fallback uses received PCM. GRID DATA describes the selected scope feed, not the remote microphone's physical level. K/G mode is latest-known session state, not frame-exact attribution.

## Required live evidence and recovery

1. B15 requires the Phase0 baseline and three fixed-brightness grid-only captures per candidate. Record exact shared minor/axis coefficients, ROI, line-minus-background differences, baseline noise/variance, lowest passing pair and Ben's visibility confirmation. Current coefficients remain `0.003` and `0.0063`.
2. Run left-only/right-only and no-data/silence cases, HUD-off GRID DATA, actual settings restoration, source replacement and foreground return on the exact APK.
3. Run Xy, Xy45 and other modes through actual transitions, then loud/silence/loud, new-item, replay and seek sequences. Preserve physical beam behavior and never-cutoff limits.
4. Actual Compose, JNI, Looper, AudioRecord, Oboe and Android surface delivery remain unmeasured. The selected native gate is not the full workspace runtime suite.

No phone, permission, preference, active service, screen brightness or media state changed in this phase's offline work. Drift remains 21. The exact committed debug artifact follows separately and will not itself close these cards.

Rollback uses new revert commits for this mobile change and the scoped shared grid commit, then the retained exact Phase10 APK and approved preference restoration only if needed. Do not reset shared history or overwrite a newer user's settings.

## 07:58 UTC: identifier-only boundary correction

The pre-commit source boundary failed at 07:53:42 because two private DSP identifiers matched the scanner's analytics-vendor term. No commit ran after that failure. Root renamed only the local reader helper and local formatting variable from `amplitude` to `rawPeak`, including six references/declarations. Exact reverse replacement recovers the reviewed ScopeUiState bytes. No JSON, label, arithmetic, test, preference, dependency or scanner changed. The other 26 reviewed paths remained identical.

The complete frozen attempt05 then passed at 07:58:30 with 362 JVM, 50 selected native and 26 shared renderer tests. Lint remained 16 warnings and zero errors. The unchanged source privacy gate passed 11 checks inside the runner. All 827 input rows matched before/after and were independently rehashed after completion. The extra two rows are the newly written shared and mobile receipts.

| Correction evidence | SHA256 |
|---|---|
| `root-correction05-pre-gate.md` | `43f9f0e5c9285cae549efd5acd7dc4f0720b02990628a7935bfd6fb3b1144dda` |
| `combined05-inputs.sha256` | `a6f4ce1df051177606189c101c228a0bda937794bd23852543d2da2e92fc9337` |
| `implementation-attempt05-before.sha256` and `-after.sha256` | `b3dc586f95ee42200abdcc62dfd498102c4c671445b982dea8434ee192e97482` |
| `root-render-correction05-gates.sh` | `f4cc7694e3a1cebbf03879b730599142805f0452d37e1f81330d1465d1083299` |

Earlier independent approvals retain their exact original snapshot. Root's local-name equivalence proof and full retry are separate evidence, not a newly commissioned independent review. All physical and source-contract limits above remain open.
