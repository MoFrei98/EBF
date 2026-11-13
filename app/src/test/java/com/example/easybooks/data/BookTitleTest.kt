package com.example.easybooks.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookTitleTest {

    @Test
    fun `removes extension, site tag and underscores`() {
        assertEquals(
            "[Abnett Dan] Aufstieg des Horus",
            Book.titleFromFileName("[Abnett_Dan]_Aufstieg_des_Horus(libcats.org).pdf"),
        )
    }

    @Test
    fun `keeps normal titles`() {
        assertEquals("Mein Buch", Book.titleFromFileName("Mein Buch.PDF"))
    }

    @Test
    fun `keeps meaningful parentheses`() {
        assertEquals("Roman (Band 2)", Book.titleFromFileName("Roman (Band 2).pdf"))
    }

    @Test
    fun `falls back to file name`() {
        assertEquals(".pdf", Book.titleFromFileName(".pdf"))
    }

    @Test
    fun `progress counts the current page`() {
        assertEquals(0.5f, Book(uri = "x", title = "t", pageCount = 10, lastPage = 4).progress, 0.0001f)
    }
}
