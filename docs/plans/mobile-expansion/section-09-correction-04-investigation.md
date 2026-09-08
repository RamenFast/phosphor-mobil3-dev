# Section 09 correction04 bounded source addendum

## Decision and scope

Completed 2026-09-08, before the 14:55 UTC release deadline. This is a correction investigator's source-only assessment, not a complete section review, implementation approval, rescore, or Android acceptance.

**Finite answer:** correction04 cancels both exact retained Q1 and Q2 schedules before the next physical budget replaces the unresolved budget. After that cancellation, late nested/header callbacks and untagged fling cannot clear the latch, create a new ticket, or commit CLOSE. This closes those two source schedules by abandoning dismissal, not by delivering the queued travel correctly.

**Material remaining usability defect/condition:** the receipt predicate cannot distinguish a queued body scroll from a consumed non-nested child move. A consumed slider/control move with any vertical displacement and no completed header/nested post disables all drag dismissal for that opening at the next Initial. The inherited slider fixture does not supply consumed Final and therefore misses this path. Target incidence and acceptability remain unmeasured.

Original reports and their original scores remain unchanged. No new score is assigned.

## Exact identity and read boundary

- Mobile: `5d4d19d140638531b593fdde463f7e0e0d97dbfb`.
- Compare: `8bf88d0e50153d179254cfd9af219d793f9a4506`.
- Shared: `0ffd658d7f19e68180c2720e0500b23644619e90`, resolved in sibling phosphor. No shared production source was needed or read.
- All product source came from immutable Git objects or private copies of those objects. No live product files were read.
- Five distinct production paths: SettingsGestureAdapter.kt, SettingsInteraction.kt, SettingsSheetAdapter.kt, original compare-pin Sheets.kt, and mobile-pin PhosphorScreen.kt.
- PhosphorScreen was the one necessary neighbor. Its conditional Settings branch and dismiss callback resolve whether close/reopen actually removes the remembered dismissal owner.
- Sheets was used only for the owner lifetime, captured connection, and explicit-close availability trace. Incidental adjacent displayed lines were not reviewed as controls.
- Both authorized test classes were read as source only. Correction03, correction04, retained dependency addendum, and immutable docs/AGENTS.md were read.
- `OBJECTS.txt` records commit/blob/path identities. `READSET.sha256` records all retained source/context bytes. `BOUNDED.diff` retains the three authorized adapter/decision-file comparisons. SettingsInteraction has no change in that comparison.
- Governance and the context/scoping/private-file skills were read before the investigation. Python was forbidden and was not used for prose linting.

Path abbreviations below refer to `app/src/main/kotlin/dev/phosphor/mobil3/ui/`, unless tests are named. All lines use the mobile pin except Sheets, which uses the compare pin.

## Checked obligations

