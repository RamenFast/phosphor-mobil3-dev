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

## 8. Picture-in-picture

Picture-in-picture must retain the live scope and current source truth.

Android 12 and newer may use automatic entry. Android 10 and 11 use a compatible manual entry path.

The app must update the source rectangle and aspect ratio after configuration changes.

## 9. Themes, beam, and grid

Existing rooms and beam controls remain available. The cleanup must not replace them with generic Material surfaces.

Theme, beam, grid, glow, motion, and photosensitivity settings must persist without retaining behavioral history.

Controls must keep sufficient contrast against both black and tinted grounds.

## 10. Haptics and sound

Existing haptics remain restrained and purposeful. The cleanup does not introduce a new UI-sound system.

A future sound system requires a separate specification for categories, accessibility, and defaults.

## 11. Accessibility and disclosure

Permission copy must name the feature, the data path, and the system surface that opens next.

The privacy surface must be reachable in-app. It must state that Phosphor has no analytics, ads, behavior tracking, or silent reporting.

The remote surface must state that the relay requires Tailscale and must not be exposed to the public internet.

## 12. Performance

The scope should render at the selected supported panel cadence without allocating or crossing JNI per frame.

Touch feedback must begin in the frame that accepts ownership. Long work must not block the main thread or relay control loop.

Lifecycle transitions must release surfaces, capture sessions, players, callbacks, and relay links without leaks or overlapping owners.
