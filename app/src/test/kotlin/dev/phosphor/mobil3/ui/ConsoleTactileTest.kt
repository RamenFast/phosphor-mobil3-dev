package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.SettingsArchive
import dev.phosphor.mobil3.settings.SettingsWriteOwner
import dev.phosphor.mobil3.settings.appearance.*
import org.junit.Assert.*
import org.junit.Test

class ConsoleTactileTest {
    @Test fun allFamiliesAndExtremeAuthoredPalettesHaveMeasuredOpaqueFloors() {
        val custom = (0..255 step 17).map { n ->
            val gray = (n shl 16) or (n shl 8) or n
            CuratedAppearances.glass.copy(colors = CuratedAppearances.glass.colors.copy(
                plane = gray, stone = 0xffffff - gray, ink = gray, muted = gray, accent = gray))
        }
        for (value in CuratedAppearances.all + custom) {
            val before = value.copy()
            val t = ConsoleTactileTokens.from(value)
            assertTrue(AppearanceContrast.ratio(t.edgeQuiet, t.well) >= 3)
            assertTrue(AppearanceContrast.ratio(t.focusRing, t.well) >= 3)
            for (c in listOf(t.raised, t.sunk)) {
                assertTrue(AppearanceContrast.ratio(c.ink, c.face) >= 4.5)
                if (AppearanceContrast.ratio(value.colors.ink, c.face) >= 4.5) assertEquals(value.colors.ink, c.ink)
                assertTrue(AppearanceContrast.ratio(c.edge, c.face) >= 4.5)
                assertTrue(AppearanceContrast.ratio(c.disabledInk, c.face) >= 3)
                assertTrue(AppearanceContrast.ratio(c.accent, c.face) >= 3)
            }
            assertEquals(before, value)
        }
    }

    @Test fun amoledIsBlackWithBoundedFaceAndStaticAuthoredBevel() {
        val value = CuratedAppearances.amoled.copy(lookVersion = 2)
        val t = ConsoleTactileTokens.from(value)
        assertEquals(0, t.well)
        assertEquals(0x141414, t.raised.face)
        assertEquals(0, t.sunk.face)
        assertTrue(AppearanceContrast.ratio(t.raised.high, t.raised.face) >= 1.6)
        assertEquals(t, ConsoleTactileTokens.from(value.copy()))
    }

    @Test fun deviceAndNarrowWindowLayoutsHaveFixedPhysicalWidthVectors() {
        assertFalse(ConsoleKeybedPolicy.tactile(1, false))
        assertFalse(ConsoleKeybedPolicy.tactile(2, true))
        assertTrue(ConsoleKeybedPolicy.tactile(2, false))
        // Actual ASUS348dp card leaves about306dp after its frame/padding.
        // A320dp window with the same insets leaves about234dp. These are inner widths.
        val vectors = listOf(
            Triple(306f, 1f, ConsoleKeybedPolicy.Layout(1, 56f, 64f, 56f, 48f)),
            Triple(306f, 1.3f, ConsoleKeybedPolicy.Layout(1, 56f, 67f, 56f, 48f)),
            Triple(306f, 2f, ConsoleKeybedPolicy.Layout(4, 306f, 306f, 306f, 306f)),
            Triple(234f, 1f, ConsoleKeybedPolicy.Layout(2, 234f, 64f, 56f, 48f)),
            Triple(234f, 1.3f, ConsoleKeybedPolicy.Layout(2, 234f, 67f, 56f, 48f)),
            Triple(234f, 2f, ConsoleKeybedPolicy.Layout(4, 234f, 234f, 234f, 234f)),
        )
        vectors.forEach { (width, font, expected) ->
            val actual = ConsoleKeybedPolicy.layout(width, font)
            assertEquals(expected, actual)
            if (actual.rows == 1) assertTrue(actual.primaryWidth + actual.modeWidth + actual.sourceWidth +
                actual.overflowWidth + 28f <= width)
            if (actual.rows == 2) assertTrue(actual.modeWidth + actual.sourceWidth + actual.overflowWidth + 16f <= width)
        }
        assertEquals(ConsoleKeybedPolicy.Layout(1, 67f, 67f, 56f, 48f), ConsoleKeybedPolicy.layout(306f, 1.3f, true))
    }

    @Test fun androidDisplayDensityKeepsPhysicalMinimumsAtEveryFontScale() {
        for (font in listOf(1f, 1.3f, 2f)) {
            with(androidx.compose.ui.unit.Density(2.75f, font)) {
                assertEquals(132, androidx.compose.ui.unit.Dp(48f).roundToPx())
                assertEquals(154, androidx.compose.ui.unit.Dp(56f).roundToPx())
            }
        }
    }

    @Test fun raisedAmoledLampExceedsItsContinuousOutlineAndRecessedStep() {
        val t = ConsoleTactileTokens.from(CuratedAppearances.amoled)
        assertTrue(AppearanceContrast.ratio(t.edgeQuiet, t.well) >= 3)
        assertTrue(t.raised.high > t.edgeQuiet)
        assertTrue(t.raised.high > t.raised.face)
        assertTrue(t.raised.low < t.raised.face)
        assertTrue(AppearanceContrast.ratio(t.raised.high, t.raised.face) >= 4.5)
    }

