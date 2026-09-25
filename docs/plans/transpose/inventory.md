# Transpose inventory · Phase 0

Cartographer · 2026-09-24 · read-only research on `master` at `6bfb0b6` (source build Ben rejected: `9d5d77e`).
Reference: S25 build `06f84e2`. Serves [the transpose plan](README.md). Line numbers are for these two commits.

Summary:

- 124 commits since `06f84e2` (plus the plan commit `6bfb0b6`). 63 change product code; 61 change only docs/receipts.
- Kotlin UI grew from 7,273 to 10,174 lines. Prose blocks under controls: 44 → 90. Kotlin unit tests: 454 → 948. Rust tests: 73 → 134.
- The `×63.69·a` readout already existed on the S25. It became loud because AUTO can now reach ×256 (S25 cap: ×6) and because the band was put on an opaque bordered plate.
- The strongest cause of the mic "glitch" is the new AUTO framing controller (instant drop, fast regrow, ×256 ceiling). The device evidence (§5) agrees with this.
- LIGHT lost its colored swatch grid in `7ac1e55`. The nine presets became text rows with no color.

---

## 1. Port matrix (capabilities, not commits)

Verdicts: **KEEP** = behavior stays, no UI work · **PORT** = keep capability, re-surface in new UI · **SIMPLIFY** = keep a smaller version · **CUT** = remove the UI (engine may stay).

