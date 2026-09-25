package dev.phosphor.mobil3.ui

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import dev.phosphor.mobil3.settings.appearance.*

/** Source checks identify adapter wiring. They do not execute Android or prove Compose lifecycle order. */
class AppearanceRuntimeWiringTest {
    private fun source(path: String): String = listOf(File("src/main/kotlin/dev/phosphor/mobil3", path),
        File("app/src/main/kotlin/dev/phosphor/mobil3", path)).first { it.isFile }.readText()
    private fun activity() = source("MainActivity.kt")

    @Test fun lifecyclePersistenceNeverWritesLegacyThemeOrDerivedCustomIds() {
        val body = activity().substringAfter("private fun saveTuning()").substringBefore("private fun restoreTuning")
        listOf("room", "ov_char", "ov_motion", "ov_radius", "ov_desig", "appearance_state").forEach { key ->
            assertFalse(body.contains("putString(\"$key\""))
            assertFalse(body.contains("putInt(\"$key\""))
        }
        assertFalse(body.contains("ui.room"))
        assertFalse(body.contains("appearanceValue"))
    }

    @Test fun settingsMergeUsesActualAppearanceMigrationInsideWriteBeforeSnapshotAndEditor() {
        val body = activity().substringAfter("private fun acceptSettingsArchive(").substringBefore("private val openSettingsArchive")
        val owner = body.indexOf("settingsWriteOwner.write {")
        val merge = body.indexOf("AppearanceMigration.merge(SettingsArchive.merge(decoded, prefs().all), prefs().all)")
        val snapshot = body.indexOf("preferenceValueSnapshots(")
        val editor = body.indexOf("val editor = prefs().edit()")
        assertTrue(owner >= 0 && merge > owner && snapshot > merge && editor > snapshot)
        assertTrue(body.contains("appearanceWorkflow?.imported"))
        assertTrue(body.indexOf("appearanceWorkflow?.imported") < body.indexOf("restoreTuning(lightPublished)"))
    }

    @Test fun independentAppearanceTicketIsCapturedBeforePickerAndCheckedOnBothReplies() {
        val launch = activity().substringAfter("override fun importSettings()").substringBefore("override fun startMic")
        assertTrue(launch.indexOf("pendingAppearanceImport = appearanceWorkflow?.ticket()") < launch.indexOf("openSettingsArchive.launch"))
        val callback = activity().substringAfter("private val openSettingsArchive").substringBefore("private val captureConsent")
        assertEquals(2, Regex("appearanceWorkflow\\?\\.accepts\\(appearanceTicket\\)").findAll(callback).count())
        assertTrue(callback.contains("owner.cancelSettingsImport(ticket)"))
        assertTrue(callback.contains("owner.finishSettingsImport(ticket)"))
    }

    @Test fun ordinaryAppearanceActionsHaveNoNativeTuningOrInstrumentEditAuthority() {
        val actions = activity().substringAfter("override fun previewAppearance").substringBefore("override fun recoverAppearance")
        listOf("PhosphorNative", "instrumentValueEdit", "instrumentEdit", ".settle(", ".forget(", "selectSource(").forEach {
            assertFalse("Unexpected authority: $it", actions.contains(it))
        }
        val legacy = activity().substringAfter("override fun setRoom(room:").substringBefore("override fun setFocus")
        assertTrue(legacy.contains("owner.apply(value"))
        assertFalse(legacy.contains("PhosphorNative"))
        val room = source("ui/Sheets.kt").substringAfter("fun RoomSheet(").substringBefore("private fun StyleSampleChip")
        assertFalse(room.contains("state.styleOverride ="))
        assertTrue(room.contains("onStyle(state.appearanceStyle.nextCharacter())"))
    }

    @Test fun lifecycleAndEditorRetirementCancelPreview() {
        val main = activity()
        listOf("onPause", "onStop").forEach { method ->
            assertTrue(main.substringAfter("override fun $method()").substringBefore("super.$method()").contains("appearanceWorkflow?.cancel()"))
        }
        assertTrue(main.contains("if (ui.pip) appearanceWorkflow?.cancel()"))
        assertTrue(main.substringAfter("override fun onDestroy()").substringBefore("instrumentWorkflow?.close()").contains("appearanceWorkflow?.close()"))
        assertTrue(source("ui/AppearanceEditor.kt").contains("onDispose { currentActions.value.cancelAppearancePreview() }"))
        assertTrue(source("ui/Sheets.kt").contains("onClosing = actions::cancelAppearancePreview"))
    }

    @Test fun runtimeUsesExplicitStyleRevisionAndGuardsThePreEffectDisplayedPalette() {
        val screen = source("ui/PhosphorScreen.kt")
        assertTrue(screen.contains("LaunchedEffect(state.appearanceRevision, reduced)"))
        assertTrue(screen.contains("val style = state.appearanceStyle"))
        assertTrue(screen.contains("if (lastRevision != state.appearanceRevision) lastShown"))
        assertFalse(screen.contains("LaunchedEffect(target.id)"))
        assertFalse(screen.contains(".style.overridden(state.styleOverride)"))
    }

    @Test fun editorHasExactInputsAccessibleActionsAndRootOwnedChildGestureHook() {
        val editor = source("ui/AppearanceEditor.kt")
        listOf("PREVIEW", "APPLY", "CANCEL", "SAVE AS NEW", "RENAME", "DELETE", "RESET TO AMOLED", "READABILITY WARNING").forEach {
            assertTrue("Missing editor action $it", editor.contains(it))
        }
        assertTrue(editor.contains("AppearanceEditorValues.value("))
        assertTrue(editor.contains(".heightIn(min = 48.dp).settingsChildInput().settingsFocusBorder(p)"))
        assertTrue(editor.contains("role = Role.Button"))
        assertTrue(editor.contains("contentDescription = label"))
        assertTrue(editor.contains("Key.Escape"))
        assertTrue(source("ui/Sheets.kt").contains("LocalSettingsGestureOwner provides settingsDismiss"))
    }

    @Test fun restylingPreservesExactAuthoredColorsAndAllExplicitStyleFields() {
        val source = CuratedAppearances.light
        val style = GlassStyle.copy(durationScale = 1.7f, densityScale = 1.2f, cornerRadius = androidx.compose.ui.unit.Dp(64f),
            monoProse = true, designators = true, panelAlphaScale = .25f)
        val value = AppearancePalette.restyled(source, style)
        assertEquals(source.colors, value.colors)
        assertEquals(style, AppearancePalette.style(value))
        assertEquals(source.dark, value.dark)
        assertEquals(source.accentFollowsBeam, value.accentFollowsBeam)
    }
}
