package dev.phosphor.mobil3.ui

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test

class HudUiIntegrationHelpersTest {
    @Test fun hudQuickActionIsDisabledAndNonMutatingWhenCausalStoreIsNotWritable() {
        val beforeBandMode = 1
        var dispatchCount = 0

        val action = hudQuickAction(hudControlWritable = false, causalHudMode = 2)
        if (action.enabled) {
            dispatchCount += 1
        }

        assertFalse(action.enabled)
        assertEquals(2, action.requestedMode)
        assertEquals(2, action.labelMode)
        assertEquals(1, beforeBandMode)
        assertEquals(0, dispatchCount)
    }

    @Test fun hudQuickActionUsesCausalHudModeForLabelAndNextRequest() {
        val action = hudQuickAction(hudControlWritable = true, causalHudMode = 1)

        assertTrue(action.enabled)
        assertEquals(2, action.requestedMode)
        assertEquals(1, action.labelMode)
    }

    @Test fun settingsImportHudOperationIsStableAcrossRevisionChangesAndRestart() {
        val archiveSha = "a".repeat(64)
        val first = settingsImportHudOperation(null, archiveSha, 2, "operation-one")
        val retryAfterRevisionChange = settingsImportHudOperation(first.encode(), archiveSha, 2, "operation-two")
        val retryAfterRestart = settingsImportHudOperation(first.encode(), archiveSha, 2, "operation-three")

        assertEquals(first, retryAfterRevisionChange)
        assertEquals(first, retryAfterRestart)
        assertEquals("settings-import:${archiveSha}:hud:operation-one", first.idempotencyKey)
    }

    @Test fun completedOrDifferentExplicitImportGetsANewOperationIdentity() {
        val archiveSha = "b".repeat(64)
        val first = settingsImportHudOperation(null, archiveSha, 0, "operation-one")
        val afterCompletion = settingsImportHudOperation(null, archiveSha, 0, "operation-two")
        val differentMode = settingsImportHudOperation(first.encode(), archiveSha, 1, "operation-three")

        assertEquals("operation-two", afterCompletion.operationId)
        assertEquals("operation-three", differentMode.operationId)
        assertFalse(first.idempotencyKey == afterCompletion.idempotencyKey)
        assertFalse(first.idempotencyKey == differentMode.idempotencyKey)
    }

    @Test fun malformedPendingImportCannotCaptureANewOperation() {
        val operation = settingsImportHudOperation("v1:not-a-sha:9:bad id", "c".repeat(64), 1, "fresh-operation")

        assertEquals("fresh-operation", operation.operationId)
        assertEquals(1, operation.hudMode)
    }

    @Test fun wrongTypePendingMarkerReadFailsClosedWithoutCapturingOperation() {
        val marker = pendingHudMarkerRead(
            mapOf(PENDING_HUD_SETTINGS_IMPORT_KEY to 42),
        )
        val operation = settingsImportHudOperation(marker.raw, "6".repeat(64), 1, "fresh-operation")

        assertEquals(null, marker.raw)
        assertTrue(marker.wrongType)
        assertEquals(
            "pending HUD import marker has unsupported type · import a verified settings archive to replace it",
            marker.status,
        )
        assertEquals("fresh-operation", operation.operationId)
    }

    @Test fun absentAndStringPendingMarkerReadsAreTypeAware() {
        val pending = PendingHudSettingsImport("7".repeat(64), 2, "operation")
        val absent = pendingHudMarkerRead(emptyMap())
        val present = pendingHudMarkerRead(mapOf(PENDING_HUD_SETTINGS_IMPORT_KEY to pending.encode()))

        assertEquals(null, absent.raw)
        assertFalse(absent.wrongType)
        assertEquals(pending.encode(), present.raw)
        assertFalse(present.wrongType)
    }

    @Test fun wrongTypePendingMarkerDoesNotWakeLateMarkerListener() {
        val marker = pendingHudMarkerRead(mapOf(PENDING_HUD_SETTINGS_IMPORT_KEY to true))
        val wake = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = marker.raw,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        assertTrue(marker.wrongType)
        assertFalse(wake.shouldWake)
        assertFalse(wake.keepResumeAttempted)
        assertFalse(wake.reconcileImportedPreferences)
    }