| # | Capability · what it does for a user | Commits | Main files | Verdict | Reason (anti-bloat law) |
|---|---|---|---|---|---|
| C1 | **Floating HUD** · small scope over other apps, with its own play/SRC/LIVE buttons | a3223b8, 44172dc, 35d8c98, 73e13ea | `FloatingHudService.kt`, `HudPolicy.kt`, `SurfaceHost.kt`, `SurfaceOwner.kt`, `rust/src/surface_lifecycle.rs`, `surface_policy.rs` | **PORT** | Real capability. Today it needs 3 controls + 1 status line in Settings (`Sheets.kt:1337-1346`). Make it one entry. Law 1, 2. |
| C2 | **Surface ownership, PiP rebind** · scope survives PiP/HUD handoff | a3223b8, 9ff96bd | `SurfaceHost.kt`, `SurfaceOwner.kt`, `PictureInPicturePolicy.kt` | **KEEP** | Invisible correctness. |
| C3 | **HOLD / display pause** · freeze the image, pan/pinch the held frame | 44172dc, 35d8c98, 0591d73, 73e13ea, 2997afe | `rust/src/pause.rs`, `ui/PauseDisplayPolicy.kt`, `CaptureReadFence.kt`, `CapturePauseObservation.kt` | **SIMPLIFY** | Keep the engine. One entry (play key when display-only). Cut the Settings duplicate "PAUSE DISPLAY ONLY", "RESET INSPECTION" and the stage pause prose (`PhosphorScreen.kt:715-720`). Law 1, 5. |
| C4 | **Root capture hardening** · stereo probe bounds, PCM epochs, deferred root preview | 15bcbe5, 79af079, b222818 | `root-helper/**`, `RootEpoch.kt`, `RootCaptureSession.kt`, `RootCapturePolicy.kt` | **KEEP** (engine) / **CUT** (UI) | Product flag is off. Remove the root prose from SOURCE (`Sheets.kt:791-802`, `815-816`, `838-842`). Developer view only. |
| C5 | **Six saved colors + generated cycles** · 6 slots, shuffle, auto color, random interval, TRACK/TIMER | 7ac1e55, 1e68b63, 73e13ea | `ui/LightSettings.kt`, `ui/LightSheet.kt`, `rust/src/light_cycle.rs`, `settings/SettingsArchive.kt` | **PORT** | Good capability, bad surface: text rows replaced the S25 swatch grid (§2.5, §3.3). Rebuild on the swatch grid in Phase 2. |
| C6 | **Serialized settings writes** · no lost writes under fast edits | 1e68b63 | `settings/SettingsWriteOwner.kt` | **KEEP** | Invisible correctness. |
| C7 | **Instrument presets** · save/recall a whole look+tuning; curated starters; import/export with conflict preview | 019c0da, 5b1be19, c7b5c1f, 914f585, 3f2c198, 8c9d56e | `settings/instrument/*`, `rust/src/instrument.rs`, `ui/InstrumentPresetSheet.kt` | **SIMPLIFY** | Keep typed presets and atomic apply. The sheet has 28 PresetKey call sites and 14 prose blocks. Keep list + apply/save/rename/delete. Move import conflict UI to developer view or later. Law 2, 6. |
| C8 | **Signal check / observations** · per-source format, route, admitted/consumed frames | 15e4072, 6355074, a0d3c12, 23541ca | `Signal*.kt`, `ui/SignalCheckSheet.kt`, `rust/src/engine.rs` stats | **SIMPLIFY → developer view** | Valuable for the mic fix (§5). Not for users. Cut the stage "SIGNAL CHECK" action (`PhosphorScreen.kt:706-714`) and the Settings inline copy (`Sheets.kt:1231`). Law 1, 5. |
| C9 | **Settings dismissal gesture machinery** · accidental-dismiss protection, flick rules, child-input exclusion | f504b3f, 374326f, 4a0e8b6, 5d4d19d, d8760e5, 82f3f98 | `ui/SettingsInteraction.kt`, `SettingsGestureAdapter.kt`, `SettingsSheetAdapter.kt` | **SIMPLIFY** | 660 lines, and it still fails REC03 (no pull-to-dismiss after scrolling). It also shows its own error prose (`Sheets.kt:529-531`). Rebuild the drawer dismissal in Phase 3. |
| C10 | **Settings expandable sections with summaries** | 374326f, 485bc9f | `SettingsSheetAdapter.kt:276-316`, `Sheets.kt:1573-1608` | **PORT** | Ben liked the expanded sections (REC01). Keep the pattern and cut the content. Summary lines are engineering readouts (`×%.3f`). Law 5. |
| C11 | **Accessible settings controls** · semantics, range actions, keyboard focus | f9a1106, 8bf88d0 | `ui/SettingsControlAccess.kt`, `Controls.kt` | **KEEP → PORT** | Move into the one new button family. Needed for the plan's accessibility goal. |
| C12 | **Authored appearance system** · appearance documents, migration, contrast checks, curated looks | 4375041, 855ae09, 7fdab21, 485bc9f, 4f285a6, 1921b38 | `settings/appearance/*`, `ui/AppearanceMigration.kt`, `AppearancePalette.kt` | **KEEP** (engine) | Lossless migration and contrast are real value. |
| C13 | **Appearance editor UI** · raw RGB/ARGB hex fields, 23 text buttons, preview/apply/save | 485bc9f, 4f285a6, 7204b91 | `ui/AppearanceEditor.kt` | **CUT** (→ developer view) | A raw-value editor that the plan rejects ("direct previews, not a raw-value editor"). REC06. 12 prose blocks. Law 2, 3. |
| C14 | **Legacy ROOM sheet + style chips** (pre-S25, extended) | 485bc9f | `Sheets.kt:991-1124` | **PORT** | Merge with C12 into one theme picker: Glass / AMOLED / Light / Dark tiles as live previews. Today two theme systems coexist. Law 1. |
| C15 | **Manual: indexed offline field manual + bestiary** | cca1e8d, acb3d5d, 2997afe, b222818 | `ui/ManualContent.kt`, `ui/ManualSheet.kt` | **SIMPLIFY** | Keep the search, index and humor. Split everyday vs developer (REC08/09). Fix Back: `PhosphorScreen.kt:349` closes the whole sheet, and there is no chapter back (REC07). |
| C16 | **Brightness pin** · keep panel brightness up while scoping | b74beb2 | `ForegroundBrightnessPolicy.kt` | **PORT** | One toggle. Cut 3 prose blocks (`Sheets.kt:1310-1316`). |
| C17 | **Portable settings archive checksums** | b74beb2 | `settings/SettingsArchive.kt` | **KEEP** | Invisible. |
| C18 | **Tactile vector console** · beveled keys, vector glyphs, contrast tokens, multi-row reflow, edge flash, dashed disabled PLAY | 7204b91, 0d9d751, bd35b08, 15742d2, 4bb2c0f, 6e819f5, 954984c, 804e40d | `ui/TactileConsole.kt`, `ConsoleTactileTokens.kt`, `StageReadability.kt` | **SIMPLIFY** | Replace with the S25 key language (§3.1). Keep: font-independent vector glyphs, contrast token math, 48dp minimums, TalkBack descriptions. Cut: uneven widths/heights, 1/2/4-row reflow, 80 ms edge flash. Law 3. |
| C19 | **Service-owned mic + visualization mixer** · mic keeps running in background; choose input; mix mic into capture | 8bed59a, 14dac99, ca0c63d, 583f4af, 23541ca | `MicCaptureService.kt`, `MicController.kt`, `MicrophoneRoutes.kt`, `MicrophoneRoutePolicy.kt`, `CaptureMixSession.kt`, `CaptureMixPolicy.kt`, `ui/MicrophoneControls.kt` | **KEEP** service · **SIMPLIFY** path · **PORT** UI | Keep the FGS ownership. Standalone mic should not go through the timestamp resampler (§5 H2). SRC: one mic row + input picker. "Include mic" becomes one toggle under capture. |
| C20 | **Stereo-first mic default** | 583f4af | `MicrophoneRoutePolicy.kt:31-40`, `decisions/2026-09-13-stereo-first-input.md` | **KEEP** | Ben's decision. Bound the format churn (§5 H5). |
| C21 | **HDR request + truthful SDR fallback** | 79601c0, 0c86b8f, 3e0610d, bb3044d | `HdrPresentationPolicy.kt`, `rust/src/render.rs`, `surface_policy.rs` | **KEEP** behavior · **SIMPLIFY** UI | One toggle. The HDR status line goes to developer view (`Sheets.kt:1309`). Law 5. |
| C22 | **Default source on launch + automatic permission popup** | ca0c63d | `StartupCoordinatorPolicy.kt` | **PORT** | One 3-way choice. Cut the prose (`Sheets.kt:1220`). Fold the popup toggle in, or cut it. Law 2, 4. |
| C23 | **Sheet chrome polish** · vector close/tick/search-clear, opaque plates, hide band under sheets, narrow-type trailing actions, latched ChipCell | 59fb128, 1f7d728, 12d457f, 892180d, 5c6bdad | `Controls.kt:370-418`, `Sheets.kt:648-686` | **KEEP** marks & band hiding · **CUT** TrailingActionRow | Trailing actions exist only to carry prose. Latch state goes into the one button family. |
| C24 | **Readable stage chrome** · opaque bordered status plate | 7204b91, bd35b08 | `Console.kt:105-109`, `StageReadability.kt` | **CUT** | This made the engineering band louder than on the S25 (§4). Law 5. |
| C25 | **Unit 1 AUTO framing** · pinch adjusts remembered framing and AUTO stays on; quiet sounds grow; rotated XY fit | 583f4af, 9d5d77e, a6cd028 | `rust/src/engine.rs:655-828`, `render.rs:876-892`, `ui/AutoFramePreference.kt`, `ui/Gestures.kt` | **SIMPLIFY / rework** | Keep Ben-confirmed semantics (pinch = preferred framing, AUTO stays on) and rotated fit. Rework the controller: ceiling, instant attack, noise gate (§5). Cut numeric ribbons and summaries. |
| C26 | **Acceptance trace instrumentation** (debug-only logs) | many | `app/src/debug/.../AcceptanceTrace.kt` | **KEEP** | Zero cost in release. Useful for device UX scripts. |
| C27 | **Docs / receipts / reviews** | 61 docs-only commits | `docs/**`, `HANDOFF.md`, `spec/**` | **KEEP** | History law. No product effect. |

Unassigned code commits: `85ca212` (build.gradle one-liner, root epochs) → C4.

---

## 2. Current build UI surface (`6bfb0b6` ≈ `9d5d77e` UI)

### 2.1 Stage layers (`ui/PhosphorScreen.kt`)

