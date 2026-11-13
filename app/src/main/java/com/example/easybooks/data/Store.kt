package com.example.easybooks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Eine gemeinsame DataStore-Datei für Bibliothek und Einstellungen. Der Dateiname "ebf"
 * stammt aus der Zeit, als die App "EBF" hieß, und bleibt, damit Einstellungen erhalten bleiben.
 */
internal val Context.easyBooksDataStore: DataStore<Preferences> by preferencesDataStore(name = "ebf")
