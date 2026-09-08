package dev.phosphor.mobil3.settings.instrument

/** Activity-owned document generation. A cancelled provider operation still owns its slot until reply. */
class InstrumentDocumentOwner {
    class Ticket internal constructor(val id: Long)
    private val thread = Thread.currentThread()
    private var serial = 0L
    private var active: Ticket? = null
    private var cancelled = false
    private var closed = false
    val busy: Boolean get() = active != null
    private fun owner() { check(Thread.currentThread() === thread) { "Documents need their Activity owner" } }

    fun begin(): Ticket? {
        owner()
        if (closed || busy) return null
        cancelled = false
        return Ticket(++serial).also { active = it }
    }

    /** Called when the picker returns, before scheduling provider I/O. */
    fun picked(ticket: Ticket): Boolean {
        owner()
        if (active != ticket) return false
        if (closed || cancelled) { active = null; return false }
        return true
    }

    /** Always drains the exact owned slot. Only true permits UI publication. */
    fun finish(ticket: Ticket): Boolean {
        owner()
        if (active != ticket) return false
        active = null
        return !closed && !cancelled
    }

    fun cancel() { owner(); cancelled = true }
    fun close() { owner(); closed = true; cancelled = true }
}
