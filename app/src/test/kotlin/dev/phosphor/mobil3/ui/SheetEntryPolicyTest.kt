package dev.phosphor.mobil3.ui

import java.io.File
import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.coroutines.CoroutineContext

/** Policy behavior and separately labeled source-only Compose wiring checks. */
class SheetEntryPolicyTest {

    @Test
    fun unrotatedPortraitRisesFromTheBottomWhereTheConsoleIs() {
        assertEquals(SheetEntry.FROM_BOTTOM, SheetEntryPolicy.entry(landscape = false, uiPlacementLocked = false))
        assertEquals(SheetEntry.FROM_BOTTOM, SheetEntryPolicy.entry(landscape = false, uiPlacementLocked = true))
    }

    @Test
    fun lockedLandscapeSlidesInFromTheEdgeItIsAnchoredTo() {
        // The card is pinned to an edge here, so rising from the screen bottom made it
        // look like it came from somewhere unrelated to the bar being touched.
        assertEquals(SheetEntry.FROM_EDGE, SheetEntryPolicy.entry(landscape = true, uiPlacementLocked = true))
        assertTrue(SheetEntryPolicy.animatesHorizontally(landscape = true, uiPlacementLocked = true))
    }

    @Test
    fun unlockedLandscapeRisesFromTheCentreBecauseThatIsWhereTheConsoleSits() {
        // Ben's separate report: unlocked landscape was defaulting to the right side in
        // BOTH rotations, while the console itself is centred.
        assertEquals(SheetEntry.FROM_BOTTOM, SheetEntryPolicy.entry(landscape = true, uiPlacementLocked = false))
        assertFalse(SheetEntryPolicy.animatesHorizontally(landscape = true, uiPlacementLocked = false))
    }

    @Test
    fun theTravelIsMeasuredAlongTheAxisTheCardActuallyMovesOn() {
        // A sideways card given a height-sized travel felt like the drag had to cover
        // the whole screen before anything appeared.
        assertEquals(
            2340,
            SheetEntryPolicy.travelPx(landscape = true, uiPlacementLocked = true, widthPx = 2340, heightPx = 1080),
        )
        assertEquals(
            1080,
            SheetEntryPolicy.travelPx(landscape = true, uiPlacementLocked = false, widthPx = 2340, heightPx = 1080),
        )
        assertEquals(
            2340,
            SheetEntryPolicy.travelPx(landscape = false, uiPlacementLocked = false, widthPx = 1080, heightPx = 2340),
        )
    }

    @Test
    fun exactlyOneOfTheFourCombinationsSlidesSideways() {
        // Guards the shape of the rule: only locked landscape is the edge case, so a
        // future change cannot quietly make portrait slide sideways too.
        val sideways = listOf(false, true).flatMap { landscape ->
            listOf(false, true).map { locked ->
                Triple(landscape, locked, SheetEntryPolicy.animatesHorizontally(landscape, locked))
            }
        }.filter { it.third }
        assertEquals(1, sideways.size, "unexpected sideways cases: $sideways")
        assertEquals(true to true, sideways.single().first to sideways.single().second)
    }

    @Test
    fun portraitHalfTurnUsesLocalTopWhileLandscapeKeepsViewerRight() {
        val portrait = listOf(SheetEntry.FROM_BOTTOM, SheetEntry.FROM_BOTTOM, SheetEntry.FROM_TOP, SheetEntry.FROM_BOTTOM)
        for (q in 0..3) {
            assertEquals(portrait[q], SheetEntryPolicy.entry(false, true, q), "portrait q$q")
            assertEquals(SheetEntry.FROM_EDGE, SheetEntryPolicy.entry(true, true, q), "landscape q$q")
            for (landscape in listOf(false, true)) {
                assertEquals(SheetEntry.FROM_BOTTOM, SheetEntryPolicy.entry(landscape, false, q))
            }
        }
        assertEquals(SheetEntry.FROM_TOP, SheetEntryPolicy.entry(false, true, -2))
        assertEquals(SheetEntry.FROM_TOP, SheetEntryPolicy.entry(false, true, 6))
        // Local top rotated by a half-turn is physical bottom. The pinned console stays there.
        assertEquals(SheetEntry.FROM_TOP, SheetEntryPolicy.entry(false, true,
            RotationDetent.presentation(false, RotationDetent.Presentation(), false, true, 180, 0).uprightQuadrant))
    }

    @Test
    fun gestureExitContinuesLocalDownForEveryEntryAndNormalExitFollowsChosenEdge() {
        for (entry in SheetEntry.entries) {
            assertEquals(SheetEntry.FROM_BOTTOM, SheetEntryPolicy.exit(entry, fromDrag = true, committed = null))
            assertEquals(entry, SheetEntryPolicy.exit(entry, fromDrag = false, committed = null))
        }
    }

