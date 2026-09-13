package dev.phosphor.mobil3.settings

import dev.phosphor.mobil3.ui.LightSettings
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.security.MessageDigest

class SettingsArchiveTest {
    @Test fun brightnessPinIsV2OnlyBooleanAndExcludesActiveWindowState() {
        val key = dev.phosphor.mobil3.ForegroundBrightnessPolicy.KEY
        for (on in listOf(false, true)) {
            val decoded = SettingsArchive.decode(export(mapOf(key to on)).json)
            assertEquals(mapOf(key to on), decoded.values)
            assertEquals(on, dev.phosphor.mobil3.ForegroundBrightnessPolicy.requested(decoded.values))
            val legacy = SettingsArchive.decode(legacyFixture(mapOf(key to on)))
            assertTrue(legacy.values.isEmpty())
            assertEquals(listOf(key), legacy.skippedKeys)
        }
        for (bad in listOf<Any>("true", 1, 0, 1.0)) {
            assertEquals("invalid_setting_type", assertFailsWith<SettingsArchive.ArchiveException> {
                export(mapOf(key to bad))
            }.error)
            assertEquals("invalid_setting_type", assertFailsWith<SettingsArchive.ArchiveException> {
                SettingsArchive.decode(singleSettingFixture(key, bad, SettingsArchive.SCHEMA))
            }.error)
        }
        for (private in listOf("brightness_pin_active", "screen_brightness", "brightness_window_owner")) {
            assertTrue(SettingsArchive.decode(singleSettingFixture(private, true)).values.isEmpty())
        }
    }

    @Test fun brightnessPinImportPreservesOmissionAndExplicitFalseOverridesTrue() {
        val key = dev.phosphor.mobil3.ForegroundBrightnessPolicy.KEY
        val existing = mapOf<String, Any>(key to true)
        val omitted = SettingsArchive.decode(export(mapOf("grid" to true)).json)
        assertFalse(key in SettingsArchive.merge(omitted, existing))
        assertTrue(dev.phosphor.mobil3.ForegroundBrightnessPolicy.requested(existing + SettingsArchive.merge(omitted, existing)))
        val off = SettingsArchive.decode(export(mapOf(key to false)).json)
        assertFalse(dev.phosphor.mobil3.ForegroundBrightnessPolicy.requested(existing + SettingsArchive.merge(off, existing)))
    }

    @Test fun pauseModeRoundTripsWithoutImagesOrInspectionState() {
        for (mode in listOf("HOLD", "BLACK")) {
            val values = mapOf("pause_display" to mode, "gain" to 1.25f)
            val decoded = SettingsArchive.decode(export(values).json).values
            assertEquals(mode, decoded["pause_display"])
            assertEquals(mode == "BLACK", dev.phosphor.mobil3.ui.PauseDisplayPolicy.black(decoded))
        }
        val old = SettingsArchive.decode(export(mapOf("mode" to 4)).json).values
        assertFalse("pause_display" in old)
        assertFalse(dev.phosphor.mobil3.ui.PauseDisplayPolicy.black(old))
        for (key in listOf("held_frame", "held_source", "inspection_zoom", "inspection_pan")) {
            assertFalse(key in SettingsArchive.decode(singleSettingFixture(key, 1)).values)
        }
    }

    @Test fun pauseModeArchiveRejectsInvalidTypesAndValues() {
        rejectsBoth("pause_display", true, "invalid_setting_type")
        rejectsBoth("pause_display", "FROZEN", "invalid_setting_value")
    }

    @Test fun floatingHudTypedPreferencesRoundTripWithoutRuntimeStateOrLegacyChanges() {
        for (enabled in listOf(false, true)) for (background in listOf("SOLID", "TRANSPARENT")) {
            val values = mapOf("floating_hud_enabled" to enabled, "floating_hud_background" to background,
                "floating_hud_width_dp" to 240, "floating_hud_height_dp" to 640, "hud_mode" to 1,
                "pip_auto_enter" to true, "linger_background" to false)
            assertEquals(values, SettingsArchive.decode(export(values).json).values)
        }
        for (key in listOf("floating_hud_granted", "floating_hud_running", "floating_hud_generation")) {
            assertFalse(key in SettingsArchive.decode(singleSettingFixture(key, true)).values)
        }
        assertFalse(dev.phosphor.mobil3.HudPolicy.read(SettingsArchive.decode(export(mapOf("hud_mode" to 1)).json).values).enabled)
    }

