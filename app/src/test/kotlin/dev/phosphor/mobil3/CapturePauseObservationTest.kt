package dev.phosphor.mobil3

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapturePauseObservationTest {
    @Test fun replacementPausedSnapshotDoesNotInheritOldPlayingBaseline() {
        val state = CapturePauseObservation<Any>()
        val a = Any(); val b = Any()
        state.bind(a)
        assertNull(state.observe(a, false))
        state.bind(null)
        assertNull(state.observe(a, true))
        state.bind(b)
        assertNull(state.observe(b, true))
        assertEquals(false, state.observe(b, false))
        assertEquals(true, state.observe(b, true))
    }

    @Test fun staleOwnerAndUnknownStatesCannotSeedOrChangeCurrentBaseline() {
        val state = CapturePauseObservation<Any>()
        val a = Any(); val b = Any()
        state.bind(b)
        assertNull(state.observe(a, false))
        assertNull(state.observe(null, false))
        assertNull(state.observe(b, null))
        assertNull(state.observe(b, true))
        assertNull(state.observe(a, false))
        assertNull(state.observe(b, true))
        assertEquals(false, state.observe(b, false))
        assertNull(state.observe(b, false))
    }

    @Test fun rebindingClearsOnlyObservationAndEmitsNoAction() {
        val state = CapturePauseObservation<Any>()
        val owner = Any()
        state.bind(owner)
        assertNull(state.observe(owner, false))
        assertEquals(true, state.observe(owner, true))
        state.bind(owner)
        assertNull(state.observe(owner, false))
        assertEquals(true, state.observe(owner, true))
    }

    @Test fun actualBindingAndCallbackUseTheTestedAdapterBeforeMetadata() {
        val service = File("src/main/kotlin/dev/phosphor/mobil3/PlaybackService.kt").readText()
        assertTrue("capturePause.bind(chosen)" in service && "capturePause.bind(null)" in service)
        val callback = service.substringAfter("override fun onPlaybackStateChanged(state: PlatformPlaybackState?)")
            .substringBefore("override fun onSessionDestroyed()")
        assertTrue(callback.indexOf("observeCapturePause(state)") in 0 until callback.indexOf("publishCaptureMetadata(chosen.metadata)"))
        assertTrue("captureActive && isCurrent()" in callback)
        assertTrue("capturePause.observe(externalCaptureController, paused)" in service)
        assertTrue("observeTransportPaused" !in service)
    }
}
