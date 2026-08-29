package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LatestRequestSlotTest {
    private class ManualWorker {
        val tasks = ArrayDeque<() -> Unit>()

        fun schedule(task: () -> Unit) {
            tasks.addLast(task)
        }

        fun runNext() {
            tasks.removeFirst().invoke()
        }
    }

    @Test
    fun latestRequestWinsBeforeDrainStarts() {
        val worker = ManualWorker()
        val consumed = mutableListOf<Int>()
        val slot = LatestRequestSlot<Int>(worker::schedule) { value, _ -> consumed += value }

        slot.enqueue(1)
        slot.enqueue(2)
        slot.enqueue(3)
        worker.runNext()

        assertEquals(listOf(3), consumed)
        assertTrue(worker.tasks.isEmpty())
    }

    @Test
    fun queueSwitchKeepsOnlyLatestPendingRequest() {
        val worker = ManualWorker()
        val consumed = mutableListOf<String>()
        lateinit var slot: LatestRequestSlot<String>
        slot = LatestRequestSlot(worker::schedule) { value, isLatest ->
            consumed += value
            if (value == "current") {
                assertTrue(isLatest())
                slot.enqueue("next")
                assertFalse(isLatest())
                slot.enqueue("previous")
            }
        }

        slot.enqueue("current")
        worker.runNext()

        assertEquals(listOf("current", "previous"), consumed)
        assertTrue(worker.tasks.isEmpty())
    }

    @Test
    fun enqueueReturnsWithoutRunningWorkOnCaller() {
        val worker = ManualWorker()
        var consumed = false
        val slot = LatestRequestSlot<String>(worker::schedule) { _, _ -> consumed = true }

        slot.enqueue("seek")

        assertFalse(consumed)
        assertEquals(1, worker.tasks.size)
        worker.runNext()
        assertTrue(consumed)
    }
}
