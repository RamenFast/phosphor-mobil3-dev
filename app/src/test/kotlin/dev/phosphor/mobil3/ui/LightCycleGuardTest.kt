package dev.phosphor.mobil3.ui

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LightCycleGuardTest {
    @Test fun trackToFastTimerPublishesSafeStateBeforeWarning() {
        val state = ScopeUiState().apply {
            cycleSeconds = 0.1f
            cyclePerTrack = true
        }
        val events = mutableListOf<String>()
        applyGuardedCycle(state, state.cycleSeconds, false, false, { seconds, track ->
            assertEquals(1f, seconds)
            assertEquals(seconds, state.cycleSeconds)
            assertFalse(track)
            assertFalse(state.cyclePerTrack)
            events += "safe-native"
        }) { pending ->
            assertEquals(0.1f, pending)
            events += "warning"
        }
        assertEquals(listOf("safe-native", "warning"), events)
        assertEquals(1f, state.cycleSeconds)
    }

    @Test fun trackKeepsTheLegalMinimumWithoutWarning() {
        val state = ScopeUiState()
        applyGuardedCycle(state, 0.1f, true, false, { seconds, track ->
            assertEquals(0.1f, seconds)
            assertTrue(track)
        }) { error("TRACK must not request acknowledgment") }
        assertEquals(0.1f, state.cycleSeconds)
        assertTrue(state.cyclePerTrack)
    }

    @Test fun acknowledgedTimerKeepsTheRequestedMinimum() {
        val state = ScopeUiState()
        applyGuardedCycle(state, 0.1f, false, true, { seconds, track ->
            assertEquals(0.1f, seconds)
            assertFalse(track)
        }) { error("Acknowledged TIMER must not warn again") }
        assertEquals(0.1f, state.cycleSeconds)
    }

    @Test fun oneSecondAndMaximumDoNotNeedAcknowledgment() {
        for (seconds in listOf(1f, 3f, 60f)) {
            val state = ScopeUiState()
            applyGuardedCycle(state, seconds, false, false, { actual, track ->
                assertEquals(seconds, actual)
                assertFalse(track)
            }) { error("Safe TIMER must not warn") }
            assertEquals(seconds, state.cycleSeconds)
        }
    }

    @Test fun unacknowledgedSliderRequestAlsoUpdatesNativeSafeValue() {
        val state = ScopeUiState().apply { cycleSeconds = 60f }
        val published = mutableListOf<Float>()
        applyGuardedCycle(state, 0.9f, false, false, { seconds, _ ->
            published += seconds
        }) { assertEquals(0.9f, it) }
        assertEquals(listOf(1f), published)
        assertEquals(1f, state.cycleSeconds)
    }
}
