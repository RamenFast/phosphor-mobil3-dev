package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderTreeWalkerTest {
    private fun audio(id: String, name: String = "$id.wav") =
        FolderTreeWalker.Document(id, name, "audio/wav")

    private fun directory(id: String, name: String = id) =
        FolderTreeWalker.Document(id, name, FolderTreeWalker.DIRECTORY_MIME)

    private fun page(vararg documents: FolderTreeWalker.Document) =
        FolderTreeWalker.Page(documents.toList(), false)

    @Test
    fun walksNestedFoldersInDepthFirstSiblingOrder() {
        val queried = mutableListOf<String>()
        val tree = mapOf(
            "root" to page(audio("last", "z.wav"), directory("nested", "b"), audio("first", "a.wav")),
            "nested" to page(directory("deep", "c"), audio("middle", "a.wav")),
            "deep" to page(audio("deep-track")),
        )
        val walker = FolderTreeWalker(children = { id, _, _ ->
            queried += id
            tree.getValue(id)
        })

        assertEquals(listOf("first", "middle", "deep-track", "last"), walker.walk("root").map { it.id })
        assertEquals(listOf("root", "nested", "deep"), queried)
    }

    @Test
    fun emptyTreeReturnsNoTracks() {
        val walker = FolderTreeWalker(children = { _, _, _ -> page() })
        assertTrue(walker.walk("root").isEmpty())
    }

    @Test
    fun recognizesAudioMimeOrSupportedCaseInsensitiveExtensionOnly() {
        val documents = listOf(
            FolderTreeWalker.Document("mime", "1.unknown", "audio/custom"),
            FolderTreeWalker.Document("extension", "2.FLAC", "application/octet-stream"),
            FolderTreeWalker.Document("aiff", "3.AIFF", ""),
            FolderTreeWalker.Document("text", "4.txt", "text/plain"),
            FolderTreeWalker.Document("almost", "5.wav.txt", "text/plain"),
        )
        val walker = FolderTreeWalker(children = { _, _, _ -> FolderTreeWalker.Page(documents, false) })
        assertEquals(listOf("mime", "extension", "aiff"), walker.walk("root").map { it.id })
    }

    @Test
    fun sortsAcrossPageBoundariesRatherThanSortingEachPage() {
        val entries = (0 until 270).reversed().map { audio("id-$it", "%03d.wav".format(it)) }
        val offsets = mutableListOf<Int?>()
        val walker = FolderTreeWalker(children = { _, offset, limit ->
            assertEquals(FolderTreeWalker.PAGE_SIZE, limit)
            offsets += offset
            val start = requireNotNull(offset)
            FolderTreeWalker.Page(entries.drop(start).take(limit), start + limit < entries.size)
        })

        assertEquals((0 until 270).map { "id-$it" }, walker.walk("root").map { it.id })
        assertEquals(listOf<Int?>(0, 128, 256), offsets)
    }

    @Test
    fun equalNamesUseCaseThenDocumentIdentityForStableOrder() {
        val entries = listOf(audio("z", "a.wav"), audio("b", "A.wav"), audio("a", "A.wav"))
        val expected = listOf("a", "b", "z")
        for (input in listOf(entries, entries.reversed(), listOf(entries[1], entries[0], entries[2]))) {
            val walker = FolderTreeWalker(children = { _, _, _ -> FolderTreeWalker.Page(input, false) })
            assertEquals(expected, walker.walk("root").map { it.id })
        }
    }

    @Test
    fun repeatedFilesAndDirectoryCyclesAreVisitedOnlyOnce() {
        val queries = mutableListOf<String>()
        val tree = mapOf(
            "root" to page(directory("child", "a"), audio("shared", "z.wav")),
            "child" to page(directory("root"), directory("child"), audio("shared", "a.wav")),
        )
        val walker = FolderTreeWalker(children = { id, _, _ ->
            queries += id
            tree.getValue(id)
        })

        assertEquals(listOf("shared"), walker.walk("root").map { it.id })
        assertEquals(listOf("root", "child"), queries)
    }

    @Test
    fun duplicateIdentityAcrossPagesUsesStableSmallestName() {
        val walker = FolderTreeWalker(children = { _, offset, _ ->
            when (offset) {
                0 -> FolderTreeWalker.Page(listOf(audio("same", "z.wav")), true)
                1 -> page(audio("same", "a.wav"), audio("other", "b.wav"))
                else -> error("Unexpected page $offset")
            }
        })
        assertEquals(listOf("a.wav", "b.wav"), walker.walk("root").map { it.name })
    }

    @Test
    fun repeatedPagingFallsBackToOneCompleteListing() {
        val offsets = mutableListOf<Int?>()
        val errors = mutableListOf<String>()
        val walker = FolderTreeWalker(
            children = { _, offset, _ ->
                offsets += offset
                if (offset == null) page(audio("second"), audio("first"))
                else FolderTreeWalker.Page(listOf(audio("second")), true)
            },
            report = { _, error -> errors += error },
        )

        assertEquals(listOf("first", "second"), walker.walk("root").map { it.id })
        assertEquals(listOf(0, 1, null), offsets)
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("repeated"))
    }

    @Test
    fun unpagedProviderResponseIsNotTruncatedAtPageSize() {
        val entries = (0 until 300).map { audio("id-$it", "%03d.wav".format(it)) }
        var calls = 0
        val walker = FolderTreeWalker(children = { _, _, _ ->
            calls++
            FolderTreeWalker.Page(entries.reversed(), false)
        })
        assertEquals(entries, walker.walk("root"))
        assertEquals(1, calls)
    }

    @Test
    fun emptyPageClaimingMoreFallsBackWithoutLooping() {
        val offsets = mutableListOf<Int?>()
        val walker = FolderTreeWalker(children = { _, offset, _ ->
            offsets += offset
            if (offset == null) page(audio("found")) else FolderTreeWalker.Page(emptyList(), true)
        })
        assertEquals(listOf("found"), walker.walk("root").map { it.id })
        assertEquals(listOf(0, null), offsets)
    }

    @Test
    fun unreadableDirectoryReportsAndContinuesToValidSibling() {
        val errors = mutableListOf<Pair<String, String>>()
        val walker = FolderTreeWalker(
            children = { id, _, _ ->
                if (id == "root") page(directory("denied", "a"), audio("valid", "z.wav"))
                else throw SecurityException("No grant for folder")
            },
            report = { id, error -> errors += id to error },
        )
        assertEquals(listOf("valid"), walker.walk("root").map { it.id })
        assertEquals(listOf("denied" to "No grant for folder"), errors)
    }

    @Test
    fun failedLaterPageReportsWithoutDiscardingReadableEntries() {
        val errors = mutableListOf<String>()
        val walker = FolderTreeWalker(
            children = { _, offset, _ ->
                if (offset == 0) FolderTreeWalker.Page(listOf(audio("valid")), true)
                else error("Provider failed on next page")
            },
            report = { _, error -> errors += error },
        )
        assertEquals(listOf("valid"), walker.walk("root").map { it.id })
        assertEquals(listOf("Provider failed on next page"), errors)
    }

    @Test
    fun brokenUnpagedFallbackReportsAndTerminates() {
        var calls = 0
        val errors = mutableListOf<String>()
        val walker = FolderTreeWalker(
            children = { _, _, _ ->
                calls++
                FolderTreeWalker.Page(listOf(audio("valid")), true)
            },
            report = { _, error -> errors += error },
        )
        assertEquals(listOf("valid"), walker.walk("root").map { it.id })
        assertEquals(3, calls)
        assertEquals(2, errors.size)
        assertTrue(errors.last().contains("unpaged"))
    }

    @Test
    fun alreadySupersededRequestDoesNotQueryProvider() {
        var calls = 0
        val walker = FolderTreeWalker(
            children = { _, _, _ -> calls++; page(audio("stale")) },
            isCurrent = { false },
        )
        assertTrue(walker.walk("root").isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun supersessionDuringQuerySuppressesPartialTreeResult() {
        var current = true
        val queries = mutableListOf<String>()
        val walker = FolderTreeWalker(
            children = { id, _, _ ->
                queries += id
                if (id == "root") page(audio("early", "a.wav"), directory("child", "z"))
                else {
                    current = false
                    page(audio("late"))
                }
            },
            isCurrent = { current },
        )
        assertTrue(walker.walk("root").isEmpty())
        assertEquals(listOf("root", "child"), queries)
    }

    @Test
    fun supersessionInsidePagedQueryDoesNotStartFallback() {
        var current = true
        var calls = 0
        val walker = FolderTreeWalker(
            children = { _, _, _ ->
                calls++
                current = false
                FolderTreeWalker.Page(emptyList(), true)
            },
            isCurrent = { current },
        )
        assertTrue(walker.walk("root").isEmpty())
        assertEquals(1, calls)
    }

    @Test
    fun supersessionAtFallbackWarningDoesNotQueryAgain() {
        var current = true
        var calls = 0
        val walker = FolderTreeWalker(
            children = { _, _, _ ->
                calls++
                FolderTreeWalker.Page(emptyList(), true)
            },
            isCurrent = { current },
            report = { _, _ -> current = false },
        )
        assertTrue(walker.walk("root").isEmpty())
        assertEquals(1, calls)
    }

    @Test
    fun deepTreeDoesNotUseTheCallStackForTraversal() {
        val depth = 2_000
        var calls = 0
        val walker = FolderTreeWalker(children = { id, _, _ ->
            calls++
            val level = id.toInt()
            if (level == depth) page(audio("leaf")) else page(directory((level + 1).toString()))
        })
        assertEquals(listOf("leaf"), walker.walk("0").map { it.id })
        assertEquals(depth + 1, calls)
    }
}
