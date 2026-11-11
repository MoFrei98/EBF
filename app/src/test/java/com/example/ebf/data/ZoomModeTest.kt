package com.example.ebf.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomModeTest {
    @Test
    fun `old settings are migrated`() {
        assertEquals(ZoomMode.WIDE, ZoomMode.fromStored("FIT_PAGE"))
        assertEquals(ZoomMode.CLOSE, ZoomMode.fromStored("CROP_MARGINS"))
    }

    @Test
    fun `default is wide`() {
        assertEquals(ZoomMode.WIDE, ZoomMode.fromStored(null))
        assertEquals(ZoomMode.WIDE, ZoomMode.fromStored("UNKNOWN"))
        assertEquals(ZoomMode.NORMAL, ZoomMode.fromStored("NORMAL"))
    }
}
