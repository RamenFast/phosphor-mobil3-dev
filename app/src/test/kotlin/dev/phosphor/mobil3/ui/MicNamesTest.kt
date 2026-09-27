package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.MicrophoneChoice
import org.junit.Test
import org.junit.Assert.*

class MicNamesTest {
    @Test fun microphonesReadAsKindsNeverIds() {
        val names = MicNames.labels(listOf(
            MicrophoneChoice(19, 15, "ASUS_AI2202", displayTag = "19"),
            MicrophoneChoice(23, 15, "ASUS_AI2202", displayTag = "23"),
            MicrophoneChoice(40, 22, "Headset"),
            MicrophoneChoice(41, 7, "Buds"),
        ))
        assertEquals("built-in", names[19])
        assertEquals("built-in · second", names[23])
        assertEquals("USB headset", names[40])
        assertEquals("Bluetooth", names[41])
        assertTrue(names.values.none { it.any(Char::isDigit) })
    }

    @Test fun manyOfOneKindKeepCountingPlainly() {
        val names = MicNames.labels((1..4).map { MicrophoneChoice(it, 11, "USB") })
        assertEquals(listOf("USB", "USB · second", "USB · third", "USB · 4"), (1..4).map { names[it] })
    }
}
