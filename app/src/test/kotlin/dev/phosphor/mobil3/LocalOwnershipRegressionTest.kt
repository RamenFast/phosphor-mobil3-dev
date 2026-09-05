package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalOwnershipRegressionTest {
    @Test
    fun invalidNewestRequestSeesDestructionEvenAfterPreparedPublicationIsRejected() {
        val survival = LocalSourceSurvival()
        val publication = LocalSourcePublication()
        survival.published()
        publication.published(LocalSourcePublication.Source.LOCAL)
        val drains = ArrayDeque<() -> Unit>()
        val main = ArrayDeque<() -> Unit>()
        var preparedPublished = false
        lateinit var slot: LatestRequestSlot<String>
        slot = LatestRequestSlot(schedule = { drains.addLast(it) }) { request, current ->
            when (request) {
                "prepare-B" -> {
                    survival.nativeReplacing()
                    main.addLast {
                        if (current()) {
                            preparedPublished = true
                            survival.published()
                            publication.published(LocalSourcePublication.Source.LOCAL)
                        }
                    }
                    publication.selected()
                    slot.enqueue("invalid-C")
                }
                "invalid-C" -> main.addLast {
                    if (current()) publication.failed(released = survival.loss().native)
                }
            }
        }
        slot.enqueue("prepare-B")
        assertEquals(1, drains.size)
        drains.removeFirst().invoke()
        assertEquals(2, main.size)
        main.removeFirst().invoke()
        main.removeFirst().invoke()
        assertFalse(preparedPublished)
        assertTrue(survival.loss().native)
        assertEquals(LocalSourcePublication.Source.NONE, publication.current.source)
    }

    @Test
    fun preflightOnlyRejectionKeepsTheGenuinelySurvivingPublication() {
        val survival = LocalSourceSurvival()
        val publication = LocalSourcePublication()
        survival.published()
        publication.published(LocalSourcePublication.Source.LOCAL)
        publication.selected()
        val before = publication.current
        assertNull(publication.failed(released = survival.loss().native))
        assertEquals(before, publication.current)
        assertTrue(survival.loss().readers.isEmpty())
    }

    @Test
    fun lateOwnedStopAfterNewestRejectionStillIdentifiesTheDeadReaderFace() {
        val survival = LocalSourceSurvival()
        val publication = LocalSourcePublication()
        val stop = SourceStopRequest(12)
        val epoch = survival.epoch()
        val drains = ArrayDeque<() -> Unit>()
        var newestRejected = false
        lateinit var slot: LatestRequestSlot<String>
        slot = LatestRequestSlot(schedule = { drains.addLast(it) }) { request, current ->
            when (request) {
                "stop-B" -> {
                    slot.enqueue("invalid-C")
                    assertEquals("Source request was superseded", stop.await(50, current))
                }
                "invalid-C" -> {
                    assertTrue(current())
                    newestRejected = true
                    assertTrue(survival.loss().readers.isEmpty())
                }
            }
        }
        slot.enqueue("stop-B")
        drains.removeFirst().invoke()
        assertTrue(newestRejected)
        assertTrue(stop.complete(12, null, owned = true))
        assertTrue(survival.readerStopped(epoch, LocalSourcePublication.Reader.MIC, stop))
        val result = publication.readersReleased(survival.loss().readers)
        assertTrue(result.clearsReaderFace("mic"))
        assertFalse(result.clearsReaderFace("capture"))
        assertFalse(result.clearsReaderFace("deck"))
        assertFalse(result.clearsReaderFace("remote"))
    }

    @Test
    fun newerRealPublicationRejectsAnOldOwnedStopAcknowledgement() {
        val survival = LocalSourceSurvival()
        val publication = LocalSourcePublication()
        val stop = SourceStopRequest(23)
        val oldEpoch = survival.epoch()
        val drains = ArrayDeque<() -> Unit>()
        val slot = LatestRequestSlot<String>(schedule = { drains.addLast(it) }) { _, current ->
            assertTrue(current())
            survival.published()
            publication.published(LocalSourcePublication.Source.LOCAL)
        }
        slot.enqueue("publish-D")
        drains.removeFirst().invoke()
        val newest = publication.current
        assertTrue(stop.complete(23, null, owned = true))
        assertFalse(survival.readerStopped(oldEpoch, LocalSourcePublication.Reader.MIC, stop))
        assertTrue(survival.loss().readers.isEmpty())
        assertEquals(newest, publication.current)
    }

    @Test
    fun ownedStopErrorInvalidatesItsFaceWithoutAuthorizingReplacement() {
        for (reader in LocalSourcePublication.Reader.entries) {
            val survival = LocalSourceSurvival()
            val stop = SourceStopRequest(34)
            assertTrue(stop.complete(34, ReaderStop.TIMEOUT, owned = true))
            assertEquals(ReaderStop.TIMEOUT, stop.await(50) { true })
            assertTrue(survival.readerStopped(survival.epoch(), reader, stop))
            assertEquals(setOf(reader), survival.loss().readers)
            assertFalse(survival.loss().native)
        }
    }

    @Test
    fun unownedStopCannotInvalidateAnUnrelatedWorkingSource() {
        for (error in listOf(null, ReaderStop.TIMEOUT)) {
            val survival = LocalSourceSurvival()
            val stop = SourceStopRequest(45)
            assertTrue(stop.complete(45, error, owned = false))
            assertFalse(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.CAPTURE, stop))
            assertTrue(survival.loss().readers.isEmpty())
            assertFalse(survival.loss().native)
        }
    }

    @Test
    fun freshPublicationClearsNativeAndReaderLossTogether() {
        val survival = LocalSourceSurvival()
        val oldEpoch = survival.epoch()
        survival.nativeReplacing()
        assertTrue(survival.readerReleased(oldEpoch, LocalSourcePublication.Reader.MIC))
        assertTrue(survival.loss().native)
        survival.published()
        assertFalse(survival.loss().native)
        assertTrue(survival.loss().readers.isEmpty())
        assertFalse(survival.readerReleased(oldEpoch, LocalSourcePublication.Reader.CAPTURE))
    }

    @Test
    fun wrongAndDuplicateAcknowledgementsCannotInventOwnedLoss() {
        val survival = LocalSourceSurvival()
        val stop = SourceStopRequest(56)
        assertFalse(stop.complete(55, null, owned = true))
        assertTrue(stop.complete(56, null, owned = false))
        assertFalse(stop.complete(56, null, owned = true))
        assertFalse(survival.readerStopped(survival.epoch(), LocalSourcePublication.Reader.MIC, stop))
        assertTrue(survival.loss().readers.isEmpty())
    }
}
