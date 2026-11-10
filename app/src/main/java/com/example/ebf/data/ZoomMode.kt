package com.example.ebf.data

/**
 * Seitengröße im Reader (Einstellung). In keiner Stufe wird links oder rechts Inhalt
 * abgeschnitten.
 */
enum class ZoomMode {
    /** Die komplette Seite passt auf den Bildschirm. */
    FIT_PAGE,

    /** Weiße Ränder werden abgeschnitten, der Inhalt füllt die Breite. Ist die Seite
     *  dadurch höher als der Bildschirm, wird vertikal gescrollt. */
    CROP_MARGINS,
}
