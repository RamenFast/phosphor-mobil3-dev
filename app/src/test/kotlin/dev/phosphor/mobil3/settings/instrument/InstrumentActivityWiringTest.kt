package dev.phosphor.mobil3.settings.instrument

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Supplementary adapter checks. State-machine tests own behavioral evidence, not these source checks. */
class InstrumentActivityWiringTest {
    private fun source(relative: String): String = listOf(File("app/src/main/kotlin/dev/phosphor/mobil3/$relative"),
        File("src/main/kotlin/dev/phosphor/mobil3/$relative")).first { it.isFile }.readText()
    private fun section(start: String, end: String) = source("MainActivity.kt").substringAfter(start).substringBefore(end)

    @Test fun authoredSnapshotDoesNotUseMeasuredOrRemoteGain() {
        val capture = section("private fun captureInstrument()", "private fun publishInstrument(")
        assertTrue(capture.contains("gainValue, ui.localAutoGain, ui.focus"))
        assertFalse(capture.contains("ui.gain,"))
        assertFalse(capture.contains("ui.autoGain"))
        assertTrue(capture.contains("ui.light"))
        val tick = section("private val uiTick", "override fun onNewIntent")
        assertFalse(tick.contains("ui.localAutoGain ="))
        assertTrue(tick.contains("instrumentWorkflow?.pending != true"))
        assertTrue(tick.contains("if (ui.remote && ui.remoteGeometry) {\n                remoteGain"))
    }

    @Test fun completePresetPublicationHasNoNativeSetterSourceOrHoldSideEffect() {
        val publication = section("private fun publishInstrument(", "private fun persistInstrument(")
        assertFalse(publication.contains("PhosphorNative"))
        assertFalse(publication.contains("startSource"))
        assertFalse(publication.contains("displayPaused ="))
        assertFalse(publication.contains("remote ="))
        assertFalse(publication.contains("surface"))
        val initialize = section("private fun initializeInstruments()", "private fun refreshInstrumentState()")
        assertEquals(1, Regex("PhosphorNative.requestInstrument").findAll(initialize).count())
        assertTrue(initialize.contains("Thread({"))
        assertTrue(initialize.indexOf("Thread({") < initialize.indexOf("PhosphorNative.awaitInstrument"))
        assertTrue(initialize.contains("tick.post { if (!activityDestroyed) done(result) }"))
    }

    @Test fun tuningAndArchiveEntriesSettleBeforeSynchronousPersistenceOwner() {
        val edit = section("private fun <T> instrumentValueEdit", "private fun instrumentFailure")
        assertTrue(edit.indexOf("owner.settle") < edit.indexOf("owner.edit(block)"))
        assertTrue(edit.contains("owner.editsBlocked"))
        val imported = section("private fun acceptSettingsArchive", "private val openSettingsArchive")
        assertTrue(imported.indexOf("instrumentWorkflow?.settle") < imported.indexOf("settingsWriteOwner.write"))
        assertTrue(imported.contains("instrumentWorkflow?.editsBlocked"))
        val light = section("private fun applyLight", "override fun rollLight")
        assertTrue(light.contains("instrumentValueEdit(false) { settingsWriteOwner.write"))
        assertTrue(section("private fun saveTuning()", "private fun restoreTuning").contains("restoreSnapshots(preserved)"))
    }

    @Test fun lifecycleAndSourceChangesRetireBeforeLaterOwnerActions() {
        val destroy = section("override fun onDestroy()", "private var baseRoom")
        assertTrue(destroy.indexOf("instrumentWorkflow?.close()") < destroy.indexOf("activityDestroyed = true"))
        assertTrue(destroy.contains("instrumentDocuments.close()"))
        val source = section("private fun selectSource()", "private fun micRequestIsCurrent")
        assertTrue(source.indexOf("instrumentWorkflow?.settle") < source.indexOf("++sourceSelection"))
        val streams = section("override fun setRemoteStreams", "override fun disconnectRemote")
        assertTrue(streams.contains("instrumentWorkflow?.settle"))
    }

    @Test fun sheetRoutesEveryPresetActionThroughActivityAndModeBansHaveNoUiBypass() {
        val sheets = source("ui/Sheets.kt")
        assertTrue(sheets.contains("actions.openInstrument()"))
        assertFalse(sheets.contains("state.randomBanModes ="))
        val screen = source("ui/PhosphorScreen.kt")
        assertTrue(screen.contains("onBanModes = actions::setRandomBanModes"))
        assertTrue(screen.contains("Sheet.INSTRUMENT -> InstrumentPresetSheet"))
        val preset = source("ui/InstrumentPresetSheet.kt")
        for (action in listOf("applyInstrument", "saveInstrument", "updateInstrument", "renameInstrument", "duplicateInstrument",
            "deleteInstrument", "undoInstrument", "retryInstrumentSave", "chooseInstrumentImport", "commitInstrumentImport")) {
            assertTrue(action, preset.contains("actions.$action"))
        }
        assertTrue(preset.contains("heightIn(min = 48.dp)"))
        assertTrue(preset.contains("maxLines = Int.MAX_VALUE"))
    }
}
