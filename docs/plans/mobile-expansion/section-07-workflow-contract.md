# Section 7 Activity workflow contract

## Context

R16 adds an Activity-owned instrument browser over the frozen typed core and native receipt API. This contract precedes runtime edits. Existing section-07 instrument and native contracts remain authoritative.

## Outcome and boundaries

The browser contains curated starting points and a separate saved collection. Browse, name, duplicate, update, delete, preview, import and export do not apply tuning. APPLY and one-level UNDO each submit one metadata-free immutable setup. Source selection, audio, HOLD pixels, inspection, permissions and startup are outside the setup.

The Activity captures manual gain from `gainValue`, local auto-gain from `ui.localAutoGain`, and focus from `ui.focus`. The remaining typed fields come from their authored UI authorities. Measured gain, temporary light rolls and remote mirrors are not saved as authored values.

## Main-thread ordering

A pure Kotlin workflow owner retains the exact positive native receipt before scheduling its wait. Its production adapter waits on a worker thread and posts the result to the Activity main handler. No main-thread path calls await. Each terminal receipt is released once.

Before any later authored edit, settings import, track-driven random setter, source selection or lifecycle retirement, the owner synchronously cancels its pending receipt. Native cancel returns the actual terminal result. If admission already committed, the owner publishes and persists that exact immutable setup before the later edit runs. If cancelled, queued native work remains inert. A delayed callback for a retired receipt does nothing. Nested manual calls share the outer ordered edit, rather than recursively entering `SettingsWriteOwner`.

The heartbeat does not read authored local auto-gain from native observations. Measured gain publication pauses while a preset is pending. Source stop and safety controls remain enabled. On stop, settle pending work before saving. On destroy, settle and release owned work before retiring the Activity. No stale callback publishes after retirement.

## Apply, guard and undo

Geometry-owned remote rendering refuses complete local apply without changing source. Native admission checks this again. A rapid candidate uses the existing whole-light guard. Safe timing is applied first and the original remains an explicit review candidate. The sheet offers KEEP SAFE or the existing acknowledgement followed by a new explicit apply. Guard-clamped tuning is labeled modified, never exact recall.

A committed native result updates all enumerated authored controls together. Persistence writes only the corresponding tuning keys. It never replays individual native setters. One prior setup becomes the undo slot. Undo follows the same admission and guard path and consumes its slot after commitment. A later apply replaces that slot. Manual edits mark an association modified. Deleting its saved record clears association only.

Persistence failure after native commitment is reported as active but not saved. Exact prior preference values, including absence, are restored. A rollback failure is reported as uncertain storage, not successful recovery. Automatic lifecycle saves do not silently persist a failed preset. The retained prior setup allows explicit undo. Renderer rejection leaves authored UI and preferences unchanged.

The workflow carries typed persistence failure with a `restored` flag. Failed rollback blocks tuning, APPLY, UNDO and whole-settings import. Source stop remains available. RETRY SAVE CURRENT is explicit recovery: it persists the known active authored tuple without submitting native work. Only a successful complete commit clears storage uncertainty. A missing native receipt instead blocks tuning until restart, since persistence cannot establish the unknown native outcome. Lifecycle autosave preserves instrument keys while still saving unrelated settings and runtime metadata.

## Collection and SAF

The file is `phosphor.instrument.presets`, with one `collection` value. Only an absent value means an empty collection. Invalid bytes disable collection writes and remain preserved. Each write checks its exact base bytes, writes one encoded candidate and publishes only after commit success. A false or throwing commit restores exact prior bytes and reports rollback failure separately.

Document reads stop at the core 1 MiB bound and decode strict UTF-8 through the production codec. The Activity retains a generation for each document operation. Cancel and destruction invalidate late replies. Preview is inert. Every incoming record requires an explicit add, skip, identified replacement or named copy choice. The complete resolved collection is validated before one write. Import never applies or acknowledges rapid timing. Export snapshots all saved records or one selected stable ID before opening the document. Cancellation reports no export. Failed output warns that the selected document may be incomplete.

An identity-owned document ticket retains its slot until its picker or provider callback returns, even after cancellation. This prevents repeated cancellation from creating unbounded blocked provider threads. A provider may not support immediate I/O cancellation. The interface states that it is waiting and that an already-writing export may leave a document. No cancelled or retired ticket publishes preview or success.

Audio-only relay renders locally. Its gain-mode display follows authored local auto-gain. Only geometry-owned relay displays the peer gain policy. Source transitions preserve a known active unsaved gain tuple rather than replacing it with uncertain saved preferences.

## Surface

INSTRUMENT PRESETS appears adjacent to LIGHT in settings, with a compact recall entry. A scrollable square-corner sheet shows current association, pending/error/durability state, HOLD readiness, curated and saved entries, named save, update, rename, duplicate, confirmed delete, import preview and export. Actions have at least 48dp targets. Source controls remain reachable from an apply refusal. Names are validated by the existing Unicode-aware core, not a second validator.

Example: apply Ambient A, then drag focus to 1.2 before its wait reply. Cancel reports committed A. The owner first publishes A, then the drag sets focus 1.2. The delayed A reply cannot replace that later edit. Ambient is shown as modified.

## Observable checks and valid failure

Actual-source pure Kotlin tests exercise delayed admission, queued cancellation, lost committed reply, stale callback, source supersession, destroy, nested manual/import edits, undo and guard differences. Storage tests simulate Android's in-memory mutation on commit false, exact absent/string recovery, rollback failure, stale base and corrupt bytes. Core codec/collection tests remain unchanged and are reused.

Android compilation, JNI execution, real SharedPreferences durability, SAF providers, HOLD pixels and accessibility require root-owned checks. This worker does not run those targets. A valid blocked result names the exact unresolved seam, retains useful source and host evidence, and releases all ownership by 11:07 UTC. No hardware acceptance is inferred from host tests.

## Requirement-linked host checks

| Requirement | Actual production seam exercised |
| --- | --- |
| Delayed apply, committed cancel before manual B, stale reply A | `InstrumentWorkflowTest` records exact request/admit/cancel/publish/persist/release/manual order |
| Cancelled queue, changed source, rejected geometry, Activity retirement | Production workflow owner runs against a controlled native receipt adapter, including destroyed and replacement owner traces |
| Nested manual/import/light ownership | Production `InstrumentWorkflow` and `SettingsWriteOwner` tests exercise sequential nested edits and rejected reentry inside an active synchronous commit |
| Failed commit mutates in-memory preferences, rollback failure, explicit recovery | Production store and write owner restore exact bytes or absence. Typed workflow failure blocks later tuning until explicit save succeeds |
| Undo, association, guard-safe not exact recall | Production owner checks one slot, modified tuples, rename/delete association and explicit rapid acknowledgement |
| Inert complete import, stale preview and byte bounds | Production collection resolver, store and stream reader reject unresolved/stale/oversize inputs without writes |
| Cancelled picker/provider and stale callback | Production document ticket owner retains one slot and denies stale publication after cancel or close |
| Android-facing adapter boundaries | Supplementary source checks verify local snapshot fields, setter-free publication, source retirement, sheet actions and off-main await placement. These do not prove Android behavior |
