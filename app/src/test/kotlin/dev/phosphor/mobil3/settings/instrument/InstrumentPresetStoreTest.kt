package dev.phosphor.mobil3.settings.instrument

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class InstrumentPresetStoreTest {
    private val setup = CuratedInstrumentPresets.ambient.setup
    private fun seeded() = InstrumentPresetCollection.empty().create("Existing", setup)

    @Test fun failedCommitRestoresExactBytesEvenWhenAndroidMapChanged() {
        val original = " \n" + InstrumentPresetCodec.encode(seeded()) + "\n"
        var bytes: String? = original
        var writes = 0
        val store = InstrumentPresetStore({ bytes }, { next -> bytes = next; ++writes != 1 })
        store.load()
        assertFalse(store.change { it.create("New", setup) })
        assertEquals(original, bytes)
        assertEquals(2, writes)
        assertEquals(1, store.collection!!.records.size)
        assertTrue(store.error.contains("Previous bytes restored"))
    }

    @Test fun failedFirstSaveRestoresAbsenceNotAnEmptyEncodedDocument() {
        var bytes: String? = null
        var calls = 0
        val store = InstrumentPresetStore({ bytes }, { bytes = it; ++calls > 1 })
        store.load()
        assertFalse(store.change { it.create("New", setup) })
        assertNull(bytes)
        assertEquals(2, calls)
        assertTrue(store.collection!!.records.isEmpty())
    }

    @Test fun throwingWriteAlsoRestoresMutatedMap() {
        val original = InstrumentPresetCodec.encode(seeded())
        var bytes: String? = original
        var calls = 0
        val store = InstrumentPresetStore({ bytes }, {
            bytes = it
            if (++calls == 1) error("disk")
            true
        })
        store.load()
        assertFalse(store.change { it.create("Another", setup) })
        assertEquals(original, bytes)
        assertTrue(store.error.contains("restored"))
    }

    @Test fun rollbackFailureIsTruthfulAndPreventsLaterOverwriteEvenIfMapLooksRestored() {
        val original = InstrumentPresetCodec.encode(seeded())
        var bytes: String? = original
        var writes = 0
        val store = InstrumentPresetStore({ bytes }, { bytes = it; writes++; false })
        store.load()
        assertFalse(store.change { it.create("New", setup) })
        assertEquals(original, bytes)
        assertTrue(store.error.contains("rollback failed"))
        assertTrue(store.error.contains("uncertain"))
        assertNull(store.load())
        assertFalse(store.change { it.create("Retry", setup) })
        assertEquals(2, writes)
    }

    @Test fun corruptBytesArePreservedAndNeverReplacedWithEmptyCollection() {
        for (raw in listOf("not json", "", "{\"schema\":\"wrong\"}")) {
            var bytes: String? = raw
            var calls = 0
            val store = InstrumentPresetStore({ bytes }, { bytes = it; calls++; true })
            assertNull(store.load())
            assertFalse(store.change { it.create("New", setup) })
            assertEquals(raw, bytes)
            assertEquals(0, calls)
            assertNull(store.collection)
            assertTrue(store.error.contains("preserved"))
        }
    }

    @Test fun staleBytesAndStalePreviewCannotOverwriteNewCollection() {
        var bytes: String? = InstrumentPresetCodec.encode(seeded())
        var writes = 0
        val store = InstrumentPresetStore({ bytes }, { bytes = it; writes++; true })
        val base = store.load()!!
        val incoming = InstrumentPresetCollection.empty().create("Incoming", setup)
        val preview = base.previewImport(InstrumentPresetCodec.encode(incoming))
        val choice = mapOf(incoming.records.single().id to InstrumentImportChoice.Add)
        assertTrue(store.change { it.create("Intervening", setup) })
        val afterEdit = bytes
        assertFalse(store.change { it.resolveImport(preview, choice) })
        assertEquals(afterEdit, bytes)
        assertEquals(1, writes)
        bytes = InstrumentPresetCodec.encode(base)
        assertFalse(store.change { it.create("Lost update", setup) })
        assertEquals(1, writes)
    }

    @Test fun previewCancelAndConflictChoicesRemainInertUntilOneSuccessfulCommit() {
        val existing = seeded()
        var bytes: String? = InstrumentPresetCodec.encode(existing)
        var writes = 0
        val store = InstrumentPresetStore({ bytes }, { bytes = it; writes++; true })
        store.load()
        val incoming = InstrumentPresetCollection.empty().create("Existing", setup.copy(focus = 2f))
        val preview = existing.previewImport(InstrumentPresetCodec.encode(incoming))
        assertEquals(0, writes)
        assertEquals(InstrumentPresetCodec.encode(existing), bytes)
        assertFalse(store.change { it.resolveImport(preview, emptyMap()) })
        assertEquals(0, writes)
        val id = incoming.records.single().id
        assertTrue(store.change { it.resolveImport(preview, mapOf(id to InstrumentImportChoice.Replace(existing.records.single().id))) })
        assertEquals(1, writes)
        assertEquals(existing.records.single().id, store.collection!!.records.single().id)
        assertEquals(2f, store.collection!!.records.single().setup.focus)
    }

    @Test fun boundedReaderRejectsOversizeWithoutReadingTheRemainder() {
        var reads = 0
        val unlimited = object : InputStream() {
            override fun read(): Int { reads++; return 32 }
            override fun read(target: ByteArray, off: Int, len: Int): Int {
                target.fill(32, off, off + len)
                reads += len
                return len
            }
        }
        assertThrows(InstrumentPresetException::class.java) { InstrumentDocuments.read(unlimited) }
        assertEquals(InstrumentPresetCodec.MAX_BYTES + 1, reads)
    }

    @Test fun documentReaderUsesStrictUtf8AndProductionCodec() {
        val collection = seeded()
        assertEquals(collection, InstrumentDocuments.read(ByteArrayInputStream(InstrumentPresetCodec.encode(collection).toByteArray(Charsets.UTF_8))))
        assertThrows(InstrumentPresetException::class.java) {
            InstrumentDocuments.read(ByteArrayInputStream(byteArrayOf(0xc0.toByte(), 0xaf.toByte())))
        }
    }
}
