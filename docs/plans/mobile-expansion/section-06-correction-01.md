# Section 6 correction 01: coherent publication and slot identity

## Context and boundary

The immutable section-06-round-01 critique scores exact 7ac1 at 7/10. Its original report and process disclosure remain unchanged. This correction addresses its five finite findings. Root protocol work belongs to Retriever. No device, audio, protected partition, shared renderer or source-lifecycle change belongs to this correction.

## Required behavior

1. Background archive work only reads and decodes the bounded document. One Activity main-thread owner performs effective merge, guard, preference commit, native enqueue, rollback and UI restoration without another edit interleaving. A destroyed Activity cannot apply a late decoded result. Tests queue a decoded partial import after an intervening edit and prove omitted values use the current owner state.
2. A shared owner primitive rejects cross-thread or nested writes before mutation. Import and ordinary LIGHT edits use it. Rollback failure returns an explicit uncertain-persistence result. No message claims restoration without a successful rollback commit. Native queue acceptance remains distinct from a displayed frame.
3. Identical native settings publication is idempotent unless an explicit temporary roll is being retired. Timing-only and inactive-setting changes do not fabricate TRACK events. New timing applies at the next real leg boundary. Actual owner or selected-bank changes retain their activation semantics.
4. Deletion carries its exact removed positional index through the existing typed LIGHT action and one native command. It is transient operation metadata, never an archive or preference field. Native code validates the old bank with that exact index removed and the shifted mask before changing state. It remaps the previous selected identity before refill, including duplicate RGB slots. Ordinary full snapshot replacement does not guess deletion lineage from RGB equality.
5. A complete valid /2 light tuple can establish its own base and repair an invalid existing tuple. Partial imports still validate their effective omitted values and preserve failure without writes. No unrelated preference is reset.

## Checks and completion

Retain the two baseline native reproductions. Add production tests for identical TRACK/TIMER publication, timing-only TRACK edits, exact deletion before the current identity, duplicate RGB deletion, and invalid deletion preservation. Add archive complete-repair and invalid-partial tests. Add owner-thread, queued import/edit and rollback outcome tests using the production owner/transaction primitive. Update source-linked adapter assertions without dropping their behavior requirements.

Run private host checks while the root writer owns its files. After all writers release, freeze both source sets for the full Android/native/GPU/boundary gate and build both APKs together. Retain hashes, then request a separate bounded round-2 critique. ASUS runtime acceptance follows separately. A host pass is not Android callback, persistence-fault or pixel acceptance.

## Coordinator evidence at 09:12 UTC

The production native module passes 13 tests through Rust 1.96.0 `rustc --test`. The corrected Kotlin owner and archive paths pass 57 cached JVM tests. Nine source-linked KnownDefaults adapters pass separately. JNI syntax parsing and `git diff --check` pass. The first standalone Kotlin compilation omitted cached unchanged policy dependencies and failed before tests. The corrected private runner includes those dependencies, without running Android callbacks.

Evidence lives in `/home/ben/.jcode/scratch/color-correction-20260908T0909/`. The earlier two failing native baseline reproductions remain in `color-correction-20260908T0854/`. The exact deletion action carries its index outside portable settings and reaches `LightCycle.apply_edit`. Full Android compilation, independent correction review and device acceptance are pending. Retriever still owns root protocol files, so no full Gradle gate has run during its source window.

The offered ASUS is not yet enumerated by ADB or host USB. No S25 command or mutation followed Ben's sleep handoff. Its root work remains offline.
