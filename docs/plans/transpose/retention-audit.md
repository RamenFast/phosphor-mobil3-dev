# Functionality retention audit

Auditor for Prime · 2026-09-26 · code baseline `8cbad15`.

## Scope and meaning

This is a read-only code audit. No device actions, production edits, commits or tests were run for Task 2.
The only Task 2 write is this document. Baseline files were read with `git show 8cbad15:<path>`.
The design reference is `docs/plans/transpose/design/REDESIGN.md`, §7, as read on 2026-09-26.

**covered** means the retention table promises a home. It does not mean the current slice implements that home.
**missing from table** means §7 does not name the capability, even if another design section mentions it.
**ambiguous** means the broad row needs a specific decision, preserves a setting but omits a behavior, or lists a dormant API.
Dormant interface methods and read-only state are identified separately. They are not invented user features.

Inventory: 71 methods declared by SheetActions, 91 by ScopeActions, 10 by AppearanceActions, and 18 by InstrumentPresetActions.
Their union is **123 distinct methods**, all listed below. All **139 mutable ScopeUiState declarations/accessors** are classified.
The gesture host, callbacks outside those interfaces, HUD, notifications, manual, transient editors and accessibility paths were inspected separately.

## Findings for Prime

1. **L1 · Generated-color timing can disappear.** `LightSheetV2` exposes TIMER/TRACK, seconds, random interval, minimum and maximum even with no saved slots.
   Generated-auto uses those choices. REDESIGN §2/§6 hides CYCLE unless at least two slots exist.
   Retain timing when generated-auto is active, including zero/one saved slot. This is not fixed by retaining the saved values in a file.
2. **L2 · RGB and accessible color editing are not preserved by pointer-only HSV.** Baseline `LightRule` exposes R/G/B values, keyboard arrows and accessibility `setProgress`.
   `HsvSquare` has pointer input only. R19 explicitly removes RGB rules and says RGB remains in the setup file.
   That loses direct component editing and the existing non-touch editing route unless equivalent accessible controls remain.
3. **L3 · Independent corners need an actual edit route.** Baseline RoomSheet changes radius independently of feel with 0/8/12 choices.
   R27 says corners follow the look, with raw radius in DEVELOPER. This can retain the capability only if that radius editor applies without resetting the other style choices.
   Persisted custom radii must not be reset merely by visiting LOOK.
4. **L4 · Ban editing before the first random roll changes.** Baseline BAN FACES opens while random is off.
   The redesign only shows ban chips while random is on, so the first roll can select a face the person wanted to exclude first.
   Keep a way to configure bans before arming random, or record this as an intentional behavior change rather than “same”.
5. **L5 · §7 omits whole interaction surfaces.** Main transport/seek, 3D camera, held-image pan/pinch, HUD drag/resize/buttons, and notification/media controls need explicit retention entries.
   Ordinary stage gestures are mostly in §1, but 3D and inspection are not adequately covered there either.
6. **L6 · Broad rows hide recovery actions.** Preserve all four setup-import decisions, setup restore/retry, appearance authoritative recovery/reset/readability proposal,
   signal-check OPEN SOURCES, all remote library roots and play-folder-as-queue. Do not certify these from the words “same editor” or “browse library”.
7. **L7 · Saved-color behavior is deliberately changed, not merely re-skinned.** Baseline add saves the preset, selectedMask can be empty, and slot taps toggle independent membership.
   The plan changes add to the displayed color, uses single selection while cycle is off, and requires a nonempty ring while cycling.
   These may be good changes, but the defaults, selected subset and generated-auto interaction need explicit behavior checks.

No missing table entry is a claim that the current implementation has already deleted that feature.
R28 explicitly replaces the live style sample with look tiles. This is a preview-surface change, not a tuning setting.

## §7 row key

These audit IDs are stable references to the table snapshot. The row title is included so later table edits cannot silently renumber evidence.

| ID | REDESIGN §7 control row |
|---|---|
| R01 | Open file · open folder · queue rows |
| R02 | Everything playing + consent card |
| R03 | Include mic · playback level · mic level |
| R04 | Track names access (grant) |
| R05 | Capture status / fix |
| R06 | Root capture keys (flag-gated, off) |
| R07 | "manual…" beside capture prose |
| R08 | Start selected mic · input rows · retry |
| R09 | Stop microphone |
| R10 | Bluetooth mic confirm |
| R11 | Mic status |
| R12 | ⏻ LIVE stone (stop / start capture) |
| R13 | Relays: connect, edit, add, remove |
| R14 | Music / visualizer streams, desktop sources, browse library, disconnect |
| R15 | Remote failure message |
| R16 | Mode rows, random, ban faces |
| R17 | Geometry + amount |
| R18 | 9 presets |
| R19 | Saved colours: select, edit (HSV + RGB), delete, add |
| R20 | Random roll · automatic generated colour |
| R21 | Shuffle order ("Le random order") |
| R22 | Timer / track · leg seconds · random interval min/max |
| R23 | Photosensitivity guard |
| R24 | Recall instrument |
| R25 | Legacy rooms · appearance looks |
| R26 | FEEL · MOTION · LABELS |
| R27 | CORNERS chip |
| R28 | Live style sample |
| R29 | Appearance editor (hex, preview, save, rename, delete, repair) |
| R30 | Auto size, size, reset, view lock, focus, beam, glow, vary + ranges |
| R31 | Size not saved · retry |
| R32 | Frame rate, fps line, bright, HDR, fullscreen, keys visible, double tap, when paused, rotation locks |
| R33 | Brightness pin error |
| R34 | Auto PiP, floating HUD, HUD background, show/hide HUD, keep playing |
| R35 | On launch, ask permission, relay latency |
| R36 | Saved setups sheet (curated, apply, duplicate, update, rename, export, delete, save, import preview, undo, cancel, retry, rapid guard, source controls) |
| R37 | Export / import settings + transfer status |
| R38 | Manual · version · developer unlock · CAL line |
| R39 | Signal check, status band, beam rate, grid data, pause display only, reset inspection, HDR/HUD status |
| R40 | Stage SIGNAL CHECK · pause label |
| R41 | GRID |
| R42 | Enter PiP |
| R43 | Manual search, chapters, previous/next, bestiary, link cards |

## Interface inventory

`S` = SheetActions, `C` = ScopeActions, `A` = AppearanceActions, `I` = InstrumentPresetActions.
Inheritance is included through the distinct interface inventories, not by duplicating each inherited method.
References below are baseline file names and line numbers under `app/src/main/kotlin/dev/phosphor/mobil3/ui/`.
A PhosphorScreen reference can be a forwarding callback; the concrete surface is detailed in the next table.