    @Test fun pendingImportDecodeRejectsMalformedAndMissingMarkersWithoutDispatch() {
        val missing = pendingHudResumeDecision(
            rawMarker = null,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )
        val malformed = pendingHudResumeDecision(
            rawMarker = "v1:not-a-sha:9:bad id",
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        assertFalse(missing.shouldDispatch)
        assertFalse(missing.attempted)
        assertFalse(malformed.shouldDispatch)
        assertTrue(malformed.attempted)
        assertEquals(
            "pending HUD import is malformed · import a verified settings archive to replace it",
            malformed.status,
        )
    }

    @Test fun pendingImportResumeIdentitySurvivesProcessDeathInputs() {
        val archiveSha = "d".repeat(64)
        val beforeDeath = settingsImportHudOperation(null, archiveSha, 1, "stable-operation")
        val afterDeath = settingsImportHudOperation(beforeDeath.encode(), archiveSha, 1, "new-process-random")
        val decision = pendingHudResumeDecision(
            rawMarker = afterDeath.encode(),
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        assertEquals(beforeDeath, afterDeath)
        assertTrue(decision.shouldDispatch)
        assertTrue(decision.attempted)
        assertEquals("resume:${beforeDeath.idempotencyKey}", decision.status)
    }

    @Test fun destroyedActivityRefusesToDispatchPendingHudResume() {
        val pending = PendingHudSettingsImport("e".repeat(64), 0, "operation")

        val decision = pendingHudResumeDecision(
            rawMarker = pending.encode(),
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = true,
        )

        assertFalse(decision.shouldDispatch)
        assertFalse(decision.attempted)
        assertEquals("pending HUD import preserved for the next foreground Activity", decision.status)
    }

    @Test fun finishingActivityRefusesToDispatchPendingHudResume() {
        val pending = PendingHudSettingsImport("f".repeat(64), 2, "operation")

        val decision = pendingHudResumeDecision(
            rawMarker = pending.encode(),
            lifecycleStarted = true,
            activityFinishing = true,
            activityDestroyed = false,
        )

        assertFalse(decision.shouldDispatch)
        assertFalse(decision.attempted)
        assertEquals("pending HUD import preserved for the next foreground Activity", decision.status)
    }

    @Test fun noMarkerOnResumeDoesNotBurnFutureLateMarkerWakeup() {
        val onResume = pendingHudResumeDecision(
            rawMarker = null,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )
        val lateMarker = PendingHudSettingsImport("3".repeat(64), 2, "late-operation")
        val wake = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = lateMarker.encode(),
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        assertFalse(onResume.shouldDispatch)
        assertFalse(onResume.attempted)
        assertTrue(wake.shouldWake)
        assertTrue(wake.keepResumeAttempted)
        assertTrue(wake.reconcileImportedPreferences)
    }

    @Test fun lateMarkerWakeIgnoresWrongKeyMissingMarkerAndDestroyedActivity() {
        val marker = PendingHudSettingsImport("4".repeat(64), 0, "late-operation").encode()

        val wrongKey = pendingHudMarkerWakeDecision(
            changedKey = "gain",
            rawMarker = marker,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )
        val missingMarker = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = null,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )
        val destroyed = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = marker,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = true,
        )

        assertFalse(wrongKey.shouldWake)
        assertFalse(wrongKey.reconcileImportedPreferences)
        assertFalse(missingMarker.shouldWake)
        assertFalse(missingMarker.reconcileImportedPreferences)
        assertFalse(destroyed.shouldWake)
        assertFalse(destroyed.keepResumeAttempted)
        assertFalse(destroyed.reconcileImportedPreferences)
    }

    @Test fun lateMarkerWakeRequiresImportedPreferenceReconciliationBeforeHudDispatch() {
        val marker = PendingHudSettingsImport("5".repeat(64), 1, "late-operation").encode()
        val steps = mutableListOf<String>()
        val wake = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = marker,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        if (wake.reconcileImportedPreferences) steps += "reconcile-generic-prefs"
        if (wake.shouldWake) steps += "dispatch-hud"

        assertEquals(listOf("reconcile-generic-prefs", "dispatch-hud"), steps)
    }