| Layer | What | Where |
|---|---|---|
| 0 | Scope `SurfaceView` (Rust/wgpu) | `PhosphorScreen.kt:571` |
| 0.5 | Stage gestures: 1-finger drag = gain/frame, 2-finger pinch = gain/frame (3D: dolly), 2-finger horizontal swipe = mode step, 2-finger vertical = glow, bottom-edge pull = console → settings, tap = console toggle, double-tap = play (optional) | `PhosphorScreen.kt:590-701`, `Gestures.kt:410-552` |
| Ribbon | Numeric gesture readout next to the finger | `PhosphorScreen.kt:705`, `Gestures.kt:567-593` |
| Signal action | "SIGNAL CHECK" button on the stage whenever no signal / no source / capture status / remote failure | `PhosphorScreen.kt:706-714` |
| Pause label | Prose plate at top during HOLD | `PhosphorScreen.kt:715-720` |
| Status band | src, mode, gain, grid data, HUD lines | `PhosphorScreen.kt:724-732`, `Console.kt:83-135` |
| Bench POST | "V1 ENGINE · OK" lines (Annotated look only) | `PhosphorScreen.kt:735-744`, `Console.kt:344-366` |
| Console | Transport + MODE + SRC + ⋯ | `PhosphorScreen.kt:747-777`, `Console.kt:177-305`, `TactileConsole.kt:41-218` |
| Overflow popout | 2×2 nav + 3 quick toggles + AUTO PiP chip | `PhosphorScreen.kt:780-825`, `Console.kt:386-529` |
| Sheets | SOURCE, MODE, LIGHT, INSTRUMENT, SIGNAL_CHECK, ROOM, SETTINGS, MANUAL | `PhosphorScreen.kt:842-897` |

### 2.2 Console (tactile, look v2 = AMOLED default on fresh install)

- Keys: PREV 48w, PLAY (primary) min 56h, NEXT 48w, MODE ≥64w, SRC ≥56w, ⋯ 48w. Others min 48h (`TactileConsole.kt:273`, `ConsoleTactileTokens.kt:64-81`).
- The layout switches between 1, 2 and 4 rows by width and font scale (`ConsoleTactileTokens.kt:71-75`). With a track, transport takes its own row (`TactileConsole.kt:172-179`).
- Keys differ in width and height. PLAY is taller. Rounded by `style.cornerRadius` (`TactileConsole.kt:278`). The selected mark is an accent underline + left tick (`:307-312`). Disabled is a dashed outline (`:284-285`). An 80 ms edge flash plays on press and on room/mode/source events (`:83-105`).
- The track title marquee and seek rule sit inside the key well (`TactileConsole.kt:198-214`).
- Legacy (non-tactile) path still exists: `Console.kt:262-302`.

### 2.3 Overflow popout (`Console.kt:478-527`)

PiP · light · room · settings (2×2 nav cells), FPS / HUD / GRID quick toggles, "AUTO PiP · on/off" ChipCell.

### 2.4 Sheets

**SOURCE** (`Sheets.kt:690-856`): MY LIBRARY (open file, open folder) → QUEUE (+ prose) → OTHER APPS header with app marks → "everything playing" → MicrophoneMixControls (INCLUDE MIC StoneToggle, 2 level rules, prose) → root block (prose + 2 keys, flag-gated) → capture status prose → TrailingActionRow prose + "manual…" → notification-access prose + "grant…" → MICROPHONE: "start selected microphone" + input rows + status prose + Bluetooth prose/2 rows + "retry selected microphone" + "stop microphone" (`MicrophoneControls.kt:21-36`) → REMOTE flow → prose → ⏻ LIVE StoneToggle. Consent card: 2 prose + LinkCard + 2 keys (`:704-729`).

**MODE** (`Sheets.kt:860-987`): random row, BAN FACES chip + 11 chips + prose, 11 mode rows in 4 groups, GEOMETRY 5 chips + AMOUNT rule, 2 prose.

**LIGHT** (`LightSheet.kt:53-159`): "RECALL INSTRUMENT" + prose → optional seizure guard prose + 2 keys → owner status prose → PRESETS: **9 full-width text rows, no color** (`:91-96`) → SAVED COLORS n/6: per slot a 24dp swatch + 3 full-width text keys Select/Edit/Delete (+HSV + 3 RGB rules when editing) (`:98-123`) → ADD CURRENT PRESET COLOR → RANDOM COLOR: ROLL NOW, Automatic generated color, Le random order + prose → CYCLE: TIMER, TRACK, Random interval, 1–2 rules, prose → HOLD prose.

**INSTRUMENT PRESETS** (`InstrumentPresetSheet.kt:49-162`): 3–5 status prose → recovery keys → CURATED (key + purpose prose each) → SAVED n/64 → SELECTED: APPLY, DUPLICATE, UPDATE, RENAME, EXPORT, DELETE → NAME AND SAVE (field + key) → PORTABLE DOCUMENT (prose + IMPORT PREVIEW + EXPORT ALL) → import preview per-record choice keys.

**SIGNAL CHECK** (`SignalCheckSheet.kt:31-59`): status, prose, label/value rows, OPEN SOURCES. One real action → law 4.

**ROOM** (`Sheets.kt:991-1124`): 2-col grid of legacy room tiles, STYLE: FEEL / MOTION / CORNERS / LABELS cycle chips, live sample, prose.

**SETTINGS** (`Sheets.kt:1186-1611`), 6 expandable sections:

| Section | Controls | Prose blocks |
|---|---|---|
| SIGNAL & STARTUP (`:1216-1276` + remote `:1471-1492`) | source row → SRC; DEFAULT none/mic/capture; AUTOMATIC PERMISSION POPUP; SIGNAL CHECK (inline expand); MANUAL GAIN rule 0.1–7; AUTO-FRAMING; VIEW LOCK; RESET AUTO FRAMING ×1.000; RETRY FRAMING SAVE; relay LATENCY tight/balanced/safe | 6 (`:1220`, `:1253-1259` "LOCAL AUTO FRAMING ×%.3f", `:1262`, `:1267-1275`, `:1482-1491` ×2) |
| BEAM & LIGHT (`:1277-1301`, `:1499-1518`) | FOCUS, BEAM, BEAM RANGE ⚄, GLOW, GLOW RANGE ⚄, GRID, GRID DATA, light row, instrument row | 1 |
| DISPLAY & HUD (`:1302-1420`) | PIN BRIGHTNESS, REQUEST HDR, PAUSE DISPLAY black/hold, PAUSE DISPLAY ONLY, RESET INSPECTION, CONTROLS ALWAYS VISIBLE, AUTO PiP, ENTER PiP, FLOATING HUD, HUD BACKGROUND, SHOW/HIDE HUD, DOUBLE TAP, BACKGROUND LINGER, FULLSCREEN, SCOPE ROTATION, UI PLACEMENT (16) | 8 (`:1309`, `:1310-1312`, `:1313-1315`, `:1316`, `:1324`, `:1346`, `:1355-1359`, `:1408-1419`) |
| MOTION & PERFORMANCE (`:1421-1470`) | FRAME RATE ×4, BEAM RATE ×3, STATS HUD, BAND | 3 (FpsNote, BeamRateNote, HUD status) |
| APPEARANCE (`:1493-1498`) | full `AppearanceEditor` (~40 controls incl. 10 hex fields, `AppearanceEditor.kt:88-185`) + room row | 12 EditorText blocks |
| ABOUT & MANUAL (`:1519-1564`) | EXPORT, IMPORT, manual row | 2 + transfer status + CAL stamp |

