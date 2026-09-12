package com.lgka

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/// Accent palette — mirrors ColorProvider (blue/mint/lavender/rose/peach).
/// See DESIGN_GUIDELINES.md §1.2 for the contrast rules.
enum class Accent(val key: String, val color: Color, val labelRes: Int) {
    Blue("blue", Color(0xFF3770D4), R.string.accent_blue),
    Mint("mint", Color(0xFF45A88A), R.string.accent_mint),
    Lavender("lavender", Color(0xFF9B6BDF), R.string.accent_lavender),
    Rose("rose", Color(0xFFC47A7A), R.string.accent_rose),
    Peach("peach", Color(0xFFBF7F46), R.string.accent_peach);

    /** WCAG-safe "on" color: white only where it reaches 4.5:1, otherwise near-black. */
    val onColor: Color get() = if (contrastWithWhite() >= 4.5f) Color.White else Color(0xFF0B0B0B)

    private fun contrastWithWhite(): Float {
        fun lin(c: Float) = if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        val l = 0.2126f * lin(color.red) + 0.7152f * lin(color.green) + 0.0722f * lin(color.blue)
        return (1.05f) / (l + 0.05f)
    }

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Blue
    }
}

/// Persisted preferences — mirrors PreferencesManager. Compose-observable so
/// screens recompose on change; every write lands in SharedPreferences.
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("lgka", Context.MODE_PRIVATE)

    var onboardingCompleted by pref("onboardingCompleted", false)
    var isAuthenticated by pref("isAuthenticated", false)
    var accentColor by pref("accentColor", "blue")
    var themeMode by pref("themeMode", "system")
    var krankmeldungInfoShown by pref("krankmeldungInfoShown", false)
    var selectedScheduleClass by pref("selectedScheduleClass", "")
    /** Set when the API confirmed a 401: the school changed the password; the login screen explains why. */
    var passwordRotated by pref("passwordRotated", false)

    val accent: Accent get() = Accent.of(accentColor)

    /**
     * Signed in means both the flag and stored credentials exist; a missing
     * credential store sends the user back through the login gate instead of
     * failing every request.
     */
    fun isSignedIn(credentials: Credentials): Boolean = isAuthenticated && credentials.load() != null

    fun signOut(credentials: Credentials) {
        credentials.clear()
        isAuthenticated = false
    }

    /**
     * Fresh-install state: an explicit sign-out in Settings starts over at the welcome
     * screen (also the seed for automated screenshots). A rotated school password uses
     * [signOut] instead and keeps the preferences.
     */
    fun reset(credentials: Credentials) {
        signOut(credentials)
        onboardingCompleted = false
        krankmeldungInfoShown = false
        selectedScheduleClass = ""
        accentColor = "blue"
        themeMode = "system"
        passwordRotated = false
    }

    private fun <T> pref(key: String, default: T) = object : ReadWriteProperty<Any?, T> {
        private var state by mutableStateOf(read(key, default))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state = value
            write(key, value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(key: String, default: T): T = when (default) {
        is Boolean -> sp.getBoolean(key, default) as T
        is String -> (sp.getString(key, default) ?: default) as T
        else -> default
    }

    private fun <T> write(key: String, value: T) = sp.edit {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
        }
    }
}
