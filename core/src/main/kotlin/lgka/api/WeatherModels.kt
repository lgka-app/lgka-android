package lgka.api

import kotlinx.serialization.Serializable

// ── weather ──────────────────────────────────────────────────────────────────

@Serializable
data class CurrentWeather(
    val temp: Double = 0.0,
    val feelsLike: Double = 0.0,
    val humidity: Int = 0,
    val windSpeed: Double = 0.0,
    val windDeg: Int = 0,
    val windGust: Double = 0.0,
    val pressure: Int = 0,
    val clouds: Int = 0,
    val visibility: Double = 0.0,
    val uvi: Double = 0.0,
    val weatherCode: Int = 0,
    val isDay: Boolean = true,
    /** Local Europe/Berlin "YYYY-MM-DDTHH:MM". */
    val dt: String = "",
    /** "school" | "open-meteo" */
    val provider: String = "open-meteo",
)

@Serializable
data class HourlyForecast(
    val dt: String,
    val temp: Double = 0.0,
    val humidity: Int = 0,
    val windSpeed: Double = 0.0,
    val windDeg: Int = 0,
    /** 0..1 */
    val pop: Double = 0.0,
    val weatherCode: Int = 0,
    val isDay: Boolean = true,
)

@Serializable
data class DailyForecast(
    val dt: String,
    val sunrise: String = "",
    val sunset: String = "",
    val tempMax: Double = 0.0,
    val tempMin: Double = 0.0,
    val pop: Double = 0.0,
    val uvi: Double = 0.0,
    val windSpeed: Double = 0.0,
    val weatherCode: Int = 0,
)

@Serializable
data class StationSample(
    val time: String,
    val windSpeed: Double = 0.0,
    val windDeg: Int = 0,
    val temp: Double = 0.0,
    val humidity: Double = 0.0,
    val precipitation: Double = 0.0,
    val pressure: Double = 0.0,
    val radiation: Double = 0.0,
)

/** The school's rooftop station: used for `current` only while `healthy`. */
@Serializable
data class Station(
    val healthy: Boolean = false,
    val reason: String? = null,
    val updatedAt: String? = null,
    val rows: Int = 0,
    val latest: StationSample? = null,
    val today: List<StationSample> = emptyList(),
    val units: Map<String, String> = emptyMap(),
)

@Serializable
data class Weather(
    /** Who provides `current`: "school" | "open-meteo". */
    val source: String = "open-meteo",
    val sources: Map<String, String> = emptyMap(),
    val timezone: String = "Europe/Berlin",
    val current: CurrentWeather = CurrentWeather(),
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
    val station: Station = Station(),
    val attribution: List<String> = emptyList(),
) {
    val fromSchoolStation: Boolean get() = source == "school"
}
