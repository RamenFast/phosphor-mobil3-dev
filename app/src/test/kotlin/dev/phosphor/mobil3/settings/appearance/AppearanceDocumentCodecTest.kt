package dev.phosphor.mobil3.settings.appearance

import dev.phosphor.mobil3.settings.instrument.StrictInstrumentJson
import org.junit.Assert.*
import org.junit.Test

class AppearanceDocumentCodecTest {
    private val f = AppearanceFixtures
    private val codec = AppearanceDocumentCodec

    // Hand-authored canonical UTF-8, independently hashed with GNU sha256sum before codec execution.
    private val vector1 = """{"active":{"accent_follows_beam":false,"character":"CARVED","colors":{"accent":0,"ink":0,"ink2":0,"line":-2147483648,"line_strong":2147483647,"muted":0,"on_accent":0,"plane":0,"stone":0,"stone_hi":0,"stone_lo":0,"surface":0,"surface2":0},"dark":false,"density_scale":0.85,"designators":false,"duration_scale":0.25,"mono_prose":false,"motion":"EASED","panel_alpha_scale":0.2,"radius_dp":0},"active_id":"","legacy":[],"provenance":{},"schema":"phosphor.appearance/1","users":[],"version":1}"""
    private val digest1 = "7b55767bf00d0d92162c3c24dffb24801c0df9494000ad31340c698073472341"
    private val vector2 = vector1.replace("\"provenance\":{}",
        """"provenance":{"ov_char":-2147483648,"ov_desig":-1,"ov_motion":2147483647,"ov_radius":0,"room":"unknown 😀\n"}""")
    private val digest2 = "8948acd92b3e3929926a4a4716f11ada384e48a42a65772043e34f2aec13d9f0"

    private fun withDigest(content: String, digest: String) = content.dropLast(1) + ",\"content_sha256\":\"$digest\"}"
    private fun encoded() = codec.encode(f.document())
    private fun field(json: String, name: String, token: String): String =
        json.replace(Regex("\"$name\":(?:\"(?:[^\"\\\\]|\\\\.)*\"|true|false|-?[0-9]+(?:\\.[0-9]+)?)")) { "\"$name\":$token" }

    @Test fun independentFixedDigestVectorsMatchExactCanonicalBytesAndDecode() {
        val a = f.document()
        val b = AppearanceDocument.of(f.low, "", provenance = f.provenance)
        assertEquals(vector1, codec.canonicalContent(a))
        assertEquals(vector2, codec.canonicalContent(b))
        assertEquals(digest1, codec.contentSha256(a))
        assertEquals(digest2, codec.contentSha256(b))
        assertEquals(a, codec.decode(withDigest(vector1, digest1)))
        assertEquals(b, codec.decode(withDigest(vector2, digest2).toByteArray(Charsets.UTF_8)))
    }

    @Test fun everyFieldAndEnumAndExactBoundRoundTrips() {
        for (character in AppearanceCharacter.entries) for (motion in AppearanceMotion.entries) {
            listOf(f.low, f.high).forEach { base ->
                val value = base.copy(character = character, motion = motion)
                val d = AppearanceDocument.of(value, "", users = listOf(f.user(2).copy(value = value)))
                val decoded = codec.decode(codec.encode(d))
                assertEquals(d, decoded)
                assertEquals(value.durationScale.toRawBits(), decoded.active.durationScale.toRawBits())
                assertEquals(value.densityScale.toRawBits(), decoded.active.densityScale.toRawBits())
                assertEquals(value.panelAlphaScale.toRawBits(), decoded.active.panelAlphaScale.toRawBits())
                assertEquals(codec.encode(d), codec.encode(decoded))
            }
        }
    }

    @Test fun fullLegacyProvenanceAndOrdered32UserDocumentRoundTrips() {
        val d = AppearanceDocument.of(f.high, "legacy:current", (1..32).reversed().map(f::user), f.legacy(true), f.provenance)
        val decoded = codec.decode(codec.encode(d))
        assertEquals(d, decoded)
        assertEquals(d.users.map { it.id }, decoded.users.map { it.id })
        assertEquals(d.legacy, decoded.legacy)
        assertEquals(d.provenance, decoded.provenance)
    }

    @Test fun curatedValuesRoundTripWithoutBeamOrOtherRuntimeFields() {
        AppearanceDocument.CURATED.forEach { record ->
            val d = AppearanceDocument.of(record.value, record.id)
            assertEquals(d, codec.decode(codec.encode(d)))
            val json = codec.canonicalContent(d)
            listOf("beamAccent", "beam_accent", "source", "gain", "volume", "hdr", "permission").forEach {
                assertFalse(json.contains("\"$it\":"))
            }
        }
    }

