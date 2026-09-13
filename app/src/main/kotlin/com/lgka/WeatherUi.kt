package com.lgka

import lgka.api.Weather
import lgka.api.hourlyWindow
import java.time.LocalDateTime

/** What the weather screens render — a flat view over [Weather]. */
data class WeatherUi(
    val temp: Double, val feelsLike: Double, val humidity: Int,
    val windSpeed: Double, val pressure: Int, val uvi: Double,
    val code: Int, val isDay: Boolean,
    val hourly: List<Hour>, val daily: List<Day>,
    /** "school" | "open-meteo" — who measured `temp` & co. */
    val source: String,
    val attribution: List<String>,
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
        )
    }
}
