# HOLD present and color continuity correction gate

## Provenance

Coordinator task `315155k95w` completed on 2026-09-08 at 09:39:49 UTC, exit 0, after 74.29 seconds.
The gate used mobile `79af079dc5e4ef3c70f2349c692ecfaa6a86ee88` plus the frozen correction delta, and unchanged shared `0ffd658d7f19e68180c2720e0500b23644619e90`.
This is a working-source integration gate, not a clean-commit artifact freeze. Both APKs were built together and retained. Neither was installed.

Runner and evidence prefix are under ignored `dev/scratch/mobile-expansion-20260908T001819Z/`:

| Evidence | SHA-256 |
|---|---|
| `run-color-present-integration-0938.sh` | `a8281f4730be9b3f50bc59fef587c50f69a9df191380cf4dc358cd1ed4fe36e8` |
| `color-present-integration-0938-mobile-source.sha256` | `2810c700ea146e96eece7641c9618cff1d1005383cbc929ee0c7477e1a953b89` |
| `color-present-integration-0938-engine-source.sha256` | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| `color-present-integration-0938-results.json` | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |
| `color-present-integration-0938.apk` | `0f50f26448d2672a1014e2f6d1cb6d9222434fdae49d6637f024b6abd5b017ab` |
| `color-present-integration-0938-androidTest.apk` | `33126df32ce7aed6ced64def864b8ca6b41acb45e078959ccd91e833b84b83b4` |

Complete mobile and shared file lists and hashes matched before and after the gate. This validation document was added after the gate and is not part of its source manifest.

## Observed checks

- **557 JVM tests in 50 suites**, with zero failures, errors or skips. Counted from actual Gradle XML reports.
- **111 mobile native host tests**, locked and offline with Rust 1.96.0.
- **Three offscreen Vulkan retained-frame tests** on the reported RADV adapter. These exercise destination opacity, retained ownership, and both live-energy sides on source reset.
- Android debug unit compilation, lint, app APK, androidTest APK, `checkEngine`, and release root-launcher packaging succeeded through the Gradle wrapper.
- Source production-boundary gate succeeded. This run did not sign or install a production artifact.
- Focused original-module color tests passed **16/16**, including all 13 prior tests.
- `git diff --check` passed.

The new color tests exercise inactive RGB edits, generated-owner deletion, and unselected deletion with identity/bag remapping in TRACK and TIMER. Duplicate RGB and selected-deletion regressions remain enabled.

Production History tests keep an old candidate behind a bounded barrier and verify that it cannot clear current-token pending state. Only the current application commit clears it. UI policy tests distinguish pending from held state and actual audio activity. A source-adapter assertion checks actual post-`present()` commit ordering, JNI bit forwarding, visible Activity status, and HUD refresh/cleanup wiring. That assertion does not execute Android callbacks.

## Limits and follow-through

The immutable color round-2 report remains 7/10 for its original checkpoint. These corrections need a separate independent assessment. R13's earlier 6/7/7 scores remain unchanged.

Application-present acknowledgement is not physical scanout or a source-age measurement. Buffered AudioRecord data can still predate a producing-read epoch. The controlled source-time experiment, actual Android/HUD behavior, SoundCloud stereo, audibility and latency remain unproven.

Ben's S25 remains undisturbed while he sleeps. The offered ASUS has not yet been identified through ADB. No device command or device mutation occurred in this gate.

## Independent follow-up, 2026-09-08 10:03 UTC

The separate color correction assessment accepts section 6 at **8/10 for exact `73e13eaf6a65df94557f8eb8107e5a44252a13bd`**, after 16 production native tests and three additional finite continuation cases. The original 7/10 reports remain unchanged. Retained addendum: `critiques/section-06-round-02-addendum.md`, SHA-256 `d43fd4995085090284b4cdec84a36667fd117cc28b42d9d3c58ebff9f048b49b`. Android acceptance remains open.

The separate present-completion audit supports the narrow application-present boundary with an important qualification: bit8 means **LIVE requested and not yet acknowledged**. HOLD suppresses that bit without acknowledging the interrupted LIVE request. Only a current-token commit acknowledges LIVE, but HOLD can also make the exported pending predicate false. SurfaceHost's `presented` callback means attach readiness, not a frame acknowledgement. Native History supplies the frame acknowledgement, sampled by the visible adapters.

That source-only addendum is `critiques/section-05-correction-04-addendum.md`, SHA-256 `8d4b874ad037bd684194a4c3a38e19e06bdcead47342970e1830c3e42fb0656f`. It ran no tests or device commands. It preserves R13's 6/7/7 scores and does not consume the reserved fourth full review. Old first submissions after the CPU request remain possible while LIVE is pending. Source age and physical scanout remain unproven.
