package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.appearance.AppearanceDocument
import org.junit.Test
import org.junit.Assert.*

class LookTilesTest {
    private fun tiles() = LookTiles.build(AppearanceDocument.of()) {
        AppearancePalette.legacy(LegacyAppearanceInput(it.id)).value
    }

    @Test fun curatedLooksLeadInBensOrder() {
        assertEquals(listOf("Glass", "AMOLED", "Dark", "Light"), tiles().take(4).map { it.label })
        assertTrue(tiles().take(4).all { it.kind == LookTiles.Kind.CURATED })
    }

    @Test fun everyRoomIsReachableOnceUnderADistinctName() {
        val all = tiles()
        val curatedValues = all.filter { it.kind == LookTiles.Kind.CURATED }.map { it.value }.toSet()
        for (room in Rooms) {
            val value = AppearancePalette.legacy(LegacyAppearanceInput(room.id)).value
            val shown = all.any { it.room?.id == room.id } || value in curatedValues
            assertTrue("room ${room.id} reachable", shown)
        }
        assertEquals(all.size, all.map { it.label.lowercase() }.toSet().size)
        assertFalse(all.any { it.label.contains("legacy", ignoreCase = true) })
    }

    @Test fun cornersMapToTheNearestNamedChoice() {
        assertEquals(0, LookTiles.corner(0))
        assertEquals(8, LookTiles.corner(7))
        assertEquals(12, LookTiles.corner(12))
        assertEquals(12, LookTiles.corner(20))
        assertEquals(listOf(0, 8, 12), LookTiles.cornerWords.map { it.first })
    }

    @Test fun activeFollowsTheCommittedIdOrTheDisplayedRoom() {
        val all = tiles()
        val glass = all.first { it.key == "curated:glass" }
        assertTrue(LookTiles.active(glass, AppearanceDocument.of(activeId = "curated:glass"), "x"))
        assertFalse(LookTiles.active(glass, AppearanceDocument.of(activeId = "curated:amoled"), "x"))
        val basalt = all.first { it.room?.id == "basalt" }
        assertTrue(LookTiles.active(basalt, null, "basalt"))
    }
}
