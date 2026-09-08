package dev.phosphor.mobil3.settings.appearance

import java.util.UUID

/** Explicit immutable edits only. The caller owns publication, persistence and previews. */
class AppearanceCollection private constructor(val document: AppearanceDocument) {
    val modified: Boolean get() = document.activeId.isNotEmpty() && record(document.activeId).value != document.active

    fun record(id: String): AppearanceRecord =
        (AppearanceDocument.CURATED + document.users + document.legacy).find { it.id == id }
            ?: throw AppearanceException("missing_record", "No appearance has ID $id", "Select an existing appearance")

    fun create(name: String, value: AppearanceValue, id: String = UUID.randomUUID().toString()): AppearanceCollection =
        changed(users = document.users + AppearanceRecord(id, name, value))

    /** Save the active authored value and associate it with this newly created user record. */
    fun save(name: String, id: String = UUID.randomUUID().toString()): AppearanceCollection =
        create(name, document.active, id).applyEdit(document.active, id)

    fun update(id: String, value: AppearanceValue): AppearanceCollection {
        val prior = user(id)
        return changed(users = document.users.map { if (it.id == id) prior.copy(value = value) else it })
    }

    fun rename(id: String, name: String): AppearanceCollection {
        val prior = user(id)
        return changed(users = document.users.map { if (it.id == id) prior.copy(name = name) else it })
    }

    fun delete(id: String): AppearanceCollection {
        user(id)
        return changed(activeId = if (document.activeId == id) "" else document.activeId,
            users = document.users.filterNot { it.id == id })
    }

    fun apply(id: String): AppearanceCollection = applyEdit(record(id).value, id)

    fun applyEdit(value: AppearanceValue, activeId: String = ""): AppearanceCollection =
        changed(active = value, activeId = activeId)

    fun reset(): AppearanceCollection = apply("curated:amoled")

    private fun user(id: String): AppearanceRecord {
        appearanceRequire(!id.startsWith("curated:") && !id.startsWith("legacy:"), "immutable_record",
            "Curated and legacy appearances cannot be changed", "Save the appearance as a new user record")
        return document.users.find { it.id == id }
            ?: throw AppearanceException("missing_record", "No user appearance has ID $id", "Select an existing user appearance")
    }

    private fun changed(active: AppearanceValue = document.active, activeId: String = document.activeId,
        users: List<AppearanceRecord> = document.users): AppearanceCollection = of(
        AppearanceDocument.of(active, activeId, users, document.legacy, document.provenance))

    override fun equals(other: Any?): Boolean = other is AppearanceCollection && document == other.document
    override fun hashCode(): Int = document.hashCode()

    companion object {
        fun of(document: AppearanceDocument = AppearanceDocument.of()): AppearanceCollection = AppearanceCollection(document)
    }
}
