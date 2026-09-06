package dev.phosphor.mobil3.ui

import kotlin.test.assertEquals
import org.junit.Test

class ScopeUiStateTest {
    @Test fun hudDefaultsAutoAndCyclesWithoutAnAuthorityStore() {
        val state = ScopeUiState()

        assertEquals(1, state.hudMode)
        assertEquals(2, nextHudMode(state.hudMode))
        assertEquals(1, nextHudMode(0))
        assertEquals(2, nextHudMode(1))
        assertEquals(0, nextHudMode(2))
    }

    @Test fun invalidHudInputsAreNormalizedBeforeCycling() {
        assertEquals(0, nextHudMode(-10))
        assertEquals(0, nextHudMode(99))
    }
}
