package dev.phosphor.mobil3.ui

import org.junit.Test
import org.junit.Assert.*

/** Ben's settings changed under Prime's test drags: a scroll or pull is never also a tap. */
class SheetGestureGuardTest {
    @Test fun aTapWithoutMovementRuns() {
        val guard = SheetGestureGuard()
        var taps = 0
        guard.down()
        guard.tap { taps++ }()
        assertEquals(1, taps)
        assertTrue(guard.allowsTap())
    }

    @Test fun aGestureThatScrolledOrPulledNeverTaps() {
        val guard = SheetGestureGuard()
        var toggled = false
        guard.down()
        guard.markMoved() // content scrolled, or the card followed the finger
        guard.tap { toggled = true }()
        assertFalse(toggled)
        assertFalse(guard.allowsTap())
    }

    @Test fun theNextDownStartsAFreshGesture() {
        val guard = SheetGestureGuard()
        guard.down(); guard.markMoved()
        guard.down()
        var taps = 0
        guard.tap { taps++ }()
        assertEquals(1, taps)
    }

    @Test fun keyboardAndTalkBackActivateAfterATouchScrollEnds() {
        val guard = SheetGestureGuard()
        guard.down(); guard.markMoved()
        var during = 0
        guard.tap { during++ }()          // the scroll's own UP: suppressed
        assertEquals(0, during)
        guard.up()                        // the sequence ends (Final pass of the last UP)
        var semantic = 0
        guard.tap { semantic++ }()        // a later semantic onClick has no DOWN of its own
        assertEquals(1, semantic)
    }

    @Test fun outsideASheetEverythingTaps() {
        val none: SheetGestureGuard? = null
        assertTrue(none.allowsTap())
    }

    @Test fun skipOnRandomKeepsAtLeastTwoFacesInPlay() {
        var banned = emptySet<Int>()
        for (i in 0 until 11) banned = ModeBans.toggle(banned, i, 11)
        assertEquals(9, banned.size)
        assertEquals(banned, ModeBans.toggle(banned, 10, 11))
        assertEquals(8, ModeBans.toggle(banned, 0, 11).size)
    }
}
