# U1 exact-Compose ordering addendum

## Decision and evidence class

This is a bounded dependency investigation, not an R15 review or rescore.
Mobile pin: `a38f39ef9b47c4c9fdb52a7930d35766dea022a4`.
Investigated on 2026-09-08, beginning 14:15 UTC. Root parrot owns implementation, tests, integration and acceptance.

**Static answer:** the inspected Compose **1.11.4** pointer dispatcher does not itself enforce a drain of Scrollable's drag-event channel before ancestor Final, including Final up. Scrollable enqueues drag deltas and the stop marker. A separate node-scope coroutine receives them and calls nested scroll. FIFO processing orders a delta before its own DragStopped handling, not before the ancestor's independent pointer-up observer.

**Supported schedule:** if the next input dispatch runs before the queued receiver resumes, a last move can remain undelivered through up Initial and Final. An old delta can also reach the bridge after a later movement's Initial and spend that movement's same-direction budget. The exact dependency bytecode therefore supports the missing-order boundary identified in U1. This finite dependency inspection establishes no drain invariant that would close U1 as safe.

**Limit:** this is not a reproduced Android gesture. The eight-family inspection reaches the owner's supplied composition effect context, but does not establish its live ContinuationInterceptor. It also does not inspect Android framework input-versus-Handler scheduling. Consequently, this addendum does not prove that framework-delivered move/up events took this schedule on the target, or measure its frequency. An ordering claim about every real gesture would exceed this evidence.

Evidence labels throughout:

- **B:** exact cached bytecode or existing build metadata observed here.
- **S:** exact pinned application source observed here.
- **Q:** schedule supported by those boundaries, with its external scheduling condition stated.
- **U:** unexecuted target behavior or uninspected factory/framework wiring.

## Exact artifact identity

Existing resolved metadata, not a new resolution:

- `app/build/intermediates/incremental/lintAnalyzeDebug/debug-artifact-dependencies.xml` names `androidx.compose.foundation:foundation-android:1.11.4@aar` and `androidx.compose.ui:ui-android:1.11.4@aar` in compile/runtime roots.
- Adjacent `debug-artifact-libraries.xml:68-71,146-149` gives those resolved coordinates and the transformed classes JAR paths below.
- Existing `gradle/libs.versions.toml` specifies Compose BOM `2026.06.01`. Existing `app/build.gradle.kts:483-485` uses that platform plus Compose UI and Foundation. The resolved metadata, rather than the BOM name alone, establishes the inspected version.
- Existing metadata is not a new build receipt for the mobile Git pin. No APK or installed device package was inspected.

| Coordinate | Original cached artifact | SHA-256 |
| --- | --- | --- |
| `androidx.compose.foundation:foundation-android:1.11.4` | `/home/ben/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/foundation-android/1.11.4/90a234e3eec71a1d9600c60db2711253798e474e/foundation.aar` | `a6aac6be46b7756982436b11118d3e630f9a1845e167bf821ae6986ef2c45895` |
| `androidx.compose.ui:ui-android:1.11.4` | `/home/ben/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-android/1.11.4/f74c70bffcd2668943ad9b48969c0dd85a54e698/ui.aar` | `893c3b0a4f90631bbd8cc053a0271e51c525b427b900cc10c6f1a4b3c89e3041` |

Extracted Foundation `classes.jar`: `d046555d433f34de022e0048112669b5aac025d8279695ab08d3d5ab62a6abfa`.
Extracted UI `classes.jar`: `82fa4d600cdbffdb996c65123958349775b6c80bcb48bc1989fa9334d20ffd1a`.

These SHA-256 values exactly match the existing build's respective transformed JARs:

- `.toolchain/gradle-user-home/caches/9.3.1/transforms/4f16be068b44b4df3391077353b32aaf/transformed/foundation/jars/classes.jar`
- `.toolchain/gradle-user-home/caches/9.3.1/transforms/18b33d3f72ccfdf74536789235c29484/transformed/ui/jars/classes.jar`

No matching source JAR was found under either `/home/ben/.gradle` or this project's `.toolchain/gradle-user-home`. The finite fallback was `javap 21.0.11 -p -c -l` over copied classes JARs. No alternate Compose version or downloaded source was used. References below are JVM bytecode offsets within named methods. Retained LineNumberTables are compiler metadata, not independently read Kotlin source.

