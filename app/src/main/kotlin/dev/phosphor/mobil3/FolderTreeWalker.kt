package dev.phosphor.mobil3

import java.util.Locale

internal class FolderTreeWalker(
    private val children: (id: String, offset: Int?, limit: Int) -> Page,
    private val isCurrent: () -> Boolean = { true },
    private val report: (id: String, error: String) -> Unit = { _, _ -> },
) {
    data class Document(val id: String, val name: String, val mime: String) {
        val isDirectory: Boolean get() = mime == DIRECTORY_MIME
        val isAudio: Boolean get() = mime.startsWith("audio/") ||
            name.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXTENSIONS
    }

    data class Page(
        val entries: List<Document>,
        val hasMore: Boolean,
        val rowsRead: Int = entries.size,
    )

    fun walk(rootId: String): List<Document> {
        val seen = hashSetOf(rootId)
        val result = mutableListOf<Document>()
        val pending = ArrayDeque<Document>()
        listChildren(rootId).asReversed().forEach(pending::addLast)
        while (pending.isNotEmpty() && isCurrent()) {
            val entry = pending.removeLast()
            if (!seen.add(entry.id)) continue
            when {
                entry.isDirectory -> listChildren(entry.id).asReversed().forEach(pending::addLast)
                entry.isAudio -> result += entry
            }
        }
        return if (isCurrent()) result else emptyList()
    }

    private fun listChildren(id: String): List<Document> {
        val entries = mutableMapOf<String, Document>()
        var offset = 0
        try {
            while (isCurrent()) {
                val page = children(id, offset, PAGE_SIZE)
                if (!isCurrent()) return emptyList()
                val previousSize = entries.size
                for (entry in page.entries) {
                    val prior = entries[entry.id]
                    if (prior == null || ORDER.compare(entry, prior) < 0) entries[entry.id] = entry
                }
                if (!page.hasMore) break
                if (entries.size == previousSize || page.entries.isEmpty()) {
                    // A provider may claim paging support but repeat a page. Read one full cursor.
                    report(id, "Provider repeated a page, retrying without paging")
                    if (!isCurrent()) return emptyList()
                    val all = children(id, null, PAGE_SIZE)
                    if (!isCurrent()) return emptyList()
                    check(!all.hasMore) { "Provider did not complete an unpaged listing" }
                    entries.clear()
                    for (entry in all.entries.sortedWith(ORDER)) entries.putIfAbsent(entry.id, entry)
                    break
                }
                offset = Math.addExact(offset, page.rowsRead)
            }
        } catch (error: Exception) {
            report(id, error.message ?: "Could not read folder, choose a readable tree")
        }
        return if (isCurrent()) entries.values.sortedWith(ORDER) else emptyList()
    }

    companion object {
        const val DIRECTORY_MIME = "vnd.android.document/directory"
        const val PAGE_SIZE = 128
        private val AUDIO_EXTENSIONS = setOf("wav", "flac", "mp3", "ogg", "opus", "m4a", "aac", "aiff")
        private val ORDER = compareBy<Document>({ it.name.lowercase(Locale.ROOT) }, { it.name }, { it.id })
    }
}
