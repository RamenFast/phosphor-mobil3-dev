package dev.phosphor.mobil3

/** A new controller snapshot seeds state. Only that controller's later edges act. */
internal class CapturePauseObservation<T : Any> {
    private var owner: T? = null
    private var previous: Boolean? = null

    fun bind(next: T?) {
        owner = next
        previous = null
    }

    fun observe(from: T?, paused: Boolean?): Boolean? {
        if (owner == null || from !== owner || paused == null) return null
        val old = previous
        previous = paused
        return paused.takeIf { old != null && old != paused }
    }
}
