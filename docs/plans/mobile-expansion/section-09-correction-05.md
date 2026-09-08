# Section 9 correction05: intentional child input

Written before code on2026-09-08 at14:48UTC. Bat's pinned5d4d19d source trace shows that a slider's consumed movement can look like pending body scrolling to correction04. Its older slider fixture did not pass the actual consumed Final flag. The original correction assessment will remain unchanged.

Settings-only controls now explicitly identify their fresh down before any consumed move. A CompositionLocal carries the existing SettingsSheetDismiss owner into the Settings body. A non-consuming Initial observer on SliderLane and appearance editor text/custom drag inputs cancels only that matching pointer's dismissal ticket. It does not consume movement, change child scrolling/selection, latch the opening, or change a tuning value. An unrelated pointer ID does not cancel another ticket. Fresh later gestures on the body/header can still dismiss normally.

Do not apply this observer to the whole editor, chapter/group or scroll container. It belongs on intentional input controls only. Other sheets have no supplied Settings owner and receive the original modifier unchanged. The body queue ambiguity latch remains strict. A child must not clear a prior ambiguous body latch or resurrect its old callbacks.

Pure owner tests exercise actual consumed slider frames after the fresh-down exclusion, wrong-pointer exclusion, subsequent header/body qualification, and preservation of a previous ambiguity latch. Source assertions verify SliderLane's modifier precedes its real seek gesture and the new observer neither consumes input nor emits values. Appearance provider/text-field wiring is authored by Ant and belongs to the combined Android gate after release. Real pointer dispatch, text selection, sliders and accidental dismissal remain device checks.

## Coordinator evidence at15:03UTC

Actual pure SettingsInteraction/SettingsGestureAdapter production and tests passed54 checks in `run-settings-child-host-1449.sh`. Runner SHA256 `dddc8fff4e5e9c5c910953f818f3e1ba616be0ef8a931664a833904682b75498`. Source inventories matched before and after. The Android adapter/SliderLane assertions read source, not target callbacks. Ant's provider/editor integration remains outside that host runner and requires the released combined Android gate. No phone action occurred.

The original bounded correction04 investigation is retained byte-identically at `section-09-correction-04-investigation.md`, SHA256 `62f10cbdc0ec64a93963a97f0c9cd2ebd3fbd667f5191b6668e4b6279a75ce59`. It accepts only the two finite queue cancellation schedules at5d4d19d and records the child-input issue fixed here. It assigns no new full-review score. Android input ordering, usability and physical focus remain unaccepted.
