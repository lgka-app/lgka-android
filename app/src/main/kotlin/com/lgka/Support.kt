package com.lgka

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.Locale

/// Accent palette — mirrors ColorProvider (blue/mint/lavender/rose/peach).
enum class Accent(val key: String, val color: Color) {
    Blue("blue", Color(0xFF3770D4)),
    Mint("mint", Color(0xFF45A88A)),
    Lavender("lavender", Color(0xFF9B6BDF)),
    Rose("rose", Color(0xFFC47A7A)),
    Peach("peach", Color(0xFFBF7F46));

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Blue
    }
}

/// Persisted preferences — mirrors PreferencesManager (Compose-observable).
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("lgka", Context.MODE_PRIVATE)

    var onboardingCompleted by pref("onboardingCompleted", false)
    var isAuthenticated by pref("isAuthenticated", false)
    var accentColor by pref("accentColor", "blue")
    var themeMode by pref("themeMode", "system")
    var krankmeldungInfoShown by pref("krankmeldungInfoShown", false)
    var selectedScheduleClass by pref("selectedScheduleClass", "")

    val accent: Color get() = Accent.of(accentColor).color

    private fun <T> pref(key: String, default: T) =
        object : kotlin.properties.ReadWriteProperty<Any?, T> {
            private var state by mutableStateOf(read(key, default))
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = state
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
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

    private fun <T> write(key: String, value: T) = sp.edit().apply {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
        }
    }.apply()
}

/// DE/EN string catalog — mirrors the iOS `L` / lib/l10n arb files.
object L {
    val isGerman: Boolean get() = Locale.getDefault().language == "de"
    fun s(key: String): String = (if (isGerman) de[key] else en[key]) ?: de[key] ?: key

    val de = mapOf(
        "appTitle" to "LGKA+",
        "welcomeHeadline" to "Willkommen!",
        "welcomeSubtitle" to "Bei der neuen App fürs Lessing-Gymnasium Karlsruhe.",
        "continueLabel" to "Weiter",
        "infoHeader" to "Alle Funktionen im Überblick",
        "featureSubstitutionTitle" to "Vertretungsplan",
        "featureSubstitutionDesc" to "Aktueller Vertretungsplan für heute/morgen",
        "featureScheduleTitle" to "Stundenplan",
        "featureScheduleDesc" to "Stundenplan fürs 1./2. Halbjahr",
        "featureWeatherTitle" to "Wetterdaten",
        "featureWeatherDesc" to "Aktuelle Wetterdaten via Open-Meteo",
        "featureNewsTitle" to "Neuigkeiten",
        "featureNewsDesc" to "Aktuelle Neuigkeiten und Ankündigungen der Schule",
        "featureSickTitle" to "Krankmeldung",
        "featureSickDesc" to "Krankmeldung direkt über die App einreichen",
        "featureEventsTitle" to "Schulveranstaltungen",
        "featureEventsDesc" to "Alle anstehenden Schulveranstaltungen auf einen Blick",
        "accentColorTitle" to "Deine Akzentfarbe",
        "accentColorDescription" to "Wähle deine Lieblingsfarbe aus. Diese wird überall in der App verwendet.",
        "appearanceTitle" to "Erscheinungsbild",
        "themeDark" to "Dunkel", "themeLight" to "Hell", "themeAuto" to "Auto",
        "letsGo" to "Los geht's!",
        "authTitle" to "Anmeldung erforderlich",
        "authSubtitle" to "Verwende die Zugangsdaten, die du bereits von der Schulwebsite kennst",
        "username" to "Benutzername", "password" to "Passwort", "login" to "Anmelden",
        "substitutionPlan" to "Vertretungsplan",
        "schedule" to "Stundenplan",
        "termine" to "Bevorstehende Termine",
        "news" to "Neuigkeiten",
        "krankmeldung" to "Krankmeldung",
        "settings" to "Einstellungen",
        "today" to "Heute", "tomorrow" to "Morgen",
        "noInfoYet" to "Noch keine Infos",
        "serverConnectionFailed" to "Serververbindung fehlgeschlagen",
        "serverConnectionHint" to "Möglicherweise besteht keine Internetverbindung oder es finden gerade Wartungsarbeiten am Lessing-Gymnasium statt.",
        "tryAgain" to "Erneut versuchen",
        "noSchedulesAvailable" to "Keine Stundenpläne verfügbar",
        "noEventsAvailable" to "Keine Termine verfügbar",
        "scheduleNoClassTitle" to "In welcher Klasse bist du?",
        "scheduleNoClassSub" to "Tippe, um deine Klasse festzulegen",
        "setClassTitle" to "Klasse eingeben",
        "setClassButton" to "Speichern",
        "searchHint" to "Gib deine Klasse ein",
        "loadingSchedule" to "Lade Stundenplan...",
        "firstSemester" to "1. Halbjahr", "secondSemester" to "2. Halbjahr",
        "jahrgang11" to "Jahrgang 11", "jahrgang12" to "Jahrgang 12",
        "weatherPageTitle" to "Wetter Karlsruhe",
        "weatherDataNotAvailable" to "Wetterdaten nicht verfügbar",
        "checkInternetConnection" to "Bitte prüfe deine Internetverbindung.",
        "hourlyForecastLabel" to "STÜNDLICH", "threeDayForecastLabel" to "3 TAGE",
        "weatherAttribution" to "Wetterdaten von Open-Meteo.com",
        "weatherHumidityShort" to "Luftfeuchte", "weatherWindShort" to "Wind",
        "uviLow" to "Niedrig", "uviMedium" to "Mittel", "uviHigh" to "Hoch",
        "uviVeryHigh" to "Sehr hoch", "uviExtreme" to "Extrem",
        "krankmeldungInfoHeader" to "Hinweis zur Krankmeldung",
        "krankmeldungDisclaimer" to "Die Krankmeldung wird vom Lessing-Gymnasium bereitgestellt und ist unabhängig von der LGKA+ App.",
        "krankmeldungContact" to "Bei technischen Fragen oder Problemen wende dich bitte direkt an das Lessing-Gymnasium Karlsruhe.",
        "krankmeldungButton" to "Zur Krankmeldung",
        "settingsSectionAppearance" to "DARSTELLUNG", "settingsSectionMore" to "MEHR",
        "accentColor" to "Akzentfarbe",
        "bugReport" to "Fehler gefunden?", "bugReportTitle" to "Bug Report",
        "privacyLabel" to "Datenschutzerklärung", "legalLabel" to "Impressum",
        "loading" to "Lädt...",
        "formLoadError" to "Formular konnte nicht geladen werden",
        "formLoadErrorHint" to "Bitte überprüfe deine Internetverbindung und versuche es erneut.",
        "noNewsAvailable" to "Keine Neuigkeiten verfügbar",
        "openInBrowser" to "Im Browser öffnen",
        "sharePdf" to "PDF teilen", "searchInPdf" to "Im PDF suchen",
        "cancel" to "Abbrechen",
    )

