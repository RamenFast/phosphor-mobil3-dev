package dev.phosphor.mobil3

/** Prevents old EOF observations from replacing an explicit source/queue request. Main-thread owned. */
internal class LocalQueuePolicy {
    private var pending = false
    private var nativeAvailable = true

    fun beginSelection() { pending = true }
    fun published() { pending = false; nativeAvailable = true }
    fun failed(preservesNative: Boolean) { pending = false; nativeAvailable = preservesNative }
    fun exhausted(fromEof: Boolean) { pending = false; nativeAvailable = !fromEof }
    fun mayAdvance(): Boolean = !pending && nativeAvailable
}

internal fun localTrackCandidates(size: Int, requested: Int, opened: Int?, fromEof: Boolean): IntProgression {
    if (size <= 0) return IntRange.EMPTY
    val start = requested.coerceIn(0, size - 1)
    return if (!fromEof && opened != null && start < opened) start downTo 0 else start until size
}
