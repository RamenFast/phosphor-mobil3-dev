package dev.phosphor.mobil3.distribution

import dev.phosphor.mobil3.BuildConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DistributionCapabilitiesTest {
    @Test
    fun profileMatchesTheCompiledFlavorWithoutClaimingFutureCapabilities() {
        val profile = DistributionCapabilities.profile
        assertEquals(BuildConfig.DISTRIBUTION, profile.distribution.wireName)
        assertFalse(profile.agentControlCompiled)
        assertFalse(profile.privilegedAudioCompiled)
        assertFalse(profile.transparentOverlayCompiled)

        when (profile.distribution) {
            Distribution.PLAY -> {
                assertFalse(profile.fortressSourceSet)
                assertFalse(profile.privateEndpointSeedingAllowed)
                assertTrue(profile.seededRemoteHosts.isEmpty())
            }
            Distribution.FORTRESS -> {
                assertTrue(profile.fortressSourceSet)
                assertTrue(profile.privateEndpointSeedingAllowed)
            }
        }
    }
}
