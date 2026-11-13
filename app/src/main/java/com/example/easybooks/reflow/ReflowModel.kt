package com.example.easybooks.reflow

/*
 * Neu umbrochener Text für die Schriftgröße "Groß".
 *
 * Die PDF-Seite wird als Bild analysiert: Zeilen, Wörter, Trennstriche und Bilder.
 * Danach werden die Wortbilder vergrößert in kürzere Zeilen gesetzt, wie in einem
 * E-Book. Das funktioniert ohne Textebene (auch bei Scans) und behält Schriftart,
 * Kursivschrift usw. bei. Alle Koordinaten der Analyse sind Pixel der Analyse-Bitmap.
 */

/** Ein Wort einer Zeile. */
data class Word(
    val left: Int,
    val right: Int,
    /** Endet das Wort mit einem Trennstrich: rechter Rand ohne den Strich, sonst -1. */
    val stemRight: Int = -1,
    /** Linker Rand des Trennstrichs, sonst -1. Der Abstand zu [stemRight] ist ein Buchstabenabstand. */
    val hyphenLeft: Int = -1,
) {
    val hyphenated: Boolean get() = stemRight >= 0
    val width: Int get() = right - left
}

/** Eine Zeile (oder ein Bild, wenn [figure] gilt). */
data class TextLine(
    val top: Int,
    val bottom: Int,
    /** Grundlinie: Unterkante der Kleinbuchstaben. */
    val baseline: Int,
    /** Höhe der Kleinbuchstaben ohne Ober- und Unterlängen. */
    val xHeight: Int,
    val left: Int,
    val right: Int,
    val words: List<Word>,
    val figure: Boolean = false,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Ergebnis der Analyse einer PDF-Seite. */
data class PageText(
    val width: Int,
    val height: Int,
    val lines: List<TextLine>,
    /** Die ganze Seite ist ein Bild (z. B. ein Cover). */
    val image: Boolean = false,
) {
    val textLines: List<TextLine> get() = lines.filter { !it.figure }
}

/** Bildschirmfläche einer Seite in Pixeln, mit Rändern für den Text. */
data class ReflowFrame(
    val width: Int,
    val height: Int,
    val marginX: Int,
    val marginTop: Int,
    val marginBottom: Int,
) {
    val textWidth: Int get() = width - 2 * marginX
    val textHeight: Int get() = height - marginTop - marginBottom
}

/**
 * Ein Stück einer PDF-Seite, das auf eine Bildschirmseite kopiert wird.
 *
 * @param scale Bildschirm-Pixel pro Analyse-Pixel
 * @param word Text (wird beim Zeichnen nur abgedunkelt, nie aufgehellt) oder Bild
 */
data class Placement(
    val page: Int,
    val srcLeft: Int,
    val srcTop: Int,
    val srcRight: Int,
    val srcBottom: Int,
    val dstX: Float,
    val dstY: Float,
    val scale: Float,
    val word: Boolean = true,
) {
    val dstWidth: Float get() = (srcRight - srcLeft) * scale
    val dstHeight: Float get() = (srcBottom - srcTop) * scale
}

/**
 * Eine Bildschirmseite im Modus "Groß".
 *
 * @param startPage PDF-Seite, auf der der Inhalt dieser Seite beginnt
 * @param startLine Zeile auf dieser PDF-Seite (Index in [PageText.lines])
 * @param imageOnly Seite zeigt nur eine Bildseite (wird im Dark Mode nicht invertiert)
 */
data class ScreenPage(
    val placements: List<Placement>,
    val startPage: Int,
    val startLine: Int,
    val imageOnly: Boolean = false,
)

/** Lesestelle unabhängig von der Schriftgröße: PDF-Seite und Anteil (0..1) innerhalb der Seite. */
data class ReadingPosition(val page: Int, val fraction: Float = 0f)
