package dev.phosphor.mobil3

/** Source facts only. A session mirror, PiP, and background linger cannot grant wake ownership. */
internal object SourceWakePolicy {
    fun local(published: Boolean, playing: Boolean, ready: Boolean, failed: Boolean): Boolean =
        published && playing && ready && !failed

    fun remote(owned: Boolean, state: RemoteLinkState?): Boolean = owned &&
        (state == RemoteLinkState.STREAMING || state == RemoteLinkState.SILENT)

    fun capture(recording: Boolean, projection: Boolean): Boolean = recording && projection

    fun root(recording: Boolean, helper: Boolean): Boolean = recording && helper

    fun microphone(recording: Boolean, activityDestroyed: Boolean): Boolean = recording && !activityDestroyed

    fun visible(started: Boolean, sourceLive: Boolean): Boolean = started && sourceLive
}
