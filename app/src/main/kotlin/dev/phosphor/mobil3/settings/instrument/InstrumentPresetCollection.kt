package dev.phosphor.mobil3.settings.instrument

import java.util.Collections
import java.util.UUID

internal fun validUnicode(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val char = text[index++]
        if (char.isHighSurrogate()) {
            if (index == text.length || !text[index++].isLowSurrogate()) return false
        } else if (char.isLowSurrogate()) return false
    }
    return true
}

internal fun nameKey(name: String): String = buildString {
    name.codePoints().forEach { appendCodePoint(Character.toLowerCase(Character.toUpperCase(it))) }
}

internal fun validateName(name: String) {
    presetRequire(validUnicode(name) && name == name.trim() &&
        name.codePointCount(0, name.length) in 1..64 &&
        name.codePoints().noneMatch(Character::isISOControl),
        "invalid_name", "Names need 1..64 trimmed Unicode code points without controls",
        "Choose a trimmed name with 1..64 Unicode code points and no control characters")
}

internal fun validateId(id: String) {
    presetRequire(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) &&
        runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false),
        "invalid_id", "Record ID must be a canonical lowercase UUID", "Generate a new UUID or use the original exported ID")
}

data class InstrumentPresetRecord(val id: String, val name: String, val setup: InstrumentSetup) {
    init {
        validateId(id)
        validateName(name)
    }
}

/** A value, not a persistence owner. No operation changes tuning or a previous collection. */
class InstrumentPresetCollection private constructor(records: List<InstrumentPresetRecord>) {
    val records: List<InstrumentPresetRecord> = Collections.unmodifiableList(records.sortedBy { it.id })

    fun record(id: String): InstrumentPresetRecord = records.find { it.id == id }
        ?: throw InstrumentPresetException("missing_record", "No preset has ID $id", "Refresh the preset list and select an existing record")

    fun create(name: String, setup: InstrumentSetup, id: String = UUID.randomUUID().toString()): InstrumentPresetCollection =
        of(records + InstrumentPresetRecord(id, name.trim(), setup))

    fun update(id: String, setup: InstrumentSetup): InstrumentPresetCollection {
        val prior = record(id)
        return of(records.map { if (it.id == id) prior.copy(setup = setup) else it })
    }

    fun rename(id: String, name: String): InstrumentPresetCollection {
        val prior = record(id)
        return of(records.map { if (it.id == id) prior.copy(name = name.trim()) else it })
    }

    fun delete(id: String): InstrumentPresetCollection {
        record(id)
        return of(records.filterNot { it.id == id })
    }

    /** Calling this is the explicit save action. A name proposal alone creates nothing. */
    fun duplicate(id: String, name: String, newId: String = UUID.randomUUID().toString()): InstrumentPresetCollection =
        create(name, record(id).setup, newId)

    fun proposeDuplicateName(name: String): String {
        validateName(name)
        for (index in 1..MAX_RECORDS + 1) {
            val suffix = if (index == 1) " copy" else " copy $index"
            val points = minOf(name.codePointCount(0, name.length), 64 - suffix.length)
            val candidate = name.substring(0, name.offsetByCodePoints(0, points)).trimEnd() + suffix
            if (records.none { nameKey(it.name) == nameKey(candidate) }) return candidate
        }
        error("A bounded collection always has a free copy name")
    }

    fun previewImport(json: String): InstrumentImportPreview =
        InstrumentImportPreview(this, InstrumentPresetCodec.decode(json))

