package dev.phosphor.mobil3.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rotation detent, stated as how the phone should feel in the hand.
 *
 * Ben's ask was "you have to really rotate it and then it's set" — a physical detent,
 * explicitly not a time delay, because a delay makes a correct turn feel laggy.
 */
class RotationDetentTest {

    @Test
    fun theFirstReadingTakesWhateverOrientationThePhoneIsIn() {
        // Nothing committed yet, so there is nothing to resist.
        assertTrue(RotationDetent.shouldCommit(RotationDetent.NONE, 0))
        assertEquals(90, RotationDetent.next(RotationDetent.NONE, 92))
    }

    @Test
    fun aSmallTiltDoesNotFlipTheChrome() {
        // The bug being fixed: a symmetric window commits the instant the phone crosses
        // 45°, so a wrist tilt reoriented the whole UI mid-use.
        assertEquals(0, RotationDetent.next(committed = 0, degrees = 44))
        assertEquals(0, RotationDetent.next(committed = 0, degrees = 50))
        assertEquals(0, RotationDetent.next(committed = 0, degrees = 60))
    }

    @Test
    fun aDeliberateTurnLandsImmediatelyWithNoWaiting() {
        // The other half: once the phone is genuinely turned, the chrome follows at
        // once. No dwell timer, so a correct turn never feels laggy.
        assertEquals(90, RotationDetent.next(committed = 0, degrees = 85))
        assertEquals(90, RotationDetent.next(committed = 0, degrees = 90))
        assertEquals(180, RotationDetent.next(committed = 90, degrees = 178))
    }

    @Test
    fun anUntidyHandHeldAngleKeepsTheOrientationItAlreadyHas() {
        // Nobody holds a phone at exactly 0°. Staying put must tolerate real hands.
        (-HOLD..HOLD).forEach { off ->
            val degrees = (off + 360) % 360
            assertEquals(
                0, RotationDetent.next(committed = 0, degrees = degrees),
                "drifting $off° from upright should hold",
            )
        }
    }

    @Test
    fun leavingIsHarderThanStayingWhichIsTheWholeDetent() {
        // The asymmetry IS the feature. At the same distance from a cardinal, holding
        // succeeds and switching does not.
        val justInsideHold = 30
        assertTrue(RotationDetent.shouldCommit(committed = 0, degrees = justInsideHold))
        // Same 30° offset, but now measured against a cardinal we are NOT committed to.
        assertFalse(RotationDetent.shouldCommit(committed = 0, degrees = 90 - justInsideHold))
    }

    @Test
    fun theTwoTolerancesCannotOverlapIntoAmbiguity() {
        // Both must stay under 45°, or a single angle could satisfy two cardinals and
        // the orientation would depend on sample order.
        assertTrue(RotationDetent.COMMIT_TOLERANCE < 45)
        assertTrue(RotationDetent.HOLD_TOLERANCE < 45)
        assertTrue(
            RotationDetent.COMMIT_TOLERANCE < RotationDetent.HOLD_TOLERANCE,
            "committing must be stricter than holding, or there is no detent",
        )
    }

    @Test
    fun theDetentSurvivesWrappingPastZero() {
        // 350° is upright. Modular arithmetic near the wrap is where this kind of code
        // usually breaks.
        assertEquals(0, RotationDetent.next(committed = 0, degrees = 350))
        assertEquals(0, RotationDetent.next(committed = 0, degrees = 10))
        assertEquals(270, RotationDetent.next(committed = 270, degrees = 300))
    }

    @Test
    fun aSlowSweepCommitsOnceRatherThanFlickering() {
        // Sweeping upright to landscape must produce exactly one change, at a decisive
        // angle. Two changes would mean the chrome hunted on the way round.
        var committed = 0
        var changes = 0
        (0..90).forEach { degrees ->
            val next = RotationDetent.next(committed, degrees)
            if (next != committed) changes++
            committed = next
        }
        assertEquals(1, changes, "a single sweep should commit exactly once")
        assertEquals(90, committed)
    }

    @Test
    fun aWobbleAroundTheBoundaryDoesNotOscillate() {
        // Hovering near 45° is the worst case for a symmetric rule: it would flip back
        // and forth every sample. The detent must sit still.
        var committed = 0
        listOf(40, 48, 43, 50, 44, 47).forEach { degrees ->
            committed = RotationDetent.next(committed, degrees)
        }
        assertEquals(0, committed, "a wobble near the boundary must not reorient")
    }

    private companion object {
        const val HOLD = RotationDetent.HOLD_TOLERANCE
    }
}