    val en = mapOf(
        "welcomeHeadline" to "Welcome!",
        "welcomeSubtitle" to "To the new app for Lessing-Gymnasium Karlsruhe.",
        "continueLabel" to "Continue",
        "infoHeader" to "All features at a glance",
        "featureSubstitutionTitle" to "Substitution plan",
        "featureSubstitutionDesc" to "Current substitution plan for today/tomorrow",
        "featureScheduleTitle" to "Timetable",
        "featureScheduleDesc" to "Timetable for the 1st/2nd semester",
        "featureWeatherTitle" to "Weather data",
        "featureWeatherDesc" to "Current weather data via Open-Meteo",
        "featureNewsTitle" to "News",
        "featureNewsDesc" to "Current news and announcements from the school",
        "featureSickTitle" to "Sick note",
        "featureSickDesc" to "Submit a sick note directly from the app",
        "featureEventsTitle" to "School events",
        "featureEventsDesc" to "All upcoming school events at a glance",
        "accentColorTitle" to "Your accent color",
        "accentColorDescription" to "Choose your favorite color. It is used everywhere in the app.",
        "appearanceTitle" to "Appearance",
        "themeDark" to "Dark", "themeLight" to "Light", "themeAuto" to "Auto",
        "letsGo" to "Let's go!",
        "authTitle" to "Login required",
        "authSubtitle" to "Use the credentials you already know from the school website",
        "username" to "Username", "password" to "Password", "login" to "Log in",
        "substitutionPlan" to "Substitution plan",
        "schedule" to "Timetable",
        "termine" to "Upcoming events",
        "news" to "News",
        "krankmeldung" to "Sick note",
        "settings" to "Settings",
        "today" to "Today", "tomorrow" to "Tomorrow",
        "noInfoYet" to "No info yet",
        "serverConnectionFailed" to "Server connection failed",
        "serverConnectionHint" to "You may be offline, or maintenance is being carried out at the Lessing-Gymnasium.",
        "tryAgain" to "Try again",
        "noSchedulesAvailable" to "No timetables available",
        "noEventsAvailable" to "No events available",
        "scheduleNoClassTitle" to "Which class are you in?",
        "scheduleNoClassSub" to "Tap to set your class",
        "setClassTitle" to "Enter class",
        "setClassButton" to "Save",
        "searchHint" to "Enter your class",
        "loadingSchedule" to "Loading timetable...",
        "firstSemester" to "1st semester", "secondSemester" to "2nd semester",
        "jahrgang11" to "Year 11", "jahrgang12" to "Year 12",
        "weatherPageTitle" to "Weather Karlsruhe",
        "weatherDataNotAvailable" to "Weather data not available",
        "checkInternetConnection" to "Please check your internet connection.",
        "hourlyForecastLabel" to "HOURLY", "threeDayForecastLabel" to "3 DAYS",
        "weatherAttribution" to "Weather data by Open-Meteo.com",
        "weatherHumidityShort" to "Humidity", "weatherWindShort" to "Wind",
        "uviLow" to "Low", "uviMedium" to "Medium", "uviHigh" to "High",
        "uviVeryHigh" to "Very high", "uviExtreme" to "Extreme",
        "krankmeldungInfoHeader" to "About the sick note",
        "krankmeldungDisclaimer" to "The sick note is provided by the Lessing-Gymnasium and is independent of the LGKA+ app.",
        "krankmeldungContact" to "For technical questions or problems, please contact the Lessing-Gymnasium Karlsruhe directly.",
        "krankmeldungButton" to "To the sick note",
        "settingsSectionAppearance" to "APPEARANCE", "settingsSectionMore" to "MORE",
        "accentColor" to "Accent color",
        "bugReport" to "Found a bug?", "bugReportTitle" to "Bug Report",
        "privacyLabel" to "Privacy policy", "legalLabel" to "Legal notice",
        "loading" to "Loading...",
        "formLoadError" to "Form could not be loaded",
        "formLoadErrorHint" to "Please check your internet connection and try again.",
        "noNewsAvailable" to "No news available",
        "openInBrowser" to "Open in browser",
        "sharePdf" to "Share PDF", "searchInPdf" to "Search in PDF",
        "cancel" to "Cancel",
    )

