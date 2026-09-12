package com.lgka

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lgka.Events
import lgka.Extractor
import lgka.News
import lgka.ScheduleHtml
import lgka.Weather
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class FetchMode { CacheFirst, CacheAny, Refresh }

class NotAuthenticatedException : IOException("not authenticated")
class HttpStatusException(val status: Int, url: String) : IOException("HTTP $status for $url")

/**
 * Serializes concurrent requests for the same URL: bootstrap, the foreground
 * refresh and the periodic loop may ask for one resource at once; only the
 * first hits the network, the rest await its result.
 */
class Downloader(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val inFlight = HashMap<String, Deferred<ByteArray>>()

    suspend fun get(url: String, fetch: suspend () -> ByteArray): ByteArray {
        val deferred = mutex.withLock {
            inFlight[url] ?: scope.async { fetch() }.also { d ->
                inFlight[url] = d
                d.invokeOnCompletion {
                    scope.launch { mutex.withLock { if (inFlight[url] === d) inFlight.remove(url) } }
                }
            }
        }
        return deferred.await()
    }
}

/// UI-facing data access on the verified :core parsers — port of the iOS
/// SchoolAPI. Fetch + disk cache + reshape parser maps into UI models.
class SchoolApi(
    private val credentials: Credentials,
    private val cache: DiskCache,
    private val downloader: Downloader,
) {
    companion object {
        private const val TAG = "SchoolApi"
        const val BASE = "https://lessing-gymnasium-karlsruhe.de"
        const val HOST = "lessing-gymnasium-karlsruhe.de"
        /** Same User-Agent format the Flutter app sends (app_info.dart). */
        val userAgent = "LGKA-App-Luka-Loehr/" + BuildConfig.VERSION_NAME
        val berlin: ZoneId = ZoneId.of("Europe/Berlin")

        fun isSchoolHost(host: String?): Boolean =
            host != null && (host == HOST || host.endsWith(".$HOST"))

        const val WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=49.00775&longitude=8.375&elevation=122" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,cloud_cover,visibility,uv_index,is_day" +
            "&hourly=temperature_2m,relative_humidity_2m,weather_code,precipitation_probability,wind_speed_10m,wind_direction_10m,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,wind_speed_10m_max,sunrise,sunset" +
            "&timezone=Europe%2FBerlin&forecast_days=3"
    }

    object TTL {
        const val SUBSTITUTION = 60L
        const val SCHEDULES = 24 * 3600L
        const val NEWS = 3600L
        const val WEATHER = 60L // CacheService parity (1 min)
        const val EVENTS = 3600L
    }

    private fun open(url: String, authorization: String?, method: String = "GET"): HttpURLConnection {
        val target = URL(url)
        val conn = target.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", userAgent)
        if (authorization != null && isSchoolHost(target.host)) {
            conn.setRequestProperty("Authorization", authorization)
        }
        return conn
    }

    private suspend fun fetch(url: String, authenticated: Boolean): ByteArray = withContext(Dispatchers.IO) {
        val authorization = if (authenticated) {
            (credentials.load() ?: throw NotAuthenticatedException()).authorizationHeader
        } else null
        val conn = open(url, authorization)
        try {
            val status = conn.responseCode
            if (status != 200) {
                Log.w(TAG, "GET $url -> $status")
                throw HttpStatusException(status, url)
            }
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** Login gate: verified against the server (HEAD on the substitution PDF), never locally. */
    suspend fun verify(pair: Credentials.Pair): Boolean = withContext(Dispatchers.IO) {
        val conn = open("$BASE/stundenplan/schueler/v_schueler_heute.pdf", pair.authorizationHeader, "HEAD")
        try {
            when (val status = conn.responseCode) {
                200 -> true
                401, 403 -> false
                else -> throw HttpStatusException(status, "verify")
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun cachedGet(url: String, authenticated: Boolean = true, ttl: Long, mode: FetchMode): ByteArray {
        val cached = cache.load(url)
        when (mode) {
            FetchMode.CacheAny -> if (cached != null) return cached.first
            FetchMode.CacheFirst -> if (cached != null && cached.second < ttl) return cached.first
            FetchMode.Refresh -> {}
        }
        return try {
            downloader.get(url) { fetch(url, authenticated) }.also { cache.store(it, url) }
        } catch (e: IOException) {
            if (cached != null) {
                Log.i(TAG, "using stale cache for $url: $e")
                cached.first
            } else throw e
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
        /** Mirrors SubstitutionState.canDisplay. */
        val canDisplay: Boolean
            get() = !isEmpty && weekday != null && weekday != "weekend" && planDate != null && file != null
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun substitutionPlan(today: Boolean, mode: FetchMode = FetchMode.CacheFirst): SubPlan =
        withContext(Dispatchers.IO) {
            val name = if (today) "heute" else "morgen"
            val url = "$BASE/stundenplan/schueler/v_schueler_$name.pdf"
            val data = cachedGet(url, ttl = TTL.SUBSTITUTION, mode = mode)
            val file = File(cache.dir, "sub_$name.pdf").apply { writeBytes(data) }
            val dict = try {
                Extractor.extract(extractLinesAndroid(file))
            } catch (e: Exception) {
                // A payload that cannot be parsed must not be served from the
                // cache again on the next cold start.
                cache.remove(url)
                throw e
            }
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

    data class Schedule(val title: String, val halbjahr: String, val gradeLevel: String, val fullUrl: String)

    suspend fun schedules(mode: FetchMode = FetchMode.CacheFirst): List<Schedule> = withContext(Dispatchers.IO) {
        val url = "$BASE/cm3/index.php/unterricht/stundenplan"
        val data = cachedGet(url, ttl = TTL.SCHEDULES, mode = mode)
        try {
            ScheduleHtml.parse(String(data)).map {
                Schedule(it["title"] as? String ?: "", it["halbjahr"] as? String ?: "",
                         it["gradeLevel"] as? String ?: "", it["fullUrl"] as? String ?: "")
            }
        } catch (e: Exception) {
            cache.remove(url)
            throw e
        }
    }

    suspend fun schedulePdf(schedule: Schedule, mode: FetchMode = FetchMode.CacheFirst): Pair<File, Map<String, Int>> =
        withContext(Dispatchers.IO) {
            val data = cachedGet(schedule.fullUrl, ttl = TTL.SCHEDULES, mode = mode)
            val file = File(cache.dir, "schedule_${DiskCache.key(schedule.fullUrl)}.pdf").apply { writeBytes(data) }
            if (schedule.gradeLevel == "J11/J12") {
                file to mapOf("j11" to 2, "j12" to 3) // app-constant, never parsed
            } else {
                file to buildClassIndexAndroid(file)
            }
        }

    // ── News ────────────────────────────────────────────────────────────────

    suspend fun newsList(mode: FetchMode = FetchMode.CacheFirst): List<News.Metadata> = withContext(Dispatchers.IO) {
        val data = cachedGet("$BASE/cm3/index.php/neues", authenticated = false, ttl = TTL.NEWS, mode = mode)
        News.parseListPage(String(data))
    }

    suspend fun article(url: String, mode: FetchMode = FetchMode.CacheFirst): News.Article = withContext(Dispatchers.IO) {
        val data = cachedGet(url, authenticated = false, ttl = TTL.NEWS, mode = mode)
        News.parseArticle(String(data))
    }

    // ── Events ──────────────────────────────────────────────────────────────

    data class Event(val date: String, val time: String?, val title: String)

    suspend fun events(mode: FetchMode = FetchMode.CacheFirst): List<Event> = withContext(Dispatchers.IO) {
        val today = LocalDate.now(berlin)
        val htmls = (0 until 3).map { week ->
            val path = today.plusDays(week * 7L).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            val url = "$BASE/cm3/index.php/termine/week.listevents/$path/-?catids="
            String(cachedGet(url, authenticated = false, ttl = TTL.EVENTS, mode = mode))
        }
        Events.aggregate(htmls, today).map {
            Event(date = (it["date"] as? String ?: "").take(10), time = it["time"] as? String,
                  title = it["title"] as? String ?: "")
        }
    }

    // ── Weather ─────────────────────────────────────────────────────────────

    data class Hour(val time: String, val temp: Double, val pop: Double, val code: Int, val isDay: Boolean)
    data class Day(val date: String, val tempMax: Double, val tempMin: Double, val pop: Double, val code: Int)
    data class WeatherData(
        val temp: Double, val feelsLike: Double, val humidity: Int,
        val windSpeed: Double, val pressure: Int, val uvi: Double,
        val code: Int, val isDay: Boolean, val hourly: List<Hour>, val daily: List<Day>,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun weather(mode: FetchMode = FetchMode.CacheFirst): WeatherData = withContext(Dispatchers.IO) {
        val data = cachedGet(WEATHER_URL, authenticated = false, ttl = TTL.WEATHER, mode = mode)
        // Open-Meteo returns Europe/Berlin wall-clock times; the window must
        // start at the current Berlin hour, not the device's zone.
        val refNow = LocalDateTime.now(berlin).withMinute(0).withSecond(0).withNano(0)
        val parsed = try {
            Weather.parse(String(data), refNow)
        } catch (e: Exception) {
            cache.remove(WEATHER_URL)
            throw e
        }
        val c = parsed["current"] as? Map<String, Any?> ?: emptyMap()
        fun d(v: Any?) = (v as? Number)?.toDouble() ?: 0.0
        fun i(v: Any?) = (v as? Number)?.toInt() ?: 0
        WeatherData(
            temp = d(c["temp"]), feelsLike = d(c["feelsLike"]), humidity = i(c["humidity"]),
            windSpeed = d(c["windSpeed"]), pressure = i(c["pressure"]), uvi = d(c["uvi"]),
            code = i(c["weatherCode"]), isDay = c["isDay"] as? Boolean ?: true,
            hourly = (parsed["hourly"] as? List<Map<String, Any?>> ?: emptyList()).map { h ->
                Hour(time = (h["dt"] as? String ?: "").drop(11).take(5), temp = d(h["temp"]),
                     pop = d(h["pop"]), code = i(h["weatherCode"]), isDay = h["isDay"] as? Boolean ?: true)
            },
            daily = (parsed["daily"] as? List<Map<String, Any?>> ?: emptyList()).map { day ->
                Day(date = (day["dt"] as? String ?: "").take(10), tempMax = d(day["tempMax"]),
                    tempMin = d(day["tempMin"]), pop = d(day["pop"]), code = i(day["weatherCode"]))
            })
    }
}
