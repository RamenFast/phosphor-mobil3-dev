package dev.phosphor.mobil3

internal class LocalTransportIntent {
    var revision = 0L
        private set
    private var requestedPlaying = false
    var playing = false
        private set

    fun record(play: Boolean) { revision++; requestedPlaying = play }
    fun publish(play: Boolean) { playing = play }
    fun atPublication(selectedAt: Long, autoplay: Boolean): Boolean =
        if (revision == selectedAt) autoplay else requestedPlaying
}

internal class LocalFocusResume {
    private var revision: Long? = null
    fun arm(wasPlaying: Boolean, atRevision: Long) { revision = atRevision.takeIf { wasPlaying } }
    fun cancel() { revision = null }
    fun take(atRevision: Long): Boolean {
        val allowed = revision == atRevision
        revision = null
        return allowed
    }
}

internal class LocalSourcePublication {
    enum class Source { OTHER, LOCAL, NONE, RELEASED_READERS }
    enum class Reader { MIC, CAPTURE }
    data class Snapshot(val revision: Long, val source: Source, val readers: Set<Reader> = emptySet()) {
        fun clearsReaderFace(label: String): Boolean =
            (Reader.MIC in readers && label == "mic") ||
                (Reader.CAPTURE in readers && label.startsWith("capture"))
    }
    var current = Snapshot(0, Source.OTHER)
        private set

    fun selected() { current = current.copy(revision = current.revision + 1) }
    fun published(source: Source): Snapshot {
        current = Snapshot(current.revision + 1, source)
        return current
    }
    fun failed(released: Boolean): Snapshot? = if (released) published(Source.NONE) else null
    fun readersReleased(readers: Set<Reader>): Snapshot {
        current = Snapshot(current.revision + 1, Source.RELEASED_READERS, readers)
        return current
    }
    fun accepts(revision: Long): Boolean = current.revision == revision
}

/** Ownership loss outlives the request that caused it. Selection alone cannot restore it. */
internal class LocalSourceSurvival {
    data class Loss(val native: Boolean, val readers: Set<LocalSourcePublication.Reader>)
    private var epoch = 0L
    private var nativeLost = false
    private val releasedReaders = mutableSetOf<LocalSourcePublication.Reader>()

    @Synchronized fun epoch(): Long = epoch
    @Synchronized fun nativeReplacing() { nativeLost = true }
    fun readerStopped(atEpoch: Long, reader: LocalSourcePublication.Reader, stop: SourceStopRequest): Boolean =
        stop.stoppedOwner && readerReleased(atEpoch, reader)
    @Synchronized fun readerReleased(atEpoch: Long, reader: LocalSourcePublication.Reader): Boolean {
        if (atEpoch != epoch) return false
        releasedReaders.add(reader)
        return true
    }
    @Synchronized fun loss() = Loss(nativeLost, releasedReaders.toSet())
    @Synchronized fun published() {
        epoch++
        nativeLost = false
        releasedReaders.clear()
    }
}
