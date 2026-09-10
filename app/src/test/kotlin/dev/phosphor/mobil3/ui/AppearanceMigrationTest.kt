package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.appearance.AppearanceDocument
import dev.phosphor.mobil3.settings.appearance.AppearanceDocumentCodec
import dev.phosphor.mobil3.settings.appearance.AppearanceException
import dev.phosphor.mobil3.settings.appearance.AppearanceProvenance
import dev.phosphor.mobil3.settings.appearance.AppearanceRecord
import dev.phosphor.mobil3.settings.appearance.CuratedAppearances
import org.junit.Assert.*
import org.junit.Test

class AppearanceMigrationTest {
    private val key = AppearanceMigration.KEY
    private val user = AppearanceRecord("12345678-1234-4234-8234-123456789abc", "Saved custom", CuratedAppearances.light)

    private fun encode(document: AppearanceDocument) = AppearanceDocumentCodec.encode(document)
    private fun decode(values: Map<String, *>) = AppearanceMigration.stored(values)!!
    private fun rejected(block: () -> Unit) {
        try { block(); fail("invalid original data must remain recoverable") }
        catch (_: AppearanceException) { }
    }

    @Test fun emptyInstallSeedsTactileAmoledWithoutRewritingUpgradePrefs() {
        val empty = AppearanceMigration.initial(emptyMap<String, Any>())
        assertEquals(2, empty.active.lookVersion)
        assertEquals("curated:amoled", empty.activeId)
        val upgrade = AppearanceMigration.initial(mapOf("gain" to 2f))
        assertEquals(1, upgrade.active.lookVersion)
        assertEquals(CuratedAppearances.amoled, upgrade.active)
    }

    @Test fun cleanInstallUsesCuratedAmoledAndRetainsAllActualLegacyRows() {
        val result = AppearanceMigration.initial(mapOf("gain" to 2f))
        assertEquals(CuratedAppearances.amoled, result.active)
        assertEquals("curated:amoled", result.activeId)
        assertEquals(AppearanceProvenance(), result.provenance)
        assertEquals(13, result.legacy.size)
        for (room in Rooms) {
            val row = result.legacy.single { it.id == "legacy:${room.id}" }
            assertEquals(AppearancePalette.legacy(LegacyAppearanceInput(room.id)).value, row.value)
            assertEquals(room, AppearancePalette.palette(row.value, room.id, room.label))
        }
    }

    @Test fun existingValidDocumentWinsEvenOverUnrepresentableLegacyInputs() {
        val document = AppearanceDocument.of(active = CuratedAppearances.glass, activeId = "curated:glass")
        val original = mapOf(key to encode(document), "ov_radius" to Int.MAX_VALUE, "room" to true)
        repeat(3) { assertEquals(encode(document), encode(AppearanceMigration.initial(original))) }
    }

    @Test fun actualLegacyOverrideAndUnknownFallbackPreserveProvenance() {
        val original = mapOf("room" to "future_room", "ov_char" to 2, "ov_motion" to -1,
            "ov_radius" to 17, "ov_desig" to 0)
        val expected = AppearancePalette.legacy(LegacyAppearanceInput("future_room", 2, -1, 17, 0))
        val result = AppearanceMigration.initial(original)
        assertEquals(expected.value, result.active)
        assertEquals("legacy:current", result.activeId)
        assertEquals(14, result.legacy.size)
        assertEquals(AppearanceProvenance("future_room", 2, -1, 17, 0), result.provenance)
        assertEquals(encode(result), encode(AppearanceMigration.initial(original)))
        assertEquals(encode(result), encode(AppearanceMigration.initial(original + (key to encode(result)))))
    }

    @Test fun explicitSentinelsRemainDifferentFromAbsentKeys() {
        val sentinel = AppearanceMigration.initial(mapOf("ov_char" to -1, "ov_motion" to -1,
            "ov_radius" to -1, "ov_desig" to -1))
        assertEquals("legacy:amoled", sentinel.activeId)
        assertEquals(AppearancePalette.legacy(LegacyAppearanceInput()).value, sentinel.active)
        assertEquals(AppearanceProvenance(null, -1, -1, -1, -1), sentinel.provenance)
        assertNotEquals(AppearanceMigration.initial(emptyMap<String, Any>()).provenance, sentinel.provenance)
    }

