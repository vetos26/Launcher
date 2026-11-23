package dev.utouch.launcher.data

import android.content.Context

/**
 * Small persistence layer: pinned apps, recently opened apps and the display settings.
 * Keys are "<package>/<activity>" strings, which never contain a newline.
 */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var includeSystemApps: Boolean
        get() = prefs.getBoolean(KEY_SYSTEM_APPS, true)
        set(value) = prefs.edit().putBoolean(KEY_SYSTEM_APPS, value).apply()

    var useWallpaper: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER, true)
        set(value) = prefs.edit().putBoolean(KEY_WALLPAPER, value).apply()

    var showLabels: Boolean
        get() = prefs.getBoolean(KEY_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_LABELS, value).apply()

    var coachShown: Boolean
        get() = prefs.getBoolean(KEY_COACH, false)
        set(value) = prefs.edit().putBoolean(KEY_COACH, value).apply()

    fun pinnedKeys(): List<String> = prefs.getString(KEY_PINNED, null).orEmpty().toLines()

    fun savePinned(keys: List<String>) {
        prefs.edit().putString(KEY_PINNED, keys.joinToString("\n")).apply()
    }

    fun recentKeys(): List<String> = prefs.getString(KEY_RECENTS, null).orEmpty().toLines()

    fun saveRecents(keys: List<String>) {
        prefs.edit().putString(KEY_RECENTS, keys.joinToString("\n")).apply()
    }

    /** Moves [key] to the front of the recents list and returns the new list. */
    fun pushRecent(key: String): List<String> {
        val updated = (listOf(key) + recentKeys().filterNot { it == key }).take(MAX_RECENTS)
        saveRecents(updated)
        return updated
    }

    // ------------------------------------------------------------- weather

    val weatherCity: String?
        get() = prefs.getString(KEY_WEATHER_CITY, null)

    val hasWeatherPlace: Boolean
        get() = prefs.contains(KEY_WEATHER_LAT) && prefs.contains(KEY_WEATHER_LON)

    val weatherLatitude: Double
        get() = prefs.getFloat(KEY_WEATHER_LAT, 0f).toDouble()

    val weatherLongitude: Double
        get() = prefs.getFloat(KEY_WEATHER_LON, 0f).toDouble()

    fun saveWeatherPlace(place: Place) {
        prefs.edit()
            .putString(KEY_WEATHER_CITY, place.name)
            .putFloat(KEY_WEATHER_LAT, place.latitude.toFloat())
            .putFloat(KEY_WEATHER_LON, place.longitude.toFloat())
            .apply()
    }

    fun saveWeather(weather: Weather) {
        prefs.edit()
            .putInt(KEY_WEATHER_TEMP, weather.temperature)
            .putInt(KEY_WEATHER_CODE, weather.code)
            .putBoolean(KEY_WEATHER_DAY, weather.isDay)
            .putLong(KEY_WEATHER_AT, System.currentTimeMillis())
            .apply()
    }

    fun cachedWeather(): Weather? {
        if (!prefs.contains(KEY_WEATHER_TEMP)) return null
        return Weather(
            temperature = prefs.getInt(KEY_WEATHER_TEMP, 0),
            code = prefs.getInt(KEY_WEATHER_CODE, -1),
            isDay = prefs.getBoolean(KEY_WEATHER_DAY, true),
        )
    }

    val weatherUpdatedAt: Long
        get() = prefs.getLong(KEY_WEATHER_AT, 0L)

    private fun String.toLines(): List<String> =
        split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        const val FILE_NAME = "utouch_launcher"
        const val KEY_PINNED = "pinned_apps"
        const val KEY_RECENTS = "recent_apps"
        const val KEY_SYSTEM_APPS = "include_system_apps"
        const val KEY_WALLPAPER = "use_wallpaper"
        const val KEY_LABELS = "show_labels"
        const val KEY_COACH = "coach_shown"
        const val KEY_WEATHER_CITY = "weather_city"
        const val KEY_WEATHER_LAT = "weather_lat"
        const val KEY_WEATHER_LON = "weather_lon"
        const val KEY_WEATHER_TEMP = "weather_temp"
        const val KEY_WEATHER_CODE = "weather_code"
        const val KEY_WEATHER_DAY = "weather_is_day"
        const val KEY_WEATHER_AT = "weather_updated_at"
        const val MAX_RECENTS = 12
    }
}
