# Unit 1: installed for Ben's feedback

Status: **verify**. Unit 1 implementation and bounded ASUS checks are complete.
Ben's physical usefulness feedback remains open. No later recovery unit is authorized.

## Exact installed build

- Mobile application source: `9d5d77ec7443b8e5138c3bd9f5a9e8a60ebbfc99`.
- APK SHA256: `4a3b5928f2d42d911e1d4165cb4164e7a8b221dab6ef7c64e905a6671c80e594`.
- Signer SHA256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- Sibling engine: `3171e0f3bb99bfcb881f17cc034e8794c9e249e4`, unchanged and clean.
- Package/version: `dev.phosphor.mobil3.debug`, `2.0.0-debug`.
- Device: ASUS `NAAIB70036673ZC`, ASUS_AI2202, Android 14.
- `pm3 install` matched installed readback, local hash and signer. A final device hash after cleanup matched again.
- The follow-up source diff only expands the adversarial mode-landing test. It does not change the installed production implementation.

## What changed

Stereo microphone formats precede bounded mono fallbacks. The old loop selected mono first.
Client format, platform device format and measured channel relationship remain distinct.
A corrected service getter now reads fresh microphone diagnostics rather than its startup snapshot.

AUTO follows eligible quiet structure. Pinch and vertical one-finger zoom change a separate
remembered framing preference without disabling AUTO. Manual gain remains explicit.
Default/reset is 1.0. Whole-settings archives include it. Older archives preserve it when absent.
Completed gestures and RESET use checked saves. Failed writes retain a retryable pending owner across Activity recreation.

The controller uses elapsed time, protects the current loud frame and bounds magnification at 256x.
XY45 and swirl reserve rotation headroom. The actual animated mode landing precedes the single AUTO update.
No PCM gain, speaker-monitoring path, mixer rewrite, shared-engine modification or relay protocol was added.

## Executed checks

| Check | Result |
|---|---|
| Android unit suite | 981 passed, zero failures/errors/skips |
| Debug lint | Passed |
| Locked mobile native suite | 134 passed, including opposite-channel first-mode-landing cases |
| Locked relay suite | 43 passed |
| Developer CLI fixtures | Passed |
| Manifest policy | 107 checks passed |
| Actual-format APK/AAB boundary and release-rejection fixtures | Passed |
| Source boundary | 12 checks passed |
| Gradle debug APK and engine check | Passed from sealed source |
| Protected archive hashes | Unchanged |
| Independent source review | Findings corrected and preserved in `reviews/` |

Tests are not claims of public release, API29 hardware, accessory coverage or complete B1-B21/R01-R17 acceptance.

## Actual ASUS evidence

- Final APK opened selected built-in input 19 at client **48 kHz stereo PCM16** and device **48 kHz stereo PCM16**.
- Final fresh window reported 2,471,040 input frames and 5,148 completed reads.
- That window had 196 identical pairs among 11,518 non-silent pairs and difference RMS 0.00367.
- This proves delivered non-identical channels and current formats. It does not calibrate physical capsule independence or spatial separation.
- Final live microphone screenshot showed AUTO about 32.61x. Other room observations varied with input.
- The controlled 0.005-peak stereo fixture produced an XY45 trace 748px wide at neutral framing and 840px at maximum framing, inside 1080px.
- The earlier candidate's 1054px trace exposed the missing rotation margin. Its screenshots are not final-margin acceptance.
- A controlled 0.001007-peak stereo fixture produced visible waveform detail at measured gain 255.999, displayed 256.00x.
- On the quiet/loud/quiet/silence fixture, the first logged loud sample showed gain 1.125 at raw peak0.799988.
- From the first quiet-only 500 ms sample after loud input, gain exceeded20x in about2.0s and reached95% of its target in about4.0s.
- Digital-zero windows held gain 179.719 without growth. Source tests separately prove first-loud-frame protection.
- The 500 ms display observation is not a calibrated acoustic measurement or proof of panel scanout timing.

