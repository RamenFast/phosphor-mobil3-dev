package dev.phosphor.mobil3.ui

import org.junit.Test
import org.junit.Assert.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

class SheetPullPolicyTest {
    private val close = 96f
    private val flickMin = 48f
    private val flick = 920f

    @Test fun slowPullClosesOnlyPastTheCloseDistance() {
        assertFalse(SheetPullPolicy.closes(95f, 0f, close, flickMin, flick))
        assertTrue(SheetPullPolicy.closes(96f, 0f, close, flickMin, flick))
    }

    @Test fun flickClosesOnlyAfterRealTravel() {
        assertFalse(SheetPullPolicy.closes(20f, 5000f, close, flickMin, flick))
        assertTrue(SheetPullPolicy.closes(48f, 920f, close, flickMin, flick))
        assertFalse(SheetPullPolicy.closes(60f, 919f, close, flickMin, flick))
    }

    @Test fun resistanceSlowsTheCardButNeverReversesOrExceedsTheFinger() {
        assertEquals(0f, SheetPullPolicy.offset(0f, 240f), 0f)
        assertEquals(50f, SheetPullPolicy.offset(50f, 0f), 0f)
        var last = 0f
        for (raw in listOf(10f, 50f, 96f, 200f, 600f)) {
            val shown = SheetPullPolicy.offset(raw, 240f)
            assertTrue(shown > last)
            assertTrue(shown <= raw)
            last = shown
        }
        assertTrue(SheetPullPolicy.offset(100_000f, 240f) < 240f)
        assertEquals(0f, SheetPullPolicy.offset(-5f, 240f), 0f)
    }

    @Test fun fingerVelocityUsesTheLastHundredMilliseconds() {
        assertEquals(0f, SheetPullPolicy.velocity(emptyList()), 0f)
        assertEquals(0f, SheetPullPolicy.velocity(listOf(10L to 5f)), 0f)
        // 94 px in 60 ms is a real flick: ~1567 px/s.
        assertEquals(1566.7f, SheetPullPolicy.velocity(listOf(0L to 0f, 30L to 50f, 60L to 94f)), 1f)
        // Old, slow travel outside the window does not dilute the release speed.
        assertEquals(1000f, SheetPullPolicy.velocity(listOf(0L to 0f, 500L to 10f, 550L to 60f, 600L to 110f)), 1f)
    }

    @Test fun aCancelledPullNeverClosesWhateverItsTravelOrSpeed() {
        assertTrue(SheetPullPolicy.closes(500f, 5000f, close, flickMin, flick))
        assertFalse(SheetPullPolicy.closes(500f, 5000f, close, flickMin, flick, cancelled = true))
        assertFalse(SheetPullPolicy.closes(96f, 0f, close, flickMin, flick, cancelled = true))
    }

    @Test fun travelIsThePlainSumOfFingerDeltasAndReleaseSpeedAges() {
        val dispatcher = QueuedDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job() + dispatcher)
        val state = SheetDismissState(scope, resistancePx = 240f)
        state.begin()
        // Six 12 px finger deltas, 10 ms apart, while the card follows (resisted).
        for (i in 1..6) { state.dragByFinger(12f, i * 10L); dispatcher.drain() }
        assertEquals(72f, state.rawPx, 1e-3f)
        assertTrue(state.offsetPx < state.rawPx)
        // Released right away: a real flick (~1200 px/s).
        val copy = SheetDismissState(scope, resistancePx = 240f).apply {
            begin(); for (i in 1..6) dragByFinger(12f, i * 10L)
        }
        assertTrue(copy.releaseVelocity(60L) > 1000f)
        // Flick, hold still 300 ms, release: still.
        assertEquals(0f, state.releaseVelocity(360L), 0f)
    }

    @Test fun aCancelledStatePullReturnsHomeAndForgetsItsSpeed() {
        val dispatcher = QueuedDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job() + dispatcher)
        val state = SheetDismissState(scope, resistancePx = 240f)
        state.begin()
        for (i in 1..20) state.dragByFinger(20f, i * 5L)
        dispatcher.drain()
        var closed = false
        state.settle(0f, close, flick, reduced = true, style = RoomStyle(), flickMinPx = flickMin, cancelled = true) { closed = true }
        dispatcher.drain()
        assertFalse(closed)
        assertNull(state.committed)
        assertEquals(0f, state.rawPx, 0f)
        assertEquals(0f, state.offsetPx, 0f)
        assertEquals(0f, state.releaseVelocity(101L), 0f)
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
}
