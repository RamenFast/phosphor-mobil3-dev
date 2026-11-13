package dev.phosphor.mobil3

import android.media.session.PlaybackState

internal object CaptureMirrorPolicy {
    const val OBSERVED_STATE = "phosphor.capture.observed_state"
    fun playing(state: Int): Boolean = state in PlaybackService.ACTIVE_PLATFORM_STATES

    fun available(state: Int): Boolean = state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_ERROR

    fun supports(state: Int, actions: Long, action: Long): Boolean =
        available(state) && actions and action != 0L

    fun seekable(state: Int, actions: Long, durationMs: Long): Boolean =
        available(state) && durationMs in 1..(Long.MAX_VALUE / 1000) &&
            actions and PlaybackState.ACTION_SEEK_TO != 0L

    fun displayedPlaying(capture: Boolean, isPlaying: Boolean, observedState: Int): Boolean =
        if (capture) playing(observedState) else isPlaying

    fun canPlayPause(state: Int, actions: Long): Boolean {
        val direct = if (playing(state)) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
        return available(state) && actions and (direct or PlaybackState.ACTION_PLAY_PAUSE) != 0L
    }

    fun routePlayPause(state: Int, actions: Long, play: Boolean, route: (Boolean) -> Unit) {
        if (play != playing(state) && canPlayPause(state, actions)) route(play)
    }

    fun <T : Any> attach(current: T?, capture: T, setPlayer: (T) -> Unit) {
        if (current !== capture) setPlayer(capture)
    }
}

/**
 * Which other app's media session capture mirrors: the one playing wins, then a busy one
 * (buffering, skipping), then one with metadata, then the one already mirrored. The
 * system's own order (most recent first) settles a full tie.
 */
internal object CaptureSessionChoice {
    data class Candidate(val state: Int, val hasMetadata: Boolean, val isCurrent: Boolean)

    /** 2 = playing, 1 = busy, 0 = idle. A session is re-chosen only when this changes. */
    fun rank(state: Int): Int = when {
        state == PlaybackState.STATE_PLAYING -> 2
        state in PlaybackService.ACTIVE_PLATFORM_STATES -> 1
        else -> 0
    }

    /** Index of the session to mirror, or -1 when there is none. */
    fun pick(candidates: List<Candidate>): Int {
        var best = -1
        for (index in candidates.indices) {
            if (best < 0 || ORDER.compare(candidates[index], candidates[best]) > 0) best = index
        }
        return best
    }

    private val ORDER = compareBy<Candidate>({ rank(it.state) }, { it.hasMetadata }, { it.isCurrent })
}

/** A queued platform callback loses authority even when the same controller is later rebound. */
internal class CaptureControllerBinding<T : Any> {
    var current: T? = null
        private set
    private var generation = 0L

    fun bind(controller: T?): () -> Boolean {
        current = controller
        val epoch = ++generation
        return { controller != null && current === controller && generation == epoch }
    }
}