Ordinary inward pinch changed framing 1.0→0.5263159. A 240-move slow outward pinch reached0.5779558,
kept AUTO on, and survived an immediate process stop/restart. Manual gain stayed 1.8332275.
One-finger up/down changed the same preference. The final APK also exercised maximum framing,
bottom-band rejection and reset durability. VIEW LOCK and explicit manual takeover were checked.
The actual held-image gesture preserved tuning and resumed. Both 3D modes retained camera ownership.
Some early attempts happened over still-open MODE/SOURCE sheets. Those runs are excluded from acceptance.

The new archive restored 1.125 after a reset to 1.0. The original archive then restored prior settings
while preserving 1.125 because it had no framing key. Final RESET returned it to 1.0 and survived force-stop.

## Restoration and recovery

- Every pre-existing authored preference matches the initial snapshot. The only added key is `auto_frame_scale=1.0`.
- System and secure settings match the baseline, including the original media-button receiver.
- All 64 normalized URI grants match the baseline. Only the owned test-tree grant was released.
- RECORD_AUDIO remains granted with the same flags. No permission was revoked or newly granted.
- MUSIC remains 1/30. The PC produced no test audio.
- No Phosphor process or source service remains. MediaProjection is null. The microphone is off.
- Owned phone WAVs, exports, gesture/cleanup jars, staged test audio and temporary UI files were removed.
- Only a test track marker was restored in private runtime state. `last_source=none` truthfully records stopped input, and the current calibration date remains.
- No S25 operation, root/system partition write, uninstall, data clear, public release or production signing occurred.

The original APK/app-state, exact installed APK, source archives, drivers and hashes are retained in
`/media/ben/Mass storage/agenticTinkering/claude/phosphor-mobile-recovery/unit1-20260913/`.
Detailed local observations are in ignored `dev/scratch/recovery-unit1-20260913/`.
The previous APK is hash `2bb7b9830f5bd1dbd10256e4dffc6faa9a3ebebff3784e8d2419ac2335e1882b`.
Root can restore that APK and the retained state if needed. No rollback was needed.

## Honest limits and next action

Coherent hum can pass the structure heuristic. Uncorrelated wanted sound may not.
The silence fixtures are digital silence, not a promise that every room is quiet.
Existing permission proves preservation and operation, not fresh denial/grant UI.
Source/volume checks support unchanged audible levels. Ben's listening and instrument comfort remain his feedback.
Exact 3D comfort, every chrome/orientation variant and all accessory routes are not blanket accepted.

Read [Ben's test card](UNIT1-TEST-CARD.md). **Stop for his feedback.** Do not proceed to Unit 2 or the wider recovery automatically.

## Independent visual check

[The bounded visual review](reviews/unit1-visual-review.md) found complete XY traces with side margins and no visible clipping. It found two legible waveform signals. Input quietness comes from the fixture and numeric receipt, not screenshot appearance. No physical stereo, latency or comfort claim comes from this image review.

## Test-driver guard notes

MODE and SOURCE can stay open after selecting an item. Confirm the exposed stage before testing gestures.
Control names can be in Android `content-desc`, not only `text`.
A failed Settings-opening swipe must not be followed by a scroll unless SETTINGS is visibly present.
This run caught a later stage swipe that changed the new preference to0.25 during test setup.
Final RESET and a complete preference comparison corrected it to1.0 before closeout.
Android's file picker appended `.json` and retained its last directory. Archive paths were checked rather than inferred.

## Second ASUS install · 2026-09-24

Ben connected a second ASUS_AI2202, serial `NAAIB700B7373PZ`, Android 14.
Phosphor debug was not previously installed, so no prior app state existed.
`pm3 install` installed the same APK `4a3b5928f2d42d911e1d4165cb4164e7a8b221dab6ef7c64e905a6671c80e594`. Readback hash and signer matched.
The app was not launched. Microphone permission remains ungranted until Ben chooses it.
This install is not separate device acceptance.
