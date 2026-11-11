package com.example.ebf.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ebf.R
import com.example.ebf.ebfApp
import com.example.ebf.pdf.Crop
import com.example.ebf.pdf.CropMath
import com.example.ebf.pdf.PageAnalysis
import com.example.ebf.pdf.PdfDocument
import com.example.ebf.pdf.TypicalBounds
import com.example.ebf.reflow.ReadingPosition
import com.example.ebf.reflow.ReflowEngine
import com.example.ebf.reflow.ReflowFrame
import com.example.ebf.reflow.ReflowLayouter
import com.example.ebf.reflow.ScreenPage
import com.example.ebf.reflow.indexOf
import com.example.ebf.reflow.position
import com.example.ebf.reflow.reflowScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import kotlin.math.max

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Failed(val message: String, val canRemove: Boolean) : ReaderState
    data class Ready(val title: String, val pageCount: Int) : ReaderState
}

/** Bildschirmgröße und gewünschte Schriftgröße für "Groß" (in Pixeln). */
data class ReflowParams(val frame: ReflowFrame, val targetXHeight: Float)

/**
 * Neu umbrochene Seiten für "Groß". Während die Analyse noch läuft, kommen am Ende weitere
 * Seiten dazu; [estimatedCount] schätzt die Gesamtzahl.
 */
data class ReflowLayout(
    val params: ReflowParams,
    val pages: List<ScreenPage>,
    val complete: Boolean,
    val analyzed: Int,
    val total: Int,
) {
    val estimatedCount: Int
        get() = if (complete || analyzed <= 0) pages.size
        else max(pages.size, (pages.size.toLong() * total / analyzed).toInt())
}

class ReaderViewModel(app: Application, private val bookId: String) : AndroidViewModel(app) {

    private val ebf = app.ebfApp
    private val repo = ebf.library

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var document: PdfDocument? = null
    private var fileSize: Long = -1

    /** Aktuelle Lesestelle, unabhängig von der Schriftgröße. */
    var position = ReadingPosition(0)
        private set

    private var reflow: ReflowEngine? = null
    private val reflowParams = MutableStateFlow<ReflowParams?>(null)
    private val _reflowLayout = MutableStateFlow<ReflowLayout?>(null)
    val reflowLayout: StateFlow<ReflowLayout?> = _reflowLayout.asStateFlow()
    private val _reflowProgress = MutableStateFlow(0f)

    /** Fortschritt der Vorbereitung von "Groß" (0..1). */
    val reflowProgress: StateFlow<Float> = _reflowProgress.asStateFlow()

    /** Analyse je Seite. Nur auf dem Main-Thread benutzt. */
    private val analyses = HashMap<Int, PageAnalysis>()
    private var typicalBounds: Deferred<TypicalBounds>? = null

