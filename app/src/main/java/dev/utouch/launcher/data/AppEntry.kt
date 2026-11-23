package dev.utouch.launcher.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * One launchable activity on the device.
 *
 * Icons are rendered to an [ImageBitmap] once, in the background, so scrolling the app grid
 * never blocks on the PackageManager.
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?,
    val accent: Color,
    val isSystem: Boolean,
) {
    /** Stable identifier, also used to persist pinned and recent apps. */
    val key: String get() = "$packageName/$activityName"

    val initial: Char
        get() = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
}
