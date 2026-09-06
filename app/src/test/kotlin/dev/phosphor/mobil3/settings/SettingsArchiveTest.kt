package dev.phosphor.mobil3.settings

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.security.MessageDigest

class SettingsArchiveTest {
    @Test fun gridDataRoundTripsIndependentlyFromGridHudAndBand() {
        for (gridData in listOf(false, true)) for (grid in listOf(false, true)) {
            val values = mapOf("grid_data" to gridData, "grid" to grid, "hud_mode" to 2, "band_mode" to 0)
            val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], values)
            assertEquals(values, SettingsArchive.decode(exported.json).values)
            assertTrue("grid_data" in exported.exportedKeys)
        }
    }

    @Test fun gridDataAbsentOldArchiveKeepsExistingPreferenceOrFalseDefault() {
        val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true))
        val decoded = SettingsArchive.decode(exported.json).values
        assertFalse(decoded.containsKey("grid_data"))
        assertEquals(false, decoded["grid_data"] ?: dev.phosphor.mobil3.ui.GridData.DEFAULT)
        val existing = mutableMapOf<String, Any>("grid_data" to true)
        existing.putAll(decoded) // Activity imports only provided keys, then restoreTuning reads the real preference.
        assertEquals(true, existing["grid_data"])
    }

    @Test fun gridDataRejectsNonBooleanAndDoesNotArchiveRawSignal() {
        for (invalid in listOf<Any>(0, 1, "false", "true")) {
            val error = assertFailsWith<SettingsArchive.ArchiveException> {
                SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid_data" to invalid))
            }
            assertEquals("invalid_setting_type", error.error)
            val good = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid_data" to true))
            val altered = JSONObject(good.json)
            altered.getJSONObject("settings").put("grid_data", invalid)
            // Match the existing archive fixtures so checksum rejection cannot mask the type check.
            val encoded = if (invalid is String) "\"$invalid\"" else invalid.toString()
            val canonical = "{" +
                "\"exported_at\":\"${metadata[3]}\"," +
                "\"schema\":\"${SettingsArchive.SCHEMA}\"," +
                "\"settings\":{\"grid_data\":$encoded}," +
                "\"source_distribution\":\"${metadata[2]}\"," +
                "\"source_package\":\"${metadata[0]}\"," +
                "\"source_version\":\"${metadata[1]}\"}"
            altered.put("content_sha256", MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) })
            assertEquals("invalid_setting_type", assertFailsWith<SettingsArchive.ArchiveException> {
                SettingsArchive.decode(altered.toString())
            }.error)
        }
        val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3],
            mapOf("grid_data" to true, "left_dbfs" to -6.0f, "raw_stereo" to "0.5,0", "open_id" to 42))
        assertEquals(listOf("grid_data"), exported.exportedKeys)
        assertEquals(listOf("left_dbfs", "open_id", "raw_stereo"), exported.skippedKeys)
    }

    @Test fun phase9SettingsRoundTripAllIndependentBooleanCombinations() {
        for (controls in listOf(false, true)) for (pip in listOf(false, true)) {
            val values = mapOf("controls_always_visible" to controls, "pip_auto_enter" to pip)
            val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3],
                values + mapOf("consent_seen" to true, "last_source" to "capture"))
            assertEquals(values.keys.sorted(), exported.exportedKeys)
            val decoded = SettingsArchive.decode(exported.json).values
            assertEquals(values, decoded)
            assertEquals(controls, dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.alwaysVisible(decoded))
            assertEquals(pip, dev.phosphor.mobil3.PictureInPicturePolicy.autoEnter(decoded))
        }
    }

    @Test fun missingPhase9KeysKeepDefaultsOrPreviouslyExplicitValues() {
        val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to false))
        val old = SettingsArchive.decode(exported.json).values
        assertFalse(dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.alwaysVisible(old))
        assertTrue(dev.phosphor.mobil3.PictureInPicturePolicy.autoEnter(old))
        val existing = mutableMapOf<String, Any>("controls_always_visible" to true, "pip_auto_enter" to false)
        existing.putAll(old)
        assertTrue(dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.alwaysVisible(existing))
        assertFalse(dev.phosphor.mobil3.PictureInPicturePolicy.autoEnter(existing))
    }

    @Test fun phase9KeysRejectNonBooleanExportAndImportValues() {
        for (key in listOf("controls_always_visible", "pip_auto_enter")) for (invalid in listOf<Any>("false", 0, 1)) {
            val error = assertFailsWith<SettingsArchive.ArchiveException> {
                SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf(key to invalid))
            }
            assertEquals("invalid_setting_type", error.error)
            val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf(key to true))
            val root = JSONObject(exported.json)
            root.getJSONObject("settings").put(key, invalid)
            val encoded = if (invalid is String) "\"$invalid\"" else invalid.toString()
            val canonical = "{" +
                "\"exported_at\":\"${metadata[3]}\"," +
                "\"schema\":\"${SettingsArchive.SCHEMA}\"," +
                "\"settings\":{\"$key\":$encoded}," +
                "\"source_distribution\":\"${metadata[2]}\"," +
                "\"source_package\":\"${metadata[0]}\"," +
                "\"source_version\":\"${metadata[1]}\"}"
            root.put("content_sha256", MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) })
            val imported = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(root.toString()) }
            assertEquals("invalid_setting_type", imported.error)
        }
    }

    @Test
    fun doubleTapBooleanRoundTripsWithoutRuntimeOrOtherSettings() {
        for (enabled in listOf(false, true)) {
            val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3],
                mapOf("double_tap_playback" to enabled, "consent_seen" to true))
            assertEquals(listOf("double_tap_playback"), exported.exportedKeys)
            assertEquals(mapOf("double_tap_playback" to enabled), SettingsArchive.decode(exported.json).values)
        }
    }

    @Test
    fun oldArchiveDoesNotEraseExplicitDoubleTapOff() {
        val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true))
        val imported = SettingsArchive.decode(exported.json)
        assertFalse(imported.values.containsKey("double_tap_playback"))
        val existing = mutableMapOf<String, Any>("double_tap_playback" to false)
        existing.putAll(imported.values)
        assertEquals(false, existing["double_tap_playback"])
    }

    @Test
    fun doubleTapRejectsNonBooleanExportValues() {
        for (invalid in listOf<Any>("false", 1, 0, 0.0f)) {
            val error = assertFailsWith<SettingsArchive.ArchiveException> {
                SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("double_tap_playback" to invalid))
            }
            assertEquals("invalid_setting_type", error.error)
        }
    }

    @Test
    fun doubleTapRejectsNonBooleanImportEvenWithValidChecksum() {
        for (invalid in listOf<Any>("false", 1, 0)) {
            val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("double_tap_playback" to true))
            val root = JSONObject(exported.json)
            root.getJSONObject("settings").put("double_tap_playback", invalid)
            val value = if (invalid is String) "\"$invalid\"" else invalid.toString()
            val canonical = "{" +
                "\"exported_at\":\"${metadata[3]}\"," +
                "\"schema\":\"${SettingsArchive.SCHEMA}\"," +
                "\"settings\":{\"double_tap_playback\":$value}," +
                "\"source_distribution\":\"${metadata[2]}\"," +
                "\"source_package\":\"${metadata[0]}\"," +
                "\"source_version\":\"${metadata[1]}\"}"
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            root.put("content_sha256", digest)
            val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(root.toString()) }
            assertEquals("invalid_setting_type", error.error)
        }
    }

    @Test
    fun lingerDefaultsFalseAndBooleanValuesRoundTripWithoutRuntimeOrPipState() {
        assertFalse(dev.phosphor.mobil3.BackgroundLifecyclePolicy.linger(emptyMap<String, Any>()))
        for (enabled in listOf(false, true)) {
            val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3],
                mapOf("linger_background" to enabled, "consent_seen" to true, "last_source" to "capture"))
            assertEquals(listOf("linger_background"), exported.exportedKeys)
            val imported = SettingsArchive.decode(exported.json)
            assertEquals(mapOf("linger_background" to enabled), imported.values)
            assertEquals(enabled, dev.phosphor.mobil3.BackgroundLifecyclePolicy.linger(imported.values))
        }
    }

    @Test
    fun oldArchiveDoesNotEraseAnExplicitLingerPreference() {
        val exported = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true))
        val imported = SettingsArchive.decode(exported.json)
        assertFalse(imported.values.containsKey("linger_background"))
        val existing = mutableMapOf<String, Any>("linger_background" to true)
        existing.putAll(imported.values)
        assertTrue(dev.phosphor.mobil3.BackgroundLifecyclePolicy.linger(existing))
    }

    @Test
    fun lingerRejectsNonBooleanArchiveValues() {
        for (invalid in listOf<Any>("true", 1, 0)) {
            val error = assertFailsWith<SettingsArchive.ArchiveException> {
                SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("linger_background" to invalid))
            }
            assertEquals("invalid_setting_type", error.error)
        }
    }

    private val metadata = arrayOf(
        "dev.phosphor.mobil3",
        "2.0.0",
        "local_dev",
        "2026-07-25T23:45:00Z",
    )

    @Test
    fun roundTripIsDeterministicAndAllowlisted() {
        val preferences = linkedMapOf<String, Any>(
            "room" to "blossom_dark",
            "gain" to 1.25f,
            "grid" to true,
            "mode" to 4,
            "remote_network_mode" to 2,
            "host" to "private-host",
            "consent_seen" to true,
        )
        val first = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], preferences)
        val second = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], preferences.toSortedMap())
        assertEquals(first.contentSha256, second.contentSha256)
        assertEquals(listOf("gain", "grid", "mode", "room"), first.exportedKeys)
        assertEquals(listOf("consent_seen", "host", "remote_network_mode"), first.skippedKeys)
        assertFalse(first.json.contains("private-host"))

        val imported = SettingsArchive.decode(first.json)
        assertEquals(4, imported.values["mode"])
        assertEquals(1.25f, imported.values["gain"])
        assertEquals("blossom_dark", imported.values["room"])
        assertTrue(imported.skippedKeys.isEmpty())
    }

    @Test
    fun tamperingIsRejected() {
        val archive = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("gain" to 1.25f)
        ).json
        val tampered = archive.replace("1.25", "6.25")
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(tampered) }
        assertEquals("checksum_mismatch", error.error)
    }

    @Test
    fun provenanceMetadataTamperingIsRejected() {
        val archive = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("gain" to 1.25f)
        ).json
        val tampered = archive.replace("local_dev", "fortress")
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(tampered) }
        assertEquals("checksum_mismatch", error.error)
    }

    @Test
    fun exportTimestampMustBeRfc3339() {
        val error = assertFailsWith<SettingsArchive.ArchiveException> {
            SettingsArchive.export(metadata[0], metadata[1], metadata[2], "yesterday", mapOf("grid" to true))
        }
        assertEquals("metadata_invalid", error.error)
    }

    @Test
    fun unknownMajorIsRejectedWithFix() {
        val archive = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true)
        ).json.replace(SettingsArchive.SCHEMA, "phosphor.settings/9")
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(archive) }
        assertEquals("unsupported_schema", error.error)
        assertTrue(error.fix.isNotBlank())
    }

    @Test
    fun unknownScalarFieldIsVerifiedThenSkipped() {
        val exported = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true)
        )
        val root = JSONObject(exported.json)
        val settings = root.getJSONObject("settings")
        settings.put("future_field", "inert")
        val canonical = "{" +
            "\"exported_at\":\"${metadata[3]}\"," +
            "\"schema\":\"${SettingsArchive.SCHEMA}\"," +
            "\"settings\":{\"future_field\":\"inert\",\"grid\":true}," +
            "\"source_distribution\":\"${metadata[2]}\"," +
            "\"source_package\":\"${metadata[0]}\"," +
            "\"source_version\":\"${metadata[1]}\"}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        root.put("content_sha256", digest)
        val imported = SettingsArchive.decode(root.toString())
        assertTrue(imported.values.containsKey("grid"))
        assertEquals(listOf("future_field"), imported.skippedKeys)
    }

    @Test
    fun nestedOrExecutableShapedValueIsRejected() {
        val exported = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true)
        )
        val root = JSONObject(exported.json)
        root.getJSONObject("settings").put("future", JSONObject().put("script", "run"))
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(root.toString()) }
        assertEquals("non_inert_value", error.error)
    }

    @Test
    fun executableShapedTopLevelFieldIsRejectedRatherThanIgnored() {
        val exported = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true)
        )
        val root = JSONObject(exported.json)
        root.put("script", JSONObject().put("command", "run"))
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(root.toString()) }
        assertEquals("unknown_archive_field", error.error)
    }

    @Test
    fun knownOutOfRangeValueIsRejectedEvenWithAValidArchiveHash() {
        val error = assertFailsWith<SettingsArchive.ArchiveException> {
            SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("gain" to 99f))
        }
        assertEquals("invalid_setting_value", error.error)
    }
}
