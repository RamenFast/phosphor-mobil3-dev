package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.appearance.AppearanceColors
import dev.phosphor.mobil3.settings.appearance.AppearanceContrast
import dev.phosphor.mobil3.settings.appearance.AppearanceValue
import kotlin.math.roundToInt

/** Opaque, authored-only presentation. No live beam sample or saved color mutation. */
internal data class ConsoleKeyColors(
    val face: Int, val ink: Int, val disabledInk: Int, val edge: Int,
    val high: Int, val low: Int, val accent: Int,
)

internal data class ConsoleTactileTokens(
    val well: Int, val edgeQuiet: Int, val focusRing: Int,
    val raised: ConsoleKeyColors, val sunk: ConsoleKeyColors,
) {
    companion object {
        fun from(value: AppearanceValue): ConsoleTactileTokens = with(value.colors) {
            val blackField = value.dark && plane == 0 && surface == 0
            val face = if (blackField) 0x141414 else stone
            val well = if (value.dark) plane else toward(face, 0, 1.2)
            val sunk = if (blackField) 0 else blend(face, well, .55)
            ConsoleTactileTokens(well, readable(ink, well, 3.0),
                if (AppearanceContrast.ratio(accent, well) >= 4.6) accent else readable(accent, well, 4.6),
                key(face, this), key(sunk, this))
        }

        private fun key(face: Int, c: AppearanceColors) = ConsoleKeyColors(face,
            if (AppearanceContrast.ratio(c.ink, face) >= 4.5) c.ink else readable(c.ink, face, 4.5),
            readable(c.muted, face, 3.0), readable(c.ink, face, 4.5),
            toward(face, 0xffffff, 4.5), toward(face, 0, 1.6), readable(c.accent, face, 3.0))

        /** Nearest sRGB blend to the floor. Extreme authored ink gets a local endpoint fallback. */
        private fun readable(ink: Int, background: Int, floor: Double): Int {
            val endpoint = if (AppearanceContrast.ratio(ink, background) >= floor) ink else
                listOf(0, 0xffffff).maxBy { AppearanceContrast.ratio(it, background) }
            return toward(background, endpoint, floor)
        }

        private fun toward(background: Int, endpoint: Int, floor: Double): Int {
            for (step in 0..255) {
                val color = blend(background, endpoint, step / 255.0)
                if (AppearanceContrast.ratio(color, background) >= floor) return color
            }
            return endpoint
        }

        private fun blend(a: Int, b: Int, fraction: Double): Int {
            fun channel(shift: Int): Int = (((a ushr shift) and 255) * (1 - fraction) +
                ((b ushr shift) and 255) * fraction).roundToInt().coerceIn(0, 255)
            return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}

internal object ConsoleKeybedPolicy {
    data class Layout(val rows: Int, val primaryWidth: Float, val modeWidth: Float, val sourceWidth: Float,
        val overflowWidth: Float = 48f)

    fun tactile(lookVersion: Int, hasTransport: Boolean) = lookVersion == 2

    /** Width is the actual inner well, in Android display dp, not the screen width. */
    fun layout(widthDp: Float, fontScale: Float, displayOnly: Boolean = false,
        hasTransport: Boolean = false): Layout {
        fun label(chars: Int) = kotlin.math.ceil(20f + chars * 9f * fontScale)
        val primary = if (displayOnly) maxOf(56f, label(4)) else 56f
        val mode = maxOf(64f, label(4))
        val source = maxOf(56f, label(3))
        val skip = if (hasTransport) 112f else 0f
        val rows = when {
            fontScale >= 1.8f || widthDp < mode + source + 48f + 16f -> 4
            widthDp < primary + skip + mode + source + 48f + 28f -> 2
            else -> 1
        }
        return when (rows) {
            4 -> Layout(4, widthDp, widthDp, widthDp, widthDp)
            2 -> Layout(2, widthDp, mode, source)
            else -> Layout(1, primary, mode, source)
        }
    }
}
