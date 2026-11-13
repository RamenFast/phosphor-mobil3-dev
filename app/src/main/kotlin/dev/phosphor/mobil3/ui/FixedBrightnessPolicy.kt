package dev.phosphor.mobil3.ui

/**
 * Fixed brightness: hold the screen at a level the user chose while Phosphor is in front.
 * Android applies a window's brightness only while that window is shown, so the phone's own
 * setting (auto-brightness included) returns when the app leaves. No permission is involved.
 */
internal object FixedBrightnessPolicy {
    const val KEY_ON = "fixed_brightness"
    const val KEY_LEVEL = "fixed_brightness_level"
    const val DEFAULT_ON = false
    const val DEFAULT_LEVEL = 0.5f

    /** Zero asks for the dimmest the panel can do; keep a floor the controls stay readable at. */
    const val MIN_LEVEL = 0.01f
    const val MAX_LEVEL = 1f

    /** WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE: follow the phone's own setting. */
    const val FOLLOW_SYSTEM = -1f

    fun level(saved: Float): Float =
        if (saved.isFinite()) saved.coerceIn(MIN_LEVEL, MAX_LEVEL) else DEFAULT_LEVEL

    /** The value for the window's `screenBrightness`. */
    fun windowValue(on: Boolean, level: Float): Float = if (on) level(level) else FOLLOW_SYSTEM
}
