# Experience specification

**Status:** Binding
**Design thesis:** Sharp Stone, Living Signal

## 1. Motion grammar

Motion has four legitimate causes:

1. direct user manipulation;
2. accepted state transition;
3. real audio or beam energy;
4. real connection, agent, or lifecycle state.

An animation without one of these causes is decorative theater and SHOULD be removed.

### 1.1 Motion roles

| Role | Behavior |
|---|---|
| Direct manipulation | One-to-one with the finger, no easing while held |
| Settle | Velocity and distance aware after release |
| State change | Short transition that names the old and new state |
| Signal response | Derived from drained audio/beam values, stops on silence |
| Presence | Derived from attached Nexus session and real commands |
| Error/recovery | Communicates failure, backoff, retry, or repair |

Room personality may change the settle rail, sound, tint, and dimensional treatment. It MUST NOT fork control logic.

### 1.2 Reduced motion

Reduced motion keeps:

- direct tracking;
- visible selected state;
- provenance;
- capture/link truth;
- static signal tint where needed.

It removes or shortens travel, breathing, overshoot, and parallax. A reduced-motion cut should complete within 80 ms where a transition is necessary.

## 2. Sheet behavior

### 2.1 Held state

`position = pointer displacement`, sampled synchronously. A 200 px finger movement produces 200 px plus or minus 2 px of sheet travel in instrumentation.

Do not call `Animatable.snapTo` through a newly launched coroutine on every pointer delta. Use raw drag state during ownership and hand the final position/velocity to animation only on release.

### 2.2 Release state

Open/close is decided by velocity plus a 40 percent travel threshold. Fast intentional flick may win below threshold. Slow release below threshold returns.

Dismiss paths are exactly:

- close button;
- scrim tap;
- drag past dismiss threshold;
- system Back.

Pointer drift outside bounds is not dismissal.

### 2.3 Edge and orientation

- Portrait primary sheets rise from the bottom.
- Landscape primary sheets enter from the screen-right edge.
- Both landscape quadrants use the same screen-relative right side.
- Insets are transformed into the chrome coordinate space after rotation.
- Enter and exit use the same edge.

## 3. Rotation settings

The settings model exposes:

```text
system_rotation = locked | unlocked | unknown
system_quadrant
scope_lock.desired
scope_lock.effective
ui_lock.desired
ui_lock.effective
authority = android | phosphor
availability
fix
```

When Android is locked, app controls are greyed but readable. Copy example:

> Android rotation lock is holding this view. Unlock rotation in Quick Settings to let Phosphor change it.

The app does not open hidden settings or use sensor authority to silently defeat the system decision.

## 4. Gesture arbitration

### 4.1 Ownership order

1. Android system edges
2. Active sheet or scrim
3. Compose/drawing mode
4. Visible control
5. Stage

Once recognized, ownership cannot transfer. A pinch cannot begin after a one-finger gain action already mutated state. A sheet touch never leaks to the stage.

### 4.2 One-finger arm

State machine:

```text
idle -> candidate -> armed -> adjusting -> completed
                 \-> cancelled
```

- Candidate begins after a stage contact that is not a tap/double-tap decision yet.
- A 1500 ms ring is centered on the original contact.
- Movement beyond cancellation slop before completion cancels.
- Second pointer cancels candidate and transfers to the two-finger recognizer before any one-finger mutation.
- On arm, haptic and subtle ring completion occur in the same frame.
- The ring follows room tokens but remains high contrast.
- View lock disables candidate creation and shows a brief locked indicator instead.

Sensitivity after arm is density-independent and based on normalized stage distance, not raw pixels.

## 5. Living chrome and beam state

Chrome liveness states:

| State | Cause | Visual behavior |
|---|---|---|
| `live` | valid source, flowing audio above floor | beam tint and allowed energy response |
| `hold` | paused or short transient silence | last tint held, no breathing |
| `no_signal` | connected source silent past window | tint drains to structural accent, resting beam |
| `absent` | no source/capability | structural chrome only |
| `stalled` | transport exists but data stopped | still warning state, never live breathing |

The UI MUST render `no signal · <source>` and a centered resting beam where appropriate. A stale trace is not a valid no-signal face.

## 6. Grid and ground editor

LIGHT settings add:

- grid enabled;
- grid color;
- grid alpha;
- grid major/minor balance if renderer supports it;
- ground/background color;
- ground alpha;
- ground tint amount;
- reset to room default.

On true black, the default grid must pass a visual threshold on the target S25 in a dark room while remaining subordinate to the beam. Theme packs may supply defaults, and user overrides remain separate from room defaults.

## 7. Beam color editor

### 7.1 Model

```text
kind = solid | multi_stop | random | spectrum_glide
stops[1..16+]
advance = manual | timer | track
interpolation = direct | full_spectrum
random.allowed_hues
random.banned_hues
random.saturation_range
random.value_range
```

Black is excluded from random beam generation. Very low luminance colors are either rejected or raised to a configurable minimum.

### 7.2 Controls