| Action / method | Declared in | Baseline call-site witness | §7 row | Status | Retention detail |
|---|---|---|---|---|---|
| `ackEpilepsy` | C | `PhosphorScreen.kt:871` | R23 | covered | Same behavior at new home. |
| `allowInstrumentRapid` | I | `InstrumentPresetSheet.kt:82` | R36 | covered | Import choices and recovery substates are expanded below. |
| `applyAppearance` | A | `AppearanceEditor.kt:154` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `applyInstrument` | I | `InstrumentPresetSheet.kt:97` | R36 | covered | Import choices and recovery substates are expanded below. |
| `cancelAppearancePreview` | A | `AppearanceEditor.kt:56; AppearanceEditor.kt:92; AppearanceEditor.kt:156` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `cancelInstrumentApply` | I | `InstrumentPresetSheet.kt:72` | R36 | covered | Import choices and recovery substates are expanded below. |
| `cancelInstrumentImport` | I | `InstrumentPresetSheet.kt:130; InstrumentPresetSheet.kt:158` | R36 | covered | Import choices and recovery substates are expanded below. |
| `captureConsentNeeded` | S, C | `Sheets.kt:781; Sheets.kt:843` | R02 | covered | Same behavior at new home. |
| `chooseInstrumentImport` | I | `InstrumentPresetSheet.kt:139; InstrumentPresetSheet.kt:142; InstrumentPresetSheet.kt:147` | R36 | covered | Import choices and recovery substates are expanded below. |
| `chooseMicrophone` | S, C | `MicrophoneControls.kt:25` | R08 | covered | Same behavior at new home. |
| `commitInstrumentImport` | I | `InstrumentPresetSheet.kt:157` | R36 | covered | Import choices and recovery substates are expanded below. |
| `confirmMicrophoneBluetooth` | S, C | `MicrophoneControls.kt:31; MicrophoneControls.kt:32` | R10 | covered | Same behavior at new home. |
| `deleteAppearance` | A | `AppearanceEditor.kt:166` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `deleteInstrument` | I | `InstrumentPresetSheet.kt:109` | R36 | covered | Import choices and recovery substates are expanded below. |
| `deleteLightSlot` | C | `PhosphorScreen.kt:868` | R19 | covered | Same behavior at new home. |
| `disconnectRemote` | S, C | `Sheets.kt:1606` | R14 | covered | Same behavior at new home. |
| `dollyBy` | C | `Gestures.kt:505` | none | missing from table | 3D camera gestures are absent from §7 and from the 2D-only gesture summary. |
| `duplicateInstrument` | I | `InstrumentPresetSheet.kt:122` | R36 | covered | Import choices and recovery substates are expanded below. |
| `enterPictureInPicture` | S, C | `PhosphorScreen.kt:813` | R42 | covered | Same behavior at new home. |
| `epilepsyAcknowledged` | C | `PhosphorScreen.kt:870` | R23 | covered | Same behavior at new home. |
| `exportInstrumentPresets` | I | `InstrumentPresetSheet.kt:105; InstrumentPresetSheet.kt:129` | R36 | covered | Import choices and recovery substates are expanded below. |
| `exportSettings` | S, C | `SettingsPages.kt:331` | R37 | covered | Same behavior at new home. |
| `finishAutoFrameScale` | S, C | `Gestures.kt:578; SettingsPages.kt:258` | R31 | covered | Used for gesture-end persistence and retry, not just the retry button. |
| `hideFloatingHud` | S, C | `SettingsPages.kt:314` | R34 | covered | Same behavior at new home. |
| `importInstrumentPresets` | I | `InstrumentPresetSheet.kt:128` | R36 | covered | Import choices and recovery substates are expanded below. |
| `importSettings` | S, C | `SettingsPages.kt:332` | R37 | covered | Same behavior at new home. |
| `instrumentSourceControls` | I | `InstrumentPresetSheet.kt:70` | R36 | covered | Import choices and recovery substates are expanded below. |
| `isScopeRotationLocked` | S, C | `SettingsPages.kt:296` | R32 | covered | Same behavior at new home. |
| `isUiPlacementLocked` | S, C | `SettingsPages.kt:298` | R32 | covered | Same behavior at new home. |
| `jumpToQueue` | S, C | `Sheets.kt:746` | R01 | covered | Same behavior at new home. |
| `keepInstrumentSafe` | I | `InstrumentPresetSheet.kt:81` | R36 | covered | Import choices and recovery substates are expanded below. |
| `lockedUiLandscape` | C | `No baseline UI caller found` | R32 | ambiguous | Read-only interface query with no baseline caller; not a setting. |
| `makeSurface` | C | `PhosphorScreen.kt:248; PhosphorScreen.kt:573` | none | ambiguous | Rendering factory, not a user action. Keep surface lifecycle wiring; exclude from capability count. |
| `markBestiaryFound` | C | `PhosphorScreen.kt:899` | R43 | covered | Five-tap discovery and five external link targets are expanded below; capture consent also links to source. |
| `next` | C | `PhosphorScreen.kt:770` | none | missing from table | Visible Console transport/seek and mirrored capture transport are absent from §7, although navigation §1 draws transport. |
| `openCaptureMetadataSettings` | S, C | `Sheets.kt:821` | R04 | covered | Same behavior at new home. |
| `openFile` | S, C | `Sheets.kt:730` | R01 | covered | Same behavior at new home. |
| `openFolder` | S, C | `Sheets.kt:735` | R01 | covered | Same behavior at new home. |
| `openInstrument` | S | `SettingsPages.kt:330` | R24/R36 | covered | Duplicate destinations can disappear only if inline SETUPS remains reachable, including programmatic open requests. |
| `openInstrumentPresets` | I | `PhosphorScreen.kt:872; PhosphorScreen.kt:885` | R24/R36 | covered | Duplicate destinations can disappear only if inline SETUPS remains reachable, including programmatic open requests. |
| `openLight` | S | `No baseline UI caller found` | R18–R23 | ambiguous | SheetActions declaration has no baseline caller. Real LIGHT navigation uses closeOverflow(Sheet.LIGHT). |
| `openLink` | S, C | `PhosphorScreen.kt:900; Sheets.kt:714` | R43 | covered | Five-tap discovery and five external link targets are expanded below; capture consent also links to source. |
| `openManual` | S | `SettingsPages.kt:337; Sheets.kt:813` | R07/R38 | covered | Same behavior at new home. |
| `openRoom` | S | `No baseline UI caller found` | R25 | ambiguous | SheetActions declaration has no baseline caller. Real LOOK navigation uses closeOverflow(Sheet.ROOM). |
| `openRootManager` | S, C | `Sheets.kt:793` | R06 | covered | Root-only controls stay product-flag gated. |
| `orbitBy` | C | `Gestures.kt:543; PhosphorScreen.kt:631` | none | missing from table | 3D camera gestures are absent from §7 and from the 2D-only gesture summary. |
| `prev` | C | `PhosphorScreen.kt:771` | none | missing from table | Visible Console transport/seek and mirrored capture transport are absent from §7, although navigation §1 draws transport. |
| `previewAppearance` | A | `AppearanceEditor.kt:153` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `recoverAppearance` | A | `AppearanceEditor.kt:105` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `remoteHosts` | S, C | `Sheets.kt:1399` | R13 | covered | Same behavior at new home. |
| `removeRemoteHost` | S, C | `PhosphorScreen.kt:453; Sheets.kt:1463` | R13 | covered | Same behavior at new home. |
| `renameAppearance` | A | `AppearanceEditor.kt:165` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `renameInstrument` | I | `InstrumentPresetSheet.kt:121` | R36 | covered | Import choices and recovery substates are expanded below. |
| `repairAppearance` | A | `AppearanceEditor.kt:102` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `resetAppearance` | A | `AppearanceEditor.kt:184` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `resetAutoFrameScale` | S, C | `SettingsPages.kt:249` | R30 | covered | Same behavior at new home. |
| `resetInspection` | S, C | `SettingsPages.kt:364` | R39 | covered | Same behavior at new home. |
| `retryInstrumentSave` | I | `InstrumentPresetSheet.kt:77` | R36 | covered | Import choices and recovery substates are expanded below. |
| `retryMicrophone` | S, C | `MicrophoneControls.kt:34` | R08 | covered | Same behavior at new home. |
| `rollLight` | C | `PhosphorScreen.kt:869` | R20 | covered | Same behavior at new home. |
| `saveAppearance` | A | `AppearanceEditor.kt:161; AppearanceEditor.kt:164` | R29 | covered | Editor draft subcontrols and recovery are expanded below. |
| `saveInstrument` | I | `InstrumentPresetSheet.kt:123` | R36 | covered | Import choices and recovery substates are expanded below. |
| `saveRemoteHost` | S, C | `PhosphorScreen.kt:451; Sheets.kt:1447` | R13 | covered | Same behavior at new home. |
| `seekTo` | C | `PhosphorScreen.kt:772` | none | missing from table | Visible Console transport/seek and mirrored capture transport are absent from §7, although navigation §1 draws transport. |
| `selectAppearance` | A | `No baseline UI caller found` | R25/R29 | ambiguous | Declared but baseline editor loads drafts and applies them; no baseline UI selectAppearance caller. |
| `setAutoFrameScale` | S, C | `Gestures.kt:517; Gestures.kt:560; SettingsPages.kt:247` | R30 | covered | Same behavior at new home. |
| `setAutomaticPermissionPopup` | S, C | `SettingsPages.kt:324` | R35 | covered | Same behavior at new home. |
| `setBeam` | C | `PhosphorScreen.kt:866` | R18 | covered | Same behavior at new home. |
| `setBeamCycle` | C | `No baseline UI caller found` | R22 | ambiguous | Compatibility method without a baseline UI caller; active UI publishes setLight snapshots. |
| `setBeamEnergy` | S, C | `SettingsPages.kt:263` | R30 | covered | Same behavior at new home. |
| `setBeamRandomRange` | S, C | `PhosphorScreen.kt:408; SettingsPages.kt:269` | R30 | covered | Same behavior at new home. |
| `setControlsAlwaysVisible` | S, C | `SettingsPages.kt:291` | R32 | covered | Same behavior at new home. |
| `setCustomBeam` | C | `No baseline UI caller found` | R19 | ambiguous | Compatibility method without a baseline UI caller; active UI publishes setLight snapshots. |
| `setDefaultSource` | S, C | `SettingsPages.kt:322` | R35 | covered | Same behavior at new home. |
| `setDeveloperView` | S, C | `SettingsPages.kt:345` | R38 | covered | Seven-tap toggle must support both enable and disable. |
| `setDoubleTapPlayback` | S, C | `SettingsPages.kt:292` | R32 | covered | Same behavior at new home. |
| `setFloatingHudEnabled` | S, C | `SettingsPages.kt:307` | R34 | covered | Same behavior at new home. |
| `setFloatingHudTransparent` | S, C | `SettingsPages.kt:311` | R34 | covered | Same behavior at new home. |
| `setFocus` | C | `PhosphorScreen.kt:889` | R30 | covered | Same behavior at new home. |
| `setFps` | S, C | `PhosphorScreen.kt:821; SettingsPages.kt:282` | R32 | covered | Same behavior at new home. |
| `setFullscreen` | S, C | `SettingsPages.kt:290` | R32 | covered | Same behavior at new home. |
| `setGainAbsolute` | S, C | `Gestures.kt:522; Gestures.kt:565; SettingsPages.kt:254` | R30 | covered | Same behavior at new home. |
| `setGainAuto` | S, C | `SettingsPages.kt:242` | R30 | covered | Same behavior at new home. |
| `setGeomAmount` | C | `PhosphorScreen.kt:859` | R17 | covered | Same behavior at new home. |
| `setGeomFx` | C | `PhosphorScreen.kt:858` | R17 | covered | Same behavior at new home. |
| `setGlow` | S, C | `SettingsPages.kt:264` | R30 | covered | Same behavior at new home. |
| `setGlowRandomRange` | S, C | `PhosphorScreen.kt:411; SettingsPages.kt:276` | R30 | covered | Same behavior at new home. |
| `setGrid` | S, C | `PhosphorScreen.kt:826` | R41 | covered | Same behavior at new home. |
| `setGridData` | S, C | `SettingsPages.kt:360` | R39 | covered | Same behavior at new home. |
| `setHdrRequested` | S, C | `SettingsPages.kt:289` | R32 | covered | Same behavior at new home. |
| `setHudMode` | S, C | `PhosphorScreen.kt:824; SettingsPages.kt:284` | R32 | covered | Same behavior at new home. |
| `setIncludeMicrophone` | S, C | `MicrophoneControls.kt:11` | R03 | covered | Same behavior at new home. |
| `setLight` | C | `PhosphorScreen.kt:867` | R19–R23 | ambiguous | Whole LightSettings publication spans selection, RGB/HSV edits, timing, shuffle and generated mode. See L1/L2 loss checks below. |
| `setLingerBackground` | S, C | `SettingsPages.kt:318` | R34 | covered | Same behavior at new home. |
| `setMicrophoneMixLevel` | S, C | `MicrophoneControls.kt:14; MicrophoneControls.kt:15` | R03 | covered | Same behavior at new home. |
| `setMode` | C | `PhosphorScreen.kt:635; PhosphorScreen.kt:857` | R16 | covered | Same behavior at new home. |
| `setOversample` | S, C | `SettingsPages.kt:359` | R39 | covered | Same behavior at new home. |
| `setPauseBlack` | S, C | `SettingsPages.kt:294` | R32 | covered | Same behavior at new home. |
| `setPinScreenBrightness` | S, C | `SettingsPages.kt:287` | R32 | covered | Same behavior at new home. |
| `setPipAutoEnter` | S, C | `PhosphorScreen.kt:814; SettingsPages.kt:304` | R34 | covered | Same behavior at new home. |
| `setRandomBanModes` | C | `PhosphorScreen.kt:860` | R16 | covered | Same behavior at new home. |
| `setRemoteLatencyMode` | S, C | `SettingsPages.kt:327` | R35 | covered | Same behavior at new home. |
| `setRemoteStreams` | S, C | `PhosphorScreen.kt:457; Sheets.kt:1490; Sheets.kt:1493` | R14 | covered | Same behavior at new home. |
| `setRoom` | C | `PhosphorScreen.kt:876` | R25 | covered | Same behavior at new home. |
| `setRoomStyle` | C | `PhosphorScreen.kt:877` | R26–R27 | ambiguous | One snapshot carries character, motion, radius and designators. Independent radius must survive the LOOK merge. |
| `setRootCapture` | S, C | `No baseline UI caller found` | R06 | ambiguous | Declared but no baseline UI caller. Do not add a root authorization switch during redesign. |
| `setScopeRotationLocked` | S, C | `PhosphorScreen.kt:435; SettingsPages.kt:297` | R32 | covered | Same behavior at new home. |
| `setUiPlacementLocked` | S, C | `PhosphorScreen.kt:438; SettingsPages.kt:299` | R32 | covered | Same behavior at new home. |
| `setViewLock` | S, C | `SettingsPages.kt:261` | R30 | covered | Same behavior at new home. |
| `showFloatingHud` | S, C | `SettingsPages.kt:314` | R34 | covered | Same behavior at new home. |
| `startCapture` | S, C | `Sheets.kt:719; Sheets.kt:782; Sheets.kt:792` | R02 | covered | Same behavior at new home. |
| `startMic` | S, C | `Sheets.kt:828` | R08 | covered | Same behavior at new home. |
| `startRemote` | S, C | `No baseline UI caller found` | R13 | ambiguous | Declared adapter entry, no baseline UI caller; named relay rows use startRemoteHost. |
| `startRemoteHost` | S, C | `PhosphorScreen.kt:455; Sheets.kt:1429` | R13 | covered | Same behavior at new home. |
| `startStandardCapture` | S, C | `Sheets.kt:788` | R06 | covered | Root-only controls stay product-flag gated. |
| `stopLive` | S, C | `Sheets.kt:843` | R12 | ambiguous | Not only capture: activity stopLive also retires local, mic, root and remote ownership. Active-row replacement must retain all owned-source stop paths. |
| `stopMicrophone` | S, C | `MicrophoneControls.kt:35` | R09 | covered | Same behavior at new home. |
| `tapBeamRandom` | S, C | `SettingsPages.kt:265` | R30 | covered | Same behavior at new home. |
| `tapGlowRandom` | S, C | `SettingsPages.kt:272` | R30 | covered | Same behavior at new home. |
| `toggleDisplayPause` | S, C | `SettingsPages.kt:362` | R39 | covered | Same behavior at new home. |
| `togglePlay` | C | `PhosphorScreen.kt:698; PhosphorScreen.kt:769` | none | missing from table | Visible Console transport/seek and mirrored capture transport are absent from §7, although navigation §1 draws transport. |
| `undoInstrument` | I | `InstrumentPresetSheet.kt:73` | R36 | covered | Import choices and recovery substates are expanded below. |
| `updateInstrument` | I | `InstrumentPresetSheet.kt:103` | R36 | covered | Import choices and recovery substates are expanded below. |

