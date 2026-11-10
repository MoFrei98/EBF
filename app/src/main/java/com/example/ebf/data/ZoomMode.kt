package com.example.ebf.data

/**
 * Feste Zoom-Formate. In keinem Format wird links oder rechts Inhalt abgeschnitten.
 */
enum class ZoomMode {
    /** Die komplette Seite passt auf den Bildschirm. */
    FIT_PAGE,

    /** Die Seite füllt die Breite, bei Bedarf wird vertikal gescrollt. */
    FIT_WIDTH,

    /** Weiße Ränder werden abgeschnitten, der Inhalt füllt die Breite. */
    CROP_MARGINS,
}
