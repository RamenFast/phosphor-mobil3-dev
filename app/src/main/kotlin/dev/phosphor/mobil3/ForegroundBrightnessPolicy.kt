package dev.phosphor.mobil3

/** Window requests only, never a panel-luminance measurement or source owner. */
internal object ForegroundBrightnessPolicy {
    const val KEY = "pin_screen_brightness"
    const val SYSTEM = -1f
    const val FULL = 1f
    fun requested(values: Map<String, *>) = values[KEY] as? Boolean ?: false

    data class WindowState(
        val started: Boolean = false,
        val resumed: Boolean = false,
        val focused: Boolean = false,
        val current: Boolean = false,
        val destroyed: Boolean = false,
        val pip: Boolean = false,
        val hud: Boolean = false,
    )

    fun active(requested: Boolean, state: WindowState) = requested && state.started &&
        state.resumed && state.focused && state.current && !state.destroyed && !state.pip && !state.hud

    fun brightness(active: Boolean) = if (active) FULL else SYSTEM
    fun awake(sourceAwake: Boolean, active: Boolean) = sourceAwake || active
}