Section headers carry engineering summaries, e.g. `"manual ×%.2f · auto frame ×%.3f"` (`Sheets.kt:1573-1605`).
Every sheet in Glass adds a disclaimer under the title (`Sheets.kt:526-528`). Settings adds "Drag paused after delayed input…" (`:529-531`).

**MANUAL** (`ManualSheet.kt`): search field, index, chapters, bestiary key, 5 LinkCards (`:315-327`). System Back closes the whole sheet (`PhosphorScreen.kt:349`).

**Floating HUD** (Android Views, `FloatingHudService.kt:137-195`): header drag label, ↗, ×, FIT, LIVE, SRC, ‹, play, ›, info line, resize handle.

### 2.5 Duplicates (law 1: one home per control)

| Setting / action | Homes | Refs |
|---|---|---|
| AUTO PiP | overflow chip · Settings DISPLAY | `Console.kt:523-527` · `Sheets.kt:1332-1335` |
| Enter PiP | overflow "PiP" · Settings ENTER PiP | `Console.kt:479` · `Sheets.kt:1336` |
| Frame rate | overflow FPS cycle · Settings FRAME RATE chips | `Console.kt:504-508` · `Sheets.kt:1423-1433` |
| Stats HUD | overflow HUD cycle · Settings STATS HUD | `Console.kt:510-516` · `Sheets.kt:1447-1455` |
| Grid | overflow GRID · Settings GRID | `Console.kt:517-521` · `Sheets.kt:1502` |
| LIGHT sheet | overflow "light" · Settings row | `Console.kt:480` · `Sheets.kt:1512` |
| Theme | overflow "room" → ROOM sheet · Settings APPEARANCE editor · Settings room row (two theme systems) | `Console.kt:481` · `Sheets.kt:1494-1497` |
| Instrument presets | LIGHT "RECALL INSTRUMENT" · Settings row | `LightSheet.kt:70` · `Sheets.kt:1515` |
| Signal check | stage action → sheet · Settings inline expander | `PhosphorScreen.kt:712` · `Sheets.kt:1231`, `SignalCheckSheet.kt:23-28` |
| Source picker | SRC key · Settings "source · x" row · Signal check "OPEN SOURCES" · HUD SRC | `TactileConsole.kt:134` · `Sheets.kt:1217` · `SignalCheckSheet.kt:57` |
| Manual | Settings about row · SOURCE "manual…" | `Sheets.kt:1551` · `:814-819` |
| Settings sheet | console swipe-up · bottom-edge pull · overflow "settings" | `Console.kt:233` · `PhosphorScreen.kt:640-683` · `Console.kt:482` |
| Stop mic | "stop microphone" row · ⏻ LIVE toggle · notification action | `MicrophoneControls.kt:35` · `Sheets.kt:844-852` · `MicCaptureService.kt:139` |
| Zoom / gain | stage pinch · stage 1-finger drag · MANUAL GAIN rule · AUTO chip · RESET key | `Gestures.kt:482-549` · `Sheets.kt:1233-1260` |
| HOLD | play key (display-only sources) · PAUSE DISPLAY ONLY · HUD LIVE | `TactileConsole.kt:111-125` · `Sheets.kt:1320` · `FloatingHudService.kt:174` |
| Photosensitivity guard text | LIGHT · INSTRUMENT | `LightSheet.kt:74-75` · `InstrumentPresetSheet.kt:79-80` |
| Import/export | settings archive · instrument documents | `Sheets.kt:1519-1531` · `InstrumentPresetSheet.kt:126-130` |

The first six rows were already duplicated on the S25 (same popout, `06f84e2:Console.kt:465-515`). They are inherited, not new.

### 2.6 Button families in use (law 3: one family)

| Family | Shape / size | States | Defined | Used in |
|---|---|---|---|---|
| TactileConsoleKey / TactileOverflowKey | rounded (room radius), bevel, 48 or 56 min h, varying w | raised/sunk, underline+tick selected, dashed disabled, focus ring, edge flash | `TactileConsole.kt:223-346` | console |
| OverflowHandleKey | 52×48, hairline | accent rim active | `Console.kt:310-338` | legacy console |
| StoneKey | square 52 (via policy), carved bevel | sink 1dp | `Controls.kt:128` | legacy PLAY, style sample |
| StoneToggle | 48 h, full width, bevel | engaged accent | `Controls.kt:241` | ⏻ LIVE, INCLUDE MIC |
| FlatKey | hairline rect, 48 min, text | accent rim + text on press/active | `Controls.kt:327-368` | 25 call sites |
| ChipCell | hairline rect, full width, 48 min | latched = surface2 + 1dp text sink + accent | `Controls.kt:463-499` | 33 call sites |
| SheetRow | full-width row, glyph, 48 min | checked = tick + accent + surface2 | `Controls.kt:421-460` | 18 call sites |
| SettingsGlyphRow | full-width row, glyph | none | `Sheets.kt:1162-1183` | 6 |
| LightKey | full-width, 48 min | 2dp accent border active | `LightSheet.kt:162-170` | 15 |
| PresetKey | full-width Mono text box | accent active, muted disabled | `InstrumentPresetSheet.kt:165-171` | 28 |
| AppearanceButton | full-width, 1dp lineStrong, 14sp BasicText | "· unavailable" suffix | `AppearanceEditor.kt:194-200` | 23 |
| SignalCheckAction | text only, no border | none | `SignalCheckSheet.kt:62-67` | 3 |
| ManualKey / LinkCard | own styles | – | `ManualSheet.kt:114`, `:128` | manual, SOURCE |
| QuickToggle / PopoutNavCell | borderless glyph cell | accent text | `Console.kt:533-582` | overflow |
| Inline rows (mode, queue, room tile) | ad-hoc Row+border | accent border | `Sheets.kt:885-898`, `939-955`, `749-760`, `1012-1071` | MODE, SOURCE, ROOM |
| Android `Button` | platform | platform | `FloatingHudService.kt` | floating HUD |

