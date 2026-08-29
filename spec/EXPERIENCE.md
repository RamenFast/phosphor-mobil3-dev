# Experience specification

## 1. Instrument hierarchy

The signal is the primary surface. Controls must not divide the display into a permanent dashboard.

The app uses one activity with a full-bleed scope surface and transient Compose chrome. A visible state must have one clear owner.

## 2. Motion

Motion must explain where an element came from, who owns the gesture, and what state changed.

A dragged sheet follows the finger without a delayed catch-up. Release motion continues from the measured position and velocity. Dismissal leaves in the same direction as the controlling gesture.

Reduced motion must remove decorative travel and repeated effects. It must preserve state feedback and touch ownership.

## 3. Gesture ownership

One recognizer owns each pointer sequence.

Priority order:

1. An open sheet or popout owns gestures inside its bounds.
2. A direct control owns a gesture that started on that control.
3. Edge and console gestures own their documented activation regions.
4. Scope gain or orbit owns only the remaining stage gesture.

A scope adjustment must not activate while the user is opening, moving, or dismissing chrome.

The stage recognizer owns scope gain and orbit arbitration. It must map the console, active sheet, and overflow bounds through the current rotation, then add a 24dp exclusion margin. Scope pinch and drag remain blocked through exactly 333ms after that chrome moves or dismisses. This block must not consume chrome reveal or an armed mode-6 console pull.

If any pointer enters the existing 88dp Android bottom-edge band, scope gain and orbit must not change. The one-finger armed upward console pull remains available from that band.

When `double_tap_playback=false`, the tap recognizer must have no double-tap handler. Disabled double tap must not delay a single tap.

## 4. Sheets and popouts

A sheet must enter from the physical edge associated with its control and current orientation.

The sheet must respect safe drawing insets, display cutouts, and rounded physical corners. Android 10 and 11 devices without rounded-corner metrics use safe-drawing fallbacks.

Back, scrim, close control, and dismissal gestures must converge on one exit path.

## 5. Rotation authority

Android system rotation lock has final authority.

When system rotation is locked, the app must show its dependent orientation controls as unavailable and explain why. The app must preserve the current usable orientation instead of fighting the system.

On large screens, foldables, desktop windows, and multi-window layouts, the interface must remain usable when Android ignores requested orientation.

## 6. Capture consent

The source surface must explain microphone permission before requesting it.

When playback capture starts:

1. Request microphone permission if playback capture needs it.
2. Open Android's MediaProjection consent.
3. Start the typed foreground service.
4. Publish starting, flowing, silence, revoked, and error states honestly.

On Android 14 and newer, the capture request selects the complete default display. On Android 10 through 13, the platform prompt already captures the complete display.

The app must not reprompt while a valid capture session is flowing.

## 7. Source truth

The interface must distinguish:

- no selected source
- permission required
- capture starting
- capture flowing with audio
- capture flowing but silent
- remote connected with no sound
- remote stalled
- remote reconnecting
- unsupported or opted-out content

A black beam alone is not enough to communicate these states.

Captured-media title and artist must come from the active Android MediaSession. Generic capture remains `everything playing` when no session metadata exists. The in-app play/pause glyph must match the captured session across playing, buffering, paused, and resumed states.

The capture seek rule appears only when the active session advertises seek, supplies a real duration, and accepts the routed command. A non-seekable session must not show a dead seek rule.

## 8. Picture-in-picture

Picture-in-picture must retain the live scope and current source truth.

Android 12 and newer may use automatic entry. Android 10 and 11 use a compatible manual entry path.

The `pip_auto_enter` setting defaults to `true` and appears in quick and full settings. It controls automatic entry only. Manual picture-in-picture remains available in either setting state, and picture-in-picture must not depend on `linger_background` or `controls_always_visible`.

The app must update the source rectangle and aspect ratio after configuration changes.

## 9. Themes, beam, and grid

Existing rooms and beam controls remain available. The cleanup must not replace them with generic Material surfaces.

Theme, beam, grid, glow, motion, and photosensitivity settings must persist without retaining behavioral history.

FEEL, MOTION, CORNERS, and LABELS must change visible chrome immediately while their settings surface is open. The preview must not restyle the CRT beam or grid.

DECK must not remain a destination. Before removal, its existing queue and jump action move to SOURCE, and its volume control moves to the console. `controls_always_visible=false` keeps the current hide behavior. When enabled, it blocks both timed and tap-to-hide paths.

Seek, tuning, range, and inline volume controls must provide a 44dp touch lane, a sharp 2dp track, square thumbs, live beam accent, and tap-to-jump or nearest-thumb behavior.

The grid must remain visible against the tube. When `grid_data=true`, the status surface shows independent raw left and right amplitude with absolute dBFS. A left-only or right-only source must label the correct side. Xy45 rotates both trace and grid by 45 degrees. Other modes restore the Cartesian grid.

Auto-gain must hold its effective gain through raw peaks below 0.02 and release only on sounding frames. Only a proven new local item may reset the tracked peak. Metadata refresh, capture, and microphone input must not invent track boundaries. The 6.0 clamp, 0.92 headroom, and 0.05 glide remain unchanged.

Beam energy must remain stable through chrome motion, settings cycles, and surface recreation. Intentional modal scrim dimming is not a beam-energy change.

Controls must keep sufficient contrast against both black and tinted grounds.

## 10. Haptics and sound

Existing haptics remain restrained and purposeful. The cleanup does not introduce a new UI-sound system.

A future sound system requires a separate specification for categories, accessibility, and defaults.

## 11. Accessibility and disclosure

Permission copy must name the feature, the data path, and the system surface that opens next.

The privacy surface must be reachable in-app. It must state that Phosphor has no analytics, ads, behavior tracking, or silent reporting.

The remote surface must state that the relay requires Tailscale and must not be exposed to the public internet.

## 12. Performance

The main scope and picture-in-picture must remain awake while a playback or capture source is live. Wake ownership follows the live source, not picture-in-picture visibility or background linger. After the source stops, the display must become sleep-eligible promptly.

The scope should render at the selected supported panel cadence without allocating or crossing JNI per frame.

Touch feedback must begin in the frame that accepts ownership. Long work must not block the main thread or relay control loop.

Lifecycle transitions must release surfaces, capture sessions, players, callbacks, and relay links without leaks or overlapping owners.
