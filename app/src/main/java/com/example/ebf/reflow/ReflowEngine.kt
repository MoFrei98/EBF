package com.example.ebf.reflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.ebf.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Analysiert ein Buch für die Schriftgröße "Groß" im Hintergrund (Seite für Seite von vorn)
 * und setzt Bildschirmseiten aus Ausschnitten der PDF zusammen.
 *
 * Die Analyse wird gespeichert, beim nächsten Öffnen geht es dort weiter.
 */
class ReflowEngine(
    private val doc: PdfDocument,
    private val cacheFile: File,
    private val fileSize: Long,
) {
    val pageCount: Int = doc.pageCount
    private val texts = arrayOfNulls<PageText>(pageCount)

    /** Analyse-Pixel pro PDF-Punkt (für die meisten Seiten). */
    private var scale = 0f

    @Volatile
    var style: BookStyle? = null
        private set

    private val _analyzed = MutableStateFlow(-1)

    /** Anzahl der Seiten von vorn, die fertig analysiert sind; -1, solange die Stichprobe läuft. */
    val analyzed: StateFlow<Int> = _analyzed.asStateFlow()

    fun text(page: Int): PageText? = texts.getOrNull(page)

    fun lineCount(page: Int): Int = texts.getOrNull(page)?.lines?.size ?: 0

    suspend fun run() = withContext(Dispatchers.Default) {
        try {
            analyzeAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reflow analysis failed", e)
        }
    }

    private suspend fun analyzeAll() {
        val cached = ReflowCache.read(cacheFile, pageCount, fileSize)
        if (cached != null) {
            scale = cached.scale
            style = cached.style
            cached.pages.copyInto(texts)
        } else {
            sample()
        }
        var fresh = 0
        for (i in 0 until pageCount) {
            if (texts[i] == null) {
                texts[i] = analyze(i)
                fresh++
                if (fresh % SAVE_EVERY == 0) save()
            }
            _analyzed.value = i + 1
            yield()
        }
        if (fresh > 0 || cached == null) save()
    }

    /** Bestimmt die Analyse-Auflösung und die typischen Maße des Texts aus einer Stichprobe. */
    private suspend fun sample() {
        // Probe-Seite in mittlerer Auflösung: Wie groß ist die Schrift?
        var xHeight = 0f
        for (probe in listOf(pageCount / 2, pageCount / 3, pageCount * 2 / 3, 0).distinct()) {
            val text = analyze(probe, PROBE_SCALE)
            val lines = text.textLines.filter { it.words.size >= 3 }
            if (lines.size >= 3) {
                xHeight = BookStyle.medianF(lines.map { it.xHeight.toFloat() }) ?: 0f
                break
            }
        }
        scale = if (xHeight > 0f) (PROBE_SCALE * TARGET_X_HEIGHT / xHeight).coerceIn(1.2f, 4f) else PROBE_SCALE
        val count = min(SAMPLES, pageCount)
        val first = if (pageCount > 4) 1 else 0
        val sampled = (0 until count).map { first + ((pageCount - first).toLong() * it / count).toInt() }.distinct()
        for (p in sampled) texts[p] = analyze(p)
        val pages = sampled.mapNotNull { texts[it] }
        style = BookStyle.from(pages.filter { it.textLines.size >= 5 }.ifEmpty { pages })
    }

    private suspend fun analyze(page: Int, wanted: Float = scale): PageText {
        val size = doc.pageSize(page)
        val area = size.width.toFloat() * size.height
        val s = min(wanted, sqrt(MAX_ANALYSIS_PIXELS / max(1f, area)))
        val pixels = doc.renderPixels(page, s)
        return ReflowAnalyzer.analyze(pixels.argb, pixels.width, pixels.height)
    }

    private fun save() {
        val s = style ?: return
        try {
            ReflowCache.write(ReflowAnalysis(pageCount, fileSize, scale, s, texts.copyOf()), cacheFile)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot save reflow cache", e)
        }
    }

    /**
     * Setzt eine Bildschirmseite zusammen. Wörter werden nur abgedunkelt eingezeichnet,
     * damit der weiße Rand eines Wortes nie die Schrift eines Nachbarn überdeckt.
     */
    suspend fun compose(page: ScreenPage, frame: ReflowFrame): Bitmap {
        // Ganzseitige Bilder bekommen ein Blatt in ihrer eigenen Größe (kein weißer Rand).
        val image = page.placements.singleOrNull()?.takeIf { page.imageOnly }
        val bitmap = if (image != null) {
            Bitmap.createBitmap(max(1, image.dstWidth.roundToInt()), max(1, image.dstHeight.roundToInt()), Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        }
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        if (image != null) canvas.translate(-image.dstX, -image.dstY)
        val darken = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN) }
        val plain = Paint(Paint.FILTER_BITMAP_FLAG)
        for (band in bands(page.placements)) {
            val first = band.first()
            val text = texts[first.page] ?: continue
            val sizePt = doc.pageSize(first.page)
            // Analyse-Pixel pro Punkt dieser Seite (bei sehr großen Seiten kleiner als üblich).
            val pageScale = text.width / sizePt.width.toFloat()
            val s = first.scale
            val left = band.minOf { it.srcLeft }
            val top = band.minOf { it.srcTop }
            val right = band.maxOf { it.srcRight }
            val bottom = band.maxOf { it.srcBottom }
            val area = RectF(left / pageScale, top / pageScale, right / pageScale, bottom / pageScale)
            val rendered = doc.renderArea(first.page, s * pageScale, area)
            for (p in band) {
                val src = Rect(
                    ((p.srcLeft - left) * s).roundToInt(),
                    ((p.srcTop - top) * s).roundToInt(),
                    ((p.srcRight - left) * s).roundToInt().coerceAtMost(rendered.width),
                    ((p.srcBottom - top) * s).roundToInt().coerceAtMost(rendered.height),
                )
                val dst = RectF(p.dstX, p.dstY, p.dstX + src.width(), p.dstY + src.height())
                canvas.drawBitmap(rendered, src, dst, if (p.word) darken else plain)
            }
            rendered.recycle()
        }
        return bitmap
    }

    /**
     * Teilt die Stücke einer Seite in Bänder, die je mit einem Aufruf gerendert werden:
     * gleiche PDF-Seite, gleicher Maßstab und senkrecht nah beieinander.
     */
    private fun bands(placements: List<Placement>): List<List<Placement>> {
        val result = ArrayList<List<Placement>>()
        val groups = placements.groupBy { it.page to it.scale }
        for (group in groups.values) {
            val sorted = group.sortedBy { it.srcTop }
            var current = ArrayList<Placement>()
            var bottom = Int.MIN_VALUE
            var top = 0
            for (p in sorted) {
                val gapTooBig = current.isNotEmpty() && p.srcTop - bottom > max(40, (bottom - top) / 4)
                val tooTall = current.isNotEmpty() && (p.srcBottom - top) * p.scale > MAX_BAND_HEIGHT
                if (gapTooBig || tooTall) {
                    result += current
                    current = ArrayList()
                }
                if (current.isEmpty()) {
                    top = p.srcTop
                    bottom = p.srcBottom
                }
                current += p
                bottom = max(bottom, p.srcBottom)
            }
            if (current.isNotEmpty()) result += current
        }
        return result
    }

    companion object {
        private const val TAG = "ReflowEngine"

        /** x-Höhe des Fließtexts in der Analyse-Bitmap (Pixel). */
        private const val TARGET_X_HEIGHT = 12f
        private const val PROBE_SCALE = 2f
        private const val MAX_ANALYSIS_PIXELS = 4_500_000f
        private const val SAMPLES = 12
        private const val SAVE_EVERY = 25
        private const val MAX_BAND_HEIGHT = 1600

        /** Speicherort der Analyse eines Buchs. */
        fun cacheFile(context: Context, bookId: String): File =
            File(File(context.noBackupFilesDir, "reflow"), "$bookId.bin")
    }
}