| Obligation | Exact source trace | Result and evidence class |
| --- | --- | --- |
| Q1 raw200, delayed reverse100, then up | GestureAdapter:105-108 creates pending at consumed move Final. Up Initial:28-29 calls cancel before :30 can replace budget. Cancel:128-136 latches, clears ticket/budget, and cancels raw through Interaction:115-120. Up Final:106 returns NONE without release. | Static finite closure of this schedule. No stale raw200 CLOSE. |
| Q2 raw100, queued40, later10 | M1 Final:108 sets pending. M2 Initial:28-29 cancels and returns before budget assignment :54. Late remainder has no ticket at :87 or :93. | Static finite closure. Neither old40 nor new10 can spend a newer budget. Raw returns to zero rather than correctly accumulating150. |
| Late callbacks after cancellation | completeDelivery:86-90 returns when ticket is null. admit:92-101 returns when ticket is null. initial:29 rejects every new down while requiresReopen is true. | Static finite closure after the pending-delivery latch. A late callback does not rearm. |
| Untagged fling | GestureAdapter:125-126 returns NONE without writes. SheetAdapter:118-120 only calls it and returns zero velocity. | No second CLOSE and no latch clear. |
| Delayed delivery within the same event interval | GestureAdapter:108 keeps pending and retains budget through Final. Direct remainder:68-75 completes receipt before admission. The next Initial sees pending false. | Preserved under the retained synchronous pre/child/post condition. No new dispatcher proof. |
| Reversal completion | reverse:78-80 repays travel, but does not itself acknowledge the entire nested unit. Direct post at :68-73 completes it. | Correct for retained pre/child/post order. Reverse alone followed by next Initial still cancels conservatively. |
| Header path | header:62-65 admits and completes before Final. SheetAdapter:173-182 invokes it from the header drag callback and consumes only admitted travel. | Ordinary completed header path is retained before an interruption. Header is also disabled after the opening-wide latch. |
| Fully consumed/non-top body delivery | remainder:69-74 acknowledges a direct post even when the child consumed everything or the remainder is ineligible. | Such a completed delivery does not latch. A queued, uncompleted one does. |
| Slop | GestureAdapter:108 requires consumedByChild and a changed y. | Unconsumed slop does not latch. This is not a guarantee about a consumed child-control move or target slop ownership. |
| Actual Final wiring | SheetAdapter:151-152 passes `finalEvent.changes.any { it.isConsumed }`, not Initial state or an omitted default. Initial:139,143-147 restricts pointer count/type. Observer :132-165 contains no consume call. | Source wiring is correct for the conservative predicate. Ancestor Final remains ancestor-first under the retained audit, not a drain or an after-descendant-Final guarantee. |
| Visible return | SheetAdapter:155-158 returns a displaced, uncommitted card when cancellation leaves raw zero. :69-83 uses reduced-motion immediate return or existing160ms tween. | Authored source path only. Animation/coroutine/render behavior was not executed. |
| Correct owner recreation | Sheets:298 creates a local remembered SettingsSheetDismiss. :300-308 retires on disposal and committed close. :366-369 calls onDismiss after exit and provides Back. :511-514 supplies explicit Close settings. :1170-1173 passes the host's dismiss callback and scroll. PhosphorScreen:814-829 removes Settings via sheet=NONE, with empty NONE branch :841. | No latch survives an actually removed and recreated dismissal owner in this source composition. Mere recomposition or pointerInput restart is not recreation. |
| Old connection isolation | Sheets:358-360 remembers the connection by settingsDismiss. SheetAdapter:99-122 connection methods close over that SettingsSheetDismiss and its gesture. Each new SettingsSheetDismiss creates its own adapter at :50. | A callback on the old captured connection cannot mutate the new owner's adapter. This does not independently validate Android node attachment/disposal. |

### Q1, step by step

1. Start with the retained condition: one active body ticket, stable geometry, raw200, and Scrollable already dragging.
2. Initial for -100 installs the negative physical budget.
3. Child Main consumes and queues -100 without nested completion.
4. Final sets pendingDelivery. Raw may still be200, but no release occurs yet.
5. Up Initial calls cancel before any replacement or releasePending assignment. This sets requiresReopen and zeros uncommitted raw.
6. Up Final finds no ticket and returns NONE. SheetAdapter returns any displaced card.
7. Late reverse/post cannot admit travel or clear the latch. Post-fling also cannot change it.

If the complete pre/child/post arrives between steps4 and5 instead, reverse pays back100, post clears pending, and ordinary up returns from raw100. Both alternatives are explicit in the corrected source.

### Q2, step by step

1. Begin with the retained raw100 and top-edge conditions.
2. M1 Initial budgets40, its child queues40, and consumed Final creates pending.
3. M2 Initial cancels and latches before it can set budget10.
4. M1 post40 and M2 post10 find no active ticket. Neither modifies raw or enables another release.

The reverse direction and same-direction cases share the cancellation boundary. No channel reordering or mid-pass UI-thread preemption is needed for either argument.

## Material usability gap and fixture blind spot

The actual predicate at GestureAdapter:108 is:

`consumedByChild && eventY != y && !releasePending && !delivered`

It does not require raw travel, body ownership, top eligibility, or proof of queue production. Therefore this finite sequence latches even at raw0:

1. A fresh down creates a ticket.
2. A child control consumes a move with nonzero vertical displacement. It calls neither header nor nested post.
3. Actual Final is consumed, so the adapter records pending.
4. The next move or up Initial calls cancel and sets requiresReopen.
5. Later down and header/body drags are ignored for the remainder of that opening. Waiting or a fling does not recover them.

This is a conditional source fact. It is not a claim that a particular real slider delivered this event sequence on Android. Purely horizontal movement with exactly unchanged y does not meet this predicate. Even slight vertical drift can meet it when the other stated conditions hold.

`SettingsGestureAdapterTest.kt:141-147`, named childConsumedSliderMoveCannotBecomeEligibleTravel, calls the helper at :120-124. That helper invokes `final()` with the default consumed flag false. The test still checks that an undelivered child move does not become eligible raw, but it does not check the newly wired consumed-Final state, opening-wide latch, or subsequent header recovery. The new slop/child-only fixture :79-92 covers unconsumed slop and a completed nested post, not a consumed non-nested control.

