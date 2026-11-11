package com.example.ebf.data

/**
 * Sichtbarer Seitenausschnitt im Reader (Einstellung). Je kleiner der Ausschnitt,
 * desto größer die Schrift. In keiner Stufe wird links oder rechts Text abgeschnitten:
 * Mehr als "Nah" (Text füllt genau die Bildschirmbreite) geht im Hochformat nicht.
 */
enum class ZoomMode {
    /** Die ganze Seite ist sichtbar. */
    WIDE,

    /** Weiße Ränder zur Hälfte weg. */
    NORMAL,

    /** Weiße Ränder ganz weg, der Text füllt die Breite. Hohe Seiten scrollen. */
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
