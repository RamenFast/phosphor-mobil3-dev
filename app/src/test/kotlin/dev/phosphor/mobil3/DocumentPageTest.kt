package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentPageTest {
    private fun readRows(rows: List<List<String?>>, limit: Int?): FolderTreeWalker.Page {
        var index = -1
        return readDocumentPage(
            next = { ++index < rows.size },
            value = { column -> rows[index][column] },
            isCurrent = { true },
            limit = limit,
        )
    }

    @Test
    fun invalidIdentityDoesNotShortenRawPageProgress() {
        val rows = (0 until 128).map { index ->
            listOf(if (index == 0) null else "id-$index", "$index.wav", "audio/wav")
        }
        val page = readRows(rows, 128)
        assertEquals(128, page.rowsRead)
        assertEquals(127, page.entries.size)
        assertTrue(page.hasMore)
    }

    @Test
    fun walkerRequestsLaterValidRowsAfterAFilteredFullPage() {
        val offsets = mutableListOf<Int?>()
        val walker = FolderTreeWalker(children = { _, offset, limit ->
            offsets += offset
            when (offset) {
                0 -> readRows((0 until limit).map { index ->
                    listOf(if (index == 0) null else "id-$index", "$index.wav", "audio/wav")
                }, limit)
                128 -> readRows(listOf(listOf("last", "last.wav", "audio/wav")), limit)
                else -> error("Unexpected raw offset $offset")
            }
        })
        val tracks = walker.walk("root")
        assertEquals(listOf<Int?>(0, 128), offsets)
        assertEquals(128, tracks.size)
        assertEquals("last", tracks.last().id)
    }

    @Test
    fun missingDisplayNameAndMimeKeepTheDocumentIdentity() {
        val page = readRows(listOf(listOf("track.wav", null, null)), 128)
        assertEquals(listOf(FolderTreeWalker.Document("track.wav", "track.wav", "")), page.entries)
        assertFalse(page.hasMore)
        assertEquals(1, page.rowsRead)
    }

    @Test
    fun unpagedReadNeverAdvertisesAnotherPage() {
        val page = readRows((0 until 300).map { listOf("$it", "$it.wav", "audio/wav") }, null)
        assertEquals(300, page.rowsRead)
        assertFalse(page.hasMore)
    }

    @Test
    fun supersededReadDoesNotTouchTheCursor() {
        var advances = 0
        val page = readDocumentPage(
            next = { advances++; true },
            value = { error("Stale cursor must not be read") },
            isCurrent = { false },
            limit = 128,
        )
        assertEquals(0, advances)
        assertEquals(0, page.rowsRead)
        assertTrue(page.entries.isEmpty())
    }

    @Test
    fun cursorFailurePropagatesForTheWalkerToReport() {
        assertFailsWith<IllegalStateException> {
            readDocumentPage(
                next = { error("Cursor failed") },
                value = { null },
                isCurrent = { true },
                limit = 128,
            )
        }
    }
}