Smallest source fixture follow-up for root: pass true at a consumed no-post move Final, then assert interruption on next Initial and attempt a later fresh header gesture. Root owns the desired control-ownership correction and its checks. No runner was invoked here.

## Attribution condition that remains explicit

The correction is a conservative receipt protocol, not producer-tagged identity. `completeDelivery` checks ticket existence, not the originating physical event or ticket. An untagged direct post arriving while an active ticket exists can acknowledge that ticket's current receipt, including zero or ineligible remainder. Within exact Q1/Q2, the first unresolved consumed Final prevents the next Initial from ever creating such a later eligible ticket, so this is not a counterexample to those closures.

The bounded proof depends on the retained pre/child/post unit completing synchronously and on each relevant queued move being represented by an uncompleted consumed Final before budget replacement. It does not establish arbitrary-callback safety for an unrelated direct UserInput producer, an unrepresented earlier stream, or cancellation before its receipt is recorded. This investigation did not establish a realizable Android schedule for those cases and does not label one as a reproduced defect. No broader dispatcher audit or fuzzing was performed. Do not promote these two schedule closures into a universal event-identity guarantee.

## Test and target evidence separation

Read as source:

- New Q1/Q2 and sticky/reopen fixtures: SettingsGestureAdapterTest:7-55.
- Consumed late-within-event, completed reversal, slop, completed child, header, and actual Final source assertion: :57-114.
- Existing ownership, thresholds, direction, cancellation and retirement fixtures in that class.
- SettingsInteractionTest's decision-owner and presentation fixtures. These do not execute Compose input or establish callback identity.

Inherited coordinator claim, not independently run or verified here: `run-settings-queue-host-1442.sh` passed50 actual pure tests. The supplied identities are:

- Snapshot manifest: `7e1eb1f74d8e335311e0b69714d1d534d3fa1122bdf507c06292b1db26eb6902`.
- Live manifest: `44753af40e79079e03db5369a492f5b4fba5b998d8a2bec401346f9722daabad`.
- Runner: `958c1d8f0c1a6c98e7b8d0c1a334c4a2ff10af4a425d8137ece19ebca514fe96`.

The retained dependency addendum's Compose1.11.4 queue-versus-Final result is inherited immutable context. No cached bytecode, factory, dependency source, or dispatcher was reread. Its unknown live owner interceptor and Android input-versus-handler scheduling remain unknown.

Unchecked obligations:

- Frequency of cancellation during ordinary body scrolling, fast flicks, queued last moves, and move/up batching on the target.
- Actual slider and new appearance control consumption, vertical drift, slop, and child-input ownership.
- Physical close/Back availability, close-animation completion, reopen owner identity, and late callback routing on Android.
- Real animation return, observed interruption state, and any rendered recovery message.
- Ant's live/uncommitted appearance files and inline message, all implementation after this pin, combined compilation, target accessibility, and Android acceptance.

At14:47 UTC root acknowledged the slider fixture gap and described a separate child-input ownership correction. That is outside this pin and this review. It is not inspected, approved, or included in the closure claim.

**Blocked for target acceptance:** actual owner dispatcher/input ordering and control-consumption behavior are not observed, and behavioral execution is forbidden. **Best current result:** exact Q1/Q2 cancel safely under their retained source conditions, with the consumed-child false-interruption path and test blind spot explicitly reported. **Smallest target check:** under root authorization, trace Initial/Final consumption, nested completion, interruption and owner identity during one fast body sequence, one consumed slider drag with vertical drift, and explicit close/reopen. This measures the named boundary without requiring a broad scrollable rewrite.

## Preservation and release

Only this worker's private snapshot, addendum and receipt files were written. No Gradle, build, runner, Android/JNI/GPU, ADB/device, GUI/audio, network/download, service, Python, Git mutation, product edit, or worker spawn occurred. SHA-256 verification passed for the14 retained source/context objects after snapshot creation.

Original review reports and dependency addendum were not edited. All material source conditions are listed above. No undisclosed source hold remains. Root owns implementation, integration, tests, builds, devices, Git retention and cleanup. Source reading ended after the finite owner-lifetime facts at14:47:37 UTC. Completion report explicitly releases all source/read/build ownership before14:55 UTC, with no further reads planned.
