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

    @Test fun everyMicrophoneRowIsOneTap() {
        assertEquals(MicNames.Tap.STOP, MicNames.tap(active = true, chosen = true, micLive = true))
        assertEquals(MicNames.Tap.START, MicNames.tap(active = false, chosen = true, micLive = false))
        assertEquals(MicNames.Tap.MOVE, MicNames.tap(active = false, chosen = false, micLive = true))
        assertEquals(MicNames.Tap.CHOOSE_AND_START, MicNames.tap(active = false, chosen = false, micLive = false))
    }

    @Test fun retryAppearsOnlyOnFailure() {
        listOf("", "Microphone off", "Microphone stopped", "Microphone included", "Microphone starting",
            "Microphone active · built-in", "Bluetooth microphone not started").forEach { assertFalse(it, MicNames.failed(it)) }
        listOf("Microphone did not initialize, change the audio route and retry",
            "Selected microphone unavailable. Connect it or choose an input",
            "Microphone read failed (-3). Check permission and retry").forEach { assertTrue(it, MicNames.failed(it)) }
    }
}
