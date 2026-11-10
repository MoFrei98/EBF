package com.example.ebf.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Farbfilter für die gerenderten PDF-Seiten.
 *
 * Für den Dark Mode wird die Helligkeit umgekehrt, der Farbton bleibt aber erhalten
 * (Invertierung + 180° Farbtondrehung). Weißes Papier wird so dunkel und schwarze
 * Schrift hell, während z. B. ein rotes Bild rötlich bleibt.
 */
internal object PageColors {
    // Luminanz-Gewichte (wie in der Standard-Farbtonmatrix).
    private const val LR = 0.213f
    private const val LG = 0.715f
    private const val LB = 0.072f

    /** Leicht warmes Papier im hellen Design: Weiß wird zu #FFFDF8. */
    val day: ColorFilter = ColorFilter.colorMatrix(
        ColorMatrix().apply { setToScale(1f, 0.992f, 0.973f, 1f) }
    )

    /**
     * Dunkle Seite: Weiß wird zu [paper], Schwarz zu [ink].
     * Gedämpfte Werte statt reinem Schwarz/Weiß sind angenehmer für die Augen.
     */
    fun night(paper: Color, ink: Color): ColorFilter = ColorFilter.colorMatrix(nightMatrix(paper, ink))

    internal fun nightMatrix(paper: Color, ink: Color): ColorMatrix {
        // Invertieren mit erhaltenem Farbton: M = I - 2L, verschoben um 255.
        val base = arrayOf(
            floatArrayOf(1 - 2 * LR, -2 * LG, -2 * LB),
            floatArrayOf(-2 * LR, 1 - 2 * LG, -2 * LB),
            floatArrayOf(-2 * LR, -2 * LG, 1 - 2 * LB),
        )
        val low = floatArrayOf(paper.red, paper.green, paper.blue).map { it * 255f }
        val high = floatArrayOf(ink.red, ink.green, ink.blue).map { it * 255f }
        val values = FloatArray(20)
        for (row in 0..2) {
            // Ergebnis von 0..255 auf low..high zusammenstauchen.
            val k = (high[row] - low[row]) / 255f
            for (col in 0..2) values[row * 5 + col] = base[row][col] * k
            values[row * 5 + 4] = low[row] + 255f * k
        }
        values[18] = 1f // Alpha unverändert
        return ColorMatrix(values)
    }
}
