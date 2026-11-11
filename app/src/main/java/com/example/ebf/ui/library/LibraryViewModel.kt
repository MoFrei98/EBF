package com.example.ebf.ui.library

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebf.R
import com.example.ebf.data.Book
import com.example.ebf.data.BookFilter
import com.example.ebf.data.FolderPaths
import com.example.ebf.data.ImportException
import com.example.ebf.data.LibraryException
import com.example.ebf.data.LibrarySort
import com.example.ebf.data.MoveOutcome
import com.example.ebf.data.SearchText
import com.example.ebf.ebfApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.Collator
import java.util.Locale

/** Ein Ordner in der Bibliothek, mit bis zu drei Covern als Vorschau. */
data class FolderTile(val path: String, val name: String, val bookCount: Int, val covers: List<Book>)

data class LibraryUiState(
    /** Aktuell geöffneter Ordner ("" = oberste Ebene). */
    val path: String,
    val folders: List<FolderTile>,
    val books: List<Book>,
    /** Alle Ordner (für "In Ordner verschieben"). */
    val allFolders: List<String>,
    val hasLibraryFolder: Boolean,
    val sort: LibrarySort,
    val filter: BookFilter,
    /** Suchbegriff; `null`, solange die Suche geschlossen ist. */
    val query: String?,
    /** Anzahl aller Bücher in der Bibliothek (ohne Filter). */
    val totalBooks: Int,
) {
    /** Suche mit Text: Treffer aus allen Ordnern. */
    val searching: Boolean get() = !query.isNullOrBlank()
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = app.ebfApp.library
    private val settingsRepo = app.ebfApp.settings
    private val collator = Collator.getInstance(Locale.GERMAN)

    private val requestedPath = MutableStateFlow(FolderPaths.ROOT)
    private val query = MutableStateFlow<String?>(null)

    val state: StateFlow<LibraryUiState?> = combine(
        repo.books, repo.folders, settingsRepo.settings, requestedPath, query,
    ) { books, folders, settings, requested, query ->
        // Wurde der geöffnete Ordner gelöscht oder umbenannt, zurück nach oben.
        val path = if (requested.isEmpty() || requested in folders) requested else FolderPaths.ROOT
        val order = when (settings.sort) {
            LibrarySort.RECENT -> compareByDescending<Book> { it.lastOpenedAt }
            LibrarySort.TITLE -> compareBy<Book, String>(collator) { it.title }
        }
        val filter = settings.filter
        val visible = books.filter { filter.accepts(it) }
        fun tile(folder: String): FolderTile {
            val inside = visible.filter { it.folder != null && FolderPaths.isInside(it.folder, folder) }
            return FolderTile(
                path = folder,
                name = FolderPaths.name(folder),
                bookCount = inside.size,
                covers = inside.sortedByDescending { it.lastOpenedAt }.take(3),
            )
        }

        val tiles: List<FolderTile>
        val shown: List<Book>
        if (!query.isNullOrBlank()) {
            // Suche: Treffer aus allen Ordnern.
            tiles = FolderPaths.treeOrder(folders)
                .filter { SearchText.matches(query, FolderPaths.name(it)) }
                .map(::tile)
                .filter { filter == BookFilter.ALL || it.bookCount > 0 }
            shown = visible.filter { SearchText.matches(query, it.title, it.fileName) }.sortedWith(order)
        } else {
            tiles = FolderPaths.subfolders(folders, path).map(::tile)
                // Bei aktivem Filter leere Ordner ausblenden.
                .filter { filter == BookFilter.ALL || it.bookCount > 0 }
            shown = visible.filter {
                if (path.isEmpty()) it.folder == null || it.folder == FolderPaths.ROOT else it.folder == path
            }.sortedWith(order)
        }
        LibraryUiState(
            path = path,
            folders = tiles,
            books = shown,
            allFolders = FolderPaths.treeOrder(folders),
            hasLibraryFolder = settings.libraryTree != null,
            sort = settings.sort,
            filter = filter,
            query = query,
            totalBooks = books.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** URIs von Büchern, die direkt nach dem Import geöffnet werden sollen. */
    private val _openRequests = Channel<String>(Channel.BUFFERED)
    val openRequests: Flow<String> = _openRequests.receiveAsFlow()

    init {
        // Beim Start mit dem Ordner auf dem Gerät abgleichen.
        viewModelScope.launch {
            if (!repo.sync()) message(R.string.error_library_unreadable)
        }
    }

    // ------------------------------------------------------------ Navigation

    fun openFolder(path: String) {
        requestedPath.value = path
        query.value = null
    }

    fun openSearch() {
        if (query.value == null) query.value = ""
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun closeSearch() {
        query.value = null
    }

    fun setFilter(filter: BookFilter) {
        viewModelScope.launch { settingsRepo.setFilter(filter) }
    }

    /** Eine Ebene nach oben. @return false, wenn wir schon ganz oben sind. */
    fun navigateUp(): Boolean {
        val path = state.value?.path ?: return false
        if (path.isEmpty()) return false
        requestedPath.value = FolderPaths.parent(path)
        return true
    }

    private val currentPath: String get() = state.value?.path ?: FolderPaths.ROOT

    // ---------------------------------------------------------------- Bücher

    /**
     * Nimmt eine PDF auf. Ist ein Bibliotheksordner gewählt, landet sie im gerade
     * geöffneten Ordner ([intoCurrentFolder]) bzw. ganz oben (z. B. bei "Öffnen mit").
     */
    fun import(uri: Uri, intoCurrentFolder: Boolean = true) {
        val target = if (intoCurrentFolder) currentPath else FolderPaths.ROOT
        launchBusy {
            var book = repo.import(uri)
            if (state.value?.hasLibraryFolder == true && book.folder == null) {
                try {
                    val (moved, outcome) = repo.moveBook(book, target)
                    book = moved
                    if (outcome == MoveOutcome.COPIED) message(R.string.info_copied)
                } catch (e: LibraryException) {
                    message(e.message)
                }
            }
            _openRequests.send(book.uri)
        }
    }

    fun moveBook(book: Book, targetPath: String) {
        launchBusy {
            val (_, outcome) = repo.moveBook(book, targetPath)
            val folder = if (targetPath.isEmpty()) getString(R.string.library_title) else FolderPaths.name(targetPath)
            message(
                if (outcome == MoveOutcome.MOVED) getString(R.string.info_moved, folder) else getString(R.string.info_copied)
            )
        }
    }

    fun rename(book: Book, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.update(book.uri) { it.copy(title = clean) } }
    }

    /** Lose Bücher: aus der Bibliothek nehmen, die PDF bleibt erhalten. */
    fun remove(book: Book) {
        viewModelScope.launch { repo.remove(book) }
    }

    /** Bücher im Bibliotheksordner: PDF vom Gerät löschen. */
    fun deleteBook(book: Book) {
        launchBusy { repo.deleteBookFile(book) }
    }

    fun coverFile(book: Book): File = repo.coverFile(book)

    // ---------------------------------------------------------------- Ordner

    /** Legt einen Ordner im aktuellen Ordner an und verschiebt optional gleich ein Buch hinein. */
    fun createFolder(name: String, moveInto: Book? = null) {
        launchBusy {
            val path = repo.createFolder(currentPath, name)
            if (moveInto != null) {
                val (_, outcome) = repo.moveBook(moveInto, path)
                if (outcome == MoveOutcome.COPIED) message(R.string.info_copied)
            }
        }
    }

    fun renameFolder(path: String, name: String) {
        launchBusy { repo.renameFolder(path, name) }
    }

    fun deleteFolder(path: String) {
        launchBusy { repo.deleteFolder(path) }
    }

    fun setLibraryFolder(treeUri: Uri) {
        launchBusy {
            if (repo.setLibraryFolder(treeUri)) message(R.string.info_library_folder_set)
            else message(R.string.error_library_unreadable)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                if (!repo.sync()) message(R.string.error_library_unreadable)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { settingsRepo.setSort(sort) }
    }

    // ---------------------------------------------------------------- Hilfen

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                message(e.message)
            } catch (e: LibraryException) {
                message(e.message)
            } catch (e: Exception) {
                message(R.string.error_unknown)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun getString(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private suspend fun message(@StringRes id: Int) = message(getString(id))

    private suspend fun message(text: String?) {
        if (!text.isNullOrBlank()) _messages.send(text)
    }
}