    @Test fun legacyCanonicalHashesStayExactAndExtensionIsStrict() {
        val hashes = listOf(
            "efa9bbf002eaadcf41cb130f01fc2478b9c9f5e8fb05d1a3fbf93e409e6b7406",
            "18d7e4b91a6c803c9060b1b2c443f7e6a005cd9297212d68c7ebfeb9a370e99b",
            "02b3e67158b15439bce1dfc81e07f0157275cb8b8d6b41423c31479fa6f87c91",
            "9391ffcd3fe0f42e6f04fe0e903e21e2cd8b818cb4b1a62c6136dc55610add68")
        CuratedAppearances.all.forEachIndexed { index, value ->
            val legacy = AppearanceDocument.of(active = value)
            val wire = AppearanceDocumentCodec.encode(legacy)
            assertFalse(wire.contains("look_version"))
            assertEquals(hashes[index], AppearanceDocumentCodec.contentSha256(legacy))
            assertEquals(wire, AppearanceDocumentCodec.encode(AppearanceDocumentCodec.decode(wire)))
            val tactile = AppearanceDocument.of(active = value.copy(lookVersion = 2))
            val modern = AppearanceDocumentCodec.encode(tactile)
            assertEquals(tactile, AppearanceDocumentCodec.decode(modern))
            assertTrue(modern.contains("\"look_version\":2"))
            listOf("1", "0", "3", "2.0", "2e0", "true", "null", "\"2\"").forEach { bad ->
                val error = assertThrows(AppearanceException::class.java) {
                    AppearanceDocumentCodec.decode(modern.replace("\"look_version\":2", "\"look_version\":$bad"))
                }
                assertNotEquals("checksum_mismatch", error.code)
            }
            val unknown = modern.replace("look_version", "unknown_look")
            assertEquals("invalid_fields", assertThrows(AppearanceException::class.java) {
                AppearanceDocumentCodec.decode(unknown)
            }.code)
            assertThrows(IllegalArgumentException::class.java) { value.copy(lookVersion = 3) }
        }
    }

    @Test fun styleDraftMigrationAndArchiveKeepAuthoredChoiceAndActualGlassGeometry() {
        val legacyGlass = AppearancePalette.legacy(LegacyAppearanceInput("glass")).value
        assertEquals(12, legacyGlass.radiusDp)
        assertEquals(0, CuratedAppearances.glass.radiusDp)
        for (base in listOf(legacyGlass, CuratedAppearances.glass)) {
            val value = base.copy(lookVersion = 2)
            assertEquals(value.radiusDp, AppearancePalette.style(value).cornerRadius.value.toInt())
            assertEquals(2, AppearancePalette.style(value).lookVersion)
            for (character in ChromeCharacter.entries) {
                val edit = StyleOverride(character = character)
                assertEquals(2, AppearancePalette.style(value).overridden(edit).lookVersion)
                assertEquals(2, AppearancePalette.editCurrentStyle(value, edit).lookVersion)
            }
            val draft = AppearanceEditorValues.value(value, AppearanceEditorValues.colors(value),
                "1.0", "1.0", value.radiusDp.toString(), value.panelAlphaScale.toString())
            assertEquals(2, draft.lookVersion)
            val wire = AppearanceDocumentCodec.encode(AppearanceDocument.of(active = value))
            val archive = SettingsArchive.export("dev.phosphor.mobil3.debug", "2", "debug",
                "2026-09-09T23:23:29Z", mapOf("appearance_state" to wire))
            val decoded = SettingsArchive.decode(archive.json)
            assertEquals(wire, decoded.values["appearance_state"])
            assertEquals(2, AppearanceMigration.stored(decoded.values)!!.active.lookVersion)
        }
        val saved = AppearanceRecord("00000000-0000-4000-8000-000000000001", "Tactile saved",
            CuratedAppearances.amoled.copy(lookVersion = 2))
        val existing = AppearanceDocumentCodec.encode(AppearanceDocument.of(active = saved.value, users = listOf(saved)))
        val merged = AppearanceMigration.merge(mapOf("room" to "glass"), mapOf("appearance_state" to existing))
        val importedLegacy = AppearanceMigration.stored(merged)!!
        assertEquals(1, importedLegacy.active.lookVersion)
        assertEquals(saved, importedLegacy.users.single())
        assertEquals(1, AppearanceMigration.initial(emptyMap<String, Any>()).active.lookVersion)
        assertEquals(1, AppearanceMigration.initial(mapOf("gain" to 1f)).active.lookVersion)
        assertEquals(1, AppearanceMigration.initial(mapOf("room" to "glass")).active.lookVersion)
    }

    @Test fun workflowPreviewCancelApplySaveResetAndRestartAreLossless() {
        val values = mutableMapOf<String, Any>()
        val preferences = AppearancePreferences(SettingsWriteOwner(), { values.toMap() },
            { values[AppearancePreferences.KEY] = it; true }, { true })
        val owner = AppearanceWorkflow(preferences, AppearanceMigration::initial)
        owner.load()
        val initial = values[AppearancePreferences.KEY]
        val tactile = owner.committed!!.active.copy(lookVersion = 2)
        owner.preview(tactile)
        assertEquals(2, owner.effective!!.lookVersion)
        assertEquals(initial, values[AppearancePreferences.KEY])
        owner.cancel()
        assertEquals(1, owner.effective!!.lookVersion)
        owner.apply(tactile)
        assertEquals(2, AppearanceDocumentCodec.decode(values[AppearancePreferences.KEY] as String).active.lookVersion)
        owner.save("Tactile bench", tactile)
        val saved = owner.committed!!.users.single()
        assertEquals(2, saved.value.lookVersion)
        owner.reset()
        assertEquals(1, owner.effective!!.lookVersion)
        assertEquals(saved, owner.committed!!.users.single())
        owner.select(saved.id)
        val resumed = AppearanceWorkflow(preferences, AppearanceMigration::initial)
        resumed.load()
        assertEquals(2, resumed.effective!!.lookVersion)
        assertEquals(owner.committed, resumed.committed)
    }
}