## Concrete actions outside, or compressed inside, the interfaces

This expands the table’s grouped promises. Local editor state is included: a cancel key, import conflict choice, or directory action can disappear while every interface method still compiles.

| ID | User action / setting | Baseline owner | §7 row | Status | Required detail |
|---|---|---|---|---|---|
| N1 | Show/hide console by stage tap; Back hides console unless always-visible | PhosphorScreen | none | missing from table | Specified in §1, not §7. |
| N2 | Console MODE, SRC, more; overflow LIGHT, LOOK, SETTINGS, PiP, GRID | Console / OverflowPopout | R16/R18/R25/R38/R41/R42 | covered | Navigation map §1 supplies destinations; do not count obsolete unused popup FPS/HUD/AUTO-PiP parameters as buttons. |
| N3 | Console play/pause; display-only HOLD/LIVE fallback; previous and next | Console → ScopeActions | none | missing from table | Transport availability follows local, remote or captured media session. HOLD does not pause an uncapturable source app. |
| N4 | Seek by touching/dragging seek rule when duration and seekability exist | Console → SeekRule → seekTo | none | missing from table | Track title and seek readout must remain alongside transport. |
| N5 | MODE key rerolls when random mode is already armed, then opens MODE | Console → state.requestRandomMode | R16 | ambiguous | Same MODE key has an extra existing random-roll side effect; not stated in §7. |
| N6 | Overflow handle tap/open; downward drag, scrim/Back close | OverflowHandleKey / OverflowPopout / PhosphorScreen | none | missing from table | Opening/closing behavior appears in §1/§5, not §7. |
| N7 | Every sheet: close key, scrim tap, system Back, header drag and content overscroll dismissal | SheetHost / settings dismissal adapter | none | missing from table | §5 promises replacement. Slider drags and fling-at-top must not dismiss. |
| N8 | Settings topic navigation and page Back | SettingsSheet / SettingsPageBody | R30–R39 | covered | Flat sections deliberately replace page navigation; retain actions, not old nesting. |
| G1 | 2D pinch and one-finger vertical drag: manual gain or AUTO framing, with view-lock refusal | stageGestures → StageGestureHost | R30 | ambiguous | The setting is covered; both stage gestures are only explicit in §1. |
| G2 | 3D one-finger drag orbits yaw/pitch; pinch dollies camera | stageGestures → orbitBy/dollyBy | none | missing from table | Different from 2D framing; view lock must not silently remove camera controls. |
| G3 | Two-finger horizontal swipe selects one next/previous mode per gesture | stageGestures → modeStep | none | missing from table | §1 lists it; §7 does not. |
| G4 | Two-finger vertical swipe continuously changes glow | stageGestures → setGlowAbsolute | none | missing from table | §1 lists it; §7 does not. |
| G5 | Held-image one-/two-finger pan and pinch inspect pixels without changing live tuning | stageGestures → inspect → PhosphorNative.inspectHeld | none | missing from table | Not equivalent to resetInspection or WHEN PAUSED choice. |
| G6 | Optional stage double-tap invokes source transport or display-only pause | PhosphorScreen tap layer → togglePlay | R32 | covered | Keep the gesture as well as its setting. |
| G7 | Swipe console upward; bottom-edge pull reveals console then Settings; cancel/release settles | playBarSwipeUp / StageGestureHost bottom-pull methods | none | missing from table | §1/§5 cover intent. One gesture owner, chrome blocking and post-settle rebasing must remain. |
| S1 | Open capture explanation: external source link, cancel/not now, approve capture | SourceSheet consentCard | R02 | covered | Do not replace normal Android consent with a settings toggle. |
| S2 | Select each microphone route, start selected, retry selected, stop; Bluetooth accept/cancel | MicrophoneInputControls / SourceSheet | R08–R11 | covered | Combined row must not lose route selection before successful start, retry on failure, or Bluetooth cancellation. |
| S3 | Capture stop and permission-ended recovery; source-specific active-row stop | SourceSheet LIVE / MainActivity.stopLive | R12 | ambiguous | Verify source ownership release, not merely a label or display pause. |
| S4 | Root retry, use standard capture, open root manager while product flag permits | SourceSheet root block | R06 | covered | Baseline has no reachable setRootCapture toggle; manual preview is informational only. |
| S5 | Relay editor name/address/port fields; save add/edit; cancel; remove; refusal feedback | RemoteHostEditor / RemoteFlow | R13 | covered | Retain optional label and exact host+port identity; no silent changes to address validation. |
| S6 | Toggle audio and geometry independently; request/show desktop source list; choose source ID | RemoteFlow → remoteRequestSources/remoteChooseSource | R14 | covered | These native calls do not appear in ScopeActions. |
| S7 | Open/close remote library browser; select any advertised library root | RemoteFlow / RemoteBrowseRequest.selectRoot | R14 | ambiguous | “Browse library” must mean every root, not first-root-only. |
| S8 | Navigate into directory, go up, play file, play folder as queue; disconnect clears browser | RemoteFlow / RemoteFolderAction / remoteBrowse / remotePlayFile | R14 | ambiguous | Each is a separate action under the broad row; keep stale-listing and peer guards. |
| M1 | Select all 11 modes; reroll random excluding current/banned faces | ModeSheet / requestRandomMode | R16 | covered | xy, xy45, swirl, dots, attractor, time helix, waveform, ring, spectrum, radial, tunnel. |
| M2 | Configure individual random bans while random is OFF; preserve at least two allowed modes | ModeSheet BAN FACES chips | R16 | ambiguous | New random-only visibility removes pre-roll ban editing. See L4. |
| M3 | Choose off/kaleido/spin/tunnel/pulse; set amount 0–1 when effect is on | ModeSheet | R17 | covered | Baseline already hides amount for off; this is not a new loss. |
| L1 | Choose each of nine beam presets | LightSheetV2 BeamColors loop | R18 | covered | P7 Green, Amber, Ice Blue, White, Vaporwave, Red Phosphor, Ultraviolet, Solar Gold, Cyan Tube. |
| L2 | Add preset color to up to six saved slots; toggle any subset including none; edit/delete each slot | LightSheetV2 / LightSettings | R19 | ambiguous | Spec changes add to current displayed color, single-selection when cycle off, and ring minimum one. These are behavior changes, not merely styling. |
| L3 | Edit hue, saturation/value; edit separate red, green and blue normalized components | HsvSquare / LightRule | R19 | ambiguous | RGB rules carry keyboard and setProgress accessibility. Pointer-only HSV and a setup file are not equivalent. See L2 finding. |
| L4 | Roll generated color now; toggle generated-auto; retain underlying saved setup | LightSheetV2 | R20 | covered | One-shot roll is temporary. Saving a setup must not silently save an unowned temporary roll. |
| L5 | Shuffle selected slots; disable shuffle; retain order while generated-auto is active | LightSheetV2 shuffle key | R21 | covered | Each selected slot appears once per bag. |
| L6 | Timer/track choice and timer period 0.1–60s even with zero or one saved slot | LightSheetV2 cycle group | R22 | ambiguous | Generated-auto also consumes timing. Hiding CYCLE by saved-slot count loses controls. See L1 finding. |
| L7 | Random interval toggle plus min/max 0.1–60s; preserve min ≤ max | LightSheetV2 cycle group | R22 | ambiguous | Same generated-auto visibility issue as L6. |
| L8 | Keep safe timing or acknowledge faster timing; dismiss drops pending request, not safe active value | LightSheetV2 / LightCycleGuard | R23 | covered | Restore pending requested settings only after acknowledgment. |
| L9 | Light error, temporary-roll ownership and HOLD result feedback | LightSheetV2 | R19–R23 | ambiguous | No explicit error/temporary-ownership row in §7. A compact indication must not hide failure or saving semantics. |
| A1 | Select all 13 legacy rooms, plus curated and saved appearance records | RoomSheet / AppearanceEditor | R25 | covered | Blossom, Blossom Dark, Light, Dark, Chromacore, Basalt, Afterglow, Stonework 95, AMOLED, Paper, CRT Amber, Fable, Liquid Glass. |
| A2 | Independent FEEL, MOTION, CORNERS and LABELS choices | RoomSheet / RoomStyle | R26/R27 | ambiguous | Radius 0/8/12 can be changed independently of feel in baseline; developer radius must preserve this ability. See L3 finding. |
| A3 | Live style sample stone, labels and toggle preview | RoomSheet | R28 | ambiguous | Table explicitly cuts it. Tiles replace visual preview, but verify interactive feel preview remains adequate; not a tuning setting. |
| A4 | Load curated, saved or immutable legacy draft without applying it | AppearanceEditor | R29 | covered | Legacy snapshot expand/load remains a distinct recovery path. |
| A5 | Edit every RGB/ARGB color role; dark flag; measured-beam accent; console lookVersion | AppearanceEditor / AppearanceEditorValues | R29 | covered | Do not confuse beam colors with app appearance colors. |
| A6 | Edit character, motion, duration scale, density scale, radius, panel opacity, mono prose, designators | AppearanceEditor | R29 | covered | All draft fields remain editable, even if not in everyday LOOK. |
| A7 | Readability warnings and PROPOSE READABLE COLORS IN DRAFT | AppearanceEditor | R29 | ambiguous | Auto-proposal is a separate reachable action not named in the broad editor row. |
| A8 | Preview, apply, cancel button/Escape, auto-cancel on leaving; save new or overwrite named record | AppearanceEditor | R29 | covered | Preview must stay temporary and cancelable; both save variants remain. |
| A9 | Rename/delete saved appearance, reset to AMOLED retaining saved records, replace corrupt document, retry authoritative recovery | AppearanceEditor | R29 | ambiguous | Repair is named, but reset and authoritative recovery are distinct from delete and repair. Preserve each. |
| I1 | Select curated/user setup; apply; choose save/duplicate/rename operation and edit name | InstrumentPresetSheet | R36 | covered | Selection is not apply. |
| I2 | Update selected user record; export selected; delete confirmation and KEEP RECORD cancel | InstrumentPresetSheet | R36 | covered | Delete must leave current tuning unchanged. |
| I3 | Undo last apply; cancel pending apply; retry save or restore displayed setup | InstrumentPresetSheet | R36 | covered | Restore-required and ordinary retry are distinct states of retryInstrumentSave. |
| I4 | Source controls escape for remote geometry/refused local apply; keep-safe or allow-rapid guard | InstrumentPresetSheet | R36 | covered | Do not hide the only recovery door while simplifying status prose. |
| I5 | Import preview; export all; cancel document operation; per-record Add/Keep/Replace/Save Copy + copy-name field | InstrumentPresetSheet | R36 | ambiguous | The broad import-preview promise must retain all four conflict decisions, not only Add/Replace. |
| I6 | Commit import only after each incoming record has a decision; cancel preview | InstrumentPresetSheet | R36 | covered | Keep refusal/busy/collection-unavailable feedback. |
| D1 | Signal check open/close; developer stage shortcut; OPEN SOURCES from signal view | SignalCheckEntry / SignalCheckSheet / PhosphorScreen | R39/R40 | ambiguous | OPEN SOURCES is not explicit in the row; keep it and refresh only while visible. |
| D2 | Developer seven-tap enable AND disable; CAL/bestiary marker | SettingsPageBody ABOUT | R38 | covered | Do not implement a one-way unlock if toggle-off existed. |
| D3 | HDR actual-status/refusal, HUD-control refusal, brightness errors, settings transfer result | SettingsPageBody SCREEN/DEVELOPER/SETUPS | R33/R37/R39 | covered | Errors and recovery actions survive removal of routine prose. |
| H1 | Floating HUD header drag and resize handle; retain saved width/height | FloatingHudService.gesture | none | missing from table | HUD setting alone does not cover HUD interactions. |
| H2 | HUD return to app, close, SRC return-and-open-source | FloatingHudService header/transport | none | missing from table | Each button has separate lifecycle/navigation behavior. |
| H3 | HUD previous/play-pause/next; fallback HOLD for sources with no transport | FloatingHudService transport | none | missing from table | Follow source command availability. |
| H4 | HUD held-image pan/pinch, FIT reset, LIVE resumes display without audio transport | FloatingHudService scope/inspection | none | missing from table | Not equivalent to main Settings resetInspection alone. |
| T1 | Playback notification/media session: open app, play/pause, prev/next, seek/back/forward, local queue-item selection and stop where source permits | PlaybackService + local/remote/capture Media3 players | none | missing from table | System chooses visible controls from available commands; keep routing and source ownership. |
| T2 | Microphone notification opens app or stops microphone | MicCaptureService notification | none | missing from table | Stop is real recorder teardown, not display HOLD. |
| T3 | Floating-HUD notification opens app or closes HUD | FloatingHudService notification | none | missing from table | Close leaves source unchanged. |
| T4 | Capture notification is informational; Android projection Stop ends capture and returns permission-needed status | CaptureService / MediaProjection callback | R02/R05 | covered | No Phosphor capture-notification button exists in baseline; do not invent one for retention. |
| U1 | Manual search, clear query, open matched chapter, BACK history, INDEX, previous/next | ManualSheet / ManualNavigation | R43 | covered | Search filter and workshop state survive returning to index. |
| U2 | Five welcome-art taps unlock bestiary, persisted marker; open/close bestiary | ManualSheet → markBestiaryFound | R43 | covered | Keep discovery gesture, not merely a visible bestiary page. |
| U3 | Bestiary root preview open and GOT IT close, without authorization | ManualSheet rootDisclosure | R43/R06 | ambiguous | Informational root preview is distinct from Source root keys. |
| U4 | Manual privacy/source/releases/desktop/license links open external browser | ManualSheet LinkCard | R43 | covered | Five exact destinations; source consent card has an additional source link. |
| U5 | Manual system Back chapter→index→Settings; ordinary close/scrim/header behavior | PhosphorScreen / ManualSheet / SheetHost | R43 | ambiguous | Intended fix is explicit in §1; preserve filter/history and define close-key versus Back consistently. |
| X1 | Keyboard/DPAD and TalkBack activation of choices, sliders and fields | Setting* / LightRule / AppearanceField / accessibility helpers | none | missing from table | Capabilities include non-touch input. Retaining a visual row alone does not retain accessible editing. |

