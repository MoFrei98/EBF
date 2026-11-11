package com.example.ebf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    /** Weiße Seite mit einem schwarzen Rechteck von (x0,y0) bis exklusive (x1,y1). */
    private fun page(width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int) =
        IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if (x in x0 until x1 && y in y0 until y1) black else white
        }

    @Test
    fun `finds content rectangle`() {
        val bounds = CropMath.findContentBounds(page(100, 200, 10, 20, 90, 180), 100, 200)!!
        assertEquals(Crop(0.1f, 0.1f, 0.9f, 0.9f), bounds)
    }

    @Test
    fun `blank page has no bounds`() {
        assertNull(CropMath.findContentBounds(IntArray(50 * 50) { white }, 50, 50))
    }

    @Test
    fun `light grey counts as margin`() {
        val pixels = IntArray(10 * 10) { 0xFFF0F0F0.toInt() }
        assertNull(CropMath.findContentBounds(pixels, 10, 10))
    }

    @Test
    fun `text page is not an image`() {
        val analysis = CropMath.analyze(page(100, 200, 10, 20, 90, 30), 100, 200)
        assertTrue(analysis.luminance > 0.9f)
        assertTrue(!analysis.isImage)
    }

    @Test
    fun `dark cover is an image`() {
        val analysis = CropMath.analyze(page(100, 200, 0, 0, 100, 160), 100, 200)
        assertTrue(analysis.isImage)
    }

    @Test
    fun `yellowish scanned paper is not an image`() {
        val scanned = IntArray(100 * 100) { 0xFFE8DFC8.toInt() }
        assertTrue(!CropMath.analyze(scanned, 100, 100).isImage)
    }

    @Test
    fun `crop never cuts page content`() {
        val wideTable = Crop(0.05f, 0.1f, 0.95f, 0.9f)
        val typical = Crop(0.15f, 0.1f, 0.85f, 0.9f)
        val crop = CropMath.cropFor(wideTable, typical)
        assertTrue(crop.left <= wideTable.left)
        assertTrue(crop.right >= wideTable.right)
    }

    @Test
    fun `short pages are not zoomed more than typical pages`() {
        val heading = Crop(0.4f, 0.4f, 0.6f, 0.45f)
        val typical = Crop(0.15f, 0.1f, 0.85f, 0.9f)
        assertEquals(CropMath.cropFor(typical, typical), CropMath.cropFor(heading, typical))
    }

    @Test
    fun `page without margins stays full`() {
        assertEquals(Crop.FULL, CropMath.cropFor(Crop(0.005f, 0f, 1f, 0.995f), null))
    }

    @Test
    fun `blank page without typical bounds stays full`() {
        assertEquals(Crop.FULL, CropMath.cropFor(null, null))
    }

    @Test
    fun `padding stays within page`() {
        val crop = CropMath.cropFor(Crop(0.01f, 0.2f, 0.7f, 0.8f), null)
        assertEquals(0f, crop.left, 0.0001f)
        assertEquals(0.72f, crop.right, 0.0001f)
    }

    @Test
    fun `tight padding keeps a little air but never cuts content`() {
        val content = Crop(0.1f, 0.1f, 0.9f, 0.9f)
        val tight = CropMath.cropFor(content, content, CropMath.PADDING_TIGHT)
        val normal = CropMath.cropFor(content, content)
        assertTrue(tight.width < normal.width)
        assertTrue(tight.left < content.left && tight.right > content.right)
    }

    @Test
    fun `halfway lies between full page and crop`() {
        assertEquals(Crop(0.05f, 0.1f, 0.95f, 0.85f), Crop(0.1f, 0.2f, 0.9f, 0.7f).halfway())
        assertEquals(Crop.FULL, Crop.FULL.halfway())
    }

    @Test
    fun `typical bounds use median per parity`() {
        val samples = listOf(
            2 to Crop(0.2f, 0.1f, 0.9f, 0.9f),
            4 to Crop(0.2f, 0.1f, 0.9f, 0.9f),
            6 to Crop(0.0f, 0.0f, 1.0f, 1.0f), // Ausreißer (z. B. Bild)
            1 to Crop(0.1f, 0.1f, 0.8f, 0.9f),
            3 to Crop(0.1f, 0.1f, 0.8f, 0.9f),
            5 to Crop(0.1f, 0.1f, 0.8f, 0.9f),
        )
        val typical = TypicalBounds.from(samples)
        assertEquals(Crop(0.2f, 0.1f, 0.9f, 0.9f), typical.forPage(10))
        assertEquals(Crop(0.1f, 0.1f, 0.8f, 0.9f), typical.forPage(11))
    }

    @Test
    fun `typical bounds fall back to all pages`() {
        val typical = TypicalBounds.from(listOf(1 to Crop(0.1f, 0.1f, 0.9f, 0.9f)))
        assertEquals(Crop(0.1f, 0.1f, 0.9f, 0.9f), typical.forPage(4))
    }
}
