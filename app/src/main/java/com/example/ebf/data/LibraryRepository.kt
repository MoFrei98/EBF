package com.example.ebf.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.ebf.R
import com.example.ebf.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Fehler bei einer Aktion im Bibliotheksordner, mit verständlicher Meldung. */
class LibraryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Was beim Einsortieren mit der Original-PDF passiert ist. */
enum class MoveOutcome {
    /** Die PDF wurde verschoben. */
    MOVED,

    /** Die PDF wurde kopiert, weil das Original nicht gelöscht werden durfte. */
    COPIED,
}

/**
 * Speichert die Bücher der Bibliothek samt Lesefortschritt und hält sie mit dem
 * Bibliotheksordner auf dem Gerät abgeglichen.
 */
class LibraryRepository(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    private val booksKey = stringPreferencesKey("books")
    private val foldersKey = stringPreferencesKey("folders")
    private val store get() = context.ebfDataStore
    private val resolver get() = context.contentResolver

    /** Schützt Abgleich und Ordner-Aktionen vor gleichzeitigem Zugriff. */
    private val mutex = Mutex()

    /** Ordnerpfad -> Dokument-ID im Bibliotheksordner, aus dem letzten Abgleich. */
    private var folderIds: Map<String, String> = emptyMap()

    val books: Flow<List<Book>> = store.data
        .map { BookJson.decode(it[booksKey]) }
        .distinctUntilChanged()

    /** Alle Unterordner des Bibliotheksordners als Pfade. */
    val folders: Flow<List<String>> = store.data
        .map { decodeFolders(it[foldersKey]) }
        .distinctUntilChanged()

    suspend fun get(uri: String): Book? = books.first().firstOrNull { it.uri == uri }

    suspend fun update(uri: String, transform: (Book) -> Book) {
        store.edit { prefs ->
            prefs.writeBooks(prefs.readBooks().map { if (it.uri == uri) transform(it) else it })
        }
    }

    fun coverFile(book: Book): File = coverFile(book.uri)

    private fun coverFile(uri: String): File = File(context.filesDir, "covers/${sha1(uri)}.jpg")

    // ---------------------------------------------------------------- Import

    /**
     * Nimmt eine PDF in die Bibliothek auf.
     *
     * Aus dem Dateidialog bekommen wir eine dauerhafte Berechtigung. Kommt die PDF über
     * "Öffnen mit" aus einer anderen App, gilt die Berechtigung nur kurz. Dann legen wir
     * eine interne Kopie an, damit das Buch auch später noch geöffnet werden kann.
     */
    suspend fun import(source: Uri): Book = withContext(Dispatchers.IO) {
        try {
            importInternal(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImportException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw ImportException(context.getString(R.string.error_missing), e)
        } catch (e: SecurityException) {
            throw ImportException(context.getString(R.string.error_missing), e)
        } catch (e: Exception) {
            throw ImportException(context.getString(R.string.error_open), e)
        }
    }

    private suspend fun importInternal(source: Uri): Book {
        val (displayName, size) = queryMeta(source)

        // Dieselbe Datei ist schon in der Bibliothek, z. B. einmal über den Dateidialog
        // und einmal über "Öffnen mit" geöffnet.
        if (displayName != null && size >= 0) {
            books.first().firstOrNull { it.fileName == displayName && it.fileSize == size }
                ?.let { existing -> return touch(existing) }
        }

        val persisted = source.scheme == ContentResolver.SCHEME_CONTENT && persistPermission(source)
        val uri = if (persisted) source else copyToInternal(source, displayName, size)

        get(uri.toString())?.let { existing -> return touch(existing) }

        val document = try {
            PdfDocument.open(context, uri)
        } catch (e: Exception) {
            if (!persisted) uri.path?.let { File(it).delete() }
            throw ImportException(context.getString(R.string.error_open), e)
        }
        try {
            if (document.pageCount == 0) {
                throw ImportException(context.getString(R.string.error_empty_pdf))
            }
            val book = Book(
                uri = uri.toString(),
                title = Book.titleFromFileName(displayName ?: "PDF"),
                pageCount = document.pageCount,
                fileName = displayName,
                fileSize = size,
            )
            saveCover(document, book.uri)
            store.edit { prefs ->
                prefs.writeBooks(prefs.readBooks().filterNot { it.uri == book.uri } + book)
            }
            return book
        } finally {
            document.closeNow()
        }
    }

    /**
     * Dauerhafte Berechtigung sichern. Mit Schreibrecht können wir die PDF später
     * beim Einsortieren verschieben, sonst nur kopieren.
     */
    private fun persistPermission(uri: Uri): Boolean {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return runCatching { resolver.takePersistableUriPermission(uri, read or write) }.isSuccess ||
            runCatching { resolver.takePersistableUriPermission(uri, read) }.isSuccess
    }

    private suspend fun touch(book: Book): Book {
        val touched = book.copy(lastOpenedAt = System.currentTimeMillis())
        update(book.uri) { touched }
        return touched
    }

    // ------------------------------------------------------ Lose Bücher entfernen

    /** Nimmt ein Buch aus der Bibliothek, das nicht im Bibliotheksordner liegt. Die PDF bleibt erhalten. */
    suspend fun remove(book: Book) = withContext(Dispatchers.IO) {
        store.edit { prefs -> prefs.writeBooks(prefs.readBooks().filterNot { it.uri == book.uri }) }
        coverFile(book).delete()
        val uri = Uri.parse(book.uri)
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            // Interne Kopie: nur die Kopie löschen, das Original bleibt unberührt.
            uri.path?.let { File(it).delete() }
        } else {
            releasePermission(uri)
        }
    }

    // --------------------------------------------------------- Bibliotheksordner

    private suspend fun tree(): LibraryTree? {
        val uri = settings.settings.first().libraryTree ?: return null
        return runCatching { LibraryTree(context, Uri.parse(uri)) }.getOrNull()
    }

    private suspend fun requireTree(): LibraryTree =
        tree() ?: throw LibraryException(context.getString(R.string.error_no_library_folder))

    /** Legt den Bibliotheksordner fest (aus dem Ordnerdialog) und liest ihn ein. */
    suspend fun setLibraryFolder(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: SecurityException) {
            throw LibraryException(context.getString(R.string.error_library_unreadable), e)
        }
        val old = settings.settings.first().libraryTree
        if (old != null && old != treeUri.toString()) {
            runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        val label = runCatching { LibraryTree(context, treeUri).label() }.getOrDefault("EBF")
        settings.setLibraryFolder(treeUri.toString(), label)
        mutex.withLock { folderIds = emptyMap() }
        sync()
    }

    /**
     * Gleicht die Bibliothek mit dem Bibliotheksordner ab: neue PDFs und Ordner kommen
     * dazu, gelöschte verschwinden. Neue Bücher bekommen danach im Hintergrund ein Cover.
     *
     * @return false, wenn der Ordner nicht gelesen werden konnte
     */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        val ok = mutex.withLock { syncLocked() }
        if (ok) indexNewBooks()
        ok
    }

    private suspend fun syncLocked(): Boolean {
        val tree = tree() ?: run {
            folderIds = emptyMap()
            return true
        }
        val scan = try {
            tree.scan()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        folderIds = scan.folders
        val now = System.currentTimeMillis()
        val released = mutableListOf<String>()
        store.edit { prefs ->
            val books = prefs.readBooks().toMutableList()
            val seen = HashSet<String>()
            for (file in scan.files) {
                val uri = tree.uriOf(file.id).toString()
                var index = books.indexOfFirst { it.uri == uri }
                if (index < 0) {
                    // Gleiche Datei unter neuer Adresse (verschoben, umbenannter Ordner) oder
                    // ein loses Buch, das inzwischen auch im Bibliotheksordner liegt.
                    index = books.indexOfFirst {
                        it.uri !in seen && it.fileName == file.name && it.fileSize == file.size
                    }
                }
                if (index >= 0) {
                    val old = books[index]
                    if (old.uri != uri) {
                        moveCover(old.uri, uri)
                        if (old.folder == null) released += old.uri
                    }
                    books[index] = old.copy(uri = uri, folder = file.folder, fileName = file.name, fileSize = file.size)
                } else {
                    books += Book(
                        uri = uri,
                        title = Book.titleFromFileName(file.name),
                        pageCount = 0,
                        folder = file.folder,
                        fileName = file.name,
                        fileSize = file.size,
                        addedAt = now,
                        lastOpenedAt = now,
                    )
                }
                seen += uri
            }
            // PDFs, die nicht mehr im Bibliotheksordner liegen.
            val gone = books.filter { it.folder != null && it.uri !in seen }
            gone.forEach { coverFile(it).delete() }
            books.removeAll(gone)
            prefs.writeBooks(books)
            prefs[foldersKey] = encodeFolders(scan.folders.keys)
        }
        released.forEach { forgetSource(Uri.parse(it)) }
        return true
    }

    /** Seitenzahl und Cover für neu gefundene Bücher. */
    private suspend fun indexNewBooks() {
        val pending = books.first().filter { it.pageCount == 0 || !coverFile(it).exists() }
        for (book in pending) {
            val document = runCatching { PdfDocument.open(context, Uri.parse(book.uri)) }.getOrNull() ?: continue
            try {
                if (!coverFile(book).exists()) saveCover(document, book.uri)
                val pageCount = document.pageCount
                update(book.uri) { it.copy(pageCount = pageCount) }
            } finally {
                document.closeNow()
            }
        }
    }

    private suspend fun ensureFolderIds(): LibraryTree {
        val tree = requireTree()
        if (folderIds.isEmpty() && !syncLocked()) fail(R.string.error_library_unreadable)
        return tree
    }

    // ------------------------------------------------------------ Bücher sortieren

    /**
     * Sortiert ein Buch in einen Ordner des Bibliotheksordners ein. Die PDF wird dabei
     * verschoben; darf das Original nicht gelöscht werden, wird sie kopiert.
     */
    suspend fun moveBook(book: Book, targetPath: String): Pair<Book, MoveOutcome> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val tree = ensureFolderIds()
                if (book.folder == targetPath) return@withLock book to MoveOutcome.MOVED
                val targetId = folderIds[targetPath] ?: fail(R.string.error_folder_missing)
                val source = Uri.parse(book.uri)
                val name = book.fileName ?: "${book.title}.pdf"
                var outcome = MoveOutcome.MOVED

                val newUri = if (book.folder != null) {
                    // Innerhalb des Bibliotheksordners: direkt verschieben.
                    val parentId = folderIds[book.folder]
                    val moved = parentId?.let {
                        runCatching { tree.move(DocumentsContract.getDocumentId(source), it, targetId) }.getOrNull()
                    }
                    moved ?: copyInto(tree, targetId, source, name).also {
                        if (!deleteDocument(source)) outcome = MoveOutcome.COPIED
                    }
                } else {
                    // Von außerhalb (z. B. Downloads): kopieren und das Original löschen.
                    copyInto(tree, targetId, source, name).also {
                        if (!deleteSource(source)) outcome = MoveOutcome.COPIED
                    }
                }

                val newName = runCatching { queryMeta(newUri).first }.getOrNull() ?: name
                val moved = book.copy(uri = newUri.toString(), folder = targetPath, fileName = newName)
                store.edit { prefs ->
                    prefs.writeBooks(prefs.readBooks().map { if (it.uri == book.uri) moved else it })
                }
                moveCover(book.uri, moved.uri)
                moved to outcome
            }
        }

    private fun copyInto(tree: LibraryTree, parentId: String, source: Uri, name: String): Uri {
        val target = runCatching { tree.createPdf(parentId, name) }.getOrNull()
            ?: fail(R.string.error_move)
        try {
            val input = resolver.openInputStream(source) ?: throw FileNotFoundException(source.toString())
            input.use { stream ->
                val output = resolver.openOutputStream(target, "w") ?: throw FileNotFoundException(target.toString())
                output.use { stream.copyTo(it) }
            }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw LibraryException(context.getString(R.string.error_move), e)
        }
        return target
    }

    /** Löscht die Original-PDF außerhalb des Bibliotheksordners, wenn Android es erlaubt. */
    private fun deleteSource(uri: Uri): Boolean {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let { File(it).delete() } ?: false
        }
        val deleted = deleteDocument(uri)
        releasePermission(uri)
        return deleted
    }

    private fun deleteDocument(uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)

    /** Ein loses Buch wurde mit einer Datei im Bibliotheksordner zusammengeführt. */
    private fun forgetSource(uri: Uri) {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { File(it).delete() }
        } else {
            releasePermission(uri)
        }
    }

    private fun releasePermission(uri: Uri) {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(uri, read or write) }
            .onFailure { runCatching { resolver.releasePersistableUriPermission(uri, read) } }
    }

    /** Löscht die PDF eines Buchs aus dem Bibliotheksordner vom Gerät. */
    suspend fun deleteBookFile(book: Book) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!deleteDocument(Uri.parse(book.uri))) fail(R.string.error_delete)
            store.edit { prefs -> prefs.writeBooks(prefs.readBooks().filterNot { it.uri == book.uri }) }
            coverFile(book).delete()
        }
    }

    // ------------------------------------------------------------------ Ordner

    /** Legt einen Ordner in [parentPath] an und gibt seinen Pfad zurück. */
    suspend fun createFolder(parentPath: String, name: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tree = ensureFolderIds()
            val clean = FolderPaths.cleanName(name)
            if (clean.isEmpty()) fail(R.string.error_folder_name)
            val path = FolderPaths.child(parentPath, clean)
            if (folderIds.keys.any { it.equals(path, ignoreCase = true) }) fail(R.string.error_folder_exists)
            val parentId = folderIds[parentPath] ?: fail(R.string.error_folder_missing)
            val id = runCatching { tree.createFolder(parentId, clean) }.getOrNull()
                ?: fail(R.string.error_folder_create)
            folderIds = folderIds + (path to id)
            store.edit { it[foldersKey] = encodeFolders(folderIds.keys) }
            path
        }
    }

    /** Benennt einen Ordner um und gibt den neuen Pfad zurück. */
    suspend fun renameFolder(path: String, name: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tree = ensureFolderIds()
            val clean = FolderPaths.cleanName(name)
            if (clean.isEmpty()) fail(R.string.error_folder_name)
            val newPath = FolderPaths.child(FolderPaths.parent(path), clean)
            if (newPath == path) return@withLock path
            if (folderIds.keys.any { it.equals(newPath, ignoreCase = true) && it != path }) {
                fail(R.string.error_folder_exists)
            }
            val id = folderIds[path] ?: fail(R.string.error_folder_missing)
            runCatching { tree.rename(id, clean) }.getOrNull() ?: fail(R.string.error_folder_rename)
            // Adressen aller enthaltenen Dateien ändern sich, deshalb neu einlesen.
            syncLocked()
            newPath
        }
    }

    /** Löscht einen leeren Ordner. */
    suspend fun deleteFolder(path: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tree = ensureFolderIds()
            val id = folderIds[path] ?: fail(R.string.error_folder_missing)
            // Nur wirklich leere Ordner löschen, damit keine anderen Dateien verloren gehen.
            if (tree.list(id).isNotEmpty()) fail(R.string.error_folder_not_empty)
            if (!runCatching { tree.delete(id) }.getOrDefault(false)) fail(R.string.error_delete)
            folderIds = folderIds.filterKeys { !FolderPaths.isInside(it, path) || it.isEmpty() }
            store.edit { it[foldersKey] = encodeFolders(folderIds.keys) }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private fun fail(@StringRes message: Int): Nothing =
        throw LibraryException(context.getString(message))

    private suspend fun saveCover(document: PdfDocument, uri: String) {
        runCatching {
            val bitmap = document.render(0, COVER_WIDTH_PX)
            val file = coverFile(uri).apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
        }
    }

    /** Das Cover gehört zur Adresse der PDF; ändert sie sich, wandert das Cover mit. */
    private fun moveCover(oldUri: String, newUri: String) {
        val old = coverFile(oldUri)
        if (old.exists()) old.renameTo(coverFile(newUri))
    }

    private fun copyToInternal(source: Uri, displayName: String?, size: Long): Uri {
        val dir = File(context.filesDir, "books").apply { mkdirs() }
        // Gleiche Datei (Name + Größe) nur einmal kopieren.
        val id = if (displayName != null && size >= 0) "$displayName:$size" else source.toString()
        val target = File(dir, sha1(id) + ".pdf")
        if (!target.exists() || target.length() == 0L) {
            val temp = File(dir, target.name + ".tmp")
            val input = resolver.openInputStream(source) ?: throw FileNotFoundException(source.toString())
            input.use { stream -> temp.outputStream().use { stream.copyTo(it) } }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw FileNotFoundException(target.path)
            }
        }
        return Uri.fromFile(target)
    }

    private fun queryMeta(uri: Uri): Pair<String?, Long> {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(uri.path ?: return null to -1L)
            return file.name to file.length()
        }
        val meta = runCatching {
            resolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                name to size
            }
        }.getOrNull()
        return meta ?: (uri.lastPathSegment to -1L)
    }

    private fun Preferences.readBooks(): List<Book> = BookJson.decode(this[booksKey])

    private fun MutablePreferences.writeBooks(books: List<Book>) {
        this[booksKey] = BookJson.encode(books)
    }

    private fun encodeFolders(paths: Collection<String>): String =
        JSONArray(paths.filter { it.isNotEmpty() }).toString()

    private fun decodeFolders(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).ifEmpty { null } }
    }

    private companion object {
        const val COVER_WIDTH_PX = 360
    }
}
