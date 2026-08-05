package dev.phosphor.mobil3

import dev.phosphor.mobil3.ui.RemoteEmptyStateText
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteEmptyStateTextTest {
    @Test
    fun emptyStatePromisesOnlyTheNetworkBoundaryTheStoreAccepts() {
        assertTrue(RemoteEmptyStateText.contains("Tailscale"))
        assertTrue(RemoteEmptyStateText.contains("tailnet", ignoreCase = true))
        assertFalse(RemoteEmptyStateText.contains("your LAN", ignoreCase = true))
        assertFalse(RemoteEmptyStateText.contains("local network", ignoreCase = true))
    }
}
