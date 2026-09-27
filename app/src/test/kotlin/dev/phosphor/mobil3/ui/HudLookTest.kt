package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.appearance.AppearanceContrast
import dev.phosphor.mobil3.settings.appearance.AppearanceDocument
import dev.phosphor.mobil3.settings.appearance.AppearanceDocumentCodec
import dev.phosphor.mobil3.settings.appearance.CuratedAppearances
import org.junit.Test
import org.junit.Assert.*

class HudLookTest {
    private fun rgb(argb: Int) = argb and 0xffffff

    @Test fun missingOrBrokenLooksFallBackToTheNeutralDark() {
        assertEquals(HudLook.NEUTRAL, HudLook.read(null))
        assertEquals(HudLook.NEUTRAL, HudLook.read(42))
        assertEquals(HudLook.NEUTRAL, HudLook.read("{not json"))
        assertEquals(HudLook.NEUTRAL, HudLook.from(null))
    }

    @Test fun theHudWearsTheStoredLookAndStaysReadable() {
        for (value in CuratedAppearances.all) {
            val encoded = AppearanceDocumentCodec.encode(AppearanceDocument.of(value, ""))
            val look = HudLook.read(encoded)
            assertEquals(value.colors.plane, rgb(look.plane))
            for (text in listOf(look.ink, look.ink2, look.accent)) {
                assertTrue(AppearanceContrast.ratio(rgb(text), rgb(look.plane)) >= 4.5)
            }
            assertTrue(AppearanceContrast.ratio(rgb(look.line), rgb(look.plane)) >= 3.0)
        }
        val light = HudLook.from(CuratedAppearances.light)
        assertNotEquals(HudLook.NEUTRAL.plane, light.plane)
    }
}