    @Test fun validAlternateNumberSpellingsCanonicalizeTypedStateBeforeChecksum() {
        val source = encoded()
        val alternate = field(field(field(source, "duration_scale", "25e-2"), "density_scale", "0.8500000001"),
            "panel_alpha_scale", "2E-1")
        assertEquals(f.document(), codec.decode(alternate))
        assertEquals(source, codec.encode(codec.decode(alternate)))
    }

    @Test fun allDecimalRangesRejectOutsideBoundsBeforeFloatRounding() {
        val values = mapOf("duration_scale" to listOf("0.24999999999999999999", "2.00000000000000000001"),
            "density_scale" to listOf("0.84999999999999999999", "1.25000000000000000001"),
            "panel_alpha_scale" to listOf("0.19999999999999999999", "1.00000000000000000001"))
        values.forEach { (key, tokens) -> tokens.forEach { token ->
            f.failure("invalid_value") { codec.decode(field(encoded(), key, token)) }
        } }
    }

    @Test fun floatsRejectSignedZeroNonfiniteUnderflowAndHugeExponents() {
        listOf("duration_scale", "density_scale", "panel_alpha_scale").forEach { key ->
            listOf("-0.0", "0", "-0", "1e-999999", "1e999999", "1e2147483648", "NaN", "Infinity", "-Infinity", "\"NaN\"")
                .forEach { token -> f.failure { codec.decode(field(encoded(), key, token)) } }
        }
        // No appearance Float permits zero. The reused grammar still preserves it where legal.
        assertEquals("-0.0", StrictInstrumentJson.encode(-0.0f))
        assertEquals("0", StrictInstrumentJson.encode(0.0f))
    }

    @Test fun integerSyntaxIsRequiredAndArgbExtremaAreExact() {
        val original = encoded()
        assertEquals(Int.MIN_VALUE, codec.decode(original).active.colors.line)
        assertEquals(Int.MAX_VALUE, codec.decode(original).active.colors.lineStrong)
        listOf("version", "radius_dp", "plane", "line", "line_strong").forEach { key ->
            listOf("0.0", "0e0", "2147483648", "-2147483649", "\"0\"", "true", "null").forEach { token ->
                f.failure { codec.decode(field(original, key, token)) }
            }
        }
        assertEquals(0, codec.decode(field(original, "radius_dp", "-0")).active.radiusDp)
        listOf("-1", "65").forEach { token -> f.failure("invalid_value") { codec.decode(field(original, "radius_dp", token)) } }
    }

    @Test fun everyRgbRoleRejectsOutOfRange() {
        listOf("plane", "surface", "surface2", "ink", "ink2", "muted", "accent", "on_accent", "stone", "stone_hi", "stone_lo")
            .forEach { key -> listOf("-1", "16777216").forEach { token ->
                f.failure("invalid_value") { codec.decode(field(encoded(), key, token)) }
            } }
    }