    @Test fun floatingHudArchiveRejectsWrongTypesModesAndOutOfBoundsSizes() {
        rejectsBoth("floating_hud_enabled", "true", "invalid_setting_type")
        rejectsBoth("floating_hud_background", true, "invalid_setting_type")
        rejectsBoth("floating_hud_background", "GLASS", "invalid_setting_value")
        for (key in listOf("floating_hud_width_dp", "floating_hud_height_dp")) {
            rejectsBoth(key, 239, "invalid_setting_value")
            rejectsBoth(key, 641, "invalid_setting_value")
            rejectsBoth(key, "320", "invalid_setting_type")
        }
    }

    @Test fun rootLocalFlagsStayInertEvenWithAValidImportedChecksum() {
        for (key in listOf("root_capture_enabled", "root_capture_profile_ack")) {
            val decoded = SettingsArchive.decode(singleSettingFixture(key, true))
            assertFalse(key in decoded.values)
            assertEquals(listOf(key), decoded.skippedKeys)
        }
    }

    private val fiveKeys = listOf(
        "pip_auto_enter", "controls_always_visible", "grid_data", "double_tap_playback", "linger_background",
    )

    @Test fun allFiveKeysRoundTripEveryBooleanCombinationAcrossPackageMetadata() {
        for ((pkg, distribution) in listOf(
            "dev.phosphor.mobil3.debug" to "debug",
            "dev.phosphor.mobil3" to "release",
            "dev.phosphor.mobil3" to "play",
            "dev.phosphor.mobil3.fortress" to "fortress",
        )) for (mask in 0 until 32) {
            val values = fiveKeys.mapIndexed { index, key -> key to (mask and (1 shl index) != 0) }.toMap()
            val archive = SettingsArchive.export(pkg, "1.9.0", distribution, metadata[3], values)
            val decoded = SettingsArchive.decode(archive.json)
            assertEquals(values, decoded.values, "$pkg/$distribution mask=$mask")
            assertEquals(fiveKeys.sorted(), archive.exportedKeys)
            assertEquals(pkg, decoded.sourcePackage)
            assertEquals(distribution, decoded.sourceDistribution)
            assertEquals("1.9.0", decoded.sourceVersion)
            val reexported = SettingsArchive.export(metadata[0], "2.0.0", "release", metadata[3], decoded.values)
            assertEquals(values, SettingsArchive.decode(reexported.json).values)
            assertEquals(values["pip_auto_enter"], dev.phosphor.mobil3.PictureInPicturePolicy.autoEnter(decoded.values))
            assertEquals(values["controls_always_visible"], dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.alwaysVisible(decoded.values))
            assertEquals(values["linger_background"], dev.phosphor.mobil3.BackgroundLifecyclePolicy.linger(decoded.values))
        }
    }

    @Test fun everyMissingOldKeyStaysAbsentAndDoesNotReplaceExplicitExistingValues() {
        for (missing in fiveKeys) for (mask in 0 until 32) {
            val existing = fiveKeys.mapIndexed { index, key -> key to (mask and (1 shl index) != 0) }.toMap()
            val provided = existing.filterKeys { it != missing }.mapValues { !it.value }
            val decoded = SettingsArchive.decode(export(provided).json).values
            assertEquals(provided, decoded)
            assertFalse(missing in decoded)
            // Map-level merge model only. KnownDefaultsTest separately inspects the actual typed Activity merge.
            val merged = mutableMapOf<String, Any>().apply { putAll(existing) }
            merged.putAll(decoded)
            assertEquals(existing[missing], merged[missing])
            provided.forEach { (key, value) -> assertEquals(value, merged[key]) }
        }
        val old = SettingsArchive.decode(export(mapOf("mode" to 4)).json).values
        assertTrue(fiveKeys.none(old::containsKey))
        assertTrue(dev.phosphor.mobil3.PictureInPicturePolicy.autoEnter(old))
        assertFalse(dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.alwaysVisible(old))
        assertFalse(dev.phosphor.mobil3.BackgroundLifecyclePolicy.linger(old))
    }