That makes 16 families with 4 different "selected" languages: accent rim, 2dp border, surface2 fill + sink, underline tick.

---

## 3. S25 (`06f84e2`) UI surface

### 3.1 Console key language (what Ben loved)

`06f84e2:app/src/main/kotlin/dev/phosphor/mobil3/ui/Console.kt:176-302`, `Controls.kt:118-351`, `Dimens.kt:15-21`

- **One row, always:** `[◂◂] [▶ stone] [▸▸] · [MODE] [SRC] ······ [⋯]` (`Console.kt:252-286`). Title marquee and seek rule sit above the row only when a track is present.
- **Two key types, clear hierarchy:** exactly one dimensional key, PLAY (`StoneKey`, square 52dp, 2dp catch-light top/left and shadow bottom/right, sinks 1dp on press). Everything else is `FlatKey` (44dp tall, hairline border, **sharp 90° corners**, 14dp side padding, mono `Type.data`) (`Controls.kt:118-224`, `313-351`).
- **One state language:** press or active = accent rim + accent text + 10% accent tint, instant, no animation delay (`Controls.kt:322-339`). Idle text is `ink2` (quiet).
- **Same height across flat keys; aligned on one baseline.** The ⋯ handle is also flat and hairline (`Console.kt:295-318`).
- **Room character changes the stone, not the layout:** Engraved (AMOLED) = double hairline, Glass = translucent slab with sheen, Carved = bevel (`Controls.kt:134-207`).
- Part designators (S1/V2/J1/S9) appear only in the Annotated look.

What made it feel good, concretely: one row, one hero key, flat quiet siblings, sharp corners, instant accent feedback, no reflow, no text beyond 3–4-letter labels.

### 3.2 Stage (`06f84e2:PhosphorScreen.kt:620-650`, `Console.kt:81-135`)

Status band text only, **no plate**, `ink2` at **70% alpha** (`Console.kt:106`, `:129`). The `×gain·a` tag was already there (`:124-126`), but AUTO capped at ×6 (`06f84e2:rust/src/engine.rs:263`) and glided, so it read as a small, calm number. No stage SIGNAL CHECK button, no pause prose.

### 3.3 LIGHT swatch grid (`06f84e2:LightSheet.kt:56-230`, `Controls.kt:420-437`)

- First thing in the sheet: **PRESETS as a 3×3 grid of real color blocks** (`LazyVerticalGrid(GridCells.Fixed(3))`, 200dp tall, `:103-114`). Each `SwatchCell` is a 38dp full-width color block + tiny label. Selected = 2dp accent border + accent label.
- CUSTOM: chips **1 / 2 / 3**. Tapping a count makes 44dp color squares appear. Tapping a square opens the HSV square + hue rule (`:119-178`).
- CYCLE appears only when ≥2 colors: gradient strip of the ring, LEG rule, TIMER/TRACK chips, 1 line of prose (`:183-222`).
- One closing line: "the beam wears it immediately — browse freely".
- 4 prose blocks total (current: 9).

### 3.4 Other S25 sheets (for comparison)

| Sheet | S25 content | Ref |
|---|---|---|
| SOURCE | Same library/capture/remote flow. **Mic = one row "built-in mic"; tap starts and closes the sheet.** No mixer, no input list. | `Sheets.kt:562-740` (mic `:714-719`) |
| MODE | Same as today (random, ban faces, groups, geometry) | `Sheets.kt:744-872` |
| ROOM | Room tiles + style chips | `Sheets.kt:874-1044` |
| SETTINGS | Flat headed groups, no expanders: SIGNAL (FOCUS, GAIN 0.1–7, AUTO-GAIN, VIEW LOCK, BEAM, ⚄ ranges, GLOW), DISPLAY (9 toggles + FPS + BEAM RATE), PERFORMANCE (HUD, BAND), REMOTE, ROOM & LIGHT (2 rows), MIGRATION, ABOUT. Two columns in landscape. | `Sheets.kt:1089-1424` |
| Overflow | Identical to today | `Console.kt:465-515` |

Honest note: the S25 also had prose under controls (44 blocks) and the same overflow duplicates. It felt calmer because it had fewer controls, fewer button families (about 8: StoneKey, StoneToggle, FlatKey, ChipCell, SheetRow, SwatchCell, overflow handle, popout cells), one selected language (accent rim/text), color where color matters, and a quiet stage.

---

## 4. Stage readouts (text drawn over the scope)

The Rust renderer draws **no text** (`rust/src/render.rs`: no font/text path). It draws one non-text readout: the graticule spacing follows effective gain, `grid_spacing_fraction = (0.1125 * gain).clamp(0.035, 0.55)` (`render.rs:892`), only when GRID is on.

