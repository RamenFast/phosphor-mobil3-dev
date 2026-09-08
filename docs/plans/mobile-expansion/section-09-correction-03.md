# Section 9 correction03: primary focus and ordering investigation

The original a38f39e round2 report remains unchanged at7/10. Its SHA256 is ecca882edbfc723788bb0f98c14bfd9bd47cae346f73fd0a5d13b02af31fb29c. This correction is written before code at14:16UTC. It does not change the original round1 score or claim Android acceptance.

## D1 primary focus cue

Inside the Settings-only access context, FlatKey installs the existing settingsFocusBorder modifier before its clickable focus target. The modifier already observes focused state and paints a2dp accent outline with drawBehind. Keep active/pressed feedback, semantics, labels and authoritative callbacks unchanged. Non-Settings FlatKey keeps its current modifier path.

The focused border is independent of selected state and press state. A focused, inactive PAUSE DISPLAY, RESET INSPECTION, PiP or HUD action must therefore have a visible cue. Source-linked host checks verify the actual conditional modifier and ordering plus the existing draw branch. They do not prove attached Android focus delivery or rendered pixels. Actual keyboard traversal, focused/unfocused screenshots and activation of those actions remain in device acceptance.

## U1 finite investigation

Do not relabel the report's delayed last-move schedule as a reproduced Android failure. A separate bounded read-only investigation resolves the exact cached Compose scrollable coroutine/queue ordering and dependency identity. Root will retain its source evidence separately, then choose the smallest proven correction or record the exact unavailable acceptance. No change to opening thresholds, slow/flick distances or post-fling close ownership is authorized by this note alone.

## Observed coordinator gate

Gate5457814owf passed on2026-09-08 at14:27:07UTC:803 JVM tests,126 native tests,3 offscreen GPU tests, Android compilation/lint/dual APKs, engine and source boundaries. Both repositories' source inventories remained unchanged. The new actual FlatKey conditional-focus wiring assertion passed. Exact artifacts and the combined appearance gate boundary are retained in section-10-core-integration.md. This closes the authored focus path within source/build scope only. Herb's cached-dependency U1 investigation is still pending. Original7/7 reviews and Android acceptance remain unchanged.
