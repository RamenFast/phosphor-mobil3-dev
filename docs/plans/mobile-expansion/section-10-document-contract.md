# Section 10 appearance document and collection seam

Written before codec or collection implementation on2026-09-08. This refines the pending serialization seam in section-10-appearance-contract.md. Existing AppearanceValue and the lossless production Palette/RoomStyle bridge stay authoritative. This phase is pure Kotlin, without UI, preference, archive admission or runtime activation.

## Exact document

Schema is `phosphor.appearance/1`, integer version1. Maximum UTF-8 size is128KiB, including whitespace. The whole enclosing settings archive remains limited to1MiB. Validate the entire document before returning a replacement state.

The root has exactly `schema`, `version`, `active`, `active_id`, `users`, `legacy`, `provenance`, `content_sha256`.

- `active` is the complete authored AppearanceValue. It never contains transient beam tint.
- `active_id` is an empty string for an unsaved appearance, one curated ID, or the ID of an existing user/legacy record. An associated value may differ from the record, allowing a truthful modified indication.
- `users` contains0..32 records. Each has exactly `id`, `name`, `value`. IDs are canonical lowercase UUID strings. Names are1..64 Unicode code points, valid Unicode, trimmed and free of control characters. User names must be case-independent unique under the same locale-independent policy as instrument presets. Preserve spelling rather than silently normalizing names.
- `legacy` contains either no records before migration, all thirteen canonical legacy identities, or those thirteen plus `legacy:current`. Each record has `id`, `name`, `value`. These are immutable snapshots supplied by the actual production migration bridge, not duplicated palette tables in this module. Imported valid legacy snapshots preserve their authored bytes and values. They are not mutable user records.
- `provenance` is an object containing only present original legacy keys: `room`, `ov_char`, `ov_motion`, `ov_radius`, `ov_desig`. Missing stays absent, including distinction from explicit sentinel values. Room is a valid Unicode string of at most4096 code points. Override values retain exact signed32-bit integers. Empty object represents all keys absent. The codec does not interpret or clamp this provenance.
- `content_sha256` is64 lowercase hexadecimal characters, SHA256 of canonical UTF-8 for the complete root with only that checksum field omitted.

Curated IDs are `curated:light`, `curated:dark`, `curated:glass`, `curated:amoled`. Their immutable values come from CuratedAppearances. Reserved legacy IDs are `legacy:` followed by blossom, blossom_dark, light, dark, chromacore, basalt, afterglow, stonework95, amoled, paper, amber, fable, glass, plus optional current. Reject user attempts to use reserved identities and reject duplicate IDs in either collection.

`value` has exactly `colors`, `dark`, `accent_follows_beam`, `character`, `motion`, `duration_scale`, `density_scale`, `radius_dp`, `mono_prose`, `designators`, `panel_alpha_scale`. Colors has exactly `plane`, `surface`, `surface2`, `ink`, `ink2`, `muted`, `line`, `line_strong`, `accent`, `on_accent`, `stone`, `stone_hi`, `stone_lo`. RGB fields are integer0..16777215. Line fields are signed32-bit ARGB integers. All remaining enums, numeric domains and Boolean meanings are the existing AppearanceValue contract.

## Strict grammar and canonical form

Reuse StrictInstrumentJson read-only through its existing internal API. Do not fork or relax its grammar or edit instrument serialization. It already rejects duplicate keys, trailing material, malformed Unicode, invalid JSON numbers and excessive depth/nodes. This schema uses no JSON null, avoiding a new shared encoder behavior. Enforce appearance-specific byte limits and exact fields/types before constructing state.

Canonical object keys are lexicographically sorted. Preserve record-array order, Boolean spelling and signed integer values. Float spelling uses the existing finite Float canonical rule, preserving signed zero where legal. Decode checks decimal ranges before Float rounding. Do not round an out-of-range decimal into a legal boundary. Re-encode typed state for checksum validation. Wrap shared grammar errors into an appearance-specific error with code, message and fix, rather than presenting instrument instructions.

## Immutable collection behavior

The core exposes immutable records, active state and provenance. Defensive copies prevent caller mutation of lists/maps. Curated and legacy entries cannot be renamed, overwritten or deleted. User create/save/update/rename/delete require explicit caller actions and stable IDs. Duplicate names or IDs fail before change. Collection operations do not publish or persist automatically.

Applying an existing record replaces active value and association only. Applying a transient edit keeps it unsaved or modified according to the explicit caller association. Saving records a named complete authored value, without activating an unrelated record. Deleting the active user record clears its association but preserves its exact active value. Reset activates curated AMOLED and preserves every saved record and provenance. Failed validation leaves the original state unchanged.

No collection operation grants permissions, activates a source, invokes native tuning, changes instrument association, previews a theme or writes storage. The later serialized appearance owner is responsible for transactional persistence, reversible previews and stale import tickets. New complete-state decoding must not consult existing potentially corrupt bytes, allowing explicit whole-state repair.

## Required checks and boundary

Use actual production Kotlin sources and cached compiler dependencies. Test all fields and enums, RGB/ARGB extrema, exact Float bounds, signed zero, malformed/duplicate/unknown/type/nonfinite/Unicode input, byte/depth/node limits, checksum mutation and independent fixed digest fixtures. Cover all reserved identities, user32/name64 bounds, mutable input isolation, name/ID conflicts, active delete, reset preservation, strict round trips with full legacy provenance, and whole replacement independent of corrupt old bytes. Capture exact source/dependency/runner/fixture hashes.

The result is a pure-core handoff. Android, real preferences, settings archive admission, Palette/RoomStyle migration wiring, editor/preview, lifecycle and device appearance acceptance remain coordinator work. A successful codec suite must not be described as a shipped theme feature.