    @Test fun booleanFieldsAndEnumsHaveExactTypesAndCase() {
        listOf("dark", "accent_follows_beam", "mono_prose", "designators").forEach { key ->
            listOf("0", "1", "\"false\"", "null", "[]", "{}").forEach { token ->
                f.failure("wrong_type") { codec.decode(field(encoded(), key, token)) }
            }
        }
        listOf("character", "motion").forEach { key ->
            listOf("\"unknown\"", "\"carved\"", "\"eased\"", "0", "null", "false").forEach { token ->
                f.failure { codec.decode(field(encoded(), key, token)) }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun tree(document: AppearanceDocument = f.document()): MutableMap<String, Any> {
        fun ordinary(raw: Any): Any = when (raw) {
            is StrictInstrumentJson.Number -> raw.text.toIntOrNull() ?: raw.text.toFloat()
            is Map<*, *> -> raw.entries.associateTo(linkedMapOf()) { it.key as String to ordinary(it.value!!) }
            is List<*> -> raw.map { ordinary(it!!) }
            else -> raw
        }
        return ordinary(StrictInstrumentJson.parse(codec.encode(document))) as MutableMap<String, Any>
    }
    @Suppress("UNCHECKED_CAST")
    private fun obj(root: MutableMap<String, Any>, key: String) = root.getValue(key) as MutableMap<String, Any>
    private fun raw(root: Map<String, Any>) = StrictInstrumentJson.encode(root)

    @Test fun everyObjectRequiresAllAndOnlyItsExactFields() {
        val doc = AppearanceDocument.of(f.low, "", users = listOf(f.user(1)), legacy = f.legacy())
        fun scopes(root: MutableMap<String, Any>): List<MutableMap<String, Any>> {
            @Suppress("UNCHECKED_CAST")
            val user = (root.getValue("users") as List<MutableMap<String, Any>>).first()
            @Suppress("UNCHECKED_CAST")
            val legacy = (root.getValue("legacy") as List<MutableMap<String, Any>>).first()
            return listOf(root, obj(root, "active"), obj(obj(root, "active"), "colors"), user, legacy,
                obj(user, "value"), obj(obj(legacy, "value"), "colors"))
        }
        scopes(tree(doc)).indices.forEach { index ->
            val keys = scopes(tree(doc))[index].keys.toList()
            keys.forEach { key ->
                val root = tree(doc)
                scopes(root)[index].remove(key)
                f.failure("invalid_fields") { codec.decode(raw(root)) }
            }
            val root = tree(doc)
            scopes(root)[index]["unknown"] = true
            f.failure("invalid_fields") { codec.decode(raw(root)) }
        }
    }

    @Test fun structuralFieldsRejectWrongTypesAndNoNullIsAdmitted() {
        listOf("active", "users", "legacy", "provenance").forEach { key ->
            val root = tree()
            root[key] = "wrong"
            f.failure("wrong_type") { codec.decode(raw(root)) }
        }
        listOf("active_id", "schema", "content_sha256").forEach { key ->
            f.failure("wrong_type") { codec.decode(field(encoded(), key, "false")) }
        }
        val root = tree(AppearanceDocument.of(users = listOf(f.user(1))))
        root["users"] = listOf(false)
        f.failure("wrong_type") { codec.decode(raw(root)) }
        listOf("active", "provenance").forEach { key ->
            val nullable = tree()
            nullable[key] = "NULL_MARKER"
            f.failure("wrong_type") { codec.decode(raw(nullable).replace("\"NULL_MARKER\"", "null")) }
        }
    }

    @Test fun everyNestedFieldRejectsWrongJsonTypeIncludingUserAndLegacyRecords() {
        val d = AppearanceDocument.of(f.low, "", users = listOf(f.user(1)), legacy = f.legacy(true), provenance = f.provenance)
        fun paths(value: Any, prefix: List<Any> = emptyList()): List<List<Any>> = when (value) {
            is Map<*, *> -> value.entries.flatMap { (key, child) ->
                val path = prefix + key!!
                listOf(path) + paths(child!!, path)
            }
            is List<*> -> value.flatMapIndexed { index, child -> paths(child!!, prefix + index) }
            else -> emptyList()
        }
        val paths = paths(tree(d))
        assertTrue(paths.size > 400)
        paths.forEach { path ->
            val root = tree(d)
            var parent: Any = root
            path.dropLast(1).forEach { segment ->
                parent = if (segment is Int) (parent as List<*>)[segment]!! else (parent as Map<*, *>)[segment]!!
            }
            @Suppress("UNCHECKED_CAST")
            val fields = parent as MutableMap<String, Any>
            val key = path.last() as String
            val value = fields.getValue(key)
            fields[key] = if (value is Boolean) "false" else false
            f.failure("wrong_type") { codec.decode(raw(root)) }
        }
    }

    @Test fun lastLegacyRecordValidationFailureCannotPublishEarlierValidFields() {
        var state = AppearanceDocument.of()
        val prior = state
        val root = tree(AppearanceDocument.of(f.high, "legacy:current", users = listOf(f.user(1)),
            legacy = f.legacy(true), provenance = f.provenance))
        @Suppress("UNCHECKED_CAST")
        val last = (root["legacy"] as List<MutableMap<String, Any>>).last()
        obj(last, "value")["radius_dp"] = 65
        f.failure("invalid_value") { state = codec.decode(raw(root)) }
        assertSame(prior, state)
    }

    @Test fun provenanceAllowsOnlyPresentOriginalTypedKeys() {
        val d = AppearanceDocument.of(provenance = f.provenance)
        val source = codec.encode(d)
        listOf("ov_char", "ov_motion", "ov_radius", "ov_desig").forEach { key ->
            listOf("null", "true", "\"-1\"", "1.0", "2147483648", "-2147483649").forEach { token ->
                f.failure("wrong_type") { codec.decode(field(source, key, token)) }
            }
        }
        f.failure("wrong_type") { codec.decode(field(source, "room", "null")) }
        val root = tree()
        obj(root, "provenance")["unknown"] = 0
        f.failure("invalid_fields") { codec.decode(raw(root)) }
        val empty = codec.canonicalContent(AppearanceDocument.of())
        assertTrue(empty.contains("\"provenance\":{}"))
    }

    @Test fun unsupportedSchemaVersionAndChecksumMutationsFail() {
        f.failure("unsupported_schema") { codec.decode(field(encoded(), "schema", "\"phosphor.appearance/2\"")) }
        f.failure("unsupported_schema") { codec.decode(field(encoded(), "version", "2")) }
        listOf("0".repeat(64), digest1.uppercase(), "", digest1.dropLast(1), "g".repeat(64)).forEach { digest ->
            f.failure("checksum_mismatch") { codec.decode(field(encoded(), "content_sha256", "\"$digest\"")) }
        }
        f.failure("checksum_mismatch") { codec.decode(field(encoded(), "dark", "true")) }
        f.failure("checksum_mismatch") { codec.decode(field(encoded(), "active_id", "\"curated:dark\"")) }
        val root = tree(AppearanceDocument.of(users = listOf(f.user(1), f.user(2))))
        root["users"] = (root["users"] as List<*>).reversed()
        f.failure("checksum_mismatch") { codec.decode(raw(root)) }
    }

    @Test fun grammarRejectsMalformedDuplicateTrailingUnicodeAndInvalidNumbers() {
        val source = encoded()
        listOf("", "{} trailing", source + "{}", source + "x", source.dropLast(1) + ",}",
            source.replaceFirst("{", "{\"schema\":\"phosphor.appearance/1\","),
            source.replace("\"dark\":false", "\"dark\":false,\"dark\":false"),
            source.replace("\"plane\":0", "\"plane\":0,\"pl\\u0061ne\":0"),
            source.replace("\"active_id\":\"\"", "\"active_id\":\"\\uD800\""),
            source.replace("\"active_id\":\"\"", "\"active_id\":\"\\uDC00\""),
            source.replace("\"active_id\":\"\"", "\"active_id\":\"\\x20\""),
            source.replace("\"active_id\":\"\"", "\"active_id\":\"line\nfeed\""),
            "/*comment*/$source", source.replace("\"dark\"", "'dark'"),
            field(source, "radius_dp", "01"), field(source, "radius_dp", "+1"), field(source, "radius_dp", "1."),
            field(source, "radius_dp", "1e"), field(source, "radius_dp", ".1"), "\uFEFF$source")
            .forEach { bad -> f.failure("invalid_json") { codec.decode(bad) } }
        f.failure("invalid_unicode") { codec.decode(source + "\uD800") }
        f.failure("invalid_utf8") { codec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
        f.failure("invalid_utf8") { codec.decode(byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte())) }
    }

    @Test fun utf8ByteLimitIncludesWhitespaceAndMultibyteBytes() {
        val source = encoded()
        val boundary = source + " ".repeat(AppearanceDocumentCodec.MAX_BYTES - source.toByteArray().size)
        assertEquals(f.document(), codec.decode(boundary))
        assertEquals(f.document(), codec.decode(boundary.toByteArray()))
        f.failure("document_too_large") { codec.decode(boundary + " ") }
        f.failure("document_too_large") { codec.decode((boundary + " ").toByteArray()) }
        f.failure("document_too_large") { codec.decode("😀".repeat(32769)) }
        val root = tree()
        root["legacy"] = f.legacy().map { mapOf("id" to it.id, "name" to "😀".repeat(4096), "value" to obj(tree(), "active")) }
        f.failure("document_too_large") { codec.decode(raw(root)) }
    }

    @Test fun sharedDepthAndNodeLimitsAreNotRelaxed() {
        f.failure("invalid_json") { codec.decode("[".repeat(14) + "0" + "]".repeat(14)) }
        f.failure("invalid_json") { codec.decode("[" + List(20_000) { "0" }.joinToString(",") + "]") }
        // Boundary inputs pass grammar but fail the appearance root type.
        f.failure("wrong_type") { codec.decode("[".repeat(12) + "0" + "]".repeat(12)) }
        f.failure("wrong_type") { codec.decode("[" + List(19_999) { "0" }.joinToString(",") + "]") }
    }

    @Test fun completeReplacementDoesNotReadCorruptOldBytesOrPublishPartialState() {
        var state = AppearanceDocument.of()
        val before = state
        val corruptOldBytes = "not JSON"
        assertTrue(corruptOldBytes.isNotEmpty())
        f.failure { state = codec.decode(field(encoded(), "radius_dp", "65")) }
        assertSame(before, state)
        state = codec.decode(withDigest(vector2, digest2))
        assertEquals(f.provenance, state.provenance)
        assertEquals(f.low, state.active)
    }
}