    @Test
    fun firstCommittedExitCannotBeRedirectedByLaterCallbacksOrEntryChanges() {
        for (entry in SheetEntry.entries) {
            for (firstDrag in listOf(false, true)) {
                val committed = SheetEntryPolicy.exit(entry, firstDrag, null)
                for (laterEntry in SheetEntry.entries) {
                    for (laterDrag in listOf(false, true)) {
                        assertEquals(committed, SheetEntryPolicy.exit(laterEntry, laterDrag, committed))
                    }
                }
            }
        }
    }

    @Test
    fun sourceOnlyAppliedQuadrantControlsAnchorAndBothEntryAnimations() {
        val screen = File("src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt").readText()
        assertTrue(screen.contains("LocalSheetEntryQuadrant provides sheetQuadrant"))
        val host = sheetHostSource()
        for (wiring in listOf(
            "SheetEntryPolicy.entry(landscape, uiLocked, LocalSheetEntryQuadrant.current)",
            "SheetEntry.FROM_EDGE -> AbsoluteAlignment.BottomRight",
            "SheetEntry.FROM_TOP -> Alignment.TopCenter",
            "SheetEntry.FROM_BOTTOM -> Alignment.BottomCenter",
            "top = if (entry == SheetEntry.FROM_TOP) Dim.cardMarginBottom else 0.dp",
            "bottom = if (entry == SheetEntry.FROM_TOP) 0.dp else Dim.cardMarginBottom",
            "translationX = 0f", "translationY = 0f",
            "translationX = (1f - progress) * sheetWidthPx",
            "translationY = entrySign * (1f - progress) * sheetHeightPx",
        )) assertTrue(host.contains(wiring), wiring)
        assertEquals(2, Regex(Regex.escape("{ entrySign * (it / 3) }")).findAll(host).count())
    }

    @Test
    fun sourceOnlyFirstCommitLatchesCurrentEntryBeforeClosingWithoutResettingLiveOffset() {
        val host = sheetHostSource()
        assertTrue(host.contains("val currentEntry by rememberUpdatedState(entry)"))
        val commit = host.substringAfter("val commitDismiss:").substringBefore("val dismissOffset")
        val guard = commit.indexOf("if (openState.targetState &&")
        val latch = commit.indexOf("dismissal.commit(currentEntry, fromDrag)")
        val close = commit.indexOf("openState.targetState = false")
        assertTrue(guard >= 0 && latch > guard && close > latch)
        assertFalse(commit.contains("snapTo"))
        assertFalse(commit.contains("onDismiss()"))
        assertTrue(host.contains("val dismiss = { commitDismiss(false) }"))
        val settle = host.substringAfter("val settleDismiss:").substringBefore("val dismissNestedScroll")
        assertTrue(settle.contains("dismissal.settle(velocityY, dismissDistancePx, dismissFlickPx, reduced, style)"))
        assertTrue(settle.contains("commitDismiss(true)"))
        assertTrue(host.contains("val exit = dismissal.committed?.edge ?: entry"))
        assertTrue(host.contains("exit = if (reduced) fadeOut() else if (exit == SheetEntry.FROM_EDGE)"))
        assertTrue(host.contains("{ exitSign * (it / 2) }"))
        assertTrue(host.contains("dismissal.offsetPx.roundToInt()"))
        assertEquals(1, Regex("onDismiss\\(\\)").findAll(host).count())
    }

    @Test
    fun sourceOnlyHeaderNestedAndExplicitClosesKeepTheirExistingOwners() {
        val host = sheetHostSource()
        for (wiring in listOf(
            "BackHandler { dismiss() }",
            "detectTapGestures(onTap = { dismiss() })",
            "Modifier.clickable(onClick = dismiss)",
            "onDragEnd = { settleDismiss(0f) }",
            "settleDismiss(available.y.coerceAtLeast(0f))",
            "if (!openState.targetState && openState.isIdle) onDismiss()",
            "!openState.isIdle || dismissOffset.isRunning",
        )) assertTrue(host.contains(wiring), wiring)
    }

    private fun sheetHostSource(): String = File("src/main/kotlin/dev/phosphor/mobil3/ui/Sheets.kt")
        .readText().substringAfter("fun SheetHost(").substringBefore("// Labels sit above")

