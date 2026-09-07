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

The existing 0.001 relative pinch threshold rejects jitter, not slow deliberate movement.
Retain the last applied distance while individual samples stay below that threshold so their movement accumulates.
Blocked and rebase events replace the reference distance. Movement made during exclusion never applies later as a jump.
Zero or invalid distances reset the reference without publishing a scale. Keep gain limits and view-lock behavior unchanged.

If any pointer enters the existing 88dp Android bottom-edge band, scope gain and orbit must not change. The one-finger armed upward console pull remains available from that band.

When `double_tap_playback=false`, the tap recognizer must have no double-tap handler. Disabled double tap must not delay a single tap.

## 4. Sheets and popouts

A sheet must enter from the physical edge associated with its control and current orientation. UI-locked landscape is the explicit exception: it enters from viewer-right in both landscape directions. In UI-locked portrait with a relative half-turn, local-top entry and anchoring preserve the physical control edge instead of arriving from the opposite edge.

The sheet must respect safe drawing insets, display cutouts, and rounded physical corners. Android 10 and 11 devices without rounded-corner metrics use safe-drawing fallbacks.

Back, scrim, close control, and dismissal gestures must converge on one completion path. The first committed dismissal retains its direction and actually applied displacement until completion. Header and content-remainder drags continue along their tracked local positive-Y axis from that displacement. Back, scrim, and close use the current entry edge. A pull restored before commitment does not select a later exit direction. Later pointer callbacks and queued tracking or restoration work must not move the committed base. A running restore stops at commitment, and repeated close requests do not redirect the exit.

The quick popout stays anchored above the measured console until an explicit action or dismissal drag closes it. Its viewport uses the remaining safe height in the current rotated chrome frame. All destinations and quick toggles, including AUTO PiP, share one vertical scroll owner. Content consumes normal scrolling first. Only a downward remainder at the top pulls the existing reveal closed. Reversing that pull restores the popout before scrolling content. A content fling must not initiate dismissal. Short frames and larger text must retain scroll recovery without shrinking the 44dp slider lanes.

## 5. Rotation authority

Android system rotation lock has final authority.

When system rotation is locked, the app must show its dependent orientation controls as unavailable and explain why. The app must preserve the current usable orientation instead of fighting the system.

System authority is transient, not a saved app preference. Activity requests must hold the observed current orientation, never impose a stored cardinal while Android rotation is locked. Chrome, labels, sheets, and beam must retain their current rotation. Imports may update saved app lock choices, but must not change the held presentation. Disabled controls must also reject stale setter callbacks before changing preferences.

The existing foreground tick and lifecycle callbacks refresh system authority even when gravity does not change. When Android permits rotation again, the existing app preferences and detent resume. Unknown gravity must not invent a cardinal. Detent tolerances and gravity filtering remain unchanged.

Each Activity owns its gravity listener. Destruction clears that ownership before unregistering the listener. Queued sensor callbacks and rotation mutations must reject retired task/Activity owners. Replacing an Activity within one process must leave exactly one active listener for the replacement, not retain the old Activity or let it change native beam orientation.

The foreground tick refreshes rotation authority unconditionally. Android owns music volume. The app does not poll or display a volume fraction.

### Grid visibility acceptance, 2026-09-06

Ben rejected the faint grid again during actual device testing. The grid must be readable at the user's low display brightness without brightening the background or changing the beam. Change only shared minor and axis linear-light coefficients, equally in CPU and GPU. The first measured correction uses 0.035 and 0.08. Compare three grid-only captures against the prior coefficients at fixed brightness. In a clear region away from chrome and the resting point, minor line cores must exceed nearby background by at least 35 sRGB code values in their strongest color channel. Axes remain stronger than minor lines. Preserve Xy45 rotation and grid-off behavior. Pixel measurements are not Ben's visual approval.

Sensor cardinals and Android Surface rotation use opposite landscape conventions. Convert them once at presentation routing. On a portrait-natural display, aligned observations C0/D0, C270/D1, C180/D2, and C90/D3 produce zero relative rotation in every app-lock combination. A display pinned at D0 retains the existing C0/90/180/270 to q0/1/2/3 mapping. Screen targets, Compose rotation signs, and native rotation signs do not change.

On large screens, foldables, desktop windows, and multi-window layouts, the interface must remain usable when Android ignores requested orientation.

Layout uses observed configuration and measured constraints, not a requested or saved orientation as proof of display geometry. A system-driven resize may adapt layout without applying a new gravity rotation.

Safe drawing and rounded-corner insets are physical left/top/right/bottom edges, including under RTL. Map their union once into the actual applied frame. Chrome uses its applied quadrant. Nested sheets use the total chrome plus sheet quadrant. Odd frames swap the content gutters before calculating physical corner clearance. A popout that maps explicitly must request physical insets first, without an inherited second rotation. Zero rotation preserves existing safe and corner clearance.

