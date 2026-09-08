package dev.phosphor.mobil3.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.phosphor.mobil3.settings.appearance.CuratedAppearances
import org.junit.Assert.*
import org.junit.Test

class AppearancePaletteTest {
    @Test fun allThirteenActualPalettesRoundTripWithoutAuthoredColorOrStyleLoss() {
        assertEquals(13, Rooms.size)
        for (p in Rooms) {
            val migrated = AppearancePalette.legacy(LegacyAppearanceInput(room = p.id))
            assertEquals(p, AppearancePalette.palette(migrated.value, migrated.resolvedId, migrated.label))
            assertEquals(p.style, AppearancePalette.style(migrated.value))
            assertFalse(migrated.usedUnknownRoomFallback)
            assertEquals(Color.Unspecified, AppearancePalette.palette(migrated.value, p.id, p.label).beamAccent)
            assertEquals(p.line.toArgb(), migrated.value.colors.line)
            assertEquals(p.lineStrong.toArgb(), migrated.value.colors.lineStrong)
        }
    }

    @Test fun everyLegacyOverrideCombinationUsesTheActualCoupledResolver() {
        var checked = 0
        for (p in Rooms) for (character in listOf(null) + ChromeCharacter.entries)
            for (motion in listOf(null) + MotionFeel.entries) for (labels in listOf(null, false, true))
                for (radius in listOf(null) + (0..64).toList()) {
                    val old = StyleOverride(character, motion, radius, labels)
                    val raw = LegacyAppearanceInput(p.id, character?.ordinal, motion?.ordinal, radius,
                        labels?.let { if (it) 1 else 0 })
                    val migrated = AppearancePalette.legacy(raw)
                    assertEquals(raw, migrated.original)
                    assertEquals(p.style.overridden(old), AppearancePalette.style(migrated.value))
                    assertEquals(p, AppearancePalette.palette(migrated.value, p.id, p.label))
                    checked++
                }
        assertEquals(64350, checked)
    }

    @Test fun absentSentinelAndUnknownRoomKeepTheirOriginalIdentityAndFallback() {
        val absent = AppearancePalette.legacy(LegacyAppearanceInput())
        val sentinel = AppearancePalette.legacy(LegacyAppearanceInput("amoled", -1, -1, -1, -1))
        assertEquals("amoled", absent.resolvedId)
        assertEquals(absent.value, sentinel.value)
        assertNotEquals(absent.original, sentinel.original)
        val original = LegacyAppearanceInput("future_room", 99, -2, -9, 8)
        val unknown = AppearancePalette.legacy(original)
        assertEquals(original, unknown.original)
        assertTrue(unknown.usedUnknownRoomFallback)
        assertEquals("blossom_dark", unknown.resolvedId)
        assertEquals(BlossomDark, AppearancePalette.palette(unknown.value, unknown.resolvedId, unknown.label))
        assertEquals(CarvedStyle, AppearancePalette.style(unknown.value))
    }

    @Test fun invalidRadiusIsRejectedWithoutClampingTheOriginalRecord() {
        for (radius in listOf(65, Int.MAX_VALUE)) {
            val original = LegacyAppearanceInput("glass", radius = radius)
            try {
                AppearancePalette.legacy(original)
                fail("out-of-domain radius must not be silently changed")
            } catch (_: IllegalArgumentException) {
                assertEquals(radius, original.radius)
            }
        }
    }

    @Test fun legacyBeamFollowingDoesNotSaveTheCurrentSampledTint() {
        for (p in Rooms.filter { it.accentFollowsBeam }) {
            val first = AppearancePalette.legacy(LegacyAppearanceInput(p.id))
            val sampled = p.withBeam(floatArrayOf(1f, 0f, 0f))
            assertNotEquals(p.accent, sampled.accent)
            val again = AppearancePalette.legacy(LegacyAppearanceInput(p.id))
            assertEquals(first, again)
            assertEquals(p.accent.toArgb() and 0xffffff, again.value.colors.accent)
            assertEquals(Color.Unspecified, AppearancePalette.palette(again.value, p.id, p.label).beamAccent)
        }
    }

    @Test fun curatedStyleComesFromAuthoredValueNotTheLegacyIdentityTable() {
        for (value in CuratedAppearances.all) {
            val p = AppearancePalette.palette(value, "user:test", "Authored test")
            val style = AppearancePalette.style(value)
            assertEquals("user:test", p.id)
            assertEquals(value.colors.surface, p.surface.toArgb() and 0xffffff)
            assertEquals(value.densityScale, style.densityScale, 0f)
            assertEquals(value.panelAlphaScale, style.panelAlphaScale, 0f)
            assertEquals(value.radiusDp.dp, style.cornerRadius)
        }
        assertEquals(MotionFeel.Eased, AppearancePalette.style(CuratedAppearances.glass).motion)
        assertEquals(MotionFeel.Springy, AppearancePalette.style(AppearancePalette.legacy(LegacyAppearanceInput("glass")).value).motion)
    }
}
