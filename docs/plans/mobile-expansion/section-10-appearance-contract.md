# Appearance and motion contract

## Source and outcome

This prepares approved section10, R04 and R12, before runtime changes. Canonical `MOBILE-EXPANSION-PLAN.md` remains authoritative. Source inspection uses mobile `6355074c1d20158c29e749da9fd4c50e0d2bd2a7` and unchanged shared `0ffd658d7f19e68180c2720e0500b23644619e90`. Section9 owns its separate settings adapter and grouping implementation.

Ben can choose Light, Dark, Glass or AMOLED, then edit and save that appearance without changing the instrument, audio source or permissions. These are four entry points into one palette/material model, not four component trees. Appearance presets remain separate from instrument presets. AMOLED remains the clean-install default. Existing saved appearances do not change during migration.

### Inspected seams

- `ui/Palette.kt` has thirteen palette IDs and fourteen color roles including transient `beamAccent`. `withBeam` changes transient accent values. Never save those sampled values as authored appearance colors.
- `ui/RoomStyle.kt` resolves palette identity to style, then applies `StyleOverride`. FEEL replaces the coupled base style. Motion, radius and designators override that result individually. Flatten only after this exact resolution.
- `MainActivity.restoreTuning` reads `room`, `ov_char`, `ov_motion`, `ov_radius`, `ov_desig`. Missing room chooses AMOLED. Unknown room IDs currently display Blossom Dark. `setRoom` changes `baseRoom` and displayed room, while `saveTuning` later writes the legacy keys.
- `SettingsArchive` validates all five legacy keys in both existing archive readers. Unknown syntactically valid room IDs can occur. Preserve their identifier and current fallback explicitly rather than silently relabeling them.
- `PhosphorScreen` crossfades on palette ID changes. Custom editing under the same identity needs a revision separate from measured beam tint. Its early hidden/PiP exits already remove the full UI composition.
- `RoomSheet` owns an unconditional infinite tile pulse. `Console.burnInWalk` checks reduced motion but not component visibility itself. New motion work must inspect actual caller visibility, not infer visibility from retained composition or alpha.

## One authored appearance

Use an immutable, host-testable value independent of Compose colors. It carries explicit sRGB colors and resolved style. Adapt it into the existing `Palette` and `RoomStyle` at one UI boundary. Keep existing component roles and audible/native controls unchanged.

| Field | Domain and meaning |
| --- | --- |
| `plane`, `surface`, `surface2` | Opaque RGB24 authored background colors. Panel translucency is a separate material value. |
| `ink`, `ink2`, `muted` | Opaque RGB24 normal, secondary and contextual text colors. |
| `line`, `line_strong` | ARGB32 structural separator colors, preserving legacy alpha exactly. Essential actionable outlines receive a readable resolved role. |
| `accent`, `on_accent` | Opaque RGB24 editable/selected control color and readable foreground. |
| `stone`, `stone_hi`, `stone_lo` | Opaque RGB24 existing dimensional control roles. |
| `dark` | Explicit Boolean retained from the source palette. |
| `accent_follows_beam` | Boolean. Uses the existing transient measured beam tint path, never writes samples into the appearance. |
| `character` | `CARVED`, `ENGRAVED`, `ANNOTATED`, `GLASS`, mapped to the existing enum without changing meaning. |
| `motion` | `EASED`, `CUT`, `DETENTED`, `SPRINGY`. Legacy values stay lossless. Curated new Glass does not introduce a decorative bounce. |
| `duration_scale` | Finite 0.25–2.0, preserving current0.5/1.0. Reduced motion still wins. |
| `density_scale` | Finite0.85–1.25. Scale spacing, not minimum48dp touch targets or accessible type. |
| `radius_dp` | Integer0–64, preserving accepted legacy archives. New curated shapes use zero. |
| `mono_prose`, `designators` | Booleans preserving resolved legacy style. |
| `panel_alpha_scale` | Finite0.2–1.0. Used only for app chrome, not HUD window transparency or source surfaces. |

The authored model excludes live beam color, source identity, volume, gain, HDR request or health, HUD permission, display images and device data. Reset replaces appearance only. It must not call a native tuning setter, stop a source or clear an instrument preset association.

## Four curated design contracts

All presets use the same semantic mapping: beam means observed signal, accent means editable/selected control, quiet ink means context, disabled shape plus text means unavailable, and a labeled error means failure. Color alone never conveys availability. Live accent must remain readable, especially over light surfaces.

