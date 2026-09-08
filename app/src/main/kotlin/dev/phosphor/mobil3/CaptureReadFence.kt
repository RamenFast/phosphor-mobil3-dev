package dev.phosphor.mobil3

/** The returned batch keeps the epoch sampled before its read, including a blocked read. */
internal fun readSourceSamples(
    running: () -> Boolean,
    readEpoch: () -> Long,
    read: () -> Int,
    push: (Int, Long) -> Unit,
    failed: (RuntimeException) -> Unit,
) {
    try {
        while (running()) {
            val epoch = readEpoch()
            val count = read()
            if (count < 0) throw IllegalStateException("AudioRecord read failed: $count")
            if (running() && count > 0) push(count, epoch)
        }
    } catch (error: RuntimeException) {
        failed(error)
    }
}