    @Test fun fiveKeysRejectInvalidTypesOnExportAndValidChecksumImport() {
        for (key in fiveKeys) for (invalid in listOf<Any>("false", "true", 0, 1, 0.5)) {
            rejectsBoth(key, invalid, "invalid_setting_type")
        }
    }

    @Test fun legalTuningAndExplicitFalseValuesSurviveCrossVersionArchiveRoundTrip() {
        val tuning = mapOf<String, Any>(
            "room" to "paper", "mode" to 0, "beam" to 2, "gain" to 2.25f,
            "auto_gain" to false, "grid" to true, "fullscreen" to false,
            "hud_mode" to 2, "band_mode" to 0, "focus" to 1.5f, "geom_amount" to 0.2f,
            "beam_random_range" to "4,25", "glow_random_range" to "0.1,0.8",
            "scope_rotation_locked" to false, "scope_locked_orientation" to 8,
            "custom_count" to 3, "custom_rgb" to "0,1,0,1,0,1,0.25,0.5,0.75",
            "cycle_seconds" to 60f, "cycle_per_track" to false,
        )
        val old = legacyFixture(tuning)
        val values = SettingsArchive.decode(old).values
        assertEquals(tuning, values)
        val current = SettingsArchive.export(metadata[0], "2.0.0", "release", metadata[3], values)
        assertEquals(portable(tuning), SettingsArchive.decode(current.json).values)
        assertTrue(fiveKeys.none(values::containsKey))
    }

    @Test fun cycleLegalEndpointsAndExistingInteriorValuesRoundTripExactly() {
        for (seconds in listOf(0.1f, 0.25f, 3f, 30f, 60f)) for (perTrack in listOf(false, true)) {
            val values = mapOf("cycle_seconds" to seconds, "cycle_per_track" to perTrack)
            assertEquals(values, SettingsArchive.decode(export(values).json).values)
        }
    }

    @Test fun cycleNextOutsideValuesFailExportAndValidChecksumImport() {
        for (seconds in listOf(Math.nextDown(0.1f), Math.nextUp(60f), 0f, -1f, 61f)) {
            rejectsBoth("cycle_seconds", seconds, "invalid_setting_value")
        }
    }