| Entry point | Visible language | Collapsed settings example | Expanded example |
| --- | --- | --- | --- |
| Light | Warm paper plane and surface, dark ink, crisp flat separators, compact mono parameter labels. Active accent is darker than the paper. Press depth belongs to actions only. | Full-width `Display & HUD` row with dark heading, readable current values and one chevron. No filled status capsule. | Distinct toggles and sliders on a warm unboxed body, at least48dp targets. Beam stays its existing signal color. |
| Dark | Charcoal plane with restrained surface separation. Eased80–200ms state transitions. Selected controls use accent, not a glowing background. | The same row, quiet context and clear control edges rather than decoration. | Same order and controls. A short reveal explains expansion, then stops. |
| Glass | Translucent instrument-cover material over the current app surface, sharp edges, readable text backplates. No permission or actual blur promise. | Values remain legible over a bright moving trace and black. Material transparency is visible away from text. | Text and essential controls use an opaque readability layer when needed. Show the material fallback, do not claim unsupported compositor blur. |
| AMOLED | True-black plane and primary surface, sparse unfilled controls and limited accent. No background glow or raised grey slabs by default. Cut or brief state transitions. | The same row in black, with only meaningful ink and a hairline boundary. | Identical control hierarchy. Focused or pressed state stays visible without a permanent filled panel. |

Before replacing the room picker, retain representative collapsed/expanded layout evidence for all four. Host contrast checks are necessary, not enough to prove device readability or dynamic Glass composition. Preserve existing section9 opening swipes and deliberate dismissal semantics.

## Legacy migration and collection

The thirteen IDs are `blossom`, `blossom_dark`, `light`, `dark`, `chromacore`, `basalt`, `afterglow`, `stonework95`, `amoled`, `paper`, `amber`, `fable`, `glass`. Snapshot every authored color and resolved style from the pinned source. Do not substitute a nearby curated preset, improve contrast silently, or reset explicit overrides during migration.

1. Without a new appearance state, resolve existing `room` and all four overrides through the actual old production functions.
2. Preserve all thirteen built-in looks as named legacy appearances. Preserve the current resolved override combination as a separate current legacy appearance when needed.
3. Retain original room ID and raw override values as migration provenance. Unknown IDs keep their exact string and record that the existing display fallback is Blossom Dark.
4. Install the migrated state transactionally. A failed write leaves original keys and displayed appearance unchanged. Repeated migration does not create duplicate records.
5. Keep old archive readers and legacy keys working. A new appearance state, when present and valid, owns rendering. Lifecycle saves must not overwrite it with transient `ui.room` tint or stale legacy mirror keys.
6. An old archive that explicitly changes legacy room/override keys creates the corresponding appearance through the same migration function. Omitted appearance fields preserve the current state. Unrelated tuning imports do not reset it.
7. A complete new appearance payload can repair corrupt appearance state. Validate that complete replacement before trying to parse invalid existing state. Invalid bytes remain recoverable until explicit repair succeeds.

Use stable record IDs and immutable collection edits. Curated and legacy identities are reserved. User save, rename, apply and delete have explicit results. Applying does not implicitly save an edit. Deleting an active user record keeps its displayed values as an unsaved appearance until an explicit apply/reset, rather than switching the look unexpectedly. Reset restores curated AMOLED without touching saved user records or other settings.

### Serialization seam to finalize before implementation

Use a bounded strict appearance document carried by a new whitelisted `appearance_state` string in the existing `phosphor.settings/2` payload. It uses its own `phosphor.appearance/1` schema with active authored value, stable records and legacy provenance. Reuse the existing strict JSON grammar and canonical checksum patterns where appropriate, not an Android-only unvalidated JSON blob. Preserve the whole archive's one-MiB limit.

The implementation contract must settle exact document field names, ID/name/record limits, canonical number spelling, duplicate/conflict handling and checksum test vectors before writing the codec. Expected bounds are at most32 user records, all thirteen reserved legacy appearances, names at most64 code points, and encoded appearance state at most128KiB. A complete document must validate all fields before one commit. Do not weaken old unknown-key, type, omission or rollback behavior to admit the new state. No standalone appearance file picker is required by this section.

## Editor, persistence and recovery

Place the expandable editor under Appearance, with live local preview and separate APPLY/SAVE. Expose background, surface, text and accent color roles, beam-following accent, opacity, density and motion. Keep advanced legacy style fields available without adding a second independent theme engine. Show current name and modified state.

Preview is transient and reversible. It cannot contaminate lifecycle saves or an instrument snapshot. APPLY publishes the chosen appearance through one serialized UI owner. SAVE records a named appearance. A failed persistence transaction either restores the exact previous bytes or enters the shared explicit storage-uncertainty recovery path. Do not turn a typed rollback failure into only a toast. Pending picker/import callbacks use authored revision tickets and cannot overwrite newer preview/apply choices. No source is restarted for an appearance change.

Custom low-contrast values remain stored exactly as authored. Show the failed role/contrast and offer a readable correction as an explicit action. Unavailable-control styling and text remain legible through a separately labeled safe presentation layer if required. Do not silently change saved colors or pretend a dynamic beam accent always passes contrast.

## Motion ownership

