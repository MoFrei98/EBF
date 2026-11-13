package com.example.easybooks

import android.app.Application
import android.content.Context
import com.example.easybooks.data.LibraryRepository
import com.example.easybooks.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EasyBooksApp : Application() {
    /** Für Schreibvorgänge, die auch nach dem Schließen eines Bildschirms fertig werden sollen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsRepository(this) }
    val library by lazy { LibraryRepository(this, settings) }
}

val Context.easyBooksApp: EasyBooksApp get() = applicationContext as EasyBooksApp
