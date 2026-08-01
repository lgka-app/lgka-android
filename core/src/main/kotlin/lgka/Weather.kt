package lgka

import com.google.gson.JsonParser
import java.time.Duration
import java.time.LocalDateTime

/**
 * Open-Meteo response mapper — Kotlin port of WeatherService.fetchAll's
 * parsing (weather_service.dart), verified against the weather goldens.
 *
 * referenceNow is the recorded hourly-window start from the golden params
 * (the app uses DateTime.now() truncated to the hour); the window is
 * [referenceNow, referenceNow + 24h).
 */
object Weather {
    fun parse(jsonText: String, referenceNow: LocalDateTime): LinkedHashMap<String, Any?> {
        val root = JsonParser.parseString(jsonText).asJsonObject

        val c = root.getAsJsonObject("current")
        val current = linkedMapOf<String, Any?>(
            "temp" to c["temperature_2m"].asDouble,
            "feelsLike" to c["apparent_temperature"].asDouble,
            "humidity" to c["relative_humidity_2m"].asInt,
            "windSpeed" to c["wind_speed_10m"].asDouble,
            "windDeg" to c["wind_direction_10m"].asInt,
            "windGust" to c["wind_gusts_10m"].asDouble,
            "pressure" to Math.round(c["pressure_msl"].asDouble).toInt(),
            "clouds" to c["cloud_cover"].asInt,
            "visibility" to c["visibility"].asDouble,
            "uvi" to c["uv_index"].asDouble,
            "weatherCode" to c["weather_code"].asInt,
            "isDay" to (c["is_day"].asInt == 1),
            "dt" to iso(LocalDateTime.parse(c["time"].asString)),
        )

        val h = root.getAsJsonObject("hourly")
        val hTimes = h.getAsJsonArray("time")
        val hourEnd = referenceNow.plus(Duration.ofHours(24))
        val hourly = mutableListOf<LinkedHashMap<String, Any?>>()
        for (i in 0 until hTimes.size()) {
            val dt = LocalDateTime.parse(hTimes[i].asString)
            if (!dt.isBefore(referenceNow) && dt.isBefore(hourEnd)) {
                hourly.add(linkedMapOf(
                    "dt" to iso(dt),
                    "temp" to h.getAsJsonArray("temperature_2m")[i].asDouble,
                    "humidity" to h.getAsJsonArray("relative_humidity_2m")[i].asInt,
                    "windSpeed" to h.getAsJsonArray("wind_speed_10m")[i].asDouble,
                    "windDeg" to h.getAsJsonArray("wind_direction_10m")[i].asInt,
                    "pop" to h.getAsJsonArray("precipitation_probability")[i].asDouble / 100.0,
                    "weatherCode" to h.getAsJsonArray("weather_code")[i].asInt,
                    "isDay" to (h.getAsJsonArray("is_day")[i].asInt == 1),
                ))
            }
        }

        val d = root.getAsJsonObject("daily")
        val dTimes = d.getAsJsonArray("time")
        val daily = mutableListOf<LinkedHashMap<String, Any?>>()
        for (i in 0 until dTimes.size()) {
            daily.add(linkedMapOf(
                "dt" to iso(LocalDateTime.parse(dTimes[i].asString + "T00:00")),
                "sunrise" to iso(LocalDateTime.parse(d.getAsJsonArray("sunrise")[i].asString)),
                "sunset" to iso(LocalDateTime.parse(d.getAsJsonArray("sunset")[i].asString)),
                "tempMax" to d.getAsJsonArray("temperature_2m_max")[i].asDouble,
                "tempMin" to d.getAsJsonArray("temperature_2m_min")[i].asDouble,
                "pop" to d.getAsJsonArray("precipitation_probability_max")[i].asDouble / 100.0,
                "uvi" to d.getAsJsonArray("uv_index_max")[i].asDouble,
                "windSpeed" to d.getAsJsonArray("wind_speed_10m_max")[i].asDouble,
                "weatherCode" to d.getAsJsonArray("weather_code")[i].asInt,
            ))
        }

        return linkedMapOf("current" to current, "hourly" to hourly, "daily" to daily)
    }

    /** Dart DateTime.toIso8601String() for a naive local time. */
    private fun iso(dt: LocalDateTime): String =
        "%04d-%02d-%02dT%02d:%02d:%02d.%03d".format(
            dt.year, dt.monthValue, dt.dayOfMonth,
            dt.hour, dt.minute, dt.second, dt.nano / 1_000_000)
}
