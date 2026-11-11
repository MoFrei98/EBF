package com.example.ebf.data

import org.json.JSONArray
import java.security.MessageDigest
import java.util.UUID

data class Book(
    /** Eindeutige ID. Ändert sich nie, auch nicht beim Verschieben oder Umbenennen der Datei. */
    val uuid: String = UUID.randomUUID().toString(),
    /** Android-Adresse der PDF (content:// oder file://). Darüber liest die App die Datei. */
    val uri: String,
    val title: String,
    val authorFirstName: String? = null,
    val authorLastName: String? = null,
    val publishYear: Int? = null,
    val pageCount: Int,
    /** Absoluter Pfad, z. B. /storage/emulated/0/Documents/EBF/Buch.pdf (zur Anzeige). */
    val pdfLocation: String? = null,
    /** Dateiname und Größe der PDF. */
    val fileName: String? = null,
    val fileSize: Long = -1,
    /** SHA-256 des Dateiinhalts; erkennt dieselbe Datei auch nach Umbenennen/Verschieben. */
    val sha256: String? = null,
    /**
     * Ordner im Bibliotheksordner auf dem Gerät ("" = oberste Ebene).
     * `null`, wenn die PDF woanders liegt (z. B. in Downloads).
     */
    val folder: String? = null,
    /** Zuletzt gelesene Seite (0-basiert). */
    val lastPage: Int = 0,
    /** Titel/Autor/Jahr wurden von Hand bearbeitet und werden nicht mehr automatisch überschrieben. */
    val detailsEdited: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = addedAt,
) {
    /** Autor zur Anzeige, z. B. "Dan Abnett", oder `null`. */
    val author: String?
        get() = listOfNotNull(authorFirstName, authorLastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }

    /** Normalisierter Text für die Suche in der Datenbank. */
    val searchText: String
        get() = SearchText.normalize(
            listOfNotNull(title, fileName, authorFirstName, authorLastName, publishYear?.toString())
                .joinToString(" ")
        )

    /** Liegt die PDF im Bibliotheksordner auf dem Gerät? */
    val inLibraryFolder: Boolean get() = folder != null

    /** Ungelesen (Seite 1), angefangen oder gelesen (letzte Seite erreicht). */
    val readState: ReadState
        get() = when {
            pageCount > 1 && lastPage >= pageCount - 1 -> ReadState.FINISHED
            lastPage <= 0 -> ReadState.UNREAD
            else -> ReadState.STARTED
        }

    /** Lesefortschritt von 0 bis 1. */
    val progress: Float get() = if (pageCount <= 0) 0f else (lastPage + 1f) / pageCount

    companion object {
        private val siteTag = Regex("""\((?:www\.)?[\w-]+\.[a-z]{2,}\)""", RegexOption.IGNORE_CASE)
        private val whitespace = Regex("""\s+""")

        /** Macht aus einem Dateinamen einen lesbaren Titel (ohne Autor-Erkennung). */
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

/** Liest die Bibliothek aus dem alten JSON-Format (vor der Datenbank), nur für die Übernahme. */
internal object LegacyBookJson {
    /** Bücher im alten Format; [Book.uuid] wird neu vergeben. */
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
                folder = if (o.isNull("folder")) null else o.optString("folder"),
                addedAt = addedAt,
                lastOpenedAt = o.optLong("lastOpenedAt", addedAt),
            )
        }
    }

    fun decodeFolders(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).ifEmpty { null } }
    }
}
