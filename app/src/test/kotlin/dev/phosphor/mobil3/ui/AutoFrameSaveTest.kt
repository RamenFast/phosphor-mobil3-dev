package dev.phosphor.mobil3.ui

import org.junit.Test
import kotlin.test.*

class AutoFrameSaveTest {
    @Test fun failedCommitMemoryPublicationDoesNotBecomeDurableOnActivityRecreation() {
        val processOwner = AutoFrameSave()
        var processMemory = 1f
        var disk = 1f
        processOwner.stage(.6f)
        assertFalse(processOwner.flush { processMemory = it; false })
        // Android publishes its in-process preference map even when disk commit fails.
        assertEquals(.6f, processMemory)
        assertEquals(1f, disk)
        val recreatedActivityOwner = processOwner
        assertEquals(.6f, recreatedActivityOwner.pending)
        assertFalse(recreatedActivityOwner.flush { processMemory = it; false })
        assertEquals(.6f, recreatedActivityOwner.pending)
        assertTrue(recreatedActivityOwner.flush { processMemory = it; disk = it; true })
        assertEquals(.6f, disk)
        assertNull(recreatedActivityOwner.pending)
    }
    @Test fun completionFlushesWithoutWaitingForDebounceAndRestartReadsCommittedValue() {
        val saved = mutableMapOf<String, Any>()
        val owner = AutoFrameSave()
        owner.stage(.7f)
        assertTrue(owner.flush { saved[AutoFramePreference.KEY] = it; true })
        assertNull(owner.pending)
        assertEquals(.7f, AutoFramePreference.read(saved))
        owner.stage(AutoFramePreference.DEFAULT)
        assertTrue(owner.flush { saved[AutoFramePreference.KEY] = it; true })
        assertEquals(1f, AutoFramePreference.read(saved))
    }
    @Test fun failedWritePreservesDurableValueAndLatestEditSurvivesRetry() {
        var durable = 1f
        val owner = AutoFrameSave()
        owner.stage(.5f)
        assertFalse(owner.flush { false })
        assertEquals(1f, durable)
        assertEquals(.5f, owner.pending)
        owner.stage(.8f)
        assertFalse(owner.flush { throw IllegalStateException("disk unavailable") })
        assertEquals(.8f, owner.pending)
        assertTrue(owner.flush { durable = it; true })
        assertEquals(.8f, durable)
        assertNull(owner.pending)
        assertTrue(owner.flush { error("no pending write") })
    }
    @Test fun importRetiresPendingOlderValue() {
        val owner = AutoFrameSave()
        owner.stage(.5f)
        owner.restored()
        assertTrue(owner.flush { error("older value must not replace imported state") })
    }
}
