package com.example.ebf.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Speichert die Bücher der Bibliothek samt Lesefortschritt. */
class LibraryRepository(private val context: Context) {

    private val booksKey = stringPreferencesKey("books")
    private val store get() = context.ebfDataStore

    val books: Flow<List<Book>> = store.data
        .map { BookJson.decode(it[booksKey]) }
        .distinctUntilChanged()

    suspend fun get(uri: String): Book? = books.first().firstOrNull { it.uri == uri }

    suspend fun update(uri: String, transform: (Book) -> Book) {
        store.edit { prefs ->
            prefs.writeBooks(prefs.readBooks().map { if (it.uri == uri) transform(it) else it })
        }
    }

    suspend fun remove(book: Book) = withContext(Dispatchers.IO) {
        store.edit { prefs -> prefs.writeBooks(prefs.readBooks().filterNot { it.uri == book.uri }) }
        coverFile(book).delete()
        val uri = Uri.parse(book.uri)
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            // Interne Kopie: nur die Kopie löschen, das Original bleibt unberührt.
            uri.path?.let { File(it).delete() }
        } else {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    fun coverFile(book: Book): File = File(context.filesDir, "covers/${book.key}.jpg")

    /**
     * Nimmt eine PDF in die Bibliothek auf.
     *
     * Aus dem Dateidialog bekommen wir eine dauerhafte Leseberechtigung. Kommt die PDF
     * über "Öffnen mit" aus einer anderen App, gilt die Berechtigung nur kurz. Dann
     * legen wir eine interne Kopie an, damit das Buch auch später noch geöffnet werden kann.
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
        val persisted = source.scheme == ContentResolver.SCHEME_CONTENT && runCatching {
            context.contentResolver.takePersistableUriPermission(
                source, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        val uri = if (persisted) source else copyToLibrary(source, displayName, size)

        get(uri.toString())?.let { existing ->
            val touched = existing.copy(lastOpenedAt = System.currentTimeMillis())
            update(existing.uri) { touched }
            return touched
        }

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
            )
            saveCover(document, book)
            store.edit { prefs ->
                prefs.writeBooks(prefs.readBooks().filterNot { it.uri == book.uri } + book)
            }
            return book
        } finally {
            document.closeNow()
        }
    }

    private suspend fun saveCover(document: PdfDocument, book: Book) {
        runCatching {
            val bitmap = document.render(0, COVER_WIDTH_PX)
            val file = coverFile(book).apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
        }
    }

    private fun copyToLibrary(source: Uri, displayName: String?, size: Long): Uri {
        val dir = File(context.filesDir, "books").apply { mkdirs() }
        // Gleiche Datei (Name + Größe) nur einmal kopieren.
        val id = if (displayName != null && size >= 0) "$displayName:$size" else source.toString()
        val target = File(dir, sha1(id) + ".pdf")
        if (!target.exists() || target.length() == 0L) {
            val temp = File(dir, target.name + ".tmp")
            val input = context.contentResolver.openInputStream(source)
                ?: throw FileNotFoundException(source.toString())
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
            context.contentResolver.query(
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

    private fun androidx.datastore.preferences.core.MutablePreferences.writeBooks(books: List<Book>) {
        this[booksKey] = BookJson.encode(books)
    }

    private companion object {
        const val COVER_WIDTH_PX = 360
    }
}