## Eight dependency source families

Exactly eight distinct `Compiled from` SourceFile families were inspected. Nested and coroutine-generated classes count with their declaring family. No ninth dependency family was opened.

| ID | Family | Retained disassembly | Scope of inspection |
| --- | --- | --- | --- |
| B1 | Foundation `gestures/Draggable.kt` | `Draggable.bytecode.txt`, `DragEvent.bytecode.txt` | DragGestureNode, receiver coroutine and loop, DragDelta/DragStopped payload |
| B2 | Foundation `gestures/Scrollable.kt` | `Scrollable.bytecode.txt` | ScrollableNode drag and pointer delegation, ScrollingLogic, relevant generated lambdas |
| B3 | UI `input/pointer/HitPathTracker.kt` | `HitPathTracker.bytecode.txt` | HitPathTracker, Node and NodeParent pass dispatch |
| B4 | UI `input/pointer/SuspendingPointerInputFilter.kt` | `SuspendingPointerInputFilter.bytecode.txt` | SuspendingPointerInputModifierNodeImpl and PointerEventHandlerCoroutine |
| B5 | UI `platform/AndroidUiDispatcher.android.kt` | `AndroidUiDispatcher.bytecode.txt` | Dispatcher and dispatchCallback |
| B6 | UI `Modifier.kt` | `Modifier.bytecode.txt` | Modifier.Node coroutine-scope ownership |
| B7 | UI `input/nestedscroll/NestedScrollModifier.kt` | `NestedScrollModifier.bytecode.txt` | NestedScrollDispatcher's direct pre/post forwarding |
| B8 | UI `platform/AndroidComposeView.android.kt` | `AndroidComposeView.bytecode.txt` | Owner effect context and touch dispatch/handle/send path |

Whole selected classes were disassembled to retain stable offsets. Only the listed ordering/ownership methods were causally examined. This is not a full audit of those classes. `NestedScrollNode`, verticalScroll's implementation, WindowRecomposer factories, coroutine-library internals and Android framework internals were not inspected.

## Bytecode evidence map

### B1: the queue belongs to DragGestureNode, not the ancestor observer

`androidx.compose.foundation.gestures.DragGestureNode`:

- `startListeningForEvents`, offsets **12-23**: creates a Channel with capacity `2147483647`, the unlimited channel capacity. Offsets **26-46**: obtains `getCoroutineScope()` and invokes `launch$default` with default context/start arguments. This is not an undispatched call to nested scroll from the pointer producer.
- `onPointerEvent-H0pRuoY`, **34-37**: calls `processRawPointerEvent` when enabled.
- `processDraggingState`, **0-7**: ignores passes other than Main.
- Same method, **118-123,219-257**: on last unconsumed up, calls `sendDragStopped` and returns to AwaitDown state.
- Same method, **287-333**: for a nonzero unconsumed move, calls `sendDragEvent-Uv8p0NA` at **328**, then consumes the pointer change at **333**. Consumption here is not proof the nested callback has run.
- `sendDragEvent-Uv8p0NA`, **85-104**: constructs `DragDelta(delta, false)`, invokes Channel.trySend at **99**, discards its result, and returns. There is no acknowledgement wait.
- `sendDragStopped`, **50-72**: constructs `DragStopped(velocity, false)`, trySends it at **67**, and returns. It does not drain preceding deltas before returning to pointer dispatch.
- `DragEvent$DragDelta` constructor, **5-14**, stores only delta and `isIndirectPointerEvent`. There is no pointer ID, physical-event sequence, timestamp or application ticket in this payload.

`DragGestureNode$startListeningForEvents$1.invokeSuspend` receives a DragStarted at **123**, invokes node `drag` at **299**, and handles the resulting DragStopped through `processDragStop` at **385**.

`DragGestureNode$startListeningForEvents$1$1.invokeSuspend`:

- **44-67** tests Stop/Cancel.
- **79-107** invokes the delta callback for DragDelta, at **102**.
- **113-165** receives the next channel event, with a possible coroutine suspension at **152-165**.
- **198-201** stores that event and loops.

This establishes the receiver-side order: process earlier queued deltas, then stop handling. It does not establish any ordering against another coroutine's pointer-up observer.

### B2 and B7: nested pre/child/post are synchronous inside the receiver