- `+` adds a stop.
- Reorder is direct and animated.
- Each stop supports manual entry and color picker.
- Random is a real checkbox/switch with immediate state feedback.
- `FULL-SPECTRUM GLIDE` is a separate switch.
- An undo/revert affordance appears after destructive edits.

## 8. Theme room and packs

### 8.1 Shelf

Six curated room tiles appear first. A separate `PACKS` section contains installed packs and an unambiguous `+` action.

Legacy first-party rooms are not deleted. They are importable as a maintained first-party pack and migrated automatically for a user currently selecting one.

### 8.2 Pack format

Use a ZIP-based `.phostheme` container with a mandatory `theme.json`. Individual rooms MAY export as `.phosroom` JSON.

Required metadata:

```text
schema
id
label
version
author
license
rooms
minimum_engine
assets manifest
```

Colors support hex or normalized RGBA. Unknown optional fields are skipped and reported. Unknown major schema versions refuse with a fix.

Validation covers:

- archive traversal and size limits;
- schema and enum values;
- token completeness/defaulting;
- contrast and focus visibility;
- bounded animation/workload;
- asset type and dimensions;
- duplicate IDs;
- license/source metadata.

Low contrast may be previewed with a clear warning but cannot become the default without an explicit user confirmation. Safety and trust surfaces always use protected system tokens that a pack cannot make invisible.

### 8.3 AI authoring

The theme screen includes an AI/star-circuit symbol with the label `MAKE WITH AI`. It opens a local explanation, copyable prompt template, manual schema, and GitHub guide. If Nexus is attached, `ASK NEXUS TO MAKE A ROOM` creates a proposed pack through the normal validation and preview path.

## 9. UI sounds and haptics

Sound categories:

| Category | Examples |
|---|---|
| Navigation | open/close sheet, move detent |
| Controls | mode/source/value detents |
| Commit | keep theme, confirm source |
| Live | arm/disarm capture, link established/lost |
| Theme | room settle |
| Warning | denial or invalid action |

Rules:

- zero ambient or idle sounds;
- no sound on high-frequency continuous changes;
- haptic, visual, and sound occur together for discrete events;
- volume is capped and routed as assistance sonification;
- respect system volume, calls, do-not-disturb behavior, and reduced-sound preference;
- do not request audio focus that interrupts music;
- suppress sounds from any audio analysis or recording feed.

## 10. OOBE script

### Beat 1, under 8 seconds

Copy:

> This is a vectorscope for your music. Left and right channels pull the beam. The shape is the stereo signal itself.

Play a bundled, quiet, band-limited stereo example and draw it immediately. A short captioned narration speaks the same explanation, starts at a conservative level, and has an always-visible mute action. The beam and geometry act as Phosphor's mouth by changing exactly with the narrated stereo example. Buttons: `MUTE`, `SKIP`, `CONTINUE`.

### Beat 2, under 8 seconds

Show two or three basic hints over the stage:

- tap for controls;
- choose a source;
- hold until the ring completes, then drag to adjust.

Offer `QUICK START` and `FULL TOUR`.

### Beat 3, under 10 seconds

Explain source truth:

> Phosphor can play files, use the microphone, connect to your computers, or visualize other apps. Android lets some apps block capture. If a source is silent, Phosphor will tell you what it knows.

Buttons: `PICK A SOURCE`, `ENTER PHOSPHOR`.

The first-run commercial and diagnostics setup may follow as a Phosphor sheet, but it cannot obscure the fact that the trial is free, does not auto-renew, and diagnostics can be off.

## 11. PiP

PiP entry is conditional on the PiP setting and a valid display/source state. It is not unconditional on Home.

Required behavior:

- on/off setting;
- auto-enter choice;
- play/pause action when the current source supports it;
- tap expands;
- no chrome beyond system PiP affordances and scope status;
- no-signal face on silence;
- agent field `display.mode = pip`.

## 12. Transparent overlay

The overlay is a separate user-owned window mode.

Controls:

- enable/disable;
- center color;
- center opacity;
- fade radius and softness;
- size and aspect;
- interactive/click-through;
- show minimal HUD;
- opacity cap;
- burn-in shift;
- quiet-state frame cap.

The overlay notification includes `HIDE`, `INTERACTIVE/CLICK-THROUGH`, and `OPEN PHOSPHOR` actions. Denial or unavailable policy state degrades to PiP without nagging.

## 13. Nexidex respondent

Phosphor HUD states:

- detached: nothing;
- authenticating: small still eye outline and label;
- observing: open eye plus session identity;
- driving: hand/beam interaction linked to the affected control;
- error/revoked: still, concise fix, then decay.

Nexus-driven controls animate exactly as human-driven controls do because both dispatch the same action. A small `NEXUS` provenance designator remains long enough to be perceived and is available persistently in details/audit.

## 14. Performance budgets

- Direct manipulation: target input-to-visual latency under one display frame, no intentional coroutine delay.
- Scope: preserve 120 Hz target where currently possible.
- ProjectM field: 30 or 60 Hz quality tier, never forces true beam below its minimum budget.
- UI animation: no sustained jank over 1 percent on target-device macrobenchmark.
- No per-frame JNI introduced by these features.
- No second audio capture path merely because PiP or overlay is visible.
