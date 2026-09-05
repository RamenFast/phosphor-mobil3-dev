# Phosphor Mobile: the instrument we can trust

**A comprehensive, contained roadmap for Ben. Written 2026-09-05. Planning only, not implemented.**

The goal is simple: sound moves, the beam answers, and neither the controls nor a lifecycle race lies about what is happening.
We keep Phosphor's existing Android/Rust instrument. We make failures easier to catch before attempting broad structural change.

## The chosen direction

**Do not split the application first. Make the evidence trustworthy, then simplify only the ownership boundaries that tests expose.**
This is the non-obvious choice from three isolated candidates and a separate critique.
It favors reliable source truth, real beam/audio checks, settings survival and accessible controls over a framework rewrite.

| Order | Phase | Concrete outcome |
|---|---|---|
| 00 | [Accepted baseline](phases/00-baseline.md) | Finish inherited B1-B21 behavior acceptance and recompile this plan against exact source |
| 01 | [Trustworthy test boundary](phases/01-test-boundary.md) | Stale smoke receipts fail, fake APKs stay isolated, real Android tests have a safe entry point |
| 02 | [Source face](phases/02-source-face.md) | One pure face contract rejects stale callbacks without making mic service-owned |
| 03 | [Reader health](phases/03-reader-health.md), then [native boundaries](phases/03-native-boundaries.md) | Terminal errors, decoder outcomes, provider cancellation and remote completion stay truthful |
| 04 | [Instrument evidence](phases/04-instrument-evidence.md) | Fixed samples distinguish channel, beam, output and Android integration defects |
| 05 | [Settings survival](phases/05-settings-survival.md) | Accepted edits survive updates, recreation and failed imports |
| 06 | [Accessible controls](phases/06-controls.md) | Controls expose meaning, focus and immediate feedback without changing the beam |
| 07 | [Lifetime and performance](phases/07-lifetime-performance.md) | Real resource and presentation measurements guide bounded follow-up work |
| 08 | [Exact delivery](phases/08-delivery.md) | Source rebuilds, public exports are safe, and acceptance follows exact retained artifacts |

The nine numbered phases contain 21 tasks with exact file owners, proposed tests, expected results and rollback.
All runtime phases are sequential. No calendar estimate pretends to know future device availability or repair duration.

## Start here with no conversation history

1. Read [vision and boundaries](context/VISION-AND-BOUNDARIES.md).
2. Read [baseline evidence](context/BASELINE.md) and [the exact current repair resume point](context/CURRENT-REPAIRS.md).
3. Read [decisions and rejected traps](DECISIONS.md), then [risks and external gates](RISKS.md).
4. Follow [EXECUTION](EXECUTION.md), the relevant phase, and [requirement-to-check coverage](verification/MATRIX.md).
5. Use [evidence rules](verification/EVIDENCE.md), [guarded device procedure](verification/DEVICE.md) and [source inventory](context/SOURCE-INVENTORY.md).
6. Read [the planning validation receipt](verification/PLAN-REVIEW.md) before treating the document as a checked handoff.

This folder contains the roadmap context, choices, phase procedures, verification and recovery rules.
It requires the named repositories and canonical specs, not this chat, an old worker session or ignored candidate files.
Those specs remain product authority. This plan is a compiled execution guide, not a competing specification.

## What is done now, and what waits

- B2 is committed, independently reviewed and built. Its 177 Android unit tests pass. The exact APK is retained, not installed.
- B4 required live acceptance passed. Its final regression remains open, as does the overall B1-B21 sequence.
- Ben's quiet-work boundary remains active. No phone, visible window, audio change or active-relay action follows from this roadmap.
- New roadmap implementation requires later authorization and accepted inherited Phase 18 behavior closure. Publication/signing remain separate.

**Not proposed:** a new renderer, framework migration, account/cloud service, runtime agent API, background mic product or guessed core feature.
The two future core features Ben has not briefed remain unbriefed.
