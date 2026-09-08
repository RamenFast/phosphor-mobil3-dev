package dev.phosphor.mobil3.ui

/** Display intent never stands in for observed audio transport. */
object PauseDisplayPolicy {
    fun black(values: Map<String, *>) = values["pause_display"] == "BLACK"

    fun displayOnly(liveInput: Boolean, captureCanPlay: Boolean) = liveInput && !captureCanPlay

    fun controlLabel(displayOnly: Boolean, playing: Boolean, paused: Boolean): String =
        if (displayOnly) { if (paused) "LIVE" else "HOLD" } else if (playing) "❚❚" else "▶"

    fun status(paused: Boolean, black: Boolean, hasFrame: Boolean, sourceLive: Boolean): String {
        if (!paused) return ""
        val image = if (!hasFrame) "no held frame" else if (black) "display black" else "display held"
        return image + if (sourceLive) " · source live" else ""
    }
}
