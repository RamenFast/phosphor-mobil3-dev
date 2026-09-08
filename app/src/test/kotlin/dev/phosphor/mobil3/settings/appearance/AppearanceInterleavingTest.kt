package dev.phosphor.mobil3.settings.appearance

import dev.phosphor.mobil3.settings.SettingsWriteOwner
import dev.phosphor.mobil3.settings.instrument.*
import dev.phosphor.mobil3.ui.AppearanceMigration
import org.junit.Assert.*
import org.junit.Test

/** Real production owners. Only their storage and native I/O ports are controlled by the tests. */
class AppearanceInterleavingTest {
    private class Rig {
        val a = AppearanceDocument.of(active = CuratedAppearances.dark, activeId = "curated:dark")
        val values = mutableMapOf<String, Any>(AppearancePreferences.KEY to AppearanceDocumentCodec.encode(a))
        val trace = mutableListOf<String>()
        var commitOk = true
        var rollbackOk = true
        var setup = CuratedInstrumentPresets.cleanXy.setup
        var sequence = 0L
        val replies = mutableMapOf<Long, (Int) -> Unit>()
        val settings = SettingsWriteOwner()
        lateinit var instrument: InstrumentWorkflow
        val appearance = AppearanceWorkflow(
            AppearancePreferences(settings, { values.toMap() }, { bytes ->
                trace += "appearance-save"
                values[AppearancePreferences.KEY] = bytes
                commitOk
            }, { before ->
                trace += "appearance-rollback"
                if (rollbackOk) {
                    values.remove(AppearancePreferences.KEY)
                    before.forEach { (key, value) -> if (value != null) values[key] = value }
                }
                rollbackOk
            }), AppearanceMigration::initial,
            failed = { instrument.persistenceFailed(InstrumentWorkflow.PersistenceFailure(it.message(), it.restored)) },
            sharedBlocked = { instrument.storageUncertain },
        )
        init {
            instrument = InstrumentWorkflow(object : InstrumentWorkflow.Native {
                override fun request(setup: InstrumentSetup): Long { trace += "native-request"; return ++sequence }
                override fun cancel(id: Long): Int { trace += "native-cancel"; return 2 }
                override fun release(id: Long) { trace += "native-release" }
            }, snapshot = { setup }, publish = { setup = it; trace += "instrument-publish" },
                persist = { value ->
                    if (appearance.uncertain) InstrumentWorkflow.PersistenceFailure("Recover appearance first", false)
                    else settings.write {
                        trace += "instrument-save"
                        values.putAll(value.preferenceValues())
                        null
                    }
                }, canApply = { true }, acknowledged = { true },
                waitOffMain = { id, callback -> replies[id] = callback }, changed = {})
            appearance.load()
        }
        fun recall() {
            val preset = CuratedInstrumentPresets.ambient.setup
            instrument.apply(preset, InstrumentWorkflow.Association("saved-instrument", "Ambient", preset))
            replies.getValue(sequence)(1)
            trace.clear()
        }
        fun retry() {
            if (appearance.recover()) instrument.retryPersistence()
        }
    }

    @Test fun delayedOldDocumentAfterPreviewAndApplyCannotOverwriteNewAppearanceOrInstrumentAssociation() {
        val r = Rig()
        r.recall()
        val association = r.instrument.association
        val originalSetup = r.setup
        r.appearance.preview(CuratedAppearances.glass)
        val instrumentTicket = r.instrument.beginSettingsImport()!!
        val appearanceTicket = r.appearance.ticket()
        r.appearance.apply(CuratedAppearances.light, "curated:light")
        assertFalse(r.appearance.accepts(appearanceTicket))
        // The Activity rejects the appearance ticket before entering instrument finish/edit.
        r.instrument.cancelSettingsImport(instrumentTicket)
        assertEquals(CuratedAppearances.light, r.appearance.committed!!.active)
        assertEquals(association, r.instrument.association)
        assertEquals(originalSetup, r.setup)
        assertTrue(r.trace.none { it.startsWith("native-") || it == "instrument-publish" || it == "instrument-save" })
    }

    @Test fun appearancePreviewApplySaveRenameDeleteResetNeverCancelPendingNativeRequest() {
        val r = Rig()
        r.recall()
        val association = r.instrument.association
        r.instrument.apply(CuratedInstrumentPresets.spectralBench.setup)
        r.trace.clear()
        r.appearance.preview(CuratedAppearances.glass)
        r.appearance.apply(CuratedAppearances.light)
        r.appearance.save("Saved appearance", CuratedAppearances.dark)
        val id = r.appearance.committed!!.activeId
        r.appearance.rename(id, "Renamed appearance")
        r.appearance.delete(id)
        r.appearance.reset()
        r.appearance.cancel()
        assertTrue(r.instrument.pending)
        assertEquals(association, r.instrument.association)
        assertTrue(r.trace.none { it.startsWith("native-") || it == "instrument-publish" || it == "instrument-save" })
    }

    @Test fun failedAppearanceRecoveryCannotClearInstrumentUncertaintyThroughUnrelatedSave() {
        val r = Rig()
        r.recall()
        val association = r.instrument.association
        r.appearance.preview(CuratedAppearances.glass)
        r.commitOk = false
        r.rollbackOk = false
        r.appearance.apply(CuratedAppearances.light)
        assertTrue(r.instrument.storageUncertain)
        r.trace.clear()
        r.instrument.retryPersistence()
        assertTrue(r.instrument.storageUncertain)
        assertFalse("instrument-save" in r.trace)
        r.retry()
        assertTrue(r.appearance.uncertain)
        assertTrue(r.instrument.storageUncertain)
        assertFalse("instrument-save" in r.trace)
        assertEquals(r.a, r.appearance.committed)
        assertEquals(association, r.instrument.association)
        assertTrue(r.trace.none { it.startsWith("native-") })
    }

    @Test fun successfulExplicitRecoveryOrdersCompleteAppearanceBeforeInstrumentAndNeverSavesPreview() {
        val r = Rig()
        r.recall()
        r.appearance.preview(CuratedAppearances.glass)
        r.commitOk = false
        r.rollbackOk = false
        r.appearance.apply(CuratedAppearances.light)
        r.commitOk = true
        r.trace.clear()
        r.retry()
        assertEquals(listOf("appearance-save", "instrument-save"), r.trace)
        assertEquals(r.a, AppearanceDocumentCodec.decode(r.values.getValue(AppearancePreferences.KEY) as String))
        assertFalse(r.appearance.uncertain)
        assertFalse(r.instrument.storageUncertain)
        assertFalse(r.appearance.blocked)
        assertNull(r.appearance.preview)
    }

    @Test fun appearanceTicketIsIndependentOfAnUnchangedInstrumentTicket() {
        val r = Rig()
        val instrumentTicket = r.instrument.beginSettingsImport()!!
        val appearanceTicket = r.appearance.ticket()
        r.appearance.preview(CuratedAppearances.glass)
        assertFalse(r.appearance.accepts(appearanceTicket))
        assertTrue(r.instrument.settingsImportPicked(instrumentTicket))
        r.instrument.cancelSettingsImport(instrumentTicket)
        assertTrue(r.trace.none { it.startsWith("native-") })
    }
}
