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

    @Test fun choicesStackOnlyWhenAMeasuredLabelWouldNotFitItsCell() {
        // 300px row, 8px gaps, 32px cell chrome: three cells hold 62.67px of text each.
        assertFalse(choicesStack(300f, 8f, 32f, listOf(20f, 60f, 20f)))
        assertTrue(choicesStack(300f, 8f, 32f, listOf(20f, 76f, 20f)))
        assertFalse(choicesStack(300f, 8f, 32f, listOf(100f, 100f)))
        assertTrue(choicesStack(300f, 8f, 32f, listOf(100f, 120f)))
        assertFalse(choicesStack(300f, 8f, 32f, emptyList()))
    }
}
