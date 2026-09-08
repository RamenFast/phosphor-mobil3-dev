# Section 10 appearance runtime ownership

Written before runtime migration, archive admission or editor changes on2026-09-08 at14:06UTC. This refines section-10-appearance-contract.md using MainActivity, SettingsArchive, SettingsWriteOwner, InstrumentWorkflow, PhosphorScreen, Sheets and Console at10f10a5. The pure document writer owns its separate new files until release. No runtime acceptance is claimed here.

## Authoritative state and migration

One Activity-owned appearance state separates the committed AppearanceDocument from a transient preview. It never reads sampled ui.room colors into a document. ScopeUiState receives the effective authored AppearanceValue, its explicit RoomStyle and a monotonically changing authored revision. The existing measured beam tick remains the sole transient tint source. An appearance action calls no native tuning setter and preserves instrument association and source ownership.

The migration adapter uses AppearancePalette.legacy and the actual thirteen Rooms entries. It creates immutable legacy records from those production values, not copied color tables. Original nullable room and override values become exact provenance. Unknown room strings retain the original value and existing Blossom Dark resolution. A current resolved override value gets legacy:current when it differs from the corresponding built-in. No appearance-related legacy keys on a clean install selects curated AMOLED, while retaining all thirteen legacy records for explicit selection.

An existing valid appearance_state wins without migration or a write. An absent key permits one migration transaction. Invalid or out-of-domain legacy data leaves legacy rendering and original keys untouched. A corrupt appearance_state remains byte-preserved and is reported as recoverable. Only an explicit complete replacement may repair it. Do not silently install a default over corrupt bytes.

Migration commits the complete document before publishing it. Commit failure restores exact key absence or prior bytes. Failed rollback also enters shared settings uncertainty. Repeated startup does not add records or overwrite an existing valid state. Lifecycle save no longer derives legacy room/override fields from preview or custom appearance IDs. Legacy provenance keys remain inert compatibility inputs, not a second active theme owner.

## Portable settings integration

Add appearance_state only to the /2 whitelist. Validate its complete nested string through AppearanceDocumentCodec with the128KiB bound. Preserve the enclosing one-MiB archive cap and all existing inert value, checksum, unknown-key, omission and light migration rules. A /1 unknown appearance_state remains skipped under the existing newer-field behavior.

Merge has three explicit branches before any write:

1. A complete new appearance_state validates independently of existing appearance bytes and supplies the replacement. Explicit new state wins if legacy keys also occur, while those legacy keys retain their exact imported values.
2. Without new state, an explicit legacy room or override update resolves the merged legacy preference inputs through the actual migration bridge. Preserve existing user and immutable legacy records when the current document is valid. Replace only the active authored value, its legacy association and exact updated provenance. If existing appearance bytes are corrupt, require explicit complete repair instead of discarding records.
3. No new state and no legacy appearance keys preserves the exact existing appearance state without parsing or rewriting it. An unrelated tuning import cannot reset the look or destroy recoverable appearance bytes.

SettingsArchive owns string/schema validation. The production appearance merge adapter owns Palette-dependent legacy translation. MainActivity invokes both inside SettingsWriteOwner before taking key snapshots or constructing its editor. The one successful archive transaction publishes appearance on the same UI thread as the existing tuning restoration. Failure publishes neither a candidate theme nor a successful-import status.

## Preview, apply, save and recovery

All appearance operations use one UI-thread owner. Preview changes only effective presentation. APPLY persists the complete candidate document, then makes it committed. SAVE explicitly creates or updates a named user record and commits that complete document. Rename/delete preserve the active value rules of the collection. RESET explicitly commits curated AMOLED while preserving all saved records and provenance.

Leaving the editor, closing its group, backgrounding, entering PiP or retiring the Activity cancels preview and restores committed presentation. Preview does not enter lifecycle saves, whole-settings export or an instrument snapshot. Ordinary configuration handling may retain editor state only while the same valid owner remains. Recreated owners read committed state, never infer preview from visible Palette values.

A whole-settings picker carries both the existing instrument-authored ticket and an appearance revision captured before launch. Preview/apply/save/reset/rename/delete invalidate the appearance ticket. A delayed archive reply cannot overwrite a later appearance choice. This independent revision does not cancel native tuning or clear instrument association merely because the user edits a theme.

Reuse SettingsWriteOwner for commit and exact rollback. Report typed rollback failure to both appearance recovery and the existing shared instrument workflow. Recovery must first establish the complete authoritative appearance document as durable, then permit the existing complete instrument recovery. A failed appearance recovery cannot clear shared uncertainty through an unrelated successful instrument save. Recovery saves committed authored appearance, never an unaccepted preview. Corrupt state repair remains an explicit user action.

## Surface and motion

Keep the original legacy ROOM picker reachable until representative collapsed/expanded layout and device acceptance establish replacement. Add the four curated appearances and expandable editor within the existing Appearance group, using the same action hierarchy and settings dismissal owner. Route legacy room and override actions through the appearance owner instead of direct mutable ScopeUiState writes.

The editor exposes all authored roles and style fields without a second component tree. Use labeled RGB/ARGB inputs, exact values, named save/rename/delete, distinct PREVIEW/APPLY/CANCEL actions and a current-name/modified summary. All actions and range endpoints have48dp minimum targets, wrapping text, semantics and keyboard access. A stored low-contrast color remains exact. Report the failing role and offer an explicit readable correction. Any protective text/material presentation layer is labeled and stays separate from stored values.

PhosphorScreen resolves explicit authored RoomStyle rather than looking up style from custom IDs. Theme transitions key on authored revision, not beam tint. A new revision replaces the current finite transition from its last displayed palette. Reduced motion uses the existing static/brief policy. Appearance preview retirement cannot publish into another owner.

Remove the ROOM tile's decorative infinite breath. Burn-in motion requires actually visible chrome and stops under reduced motion, hidden presentation or PiP. Keep existing source/UI refresh ownership and add no appearance poller. Glass means translucent app chrome with a readable text surface, not claimed compositor blur or HUD transparency. HUD surface and native source behavior remain unchanged.

## Required observed checks

| Part | Smallest checks before the full gate |
| --- | --- |
| Migration | Actual production Rooms/AppearancePalette inputs, thirteen records, unknown ID, missing/sentinel overrides, current override, clean default, repeated migration and exact byte/absence restoration. |
| Archive | /1 and /2 fixtures, unrelated omission, explicit legacy patch, new-state precedence, complete repair over corrupt old bytes, malformed/oversized rejection and failed commit/rollback. |
| Owner | Preview/export/save separation, cancel/retirement, active deletion, reset preservation, stale provider reply after each authored action, shared uncertainty and failed/successful ordered recovery. |
| UI adapters | Actual Activity setter/transaction/provider wiring, explicit RoomStyle, authored-revision transition and unchanged source/instrument actions. Pure owner tests do not replace Android callbacks. |
| Motion | No ROOM idle clock, visible-only burn-in, reduced/hidden/PiP cancellation and finite rapid revision replacement. |
| Target | Exact reviewed dual APK freeze/readback, four collapsed/expanded looks, bright/black Glass readability, large fonts/focus/touch, source continuity and persisted recreation on authorized ASUS. |

The first deliverable is the integrated pure document and migration/archive seam. Full runtime/editor work follows its observed gate. A blocked target keeps the requirement open, rather than turning a host screenshot or compile into device acceptance.
