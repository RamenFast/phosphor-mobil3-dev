# Phase 06: accessible controls that leave the beam alone

Entry: Phase 05 PASS. Accepted B1/B10/B12/B13/B14/B18/B19 gesture and layout rules remain fixed.
Outcome: controls expose their meaning and immediate feedback without replacing Phosphor's instrument design.

## ▸ 06.1 Give existing controls a complete semantic contract

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Controls.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Console.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Sheets.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/LightSheet.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt`.
NEW: `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/ui/InstrumentSemanticsTest.kt`.

Add labels, roles, selected/state descriptions, unavailable reasons and range actions to the existing custom controls.
Expose each range thumb separately with non-crossing SetProgress actions. Seek requires actual seek capability and real elapsed/duration values.
Semantics and touch call the same production action once. A disabled session must not expose a working-looking dead action.

Avoid duplicate icon/text announcements and per-frame live-region updates.
Traversal order is SOURCE, transport, mode, light, then settings. Sheets receive focus and return it to their invoking control on dismissal.
Hidden chrome stays outside accessibility traversal. The beam is one labelled surface, not thousands of pixel nodes.

Preserve 44dp acquisition lanes, sharp 2dp tracks, square thumbs and protected system-edge/stage regions.
Larger semantic hit regions must not overlap neighboring actions or capture stage gestures.

✅ Compile and run the guarded real Compose class, then manual D5:

```bash
cd "$M"
./gradlew --no-daemon :app:assembleDebugAndroidTest :app:lintDebug
export TEST_CLASS=dev.phosphor.mobil3.ui.InstrumentSemanticsTest
run_device_case
```

Expected: names, state, progress, focus return, hidden-state exclusion and exactly-once callbacks pass on actual controls.
Keyboard/D-pad, TalkBack and Switch Access observations remain explicit D5 evidence, not inferred from node inspection.

## ▸ 06.2 Separate chrome accessibility and beam energy

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Palette.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Motion.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`.
NEW: `M/app/src/test/kotlin/dev/phosphor/mobil3/ui/ChromeContrastTest.kt`, `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/ui/InstrumentFeedbackTest.kt`.

Set chrome contrast floors at 4.5:1 for ordinary text and 3:1 for essential nontext/focus boundaries.
These are proposed acceptance requirements, not measured current claims. Test all accepted rooms, black/tinted grounds and extreme beam accents.
Repair chrome tokens or add stable non-color cues. Do not recolor the beam or reset the accepted AMOLED default.

Keep sharp corners, carved important controls, flat secondary detail and persistent click-to-dismiss popouts. Add no pills or generic Material redesign.
Use the Compose clock to verify feedback on the frame that accepts ownership. Refresh reduced-motion state on resume for Compose and native effects.
Check large font, landscape, cutouts and multi-window. No clipped action label or hidden focus is accepted.

Compare chrome and beam ROIs separately using Phase 04 fixtures. Modal scrim is intentional, but post-dismiss beam parameters must remain identical.

✅ Run contrast, actual feedback and surface-inclusive D6:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest --tests dev.phosphor.mobil3.ui.ChromeContrastTest
./gradlew --no-daemon :app:assembleDebugAndroidTest :app:checkEngine
export TEST_CLASS=dev.phosphor.mobil3.ui.InstrumentFeedbackTest
run_device_case
```

Expected: measured contrast floors pass, immediate values/focus remain visible, and reduced motion updates without suppressing state changes.
Opening/dismissing controls does not change fixed-scene beam energy or retained nondefault tuning. Ben's required visual acceptance remains separate.

↩ Rollback: revert feedback/token activation, then semantic wiring. Restore compatible exact debug APK if installed.
No preference schema changed. Preserve existing room choices and before/after images.
⛔ No UI-sound system, accessibility-service permission, beam palette migration or stage-gesture weakening.
