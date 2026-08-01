package com.lgka

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import lgka.News

/// Shared preloading store — port of the iOS HomeModel: cached data appears
/// instantly, TTL-based refresh in background, news articles prefetched.
object HomeModel {
    var today by mutableStateOf<SchoolApi.SubPlan?>(null)
    var tomorrow by mutableStateOf<SchoolApi.SubPlan?>(null)
    var subError by mutableStateOf(false)
    var subLoading by mutableStateOf(true)

    var weather by mutableStateOf<SchoolApi.WeatherData?>(null)
    var weatherError by mutableStateOf(false)

    var schedules by mutableStateOf<List<SchoolApi.Schedule>>(emptyList())
    var scheduleError by mutableStateOf(false)
    var scheduleLoading by mutableStateOf(true)

    var events by mutableStateOf<List<SchoolApi.Event>>(emptyList())
    var eventsError by mutableStateOf(false)
    var eventsLoading by mutableStateOf(true)

    var newsList by mutableStateOf<List<News.Metadata>?>(null)
    var newsFailed by mutableStateOf(false)

    private var bootstrapped = false

    suspend fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true
        loadAll(FetchMode.CacheAny)
        loadAll(FetchMode.CacheFirst)
        prefetchArticles()
    }

    suspend fun loadAll(mode: FetchMode = FetchMode.CacheFirst) = coroutineScope {
        listOf(
            async { loadSubstitution(mode) },
            async { loadWeather(mode) },
            async { loadSchedules(mode) },
            async { loadEvents(mode) },
            async { loadNews(mode) },
        ).awaitAll()
    }

    suspend fun loadSubstitution(mode: FetchMode = FetchMode.CacheFirst) {
        subLoading = today == null && tomorrow == null
        try {
            coroutineScope {
                val t = async { SchoolApi.substitutionPlan(true, mode) }
                val m = async { SchoolApi.substitutionPlan(false, mode) }
                today = t.await()
                tomorrow = m.await()
            }
            subError = false
        } catch (e: Exception) {
            if (today == null) subError = true
        }
        subLoading = false
    }

    suspend fun loadWeather(mode: FetchMode = FetchMode.CacheFirst) {
        try {
            weather = SchoolApi.weather(mode)
            weatherError = false
        } catch (e: Exception) {
            if (weather == null) weatherError = true
        }
    }

    suspend fun loadSchedules(mode: FetchMode = FetchMode.CacheFirst) {
        scheduleLoading = schedules.isEmpty()
        try {
            schedules = SchoolApi.schedules(mode)
            scheduleError = false
        } catch (e: Exception) {
            if (schedules.isEmpty()) scheduleError = true
        }
        scheduleLoading = false
    }

    suspend fun loadEvents(mode: FetchMode = FetchMode.CacheFirst) {
        eventsLoading = events.isEmpty()
        try {
            events = SchoolApi.events(mode)
            eventsError = false
        } catch (e: Exception) {
            if (events.isEmpty()) eventsError = true
        }
        eventsLoading = false
    }

    suspend fun loadNews(mode: FetchMode = FetchMode.CacheFirst) {
        try {
            newsList = SchoolApi.newsList(mode)
            newsFailed = false
        } catch (e: Exception) {
            if (newsList == null) newsFailed = true
        }
    }

    private suspend fun prefetchArticles() = coroutineScope {
        (newsList ?: return@coroutineScope).take(20).map { md ->
            async { runCatching { SchoolApi.article(md.url) } }
        }.awaitAll()
    }

    /// Prefer 2. Halbjahr — mirrors the provider's active-group logic.
    val preferredGroup: List<SchoolApi.Schedule>
        get() {
            val second = schedules.filter { it.halbjahr == "2. Halbjahr" }
            return second.ifEmpty { schedules.filter { it.halbjahr == "1. Halbjahr" } }
        }
}