| # | Text | Source | Shown when | Verdict |
|---|---|---|---|---|
| R1 | `src · <label>` (+ `· no signal`) | `Console.kt:90-93` | band on/auto (default auto = with console) | Keep a quiet source name, or move it to the SRC key. |
| R2 | **`<mode> ⚄ · ×<gain>[·a]`**, e.g. `xy45 · ×63.69·a` | `Console.kt:94-100`. `gain` = `PhosphorNative.gainNow()` (`MainActivity.kt:1154` → `jni_glue.rs:613-618` → `GAIN_MILLI`, written by AUTO each frame `render.rs:880-885`). `·a` = `state.localAutoGain`. | same as R1 | **Developer view.** Ben's `×63.69·a`. |
| R3 | remote line `<mode> · auto ×g · pc` | `MainActivity.kt:1161-1172` | remote geometry | Developer view. |
| R4 | `L · 0.012 · −38.4 dBFS` / `R · …` | `ScopeUiState.kt:207-214`, `Console.kt:118-121` | GRID DATA on | Developer view. |
| R5 | `119.4 fps · 480 segs · x Mb/s` | `MainActivity.kt:1195-1199`, `Console.kt:122` | HUD on/auto (default auto) | Developer view. |
| R6 | `bridge · buf … · und … · skip … · drop … · LEAK` | `MainActivity.kt:1203-1213` | HUD + remote | Developer view. |
| R7 | Gesture ribbon: `auto frame × 1.125`, `manual gain × 2.40`, `glow 40 %`, `view locked`… | `Gestures.kt:475, 486-501, 523-543` | during a gesture, 600 ms fade | Replace with a quiet closer/farther cue. Numbers go to developer view. |
| R8 | `SIGNAL CHECK` button | `PhosphorScreen.kt:706-714` | no signal / no source / capture status / remote failure | Cut from the stage. One calm hint at most. |
| R9 | Pause label prose | `PhosphorScreen.kt:715-720` | HOLD | Replace with a glyph state on the play key. |
| R10 | Bench POST `V1 ENGINE · OK` … | `Console.kt:344-366` | Annotated look, startup | Keep (look-specific humor). It retires itself. |
| R11 | Track title marquee + seek times | `TactileConsole.kt:199-213`, `Console.kt:138-173` | with a track | Keep (in console). |
| R12 | Status band plate (opaque surface + hairline) | `Console.kt:107-109` (bd35b08) | band shown | Cut (S25 had no plate). |

Default today: `hud_mode=1`, `band_mode=1` (`MainActivity.kt:2202-2203`). Every console summon therefore shows R1+R2+R5 on a plate. The S25 had the same defaults, but no plate and a calmer number.

---

## 5. Mic glitch and zoom · source hypotheses

### 5.0 Device evidence (Prime, test bed `NAAIB700B7373PZ`, `9d5d77e`, built-in mic, quiet room, XY45, AUTO on)

- 12 s at 30 fps: the trace collapses to a small blob or center dot for **13–14 frames (0.43–0.47 s)** at t≈4.2 s and t≈11.5 s, then regrows. There was no input event. Trace height swings **72–244 px** in quiet ambient.
- Band: `119.4 fps · 480 segs · ×29.88·a`, then ~1 s later `0 segs · ×23.58·a`.
- logcat: wgpu `VKDBGUTILWARN003` validation warnings every frame.

### 5.1 Ranking for Prime's three candidates

| Rank | Candidate | Verdict | Evidence |
|---|---|---|---|
| **1** | **AUTO loud-frame protection drop + fast regrow** | **Most likely cause of the 0.45 s collapses and the 72–244 px pumping** | See H1. The regrow time follows from the constants. Gain moved ×29.88 → ×23.58 in ~1 s. |
| 2 | Empty drains / read cadence starvation | **Not the collapse.** The `0 segs` sample is expected from the 100 Hz push / 120 Hz render beat. At most a faint shimmer. | See H3. A mic data *gap* reaches the renderer as mixer zeros, not as missing frames, because the mixer always publishes 480 frames per 10 ms (H2). |
| 3 | Validation-layer cost | **Not causal** for the collapse (render held 119.4 fps). Worth removing from debug builds. | See H8. The S25 debug build had the same `Instance::default()`. |

### 5.2 Mic hypotheses, ranked

**H1 · AUTO framing pumps and snaps (new controller from `583f4af`).** *High confidence.*
- Code: `rust/src/engine.rs:655-664` constants: `AUTO_GAIN_MAX = 256` (S25: 6), `GAIN_RISE_SECONDS = 0.35`, `PROTECTION_RELEASE_SECONDS = 0.75`, `STRUCTURE_PEAK_MIN = 0.0005` (−66 dBFS), `STRUCTURE_CORRELATION_MIN = 0.20`.
- `engine.rs:818-820`: if target < effective, gain snaps down **in the same frame** (instant attack).
- `engine.rs:807`: the peak memory decays with τ = 0.75 s. `:821-823`: gain re-rises with τ = 0.35 s, but only on "structured" frames.
- Regrow time ≈ 0.75·ln(P_transient / P_ambient) + ~0.35 s. A 1.5–2× transient gives 0.3–0.9 s. The 0.45 s collapses match a small room bump or click.
- Noise counts as structure. Low-frequency room noise easily has lag-1/2 autocorrelation ≥ 0.2 (`engine.rs:684-700`), and −66 dBFS is below a phone mic noise floor. So in a quiet room AUTO blows the noise up toward 80% fill at gains of ×20–×60. Any real sound then snaps it down. That is the 72–244 px swing and Ben's `×63.69·a`.
- S25 comparison (`06f84e2:rust/src/engine.rs:255-266`): holds below peak 0.02, peak release 0.999/frame (τ≈8 s at 120 fps), 0.05 glide both ways, cap ×6. Slow and calm.
- Confirm on device (any one):
  1. Repeat the 12 s capture with AUTO off (manual ×7) or VIEW LOCK on. If the collapses and pumping disappear, H1 is confirmed.
  2. Turn **GRID on** and repeat. The graticule spacing is a gain meter (`render.rs:892`). If it jumps at the same moment as the collapse (from 0.55 clamp down), the gain dropped (H1). If the grid holds while the trace becomes a dot, the data went to zero (H2).
  3. `adb shell setprop log.tag.PhosphorAcceptance VERBOSE` and read `event=framing_sample … effective_gain=… raw_left=… raw_right=…` at 2 Hz (`MainActivity.kt:1180-1185`). A raw-peak spike with a gain drop means H1. Raw peak 0 means H2.

**H2 · Mixer zero-fill gaps on the standalone mic path (new in `8bed59a`).** *Medium.* The trace goes to a center point while segments stay non-zero.
- S25 pushed AudioRecord reads straight to the native ring (`06f84e2:MicController.kt:92-97`). Now: reader → `CaptureMixInput` timeline → a separate timer thread renders 480 frames every 10 ms at **now − 50 ms** by AudioTimestamp and pushes them (`CaptureMixSession.kt:23-35`, `CaptureMixPolicy.kt:104-129`, `:157-181`).
- `sample()` returns null (→ 0.0 output, with a 20 ms level ramp, `CaptureMixPolicy.kt:164-173`) when:
  - (a) the reader is >200 ms late (`:107`),
  - (b) the requested position leaves `[first, end)` (`:110`, `:116`): pipeline latency > 50 ms, or AudioTimestamp `framePosition` domain ≠ the reader's own `frames` counter (`MicController.kt:204-206` passes the app counter as `frame` and the HAL timestamp as `clock`, mixed at `CaptureMixPolicy.kt:108`),
  - (c) a clock check clears the queue: non-monotonic timestamp or |drift| > 1000 ppm (`:89-96`).
