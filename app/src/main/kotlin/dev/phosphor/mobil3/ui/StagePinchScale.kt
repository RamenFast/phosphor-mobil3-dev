package dev.phosphor.mobil3.ui

import kotlin.math.abs

/** Keep sub-threshold distance until it becomes deliberate movement. */
internal class StagePinchScale {
    private var reference = 0f

    fun reset(distance: Float = 0f) {
        reference = distance.takeIf { it.isFinite() && it > 0f } ?: 0f
    }

    fun sample(distance: Float): Float? {
        if (!distance.isFinite() || distance <= 0f) {
            reset()
            return null
        }
        if (reference <= 0f) {
            reset(distance)
            return null
        }
        val scale = distance / reference
        if (abs(scale - 1f) <= 0.001f) return null
        reference = distance
        return scale
    }
}
