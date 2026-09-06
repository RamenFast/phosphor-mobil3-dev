package dev.phosphor.mobil3.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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

    @Test
    fun gravityCardinalsMapToTheCorrectAndroidLandscapeQuadrants() {
        assertEquals(
            RotationDetent.ScreenTarget.PORTRAIT,
            RotationDetent.screenTarget(0),
        )
        assertEquals(
            RotationDetent.ScreenTarget.REVERSE_LANDSCAPE,
            RotationDetent.screenTarget(90),
        )
        assertEquals(
            RotationDetent.ScreenTarget.REVERSE_PORTRAIT,
            RotationDetent.screenTarget(180),
        )
        assertEquals(
            RotationDetent.ScreenTarget.LANDSCAPE,
            RotationDetent.screenTarget(270),
        )
        assertEquals(
            RotationDetent.ScreenTarget.UNSPECIFIED,
            RotationDetent.screenTarget(RotationDetent.NONE),
        )
    }

    @Test
    fun systemLockHoldsEveryAppliedPresentationDespiteNewAppChoicesAndGravity() {
        for (priorScope in listOf(false, true)) for (priorUi in listOf(false, true)) {
            for (priorCardinal in listOf(0, 90, 180, 270)) {
                val current = RotationDetent.presentation(
                    false, RotationDetent.Presentation(), priorScope, priorUi, priorCardinal, 0,
                )
                for (scope in listOf(false, true)) for (ui in listOf(false, true)) {
                    for (cardinal in listOf(RotationDetent.NONE, 0, 90, 180, 270)) {
                        for (display in 0..3) {
                            assertSame(current, RotationDetent.presentation(
                                true, current, scope, ui, cardinal, display,
                            ))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun permittedRoutingRetainsAllFourAppLockCombinations() {
        val current = RotationDetent.Presentation()
        assertEquals(current, RotationDetent.presentation(false, current, false, false, 90, 0))
        assertEquals(
            RotationDetent.Presentation(chromeQuadrant = 1),
            RotationDetent.presentation(false, current, true, false, 90, 0),
        )
        assertEquals(
            RotationDetent.Presentation(uiPlacementLocked = true, uprightQuadrant = 1, beamQuadrant = 1),
            RotationDetent.presentation(false, current, false, true, 90, 0),
        )
        assertEquals(
            RotationDetent.Presentation(uiPlacementLocked = true, uprightQuadrant = 1),
            RotationDetent.presentation(false, current, true, true, 90, 0),
        )
    }

    @Test
    fun systemUnlockUsesImportedChoicesWithoutAnotherGravityChange() {
        val current = RotationDetent.presentation(
            false, RotationDetent.Presentation(), true, false, 90, 0,
        )
        val held = RotationDetent.presentation(true, current, false, true, 90, 0)
        assertSame(current, held)
        assertEquals(
            RotationDetent.Presentation(uiPlacementLocked = true, uprightQuadrant = 1, beamQuadrant = 1),
            RotationDetent.presentation(false, held, false, true, 90, 0),
        )
        assertEquals(
            RotationDetent.Presentation(),
            RotationDetent.presentation(false, held, false, false, 90, 0),
        )
    }

    @Test
    fun unknownGravityDoesNotInventOrResetPresentation() {
        val current = RotationDetent.Presentation(uiPlacementLocked = true, uprightQuadrant = 3, beamQuadrant = 3)
        for (locked in listOf(false, true)) {
            for (scope in listOf(false, true)) for (ui in listOf(false, true)) {
                for (invalid in listOf(RotationDetent.NONE, -90, 45, 360)) {
                    assertSame(current, RotationDetent.presentation(locked, current, scope, ui, invalid, 0))
                }
            }
        }
    }

    @Test
    fun physicalCardinalAndObservedSurfaceTableRoutesEveryAppLockCombination() {
        // Columns are observed Surface D0/D1/D2/D3 on a portrait-natural display.
        // D0 pins the original sensor signs. Each aligned physical diagonal is zero.
        val rows = listOf(
            Triple("portrait", 0, listOf(0, 1, 2, 3)),
            Triple("reverse landscape", 90, listOf(1, 2, 3, 0)),
            Triple("reverse portrait", 180, listOf(2, 3, 0, 1)),
            Triple("landscape", 270, listOf(3, 0, 1, 2)),
        )
        val prior = RotationDetent.Presentation(true, 3, 2, 1)
        for ((physical, cardinal, relative) in rows) for (display in 0..3) {
            for (scope in listOf(false, true)) for (ui in listOf(false, true)) {
                val q = relative[display]
                val expected = when {
                    ui -> RotationDetent.Presentation(true, q, 0, if (scope) 0 else q)
                    scope -> RotationDetent.Presentation(chromeQuadrant = q)
                    else -> RotationDetent.Presentation()
                }
                assertEquals(expected, RotationDetent.presentation(
                    false, prior, scope, ui, cardinal, display,
                ), "$physical D$display scope=$scope ui=$ui")
            }
        }
    }

    @Test
    fun alignedPhysicalOrientationsActivelyClearNonzeroRotationInEveryLockMode() {
        val aligned = listOf(
            Triple(RotationDetent.ScreenTarget.PORTRAIT, 0, 0),
            Triple(RotationDetent.ScreenTarget.LANDSCAPE, 270, 1),
            Triple(RotationDetent.ScreenTarget.REVERSE_PORTRAIT, 180, 2),
            Triple(RotationDetent.ScreenTarget.REVERSE_LANDSCAPE, 90, 3),
        )
        val prior = RotationDetent.Presentation(true, 1, 2, 3)
        for ((physical, cardinal, display) in aligned) {
            assertEquals(physical, RotationDetent.screenTarget(cardinal))
            for (scope in listOf(false, true)) for (ui in listOf(false, true)) {
                assertEquals(RotationDetent.Presentation(uiPlacementLocked = ui),
                    RotationDetent.presentation(false, prior, scope, ui, cardinal, display),
                    "$physical scope=$scope ui=$ui must remove the previous rotation")
            }
        }
    }

    @Test
    fun freeActivityToEitherLandscapeLockDoesNotAddAHalfTurn() {
        for ((cardinal, display) in listOf(270 to 1, 90 to 3)) {
            val free = RotationDetent.presentation(
                false, RotationDetent.Presentation(true, 3, 2, 1), false, false, cardinal, display,
            )
            assertEquals(RotationDetent.Presentation(), free)
            for (scope in listOf(false, true)) for (ui in listOf(false, true)) {
                val locked = RotationDetent.presentation(false, free, scope, ui, cardinal, display)
                assertEquals(RotationDetent.Presentation(uiPlacementLocked = ui), locked)
                assertEquals(free, RotationDetent.presentation(false, locked, false, false, cardinal, display))
            }
        }
    }

    @Test
    fun stationaryLandscapeUnlockAppliesImportedChoicesAfterHoldingTheOldFrame() {
        // The display catches up while Android authority holds a previously pinned D0 frame.
        for ((cardinal, display, pinnedQ) in listOf(Triple(270, 1, 3), Triple(90, 3, 1))) {
            val pinned = RotationDetent.presentation(
                false, RotationDetent.Presentation(), true, false, cardinal, 0,
            )
            assertEquals(RotationDetent.Presentation(chromeQuadrant = pinnedQ), pinned)
            for (scope in listOf(false, true)) for (ui in listOf(false, true)) {
                val held = RotationDetent.presentation(true, pinned, scope, ui, cardinal, display)
                assertSame(pinned, held)
                assertSame(held, RotationDetent.presentation(
                    false, held, scope, ui, RotationDetent.NONE, display,
                ))
                assertEquals(RotationDetent.Presentation(uiPlacementLocked = ui),
                    RotationDetent.presentation(false, held, scope, ui, cardinal, display))
            }
        }
    }

    @Test
    fun resizedLayoutUsesActualGeometryEvenWithUiPlacementLocked() {
        val held = RotationDetent.Presentation(uiPlacementLocked = true, uprightQuadrant = 1, beamQuadrant = 1)
        assertFalse(RotationDetent.chromeLandscape(held, actualLandscape = false))
        assertTrue(RotationDetent.chromeLandscape(held, actualLandscape = true))
        for (quadrant in 0..3) for (landscape in listOf(false, true)) {
            assertEquals(
                landscape != (quadrant % 2 != 0),
                RotationDetent.chromeLandscape(RotationDetent.Presentation(chromeQuadrant = quadrant), landscape),
            )
        }
    }

    @Test
    fun systemAuthorityDoesNotChangeTheExactDetentTolerances() {
        assertEquals(18, RotationDetent.COMMIT_TOLERANCE)
        assertEquals(38, RotationDetent.HOLD_TOLERANCE)
        assertEquals(0, RotationDetent.next(0, 71))
        assertEquals(90, RotationDetent.next(0, 72))
        assertTrue(RotationDetent.shouldCommit(0, 38))
        assertFalse(RotationDetent.shouldCommit(0, 39))
    }

    private companion object {
        const val HOLD = RotationDetent.HOLD_TOLERANCE
    }
}
