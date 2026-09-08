# Section 7 correction01 design

## Context and current boundary

This correction design precedes runtime edits. Cactus is independently reviewing exact mobile `5b1be193e6fbcb0d3f779a8c0da5774a9a05efc6`. Its early source traces identify three integration gaps below. Its original report will remain immutable. This document is neither that report nor a claim that corrections already passed.

The coordinator will implement these changes only after the current R17 writer releases the affected source. Preserve R17 observation additions. Do not change native preset schema/admission, source behavior, audio, R13 history, color timing or portable archive formats to solve persistence ownership.

## F1: source selection bypasses failed-preset preservation

At the reviewed checkpoint, `startRemoteHost` settles a pending instrument request and then writes `gainValue` directly to preferences. A native preset can be active while its failed persistence has correctly restored the prior tuple. In that state, source selection writes one field of the failed preset, producing mixed saved tuning without RETRY SAVE CURRENT.

Required correction:

- Centralize the decision whether an automatic/source-boundary write may persist instrument keys.
- Preserve the complete prior instrument preference tuple while the workflow reports active-but-unsaved or uncertain state.
- Keep selecting/stopping sources available. Do not turn a persistence problem into a source lock.
- Preserve the existing ordinary local-gain save when no failed/uncertain instrument state exists.
- Inspect queued gain persistence and lifecycle writes for the same bypass. A previously scheduled callback must not escape the new failed-rollback latch.

Concrete check: inject preset B commitment, failed persistence and successful restoration of A. Select a saved relay through the adapter policy. All enumerated instrument preferences remain A. The source action remains available. A later explicit complete retry may save B, and normal source selection without failure still saves authored local gain as before.

## F2: asynchronous whole-settings import lacks an edit revision

The reviewed whole-settings archive path decodes on a provider thread, then posts acceptance to main. Main-thread serialization prevents simultaneous writes, but it does not prevent an earlier slow import from overwriting a later preset or manual edit.

Required correction:

- The authored tuning owner exposes a monotonic revision or an identity token for explicit tuning intent/publication.
- Starting whole-settings import settles prior native work before capturing its baseline token. This prevents ordinary picker lifecycle settlement from manufacturing a stale baseline.
- Carry a single operation identity and its authored baseline through picker result, background decode and main-thread acceptance.
- Before any preference or native mutation, verify current Activity/task ownership, operation identity and unchanged authored revision.
- A later manual edit, preset/undo intent or publication, successful settings restore, owner retirement or replacement invalidates the earlier acceptance. A return to equal values does not erase the fact that a later edit occurred.
- Reject stale acceptance with a concrete choose-again or explicitly refreshed preview action. Never silently merge stale imported tuning into newer author intent.
- Picking, cancellation and failed decode do not themselves mutate tuning. No main-thread provider read or blocking native await is introduced.

Concrete check: hold provider decoding of archive A, apply preset B or edit focus, then deliver A. The actual production acceptance owner rejects the old token before writes or native setters. Cover a queued native request settled before import begins, A-to-B-to-A edits, cancelled picker, stale duplicate result, destroyed owner and unchanged-baseline success. Existing inert instrument-document import remains unchanged.

## F3: typed rollback uncertainty stops at only one adapter

`persistInstrument` forwards `SettingsWriteOwner.Failure.restored`, but ordinary light and whole-settings writes reduce that failure to a string. Failed rollback can therefore leave later APPLY, UNDO, tuning and lifecycle saves enabled without a trustworthy saved tuple.

Required correction:

- Add one typed failure admission on the existing instrument/tuning owner and use it from all three settings-write adapters: preset persistence, ordinary whole-light commit and whole-settings archive commit.
- A failure with verified prior-value restoration remains an ordinary reported failure. Do not invent storage uncertainty when recovery is proven.
- An unconfirmed/failed rollback latches storage uncertainty and active-unsaved state. Block further authored tuning, preset apply/undo and whole-settings acceptance until explicit recovery.
- Keep source stop, permission dismissal, navigation and other safety controls available.
- Explicit RETRY SAVE CURRENT writes the complete known authored tuple through the serialized persistence owner without replaying native setters. Only verified success clears the latch.
- Cancel or suppress previously queued automatic instrument-key persistence. Lifecycle saves must preserve those keys while retaining unrelated settings behavior.
- Native receipt uncertainty remains distinct from storage uncertainty. A save cannot repair an unknown native outcome.

Concrete checks: run actual production owner and write-owner fixtures with in-memory mutation followed by failed commit and failed rollback, once through the light adapter and once through whole-settings acceptance. Verify tuning/apply/undo/import are blocked, automatic gain/lifecycle writes preserve instrument keys, source stop remains usable and explicit complete retry clears storage uncertainty without native calls. Successful rollback controls remain editable, and unknown native receipt stays blocked after a storage retry.

## Integration and acceptance

The original review also notes that the compact recall action is not distinguishable from the settings browser row. Add a small, labeled RECALL INSTRUMENT action inside LIGHT, separate from individual beam-color presets. It opens the existing instrument browser without applying tuning. Retain the full settings entry and explicit APPLY/UNDO actions. Verify both routes use the same Activity/browser owner and remain at least 48dp tall. This closes the stated navigation intent without creating a second preset engine or automatic recall.

At 11:30 UTC, Clover explicitly released only `settings/instrument/**` and their tests to the coordinator. Clover retains every R17/Activity/runtime path until its full release. The coordinator may implement and host-test the pure tuning owner in that disjoint window. No Android build or live Activity/UI correction runs during Clover's ownership.

Retain the original critique and exact initial gate624 JVM/121 native/3 GPU without rewriting their scores or provenance. Add production state/transaction tests, with Android source assertions only as supplementary adapter checks. After sole-writer release, run the complete frozen Kotlin/native/GPU/lint/dual-APK/engine/boundary gate and preserve exact before/after source hashes and artifacts.

Independent round2 should assess the full section against the original findings and unchanged requirements. Actual SharedPreferences durability, SAF provider lifecycle, JNI scheduling, source continuity and user interface acceptance still need the identified ASUS. It is currently unavailable, and S25 remains undisturbed. A passing host correction is not phone acceptance.