- Zeros draw the beam at the center point (or nothing, if the DSP drops zero-length segments). Either way the figure vanishes at once, with no persistence fade of the old shape. Not verified on device.
- Also: once the 200 ms window is full, every offer increments `discontinuities` (`:71`), so the counter is useless as a gap signal. For the standalone mic, `describe()` is not shown anywhere (only `CaptureService.mixDescription()`, `CaptureService.kt:567`).
- Confirm: log per 10 ms render the count of null samples, plus `anchor.frame − end` (domain offset) and clear() events. A gap during a collapse, with the grid holding, confirms H2. Quick A/B: a debug build that pushes standalone-mic reads directly with `pushCaptureRead` (the S25 path) and bypasses the mixer.

**H3 · Empty drains from push/render cadence beat.** *Low for collapse, explains `0 segs`.*
- The publisher pushes exactly 480 frames every 10 ms (100 Hz, `CaptureMixSession.kt:34`). The renderer drains the whole ring each frame at ~120 Hz (`render.rs:786-801`, `phosphor-audio/src/ring.rs:86-97`). About 1 frame in 6 gets 0 samples and deposits nothing (`render.rs:955+`). A single 500 ms `segs` sample (`jni_glue.rs:969`) reads `0 segs` ~17% of the time. So `0 segs` is expected, not starvation.
- The recorder is adequate: 100 ms buffer (`MicController.kt:122-126`), `READ_BLOCKING` 10 ms chunks (`:177`, `:189`). The S25 had the same 10 ms cadence.
- A starvation long enough to fade the trace would need the publisher thread (plain `parkNanos`, resets after >20 ms lateness, `CaptureMixSession.kt:28-30`) to stall for hundreds of ms. The render thread would still hold 119 fps.
- Confirm: the Signal Check "Visual admission" counter (`SignalNativeObservation.kt:97`) should advance at 48,000 frames/s. A shortfall during a collapse means starvation.

**H4 · Strict route check kills the recorder.** *Medium for "stops working"; not the 0.45 s collapse.*
- `routed()` requires every routed device id == the selected id (`MicrophoneRoutePolicy.kt:29`). It is checked twice per 10 ms read (`MicController.kt:184`, `:197`), on API 36 via `routedDevices` (`MicrophoneRoutes.kt:29-33`). Any exception or transient null also counts as false.
- On false: "Selected microphone route was lost" → service stops, and there is **no automatic restart** by design (`MicCaptureService.kt:12`). The UI flips to not-live (`MainActivity.kt:1772-1775`).
- ASUS exposes duplicate built-in mic devices (`14dac99`). A stereo stream can report both.
- Confirm: SRC status text or logcat shows "route was lost" or "did not become the actual recording route". `ui.live` drops while Ben is scoping.

**H5 · Stereo-first format churn / low-rate fallback.** *Low–medium.*
- Candidates try up to 8 stereo tuples (48k→44.1k→16k→8k × float/PCM16) before mono (`MicrophoneRoutePolicy.kt:31-40`). Each opens, starts, and waits up to 250 ms for route proof (`MicController.kt:64-91`). The mic can land on **stereo 16 or 8 kHz**, which is then upsampled by the 32-tap sinc (`CaptureMixPolicy.kt:112-128`). That gives a smooth, dull figure and a slow start.
- Confirm: Signal Check client/device format row (`MicController.kt:172`, `observeSignalRecorder` `:248-264`).

**H6 · Android input-policy silencing clears the queue.** *Low.*
- When `isClientSilenced` or system mic mute turns on, the mic queue is cleared and offers stop (`MicController.kt:163-175`, `:204`). Triggers: assistant hotword, camera, call.
- Confirm: status suffix "silenced by Android input policy".

**H7 · Reader-thread binder calls add jitter.** *Low.*
- Per 10 ms read: two `routedDevices` calls and `getTimestamp`. Every 250 ms: `activeRecordingConfiguration` + `isMicrophoneMute` (`MicController.kt:184-201`). A slow call feeds H2 (a) or (b).

**H8 · Vulkan validation in debug builds.** *Not causal; cheap fix.*
- `wgpu::Instance::default()` (`render.rs:1179`) uses build-config flags. Debug APKs build Rust without `--release` (`app/build.gradle.kts:337`), so validation is on and logs every frame. The render loop still held 119.4 fps. The S25 debug build had the same (`06f84e2:render.rs:890`).
- Fix: pass explicit `InstanceFlags` (off unless a dev switch is set). Test Ben's builds with validation off.

### 5.3 Zoom: why "measurement rates make no sense"

1. **Three different "×" numbers.** The stage shows the *effective gain* ×0.10–×256 with `·a` (`Console.kt:98`). The pinch ribbon shows *auto frame* ×0.250–×1.125 (`Gestures.kt:497`, `:539`). Settings shows *MANUAL GAIN* ×0.10–×7.00 (`Sheets.kt:1233-1237`), plus "LOCAL AUTO FRAMING ×%.3f" (`:1254`) and a summary with both (`:1577`). None of them tracks what the finger did.
2. **Asymmetric pinch range.** In AUTO, pinch multiplies `autoFrameScale` clamped to **0.25…1.125** (`AutoFramePreference.kt:7-8`, `:33-35`; `engine.rs:657-658`). From the default 1.0, "closer" stops after **+12.5%**, while "farther" goes to −75%. Pinch-open hits the wall almost at once.
3. **The pinch effect is swamped by AUTO.** Frame scale only sets fill = 0.8 × scale (`engine.rs:812`). The effective gain keeps moving with the signal (H1), so the image does not follow the fingers. The stage number changes when nobody touches the screen.
4. **1-finger vertical drag also zooms** with `exp(−dy·0.0042)` (`Gestures.kt:532-534`). This conflicts with pull gestures and surprises people.
5. **The graticule stops meaning scale.** Spacing = 0.1125 × gain, clamped at 0.55 (`render.rs:892`). Above ×4.9 (normal for quiet AUTO) the grid is pinned. Below that, it jumps with every AUTO snap.
6. **Leaving AUTO jumps the zoom.** Turning AUTO off resets gain to the manual value clamped at 0.1–7 (`engine.rs:740-753`), from e.g. ×63. The trace shrinks 9× instantly.
7. The neutral haptic fires when frame scale crosses 1.0 (`AutoFramePreference.kt:38`, `Gestures.kt:503`). That is invisible and unrelated to what is on screen.