    @Test
    fun productionDismissalIgnoresEndCancelReversalAndFlingAfterPartialDragClose() {
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(Job() + dispatcher)
        try {
            for (entry in SheetEntry.entries) {
                val state = SheetDismissState(scope)
                state.begin()
                state.dragBy(50f)
                dispatcher.drain()
                assertEquals(50f, state.offsetPx)
                assertTrue(state.commit(entry, fromDrag = false))
                val committed = state.committed
                repeat(2) { state.settle(0f, 72f, 1000f, true, RoomStyle()) { error("late end/cancel") } }
                state.begin()
                state.dragBy(-40f)
                state.dragBy(200f)
                state.settle(2000f, 72f, 1000f, true, RoomStyle()) { error("late fling") }
                assertFalse(state.commit(SheetEntry.FROM_BOTTOM, fromDrag = true))
                dispatcher.drain()
                assertSame(committed, state.committed)
                assertEquals(entry, state.committed?.edge)
                assertEquals(50f, state.rawPx)
                assertEquals(50f, state.animation.value)
                assertEquals(50f, state.offsetPx)
            }
        } finally { scope.cancel() }
    }

    @Test
    fun productionDismissalFencesWritersQueuedBeforeCommitAndCapturesAppliedNotRaw() {
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(Job() + dispatcher)
        try {
            for (pending in listOf("begin", "snap", "restore")) {
                val state = SheetDismissState(scope)
                state.dragBy(50f)
                dispatcher.drain()
                when (pending) {
                    "begin" -> state.begin()
                    "snap" -> state.dragBy(30f)
                    else -> state.settle(0f, 72f, 1000f, true, RoomStyle()) { error("subthreshold") }
                }
                assertEquals(50f, state.animation.value)
                if (pending == "snap") assertEquals(80f, state.rawPx)
                if (pending == "restore") assertEquals(0f, state.rawPx)
                assertTrue(state.commit(SheetEntry.FROM_EDGE, fromDrag = false))
                dispatcher.drain()
                assertEquals(50f, state.animation.value, pending)
                assertEquals(50f, state.offsetPx, pending)
                assertFalse(state.animation.isRunning)
            }
        } finally { scope.cancel() }
    }

    @Test
    fun productionDismissalRestoresBeforeCommitAndGestureCommitWinsOnce() {
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(Job() + dispatcher)
        try {
            val state = SheetDismissState(scope)
            state.dragBy(50f)
            dispatcher.drain()
            state.settle(0f, 72f, 1000f, true, RoomStyle()) { error("subthreshold") }
            dispatcher.drain()
            assertEquals(0f, state.offsetPx)
            assertEquals(null, state.committed)
            state.begin()
            state.dragBy(90f)
            dispatcher.drain()
            var commits = 0
            val commit = { if (state.commit(SheetEntry.FROM_EDGE, true)) commits += 1 }
            state.settle(0f, 72f, 1000f, true, RoomStyle(), commit)
            state.settle(2000f, 72f, 1000f, true, RoomStyle(), commit)
            assertFalse(state.commit(SheetEntry.FROM_TOP, false))
            dispatcher.drain()
            assertEquals(1, commits)
            assertEquals(SheetEntry.FROM_BOTTOM, state.committed?.edge)
            assertEquals(90f, state.offsetPx)
        } finally { scope.cancel() }
    }