### StageGestureHost and gesture-policy coverage

The complete StageGestureHost method set is classified here. Read-only helpers are not extra settings.

| Methods | User behavior / invariant | Mapping |
|---|---|---|
| `inspecting`, `inspect` | Held image pan/pinch without live tuning changes | G5, missing from §7 |
| `physicalPosition`, `physicalBounds`, `chromeBlocks` | Gesture hit testing; sheets/console/popout cannot change the beam underneath | G7; preserve StageGesturePolicy blocking/rebase behavior |
| `currentGain`, `setGainAbsolute`, `currentAutoFrameScale`, `setAutoFrameScale`, `finishAutoFrameScale` | Manual gain versus AUTO framing, then persist framing once on finish | G1, R30/R31 |
| `gainLocked`, `gainAutoArmed`, `autoFrameArmed` | Select the correct 2D gesture meaning and honor view lock | G1, R30 |
| `orbitBy`, `dollyBy`, `is3d` | Distinct 3D camera interaction | G2, missing from §7 |
| `modeStep` | One mode detent per horizontal two-finger swipe | G3, missing from §7 |
| `currentGlow`, `setGlowAbsolute` | Continuous vertical two-finger glow change | G4, missing from §7 |
| `bottomPullArmed`, `beginBottomChromePull`, `dragBottomChromePull`, `releaseBottomChromePull`, `cancelBottomChromePull` | Console-first edge pull and Settings continuation, release/cancel | G7, design §1/§5 only |
| `view` | Haptics and view access, not a user setting | Keep supporting wiring |

