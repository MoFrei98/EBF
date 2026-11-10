package com.example.ebf.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Eine gemeinsame DataStore-Datei für Bibliothek und Einstellungen. */
internal val Context.ebfDataStore: DataStore<Preferences> by preferencesDataStore(name = "ebf")
