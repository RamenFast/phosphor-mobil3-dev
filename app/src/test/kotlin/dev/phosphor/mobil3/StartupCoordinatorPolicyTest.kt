package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.*

class StartupCoordinatorPolicyTest {
    @Test fun missingDefaultIsNoneAndDoesNotAutoStart() {
        assertEquals("none", StartupCoordinatorPolicy.defaultOf(emptyMap<String, Any>()))
        assertFalse(StartupCoordinatorPolicy.popup(emptyMap<String, Any>()))
        assertFalse(StartupCoordinatorPolicy.locallyConfirmed(emptyMap<String, Any>()))
        assertFalse(StartupCoordinatorPolicy.shouldAutoStart("none", false, false, true))
        assertFalse(StartupCoordinatorPolicy.promptFreeCapture())
    }
    @Test fun processDeathIsFreshAndRotationInSameProcessIsNot() {
        assertTrue(StartupCoordinatorPolicy.processFreshLaunch(alreadyConsumed = false))
        assertFalse(StartupCoordinatorPolicy.processFreshLaunch(alreadyConsumed = true))
    }
    @Test fun importedDefaultDoesNotAutoStartUntilLocalConfirm() {
        assertFalse(StartupCoordinatorPolicy.shouldAutoStart("mic", false, false, confirmed = false))
        assertTrue(StartupCoordinatorPolicy.shouldAutoStart("mic", false, false, confirmed = true))
        assertTrue(StartupCoordinatorPolicy.promptFreeMic(true))
        assertFalse(StartupCoordinatorPolicy.promptFreeMic(false))
    }
    @Test fun survivingOwnerOrLiveSourceBlocksRestart() {
        assertFalse(StartupCoordinatorPolicy.shouldAutoStart("mic", true, false, true))
        assertFalse(StartupCoordinatorPolicy.shouldAutoStart("capture", false, true, true))
        assertFalse(StartupCoordinatorPolicy.shouldAutoStart("file", false, false, true))
    }
}