PullGestureHost `begin/dragBy/release/cancel` serves `playBarSwipeUp` and `overflowHandleGesture`.
`consoleSeekGesture` serves track seeking, not stage gain. StageGesturePolicy latches bottom-edge rejection for the pointer sequence,
blocks moving/recently changed chrome, and rebases after a block. No user toggle is added by that policy.

### Nested value completeness

- `LightSettings`: `slots` R/G/B triples, `selectedMask`, `preset`, `seconds`, `perTrack`, `generatedAuto`, `shuffle`, `randomInterval`, `intervalMin`, `intervalMax`.
  Every member has a baseline user edit path. A missing control cannot be excused because import still carries the value.
- Appearance draft color roles: `plane`, `surface`, `surface2`, `ink`, `ink2`, `muted`, `line`, `lineStrong`, `accent`, `onAccent`, `stone`, `stoneHi`, `stoneLo`.
  Other draft fields: `lookVersion`, `dark`, `accentFollowsBeam`, `character`, `motion`,
  `durationScale`, `densityScale`, `radiusDp`, `panelAlphaScale`, `monoProse`, `designators`, plus record name and selected draft identity.
  Legacy draft loading, proposal, repair and recovery are separate actions.
- Setup conflict choices: Add, KeepExisting, Replace(target), SaveCopy(copyName). Cancel document operation and cancel preview are both reachable.
- Relay editor: optional label, address, port, edit identity, save, remove, cancel, and validation refusal. Remote browser roots are data-driven, not limited to one.
- Frame-rate choices: 60, 90, panel max (0), uncapped (-1). Beam reconstruction rates: 1×, 2×, 4×. FPS line and status band each retain on/with-keys-or-auto/off.
- Startup source choices: none, microphone, capture. Relay latency: tight, balanced, safe. Pause presentation: held frame or black.
- All 13 rooms, all 9 beam presets, all 11 modes and all 5 geometry choices remain enumerated in the concrete table above.

## ScopeUiState write and observation inventory

This table classifies all 139 mutable declarations/accessors in baseline ScopeUiState, exactly once.
“Control” includes state written indirectly by an action through MainActivity or a settings owner.
“Observation/result” is not an independent setting, but losing its feedback can strand the user after a failed action.
Status follows the referenced §7 row, subject to the concrete ambiguities above.

| Fields | §7 row | Kind | Baseline control / owner |
|---|---|---|---|
| `modeIndex`, `randomModeArmed`, `randomBanModes` | R16 | control | ModeSheet rows, random key/ban chips; Console MODE rerolls when random is armed. |
| `beamIndex`, `light`, `customColors`, `customCount`, `cycleSeconds`, `cyclePerTrack` | R18–R23 | control/compatibility | LightSheetV2 controls publish typed LightSettings; compatibility accessors share light. |
| `lightPending`, `lightTemporary`, `lightError` | R19–R23 | control workflow/result | Guard keep/allow, roll, edit, failure feedback; pending cleared on dismissal. |
| `room`, `styleOverride`, `amoledCaptionSeen` | R25–R28 | control | RoomSheet room tiles and FEEL/MOTION/CORNERS/LABELS; room tile marks AMOLED caption seen. |
| `appearanceValue`, `appearanceDocument`, `appearanceStyle`, `appearanceRevision`, `appearanceSummary`, `appearanceStatus`, `appearancePreview`, `appearanceBlocked`, `appearanceRepairRequired`, `appearanceRecoveryRequired` | R29 | control workflow/result | AppearanceEditor preview/apply/save/cancel/repair/recover publishes these snapshots; not ten separate settings. |
| `focus`, `gain`, `manualGain`, `autoFrameScale`, `autoGain`, `localAutoGain`, `viewLock`, `beamEnergy`, `glow`, `beamRandomArmed`, `beamRandomLo`, `beamRandomHi`, `glowRandomArmed`, `glowRandomLo`, `glowRandomHi` | R30 | control | SettingsPageBody SOUND + stage gestures; local and remote gain meanings remain distinct. |
| `autoFrameSaveStatus` | R31 | control result | Failed persistence exposes retry; do not hide failure text. |
| `geomFx`, `geomAmount` | R17 | control | ModeSheet geometry choice + amount. |
| `fpsValue`, `hudMode`, `pinScreenBrightness`, `hdrRequested`, `fullscreen`, `controlsAlwaysVisible`, `keepControls`, `doubleTapPlayback`, `pauseBlack` | R32 | control | SCREEN controls; keepControls is backing storage, not an extra toggle. |
| `brightnessPinError`, `brightnessPinActive` | R33 | control result/observation | Pin request outcome and effective brightness lease, not separate settings. |
| `systemRotationLocked`, `rotationPresentation` | R32 | observation | Android rotation state and computed detents. Rotation locks live in their owners, not direct writable state fields. |
| `controlsVisibilityRevision` | R32 | derived | Revision changes through controlsAlwaysVisible; no independent control. |
| `pipAutoEnter`, `floatingHudEnabled`, `floatingHudTransparent`, `lingerBackground` | R34 | control | PiP and background choices; actual HUD interactions are missing from §7. |
| `floatingHudActive`, `floatingHudStatus` | R34/R39 | control result/observation | Show/hide outcome and error/status. |
| `defaultSource`, `automaticPermissionPopup`, `latencyMode` | R35 | control | Startup choices and remote latency, including saved preference when disconnected. |
| `developerView`, `calDate`, `bestiaryFound` | R38/R43 | control/observation | Seven-tap developer toggle; five-tap bestiary persistence; CAL is observation. |
| `oversample`, `bandMode`, `gridData` | R39 | control | Developer beam-rate/grid-data and directly written status-band choice. |
| `grid` | R41 | control | Overflow GRID toggle only. |
| `displayPaused`, `pauseSourceLive`, `displayPresentPending`, `heldFrameAvailable` | R39; transport absent | control result/observation | Display-only pause, transport-dependent HOLD/LIVE and held-image availability. |
| `signalCheckExpanded`, `signalCheckVisible`, `signalCheck` | R39/R40 | control workflow/observation | Developer signal entry toggles expansion; visibility drives refresh; sheet dismissal retires visibility. |
| `microphoneInputs`, `selectedMicrophone`, `microphoneStatus`, `microphoneActive`, `micBluetoothExplain` | R08–R11 | control workflow/result | Device choice, start/retry/stop and Bluetooth accept/cancel. |
| `includeMicrophone`, `playbackMixLevel`, `microphoneMixLevel` | R03 | control | Capture mic toggle and independent visualization levels. |
| `sourceLabel`, `live`, `captureStatus`, `captureFix`, `captureRoot` | R02/R05/R12 | control result/observation | Source ownership and capture outcomes, not independent settings. |
| `rootCaptureEnabled`, `rootCaptureBusy`, `rootCaptureStatus` | R06 | flag-gated workflow | Root controls are product-disabled. Preserve deferred state; no new authorization path. |
| `captureMetadataAccess`, `captureCanPlay`, `captureCanNext`, `captureCanPrevious` | R04; transport absent | permission/capability observation | Notification access gates mirrored metadata and transport availability. |
| `remote`, `remoteFailure`, `remoteAudio`, `remoteGeometry` | R13–R15 | control/result | Relay connection and separate audio/geometry toggles. |
| `playing`, `trackTitle`, `trackArtist`, `artwork`, `queueTitles`, `queueIndex`, `seekable`, `positionMs`, `durationMs` | R01; transport absent | control result/observation | Transport/seek/queue selection updates playback and metadata; metadata itself is not editable. |
| `settingsTransferStatus` | R37 | control result | Settings document operations and failures. |
| `instrumentCollection`, `instrumentPreview`, `instrumentChoices`, `instrumentStatus`, `instrumentApplyStatus`, `instrumentRecall`, `instrumentPending`, `instrumentUndo`, `instrumentUnsaved`, `instrumentRestoreRequired`, `instrumentSourceRequired`, `instrumentRapid`, `instrumentDocumentBusy` | R36 | control workflow/result | All setup selection, conflict choices, import/export, pending apply, undo, retry, restore and rapid guard states. |
| `showInstrumentPresets`, `showSourcePicker` | R24/R36/R39 | navigation request | Consumed by PhosphorScreen; inline SETUPS must still honor open requests; source controls must open SRC. |
| `gridReading`, `noSignal`, `hudControlStatus`, `hudLine`, `hudLine2`, `remoteScopeLine`, `hdrStatus` | R32/R39/R40 | observation | Native levels, absence, HUD metadata/control refusal and HDR fallback; no independent user setters. |
| `pip`, `presentationVisible` | R34/R42 | lifecycle observation | Actual PiP and surface presentation; not independent user preferences. |
| `randomModeRequest` | R16 | wiring | Private callback set by bindRandomModeRequest; UI random requests delegate through it. |

### Dormant declarations are not lost user capabilities

