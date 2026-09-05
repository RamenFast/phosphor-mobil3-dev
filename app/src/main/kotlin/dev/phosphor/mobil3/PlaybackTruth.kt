package dev.phosphor.mobil3

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** The service's serial deck worker owns reads. Publication also checks the exact open request. */
internal class PlaybackTruth {
    data class Metadata(
        val path: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val artwork: ByteArray?,
    )

    private class Open(val path: String, val filename: String, val isLatest: () -> Boolean) {
        @Volatile var metadata: Metadata? = null
        var started = false
        var failed = false
    }

    @Volatile private var opened: Open? = null
    private val polling = AtomicBoolean()

    fun opened(path: String, filename: String, isLatest: () -> Boolean) {
        opened = Open(path, filename, isLatest)
    }

    fun closed() { opened = null }

    /** A rejected preflight may keep the already audible source, not a failed replacement. */
    fun retain(isLatest: () -> Boolean) {
        opened = opened?.let { old ->
            Open(old.path, old.filename, isLatest).also {
                it.metadata = old.metadata
                it.started = old.started
                it.failed = old.failed
            }
        }
    }

    fun publishCurrent(publish: (Metadata) -> Unit) {
        val owner = opened ?: return
        if (owner.isLatest()) owner.metadata?.let(publish)
    }

    fun poll(
        schedule: (() -> Unit) -> Unit,
        onMain: (() -> Unit) -> Unit,
        readEvent: () -> String?,
        readMetadata: () -> String,
        readArtwork: () -> ByteArray?,
        publish: (Metadata) -> Unit,
        failed: (isLatest: () -> Boolean) -> Unit,
    ) {
        if (!polling.compareAndSet(false, true)) return
        schedule {
            try {
                val owner = opened
                val event = readEvent()?.let(::JSONObject)
                if (owner != null && event?.optString("event") == "track_started" &&
                    event.optString("path") == owner.path
                ) {
                    owner.started = true
                    val json = JSONObject(readMetadata())
                    if (json.optString("path") == owner.path) {
                        fun tag(key: String) = if (json.isNull(key)) null else
                            json.optString(key).trim { it.isWhitespace() || it == '\u0000' }.ifEmpty { null }
                        val metadata = Metadata(
                            owner.path, tag("title") ?: owner.filename,
                            tag("artist"), tag("album"),
                            if (json.isNull("duration_ms")) null else
                                json.optLong("duration_ms").takeIf { it in 1..(Long.MAX_VALUE / 1000) },
                            readArtwork(),
                        )
                        owner.metadata = metadata
                        onMain {
                            if (opened === owner && owner.isLatest()) publish(metadata)
                        }
                    }
                }
                if (owner != null && event?.optString("event") == "playback_ended" &&
                    event.optString("path") == owner.path && !owner.started) owner.failed = true
                if (owner != null && owner.failed && opened === owner && owner.isLatest()) failed(owner.isLatest)
                // PlaybackEnded is consumed here too. Decode EOF alone does not prove output drained.
            } finally {
                onMain { polling.set(false) }
            }
        }
    }
}
