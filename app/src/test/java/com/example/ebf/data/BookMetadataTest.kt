package com.example.ebf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookMetadataTest {

    @Test
    fun `bracket author in file name`() {
        val m = BookMetadataParser.fromFileName("[Abnett_Dan]_Aufstieg_des_Horus(libcats.org).pdf")
        assertEquals(BookMetadata("Aufstieg des Horus", "Dan", "Abnett", null), m)
    }

    @Test
    fun `comma author with dash and year in file name`() {
        val m = BookMetadataParser.fromFileName("Abnett, Dan - Aufstieg des Horus (2006).pdf")
        assertEquals(BookMetadata("Aufstieg des Horus", "Dan", "Abnett", 2006), m)
    }

    @Test
    fun `plain file name has no author`() {
        val m = BookMetadataParser.fromFileName("Der_Leuchtturm.pdf")
        assertEquals(BookMetadata("Der Leuchtturm", null, null, null), m)
    }

    @Test
    fun `author name forms`() {
        assertEquals("Dan" to "Abnett", BookMetadataParser.splitAuthor("Dan Abnett"))
        assertEquals("Dan" to "Abnett", BookMetadataParser.splitAuthor("Abnett, Dan"))
        assertEquals("Johann Wolfgang von" to "Goethe", BookMetadataParser.splitAuthor("Johann Wolfgang von Goethe"))
        assertEquals("Terry" to "Pratchett", BookMetadataParser.splitAuthor("Terry Pratchett; Neil Gaiman"))
        assertEquals("Terry" to "Pratchett", BookMetadataParser.splitAuthor("Terry Pratchett and Neil Gaiman"))
        assertEquals(null to "Homer", BookMetadataParser.splitAuthor("Homer"))
        assertEquals(null to null, BookMetadataParser.splitAuthor("Administrator"))
        assertEquals(null to null, BookMetadataParser.splitAuthor("  "))
    }

    @Test
    fun `info dictionary with literal strings`() {
        val pdf = "1 0 obj << /Title (Aufstieg des Horus) /Author (Dan Abnett) /Producer (x) >> endobj"
        val m = BookMetadataParser.fromPdf(pdf.toByteArray(Charsets.ISO_8859_1))
        assertEquals(BookMetadata("Aufstieg des Horus", "Dan", "Abnett", null), m)
    }

    @Test
    fun `info dictionary with escapes and utf16 hex`() {
        // "Märchen (neu)" als Literal mit Oktal-Escape für ä (Latin-1 0xE4) und Klammern
        val literal = "/Title (M\\344rchen \\(neu\\))"
        assertEquals("Märchen (neu)", BookMetadataParser.pdfString(literal, "Title"))
        // "Jörg" als UTF-16BE mit BOM
        val hex = "/Author <FEFF004A00F60072 0067>"
        assertEquals("Jörg", BookMetadataParser.pdfString(hex, "Author"))
    }

    @Test
    fun `junk titles are ignored`() {
        val pdf = "<< /Title (Microsoft Word - entwurf.docx) /Author (Unknown) >>"
        assertEquals(BookMetadata(), BookMetadataParser.fromPdf(pdf.toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun `xmp metadata wins and is utf8`() {
        val xmp = """
            <x:xmpmeta><rdf:RDF><rdf:Description>
              <dc:title><rdf:Alt><rdf:li xml:lang="x-default">Die Straße</rdf:li></rdf:Alt></dc:title>
              <dc:creator><rdf:Seq><rdf:li>Jörg M&#252;ller</rdf:li></rdf:Seq></dc:creator>
              <prism:publicationDate>2019-03-01</prism:publicationDate>
            </rdf:Description></rdf:RDF></x:xmpmeta>
            << /Title (Anderer Titel) >>
        """.trimIndent()
        val m = BookMetadataParser.fromPdf(xmp.toByteArray(Charsets.UTF_8))
        assertEquals(BookMetadata("Die Straße", "Jörg", "Müller", 2019), m)
    }

    @Test
    fun `real world word export falls back to file name`() {
        // So steht es im Testbuch: Word-Export als Titel, Benutzername als Autor.
        val pdf = "<< /Title (Microsoft Word - Warhammer _40K_ - Dan Abnett - DGB 01 - Aufstieg des Horus) /Author (jo) >>"
        val fromPdf = BookMetadataParser.fromPdf(pdf.toByteArray(Charsets.ISO_8859_1))
        assertEquals(BookMetadata(), fromPdf)
        val fromName = BookMetadataParser.fromFileName("[Abnett_Dan]_Aufstieg_des_Horus(libcats.org).pdf")
        assertEquals(BookMetadata("Aufstieg des Horus", "Dan", "Abnett", null), fromPdf.orElse(fromName))
    }

    @Test
    fun `user names are not authors`() {
        assertEquals(null to null, BookMetadataParser.splitAuthor("jo"))
        assertEquals(null to null, BookMetadataParser.splitAuthor("user01"))
        assertEquals(null to null, BookMetadataParser.splitAuthor("max@example.org"))
    }

    @Test
    fun `pdf metadata is completed from file name`() {
        val fromPdf = BookMetadata(title = "Aufstieg des Horus")
        val fromName = BookMetadataParser.fromFileName("[Abnett_Dan]_Horus.pdf")
        assertEquals(BookMetadata("Aufstieg des Horus", "Dan", "Abnett", null), fromPdf.orElse(fromName))
    }

    @Test
    fun `absolute paths from document ids`() {
        val ext = "com.android.externalstorage.documents"
        assertEquals(
            "/storage/emulated/0/Documents/EBF/Warhammer/Horus.pdf",
            PdfPaths.fromDocumentId(ext, "primary:Documents/EBF/Warhammer/Horus.pdf"),
        )
        assertEquals("/storage/1A2B-3C4D/Bücher/a.pdf", PdfPaths.fromDocumentId(ext, "1A2B-3C4D:Bücher/a.pdf"))
        assertEquals(
            "/storage/emulated/0/Download/a.pdf",
            PdfPaths.fromDocumentId("com.android.providers.downloads.documents", "raw:/storage/emulated/0/Download/a.pdf"),
        )
        assertNull(PdfPaths.fromDocumentId("com.android.providers.downloads.documents", "msf:42"))
        assertNull(PdfPaths.fromDocumentId("com.example.other", "x"))
    }

    @Test
    fun `search text contains author and year`() {
        val book = Book(uri = "u", title = "Aufstieg des Horus", authorFirstName = "Dan", authorLastName = "Abnett", publishYear = 2006, pageCount = 1)
        assertEquals("aufstieg des horus dan abnett 2006", book.searchText)
        assertEquals("Dan Abnett", book.author)
    }
}
