package com.example.ebf.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Helles oder dunkles Design. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Reihenfolge der Bücher in der Bibliothek. */
enum class LibrarySort { RECENT, TITLE }

/** Wie die Seiten beim Blättern animiert werden. */
enum class PageTurn {
    /** Die obere Seite gleitet weg und gibt die darunterliegende frei (wie im Buch). */
    CURL,

    /** Beide Seiten schieben sich gemeinsam zur Seite. */
    SLIDE,
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Im Dark Mode auch die PDF-Seiten dunkel darstellen. */
    val darkPages: Boolean = true,
    /** Seitengröße im Reader, gilt für alle Bücher. */
    val pageSize: ZoomMode = ZoomMode.FIT_PAGE,
    val pageTurn: PageTurn = PageTurn.CURL,
    /** Bibliotheksordner auf dem Gerät (Tree-URI) oder `null`, solange keiner gewählt ist. */
    val libraryTree: String? = null,
    /** Lesbare Bezeichnung des Bibliotheksordners, z. B. "Documents/EBF". */
    val libraryLabel: String? = null,
    val sort: LibrarySort = LibrarySort.RECENT,
)

class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")
    private val darkPagesKey = booleanPreferencesKey("dark_pages")
    private val pageTurnKey = stringPreferencesKey("page_turn")
    private val pageSizeKey = stringPreferencesKey("page_size")
    private val libraryTreeKey = stringPreferencesKey("library_tree")
    private val libraryLabelKey = stringPreferencesKey("library_label")
    private val sortKey = stringPreferencesKey("sort")

    val settings: Flow<AppSettings> = context.ebfDataStore.data
        .map { prefs ->
            AppSettings(
                theme = enumOrDefault(prefs[themeKey], ThemeMode.SYSTEM),
                darkPages = prefs[darkPagesKey] ?: true,
                pageSize = enumOrDefault(prefs[pageSizeKey], ZoomMode.FIT_PAGE),
                pageTurn = enumOrDefault(prefs[pageTurnKey], PageTurn.CURL),
                libraryTree = prefs[libraryTreeKey],
                libraryLabel = prefs[libraryLabelKey],
                sort = enumOrDefault(prefs[sortKey], LibrarySort.RECENT),
            )
        }
        .distinctUntilChanged()

    suspend fun setTheme(value: ThemeMode) {
        context.ebfDataStore.edit { it[themeKey] = value.name }
    }

    suspend fun setDarkPages(value: Boolean) {
        context.ebfDataStore.edit { it[darkPagesKey] = value }
    }

    suspend fun setPageSize(value: ZoomMode) {
        context.ebfDataStore.edit { it[pageSizeKey] = value.name }
    }

    suspend fun setPageTurn(value: PageTurn) {
        context.ebfDataStore.edit { it[pageTurnKey] = value.name }
    }

    suspend fun setLibraryFolder(treeUri: String, label: String) {
        context.ebfDataStore.edit {
            it[libraryTreeKey] = treeUri
            it[libraryLabelKey] = label
        }
    }

    suspend fun setSort(value: LibrarySort) {
        context.ebfDataStore.edit { it[sortKey] = value.name }
    }
}

internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
