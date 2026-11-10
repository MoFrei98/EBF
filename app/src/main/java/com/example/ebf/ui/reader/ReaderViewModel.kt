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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileNotFoundException

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Failed(val message: String, val canRemove: Boolean) : ReaderState
    data class Ready(val title: String, val pageCount: Int, val initialPage: Int) : ReaderState
}

class ReaderViewModel(app: Application, private val bookUri: String) : AndroidViewModel(app) {

    private val ebf = app.ebfApp
    private val repo = ebf.library

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var document: PdfDocument? = null

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
        val book = repo.get(bookUri)
            ?: return fail(R.string.error_unknown_book, canRemove = false)
        val doc = try {
            PdfDocument.open(getApplication(), Uri.parse(bookUri))
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
        val now = System.currentTimeMillis()
        ebf.appScope.launch {
            repo.update(bookUri) { it.copy(lastOpenedAt = now, pageCount = doc.pageCount) }
        }
        _state.value = ReaderState.Ready(
            title = book.title,
            pageCount = doc.pageCount,
            initialPage = book.lastPage.coerceIn(0, doc.pageCount - 1),
        )
    }

    private fun fail(@StringRes message: Int, canRemove: Boolean = true) {
        _state.value = ReaderState.Failed(getApplication<Application>().getString(message), canRemove)
    }

    private fun requireDocument(): PdfDocument =
        document ?: throw CancellationException("Document not open")

    /** Seitengröße in PDF-Punkten. */
    suspend fun pageSize(page: Int): Size = requireDocument().pageSize(page)

    /** Ausschnitt einer Seite für das Format "Ränder entfernen". */
    suspend fun pageCrop(page: Int): Crop {
        val doc = requireDocument()
        val typical = (typicalBounds ?: viewModelScope.async { computeTypicalBounds(doc) }
            .also { typicalBounds = it }).await()
        return CropMath.cropFor(boundsOf(doc, page), typical.forPage(page))
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
        ebf.appScope.launch {
            repo.update(bookUri) {
                it.copy(lastPage = page, lastOpenedAt = System.currentTimeMillis())
            }
        }
    }

    fun removeBook() {
        ebf.appScope.launch { repo.get(bookUri)?.let { repo.remove(it) } }
    }

    override fun onCleared() {
        document?.close()
        document = null
        bitmaps.evictAll()
    }

    companion object {
        fun factory(bookUri: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ReaderViewModel(app, bookUri)
            }
        }

        private const val BOUNDS_SAMPLES = 16

        private fun cacheSizeBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 5).coerceAtMost(160L * 1024 * 1024).toInt()
    }
}