    @Test fun corruptAndWrongTypeExistingStateIsNotOverwrittenByMigration() {
        for (raw in listOf<Any>("{broken", 1, true)) {
            val original = mapOf(key to raw, "room" to "glass")
            rejected { AppearanceMigration.initial(original) }
            assertEquals(raw, original[key])
        }
        val nullState = mapOf<String, Any?>(key to null)
        rejected { AppearanceMigration.initial(nullState) }
        assertTrue(nullState.containsKey(key))
        assertNull(nullState[key])
    }

    @Test fun wrongLegacyTypesAndOutOfDomainRadiusFailWithoutChangingInputs() {
        for (original in listOf(mapOf("room" to false), mapOf("ov_char" to 1L),
            mapOf("ov_radius" to 65), mapOf("ov_radius" to Int.MAX_VALUE))) {
            val before = original.toMap()
            rejected { AppearanceMigration.initial(original) }
            assertEquals(before, original)
        }
    }

    @Test fun unrelatedImportDoesNotParseCorruptAppearanceOrEmitAnAppearanceWrite() {
        val imported = mapOf<String, Any>("gain" to 2f)
        val existing = mapOf(key to "broken", "ov_radius" to Int.MAX_VALUE)
        assertEquals(imported, AppearanceMigration.merge(imported, existing))
        assertFalse(AppearanceMigration.merge(imported, existing).containsKey(key))
        assertEquals("broken", existing[key])
    }

    @Test fun completeReplacementRepairsWithoutInspectingCorruptPriorOrLegacyPatch() {
        val replacement = AppearanceDocument.of(active = CuratedAppearances.dark, activeId = "curated:dark")
        val imported = mapOf<String, Any>(key to encode(replacement), "room" to "future_room", "ov_radius" to 13)
        val existing = mapOf(key to "broken", "ov_radius" to Int.MAX_VALUE)
        assertEquals(imported, AppearanceMigration.merge(imported, existing))
        assertEquals(CuratedAppearances.dark, decode(AppearanceMigration.merge(imported, existing)).active)
        assertEquals("broken", existing[key])
    }

    @Test fun legacyUpdateUsesOriginalMergedResolverAndPreservesSavedRecords() {
        val baseline = AppearanceMigration.initial(mapOf("room" to "glass", "ov_radius" to 7))
        val saved = AppearanceDocument.of(active = user.value, activeId = user.id, users = listOf(user),
            legacy = baseline.legacy, provenance = baseline.provenance)
        val existing = mapOf<String, Any>(key to encode(saved), "room" to "glass", "ov_radius" to 7)
        val merged = AppearanceMigration.merge(mapOf("room" to "paper"), existing)
        val result = decode(merged)
        assertEquals(AppearancePalette.legacy(LegacyAppearanceInput("paper", radius = 7)).value, result.active)
        assertEquals(saved.users, result.users)
        assertEquals(saved.legacy, result.legacy)
        assertEquals("", result.activeId)
        assertEquals(AppearanceProvenance("paper", radius = 7), result.provenance)
        assertEquals(setOf("room", key), merged.keys)
        assertEquals(encode(saved), existing[key])
    }

    @Test fun legacyUpdateNeverSilentlyDiscardsCorruptUserCollection() {
        val existing = mapOf(key to "broken", "room" to "amoled")
        rejected { AppearanceMigration.merge(mapOf("room" to "light"), existing) }
        assertEquals("broken", existing[key])
    }

    @Test fun legacyUpdatePopulatesUnmigratedDocumentWithoutDroppingItsUsers() {
        val before = AppearanceDocument.of(users = listOf(user))
        val result = decode(AppearanceMigration.merge(mapOf("room" to "dark"), mapOf(key to encode(before))))
        assertEquals(before.users, result.users)
        assertEquals(13, result.legacy.size)
        assertEquals("legacy:dark", result.activeId)
        assertEquals(AppearancePalette.legacy(LegacyAppearanceInput("dark")).value, result.active)
    }

    @Test fun legacyOnlyImportCreatesOneCompleteDocumentWithNoWritesToOmittedKeys() {
        val imported = mapOf<String, Any>("ov_motion" to 1)
        val merged = AppearanceMigration.merge(imported, mapOf("room" to "paper"))
        assertEquals(setOf("ov_motion", key), merged.keys)
        val result = decode(merged)
        assertEquals(AppearancePalette.legacy(LegacyAppearanceInput("paper", motion = 1)).value, result.active)
        assertEquals(AppearanceProvenance("paper", motion = 1), result.provenance)
    }
}
