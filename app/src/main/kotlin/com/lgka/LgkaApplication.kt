package com.lgka

import android.app.Application
import android.content.Context
import lgka.api.LgkaApi
import lgka.api.SyncStore
import java.io.File

/** Process-wide dependencies, created once; no globals, no service locator. */
class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val credentials = Credentials(context)
    /** Synced resources + mirrored PDFs live in the private files dir (survives cache trims). */
    val store = SyncStore(File(context.filesDir, "lgka-data"))
    val api = LgkaApi(userAgent = AppInfo.userAgent)
    /** The custom J11/J12 timetable, if one was made on this device. */
    val customPlans = CustomPlanStore(File(context.filesDir, "custom-plan.json"))
}

object AppInfo {
    /** `LGKA+/<version> (android)` — identifies the app to api.lgka.app. */
    val userAgent: String = "LGKA+/" + BuildConfig.VERSION_NAME + " (android)"
}

class LgkaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // fonts and glyph lists for reading the Stufenplan PDFs (custom timetable)
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(applicationContext)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LgkaApplication).container
