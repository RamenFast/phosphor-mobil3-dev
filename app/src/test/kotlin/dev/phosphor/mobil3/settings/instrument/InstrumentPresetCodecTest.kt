package dev.phosphor.mobil3.settings.instrument

import dev.phosphor.mobil3.ui.LightRgb
import dev.phosphor.mobil3.ui.LightSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InstrumentPresetCodecTest {
    private val setup = InstrumentPresetFixtures.sixSlotSetup
    private val setupJson get() = InstrumentPresetCodec.encodeSetup(setup)
    private val collection get() = InstrumentPresetCollection.empty().create("Bench 🎛", setup, ID)
    private val json get() = InstrumentPresetCodec.encode(collection)

    @Test fun canonicalFixtureIsDeterministicAndRoundTrips() {
        val empty = InstrumentPresetCollection.empty()
        assertEquals("{\"records\":[],\"schema\":\"phosphor.instrument-presets/1\",\"version\":1}", InstrumentPresetCodec.canonicalContent(empty))
        assertEquals("582021ed304436bf7ff9a3eb35a5d504bc2ff830f2414ad868b94eb5f865fa0a", InstrumentPresetCodec.contentSha256(empty))
        assertEquals(collection, InstrumentPresetCodec.decode(json))
        assertEquals(collection, InstrumentPresetCodec.decode(json.toByteArray()))
        assertEquals(json, InstrumentPresetCodec.encode(InstrumentPresetCodec.decode(json)))
        val reordered = JSONObject(json).toString(2).replace("\"gain\": 7", "\"gain\": 7.000e0")
        assertEquals(collection, InstrumentPresetCodec.decode(reordered))
        val second = collection.create("Other", setup, "00000000-0000-0000-0000-000000000002")
        assertEquals(InstrumentPresetCodec.encode(second), InstrumentPresetCodec.encode(InstrumentPresetCollection.of(second.records.reversed())))
        assertEquals(collection, InstrumentPresetCodec.decode(InstrumentPresetCodec.exportRecord(second, ID)))
    }

    @Test fun fixedNonemptyCanonicalFixtureHasIndependentSha256() {
        val expected = """{"records":[{"id":"00000000-0000-0000-0000-000000000001","name":"Clean XY","setup":{"auto_gain":true,"beam_energy":8,"beam_random_armed":false,"beam_random_max":20,"beam_random_min":6,"focus":0.3,"gain":1.8332275,"geom_amount":0.6,"geom_fx":0,"glow":0.2,"glow_random_armed":false,"glow_random_max":0.9,"glow_random_min":0.3,"grid":true,"grid_data":false,"light":{"clock":"TIMER","generated_auto":false,"interval_max":6,"interval_min":3,"preset":0,"random_interval":false,"seconds":3,"selected_mask":0,"shuffle":false,"slots":[]},"mode":0,"oversample":1,"random_ban_modes":[],"random_mode_armed":false}}],"schema":"phosphor.instrument-presets/1","version":1}"""
        val clean = InstrumentPresetCollection.empty().create("Clean XY", CuratedInstrumentPresets.cleanXy.setup, ID)
        assertEquals(expected, InstrumentPresetCodec.canonicalContent(clean))
        assertEquals("e8c0cb6fe0eff2e7cfb0e47451052a5e11e05493d7aee33d998411f2a8bd44da", InstrumentPresetCodec.contentSha256(clean))
    }

    @Test fun lightNumericBoundariesDelegateToTheActualLightValue() {
        val factories: List<(Float) -> LightSettings> = listOf(
            { LightSettings(seconds = it) },
            { LightSettings(intervalMin = it, intervalMax = 60f) },
            { LightSettings(intervalMin = 0.1f, intervalMax = it) })
        factories.forEach { factory ->
            listOf(0.1f, 60f).forEach {
                val value = setup.copy(light = factory(it))
                assertEquals(value, InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(value)))
            }
            listOf(Math.nextDown(0.1f), Math.nextUp(60f), Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY).forEach {
                assertThrows(IllegalArgumentException::class.java) { factory(it) }
            }
        }
        for (component in 0..2) {
            listOf(0f, 1f).forEach { value ->
                val rgb = MutableList(3) { 0.5f }.also { it[component] = value }
                val light = LightSettings(listOf(LightRgb(rgb[0], rgb[1], rgb[2])), 1)
                assertEquals(light, InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(setup.copy(light = light))).light)
            }
            listOf(Math.nextDown(0f), Math.nextUp(1f), Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY).forEach { value ->
                val rgb = MutableList(3) { 0.5f }.also { it[component] = value }
                assertThrows(IllegalArgumentException::class.java) { LightRgb(rgb[0], rgb[1], rgb[2]) }
            }
        }
        (0..8).forEach { preset ->
            val value = setup.copy(light = LightSettings(preset = preset))
            assertEquals(value, InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(value)))
        }
    }

    @Test fun everyDocumentAndRecordFieldIsRequiredAndNoMetadataCanEnter() {
        JSONObject(json).keys().asSequence().toList().forEach { key ->
            reject { InstrumentPresetCodec.decode(JSONObject(json).also { it.remove(key) }.toString()) }
        }
        listOf("id", "name", "setup").forEach { key ->
            reject { InstrumentPresetCodec.decode(JSONObject(json).also { it.getJSONArray("records").getJSONObject(0).remove(key) }.toString()) }
            listOf(JSONObject.NULL, true, 1, JSONArray()).forEach { bad ->
                reject { InstrumentPresetCodec.decode(JSONObject(json).also { it.getJSONArray("records").getJSONObject(0).put(key, bad) }.toString()) }
            }
        }
        listOf("source", "source_file", "endpoint", "token", "authorization", "root", "timestamp", "listening_history",
            "fps", "rotation", "appearance", "brightness", "pause_display", "hud_mode", "volume", "mic_mix",
            "capture_grant", "measured_gain", "remote_gain", "remote_auto_gain", "auto_frame_scale", "startup", "rng", "history").forEach { key ->
            reject { InstrumentPresetCodec.decodeSetup(JSONObject(setupJson).put(key, "excluded").toString()) }
            reject { InstrumentPresetCodec.decode(JSONObject(json).put(key, "excluded").toString()) }
            reject { InstrumentPresetCodec.decode(JSONObject(json).also { it.getJSONArray("records").getJSONObject(0).put(key, "excluded") }.toString()) }
        }
        assertEquals(7f, InstrumentPresetCodec.decodeSetup(setupJson).gain)
        assertFalse(InstrumentPresetCodec.decodeSetup(setupJson).autoGain)
    }

    @Test fun schemaChecksumAndDuplicateRecordsRejectExactly() {
        listOf("phosphor.instrument-presets/2", "phosphor.settings/2", "").forEach { bad ->
            assertEquals("unsupported_schema", reject { InstrumentPresetCodec.decode(JSONObject(json).put("schema", bad).toString()) }.error)
        }
        listOf(0, 2, -1).forEach { bad -> reject { InstrumentPresetCodec.decode(JSONObject(json).put("version", bad).toString()) } }
        listOf("1", true, JSONObject.NULL, 1.1).forEach { bad -> reject { InstrumentPresetCodec.decode(JSONObject(json).put("version", bad).toString()) } }
        listOf("0".repeat(64), "A".repeat(64), "", "x".repeat(64), "0".repeat(63)).forEach { bad ->
            assertEquals("checksum_mismatch", reject { InstrumentPresetCodec.decode(JSONObject(json).put("content_sha256", bad).toString()) }.error)
        }
        reject { InstrumentPresetCodec.decode(json.replace("Bench 🎛", "Changed")) }
        val duplicated = JSONObject(json).also { it.getJSONArray("records").put(it.getJSONArray("records").getJSONObject(0)) }
        assertEquals("duplicate_id", reject { InstrumentPresetCodec.decode(duplicated.toString()) }.error)
        duplicated.getJSONArray("records").put(1, JSONObject(duplicated.getJSONArray("records").getJSONObject(0).toString()).put("id", "00000000-0000-0000-0000-000000000002"))
        assertEquals("duplicate_name", reject { InstrumentPresetCodec.decode(duplicated.toString()) }.error)
        listOf("1-1-1-1-1", ID.uppercase().replace("000000000001", "ABCDEF000001"), "not-a-uuid", " $ID").forEach { bad ->
            reject { InstrumentPresetCodec.decode(JSONObject(json).also { it.getJSONArray("records").getJSONObject(0).put("id", bad) }.toString()) }
        }
    }

    @Test fun duplicateKeysAreRejectedByProductionGrammarBeforeMapOverwrite() {
        listOf(json.replaceFirst("{", "{\"version\":1,"),
            json.replace("\"mode\":10", "\"mode\":10,\"mode\":10"),
            json.replace("\"clock\":\"TIMER\"", "\"clock\":\"TIMER\",\"clock\":\"TRACK\""),
            json.replace("\"mode\":10", "\"mode\":10,\"m\\u006fde\":10"),
            json.replace("\"name\":\"Bench 🎛\"", "\"name\":\"Bench 🎛\",\"name\":\"Bench 🎛\""))
            .forEach { assertEquals("invalid_json", reject { InstrumentPresetCodec.decode(it) }.error) }
    }

    @Test fun malformedJsonUtf8UnicodeAndResourceBombsAreRejected() {
        listOf("", " ", "{} trailing", json + "{}", json + " false", json.dropLast(1),
            json.replaceFirst("{", "{/*comment*/"), json.replace("\"version\":1", "version:1"),
            json.replace("\"version\":1", "\"version\":01"), json.replace("\"version\":1", "\"version\":+1"),
            json.replace("\"version\":1", "\"version\":1,"), json.replace("\"version\":1", "\"version\":NaN"),
            json.replace("\"version\":1", "\"version\":Infinity"), json.replace("\"schema\"", "'schema'"),
            json.replace("Bench 🎛", "\\ud800"), json.replace("Bench 🎛", "\\udc00"),
            json.replace("Bench 🎛", "bad\nname"), "[".repeat(50) + "0" + "]".repeat(50),
            "[" + "0,".repeat(20_001) + "0]", json.replace("\"gain\":7", "\"gain\":" + "9".repeat(65)))
            .forEach { reject { InstrumentPresetCodec.decode(it) } }
        assertEquals("invalid_utf8", reject { InstrumentPresetCodec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }.error)
        assertEquals("invalid_unicode", reject { InstrumentPresetCodec.decode("\ud800") }.error)
        assertEquals(collection, InstrumentPresetCodec.decode(json.replace("🎛", "\\ud83c\\udf9b")))
    }

    @Test fun inputByteBoundIncludesMultibyteUnicodeAndAllowsExactlyOneMiB() {
        val padding = InstrumentPresetCodec.MAX_BYTES - json.toByteArray().size
        assertEquals(collection, InstrumentPresetCodec.decode(json + " ".repeat(padding)))
        assertEquals("document_too_large", reject { InstrumentPresetCodec.decode(json + " ".repeat(padding + 1)) }.error)
        assertEquals("document_too_large", reject { InstrumentPresetCodec.decode("é".repeat(InstrumentPresetCodec.MAX_BYTES / 2 + 1)) }.error)
        assertEquals("document_too_large", reject { InstrumentPresetCodec.decode(ByteArray(InstrumentPresetCodec.MAX_BYTES + 1)) }.error)
    }

    @Test fun completeLightUsesExistingValidatorAndStrictWireTypes() {
        val original = JSONObject(setupJson).getJSONObject("light")
        original.keys().asSequence().toList().forEach { key ->
            reject { InstrumentPresetCodec.decodeSetup(JSONObject(setupJson).also { it.getJSONObject("light").remove(key) }.toString()) }
            listOf(JSONObject.NULL, JSONObject()).forEach { bad ->
                reject { InstrumentPresetCodec.decodeSetup(JSONObject(setupJson).also { it.getJSONObject("light").put(key, bad) }.toString()) }
            }
        }
        fun light(key: String, value: Any): String = JSONObject(setupJson).also { it.getJSONObject("light").put(key, value) }.toString()
        listOf("generated_auto", "shuffle", "random_interval").forEach { key ->
            listOf("false", 0, 1).forEach { reject { InstrumentPresetCodec.decodeSetup(light(key, it)) } }
        }
        listOf("seconds", "interval_min", "interval_max").forEach { key ->
            listOf("3", true, 0.0999999999, 60.0000001).forEach { reject { InstrumentPresetCodec.decodeSetup(light(key, it)) } }
        }
        listOf(-1, 9, "1", 1.1, true).forEach { reject { InstrumentPresetCodec.decodeSetup(light("preset", it)) } }
        listOf(-1, 64, "1", 1.1, true).forEach { reject { InstrumentPresetCodec.decodeSetup(light("selected_mask", it)) } }
        listOf("timer", "track", "", true).forEach { reject { InstrumentPresetCodec.decodeSetup(light("clock", it)) } }
        listOf(JSONArray().put(JSONArray().put(0).put(0)), JSONArray().put(JSONArray().put(0).put(0).put(0).put(0)),
            JSONArray().put(JSONArray().put(-0.0000001).put(0).put(0)), JSONArray().put(JSONArray().put(1.0000001).put(0).put(0)),
            JSONArray().put(JSONArray().put("0").put(0).put(0)), JSONArray((0..6).map { listOf(0, 0, 0) }))
            .forEach { reject { InstrumentPresetCodec.decodeSetup(light("slots", it)) } }
        reject { InstrumentPresetCodec.decodeSetup(light("slots", JSONArray())) }
        reject { InstrumentPresetCodec.decodeSetup(JSONObject(setupJson).also {
            it.getJSONObject("light").put("interval_min", 50).put("interval_max", 40)
        }.toString()) }
        for (count in 0..6) for (mask in 0 until (1 shl count)) {
            val valid = setup.copy(light = LightSettings(List(count) { LightRgb(0f, 0.5f, 1f) }, mask))
            assertEquals(valid, InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(valid)))
        }
    }

    @Test fun decimalBoundsAreCheckedBeforeFloatRoundingAndAllBansAreTyped() {
        mapOf("gain" to "7.0000000001", "focus" to "0.29999999999", "glow" to "0.9800000001",
            "geom_amount" to "-0.00000000000001", "beam_energy" to "30.00000001").forEach { (key, value) ->
            reject { InstrumentPresetCodec.decodeSetup(setupJson.replace(Regex("\"$key\":[0-9.]+"), "\"$key\":$value")) }
        }
        listOf("[1,1]", "[2,1]", "[-1]", "[11]", "[1.0]", "[true]", "[\"1\"]", "[0,1,2,3,4,5,6,7,8,9]").forEach { bans ->
            reject { InstrumentPresetCodec.decodeSetup(setupJson.replace(Regex("\"random_ban_modes\":\\[[^]]*]"), "\"random_ban_modes\":$bans")) }
        }
    }

    @Test fun authoredFloatsIncludingSignedZeroRoundTrip() {
        val signed = setup.copy(geomAmount = -0.0f, glow = -0.0f, glowRandomMin = -0.0f,
            light = setup.light.copy(slots = listOf(LightRgb(-0.0f, 0f, 1f)), selectedMask = 1))
        assertEquals(signed, InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(signed)))
    }

    @Test fun numericGrammarAllowsLongEquivalentFiniteSpellingWithinDocumentBound() {
        val long = setupJson.replace("\"gain\":7", "\"gain\":7." + "0".repeat(10_000))
        assertEquals(setup, InstrumentPresetCodec.decodeSetup(long))
        listOf("-", "1.", "1e", "1e+", "-.1", "00", "-01", "+1", ".1", "1e+-1").forEach { bad ->
            assertEquals("invalid_json", reject {
                InstrumentPresetCodec.decodeSetup(setupJson.replace("\"gain\":7", "\"gain\":$bad"))
            }.error)
        }
    }

    companion object { const val ID = "00000000-0000-0000-0000-000000000001" }
}
