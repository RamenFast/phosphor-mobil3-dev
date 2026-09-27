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
        val main = all.filter { it.kind != LookTiles.Kind.CLASSIC }
        val classic = all.filter { it.kind == LookTiles.Kind.CLASSIC }
        assertEquals(main.size, main.map { it.label.lowercase() }.toSet().size)
        assertEquals(classic.size, classic.map { it.label.lowercase() }.toSet().size)
        assertFalse(all.any { it.label.contains("legacy", ignoreCase = true) || it.label.contains("classic", ignoreCase = true) })
        // Classic versions of the curated four sit together at the end, never among the main tiles.
        assertTrue(classic.all { it.room?.id in LookTiles.classicIds })
        assertEquals(classic, all.takeLast(classic.size))
        assertTrue(main.none { it.room?.id in LookTiles.classicIds })
    }

    @Test fun cornersMapToTheNearestNamedChoice() {
        assertEquals(0, LookTiles.corner(0))
        assertEquals(8, LookTiles.corner(7))
        assertEquals(12, LookTiles.corner(12))
        assertEquals(12, LookTiles.corner(20))
        assertEquals(listOf(0, 8, 12), LookTiles.cornerWords.map { it.first })
    }

    @Test fun exactlyOneTileIsMarkedAndACustomLookGetsACurrentTile() {
        val all = tiles()
        assertEquals("curated:glass", LookTiles.activeKey(all, AppearanceDocument.of(
            dev.phosphor.mobil3.settings.appearance.CuratedAppearances.glass, "curated:glass"), "x"))
        // Values equal to a curated look mark that look even under another id.
        assertEquals("curated:amoled", LookTiles.activeKey(all, AppearanceDocument.of(activeId = ""), "x"))
        // A custom look matches no tile: the sheet adds one "current" tile.
        val custom = dev.phosphor.mobil3.settings.appearance.CuratedAppearances.glass.copy(radiusDp = 5)
        val doc = AppearanceDocument.of(custom, activeId = "")
        assertNull(LookTiles.activeKey(all, doc, "x"))
        assertEquals(LookTiles.CURRENT_KEY, LookTiles.current(doc)?.key)
        // The displayed room marks its tile when nothing else does.
        val basalt = all.first { it.room?.id == "basalt" }
        assertEquals(basalt.key, LookTiles.activeKey(all, null, "basalt"))
    }

    @Test fun aSavedLookNamedLikeABuiltInOneIsDisambiguated() {
        val doc = AppearanceDocument.of(users = listOf(
            dev.phosphor.mobil3.settings.appearance.AppearanceRecord("3f2b8c1e-4d5a-4b6c-8e9f-0a1b2c3d4e5f", "Glass",
                dev.phosphor.mobil3.settings.appearance.CuratedAppearances.glass.copy(radiusDp = 3))))
        val all = LookTiles.build(doc) { AppearancePalette.legacy(LegacyAppearanceInput(it.id)).value }
        assertEquals("Glass · saved", all.first { it.key == "3f2b8c1e-4d5a-4b6c-8e9f-0a1b2c3d4e5f" }.label)
    }
}
