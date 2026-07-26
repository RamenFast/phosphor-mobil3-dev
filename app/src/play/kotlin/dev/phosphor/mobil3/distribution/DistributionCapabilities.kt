package dev.phosphor.mobil3.distribution

import dev.phosphor.mobil3.BuildConfig

object DistributionCapabilities {
    val profile = DistributionProfile(
        distribution = Distribution.PLAY,
        fortressSourceSet = false,
        agentControlCompiled = false,
        privilegedAudioCompiled = false,
        transparentOverlayCompiled = false,
        privateEndpointSeedingAllowed = false,
        seededRemoteHosts = BuildConfig.REMOTE_HOSTS,
    )
}
