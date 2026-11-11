package com.example.ebf.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Rechnet Android-Dateiadressen in einen lesbaren absoluten Pfad um, z. B.
 * `/storage/emulated/0/Documents/EBF/Buch.pdf`. Der Pfad dient der Anzeige;
 * geöffnet wird die Datei weiterhin über ihre Adresse (ohne Speicher-Berechtigung).
 */
object PdfPaths {

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    private const val DOWNLOADS = "com.android.providers.downloads.documents"

    fun absolutePath(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        return fromDocumentId(uri.authority, documentId)
    }

    /**
     * Reine Umrechnung aus Anbieter und Dokument-ID:
     * - `primary:Documents/EBF/a.pdf` → `/storage/emulated/0/Documents/EBF/a.pdf`
     * - `1A2B-3C4D:Bücher/a.pdf` (SD-Karte) → `/storage/1A2B-3C4D/Bücher/a.pdf`
     * - `raw:/storage/emulated/0/Download/a.pdf` (Downloads) → der Pfad dahinter
     */
    fun fromDocumentId(authority: String?, documentId: String): String? = when (authority) {
        EXTERNAL_STORAGE -> {
            val volume = documentId.substringBefore(':', "")
            val path = documentId.substringAfter(':', "")
            when {
                volume.isEmpty() -> null
                volume == "primary" -> "/storage/emulated/0/$path".trimEnd('/')
                else -> "/storage/$volume/$path".trimEnd('/')
            }
        }
        DOWNLOADS -> documentId.removePrefix("raw:").takeIf { it.startsWith("/") }
        else -> null
    }
}
