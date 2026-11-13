package dev.phosphor.mobil3.ui

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ScopeModelTest {
    @Test fun shippedFacesKeepTheirEngineIndices() {
        assertEquals(listOf(0, 1, 6, 7, 8), ScopeModeIds)
        assertEquals(listOf("xy", "xy45", "wave", "ring", "spec"), ScopeModes.map { it.tag })
    }

    @Test fun removedOrUnknownStoredModeFallsBackToGoniometer() {
        for (gone in listOf(2, 3, 4, 5, 9, 10, -1, 99)) assertEquals(1, keptModeOrDefault(gone))
        for (kept in ScopeModeIds) assertEquals(kept, keptModeOrDefault(kept))
    }

    @Test fun stepWalksTheShippedListAndWraps() {
        assertEquals(listOf(1, 6, 7, 8, 0), ScopeModeIds.map { stepMode(it, 1) })
        assertEquals(listOf(8, 0, 1, 6, 7), ScopeModeIds.map { stepMode(it, -1) })
        assertEquals(6, stepMode(4, 1))
    }

    @Test fun bansKeepOnlyShippedFacesAndNeverStarveTheDie() {
        assertEquals(setOf(0, 7), keptBans(listOf(0, 3, 7, 10)))
        assertEquals(emptySet(), keptBans(listOf(0, 1, 6, 7)))
    }

    @Test fun rollIsAShippedFaceOtherThanCurrentAndNotBanned() {
        repeat(200) {
            val rolled = rollModeExcluding(1, setOf(0, 6))
            assertTrue(rolled in setOf(7, 8), "rolled $rolled")
        }
        repeat(50) { assertTrue(rollModeExcluding(4) in ScopeModeIds - 1) }
    }
}
