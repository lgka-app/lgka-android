package com.lgka

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lgka.Events
import lgka.Extractor
import lgka.News
import lgka.ScheduleHtml
import lgka.Weather
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class FetchMode { CacheFirst, CacheAny, Refresh }

/// UI-facing data access on the verified :core parsers — port of the iOS
/// SchoolAPI. Fetch + disk cache + reshape parser maps into UI models.
object SchoolApi {
    const val BASE = "https://lessing-gymnasium-karlsruhe.de"
    private val auth =
        "Basic " + Base64.encodeToString("vertretungsplan:ephraim".toByteArray(), Base64.NO_WRAP)

    object TTL {
        const val SUBSTITUTION = 60L
        const val SCHEDULES = 24 * 3600L
        const val NEWS = 3600L
        const val WEATHER = 3600L
        const val EVENTS = 3600L
    }

    private suspend fun fetch(url: String, authenticated: Boolean): ByteArray =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "LGKA+/3.0.0")
            if (authenticated) conn.setRequestProperty("Authorization", auth)
            try {
                if (conn.responseCode != 200) throw RuntimeException("HTTP ${conn.responseCode}")
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }

    suspend fun cachedGet(url: String, authenticated: Boolean = true,
                          ttl: Long, mode: FetchMode): ByteArray {
        val cached = DiskCache.load(url)
        when (mode) {
            FetchMode.CacheAny -> if (cached != null) return cached.first
            FetchMode.CacheFirst -> if (cached != null && cached.second < ttl) return cached.first
            FetchMode.Refresh -> {}
        }
        return try {
            fetch(url, authenticated).also { DiskCache.store(it, url) }
        } catch (e: Exception) {
            cached?.first ?: throw e // stale fallback
        }
    }

    // ── Substitution ────────────────────────────────────────────────────────

    data class SubEntry(
        val type: String?, val period: String?, val classes: List<String>,
        val substitute: String?, val subject: String?, val room: String?,
        val note: String?,
    )

    data class SubPlan(
        val weekday: String?, val planDate: String?, val generatedAt: String?,
        val isEmpty: Boolean, val announcements: List<String>,
        val absentTeachers: List<String>, val absentClasses: List<String>,
        val entries: List<SubEntry>, val file: File?,
    ) {
        val canDisplay: Boolean
            get() = !isEmpty && weekday != null && weekday != "weekend"
                && planDate != null && file != null
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun substitutionPlan(today: Boolean, mode: FetchMode = FetchMode.CacheFirst): SubPlan =
        withContext(Dispatchers.IO) {
            val name = if (today) "heute" else "morgen"
            val data = cachedGet("$BASE/stundenplan/schueler/v_schueler_$name.pdf",
                                 ttl = TTL.SUBSTITUTION, mode = mode)
            val file = File(DiskCache.dir, "sub_$name.pdf").apply { writeBytes(data) }
            val dict = Extractor.extract(extractLinesAndroid(file))
            fun s(k: String) = dict[k] as? String
            SubPlan(
                weekday = s("weekday"), planDate = s("planDate"), generatedAt = s("generatedAt"),
                isEmpty = dict["isEmpty"] as? Boolean ?: false,
                announcements = dict["announcements"] as? List<String> ?: emptyList(),
                absentTeachers = dict["absentTeachers"] as? List<String> ?: emptyList(),
                absentClasses = dict["absentClasses"] as? List<String> ?: emptyList(),
                entries = (dict["entries"] as? List<Map<String, Any?>> ?: emptyList()).map { e ->
                    SubEntry(
                        type = e["type"] as? String, period = e["period"] as? String,
                        classes = e["classes"] as? List<String> ?: emptyList(),
                        substitute = e["substitute"] as? String,
                        subject = e["subject"] as? String, room = e["room"] as? String,
                        note = e["note"] as? String)
                },
                file = file)
        }

    // ── Schedule ────────────────────────────────────────────────────────────

    data class Schedule(val title: String, val halbjahr: String,
                        val gradeLevel: String, val fullUrl: String)

    suspend fun schedules(mode: FetchMode = FetchMode.CacheFirst): List<Schedule> =
        withContext(Dispatchers.IO) {
            val data = cachedGet("$BASE/cm3/index.php/unterricht/stundenplan",
                                 ttl = TTL.SCHEDULES, mode = mode)
            ScheduleHtml.parse(String(data)).map {
                Schedule(it["title"] as? String ?: "", it["halbjahr"] as? String ?: "",
                         it["gradeLevel"] as? String ?: "", it["fullUrl"] as? String ?: "")
            }
        }

    suspend fun schedulePdf(schedule: Schedule,
                            mode: FetchMode = FetchMode.CacheFirst): Pair<File, Map<String, Int>> =
        withContext(Dispatchers.IO) {
            val data = cachedGet(schedule.fullUrl, ttl = TTL.SCHEDULES, mode = mode)
            val file = File(DiskCache.dir, "schedule_${DiskCache.key(schedule.fullUrl)}.pdf")
                .apply { writeBytes(data) }
            if (schedule.gradeLevel == "J11/J12") {
                file to mapOf("j11" to 2, "j12" to 3) // app-constant, never parsed
            } else {
                file to buildClassIndexAndroid(file)
            }
        }

    // ── News ────────────────────────────────────────────────────────────────

    suspend fun newsList(mode: FetchMode = FetchMode.CacheFirst): List<News.Metadata> =
        withContext(Dispatchers.IO) {
            val data = cachedGet("$BASE/cm3/index.php/neues", authenticated = false,
                                 ttl = TTL.NEWS, mode = mode)
            News.parseListPage(String(data))
        }

    suspend fun article(url: String, mode: FetchMode = FetchMode.CacheFirst): News.Article =
        withContext(Dispatchers.IO) {
            val data = cachedGet(url, authenticated = false, ttl = TTL.NEWS, mode = mode)
            News.parseArticle(String(data))
        }

    // ── Events ──────────────────────────────────────────────────────────────

    data class Event(val date: String, val time: String?, val title: String)

    suspend fun events(mode: FetchMode = FetchMode.CacheFirst): List<Event> =
        withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            val htmls = (0 until 3).map { week ->
                val target = today.plusDays(week * 7L)
                val path = target.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                val url = "$BASE/cm3/index.php/termine/week.listevents/$path/-?catids="
                String(cachedGet(url, authenticated = false, ttl = TTL.EVENTS, mode = mode))
            }
            Events.aggregate(htmls, today).map {
                Event(date = (it["date"] as? String ?: "").take(10),
                      time = it["time"] as? String,
                      title = it["title"] as? String ?: "")
            }
        }

    // ── Weather ─────────────────────────────────────────────────────────────

    data class Hour(val time: String, val temp: Double, val pop: Double,
                    val code: Int, val isDay: Boolean)
    data class Day(val date: String, val tempMax: Double, val tempMin: Double,
                   val pop: Double, val code: Int)
    data class WeatherData(
        val temp: Double, val feelsLike: Double, val humidity: Int,
        val windSpeed: Double, val pressure: Int, val uvi: Double,
        val code: Int, val isDay: Boolean, val hourly: List<Hour>, val daily: List<Day>)

    @Suppress("UNCHECKED_CAST")
    suspend fun weather(mode: FetchMode = FetchMode.CacheFirst): WeatherData =
        withContext(Dispatchers.IO) {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=49.00775&longitude=8.375&elevation=122" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,cloud_cover,visibility,uv_index,is_day" +
                "&hourly=temperature_2m,relative_humidity_2m,weather_code,precipitation_probability,wind_speed_10m,wind_direction_10m,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,wind_speed_10m_max,sunrise,sunset" +
                "&timezone=Europe%2FBerlin&forecast_days=3"
            val data = cachedGet(url, authenticated = false, ttl = TTL.WEATHER, mode = mode)
            val refNow = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
            val parsed = Weather.parse(String(data), refNow)
            val c = parsed["current"] as Map<String, Any?>
            fun d(v: Any?) = (v as? Number)?.toDouble() ?: 0.0
            fun i(v: Any?) = (v as? Number)?.toInt() ?: 0
            WeatherData(
                temp = d(c["temp"]), feelsLike = d(c["feelsLike"]), humidity = i(c["humidity"]),
                windSpeed = d(c["windSpeed"]), pressure = i(c["pressure"]), uvi = d(c["uvi"]),
                code = i(c["weatherCode"]), isDay = c["isDay"] as? Boolean ?: true,
                hourly = (parsed["hourly"] as List<Map<String, Any?>>).map { h ->
                    Hour(time = (h["dt"] as? String ?: "").drop(11).take(5),
                         temp = d(h["temp"]), pop = d(h["pop"]),
                         code = i(h["weatherCode"]), isDay = h["isDay"] as? Boolean ?: true)
                },
                daily = (parsed["daily"] as List<Map<String, Any?>>).map { day ->
                    Day(date = (day["dt"] as? String ?: "").take(10),
                        tempMax = d(day["tempMax"]), tempMin = d(day["tempMin"]),
                        pop = d(day["pop"]), code = i(day["weatherCode"]))
                })
        }
}