Animate state, not idle decoration. Chevron rotation represents expanded state. Progress rotation requires actual bounded work. A signal glyph requires a current signal observation. Stop any infinite transition or polling when its component, chrome, sheet, surface or Activity is not visible. Reduced motion removes infinite clocks and makes state changes static/brief.

Keep existing measured source/UI ticks rather than adding a theme poller. A same-appearance beam tint update does not restart a full theme transition. An authored appearance revision can trigger one cancellable transition from the currently displayed palette. A second revision replaces that transition, and retirement cannot publish to the next sheet.

## Requirement-linked validation

| Area | Required checks |
| --- | --- |
| Four entry points | Real collapsed/expanded layouts, all six section headers, identical action map, contrast over representative surfaces, no unsupported capability claims. |
| Model and migration | All thirteen exact palettes, every legacy override domain and coupled-style resolution, unknown IDs, missing keys, repeated migration, clean AMOLED and current-user preservation. |
| Archive and CRUD | Old/new round trips, omission, explicit legacy updates, complete repair, malformed/oversized/duplicate input, failed commit and failed rollback, stale import/edit scheduling, active delete and reset isolation. |
| Preview/apply | No authored save before APPLY/SAVE, cancel/rotation/close recovery, source and instrument association unchanged, transient beam colors excluded. |
| Accessibility | Contrast4.5:1 normal text and3:1 large text/essential boundaries, moving Glass extremes,48dp targets, large fonts, TalkBack/keyboard focus and multi-window/HUD dimensions. |
| Motion | Visible versus hidden clocks, no idle busy glyph, reduced motion, rapid appearance changes, Activity/PiP/HUD transitions, no new audio reads or polling loop. |

Passing host models or a render snapshot does not accept Android persistence, Compose lifecycle, accessibility, compositor Glass or the complete section. If exact legacy equivalence cannot be established, keep the original picker reachable and report the precise blocker instead of silently retiring its entries.

## Exact legacy translation seam before implementation,13:21UTC

`LegacyAppearanceInput` retains the nullable original room string and raw nullable override integers.
Missing values stay distinct from explicit legacy sentinel values. It carries no device or runtime data.
The translation resolves `paletteById(room ?: "amoled")`, then the existing production `style.overridden` function.
Character/motion use the same ordinal getOrNull rule as MainActivity. Designators1/0 mean true/false, all other values follow the base.
A nonnegative radius is preserved exactly when within the authored model's0..64 domain.
An out-of-domain legacy radius fails translation rather than silently clamping or writing a changed appearance.
That failure must leave existing preferences and visible legacy rendering untouched when the later migration owner is connected.

`AppearancePalette` converts authored values into the existing Palette and RoomStyle roles.
It does not infer style from a new appearance ID. The caller must provide its explicit resolved RoomStyle at the future root boundary.
Legacy resolution only reads canonical palette rows, never a sampled `ui.room` or `withBeam` value.
ARGB separator alpha is preserved. Transient beamAccent remains unspecified until the existing live tint path supplies it.
No current root provider, picker, preference key or visible palette changes in this translation checkpoint.

Verification compares all thirteen actual production palette rows and their exact resolved styles through round trips.
Enumerate every supported character/motion/labels combination and all radius values, plus sentinel, absent and unknown identities.
Use the original production resolver as the oracle, not a copied palette/style table.
Later codec, collection migration, UI and real-device checks remain required for full section10 acceptance.

### Legacy bridge gate observed13:24UTC

Gate768672faau passed722 JVM tests across62 suites,126 native tests,three offscreen GPU tests, lint, engine/source checks and both APK builds.
The six actual AppearancePalette tests passed, including64,350 combinations against the original production resolver.
All thirteen actual palettes round-trip with exact authored colors, separator alpha and coupled styles.
Unknown IDs and missing/sentinel provenance remain distinct. Sampled beam tint never enters migration values.
Complete source manifests were unchanged during the gate. This adds a compiled translation boundary, not a running migration or editor.

Evidence prefix: `dev/scratch/mobile-expansion-20260908T001819Z/appearance-legacy-integration-1323`.
Runner SHA256: `7b7ac2e0fe103c87cf63d805132d09e6554865f8b7138ec6fc1b2dba370a4fbb`.
Mobile manifest: `d6527a235e3ffbd54b4505f868bbd20a19b6835174465f34c6e057484b9be67b`.
App APK: `f4817a4ba82602f429e58e77034b27a11d6fbf42cd06c2f53ee9f04f55208043`.
androidTest APK: `a398effaa725bbbf36bc73f839f91a5d53df2a7c9d0ac928e97a36357d484af6`.
JVM XML archive: `d33789afd0abe02031385d225e932712f91a54ab9aa36e96ca9a73812d8180cb`.
Both artifacts remain uninstalled. Existing runtime Palette/RoomStyle definitions, root provider and preference writes are unchanged.
