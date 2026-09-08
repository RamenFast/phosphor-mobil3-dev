package dev.phosphor.mobil3.settings.instrument

/** All methods except the supplied wait task run on the creating thread. */
class InstrumentWorkflow(
    private val native: Native,
    private val snapshot: () -> InstrumentSetup,
    private val publish: (InstrumentSetup) -> Unit,
    private val persist: (InstrumentSetup) -> PersistenceFailure?,
    private val canApply: () -> Boolean,
    private val acknowledged: () -> Boolean,
    private val waitOffMain: (Long, (Int) -> Unit) -> Unit,
    private val changed: () -> Unit,
) {
    data class PersistenceFailure(val message: String, val restored: Boolean)
    interface Native {
        fun request(setup: InstrumentSetup): Long
        fun cancel(id: Long): Int
        fun release(id: Long)
    }
    data class Association(val key: String, val name: String, val setup: InstrumentSetup)
    private data class Request(val id: Long, val before: InstrumentSetup, val setup: InstrumentSetup,
        val association: Association?, val undo: Boolean)
    private val thread = Thread.currentThread()
    private var request: Request? = null
    private var depth = 0
    private var closed = false
    class SettingsImport internal constructor(internal val revision: Any)
    private var authoredRevision = Any()
    private var settingsImport: SettingsImport? = null
    var association: Association? = null
        private set
    var undoSetup: InstrumentSetup? = null
        private set
    var rapidReview: Pair<InstrumentSetup, Association?>? = null
        private set
    var status = ""
        private set
    var unsaved = false
        private set
    var uncertain = false
        private set
    var storageUncertain = false
        private set
    val editsBlocked: Boolean get() = uncertain || storageUncertain
    val pending: Boolean get() = request != null
    val automaticPersistenceAllowed: Boolean get() = !closed && !pending && !unsaved && !editsBlocked
    val modified: Boolean get() = association?.let { it.setup != snapshot() } ?: false

    private fun owner() { check(Thread.currentThread() === thread) { "Instrument workflow needs its Activity owner" } }

    fun apply(setup: InstrumentSetup, recalled: Association? = null, undo: Boolean = false) {
        owner()
        if (closed) return
        settle("Superseded by another apply")
        if (editsBlocked) { changed(); return }
        authoredRevision = Any()
        if (!canApply()) {
            status = "Desktop geometry owns shape and gain. Choose a local or audio-only source before APPLY."
            changed()
            return
        }
        val guarded = setup.guardLight(acknowledged())
        rapidReview = guarded.pending?.let { it to recalled }
        val before = snapshot()
        val id = try { native.request(guarded.safe) } catch (_: Exception) { -3L }
        if (id <= 0) {
            status = when (id) {
                -1L -> "Renderer rejected this setup. Select a valid preset and retry."
                -2L -> "Renderer receipts are busy. Wait for the pending operation and retry."
                else -> "Renderer unavailable. Reopen Phosphor and retry."
            }
            changed()
            return
        }
        request = Request(id, before, guarded.safe, recalled, undo)
        status = "Applying instrument setup…"
        changed()
        try { waitOffMain(id) { result -> complete(id, result) } }
        catch (_: Exception) { settle("Wait could not start. Retry APPLY.") }
    }

    fun undo() {
        owner()
        settle("Superseded by undo")
        authoredRevision = Any()
        undoSetup?.let { apply(it, undo = true) }
    }

    fun keepSafe() { owner(); rapidReview = null; changed() }
    fun allowRapid() {
        owner()
        if (!acknowledged()) return
        rapidReview?.let { (setup, recalled) -> apply(setup, recalled) }
    }

    /** Cancel is finite native CPU admission, not the blocking await operation. */
    fun settle(reason: String = "Apply cancelled. Existing setup kept.") {
        owner()
        val pending = request ?: return
        val result = try { native.cancel(pending.id) } catch (_: Exception) { 4 }
        complete(pending.id, result, reason)
    }

    private fun complete(id: Long, result: Int, cancellation: String = "Apply expired or was cancelled. Retry APPLY.") {
        owner()
        val pending = request?.takeIf { it.id == id } ?: return
        request = null
        try {
            if (result == 1) {
                // The exact committed setup is reconciled before any later edit.
                authoredRevision = Any()
                publish(pending.setup)
                undoSetup = if (pending.undo) null else pending.before
                association = pending.association
                val error = save(pending.setup)
                status = error?.let { "Active, not saved. ${it.message} " +
                    if (storageUncertain) "Use RETRY SAVE CURRENT before further tuning." else "Retry save or use UNDO." }
                    ?: if (rapidReview != null) "Applied with safe timing. Modified, not an exact recall. Review faster timing below."
                    else "Instrument setup applied and saved."
            } else {
                if (result !in 2..3) {
                    uncertain = true
                    unsaved = true
                }
                status = when (result) {
                    2 -> cancellation
                    3 -> "Renderer rejected the setup after a capability change. Choose a local source and retry."
                    else -> "Native receipt unavailable. Outcome cannot be confirmed. Reopen Phosphor before another apply."
                }
            }
        } finally {
            try { native.release(id) } catch (_: Exception) {
                status += " Receipt release failed. Reopen Phosphor before another apply."
            }
            if (!closed) changed()
        }
    }

    /** Nested setter/import calls remain one ordered edit. */
    fun <T> edit(block: () -> T): T {
        owner()
        check(!closed) { "Instrument owner retired" }
        if (depth == 0) {
            settle("Apply superseded by a later tuning edit.")
            rapidReview = null
        }
        check(!editsBlocked) { "Instrument state is uncertain. Recover it before tuning." }
        if (depth == 0) authoredRevision = Any()
        depth++
        return try { block() } finally {
            depth--
            if (depth == 0) changed()
        }
    }

    fun forget(key: String) {
        owner()
        if (association?.key == key) association = null
        changed()
    }

    fun externalRestoreSaved() {
        owner()
        check(!pending) { "Settle native work before restoring saved tuning" }
        check(!editsBlocked) { "Instrument state is uncertain. Recover it before restoring tuning." }
        authoredRevision = Any()
        unsaved = false
        changed()
    }

    private fun save(setup: InstrumentSetup): PersistenceFailure? {
        val failure = try { persist(setup) } catch (_: Exception) {
            PersistenceFailure("Saving failed and recovery is unconfirmed.", false)
        }
        unsaved = failure != null
        if (failure == null) storageUncertain = false else notePersistenceFailure(failure)
        return failure
    }

    private fun notePersistenceFailure(failure: PersistenceFailure) {
        if (!failure.restored) {
            storageUncertain = true
            unsaved = true
            authoredRevision = Any()
        }
        status = if (storageUncertain)
            "Saved settings are uncertain. Use RETRY SAVE CURRENT before further tuning. ${failure.message}"
        else failure.message
    }

    /** Every settings transaction reports its typed rollback result to the same owner. */
    fun persistenceFailed(failure: PersistenceFailure) {
        owner()
        if (closed) return
        notePersistenceFailure(failure)
        changed()
    }

    /** Settle before opening the picker, then bind its eventual reply to authored intent. */
    fun beginSettingsImport(): SettingsImport? {
        owner()
        if (closed || settingsImport != null) return null
        settle("Pending apply settled before choosing settings.")
        if (editsBlocked) return null
        return SettingsImport(authoredRevision).also { settingsImport = it }
    }

    fun settingsImportPicked(ticket: SettingsImport): Boolean {
        owner()
        if (settingsImport !== ticket) return false
        if (closed || editsBlocked || ticket.revision !== authoredRevision) {
            settingsImport = null
            return false
        }
        return true
    }

    /** Provider replies consume only their own slot. No stale reply may enter a write. */
    fun finishSettingsImport(ticket: SettingsImport, accept: () -> Unit): Boolean {
        owner()
        if (settingsImport !== ticket) return false
        settingsImport = null
        if (closed || editsBlocked || ticket.revision !== authoredRevision) return false
        edit(accept)
        return true
    }

    fun cancelSettingsImport(ticket: SettingsImport) {
        owner()
        if (settingsImport === ticket) settingsImport = null
    }

    /** Explicit recovery establishes the complete current tuple as durable without native setters. */
    fun retryPersistence() {
        owner()
        if (closed || uncertain) return
        settle()
        if (uncertain) return
        authoredRevision = Any()
        val failure = save(snapshot())
        status = failure?.let { "Active, not saved. ${it.message} Retry when storage is available." }
            ?: "Current authored setup saved. Tuning unchanged."
        changed()
    }

    fun refreshAssociation(records: InstrumentPresetCollection) {
        owner()
        association?.let { old ->
            if (!old.key.startsWith("curated:")) {
                association = records.records.find { it.id == old.key }?.let {
                    Association(it.id, it.name, it.setup)
                }
            }
        }
        changed()
    }

    fun close() {
        owner()
        if (closed) return
        settle("Activity closed. Pending setup cancelled.")
        closed = true
        authoredRevision = Any()
        rapidReview = null
    }
}
