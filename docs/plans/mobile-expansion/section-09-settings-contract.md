# Settings interaction contract

## Context and source boundary

This contract prepares approved section9, R10 and R11, before implementation. Canonical `MOBILE-EXPANSION-PLAN.md` remains authoritative. The inspected source is mobile `5b1be193e6fbcb0d3f779a8c0da5774a9a05efc6`. R17 is being implemented separately and its eventual Signal Check entry must be preserved during settings regrouping.

At this checkpoint, `ui/Sheets.kt` shares `SheetDismissState` and `SheetHost` among all sheets. Dismissal accepts raw72dp travel or920dp/s remaining velocity. Nested post-scroll accepts downward remainder without checking its source. Header cancellation calls the ordinary settle path. Those paths cannot distinguish deliberate settings dismissal from leftover motion. `SettingsSheet` creates its own `rememberScrollState`, so its removal from the sheet switch loses that local scroll owner.

Settings opening lives in the existing `PhosphorScreen` pull host and stage gesture policy. Leave its thresholds, flick velocity, finger-tracked reveal, physical/chrome coordinate routing and reduced-motion behavior unchanged. Do not raise the global `Dim.chromeFlickVelocity` or change other sheets to solve settings dismissal.

## Defined outcome

Settings closes through deliberate direct dragging, its close action, scrim or Back. Accidental content fling, programmatic scrolling, slider interaction and expansion cannot close it. Six full-width expandable sections keep existing controls available, with stable summaries, several sections open at once and retained presentation state. Expanding or returning to settings does not change source or tuning.

## Settings-only direct-dismiss owner

Use an explicit settings-only behavior selected by `SettingsSheet`. The default `SheetHost` behavior for source, mode, light, presets, room and manual remains unchanged. Share layout and close animation infrastructure, not a newly restrictive policy applied to every sheet.

One small host-testable owner distinguishes pointer lifetime, direct eligible travel, release and cancellation:

1. A fresh pointer gesture may become eligible on the header or from downward content remainder at the actual top boundary.
2. Content scroll consumes ordinary travel first. Only direct user-input remainder associated with the current pointer gesture contributes to dismissal.
3. Nested-scroll source alone is insufficient. Without a current direct pointer gesture, a fling, wheel, accessibility/programmatic scroll or expansion cannot arm dismissal.
4. Slider drag does not produce eligible content remainder. Multi-pointer changes, pointer cancellation, source loss, sheet retirement and geometry changes cancel the dismissal owner. Do not steal gestures from controls.
5. Record intentional raw travel separately from the displayed offset. The displayed offset follows a monotonic progressively resistant function. Its slope does not increase with travel.
6. Reversal first pays back raw displacement. Reaching zero disarms the partial pull. An upward release cannot count as a downward flick.
7. Decide once at the direct gesture's release, using its measured downward velocity and eligible travel. Do not turn `onPostFling` into a second commit opportunity.
8. Fast dismissal requires both a minimum intentional distance and a downward velocity threshold. Slow dismissal remains possible through a longer pull without a speed requirement.
9. Cancellation always returns. A below-threshold release returns firmly without an overshooting spring. Reduced motion snaps to rest. A committed close captures one exit edge and cannot be reversed by later callbacks.
10. No animation, delayed callback or stale gesture can dismiss a replacement sheet.

Initial engineering parameters must be named and independently testable. Start evaluation with192dp slow travel,64dp minimum flick travel and the existing920dp/s velocity magnitude, with a resistant scale of160dp. A candidate offset is `scale * raw / (scale + raw)`. These are provisional test inputs, not an accepted UX claim. Keep them settings-specific. On-device deliberate/accidental gesture trials must adjust them if needed. Preserve the longer slow alternative and explicit close for limited motion or small windows.

The pointer adapter must establish direct-gesture provenance without consuming a child slider or changing opening. Its tests must exercise adapter event ordering as well as the pure decision function. A pure predicate alone does not prove Compose nested-scroll provenance.

## Expandable settings structure