`ScrollableNode.drag`, **8-26**, enters `ScrollingLogic.scroll(MutatePriority.UserInput, ...)`. Offsets **29-36** permit suspension.

`ScrollableNode$drag$2$1.invokeSuspend$lambda$0`, **14-38**, converts the received DragDelta to a single-axis offset and calls `scrollByWithOverscroll` with `NestedScrollSource.UserInput` at **33**. This happens in the channel receiver, not in the Main-pass producer.

`ScrollingLogic.performScroll-3eAAhYA`:

1. **0-10** calls nested `dispatchPreScroll` at **7**.
2. **12-52** subtracts pre-consumption and calls the child `ScrollScope.scrollBy` at **41**.
3. **65-87** computes the remainder and calls nested `dispatchPostScroll` at **84**.
4. **89-101** returns total consumption.

There is no suspension parameter or queue between these three operations. `NestedScrollDispatcher.dispatchPreScroll-OzD1aCk`, **0-25**, directly calls parent `onPreScroll` at **10**. `dispatchPostScroll-DzOQY0M`, **0-27**, directly calls parent `onPostScroll` at **12**.

Thus the delayed object is the receiver's processing of the DragDelta. This is not evidence that pre-scroll itself is independently queued after child scroll or post-scroll. Once this processing starts on the UI thread, the inspected pre/child/post segment is synchronous. Parent-chain implementation beyond the dispatcher was not separately disassembled.

### B3 and B4: ancestor Final is not a coroutine-drain barrier

`HitPathTracker.dispatchChanges`, **29-53**, calls the complete Main-event traversal at **43**, then the Final traversal at **53**. Its remaining **71-170** handles deferred node removal/cancel/cache cleanup and returns. No drag-channel receive, coroutine join or UI-dispatcher drain appears.

`Node.dispatchMainEventPass` invokes its own Initial at **178**, traverses children at **501**, then invokes its own Main at **600**. `Node.dispatchFinalEventPass` invokes its own Final at **174**, then children's Final at **483**. Final is ancestor-first, not an after-all-descendant-Final callback.

`SuspendingPointerInputModifierNodeImpl$PointerEventHandlerCoroutine` constructor **29-36** installs `EmptyCoroutineContext`. `offerPointerEvent`, **0-37**, matches the awaited pass, clears the awaiter and resumes it directly at **37**. `awaitPointerEvent`, **38-46**, records the next pass and awaiter. The restricted await scope therefore does not wait for the node-scope drag receiver's dispatcher before running the application's Initial/Final observer body.

### B5, B6 and B8: coroutine ownership and the exact boundary of proof

`Modifier$Node.getCoroutineScope`, **9-58**, obtains the attached Owner's coroutineContext, adds a child Job, and creates the node scope. Node `markAsDetached$ui`, **78-106**, cancels and clears that scope. The drag receiver is node-owned.

`AndroidComposeView` constructor **125-133** obtains the CompositionContext from its supplied ComposeViewContext, reads `effectCoroutineContext`, and stores it as owner coroutineContext. `getCoroutineContext`, **0-4**, returns that field. This is not enough to identify the live app's dispatcher instance. The factory selecting that parent context lies outside the eight-family readset.

The exact cached `AndroidUiDispatcher.dispatch`, **18-23**, appends a Runnable to `toRunTrampolined`. **26-76** schedules `Handler.post(dispatchCallback)` at **49** and a Choreographer callback at **76** when needed. It does not run the Runnable inline in that method.

`AndroidUiDispatcher$dispatchCallback.run`, **0-4**, and `doFrame`, **14-18**, enter `performTrampolineDispatch`. That method **0-20** repeatedly takes and runs queued tasks. These are coroutine-queue drain sites, not pointer-pass drain sites.

`AndroidComposeView.dispatchTouchEvent` calls `handleMotionEvent` at **95**. `handleMotionEvent-8iAsVTc` calls `sendMotionEvent` at **498**, then exits through **519-528**. `sendMotionEvent-8iAsVTc` calls PointerInputEventProcessor.process at **214**, clears the MotionEvent reference at **219-224**, and returns at **288**. The inspected touch path contains no explicit drag-channel or AndroidUiDispatcher drain. PointerInputEventProcessor's body was not disassembled.

