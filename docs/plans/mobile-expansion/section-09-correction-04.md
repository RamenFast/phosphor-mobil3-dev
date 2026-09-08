# Section 9 correction04: cancel ambiguous queued dismissal

Written before the adapter correction on2026-09-08 at14:35UTC. The exact Compose1.11.4 dependency evidence is retained unchanged in section-09-compose-order-addendum.md. It establishes a queued nested-scroll boundary without a Final-up drain. It does not establish the target's actual input scheduling. Original full review scores remain7/7.

## Defined behavior

A Settings body gesture may qualify only while physical movement and its child delivery remain attributable to the same observed event. At Final, a consumed physical move with no completed header or nested post callback creates an unresolved delivery receipt. A callback arriving after that Final but before the next Initial may complete the receipt and use the original event budget.

If another physical Initial arrives before that completion, cancel the dismissal before replacing its budget. Discard its raw travel and return the card. Do not allow a delayed callback to spend a later event budget. Do not commit CLOSE at the subsequent up. This covers both the queued reversal and same-direction schedules from the retained audit. Unconsumed touch-slop movement does not create a pending queue receipt.

Cancellation of an unresolved body delivery disables drag dismissal for this Settings opening. A new down is ignored, so delayed work cannot enter a new ticket. The existing explicit close/Back and ordinary scrolling remain available. Close/reopen creates a new owner and retires the old captured callback target. An untagged fling callback cannot prove which stream ended and therefore never clears this latch or creates CLOSE. No timer, coroutine wait, inferred frame drain or source action is added.

Normal header delivery, normal synchronous nested delivery and delayed delivery completed before the next Initial retain their existing thresholds and single Final release. Returning offsets and cancellation use the existing finite animation. A pointer lost, multiple pointers, geometry replacement and sheet retirement retain their existing cancellation behavior.

## Implementation and checks

SettingsGestureAdapter tracks completed delivery per physical event and unresolved stream retirement separately from raw distance. Header callbacks complete their own delivery. A direct nested post callback completes the pre/child/post unit, including fully child-consumed or ineligible remainder. SettingsSheetAdapter passes the actual Final event's consumed state to this production owner. Initial/Final remain observers, not additional pointer consumers.

Production host fixtures must cover raw200/reverse100/up returning, raw100/queued40/new10 refusing old-budget reassignment, late work after cancellation, sticky rejection across a new down and untagged fling, and a new owner accepting a fresh down. Retain the existing ordinary/slop/header/delayed-within-event cases. Add a source-linked check for actual Final consumed-state wiring. Android callback delivery and fast-gesture usability still require the authorized target. If cancellation proves too frequent there, replace the queue boundary with a producer-identified stream rather than weakening this ambiguity check.

## Coordinator host evidence

The actual production SettingsInteraction and SettingsGestureAdapter suite passed50 tests at14:41:48UTC. Eight new cases exercise the finite queued-delivery and sticky retirement schedules plus real adapter source wiring. The exact snapshot includes the root-owned SettingsSheetAdapter and pinned8bf88d0 PhosphorScreen for source assertions only. The appearance writer's live files were neither read nor compiled by this runner.

Runner `dev/scratch/mobile-expansion-20260908T001819Z/run-settings-queue-host-1442.sh` has SHA256958c1d8f0c1a6c98e7b8d0c1a334c4a2ff10af4a425d8137ece19ebca514fe96. Its sibling settings-queue-host-1442 directory retains before/after live hashes, snapshot and exact cached dependency hashes, compiler output and test log. The source and compiled test inputs matched before and after the run. No Gradle or Android target was executed.

SettingsSheetDismiss exposes observable interrupted state after cancellation or the next Initial. The appearance writer is adding one inline Settings-only recovery message in its owned SheetHost path. That message is not part of this host-run input. Combined Android compilation, review and physical usability remain open. This is a conservative recovery path, not proof that rapid queued gestures remain usable on the phone.
