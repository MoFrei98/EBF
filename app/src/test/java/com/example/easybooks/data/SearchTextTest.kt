package com.example.easybooks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun `case and umlauts do not matter`() {
        assertTrue(SearchText.matches("aufstieg", "Aufstieg des Horus"))
        assertTrue(SearchText.matches("Maerchen", "Märchen"))
        assertTrue(SearchText.matches("märchen", "Maerchen"))
        assertTrue(SearchText.matches("strasse", "Straße"))
        assertTrue(SearchText.matches("cafe", "Café"))
    }

    @Test
    fun `all words must match, in any field`() {
        assertTrue(SearchText.matches("horus abnett", "Aufstieg des Horus", "[Abnett_Dan]_Aufstieg.pdf"))
        assertFalse(SearchText.matches("horus tolkien", "Aufstieg des Horus", null))
    }

    @Test
    fun `underscores in file names count as spaces`() {
        assertTrue(SearchText.matches("der leuchtturm", null, "Der_Leuchtturm.pdf"))
    }

    @Test
    fun `empty query matches everything`() {
        assertTrue(SearchText.matches("  ", "x"))
    }

    @Test
    fun `read state`() {
        fun state(page: Int, count: Int) = Book(uri = "u", title = "t", pageCount = count, lastPage = page).readState
        assertEquals(ReadState.UNREAD, state(0, 300))
        assertEquals(ReadState.UNREAD, state(0, 0))
        assertEquals(ReadState.STARTED, state(1, 300))
        assertEquals(ReadState.STARTED, state(298, 300))
        assertEquals(ReadState.FINISHED, state(299, 300))
        assertEquals(ReadState.UNREAD, state(0, 1))
    }

    @Test
    fun `filter accepts matching books`() {
        val started = Book(uri = "u", title = "t", pageCount = 10, lastPage = 4)
        assertTrue(BookFilter.ALL.accepts(started))
        assertTrue(BookFilter.STARTED.accepts(started))
        assertFalse(BookFilter.FINISHED.accepts(started))
        assertFalse(BookFilter.UNREAD.accepts(started))
    }
}
