package dev.phosphor.mobil3.settings.instrument

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Supplementary adapter checks. State-machine tests own behavioral evidence, not these source checks. */
class InstrumentActivityWiringTest {
    private fun source(relative: String): String = listOf(File("app/src/main/kotlin/dev/phosphor/mobil3/$relative"),
        File("src/main/kotlin/dev/phosphor/mobil3/$relative")).first { it.isFile }.readText()
    private fun section(start: String, end: String) = source("MainActivity.kt").substringAfter(start).substringBefore(end)

    @Test fun typedNativeRefusalReachesSourceNavigationWithoutStatusStringInference() {
        val refresh = section("private fun refreshInstrumentState()", "private fun instrumentEdit(")
        assertTrue(refresh.contains("ui.instrumentSourceRequired = owner.sourceControlsRequired"))
        val sheet = source("ui/InstrumentPresetSheet.kt")
        assertTrue(sheet.contains("if ((state.remote && state.remoteGeometry) || state.instrumentSourceRequired)"))
        assertTrue(sheet.contains("PresetKey(\"SOURCE CONTROLS\", p) { actions.instrumentSourceControls() }"))
        assertFalse(sheet.contains("instrumentApplyStatus.contains"))
    }

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
        val source = section("private fun selectSource(", "private fun micRequestIsCurrent")
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
        assertTrue(source("ui/LightSheet.kt").contains("LightKey(\"RECALL INSTRUMENT\", p, action = onRecallInstrument)"))
        assertTrue(screen.contains("onRecallInstrument = { actions.openInstrumentPresets(); sheet = Sheet.INSTRUMENT }"))
    }

    @Test fun wholeSettingsPickerAndProviderCarryTheSameAuthoredTicketBeforeMutation() {
        val launch = section("override fun importSettings()", "override fun startMic()")
        assertTrue(launch.indexOf("owner.beginSettingsImport()") < launch.indexOf("openSettingsArchive.launch"))
        assertTrue(launch.contains("pendingSettingsImport = ticket"))
        val decode = section("private val openSettingsArchive", "private val captureConsent")
        assertTrue(decode.contains("val ticket = pendingSettingsImport ?: return@registerForActivityResult"))
        assertTrue(decode.indexOf("owner.settingsImportPicked(ticket)") < decode.indexOf("Thread {"))
        assertTrue(decode.contains("owner.finishSettingsImport(ticket) { acceptSettingsArchive(decoded) }"))
        assertTrue(decode.contains("if (isFinishing || isDestroyed)"))
        assertTrue(decode.contains("owner.cancelSettingsImport(ticket)"))
    }

    @Test fun allAutomaticGainAndLifecycleWritesUseTheRecoveryPolicy() {
        val gain = section("private fun persistAutomaticGain()", "private fun saveTuning()")
        assertTrue(gain.indexOf("automaticPersistenceAllowed == false") < gain.indexOf("prefs().edit"))
        assertTrue(section("private val persistGain", "private val captureStatusReceiver").contains("persistAutomaticGain()"))
        val relay = section("override fun startRemoteHost", "override fun setRemoteStreams")
        assertTrue(relay.contains("persistAutomaticGain()"))
        assertFalse(relay.contains("putFloat(\"gain\""))
        assertTrue(section("private fun saveTuning()", "private fun restoreTuning").contains("automaticPersistenceAllowed == false"))
    }

    @Test fun bothSharedWriteAdaptersForwardTypedRollbackRatherThanOnlyAnErrorString() {
        val bridge = section("private fun reportTuningWriteFailure", "private fun staleSettingsImport")
        assertTrue(bridge.contains("InstrumentWorkflow.PersistenceFailure(message, failure.restored)"))
        assertTrue(bridge.contains("instrumentWorkflow?.persistenceFailed"))
        assertTrue(bridge.contains("tick.removeCallbacks(persistGain)"))
        assertTrue(section("private fun acceptSettingsArchive", "private val openSettingsArchive")
            .contains("error(reportTuningWriteFailure(it))"))
        assertTrue(section("private fun applyLight", "override fun rollLight").contains("reportTuningWriteFailure(failure)"))
    }

    @Test fun startupCreatesRecoveryOwnerBeforeAnyRestoreTimeLightWrite() {
        val create = section("override fun onCreate(", "override fun onResume()")
        val initialize = create.indexOf("initializeInstruments()")
        val restore = create.indexOf("restoreTuning()")
        assertTrue(initialize >= 0)
        assertTrue(restore > initialize)
        assertEquals(1, Regex("initializeInstruments\\(\\)").findAll(create).count())
        val restoreBody = section("private fun restoreTuning", "override fun captureConsentNeeded")
        assertTrue(restoreBody.contains("else if (!applyLight(it)) markLightRestoreUnconfirmed(it)"))
        assertTrue(restoreBody.contains("if (!lightPublished) markLightRestoreUnconfirmed(ui.light)"))
        val unconfirmed = section("private fun markLightRestoreUnconfirmed", "override fun captureConsentNeeded")
        assertTrue(unconfirmed.contains("instrumentWorkflow?.restoreUnconfirmed(captureInstrument())"))
        assertFalse(unconfirmed.contains("PhosphorNative"))
        assertTrue(source("ui/InstrumentPresetSheet.kt").contains("if (state.instrumentRestoreRequired) \"RESTORE DISPLAYED SETUP\""))
    }
}
