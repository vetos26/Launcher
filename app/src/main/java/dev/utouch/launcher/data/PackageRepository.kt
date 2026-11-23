package dev.utouch.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap

/** Reads every app that advertises a launcher icon. */
class PackageRepository(context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun loadApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = queryActivities(intent)

        val apps = LinkedHashMap<String, AppEntry>()
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            val application = activity.applicationInfo ?: continue

            val label = info.loadLabel(packageManager)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: activity.packageName

            val iconDrawable = runCatching { info.loadIcon(packageManager) }.getOrNull()
            val bitmap = iconDrawable?.renderBitmap(ICON_SIZE_PX)
            val accent = bitmap?.dominantAccent() ?: FALLBACK_ACCENT

            apps[activity.packageName + "/" + activity.name] = AppEntry(
                packageName = activity.packageName,
                activityName = activity.name,
                label = label,
                icon = bitmap?.asImageBitmap(),
                accent = accent,
                isSystem = (application.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0,
            )
        }

        return apps.values.sortedBy { it.label.lowercase() }
    }

    private fun queryActivities(intent: Intent) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, 0)
    }

    private companion object {
        const val ICON_SIZE_PX = 144
    }
}