No baseline UI call was found for `lockedUiLandscape`, `selectAppearance`, `setCustomBeam`, `setBeamCycle`, `setRootCapture`, or `startRemote`.
`openRoom` and `openLight` exist on SheetActions, but real navigation dispatches the sheet directly from PhosphorScreen.
`makeSurface` is a factory. Query methods such as `captureConsentNeeded`, rotation-lock getters and `remoteHosts` support reachable controls but are not buttons.
The unused OverflowPopout callback parameters for FPS/HUD/AUTO-PiP are likewise not baseline buttons; only GRID is rendered below its four destination keys.

## Slice acceptance protocol

For each slice Prime sends:

1. Compare the named files against `8cbad15`, including extracted/replaced files and forwarding adapters.
2. Trace every affected baseline action from a reachable composable or service control to its callback and owner.
3. Check data-driven branches, failure/permission/busy states and local editor actions, not only matching method names.
4. Record the new composable and callback. A dead helper or an unused callback declaration does not count as a reachable home.
5. Record compile verification separately. Compilation proves signatures, not that a branch can be opened or a button can be reached.
6. Report gaps to Prime. Prime holds device acceptance; this audit does not claim device proof.

| Slice | Code comparison | Reachability result | Compile evidence | Device acceptance |
|---|---|---|---|---|
| Initial design inventory | Baseline code vs REDESIGN §7 | This document; L1–L7 require explicit handling | No compile run for read-only inventory | Not performed; Prime owns phone |

Future slice results append here. Do not remove baseline entries when the old file is deleted; move each action to its new owner.

## slice-a-b

Read-only reachability check #1 · 2026-09-26, approximately 17:13 local.
UI baseline: `8cbad15`. Non-UI owner baseline: HEAD `ccc7c370a05df967c4b1b3df6147650bfc7220ba`.
Working-tree snapshot: flat Settings + inline SETUPS (a/a′), LIGHT (b), with the shared dismissal rewrite (a2) in progress.

### Result

**All affected top-level action endpoints still have a source-level path from a mounted composable.**
The removed `openInstrument` row and LIGHT recall link are intentional duplicates: SETUPS now renders inside Settings.
This is not full acceptance. The following behavior/feedback gaps remain in the snapshot:

| Gap | Evidence | Effect | Smallest correction |
|---|---|---|---|
| AB1 · Setup-operation errors can be hidden | `SettingsSetups.kt:146`, `instrumentApplyStatus.ifBlank { instrumentStatus }` | After any persistent apply/workflow status, a newer import/export/save/rename error in instrumentStatus is not shown. Baseline displayed both. | Keep distinct nonblank result/error channels visible, or arbitrate by an actual revision/time. Do not prefer stale workflow text unconditionally. |
| AB2 · Keyboard timing changes can be no-ops forever | `SettingsControlAccess.kt` steps normalized ranges by 1%; `LightChoices.kt:70` floors converted seconds; LIGHT uses normalized SliderRow/SettingRange | At 0.1s, Right maps to about 0.1066s then floors back to 0.1s. At 0.5s it maps to about 0.533s then back to 0.5s. At 12s it maps to about 12.79s then back to 12s. Recomposition restores the same thumb value, so repeated Right never advances. | Keep the mapped value continuous, or give semantic/keyboard adjustment a quantization-aware seconds step. Test repeated arrows at min, 0.5s, 12s, and both interval ends. |
| AB3 · “Save the current color” does not sample the current beam | `LightChoices.addCurrent` only sees authored LightSettings. With multiple selected slots it falls back to BeamColors[preset]; with generated-auto and one selected slot it picks that stored slot. No temporary/generated/interpolated live color is provided. | Plus can save a different color than the one displayed. With generated-auto it can leave generated-auto on, so the added slot is not worn despite the label/spec. | Either pass a real current-color snapshot and define the handoff, or label/promise the authored source it actually saves. Baseline was honest “ADD CURRENT PRESET COLOR”. |
| AB4 · Current setup association and temporary-color save meaning disappeared | `instrumentRecall` and the temporary-roll note were removed from SetupsGroup; `savedWorn` ignores `state.lightTemporary` | Selected row is only the browsing selection, not proof of the active/modified setup. After roll, a saved swatch can still claim selected while the beam wears a temporary color. Saving still records the underlying authored setup. | Keep compact active/modified/temporary state where it changes what the action means. Pass temporary state to saved selection, as preset selection already does. |

AB1 and AB2 are concrete regressions. AB3 contradicts the new label/spec, rather than removing the old preset-add action.
AB4 loses user-visible state while leaving the engine action intact. Prime should decide the minimum honest status surface.
No production edits were made by this audit.

### Mount and forwarding proof

- `PhosphorScreen` handles `Sheet.SETTINGS` with `SettingsSheet(..., setups = actions)`.
- `SettingsSheet` provides settings accessibility, keeps the retained scroll state, and mounts `SettingsBody` inside its scroll column.
- `SettingsBody` renders SOUND, SCREEN, PiP, SOURCES, SETUPS and ABOUT. It additionally mounts DEVELOPER when enabled.
- `SetupsGroup` receives ScopeActions through InstrumentPresetActions. Its interface still declares all 18 original methods.
- MainActivity initializes the setup store before display. `SettingsSheet` also calls `openInstrumentPresets` if the collection is null.
- `showInstrumentPresets` requests now route to SETTINGS. When arriving from elsewhere they request the SETUPS group through `settingsFocus` and BringIntoViewRequester.
- `instrumentSourceControls` still raises `showSourcePicker`, handled by PhosphorScreen’s SOURCE route.
- `Sheet.INSTRUMENT` is a compatibility redirect to SETTINGS. No old sheet is required for an action to work.
- `PhosphorScreen` mounts `LightSheetV2` for `Sheet.LIGHT` with setBeam, setLight, deleteLightSlot, rollLight, epilepsy query and acknowledgment callbacks.
- The ordinary overflow LIGHT and SETTINGS destinations remain mounted. Source/Mode/Look/manual entry paths, stage gestures, HUD and notifications are otherwise untouched by a/b.

### Settings and SETUPS endpoint matrix

All references are from the inspected working-tree snapshot. A callback reference counts only after its enclosing composable mount path above was traced.

| Baseline endpoint | New reachable composable / call site | Result |
|---|---|---|
| `allowInstrumentRapid` | `SetupsGroup` (SettingsSetups.kt:152) | reachable |
| `applyInstrument` | `SetupsGroup` (SettingsSetups.kt:197) | reachable |
| `cancelInstrumentApply` | `SetupsGroup` (SettingsSetups.kt:158) | reachable |
| `cancelInstrumentImport` | `SetupsGroup` (SettingsSetups.kt:241); `ImportPreview` (SettingsSetups.kt:287) | reachable |
| `chooseInstrumentImport` | `ImportPreview` (SettingsSetups.kt:262); `ImportPreview` (SettingsSetups.kt:265); `ImportPreview` (SettingsSetups.kt:270); `ImportPreview` (SettingsSetups.kt:274); `ImportPreview` (SettingsSetups.kt:279) | reachable |
| `commitInstrumentImport` | `ImportPreview` (SettingsSetups.kt:286) | reachable |
| `deleteInstrument` | `SetupsGroup` (SettingsSetups.kt:192) | reachable |
| `duplicateInstrument` | `SetupsGroup` (SettingsSetups.kt:184) | reachable; AB1 can hide its result |
| `exportInstrumentPresets` | `SetupsGroup` (SettingsSetups.kt:207); `SetupsGroup` (SettingsSetups.kt:238) | reachable; AB1 can hide its result |
| `exportSettings` | `SetupsGroup` (SettingsSetups.kt:232) | reachable |
| `finishAutoFrameScale` | `SoundGroup` (SettingsPages.kt:345) | reachable |
| `hideFloatingHud` | `PipGroup` (SettingsPages.kt:398) | reachable |
| `importInstrumentPresets` | `SetupsGroup` (SettingsSetups.kt:239) | reachable; AB1 can hide its result |
| `importSettings` | `SetupsGroup` (SettingsSetups.kt:233) | reachable |
| `instrumentSourceControls` | `SetupsGroup` (SettingsSetups.kt:164) | reachable |
| `isScopeRotationLocked` | `ScreenGroup` (SettingsPages.kt:380) | reachable |
| `isUiPlacementLocked` | `ScreenGroup` (SettingsPages.kt:382) | reachable |
| `keepInstrumentSafe` | `SetupsGroup` (SettingsSetups.kt:151) | reachable |
| `openInstrument` | SETUPS is mounted directly by `SettingsBody`; the old navigation-only row is removed | retained inline, duplicate entry removed |
| `openInstrumentPresets` | `SettingsSheet` (Sheets.kt:1218) | reachable |
| `openManual` | `AboutGroup` (SettingsPages.kt:422); `SourceSheet` (Sheets.kt:829) | reachable |
| `renameInstrument` | `SetupsGroup` (SettingsSetups.kt:183) | reachable; AB1 can hide its result |
| `resetAutoFrameScale` | `SoundGroup` (SettingsPages.kt:336) | reachable |
| `resetInspection` | `DeveloperGroup` (SettingsPages.kt:451) | reachable |
| `retryInstrumentSave` | `SetupsGroup` (SettingsSetups.kt:161) | reachable |
| `saveInstrument` | `SetupsGroup` (SettingsSetups.kt:221) | reachable; AB1 can hide its result |
| `setAutoFrameScale` | `SoundGroup` (SettingsPages.kt:338) | reachable |
| `setAutomaticPermissionPopup` | `SourcesGroup` (SettingsPages.kt:410) | reachable |
| `setBeamEnergy` | `SoundGroup` (SettingsPages.kt:349) | reachable |
| `setBeamRandomRange` | `SoundGroup` (SettingsPages.kt:354) | reachable |
| `setControlsAlwaysVisible` | `ScreenGroup` (SettingsPages.kt:374) | reachable |
| `setDefaultSource` | `SourcesGroup` (SettingsPages.kt:408) | reachable |
| `setDeveloperView` | `AboutGroup` (SettingsPages.kt:430) | reachable |
| `setDoubleTapPlayback` | `ScreenGroup` (SettingsPages.kt:375) | reachable |
| `setFloatingHudEnabled` | `PipGroup` (SettingsPages.kt:392) | reachable |
| `setFloatingHudTransparent` | `PipGroup` (SettingsPages.kt:396) | reachable |
| `setFocus` | `SoundGroup` focus SettingSlider → `onFocus` → PhosphorScreen `actions::setFocus` | reachable |
| `setFps` | `ScreenGroup` (SettingsPages.kt:366) | reachable |
| `setFullscreen` | `ScreenGroup` (SettingsPages.kt:373) | reachable |
| `setGainAbsolute` | `SoundGroup` (SettingsPages.kt:342) | reachable |
| `setGainAuto` | `SoundGroup` (SettingsPages.kt:331) | reachable |
| `setGlow` | `SoundGroup` (SettingsPages.kt:350) | reachable |
| `setGlowRandomRange` | `SoundGroup` (SettingsPages.kt:359) | reachable |
| `setGridData` | `DeveloperGroup` (SettingsPages.kt:447) | reachable |
| `setHdrRequested` | `ScreenGroup` (SettingsPages.kt:372) | reachable |
| `setHudMode` | `ScreenGroup` (SettingsPages.kt:368) | reachable |
| `setLingerBackground` | `PipGroup` (SettingsPages.kt:402) | reachable |
| `setOversample` | `DeveloperGroup` (SettingsPages.kt:446) | reachable |
| `setPauseBlack` | `ScreenGroup` (SettingsPages.kt:377) | reachable |
| `setPinScreenBrightness` | `ScreenGroup` (SettingsPages.kt:371) | reachable |
| `setPipAutoEnter` | `PipGroup` (SettingsPages.kt:389) | reachable |
| `setRemoteLatencyMode` | `SourcesGroup` (SettingsPages.kt:415) | reachable |
| `setScopeRotationLocked` | `ScreenGroup` (SettingsPages.kt:381) | reachable |
| `setUiPlacementLocked` | `ScreenGroup` (SettingsPages.kt:383) | reachable |
| `setViewLock` | `SoundGroup` (SettingsPages.kt:347) | reachable |
| `showFloatingHud` | `PipGroup` (SettingsPages.kt:398) | reachable |
| `tapBeamRandom` | `SoundGroup` (SettingsPages.kt:351) | reachable |
| `tapGlowRandom` | `SoundGroup` (SettingsPages.kt:356) | reachable |
| `toggleDisplayPause` | `DeveloperGroup` (SettingsPages.kt:449) | reachable |
| `undoInstrument` | `SetupsGroup` (SettingsSetups.kt:159) | reachable |
| `updateInstrument` | `SetupsGroup` (SettingsSetups.kt:199) | reachable |

