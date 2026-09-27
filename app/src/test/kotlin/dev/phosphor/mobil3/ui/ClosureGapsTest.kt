package dev.phosphor.mobil3.ui

import org.junit.Test
import org.junit.Assert.*

/** Auditor AB1/AB2/AB4 closures: newest status, quantized accessibility steps, rolled colour. */
class ClosureGapsTest {
    @Test fun setupsShowTheNewestResultByWriteOrder() {
        val s = ScopeUiState()
        assertEquals("", s.instrumentNewestStatus)
        s.instrumentApplyStatus = "applied Clean XY"
        s.instrumentStatus = "Saved local authored setup."
        assertEquals("Saved local authored setup.", s.instrumentNewestStatus)
        s.instrumentApplyStatus = "applied Ambient"
        assertEquals("applied Ambient", s.instrumentNewestStatus)
        s.instrumentApplyStatus = ""
        assertEquals("Saved local authored setup.", s.instrumentNewestStatus)
        // Rewriting the same text is not a newer result.
        s.instrumentApplyStatus = "x"; s.instrumentStatus = "y"; s.instrumentApplyStatus = "x"
        assertEquals("y", s.instrumentNewestStatus)
    }

    @Test fun talkBackAdjustOnAQuantizedRailAlwaysMovesOneRepresentableValue() {
        for (seconds in listOf(0.1f, 0.5f, 9.9f, 12f)) {
            val position = LightTime.toSlider(seconds)
            var published = Float.NaN
            val action = SettingsRangeAction(position, 0f, 1f) { published = it }.stepping(LightTime::stepOnRail)
            // Compose's adjustable increment: a twentieth of the range from the current value.
            assertTrue(action.set(position + 1f / 20f))
            assertTrue("$seconds up", LightTime.fromSlider(published) > seconds)
            // A tiny increment that would round back still moves one step.
            assertTrue(action.set(position + 0.0001f))
            assertEquals(LightTime.next(seconds, true), LightTime.fromSlider(published), 1e-4f)
            if (seconds > LightTime.MIN) {
                assertTrue(action.set(position - 0.0001f))
                assertEquals(LightTime.next(seconds, false), LightTime.fromSlider(published), 1e-4f)
            }
        }
    }

    @Test fun aRolledColorNeverShowsASavedSquareAsWorn() {
        val l = LightSettings(listOf(LightRgb(1f, 0f, 0f)), selectedMask = 1)
        assertTrue(LightChoices.savedWorn(l, 0, temporary = false))
        assertFalse(LightChoices.savedWorn(l, 0, temporary = true))
    }

    @Test fun recallLineNamesTheWornSetupOnly() {
        assertNull(SetupsRecall.line(SetupsRecall.LOCAL))
        assertNull(SetupsRecall.line(""))
        assertEquals("wearing Clean XY · modified", SetupsRecall.line("Clean XY · modified"))
    }
}
