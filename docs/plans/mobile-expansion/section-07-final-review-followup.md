# Section 7 final-review follow-up: source recovery action

Written before code on2026-09-08. Rose's fourth and final full report at374326f scored8/10. Preserve that report and original7/7/7 scores. Do not open a fifth full review.

The final review found one low-priority recovery mismatch: native capability rejection can occur while a fresh Activity's remote-geometry mirror is false. Native correctly rejects without applying, but the preset sheet hides its direct SOURCE CONTROLS action.

Add a typed, transient `sourceControlsRequired` workflow state. A local capability refusal or exact native rejected receipt sets it. A new submission clears the prior indication before checking current capability. A stale receipt cannot change it. The Activity publishes that Boolean to the sheet independently of its source mirror. The existing source-navigation action remains unchanged and does not start a source automatically. Do not match status text to infer refusal.

Validation: drive the real workflow with a stale-capable UI and native rejection, then require no publication/persistence and the source-recovery flag. Test stale receipt and a later successful apply. Check actual Activity-to-sheet wiring, then run the frozen Android/native/GPU/dual-APK/lint/source gate. Android recreation, displayed recovery navigation and source continuity remain unexecuted until authorized device acceptance.

## Gate observed13:51UTC

Frozen gate382813l9fp passed736 JVM tests in63 suites,126 native tests and three offscreen GPU tests. Android dual-APK builds, lint, engine/helper and source-boundary checks passed. Both repository manifests stayed unchanged. Two new production workflow cases cover native refusal with stale UI capability, zero publish/save, late rejected receipt and later successful apply, plus local preflight refusal. One supplementary source test ties the typed flag to the actual Activity and sheet action. No test matches status text.

Evidence prefix: `dev/scratch/mobile-expansion-20260908T001819Z/preset-recovery-action-integration-1350`.

| Artifact | SHA256 |
| --- | --- |
| Runner | `d22033030d13e440c2d3760a6aa247a8db9c80b7750a5f34c18d6c1c6e2c1cf4` |
| App APK | `b7ec36823533d6e502db32d6f2005350ff0ff01f49faa4ce145b60d53c53c77e` |
| androidTest APK | `d602fd31a2a982ec770ff3e5ba01c1eea656b42a6af16aadf2aa6551efeebd36` |
| JVM results archive | `115b0c1da8f34ad80bb8886b32b04acdcebaf211c72d2d82a7313120a66e00e2` |
| Mobile source manifest | `c40ad64aab0e1554fb0dce121fcc4486da0ad36fa9eb29abd4df28c1c2881e70` |

This separate follow-up does not rescore the fourth full review. Its8/10 and original7/7/7 remain unchanged. No fifth full review is requested. Both artifacts are retained, not installed. Host-only ADB/USB inventory at13:51 found no ASUS. The S25 remains undisturbed. Android source-navigation/recreation and full preset acceptance remain open.
