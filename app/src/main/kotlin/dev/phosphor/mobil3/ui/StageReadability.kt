package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.appearance.AppearancePresentationPolicy

internal object StageReadability {
    const val PLOT_RGB = 0
    fun plotInk(opaqueRgb: Int): Int = AppearancePresentationPolicy.foreground(
        opaqueRgb or 0xff000000.toInt(), PLOT_RGB, 4.5)
    fun stackStatus(availablePx: Int, leftPx: Int, rightPx: Int, gapPx: Int): Boolean =
        leftPx.toLong() + rightPx + gapPx > availablePx
    fun signalTopDp(bandVisible: Boolean, bandHeightDp: Float): Float =
        if (bandVisible) maxOf(88f, bandHeightDp + 8f) else 88f

    fun legendHeight(minimum: Int, width: Int, mainWidth: Int, mainHeight: Int,
        tagWidth: Int, tagHeight: Int, inset: Int, gap: Int, padding: Int): Int {
        val ordinary = maxOf(minimum, mainHeight + 2 * padding)
        val left = (width - mainWidth) / 2
        return if (tagWidth > 0 && inset + tagWidth > left)
            maxOf(ordinary, 2 * (inset + tagHeight + gap) + mainHeight) else ordinary
    }
}
