package dev.phosphor.mobil3.ui

internal object ControlsVisibilityPolicy {
    const val KEY = "controls_always_visible"
    const val DEFAULT = false

    fun alwaysVisible(settings: Map<String, *>): Boolean = settings[KEY] as? Boolean ?: DEFAULT
    fun visible(requested: Boolean, alwaysVisible: Boolean): Boolean = requested || alwaysVisible
    fun afterHide(alwaysVisible: Boolean): Boolean = alwaysVisible
    fun afterTap(visible: Boolean, alwaysVisible: Boolean): Boolean = !visible || alwaysVisible
    fun timeoutCanHide(alwaysVisible: Boolean, startedRevision: Long, currentRevision: Long): Boolean =
        !alwaysVisible && startedRevision == currentRevision
}
