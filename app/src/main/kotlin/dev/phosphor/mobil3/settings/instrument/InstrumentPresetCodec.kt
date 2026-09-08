package dev.phosphor.mobil3.settings.instrument

import dev.phosphor.mobil3.ui.LightRgb
import dev.phosphor.mobil3.ui.LightSettings
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Strict portable documents and metadata-free setup JSON. Neither path applies tuning. */
object InstrumentPresetCodec {
    const val SCHEMA = "phosphor.instrument-presets/1"
    const val MAX_BYTES = 1024 * 1024

    fun encode(collection: InstrumentPresetCollection): String {
        val content = content(collection)
        return bounded(StrictInstrumentJson.encode(content + ("content_sha256" to sha256(StrictInstrumentJson.encode(content)))))
    }

    fun exportRecord(collection: InstrumentPresetCollection, id: String): String =
        encode(InstrumentPresetCollection.of(listOf(collection.record(id))))

    fun canonicalContent(collection: InstrumentPresetCollection): String = StrictInstrumentJson.encode(content(collection))

    fun contentSha256(collection: InstrumentPresetCollection): String = sha256(canonicalContent(collection))

    fun encodeSetup(setup: InstrumentSetup): String = bounded(StrictInstrumentJson.encode(setupObject(setup)))

    fun decodeSetup(json: String): InstrumentSetup = typed {
        setup(StrictInstrumentJson.parse(bounded(json)).obj("setup"))
    }