The settings pull keeps its gesture host across recomposition. Each begin reads current applied chrome geometry and UI placement through updated state, not captured initial booleans or imported saved choices. Its provisional axis uses current chrome-frame dimensions. Odd sheet rotation swaps both dimensions and sheet landscape, preserving that axis choice. Actual measured card travel remains the final authority. System-held imports change saved preferences without changing this applied gesture frame until authority resumes.

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

The capture glyph and toggle use the latest observed platform playback state published through
the existing MediaSession extras. Media3 may optimistically change a controller's playWhenReady
and isPlaying before the source accepts a command. Those predictions cannot change the capture glyph.
Publish observed state on platform callbacks, clear it when capture retires, and resynchronize it
when an Activity reconnects. Missing observed state means unavailable, not predicted playback.
Ignore callbacks from retired Activity/controller bindings. Local and remote playback retain their existing truth.

The capture seek rule appears only when the active session advertises seek, supplies a real duration, and accepts the routed command. A non-seekable session must not show a dead seek rule.

## 8. Picture-in-picture

Picture-in-picture must retain the live scope and current source truth.

Android 12 and newer use the platform automatic-entry parameter. Android 10 and 11 use `onUserLeaveHint` as the automatic fallback.

The `pip_auto_enter` setting defaults to `true` and appears in quick and full settings. It controls automatic entry only. Manual picture-in-picture remains available in either setting state, and picture-in-picture must not depend on `linger_background` or `controls_always_visible`.

The quick popout's PiP action and full settings' ENTER PiP action request manual entry through the existing Activity action adapters. Changing or restoring `pip_auto_enter` must refresh Android's parameters immediately. The real Android callback remains the owner of visible PiP state.

The app must update the source rectangle and aspect ratio after configuration changes.

## 9. Themes, beam, and grid

Existing rooms and beam controls remain available. The cleanup must not replace them with generic Material surfaces.

Theme, beam, grid, glow, motion, and photosensitivity settings must persist without retaining behavioral history.

### Known defaults and portable tuning

Application startup migration uses HUD AUTO when no valid portable or legacy value exists. Valid existing modes and legacy Boolean choices retain their precedence, including explicit OFF. Saving local auto-gain uses the saved local preference or its accepted true fallback, never the relay's live display value. The existing relay command owner uses the same absent auto-gain and manual-gain defaults.

An absent preference uses the accepted instrument tuning: the existing AMOLED room, mode 1, beam 7, manual gain 1.8332275, local auto-gain on, grid off, HUD 1 and BAND 1 (automatic), fullscreen on, focus 0.3, geometry amount 0.6, beam range 6 through 20, and cycle 3 seconds. Scope rotation defaults to locked through the Activity's stored orientation and existing orientation owner, not only a UI flag. A missing locked orientation uses the current exact orientation. Existing valid tuning and explicit false values take precedence over these fallbacks. Manual gain remains separate from the live auto-gain readout.

Portable imports merge only validated provided keys into the existing typed preferences, then restore the instrument through its current owners. Missing keys in older archives must not erase existing values. The five independent settings retain their current defaults and surfaces: `pip_auto_enter=true` in full and quick settings, and `controls_always_visible=false`, `grid_data=false`, `double_tap_playback=true`, and `linger_background=false` in full settings. Runtime source, media metadata, consent, endpoints, and calibration remain outside portable settings.

The LIGHT LEG and archive accept finite cycle values from 0.1 through 60 seconds, including both endpoints. Existing photosensitivity confirmation and TIMER/TRACK semantics remain unchanged. Range strings require exactly two finite, ordered components within the control's bounds. Custom RGB requires exactly nine finite components in 0 through 1. No malformed or empty extra token may disappear during parsing.

Every LIGHT control that requests TIMER below one second uses the same photosensitivity guard, including a switch from TRACK. Without acknowledgment, publish a one-second TIMER to both UI and native state before showing the existing confirmation. KEEP 1 s or dismissing the sheet retains that safe value. Only explicit acknowledgment enables the pending faster value. TRACK remains exempt. For example, TRACK at 0.1 seconds followed by TIMER must show confirmation and run at one second, not silently start a 0.1-second timer.

Without validated custom RGB and a legal active count, restore selects `custom_count=0` in the UI and retires native custom mode through the existing preset reset path. Illustrative picker colors are not recovered user settings and must not become a seeded palette. Valid stored RGB slots remain available when their count is zero. Restored focus and cycle values reach the existing native setters without requiring another surface creation or a later custom-mode selection.

