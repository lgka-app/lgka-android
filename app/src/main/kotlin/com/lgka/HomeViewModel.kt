package com.lgka

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lgka.api.Embed
import lgka.api.Events
import lgka.api.LgkaApi
import lgka.api.News
import lgka.api.NewsArticle
import lgka.api.Resource
import lgka.api.ScheduleItem
import lgka.api.Schedules
import lgka.api.SchoolEvent
import lgka.api.Substitutions
import lgka.api.SyncStatus
import lgka.api.SyncStore
import lgka.api.UnauthorizedException
import lgka.api.Weather
import lgka.api.hourlyWindow
import lgka.api.preferredGroup
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Home hub state on top of api.lgka.app: the on-disk copy renders instantly,
 * then ONE `/v1/sync` call carries the hashes we hold and brings back only
 * what changed. Survives configuration changes as a ViewModel.
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"
        val berlin: ZoneId = ZoneId.of("Europe/Berlin")
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }

    private val api: LgkaApi get() = container.api
    private val store: SyncStore get() = container.store

    var substitutions by mutableStateOf<Substitutions?>(null); private set
    var schedules by mutableStateOf<Schedules?>(null); private set
    var news by mutableStateOf<News?>(null); private set
    var events by mutableStateOf<Events?>(null); private set
    var weatherData by mutableStateOf<Weather?>(null); private set

    /** True while the very first sync runs and nothing is on disk yet. */
    var syncing by mutableStateOf(false); private set
    /** Last sync failed (network / server); shown only where nothing is cached. */
    var syncFailed by mutableStateOf(false); private set
    /** Resources the server reported as unavailable in the last sync. */
    var unavailable by mutableStateOf<Set<Resource>>(emptySet()); private set

    private val syncMutex = Mutex()
    private var bootstrapped = false
    private var lastSyncAt = 0L

    // ── derived views ────────────────────────────────────────────────────────

    val today get() = substitutions?.today
    val tomorrow get() = substitutions?.tomorrow
    val subLoading get() = substitutions == null && syncing
    val subError get() = substitutions == null && !syncing && (syncFailed || Resource.Substitutions in unavailable)

    val scheduleItems: List<ScheduleItem> get() = schedules?.items ?: emptyList()
    val scheduleLoading get() = schedules == null && syncing
    val scheduleError get() = schedules == null && !syncing && (syncFailed || Resource.Schedules in unavailable)
    /** Prefer the 2. Halbjahr once published. */
    val preferredGroup: List<ScheduleItem> get() = schedules?.preferredGroup() ?: emptyList()

    val eventList: List<SchoolEvent> get() = events?.events ?: emptyList()
    val eventsLoading get() = events == null && syncing
    val eventsError get() = events == null && !syncing && (syncFailed || Resource.Events in unavailable)

    val newsList: List<NewsArticle>? get() = news?.articles
    val newsFailed get() = news == null && !syncing && (syncFailed || Resource.News in unavailable)

    /** Weather as the screens consume it: current conditions + a 24 h hourly window. */
    val weather: WeatherUi? get() = weatherData?.let { WeatherUi.from(it, LocalDateTime.now(berlin)) }
    val weatherError get() = weatherData == null && !syncing && (syncFailed || Resource.Weather in unavailable)

    fun article(url: String): NewsArticle? = newsList?.firstOrNull { it.url == url }

    // ── lifecycle ────────────────────────────────────────────────────────────

    fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true
        viewModelScope.launch {
            loadFromDisk()
            sync()
        }
    }

    /** Resumed: one cheap sync unless we just did one. */
    fun refreshOnForeground() {
        if (!bootstrapped || System.currentTimeMillis() - lastSyncAt < 10_000) return
        viewModelScope.launch { sync() }
    }

    /** Pull-to-refresh and per-section retries. */
    suspend fun refresh(only: Set<Resource>? = null) = sync(only)

    fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    private suspend fun loadFromDisk() = withContext(Dispatchers.IO) {
        val s = store.load<Substitutions>(Resource.Substitutions)?.data
        val sch = store.load<Schedules>(Resource.Schedules)?.data
        val n = store.load<News>(Resource.News)?.data
        val e = store.load<Events>(Resource.Events)?.data
        val w = store.load<Weather>(Resource.Weather)?.data
        withContext(Dispatchers.Main) {
            substitutions = s; schedules = sch; news = n; events = e; weatherData = w
        }
    }

    /**
     * The one network call: hashes out, changes in, every mirrored PDF inline
     * ([pdfFile] only fetches one if a payload ever arrives without bytes).
     *
     * A 401 is confirmed with one `/v1/auth/check` before it counts: if that
     * also says 401 the school rotated the password → the credentials go, the
     * root navigation shows the login with a hint, and the on-disk snapshot
     * stays (public school data; it is back after re-login).
     */
    suspend fun sync(only: Set<Resource>? = null) {
        val login = container.credentials.load() ?: return
        syncMutex.withLock {
            syncing = substitutions == null && schedules == null && news == null && events == null && weatherData == null
            try {
                val hashes = withContext(Dispatchers.IO) { store.hashes() }
                val response = api.sync(login, hashes, only = only, embed = Embed.AllPdf)
                val statuses = withContext(Dispatchers.IO) { store.apply(response) }
                if (statuses.values.any { it == SyncStatus.Updated }) loadFromDisk()
                unavailable = statuses.filterValues { it == SyncStatus.Unavailable }.keys
                syncFailed = false
                lastSyncAt = System.currentTimeMillis()
            } catch (e: UnauthorizedException) {
                if (api.confirmUnauthorized(login)) {
                    Log.w(TAG, "school credentials rotated; back to login (snapshot kept)")
                    container.prefs.passwordRotated = true
                    container.prefs.signOut(container.credentials)
                } else {
                    Log.w(TAG, "transient 401 on sync; keeping the session")
                    syncFailed = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
                syncFailed = true
            } finally {
                syncing = false
            }
        }
    }

    /** The mirrored PDF for a resource file: from disk, else fetched once from the API. */
    suspend fun pdfFile(sha256: String, apiPath: String): File = withContext(Dispatchers.IO) {
        store.pdfFile(sha256) ?: run {
            val login = container.credentials.load() ?: throw UnauthorizedException()
            store.putPdf(sha256, api.file(login, apiPath))
        }
    }
}