Direction (for Phase 1): one user concept, "closer/farther". AUTO keeps sound in view with the S25-like slow glide and a lower ceiling. Pinch shifts the preferred fill over a symmetric range. The feedback is a brief non-numeric cue. All numbers live in the developer view.

---

## 6. Test inventory

Method: a test is **SOURCE-STRING/LAYOUT** when its body reads repository source (`readText`, `phase9Source`, `phase8Source`, `repoFile`, `include_str!("*.rs")`, named `.kt`/`.rs` paths) and asserts on text. The counts come from a regex, so borderline cases may shift by a few.

### 6.1 Totals

| Suite | Tests | Behavior | Source-string | Files |
|---|---|---|---|---|
| Android JUnit (`app/src/test`) | **948** (S25: 454) | ~832 | ~116 | 85 |
| Native (`rust/src`) | **134** (S25: 73) | 127 | 7 | 11 |
| Relay (`relay/src`) | 43 | 43 | 0 | – |

Of the ~832 Android behavior tests, 237 sit in `ui/` and pin UI policy for surfaces that the transpose will replace (console layout, settings adapters, tactile tokens, manual copy). They are real logic, but they belong to code that may be deleted. **Retire them with their code, not before.**

### 6.2 Retire candidates (source-string / layout)

| File | Source-string tests | Note |
|---|---|---|
| `ui/RotationDetentReachabilityTest.kt` | 11/11 | whole file |
| `ui/AutoFrameWiringTest.kt` | 4/4 | whole file |
| `HoldRenderBoundaryTest.kt` | 4/4 | whole file |
| `BrightnessRetirementTest.kt` | 3/3 | whole file |
| `AcceptanceTraceTest.kt` | 3/3 | guards debug/release split. Keep one as a build guard. |
| `settings/KnownDefaultsTest.kt` | 9/12 | e.g. `:130-131` asserts `"var focus by mutableFloatStateOf(0.3f)"` in source |
| `ui/ControlsVisibilityPolicyTest.kt` | 7/16 | e.g. `:37-50` greps PhosphorScreen for exact call text |
| `ui/AppearanceRuntimeWiringTest.kt` | 5/9 | |
| `BackgroundLifecyclePolicyTest.kt` | 6/21 | |
| `ui/AppearancePresentationTest.kt` | 5/17 | |
| `WakeOwnershipTest.kt` | 5/29 | |
| `PlaybackTruthTest.kt` | 4/22 | |
| `ui/ManualContentTest.kt` | 4/21 | the other 17 pin manual copy. Retire when the manual is rewritten. |
| `ui/SheetEntryPolicyTest.kt` | 4/19 | |
| `ui/SettingsGestureAdapterTest.kt` | 3/43 | the other 40 test the dismissal machine (C9). They go with it. |
| `ui/DoubleTapPolicyTest.kt` | 3/4 | |
| `RootCaptureProductTest.kt`, `settings/instrument/InstrumentActivityWiringTest.kt`, `ui/RoomStyleOverrideTest.kt`, `ui/StageGesturePolicyTest.kt`, `SignalObservationTest.kt`, `PictureInPicturePolicyTest.kt` | 3 each | |
| `ui/SliderGeometryTest.kt`, `ui/PauseDisplayPolicyTest.kt`, `ui/SettingsControlAccessTest.kt`, `CaptureMirrorPolicyTest.kt` | 2 each | `SliderGeometryTest.kt:95` defines shared `phase9Source` |
| `ui/ConsoleTactileTest.kt` | 1/8 (+ layout-width tests) | whole file goes with C18 |
| 9 more files | 1 each | |
| Rust | 7 | `engine.rs:1320`, `:1480`, `:1641`, `:1955`, `:2031`; `deck_events.rs:123`, `:385` (all `include_str!` greps of `render.rs`/`remote.rs`/`deck.rs`) |

### 6.3 Keep (behavior, real logic), examples

- Audio and ownership: `CaptureMixPolicyTest` (13), `ReaderStopTest` (16), `CaptureReadFenceTest` (4), `MicHandoffPolicyTest` (14), `LocalPlaybackPolicyTest` (22), `LocalOwnershipRegressionTest` (8), `RootEpochTest` (9), `StereoFirstInputTest` (6).
- Persistence and parsing: `settings/SettingsArchiveTest` (42), `LegacySettingsMigrationTest` (9), `LightArchiveTest` (7), all `settings/appearance/*` (89), `settings/instrument/*` except wiring (~79), `RemoteHostStoreTest` (15), `FolderTreeWalkerTest` (18), `DocumentPageTest` (6).
- Pure UI logic that survives any skin: `RotationDetentTest` (20), `StagePinchScaleTest` (6), `StageZoomPolicyTest` (5, rewrite with the zoom rework), `LightCycleGuardTest` (5), `LightSettingsTest` (7).
- Rust: `light_cycle.rs` (16), `spsc.rs` (15), `pause.rs` (10), `bridge_core.rs` (18), `instrument.rs` (10), most of `engine.rs` (39). The AutoGain tests in `engine.rs:2008+` pin the constants that H1 questions. Rewrite them with the controller.

### 6.4 Missing (per plan)

There are no tests of what Ben felt: nothing measures trace-size stability under steady input, collapse events, or gain slew. Proposed first device/host checks for Phase 1:

1. A host test that feeds recorded quiet-room mic PCM (from the test bed) through `AutoGain` and asserts bounded size variance and no collapse > 100 ms.
2. A mixer test that asserts zero null samples for a jittery-but-complete reader timeline.
3. A device UX script: 30 s mic capture, frame-diff trace extent, pass/fail on collapses.
