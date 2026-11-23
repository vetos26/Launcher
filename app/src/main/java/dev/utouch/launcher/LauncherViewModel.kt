package dev.utouch.launcher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.utouch.launcher.data.AppEntry
import dev.utouch.launcher.data.LauncherPrefs
import dev.utouch.launcher.data.PackageRepository
import dev.utouch.launcher.data.loadWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tabs of the launcher drawer, mirroring the Ubuntu Touch "dash". */
enum class DrawerTab { APPS, RECENTS, SETTINGS }

data class LauncherUiState(
    val loading: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
    val pinnedKeys: List<String> = emptyList(),
    val recentKeys: List<String> = emptyList(),
    val query: String = "",
    val includeSystemApps: Boolean = true,
    val useWallpaper: Boolean = true,
    val showLabels: Boolean = true,
    val wallpaper: ImageBitmap? = null,
    val message: String? = null,
) {
    val pinnedApps: List<AppEntry>
        get() = pinnedKeys.mapNotNull { key -> apps.firstOrNull { it.key == key } }

    val recentApps: List<AppEntry>
        get() = recentKeys.mapNotNull { key -> apps.firstOrNull { it.key == key } }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PackageRepository(application)
    private val prefs = LauncherPrefs(application)
    private val appContext: Context = application.applicationContext

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    val versionName: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    private var wallpaperJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = reloadApps()
    }

    init {
        _uiState.update {
            it.copy(
                includeSystemApps = prefs.includeSystemApps,
                useWallpaper = prefs.useWallpaper,
                showLabels = prefs.showLabels,
                pinnedKeys = prefs.pinnedKeys(),
                recentKeys = prefs.recentKeys(),
            )
        }
        registerPackageReceiver()
        reloadApps()
        refreshWallpaper()
    }

    override fun onCleared() {
        runCatching { appContext.unregisterReceiver(packageReceiver) }
        super.onCleared()
    }

    // ---------------------------------------------------------------- apps

    fun reloadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { repository.loadApps() }
            _uiState.update { it.copy(apps = apps, loading = false) }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            appContext.startActivity(intent)
            val recents = prefs.pushRecent(app.key)
            _uiState.update { it.copy(recentKeys = recents) }
        } catch (error: ActivityNotFoundException) {
            showMessage(appContext.getString(R.string.cannot_launch, app.label))
        } catch (error: SecurityException) {
            showMessage(appContext.getString(R.string.cannot_launch, app.label))
        }
    }

    fun openAppInfo(app: AppEntry) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", app.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            showMessage(appContext.getString(R.string.cannot_open_settings))
        }
    }

    fun requestUninstall(app: AppEntry) {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.fromParts("package", app.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            showMessage(appContext.getString(R.string.cannot_uninstall))
        }
    }

    // ---------------------------------------------------------------- pins

    fun togglePin(key: String) {
        _uiState.update { state ->
            val pinned = if (key in state.pinnedKeys) {
                state.pinnedKeys - key
            } else {
                state.pinnedKeys + key
            }
            prefs.savePinned(pinned)
            state.copy(pinnedKeys = pinned)
        }
    }

    // ------------------------------------------------------------ recents

    fun clearRecents() {
        prefs.saveRecents(emptyList())
        _uiState.update { it.copy(recentKeys = emptyList()) }
    }

    // ----------------------------------------------------------- settings

    fun setIncludeSystemApps(value: Boolean) {
        prefs.includeSystemApps = value
        _uiState.update { it.copy(includeSystemApps = value) }
    }

    fun setShowLabels(value: Boolean) {
        prefs.showLabels = value
        _uiState.update { it.copy(showLabels = value) }
    }

    fun setUseWallpaper(value: Boolean) {
        prefs.useWallpaper = value
        _uiState.update { it.copy(useWallpaper = value) }
        if (value) {
            refreshWallpaper()
        } else {
            wallpaperJob?.cancel()
            _uiState.update { it.copy(wallpaper = null) }
        }
    }

    /** Call when the wallpaper may have changed (returning to the home screen). */
    fun refreshWallpaper() {
        if (!_uiState.value.useWallpaper) return
        wallpaperJob?.cancel()
        wallpaperJob = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadWallpaper(appContext) }
            _uiState.update { it.copy(wallpaper = bitmap) }
        }
    }

    // ----------------------------------------------------------- plumbing

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun showMessage(text: String) {
        _uiState.update { it.copy(message = text) }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(packageReceiver, filter)
        }
    }
}
