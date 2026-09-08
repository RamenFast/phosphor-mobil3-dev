package dev.phosphor.mobil3.settings.appearance

import org.junit.Assert.*
import org.junit.Test

class AppearanceCollectionTest {
    private val f = AppearanceFixtures
    private fun collection() = AppearanceCollection.of(AppearanceDocument.of(f.high, "", listOf(f.user(1), f.user(2)),
        f.legacy(true), f.provenance))

    @Test fun createAddsNamedAuthoredValueWithoutActivatingIt() {
        val before = collection()
        val next = before.create("New", f.low, f.id(3))
        assertEquals(2, before.document.users.size)
        assertEquals(3, next.document.users.size)
        assertEquals(AppearanceRecord(f.id(3), "New", f.low), next.record(f.id(3)))
        assertSame(before.document.active, next.document.active)
        assertEquals(before.document.activeId, next.document.activeId)
        assertEquals(before.document.provenance, next.document.provenance)
        assertEquals(before.document.legacy, next.document.legacy)
    }

    @Test fun explicitSaveSavesActiveAndAssociatesItWithoutTouchingOtherRecords() {
        val before = collection()
        val saved = before.save("Saved", f.id(3))
        assertSame(before.document.active, saved.document.active)
        assertSame(before.document.active, saved.record(f.id(3)).value)
        assertEquals(f.id(3), saved.document.activeId)
        assertFalse(saved.modified)
        assertEquals(before.document.users, saved.document.users.take(2))
        assertEquals("", before.document.activeId)
    }

    @Test fun defaultIdsAreValidUniqueAndRemainStableThroughRenameUpdate() {
        val first = AppearanceCollection.of().save("One")
        val id = first.document.activeId
        val second = first.create("Two", f.low)
        assertNotEquals(id, second.document.users.last().id)
        val updated = second.rename(id, "Renamed").update(id, f.high)
        assertEquals(id, updated.document.users.first().id)
        assertEquals(id, updated.document.activeId)
        assertEquals("Renamed", updated.record(id).name)
        assertEquals(f.high, updated.record(id).value)
    }

    @Test fun updateChangesOnlyRecordNotActiveOrAssociation() {
        val before = collection().apply(f.id(1))
        val after = before.update(f.id(1), f.high)
        assertSame(before.document.active, after.document.active)
        assertEquals(before.document.activeId, after.document.activeId)
        assertEquals(f.high, after.record(f.id(1)).value)
        assertTrue(after.modified)
        assertEquals(before.record(f.id(1)), collection().record(f.id(1)))
        assertEquals(before.document.legacy, after.document.legacy)
    }

    @Test fun renamePreservesSpellingStableIdValueAndOrder() {
        val before = collection().apply(f.id(1))
        val after = before.rename(f.id(1), "MiXeD 😀")
        assertEquals("MiXeD 😀", after.record(f.id(1)).name)
        assertEquals(before.record(f.id(1)).value, after.record(f.id(1)).value)
        assertEquals(before.document.users.map { it.id }, after.document.users.map { it.id })
        assertEquals(before.document.active, after.document.active)
        assertEquals(before.document.activeId, after.document.activeId)
    }

    @Test fun applyReplacesOnlyActiveAndAssociationForEveryRecordKind() {
        val before = collection()
        (AppearanceDocument.CURATED + before.document.users + before.document.legacy).forEach { record ->
            val after = before.apply(record.id)
            assertSame(record.value, after.document.active)
            assertEquals(record.id, after.document.activeId)
            assertFalse(after.modified)
            assertEquals(before.document.users, after.document.users)
            assertEquals(before.document.legacy, after.document.legacy)
            assertEquals(before.document.provenance, after.document.provenance)
        }
    }

    @Test fun transientEditIsUnsavedUnlessCallerExplicitlyAssociatesIt() {
        val before = collection().apply(f.id(1))
        val unsaved = before.applyEdit(f.high)
        assertEquals("", unsaved.document.activeId)
        assertFalse(unsaved.modified)
        val modified = before.applyEdit(f.high, f.id(1))
        assertTrue(modified.modified)
        assertEquals(f.id(1), modified.document.activeId)
        assertSame(f.high, modified.document.active)
        assertEquals(before.document.users, modified.document.users)
        assertEquals(before.document.legacy, modified.document.legacy)
        assertFalse(modified.apply(f.id(1)).modified)
    }