Additional state/control checks:

| Setting or state | New owner | Result |
|---|---|---|
| Auto/manual/remote size distinction; reset only when moved; save retry | SoundGroup | Same callback branches; reset moved beside slider rather than removed. |
| Beam/glow random enable and both range endpoints | SoundGroup → SettingRange | Both endpoints retained, each with range semantics. |
| Status band (direct state write) | DeveloperGroup | Writes `state.bandMode` with on/auto/off. |
| HDR and HUD actual status/control refusal | DeveloperGroup | Separate visible status notes retained. |
| Brightness refusal | ScreenGroup | Moved into keep-screen-bright hint. |
| Relay latency | SourcesGroup | Same choices, now visible only with saved relay. Normal add-relay navigation recreates Settings; no missing setter. |
| Seven-tap developer toggle in both directions; CAL marker | AboutGroup | Retained. |
| Signal entry, expanded diagnostics, OPEN SOURCES | DeveloperGroup → SignalCheckEntry → SignalCheckContent | Existing signal view remains mounted with its source-navigation callback. |
| Appearance draft fields and all preview/apply/save/rename/delete/reset/repair/recover actions | DeveloperGroup → AppearanceEditor | Existing editor retained. The only direct editor diff removes the old a2 child-input marker. |
| Setup curated/user selection and selected-record actions | SetupsGroup → SetupRow + KeyRow | Selection does not apply. User-only update/rename/export/delete remain gated as before. |
| Setup save/copy/rename name entry, cancel, delete confirmation/keep | SetupsGroup → NameField + KeyRow | All reachable; ordinary blank-name save is disabled. |
| Pending apply cancel; undo; retry-save versus restore; source recovery | SetupsGroup recovery KeyRow | All original state branches preserved. |
| Rapid-change guard keep/allow | SetupsGroup guard KeyRow | Both callbacks retained. |
| Settings export/import and transfer result | SetupsGroup → SettingKeys/SettingNote | Retained after leaving SettingsPages. |
| Setup export selected/all, import preview, cancel file operation | SetupsGroup | All reachable. |
| Add, KeepExisting (“skip”), Replace each conflict, SaveCopy with editable name | SetupsGroup → ImportPreview | All four decisions preserved. Copy-name edits republish the choice. |
| Commit import only when all decisions exist; cancel preview | ImportPreview | Retained. |
| Active setup name/modified marker and temporary-roll save explanation | No current reader in SETUPS | AB4; lost feedback, not a dead command. |

### LIGHT behavior matrix

| Requested capability | Reachable owner and publication | Result |
|---|---|---|
| Wear any of the nine presets | LightSheetV2 → PresetSwatch → onPickPreset → setBeam | Retained; marks suppress selection during temporary roll. |
| Wear one saved color | SavedSwatch tap → LightChoices.tapSaved → onLightChange → setLight | Retained. With cycle off, exactly one bit is selected. From generated-auto, tapping a slot also turns generated-auto off. |
| Cycle membership selection | SavedSwatch tap while cycling → tapSaved | Retained with changed semantics: removing below two selected slots is refused. See note below. |
| Open/close edit | SavedSwatch long press or accessibility “edit” → editSlot; SavedColorEditor “done” | Reachable. |
| Edit hue/saturation/value by pointer | SavedColorEditor → HsvSquare → light.edit → setLight | Retained. |
| Accessible color edit | SavedColorEditor → three SliderRow controls → settingsRange | Hue, saturation and brightness expose keyboard arrows/Home/End and TalkBack setProgress. The original non-touch editing gap L2 is addressed at the color-edit capability level. |
| Exact separate RGB component sliders | No current controls; hex is read-only | Deliberate design change, not retained literally. HSV sliders provide color editing, but not the old normalized-float component controls. |
| Delete saved slot | SavedColorEditor delete or SavedSwatch accessibility “delete” → onDeleteSlot | Retained. |
| Add saved slot below six | AddSwatch → LightChoices.addCurrent → setLight | Reachable, but AB3 violates the “current color” promise. |
| Roll now | RANDOM SheetKey → onRoll → rollLight | Retained; temporary state remains in owner but saved mark does not account for it (AB4). |
| Automatic generated color | RANDOM SettingToggle → generatedAuto copy → setLight | Retained. |
| Cycle off/timer/track | CYCLE ChoiceCells → LightChoices.setCycle → setLight | Reachable. Generated-auto offers timer/track; its RANDOM toggle stops generated-auto. |
| Timing with zero/one saved slot while generated-auto is on | LightChoices.cycleVisible uses `slots.size >= 2 || generatedAuto` | L1 visibility loss is fixed in code. |
| Timer seconds, full 0.1–60s range | CYCLE SettingSlider → LightTime mapping → seconds copy → setLight | Pointer and explicit range setting reachable. Keyboard progression is broken at many values (AB2). |
| Random timing toggle | CYCLE SettingToggle → randomInterval copy → setLight | Retained while TIMER is active. |
| Random minimum and maximum | CYCLE SettingRange → LightTime mapping → intervalMin/intervalMax copy | Both endpoints retained with min≤max; AB2 also affects their normalized keyboard stepping. |
| Saved/shuffled order | CYCLE SettingChoice → shuffle copy → setLight | Reachable with saved cycling, hidden during generated-auto and cycle off. Value is retained. |
| Faster-color guard keep/allow | CYCLE pending branch → clear pending / ackEpilepsy then onLightChange(pending) | Both choices reachable when a visible timer edit creates the pending request. Owner still activates safe timing first. |
| Light error | LightSheetV2 SettingNote | Retained. |
| Close/dispose pending request | SheetHost close callback and DisposableEffect | Clears pending request as before. |

Cycle semantics note: baseline slot toggles could reduce a selected ring to one or zero. The new control retains equivalent steady-color/preset outcomes through CYCLE off and preset selection,
but membership toggling itself now keeps **two**, whereas REDESIGN §6 says at least **one**. The current host test explicitly pins two.
This needs a documented decision, not a false “same behavior” claim. The “wear one” action remains reachable through off → saved tap.

### Boundaries and compile evidence

This was source-level typed-call reachability, not a fresh compiler run or device test.
All old Settings action invocations were matched to a current group, except the deliberately removed `openInstrument` navigation row.
Every action invoked by the old InstrumentPresetSheet has a current SetupsGroup/ImportPreview call.
Focus is passed as a function reference rather than through SheetActions. AppearanceEditor’s action body is retained.

The a2 dismissal rewrite was still in flight. Its deleted SettingsInteraction/SettingsGestureAdapter classes do not remove the body action callbacks above,
but drag arbitration, text-field input, header flick and post-scroll dismissal still require Prime’s separate compile and device checks.
No acceptance claim is made for those gesture changes here.