    private val bitmaps = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val book = repo.get(bookId)
            ?: return fail(R.string.error_unknown_book, canRemove = false)
        val doc = try {
            PdfDocument.open(getApplication(), Uri.parse(book.uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FileNotFoundException) {
            return fail(R.string.error_missing)
        } catch (e: SecurityException) {
            return fail(R.string.error_missing)
        } catch (e: Exception) {
            return fail(R.string.error_open)
        }
        if (doc.pageCount == 0) {
            doc.close()
            return fail(R.string.error_empty_pdf)
        }
        document = doc
        fileSize = book.fileSize
        position = ReadingPosition(book.lastPage.coerceIn(0, doc.pageCount - 1), book.lastPageOffset.coerceIn(0f, 1f))
        val now = System.currentTimeMillis()
        ebf.appScope.launch {
            repo.update(bookId) { it.copy(lastOpenedAt = now, pageCount = doc.pageCount) }
        }
        _state.value = ReaderState.Ready(title = book.title, pageCount = doc.pageCount)
    }

    private fun fail(@StringRes message: Int, canRemove: Boolean = true) {
        _state.value = ReaderState.Failed(getApplication<Application>().getString(message), canRemove)
    }

    private fun requireDocument(): PdfDocument =
        document ?: throw CancellationException("Document not open")

    /** Seitengröße in PDF-Punkten. */
    suspend fun pageSize(page: Int): Size = requireDocument().pageSize(page)

    /** Ausschnitt einer Seite für das Format "Ränder entfernen". */
    suspend fun pageCrop(page: Int, tight: Boolean = false): Crop {
        val doc = requireDocument()
        val typical = (typicalBounds ?: viewModelScope.async { computeTypicalBounds(doc) }
            .also { typicalBounds = it }).await()
        val padding = if (tight) CropMath.PADDING_TIGHT else CropMath.PADDING
        return CropMath.cropFor(boundsOf(doc, page), typical.forPage(page), padding)
    }

    /** Ob die Seite überwiegend ein Bild ist (wird im Dark Mode nicht invertiert). */
    suspend fun isImagePage(page: Int): Boolean = analysisOf(requireDocument(), page).isImage

    private suspend fun analysisOf(doc: PdfDocument, page: Int): PageAnalysis =
        analyses[page] ?: doc.analyze(page).also { analyses[page] = it }

    private suspend fun boundsOf(doc: PdfDocument, page: Int): Crop? = analysisOf(doc, page).bounds

    /** Typische Ränder aus einer Stichprobe über das ganze Dokument (ohne Cover). */
    private suspend fun computeTypicalBounds(doc: PdfDocument): TypicalBounds {
        val first = if (doc.pageCount > 2) 1 else 0
        val available = doc.pageCount - first
        val count = minOf(BOUNDS_SAMPLES, available)
        val samples = (0 until count)
            .map { first + (it.toLong() * available / count).toInt() }
            .distinct()
            .mapNotNull { page -> boundsOf(doc, page)?.let { page to it } }
        return TypicalBounds.from(samples)
    }

    suspend fun renderPage(page: Int, widthPx: Int, crop: Crop = Crop.FULL): ImageBitmap {
        val key = "$page@$widthPx@$crop"
        bitmaps.get(key)?.let { return it.asImageBitmap() }
        val bitmap = requireDocument().render(page, widthPx, crop.toRectF())
        bitmaps.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    fun onPageSettled(page: Int) {
        // Innerhalb derselben Seite die genauere Stelle aus "Groß" behalten.
        if (page != position.page) savePosition(ReadingPosition(page))
    }

    private fun savePosition(pos: ReadingPosition) {
        position = pos
        ebf.appScope.launch {
            repo.update(bookId) {
                it.copy(lastPage = pos.page, lastPageOffset = pos.fraction, lastOpenedAt = System.currentTimeMillis())
            }
        }
    }

    // ------------------------------------------------------------------ Groß (neu umbrochen)

    /** Startet die Analyse für "Groß" (einmal pro Buch) und setzt die Seiten für [params]. */
    fun startReflow(params: ReflowParams) {
        reflowParams.value = params
        if (reflow != null) return
        val doc = document ?: return
        val engine = ReflowEngine(doc, ReflowEngine.cacheFile(getApplication(), bookId), fileSize)
        reflow = engine
        viewModelScope.launch { engine.run() }
        viewModelScope.launch {
            engine.analyzed.collect { n -> _reflowProgress.value = max(0, n) / engine.pageCount.toFloat() }
        }
        viewModelScope.launch(Dispatchers.Default) { layoutLoop(engine) }
    }

    private suspend fun layoutLoop(engine: ReflowEngine) {
        var layouter: ReflowLayouter? = null
        var used: ReflowParams? = null
        var fed = 0
        combine(engine.analyzed, reflowParams.filterNotNull()) { n, p -> n to p }.collect { (n, p) ->
            val style = engine.style ?: return@collect
            if (n < 0) return@collect
            if (p != used || layouter == null) {
                layouter = ReflowLayouter(style, p.frame, reflowScale(style, p.frame, p.targetXHeight))
                used = p
                fed = 0
            }
            val l = layouter!!
            while (fed < n) {
                engine.text(fed)?.let { l.add(fed, it) }
                fed++
            }
            val complete = n >= engine.pageCount
            val pages = if (complete) l.finish() else l.completedPages
            _reflowLayout.value = ReflowLayout(p, pages.toList(), complete, n, engine.pageCount)
        }
    }

    /** Bildschirmseite der aktuellen Lesestelle oder `null`, solange sie noch nicht gesetzt ist. */
    fun reflowIndexOfPosition(layout: ReflowLayout): Int? {
        val engine = reflow ?: return null
        return layout.pages.indexOf(position, engine::lineCount, layout.complete)
    }

    fun onReflowPageSettled(layout: ReflowLayout, index: Int) {
        val engine = reflow ?: return
        val page = layout.pages.getOrNull(index) ?: return
        savePosition(page.position(engine.lineCount(page.startPage)))
    }

    suspend fun renderReflowPage(layout: ReflowLayout, index: Int): ImageBitmap? {
        val engine = reflow ?: return null
        val page = layout.pages.getOrNull(index) ?: return null
        val key = "reflow@${layout.params}@$index"
        bitmaps.get(key)?.let { return it.asImageBitmap() }
        val bitmap = engine.compose(page, layout.params.frame)
        bitmaps.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    fun removeBook() {
        ebf.appScope.launch {
            val book = repo.get(bookId) ?: return@launch
            // Fehlt eine Datei im Bibliotheksordner, verschwindet der Eintrag beim nächsten
            // Abgleich ohnehin; hier nur den Eintrag entfernen, nie eine Datei löschen.
            repo.remove(book)
        }
    }

    override fun onCleared() {
        document?.close()
        document = null
        bitmaps.evictAll()
    }

    companion object {
        fun factory(bookId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ReaderViewModel(app, bookId)
            }
        }

        private const val BOUNDS_SAMPLES = 16

        private fun cacheSizeBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 5).coerceAtMost(160L * 1024 * 1024).toInt()
    }
}
