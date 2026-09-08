package dev.phosphor.mobil3.settings

import dev.phosphor.mobil3.settings.appearance.AppearanceDocument
import dev.phosphor.mobil3.settings.appearance.AppearanceDocumentCodec
import dev.phosphor.mobil3.settings.appearance.CuratedAppearances
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class AppearanceArchiveTest {
    private val key = "appearance_state"
    private val instant = "2026-09-08T14:00:00Z"
    private fun export(values: Map<String, *>) = SettingsArchive.export(
        "dev.phosphor.mobil3", "2.0.0", "release", instant, values)
    private fun encoded() = AppearanceDocumentCodec.encode(AppearanceDocument.of(
        active = CuratedAppearances.glass, activeId = "curated:glass"))

    private fun fixture(value: Any, schema: String = SettingsArchive.SCHEMA): String {
        val spelling = if (value is String) JSONObject.quote(value) else value.toString()
        val canonical = "{\"exported_at\":\"$instant\",\"schema\":\"$schema\"," +
            "\"settings\":{\"appearance_state\":$spelling},\"source_distribution\":\"release\"," +
            "\"source_package\":\"dev.phosphor.mobil3\",\"source_version\":\"2.0.0\"}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return JSONObject(canonical).put("content_sha256", digest).toString()
    }

    private fun rejection(block: () -> Unit): SettingsArchive.ArchiveException {
        try { block(); fail("invalid appearance must fail before any import") }
        catch (error: SettingsArchive.ArchiveException) { assertTrue(error.fix.isNotBlank()); return error }
        error("unreachable")
    }

    @Test fun schemaTwoRoundTripsCompleteAuthoredAppearanceAsAnInertString() {
        val raw = encoded()
        val values = mapOf(key to raw, "grid" to true)
        val result = export(values)
        assertEquals(values, SettingsArchive.decode(result.json).values)
        assertEquals(listOf(key, "grid"), result.exportedKeys)
        assertTrue(result.skippedKeys.isEmpty())
        assertTrue(result.json.toByteArray().size <= SettingsArchive.MAX_BYTES)
        assertEquals(raw, SettingsArchive.decode(fixture(raw)).values[key])
    }

    @Test fun nestedAppearanceSchemaAndChecksumAreValidatedOnBothPublicPaths() {
        for (raw in listOf("{broken", encoded().replace("phosphor.appearance/1", "phosphor.appearance/99"),
            encoded().replace("\"curated:glass\"", "\"curated:dark\""))) {
            assertEquals("invalid_appearance_state", rejection { export(mapOf(key to raw)) }.error)
            assertEquals("invalid_appearance_state", rejection { SettingsArchive.decode(fixture(raw)) }.error)
        }
    }

    @Test fun schemaOneStillVerifiesAndSkipsNewerAppearanceKey() {
        for (raw in listOf(encoded(), "newer opaque value")) {
            val result = SettingsArchive.decode(fixture(raw, SettingsArchive.LEGACY_SCHEMA))
            assertTrue(result.values.isEmpty())
            assertEquals(listOf(key), result.skippedKeys)
        }
    }

    @Test fun absenceStaysAbsentAndUnrelatedMergeIgnoresCorruptStoredAppearance() {
        val imported = SettingsArchive.decode(export(mapOf("focus" to 1f)).json)
        assertFalse(imported.values.containsKey(key))
        val merged = SettingsArchive.merge(imported, mapOf(key to "broken", "focus" to 2f))
        assertEquals(mapOf("focus" to 1f), merged)
        assertFalse(merged.containsKey(key))
    }

    @Test fun completeAppearanceIsAdmittedIndependentlyOfInvalidExistingState() {
        val raw = encoded()
        val imported = SettingsArchive.decode(fixture(raw))
        assertEquals(mapOf(key to raw), SettingsArchive.merge(imported, mapOf(key to "broken")))
    }

    @Test fun wrongTypesAndNestedObjectsRemainRejected() {
        for (value in listOf<Any>(true, 12, 1.25)) {
            assertEquals("invalid_setting_type", rejection { export(mapOf(key to value)) }.error)
            assertEquals("invalid_setting_type", rejection { SettingsArchive.decode(fixture(value)) }.error)
        }
        assertEquals("non_inert_value", rejection {
            SettingsArchive.decode(fixture(JSONObject(encoded())))
        }.error)
    }

    @Test fun appearanceByteBoundDoesNotRelaxTheOuterArchiveLimit() {
        val oversized = " ".repeat(AppearanceDocumentCodec.MAX_BYTES) + encoded()
        rejection { export(mapOf(key to oversized)) }
        rejection { SettingsArchive.decode(fixture(oversized)) }
        val outer = " ".repeat(SettingsArchive.MAX_BYTES) + fixture(encoded())
        assertEquals("archive_too_large", rejection { SettingsArchive.decode(outer) }.error)
    }

    @Test fun previewRuntimeAndDeviceFieldsStayOutsidePortableSettings() {
        val values = mapOf(key to encoded(), "appearance_preview" to "unsaved",
            "appearance_revision" to 9L, "appearance_storage_uncertain" to true)
        val result = export(values)
        assertEquals(listOf(key), result.exportedKeys)
        assertEquals(listOf("appearance_preview", "appearance_revision", "appearance_storage_uncertain"), result.skippedKeys)
        assertEquals(setOf(key), SettingsArchive.decode(result.json).values.keys)
    }
}