An export snapshots effective controls even when the user has not edited them: background linger, view lock, custom activation count, cycle duration and TIMER/TRACK mode. Saving custom activation does not invent or overwrite absent RGB slots. A clean export therefore explicitly carries custom_count=0, a three-second TIMER, view lock off and linger off. Importing that export after changing those controls restores their exported defaults. Older partial archives continue to preserve keys they omit.

### Existing control behavior

FEEL, MOTION, CORNERS, and LABELS must change visible chrome immediately while their settings surface is open. The preview must not restyle the CRT beam or grid.

FEEL selects its canonical chrome defaults, including motion, density, prose font, corners, labels, and panel transparency. Explicit MOTION, CORNERS, and LABELS choices then override those defaults. Match follows the selected FEEL, or the displayed room when FEEL also matches. Changing MOTION must replace the prior duration scale rather than retaining another room's scale. Reduced motion has final authority over animation. One live sample row reads the same `LocalRoomStyle` as the surrounding chrome and offers a manual TRY action without a looping effect.

DECK must not remain a destination. Its existing queue and jump action live in SOURCE. SOURCE must not duplicate artwork or transport controls. A loaded local queue advertises Media3 COMMAND_SEEK_TO_MEDIA_ITEM so actual SOURCE row taps reach the existing track-switch handler. Native and JNI deck playback remain unchanged.

Ben removed the console volume feature on 2026-09-06. The console has no volume slider, VOL label, percentage, replacement control, or empty reserved row. Its measured height contracts around the remaining transport, MODE, SRC and overflow controls. Preserve their established shapes, spacing and hierarchy. Android hardware and system volume controls remain available without an app-owned volume adapter.

`controls_always_visible=false` keeps the current hide behavior. When enabled, it immediately reveals a hidden console and blocks timed, tap, Back, and cancelled-pull hiding. Sheets still own their modal surface, and PiP still owns its chrome-free layout. Every change to this setting invalidates a pending hide timer, including an enable-disable sequence before the old delay expires. HUD and band retain their existing modes and follow effective console visibility only in automatic mode.

Seek, tuning, and range controls must provide a 44dp touch lane, a sharp 2dp track, square thumbs, live beam accent, and tap-to-jump or nearest-thumb behavior.

All three rules use the same production hit and draw geometry, with inset endpoints that keep the square thumb inside the lane. Tuning and range labels sit above a full-width lane. Range chooses the nearest thumb at the initial pointer position, chooses the lower thumb on a tie, and clamps without crossing. Seek commits on release and discards interrupted scrubs. Vertical intent yields to the console pull or sheet scroll without seeking, and additional or consumed pointers cancel slider ownership.

The grid must remain visible against the tube. When `grid_data=true`, the status surface shows independent raw left and right amplitude with absolute dBFS. A left-only or right-only source must label the correct side. Xy45 rotates both trace and grid by 45 degrees. Other modes restore the Cartesian grid.

GRID DATA defaults to false. Its full-settings action, typed preference save and restore, and settings archive use the existing owners. It does not depend on the HUD setting or change the user's BAND visibility policy. The visible band shows one labeled line per channel. For example, raw L=0.5 and R=0 shows `L · 0.500 · -6.0 dBFS` and `R · 0.000 · −∞ dBFS`. Amplitude is a linear sample peak, not normalized display width. Absolute dBFS is `20 log10(raw peak)`, with full scale at 1. Missing or expired measurements show `no data`, not measured zero.

The existing foreground 500ms UI heartbeat consumes the native meter window through scopeStats when either HUD or GRID DATA needs it. The window keeps independent channel maxima for at most 500ms and is consumed by that read. Empty presentation frames do not erase real measurements between audio batches. Source stop and replacement clear the window under the same ring-before-meter lock order as the producer. Signal measurements and native open identities are transient and never enter preferences or archives.

Each remote connection attempt owns a distinct scope lease, including reconnects within one link generation. Both remote PCM writers recheck that lease and session liveness under the ring-before-meter locks. Setup also rechecks the boundary captured before connecting, so cancellation or a newer local, capture or microphone source cannot activate an old attempt. Current remote retirement clears pending samples and measurements immediately, without waiting for window expiry or worker joins. Late predecessor cleanup cannot clear a replacement source or its active flag. These boundaries do not reset auto-gain.

Grid angle follows the render thread's actual mode after its tube-flip switch, not a requested UI label. While remote geometry owns the trace, the current native session's existing K scope mode owns the angle. Unknown mode and retired sessions use angle zero. Protocol v2 does not pair K and G with a common mode revision, so this is the latest known remote mode, not a claim of frame-exact K/G ordering.

Auto-gain must hold its effective gain through raw peaks below 0.02 and release only on sounding frames. Only a proven new local item may reset the tracked peak. Metadata refresh, capture, and microphone input must not invent track boundaries. The 6.0 clamp, 0.92 headroom, and 0.05 glide remain unchanged.

