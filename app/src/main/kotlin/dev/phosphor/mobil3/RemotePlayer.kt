package dev.phosphor.mobil3

import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject

// The remote (Tailscale bridge) deck's Player face. The transport law, remote edition:
// while this player sits in the MediaSession, every control surface — console keys,
// notification, lock screen, earbuds, Bluetooth — drives the SOURCE MACHINE's player
// over the bridge. The phone's own Spotify is never touched.
//
// SimpleBasePlayer trap, documented: seekToNext/Previous on a single-item playlist are
// silently ignored (BasePlayer resolves them against the timeline before handleSeek can
// run). The GHOST PLAYLIST [ghost-prev, now, ghost-next] with the live item at index 1
// makes next/prev resolvable; handleSeek maps them onto bridge transport and the state
// snaps back to index 1 on the next invalidate.
class RemotePlayer(looper: Looper) : SimpleBasePlayer(looper) {

    // The service owns process-network binding; STOP must return through it so a
    // remote transport stop cannot strand a process-wide route.
    var onStopRequested: (() -> Unit)? = null

    enum class Conn { IDLE, CONNECTING, STREAMING, FAILED, LOST }

    private var conn = Conn.IDLE
    private var error: PlaybackException? = null
    private var host: String = ""
    private var playing = false // last known SOURCE-machine truth (M frames)
    private var title: String? = null
    private var artist: String? = null
    private var album: String? = null
    private var art: ByteArray? = null
    private var artId: String? = null
    private var trackKey: String? = null
    // v2 relay fields; absent on v1 → seek/progress degrade away cleanly.
    private var positionMs: Long = C.TIME_UNSET
    private var positionAtMs: Long = 0
    private var durationMs: Long = C.TIME_UNSET
    private var canSeek = false

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .apply {
                if (canSeek && durationMs != C.TIME_UNSET) {
                    addAll(
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                    )
                }
            }
            .build()
        val playback = when (conn) {
            Conn.IDLE -> Player.STATE_IDLE
            Conn.CONNECTING, Conn.LOST -> Player.STATE_BUFFERING
            Conn.STREAMING -> Player.STATE_READY
            Conn.FAILED -> Player.STATE_IDLE
        }
        val b = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (conn == Conn.IDLE) Player.STATE_IDLE else playback)
            .setPlayerError(error)
            .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE) // prev = previous, never restart
        if (conn != Conn.IDLE) {
            b.setPlaylist(listOf(ghost("remote:prev"), nowItem(), ghost("remote:next")))
            b.setCurrentMediaItemIndex(1)
            if (positionMs != C.TIME_UNSET) {
                b.setContentPositionMs {
                    if (playing) positionMs + (SystemClock.elapsedRealtime() - positionAtMs)
                    else positionMs
                }
            }
        }
        return b.build()
    }

    private fun ghost(id: String): MediaItemData =
        MediaItemData.Builder(id)
            .setMediaItem(MediaItem.Builder().setMediaId(id).build())
            .build()

    private fun nowItem(): MediaItemData {
        val label = when (conn) {
            Conn.CONNECTING -> "connecting · $host"
            Conn.LOST -> "reconnecting · $host"
            Conn.FAILED -> "bridge unreachable · $host"
            else -> title ?: "remote · $host"
        }
        val mm = MediaMetadata.Builder()
            .setTitle(label)
            .setArtist(if (conn == Conn.STREAMING) artist else null)
            .setAlbumTitle(if (conn == Conn.STREAMING) album else null)
            .setExtras(Bundle().apply {
                putString("source", "remote")
                putString("conn", conn.name)
                putString("host", host)
            })
            .apply {
                art?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            }
            .build()
        return MediaItemData.Builder("remote:now")
            .setMediaItem(MediaItem.Builder().setMediaId("remote:now").setMediaMetadata(mm).build())
            .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1000)
            .setIsSeekable(canSeek)
            .build()
    }

    // ── Service-driven lifecycle (all on the player looper) ──
    fun onConnecting(host: String) {
        this.host = host
        conn = Conn.CONNECTING
        playing = true // intent to play; Media3 promotes FGS on BUFFERING+playWhenReady
        error = null
        title = null; artist = null; album = null; art = null
        artId = null; trackKey = null
        positionMs = C.TIME_UNSET; durationMs = C.TIME_UNSET; canSeek = false
        invalidateState()
    }

    fun onConnected() {
        if (conn != Conn.STREAMING) {
            conn = Conn.STREAMING
            invalidateState()
        }
    }

    fun onConnectionLost() {
        if (conn == Conn.STREAMING || conn == Conn.CONNECTING) {
            conn = Conn.LOST
            invalidateState()
        }
    }

    fun onConnectFailed(message: String) {
        conn = Conn.FAILED
        playing = false
        error = PlaybackException(
            message, null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )
        invalidateState()
    }

    fun reset() {
        conn = Conn.IDLE
        playing = false
        error = null
        title = null; artist = null; album = null; art = null
        artId = null; trackKey = null
        positionMs = C.TIME_UNSET; durationMs = C.TIME_UNSET; canSeek = false
        invalidateState()
    }

    /** Feed an M frame; returns the content-addressed art id when fresh bytes are needed. */
    fun onMeta(m: JSONObject): String? {
        val nextTitle = if (m.isNull("title")) null else m.optString("title").ifBlank { null }
        val nextArtist = if (m.isNull("artist")) null else m.optString("artist").ifBlank { null }
        val nextAlbum = if (m.isNull("album")) null else m.optString("album").ifBlank { null }
        val nextArtId = if (m.isNull("art_id")) null else m.optString("art_id").ifBlank { null }
        // Playing/position change every tick; these stable fields identify the track. Drive
        // files use path, while MPRIS sources fall back to their resolved metadata + art id.
        val nextTrackKey = listOf(
            m.optString("source"),
            if (m.isNull("path")) "" else m.optString("path"),
            nextTitle.orEmpty(),
            nextArtist.orEmpty(),
            nextAlbum.orEmpty(),
            nextArtId.orEmpty(),
        ).joinToString("\u0000")
        val trackChanged = nextTrackKey != trackKey
        if (trackChanged) art = null
        title = nextTitle
        artist = nextArtist
        album = nextAlbum
        artId = nextArtId
        trackKey = nextTrackKey
        if (m.has("playing")) playing = m.optBoolean("playing")
        if (m.has("position_ms")) {
            positionMs = m.optLong("position_ms")
            positionAtMs = SystemClock.elapsedRealtime()
        }
        durationMs = if (m.has("duration_ms") && !m.isNull("duration_ms")) {
            m.optLong("duration_ms")
        } else C.TIME_UNSET
        canSeek = m.optBoolean("can_seek", false)
        if (conn == Conn.CONNECTING || conn == Conn.LOST) conn = Conn.STREAMING
        invalidateState()
        return if (trackChanged) nextArtId else null
    }

    fun onArt(id: String, bytes: ByteArray?) {
        // R replies may overtake a newer M frame. Only the current track's content id may
        // populate its MediaMetadata; a missing/empty reply clears rather than resurrects.
        if (id.isNotBlank() && id == artId) {
            art = bytes?.takeIf { it.isNotEmpty() }
            invalidateState()
        }
    }

    fun lastKnownPlaying(): Boolean = playing

    // ── Transport → the bridge ──
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        // playpause is a TOGGLE on the wire: only send when intent differs from the last
        // known source-machine state, then trust the next M frame to reconcile.
        if (playWhenReady != playing) {
            PhosphorNative.remoteTransport(if (playWhenReady) "play" else "pause")
            playing = playWhenReady // optimistic; M reconciles ≤1 s
        }
        // Instant local silence ahead of the ~300 ms bridge round-trip.
        PhosphorNative.remoteSetMuted(!playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                PhosphorNative.remoteTransport("next")
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                PhosphorNative.remoteTransport("prev")
            else -> if (canSeek) {
                PhosphorNative.remoteSeekMs(positionMs)
                this.positionMs = positionMs
                positionAtMs = SystemClock.elapsedRealtime()
            }
        }
        // The framework's placeholder may flash a ghost index; invalidate snaps to 1.
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        onStopRequested?.invoke() ?: run {
            PhosphorNative.remoteDisconnect()
            reset()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        PhosphorNative.remoteDisconnect()
        return Futures.immediateVoidFuture()
    }
}
