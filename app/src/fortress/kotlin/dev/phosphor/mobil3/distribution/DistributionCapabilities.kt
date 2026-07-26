package dev.phosphor.mobil3.distribution

import dev.phosphor.mobil3.BuildConfig

object DistributionCapabilities {
    val profile = DistributionProfile(
        distribution = Distribution.FORTRESS,
        fortressSourceSet = true,
        // Phase 02 creates the boundary only. Later phases flip these values
        // only when the corresponding implementation and evidence are present.
        agentControlCompiled = false,
        privilegedAudioCompiled = false,
        transparentOverlayCompiled = false,
        privateEndpointSeedingAllowed = true,
        seededRemoteHosts = BuildConfig.REMOTE_HOSTS,
    )
}
