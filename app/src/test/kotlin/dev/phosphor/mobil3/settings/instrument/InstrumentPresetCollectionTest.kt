package dev.phosphor.mobil3.settings.instrument

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.UUID

class InstrumentPresetCollectionTest {
    private val setup = CuratedInstrumentPresets.cleanXy.setup
    private val changed = CuratedInstrumentPresets.ambient.setup
    private fun id(n: Int): String = "00000000-0000-0000-0000-" + n.toString().padStart(12, '0')
    private fun empty() = InstrumentPresetCollection.empty()

    @Test fun explicitCrudUsesStableIdsAndNeverOverwritesPreviousValues() {
        val original = empty().create("  Bench  ", setup, id(2)).create("Other", changed, id(1))
        assertEquals("Bench", original.record(id(2)).name)
        val originalBytes = InstrumentPresetCodec.encode(original)
        val renamed = original.rename(id(2), "  Renamed ")
        assertEquals("Renamed", renamed.record(id(2)).name)
        assertEquals(setup, renamed.record(id(2)).setup)
        val updated = renamed.update(id(2), changed)
        assertEquals(changed, updated.record(id(2)).setup)
        assertEquals("Renamed", updated.record(id(2)).name)
        val proposedName = original.proposeDuplicateName("Bench")
        assertEquals("Bench copy", proposedName)
        assertEquals(2, original.records.size)
        val copied = original.duplicate(id(2), proposedName, id(3))
        assertEquals(setup, copied.record(id(3)).setup)
        assertEquals("Bench copy 2", copied.proposeDuplicateName("Bench"))
        val deleted = copied.delete(id(2))
        assertEquals(listOf(id(1), id(3)), deleted.records.map { it.id })
        assertEquals(originalBytes, InstrumentPresetCodec.encode(original))
        assertEquals(setup, original.record(id(2)).setup)
        listOf<() -> Unit>({ original.delete(id(99)) }, { original.rename(id(99), "Missing") },
            { original.update(id(99), changed) }, { original.duplicate(id(99), "Missing") }).forEach { reject(it) }
    }

    @Test fun generatedIdsAndCuratedDuplicationAreExplicit() {
        val created = empty().create("Local", setup)
        val newId = created.records.single().id
        assertEquals(newId, UUID.fromString(newId).toString())
        val copied = created.duplicate(newId, "Copy")
        assertEquals(2, copied.records.map { it.id }.distinct().size)
        val curatedCopy = empty().create("My Ambient", CuratedInstrumentPresets.ambient.setup)
        assertEquals(changed, curatedCopy.records.single().setup)
        assertEquals(3, CuratedInstrumentPresets.all.size)
        assertEquals(0, empty().records.size)
    }

    @Test fun namesCountUnicodeCodePointsAndRejectControlsAndMalformedSurrogates() {
        val name = "🎛".repeat(64)
        val records = empty().create("\u2003$name\u00a0", setup, id(1))
        assertEquals(name, records.records.single().name)
        assertEquals(128, name.length)
        val proposed = records.proposeDuplicateName(name)
        assertEquals(64, proposed.codePointCount(0, proposed.length))
        assertTrue(validUnicode(proposed))
        records.duplicate(id(1), proposed, id(2))
        listOf("", "  ", "🎛".repeat(65), "x\ny", "x\u0000y", "x\u007fy", "x\u0085y", "\ud800", "\udc00")
            .forEach { reject { empty().create(it, setup) } }
        reject { InstrumentPresetRecord(id(1), " padded ", setup) }
        val escaped = empty().create("quote\" and \\ slash /", setup, id(1))
        assertEquals(escaped, InstrumentPresetCodec.decode(InstrumentPresetCodec.encode(escaped)))
    }

    @Test fun nameConflictIsLocaleIndependentIncludingSupplementaryCase() {
        val previous = Locale.getDefault()
        try {
            listOf(Locale.US, Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("lt-LT")).forEach { locale ->
                Locale.setDefault(locale)
                val first = empty().create("INDIGO", setup, id(1))
                reject { first.create("indigo", setup, id(2)) }
                reject { first.create("ındıgo", setup, id(2)) }
                val sigma = empty().create("Σ", setup, id(1))
                reject { sigma.create("ς", setup, id(2)) }
                val deseret = empty().create("\ud801\udc00", setup, id(1))
                reject { deseret.create("\ud801\udc28", setup, id(2)) }
                val distinct = empty().create("é", setup, id(1)).create("e\u0301", setup, id(2))
                assertEquals(2, distinct.records.size)
            }
        } finally { Locale.setDefault(previous) }
    }

