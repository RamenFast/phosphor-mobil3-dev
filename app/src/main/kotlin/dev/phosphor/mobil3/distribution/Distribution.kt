package dev.phosphor.mobil3.distribution

enum class Distribution(val wireName: String) {
    PLAY("play"),
    FORTRESS("fortress"),
}

/**
 * Compile-time distribution facts. "Compiled" means implementation is truly
 * present in this artifact, not merely intended for a later release phase.
 */
data class DistributionProfile(
    val distribution: Distribution,
    val fortressSourceSet: Boolean,
    val agentControlCompiled: Boolean,
    val privilegedAudioCompiled: Boolean,
    val transparentOverlayCompiled: Boolean,
    val privateEndpointSeedingAllowed: Boolean,
    val seededRemoteHosts: String,
)
