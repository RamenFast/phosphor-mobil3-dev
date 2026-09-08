package dev.phosphor.mobil3.ui

import java.io.File
import org.junit.Test
import kotlin.test.*

class PauseDisplayPolicyTest {
    @Test fun missingAndMalformedLocalPreferencesDefaultToHold() {
        assertFalse(PauseDisplayPolicy.black(emptyMap<String, Any>()))
        for (value in listOf<Any>("HOLD", "black", "invalid", true, 1)) {
            assertFalse(PauseDisplayPolicy.black(mapOf("pause_display" to value)))
        }
        assertTrue(PauseDisplayPolicy.black(mapOf("pause_display" to "BLACK")))
    }

    @Test fun onlyUncontrollableLiveInputsUseDisplayOnlyControl() {
        for (live in listOf(false, true)) for (canPlay in listOf(false, true)) {
            assertEquals(live && !canPlay, PauseDisplayPolicy.displayOnly(live, canPlay))
        }
    }

    @Test fun transportLabelIgnoresIndependentDisplayPause() {
        for (paused in listOf(false, true)) {
            assertEquals("❚❚", PauseDisplayPolicy.controlLabel(false, true, paused))
            assertEquals("▶", PauseDisplayPolicy.controlLabel(false, false, paused))
        }
    }

    @Test fun displayOnlyLabelDoesNotPretendToPauseAudio() {
        for (playing in listOf(false, true)) {
            assertEquals("HOLD", PauseDisplayPolicy.controlLabel(true, playing, false))
            assertEquals("LIVE", PauseDisplayPolicy.controlLabel(true, playing, true))
        }
    }

    @Test fun statusRequiresObservedSourceActivityAndHandlesMissingHistory() {
        for (black in listOf(false, true)) for (frame in listOf(false, true)) {
            assertEquals("", PauseDisplayPolicy.status(false, black, frame, true))
            val expected = if (!frame) "no held frame" else if (black) "display black" else "display held"
            assertEquals(expected, PauseDisplayPolicy.status(true, black, frame, false))
            assertEquals("$expected · source live", PauseDisplayPolicy.status(true, black, frame, true))
        }
    }

    @Test fun actualUiStateDoesNotInferReaderActivityFromSelectedCapture() {
        val state = ScopeUiState()
        state.live = true
        state.captureCanPlay = false
        state.displayPaused = true
        state.heldFrameAvailable = true
        assertEquals("display held", state.pauseLabel)
        state.pauseSourceLive = true
        assertEquals("display held · source live", state.pauseLabel)
        state.pauseBlack = true
        assertEquals("display black · source live", state.pauseLabel)
        assertEquals(1.8332275f, state.gain)
    }

    @Test fun commonSourcePausePinsBeforeTransportAndDisplayOnlyReturnsBeforeController() {
        val base = File("src/main/kotlin/dev/phosphor/mobil3")
        val local = File(base, "PhosphorPlayer.kt").readText()
            .substringAfter("override fun handleSetPlayWhenReady(").substringBefore("override fun handle")
        assertTrue(local.indexOf("setDisplayPaused") in 0 until local.indexOf("deckSetPaused"))
        val remote = File(base, "RemotePlayer.kt").readText()
            .substringAfter("override fun handleSetPlayWhenReady(").substringBefore("override fun handle")
        assertTrue(remote.indexOf("setDisplayPaused") in 0 until remote.indexOf("remote"))
        val activity = File(base, "MainActivity.kt").readText()
            .substringAfter("override fun togglePlay()").substringBefore("override fun next()")
        assertTrue(activity.indexOf("toggleDisplayPause(); return") in 0 until activity.indexOf("val c = controller"))
    }
}
