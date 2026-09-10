package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.appearance.AppearanceDocument
import dev.phosphor.mobil3.settings.appearance.AppearanceDocumentCodec
import dev.phosphor.mobil3.settings.appearance.AppearanceException
import dev.phosphor.mobil3.settings.appearance.AppearanceProvenance
import dev.phosphor.mobil3.settings.appearance.AppearanceRecord
import dev.phosphor.mobil3.settings.appearance.CuratedAppearances

/** The actual legacy resolver supplies every migrated color and coupled style. No storage writes. */
internal object AppearanceMigration {
    const val KEY = "appearance_state"
    val legacyKeys: Set<String> = setOf("room", "ov_char", "ov_motion", "ov_radius", "ov_desig")
    private val userSettingKeys = setOf("mode", "gain", "fps", "oversample", "auto_gain", "default_source")

    fun stored(values: Map<String, *>): AppearanceDocument? {
        if (KEY !in values) return null
        val encoded = values[KEY] as? String ?: invalid("Stored appearance is not a string")
        return AppearanceDocumentCodec.decode(encoded)
    }

    fun initial(values: Map<String, *>): AppearanceDocument {
        stored(values)?.let { return it }
        val input = legacyInput(values)
        val rows = Rooms.map { room ->
            AppearanceRecord("legacy:${room.id}", "Legacy · ${room.label}",
                AppearancePalette.legacy(LegacyAppearanceInput(room.id)).value)
        }
        if (values.keys.none(legacyKeys::contains)) {
            val tactile = values.keys.none { it in userSettingKeys }
            val active = if (tactile) CuratedAppearances.amoled.copy(lookVersion = 2)
                else CuratedAppearances.amoled
            return AppearanceDocument.of(active = active,
                activeId = "curated:amoled", legacy = rows)
        }
        val resolved = try { AppearancePalette.legacy(input) } catch (error: IllegalArgumentException) {
            invalid(error.message ?: "Legacy appearance cannot be represented without changes")
        }
        val base = rows.single { it.id == "legacy:${resolved.resolvedId}" }
        val overridden = resolved.value != base.value
        val current = if (overridden) rows + AppearanceRecord("legacy:current",
            "Legacy · current overrides", resolved.value) else rows
        return AppearanceDocument.of(active = resolved.value,
            activeId = if (overridden) "legacy:current" else base.id,
            legacy = current, provenance = input.provenance())
    }

    /** Omission never parses old bytes. A complete replacement never consults old bytes. */
    fun merge(imported: Map<String, Any>, existing: Map<String, *>): Map<String, Any> {
        if (KEY in imported) {
            stored(imported)
            return imported.toMap()
        }
        if (imported.keys.none(legacyKeys::contains)) return imported.toMap()
        val before = stored(existing)
        val combined = existing.filterKeys { it != KEY } + imported
        val migrated = initial(combined)
        if (before == null) return imported + (KEY to AppearanceDocumentCodec.encode(migrated))
        val legacy = before.legacy.ifEmpty { migrated.legacy }
        val matching = legacy.firstOrNull { it.id == migrated.activeId && it.value == migrated.active }
        val replacement = AppearanceDocument.of(active = migrated.active,
            activeId = matching?.id ?: "", users = before.users, legacy = legacy,
            provenance = migrated.provenance)
        return imported + (KEY to AppearanceDocumentCodec.encode(replacement))
    }

    private fun legacyInput(values: Map<String, *>): LegacyAppearanceInput {
        fun integer(key: String): Int? = if (key !in values) null else
            values[key] as? Int ?: invalid("Legacy $key is not a signed integer")
        val room = if ("room" !in values) null else
            values["room"] as? String ?: invalid("Legacy room is not a string")
        return LegacyAppearanceInput(room, integer("ov_char"), integer("ov_motion"),
            integer("ov_radius"), integer("ov_desig"))
    }

    private fun LegacyAppearanceInput.provenance() = AppearanceProvenance(room, character, motion, radius, designators)

    private fun invalid(message: String): Nothing = throw AppearanceException("legacy_appearance_invalid",
        message, "Keep the original settings or explicitly replace the complete appearance with a valid saved state")
}
