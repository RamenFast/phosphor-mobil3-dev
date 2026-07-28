package dev.phosphor.mobil3

import org.json.JSONObject

/**
 * What the relay link is actually doing, decided from the engine's own status.
 *
 * This exists as a pure function because the decision is the part worth testing and
 * `RemotePlayer` cannot be unit-tested (it needs an Android `Looper`). Keeping the
 * judgment here means the rules below are provable on the host, and the player is left
 * doing only what a player should: holding state and telling Media3 about it.
 *
 * The rules encode two corrections found by the 2026-07-28 connection-truth audit
 * (`docs/dev/receipts/phosphor-2.0/phase-B-remote-truth.md`):
 *
 *  1. The engine stores `ST_STREAMING` the instant the relay's welcome frame lands, before
 *     any media. Reporting that as a live link claims something that has not happened, so
 *     GREETED covers the window until a frame actually arrives.
 *  2. A stalled link (frames stopped, socket still open) used to be reported as
 *     reconnecting, which is a frozen live trace of the kind acceptance H-04 forbids.
 *     STALLED is now its own state.
 */
enum class RemoteLinkState {
    /** Dialing: TCP connect in flight. */
    CONNECTING,

    /** The relay greeted us. No frame has arrived, so nothing is flowing yet. */
    GREETED,

    /** Frames are arriving. This is the only state that means a live link. */
    STREAMING,

    /** Frames stopped while the socket stayed open. Not a reconnect. */
    STALLED,

    /** The link dropped and the engine is retrying on its backoff ladder. */
    RECONNECTING,

    /** Terminal for this attempt. Carries an error and a fix. */
    FAILED,
}

/** The parsed engine status, plus the failure text a user can act on. */
data class RemoteLinkReading(
    val state: RemoteLinkState,
    /** Combined `error - fix` text, empty unless the link failed. */
    val failure: String,
)

object RemoteLinkTruth {

    /** Frames of any kind prove the link is live. */
    private const val KEY_AUDIO_FRAMES = "rx_a"
    private const val KEY_GEOMETRY_FRAMES = "rx_g"
    private const val KEY_STATE = "state"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_ERROR = "error"
    private const val KEY_FIX = "fix"

    /**
     * Read one status document.
     *
     * [status] is the object returned by `PhosphorNative.remoteStatus()`. Unknown or
     * missing states resolve to CONNECTING rather than inventing a livelier answer,
     * because over-reporting health is the failure mode this whole audit was about.
     */
    fun read(status: JSONObject): RemoteLinkReading {
        val failure = failureText(status)
        return when (status.optString(KEY_STATE)) {
            "streaming" -> {
                // A VISUALIZER-only session is legitimately live with zero audio frames,
                // so geometry counts too. Either counter proves media is moving.
                val frames = status.optInt(KEY_AUDIO_FRAMES) + status.optInt(KEY_GEOMETRY_FRAMES)
                if (frames > 0) {
                    RemoteLinkReading(RemoteLinkState.STREAMING, failure)
                } else {
                    RemoteLinkReading(RemoteLinkState.GREETED, failure)
                }
            }
            "stalled" -> RemoteLinkReading(RemoteLinkState.STALLED, failure)
            "reconnecting" -> RemoteLinkReading(RemoteLinkState.RECONNECTING, failure)
            "failed" -> RemoteLinkReading(RemoteLinkState.FAILED, failure)
            else -> RemoteLinkReading(RemoteLinkState.CONNECTING, failure)
        }
    }

    /**
     * The engine and the relay both guarantee a `fix` beside every error. Joining them
     * here is what puts a remedy in front of the user instead of a bare failure.
     */
    fun failureText(status: JSONObject): String {
        val failure = status.optJSONObject(KEY_LAST_ERROR) ?: return ""
        val error = failure.optString(KEY_ERROR).orEmpty().trim()
        val fix = failure.optString(KEY_FIX).orEmpty().trim()
        return when {
            error.isEmpty() && fix.isEmpty() -> ""
            fix.isEmpty() -> error
            error.isEmpty() -> fix
            else -> "$error — $fix"
        }
    }
}
