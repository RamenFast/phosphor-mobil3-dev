package dev.phosphor.mobil3

internal object PictureInPicturePolicy {
    const val KEY = "pip_auto_enter"
    const val DEFAULT = true

    fun autoEnter(settings: Map<String, *>): Boolean = settings[KEY] as? Boolean ?: DEFAULT
    fun platformAutoEnter(sdk: Int, autoEnter: Boolean): Boolean = sdk >= 31 && autoEnter
    fun enterOnLeave(sdk: Int, autoEnter: Boolean, alreadyInPip: Boolean): Boolean =
        sdk in 29..30 && autoEnter && !alreadyInPip
    fun enterManually(alreadyInPip: Boolean): Boolean = !alreadyInPip
}
