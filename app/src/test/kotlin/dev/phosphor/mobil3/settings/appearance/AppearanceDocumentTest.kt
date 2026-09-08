package dev.phosphor.mobil3.settings.appearance

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

internal object AppearanceFixtures {
    fun id(index: Int) = "00000000-0000-0000-0000-" + index.toString().padStart(12, '0')
    val low = AppearanceValue(AppearanceColors(0, 0, 0, 0, 0, 0, Int.MIN_VALUE, Int.MAX_VALUE,
        0, 0, 0, 0, 0), false, false, AppearanceCharacter.CARVED, AppearanceMotion.EASED,
        .25f, .85f, 0, false, false, .2f)
    val high = low.copy(colors = AppearanceColors(0xffffff, 0xffffff, 0xffffff, 0xffffff, 0xffffff, 0xffffff,
        Int.MAX_VALUE, Int.MIN_VALUE, 0xffffff, 0xffffff, 0xffffff, 0xffffff, 0xffffff), dark = true,
        accentFollowsBeam = true, durationScale = 2f, densityScale = 1.25f, radiusDp = 64,
        monoProse = true, designators = true, panelAlphaScale = 1f)
    val provenance = AppearanceProvenance("unknown 😀\n", Int.MIN_VALUE, Int.MAX_VALUE, 0, -1)
    fun user(index: Int) = AppearanceRecord(id(index), "User $index", low.copy(radiusDp = index % 65))
    // Arbitrary authored snapshots prove opaque storage, not production migration or palette fidelity.
    fun legacy(current: Boolean = false): List<AppearanceRecord> =
        (AppearanceDocument.LEGACY_IDS.toList() + if (current) listOf(AppearanceDocument.LEGACY_CURRENT) else emptyList())
            .mapIndexed { i, id -> AppearanceRecord(id, "Original $i 😀", low.copy(radiusDp = i)) }
    fun document() = AppearanceDocument.of(low, "")
    fun failure(code: String? = null, block: () -> Unit): AppearanceException {
        try { block() } catch (error: AppearanceException) {
            if (code != null) assertEquals(code, error.code)
            assertTrue(error.message.isNotBlank())
            assertTrue(error.fix.isNotBlank())
            assertFalse(error.message.contains("instrument", ignoreCase = true))
            assertFalse(error.fix.contains("instrument", ignoreCase = true))
            return error
        }
        throw AssertionError("Expected an appearance-specific validation failure")
    }
}

class AppearanceDocumentTest {
    private val f = AppearanceFixtures

    @Test fun cleanDefaultIsCuratedAmoled() {
        val d = AppearanceDocument.of()
        assertSame(CuratedAppearances.amoled, d.active)
        assertEquals("curated:amoled", d.activeId)
        assertTrue(d.users.isEmpty())
        assertTrue(d.legacy.isEmpty())
        assertEquals(AppearanceProvenance(), d.provenance)
    }

