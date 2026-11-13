package com.example.easybooks.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Lokale SQLite-Datenbank der Bibliothek (Datei `ebf.db` im privaten App-Speicher).
 *
 * Nutzt nur das in Android eingebaute SQLite, keine zusätzliche Bibliothek.
 * Alle Methoden blockieren und müssen im Hintergrund (Dispatchers.IO) laufen.
 * Die PDFs selbst liegen nicht in der Datenbank, sondern als Dateien im Bibliotheksordner.
 */
class BookDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE books (
                uuid             TEXT PRIMARY KEY NOT NULL,
                title            TEXT NOT NULL,
                author_firstname TEXT,
                author_lastname  TEXT,
                publish_year     INTEGER,
                page_count       INTEGER NOT NULL DEFAULT 0,
                pdf_location     TEXT,
                pdf_uri          TEXT NOT NULL UNIQUE,
                file_name        TEXT,
                file_size        INTEGER NOT NULL DEFAULT -1,
                sha256           TEXT,
                folder           TEXT,
                last_page        INTEGER NOT NULL DEFAULT 0,
                last_page_offset REAL NOT NULL DEFAULT 0,
                details_edited   INTEGER NOT NULL DEFAULT 0,
                added_at         INTEGER NOT NULL,
                last_opened_at   INTEGER NOT NULL,
                search_text      TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX books_folder ON books(folder)")
        db.execSQL("CREATE INDEX books_sha256 ON books(sha256)")
        db.execSQL("CREATE TABLE folders (path TEXT PRIMARY KEY NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Version 2: genaue Lesestelle für die Schriftgröße "Groß".
            db.execSQL("ALTER TABLE books ADD COLUMN last_page_offset REAL NOT NULL DEFAULT 0")
        }
    }

    // ------------------------------------------------------------------ Bücher

    fun allBooks(): List<Book> = queryBooks(null, null)

    fun book(uuid: String): Book? = queryBooks("uuid = ?", arrayOf(uuid)).firstOrNull()

    fun bookByUri(uri: String): Book? = queryBooks("pdf_uri = ?", arrayOf(uri)).firstOrNull()

    fun bookBySha256(sha256: String): Book? = queryBooks("sha256 = ?", arrayOf(sha256)).firstOrNull()

    /** Bücher, deren Suchtext alle [words] enthält (Wörter bereits normalisiert). */
    fun search(words: List<String>): List<Book> {
        if (words.isEmpty()) return allBooks()
        val where = words.joinToString(" AND ") { "search_text LIKE ? ESCAPE '\\'" }
        val args = words.map { "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%" }
        return queryBooks(where, args.toTypedArray())
    }

    /** Fügt ein Buch ein oder ersetzt es (gleiche uuid). */
    fun upsert(book: Book) {
        writableDatabase.insertWithOnConflict("books", null, values(book), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(uuid: String) {
        writableDatabase.delete("books", "uuid = ?", arrayOf(uuid))
    }

    fun isEmpty(): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM books LIMIT 1", null).use { !it.moveToFirst() } &&
            readableDatabase.rawQuery("SELECT 1 FROM folders LIMIT 1", null).use { !it.moveToFirst() }

    // ------------------------------------------------------------------ Ordner

    fun folders(): List<String> =
        readableDatabase.rawQuery("SELECT path FROM folders ORDER BY path", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    /** Ersetzt alle Ordner durch [paths] (ohne die oberste Ebene ""). */
    fun setFolders(paths: Collection<String>) {
        transaction {
            writableDatabase.delete("folders", null, null)
            paths.filter { it.isNotEmpty() }.distinct().forEach {
                writableDatabase.insert("folders", null, ContentValues().apply { put("path", it) })
            }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    fun <T> transaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    private fun queryBooks(where: String?, args: Array<String>?): List<Book> =
        readableDatabase.query("books", null, where, args, null, null, null).use { c ->
            buildList { while (c.moveToNext()) add(c.toBook()) }
        }

    private fun values(b: Book) = ContentValues().apply {
        put("uuid", b.uuid)
        put("title", b.title)
        put("author_firstname", b.authorFirstName)
        put("author_lastname", b.authorLastName)
        put("publish_year", b.publishYear)
        put("page_count", b.pageCount)
        put("pdf_location", b.pdfLocation)
        put("pdf_uri", b.uri)
        put("file_name", b.fileName)
        put("file_size", b.fileSize)
        put("sha256", b.sha256)
        put("folder", b.folder)
        put("last_page", b.lastPage)
        put("last_page_offset", b.lastPageOffset)
        put("details_edited", if (b.detailsEdited) 1 else 0)
        put("added_at", b.addedAt)
        put("last_opened_at", b.lastOpenedAt)
        put("search_text", b.searchText)
    }

    private fun Cursor.toBook(): Book {
        fun str(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
        fun int(name: String): Int? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getInt(it) }
        fun long(name: String): Long = getLong(getColumnIndexOrThrow(name))
        return Book(
            uuid = str("uuid")!!,
            uri = str("pdf_uri")!!,
            title = str("title")!!,
            authorFirstName = str("author_firstname"),
            authorLastName = str("author_lastname"),
            publishYear = int("publish_year"),
            pageCount = int("page_count") ?: 0,
            pdfLocation = str("pdf_location"),
            fileName = str("file_name"),
            fileSize = long("file_size"),
            sha256 = str("sha256"),
            folder = str("folder"),
            lastPage = int("last_page") ?: 0,
            lastPageOffset = getFloat(getColumnIndexOrThrow("last_page_offset")),
            detailsEdited = int("details_edited") == 1,
            addedAt = long("added_at"),
            lastOpenedAt = long("last_opened_at"),
        )
    }

    private companion object {
        /** Alter Name aus der Zeit als "EBF"; bleibt, damit bestehende Bibliotheken erhalten bleiben. */
        const val NAME = "ebf.db"
        const val VERSION = 2
    }
}