The shared test-results directory can be overwritten by concurrent builds. This audit does not reuse an unrelated test XML as a compile receipt.

## slice-c

Reachability check #2 · exact commit `711be8e` · read-only code review, 2026-09-26.
Compared LOOK with baseline `8cbad15`. Traced owner behavior in the same reviewed commit.
No device, compiler or test command was run for this review. Existing behavior-test bodies were inspected, not counted as fresh passing runs.

### LOOK retention

`PhosphorScreen.kt:881–884` mounts LookSheet for the existing ROOM destination.
Room tiles call `actions.setRoom`; curated and saved tiles call `actions.selectAppearance`; STYLE calls `actions.setRoomStyle`.
Overflow LOOK still dispatches this destination (`PhosphorScreen.kt:822`).

| Baseline capability | New reachable home / owner | Result |
|---|---|---|
| Blossom, Blossom Dark, Light, Dark, Chromacore, Basalt, Afterglow, Stonework 95, AMOLED, Paper, CRT Amber, Fable, Liquid Glass | LookTiles.build iterates every Rooms entry; LookSheet room tile → setRoom | All 13 classic room values are represented. Exact curated-value duplicates may be merged; current curated values differ from the corresponding classic values. Name collisions receive “classic”. |
| Four curated appearance records | LookTiles curated order Glass, AMOLED, Dark, Light → onPickLook → selectAppearance | Retained, now directly selectable outside Developer. |
| Every user/saved look | document.users → LookTiles.Kind.SAVED → selectAppearance(record.id) | Retained; no fixed-size truncation or selected-only filtering. |
| Load named/curated draft without applying | Settings → DeveloperGroup → AppearanceEditor | Retained. Direct LOOK selection is an additional route, not a replacement for draft loading. |
| Preview, apply, cancel / Escape, temporary preview cleanup | DeveloperGroup → AppearanceEditor → AppearanceActions | Retained. |
| Save new user look; overwrite selected saved look | AppearanceEditor saveAppearance(name, value) and saveAppearance(name, value, id) | Both retained. |
| Rename/delete user look | AppearanceEditor renameAppearance / deleteAppearance | Retained in Developer, not dropped because LOOK tiles have no edit menu. |
| Load immutable legacy record and optional legacy:current override snapshot | AppearanceEditor LEGACY SNAPSHOTS → LOAD IMMUTABLE DRAFT | Retained. These immutable provenance snapshots are deliberately distinct from classic-room tiles. |
| FEEL: carved / engraved / bench / glass | LookSheet SettingChoice → StyleOverride(character) | Retained. Existing coupled defaults when changing feel are unchanged in the owner. |
| MOTION: eased / cut / steps / spring | LookSheet SettingChoice → StyleOverride(motion) | Retained. |
| CORNERS: 0 / 8 / 12 | LookSheet SettingChoice → StyleOverride(radiusDp) | Restored to ordinary LOOK, not hidden behind Developer. L3 is addressed. |
| Arbitrary authored radius 0–64 | AppearanceEditor radius field | Retained. LOOK only displays the nearest named choice; opening LOOK does not rewrite the actual radius. |
| LABELS/designators: plain / part numbers | LookSheet SettingChoice → StyleOverride(designators) | Retained. |
| Other appearance fields, readable-color proposal, reset, repair and authoritative recovery | DeveloperGroup → unchanged AppearanceEditor body | Retained. |
| Live preview sample | LookTile renders authored plane/surface/ink/accent/corners | Replaced as planned; no tuning capability removed. |

### Legacy identity and provenance

- `MainActivity.setRoom` resolves a classic room from `LegacyAppearanceInput(room.id)` and calls `owner.apply(value, id)`.
  It uses a legacy association only when that record exists in the committed document. It does not blindly call select on a nonexistent legacy ID.
- `selectAppearance` calls AppearanceWorkflow.select → AppearanceCollection.apply(recordId) for curated or user records.
- `setRoomStyle` edits the effective authored value and retains the current association ID. It does not rewrite the stored named record.
- AppearanceCollection.changed carries `document.legacy` and `document.provenance` unchanged into the replacement document.
  Classic-room selection, curated/user selection and style edits therefore do not erase migration provenance or immutable snapshots.
- The migrated `legacy:current` snapshot can include original overrides. It remains recoverable from AppearanceEditor; choosing a classic tile intentionally selects the classic defaults instead.

Two LOOK feedback edge cases remain, without blocking the reachable action paths:

1. **C1 · A classic tile can lack an active mark after a valid legacy-empty import.** `LookTiles.active` falls back to comparing `tile.room.id` with displayedRoomId when activeId is empty.
   MainActivity.refreshAppearance instead names an unassociated authored value `appearance:custom`. With `document.legacy` empty, setRoom correctly uses empty association,
   so neither branch marks the chosen classic tile. Compare an unassociated active value with the tile value, or carry an explicit truthful selection marker.
2. **C2 · Saved names can collide with curated/classic names.** AppearanceDocument only requires unique names among users.
   A saved record named Glass can therefore produce a second tile labeled/announced Glass. LookTiles disambiguates classic-room names but not saved names.
   Preserve a “saved” qualifier in the label or accessibility name when names collide. The old editor distinguished LOAD SAVED DRAFT from curated drafts.

### AB1–AB4 verification

| Finding | Status at 711be8e | Code evidence / remaining work |
|---|---|---|
| AB1 · Setup result/error masking | **Partly fixed, still open** | SettingsSetups.kt:147–149 replaces fixed ifBlank priority with two LaunchedEffects. Independent later changes can display correctly, but reopening with both strings nonblank launches both effects and the apply-status effect is last. Same-frame changes have no event revision/order. A newer file/store error can still be hidden by stale workflow status. Show both distinct channels or select using owner-level revisions, not effect declaration order. |
| AB2 · Quantized timing keyboard no-op | **Keyboard fixed; TalkBack still open** | LightTime.next/stepOnRail is wired into SliderRow and both SettingRange endpoints. SettingsRangeAction.step uses it for keyboard arrows. However settingsRange SetProgress still calls action.set directly (SettingsControlAccess.kt:81). AndroidX adjustable forward/back actions add 1/20 of a continuous normalized range before invoking SetProgress. At 0.1s, +0.05 maps to about 0.1377s, then rounds back to 0.1s. The increment can remain a no-op for TalkBack, especially at minimum or a narrow interval endpoint. Route semantic adjustments through a quantization-aware value contract as well. |
| AB3 · Plus saves the wrong live color | **Fixed for the normal live path** | LightSheet.kt:112–114 reads PhosphorNative.beamColorNow and passes rgbOf to addCurrent. Steady authored colors copy exact stored triples; moving/generated/temporary colors use the snapshot. Generated-auto is disabled and the new slot worn. If the native read fails, the code deliberately falls back to authored color; that exceptional fallback is not device-tested here. |
| AB4 · Active/modified/temporary feedback | **Partly fixed, still open** | ownerLine now shows rolled/generated state and explains plus. But SetupsGroup still never reads instrumentRecall, so active setup association and modified state remain absent. SavedSwatch still calls savedWorn(light,index) without state.lightTemporary (LightSheet.kt:103; LightChoices.kt:29), so a saved color remains marked selected during a temporary roll even though preset marks suppress that state. |

The TalkBack path was checked against AndroidX AndroidComposeViewAccessibilityDelegateCompat: ACTION_SCROLL_FORWARD/BACKWARD on a continuous ProgressBarRangeInfo
uses `(max−min)/AccessibilitySliderStepsCount`, where the constant is 20, then calls SemanticsActions.SetProgress.
This is a different path from the app’s onKeyEvent stepper. No device accessibility claim is made by this source review.

Prime accepted a minimum of two slots in an active cycle. That deliberate choice is no longer an open blocker.
Numeric RGB sliders remain an intentional design change; accessible HSV editing is retained.

### Five dismissal findings: closure check

| Prior finding | Current code witness | Result |
|---|---|---|
| Double-counted card displacement | Sheets.kt:252–267 dragByFinger → dragBy sums only delta; no shownAtLastDelta compensation | Fixed. |
| Cancellation commits a pull | Sheets.kt:202–204 closes rejects cancelled; 391–392 cancelDismiss passes cancelled=true; 560 header onDragCancel calls cancelDismiss | Fixed for the reviewed cancellation callback. It clears raw travel and samples before return-home. |
| Cached speed survives a hold before release | Sheets.kt:243–245 appends release uptime/raw position; 384–385 uses releaseVelocity(now) | Fixed. Old samples age out relative to release. |
| SideEffect/programmatic pre-scroll changes pull state | Sheets.kt:400 rejects non-UserInput, matching post-scroll at 415 | Fixed. |
| Header-local positions underestimate flick speed | Sheets.kt:252–256 tracks accumulated deltas, 558–560 uses release/cancel paths; no moving-local-position VelocityTracker | Fixed. |

Inspected host tests cover plain delta accumulation while the card follows, flick→hold→release, and cancelled large/fast pulls returning home.
This review did not rerun them. Production thresholds are now 32dp/700dp/s for flick and 96dp for distance; the older design text still says 48dp/920dp/s.
That threshold change should be recorded as a tuning decision, not confused with the five correctness fixes.

### Check #2 disposition

LOOK actions and provenance paths are retained. All five named dismissal fixes are present in code.
Do not close AB1, the TalkBack part of AB2, or the remaining AB4 feedback issues yet.
AB3’s normal path is fixed. C1/C2 are specific selection-feedback edge cases for valid imported/saved documents.
Device acceptance and compilation remain with Prime; this section is exact-commit source evidence only.
