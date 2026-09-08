package dev.phosphor.mobil3.settings

import dev.phosphor.mobil3.ui.LightSettings
import dev.phosphor.mobil3.ui.LightRgb
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.security.MessageDigest

class LightArchiveTest {
    private fun archive(values: Map<String, *>) = SettingsArchive.export("dev.phosphor.mobil3", "2.0", "release", "2026-09-08T00:00:00Z", values)
    private fun legacy(values: String, schema: String = SettingsArchive.LEGACY_SCHEMA): String {
        val canonical = "{\"exported_at\":\"2026-09-08T00:00:00Z\",\"schema\":\"$schema\",\"settings\":$values,\"source_distribution\":\"release\",\"source_package\":\"dev.phosphor.mobil3\",\"source_version\":\"1.0\"}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
        return JSONObject(canonical).put("content_sha256", digest).toString()
    }
    @Test fun typedLightMergePreservesPresentBooleansAndDoesNotImportAbsentPresentationChoices() {
        val existing = LightSettings().values() + mapOf(
            "double_tap_playback" to true, "grid_data" to false,
            "scope_rotation_locked" to true, "ui_placement_locked" to false,
        )
        val decoded = SettingsArchive.decode(legacy(
            "{\"color_generated_auto\":true,\"double_tap_playback\":false,\"grid_data\":true}",
            SettingsArchive.SCHEMA,
        ))
        val merged = SettingsArchive.merge(decoded, existing)
        assertEquals(false, merged["double_tap_playback"])
        assertEquals(true, merged["grid_data"])
        assertEquals(true, merged["color_generated_auto"])
        assertFalse(merged.containsKey("scope_rotation_locked"))
        assertFalse(merged.containsKey("ui_placement_locked"))
        assertEquals(true, (existing + merged)["scope_rotation_locked"])
        assertEquals(false, (existing + merged)["ui_placement_locked"])
        assertEquals(true, existing["double_tap_playback"])
    }
    @Test fun versionTwoPartialTupleValidatesAgainstExistingBankAndRange() {
        val existing = LightSettings(List(6) { LightRgb(0f, 1f, 0f) }, 63, intervalMin = 3f, intervalMax = 6f).values()
        val selected = SettingsArchive.decode(legacy("{\"custom_selected_mask\":32}", SettingsArchive.SCHEMA))
        assertEquals(32, SettingsArchive.merge(selected, existing)["custom_selected_mask"])
        for (fields in listOf("{\"custom_slot_count\":2}", "{\"cycle_interval_max\":2}",
            "{\"custom_rgb\":\"\",\"custom_selected_mask\":1,\"custom_slot_count\":0}")) {
            val imported = SettingsArchive.decode(legacy(fields, SettingsArchive.SCHEMA))
            assertThrows(SettingsArchive.ArchiveException::class.java) { SettingsArchive.merge(imported, existing) }
        }
        assertEquals(6, existing["custom_slot_count"])
        assertEquals(6f, existing["cycle_interval_max"])
    }
    @Test fun sourceSchemaControlsKnownKeysAndChecksums() {
        val old = SettingsArchive.decode(legacy("{\"color_generated_auto\":true}"))
        assertTrue(old.values.isEmpty())
        assertEquals(listOf("color_generated_auto"), old.skippedKeys)
        val new = SettingsArchive.decode(legacy("{\"color_generated_auto\":true}", SettingsArchive.SCHEMA))
        assertEquals(true, new.values["color_generated_auto"])
        assertThrows(SettingsArchive.ArchiveException::class.java) {
            SettingsArchive.decode(legacy("{\"custom_count\":0}", SettingsArchive.SCHEMA))
        }
        assertThrows(SettingsArchive.ArchiveException::class.java) {
            SettingsArchive.decode(legacy("{\"color_generated_auto\":true}").replace(SettingsArchive.LEGACY_SCHEMA, SettingsArchive.SCHEMA))
        }
    }
    @Test fun sixSlotWriterRoundtripAndNoRuntimeFields() {
        val light = LightSettings(List(6) { LightRgb(it / 6f, 1f, 0f) }, 42, generatedAuto = true, shuffle = true, randomInterval = true)
        val output = archive(light.values() + mapOf("epilepsy_ack" to true, "temporary_roll" to 2))
        assertEquals(SettingsArchive.SCHEMA, JSONObject(output.json).getString("schema"))
        assertFalse(JSONObject(output.json).getJSONObject("settings").has("custom_count"))
        assertEquals(light, LightSettings.read(SettingsArchive.decode(output.json).values))
        assertEquals(listOf("epilepsy_ack", "temporary_roll"), output.skippedKeys)
    }
    @Test fun originalSchemaChecksumAndLosslessZeroMigration() {
        val decoded = SettingsArchive.decode(legacy("{\"custom_count\":0,\"custom_rgb\":\"0,1,0,1,0,1,0.25,0.5,0.75\"}"))
        val merged = SettingsArchive.merge(decoded, mapOf("gain" to 2f))
        assertEquals(3, merged["custom_slot_count"])
        assertEquals(0, merged["custom_selected_mask"])
        assertFalse(merged.containsKey("gain"))
        assertEquals(9, (merged["custom_rgb"] as String).split(',').size)
        assertThrows(SettingsArchive.ArchiveException::class.java) { SettingsArchive.decode(legacy("{\"custom_rgb\":\"0,1,0\"}")) }
    }
    @Test fun partialMergesAndFailurePreservation() {
        val existing = LightSettings(List(6) { LightRgb(0f, 1f, 0f) }, 63).values() + mapOf("gain" to 2f)
        val copy = existing.toMap()
        val count = SettingsArchive.decode(legacy("{\"custom_count\":2}"))
        val merged = SettingsArchive.merge(count, existing)
        assertEquals(6, merged["custom_slot_count"])
        assertEquals(3, merged["custom_selected_mask"])
        val rgb = SettingsArchive.decode(legacy("{\"custom_rgb\":\"0,1,0,1,0,1,0,0,0\"}"))
        assertThrows(SettingsArchive.ArchiveException::class.java) { SettingsArchive.merge(rgb, existing) }
        assertEquals(copy, existing)
        assertThrows(SettingsArchive.ArchiveException::class.java) { SettingsArchive.merge(count, emptyMap<String, Any>()) }
    }
}
