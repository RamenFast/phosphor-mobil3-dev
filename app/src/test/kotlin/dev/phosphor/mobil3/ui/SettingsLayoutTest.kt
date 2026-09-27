package dev.phosphor.mobil3.ui

import org.junit.Test
import org.junit.Assert.*

class SettingsLayoutTest {
    @Test fun settingsIsOneFixedOrderOfGroupsWithDeveloperOnlyAfterUnlock() {
        assertEquals(listOf("SOUND & VIEW", "SCREEN", "PiP & BACKGROUND", "SOURCES", "SETUPS", "ABOUT"),
            SettingsGroup.visible(developer = false).map { it.title })
        assertEquals(SettingsGroup.DEVELOPER, SettingsGroup.visible(developer = true).last())
        assertEquals(7, SettingsGroup.visible(developer = true).size)
    }

    @Test fun choicesUseOneRowThenTwoColumnsThenOneWhicheverFirstFitsEveryLabel() {
        // 300px, 8px gaps, 32px cell chrome. Four cells hold 37px, two hold 114px.
        assertEquals(4, choiceColumns(300f, 8f, 32f, listOf(30f, 30f, 30f, 30f)))
        assertEquals(2, choiceColumns(300f, 8f, 32f, listOf(30f, 60f, 30f, 30f)))
        assertEquals(1, choiceColumns(300f, 8f, 32f, listOf(30f, 130f, 30f, 30f)))
        assertEquals(3, choiceColumns(300f, 8f, 32f, listOf(20f, 60f, 20f)))
        assertEquals(2, choiceColumns(300f, 8f, 32f, listOf(20f, 76f, 20f)))
        assertEquals(2, choiceColumns(300f, 8f, 32f, listOf(100f, 100f)))
        assertEquals(1, choiceColumns(300f, 8f, 32f, listOf(100f, 120f)))
        assertEquals(1, choiceColumns(300f, 8f, 32f, emptyList()))
    }

    @Test fun sheetsSplitIntoTwoColumnsOnlyInLandscapeWithRoom() {
        assertFalse(twoColumns(landscape = false, widthDp = 900f))
        assertFalse(twoColumns(landscape = true, widthDp = 520f))
        assertTrue(twoColumns(landscape = true, widthDp = 560f))
        assertTrue(twoColumns(landscape = true, widthDp = 820f))
    }
}
