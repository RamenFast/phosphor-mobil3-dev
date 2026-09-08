package dev.phosphor.mobil3.settings.appearance

import dev.phosphor.mobil3.settings.SettingsWriteOwner
import dev.phosphor.mobil3.settings.SettingsArchive
import dev.phosphor.mobil3.settings.instrument.*
import dev.phosphor.mobil3.ui.AppearanceMigration
import dev.phosphor.mobil3.ui.AppearancePalette
import dev.phosphor.mobil3.ui.LegacyAppearanceInput
import dev.phosphor.mobil3.ui.Rooms
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class AppearanceWorkflowTest {
    private val a = AppearanceDocument.of(active = CuratedAppearances.dark, activeId = "curated:dark")
    private val b = CuratedAppearances.glass
    private val c = CuratedAppearances.light
    private val key = AppearancePreferences.KEY

    private class Rig(initial: Map<String, Any> = emptyMap()) {
        val values = initial.toMutableMap()
        val trace = mutableListOf<String>()
        val writes = mutableListOf<AppearanceDocument>()
        val failures = mutableListOf<SettingsWriteOwner.Failure>()
        var commitOk = true
        var rollbackOk = true
        var throwCommit = false
        var sharedBlocked = false
        val settings = SettingsWriteOwner()
        val preferences = AppearancePreferences(settings, { values.toMap() }, { encoded ->
            trace += "commit"
            writes += AppearanceDocumentCodec.decode(encoded)
            values[AppearancePreferences.KEY] = encoded
            if (throwCommit) error("disk unavailable")
            commitOk
        }, { before ->
            trace += "rollback"
            if (rollbackOk) {
                values.remove(AppearancePreferences.KEY)
                before.forEach { (key, value) -> if (value != null) values[key] = value }
            }
            rollbackOk
        })
        val owner = AppearanceWorkflow(preferences, AppearanceMigration::initial,
            failed = { failures += it }, changed = { trace += "changed" }, sharedBlocked = { sharedBlocked })
        fun load() = owner.load()
    }
    private fun rig(): Rig = Rig(mapOf(key to AppearanceDocumentCodec.encode(a))).also { it.load() }
    private fun stored(r: Rig) = AppearanceDocumentCodec.decode(r.values.getValue(key) as String)

    @Test fun cleanStartupCommitsActualMigrationBeforePublishingAndRetainsThirteenRows() {
        val r = Rig()
        r.load()
        assertEquals("commit", r.trace.first())
        assertEquals(CuratedAppearances.amoled, r.owner.committed!!.active)
        assertEquals(13, r.owner.committed!!.legacy.size)
        Rooms.forEach { room ->
            assertEquals(AppearancePalette.legacy(LegacyAppearanceInput(room.id)).value,
                r.owner.committed!!.legacy.single { it.id == "legacy:${room.id}" }.value)
        }
        assertEquals(r.owner.committed, stored(r))
    }

    @Test fun existingValidBytesWinWithoutWriteIncludingWhitespace() {
        val exact = " \n" + AppearanceDocumentCodec.encode(a) + "\n "
        val r = Rig(mapOf(key to exact, "room" to "glass", "ov_radius" to 99))
        r.load()
        r.load()
        assertEquals(a, r.owner.committed)
        assertEquals(exact, r.values[key])
        assertTrue(r.writes.isEmpty())
    }

    @Test fun legacyUnknownAndSentinelsRemainExactAndCurrentOverrideIsSaved() {
        val original = mapOf<String, Any>("room" to "future-room", "ov_char" to -1, "ov_motion" to 0,
            "ov_radius" to 8, "ov_desig" to -8)
        val r = Rig(original)
        r.load()
        val doc = r.owner.committed!!
        assertEquals("legacy:current", doc.activeId)
        assertEquals(14, doc.legacy.size)
        assertEquals("future-room", doc.provenance.room)
        assertEquals(-8, doc.provenance.designators)
        assertEquals(AppearancePalette.legacy(LegacyAppearanceInput("future-room", -1, 0, 8, -8)).value, doc.active)
        original.forEach { (k, value) -> assertEquals(value, r.values[k]) }
    }

    @Test fun failedMigrationRestoresExactAbsenceAndPublishesNoCandidate() {
        val r = Rig(mapOf("room" to "glass"))
        r.commitOk = false
        r.load()
        assertNull(r.owner.committed)
        assertNull(r.owner.effective)
        assertFalse(key in r.values)
        assertEquals("glass", r.values["room"])
        assertTrue(r.owner.unavailable)
        assertFalse(r.owner.uncertain)
        assertEquals(listOf("commit", "rollback", "changed"), r.trace)
    }

    @Test fun failedMigrationRollbackEntersUncertaintyAndRequiresExplicitCompleteReplacement() {
        val r = Rig()
        r.commitOk = false
        r.rollbackOk = false
        r.load()
        assertNull(r.owner.committed)
        assertTrue(r.owner.uncertain)
        assertFalse(r.owner.recover())
        r.commitOk = true
        r.owner.replace(a)
        assertEquals(a, stored(r))
        assertFalse(r.owner.uncertain)
    }

    @Test fun invalidLegacyRadiusOrTypeNeverWritesOrPublishes() {
        listOf(mapOf<String, Any>("ov_radius" to 65), mapOf("ov_motion" to "bad")).forEach { values ->
            val r = Rig(values)
            r.load()
            assertNull(r.owner.committed)
            assertTrue(r.owner.unavailable)
            assertTrue(r.writes.isEmpty())
            assertEquals(values, r.values)
        }
    }

    @Test fun corruptNewBytesNeverBecomeMigrationDefaultAndOnlyExplicitReplacementRepairs() {
        val r = Rig(mapOf(key to "\n{ damaged bytes \u2602", "room" to "glass"))
        val before = r.values.toMap()
        r.load()
        assertEquals(before, r.values)
        r.owner.reset()
        r.owner.preview(b)
        r.owner.apply(c)
        assertEquals(before, r.values)
        assertTrue(r.writes.isEmpty())
        r.owner.replace(a)
        assertEquals(a, r.owner.committed)
        assertEquals(a, stored(r))
        assertEquals("glass", r.values["room"])
    }

    @Test fun failedCorruptReplacementRestoresExactRawWrongType() {
        val r = Rig(mapOf(key to 42))
        r.load()
        r.commitOk = false
        r.owner.replace(a)
        assertEquals(42, r.values[key])
        assertNull(r.owner.committed)
        assertTrue(r.owner.unavailable)
    }

    @Test fun previewNeverEntersPreferencesExportOrInstrumentSnapshot() {
        val r = rig()
        val snapshot = CuratedInstrumentPresets.cleanXy.setup
        val instrumentBefore = snapshot.preferenceValues()
        val bytes = r.values[key]
        r.owner.preview(b)
        assertEquals(b, r.owner.effective)
        assertEquals(a, r.owner.committed)
        assertEquals(bytes, r.values[key])
        val export = SettingsArchive.export("dev.phosphor.test", "1", "debug", "2026-09-08T00:00:00Z", r.values)
        assertEquals(bytes, SettingsArchive.decode(export.json).values[key])
        assertEquals(instrumentBefore, snapshot.preferenceValues())
        assertFalse(key in instrumentBefore)
        assertTrue(r.writes.isEmpty())
    }

    @Test fun applyPublishesOnlyAfterDurabilityAndPreservesUnrelatedPreferences() {
        val r = rig()
        r.values["gain"] = 1.5f
        r.owner.preview(b)
        r.trace.clear()
        r.owner.apply(c, "curated:light")
        assertEquals("commit", r.trace.first())
        assertEquals(c, r.owner.committed!!.active)
        assertEquals(c, r.owner.effective)
        assertNull(r.owner.preview)
        assertEquals(1.5f, r.values["gain"])
    }

    @Test fun failedCommitAndSuccessfulRollbackRetainAuthoritativeDocumentAndExactPriorBytes() {
        val r = rig()
        val exact = " \n${r.values[key]}\n"
        r.values[key] = exact
        r.commitOk = false
        r.owner.apply(c)
        assertEquals(a, r.owner.committed)
        assertEquals(exact, r.values[key])
        assertFalse(r.owner.uncertain)
        assertTrue(r.failures.single().restored)
    }

    @Test fun commitExceptionStillRestoresExactPriorBytes() {
        val r = rig()
        val prior = r.values[key]
        r.throwCommit = true
        r.owner.apply(c)
        assertEquals(prior, r.values[key])
        assertEquals(a, r.owner.committed)
        assertTrue(r.failures.single().restored)
    }

    @Test fun failedRollbackClearsPreviewRetainsAuthoritativeAAndBlocksEveryAuthoredEdit() {
        val r = rig()
        r.owner.preview(b)
        r.commitOk = false
        r.rollbackOk = false
        r.owner.apply(c)
        assertTrue(r.owner.uncertain)
        assertEquals(a, r.owner.committed)
        assertEquals(a.active, r.owner.effective)
        val attempts = r.writes.size
        r.owner.preview(b)
        r.owner.reset()
        r.owner.save("ignored", c)
        r.owner.apply(c)
        r.owner.replace(AppearanceDocument.of())
        assertEquals(attempts, r.writes.size)
    }

    @Test fun failedThenSuccessfulRecoveryPersistsAAndNeverUnacceptedBOrC() {
        val r = rig()
        r.owner.preview(b)
        r.commitOk = false
        r.rollbackOk = false
        r.owner.apply(c)
        assertFalse(r.owner.recover())
        assertEquals(a, r.writes.last())
        assertTrue(r.owner.uncertain)
        r.commitOk = true
        assertTrue(r.owner.recover())
        assertEquals(a, stored(r))
        assertEquals(a, r.owner.committed)
        assertFalse(r.owner.uncertain)
    }

    @Test fun sharedUncertaintyKeepsEditsBlockedAfterAppearanceRecoveryUntilInstrumentFinishes() {
        val r = rig()
        r.sharedBlocked = true
        r.owner.persistenceFailed(SettingsWriteOwner.Failure("Saving settings", false))
        assertTrue(r.owner.recover())
        assertTrue(r.owner.blocked)
        val writes = r.writes.size
        r.owner.apply(c)
        assertEquals(writes, r.writes.size)
        r.sharedBlocked = false
        assertFalse(r.owner.blocked)
    }

    @Test fun savedNamedRecordsRenameDeleteAndResetPreserveAuthoredValues() {
        val r = rig()
        r.owner.save("Night bench", b)
        val id = r.owner.committed!!.activeId
        assertEquals("Night bench", r.owner.committed!!.users.single().name)
        r.owner.rename(id, "Glass bench")
        assertEquals("Glass bench", r.owner.committed!!.users.single().name)
        r.owner.apply(c, id)
        assertTrue(AppearanceCollection.of(r.owner.committed!!).modified)
        r.owner.delete(id)
        assertEquals(c, r.owner.committed!!.active)
        assertEquals("", r.owner.committed!!.activeId)
        r.owner.save("Retain me", b)
        val records = r.owner.committed!!.users
        r.owner.reset()
        assertEquals(CuratedAppearances.amoled, r.owner.committed!!.active)
        assertEquals(records, r.owner.committed!!.users)
    }

    @Test fun savingPreviewIsExplicitAndInvalidNamedOperationDoesNotPersist() {
        val r = rig()
        r.owner.preview(b)
        assertTrue(r.writes.isEmpty())
        r.owner.save("", b)
        assertTrue(r.writes.isEmpty())
        assertEquals(a, r.owner.committed)
        r.owner.save("Accepted preview", b)
        assertEquals(b, stored(r).active)
    }

    @Test fun everyAppearanceActionInvalidatesIndependentProviderTicket() {
        val actions: List<(AppearanceWorkflow, String) -> Unit> = listOf(
            { owner, _ -> owner.preview(b) }, { owner, _ -> owner.apply(c) },
            { owner, _ -> owner.save("New name", b) }, { owner, _ -> owner.reset() },
            { owner, id -> owner.rename(id, "Renamed") }, { owner, id -> owner.delete(id) },
            { owner, _ -> owner.select("curated:glass") },
        )
        actions.forEach { action ->
            val r = rig()
            r.owner.save("Saved", a.active)
            val id = r.owner.committed!!.activeId
            val ticket = r.owner.ticket()
            assertTrue(r.owner.accepts(ticket))
            action(r.owner, id)
            assertFalse(r.owner.accepts(ticket))
        }
    }

    @Test fun previewCancellationAndRetirementRestoreCommittedAndInvalidateOldTickets() {
        val r = rig()
        r.owner.preview(b)
        val ticket = r.owner.ticket()
        r.owner.cancel()
        assertEquals(a.active, r.owner.effective)
        assertFalse(r.owner.accepts(ticket))
        assertTrue(r.writes.isEmpty())
        r.owner.preview(b)
        val next = r.owner.ticket()
        r.owner.close()
        assertEquals(a.active, r.owner.effective)
        assertFalse(r.owner.accepts(next))
        r.owner.apply(c)
        r.owner.preview(b)
        assertEquals(a, r.owner.committed)
        assertTrue(r.writes.isEmpty())
    }

    @Test fun recreatedOwnerReadsCommittedNeverPriorPreview() {
        val r = rig()
        r.owner.preview(b)
        val recreated = Rig(r.values).also { it.load() }
        assertEquals(a.active, recreated.owner.effective)
        assertTrue(recreated.writes.isEmpty())
    }

    @Test fun successfulArchivePublicationDoesNotWriteAgainAndCancelsPreview() {
        val r = rig()
        r.owner.preview(b)
        val imported = AppearanceDocument.of(active = c, activeId = "curated:light")
        r.values[key] = AppearanceDocumentCodec.encode(imported)
        r.owner.imported(imported)
        assertEquals(imported, r.owner.committed)
        assertNull(r.owner.preview)
        assertTrue(r.writes.isEmpty())
    }

    @Test fun appearanceActionsUseTheCreatingThread() {
        val r = rig()
        val error = AtomicReference<Throwable>()
        Thread { try { r.owner.preview(b) } catch (failure: Throwable) { error.set(failure) } }.apply { start(); join() }
        assertTrue(error.get() is IllegalStateException)
        assertEquals(a, r.owner.committed)
    }
}
