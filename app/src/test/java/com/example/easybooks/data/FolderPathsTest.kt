package com.example.easybooks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPathsTest {

    @Test
    fun `parent, name and child`() {
        assertEquals("", FolderPaths.parent("Warhammer"))
        assertEquals("Warhammer", FolderPaths.parent("Warhammer/Horus Heresy"))
        assertEquals("Horus Heresy", FolderPaths.name("Warhammer/Horus Heresy"))
        assertEquals("Krimis", FolderPaths.child("", "Krimis"))
        assertEquals("Krimis/Neu", FolderPaths.child("Krimis", "Neu"))
    }

    @Test
    fun depth() {
        assertEquals(0, FolderPaths.depth(""))
        assertEquals(1, FolderPaths.depth("A"))
        assertEquals(3, FolderPaths.depth("A/B/C"))
    }

    @Test
    fun `inside checks whole segments`() {
        assertTrue(FolderPaths.isInside("A/B", "A"))
        assertTrue(FolderPaths.isInside("A", "A"))
        assertTrue(FolderPaths.isInside("A", ""))
        assertFalse(FolderPaths.isInside("AB", "A"))
        assertFalse(FolderPaths.isInside("A", "A/B"))
    }

    @Test
    fun `subfolders are direct children sorted by name`() {
        val all = listOf("b", "A", "A/x", "A/y/z", "c")
        assertEquals(listOf("A", "b", "c"), FolderPaths.subfolders(all, ""))
        assertEquals(listOf("A/x"), FolderPaths.subfolders(all, "A").filter { it != "A/y" })
    }

    @Test
    fun `tree order puts parents before children`() {
        val all = listOf("Zeta", "Alpha/Beta", "Alpha", "Alpha-2", "")
        assertEquals(listOf("Alpha", "Alpha/Beta", "Alpha-2", "Zeta"), FolderPaths.treeOrder(all))
    }

    @Test
    fun `clean name removes forbidden characters`() {
        assertEquals("Sci_Fi", FolderPaths.cleanName(" Sci/Fi "))
        assertEquals("", FolderPaths.cleanName(" .. "))
        assertEquals("Krimis", FolderPaths.cleanName("Krimis"))
    }
}
