package com.example.ebf

import android.app.Application
import android.content.Context
import com.example.ebf.data.LibraryRepository
import com.example.ebf.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EbfApp : Application() {
    /** Für Schreibvorgänge, die auch nach dem Schließen eines Bildschirms fertig werden sollen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsRepository(this) }
    val library by lazy { LibraryRepository(this, settings) }
}

val Context.ebfApp: EbfApp get() = applicationContext as EbfApp