    fun resolveImport(preview: InstrumentImportPreview, choices: Map<String, InstrumentImportChoice>): InstrumentPresetCollection {
        presetRequire(this == preview.base, "stale_preview", "The preset collection changed after import preview",
            "Preview the document again against the current collection")
        val incoming = preview.incoming.records
        presetRequire(choices.keys == incoming.map { it.id }.toSet(), "missing_choice",
            "Choose one import action for every incoming record", "Resolve every record in the import preview")
        val removed = mutableSetOf<String>()
        val additions = mutableListOf<InstrumentPresetRecord>()
        incoming.forEach { candidate ->
            val conflicts = preview.conflictsFor(candidate.id)
            when (val choice = choices.getValue(candidate.id)) {
                InstrumentImportChoice.Add -> {
                    presetRequire(conflicts.isEmpty(), "unresolved_conflict", "Incoming preset conflicts with an existing record",
                        "Keep existing, replace the identified record, or save a new copy")
                    additions += candidate
                }
                InstrumentImportChoice.KeepExisting -> Unit
                is InstrumentImportChoice.Replace -> {
                    presetRequire(choice.existingId in conflicts, "invalid_replacement", "Replacement must identify a conflicting existing record",
                        "Select one existing record shown in this conflict")
                    presetRequire(removed.add(choice.existingId), "duplicate_target", "Two import choices replace the same record",
                        "Replace each existing record at most once")
                    additions += candidate.copy(id = choice.existingId)
                }
                is InstrumentImportChoice.SaveCopy -> {
                    presetRequire(records.none { it.id == choice.newId } && incoming.none { it.id == choice.newId },
                        "duplicate_id", "An imported copy needs a new UUID", "Generate a new UUID for the copy")
                    additions += candidate.copy(id = choice.newId, name = choice.name.trim())
                }
            }
        }
        return of(records.filterNot { it.id in removed } + additions)
    }

    override fun equals(other: Any?): Boolean = other is InstrumentPresetCollection && records == other.records
    override fun hashCode(): Int = records.hashCode()

    companion object {
        const val VERSION = 1
        const val MAX_RECORDS = 64
        const val PREFERENCES_FILE = "phosphor.instrument.presets"
        const val COLLECTION_KEY = "collection"

        fun empty(): InstrumentPresetCollection = of(emptyList())

        fun of(records: List<InstrumentPresetRecord>): InstrumentPresetCollection {
            presetRequire(records.size <= MAX_RECORDS, "too_many_records", "Keep at most 64 user presets",
                "Delete a user preset or import fewer records")
            val snapshot = InstrumentPresetCollection(records)
            presetRequire(snapshot.records.map { it.id }.distinct().size == snapshot.records.size,
                "duplicate_id", "Preset IDs must be unique", "Use a new UUID for each new record")
            presetRequire(snapshot.records.map { nameKey(it.name) }.distinct().size == snapshot.records.size,
                "duplicate_name", "Preset names must be unique without regard to case", "Choose a distinct preset name")
            InstrumentPresetCodec.encode(snapshot)
            return snapshot
        }
    }
}

sealed interface InstrumentImportChoice {
    data object Add : InstrumentImportChoice
    data object KeepExisting : InstrumentImportChoice
    data class Replace(val existingId: String) : InstrumentImportChoice
    data class SaveCopy(val name: String, val newId: String = UUID.randomUUID().toString()) : InstrumentImportChoice
}

class InstrumentImportPreview internal constructor(
    val base: InstrumentPresetCollection,
    val incoming: InstrumentPresetCollection,
) {
    fun conflictsFor(incomingId: String): List<String> {
        val candidate = incoming.record(incomingId)
        return Collections.unmodifiableList(base.records.filter {
            it.id == candidate.id || nameKey(it.name) == nameKey(candidate.name)
        }.map { it.id })
    }
}

object InstrumentPresetCommit {
    /** Root serializes this entire call. Callback false/throw must preserve its prior durable bytes. */
    fun commit(stored: String?, expected: InstrumentPresetCollection, proposed: InstrumentPresetCollection,
        persist: (String) -> Boolean): Result<InstrumentPresetCollection> = runCatching {
        val current = if (stored == null) InstrumentPresetCollection.empty() else InstrumentPresetCodec.decode(stored)
        presetRequire(current == expected, "stale_collection", "Stored presets changed before commit",
            "Read the current collection and repeat the explicit edit")
        val encoded = InstrumentPresetCodec.encode(proposed)
        val saved = try { persist(encoded) } catch (_: Exception) {
            throw InstrumentPresetException("persistence_failed", "Preset persistence failed", "Preserve prior bytes and retry saving")
        }
        presetRequire(saved, "persistence_failed", "Preset persistence did not commit", "Preserve prior bytes and retry saving")
        proposed
    }
}