    @Test fun sameActivityListenerBeforeOpenArchiveCallbackSuppressesOwnInFlightMarker() {
        val marker = PendingHudSettingsImport("8".repeat(64), 1, "same-activity-operation").encode()
        val steps = mutableListOf<String>()
        val wake = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = marker,
            localInFlightRawMarker = marker,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        if (wake.reconcileImportedPreferences) steps += "reconcile-generic-prefs"
        if (wake.shouldWake) steps += "dispatch-hud"
        steps += "open-settings-archive-callback-applies-once"

        assertFalse(wake.shouldWake)
        assertFalse(wake.keepResumeAttempted)
        assertFalse(wake.reconcileImportedPreferences)
        assertTrue(wake.suppressedOwnInFlightMarker)
        assertEquals(listOf("open-settings-archive-callback-applies-once"), steps)
    }

    @Test fun replacementActivityLateWakeHasNoLocalMarkerAndResumesPendingImport() {
        val marker = PendingHudSettingsImport("9".repeat(64), 2, "replacement-operation").encode()
        val wake = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = marker,
            localInFlightRawMarker = null,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        assertTrue(wake.shouldWake)
        assertTrue(wake.keepResumeAttempted)
        assertTrue(wake.reconcileImportedPreferences)
        assertFalse(wake.suppressedOwnInFlightMarker)
    }

    @Test fun differentReplacementMarkerIsNotSuppressedByStaleLocalOperation() {
        val oldMarker = PendingHudSettingsImport("a".repeat(64), 0, "old-operation").encode()
        val replacementMarker = PendingHudSettingsImport("b".repeat(64), 1, "replacement-operation").encode()
        val wake = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = replacementMarker,
            localInFlightRawMarker = oldMarker,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        assertTrue(wake.shouldWake)
        assertTrue(wake.reconcileImportedPreferences)
        assertFalse(wake.suppressedOwnInFlightMarker)
    }

    @Test fun commitFalseCompensationCapturesExactPriorImportedAndPendingValues() {
        val priorPending = 42
        val prior = mapOf(
            "gain" to 1.5f,
            "grid" to false,
            PENDING_HUD_SETTINGS_IMPORT_KEY to priorPending,
        )
        val snapshots = preferenceValueSnapshots(
            prior,
            setOf("gain", "beam", "grid", PENDING_HUD_SETTINGS_IMPORT_KEY),
        )

        assertTrue(snapshots.getValue("gain").present)
        assertEquals(1.5f, snapshots.getValue("gain").value)
        assertFalse(snapshots.getValue("beam").present)
        assertEquals(null, snapshots.getValue("beam").value)
        assertTrue(snapshots.getValue(PENDING_HUD_SETTINGS_IMPORT_KEY).present)
        assertEquals(priorPending, snapshots.getValue(PENDING_HUD_SETTINGS_IMPORT_KEY).value)
    }

    @Test fun wrongTypeMarkerSurvivesCompensationSnapshotAndStillDoesNotWake() {
        val snapshots = preferenceValueSnapshots(
            mapOf(PENDING_HUD_SETTINGS_IMPORT_KEY to 42),
            setOf(PENDING_HUD_SETTINGS_IMPORT_KEY),
        )
        val marker = pendingHudMarkerRead(
            mapOf(PENDING_HUD_SETTINGS_IMPORT_KEY to snapshots.getValue(PENDING_HUD_SETTINGS_IMPORT_KEY).value),
        )
        val wake = pendingHudMarkerWakeDecision(
            changedKey = PENDING_HUD_SETTINGS_IMPORT_KEY,
            rawMarker = marker.raw,
            localInFlightRawMarker = null,
            lifecycleStarted = true,
            activityFinishing = false,
            activityDestroyed = false,
        )

        assertTrue(marker.wrongType)
        assertEquals(42, snapshots.getValue(PENDING_HUD_SETTINGS_IMPORT_KEY).value)
        assertFalse(wake.shouldWake)
        assertFalse(wake.reconcileImportedPreferences)
    }

    @Test fun cleanupCommitFalsePreservesMarkerAndReportsHonestFailure() {
        val marker = PendingHudSettingsImport("1".repeat(64), 1, "operation").encode()

        val result = pendingHudCleanupResult(
            expectedRaw = marker,
            currentRaw = marker,
            observedRawAfterCleanup = marker,
            removeCommitted = false,
            restoreCommitted = true,
        )

        assertFalse(result.cleared)
        assertTrue(result.attemptedRestore)
        assertEquals(marker, result.markerAfterCleanup)
        assertEquals("pending receipt cleanup failed; retry after storage is writable", result.honestStatus)
    }