The same single raw stereo drain feeds GRID DATA, the geometry envelope and auto-gain's `max(L,R)`. Malformed non-finite stereo pairs and orphan samples do not create measurements or enter DSP. The ring retains an incomplete trailing frame until its partner arrives. Valid complete all-zero frames remain measured silence. Auto-gain holds both tracked peak and effective gain below 0.02, including empty frames. Sounding frames retain the 0.999 tracked-peak release. Auto toggles do not reset tracked peak. A proven new item resets only tracked peak and keeps the current gain as its glide origin. Automatic target remains 0.1 through 6.0. Manual gain still reaches 7, including its held landing during silence.

Beam energy must remain stable through chrome motion, settings cycles, and surface recreation. Intentional modal scrim dimming is not a beam-energy change.

Chrome must not drive deposited beam brightness. The bottom-pull beam-breath driver, its native command, state, multiplier, and bloom-only gesture displacement are retired. The shared modal scrim remains 0.40. Real settings pulls, console and overflow scrolling, ordinary tube motion, and existing beam controls remain unchanged. This repair adds no beam-energy field or preference.

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

PlaybackService owns one screen wake lock for its published, ready, playing local source or its flowing remote link. Paused, ended, failed, unpublished, or retiring local playback cannot hold it. Remote connecting, greeted without frames, stalled, reconnecting, failed, stopped, or unavailable status cannot hold it. A link carrying measured silence remains live.

Remote flow requires valid media received in the current native session within the existing 3-second stall threshold. Welcome, heartbeat, metadata, and lifetime frame counters cannot acquire or renew media wake. Nonempty, complete stereo PCM frames remain live even when every sample is zero. Valid geometry with at least two finite points also proves flow without audio. Malformed audio or geometry does not refresh media freshness. Media stops become stalled even while control traffic continues. Valid media restores flow. Retarget, reconnect, cancellation, and disconnect cannot reuse a previous session's receipt. Optional loudness starts unknown in each session, not at a previous relay's measured silence. The existing native reader and watchdog own these facts. Socket freshness still owns the 10-second dead-link decision, without another timer, poller, or service.

Local terminal truth follows the current native deck, including files without duration and decode termination before the advertised end. The decoder's PlaybackEnded event follows a bounded drain wait, so it does not alone prove that a paused tail is empty. A started deck keeps that tail for resume. After observing decoder termination, an actual nonempty output callback that pops no samples proves the audible ring drained. Silence samples and earlier underruns do not prove completion. Oboe's stopped-stream error callback separately reports output failure through the same event owner. Neither observation adds a timer, amplitude test, or position-stall heuristic.

The existing serial worker reads these events. Main-thread publication rechecks the exact Open object and latest request, including same-path seek and replacement. A drained deck publishes ENDED and stopped transport. A failed startup or output publishes an error and stopped transport. Later metadata or transport events cannot restore READY without successful native open or seek publication. Natural completion continues the existing queue when allowed, without granting wake to an unpublished next item. The existing known-duration watcher remains unchanged.

CaptureService owns projection and AudioRecord wake state. Starting or permission-only capture cannot acquire it. A started recorder with a live projection holds it even when samples are silent or captured metadata is absent. Projection revocation, reader error, stop, and destruction release it through the existing retirement paths. PlaybackService's capture face remains a mirror and cannot acquire another capture lock.

The Activity owns microphone wake state through its actual recorder start and stop callbacks. Retiring reader ownership is not recording liveness. Reader failures return to the owning main-thread stop path, and delayed failures cannot stop a replacement recorder. Activity destruction retires its microphone wake lock. This does not move microphone capture into a service or change its existing Activity lifetime.

A current microphone reader failure also clears its successful live mic face and gives a permission, audio-route, and retry remedy. This failure-only publication checks the current task and Activity, actual retained recorder, and current mic face, not an attempted source-selection token. Generic stop cannot clear another source's face. Cancelled file, folder, or capture requests preserve the live recorder's failure authority. A replaced recorder or source face rejects the old failure. Runtime source saves require actual recording as well as the mic face, so a failed recorder cannot persist as live from stale UI state.

Visible main and PiP surface/window flags mirror current live-source state. Start, focus return, surface creation, and the existing foreground UI heartbeat reassert that state. Source callbacks and that heartbeat clear the flags after stop. Activity stop clears visible flags without taking a live service's lock. Neither a visible idle scope nor background linger creates a permanent never-sleep mode.

The scope should render at the selected supported panel cadence without allocating or crossing JNI per frame.

Touch feedback must begin in the frame that accepts ownership. Long work must not block the main thread or relay control loop.

Lifecycle transitions must release surfaces, capture sessions, players, callbacks, and relay links without leaks or overlapping owners.