**Q condition:** with a queued owner dispatcher, if the caller delivers another pointer event before the receiver Runnable runs, the second event's Initial/Main/Final can run first. The exact bytecode provides both sides of this schedule and no inspected bridge forces the opposite order. Whether Android's actual input pipeline supplies that interleaving, and whether this app's live parent context uses that dispatcher, remain U.

## Four pinned application files

All paths below use `app/src/main/kotlin/dev/phosphor/mobil3/ui/` at the mobile pin.

| File | Git blob | SHA-256 of retained exact object |
| --- | --- | --- |
| `SettingsGestureAdapter.kt` | `872f734be861ec0b2f6a8607bd37e080774f93dd` | `32f31174ab1f54e5af753b98295aaf36b12a02826cfa5a6602c808b4cad145f8` |
| `SettingsSheetAdapter.kt` | `22c34a2449dd35c4ae99d30f11c3e5ca1c6eed15` | `7156b644f0e90931fbac24badbda0e5df51bf05f6712c5d6c015dd1b4997b41b` |
| `SettingsInteraction.kt` | `7e8bde301db96d3c6e0c47482b345c7f31f373fa` | `901de0e282efc664f96f02b9777e105ce4609bbe62beeefb3d651809ec6d4861` |
| `Sheets.kt` | `2199a855a1477e92cb7930653aa86668ed9eecf3` | `132dfef5e9ca8281d225466446ff6c288c92d2578c71cf5f20d11e08c71b72f3` |

S wiring:

- `Sheets:358-361,370-376,472` places the observer on the outer Box and the Settings nested connection on the card. `Sheets:1170-1173,1481-1485` supplies retained Settings scroll and a verticalScroll body with `overscrollEffect = null`.
- `SettingsSheetAdapter:131-146` calls `initial` during Initial and `final` during Final. `146-148` immediately invokes close/return. This is not a receiver-side DragStopped callback.
- `SettingsSheetAdapter:94-110` forwards only delta, source and child/top state. Pre calls `reverse`. Post calls `remainder`. It supplies no producer-time identity.
- `SettingsGestureAdapter:24,47,52` resets budget at every Initial, assigns the current movement budget, and marks up pending. `74-83` consumes whichever current ticket/budget exists when an untagged nested call arrives. `87-102` decides release and clears the ticket/budget at Final up.
- `SettingsInteraction:101-102` independently accepts the slow raw-distance branch, regardless of final direction. `128-134` contains the unchanged thresholds.
- `Sheets:303-308` retires the Settings owner when the close commits. `SettingsGestureAdapter:105-106` and `SettingsSheetAdapter:113-115` do not provide a second fling release.

## Concrete supported schedules

### Q1: U1 raw200, reversal100, then up

Conditions: a single direct pointer has an active ticket, body Scrollable is already dragging, prior admitted raw is 200dp, geometry is stable, and the last move's receiver processing is delayed until after the up dispatch. This is a schedule condition, not a claimed device observation.

| Order | Actor and callback | Result |
| --- | --- | --- |
| 1 | Ancestor Initial for move M, physical delta -100 | Adapter budget = -100 |
| 2 | Child Scrollable Main for M | trySend DragDelta(-100), pointer change consumed, receiver not yet run |
| 3 | Ancestor Final for M | Physical direction becomes upward, raw remains 200 |
| 4 | Caller supplies up U before receiver dispatch | No inspected pointer-path drain prevents this ordering |
| 5 | Ancestor Initial for U | Budget resets to 0, releasePending = true |
| 6 | Child Scrollable Main for U | trySend DragStopped after DragDelta in the same channel |
| 7 | Ancestor Final for U | raw200 satisfies independent raw>=192 branch, close commits and owner retires |
| 8 | Receiver would process the earlier DragDelta | Reversal cannot change the committed decision. If delivery survives, no active ticket/budget exists. If lifecycle cancellation prevents it, the committed decision still stands |

If receiver processing occurs between steps 3 and 5 instead, reverse repays 100 and leaves raw100. The upward-terminal gesture then returns. Therefore ordering, not a changed threshold or flick-direction calculation, distinguishes the two results.

The converse from U1 is also supported under the same condition: raw180 plus a final eligible +40 remains raw180 if that delta is processed only after up, so it returns instead of reaching raw220.

### Q2: an old callback spends a later same-gesture movement budget

