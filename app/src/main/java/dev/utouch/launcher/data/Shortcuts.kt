package dev.utouch.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** A long-press shortcut published by an app ("New note", "Call mum", …). */
data class AppShortcut(
    val id: String,
    val label: String,
    val icon: ImageBitmap?,
    val info: ShortcutInfo,
)

/**
 * Reads and launches app shortcuts through [LauncherApps].
 *
 * The platform only returns them to the current default launcher, so every call is defensive:
 * when the launcher has not been made the default yet the list is simply empty.
 */
class ShortcutRepository(private val context: Context) {

    fun shortcutsFor(packageName: String, limit: Int = 4): List<AppShortcut> = runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        }
        launcherApps.getShortcuts(query, Process.myUserHandle())
            .orEmpty()
            .asSequence()
            .filter { it.isEnabled }
            .take(limit)
            .map { info ->
                AppShortcut(
                    id = info.id,
                    label = (info.shortLabel ?: info.longLabel)?.toString()?.trim()
                        .orEmpty()
                        .ifEmpty { info.id },
                    icon = loadIcon(info.icon),
                    info = info,
                )
            }
            .toList()
    }.getOrDefault(emptyList())

    fun start(shortcut: AppShortcut): Boolean = runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        launcherApps.startShortcut(shortcut.info, null, null)
        true
    }.getOrDefault(false)

    private fun loadIcon(icon: Icon?): ImageBitmap? = runCatching {
        icon?.loadDrawable(context)?.renderBitmap(96)?.asImageBitmap()
    }.getOrNull()
}
