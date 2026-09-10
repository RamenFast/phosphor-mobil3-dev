package dev.phosphor.mobil3

/** Configured default is not last-used source. Fresh launch is a new process task, not resume. */
internal object StartupCoordinatorPolicy {
    const val DEFAULT = "default_source"
    const val POPUP = "automatic_permission_popup"
    const val CONFIRMED = "default_source_confirmed"
    val allowed = setOf("none", "mic", "capture")
    fun defaultOf(values: Map<String, *>): String =
        (values[DEFAULT] as? String)?.takeIf { it in allowed } ?: "none"
    fun popup(values: Map<String, *>): Boolean = values[POPUP] as? Boolean ?: false
    fun locallyConfirmed(runtime: Map<String, *>): Boolean = runtime[CONFIRMED] as? Boolean ?: false
    fun processFreshLaunch(alreadyConsumed: Boolean): Boolean = !alreadyConsumed
    fun shouldAutoStart(
        default: String,
        alreadyLive: Boolean,
        survivingOwner: Boolean,
        confirmed: Boolean,
    ): Boolean = confirmed && default in setOf("mic", "capture") && !alreadyLive && !survivingOwner
    fun promptFreeMic(recordAudioGranted: Boolean): Boolean = recordAudioGranted
    fun promptFreeCapture(): Boolean = false
}
