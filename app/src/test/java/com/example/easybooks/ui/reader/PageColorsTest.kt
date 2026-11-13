package com.example.easybooks.ui.reader

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageColorsTest {

    private val paper = Color(0xFF1F1D1A)
    private val ink = Color(0xFFDCD5CA)
    private val matrix = PageColors.nightMatrix(paper, ink).values

    /** Wendet die 4x5-Matrix auf eine Farbe mit Kanälen 0..255 an. */
    private fun apply(r: Float, g: Float, b: Float): FloatArray = FloatArray(3) { row ->
        val m = row * 5
        (matrix[m] * r + matrix[m + 1] * g + matrix[m + 2] * b + matrix[m + 3] * 255f + matrix[m + 4])
            .coerceIn(0f, 255f)
    }

    private fun assertColor(expected: Color, actual: FloatArray) {
        assertEquals(expected.red * 255f, actual[0], 1f)
        assertEquals(expected.green * 255f, actual[1], 1f)
        assertEquals(expected.blue * 255f, actual[2], 1f)
    }

    @Test
    fun `white paper becomes dark paper`() = assertColor(paper, apply(255f, 255f, 255f))

    @Test
    fun `black text becomes light ink`() = assertColor(ink, apply(0f, 0f, 0f))

    @Test
    fun `red keeps its hue`() {
        val (r, g, b) = apply(200f, 30f, 30f).toList()
        assertTrue("rot bleibt dominant: r=$r g=$g b=$b", r > g + 40 && r > b + 40)
    }

    @Test
    fun `alpha is unchanged`() {
        assertEquals(1f, matrix[18], 0f)
        assertEquals(0f, matrix[19], 0f)
    }
}
