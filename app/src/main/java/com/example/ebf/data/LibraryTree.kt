package com.example.ebf.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * Zugriff auf den Bibliotheksordner auf dem Gerät über das Storage Access Framework.
 *
 * Der Nutzer wählt den Ordner einmal aus, danach darf die App darin ohne weitere
 * Berechtigungen Unterordner anlegen und PDFs lesen, verschieben und löschen.
 */
class LibraryTree(context: Context, val treeUri: Uri) {

    private val resolver = context.contentResolver
    val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)

    class Entry(val id: String, val name: String, val isDirectory: Boolean, val size: Long)

    /** Eine PDF im Bibliotheksordner. */
    class PdfFile(val folder: String, val id: String, val name: String, val size: Long)

    class Scan(
        /** Ordnerpfad -> Dokument-ID, inklusive oberster Ebene (""). */
        val folders: Map<String, String>,
        val files: List<PdfFile>,
    )

    fun uriOf(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    /** Lesbare Bezeichnung, z. B. "Documents/EBF". */
    fun label(): String {
        val path = rootId.substringAfter(':', "")
        if (path.isNotEmpty()) return path
        return displayName(rootId) ?: rootId
    }

    fun list(id: String): List<Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        val result = mutableListOf<Entry>()
        resolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                result += Entry(
                    id = c.getString(0),
                    name = name,
                    isDirectory = c.getString(2) == Document.MIME_TYPE_DIR,
                    size = if (c.isNull(3)) -1L else c.getLong(3),
                )
            }
        }
        return result
    }

    /** Durchsucht den Bibliotheksordner nach Unterordnern und PDFs. */
    fun scan(): Scan {
        val folders = linkedMapOf(FolderPaths.ROOT to rootId)
        val files = mutableListOf<PdfFile>()
        val queue = ArrayDeque(listOf(FolderPaths.ROOT to rootId))
        while (queue.isNotEmpty()) {
            val (path, id) = queue.removeFirst()
            for (entry in list(id)) {
                if (entry.name.startsWith(".")) continue
                if (entry.isDirectory) {
                    val childPath = FolderPaths.child(path, entry.name)
                    if (FolderPaths.depth(childPath) > MAX_DEPTH) continue
                    folders[childPath] = entry.id
                    queue += childPath to entry.id
                } else if (entry.name.endsWith(".pdf", ignoreCase = true) && files.size < MAX_FILES) {
                    files += PdfFile(path, entry.id, entry.name, entry.size)
                }
            }
        }
        return Scan(folders, files)
    }

    fun displayName(id: String): String? =
        resolver.query(uriOf(id), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun createFolder(parentId: String, name: String): String? =
        DocumentsContract.createDocument(resolver, uriOf(parentId), Document.MIME_TYPE_DIR, name)
            ?.let { DocumentsContract.getDocumentId(it) }

    fun createPdf(parentId: String, name: String): Uri? =
        DocumentsContract.createDocument(resolver, uriOf(parentId), "application/pdf", name)

    fun rename(id: String, name: String): Uri? =
        DocumentsContract.renameDocument(resolver, uriOf(id), name)

    fun delete(id: String): Boolean = DocumentsContract.deleteDocument(resolver, uriOf(id))

    fun move(id: String, fromParentId: String, toParentId: String): Uri? =
        DocumentsContract.moveDocument(resolver, uriOf(id), uriOf(fromParentId), uriOf(toParentId))

    companion object {
        private const val MAX_DEPTH = 8
        private const val MAX_FILES = 5000

        /** Vorschlag für den Ordnerdialog: "Dokumente" im internen Speicher. */
        val DOCUMENTS_HINT: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Documents"
        )
    }
}
