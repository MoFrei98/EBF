package com.example.easybooks.data

/**
 * Schriftgröße im Reader (Einstellung "Klein / Normal / Groß"). In keiner Stufe wird links
 * oder rechts Text abgeschnitten. Standard ist "Normal".
 */
enum class ZoomMode {
    /** "Klein": Die ganze Seite ist sichtbar. */
    WIDE,

    /** "Normal": Weiße Ränder ganz weg, der Text füllt die Breite. Hohe Seiten scrollen. */
    NORMAL,

    /**
     * "Groß": Der Text wird neu umbrochen und deutlich vergrößert (siehe [com.example.easybooks.reflow]).
     * Eine PDF-Seite verteilt sich dabei auf mehrere Bildschirmseiten.
     */
    CLOSE;

    companion object {
        /** Liest den gespeicherten Wert, auch aus älteren Versionen der App. */
        fun fromStored(name: String?): ZoomMode = when (name) {
            "CROP_MARGINS" -> NORMAL
            "FIT_PAGE" -> WIDE
            else -> entries.firstOrNull { it.name == name } ?: NORMAL
        }
    }
}