    @Test fun sixtyFourRecordBoundAndCollectionImmutability() {
        val source = (1..64).map { InstrumentPresetRecord(id(it), "Preset $it", setup) }.toMutableList()
        val full = InstrumentPresetCollection.of(source)
        source.clear()
        assertEquals(64, full.records.size)
        assertEquals(full, InstrumentPresetCodec.decode(InstrumentPresetCodec.encode(full)))
        assertEquals("too_many_records", reject { full.create("Overflow", setup, id(65)) }.error)
        reject { full.duplicate(id(1), "Copy", id(65)) }
        assertEquals(64, full.rename(id(1), "Renamed").records.size)
        assertThrows(UnsupportedOperationException::class.java) { (full.records as MutableList<*>).clear() }
        val json = JSONObject(InstrumentPresetCodec.encode(full))
        json.getJSONArray("records").put(JSONObject(json.getJSONArray("records").getJSONObject(0).toString()).put("id", id(65)).put("name", "Overflow"))
        assertEquals("too_many_records", reject { InstrumentPresetCodec.decode(json.toString()) }.error)
        assertEquals(full.hashCode(), InstrumentPresetCollection.of(full.records.reversed()).hashCode())
    }

    @Test fun identityAndNameCollisionsDoNotSilentlyRepair() {
        val first = empty().create("Bench", setup, id(1))
        assertEquals("duplicate_id", reject { first.create("Different", setup, id(1)) }.error)
        assertEquals("duplicate_name", reject { first.create("BENCH", setup, id(2)) }.error)
        reject { first.create("Different", setup, "1-1-1-1-1") }
        val both = first.create("Other", changed, id(2))
        reject { both.rename(id(2), "bench") }
        reject { both.duplicate(id(1), "BENCH", id(3)) }
        assertEquals("Bench", first.record(id(1)).name)
    }

    @Test fun importIsInertAndEveryConflictNeedsAnExplicitChoice() {
        val base = empty().create("Bench", setup, id(1))
        val incoming = empty().create("Bench", changed, id(1)).create("New", setup, id(2))
        val before = InstrumentPresetCodec.encode(base)
        val preview = base.previewImport(InstrumentPresetCodec.encode(incoming))
        assertEquals(listOf(id(1)), preview.conflictsFor(id(1)))
        assertTrue(preview.conflictsFor(id(2)).isEmpty())
        assertEquals(before, InstrumentPresetCodec.encode(base))
        reject { base.resolveImport(preview, emptyMap()) }
        reject { base.resolveImport(preview, mapOf(id(1) to InstrumentImportChoice.Add, id(2) to InstrumentImportChoice.Add)) }
        val kept = base.resolveImport(preview, mapOf(id(1) to InstrumentImportChoice.KeepExisting, id(2) to InstrumentImportChoice.Add))
        assertEquals(setup, kept.record(id(1)).setup)
        assertEquals(2, kept.records.size)
        val replaced = base.resolveImport(preview, mapOf(id(1) to InstrumentImportChoice.Replace(id(1)), id(2) to InstrumentImportChoice.KeepExisting))
        assertEquals(changed, replaced.record(id(1)).setup)
        val copied = base.resolveImport(preview, mapOf(id(1) to InstrumentImportChoice.SaveCopy("Fresh", id(3)), id(2) to InstrumentImportChoice.Add))
        assertEquals(setup, copied.record(id(1)).setup)
        assertEquals(changed, copied.record(id(3)).setup)
        assertEquals(before, InstrumentPresetCodec.encode(base))
        assertEquals(base, base.resolveImport(preview, incoming.records.associate { it.id to InstrumentImportChoice.KeepExisting }))
    }

