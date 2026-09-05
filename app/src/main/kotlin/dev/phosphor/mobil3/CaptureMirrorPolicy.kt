package dev.phosphor.mobil3

import android.media.session.PlaybackState

internal object CaptureMirrorPolicy {
    fun playing(state: Int): Boolean = state in PlaybackService.ACTIVE_PLATFORM_STATES

    fun available(state: Int): Boolean = state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_ERROR

    fun supports(state: Int, actions: Long, action: Long): Boolean =
        available(state) && actions and action != 0L

    fun seekable(state: Int, actions: Long, durationMs: Long): Boolean =
        available(state) && durationMs in 1..(Long.MAX_VALUE / 1000) &&
            actions and PlaybackState.ACTION_SEEK_TO != 0L

    fun displayedPlaying(capture: Boolean, isPlaying: Boolean, playWhenReady: Boolean): Boolean =
        if (capture) playWhenReady else isPlaying

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
