package com.example.ebf.pdf

import android.graphics.RectF

/** Ausschnitt einer Seite, angegeben als Anteile von 0 bis 1. */
data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun union(other: Crop) = Crop(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    fun expand(amount: Float) = Crop(
        left = (left - amount).coerceAtLeast(0f),
        top = (top - amount).coerceAtLeast(0f),
        right = (right + amount).coerceAtMost(1f),
        bottom = (bottom + amount).coerceAtMost(1f),
    )

    /** Ausschnitt genau zwischen der ganzen Seite und diesem Ausschnitt. */
    fun halfway() = Crop(
        left = left / 2f,
        top = top / 2f,
        right = (1f + right) / 2f,
        bottom = (1f + bottom) / 2f,
    )

    fun toRectF() = RectF(left, top, right, bottom)

    companion object {
        val FULL = Crop(0f, 0f, 1f, 1f)
    }
}

/** Ergebnis der Analyse einer kleinen Seitenvorschau. */
data class PageAnalysis(
    /** Bereich mit Inhalt oder `null` für eine leere Seite. */
    val bounds: Crop?,
    /** Mittlere Helligkeit von 0 (schwarz) bis 1 (weiß). */
    val luminance: Float,
) {
    /**
     * Überwiegend dunkles Bild (z. B. Cover oder Foto). Solche Seiten werden im
     * Dark Mode nicht invertiert, sonst sähen sie aus wie ein Fotonegativ.
     * Textseiten – auch eingescannte mit gelblichem Papier – sind deutlich heller.
     */
    val isImage: Boolean get() = luminance < IMAGE_LUMINANCE_LIMIT

    private companion object {
        const val IMAGE_LUMINANCE_LIMIT = 0.55f
    }
}

/**
 * Typische Inhaltsgrenzen eines Dokuments, getrennt nach geraden und ungeraden Seiten
 * (Bücher haben oft gespiegelte Ränder).
 */
class TypicalBounds(private val even: Crop?, private val odd: Crop?, private val all: Crop?) {
    fun forPage(page: Int): Crop? = (if (page % 2 == 0) even else odd) ?: all

    companion object {
        private const val MIN_SAMPLES_PER_PARITY = 3

        fun from(samples: List<Pair<Int, Crop>>): TypicalBounds {
            val even = samples.filter { it.first % 2 == 0 }.map { it.second }
            val odd = samples.filter { it.first % 2 == 1 }.map { it.second }
            return TypicalBounds(
                even = if (even.size >= MIN_SAMPLES_PER_PARITY) median(even) else null,
                odd = if (odd.size >= MIN_SAMPLES_PER_PARITY) median(odd) else null,
                all = if (samples.isNotEmpty()) median(samples.map { it.second }) else null,
            )
        }

        private fun median(crops: List<Crop>): Crop {
            fun mid(values: List<Float>) = values.sorted()[values.size / 2]
            return Crop(
                left = mid(crops.map { it.left }),
                top = mid(crops.map { it.top }),
                right = mid(crops.map { it.right }),
                bottom = mid(crops.map { it.bottom }),
            )
        }
    }
}

object CropMath {
    /** Etwas Luft um den Inhalt, damit nichts am Rand klebt. */
    const val PADDING = 0.02f

    /** Pixel heller als dieser Wert (in allen Kanälen) gelten als weißer Rand. */
    private const val WHITE_THRESHOLD = 232

    /**
     * Sucht den Bereich mit Inhalt (nicht weißen Pixeln).
     *
     * @param pixels ARGB-Pixel, zeilenweise
     * @return Inhaltsgrenzen als Anteile oder `null` für eine leere Seite
     */
    fun findContentBounds(pixels: IntArray, width: Int, height: Int): Crop? =
        analyze(pixels, width, height).bounds

    /** Inhaltsgrenzen und mittlere Helligkeit einer Seitenvorschau. */
    fun analyze(pixels: IntArray, width: Int, height: Int): PageAnalysis {
        var luminanceSum = 0.0
        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val c = pixels[row + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                luminanceSum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                if (r < WHITE_THRESHOLD || g < WHITE_THRESHOLD || b < WHITE_THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val luminance = (luminanceSum / (255.0 * maxOf(1, width * height))).toFloat()
        if (maxX < 0) return PageAnalysis(bounds = null, luminance = luminance)
        val bounds = Crop(
            left = minX / width.toFloat(),
            top = minY / height.toFloat(),
            right = (maxX + 1) / width.toFloat(),
            bottom = (maxY + 1) / height.toFloat(),
        )
        return PageAnalysis(bounds, luminance)
    }

    /**
     * Ausschnitt für "Ränder entfernen".
     *
     * Der eigene Inhalt der Seite wird mit den typischen Grenzen des Dokuments
     * vereinigt. Dadurch wird nie Inhalt abgeschnitten, und Seiten mit wenig Text
     * (z. B. Kapitelanfänge) werden nicht stärker vergrößert als normale Seiten.
     */
    fun cropFor(page: Crop?, typical: Crop?): Crop {
        val base = when {
            page != null && typical != null -> page.union(typical)
            else -> page ?: typical ?: return Crop.FULL
        }
        val padded = base.expand(PADDING)
        // Fast kein Rand vorhanden: lieber die ganze Seite zeigen.
        if (padded.width > 0.97f && padded.height > 0.97f) return Crop.FULL
        return padded
    }
}