    @Test fun replacementPreservesIdentifiedIdAndCrossedConflictsCannotDropOtherRecords() {
        val base = empty().create("A", setup, id(1)).create("B", setup, id(2))
        val crossed = empty().create("B", changed, id(1))
        val preview = base.previewImport(InstrumentPresetCodec.encode(crossed))
        assertEquals(listOf(id(1), id(2)), preview.conflictsFor(id(1)))
        reject { base.resolveImport(preview, mapOf(id(1) to InstrumentImportChoice.Replace(id(1)))) }
        val replacement = base.resolveImport(preview, mapOf(id(1) to InstrumentImportChoice.Replace(id(2))))
        assertEquals(setup, replacement.record(id(1)).setup)
        assertEquals("A", replacement.record(id(1)).name)
        assertEquals(changed, replacement.record(id(2)).setup)
        val nameOnly = empty().create("B", changed, id(3))
        val namePreview = base.previewImport(InstrumentPresetCodec.encode(nameOnly))
        assertEquals(id(2), base.resolveImport(namePreview, mapOf(id(3) to InstrumentImportChoice.Replace(id(2)))).records.last().id)
        reject { base.resolveImport(namePreview, mapOf(id(3) to InstrumentImportChoice.Replace(id(1)))) }
        reject { base.resolveImport(namePreview, mapOf(id(3) to InstrumentImportChoice.SaveCopy("Fresh", id(3)))) }
    }

    @Test fun finalImportBatchIsAllOrNothingAndStalePreviewCannotOverwriteEdits() {
        val base = empty().create("A", setup, id(1))
        val incoming = empty().create("A", changed, id(2)).create("Other", changed, id(1))
        val preview = base.previewImport(InstrumentPresetCodec.encode(incoming))
        val before = InstrumentPresetCodec.encode(base)
        reject { base.resolveImport(preview, incoming.records.associate { it.id to InstrumentImportChoice.Replace(id(1)) }) }
        reject { base.resolveImport(preview, incoming.records.associate { it.id to InstrumentImportChoice.SaveCopy("Same", id(3)) }) }
        reject { base.rename(id(1), "Edited").resolveImport(preview, incoming.records.associate { it.id to InstrumentImportChoice.KeepExisting }) }
        val malformed = JSONObject(InstrumentPresetCodec.encode(incoming))
        malformed.getJSONArray("records").getJSONObject(1).getJSONObject("setup").remove("gain")
        reject { base.previewImport(malformed.toString()) }
        assertEquals(before, InstrumentPresetCodec.encode(base))
        val full = InstrumentPresetCollection.of((1..64).map { InstrumentPresetRecord(id(it), "P$it", setup) })
        val addition = empty().create("New", changed, id(65))
        reject { full.resolveImport(full.previewImport(InstrumentPresetCodec.encode(addition)), mapOf(id(65) to InstrumentImportChoice.Add)) }
        assertEquals(64, full.records.size)
    }

    @Test fun failedPersistencePreservesBytesAndNeverPublishesSuccess() {
        val base = empty().create("Bench", setup, id(1))
        val proposed = base.update(id(1), changed)
        val before = "\n" + InstrumentPresetCodec.encode(base) + "\n"
        var stored = before
        var calls = 0
        val rejected = InstrumentPresetCommit.commit(stored, base, proposed) { calls++; false }
        assertTrue(rejected.isFailure)
        assertEquals(1, calls)
        assertEquals(before, stored)
        assertEquals(setup, base.record(id(1)).setup)
        assertTrue(InstrumentPresetCommit.commit(stored, base, proposed) { throw IllegalStateException("disk failure") }.isFailure)
        assertEquals(before, stored)
        val accepted = InstrumentPresetCommit.commit(stored, base, proposed) { stored = it; calls++; true }
        assertEquals(proposed, accepted.getOrThrow())
        assertEquals(2, calls)
        assertEquals(proposed, InstrumentPresetCodec.decode(stored))
    }

    @Test fun invalidStoredBytesAndStaleCollectionsNeverReachPersistenceCallback() {
        val base = empty().create("Bench", setup, id(1))
        val proposed = base.update(id(1), changed)
        var calls = 0
        listOf("", "broken", "{}", InstrumentPresetCodec.encode(base).replace("Bench", "tampered")).forEach { stored ->
            val result = InstrumentPresetCommit.commit(stored, empty(), proposed) { calls++; true }
            assertTrue(result.isFailure)
            assertTrue((result.exceptionOrNull() as InstrumentPresetException).fix.isNotBlank())
        }
        assertTrue(InstrumentPresetCommit.commit(InstrumentPresetCodec.encode(base), empty(), proposed) { calls++; true }.isFailure)
        assertEquals(0, calls)
        assertEquals(base, InstrumentPresetCommit.commit(null, empty(), base) { calls++; true }.getOrThrow())
        assertEquals(1, calls)
        assertEquals(base, InstrumentPresetCodec.decode(InstrumentPresetCodec.encode(base)))
    }
}
