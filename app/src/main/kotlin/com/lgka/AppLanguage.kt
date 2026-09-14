package com.lgka

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The app's language: the system's, or German / English picked in Settings (iOS AppLanguage).
 * Android 13+ keeps it as the per-app language (res/xml/locales_config.xml), so the system recreates
 * the activity and it also shows in the system settings; older versions store it here and
 * [MainActivity] wraps its context. Resources, dates, numbers and the tutorial images all follow it.
 */
object AppLanguage {
    val supported = listOf("de", "en")

    private const val KEY = "appLanguage"
    private const val PDF_KEY = "customPlanPdfLanguage"

    private fun prefs(context: Context) = context.getSharedPreferences("lgka", Context.MODE_PRIVATE)

    /** "de" / "en" when picked, null for the system language. */
    fun code(context: Context): String? {
        val picked = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            prefs(context).getString(KEY, null)
        }
        return picked?.takeIf { it in supported }
    }

    /** Applies at once: every string, date and image of the running activity changes. */
    fun set(activity: Activity, code: String?) {
        val value = code?.takeIf { it in supported }
        if (value == code(activity)) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (value == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
        } else {
            prefs(activity).edit { if (value == null) remove(KEY) else putString(KEY, value) }
            activity.recreate()
        }
    }

    /** Before Android 13: [base] with the picked language (the device's region stays). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val system = Resources.getSystem().configuration.locales[0]
        val code = prefs(base).getString(KEY, null)?.takeIf { it in supported }
        if (code == null) {
            Locale.setDefault(system)
            return base
        }
        val locale = Locale.Builder().setLanguage(code).setRegion(system.country).build()
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** True once per language: the saved plan's PDF was rendered in another language and needs redoing. */
    fun pdfNeedsRender(context: Context): Boolean =
        prefs(context).getString(PDF_KEY, null) != context.resources.configuration.locales[0].language

    fun pdfRendered(context: Context) =
        prefs(context).edit { putString(PDF_KEY, context.resources.configuration.locales[0].language) }
}
