package dev.phosphor.mobil3

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class CaptureReadFenceTest {
    @Test fun heldReadKeepsOldEpochAndNextReadGetsNewEpoch() {
        val running = AtomicBoolean(true)
        val epoch = AtomicLong(4)
        val began = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val batches = mutableListOf<Pair<Int, Long>>()
        val reader = Thread {
            try {
                var reads = 0
                readSourceSamples(
                    running = { running.get() }, readEpoch = { epoch.get() },
                    read = {
                        if (reads++ == 0) {
                            began.countDown()
                            check(finish.await(2, TimeUnit.SECONDS))
                        }
                        960
                    },
                    push = { count, readEpoch ->
                        batches.add(count to readEpoch)
                        if (batches.size == 2) running.set(false)
                    },
                    failed = { throw it },
                )
            } catch (failure: Throwable) { error.set(failure) }
            finally { done.countDown() }
        }
        reader.start()
        try {
            assertTrue(began.await(2, TimeUnit.SECONDS))
            epoch.set(5)
        } finally { finish.countDown() }
        assertTrue(done.await(2, TimeUnit.SECONDS))
        reader.join(2000)
        assertFalse(reader.isAlive)
        error.get()?.let { throw AssertionError("reader failed", it) }
        assertEquals(listOf(960 to 4L, 960 to 5L), batches)
    }

    @Test fun zeroReadsDoNotPublishAndEveryReadSamplesItsOwnEpoch() {
        var live = true
        var epoch = 4L
        var reads = 0
        val batches = mutableListOf<Pair<Int, Long>>()
        readSourceSamples(
            running = { live }, readEpoch = { epoch++ },
            read = { if (reads++ == 0) 0 else 8 },
            push = { count, readEpoch -> batches.add(count to readEpoch); live = false },
            failed = { throw it },
        )
        assertEquals(listOf(8 to 5L), batches)
        assertEquals(2, reads)
    }

    @Test fun stopDuringReadStillDiscardsTheWholeBatch() {
        var live = true
        var pushed = false
        readSourceSamples(
            running = { live }, readEpoch = { 4L },
            read = { live = false; 960 },
            push = { _, _ -> pushed = true }, failed = { throw it },
        )
        assertFalse(pushed)
    }

    @Test fun terminalReadFailureDispatchesOnceWithoutPublishing() {
        for (throws in listOf(false, true)) {
            var failures = 0
            var reads = 0
            readSourceSamples(
                running = { true }, readEpoch = { 4L },
                read = { reads++; if (throws) throw SecurityException("revoked") else -6 },
                push = { _, _ -> fail("terminal read published") },
                failed = { failures++ },
            )
            assertEquals(1, failures)
            assertEquals(1, reads)
        }
    }
}
