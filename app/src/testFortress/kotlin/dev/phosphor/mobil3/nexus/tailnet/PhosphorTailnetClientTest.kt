package dev.phosphor.mobil3.nexus.tailnet

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PhosphorTailnetClientTest {
    @Test fun heartbeat_policy_three_miss_closing_four_miss_absent() {
        val heartbeat = TailnetHeartbeatPolicy(1_000)
        assertEquals(TailnetPresence.PRESENT, heartbeat.evaluate(2, 2_000, activeLifecycle = true))
        assertEquals(TailnetPresence.CLOSING, heartbeat.evaluate(3, 3_000, activeLifecycle = true))
        assertEquals(TailnetPresence.ABSENT, heartbeat.evaluate(4, 4_000, activeLifecycle = true))
        assertFailsWith<TailnetProtocolException> { heartbeat.evaluate(1, 5_000, activeLifecycle = false) }
    }

    @Test fun stale_generation_is_refused_by_writer_fence() {
        val fence = PhosphorTailnetGenerationFence()
        assertTrue(fence.advanceIfFresh(3))
        assertFalse(fence.advanceIfFresh(3))
        assertFalse(fence.advanceIfFresh(2))
        assertTrue(fence.advanceIfFresh(4))
    }

    @Test fun backoff_is_bounded_and_requires_bounded_jitter() {
        val backoff = PhosphorTailnetBackoff(baseMillis = 1_000, maxMillis = 10_000, jitterMillis = 100)
        assertEquals(1_050, backoff.delayMillis(0, 50))
        assertEquals(10_100, backoff.delayMillis(20, 100))
        assertFailsWith<IllegalArgumentException> { backoff.delayMillis(1, 101) }
    }
}
