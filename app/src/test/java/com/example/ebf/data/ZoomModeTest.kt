package com.example.ebf.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomModeTest {
    @Test
    fun `old settings are migrated`() {
        assertEquals(ZoomMode.WIDE, ZoomMode.fromStored("FIT_PAGE"))
        assertEquals(ZoomMode.NORMAL, ZoomMode.fromStored("CROP_MARGINS"))
        assertEquals(ZoomMode.CLOSE, ZoomMode.fromStored("CLOSE"))
    }

    @Test
    fun `default is normal`() {
        assertEquals(ZoomMode.NORMAL, ZoomMode.fromStored(null))
        assertEquals(ZoomMode.NORMAL, ZoomMode.fromStored("UNKNOWN"))
        assertEquals(ZoomMode.NORMAL, ZoomMode.fromStored("NORMAL"))
    }
}
