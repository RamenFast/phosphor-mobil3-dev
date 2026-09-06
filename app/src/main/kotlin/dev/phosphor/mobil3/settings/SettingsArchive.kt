package dev.phosphor.mobil3.settings

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

/**
 * Portable, inert Phosphor settings archive.
 *
 * Only explicitly declared user settings enter the archive. Runtime metadata, endpoints,
 * consent tokens, media paths, purchase data, and authorization material never do.
 */
object SettingsArchive {
    const val SCHEMA = "phosphor.settings/1"
    const val MAX_BYTES = 1024 * 1024

    data class ExportResult(
        val json: String,
        val exportedKeys: List<String>,
        val skippedKeys: List<String>,
        val contentSha256: String,
    )

    data class ImportResult(
        val sourcePackage: String,
        val sourceVersion: String,
        val sourceDistribution: String,
        val exportedAt: String,
        val values: Map<String, Any>,
        val skippedKeys: List<String>,
        val contentSha256: String,
    )

    class ArchiveException(
        val error: String,
        override val message: String,
        val fix: String,
    ) : IllegalArgumentException(message)

    private enum class Kind { BOOLEAN, INT, FLOAT, STRING }

    private data class Spec(
        val kind: Kind,
        val valid: (Any) -> Boolean = { true },
    )

    private fun intRange(min: Int, max: Int) = Spec(Kind.INT) { it as Int in min..max }
    private fun floatRange(min: Float, max: Float) = Spec(Kind.FLOAT) {
        val value = it as Float
        value.isFinite() && value in min..max
    }
    private fun string(max: Int = 4096, valid: (String) -> Boolean = { true }) =
        Spec(Kind.STRING) { value ->
            val text = value as String
            text.length <= max && valid(text)
        }

    private val specs: Map<String, Spec> = mapOf(
        "mode" to intRange(0, 10),
        "random_mode_armed" to Spec(Kind.BOOLEAN),
        "random_ban_modes" to string(128) { text ->
            text.isBlank() || text.split(',').all { token ->
                token.toIntOrNull()?.let { it in 0..10 } == true
            }
        },
        "beam" to intRange(0, 8),
        "fps" to Spec(Kind.INT) { it as Int in setOf(-1, 0, 60, 90, 120) },
        "oversample" to Spec(Kind.INT) { it as Int in setOf(1, 2, 4) },
        "gain" to floatRange(0.1f, 7f),
        "beam_energy" to floatRange(1f, 30f),
        "glow" to floatRange(0f, 0.98f),
        "beam_random_armed" to Spec(Kind.BOOLEAN),
        "beam_random_range" to rangeString(1f, 30f),
        "glow_random_armed" to Spec(Kind.BOOLEAN),
        "glow_random_range" to rangeString(0f, 0.98f),
        "geom_fx" to intRange(0, 4),
        "geom_amount" to floatRange(0f, 1f),
        "grid" to Spec(Kind.BOOLEAN),
        "focus" to floatRange(0.3f, 3f),
        "room" to string(80) { it.matches(Regex("[a-zA-Z0-9_.-]+")) },
        "auto_gain" to Spec(Kind.BOOLEAN),
        "hud_mode" to intRange(0, 2),
        "band_mode" to intRange(0, 2),
        "fullscreen" to Spec(Kind.BOOLEAN),
        "linger_background" to Spec(Kind.BOOLEAN),
        "double_tap_playback" to Spec(Kind.BOOLEAN),
        "controls_always_visible" to Spec(Kind.BOOLEAN),
        "pip_auto_enter" to Spec(Kind.BOOLEAN),
        "view_lock" to Spec(Kind.BOOLEAN),
        "scope_rotation_locked" to Spec(Kind.BOOLEAN),
        "scope_locked_orientation" to intRange(-1, 14),
        "ui_placement_locked" to Spec(Kind.BOOLEAN),
        "ui_locked_landscape" to Spec(Kind.BOOLEAN),
        "ui_locked_orientation" to intRange(-1, 14),
        "remote_latency_mode" to intRange(0, 2),
        "amoled_seen" to Spec(Kind.BOOLEAN),
        "bestiary_found" to Spec(Kind.BOOLEAN),
        "ov_char" to intRange(-1, 3),
        "ov_motion" to intRange(-1, 3),
        "ov_radius" to intRange(-1, 64),
        "ov_desig" to intRange(-1, 1),
        "custom_count" to intRange(0, 3),
        "custom_rgb" to string(512) { text ->
            val values = text.split(',').mapNotNull { it.toFloatOrNull() }
            values.size == 9 && values.all { it.isFinite() && it in 0f..1f }
        },
        "cycle_seconds" to floatRange(0.25f, 30f),
        "cycle_per_track" to Spec(Kind.BOOLEAN),
    )