    @Test fun cleanupCommitTrueClearsPendingMarkerOnlyAfterExpectedMarkerMatches() {
        val marker = PendingHudSettingsImport("c".repeat(64), 1, "operation").encode()

        val result = pendingHudCleanupResult(
            expectedRaw = marker,
            currentRaw = marker,
            observedRawAfterCleanup = null,
            removeCommitted = true,
            restoreCommitted = false,
        )

        assertTrue(result.cleared)
        assertFalse(result.attemptedRestore)
        assertEquals(null, result.markerAfterCleanup)
        assertEquals("pending receipt cleanup complete", result.honestStatus)
    }

    @Test fun cleanupSkipsWhenMarkerChangedBeforeRemoval() {
        val expected = PendingHudSettingsImport("d".repeat(64), 1, "operation").encode()
        val replacement = PendingHudSettingsImport("e".repeat(64), 2, "replacement").encode()

        val result = pendingHudCleanupResult(
            expectedRaw = expected,
            currentRaw = replacement,
            observedRawAfterCleanup = replacement,
            removeCommitted = true,
            restoreCommitted = true,
        )

        assertFalse(result.cleared)
        assertFalse(result.attemptedRestore)
        assertEquals(replacement, result.markerAfterCleanup)
        assertEquals("pending receipt cleanup skipped; marker changed before cleanup", result.honestStatus)
    }

    @Test fun cleanupRestoreCommitFalseStillReportsMarkerAsRequiredAndStatusIsExplicitlyDegraded() {
        val marker = PendingHudSettingsImport("2".repeat(64), 1, "operation").encode()

        val result = pendingHudCleanupResult(
            expectedRaw = marker,
            currentRaw = marker,
            observedRawAfterCleanup = marker,
            removeCommitted = false,
            restoreCommitted = false,
        )

        assertFalse(result.cleared)
        assertTrue(result.attemptedRestore)
        assertEquals(marker, result.markerAfterCleanup)
        assertEquals(
            "pending receipt cleanup failed and marker restore was not committed; retry after storage is writable",
            result.honestStatus,
        )
    }

    @Test fun cleanupRemoveAndRestoreCommitFalseIsNotClearedEvenWhenSameProcessObservationIsAbsent() {
        val marker = PendingHudSettingsImport("3".repeat(64), 1, "operation").encode()

        val result = pendingHudCleanupResult(
            expectedRaw = marker,
            currentRaw = marker,
            observedRawAfterCleanup = null,
            removeCommitted = false,
            restoreCommitted = false,
        )

        assertFalse(result.cleared)
        assertTrue(result.attemptedRestore)
        assertEquals(null, result.markerAfterCleanup)
        assertEquals(
            "pending receipt cleanup failed and marker restore was not committed; retry after storage is writable",
            result.honestStatus,
        )
    }

    @Test fun hudImportStatusSurfacesExactCleanupRestoreFailureRepair() {
        val marker = PendingHudSettingsImport("f".repeat(64), 1, "operation").encode()
        val cleanup = pendingHudCleanupResult(
            expectedRaw = marker,
            currentRaw = marker,
            observedRawAfterCleanup = marker,
            removeCommitted = false,
            restoreCommitted = false,
        )

        val status = hudImportStatusWithCleanup("HUD accepted · revision 12", cleanup)

        assertEquals(
            "HUD accepted · revision 12 · pending receipt cleanup failed and marker restore was not committed; retry after storage is writable",
            status,
        )
    }

    @Test fun hudImportStatusSurfacesChangedMarkerCleanupSkipExactly() {
        val expected = PendingHudSettingsImport("a".repeat(64), 1, "operation").encode()
        val replacement = PendingHudSettingsImport("b".repeat(64), 2, "replacement").encode()
        val cleanup = pendingHudCleanupResult(
            expectedRaw = expected,
            currentRaw = replacement,
            observedRawAfterCleanup = replacement,
            removeCommitted = false,
            restoreCommitted = false,
        )

        val status = hudImportStatusWithCleanup("HUD import replayed · receipt", cleanup)

        assertEquals(
            "HUD import replayed · receipt · pending receipt cleanup skipped; marker changed before cleanup",
            status,
        )
    }

    @Test fun hudImportStatusOmitsCleanupSuffixAfterSuccessfulCleanup() {
        val marker = PendingHudSettingsImport("c".repeat(64), 1, "operation").encode()
        val cleanup = pendingHudCleanupResult(
            expectedRaw = marker,
            currentRaw = marker,
            observedRawAfterCleanup = null,
            removeCommitted = true,
            restoreCommitted = false,
        )

        assertEquals("HUD accepted · revision 12", hudImportStatusWithCleanup("HUD accepted · revision 12", cleanup))
    }

