package dev.phosphor.mobil3

/** Pure HDR/SDR decision. Dataspace readback and panel nits are not decided here. */
internal object HdrPresentationPolicy {
    const val KEY = "hdr_requested"
    fun requested(values: Map<String, *>): Boolean = values[KEY] as? Boolean ?: false
    fun attemptLinear(requested: Boolean, vulkan: Boolean, api: Int, hasFp16: Boolean): Boolean =
        requested && vulkan && api >= 34 && hasFp16
    fun reason(requested: Boolean, vulkan: Boolean, api: Int, hasFp16: Boolean): String = when {
        !requested -> "SDR · HDR off"
        !vulkan -> "SDR · non-Vulkan adapter"
        !hasFp16 -> "SDR · no FP16 scRGB pair"
        api < 34 -> "SDR · HDR metadata needs API 34"
        else -> "attempt linear HDR"
    }
    fun activeLabel(attempted: Boolean, dataspaceKnown: Boolean, matchingPresent: Boolean): String = when {
        !attempted -> "SDR"
        !dataspaceKnown || !matchingPresent -> "SDR · linear not proven"
        else -> "linear HDR configured"
    }
}