Conditions: same active drag/ticket, top edge, no child consumption, raw100 already admitted, and two movement events reach the pointer path before receiver processing.

1. M1 Initial sets budget +40. M1 Main enqueues DragDelta(+40). M1 Final leaves budget +40.
2. M2 Initial replaces that budget with +10. M2 Main enqueues DragDelta(+10). M2 Final leaves budget +10.
3. The receiver processes M1 first. Its nested post-scroll supplies available +40, UserInput and atTop=true.
4. The adapter uses the current ticket and caps +40 against current budget +10. Raw becomes 110 and budget becomes 0.
5. The receiver processes M2. Its +10 is rejected because budget is 0. Correctly attributed total travel would have produced raw150.

No channel reordering is needed. FIFO is preserved. The attribution failure is between the channel and the adapter's latest physical budget. The late callback arrives after M2 Final in this example, which is also after M2 Initial. No impossible mid-pass UI-thread preemption is assumed.

This already answers the new-Initial question without requiring a new finger or new gesture. Cross-ticket safety also cannot be inferred from these payloads, but no separate cross-gesture Android incidence claim is made.

## Smallest bounded correction seam, not an implementation

The product seam is **SettingsGestureAdapter.initial/final plus SettingsSheetDismiss.nestedScroll** in `SettingsSheetAdapter:94-117`. The two requirements are inseparable:

1. A release must not commit from raw travel while an earlier potentially relevant movement remains unresolved. Drain with an acknowledged boundary, or safely cancel that ambiguous dismissal.
2. Late nested delivery must not spend a different physical event's budget or a different ticket. Identity must originate before the asynchronous producer boundary, or an equivalent proven attribution protocol must replace the latest-budget assumption.

Merely delaying `onClose`, reading the current ticket when the callback arrives, or moving release to a fling callback does not establish these requirements. Up Initial has already zeroed the budget and marked release pending. Likewise, one yield or one frame is not an acknowledged channel drain. FIFO stop handling orders Scrollable's own stream only. A producer/bridge design would have to preserve single-release ownership, with no second post-fling close opportunity.

This addendum specifies the seam and obligations, not a validated patch. Root owns the bounded correction and its tests. No new gesture thresholds or unrelated platform work are proposed.

## Checks, preservation and exact remaining blocker

Performed:

- Read governance, context standards and private-file policy. No Python linter was run because Python was forbidden.
- Read U1 and exactly four authorized application source paths from Git objects. Captured their blob IDs and SHA-256 values.
- Resolved exact Compose coordinates from existing build metadata and checked cached AAR/class-JAR hashes against the actual transformed build JARs.
- Searched the two Gradle homes for exact source JARs. None matched. Disassembled only the eight SourceFile families listed above.
- Traced move enqueue, receiver suspension/iteration, nested pre/child/post, pointer Initial/Main/Final, and node-scope/owner/dispatcher ownership.
- Manually evaluated Q1 and Q2 against pinned source. These were not executed fixtures.
- Retained private inputs and disassembly. Identity checks are recorded separately in `VALIDATION.txt`. `SHA256SUMS` seals every retained evidence file.

Preserved original report hashes:

- Round1: `175f9457fe60157af1dd227c31c1f5b3c33237ad393e5e5323d50983e2b378bb`.
- Round2: `ecca882edbfc723788bb0f98c14bfd9bd47cae346f73fd0a5d13b02af31fb29c`.

Both original reports and their 7/10 scores remain unchanged. This addendum does not accept gestures, rescore R15, or close Android acceptance.

**Blocked:** proof that real framework-delivered move/up events in this app take or exclude Q1. **Exact evidence:** the receiver uses the supplied owner's effect context, whose live dispatcher was not observed. The eight-family limit excludes its factory and Android input scheduling. Target execution is forbidden. **Best current result:** exact Compose 1.11.4 has the asynchronous channel boundary and no inspected Final-up drain, so the original source risk is not disproved and now has dependency-level support. **Smallest next step:** root can first verify the live/default owner dispatcher and input-delivery ordering under its own authorized acceptance scope. If further static factory tracing is wanted instead, it requires a separate source allowance, not another review.

No Gradle/build, test runner, target execution, download/network, device/ADB, GUI, audio, service, Git mutation, product edit or worker was started. Only private scratch artifacts were written. All source/read/build access will be explicitly released in the completion report, before 14:36 UTC.
