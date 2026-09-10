package dev.phosphor.mobil3.settings.appearance

import dev.phosphor.mobil3.settings.instrument.InstrumentPresetException
import dev.phosphor.mobil3.settings.instrument.StrictInstrumentJson
import dev.phosphor.mobil3.settings.instrument.validUnicode
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Strict complete replacement only. No old bytes, persistence or runtime owner is consulted. */
object AppearanceDocumentCodec {
    const val SCHEMA = "phosphor.appearance/1"
    const val VERSION = 1
    const val MAX_BYTES = 128 * 1024

    fun encode(document: AppearanceDocument): String {
        val content = content(document)
        return bounded(StrictInstrumentJson.encode(content + ("content_sha256" to sha256(StrictInstrumentJson.encode(content)))))
    }

    fun canonicalContent(document: AppearanceDocument): String = StrictInstrumentJson.encode(content(document))
    fun contentSha256(document: AppearanceDocument): String = sha256(canonicalContent(document))

    fun decode(bytes: ByteArray): AppearanceDocument {
        appearanceRequire(bytes.size <= MAX_BYTES, "document_too_large", "Appearance document exceeds 128 KiB",
            "Import a complete appearance document no larger than 128 KiB")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw AppearanceException("invalid_utf8", "Appearance document is not valid UTF-8", "Export the appearance again as UTF-8")
        }
        return decode(text)
    }

    fun decode(json: String): AppearanceDocument = typed {
        val root = StrictInstrumentJson.parse(bounded(json)).obj("document")
        root.exact("schema", "version", "active", "active_id", "users", "legacy", "provenance", "content_sha256")
        appearanceRequire(root.text("schema") == SCHEMA && root.int("version") == VERSION,
            "unsupported_schema", "Unsupported appearance schema or version", "Use phosphor.appearance/1 with integer version 1")
        val users = root.array("users")
        val legacy = root.array("legacy")
        appearanceRequire(users.size <= AppearanceDocument.MAX_USERS, "too_many_records", "Keep at most 32 user appearances")
        appearanceRequire(legacy.size == 0 || legacy.size == 13 || legacy.size == 14,
            "invalid_legacy", "Legacy snapshots need thirteen records and optionally the current appearance")
        val original = root.getValue("provenance").obj("provenance")
        appearanceRequire(original.keys.all { it in setOf("room", "ov_char", "ov_motion", "ov_radius", "ov_desig") },
            "invalid_fields", "Provenance contains an unknown original key", "Keep only original room and appearance override keys")
        fun optionalInt(key: String): Int? = if (key in original) original.int(key) else null
        val document = AppearanceDocument.of(value(root.getValue("active")), root.text("active_id"),
            users.map(::record), legacy.map(::record), AppearanceProvenance(
                if ("room" in original) original.text("room") else null,
                optionalInt("ov_char"), optionalInt("ov_motion"), optionalInt("ov_radius"), optionalInt("ov_desig")))
        val digest = root.text("content_sha256")
        appearanceRequire(digest.matches(Regex("[0-9a-f]{64}")) && digest == contentSha256(document),
            "checksum_mismatch", "Appearance checksum does not match its complete authored content",
            "Use an unmodified appearance export or export the original state again")
        document
    }

    private fun record(raw: Any): AppearanceRecord {
        val fields = raw.obj("record")
        fields.exact("id", "name", "value")
        return AppearanceRecord(fields.text("id"), fields.text("name"), value(fields.getValue("value")))
    }

    private fun content(d: AppearanceDocument): Map<String, Any> = mapOf(
        "schema" to SCHEMA, "version" to VERSION, "active" to valueObject(d.active), "active_id" to d.activeId,
        "users" to d.users.map(::recordObject), "legacy" to d.legacy.map(::recordObject),
        "provenance" to buildMap<String, Any> {
            d.provenance.room?.let { put("room", it) }
            d.provenance.character?.let { put("ov_char", it) }
            d.provenance.motion?.let { put("ov_motion", it) }
            d.provenance.radius?.let { put("ov_radius", it) }
            d.provenance.designators?.let { put("ov_desig", it) }
        })

    private fun recordObject(r: AppearanceRecord): Map<String, Any> = mapOf("id" to r.id, "name" to r.name, "value" to valueObject(r.value))

    private fun valueObject(v: AppearanceValue): Map<String, Any> = mapOf(
        "colors" to with(v.colors) { mapOf("plane" to plane, "surface" to surface, "surface2" to surface2,
            "ink" to ink, "ink2" to ink2, "muted" to muted, "line" to line, "line_strong" to lineStrong,
            "accent" to accent, "on_accent" to onAccent, "stone" to stone, "stone_hi" to stoneHi, "stone_lo" to stoneLo) },
        "dark" to v.dark, "accent_follows_beam" to v.accentFollowsBeam, "character" to v.character.name,
        "motion" to v.motion.name, "duration_scale" to v.durationScale, "density_scale" to v.densityScale,
        "radius_dp" to v.radiusDp, "mono_prose" to v.monoProse, "designators" to v.designators,
        "panel_alpha_scale" to v.panelAlphaScale) +
        if (v.lookVersion == 2) mapOf("look_version" to 2) else emptyMap()

    private fun value(raw: Any): AppearanceValue {
        val v = raw.obj("value")
        (v - "look_version").exact("colors", "dark", "accent_follows_beam", "character", "motion", "duration_scale", "density_scale",
            "radius_dp", "mono_prose", "designators", "panel_alpha_scale")
        val lookVersion = if ("look_version" in v) {
            appearanceRequire(v.int("look_version") == 2, "invalid_value",
                "Optional look_version must be integer 2", "Omit look_version for legacy appearance or use integer 2")
            2
        } else 1
        val c = v.getValue("colors").obj("colors")
        c.exact("plane", "surface", "surface2", "ink", "ink2", "muted", "line", "line_strong", "accent", "on_accent",
            "stone", "stone_hi", "stone_lo")
        return AppearanceValue(AppearanceColors(c.int("plane"), c.int("surface"), c.int("surface2"), c.int("ink"),
            c.int("ink2"), c.int("muted"), c.int("line"), c.int("line_strong"), c.int("accent"), c.int("on_accent"),
            c.int("stone"), c.int("stone_hi"), c.int("stone_lo")), v.bool("dark"), v.bool("accent_follows_beam"),
            enumValueOf<AppearanceCharacter>(v.text("character")), enumValueOf<AppearanceMotion>(v.text("motion")),
            v.float("duration_scale", "0.25", "2"), v.float("density_scale", "0.85", "1.25"), v.int("radius_dp"),
            v.bool("mono_prose"), v.bool("designators"), v.float("panel_alpha_scale", "0.2", "1"), lookVersion)
    }

    private fun bounded(text: String): String {
        appearanceRequire(text.length <= MAX_BYTES, "document_too_large", "Appearance document exceeds 128 KiB",
            "Import a complete appearance document no larger than 128 KiB")
        appearanceRequire(validUnicode(text), "invalid_unicode", "Appearance document contains an unpaired surrogate",
            "Export the appearance as valid Unicode")
        appearanceRequire(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, "document_too_large", "Appearance document exceeds 128 KiB",
            "Import a complete appearance document no larger than 128 KiB")
        return text
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    private inline fun <T> typed(block: () -> T): T = try { block() } catch (error: AppearanceException) {
        throw error
    } catch (error: InstrumentPresetException) {
        throw AppearanceException(error.error, "Appearance document is not strict bounded JSON",
            "Use a complete UTF-8 appearance export without duplicate keys or trailing content")
    } catch (error: IllegalArgumentException) {
        throw AppearanceException("invalid_value", "Invalid authored appearance: ${error.message}",
            "Correct appearance fields, enum names and finite numeric ranges")
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any.obj(key: String): Map<String, Any> = this as? Map<String, Any> ?: wrongType(key, "object")
    private fun Map<String, Any>.exact(vararg fields: String) {
        appearanceRequire(keys == fields.toSet(), "invalid_fields", "Object fields must exactly match the appearance schema",
            "Restore every required appearance field and remove unknown fields")
    }
    private fun Map<String, Any>.text(key: String): String = getValue(key) as? String ?: wrongType(key, "string")
    private fun Map<String, Any>.bool(key: String): Boolean = getValue(key) as? Boolean ?: wrongType(key, "Boolean")
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.array(key: String): List<Any> = getValue(key) as? List<Any> ?: wrongType(key, "array")
    private fun Map<String, Any>.int(key: String): Int {
        val number = getValue(key) as? StrictInstrumentJson.Number ?: wrongType(key, "integer")
        return number.text.takeIf { it.matches(Regex("-?(0|[1-9][0-9]*)")) }?.toIntOrNull()
            ?: wrongType(key, "signed 32-bit integer with integer JSON syntax")
    }
    private fun Map<String, Any>.float(key: String, minimum: String, maximum: String): Float {
        val number = getValue(key) as? StrictInstrumentJson.Number ?: wrongType(key, "number")
        val decimal = number.text.toBigDecimalOrNull() ?: wrongType(key, "finite number")
        appearanceRequire(decimal >= BigDecimal(minimum) && decimal <= BigDecimal(maximum),
            "invalid_value", "$key must be finite within $minimum..$maximum")
        val result = number.text.toFloat()
        appearanceRequire(result.isFinite(), "invalid_value", "$key must be finite")
        return result
    }
    private fun wrongType(key: String, type: String): Nothing = throw AppearanceException("wrong_type",
        "$key must be a $type", "Use the exact typed appearance document schema")
}
