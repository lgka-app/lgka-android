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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import lgka.News

/// Home hub state — mirrors home_screen.dart / the iOS HomeModel: cached
/// data appears instantly, TTL-based refresh in the background, news
/// articles prefetched. Survives configuration changes as a ViewModel.
class HomeViewModel(private val api: SchoolApi) : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"
        fun factory(api: SchoolApi): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(api) }
        }
    }

    var today by mutableStateOf<SchoolApi.SubPlan?>(null); private set
    var tomorrow by mutableStateOf<SchoolApi.SubPlan?>(null); private set
    var subError by mutableStateOf(false); private set
    var subLoading by mutableStateOf(true); private set

    var weather by mutableStateOf<SchoolApi.WeatherData?>(null); private set
    var weatherError by mutableStateOf(false); private set

    var schedules by mutableStateOf<List<SchoolApi.Schedule>>(emptyList()); private set
    var scheduleError by mutableStateOf(false); private set
    var scheduleLoading by mutableStateOf(true); private set

    var events by mutableStateOf<List<SchoolApi.Event>>(emptyList()); private set
    var eventsError by mutableStateOf(false); private set
    var eventsLoading by mutableStateOf(true); private set

    var newsList by mutableStateOf<List<News.Metadata>?>(null); private set
    var newsFailed by mutableStateOf(false); private set

    private var bootstrapped = false
    private var bootstrapFinished = false
    private var lastForegroundRefresh = 0L

    fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true
        viewModelScope.launch {
            loadAll(FetchMode.CacheAny)
            loadAll(FetchMode.CacheFirst)
            bootstrapFinished = true
            lastForegroundRefresh = System.currentTimeMillis()
            prefetchArticles()
        }
    }

    /** Resumed: refresh substitution + weather unless bootstrap runs or we just did. */
    fun refreshOnForeground() {
        if (!bootstrapFinished || System.currentTimeMillis() - lastForegroundRefresh < 10_000) return
        lastForegroundRefresh = System.currentTimeMillis()
        viewModelScope.launch {
            coroutineScope {
                listOf(async { loadSubstitution(FetchMode.Refresh) },
                       async { loadWeather(FetchMode.Refresh) }).awaitAll()
            }
        }
    }

    suspend fun loadAll(mode: FetchMode = FetchMode.CacheFirst) = coroutineScope {
        listOf(
            async { loadSubstitution(mode) },
            async { loadWeather(mode) },
            async { loadSchedules(mode) },
            async { loadEvents(mode) },
            async { loadNews(mode) },
        ).awaitAll()
        Unit
    }

    /** Runs [block]; logs non-cancellation failures and reports success. */
    private suspend inline fun guarded(what: String, block: () -> Unit): Boolean = try {
        block(); true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what failed", e); false
    }

    suspend fun loadSubstitution(mode: FetchMode = FetchMode.CacheFirst) {
        subLoading = today == null && tomorrow == null
        val ok = guarded("substitution") {
            coroutineScope {
                val t = async { api.substitutionPlan(true, mode) }
                val m = async { api.substitutionPlan(false, mode) }
                today = t.await()
                tomorrow = m.await()
            }
        }
        subError = if (ok) false else today == null
        subLoading = false
    }

    suspend fun loadWeather(mode: FetchMode = FetchMode.CacheFirst) {
        val ok = guarded("weather") { weather = api.weather(mode) }
        weatherError = if (ok) false else weather == null
    }

    suspend fun loadSchedules(mode: FetchMode = FetchMode.CacheFirst) {
        scheduleLoading = schedules.isEmpty()
        val ok = guarded("schedules") { schedules = api.schedules(mode) }
        scheduleError = if (ok) false else schedules.isEmpty()
        scheduleLoading = false
    }

    suspend fun loadEvents(mode: FetchMode = FetchMode.CacheFirst) {
        eventsLoading = events.isEmpty()
        val ok = guarded("events") { events = api.events(mode) }
        eventsError = if (ok) false else events.isEmpty()
        eventsLoading = false
    }

    suspend fun loadNews(mode: FetchMode = FetchMode.CacheFirst) {
        val ok = guarded("news") { newsList = api.newsList(mode) }
        newsFailed = if (ok) false else newsList == null
    }

    private suspend fun prefetchArticles() = coroutineScope {
        (newsList ?: return@coroutineScope).take(20).map { md ->
            async { guarded("prefetch ${md.url}") { api.article(md.url) } }
        }.awaitAll()
    }

    /// Prefer 2. Halbjahr — mirrors the provider's active-group logic.
    val preferredGroup: List<SchoolApi.Schedule>
        get() {
            val second = schedules.filter { it.halbjahr == "2. Halbjahr" }
            return second.ifEmpty { schedules.filter { it.halbjahr == "1. Halbjahr" } }
        }

    fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}
