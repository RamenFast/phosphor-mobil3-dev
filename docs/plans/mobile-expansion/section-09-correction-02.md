# Section 9 correction 02: settings control access

Written before runtime changes on2026-09-08. Baseline4a0e8b6. Maple's374326f original review remains independent and immutable.

## Outcome and affected paths

Expanded SETTINGS controls must expose the same authoritative actions to touch, keyboard and accessibility. The existing pointer-only sliders,40dp small chips,44dp flat keys and clipped single-line labels do not meet that outcome.

Use one SETTINGS-only composition context around its SheetHost. Outside that provider, shared controls keep their existing layout and semantics. Inside it:

- Flat keys and chips use at least48dp height with content-driven growth and wrapping labels. The scope is SETTINGS, not a global restyle.
- Their button semantics expose selected state rather than relying only on border color. Scalar and pointer-range lanes also use48dp height inside SETTINGS.
- Scalar rules expose their actual label, value, finite numeric range and set-progress action. Arrow keys change one percent of the allowed interval. Home/End choose its endpoints. Keyboard and accessibility call the same supplied setter as pointer scrubbing.
- Two-thumb ranges keep the existing pointer lane and add separately focusable lower/upper value controls. Their legal ranges are min..currentUpper and currentLower..max. Neither action can cross the other endpoint. Labels identify BEAM RANGE and GLOW RANGE instead of two indistinguishable RANGE controls.
- The arm row exposes checkbox state and has a48dp minimum hit height. A range change does not silently change its armed state.
- Labels and status may grow vertically at larger fonts. Range/value labels remain readable within one scroll owner. Closing/collapsing still removes their focus nodes.

No source, tuning ownership, import, guard, activation or dismissal action is replaced. This is not a new preference or persisted appearance setting.

## Verification

Test the actual range-action implementation for finite rejection, clamping, no-op, endpoints, increments, both independent range endpoints and exact callback count. Exercise the actual semantic set-progress callback where the cached Compose API permits host inspection. Check source wiring for the single SETTINGS-only provider, unchanged pointer callbacks, minimum targets and retained action inventory. Run the full frozen Android dual-APK/unit/lint/native/GPU/source boundary gate.

Real TalkBack traversal, key delivery, measured48dp layout, large-font pixels, nested scroll and close behavior still require ASUS acceptance. Pure math, source checks and Android compilation cannot replace those observations. If they cannot be run, retain that exact limit instead of claiming accessibility acceptance.

## Intermediate checks

Gate797501qref passed732 JVM cases,126 native and three offscreen GPU cases, plus Android/lint/dual-APK/source checks. It validated actual semantic set-progress execution but preceded explicit selected-state and slider-height additions.

Gate9794039m24 then passed732 of733 JVM cases. The single failure was the old SliderGeometryTest source-string assertion requiring an unconditional44dp lane. The SETTINGS-only48dp branch intentionally changes that string. The corrected assertion retains the unchanged44dp fallback, shared geometry, one recognizer, two pointer lanes and no overlay tap handler. Failed XML is retained in `settings-access-selected-integration-1343-jvm-results.tar.gz`, SHA256 `399cbb6946a88f16ad50e4a6522b0bc3f678c26daa944dab456c307643512d6b`. The full gate must pass again before acceptance of this correction.

## Final integration observed13:46UTC

Gate1246216zt4 passed733 JVM tests in63 suites,126 native tests and three offscreen GPU tests. Android compilation, both APKs, lint, engine/release-helper and source-boundary checks passed. Complete source manifests stayed unchanged. Seven actual SettingsControlAccess tests passed, including invoking the production Compose semantic node's set-progress action and reading its label/range and selected button state. These are real host modifier nodes, not a copied semantic implementation. No Android key dispatch, TalkBack, focus tree, measured target or pixels were exercised.

The38 authoritative action names and42 references match4a0e8b6 exactly. The legacy pointer setters remain unchanged. SETTINGS uses explicit48dp lanes and growing48dp-minimum keys, selected state, keyboard focus borders, finite range actions and separate lower/upper endpoints. Shared controls outside SETTINGS retain their previous layout. Source placement above hidden/PiP branches was separately gated in correction01.

Evidence prefix: `dev/scratch/mobile-expansion-20260908T001819Z/settings-access-final-integration-1345`.

| Artifact | SHA256 |
| --- | --- |
| Runner | `5eda874c5b56727308def2ae814373ca3e0c215dfe803c4a15f8f2c214e71f8c` |
| App APK | `c85ea8aa2cbfd9f06db60436873ae9a71891a8d002e13377893c2c427029a52a` |
| androidTest APK | `390aad2c5a8a7a39fa38b1d33f972c19966f00ea31a037ed6f82c8da8fa86b09` |
| JVM XML archive | `bd98065e495cafe45c9af170a084dcb975b7b1fa1be140f9b87fa4fb7b2d8e52` |
| Mobile source manifest | `37aaf7a874adff7edf310ecff1a422bd0f1abc037404a290c01cfd15b2122d2f` |

No APK was installed. Maple's original7/10 report remains unchanged. Its U1 nested-callback ordering risk and actual narrow/large-font/gesture acceptance remain open for the authorized ASUS workflow. This is working-source integration, not a clean final reviewed installation freeze.