    @Test fun cycleNonfiniteAndInvalidTypesCannotEnterThroughPublicInterfaces() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals("invalid_setting_type", assertFailsWith<SettingsArchive.ArchiveException> {
                export(mapOf("cycle_seconds" to invalid))
            }.error)
        }
        // JSON has no nonfinite numeric literal. Quoted forms are invalid types, not numeric settings.
        for (invalid in listOf<Any>("NaN", "Infinity", "-Infinity", "0.1", true, false)) {
            rejectsBoth("cycle_seconds", invalid, "invalid_setting_type")
        }
        // Valid finite JSON numbers that overflow Float must also fail after checksum verification.
        for (invalid in listOf(java.math.BigDecimal("1e39"), java.math.BigDecimal("-1e39"))) {
            rejectsBoth("cycle_seconds", invalid, "invalid_setting_type")
        }
    }

    @Test fun rgbAndRangeLegalEndpointsKeepEveryComponentAndInactivePalette() {
        for (rgb in listOf("0,0,0,0,0,0,0,0,0", "1,1,1,1,1,1,1,1,1", "0,1,0.5,1,0,0.25,0.5,0.75,1")) {
            for (count in 0..3) {
                val values = mapOf("custom_rgb" to rgb, "custom_count" to count)
                assertEquals(values, SettingsArchive.decode(legacyFixture(values)).values)
                assertEquals(portable(values), SettingsArchive.decode(export(values).json).values)
            }
        }
        for ((key, ranges) in mapOf(
            "beam_random_range" to listOf("1,30", "1,1", "30,30", "6,20"),
            "glow_random_range" to listOf("0,0.98", "0,0", "0.98,0.98", "0.3,0.9"),
        )) for (range in ranges) {
            assertEquals(mapOf(key to range), SettingsArchive.decode(export(mapOf(key to range)).json).values)
        }
        val preset = SettingsArchive.decode(legacyFixture(mapOf("custom_count" to 0))).values
        assertEquals(mapOf("custom_count" to 0), preset)
        assertFalse("custom_rgb" in preset)
        assertEquals(portable(preset), SettingsArchive.decode(export(preset).json).values)
    }

    @Test fun rgbRejectsDroppedExtraTokensWrongCountsNonfiniteAndOutOfBoundsComponents() {
        val valid = "0,1,0.5,1,0,0.25,0.5,0.75,1"
        val invalidComponents = listOf("bad", "", "NaN", "Infinity", "-Infinity", "1e1000", "-0.01", "1.01",
            Math.nextDown(0f).toString(), Math.nextUp(1f).toString())
        val invalid = listOf("", "$valid,bad", "bad,$valid", "$valid,", ",$valid", "$valid,0",
            "0,1,0.5,1,0,0.25,0.5,0.75") + (0..8).flatMap { index ->
            invalidComponents.map { component ->
                valid.split(',').toMutableList().also { it[index] = component }.joinToString(",")
            }
        }
        for (rgb in invalid) rejectsBoth("custom_rgb", rgb, "invalid_setting_value")
        for (invalidType in listOf<Any>(1, true)) rejectsBoth("custom_rgb", invalidType, "invalid_setting_type")
        for (count in listOf(-1, 4)) rejectsBoth("custom_count", count, "invalid_setting_value")
    }

    @Test fun rangesRejectDroppedExtraTokensWrongCountsNonfiniteReversedAndOutsideBounds() {
        for ((key, valid, outside) in listOf(
            Triple("beam_random_range", "6,20", listOf("0.99,20", "6,30.01", "20,6",
                "${Math.nextDown(1f)},20", "6,${Math.nextUp(30f)}")),
            Triple("glow_random_range", "0.3,0.9", listOf("-0.01,0.9", "0.3,0.99", "0.9,0.3",
                "${Math.nextDown(0f)},0.9", "0.3,${Math.nextUp(0.98f)}")),
        )) {
            val malformed = listOf("", "1", "$valid,bad", "bad,$valid", "$valid,", ",$valid", "$valid,0",
                "bad,0.9", "0.3,bad", "NaN,0.9", "0.3,NaN", "Infinity,0.9", "0.3,Infinity",
                "-Infinity,0.9", "0.3,1e1000", "0.3,", ",0.9")
            for (range in malformed + outside) rejectsBoth(key, range, "invalid_setting_value")
            for (invalid in listOf<Any>(1, false)) rejectsBoth(key, invalid, "invalid_setting_type")
        }
    }

    @Test fun portableTuningNeverCarriesRuntimeConsentEndpointOrCalibrationValues() {
        val privateValues = mapOf<String, Any>(
            "last_source" to "mic", "random_track_title" to "private title", "cal_date" to "private date",
            "consent_seen" to true, "epilepsy_ack" to true, "host" to "private endpoint",
            "remote_host" to "private endpoint", "track_artist" to "private artist", "open_id" to 42,
        )
        val exported = export(privateValues + mapOf("gain" to 1.8332275f, "custom_count" to 0))
        assertEquals(privateValues.keys.sorted(), exported.skippedKeys)
        assertEquals(portable(mapOf("gain" to 1.8332275f, "custom_count" to 0)), SettingsArchive.decode(exported.json).values)
        assertFalse(exported.json.contains("private"))
        for ((key, value) in privateValues) {
            val decoded = SettingsArchive.decode(singleSettingFixture(key, value))
            assertTrue(decoded.values.isEmpty())
            assertEquals(listOf(key), decoded.skippedKeys)
        }
    }

    private fun portable(values: Map<String, *>): Map<String, Any> =
        values.filterKeys { it !in LightSettings.keys }.mapValues { requireNotNull(it.value) } + LightSettings.read(values).values()

    // Original /1 wire data stays covered independently of the /2 writer.
    private fun legacyFixture(values: Map<String, *>): String {
        val fields = values.toSortedMap().entries.joinToString(",") { (key, value) ->
            val encoded = when (value) {
                is String -> SettingsArchive.canonicalQuote(value)
                is Boolean -> value.toString()
                is Number -> java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
                else -> error("unsupported fixture")
            }
            SettingsArchive.canonicalQuote(key) + ":" + encoded
        }
        val canonical = "{" +
            "\"exported_at\":\"${metadata[3]}\"," +
            "\"schema\":\"phosphor.settings\\/1\"," +
            "\"settings\":{$fields}," +
            "\"source_distribution\":\"${metadata[2]}\"," +
            "\"source_package\":\"${metadata[0]}\"," +
            "\"source_version\":\"${metadata[1]}\"}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return JSONObject(canonical).put("content_sha256", digest).toString()
    }

    private fun export(values: Map<String, *>) =
        SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], values)

    private fun rejectsBoth(key: String, value: Any, error: String) {
        assertEquals(error, assertFailsWith<SettingsArchive.ArchiveException> {
            export(mapOf(key to value))
        }.error, "export $key=$value")
        assertEquals(error, assertFailsWith<SettingsArchive.ArchiveException> {
            SettingsArchive.decode(singleSettingFixture(key, value))
        }.error, "valid-checksum import $key=$value")
    }

    // Single-setting wire fixture only, not a substitute validator or a copied production owner.
    // Explicit canonical bytes let decode reach validation instead of stopping at a bad checksum.
    private fun singleSettingFixture(key: String, value: Any, schema: String = SettingsArchive.LEGACY_SCHEMA): String {
        val encoded = when (value) {
            is String -> SettingsArchive.canonicalQuote(value)
            is Boolean -> value.toString()
            is Number -> java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
            else -> error("unsupported fixture")
        }
        val canonical = "{" +
            "\"exported_at\":\"${metadata[3]}\"," +
            "\"schema\":${SettingsArchive.canonicalQuote(schema)}," +
            "\"settings\":{\"$key\":$encoded}," +
            "\"source_distribution\":\"${metadata[2]}\"," +
            "\"source_package\":\"${metadata[0]}\"," +
            "\"source_version\":\"${metadata[1]}\"}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return canonical.dropLast(1) + ",\"content_sha256\":\"$digest\"}"
    }

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
                "\"schema\":${SettingsArchive.canonicalQuote(SettingsArchive.SCHEMA)}," +
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
                "\"schema\":${SettingsArchive.canonicalQuote(SettingsArchive.SCHEMA)}," +
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
                "\"schema\":${SettingsArchive.canonicalQuote(SettingsArchive.SCHEMA)}," +
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

    @Test
    fun autoFrameScaleRoundTripsAndOldArchivesPreserveDestination() {
        val key = dev.phosphor.mobil3.ui.AutoFramePreference.KEY
        for (value in listOf(0.25f, 1f, 1.125f)) {
            val decoded = SettingsArchive.decode(export(mapOf(key to value)).json)
            assertEquals(mapOf(key to value), decoded.values)
        }
        val old = SettingsArchive.decode(export(mapOf("gain" to 2f)).json)
        assertFalse(key in old.values)
        val destination = mutableMapOf<String, Any>(key to 1.1f)
        destination.putAll(SettingsArchive.merge(old, destination))
        assertEquals(1.1f, destination[key])
        val legacy = SettingsArchive.decode(legacyFixture(mapOf(key to 0.5f)))
        assertFalse(key in legacy.values)
        assertEquals(listOf(key), legacy.skippedKeys)
    }

    @Test
    fun autoFrameScaleRejectsInvalidArchivesBeforeDestinationMutation() {
        val key = dev.phosphor.mobil3.ui.AutoFramePreference.KEY
        val destination = mutableMapOf<String, Any>(key to 0.75f, "gain" to 2f)
        val before = destination.toMap()
        for (invalid in listOf<Any>(
            0.249f, 1.126f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, "1.0", true,
        )) {
            val error = assertFailsWith<SettingsArchive.ArchiveException> {
                if (invalid is Number && !invalid.toDouble().isFinite()) export(mapOf(key to invalid))
                else {
                    val decoded = SettingsArchive.decode(singleSettingFixture(key, invalid, SettingsArchive.SCHEMA))
                    destination.putAll(SettingsArchive.merge(decoded, destination))
                }
            }
            assertTrue(error.error in setOf("invalid_setting_type", "invalid_setting_value"))
            assertEquals(before, destination)
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
            "\"schema\":${SettingsArchive.canonicalQuote(SettingsArchive.SCHEMA)}," +
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
