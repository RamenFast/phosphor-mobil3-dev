package dev.phosphor.mobil3

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject

// The Media3 bridge: the Rust deck is the engine, this is its Player face.
// The loaded deck owns the transport (the v4.7.0 law) — this player IS the loaded deck.
class PhosphorPlayer(looper: Looper) : SimpleBasePlayer(looper) {

    private var playing = false
    private var item: MediaItemData? = null

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
        val b = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (item == null) Player.STATE_IDLE else Player.STATE_READY)
        item?.let {
            b.setPlaylist(listOf(it))
            b.setCurrentMediaItemIndex(0)
            b.setContentPositionMs { PhosphorNative.deckPositionMs() }
        }
        return b.build()
    }

    /** Called (on the player looper) after the Rust deck opened a track. */
    fun onTrackOpened() {
        val meta = JSONObject(PhosphorNative.deckMetadata())
        val path = meta.optString("path", "")
        val durationMs =
            if (meta.isNull("duration_ms")) C.TIME_UNSET else meta.getLong("duration_ms")
        val title =
            if (meta.isNull("title")) path.substringAfterLast('/').ifEmpty { "phosphor" }
            else meta.getString("title")
        android.util.Log.i("phosphor-mobil3", "onTrackOpened title=$title durationMs=$durationMs")
        val mm = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(if (meta.isNull("artist")) null else meta.getString("artist"))
            .setAlbumTitle(if (meta.isNull("album")) null else meta.getString("album"))
            .apply {
                PhosphorNative.deckCoverArt()?.let {
                    setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .build()
        val mediaItem = MediaItem.Builder().setMediaId(path).setMediaMetadata(mm).build()
        item = MediaItemData.Builder(path)
            .setMediaItem(mediaItem)
            .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1000)
            .setIsSeekable(true)
            .build()
        playing = true
        invalidateState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playing = playWhenReady
        PhosphorNative.deckSetPaused(!playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        PhosphorNative.deckSeekMs(positionMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playing = false
        item = null
        PhosphorNative.deckClose()
        return Futures.immediateVoidFuture()
    }
}
