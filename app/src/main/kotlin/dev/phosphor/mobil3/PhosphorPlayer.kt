package dev.phosphor.mobil3

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

// The Media3 bridge: the Rust deck is the engine, this is its Player face.
// The loaded deck owns the transport (the v4.7.0 law) — this player IS the loaded deck.
// v2: a REAL playlist. The queue (a folder, gaplessly ordered) maps to Media3 items;
// next/prev from any surface — console, lock screen, earbuds — walks it.
@androidx.annotation.OptIn(UnstableApi::class)
class PhosphorPlayer(looper: Looper) : SimpleBasePlayer(looper) {
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()


    private val transportIntent = LocalTransportIntent()
    private val output = LocalOutputState()
    private var playing: Boolean
        get() = transportIntent.playing
        set(value) { transportIntent.publish(value) }
    private var queue: List<QueueEntry> = emptyList()
    private var index = 0
    private var nativeSeekPosition: Long? = null

    internal fun transportRevision(): Long = transportIntent.revision
    internal fun recordTransportIntent(play: Boolean) = transportIntent.record(play)
    internal fun setPublishedPlaying(play: Boolean) {
        playing = play
        PhosphorNative.deckSetPaused(!play || !output.ready)
        invalidateState()
    }

    data class QueueEntry(
        val path: String, // local path once staged, else "" until resolved
        val title: String,
        val durationMs: Long = C.TIME_UNSET,
    )

    /** The service resolves + stages files; the player face only enqueues deck work. */
    var onSwitchTrack: ((Int) -> Unit)? = null
    var onSeek: ((Int, Long) -> Unit)? = null
    var onStopRequested: (() -> Unit)? = null

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
            .apply {
                if (queue.isNotEmpty()) add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                if (index < queue.size - 1) {
                    addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                if (index > 0) {
                    addAll(
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    )
                }
            }
            .build()
        val b = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(when {
                queue.isEmpty() || output.failed -> Player.STATE_IDLE
                !output.ready -> Player.STATE_ENDED
                else -> Player.STATE_READY
            })
            .setPlayerError(if (output.failed) PlaybackException(
                "Local audio stopped. Check the audio route or choose a readable file and retry",
                null, PlaybackException.ERROR_CODE_UNSPECIFIED,
            ) else null)
        if (queue.isNotEmpty()) {
            b.setPlaylist(queue.mapIndexed { i, e -> itemData(i, e) })
            b.setCurrentMediaItemIndex(index)
            b.setContentPositionMs { PhosphorNative.deckPositionMs() }
            nativeSeekPosition?.let {
                b.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK, it)
                nativeSeekPosition = null
            }
        }
        return b.build()
    }

    private fun itemData(i: Int, e: QueueEntry): MediaItemData {
        val loaded = i == index
        val meta = if (loaded) loadedMetadata() else null
        val mm = meta ?: MediaMetadata.Builder().setTitle(e.title).build()
        return MediaItemData.Builder("q$i:${e.title}")
            .setMediaItem(MediaItem.Builder().setMediaId("q$i").setMediaMetadata(mm).build())
            .setDurationUs(
                when {
                    loaded && loadedDurationMs > 0 -> loadedDurationMs * 1000
                    e.durationMs != C.TIME_UNSET -> e.durationMs * 1000
                    else -> C.TIME_UNSET
                }
            )
            .setIsSeekable(true)
            .build()
    }

    private var loadedDurationMs: Long = C.TIME_UNSET
    private var loadedMeta: MediaMetadata? = null

    private fun loadedMetadata(): MediaMetadata? = loadedMeta

    /** Called (on the player looper) after the Rust deck opened the CURRENT queue entry. */
    fun onTrackOpened(selectedAt: Long? = null) {
        output.opened()
        playing = selectedAt?.let { transportIntent.atPublication(it, autoplay = true) } ?: true
        invalidateState()
    }

    internal fun onNativeSeekCompleted() {
        output.opened()
        nativeSeekPosition = PhosphorNative.deckPositionMs()
        invalidateState()
    }

    internal fun onNativeTerminal(result: PlaybackTruth.Terminal) {
        output.ended(result)
        nativeSeekPosition = null
        playing = false
        PhosphorNative.deckSetPaused(true)
        invalidateState()
    }

    internal fun onTrackMetadata(meta: PlaybackTruth.Metadata) {
        loadedDurationMs = meta.durationMs ?: C.TIME_UNSET
        loadedMeta = MediaMetadata.Builder()
            .setTitle(meta.title)
            .setArtist(meta.artist)
            .setAlbumTitle(meta.album)
            .apply {
                meta.artwork?.let {
                    setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .build()
        invalidateState()
    }

    /** Install a fresh queue (folder play). The service stages + opens entry `start`. */
    fun setQueue(entries: List<QueueEntry>, start: Int) {
        nativeSeekPosition = null
        queue = entries
        index = start.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        loadedMeta = null
        loadedDurationMs = C.TIME_UNSET
        invalidateState()
    }

    fun queueSize(): Int = queue.size
    fun currentIndex(): Int = index
    fun currentDurationMs(): Long = loadedDurationMs

    /** Auto-advance at end-of-track (service's position watcher calls this). */
    fun advanceIfPossible(): Boolean {
        if (index >= queue.size - 1) return false
        index++
        // Publish the new queue item without the old item's resolved metadata while the
        // service stages it. In particular, its artwork must not cross this boundary.
        loadedMeta = null
        loadedDurationMs = C.TIME_UNSET
        invalidateState()
        onSwitchTrack?.invoke(index)
        return true
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        recordTransportIntent(playWhenReady)
        playing = playWhenReady
        if (playWhenReady && !output.ready) onSeek?.invoke(index, 0L)
        else PhosphorNative.deckSetPaused(!playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (mediaItemIndex != index && mediaItemIndex in queue.indices) {
            index = mediaItemIndex
            loadedMeta = null
            loadedDurationMs = C.TIME_UNSET
            onSwitchTrack?.invoke(index)
        } else {
            onSeek?.invoke(index, positionMs)
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        nativeSeekPosition = null
        playing = false
        queue = emptyList()
        index = 0
        loadedMeta = null
        onStopRequested?.invoke()
        return Futures.immediateVoidFuture()
    }
}
