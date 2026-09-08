package dev.phosphor.mobil3.settings.appearance

import dev.phosphor.mobil3.settings.SettingsWriteOwner

/** Serialized authored appearance. No instrument, native renderer or source authority is held here. */
internal class AppearanceWorkflow(
    private val preferences: AppearancePreferences,
    private val migrate: (Map<String, *>) -> AppearanceDocument,
    private val failed: (SettingsWriteOwner.Failure) -> Unit = {},
    private val changed: () -> Unit = {},
    private val sharedBlocked: () -> Boolean = { false },
) {
    private val thread = Thread.currentThread()
    private var retired = false
    private var revisionToken = Any()
    class Ticket internal constructor(internal val revision: Any)
    var revision = 0L
        private set
    var committed: AppearanceDocument? = null
        private set
    var preview: AppearanceValue? = null
        private set
    var uncertain = false
        private set
    var unavailable = false
        private set
    var status = ""
        private set
    val effective: AppearanceValue? get() = preview ?: committed?.active
    val blocked: Boolean get() = retired || uncertain || sharedBlocked() || unavailable || committed == null
    val summary: String get() {
        val document = committed ?: return "Legacy appearance · replacement required"
        val collection = AppearanceCollection.of(document)
        val name = if (document.activeId.isEmpty()) "Unsaved appearance" else collection.record(document.activeId).name
        return name + when {
            preview != null -> " · preview, not saved"
            collection.modified -> " · modified"
            else -> " · current"
        }
    }

    fun load() {
        owner()
        if (retired) return
        try {
            val values = preferences.read()
            if (AppearancePreferences.KEY in values) {
                val raw = values[AppearancePreferences.KEY] as? String
                    ?: throw IllegalArgumentException("Stored appearance is not a string")
                publish(AppearanceDocumentCodec.decode(raw))
            } else {
                val candidate = migrate(values)
                val failure = preferences.save(candidate)
                if (failure == null) publish(candidate) else {
                    unavailable = true
                    noteFailure(failure)
                    status += " Legacy appearance retained. Use complete replacement to retry."
                }
            }
        } catch (error: Exception) {
            unavailable = true
            status = "Appearance unavailable. Original settings retained. ${error.message}. Use complete replacement to repair."
        }
        changed()
    }

    fun ticket(): Ticket { owner(); return Ticket(revisionToken) }
    fun accepts(ticket: Ticket): Boolean {
        owner()
        return !retired && !uncertain && !sharedBlocked() && ticket.revision === revisionToken
    }

    fun preview(value: AppearanceValue) {
        owner()
        if (blocked) return
        invalidate()
        preview = value
        status = "Preview only. APPLY or SAVE to keep it."
        changed()
    }

    fun cancel() {
        owner()
        if (retired || preview == null) return
        preview = null
        invalidate()
        status = "Preview cancelled. Committed appearance restored."
        changed()
    }

    fun apply(value: AppearanceValue, id: String = "") = edit("Appearance applied.") { it.applyEdit(value, id) }
    fun select(id: String) = edit("Appearance applied.") { it.apply(id) }
    fun save(name: String, value: AppearanceValue, id: String? = null) = edit("Named appearance saved.") {
        if (id == null) it.applyEdit(value).save(name)
        else it.update(id, value).applyEdit(value, id)
    }
    fun rename(id: String, name: String) = edit("Appearance renamed.") { it.rename(id, name) }
    fun delete(id: String) = edit("Appearance deleted. Active colors retained.") { it.delete(id) }
    fun reset() = edit("AMOLED restored. Saved appearances retained.") { it.reset() }

    private fun edit(message: String, action: (AppearanceCollection) -> AppearanceCollection) {
        owner()
        if (blocked) return
        invalidate()
        try {
            val candidate = action(AppearanceCollection.of(checkNotNull(committed))).document
            val failure = preferences.save(candidate)
            if (failure == null) {
                publish(candidate)
                status = message
            } else noteFailure(failure)
        } catch (error: Exception) {
            status = "Appearance unchanged. ${error.message}. Check the exact values and retry."
        }
        changed()
    }

    /** Explicit complete repair. Corrupt bytes are never parsed or replaced before this action. */
    fun replace(document: AppearanceDocument) {
        owner()
        if (retired || (!unavailable && !uncertain)) return
        // Existing authoritative state must use ordered recovery, not a replacement preview.
        if (uncertain && committed != null) return
        invalidate()
        val failure = preferences.save(document)
        if (failure == null) {
            publish(document)
            uncertain = false
            status = "Complete appearance replacement saved."
        } else noteFailure(failure)
        changed()
    }

    /** A shared rollback failure blocks edits, even when it came from another settings family. */
    fun persistenceFailed(failure: SettingsWriteOwner.Failure) {
        owner()
        if (retired || failure.restored) return
        uncertain = true
        preview = null
        invalidate()
        status = "Saved settings are uncertain. Recover appearance before retrying instrument storage."
        changed()
    }

    /** Called before every instrument recovery save. It never persists a preview. */
    fun recover(): Boolean {
        owner()
        if (retired) return false
        if (!uncertain) return true
        val authoritative = committed ?: run {
            status = "No authoritative appearance is available. Use complete replacement before instrument recovery."
            changed()
            return false
        }
        val failure = preferences.save(authoritative)
        if (failure != null) {
            noteFailure(failure)
            changed()
            return false
        }
        uncertain = false
        preview = null
        invalidate()
        status = "Authoritative appearance saved. Instrument recovery can now continue."
        changed()
        return true
    }

    /** An already committed archive transaction publishes without a second persistence write. */
    fun imported(document: AppearanceDocument) {
        owner()
        if (retired) return
        publish(document)
        status = "Imported appearance saved."
        changed()
    }

    fun close() {
        owner()
        if (retired) return
        preview = null
        retired = true
        invalidate()
        changed()
    }

    private fun publish(document: AppearanceDocument) {
        committed = document
        preview = null
        unavailable = false
        invalidate()
    }
    private fun noteFailure(failure: SettingsWriteOwner.Failure) {
        if (!failure.restored) persistenceFailed(failure)
        status = failure.message()
        failed(failure)
    }
    private fun invalidate() { revisionToken = Any(); revision++ }
    private fun owner() { check(Thread.currentThread() === thread) { "Appearance actions must use their UI owner" } }
}
