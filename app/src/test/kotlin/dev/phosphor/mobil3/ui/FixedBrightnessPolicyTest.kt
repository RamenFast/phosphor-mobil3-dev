package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.SettingsArchive
import org.junit.Test
import java.io.File
import kotlin.test.*

class FixedBrightnessPolicyTest {
    @Test fun offFollowsThePhoneAndOnHoldsTheChosenLevel() {
        assertEquals(-1f, FixedBrightnessPolicy.windowValue(false, 0.8f))
        assertEquals(0.8f, FixedBrightnessPolicy.windowValue(true, 0.8f))
        assertEquals(1f, FixedBrightnessPolicy.windowValue(true, 1f))
        assertFalse(FixedBrightnessPolicy.DEFAULT_ON)
    }

    @Test fun aSavedLevelOutsideTheRangeIsBroughtInsideIt() {
        assertEquals(FixedBrightnessPolicy.MIN_LEVEL, FixedBrightnessPolicy.level(0f))
        assertEquals(FixedBrightnessPolicy.MIN_LEVEL, FixedBrightnessPolicy.level(-3f))
        assertEquals(1f, FixedBrightnessPolicy.level(7f))
        assertEquals(FixedBrightnessPolicy.DEFAULT_LEVEL, FixedBrightnessPolicy.level(Float.NaN))
        assertEquals(FixedBrightnessPolicy.DEFAULT_LEVEL, FixedBrightnessPolicy.level(Float.POSITIVE_INFINITY))
        assertEquals(FixedBrightnessPolicy.MIN_LEVEL, FixedBrightnessPolicy.windowValue(true, 0f))
    }

    @Test fun settingsExportCarriesBothKeysAndRejectsALevelOutsideTheRange() {
        val meta = listOf("dev.phosphor.mobil3", "2.0.0", "play", "2026-11-13T00:00:00Z")
        val ok = SettingsArchive.export(
            meta[0], meta[1], meta[2], meta[3],
            mapOf(FixedBrightnessPolicy.KEY_ON to true, FixedBrightnessPolicy.KEY_LEVEL to 0.4f),
        )
        assertTrue(ok.json.contains("\"fixed_brightness\""))
        assertTrue(ok.json.contains("\"fixed_brightness_level\""))
        for (bad in listOf<Any>(0f, 1.5f, "0.5")) {
            assertFailsWith<SettingsArchive.ArchiveException> {
                SettingsArchive.export(
                    meta[0], meta[1], meta[2], meta[3],
                    mapOf(FixedBrightnessPolicy.KEY_LEVEL to bad),
                )
            }
        }
    }

    @Test fun theActivityAppliesItOnLoadAndTheSliderTurnsItOn() {
        val activity = File("src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt").readText()
        val load = activity.substringAfter("ui.fixedBrightnessLevel = dev.phosphor.mobil3.ui.FixedBrightnessPolicy.level(")
        assertTrue(load.substringBefore("ui.viewLock").contains("applyFixedBrightness()"))
        val slider = activity.substringAfter("override fun setFixedBrightnessLevel(level: Float) {")
            .substringBefore("\n    }\n")
        assertTrue(slider.contains("if (!ui.fixedBrightness) {"))
        assertTrue(slider.contains("setFixedBrightness(true)"))
        assertTrue(activity.contains("attributes.screenBrightness = value"))
    }
}