    fun wmoDescription(code: Int): String = if (isGerman) wmoDe(code) else wmoEn(code)

    private fun wmoDe(code: Int) = when (code) {
        0 -> "Klarer Himmel"; 1 -> "Überwiegend klar"; 2 -> "Teilweise bewölkt"
        3 -> "Bedeckt"; 45 -> "Nebel"; 48 -> "Gefrierender Nebel"
        51 -> "Leichter Nieselregen"; 53 -> "Mäßiger Nieselregen"; 55 -> "Dichter Nieselregen"
        56, 57 -> "Gefrierender Nieselregen"
        61 -> "Leichter Regen"; 63 -> "Mäßiger Regen"; 65 -> "Starker Regen"
        66, 67 -> "Gefrierender Regen"
        71 -> "Leichter Schneefall"; 73 -> "Mäßiger Schneefall"; 75 -> "Starker Schneefall"
        77 -> "Schneekörner"
        80 -> "Leichte Regenschauer"; 81 -> "Mäßige Regenschauer"; 82 -> "Starke Regenschauer"
        85, 86 -> "Schneeschauer"; 95 -> "Gewitter"; 96, 99 -> "Gewitter mit Hagel"
        else -> "Unbekannt"
    }

    private fun wmoEn(code: Int) = when (code) {
        0 -> "Clear sky"; 1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"; in 51..57 -> "Drizzle"
        61 -> "Light rain"; 63 -> "Moderate rain"; 65 -> "Heavy rain"
        66, 67 -> "Freezing rain"; in 71..77 -> "Snowfall"
        in 80..82 -> "Rain showers"; 85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"; else -> "Unknown"
    }
}

/// Disk cache for raw payloads — port of the iOS Cache (FNV keys, TTLs).
object DiskCache {
    lateinit var dir: File

    fun init(context: Context) {
        dir = File(context.cacheDir, "lgka-cache").apply { mkdirs() }
    }

    fun key(url: String): String {
        var hash = -0x340d631b7bdddcdbL // FNV-1a offset basis
        for (b in url.toByteArray()) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(hash)
    }

    fun load(url: String): Pair<ByteArray, Long>? {
        val f = File(dir, key(url))
        if (!f.exists()) return null
        return f.readBytes() to (System.currentTimeMillis() - f.lastModified()) / 1000
    }

    fun store(data: ByteArray, url: String) = File(dir, key(url)).writeBytes(data)
}


/// New Year's Day fireworks — mirrors fireworks_overlay.dart (Jan 1, Berlin).
@androidx.compose.runtime.Composable
fun FireworksOverlay() {
    val isNewYear = androidx.compose.runtime.remember {
        val berlin = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Berlin"))
        berlin.monthValue == 1 && berlin.dayOfMonth == 1
    }
    if (!isNewYear) return
    var t by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            androidx.compose.runtime.withFrameNanos { now -> t = (now - start) / 1e9f }
        }
    }
    val colors = listOf(
        Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFFF06292),
        Color(0xFF4DD0E1), Color(0xFFBA68C8))
    androidx.compose.foundation.Canvas(
        androidx.compose.ui.Modifier.fillMaxSize()) {
        // simple radial bursts cycling every 2.2s from varying origins
        val burst = (t / 2.2f).toInt()
        val phase = (t % 2.2f) / 2.2f
        val rnd = java.util.Random(burst.toLong())
        val cx = size.width * (0.2f + rnd.nextFloat() * 0.6f)
        val cy = size.height * (0.15f + rnd.nextFloat() * 0.3f)
        val color = colors[burst % colors.size]
        for (i in 0 until 42) {
            val angle = i / 42f * (Math.PI * 2).toFloat()
            val dist = phase * (140f + rnd.nextFloat() * 120f)
            val alpha = (1f - phase).coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = alpha * 0.9f), radius = 5f * (1f - phase * 0.5f),
                       center = androidx.compose.ui.geometry.Offset(
                           cx + kotlin.math.cos(angle) * dist,
                           cy + kotlin.math.sin(angle) * dist + phase * phase * 90f))
        }
    }
}