/** What the weather screens render — a flat view over [Weather]. */
data class WeatherUi(
    val temp: Double, val feelsLike: Double, val humidity: Int,
    val windSpeed: Double, val pressure: Int, val uvi: Double,
    val code: Int, val isDay: Boolean,
    val hourly: List<Hour>, val daily: List<Day>,
    /** "school" | "open-meteo" — who measured `temp` & co. */
    val source: String,
    val attribution: List<String>,
    /** Station reading time (ISO) when the school station is the source. */
    val stationUpdatedAt: String?,
) {
    data class Hour(val time: String, val temp: Double, val pop: Double, val code: Int, val isDay: Boolean)
    data class Day(val date: String, val tempMax: Double, val tempMin: Double, val pop: Double, val code: Int)

    val fromSchoolStation: Boolean get() = source == "school"

    companion object {
        fun from(w: Weather, nowLocal: LocalDateTime): WeatherUi = WeatherUi(
            temp = w.current.temp, feelsLike = w.current.feelsLike, humidity = w.current.humidity,
            windSpeed = w.current.windSpeed, pressure = w.current.pressure, uvi = w.current.uvi,
            code = w.current.weatherCode, isDay = w.current.isDay,
            hourly = w.hourlyWindow(nowLocal).map { Hour(it.dt.drop(11).take(5), it.temp, it.pop, it.weatherCode, it.isDay) },
            daily = w.daily.map { Day(it.dt.take(10), it.tempMax, it.tempMin, it.pop, it.weatherCode) },
            source = w.source,
            attribution = w.attribution,
            stationUpdatedAt = if (w.fromSchoolStation) w.station.updatedAt else null,
        )
    }
}
