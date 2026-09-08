package dev.phosphor.mobil3.ui

import org.junit.Test
import org.junit.Assert.*

class LightSettingsTest {
    private val rgb = LightRgb(0f, 1f, 0.5f)
    @Test fun explicitPresetThemeForwardingFollowsSuccessfulPublicationOnly() {
        for (accepted in listOf(false, true)) {
            val events = mutableListOf<String>()
            applyPresetLight(LightSettings(listOf(rgb), 1, generatedAuto = true), 2, {
                assertEquals(2, it.preset)
                assertEquals(0, it.selectedMask)
                assertFalse(it.generatedAuto)
                assertEquals(listOf(rgb), it.slots)
                events += "publish"
                accepted
            }) { events += "remote-theme" }
            assertEquals(if (accepted) listOf("publish", "remote-theme") else listOf("publish"), events)
        }
    }
    @Test fun snapshotDoesNotAliasCallerStorage() {
        val source = mutableListOf(rgb)
        val light = LightSettings(source, 1)
        source.clear()
        assertEquals(1, light.slots.size)
        assertThrows(UnsupportedOperationException::class.java) { (light.slots as MutableList).clear() }
    }
    @Test fun guardCoversEveryTimerActivationWithCompletePendingReplacement() {
        val track = LightSettings(perTrack = true, seconds = 0.1f, intervalMin = 0.1f, intervalMax = 0.5f)
        assertNull(LightCycleGuard.evaluate(track, false).pending)
        assertNotNull(LightCycleGuard.evaluate(track.copy(perTrack = false), false).pending)
        val random = track.copy(perTrack = false, seconds = 3f, randomInterval = true)
        assertEquals(random, LightCycleGuard.evaluate(random, false).pending)
        assertNull(LightCycleGuard.evaluate(random.copy(randomInterval = false), false).pending)
        val newer = random.copy(preset = 2, selectedMask = 0, intervalMax = 4f)
        assertEquals(newer, LightCycleGuard.evaluate(newer, false).pending)
        assertEquals(4f, LightCycleGuard.evaluate(newer, false).safe.intervalMax)
    }
    @Test fun zeroOneSixAndIndependentSelection() {
        var s = LightSettings()
        assertTrue(s.slots.isEmpty())
        repeat(6) { s = s.add(rgb) }
        assertEquals(63, s.selectedMask)
        s = s.toggle(2)
        assertEquals(6, s.slots.size)
        assertEquals(59, s.selectedMask)
        s = s.delete(1)
        assertEquals(29, s.selectedMask)
        assertEquals(5, s.slots.size)
        s = s.preset(2)
        assertEquals(0, s.selectedMask)
        assertEquals(5, s.slots.size)
    }
    @Test fun legacyMigrationPreservesInactiveRgbAndPartialSixSlotBanks() {
        val bank = List(3) { rgb }.flatMap { it.components() }.joinToString(",")
        val old = LightSettings.read(mapOf("custom_count" to 0, "custom_rgb" to bank))
        assertEquals(3, old.slots.size)
        assertEquals(0, old.selectedMask)
        val six = LightSettings(List(6) { rgb }, 63)
        val partial = LightSettings.merge(six, mapOf("custom_count" to 2), true)
        assertEquals(6, partial.slots.size)
        assertEquals(3, partial.selectedMask)
        assertThrows(IllegalArgumentException::class.java) { LightSettings.merge(six, mapOf("custom_rgb" to bank), true) }
        assertEquals(3, LightSettings.merge(partial, mapOf("custom_rgb" to bank), true).slots.size)
        assertThrows(IllegalArgumentException::class.java) { LightSettings.read(mapOf("custom_count" to 1)) }
    }
    @Test fun invalidWholeTuplesAndFiniteBounds() {
        assertThrows(IllegalArgumentException::class.java) { LightSettings(selectedMask = 1) }
        assertThrows(IllegalArgumentException::class.java) { LightSettings(List(7) { rgb }) }
        assertThrows(IllegalArgumentException::class.java) { LightSettings(seconds = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { LightSettings(intervalMin = 7f, intervalMax = 6f) }
        assertThrows(IllegalArgumentException::class.java) { LightRgb(Float.POSITIVE_INFINITY, 0f, 0f) }
        assertThrows(IllegalArgumentException::class.java) { LightSettings.read(mapOf("custom_slot_count" to 0, "custom_rgb" to "0,0,0")) }
        for (n in 0..6) assertEquals(n, LightSettings.read(LightSettings(List(n) { rgb }).values()).slots.size)
    }
    @Test fun guardUsesEffectiveRandomMinimumAndRetainsCompleteIntent() {
        val requested = LightSettings(List(6) { rgb }, 63, seconds = 0.1f, randomInterval = true, intervalMin = 0.2f, intervalMax = 0.7f)
        val guard = LightCycleGuard.evaluate(requested, false)
        assertEquals(requested, guard.pending)
        assertEquals(1f, guard.safe.intervalMin)
        assertEquals(1f, guard.safe.intervalMax)
        assertEquals(6, guard.safe.slots.size)
        assertNull(LightCycleGuard.evaluate(requested.copy(perTrack = true), false).pending)
        assertEquals(requested, LightCycleGuard.evaluate(requested, true).safe)
    }
}
