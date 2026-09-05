# Current repair work: resume without conversation history

Canonical execution plan: `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/PRE-V2-B1-B21-EXECUTION-PLAN.md`.
That file contains the complete existing task sequence, source owners, commands, rollback steps and issue mapping.
This digest identifies the exact resume point and prevents a later executor from treating the new roadmap as repair acceptance.

## Immediate resume point

Updated 2026-09-05 after the original-report audit: B2 task 4.4 passed five exact-artifact phone cycles in receipt commit `2a3512e`.
The tested debug APK remains installed. B2, B4 and B9 retain final-regression requirements.
The next implementation phase is inherited Phase 5, covering B6/B7/B8 metadata, captured seeking and play/pause truth.
Follow the latest checkpoint in the [canonical execution plan](../../../../PRE-V2-B1-B21-EXECUTION-PLAN.md).
The [B2 live receipt](../../../dev/receipts/pre-v2-b1-b21/phase-04-b2-mic.md#live-acceptance-2026-09-05-1633-through-1638-utc) records the exact artifact, observations and limits.
The completed phone-test window does not authorize further device activity or future roadmap implementation.

## Historical resume point at roadmap compilation

The following instructions and inventory preserve the earlier planning baseline. They are not the current next action.

B2 source `47ff7cd` is independently reviewed, committed and built. Its exact debug APK is retained, not installed.
The next unfinished action is existing Phase 4 task 4.4: installation and five real capture-to-mic cycles.
Ben's quiet-work boundary currently blocks that action. It also blocks unrelated source-switching or relay tests.
No additional clarification is needed to continue offline planning.

When Ben permits device work again:

1. Recheck the live repository, active repair receipt and selected device identity.
2. If B2 is still the active phase and no later implementation superseded it, use the retained `47ff7cd` APK from BASELINE.md.
3. Preserve settings and the known-good Phase 3 debug APK before installing.
4. Check the B2 hash, package, debug flag, version and signer with the offline guard in [DEVICE.md](../verification/DEVICE.md).
   Use BASELINE.md's pinned values. Then follow the permitted-window install/readback procedure for the retained B2 APK, without an instrumentation APK.
5. Follow the B2 receipt's five-cycle matrix, not an old screenshot or fixed tap coordinates.
6. Capture finite all-PID logs, one active recorder, sample progression, beam motion and capture-stop-before-mic ordering.
7. Observe safe newer-source replacement, capture-face transitions and service-error retry. Do not force hardware failure.
8. Restore test settings and keep failed attempts. Leave B2 open if any required observation is missing.

The existing debug-only source command boundary is not a substitute for Android consent.
The new roadmap does not authorize changing keyguard, media volume, notification access or app permissions during Ben's quiet period.

## B-card inventory

| Cards | Human outcome | Existing code phase | State at this plan's baseline |
|---|---|---:|---|
| B9 | Seeking/navigation does not freeze local playback | 2 | Phase live check passed; final regression open |
| B4 | Recursive local folders play and draw honestly, invalid entries recover | 3 | Required live check passed; final regression and documented provider limit remain |
| B2 | Capture switches to a working built-in microphone | 4 | Offline delivery complete; exact-candidate phone check pending |
| B6, B7, B8 | Local/captured metadata, seek capability and play/pause truth | 5 | Open |
| B21 | Task removal stops sources by default; service-owned linger is optional | 6 | Open |
| B3 | Regular-app Tailscale relay, fresh Linux, direct and recursive-folder playback | 7 | Open |
| B1, B19, B18 | Exact 333 ms settle rule, bottom-edge protection and double-tap toggle | 8 | Open |
| B14, B13, B12, B10 | Usable sliders, replace DECK correctly, PiP control and live style previews | 9 | Open |
| B5, B11 | Stable deposited brightness and live-source screen ownership | 10 | Open |
| B15, B16, B20 | Real stereo grid data, Xy45 grid parity and stable auto-gain | 11 | Open |
| B17 | Accepted defaults and settings survival across versions | 12 | Open |

## Remaining inherited phases

- Phase 5 fixes metadata and captured transport without creating another native event consumer.
- Phase 6 implements task-swipe behavior and player release. It does not promise background mic survival.
- Phase 7 extends the existing remote Play/FileSession path. Relay deployment requires a quiet, client-free service window.
- Phases 8 through 12 deliver interaction, display, stereo truth and settings contracts in their fixed order.
- Phase 13 applies only independently revalidated cleanup. Broad structural work belongs to this new roadmap, not that cleanup bucket.
- Phase 14 runs the full automated floor and exact debug artifact activation.
- Phases 15 through 17 repeat complete device, UI/render/settings and relay acceptance.
- Phase 18 reconciles specs, drift, receipts and issues with measured results.
- Phase 19 is the separate Ben-owned push/draft-PR gate. It does not authorize a merge or a release.

The technical entry condition for future roadmap phases is accepted Phase 18 behavior closure plus a rebased source inventory.
Publication and release signing remain separate. Do not pretend missing release credentials invalidate otherwise accepted behavior.
Do not use future roadmap edits to evade an unpassed repair or to reset the drift counter early.

## Verification facts to carry forward

The canonical plan's Phase 9 PiP check now tests the PiP policy, not a whole-Activity grep that rejects other required settings.
Its Phase 13 documentation-only check now requires before/after source identity equality, not a negated successful history query.
Those check-definition fixes are in `50311b6` and do not change product requirements.

At final repair closure, inspect each row's actual receipt, source identity, artifact identity and live observation.
A grep finding the word PASS is not enough if the receipt also says acceptance is pending.
Reconcile `docs/ASKS.md`, `docs/FEEDBACK.md`, `spec/ACCEPTANCE.md` and the indexed receipt hashes.
Only the complete accepted matrix permits `drift: 0`.

## Issues and publication

Private source issue #3 has the B2 offline checkpoint at:
https://github.com/RamenFast/phosphor-mobil3-dev/issues/3#issuecomment-5551295791

The issue remains open. Source and artifact commits are local only.
Keep public sibling comments free of machine addresses, phone serials, private preference data and raw logs.
