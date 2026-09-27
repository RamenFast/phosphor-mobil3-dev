package dev.phosphor.mobil3.ui

import org.junit.Test
import org.junit.Assert.*

class LightChoicesTest {
    private val red = LightRgb(1f, 0f, 0f)
    private val blue = LightRgb(0f, 0f, 1f)
    private val green = LightRgb(0f, 1f, 0f)
    private val three = LightSettings(listOf(red, blue, green))

    @Test fun tapWearsOneSavedColorWhileCycleIsOff() {
        val worn = LightChoices.tapSaved(three, 1)
        assertEquals(listOf(1), worn.selected)
        assertEquals(LightChoices.Cycle.OFF, LightChoices.cycle(worn))
        assertEquals(listOf(2), LightChoices.tapSaved(worn, 2).selected)
    }

    @Test fun whileCyclingTapTogglesRingMembershipAndKeepsTwo() {
        val ring = LightChoices.setCycle(three, LightChoices.Cycle.TIMER)
        assertEquals(listOf(0, 1, 2), ring.selected)
        val two = LightChoices.tapSaved(ring, 2)
        assertEquals(listOf(0, 1), two.selected)
        assertEquals(two, LightChoices.tapSaved(two, 0))
        assertEquals(listOf(0, 1, 2), LightChoices.tapSaved(two, 2).selected)
    }

    @Test fun cycleChoiceMapsToTimerTrackAndOff() {
        val track = LightChoices.setCycle(three, LightChoices.Cycle.TRACK)
        assertTrue(track.perTrack)
        assertEquals(LightChoices.Cycle.TRACK, LightChoices.cycle(track))
        val off = LightChoices.setCycle(track, LightChoices.Cycle.OFF)
        assertEquals(listOf(0), off.selected)
        assertEquals(LightChoices.Cycle.OFF, LightChoices.cycle(off))
    }

    @Test fun cycleShowsWheneverItHasAnEffect() {
        assertFalse(LightChoices.cycleVisible(LightSettings(listOf(red))))
        assertTrue(LightChoices.cycleVisible(LightSettings(listOf(red, blue))))
        val generated = LightSettings(generatedAuto = true)
        assertTrue(LightChoices.cycleVisible(generated))
        assertEquals(listOf(LightChoices.Cycle.TIMER, LightChoices.Cycle.TRACK), LightChoices.cycleOptions(generated))
        assertEquals(LightChoices.Cycle.TIMER, LightChoices.cycle(generated))
    }

    @Test fun addSavesTheWornColorAndWearsIt() {
        val fromPreset = LightChoices.addCurrent(LightSettings(preset = 2))
        assertEquals(1, fromPreset.slots.size)
        assertEquals(listOf(0), fromPreset.selected)
        val c = BeamColors[2].color
        assertEquals(LightRgb(c.red, c.green, c.blue), fromPreset.slots[0])
        val ring = LightChoices.setCycle(three, LightChoices.Cycle.TIMER)
        assertEquals(listOf(0, 1, 2, 3), LightChoices.addCurrent(ring).selected)
    }

    @Test fun presetAndSavedWornStatesNeverOverlap() {
        val preset = LightSettings(listOf(red), preset = 4)
        assertTrue(LightChoices.presetWorn(preset, 4, temporary = false))
        assertFalse(LightChoices.presetWorn(preset, 4, temporary = true))
        val saved = LightChoices.tapSaved(preset, 0)
        assertFalse(LightChoices.presetWorn(saved, 4, temporary = false))
        assertTrue(LightChoices.savedWorn(saved, 0))
    }

    @Test fun timeRailIsLogarithmicAndRoundTrips() {
        assertEquals(0.1f, LightTime.fromSlider(0f), 1e-4f)
        assertEquals(60f, LightTime.fromSlider(1f), 1e-4f)
        for (s in listOf(0.5f, 1f, 3f, 12f, 45f)) assertEquals(s, LightTime.fromSlider(LightTime.toSlider(s)), s * 0.05f)
        assertTrue(LightTime.toSlider(1f) > 0.3f)
        assertEquals("3.0 s", LightTime.words(3f))
        assertEquals("12 s", LightTime.words(12f))
    }

    @Test fun colorWordsNameSwatchesForTalkBack() {
        assertEquals("red", ColorWords.name(1f, 0.05f, 0.05f))
        assertEquals("white", ColorWords.name(0.95f, 0.96f, 1f))
        assertEquals("black", ColorWords.name(0.02f, 0.02f, 0.02f))
        assertEquals("green", ColorWords.name(0.42f, 1f, 0.55f))
        assertEquals("deep blue", ColorWords.name(0f, 0f, 0.4f))
        assertEquals("pale pink", ColorWords.name(1f, 0.7f, 0.85f))
    }

    @Test fun timeStepsMoveToTheNextRepresentableValueEverywhere() {
        for (s in listOf(0.1f, 0.5f, 1f, 3f, 9.9f, 10f, 12f, 45f)) {
            val up = LightTime.next(s, up = true)
            assertTrue("$s up", up > s)
            val back = LightTime.next(up, up = false)
            assertEquals("$s round trip", s, back, 1e-4f)
            // On the rail too: a step never sticks at the same value.
            val rail = LightTime.stepOnRail(LightTime.toSlider(s), up = true)
            assertEquals("$s rail", up, LightTime.fromSlider(rail), 1e-4f)
        }
        assertEquals(0.1f, LightTime.next(0.1f, up = false), 0f)
        assertEquals(60f, LightTime.next(60f, up = true), 0f)
        assertEquals(0.5f, LightTime.fromSlider(LightTime.toSlider(0.5f)), 0f)
    }

    @Test fun plusSavesTheLiveColorWhenTheBeamIsMovingOrRolled() {
        val live = LightRgb(0.2f, 0.4f, 0.6f)
        val ring = LightChoices.setCycle(three, LightChoices.Cycle.TIMER)
        assertEquals(live, LightChoices.addCurrent(ring, live = live).slots.last())
        assertEquals(live, LightChoices.addCurrent(LightSettings(preset = 1), temporary = true, live = live).slots.last())
        val auto = LightChoices.addCurrent(LightSettings(generatedAuto = true), live = live)
        assertEquals(live, auto.slots.last())
        assertFalse(auto.generatedAuto)
        assertEquals(listOf(0), auto.selected)
        // A steady colour is copied exactly, never through the 8-bit readback.
        val steady = LightChoices.tapSaved(three, 1)
        assertEquals(blue, LightChoices.addCurrent(steady, live = live).slots.last())
        assertEquals(LightRgb(1f, 0f, 1f), LightChoices.rgbOf(0xff00ff))
    }

    @Test fun ownerLineOnlyWhenTheWornColorIsNotAVisibleSwatch() {
        assertNull(LightChoices.ownerLine(three, temporary = false))
        assertNotNull(LightChoices.ownerLine(three, temporary = true))
        assertNotNull(LightChoices.ownerLine(LightSettings(generatedAuto = true), temporary = false))
    }
}
