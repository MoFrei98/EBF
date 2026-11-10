package com.example.ebf.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class Book(
    /** content:// URI der Original-PDF oder file:// URI einer internen Kopie. */
    val uri: String,
    val title: String,
    val pageCount: Int,
    /** Zuletzt gelesene Seite (0-basiert). */
    val lastPage: Int = 0,
    /** Dateiname und Größe der Original-PDF, um Duplikate zu erkennen. */
    val fileName: String? = null,
    val fileSize: Long = -1,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = addedAt,
) {
    /** Stabile ID für Dateinamen (Cover, interne Kopie). */
    val key: String get() = sha1(uri)

    /** Lesefortschritt von 0 bis 1. */
    val progress: Float get() = if (pageCount <= 0) 0f else (lastPage + 1f) / pageCount

    companion object {
        private val siteTag = Regex("""\((?:www\.)?[\w-]+\.[a-z]{2,}\)""", RegexOption.IGNORE_CASE)
        private val whitespace = Regex("""\s+""")

        /** Macht aus einem Dateinamen einen lesbaren Titel. */
        fun titleFromFileName(fileName: String): String {
            val withoutExtension =
                if (fileName.endsWith(".pdf", ignoreCase = true)) fileName.dropLast(4) else fileName
            val title = withoutExtension
                .replace(siteTag, " ")
                .replace('_', ' ')
                .replace(whitespace, " ")
                .trim()
            return title.ifEmpty { fileName }
        }
    }
}

internal fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

internal object BookJson {
    fun encode(books: List<Book>): String = JSONArray().apply {
        books.forEach { book ->
            put(
                JSONObject()
                    .put("uri", book.uri)
                    .put("title", book.title)
                    .put("pageCount", book.pageCount)
                    .put("lastPage", book.lastPage)
                    .put("fileName", book.fileName ?: "")
                    .put("fileSize", book.fileSize)
                    .put("addedAt", book.addedAt)
                    .put("lastOpenedAt", book.lastOpenedAt)
            )
        }
    }.toString()

    fun decode(json: String?): List<Book> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = o.optString("uri")
            if (uri.isEmpty()) return@mapNotNull null
            val addedAt = o.optLong("addedAt", System.currentTimeMillis())
            Book(
                uri = uri,
                title = o.optString("title", "PDF"),
                pageCount = o.optInt("pageCount", 0),
                lastPage = o.optInt("lastPage", 0),
                fileName = o.optString("fileName").ifEmpty { null },
                fileSize = o.optLong("fileSize", -1),
                addedAt = addedAt,
                lastOpenedAt = o.optLong("lastOpenedAt", addedAt),
            )
        }
    }
}
