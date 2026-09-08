package dev.phosphor.mobil3.ui

import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.test.*

class GridDataTest {
    private fun stats(left: Double, right: Double, leftDb: Any, rightDb: Any) = JSONObject()
        .put("grid_data", JSONObject().put("left", left).put("right", right)
            .put("left_dbfs", leftDb).put("right_dbfs", rightDb))

    @Test fun defaultsOffAndHudOffStillRequestsStatsWhenGridDataIsOn() {
        assertFalse(GridData.DEFAULT)
        val state = ScopeUiState()
        assertFalse(state.gridData)
        assertNull(state.gridReading)
        state.hudMode = 2
        assertFalse(GridData.needsStats(state.hudMode, state.gridData))
        state.gridData = true
        assertTrue(GridData.needsStats(state.hudMode, state.gridData))
        state.gridReading = GridData.read(stats(0.5, 0.0, -6.0206, JSONObject.NULL))
        assertEquals("L · 0.500 · -6.0 dBFS", GridData.line(state.gridReading, left = true))
        assertEquals("R · 0.000 · −∞ dBFS", GridData.line(state.gridReading, left = false))
        for (hud in 0..1) assertTrue(GridData.needsStats(hud, false))
    }

    @Test fun rawChannelsAreIndependentAbsoluteAndNotAffectedByDisplayGain() {
        val state = ScopeUiState()
        state.gridReading = GridData.read(stats(0.0, 0.25, JSONObject.NULL, -12.0412))
        for (gain in listOf(0.1f, 1f, 6f, 7f)) {
            state.gain = gain
            assertEquals("L · 0.000 · −∞ dBFS", GridData.line(state.gridReading, left = true))
            assertEquals("R · 0.250 · -12.0 dBFS", GridData.line(state.gridReading, left = false))
        }
        state.gridReading = GridData.read(stats(2.0, 1.0, 6.0206, 0.0))
        assertEquals("L · 2.000 · 6.0 dBFS", GridData.line(state.gridReading, left = true))
        assertEquals("R · 1.000 · 0.0 dBFS", GridData.line(state.gridReading, left = false))
    }

    @Test fun noDataExpiredOrInvalidWindowCannotBeFormattedAsMeasuredSilence() {
        for (raw in listOf(null, JSONObject(), JSONObject().put("grid_data", JSONObject.NULL),
            stats(-0.1, 0.0, -20.0, JSONObject.NULL),
            stats(0.1, 0.0, JSONObject.NULL, JSONObject.NULL),
            JSONObject().put("grid_data", JSONObject().put("left", "NaN").put("right", 0.0)),
        )) {
            val reading = GridData.read(raw)
            assertNull(reading)
            assertEquals("L · no data", GridData.line(reading, left = true))
            assertEquals("R · no data", GridData.line(reading, left = false))
        }
        val measured = GridData.read(stats(0.0, 0.0, JSONObject.NULL, JSONObject.NULL))
        assertNotNull(measured)
        assertEquals("L · 0.000 · −∞ dBFS", GridData.line(measured, left = true))
        val state = ScopeUiState()
        state.gridReading = measured
        state.gridReading = GridData.read(null)
        assertEquals("R · no data", GridData.line(state.gridReading, left = false))
    }

    @Test fun numericLabelsDoNotFollowLocaleDecimalSeparators() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val reading = GridData.read(stats(0.5, 0.25, -6.0206, -12.0412))
            assertEquals("L · 0.500 · -6.0 dBFS", GridData.line(reading, left = true))
        } finally { Locale.setDefault(before) }
    }

    @Test fun actualHeartbeatSettingsAndVisibleBandAdaptersUseTheSameOwners() {
        // Source-string boundaries only. Compose drawing, Android preferences and JNI require root execution.
        val base = File("src/main/kotlin/dev/phosphor/mobil3")
        val activity = File(base, "MainActivity.kt").readText()
        val tick = activity.substringAfter("private val uiTick = object : Runnable")
            .substringBefore("// Moving chrome accents")
        assertEquals(1, Regex("PhosphorNative.scopeStats\\(\\)").findAll(tick).count())
        assertTrue(tick.contains("GridData.needsStats(ui.hudMode, ui.gridData)"))
        assertTrue(tick.indexOf("ui.gridReading =") < tick.indexOf("if (ui.hudMode != 2)"))
        val restore = activity.substringAfter("private fun restoreTuning(lightPublished: Boolean = false)")
        assertTrue(restore.contains("p.getBoolean(dev.phosphor.mobil3.ui.GridData.KEY, dev.phosphor.mobil3.ui.GridData.DEFAULT)"))
        val write = activity.substringAfter("override fun setGridData(on: Boolean)").substringBefore("override fun")
        assertTrue(write.contains("ui.gridData = on"))
        assertTrue(write.contains("putBoolean(dev.phosphor.mobil3.ui.GridData.KEY, on)"))
        assertTrue(activity.contains("putBoolean(dev.phosphor.mobil3.ui.GridData.KEY, ui.gridData)"))
        val imported = activity.substringAfter("private fun acceptSettingsArchive(")
            .substringBefore("imported ${'$'}{imported.values.size}")
        assertTrue(imported.contains("SettingsArchive.merge(decoded, prefs().all)"))
        assertTrue(imported.contains("imported.values.forEach"))
        assertTrue(imported.contains("restoreTuning(lightPublished)"))
        val sheets = File(base, "ui/Sheets.kt").readText()
        assertTrue(sheets.contains("GRID DATA · "))
        assertTrue(sheets.contains("actions.setGridData(!state.gridData)"))
        val screen = File(base, "ui/PhosphorScreen.kt").readText()
        assertTrue(screen.contains("override fun setGridData(on: Boolean) = actions.setGridData(on)"))
        assertTrue(screen.contains("if (state.bandMode == 0 || (state.bandMode == 1 && consoleShown))"))
        val band = File(base, "ui/Console.kt").readText().substringAfter("fun StatusBand(")
            .substringBefore("fun SeekRule(")
        val grid = band.substringAfter("if (state.gridData)").substringBefore("if (hudVisible")
        assertTrue(grid.contains("GridData.line(state.gridReading, left = true)"))
        assertTrue(grid.contains("GridData.line(state.gridReading, left = false)"))
        assertFalse(grid.contains("hudVisible &&"))
    }
}
