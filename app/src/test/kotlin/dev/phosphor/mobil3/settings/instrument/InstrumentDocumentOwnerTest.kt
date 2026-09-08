package dev.phosphor.mobil3.settings.instrument

import org.junit.Assert.*
import org.junit.Test

class InstrumentDocumentOwnerTest {
    @Test fun cancelledPickerDoesNotStartReadAndRetainsItsSlotUntilReply() {
        val owner = InstrumentDocumentOwner()
        val ticket = owner.begin()!!
        owner.cancel()
        assertTrue(owner.busy)
        assertNull(owner.begin())
        assertFalse(owner.picked(ticket))
        assertFalse(owner.busy)
        assertNotNull(owner.begin())
    }

    @Test fun cancelledProviderReplyCannotPublishOrClearNewerWork() {
        val owner = InstrumentDocumentOwner()
        val first = owner.begin()!!
        assertTrue(owner.picked(first))
        owner.cancel()
        assertNull(owner.begin())
        assertFalse(owner.finish(first))
        val second = owner.begin()!!
        assertFalse(owner.finish(first))
        assertTrue(owner.busy)
        assertTrue(owner.picked(second))
        assertTrue(owner.finish(second))
        assertFalse(owner.busy)
    }

    @Test fun onDestroyRejectsPickerAndProviderCallbacksWithoutPublishing() {
        for (picked in listOf(false, true)) {
            val owner = InstrumentDocumentOwner()
            val ticket = owner.begin()!!
            if (picked) assertTrue(owner.picked(ticket))
            owner.close()
            if (!picked) assertFalse(owner.picked(ticket))
            assertFalse(owner.finish(ticket))
            assertNull(owner.begin())
        }
    }

    @Test fun ordinaryCancelWithNoPendingDocumentDoesNotPoisonNextExplicitImport() {
        val owner = InstrumentDocumentOwner()
        owner.cancel()
        val ticket = owner.begin()!!
        assertTrue(owner.picked(ticket))
        assertTrue(owner.finish(ticket))
        assertFalse(owner.finish(ticket))
    }
}
