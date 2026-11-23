package dev.utouch.launcher.data

import androidx.annotation.StringRes
import dev.utouch.launcher.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Current conditions, as shown on the "Today" scope. */
data class Weather(
    val temperature: Int,
    val code: Int,
    val isDay: Boolean,
)

/** A resolved city for the weather card. */
data class Place(
    val name: String,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Thin client for Open-Meteo (no API key, no attribution requirements beyond the free tier).
 * Everything is best effort: the launcher keeps working without a network.
 */
class WeatherRepository {

    fun geocode(query: String): Place? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val language = Locale.getDefault().language
        val url = "$GEOCODING_URL?name=$encoded&count=1&language=$language&format=json"
        val body = httpGet(url) ?: return null
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            Place(
                name = first.optString("name").ifBlank { query },
                country = first.optString("country").ifBlank { null },
                latitude = first.getDouble("latitude"),
                longitude = first.getDouble("longitude"),
            )
        } catch (error: Exception) {
            null
        }
    }

    fun currentWeather(latitude: Double, longitude: Double): Weather? {
        val url = String.format(
            Locale.US,
            "$FORECAST_URL?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto",
            latitude,
            longitude,
        )
        val body = httpGet(url) ?: return null
        return try {
            val current = JSONObject(body).getJSONObject("current")
            Weather(
                temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
                code = current.optInt("weather_code", -1),
                isDay = current.optInt("is_day", 1) == 1,
            )
        } catch (error: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (error: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val TIMEOUT_MS = 8_000
    }
}

/** WMO weather code → human readable string. */
@StringRes
fun weatherDescriptionRes(code: Int): Int = when (code) {
    0, 1 -> R.string.weather_clear
    2 -> R.string.weather_partly_cloudy
    3 -> R.string.weather_overcast
    45, 48 -> R.string.weather_fog
    in 51..57 -> R.string.weather_drizzle
    in 61..67 -> R.string.weather_rain
    in 71..77 -> R.string.weather_snow
    in 80..82 -> R.string.weather_showers
    in 85..86 -> R.string.weather_snow_showers
    in 95..99 -> R.string.weather_thunderstorm
    else -> R.string.weather_unknown
}

/** WMO weather code → emoji used as the big glyph on the weather card. */
fun weatherGlyph(code: Int, isDay: Boolean): String = when {
    code in 95..99 -> "⛈️"
    code in 71..77 || code in 85..86 -> "❄️"
    code in 61..67 || code in 80..82 -> "🌧️"
    code in 51..57 -> "🌦️"
    code == 45 || code == 48 -> "🌫️"
    code == 3 -> "☁️"
    code == 2 -> if (isDay) "⛅" else "☁️"
    else -> if (isDay) "☀️" else "🌙"
}