    fun decode(bytes: ByteArray): InstrumentPresetCollection {
        presetRequire(bytes.size <= MAX_BYTES, "document_too_large", "Instrument document exceeds 1 MiB", "Import a document no larger than 1 MiB")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            throw InstrumentPresetException("invalid_utf8", "Instrument document is not valid UTF-8", "Export the document again as UTF-8")
        }
        return decode(text)
    }

    fun decode(json: String): InstrumentPresetCollection = typed {
        val root = StrictInstrumentJson.parse(bounded(json)).obj("document")
        root.exact("schema", "version", "records", "content_sha256")
        presetRequire(root.text("schema") == SCHEMA && root.int("version") == InstrumentPresetCollection.VERSION,
            "unsupported_schema", "Unsupported instrument preset schema or version", "Use a phosphor.instrument-presets/1 document with version 1")
        val rawRecords = root.array("records")
        presetRequire(rawRecords.size <= InstrumentPresetCollection.MAX_RECORDS, "too_many_records", "Keep at most 64 user presets", "Import fewer records")
        val records = rawRecords.map { raw ->
            val record = raw.obj("record")
            record.exact("id", "name", "setup")
            InstrumentPresetRecord(record.text("id"), record.text("name"), setup(record.getValue("setup").obj("setup")))
        }
        val collection = InstrumentPresetCollection.of(records)
        val digest = root.text("content_sha256")
        presetRequire(digest.matches(Regex("[0-9a-f]{64}")) && digest == contentSha256(collection),
            "checksum_mismatch", "Instrument document checksum does not match its content", "Use an unmodified export or export the original collection again")
        collection
    }

    private fun content(collection: InstrumentPresetCollection): Map<String, Any> = mapOf(
        "schema" to SCHEMA, "version" to InstrumentPresetCollection.VERSION,
        "records" to collection.records.map { mapOf("id" to it.id, "name" to it.name, "setup" to setupObject(it.setup)) })

    private fun setupObject(s: InstrumentSetup): Map<String, Any> = mapOf(
        "mode" to s.mode, "random_mode_armed" to s.randomModeArmed, "random_ban_modes" to s.randomBanModes,
        "geom_fx" to s.geomFx, "geom_amount" to s.geomAmount, "gain" to s.gain, "auto_gain" to s.autoGain,
        "focus" to s.focus, "beam_energy" to s.beamEnergy, "glow" to s.glow,
        "beam_random_armed" to s.beamRandomArmed, "beam_random_min" to s.beamRandomMin,
        "beam_random_max" to s.beamRandomMax, "glow_random_armed" to s.glowRandomArmed,
        "glow_random_min" to s.glowRandomMin, "glow_random_max" to s.glowRandomMax,
        "grid" to s.grid, "grid_data" to s.gridData, "oversample" to s.oversample,
        "light" to mapOf("preset" to s.light.preset, "slots" to s.light.slots.map { it.components() },
            "selected_mask" to s.light.selectedMask, "seconds" to s.light.seconds,
            "clock" to if (s.light.perTrack) "TRACK" else "TIMER", "generated_auto" to s.light.generatedAuto,
            "shuffle" to s.light.shuffle, "random_interval" to s.light.randomInterval,
            "interval_min" to s.light.intervalMin, "interval_max" to s.light.intervalMax))

    private fun setup(s: Map<String, Any>): InstrumentSetup {
        s.exact("mode", "random_mode_armed", "random_ban_modes", "geom_fx", "geom_amount", "gain", "auto_gain",
            "focus", "beam_energy", "glow", "beam_random_armed", "beam_random_min", "beam_random_max",
            "glow_random_armed", "glow_random_min", "glow_random_max", "grid", "grid_data", "oversample", "light")
        val light = s.getValue("light").obj("light")
        light.exact("preset", "slots", "selected_mask", "seconds", "clock", "generated_auto", "shuffle",
            "random_interval", "interval_min", "interval_max")
        val clock = light.text("clock")
        presetRequire(clock == "TIMER" || clock == "TRACK", "invalid_setup", "Light clock must be TIMER or TRACK")
        val slots = light.array("slots")
        presetRequire(slots.size <= 6, "invalid_setup", "Keep at most six saved colors")
        val rgb = slots.map { raw ->
            val triple = raw as? List<*> ?: wrongType("slots[]", "RGB array")
            presetRequire(triple.size == 3, "invalid_setup", "Each saved color needs exactly three components")
            LightRgb(float(triple[0], "red", 0f, 1f), float(triple[1], "green", 0f, 1f), float(triple[2], "blue", 0f, 1f))
        }
        val bans = s.array("random_ban_modes")
        presetRequire(bans.size <= 11, "invalid_setup", "Banned modes must fit within 0..10")
        return InstrumentSetup(s.int("mode"), s.bool("random_mode_armed"), bans.map { int(it, "random_ban_modes[]") },
            s.int("geom_fx"), s.float("geom_amount", 0f, 1f), s.float("gain", 0.1f, 7f), s.bool("auto_gain"),
            s.float("focus", 0.3f, 3f), s.float("beam_energy", 1f, 30f), s.float("glow", 0f, 0.98f),
            s.bool("beam_random_armed"), s.float("beam_random_min", 1f, 30f), s.float("beam_random_max", 1f, 30f),
            s.bool("glow_random_armed"), s.float("glow_random_min", 0f, 0.98f), s.float("glow_random_max", 0f, 0.98f),
            s.bool("grid"), s.bool("grid_data"), s.int("oversample"),
            LightSettings(rgb, light.int("selected_mask"), light.int("preset"), light.float("seconds", 0.1f, 60f),
                clock == "TRACK", light.bool("generated_auto"), light.bool("shuffle"), light.bool("random_interval"),
                light.float("interval_min", 0.1f, 60f), light.float("interval_max", 0.1f, 60f)))
    }

    private fun bounded(text: String): String {
        presetRequire(text.length <= MAX_BYTES, "document_too_large", "Instrument document exceeds 1 MiB", "Import a document no larger than 1 MiB")
        presetRequire(validUnicode(text), "invalid_unicode", "Instrument document contains an unpaired surrogate", "Export valid Unicode text")
        presetRequire(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, "document_too_large", "Instrument document exceeds 1 MiB", "Import a document no larger than 1 MiB")
        return text
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    private inline fun <T> typed(block: () -> T): T = try { block() } catch (error: InstrumentPresetException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw InstrumentPresetException("invalid_setup", error.message ?: "Invalid instrument setup", "Correct the complete setup and export it again")
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any.obj(name: String): Map<String, Any> = this as? Map<String, Any> ?: wrongType(name, "object")
    private fun Map<String, Any>.exact(vararg fields: String) {
        presetRequire(keys == fields.toSet(), "invalid_fields", "Object fields must exactly match the instrument schema", "Restore every required field and remove unknown fields")
    }
    private fun Map<String, Any>.text(key: String): String = getValue(key) as? String ?: wrongType(key, "string")
    private fun Map<String, Any>.bool(key: String): Boolean = getValue(key) as? Boolean ?: wrongType(key, "boolean")
    private fun Map<String, Any>.array(key: String): List<Any> {
        @Suppress("UNCHECKED_CAST")
        return getValue(key) as? List<Any> ?: wrongType(key, "array")
    }
    private fun Map<String, Any>.int(key: String): Int = int(getValue(key), key)
    private fun int(raw: Any, key: String): Int {
        val number = raw as? StrictInstrumentJson.Number ?: wrongType(key, "integer")
        return number.text.takeIf { it.matches(Regex("-?(0|[1-9][0-9]*)")) }?.toIntOrNull()
            ?: wrongType(key, "32-bit integer with integer JSON syntax")
    }
    private fun Map<String, Any>.float(key: String, min: Float, max: Float): Float = float(getValue(key), key, min, max)
    private fun float(raw: Any?, key: String, min: Float, max: Float): Float {
        val number = raw as? StrictInstrumentJson.Number ?: wrongType(key, "number")
        val decimal = number.text.toBigDecimalOrNull() ?: wrongType(key, "finite number")
        presetRequire(decimal >= BigDecimal(min.toString()) && decimal <= BigDecimal(max.toString()),
            "invalid_setup", "$key must be finite within $min..$max")
        val result = number.text.toFloat()
        presetRequire(result.isFinite(), "invalid_setup", "$key must be finite")
        return result
    }
    private fun wrongType(key: String, type: String): Nothing =
        throw InstrumentPresetException("wrong_type", "$key must be a $type", "Use the exact typed instrument document schema")
}

/** Small bounded JSON grammar. org.json's permissive syntax is not a portable validation policy. */
internal object StrictInstrumentJson {
    data class Number(val text: String)
    private data object Null

    fun parse(text: String): Any = Parser(text).parse()

    fun encode(value: Any): String = when (value) {
        is Map<*, *> -> value.entries.sortedBy { it.key as String }.joinToString(",", "{", "}") {
            encode(it.key as String) + ":" + encode(it.value!!)
        }
        is List<*> -> value.joinToString(",", "[", "]") { encode(it!!) }
        is String -> buildString {
            append('"')
            value.forEach { char ->
                append(when (char) {
                    '"' -> "\\\""
                    '\\' -> "\\\\"
                    '\b' -> "\\b"
                    '\u000c' -> "\\f"
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    '\t' -> "\\t"
                    else -> if (char.code < 32) "\\u" + char.code.toString(16).padStart(4, '0') else char.toString()
                })
            }
            append('"')
        }
        is Float -> when {
            value.toRawBits() == Int.MIN_VALUE -> "-0.0"
            value == 0f -> "0"
            else -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        }
        is Int, is Boolean -> value.toString()
        else -> error("Unsupported canonical value")
    }

    private class Parser(val text: String) {
        var position = 0
        var nodes = 0
        fun fail(): Nothing = throw InstrumentPresetException("invalid_json", "Instrument document is not strict bounded JSON",
            "Choose a complete UTF-8 JSON export without duplicate keys or trailing content")
        fun whitespace() { while (position < text.length && text[position] in " \t\r\n") position++ }
        fun take(char: Char): Boolean {
            if (position < text.length && text[position] == char) { position++; return true }
            return false
        }
        fun parse(): Any {
            val result = value(0)
            whitespace()
            if (position != text.length) fail()
            return result
        }
        fun value(depth: Int): Any {
            if (depth > 12 || ++nodes > 20_000) fail()
            whitespace()
            if (position == text.length) fail()
            return when (text[position]) {
                '{' -> {
                    position++
                    val fields = linkedMapOf<String, Any>()
                    whitespace()
                    if (!take('}')) {
                        do {
                            whitespace()
                            if (position == text.length || text[position] != '"') fail()
                            val key = string()
                            if (key in fields) fail()
                            whitespace()
                            if (!take(':')) fail()
                            fields[key] = value(depth + 1)
                            whitespace()
                        } while (take(','))
                        if (!take('}')) fail()
                    }
                    fields
                }
                '[' -> {
                    position++
                    val items = mutableListOf<Any>()
                    whitespace()
                    if (!take(']')) {
                        do { items += value(depth + 1); whitespace() } while (take(','))
                        if (!take(']')) fail()
                    }
                    items
                }
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", Null)
                '-', in '0'..'9' -> number()
                else -> fail()
            }
        }
        fun literal(token: String, result: Any): Any {
            if (!text.startsWith(token, position)) fail()
            position += token.length
            return result
        }
        fun number(): Number {
            val start = position
            take('-')
            if (!take('0')) digits()
            if (take('.')) digits()
            if (take('e') || take('E')) {
                if (!take('+')) take('-')
                digits()
            }
            return Number(text.substring(start, position))
        }
        fun digits() {
            val start = position
            while (position < text.length && text[position] in '0'..'9') position++
            if (position == start) fail()
        }
        fun string(): String {
            position++
            val result = StringBuilder()
            while (position < text.length) {
                val char = text[position++]
                if (char == '"') {
                    val string = result.toString()
                    if (!validUnicode(string)) fail()
                    return string
                }
                if (char.code < 32) fail()
                if (char != '\\') { result.append(char); continue }
                if (position == text.length) fail()
                result.append(when (val escaped = text[position++]) {
                    '"', '\\', '/' -> escaped
                    'b' -> '\b'
                    'f' -> '\u000c'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'u' -> {
                        if (position + 4 > text.length) fail()
                        val hex = text.substring(position, position + 4)
                        if (!hex.all { it in "0123456789abcdefABCDEF" }) fail()
                        position += 4
                        hex.toInt(16).toChar()
                    }
                    else -> fail()
                })
            }
            fail()
        }
    }
}
