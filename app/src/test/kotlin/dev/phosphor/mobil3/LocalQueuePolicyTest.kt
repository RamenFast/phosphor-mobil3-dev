package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalQueuePolicyTest {
    @Test
    fun explicitTreeOrSourceSelectionFencesOldEofUntilPublication() {
        val policy = LocalQueuePolicy()
        policy.published()
        assertTrue(policy.mayAdvance())
        policy.beginSelection()
        assertFalse(policy.mayAdvance())
        policy.published()
        assertTrue(policy.mayAdvance())
    }

    @Test
    fun failedPreflightCanRetainThePreviousSource() {
        val policy = LocalQueuePolicy()
        policy.published()
        policy.beginSelection()
        policy.failed(preservesNative = true)
        assertTrue(policy.mayAdvance())
    }

    @Test
    fun destructiveNativeFailureCannotAdvanceAnAbsentDeck() {
        val policy = LocalQueuePolicy()
        policy.published()
        policy.beginSelection()
        policy.failed(preservesNative = false)
        assertFalse(policy.mayAdvance())
        policy.beginSelection()
        assertFalse(policy.mayAdvance())
        policy.published()
        assertTrue(policy.mayAdvance())
    }

    @Test
    fun validTrackThenInvalidTailStopsInsteadOfRepeatingAtEof() {
        val policy = LocalQueuePolicy()
        policy.published()
        policy.beginSelection()
        val tail = localTrackCandidates(3, requested = 1, opened = 0, fromEof = true)
        assertEquals(listOf(1, 2), tail.toList())
        policy.exhausted(fromEof = true)
        repeat(10) { assertFalse(policy.mayAdvance()) }
    }

    @Test
    fun manualFailedNavigationDoesNotEndThePreviousTrack() {
        val policy = LocalQueuePolicy()
        policy.published()
        policy.beginSelection()
        policy.exhausted(fromEof = false)
        assertTrue(policy.mayAdvance())
    }

    @Test
    fun previousAndNextSkipBadEntriesInTheRequestedDirection() {
        val valid = setOf(0, 5)
        assertEquals(0, localTrackCandidates(7, 4, opened = 5, fromEof = false).first { it in valid })
        assertEquals(5, localTrackCandidates(7, 1, opened = 0, fromEof = false).first { it in valid })
    }

    @Test
    fun aFreshTreeAndEofAlwaysScanForward() {
        assertEquals(listOf(2, 3, 4), localTrackCandidates(5, 2, opened = null, fromEof = false).toList())
        assertEquals(listOf(2, 3, 4), localTrackCandidates(5, 2, opened = 4, fromEof = true).toList())
        assertTrue(localTrackCandidates(0, 0, opened = null, fromEof = false).none())
    }
}
