package com.example.ebf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlTest {

    private val w = 1000f
    private val h = 1400f

    /** x-Position des freien Rands in der obersten und untersten Zeile. */
    private fun edges(grab: Float, progress: Float = 0.25f, top: Float = 0f, bottom: Float = h): Pair<Float, Float> {
        val m = PageCurl.project(w, h, progress, grab, top, bottom)
        return m.x[m.index(m.cols, 0)] to m.x[m.index(m.cols, m.rows)]
    }

    @Test
    fun `grabbing the top lifts the top corner first`() {
        val (top, bottom) = edges(grab = 0.05f)
        assertTrue("oben weiter links: top=$top bottom=$bottom", top < bottom - 50f)
    }

    @Test
    fun `grabbing the bottom lifts the bottom corner first`() {
        val (top, bottom) = edges(grab = 0.95f)
        assertTrue("unten weiter links: top=$top bottom=$bottom", bottom < top - 50f)
    }

    @Test
    fun `grabbing the middle keeps the edge straight`() {
        val (top, bottom) = edges(grab = 0.5f)
        assertEquals(top, bottom, 1f)
    }

    @Test
    fun `tall page uses the visible part`() {
        // Seite doppelt so hoch wie sichtbar, oben sichtbar: Griff oben im Sichtbaren
        // muss die obere Ecke heben, nicht die untere.
        val m = PageCurl.project(w, h * 2, 0.25f, 0.05f, 0f, h)
        val topEdge = m.x[m.index(m.cols, 0)]
        val visibleBottomEdge = m.x[m.index(m.cols, m.rows / 2)]
        assertTrue("top=$topEdge visibleBottom=$visibleBottomEdge", topEdge < visibleBottomEdge - 50f)
    }

    @Test
    fun `page is straight again near the end`() {
        val (top, bottom) = edges(grab = 0.05f, progress = 0.97f)
        assertEquals(top, bottom, 15f)
    }
}
