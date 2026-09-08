package dev.phosphor.mobil3.settings.instrument

import dev.phosphor.mobil3.settings.SettingsWriteOwner
import dev.phosphor.mobil3.settings.SettingsArchive
import dev.phosphor.mobil3.ui.LightSettings
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class InstrumentWorkflowTest {
    private val original = CuratedInstrumentPresets.cleanXy.setup
    private val ambient = CuratedInstrumentPresets.ambient.setup
    private val spectral = CuratedInstrumentPresets.spectralBench.setup

    private class Rig(start: InstrumentSetup) {
        var current = start
        var rendered = start
        var saved = start
        var capable = true
        var ack = false
        var persistenceError: InstrumentWorkflow.PersistenceFailure? = null
        var persistenceAction: ((InstrumentSetup) -> InstrumentWorkflow.PersistenceFailure?)? = null
        var sequence = 0L
        val outcomes = mutableMapOf<Long, Int>()
        val candidates = mutableMapOf<Long, InstrumentSetup>()
        val replies = mutableMapOf<Long, (Int) -> Unit>()
        val released = mutableListOf<Long>()
        val trace = mutableListOf<String>()
        val settings = SettingsWriteOwner()
        var notifications = 0
        val owner = InstrumentWorkflow(
            native = object : InstrumentWorkflow.Native {
                override fun request(setup: InstrumentSetup): Long {
                    val id = ++sequence
                    outcomes[id] = 0
                    candidates[id] = setup
                    trace += "request:$id"
                    return id
                }
                override fun cancel(id: Long): Int {
                    if (outcomes[id] == 0) outcomes[id] = 2
                    trace += "cancel:$id:${outcomes[id]}"
                    return outcomes[id] ?: 4
                }
                override fun release(id: Long) { released += id; trace += "release:$id" }
            },
            snapshot = { current },
            publish = { current = it; trace += "publish:${it.mode}" },
            persist = { setup -> settings.write {
                trace += "persist:${setup.mode}"
                val failure = persistenceAction?.invoke(setup) ?: persistenceError
                if (failure == null) saved = setup
                failure
            } },
            canApply = { capable }, acknowledged = { ack },
            waitOffMain = { id, callback -> replies[id] = callback },
            changed = { notifications++ },
        )
        fun admit(id: Long = sequence): Boolean {
            if (outcomes[id] != 0) return false
            outcomes[id] = if (capable) 1 else 3
            if (capable) rendered = candidates.getValue(id)
            trace += "admit:$id:${outcomes[id]}"
            return capable
        }
        fun reply(id: Long = sequence) { replies.getValue(id)(outcomes.getValue(id)) }
        fun manualFocus(focus: Float) = owner.edit {
            current = current.copy(focus = focus)
            rendered = rendered.copy(focus = focus)
            trace += "manual:$focus"
        }
    }

    @Test fun nativeCapabilityRefusalOffersSourceRecoveryDespiteStaleUiCapability() {
        val r = Rig(original)
        r.owner.apply(ambient) // UI capability allows reservation.
        val old = r.sequence
        r.capable = false // Native observes the actual geometry owner.
        assertFalse(r.admit())
        r.reply()
        assertTrue(r.owner.sourceControlsRequired)
        assertEquals(original, r.current)
        assertEquals(original, r.saved)
        assertFalse(r.trace.any { it.startsWith("publish:") || it.startsWith("persist:") })

        r.capable = true
        r.owner.apply(spectral)
        assertFalse(r.owner.sourceControlsRequired)
        r.reply(old) // Released rejection cannot replace current recovery state.
        assertFalse(r.owner.sourceControlsRequired)
        assertTrue(r.admit())
        r.reply()
        assertEquals(spectral, r.current)
        assertFalse(r.owner.sourceControlsRequired)
    }

    @Test fun localCapabilityRefusalOffersTheSameRecoveryWithoutNativeReservation() {
        val r = Rig(original)
        r.capable = false
        r.owner.apply(ambient)
        assertTrue(r.owner.sourceControlsRequired)
        assertEquals(0L, r.sequence)
        assertEquals(original, r.current)
        assertEquals(original, r.saved)
    }

    @Test fun delayedCommitPublishesOnlyCompleteExactCandidate() {
        val r = Rig(original)
        r.owner.apply(ambient)
        assertTrue(r.owner.pending)
        assertEquals(original, r.current)
        assertEquals(original, r.saved)
        r.admit()
        assertEquals(original, r.current)
        r.reply()
        assertEquals(ambient, r.current)
        assertEquals(ambient, r.rendered)
        assertEquals(ambient, r.saved)
        assertEquals(original, r.owner.undoSetup)
        assertEquals(listOf(1L), r.released)
        assertFalse(r.owner.pending)
    }

    @Test fun knownActiveOwnerRetainsRollbackFailureAndUsesPersistenceOnlyRetry() {
        val r = Rig(original)
        val failure = r.settings.write {
            r.settings.commit(commit = { false }, publish = { error("No publication after failed commit") }, rollback = { false })
        }!!
        r.owner.persistenceFailed(InstrumentWorkflow.PersistenceFailure(failure.message(), failure.restored))
        assertTrue(r.owner.storageUncertain)
        assertTrue(r.owner.editsBlocked)
        assertFalse(r.owner.automaticPersistenceAllowed)
        r.owner.apply(ambient)
        r.owner.undo()
        assertNull(r.owner.beginSettingsImport())
        assertThrows(IllegalStateException::class.java) { r.manualFocus(1.7f) }
        assertEquals(0L, r.sequence)
        assertEquals(original, r.current)
        r.owner.retryPersistence()
        assertFalse(r.owner.editsBlocked)
        assertTrue(r.owner.automaticPersistenceAllowed)
        assertEquals(0L, r.sequence)
    }

    @Test fun retainedRendererAndFreshUiRequireExactRecoveryBeforeSaving() {
        val r = Rig(original)
        r.rendered = ambient
        r.saved = spectral
        val failure = r.settings.write {
            r.settings.commit({ false }, { error("No native publication") }, { false })
        }!!
        r.owner.persistenceFailed(InstrumentWorkflow.PersistenceFailure(failure.message(), failure.restored))
        r.owner.restoreUnconfirmed(original)
        assertTrue(r.owner.restoreRequired)
        assertFalse(r.owner.automaticPersistenceAllowed)
        assertThrows(IllegalStateException::class.java) { r.manualFocus(1.7f) }
        r.owner.apply(spectral)
        assertNull(r.owner.beginSettingsImport())
        assertEquals(0L, r.sequence)
        r.owner.retryPersistence()
        assertEquals(1L, r.sequence)
        assertEquals(ambient, r.rendered)
        assertEquals(spectral, r.saved)
        assertTrue(r.owner.restoreRequired)
        assertTrue(r.trace.none { it.startsWith("persist:") })
        r.admit()
        assertEquals(original, r.rendered)
        assertEquals(spectral, r.saved)
        r.reply()
        assertEquals(original, r.saved)
        assertEquals(r.rendered, r.current)
        assertFalse(r.owner.restoreRequired)
        assertFalse(r.owner.storageUncertain)
        assertFalse(r.owner.editsBlocked)
        assertNull(r.owner.undoSetup)
        assertNull(r.owner.association)
        assertTrue(r.trace.indexOf("admit:1:1") < r.trace.indexOf("persist:0"))
    }

    @Test fun rejectedOrCancelledStartupRestoreDoesNotBecomeStorageOnlySuccess() {
        for (result in listOf(2, 3, 4)) {
            val r = Rig(original)
            r.rendered = ambient
            r.owner.restoreUnconfirmed(original)
            r.owner.retryPersistence()
            r.outcomes[1] = result
            r.reply()
            assertTrue(r.owner.restoreRequired)
            assertTrue(r.owner.editsBlocked)
            assertFalse(r.owner.automaticPersistenceAllowed)
            assertEquals(ambient, r.rendered)
            assertTrue(r.trace.none { it.startsWith("persist:") })
            assertEquals(result == 4, r.owner.uncertain)
        }
    }

    @Test fun startupRecoveryChecksCapabilityAndCannotRunAfterOwnerRetirement() {
        val r = Rig(original)
        r.rendered = ambient
        r.owner.restoreUnconfirmed(original)
        r.capable = false
        r.owner.retryPersistence()
        assertEquals(0L, r.sequence)
        assertTrue(r.owner.restoreRequired)
        r.capable = true
        r.owner.retryPersistence()
        r.owner.close()
        assertFalse(r.admit())
        r.reply()
        assertEquals(ambient, r.rendered)
        assertTrue(r.trace.none { it.startsWith("persist:") })
        r.owner.retryPersistence()
        assertEquals(1L, r.sequence)
    }

    @Test fun lateCancelledStartupReplyCannotAcknowledgeReplacementRecovery() {
        val r = Rig(original)
        r.rendered = ambient
        r.owner.restoreUnconfirmed(original)
        r.owner.retryPersistence()
        r.owner.settle("Source changed")
        r.owner.retryPersistence()
        assertEquals(2L, r.sequence)
        r.reply(1)
        assertTrue(r.owner.restoreRequired)
        assertTrue(r.owner.pending)
        assertEquals(ambient, r.rendered)
        r.admit(2)
        r.reply(2)
        assertFalse(r.owner.restoreRequired)
        assertEquals(original, r.rendered)
        assertEquals(original, r.saved)
        assertEquals(listOf(1L, 2L), r.released)
    }

    @Test fun committedRestoreThenStorageFailureRetainsKnownActivePersistenceOnlyRetry() {
        val r = Rig(original)
        r.rendered = ambient
        r.owner.restoreUnconfirmed(original)
        r.persistenceError = InstrumentWorkflow.PersistenceFailure("Disk failed", false)
        r.owner.retryPersistence()
        r.admit()
        r.reply()
        assertFalse(r.owner.restoreRequired)
        assertTrue(r.owner.storageUncertain)
        assertEquals(original, r.rendered)
        assertEquals(original, r.current)
        val count = r.sequence
        r.persistenceError = null
        r.owner.retryPersistence()
        assertEquals(count, r.sequence)
        assertFalse(r.owner.storageUncertain)
        assertEquals(r.rendered, r.saved)
    }

    @Test fun committedCancelReconcilesBeforeManualBAndLateReplyCannotOverwriteB() {
        val r = Rig(original)
        r.owner.apply(ambient, InstrumentWorkflow.Association("curated:Ambient", "Ambient", ambient))
        r.admit()
        r.manualFocus(1.2f)
        val beforeReply = r.current
        val trace = r.trace.toList()
        assertEquals(listOf("request:1", "admit:1:1", "cancel:1:1", "publish:7", "persist:7", "release:1", "manual:1.2"), trace)
        r.reply()
        assertEquals(trace, r.trace)
        assertEquals(beforeReply, r.current)
        assertEquals(1.2f, r.current.focus)
        assertEquals(ambient.copy(focus = 1.2f), r.rendered)
        assertTrue(r.owner.modified)
    }

    @Test fun cancelledQueuedApplyCannotRunAfterLaterManualEdit() {
        val r = Rig(original)
        r.owner.apply(ambient)
        r.manualFocus(2f)
        assertFalse(r.admit())
        r.reply()
        assertEquals(original.copy(focus = 2f), r.current)
        assertEquals(r.current, r.rendered)
        assertEquals(original, r.saved)
        assertNull(r.owner.undoSetup)
        assertEquals(listOf(1L), r.released)
    }

    @Test fun settingsImportAndNestedLightEditSettleBeforeEnteringSynchronousWriteOwner() {
        val r = Rig(original)
        r.owner.apply(ambient)
        r.admit()
        r.owner.edit {
            r.settings.write {
                r.current = r.current.copy(focus = 2f)
                r.rendered = r.current
                r.trace += "import:focus"
            }
            r.owner.edit {
                r.settings.write {
                    r.current = r.current.copy(light = LightSettings(preset = 2))
                    r.rendered = r.current
                    r.trace += "manual:light"
                }
            }
        }
        r.reply()
        assertEquals(ambient.copy(focus = 2f, light = LightSettings(preset = 2)), r.current)
        assertTrue(r.trace.indexOf("persist:7") < r.trace.indexOf("import:focus"))
        assertEquals(1, r.released.size)
    }

    @Test fun supersededApplyReleasesOnlyItsReceiptAndStaleCallbackDoesNothing() {
        val r = Rig(original)
        r.owner.apply(ambient)
        r.owner.apply(spectral)
        assertEquals(listOf(1L), r.released)
        r.reply(1)
        assertTrue(r.owner.pending)
        assertEquals(original, r.current)
        r.admit(2)
        r.reply(2)
        assertEquals(spectral, r.current)
        assertEquals(listOf(1L, 2L), r.released)
    }

    @Test fun sourceChangeCancelsQueuedWorkAndCommittedResultPrecedesSourceGainPolicy() {
        for (commitFirst in listOf(false, true)) {
            val r = Rig(original)
            r.owner.apply(ambient)
            if (commitFirst) r.admit()
            r.owner.settle("Source changed")
            r.capable = false
            r.current = r.current.copy(gain = 4f)
            r.rendered = r.current
            assertFalse(r.admit())
            r.reply()
            assertEquals((if (commitFirst) ambient else original).copy(gain = 4f), r.current)
            r.owner.apply(spectral)
            assertEquals(1L, r.sequence)
            assertTrue(r.owner.status.contains("Desktop geometry"))
        }
    }

    @Test fun changedNativeCapabilityRejectsWithoutPublishingOrSaving() {
        val r = Rig(original)
        r.owner.apply(ambient)
        r.capable = false
        assertFalse(r.admit())
        r.reply()
        assertEquals(original, r.current)
        assertEquals(original, r.saved)
        assertEquals(original, r.rendered)
        assertTrue(r.owner.status.contains("rejected"))
    }

    @Test fun onDestroyReconcilesCommittedReceiptOnceAndLateReplyCannotReachReplacement() {
        for (committed in listOf(false, true)) {
            val r = Rig(original)
            r.owner.apply(ambient)
            if (committed) r.admit()
            r.owner.close()
            val count = r.notifications
            val replacement = Rig(spectral)
            r.reply()
            r.owner.close()
            assertEquals(count, r.notifications)
            assertEquals(spectral, replacement.current)
            assertEquals(if (committed) ambient else original, r.current)
            assertEquals(listOf(1L), r.released)
            assertThrows(IllegalStateException::class.java) { r.manualFocus(2f) }
        }
    }

    @Test fun delayedWorkerReplyIsDeliveredOnOwnerAndLostReplyResolvedByCancel() {
        val r = Rig(original)
        r.owner.apply(ambient)
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val delivered = AtomicReference<Int>()
        val worker = Thread {
            ready.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            delivered.set(1)
        }
        worker.start()
        assertTrue(ready.await(2, TimeUnit.SECONDS))
        r.admit()
        r.manualFocus(1.8f)
        release.countDown()
        worker.join(2000)
        assertFalse(worker.isAlive)
        r.replies.getValue(1)(delivered.get())
        assertEquals(ambient.copy(focus = 1.8f), r.current)
        assertEquals(listOf(1L), r.released)
    }

    @Test fun persistenceFailureKeepsActualCommittedUiAndPriorUndoWithoutClaimingSaved() {
        val r = Rig(original)
        r.persistenceError = InstrumentWorkflow.PersistenceFailure("Saving failed. Previous settings restored. Try again.", true)
        r.owner.apply(ambient)
        r.admit()
        r.reply()
        assertEquals(ambient, r.current)
        assertEquals(ambient, r.rendered)
        assertEquals(original, r.saved)
        assertEquals(original, r.owner.undoSetup)
        assertTrue(r.owner.unsaved)
        assertTrue(r.owner.status.startsWith("Active, not saved"))
        r.persistenceError = null
        r.owner.undo()
        r.admit()
        r.reply()
        assertEquals(original, r.current)
        assertFalse(r.owner.unsaved)
        assertNull(r.owner.undoSetup)
    }

    @Test fun guardClampedSetupIsModifiedAndImportNeverAcknowledges() {
        val rapid = ambient.copy(light = ambient.light.copy(seconds = 0.2f))
        val r = Rig(original)
        val record = InstrumentWorkflow.Association("rapid", "Rapid", rapid)
        r.owner.apply(rapid, record)
        r.admit()
        r.reply()
        assertEquals(1f, r.current.light.seconds)
        assertTrue(r.owner.modified)
        assertNotNull(r.owner.rapidReview)
        val collection = InstrumentPresetCollection.empty().create("Rapid", rapid)
        InstrumentPresetCodec.decode(InstrumentPresetCodec.encode(collection))
        assertFalse(r.ack)
        r.owner.allowRapid()
        assertEquals(1L, r.sequence)
        r.ack = true
        r.owner.allowRapid()
        r.admit()
        r.reply()
        assertEquals(rapid, r.current)
        assertFalse(r.owner.modified)
    }

    @Test fun deletionAndRenameOnlyChangeAssociationAndManualEditDoesNotMutateSavedRecord() {
        var collection = InstrumentPresetCollection.empty().create("Ambient", ambient)
        val record = collection.records.single()
        val r = Rig(original)
        r.owner.apply(record.setup, InstrumentWorkflow.Association(record.id, record.name, record.setup))
        r.admit(); r.reply()
        r.manualFocus(1f)
        assertEquals(ambient, collection.record(record.id).setup)
        collection = collection.rename(record.id, "Slow")
        r.owner.refreshAssociation(collection)
        assertEquals("Slow", r.owner.association?.name)
        assertTrue(r.owner.modified)
        r.owner.refreshAssociation(collection.delete(record.id))
        assertNull(r.owner.association)
        assertEquals(ambient.copy(focus = 1f), r.current)
    }

    @Test fun preferenceAllowlistContainsOnlyAuthoredTuning() {
        val values = ambient.copy(gain = 2.4f, autoGain = false, focus = 1.7f).preferenceValues()
        assertEquals(2.4f, values["gain"])
        assertEquals(false, values["auto_gain"])
        assertEquals(1.7f, values["focus"])
        assertFalse(values.keys.any { it.contains("source") || it.contains("root") || it.contains("pause") || it.contains("volume") })
        assertFalse(values.containsKey("fps"))
        assertEquals(ambient.light, LightSettings.read(values))
    }

    @Test fun actualSynchronousCommitFalseAndRollbackFailureBlockEditsUntilExplicitRecovery() {
        val r = Rig(original)
        val prior = original.preferenceValues() - "focus"
        var preferences = prior
        var fail = true
        var saves = 0
        r.persistenceAction = { setup ->
            val before = preferences
            r.settings.commit(
                commit = { preferences = setup.preferenceValues(); saves++; !fail },
                publish = { true },
                rollback = { preferences = before; false },
            )?.let { InstrumentWorkflow.PersistenceFailure(it.message(), it.restored) }
        }
        r.owner.apply(ambient)
        r.admit(); r.reply()
        assertEquals(prior, preferences)
        assertEquals(ambient, r.current)
        assertEquals(ambient, r.rendered)
        assertTrue(r.owner.storageUncertain)
        assertTrue(r.owner.editsBlocked)
        assertTrue(r.owner.status.contains("rollback failed"))
        assertThrows(IllegalStateException::class.java) { r.manualFocus(2f) }
        assertThrows(IllegalStateException::class.java) { r.owner.externalRestoreSaved() }
        r.owner.apply(spectral)
        assertEquals(1L, r.sequence)
        assertEquals(1, saves)
        r.owner.retryPersistence()
        assertTrue(r.owner.storageUncertain)
        fail = false
        r.owner.retryPersistence()
        assertFalse(r.owner.editsBlocked)
        assertFalse(r.owner.unsaved)
        assertEquals(ambient.preferenceValues(), preferences)
        assertEquals(1L, r.sequence)
        r.manualFocus(2f)
        assertEquals(ambient.copy(focus = 2f), r.current)
    }

    @Test fun unavailableReceiptNeverMeansSuccessAndCannotEnablePartialTuning() {
        val r = Rig(original)
        r.owner.apply(ambient)
        r.outcomes[1] = 4
        r.reply()
        assertTrue(r.owner.uncertain)
        assertTrue(r.owner.editsBlocked)
        assertEquals(original, r.current)
        assertEquals(original, r.saved)
        assertThrows(IllegalStateException::class.java) { r.manualFocus(2f) }
        r.owner.retryPersistence()
        r.owner.apply(spectral)
        assertEquals(1L, r.sequence)
        assertEquals(listOf(1L), r.released)
    }

    @Test fun repeatedApplicationsRetainOnlyOneOwnedReceiptAndUndoSlot() {
        val r = Rig(original)
        repeat(20) { index ->
            val prior = r.current
            r.owner.apply(if (index % 2 == 0) ambient else spectral)
            r.admit(); r.reply()
            assertEquals(prior, r.owner.undoSetup)
            assertEquals(index + 1, r.released.size)
            assertFalse(r.owner.pending)
        }
        r.owner.undo()
        r.admit(); r.reply()
        assertNull(r.owner.undoSetup)
        val count = r.sequence
        r.owner.undo()
        assertEquals(count, r.sequence)
    }

    @Test fun importReenteredInsideLightCommitCannotPartiallyWriteOrLeakTheOuterOwner() {
        val r = Rig(original)
        r.owner.apply(ambient)
        r.admit()
        r.owner.edit {
            r.settings.write {
                val nested = runCatching { r.settings.write { r.current = spectral } }
                assertTrue(nested.isFailure)
                assertEquals(ambient, r.current)
                r.current = r.current.copy(light = LightSettings(preset = 2))
                r.rendered = r.current
            }
        }
        r.reply()
        assertEquals(ambient.copy(light = LightSettings(preset = 2)), r.current)
        r.owner.edit { r.settings.write { r.current = r.current.copy(focus = 2f) } }
        assertEquals(2f, r.current.focus)
        assertEquals(listOf(1L), r.released)
    }

    @Test fun negativeReservationNeverSchedulesWaitOrReleasesAnotherReceipt() {
        for (code in listOf(-1L, -2L, -3L)) {
            var waits = 0
            var releases = 0
            val owner = InstrumentWorkflow(
                native = object : InstrumentWorkflow.Native {
                    override fun request(setup: InstrumentSetup) = code
                    override fun cancel(id: Long): Int = error("No owned receipt")
                    override fun release(id: Long) { releases++ }
                }, snapshot = { original }, publish = { error("No commit") }, persist = { error("No save") },
                canApply = { true }, acknowledged = { false }, waitOffMain = { _, _ -> waits++ }, changed = {},
            )
            owner.apply(ambient)
            assertEquals(0, waits)
            assertEquals(0, releases)
            assertFalse(owner.pending)
            assertFalse(owner.status.contains("applied"))
        }
    }

    @Test fun failedPresetPreservesEverySavedKeyAcrossAutomaticSourceAndGainWrites() {
        val r = Rig(original)
        var preferences = original.preferenceValues() - "focus"
        val prior = preferences
        r.persistenceError = InstrumentWorkflow.PersistenceFailure("Restored prior tuple", true)
        r.owner.apply(ambient)
        r.admit(); r.reply()
        var sourceSelections = 0
        repeat(2) {
            r.owner.settle("Select relay")
            if (r.owner.automaticPersistenceAllowed) preferences = preferences + ("gain" to r.current.gain)
            sourceSelections++
        }
        assertEquals(prior, preferences)
        assertEquals(2, sourceSelections)
        assertFalse(r.owner.editsBlocked)
        r.persistenceError = null
        r.persistenceAction = { preferences = it.preferenceValues(); null }
        r.owner.retryPersistence()
        assertEquals(ambient.preferenceValues(), preferences)
        assertTrue(r.owner.automaticPersistenceAllowed)
        r.manualFocus(1.9f)
        if (r.owner.automaticPersistenceAllowed) preferences = preferences + ("focus" to r.current.focus)
        assertEquals(1.9f, preferences["focus"])
    }

    @Test fun delayedSettingsReplyCannotOverwriteApplyManualOrEqualValueRoundTrip() {
        for (edit in listOf("apply", "manual", "roundtrip", "undo")) {
            val r = Rig(original)
            val ticket = r.owner.beginSettingsImport()!!
            assertTrue(r.owner.settingsImportPicked(ticket))
            when (edit) {
                "apply" -> { r.owner.apply(ambient); r.admit(); r.reply() }
                "manual" -> r.manualFocus(1.7f)
                "roundtrip" -> { r.manualFocus(1.7f); r.manualFocus(original.focus) }
                "undo" -> r.owner.undo()
            }
            val expected = r.current
            var mutations = 0
            assertFalse(edit, r.owner.finishSettingsImport(ticket) { mutations++; r.current = spectral })
            assertEquals(0, mutations)
            assertEquals(expected, r.current)
            assertNotNull(r.owner.beginSettingsImport())
        }
    }

    @Test fun importBeginsAfterReconcilingAlreadyCommittedApplyAndAcceptsUnchangedBaselineOnce() {
        val r = Rig(original)
        r.owner.apply(ambient)
        r.admit()
        val ticket = r.owner.beginSettingsImport()!!
        assertEquals(ambient, r.current)
        assertFalse(r.owner.pending)
        r.reply()
        assertTrue(r.owner.settingsImportPicked(ticket))
        var writes = 0
        assertTrue(r.owner.finishSettingsImport(ticket) {
            r.settings.write { writes++; r.current = spectral }
            r.owner.externalRestoreSaved()
        })
        assertFalse(r.owner.finishSettingsImport(ticket) { writes++ })
        assertEquals(1, writes)
        assertEquals(spectral, r.current)
    }

    @Test fun actualArchiveDecodeBehindProviderBarrierCannotOverwriteLaterPreset() {
        val r = Rig(original)
        val ticket = r.owner.beginSettingsImport()!!
        assertTrue(r.owner.settingsImportPicked(ticket))
        val document = SettingsArchive.export("dev.phosphor.mobil3", "2", "debug", "2026-09-08T00:00:00Z",
            original.preferenceValues()).json
        val reading = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val result = AtomicReference<SettingsArchive.ImportResult>()
        val failure = AtomicReference<Throwable>()
        val provider = Thread {
            try {
                reading.countDown()
                check(releaseRead.await(2, TimeUnit.SECONDS))
                result.set(SettingsArchive.decode(document))
            } catch (error: Throwable) { failure.set(error) }
        }
        provider.start()
        try {
            assertTrue(reading.await(2, TimeUnit.SECONDS))
            r.owner.apply(ambient); r.admit(); r.reply()
        } finally {
            releaseRead.countDown()
            provider.join(2000)
        }
        assertFalse(provider.isAlive)
        assertNull(failure.get())
        assertNotNull(result.get())
        var writes = 0
        assertFalse(r.owner.finishSettingsImport(ticket) {
            r.settings.write {
                writes++
                SettingsArchive.merge(result.get(), r.saved.preferenceValues())
                r.current = original
            }
        })
        assertEquals(0, writes)
        assertEquals(ambient, r.current)
        assertEquals(ambient, r.saved)
    }

    @Test fun cancelledStaleAndRetiredPickersCannotClaimReplacementOperation() {
        val r = Rig(original)
        val old = r.owner.beginSettingsImport()!!
        assertNull(r.owner.beginSettingsImport())
        r.owner.cancelSettingsImport(old)
        val next = r.owner.beginSettingsImport()!!
        assertFalse(r.owner.settingsImportPicked(old))
        r.owner.cancelSettingsImport(old)
        assertTrue(r.owner.settingsImportPicked(next))
        r.manualFocus(1.5f)
        assertFalse(r.owner.settingsImportPicked(next))
        val last = r.owner.beginSettingsImport()!!
        r.owner.close()
        assertFalse(r.owner.finishSettingsImport(last) { fail("Retired owner wrote") })
        assertFalse(r.owner.automaticPersistenceAllowed)
        assertNull(r.owner.beginSettingsImport())
    }

    @Test fun pendingLaterApplyInvalidatesImportBeforeNativeReplyOrSettingsMutation() {
        val r = Rig(original)
        val ticket = r.owner.beginSettingsImport()!!
        r.owner.apply(ambient)
        assertFalse(r.owner.finishSettingsImport(ticket) { fail("Stale import entered write owner") })
        assertTrue(r.owner.pending)
        r.admit(); r.reply()
        assertEquals(ambient, r.current)
    }

    @Test fun lightAndArchiveFailedRollbacksShareTheWorkflowRecoveryLatch() {
        for (adapter in listOf("light", "archive")) {
            val r = Rig(original)
            var preferences = original.preferenceValues()
            val prior = preferences
            val ticket = r.owner.beginSettingsImport()!!
            val transaction = {
                r.settings.write {
                    val failure = r.settings.commit(
                        commit = { preferences = ambient.preferenceValues(); false },
                        publish = { fail("Failed commit cannot publish"); false },
                        rollback = { false },
                    )!!
                    r.owner.persistenceFailed(InstrumentWorkflow.PersistenceFailure(failure.message(), failure.restored))
                }
            }
            if (adapter == "archive") assertTrue(r.owner.finishSettingsImport(ticket, transaction))
            else { r.owner.cancelSettingsImport(ticket); r.owner.edit(transaction) }
            assertNotEquals(prior, preferences)
            assertTrue(r.owner.storageUncertain)
            assertTrue(r.owner.unsaved)
            assertFalse(r.owner.automaticPersistenceAllowed)
            assertThrows(IllegalStateException::class.java) { r.manualFocus(2f) }
            r.owner.apply(spectral); r.owner.undo()
            assertEquals(0L, r.sequence)
            assertNull(r.owner.beginSettingsImport())
            var sourceStops = 0
            r.owner.settle("Stop source"); sourceStops++
            assertEquals(1, sourceStops)
            r.persistenceAction = { preferences = it.preferenceValues(); null }
            r.owner.retryPersistence()
            assertEquals(prior, preferences)
            assertFalse(r.owner.editsBlocked)
            assertFalse(r.owner.unsaved)
            assertEquals(0L, r.sequence)
        }
    }

    @Test fun verifiedExternalRollbackDoesNotInventUncertaintyOrClearExistingUncertainty() {
        val r = Rig(original)
        r.owner.persistenceFailed(InstrumentWorkflow.PersistenceFailure("Restored", true))
        assertTrue(r.owner.automaticPersistenceAllowed)
        r.manualFocus(1.8f)
        r.owner.persistenceFailed(InstrumentWorkflow.PersistenceFailure("Unknown rollback", false))
        r.owner.persistenceFailed(InstrumentWorkflow.PersistenceFailure("Later restored", true))
        assertTrue(r.owner.storageUncertain)
        assertTrue(r.owner.unsaved)
        assertFalse(r.owner.automaticPersistenceAllowed)
    }

    @Test fun storageRecoveryCannotRepairUnknownNativeReceiptOrAcceptOldImport() {
        val r = Rig(original)
        val ticket = r.owner.beginSettingsImport()!!
        r.owner.apply(ambient)
        r.outcomes[1] = 4
        r.reply()
        r.owner.persistenceFailed(InstrumentWorkflow.PersistenceFailure("Unknown rollback", false))
        val trace = r.trace.toList()
        r.owner.retryPersistence()
        assertEquals(trace, r.trace)
        assertTrue(r.owner.uncertain)
        assertTrue(r.owner.storageUncertain)
        assertFalse(r.owner.finishSettingsImport(ticket) { fail("Uncertain owner wrote") })
    }

    @Test fun waitLaunchFailureResolvesExactCommittedReceiptInsteadOfRetrying() {
        for (outcome in listOf(1, 2)) {
            var current = original
            var releases = 0
            var requests = 0
            val owner = InstrumentWorkflow(
                native = object : InstrumentWorkflow.Native {
                    override fun request(setup: InstrumentSetup): Long { requests++; return 42 }
                    override fun cancel(id: Long) = outcome
                    override fun release(id: Long) { assertEquals(42L, id); releases++ }
                }, snapshot = { current }, publish = { current = it }, persist = { null },
                canApply = { true }, acknowledged = { false }, waitOffMain = { _, _ -> error("Thread unavailable") }, changed = {},
            )
            owner.apply(ambient)
            assertEquals(if (outcome == 1) ambient else original, current)
            assertEquals(1, requests)
            assertEquals(1, releases)
            assertFalse(owner.pending)
        }
    }
}
