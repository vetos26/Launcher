package dev.utouch.launcher.data

import android.provider.Settings
import androidx.annotation.StringRes
import dev.utouch.launcher.R

/** A phone contact, reduced to what the search result row needs. */
data class Contact(
    val id: Long,
    val name: String,
    val number: String,
)

/**
 * A system settings screen that can be reached straight from the launcher search.
 * [keywords] make the entry findable in both languages ("wifi" works in the Russian UI).
 */
data class SystemSetting(
    val id: String,
    @StringRes val title: Int,
    val action: String,
    val keywords: List<String>,
)

val SYSTEM_SETTINGS: List<SystemSetting> = listOf(
    SystemSetting("wifi", R.string.sys_wifi, Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wi-fi", "сеть", "интернет")),
    SystemSetting("bluetooth", R.string.sys_bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "блютус")),
    SystemSetting("battery", R.string.sys_battery, Settings.ACTION_BATTERY_SAVER_SETTINGS, listOf("battery", "батарея", "заряд")),
    SystemSetting("display", R.string.sys_display, Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "screen", "экран", "яркость")),
    SystemSetting("sound", R.string.sys_sound, Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "звук", "громкость")),
    SystemSetting("location", R.string.sys_location, Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps", "геолокация", "местоположение")),
    SystemSetting("datetime", R.string.sys_datetime, Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "дата", "время")),
    SystemSetting("apps", R.string.sys_apps, Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "applications", "приложения")),
    SystemSetting("notifications", R.string.sys_notifications, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, listOf("notifications", "уведомления")),
    SystemSetting("language", R.string.sys_language, Settings.ACTION_LOCALE_SETTINGS, listOf("language", "keyboard", "язык", "клавиатура")),
    SystemSetting("accessibility", R.string.sys_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accessibility", "доступность")),
    SystemSetting("about", R.string.sys_about, Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "device", "телефон", "устройство")),
)

private val TRANSLITERATION: Map<Char, String> = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
    'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
    'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
    'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch",
    'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    'і' to "i", 'ї' to "i", 'є' to "e", 'ґ' to "g",
)

/**
 * Lowercases and romanises text so "telegram" finds "Телеграм" and vice versa.
 * Everything that is not a letter or a digit is dropped.
 */
fun normalizeForSearch(value: String): String = buildString(value.length) {
    for (char in value.lowercase()) {
        when {
            TRANSLITERATION.containsKey(char) -> append(TRANSLITERATION.getValue(char))
            char in 'a'..'z' || char in '0'..'9' -> append(char)
            char.isLetterOrDigit() -> append(char)
        }
    }
}

/** True when any of [candidates] (label, package name, keywords…) matches [query]. */
fun matchesQuery(query: String, vararg candidates: String?): Boolean {
    val needle = normalizeForSearch(query)
    if (needle.isEmpty()) return false
    return candidates.any { candidate ->
        candidate != null && normalizeForSearch(candidate).contains(needle)
    }
}

/** Matches a settings entry by its localised title or by its language-independent keywords. */
fun matchesSystemSetting(setting: SystemSetting, localizedTitle: String, query: String): Boolean {
    if (matchesQuery(query, localizedTitle)) return true
    val needle = query.trim().lowercase()
    return setting.keywords.any { it.contains(needle) || needle.contains(it) }
}
