package com.example.ebf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Dünne, threadsichere Hülle um [PdfRenderer].
 *
 * PdfRenderer darf immer nur eine Seite gleichzeitig offen haben, deshalb laufen
 * alle Zugriffe nacheinander über einen Mutex im Hintergrund.
 */
class PdfDocument private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pageCount: Int = renderer.pageCount

    private val mutex = Mutex()
    private var closed = false
    private val sizes = arrayOfNulls<Size>(pageCount)

    /** Seitengröße in PDF-Punkten (1/72 Zoll). */
    suspend fun pageSize(index: Int): Size =
        sizes[index] ?: locked {
            renderer.openPage(index).use { Size(it.width, it.height) }
        }.also { sizes[index] = it }

    /**
     * Rendert eine Seite auf [widthPx] Pixel Breite.
     *
     * @param crop Ausschnitt der Seite als Anteile von 0 bis 1 (Standard: ganze Seite).
     */
    suspend fun render(index: Int, widthPx: Int, crop: RectF = FULL_PAGE): Bitmap = locked {
        renderer.openPage(index).use { page ->
            val pageW = page.width.toFloat()
            val pageH = page.height.toFloat()
            sizes[index] = Size(page.width, page.height)
            val left = crop.left * pageW
            val top = crop.top * pageH
            val cropW = crop.width() * pageW
            val cropH = crop.height() * pageH

            var scale = widthPx / cropW
            var w = (cropW * scale).roundToInt()
            var h = (cropH * scale).roundToInt()
            val pixels = w.toLong() * h
            if (pixels > MAX_PIXELS) {
                scale *= sqrt(MAX_PIXELS.toDouble() / pixels).toFloat()
                w = (cropW * scale).roundToInt()
                h = (cropH * scale).roundToInt()
            }

            val bitmap = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
            // Viele PDFs haben einen transparenten Hintergrund.
            bitmap.eraseColor(Color.WHITE)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(-left * scale, -top * scale)
            }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /**
     * Analysiert eine kleine Vorschau der Seite: Wo steht etwas (für "Ränder entfernen")
     * und ist die Seite überwiegend ein Bild (für den Dark Mode)?
     */
    suspend fun analyze(index: Int): PageAnalysis {
        val preview = render(index, BOUNDS_PREVIEW_WIDTH_PX)
        return withContext(Dispatchers.Default) {
            val w = preview.width
            val h = preview.height
            val pixels = IntArray(w * h)
            preview.getPixels(pixels, 0, w, 0, 0, w, h)
            preview.recycle()
            CropMath.analyze(pixels, w, h)
        }
    }

    /** Schließt das Dokument, sobald laufende Render-Vorgänge fertig sind. */
    fun close() {
        closeScope.launch { closeNow() }
    }

    suspend fun closeNow() {
        mutex.withLock {
            if (!closed) {
                closed = true
                renderer.close()
                fd.close()
            }
        }
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) throw CancellationException("PDF already closed")
            block()
        }
    }

    companion object {
        val FULL_PAGE = RectF(0f, 0f, 1f, 1f)

        /** Obergrenze pro Bitmap (~24 MB), damit große Tablets nicht den Speicher sprengen. */
        private const val MAX_PIXELS = 6_000_000L

        private const val BOUNDS_PREVIEW_WIDTH_PX = 300

        private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        suspend fun open(context: Context, uri: Uri): PdfDocument = withContext(Dispatchers.IO) {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException(uri.toString())
            try {
                PdfDocument(fd, PdfRenderer(fd))
            } catch (e: Exception) {
                fd.close()
                throw e
            }
        }
    }
}
