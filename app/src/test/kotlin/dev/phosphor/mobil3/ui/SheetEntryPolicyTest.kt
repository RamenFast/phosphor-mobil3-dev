package dev.phosphor.mobil3.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where sheets enter from, stated as what the user should see.
 *
 * Ben reported this twice in one message as two separate complaints, which is the tell
 * that one rule was wrong in two directions: the card must come out of the transport bar
 * the finger touched, and where that bar sits depends on the placement lock.
 */
class SheetEntryPolicyTest {

    @Test
    fun portraitAlwaysRisesFromTheBottomWhereTheConsoleIs() {
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
}
