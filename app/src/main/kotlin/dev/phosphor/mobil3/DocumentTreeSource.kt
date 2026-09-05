package dev.phosphor.mobil3

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract

internal class DocumentTreeSource(
    private val resolver: ContentResolver,
    private val tree: Uri,
    private val isCurrent: () -> Boolean,
) {
    fun children(id: String, offset: Int?, limit: Int): FolderTreeWalker.Page {
        if (!isCurrent()) return FolderTreeWalker.Page(emptyList(), false)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        if (offset != null) {
            val args = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                ))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
            }
            resolver.query(uri, PROJECTION, args, null)?.use { cursor ->
                if (!isCurrent()) return FolderTreeWalker.Page(emptyList(), false)
                checkLoaded(cursor)
                val honored = cursor.extras.getStringArray(ContentResolver.EXTRA_HONORED_ARGS).orEmpty()
                if (PAGING_ARGS.all { it in honored }) {
                    return read(cursor, limit)
                }
            }
        }
        if (!isCurrent()) return FolderTreeWalker.Page(emptyList(), false)
        // Providers without paging support still expose a windowed cursor, not Binder queue arrays.
        return resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            checkLoaded(cursor)
            read(cursor, null)
        }
            ?: error("Provider returned no folder cursor, choose a readable tree")
    }

    private fun checkLoaded(cursor: Cursor) {
        check(!cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
            "Folder listing is still loading, choose the folder again when ready"
        }
    }

    private fun read(cursor: Cursor, limit: Int?) =
        readDocumentPage(cursor::moveToNext, cursor::getString, isCurrent, limit)

    companion object {
        private val PAGING_ARGS = setOf(
            ContentResolver.QUERY_ARG_LIMIT,
            ContentResolver.QUERY_ARG_OFFSET,
            ContentResolver.QUERY_ARG_SORT_COLUMNS,
            ContentResolver.QUERY_ARG_SORT_DIRECTION,
        )
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}

internal fun readDocumentPage(
    next: () -> Boolean,
    value: (Int) -> String?,
    isCurrent: () -> Boolean,
    limit: Int?,
): FolderTreeWalker.Page {
    var rows = 0
    val entries = buildList {
        while (isCurrent() && next()) {
            rows++
            val id = value(0) ?: continue
            add(FolderTreeWalker.Document(id, value(1) ?: id, value(2).orEmpty()))
        }
    }
    return FolderTreeWalker.Page(entries, limit != null && rows >= limit, rows)
}