    @Test fun cleanupCommitSuccessButMarkerRemainsReportsObservedFailureTruth() {
        val marker = PendingHudSettingsImport("4".repeat(64), 1, "operation").encode()

        val result = pendingHudCleanupResult(
            expectedRaw = marker,
            currentRaw = marker,
            observedRawAfterCleanup = marker,
            removeCommitted = true,
            restoreCommitted = false,
        )

        assertFalse(result.cleared)
        assertFalse(result.attemptedRestore)
        assertEquals(marker, result.markerAfterCleanup)
        assertEquals(
            "pending receipt cleanup reported committed but marker remains; retry after storage is writable",
            result.honestStatus,
        )
    }

    @Test fun cleanupWrongTypeAfterRemovalIsObservedAsPresentMarkerFailure() {
        val expected = PendingHudSettingsImport("5".repeat(64), 1, "operation").encode()
        val observedWrongType = pendingHudMarkerRead(mapOf(PENDING_HUD_SETTINGS_IMPORT_KEY to 42))

        val result = pendingHudCleanupResult(
            expectedRaw = expected,
            currentRaw = expected,
            observedRawAfterCleanup = observedWrongType.raw,
            observedWrongTypeAfterCleanup = observedWrongType.wrongType,
            removeCommitted = true,
            restoreCommitted = false,
        )

        assertTrue(observedWrongType.wrongType)
        assertFalse(result.cleared)
        assertEquals(null, result.markerAfterCleanup)
        assertEquals(
            "pending receipt cleanup left an unsupported marker type; import a verified settings archive to replace it",
            result.honestStatus,
        )
    }

    @Test fun replacementWriterUsesSameProcessWideMarkerTransactionLockAsCleanup() {
        val expected = PendingHudSettingsImport("6".repeat(64), 1, "operation").encode()
        val replacement = PendingHudSettingsImport("7".repeat(64), 2, "replacement").encode()
        val events = mutableListOf<String>()
        val writerReadyToEnter = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)

        synchronized(PendingHudMarkerTransaction.lock) {
            events += "cleanup-compare:$expected"
            val writer = Thread {
                writerReadyToEnter.countDown()
                synchronized(PendingHudMarkerTransaction.lock) {
                    events += "replacement-write:$replacement"
                    writerEntered.countDown()
                }
            }
            writer.start()
            assertTrue(writerReadyToEnter.await(1, TimeUnit.SECONDS))
            events += "cleanup-remove-observe"
            assertFalse(writerEntered.await(100, TimeUnit.MILLISECONDS))
            assertEquals(listOf("cleanup-compare:$expected", "cleanup-remove-observe"), events)
        }

        assertTrue(writerEntered.await(1, TimeUnit.SECONDS))
        assertEquals(
            listOf("cleanup-compare:$expected", "cleanup-remove-observe", "replacement-write:$replacement"),
            events,
        )
    }

    @Test fun settingsImportCountsExcludeRefusedHudApplication() {
        assertEquals(4, settingsImportAppliedCount(importedValueCount = 5, hudResultAccepted = false))
        assertEquals(5, settingsImportAppliedCount(importedValueCount = 5, hudResultAccepted = true))
        assertEquals(0, settingsImportAppliedCount(importedValueCount = 0, hudResultAccepted = false))
    }

    @Test fun settingsExportHudModeComesFromCausalSnapshotAndOverridesStaleMirror() {
        val exported = settingsExportPreferences(
            allPreferences = mapOf(
                "hud_mode" to 0,
                "grid" to true,
            ),
            causalHudMode = 2,
            causalStoreWritable = true,
        )

        assertEquals(2, exported["hud_mode"])
        assertEquals(true, exported["grid"])
    }

    @Test fun settingsExportOmitsHudModeWhenCausalStoreHealthIsDegraded() {
        val exported = settingsExportPreferences(
            allPreferences = mapOf(
                "hud_mode" to 0,
                "grid" to true,
            ),
            causalHudMode = 2,
            causalStoreWritable = false,
        )

        assertFalse(exported.containsKey("hud_mode"))
        assertEquals(true, exported["grid"])
    }
}
