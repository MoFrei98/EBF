package com.example.easybooks.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.easybooks.R
import com.example.easybooks.pdf.PdfDocument
import com.example.easybooks.reflow.ReflowEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.security.MessageDigest

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
 * Bücher und Ordner der Bibliothek. Gespeichert wird in der lokalen SQLite-Datenbank
 * ([BookDatabase]); die PDFs liegen als Dateien im Bibliotheksordner auf dem Gerät.
 * Bücher werden über ihre [Book.uuid] angesprochen.
 */
class LibraryRepository(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    private val db = BookDatabase(context)
    private val resolver get() = context.contentResolver

    /** Wird nach jeder Änderung hochgezählt; alle Abfragen laufen dann neu. */
    private val changes = MutableStateFlow(0L)

    /** Schützt Abgleich und Ordner-Aktionen vor gleichzeitigem Zugriff. */
    private val mutex = Mutex()

    /** Ordnerpfad -> Dokument-ID im Bibliotheksordner, aus dem letzten Abgleich. */
    private var folderIds: Map<String, String> = emptyMap()

    val books: Flow<List<Book>> = changes
        .map { ensureMigrated(); db.allBooks() }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    /** Alle Unterordner des Bibliotheksordners als Pfade. */
    val folders: Flow<List<String>> = changes
        .map { ensureMigrated(); db.folders() }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    /** Suche in der Datenbank nach Titel, Dateiname, Autor und Jahr. */
    fun search(query: String): Flow<List<Book>> {
        val words = SearchText.normalize(query).split(' ').filter { it.isNotEmpty() }
        return changes
            .map { ensureMigrated(); db.search(words) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    suspend fun get(uuid: String): Book? = io { ensureMigrated(); db.book(uuid) }

    suspend fun update(uuid: String, transform: (Book) -> Book) = io {
        db.transaction { db.book(uuid)?.let { db.upsert(transform(it)) } }
        changed()
    }

    /** Titel, Autor und Jahr von Hand ändern; werden danach nicht mehr automatisch überschrieben. */
    suspend fun updateDetails(uuid: String, title: String, firstName: String?, lastName: String?, year: Int?) =
        update(uuid) {
            it.copy(
                title = title.trim().ifEmpty { it.title },
                authorFirstName = firstName?.trim()?.ifEmpty { null },
                authorLastName = lastName?.trim()?.ifEmpty { null },
                publishYear = year,
                detailsEdited = true,
            )
        }

    fun coverFile(book: Book): File = File(context.filesDir, "covers/${book.uuid}.jpg")

    /** Cover und gespeicherte Analyse für "Groß" eines Buchs löschen. */
    private fun deleteCaches(book: Book) {
        coverFile(book).delete()
        ReflowEngine.cacheFile(context, book.uuid).delete()
    }

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
            ensureMigrated()
            importInternal(source).also { changed() }
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

        // Dieselbe Datei ist schon in der Bibliothek (gleicher Inhalt, auch unter anderem Namen).
        val sha256 = runCatching { sha256(source) }.getOrNull()
        val duplicate = sha256?.let(db::bookBySha256)
            ?: db.allBooks().firstOrNull { displayName != null && it.fileName == displayName && it.fileSize == size }
        if (duplicate != null) return touch(duplicate)

        val persisted = source.scheme == ContentResolver.SCHEME_CONTENT && persistPermission(source)
        val uri = if (persisted) source else copyToInternal(source, displayName, size)

        db.bookByUri(uri.toString())?.let { return touch(it) }

        val document = try {
            PdfDocument.open(context, uri)
        } catch (e: Exception) {
            if (!persisted) uri.path?.let { File(it).delete() }
            throw ImportException(context.getString(R.string.error_open), e)
        }
        try {
            if (document.pageCount == 0) throw ImportException(context.getString(R.string.error_empty_pdf))
            val meta = readMetadata(uri, displayName)
            val book = Book(
                uri = uri.toString(),
                title = meta.title ?: Book.titleFromFileName(displayName ?: "PDF"),
                authorFirstName = meta.firstName,
                authorLastName = meta.lastName,
                publishYear = meta.year,
                pageCount = document.pageCount,
                pdfLocation = PdfPaths.absolutePath(source) ?: PdfPaths.absolutePath(uri),
                fileName = displayName,
                fileSize = size,
                sha256 = sha256,
            )
            saveCover(document, book)
            db.upsert(book)
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

    private fun touch(book: Book): Book {
        val touched = book.copy(lastOpenedAt = System.currentTimeMillis())
        db.upsert(touched)
        return touched
    }

    // ------------------------------------------------------ Lose Bücher entfernen

    /** Nimmt ein Buch aus der Bibliothek, das nicht im Bibliotheksordner liegt. Die PDF bleibt erhalten. */
    suspend fun remove(book: Book) = io {
        db.delete(book.uuid)
        deleteCaches(book)
        val uri = Uri.parse(book.uri)
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            // Interne Kopie: nur die Kopie löschen, das Original bleibt unberührt.
            uri.path?.let { File(it).delete() }
        } else {
            releasePermission(uri)
        }
        changed()
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
        val label = runCatching { LibraryTree(context, treeUri).label() }.getOrDefault("Easy Books")
        settings.setLibraryFolder(treeUri.toString(), label)
        mutex.withLock { folderIds = emptyMap() }
        sync()
    }

    /**
     * Gleicht die Bibliothek mit dem Bibliotheksordner ab: neue PDFs und Ordner kommen
     * dazu, gelöschte verschwinden. Neue Bücher bekommen danach im Hintergrund Cover,
     * Seitenzahl, Prüfsumme und erkannte Details.
     *
     * @return false, wenn der Ordner nicht gelesen werden konnte
     */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        ensureMigrated()
        val ok = mutex.withLock { syncLocked() }
        changed()
        indexBooks()
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
        db.transaction {
            val books = db.allBooks().toMutableList()
            val seen = HashSet<String>()
            for (file in scan.files) {
                val uri = tree.uriOf(file.id)
                var existing = books.firstOrNull { it.uri == uri.toString() }
                if (existing == null) {
                    // Gleiche Datei unter neuer Adresse (verschoben, umbenannter Ordner) oder
                    // ein loses Buch, das inzwischen auch im Bibliotheksordner liegt.
                    existing = books.firstOrNull {
                        it.uuid !in seen && it.fileName == file.name && it.fileSize == file.size
                    }
                }
                val book = if (existing != null) {
                    if (existing.folder == null && existing.uri != uri.toString()) released += existing.uri
                    existing.copy(
                        uri = uri.toString(),
                        folder = file.folder,
                        fileName = file.name,
                        fileSize = file.size,
                        pdfLocation = PdfPaths.absolutePath(uri),
                    )
                } else {
                    val meta = BookMetadataParser.fromFileName(file.name)
                    Book(
                        uri = uri.toString(),
                        title = meta.title ?: Book.titleFromFileName(file.name),
                        authorFirstName = meta.firstName,
                        authorLastName = meta.lastName,
                        publishYear = meta.year,
                        pageCount = 0,
                        pdfLocation = PdfPaths.absolutePath(uri),
                        folder = file.folder,
                        fileName = file.name,
                        fileSize = file.size,
                        addedAt = now,
                        lastOpenedAt = now,
                    )
                }
                db.upsert(book)
                seen += book.uuid
            }
            // PDFs, die nicht mehr im Bibliotheksordner liegen.
            books.filter { it.folder != null && it.uuid !in seen }.forEach {
                db.delete(it.uuid)
                deleteCaches(it)
            }
            db.setFolders(scan.folders.keys)
        }
        released.forEach { forgetSource(Uri.parse(it)) }
        return true
    }

    /**
     * Ergänzt im Hintergrund, was beim schnellen Abgleich fehlt: Cover, Seitenzahl,
     * Prüfsumme und Details aus den PDF-Metadaten.
     */
    private suspend fun indexBooks() {
        val pending = db.allBooks().filter { it.pageCount == 0 || it.sha256 == null || !coverFile(it).exists() }
        for (book in pending) {
            val uri = Uri.parse(book.uri)
            val document = runCatching { PdfDocument.open(context, uri) }.getOrNull() ?: continue
            try {
                if (!coverFile(book).exists()) saveCover(document, book)
                val pageCount = document.pageCount
                val sha256 = book.sha256 ?: runCatching { sha256(uri) }.getOrNull()
                val meta = if (book.detailsEdited) null else readMetadata(uri, book.fileName)
                io {
                    db.transaction {
                        // Aktuellen Stand lesen, damit Lesefortschritt usw. nicht verloren gehen.
                        val current = db.book(book.uuid) ?: return@transaction
                        db.upsert(current.withMetadata(meta).copy(pageCount = pageCount, sha256 = sha256))
                    }
                }
                changed()
            } finally {
                document.closeNow()
            }
        }
    }

    /** Übernimmt erkannte Details, ohne Eingaben von Hand zu überschreiben. */
    private fun Book.withMetadata(meta: BookMetadata?): Book {
        if (meta == null || detailsEdited) return this
        val autoTitle = fileName == null || title == Book.titleFromFileName(fileName) ||
            title == BookMetadataParser.fromFileName(fileName).title
        return copy(
            title = if (autoTitle) meta.title ?: title else title,
            authorFirstName = authorFirstName ?: meta.firstName,
            authorLastName = authorLastName ?: meta.lastName,
            publishYear = publishYear ?: meta.year,
        )
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
            val result = mutex.withLock {
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
                val moved = (db.book(book.uuid) ?: book).copy(
                    uri = newUri.toString(),
                    folder = targetPath,
                    fileName = newName,
                    pdfLocation = PdfPaths.absolutePath(newUri),
                )
                db.upsert(moved)
                moved to outcome
            }
            changed()
            result
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
            db.delete(book.uuid)
            deleteCaches(book)
        }
        changed()
    }

    // ------------------------------------------------------------------ Ordner

    /** Legt einen Ordner in [parentPath] an und gibt seinen Pfad zurück. */
    suspend fun createFolder(parentPath: String, name: String): String = withContext(Dispatchers.IO) {
        val path = mutex.withLock {
            val tree = ensureFolderIds()
            val clean = FolderPaths.cleanName(name)
            if (clean.isEmpty()) fail(R.string.error_folder_name)
            val path = FolderPaths.child(parentPath, clean)
            if (folderIds.keys.any { it.equals(path, ignoreCase = true) }) fail(R.string.error_folder_exists)
            val parentId = folderIds[parentPath] ?: fail(R.string.error_folder_missing)
            val id = runCatching { tree.createFolder(parentId, clean) }.getOrNull()
                ?: fail(R.string.error_folder_create)
            folderIds = folderIds + (path to id)
            db.setFolders(folderIds.keys)
            path
        }
        changed()
        path
    }

    /** Benennt einen Ordner um und gibt den neuen Pfad zurück. */
    suspend fun renameFolder(path: String, name: String): String = withContext(Dispatchers.IO) {
        val newPath = mutex.withLock {
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
        changed()
        newPath
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
            db.setFolders(folderIds.keys)
        }
        changed()
    }

    // ------------------------------------------------------- Übernahme alter Daten

    private val legacyBooksKey = stringPreferencesKey("books")
    private val legacyFoldersKey = stringPreferencesKey("folders")
    private val migrationMutex = Mutex()

    @Volatile
    private var migrated = false

    /**
     * Übernimmt einmalig die Bibliothek aus dem alten JSON-Format in die Datenbank,
     * inklusive Lesefortschritt, Ordnern und Covern.
     */
    private suspend fun ensureMigrated() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return
            val prefs = context.easyBooksDataStore.data.first()
            val oldBooks = LegacyBookJson.decode(prefs[legacyBooksKey])
            val oldFolders = LegacyBookJson.decodeFolders(prefs[legacyFoldersKey])
            if (oldBooks.isNotEmpty() || oldFolders.isNotEmpty()) {
                db.transaction {
                    for (old in oldBooks) {
                        if (db.bookByUri(old.uri) != null) continue
                        val meta = old.fileName?.let(BookMetadataParser::fromFileName) ?: BookMetadata()
                        val autoTitle = old.fileName != null && old.title == Book.titleFromFileName(old.fileName)
                        val book = old.copy(
                            title = if (autoTitle) meta.title ?: old.title else old.title,
                            authorFirstName = meta.firstName,
                            authorLastName = meta.lastName,
                            publishYear = meta.year,
                            pdfLocation = PdfPaths.absolutePath(Uri.parse(old.uri)),
                        )
                        db.upsert(book)
                        // Cover hingen bisher an der Adresse, jetzt an der uuid.
                        val oldCover = File(context.filesDir, "covers/${sha1(old.uri)}.jpg")
                        if (oldCover.exists()) oldCover.renameTo(coverFile(book))
                    }
                    if (db.folders().isEmpty()) db.setFolders(oldFolders)
                }
                context.easyBooksDataStore.edit {
                    it.remove(legacyBooksKey)
                    it.remove(legacyFoldersKey)
                }
            }
            migrated = true
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private fun changed() = changes.update { it + 1 }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun fail(@StringRes message: Int): Nothing =
        throw LibraryException(context.getString(message))

    private suspend fun saveCover(document: PdfDocument, book: Book) {
        runCatching {
            val bitmap = document.render(0, COVER_WIDTH_PX)
            val file = coverFile(book).apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
        }
    }

    /** Details aus der PDF (Anfang und Ende der Datei), sonst aus dem Dateinamen. */
    private fun readMetadata(uri: Uri, fileName: String?): BookMetadata {
        val fromName = fileName?.let(BookMetadataParser::fromFileName) ?: BookMetadata()
        val fromPdf = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    val channel = input.channel
                    val size = channel.size()
                    val head = ByteArray(minOf(size, METADATA_HEAD_BYTES).toInt())
                    channel.position(0)
                    input.read(head)
                    val tailSize = minOf(size, METADATA_TAIL_BYTES).toInt()
                    val tail = ByteArray(tailSize)
                    channel.position(size - tailSize)
                    input.read(tail)
                    BookMetadataParser.fromPdf(tail, head)
                }
            }
        }.getOrNull() ?: BookMetadata()
        return fromPdf.orElse(fromName)
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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

    private companion object {
        const val COVER_WIDTH_PX = 360
        const val METADATA_HEAD_BYTES = 1024L * 1024
        const val METADATA_TAIL_BYTES = 256L * 1024
    }
}
