package com.example.ebf.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebf.R
import com.example.ebf.data.Book
import com.example.ebf.data.ImportException
import com.example.ebf.ebfApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = app.ebfApp.library

    /** Bücher, zuletzt gelesene zuerst. `null`, solange noch geladen wird. */
    val books: StateFlow<List<Book>?> = repo.books
        .map { list -> list.sortedByDescending { it.lastOpenedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** URIs von Büchern, die direkt nach dem Import geöffnet werden sollen. */
    private val _openRequests = Channel<String>(Channel.BUFFERED)
    val openRequests: Flow<String> = _openRequests.receiveAsFlow()

    fun import(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val book = repo.import(uri)
                _openRequests.send(book.uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                _messages.send(e.message ?: getApplication<Application>().getString(R.string.error_open))
            } catch (e: Exception) {
                _messages.send(getApplication<Application>().getString(R.string.error_open))
            } finally {
                _importing.value = false
            }
        }
    }

    fun rename(book: Book, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.update(book.uri) { it.copy(title = clean) } }
    }

    fun remove(book: Book) {
        viewModelScope.launch { repo.remove(book) }
    }

    fun coverFile(book: Book): File = repo.coverFile(book)
}
