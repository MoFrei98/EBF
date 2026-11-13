package com.example.easybooks.data

/** Automatisch erkannte Buch-Details; alles optional. */
data class BookMetadata(
    val title: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val year: Int? = null,
) {
    /** Füllt fehlende Angaben aus [other] auf. */
    fun orElse(other: BookMetadata) = BookMetadata(
        title = title ?: other.title,
        firstName = if (lastName != null) firstName else other.firstName,
        lastName = lastName ?: other.lastName,
        year = year ?: other.year,
    )
}

/**
 * Erkennt Titel, Autor und Erscheinungsjahr.
 *
 * - aus dem Dateinamen, z. B. "[Abnett_Dan]_Aufstieg_des_Horus(libcats.org).pdf"
 *   oder "Abnett, Dan - Aufstieg des Horus (2006).pdf"
 * - aus den Metadaten der PDF (Info-Dictionary und XMP), soweit sie unkomprimiert vorliegen
 */
object BookMetadataParser {

    private val siteTag = Regex("""\((?:www\.)?[\w-]+\.[a-z]{2,}\)""", RegexOption.IGNORE_CASE)
    private val bracketAuthor = Regex("""^\s*\[([^\]]+)]\s*[_\-–\s]*(.+)$""")
    private val commaAuthorDash = Regex("""^\s*([^,\-–]+,\s*[^\-–]+?)\s+[-–]\s+(.+)$""")
    private val yearInName = Regex("""[(\[]((?:1[5-9]|20)\d{2})[)\]]""")
    private val whitespace = Regex("""\s+""")
    private val junkTitle = Regex(
        """^(untitled|unbenannt|ohne titel|document|dokument|microsoft word\s*-.*|.*\.(docx?|pdf|indd|qxd|rtf|odt|txt))$""",
        RegexOption.IGNORE_CASE,
    )
    private val junkAuthor = Regex("""^(unknown|unbekannt|admin(istrator)?|user|owner|author|autor|\W+)$""", RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------- Dateiname

    fun fromFileName(fileName: String): BookMetadata {
        var name = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName.dropLast(4) else fileName
        name = name.replace(siteTag, " ")
        val year = yearInName.find(name)?.groupValues?.get(1)?.toIntOrNull()
        if (year != null) name = name.replace(yearInName, " ")

        bracketAuthor.find(name)?.let { m ->
            // "[Nachname_Vorname]_Titel" bzw. "[Nachname, Vorname] Titel"
            val (first, last) = splitBracketAuthor(m.groupValues[1])
            return BookMetadata(cleanTitle(m.groupValues[2]), first, last, year)
        }
        commaAuthorDash.find(name.replace('_', ' '))?.let { m ->
            val (first, last) = splitAuthor(m.groupValues[1])
            return BookMetadata(cleanTitle(m.groupValues[2]), first, last, year)
        }
        return BookMetadata(title = cleanTitle(name), year = year)
    }

    /** "[Abnett_Dan]" ist üblicherweise "Nachname_Vorname". */
    private fun splitBracketAuthor(raw: String): Pair<String?, String?> {
        val text = raw.trim()
        if (text.contains(',')) return splitAuthor(text)
        val parts = text.split('_', ' ').filter { it.isNotBlank() }
        return when (parts.size) {
            0 -> null to null
            1 -> null to parts[0]
            else -> parts.drop(1).joinToString(" ") to parts[0]
        }
    }

    // ---------------------------------------------------------------- Autor

    /**
     * Zerlegt einen Autor in Vor- und Nachname. "Dan Abnett" und "Abnett, Dan" ergeben
     * beide ("Dan", "Abnett"). Bei mehreren Autoren zählt der erste.
     */
    fun splitAuthor(raw: String?): Pair<String?, String?> {
        val first = raw
            ?.split(';', '&', '/', '|', '\n')
            ?.firstOrNull()
            ?.split(Regex("""\s+(and|und)\s+""", RegexOption.IGNORE_CASE))
            ?.firstOrNull()
            ?.replace('_', ' ')
            ?.replace(whitespace, " ")
            ?.trim()
            .orEmpty()
        if (first.isEmpty() || junkAuthor.matches(first) || looksLikeUserName(first)) return null to null
        if (first.contains(',')) {
            val last = first.substringBefore(',').trim()
            val given = first.substringAfter(',').trim()
            return given.ifEmpty { null } to last.ifEmpty { null }
        }
        val words = first.split(' ')
        return if (words.size == 1) null to words[0]
        else words.dropLast(1).joinToString(" ") to words.last()
    }

    /**
     * PDF-Programme tragen oft den Benutzernamen des Rechners als Autor ein, z. B. "jo"
     * oder "user01". Echte Namen haben Großbuchstaben und keine Ziffern oder @.
     */
    private fun looksLikeUserName(name: String): Boolean =
        name.none { it.isUpperCase() } || name.any { it.isDigit() || it == '@' }

    // ---------------------------------------------------------------- PDF

    /**
     * Liest Metadaten aus Rohdaten der PDF. Übergeben werden Anfang und Ende der Datei;
     * dort stehen XMP-Daten und das Info-Dictionary üblicherweise.
     */
    fun fromPdf(vararg chunks: ByteArray): BookMetadata {
        var result = BookMetadata()
        for (chunk in chunks) {
            // ISO-8859-1 bildet jedes Byte 1:1 auf ein Zeichen ab.
            val text = String(chunk, Charsets.ISO_8859_1)
            result = result.orElse(fromXmp(text)).orElse(fromInfo(text))
        }
        return result
    }

    private fun fromInfo(text: String): BookMetadata {
        val title = pdfString(text, "Title")?.takeIf(::goodTitle)
        val (first, last) = splitAuthor(pdfString(text, "Author"))
        return BookMetadata(title, first, last)
    }

    private fun fromXmp(text: String): BookMetadata {
        fun tag(name: String): String? {
            val block = Regex("""<$name\b[^>]*>(.*?)</$name>""", RegexOption.DOT_MATCHES_ALL).find(text)
                ?.groupValues?.get(1) ?: return null
            val li = Regex("""<rdf:li\b[^>]*>(.*?)</rdf:li>""", RegexOption.DOT_MATCHES_ALL).find(block)
            val value = (li?.groupValues?.get(1) ?: block).replace(Regex("<[^>]+>"), "")
            return xmlUnescape(utf8(value)).trim().ifEmpty { null }
        }
        val title = tag("dc:title")?.takeIf(::goodTitle)
        val (first, last) = splitAuthor(tag("dc:creator"))
        val year = tag("prism:publicationDate")?.take(4)?.toIntOrNull()?.takeIf { it in 1450..2100 }
        return BookMetadata(title, first, last, year)
    }

    /** Wert eines Eintrags wie `/Author (Dan Abnett)` oder `/Author <FEFF0044…>`. */
    internal fun pdfString(text: String, key: String): String? {
        val match = Regex("""/$key\s*([(<])""").find(text) ?: return null
        val start = match.groups[1]!!.range.first
        val bytes = if (text[start] == '(') literalBytes(text, start) else hexBytes(text, start)
        return bytes?.let(::decodePdfText)?.replace(whitespace, " ")?.trim()?.ifEmpty { null }
    }

    private fun literalBytes(text: String, open: Int): ByteArray? {
        val out = ArrayList<Byte>()
        var depth = 0
        var i = open
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    val n = text[i + 1]
                    when (n) {
                        'n' -> out += '\n'.code.toByte()
                        'r' -> out += '\r'.code.toByte()
                        't' -> out += '\t'.code.toByte()
                        'b' -> out += 8
                        'f' -> out += 12
                        '\r', '\n' -> Unit // Zeilenfortsetzung
                        in '0'..'7' -> {
                            var j = i + 1
                            var value = 0
                            while (j < text.length && j < i + 4 && text[j] in '0'..'7') {
                                value = value * 8 + (text[j] - '0')
                                j++
                            }
                            out += value.toByte()
                            i = j
                            continue
                        }
                        else -> out += n.code.toByte()
                    }
                    i += 2
                    continue
                }
                c == '(' -> { if (depth > 0) out += c.code.toByte(); depth++ }
                c == ')' -> { depth--; if (depth == 0) return out.toByteArray(); out += c.code.toByte() }
                else -> out += c.code.toByte()
            }
            i++
        }
        return null
    }

    private fun hexBytes(text: String, open: Int): ByteArray? {
        val close = text.indexOf('>', open)
        if (close < 0) return null
        val hex = text.substring(open + 1, close).filter { it.isLetterOrDigit() }.let {
            if (it.length % 2 == 1) it + "0" else it
        }
        return runCatching { ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }

    /** UTF-16 mit BOM oder PDFDocEncoding (für Text praktisch Latin-1). */
    private fun decodePdfText(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.ISO_8859_1)
    }

    /** XMP ist UTF-8, wurde aber als Latin-1 eingelesen. */
    private fun utf8(latin1: String) = String(latin1.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)

    private fun xmlUnescape(s: String) = s
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: "" }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")

    // ---------------------------------------------------------------- Hilfen

    private fun goodTitle(title: String): Boolean =
        title.length >= 2 && !junkTitle.matches(title.trim()) && title.any { it.isLetter() }

    private fun cleanTitle(raw: String): String? =
        raw.replace('_', ' ').replace(whitespace, " ").trim(' ', '-', '–', '.', ',').ifEmpty { null }
}