    private fun rangeString(min: Float, max: Float) = string(80) { text ->
        val values = text.split(',').mapNotNull { it.toFloatOrNull() }
        values.size == 2 && values[0].isFinite() && values[1].isFinite() &&
            values[0] in min..max && values[1] in values[0]..max
    }

    fun export(
        sourcePackage: String,
        sourceVersion: String,
        sourceDistribution: String,
        exportedAt: String,
        allPreferences: Map<String, *>,
    ): ExportResult {
        requireMetadata(sourcePackage, sourceVersion, sourceDistribution, exportedAt)
        val accepted = linkedMapOf<String, Any>()
        val skipped = mutableListOf<String>()
        allPreferences.toSortedMap().forEach { (key, raw) ->
            val spec = specs[key]
            if (spec == null || raw == null) {
                skipped += key
                return@forEach
            }
            val value = normalizeKnown(key, raw, spec)
            accepted[key] = value
        }
        val canonical = canonicalPayload(
            sourcePackage,
            sourceVersion,
            sourceDistribution,
            exportedAt,
            accepted,
        )
        val digest = sha256(canonical)
        val settings = JSONObject()
        accepted.forEach { (key, value) -> settings.put(key, value) }
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("source_package", sourcePackage)
            .put("source_version", sourceVersion)
            .put("source_distribution", sourceDistribution)
            .put("exported_at", exportedAt)
            .put("settings", settings)
            .put("content_sha256", digest)
        val json = root.toString(2) + "\n"
        check(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        return ExportResult(json, accepted.keys.toList(), skipped.sorted(), digest)
    }

    fun decode(json: String): ImportResult {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw ArchiveException(
                "archive_too_large",
                "Settings archive exceeds $MAX_BYTES bytes",
                "Export settings again from Phosphor without adding unrelated content",
            )
        }
        val root = try {
            JSONObject(json)
        } catch (error: Exception) {
            throw ArchiveException("invalid_json", "Settings archive is not valid JSON", "Choose an unmodified .phossettings export")
        }
        val allowedRootKeys = setOf(
            "schema",
            "source_package",
            "source_version",
            "source_distribution",
            "exported_at",
            "settings",
            "content_sha256",
        )
        val unknownRootKeys = root.keys().asSequence().filterNot(allowedRootKeys::contains).sorted().toList()
        if (unknownRootKeys.isNotEmpty()) {
            throw ArchiveException(
                "unknown_archive_field",
                "Settings archive contains unsupported top-level fields: ${unknownRootKeys.joinToString()}",
                "Use an original Phosphor export without added metadata or executable content",
            )
        }
        val schema = root.optString("schema")
        if (schema != SCHEMA) {
            throw ArchiveException(
                "unsupported_schema",
                "Unsupported settings schema '$schema'",
                "Import a $SCHEMA archive or upgrade Phosphor for a newer major schema",
            )
        }
        val sourcePackage = root.requiredText("source_package", 200)
        val sourceVersion = root.requiredText("source_version", 80)
        val sourceDistribution = root.requiredText("source_distribution", 40)
        val exportedAt = root.requiredText("exported_at", 80)
        requireMetadata(sourcePackage, sourceVersion, sourceDistribution, exportedAt)
        val settings = root.optJSONObject("settings") ?: throw ArchiveException(
            "settings_missing", "Settings archive has no settings object", "Export settings again from Phosphor"
        )
        if (settings.length() > 512) {
            throw ArchiveException("too_many_settings", "Settings archive has too many fields", "Use an unmodified Phosphor export")
        }

        val rawValues = linkedMapOf<String, Any>()
        settings.keys().asSequence().sorted().forEach { key ->
            val raw = settings.get(key)
            if (raw == JSONObject.NULL || raw is JSONObject || raw is JSONArray) {
                throw ArchiveException(
                    "non_inert_value",
                    "Setting '$key' contains a nested or executable-shaped value",
                    "Remove the field and export again from Phosphor",
                )
            }
            rawValues[key] = raw
        }
        val canonical = canonicalPayload(
            sourcePackage,
            sourceVersion,
            sourceDistribution,
            exportedAt,
            rawValues,
        )
        val actualDigest = sha256(canonical)
        val expectedDigest = root.optString("content_sha256").lowercase()
        if (expectedDigest.length != 64 || !MessageDigest.isEqual(
                actualDigest.toByteArray(Charsets.US_ASCII),
                expectedDigest.toByteArray(Charsets.US_ASCII),
            )
        ) {
            throw ArchiveException(
                "checksum_mismatch",
                "Settings archive content hash does not match",
                "Use the original export; do not edit the archive by hand",
            )
        }

        val accepted = linkedMapOf<String, Any>()
        val skipped = mutableListOf<String>()
        rawValues.forEach { (key, raw) ->
            val spec = specs[key]
            if (spec == null) {
                skipped += key
            } else {
                accepted[key] = normalizeKnown(key, raw, spec)
            }
        }
        return ImportResult(
            sourcePackage = sourcePackage,
            sourceVersion = sourceVersion,
            sourceDistribution = sourceDistribution,
            exportedAt = exportedAt,
            values = accepted,
            skippedKeys = skipped.sorted(),
            contentSha256 = actualDigest,
        )
    }