    @Test fun callerListsAndExposedCollectionsCannotMutateDocument() {
        val users = mutableListOf(f.user(1))
        val legacy = f.legacy().toMutableList()
        val d = AppearanceDocument.of(users = users, legacy = legacy)
        users.clear()
        legacy.clear()
        assertEquals(1, d.users.size)
        assertEquals(13, d.legacy.size)
        assertThrows(UnsupportedOperationException::class.java) { (d.users as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (d.legacy as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (AppearanceDocument.CURATED as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (AppearanceDocument.LEGACY_IDS as MutableSet).clear() }
    }

    @Test fun recordOrderIsNotSortedAndValueEqualityIncludesAllState() {
        val users = listOf(f.user(3), f.user(1), f.user(2))
        val a = AppearanceDocument.of(users = users, provenance = f.provenance)
        val b = AppearanceDocument.of(users = users.toMutableList(), provenance = f.provenance.copy())
        assertEquals(users, a.users)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, AppearanceDocument.of(users = users.reversed(), provenance = f.provenance))
        assertNotEquals(a, AppearanceDocument.of(users = users))
    }

    @Test fun everyCuratedAndLegacyIdentityCanBeAssociated() {
        assertEquals(mapOf("curated:light" to CuratedAppearances.light, "curated:dark" to CuratedAppearances.dark,
            "curated:glass" to CuratedAppearances.glass, "curated:amoled" to CuratedAppearances.amoled),
            AppearanceDocument.CURATED.associate { it.id to it.value })
        AppearanceDocument.CURATED.forEach {
            assertEquals(it.value, AppearanceCollection.of().apply(it.id).document.active)
        }
        val legacy = f.legacy(true)
        legacy.forEach { record ->
            val d = AppearanceDocument.of(f.high, record.id, legacy = legacy)
            assertEquals(f.high, d.active)
            assertTrue(AppearanceCollection.of(d).modified)
        }
        f.failure("invalid_active_id") { AppearanceDocument.of(activeId = "legacy:current") }
        f.failure("invalid_active_id") { AppearanceDocument.of(activeId = f.id(1)) }
        f.failure("invalid_active_id") { AppearanceDocument.of(activeId = "curated:unknown") }
    }

    @Test fun allReservedIdsAreRejectedInUsers() {
        (AppearanceDocument.CURATED.map { it.id } + AppearanceDocument.LEGACY_IDS + AppearanceDocument.LEGACY_CURRENT +
            listOf("legacy:unknown", "curated:unknown")).forEach { id ->
            f.failure("invalid_id") { AppearanceDocument.of(users = listOf(f.user(1).copy(id = id))) }
        }
    }

    @Test fun userIdsAreCanonicalUniqueAndStable() {
        listOf("", "1-1-1-1-1", "ABCDEF00-0000-0000-0000-000000000000", f.id(1) + " ",
            "00000000-0000-0000-0000-00000000000g").forEach { id ->
            f.failure("invalid_id") { AppearanceDocument.of(users = listOf(f.user(1).copy(id = id))) }
        }
        f.failure("duplicate_id") { AppearanceDocument.of(users = listOf(f.user(1), f.user(1).copy(name = "Other"))) }
        assertEquals(f.id(1), AppearanceDocument.of(users = listOf(f.user(1))).users.single().id)
    }

    @Test fun userCountAccepts32AndRejects33() {
        assertEquals(32, AppearanceDocument.of(users = (1..32).map(f::user)).users.size)
        f.failure("too_many_records") { AppearanceDocument.of(users = (1..33).map(f::user)) }
    }

    @Test fun nameCodePointBoundsAndSpellingAreExact() {
        val name = "😀".repeat(64)
        assertEquals(name, AppearanceDocument.of(users = listOf(f.user(1).copy(name = name))).users.single().name)
        listOf("", " ", " leading", "trailing ", "a\u0000b", "a\u007fb", "a\u0085b", "\uD800", "\uDC00",
            "😀".repeat(65)).forEach { bad ->
            f.failure("invalid_name") { AppearanceDocument.of(users = listOf(f.user(1).copy(name = bad))) }
        }
        val exact = "MiXeD é e\u0301"
        assertEquals(exact, AppearanceDocument.of(users = listOf(f.user(1).copy(name = exact))).users.single().name)
    }

    @Test fun nameUniquenessMatchesInstrumentPolicyIndependentlyOfLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            listOf("Alpha" to "aLPHA", "I" to "ı", "Σ" to "ς", "K" to "K", "𐐀" to "𐐨").forEach { (a, b) ->
                f.failure("duplicate_name") { AppearanceDocument.of(users = listOf(f.user(1).copy(name = a), f.user(2).copy(name = b))) }
            }
            assertEquals(2, AppearanceDocument.of(users = listOf(f.user(1).copy(name = "é"), f.user(2).copy(name = "e\u0301"))).users.size)
        } finally { Locale.setDefault(previous) }
    }

    @Test fun legacyMustBeEmptyOrExactly13WithOptionalCurrent() {
        assertEquals(setOf("legacy:blossom", "legacy:blossom_dark", "legacy:light", "legacy:dark",
            "legacy:chromacore", "legacy:basalt", "legacy:afterglow", "legacy:stonework95", "legacy:amoled",
            "legacy:paper", "legacy:amber", "legacy:fable", "legacy:glass"), AppearanceDocument.LEGACY_IDS)
        assertEquals("legacy:current", AppearanceDocument.LEGACY_CURRENT)
        assertEquals(13, AppearanceDocument.of(legacy = f.legacy()).legacy.size)
        assertEquals(14, AppearanceDocument.of(legacy = f.legacy(true)).legacy.size)
        val all = f.legacy()
        listOf(all.drop(1), all + all.first(), all + all.first().copy(id = "legacy:unknown"),
            listOf(AppearanceRecord("legacy:current", "Current", f.low)),
            all.drop(1) + all.first().copy(id = f.id(1))).forEach { bad ->
            f.failure("invalid_legacy") { AppearanceDocument.of(legacy = bad) }
        }
    }

    @Test fun legacyNamesRemainOpaqueValidUnicodeRatherThanUserNameNormalization() {
        val exact = "  Original\n" + "x".repeat(70)
        val records = f.legacy().map { it.copy(name = exact) }
        assertEquals(records, AppearanceDocumentCodec.decode(AppearanceDocumentCodec.encode(AppearanceDocument.of(legacy = records))).legacy)
        f.failure("invalid_unicode") { AppearanceDocument.of(legacy = records.map { it.copy(name = "\uD800") }) }
    }

    @Test fun provenancePreservesAbsenceSentinelsUnicodeAndEveryIntExtreme() {
        val values = listOf(AppearanceProvenance(), f.provenance, AppearanceProvenance("", -1, -1, -1, -1),
            AppearanceProvenance("😀".repeat(4096), 0, 0, Int.MAX_VALUE, Int.MIN_VALUE))
        values.forEach { p ->
            assertEquals(p, AppearanceDocumentCodec.decode(AppearanceDocumentCodec.encode(AppearanceDocument.of(provenance = p))).provenance)
        }
        f.failure("invalid_provenance") { AppearanceProvenance("😀".repeat(4097)) }
        f.failure("invalid_provenance") { AppearanceProvenance("\uD800") }
        assertNotEquals(AppearanceProvenance(), AppearanceProvenance("", -1, -1, -1, -1))
    }

    @Test fun canonicalSizeIsEnforcedAtConstruction() {
        val huge = f.legacy().map { it.copy(name = "😀".repeat(4096)) }
        f.failure("document_too_large") { AppearanceDocument.of(legacy = huge) }
    }
}
