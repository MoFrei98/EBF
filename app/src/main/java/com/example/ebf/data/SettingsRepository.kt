package com.example.ebf.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Wie die Seiten beim Blättern animiert werden. */
enum class PageTurn {
    /** Die obere Seite gleitet weg und gibt die darunterliegende frei (wie im Buch). */
    CURL,

    /** Beide Seiten schieben sich gemeinsam zur Seite. */
    SLIDE,
}

data class AppSettings(
    val pageTurn: PageTurn = PageTurn.CURL,
)

class SettingsRepository(private val context: Context) {

    private val pageTurnKey = stringPreferencesKey("page_turn")

    val settings: Flow<AppSettings> = context.ebfDataStore.data
        .map { prefs ->
            AppSettings(
                pageTurn = enumOrDefault(prefs[pageTurnKey], PageTurn.CURL),
            )
        }
        .distinctUntilChanged()

    suspend fun setPageTurn(value: PageTurn) {
        context.ebfDataStore.edit { it[pageTurnKey] = value.name }
    }
}

internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