Use stable section IDs, not labels or list indices. Each header is a full-width row with an existing meaningful glyph, heading, current-value summary and chevron. The header toggles expansion only. Toggles, actions, navigation and sliders retain distinct forms inside the revealed body. Several bodies can remain open.

| Section | Controls and supported navigation |
| --- | --- |
| Signal & Startup | Existing source entry, current Signal Check entry, gain/auto-gain and source-relevant behavior. Keep relay-specific controls near relay context. Add startup controls only when R14 implements them. |
| Beam & Light | Mode-related tuning, focus, beam/glow and random ranges, grid/readout, LIGHT and INSTRUMENT PRESETS navigation. Preserve authored setup ownership and preset modified-state updates. |
| Display & HUD | HOLD/BLACK, explicit display-only LIVE/inspection reset, controls visibility, fullscreen, PiP, floating HUD and background choice. HDR and brightness controls join only when their real owners exist. |
| Motion & Performance | Frame rate, beam reconstruction rate, visible stats/band policy and existing reduced-motion controls. Do not relabel reconstruction as capture negotiation. |
| Appearance | Existing room/theme navigation and real current appearance summary. Preserve all legacy room identities until section10 migration. |
| About & Manual | Manual, license/about and explicit settings import/export with their existing status/error surfaces. Portable preset import remains separate. |

Do not invent enabled HDR, brightness, startup or mixing controls before those features exist. A summary may state unavailable capability, but cannot claim an unimplemented feature is off and working. Example after the relevant features are implemented: `Hold on pause · HDR off · HUD off · brightness system`.

Preserve every current settings action. A before/after control inventory must identify its new section and verify the same action adapter. Removing a duplicated visual heading is allowed. Dropping a control or silently substituting another setting is not.

The coordinator inspected the complete `SettingsSheet` body at `5b1be193` for this initial inventory. R17's released source must add its diagnostic row without removing any row below.

| Existing control or group | Destination | Adapter that remains authoritative |
| --- | --- | --- |
| GAIN, AUTO-GAIN, VIEW LOCK | Signal & Startup | `setGainAbsolute`, `setGainAuto`, `setViewLock` |
| FOCUS | Beam & Light | Existing `onFocus` routed to `actions.setFocus` |
| BEAM, beam range and die | Beam & Light | `setBeamEnergy`, `setBeamRandomRange`, `tapBeamRandom` |
| GLOW, glow range and die | Beam & Light | `setGlow`, `setGlowRandomRange`, `tapGlowRandom` |
| GRID, GRID DATA | Beam & Light | `setGrid`, `setGridData` |
| LIGHT, INSTRUMENT PRESETS | Beam & Light | `openLight`, `openInstrument`. Opening does not apply |
| HOLD/BLACK, display-only LIVE/pause, inspection reset | Display & HUD | `setPauseBlack`, `toggleDisplayPause`, `resetInspection` |
| Always-visible controls, double-tap playback | Display & HUD | `setControlsAlwaysVisible`, `setDoubleTapPlayback` |
| PiP preference and explicit ENTER PiP | Display & HUD | `setPipAutoEnter`, `enterPictureInPicture` |
| Floating HUD preference, background, explicit show/hide, status | Display & HUD | Existing four HUD actions and actual status. Preference does not start it |
| Background linger and its mic-lifetime explanation | Display & HUD | `setLingerBackground` |
| Fullscreen, scope rotation, UI placement and Android lock explanation | Display & HUD | Existing setters and lock queries. Preserve disabled state under system rotation lock |
| FRAME RATE, BEAM RATE and explanatory notes | Motion & Performance | `setFps`, `setOversample`. Beam rate remains reconstruction |
| In-app stats HUD mode, status BAND mode and errors | Motion & Performance | `setHudMode` and existing bounded `bandMode` transition. Distinguish stats from floating HUD |
| Relay latency profile and routing explanation | Signal & Startup, clearly labeled relay-only | `setRemoteLatencyMode`. Do not move connection controls into the preset owner |
| Room/theme navigation | Appearance | `openRoom`, retaining current room label |
| Whole-settings import/export and transfer status | About & Manual | Existing corrected import/export adapters. Do not bypass R16 stale-import or recovery ownership |
| Manual, license/about text and existing designator stamp | About & Manual | `openManual` and existing conditional presentation |

