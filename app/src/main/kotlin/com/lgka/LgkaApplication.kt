package com.lgka

import android.app.Application
import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide dependencies, created once; no globals, no service locator. */
class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val credentials = Credentials(context)
    val cache = DiskCache(context)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val api = SchoolApi(credentials, cache, Downloader(appScope))
}

class LgkaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(applicationContext)
        container.appScope.launch { container.cache.evictStale() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LgkaApplication).container
