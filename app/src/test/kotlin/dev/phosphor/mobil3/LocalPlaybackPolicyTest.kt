package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalPlaybackPolicyTest {
    @Test
    fun transientFocusLossWithoutANewerCommandMayResumeOnce() {
        val intent = LocalTransportIntent()
        val resume = LocalFocusResume()
        intent.record(false)
        resume.arm(true, intent.revision)
        assertTrue(resume.take(intent.revision))
        assertFalse(resume.take(intent.revision))
    }

    @Test
    fun newerPauseOrNoisyIntentRevokesTransientFocusResume() {
        repeat(2) { // Explicit transport and noisy both use the production record seam.
            val intent = LocalTransportIntent()
            val resume = LocalFocusResume()
            val selectedAt = intent.revision
            intent.record(false)
            resume.arm(true, intent.revision)
            intent.record(false)
            assertFalse(resume.take(intent.revision))
            assertFalse(intent.atPublication(selectedAt, autoplay = true))
        }
    }

    @Test
    fun supersededPreparedReplacementCannotPreserveAnAbsentPublishedDeck() {
        val survival = LocalSourceSurvival()
        survival.published() // A published.
        survival.nativeReplacing() // B prepare or prepared seek destroyed A.
        // B publication is rejected. Selecting/rejecting invalid C does not publish a source.
        assertTrue(survival.loss().native)
        assertTrue(survival.loss().native) // The loss survives repeated latest rejection checks.
        survival.published()
        assertFalse(survival.loss().native)
    }

    @Test
    fun validationOnlyRejectionLeavesPublishedOwnershipIntact() {
        val survival = LocalSourceSurvival()
        survival.published()
        assertFalse(survival.loss().native)
        assertTrue(survival.loss().readers.isEmpty())
    }

    @Test
    fun micSuccessCaptureErrorClearsOnlyTheReleasedMicFace() {
        val survival = LocalSourceSurvival()
        val mic = SourceStopRequest(1)
        val capture = SourceStopRequest(1)
        mic.complete(1, null, owned = true)
        capture.complete(1, "inactive capture cleanup failed", owned = false)
        assertTrue(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.MIC, mic))
        assertFalse(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.CAPTURE, capture))
        val snapshot = LocalSourcePublication().readersReleased(survival.loss().readers)
        assertTrue(snapshot.clearsReaderFace("mic"))
        assertFalse(snapshot.clearsReaderFace("capture"))
        assertFalse(snapshot.clearsReaderFace("deck"))
        assertFalse(snapshot.clearsReaderFace("remote"))
    }

    @Test
    fun captureSuccessMicErrorClearsOnlyTheReleasedCaptureFace() {
        val survival = LocalSourceSurvival()
        val mic = SourceStopRequest(2)
        val capture = SourceStopRequest(2)
        mic.complete(2, "inactive mic cleanup failed", owned = false)
        capture.complete(2, null, owned = true)
        assertFalse(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.MIC, mic))
        assertTrue(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.CAPTURE, capture))
        val snapshot = LocalSourcePublication().readersReleased(survival.loss().readers)
        assertFalse(snapshot.clearsReaderFace("mic"))
        assertTrue(snapshot.clearsReaderFace("capture"))
        assertTrue(snapshot.clearsReaderFace("capture · starting…"))
    }

    @Test
    fun lateOwnedStopAfterNewInvalidRequestStillReconcilesTheReleasedFace() {
        val survival = LocalSourceSurvival()
        val publication = LocalSourcePublication()
        val epoch = survival.epoch()
        val stop = SourceStopRequest(3) // B starts stopping A.
        publication.selected() // Invalid C supersedes B and rejects before the stop callback.
        assertTrue(survival.loss().readers.isEmpty())
        stop.complete(3, null, owned = true)
        assertTrue(survival.readerStopped(epoch, LocalSourcePublication.Reader.MIC, stop))
        val late = publication.readersReleased(survival.loss().readers)
        assertTrue(publication.accepts(late.revision))
        assertTrue(late.clearsReaderFace("mic"))
    }

    @Test
    fun realNewPublicationRetiresLateOldOwnerAcknowledgements() {
        val survival = LocalSourceSurvival()
        val oldEpoch = survival.epoch()
        val stop = SourceStopRequest(4)
        survival.published()
        stop.complete(4, null, owned = true)
        assertFalse(survival.readerStopped(oldEpoch, LocalSourcePublication.Reader.MIC, stop))
        assertTrue(survival.loss().readers.isEmpty())
    }

    @Test
    fun inactiveOrMismatchedStopCannotClaimReleasedOwnership() {
        val survival = LocalSourceSurvival()
        val stop = SourceStopRequest(5)
        assertFalse(stop.complete(4, null, owned = true))
        assertFalse(stop.releasedOwner)
        assertTrue(stop.complete(5, null, owned = false))
        assertFalse(stop.complete(5, null, owned = true))
        assertFalse(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.MIC, stop))
    }

    @Test
    fun ownedMicCleanupErrorClearsDeadFlowButNeverClaimsJoinedRelease() {
        val survival = LocalSourceSurvival()
        val mic = SourceStopRequest(6)
        mic.complete(6, ReaderStop.TIMEOUT, owned = true)
        assertFalse(mic.releasedOwner)
        assertTrue(mic.stoppedOwner)
        assertEquals(ReaderStop.TIMEOUT, mic.await(1) { true })
        assertTrue(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.MIC, mic))
        assertTrue(LocalSourcePublication().readersReleased(survival.loss().readers).clearsReaderFace("mic"))
    }

    @Test
    fun ownedCaptureCleanupErrorClearsDeadFlowButNeverClaimsJoinedRelease() {
        val survival = LocalSourceSurvival()
        val capture = SourceStopRequest(7)
        capture.complete(7, "cleanup failed", owned = true)
        assertFalse(capture.releasedOwner)
        assertTrue(capture.stoppedOwner)
        assertTrue(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.CAPTURE, capture))
        val face = LocalSourcePublication().readersReleased(survival.loss().readers)
        assertTrue(face.clearsReaderFace("capture"))
        assertFalse(face.clearsReaderFace("mic"))
    }

    @Test
    fun initialFolderSelectionRetainsAutoplay() {
        val intent = LocalTransportIntent()
        val selectedAt = intent.revision
        intent.publish(intent.atPublication(selectedAt, autoplay = true))
        assertTrue(intent.playing)
    }

    @Test
    fun pauseBeforeNextKeepsExistingAutoplayPolicyUnlessANewerCommandArrives() {
        val intent = LocalTransportIntent()
        intent.record(false)
        intent.publish(false)
        val selectedAt = intent.revision
        assertTrue(intent.atPublication(selectedAt, autoplay = true))
        intent.record(false)
        assertFalse(intent.atPublication(selectedAt, autoplay = true))
    }

    @Test
    fun pauseWhileTreeIsPendingSurvivesLaterPublication() {
        val intent = LocalTransportIntent()
        intent.record(true)
        intent.publish(true)
        val selectedAt = intent.revision
        intent.record(false)
        intent.publish(false)
        intent.publish(intent.atPublication(selectedAt, autoplay = true))
        assertFalse(intent.playing)
    }

    @Test
    fun serviceRouteLossIsRetainedEvenIfLocalWasNotTheActivePlayer() {
        val local = LocalTransportIntent()
        val selectedAt = local.revision
        // Both production noisy/focus callbacks record here before pausing activePlayer.
        local.record(false)
        assertFalse(local.atPublication(selectedAt, autoplay = true))
    }

    @Test
    fun crossSourceHumanPauseUsesTheSameLocalPublicationIntent() {
        val local = LocalTransportIntent()
        val selectedAt = local.revision
        // Remote command hook and capture router both forward the explicit command to record.
        val sourceCommand = local::record
        sourceCommand(false)
        local.publish(false)
        assertFalse(local.atPublication(selectedAt, autoplay = true))
        sourceCommand(true)
        assertTrue(local.atPublication(selectedAt, autoplay = true))
    }

    @Test
    fun internalNativeFailureThenValidCandidateDoesNotCancelAutoplay() {
        val intent = LocalTransportIntent()
        val selectedAt = intent.revision
        intent.publish(false)
        assertEquals(selectedAt, intent.revision)
        intent.publish(intent.atPublication(selectedAt, autoplay = true))
        assertTrue(intent.playing)
    }

    @Test
    fun internalStateCannotEraseANewerPause() {
        val intent = LocalTransportIntent()
        val selectedAt = intent.revision
        intent.record(false)
        intent.publish(true)
        intent.publish(intent.atPublication(selectedAt, autoplay = true))
        assertFalse(intent.playing)
    }

    @Test
    fun preflightRejectionPreservesThePreviousSourceFace() {
        val source = LocalSourcePublication()
        source.published(LocalSourcePublication.Source.OTHER)
        source.selected()
        val before = source.current
        assertNull(source.failed(released = false))
        assertEquals(before, source.current)
    }

    @Test
    fun failedReplacementAfterMicReleasePublishesNoSource() {
        val source = LocalSourcePublication()
        source.published(LocalSourcePublication.Source.OTHER)
        source.selected()
        val failure = source.failed(released = true)!!
        assertEquals(LocalSourcePublication.Source.NONE, failure.source)
        assertTrue(source.accepts(failure.revision))
    }

    @Test
    fun lateFailureBroadcastCannotClearANewerValidTrack() {
        val source = LocalSourcePublication()
        val failure = source.failed(released = true)!!
        val success = source.published(LocalSourcePublication.Source.LOCAL)
        assertFalse(source.accepts(failure.revision))
        assertTrue(source.accepts(success.revision))
    }

    @Test
    fun anotherSourceSelectionInvalidatesAnOldFailureBroadcast() {
        val source = LocalSourcePublication()
        val failure = source.failed(released = true)!!
        source.selected()
        assertFalse(source.accepts(failure.revision))
        source.published(LocalSourcePublication.Source.OTHER)
        assertEquals(LocalSourcePublication.Source.OTHER, source.current.source)
    }
}
