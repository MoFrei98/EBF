package com.example.ebf.data

/**
 * Schriftgröße im Reader (Einstellung "Klein / Normal / Groß"). Die Schrift wird größer,
 * indem ein kleinerer Ausschnitt der Seite gezeigt wird. In keiner Stufe wird links oder rechts Text abgeschnitten:
 * Mehr als "Groß" (Text füllt genau die Bildschirmbreite) geht im Hochformat nicht.
 */
enum class ZoomMode {
    /** "Klein": Die ganze Seite ist sichtbar. */
    WIDE,

    /** "Normal": Weiße Ränder zur Hälfte weg. */
    NORMAL,

    /** "Groß": Weiße Ränder ganz weg, der Text füllt die Breite. Hohe Seiten scrollen. */
    CLOSE;

    companion object {
        /** Liest den gespeicherten Wert, auch aus älteren Versionen der App. */
        fun fromStored(name: String?): ZoomMode = when (name) {
            "CROP_MARGINS" -> CLOSE
            "FIT_PAGE" -> WIDE
            else -> entries.firstOrNull { it.name == name } ?: WIDE
        }
    }
}
