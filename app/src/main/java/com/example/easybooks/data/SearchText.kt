package com.example.easybooks.data

import java.text.Normalizer
import java.util.Locale

/** Vergleich für die Suche: Groß-/Kleinschreibung, Umlaute und Akzente spielen keine Rolle. */
object SearchText {
    private val marks = Regex("""\p{M}+""")
    private val separators = Regex("""[_\-.]+""")
    private val whitespace = Regex("""\s+""")

    fun normalize(text: String): String {
        val lower = text.lowercase(Locale.GERMAN)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(separators, " ")
            .replace(whitespace, " ")
            .trim()
    }

    /** Alle Wörter der Suche müssen in mindestens einem der Felder vorkommen. */
    fun matches(query: String, vararg fields: String?): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = fields.filterNotNull().joinToString(" ") { normalize(it) }
        return words.all { it in haystack }
    }
}

/** Lesestand eines Buchs. */
enum class ReadState { UNREAD, STARTED, FINISHED }

/** Filter der Bibliothek nach Lesestand. */
enum class BookFilter(private val state: ReadState?) {
    ALL(null),
    UNREAD(ReadState.UNREAD),
    STARTED(ReadState.STARTED),
    FINISHED(ReadState.FINISHED);

    fun accepts(book: Book): Boolean = state == null || book.readState == state
}
