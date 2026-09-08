package dev.phosphor.mobil3.settings.appearance

import dev.phosphor.mobil3.settings.instrument.InstrumentPresetException
import dev.phosphor.mobil3.settings.instrument.nameKey
import dev.phosphor.mobil3.settings.instrument.validUnicode
import dev.phosphor.mobil3.settings.instrument.validateId
import dev.phosphor.mobil3.settings.instrument.validateName
import java.util.Collections

class AppearanceException(val code: String, override val message: String, val fix: String) : IllegalArgumentException(message)

internal fun appearanceRequire(condition: Boolean, code: String, message: String,
    fix: String = "Correct the complete appearance document and try again") {
    if (!condition) throw AppearanceException(code, message, fix)
}

/** Record kind is established by its containing collection, not by mutable metadata. */
data class AppearanceRecord(val id: String, val name: String, val value: AppearanceValue)

/** Null means absent. Explicit legacy sentinels remain signed integers without interpretation. */
data class AppearanceProvenance(
    val room: String? = null,
    val character: Int? = null,
    val motion: Int? = null,
    val radius: Int? = null,
    val designators: Int? = null,
) {
    init {
        appearanceRequire(room == null || (validUnicode(room) && room.codePointCount(0, room.length) <= 4096),
            "invalid_provenance", "Original room needs valid Unicode with at most 4096 code points")
    }
}

/** Complete authored state. Construction snapshots lists and validates before returning. */
class AppearanceDocument private constructor(
    val active: AppearanceValue,
    val activeId: String,
    users: List<AppearanceRecord>,
    legacy: List<AppearanceRecord>,
    val provenance: AppearanceProvenance,
) {
    val users: List<AppearanceRecord> = Collections.unmodifiableList(users.toList())
    val legacy: List<AppearanceRecord> = Collections.unmodifiableList(legacy.toList())

    override fun equals(other: Any?): Boolean = other is AppearanceDocument &&
        active == other.active && activeId == other.activeId && users == other.users &&
        legacy == other.legacy && provenance == other.provenance
    override fun hashCode(): Int = listOf(active, activeId, users, legacy, provenance).hashCode()

    companion object {
        const val MAX_USERS = 32
        val CURATED: List<AppearanceRecord> = Collections.unmodifiableList(listOf(
            AppearanceRecord("curated:light", "Light", CuratedAppearances.light),
            AppearanceRecord("curated:dark", "Dark", CuratedAppearances.dark),
            AppearanceRecord("curated:glass", "Glass", CuratedAppearances.glass),
            AppearanceRecord("curated:amoled", "AMOLED", CuratedAppearances.amoled),
        ))
        val LEGACY_IDS: Set<String> = Collections.unmodifiableSet(linkedSetOf(
            "blossom", "blossom_dark", "light", "dark", "chromacore", "basalt", "afterglow",
            "stonework95", "amoled", "paper", "amber", "fable", "glass",
        ).mapTo(linkedSetOf()) { "legacy:$it" })
        const val LEGACY_CURRENT = "legacy:current"

        fun of(
            active: AppearanceValue = CuratedAppearances.amoled,
            activeId: String = "curated:amoled",
            users: List<AppearanceRecord> = emptyList(),
            legacy: List<AppearanceRecord> = emptyList(),
            provenance: AppearanceProvenance = AppearanceProvenance(),
        ): AppearanceDocument {
            val result = AppearanceDocument(active, activeId, users, legacy, provenance)
            appearanceRequire(result.users.size <= MAX_USERS, "too_many_records", "Keep at most 32 user appearances")
            result.users.forEach { record ->
                try {
                    validateId(record.id)
                    validateName(record.name)
                } catch (error: InstrumentPresetException) {
                    throw AppearanceException(error.error, "Invalid appearance record: ${error.message}",
                        "Use a canonical lowercase UUID and a trimmed name of 1..64 Unicode code points without controls")
                }
            }
            appearanceRequire(result.users.map { it.id }.distinct().size == result.users.size,
                "duplicate_id", "User appearance IDs must be unique", "Use a new UUID for each new appearance")
            appearanceRequire(result.users.map { nameKey(it.name) }.distinct().size == result.users.size,
                "duplicate_name", "User appearance names must be unique without regard to case", "Choose a distinct appearance name")
            val legacyIds = result.legacy.map { it.id }
            appearanceRequire(legacyIds.distinct().size == legacyIds.size &&
                (legacyIds.isEmpty() || legacyIds.toSet() == LEGACY_IDS || legacyIds.toSet() == LEGACY_IDS + LEGACY_CURRENT),
                "invalid_legacy", "Legacy snapshots need all thirteen identities and optionally legacy:current",
                "Supply the complete original migration snapshots without duplicate identities")
            appearanceRequire(result.legacy.all { validUnicode(it.name) }, "invalid_unicode", "Legacy names need valid Unicode")
            appearanceRequire(activeId.isEmpty() || CURATED.any { it.id == activeId } ||
                result.users.any { it.id == activeId } || result.legacy.any { it.id == activeId },
                "invalid_active_id", "Active appearance association must identify an existing record",
                "Select an existing appearance or use an empty association for an unsaved value")
            AppearanceDocumentCodec.encode(result)
            return result
        }
    }
}
