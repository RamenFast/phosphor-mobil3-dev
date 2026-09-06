package dev.phosphor.mobil3

import org.json.JSONObject

/**
 * Maps native relay status to user-facing link states without Android dependencies.
 * GREETED is not live until a media frame arrives, and STALLED is distinct from reconnecting.
 */
enum class RemoteLinkState {
    /** Dialing: TCP connect in flight. */
    CONNECTING,

    /** The relay greeted us. No valid media has arrived in this session. */
    GREETED,

    /** Valid current-session media is arriving. SILENT is also live. */
    STREAMING,

    /**
     * Frames are arriving and carrying silence.
     *
     * Reported only when the relay tells us its loudness. Against an older relay the
     * state stays STREAMING, because "we cannot tell" must not be dressed up as a
     * measurement.
     */
    SILENT,

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

    /** Native session receipt and freshness, never process-lifetime diagnostics. */
    private const val KEY_MEDIA_RECEIVED = "media_received"
    private const val KEY_MEDIA_LIVE = "media_live"
    private const val KEY_STATE = "state"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_ERROR = "error"
    private const val KEY_FIX = "fix"
    private const val KEY_REMOTE_RMS = "remote_rms"

    /**
     * Below this the window is silence rather than quiet music.
     *
     * Full-scale audio reads 1.0 and ordinary listening sits well above 0.001, while a
     * muted or paused source reads exactly 0.0. The threshold sits just off zero so
     * dither and a noise floor do not register as sound.
     */
    private const val SILENCE_RMS = 0.0005

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
                val received = status.optBoolean(KEY_MEDIA_RECEIVED, false)
                if (received && status.optBoolean(KEY_MEDIA_LIVE, false)) {
                    // Receipt and freshness already prove flow. Optional relay loudness
                    // labels silence without becoming wake authority. isNull() preserves
                    // the difference between unknown loudness and a measured zero.
                    val silent = !status.isNull(KEY_REMOTE_RMS) &&
                        status.optDouble(KEY_REMOTE_RMS, -1.0) in 0.0..SILENCE_RMS
                    if (silent) {
                        RemoteLinkReading(RemoteLinkState.SILENT, failure)
                    } else {
                        RemoteLinkReading(RemoteLinkState.STREAMING, failure)
                    }
                } else if (received) {
                    RemoteLinkReading(RemoteLinkState.STALLED, failure)
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
