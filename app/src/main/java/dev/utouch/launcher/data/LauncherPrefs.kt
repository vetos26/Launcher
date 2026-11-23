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

    private fun String.toLines(): List<String> =
        split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        const val FILE_NAME = "utouch_launcher"
        const val KEY_PINNED = "pinned_apps"
        const val KEY_RECENTS = "recent_apps"
        const val KEY_SYSTEM_APPS = "include_system_apps"
        const val KEY_WALLPAPER = "use_wallpaper"
        const val KEY_LABELS = "show_labels"
        const val MAX_RECENTS = 12
    }
}
