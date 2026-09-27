package dev.phosphor.mobil3.ui

import org.junit.Test
import org.junit.Assert.*

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
}
