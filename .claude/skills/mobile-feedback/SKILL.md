---
name: mobile-feedback
description: Maintain docs/FEEDBACK.md — Ben's live-feedback ledger for phosphor-mobil3. Read BEFORE acting on any new feedback from Ben (live message, Nexus batch, or handoff): check if the item is a REPEAT (same row exists → the previous fix missed or its receipt never reached him — treat as priority + investigate why), then update the ledger in the same pass as the fix. One row per unique item, ×N repeat counter, status open/shipped/verify/retracted.
---

# The mobile feedback ledger

Ben feeds this project live, in batches, mid-session. The ledger
(`docs/FEEDBACK.md`) is how sessions notice **repetition** — Ben asked for this
explicitly ("track if I'm repeating a feature/bug feedback").

## The loop
1. New feedback arrives (live message, NEXUS-FEEDBACK.md sync, HANDOFF queue).
2. **Scan the ledger first.** Same item already there?
   - status `shipped`/`verify` → it's a REPEAT: bump ×N, and before building
     anything, find out why the fix didn't land for him (wrong root cause? ops
     not code? receipt never shown? two-switch confusion?). The art saga
     (3 reports, root cause was an un-restarted relay service) is the canonical
     example — the code was innocent twice.
   - status `open` → it's confirmation; raise priority.
3. Fix/build, then update the row (status, resolution, receipt pointer) in the
   SAME pass — never leave the ledger for "later".
4. `verify` means shipped but Ben's eyes/ears haven't confirmed. Only Ben's
   word moves verify → shipped. His retractions get status `retracted`, kept
   as rows (they carry design information).

## Laws
- Distill his words, don't paraphrase away the feel ("looks good though~!" is
  data). Date = first report. Never delete rows.
- Repeats are never annoyances — they are the highest-signal rows in the file.
- ASKS.md tracks asks per the estate law; FEEDBACK.md tracks the feedback
  LOOP (did the fix actually reach Ben?). Cross-reference, don't duplicate.