    private fun normalizeKnown(key: String, raw: Any, spec: Spec): Any {
        val value: Any = try {
            when (spec.kind) {
                Kind.BOOLEAN -> raw as? Boolean ?: error("not boolean")
                Kind.INT -> {
                    val decimal = raw.asDecimal()
                    decimal.intValueExact()
                }
                Kind.FLOAT -> raw.asDecimal().toFloat().also { check(it.isFinite()) }
                Kind.STRING -> raw as? String ?: error("not string")
            }
        } catch (_: Exception) {
            throw ArchiveException(
                "invalid_setting_type",
                "Setting '$key' has the wrong value type",
                "Export settings again from a supported Phosphor build",
            )
        }
        if (!spec.valid(value)) {
            throw ArchiveException(
                "invalid_setting_value",
                "Setting '$key' is outside its supported range or format",
                "Reset that setting in the source app and export again",
            )
        }
        return value
    }

    private fun Any.asDecimal(): BigDecimal = when (this) {
        is BigDecimal -> this
        is Number -> BigDecimal(toString())
        else -> error("not number")
    }

    private fun JSONObject.requiredText(key: String, max: Int): String {
        val value = optString(key)
        if (value.isBlank() || value.length > max) {
            throw ArchiveException("metadata_invalid", "Archive field '$key' is missing or invalid", "Export settings again from Phosphor")
        }
        return value
    }

    private fun requireMetadata(pkg: String, version: String, distribution: String, exportedAt: String) {
        if (!pkg.matches(Regex("[a-zA-Z0-9_.-]{1,200}"))) {
            throw ArchiveException("metadata_invalid", "Source package is invalid", "Export settings again from Phosphor")
        }
        if (
            version.isBlank() || version.length > 80 ||
            distribution !in setOf("debug", "release", "play", "fortress", "local_dev")
        ) {
            throw ArchiveException("metadata_invalid", "Source version or distribution is invalid", "Export settings again from Phosphor")
        }
        if (exportedAt.isBlank() || exportedAt.length > 80 || runCatching { Instant.parse(exportedAt) }.isFailure) {
            throw ArchiveException("metadata_invalid", "Export timestamp is invalid", "Export settings again from Phosphor")
        }
    }

    private fun canonicalPayload(
        sourcePackage: String,
        sourceVersion: String,
        sourceDistribution: String,
        exportedAt: String,
        values: Map<String, Any>,
    ): String = buildString {
        append('{')
        append("\"exported_at\":").append(JSONObject.quote(exportedAt)).append(',')
        append("\"schema\":").append(JSONObject.quote(SCHEMA)).append(',')
        append("\"settings\":").append(canonicalObject(values)).append(',')
        append("\"source_distribution\":").append(JSONObject.quote(sourceDistribution)).append(',')
        append("\"source_package\":").append(JSONObject.quote(sourcePackage)).append(',')
        append("\"source_version\":").append(JSONObject.quote(sourceVersion))
        append('}')
    }

    private fun canonicalObject(values: Map<String, Any>): String = values.toSortedMap().entries.joinToString(
        prefix = "{", postfix = "}", separator = ","
    ) { (key, value) -> JSONObject.quote(key) + ":" + canonicalValue(value) }

    private fun canonicalValue(value: Any): String = when (value) {
        is Boolean -> value.toString()
        is String -> JSONObject.quote(value)
        is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        else -> throw ArchiveException(
            "non_inert_value", "Archive contains an unsupported value type", "Use an unmodified Phosphor export"
        )
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
