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
import com.example.ebf.pdf.PdfDocument
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

    suspend fun renderPage(page: Int, widthPx: Int): ImageBitmap {
        val key = "$page@$widthPx"
        bitmaps.get(key)?.let { return it.asImageBitmap() }
        val bitmap = requireDocument().render(page, widthPx)
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

        private fun cacheSizeBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 5).coerceAtMost(160L * 1024 * 1024).toInt()
    }
}