No new direct mutation of authored instrument state belongs in this regrouping. Source inspection checks can establish the action inventory, but actual touch, scroll and accessibility checks remain required.

## Presentation state and anchoring

Keep one settings presentation owner outside the conditional `Sheet.SETTINGS` body. It retains the expanded stable IDs and stable scroll anchor/offset. Returning from LIGHT, presets, appearance or manual, changing theme and ordinary rotation do not recreate that owner. Android currently handles orientation/size configuration changes in the Activity. If recreation support is added, save only this small UI state through the existing saved-state path, not portable tuning or instrument preset storage.

Use one full-width vertical reading order in portrait and landscape. Expanded bodies may arrange their controls responsively, but do not split section headers into two unrelated scrolling columns. Several expanded bodies share the same scroll owner.

On an expansion tap, retain the touched header's visible position where viewport bounds permit. Key any deferred anchor correction to that tap/layout revision. A newer gesture, toggle, close or recreation invalidates old corrections. Expansion animation is never a synthetic dismissal gesture. Collapse removes child controls from layout and accessibility focus instead of merely drawing them transparent.

Opening settings remains finger-tracked exactly as before. Retained expansion can make the body tall, but cannot change the opening decision law or create a close gesture from the opening finger's release.

## Accessibility and motion

Headers and primary actions have at least48dp targets. Headers expose meaningful expanded/collapsed state, heading and current-value text. Chevron direction is redundant with semantics. Focus proceeds header then revealed controls in reading order. Hidden controls cannot receive TalkBack, switch or keyboard focus. Large fonts wrap summaries without overlapping controls or hiding close.

Use flat aligned rows and hairline separators. Reuse current palette and glyphs. Expansion motion is brief and finite, disabled or simplified under reduced motion, and exists only while its visible owner is active. No infinite animation is needed for an idle section.

## Verification and follow-through

| Requirement | Minimum concrete check |
| --- | --- |
| Opening unchanged | Preserve exact opening constants and pure gesture/reveal behavior. Run existing stage/entry tests. Compare touched opening code against the inspected baseline and exercise real opening gestures on device. |
| Intentional dismissal only | Production owner fixtures cover no pointer, non-user scroll, fling tail, programmatic movement, slider, multi-pointer, reversal, cancellation, threshold boundaries, slow pull and qualified flick. |
| Correct adapter | Exercise pointer/nested-scroll ordering and one release decision, including an opening finger already down when the sheet mounts. No late callback closes another sheet. |
| Other sheets unchanged | Existing entry/exit tests pass and non-settings behavior retains its original configuration. Exercise source/light/preset/manual closes on device. |
| All controls retained | Inventory every old action and its new section, then verify actual action adapters and preset/source boundaries. |
| Stable presentation | Production state tests cover multiple open sections, return navigation, theme change, layout/rotation and stale anchor callbacks. Android checks confirm actual scroll/header position. |
| Accessible layout | Inspect semantics and run real large-font, keyboard/Back and available accessibility navigation. Measure targets, not only source constants. |
| Integration | Released immutable source passes unit/lint/native/engine/source gates and builds both APKs together. Independent section9 critique follows the existing R15 rules. |
| Device calibration | On the identified ASUS, compare deliberate slow/flick/header/top-edge gestures against accidental content flings, sliders and expansion, without changing phone volume or the opening law. Retain observed distances/results. |

**Blocked acceptance:** the authorized ASUS is absent. Host fixtures can establish the decision law and state ownership, not device gesture comfort or Compose callback delivery. **Next step:** implement the bounded settings-only owner and expandable surface after the active R17 writer releases, then run the smallest real gesture matrix when the identified phone reconnects. S25 remains undisturbed while Ben sleeps.
