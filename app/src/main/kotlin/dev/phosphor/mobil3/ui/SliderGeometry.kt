package dev.phosphor.mobil3.ui

import kotlin.math.abs

/** The same inset track maps pointer coordinates and draws all four slider rules. */
internal class SliderGeometry(widthPx: Float, density: Float) {
    companion object {
        const val HIT_LANE_DP = 44
        const val TRACK_DP = 2
        const val THUMB_DP = 8
    }

    private val width = widthPx.coerceAtLeast(0f)
    val thumbPx = THUMB_DP * density
    val trackPx = TRACK_DP * density
    val start = (thumbPx / 2f).coerceAtMost(width / 2f)
    val end = width - start

    fun fractionAt(x: Float): Float =
        if (end <= start) 0f else ((x - start) / (end - start)).coerceIn(0f, 1f)

    fun xAt(fraction: Float): Float = start + fraction.coerceIn(0f, 1f) * (end - start)

    fun fraction(value: Float, min: Float, max: Float): Float =
        if (max <= min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)

    fun valueAt(x: Float, min: Float, max: Float): Float =
        min + fractionAt(x) * (max - min).coerceAtLeast(0f)

    fun nearestThumb(x: Float, lo: Float, hi: Float, min: Float, max: Float): Int =
        if (abs(x - xAt(fraction(lo, min, max))) <= abs(x - xAt(fraction(hi, min, max)))) 0 else 1

    fun moveThumb(thumb: Int, x: Float, lo: Float, hi: Float, min: Float, max: Float): Pair<Float, Float> {
        val value = valueAt(x, min, max)
        return if (thumb == 0) value.coerceIn(min, hi) to hi
        else lo to value.coerceIn(lo, max)
    }
}

internal fun sliderAccent(p: Palette) = p.liveAccent