    @Test
    fun productionDismissalFreezesIntermediateRestoreAndStopsItsRealAnimation() {
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined + clock)
        try {
            val state = SheetDismissState(scope)
            state.dragBy(50f)
            state.settle(0f, 72f, 1000f, false, RoomStyle()) { error("subthreshold") }
            clock.sendFrame(0L)
            clock.sendFrame(40_000_000L)
            val applied = state.animation.value
            assertTrue(applied > 0f && applied < 50f, "actual intermediate offset=$applied")
            assertEquals(0f, state.rawPx)
            assertTrue(state.animation.isRunning)
            assertTrue(state.commit(SheetEntry.FROM_EDGE, false))
            assertEquals(applied, state.offsetPx)
            assertFalse(state.animation.isRunning)
            clock.sendFrame(200_000_000L)
            state.settle(0f, 72f, 1000f, true, RoomStyle()) { error("late cancel") }
            assertEquals(applied, state.animation.value)
            assertEquals(applied, state.offsetPx)
        } finally { scope.cancel() }
        // Production Animatable and owner execute with a synthetic host frame clock, not Android pointers.
    }

    @Test
    fun sourceOnlyHostUsesTheGuardedOwnerAndItsCommittedRenderedOffset() {
        val host = sheetHostSource()
        for (wiring in listOf(
            "val dismissal = remember { SheetDismissState(scope) }",
            "val beginDismiss = { dismissal.begin() }",
            "dismissal.dragBy(delta)",
            "dismissal.settle(velocityY, dismissDistancePx, dismissFlickPx, reduced, style)",
            "dismissal.offsetPx.roundToInt()",
            "if (dismissal.committed != null) return Velocity.Zero",
        )) assertTrue(host.contains(wiring), wiring)
        assertEquals(2, Regex(Regex.escape("if (dismissal.committed != null) return Offset.Zero")).findAll(host).count())
        assertFalse(host.contains("dismissOffset.snapTo"))
        assertFalse(host.contains("dismissOffset.animateTo"))
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queued = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queued.addLast(block) }
        fun drain() {
            var count = 0
            while (queued.isNotEmpty()) {
                check(count++ < 1000) { "queued coroutine work did not settle" }
                queued.removeFirst().run()
            }
        }
    }

    @Test
    fun currentAppliedChoicesFollowLockUnlockResizeAndHeldImportedPreferences() {
        var applied = RotationDetent.Presentation()
        fun travel(actualLandscape: Boolean, width: Int, height: Int): Int = SheetEntryPolicy.travelPx(
            RotationDetent.chromeLandscape(applied, actualLandscape), applied.uiPlacementLocked, width, height,
        )
        assertEquals(1080, travel(true, 2340, 1080))
        applied = RotationDetent.presentation(false, applied, false, true, 270, 1)
        assertEquals(2340, travel(true, 2340, 1080))
        assertEquals(2340, travel(false, 1080, 2340))
        assertEquals(800, travel(true, 800, 600))
        val held = applied
        applied = RotationDetent.presentation(true, applied, false, false, 270, 1)
        assertSame(held, applied)
        assertEquals(2340, travel(true, 2340, 1080))
        applied = RotationDetent.presentation(false, applied, false, false, 270, 1)
        assertEquals(1080, travel(true, 2340, 1080))
        assertEquals(600, travel(true, 800, 600))
        val heldFree = applied
        applied = RotationDetent.presentation(true, applied, false, true, 270, 1)
        assertSame(heldFree, applied)
        assertEquals(600, travel(true, 800, 600))
        applied = RotationDetent.presentation(false, applied, false, true, 270, 1)
        assertEquals(800, travel(true, 800, 600))
        // Policy inputs model imported choices. No preference transaction or Compose host runs here.
    }

    @Test
    fun oddSheetFramesSwapDimensionsAndMatchTheCurrentChromeFrameTravelAxis() {
        // Each row is an actual chrome-local width/height, not a requested Activity orientation.
        for ((width, height) in listOf(2340 to 1080, 1080 to 2340, 800 to 600, 600 to 800)) {
            val chromeLandscape = width > height
            for (uiLocked in listOf(false, true)) {
                val quadrants = if (uiLocked) 0..3 else 0..0
                for (sheetQuadrant in quadrants) {
                    val odd = sheetQuadrant % 2 != 0
                    val sheetWidth = if (odd) height else width
                    val sheetHeight = if (odd) width else height
                    val rootAxis = SheetEntryPolicy.travelPx(chromeLandscape, uiLocked, width, height)
                    val sheetAxis = SheetEntryPolicy.travelPx(
                        chromeLandscape != odd, uiLocked, sheetWidth, sheetHeight,
                    )
                    assertEquals(if (uiLocked) maxOf(width, height) else height, rootAxis)
                    assertEquals(rootAxis, sheetAxis, "${width}x$height ui=$uiLocked sheet=$sheetQuadrant")
                }
            }
        }
        // The source-only reachability check binds this axis equivalence to the retained begin callback.
    }

    @Test
    fun productionRevealPreservesImmediateDragPixelsWhenMeasuredTravelReplacesTheEstimate() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val reveal = PullRevealState(scope)
            val retained = reveal
            // Current locked-landscape width, before the newly opened sheet can be measured.
            val provisional = SheetEntryPolicy.travelPx(true, true, 2340, 1080) * 0.82f
            val measured = SheetEntryPolicy.travelPx(true, true, 1800, 900).toFloat()
            for ((drag, shouldOpen) in listOf(700f to false, 1100f to true)) {
                reveal.setTravelPx(provisional)
                reveal.begin(resetClosed = true)
                reveal.dragBy(drag)
                assertEquals(drag / provisional, reveal.progress, 0.0001f)
                reveal.setTravelPx(measured)
                assertSame(retained, reveal)
                assertEquals(drag / measured, reveal.progress, 0.0001f)
                var settled: Boolean? = null
                reveal.settleFromRelease(0f, 1000f, RoomStyle(motion = MotionFeel.Cut), false) { settled = it }
                assertEquals(shouldOpen, settled)
                assertEquals(if (shouldOpen) 1f else 0f, reveal.progress)
            }
        } finally {
            scope.cancel()
        }
        // Real PullRevealState and snap/cut coroutines, not Compose remember, measurement, or finger input.
    }
}
