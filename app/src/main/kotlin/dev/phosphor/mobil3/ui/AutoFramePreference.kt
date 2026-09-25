package dev.phosphor.mobil3.ui

/** One size scale for AUTO and manual gain. Must match rust `engine::GAIN_MAX`. */
object GainScale {
    const val MIN = 0.1f
    const val MAX = 64f
    fun clamp(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN, MAX) else 1f
}

/** Size words and a logarithmic slider position for the shared gain scale. No raw multipliers. */
object GainWords {
    private val lo = kotlin.math.ln(GainScale.MIN)
    private val hi = kotlin.math.ln(GainScale.MAX)
    fun toSlider(gain: Float): Float = ((kotlin.math.ln(GainScale.clamp(gain)) - lo) / (hi - lo)).coerceIn(0f, 1f)
    fun fromSlider(position: Float): Float =
        GainScale.clamp(kotlin.math.exp(lo + position.coerceIn(0f, 1f) * (hi - lo)))
    /** A short readable multiplier: ×0.25, ×1.5, ×12. */
    fun multiplier(value: Float): String = when {
        !value.isFinite() -> "×1"
        value < 1f -> "×" + String.format(java.util.Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
        value < 10f -> "×" + String.format(java.util.Locale.ROOT, "%.1f", value).removeSuffix(".0")
        else -> "×" + value.toInt()
    }
    fun word(gain: Float): String = when {
        gain < 0.7f -> "far"
        gain < 3f -> "normal"
        gain < 12f -> "close"
        else -> "very close"
    }
}

/** One global "closer / farther" multiplier around AUTO's choice. Must match rust AUTO_FRAME_*. */
object AutoFramePreference {
    const val KEY = "auto_frame_scale"
    const val DEFAULT = 1f
    const val MIN = 0.25f
    const val MAX = 4f

    fun normalize(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN, MAX) else DEFAULT

    fun read(values: Map<String, *>): Float {
        val value = values[KEY] as? Float ?: return DEFAULT
        return value.takeIf { it.isFinite() && it in MIN..MAX } ?: DEFAULT
    }
}

internal enum class StageZoomOwner { MANUAL_GAIN, AUTO_FRAME }

internal data class StageZoomAdjustment(
    val owner: StageZoomOwner,
    val value: Float,
    val crossedNeutral: Boolean,
)

/** Pure mapping shared by pinch and vertical one-finger zoom after gesture ownership is settled. */
internal object StageZoomPolicy {
    fun adjust(manualGain: Float, autoFrameScale: Float, autoFrameArmed: Boolean, factor: Float): StageZoomAdjustment {
        val owner = if (autoFrameArmed) StageZoomOwner.AUTO_FRAME else StageZoomOwner.MANUAL_GAIN
        val old = if (autoFrameArmed) AutoFramePreference.normalize(autoFrameScale)
            else GainScale.clamp(manualGain)
        val next = if (!factor.isFinite() || factor <= 0f) old else if (autoFrameArmed) {
            (old * factor).coerceIn(AutoFramePreference.MIN, AutoFramePreference.MAX)
        } else {
            GainScale.clamp(old * factor)
        }
        return StageZoomAdjustment(owner, next, (old - 1f) * (next - 1f) <= 0f && old != next)
    }
}

/** Keep the newest live edit pending until the real preference write confirms it. */
internal class AutoFrameSave {
    var pending: Float? = null
        private set
    fun stage(value: Float) { pending = AutoFramePreference.normalize(value) }
    fun flush(write: (Float) -> Boolean): Boolean {
        val value = pending ?: return true
        val saved = runCatching { write(value) }.getOrDefault(false)
        if (saved) pending = null
        return saved
    }
    fun restored() { pending = null }
}
