package dev.utouch.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    fun hiddenKeys(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, null)?.toSet() ?: emptySet()

    fun saveHidden(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, keys).apply()
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

    // ---------------------------------------------------------------- dock

    fun dockKeys(): List<String> =
        prefs.getString(KEY_DOCK, null)
            ?.split('|')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun saveDock(keys: List<String>) {
        prefs.edit().putString(KEY_DOCK, keys.joinToString("|")).apply()
    }

    // ------------------------------------------------------------- widgets

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    var widgetsEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIDGETS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WIDGETS_ENABLED, value).apply()

    fun widgets(): List<WidgetRecord> {
        val raw = prefs.getString(KEY_WIDGETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                WidgetRecord(
                    id = item.optInt("id", -1),
                    provider = item.optString("provider"),
                    spanY = item.optInt("spanY", 2),
                    spanX = item.optInt("spanX", 2).coerceIn(1, 2),
                )
            }.filter { it.id >= 0 }
        }.getOrDefault(emptyList())
    }

    /** Widget ids pinned to the home screen (a subset of [widgets]). */
    fun homeWidgetIds(): List<Int> =
        prefs.getString(KEY_WIDGETS_HOME, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it >= 0 }
            ?: emptyList()

    fun saveHomeWidgetIds(ids: List<Int>) {
        prefs.edit().putString(KEY_WIDGETS_HOME, ids.joinToString(",")).apply()
    }

    /** Free positions of home-screen widgets; widgets without an entry are auto-placed. */
    fun homePositions(): Map<Int, WidgetPos> {
        val raw = prefs.getString(KEY_WIDGETS_POS, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optInt("id", -1)
                if (id < 0) return@mapNotNull null
                id to WidgetPos(
                    x = item.optDouble("x", 0.0).toFloat(),
                    y = item.optDouble("y", 0.0).toFloat(),
                )
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun saveHomePositions(positions: Map<Int, WidgetPos>) {
        val array = JSONArray()
        positions.forEach { (id, pos) ->
            array.put(
                JSONObject()
                    .put("id", id)
                    .put("x", pos.x.toDouble())
                    .put("y", pos.y.toDouble()),
            )
        }
        prefs.edit().putString(KEY_WIDGETS_POS, array.toString()).apply()
    }

    fun saveWidgets(records: List<WidgetRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("provider", record.provider)
                    .put("spanY", record.spanY)
                    .put("spanX", record.spanX),
            )
        }
        prefs.edit().putString(KEY_WIDGETS, array.toString()).apply()
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
        const val KEY_HIDDEN = "hidden_apps"
        const val KEY_WIDGETS = "widgets"
        const val KEY_WIDGETS_ENABLED = "widgets_enabled"
        const val KEY_WIDGETS_HOME = "widgets_home"
        const val KEY_WIDGETS_POS = "widgets_home_pos"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_DOCK = "dock_apps"
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
