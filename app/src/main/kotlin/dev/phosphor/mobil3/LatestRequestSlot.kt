package dev.phosphor.mobil3

/**
 * One serial latest-request slot.
 *
 * Enqueue only stores the newest pending request and schedules a drain when needed. Work never
 * runs on the caller. A request already running finishes, then the drain takes the newest request
 * that arrived while it ran.
 */
internal class LatestRequestSlot<T : Any>(
    private val schedule: (() -> Unit) -> Unit,
    private val consume: (T, isLatest: () -> Boolean) -> Unit,
) {
    private data class Entry<T>(val sequence: Long, val value: T)

    private val lock = Any()
    private var sequence = 0L
    private var pending: Entry<T>? = null
    private var draining = false

    fun enqueue(value: T) {
        val scheduleDrain = synchronized(lock) {
            pending = Entry(++sequence, value)
            if (draining) {
                false
            } else {
                draining = true
                true
            }
        }
        if (scheduleDrain) schedule(::drain)
    }

    private fun drain() {
        while (true) {
            val entry = synchronized(lock) {
                pending?.also { pending = null } ?: run {
                    draining = false
                    return
                }
            }
            consume(entry.value) {
                synchronized(lock) { entry.sequence == sequence }
            }
        }
    }
}