    @Test fun deleteActivePreservesExactEditedValueAndClearsOnlyAssociation() {
        val before = collection().applyEdit(f.high, f.id(1))
        val after = before.delete(f.id(1))
        assertSame(before.document.active, after.document.active)
        assertEquals("", after.document.activeId)
        assertEquals(listOf(f.user(2)), after.document.users)
        assertEquals(before.document.legacy, after.document.legacy)
        assertEquals(before.document.provenance, after.document.provenance)
        assertEquals(2, before.document.users.size)
        assertEquals(f.id(1), before.document.activeId)
    }

    @Test fun deleteUnrelatedRecordDoesNotChangeActiveAssociationOrValue() {
        val before = collection().apply(f.id(1))
        val after = before.delete(f.id(2))
        assertSame(before.document.active, after.document.active)
        assertEquals(before.document.activeId, after.document.activeId)
        assertEquals(listOf(f.user(1)), after.document.users)
    }

    @Test fun resetSelectsCuratedAmoledAndPreservesEveryRecordAndRawProvenance() {
        val before = collection().applyEdit(f.high, "legacy:current")
        val after = before.reset()
        assertSame(CuratedAppearances.amoled, after.document.active)
        assertEquals("curated:amoled", after.document.activeId)
        assertEquals(before.document.users, after.document.users)
        assertEquals(before.document.legacy, after.document.legacy)
        assertSame(before.document.provenance, after.document.provenance)
        assertFalse(after.modified)
        assertEquals(after, after.reset())
        assertEquals(after.hashCode(), after.reset().hashCode())
    }

    @Test fun everyCuratedAndLegacyRecordRejectsRenameUpdateDelete() {
        val before = collection()
        val encoded = AppearanceDocumentCodec.encode(before.document)
        (AppearanceDocument.CURATED + before.document.legacy).forEach { record ->
            f.failure("immutable_record") { before.rename(record.id, "Changed") }
            f.failure("immutable_record") { before.update(record.id, f.high) }
            f.failure("immutable_record") { before.delete(record.id) }
        }
        assertEquals(encoded, AppearanceDocumentCodec.encode(before.document))
    }

    @Test fun conflictsAndInvalidActionsFailWithoutChangingOriginal() {
        val before = collection()
        val bytes = AppearanceDocumentCodec.encode(before.document)
        val invalid: List<() -> Unit> = listOf(
            { before.create("Conflict", f.low, f.id(1)) },
            { before.create("user 1", f.low, f.id(3)) },
            { before.save("User 1", f.id(3)) },
            { before.save("Other", f.id(1)) },
            { before.rename(f.id(1), "User 2") },
            { before.rename(f.id(1), " padded ") },
            { before.create(" padded ", f.low, f.id(3)) },
            { before.update(f.id(99), f.low) },
            { before.rename(f.id(99), "Other") },
            { before.delete(f.id(99)) },
            { before.apply(f.id(99)) },
            { before.applyEdit(f.low, f.id(99)) },
            { before.record("") },
        )
        invalid.forEach { operation ->
            f.failure(block = operation)
            assertEquals(bytes, AppearanceDocumentCodec.encode(before.document))
        }
    }

    @Test fun fullCollectionRejectsNewSaveButAllowsExplicitExistingEdits() {
        val before = AppearanceCollection.of(AppearanceDocument.of(users = (1..32).map(f::user)))
        f.failure("too_many_records") { before.create("Overflow", f.low, f.id(33)) }
        f.failure("too_many_records") { before.save("Overflow", f.id(33)) }
        val next = before.rename(f.id(1), "Changed").update(f.id(1), f.high).apply(f.id(1))
        assertEquals(32, next.document.users.size)
        assertSame(f.high, next.document.active)
        assertEquals(32, next.delete(f.id(2)).create("Replacement", f.low, f.id(33)).document.users.size)
    }
}
