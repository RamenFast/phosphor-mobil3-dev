package dev.phosphor.mobil3

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** The service's serial deck worker owns reads. Publication also checks the exact open request. */
internal class PlaybackTruth {
    enum class Terminal { ENDED, START_FAILED, OUTPUT_FAILED }

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
        @Volatile var terminal: Terminal? = null
        @Volatile var terminalPublished = false
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
                it.terminal = old.terminal
                it.terminalPublished = old.terminalPublished
            }
        }
    }

    fun publishCurrent(publish: (Metadata) -> Unit) {
        val owner = opened ?: return
        if (owner.isLatest() && owner.terminal == null) owner.metadata?.let(publish)
    }

    fun poll(
        schedule: (() -> Unit) -> Unit,
        onMain: (() -> Unit) -> Unit,
        readEvent: () -> String?,
        readMetadata: () -> String,
        readArtwork: () -> ByteArray?,
        publish: (Metadata) -> Unit,
        terminal: (Terminal, isCurrent: () -> Boolean) -> Unit,
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
                            if (opened === owner && owner.isLatest() && owner.terminal == null) publish(metadata)
                        }
                    }
                }
                if (owner != null && event?.optString("path") == owner.path) {
                    when (event.optString("event")) {
                        "playback_ended" -> if (!owner.started) owner.terminal = Terminal.START_FAILED
                        "playback_drained" -> owner.terminal = owner.terminal ?: Terminal.ENDED
                        "output_error" -> owner.terminal = Terminal.OUTPUT_FAILED
                    }
                }
                owner?.terminal?.let { result ->
                    val current = { opened === owner && owner.isLatest() }
                    onMain {
                        if (current() && !owner.terminalPublished) {
                            owner.terminalPublished = true
                            terminal(result, current)
                        }
                    }
                }
                // Decoder EOF alone leaves a started, possibly paused tail available for resume.
            } finally {
                onMain { polling.set(false) }
            }
        }
    }
}

/** The Media3 face retains terminal output truth across later transport and metadata updates. */
internal class LocalOutputState {
    var terminal: PlaybackTruth.Terminal? = null
        private set
    val ready: Boolean get() = terminal == null
    val failed: Boolean get() = terminal != null && terminal != PlaybackTruth.Terminal.ENDED
    fun opened() { terminal = null }
    fun ended(result: PlaybackTruth.Terminal) { terminal = result }
}
