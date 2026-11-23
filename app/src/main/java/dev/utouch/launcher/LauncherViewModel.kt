package dev.utouch.launcher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.utouch.launcher.data.AppEntry
import dev.utouch.launcher.data.LauncherPrefs
import dev.utouch.launcher.data.PackageRepository
import dev.utouch.launcher.data.Place
import dev.utouch.launcher.data.Weather
import dev.utouch.launcher.data.WeatherRepository
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
    val wallpaperAccent: Color? = null,
    val weatherCity: String? = null,
    val weather: Weather? = null,
    val weatherLoading: Boolean = false,
    val weatherError: Boolean = false,
    val batteryLevel: Int? = null,
    val batteryCharging: Boolean = false,
    val showCoach: Boolean = false,
    val message: String? = null,
) {
    val pinnedApps: List<AppEntry>
        get() = pinnedKeys.mapNotNull { key -> apps.firstOrNull { it.key == key } }

    val recentApps: List<AppEntry>
        get() = recentKeys.mapNotNull { key -> apps.firstOrNull { it.key == key } }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PackageRepository(application)
    private val weatherRepository = WeatherRepository()
    private val prefs = LauncherPrefs(application)
    private val appContext: Context = application.applicationContext

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    val versionName: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    private var wallpaperJob: Job? = null
    private var weatherJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = reloadApps()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            _uiState.update {
                it.copy(
                    batteryLevel = (level * 100f / scale).toInt().coerceIn(0, 100),
                    batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
    }

    init {
        _uiState.update {
            it.copy(
                includeSystemApps = prefs.includeSystemApps,
                useWallpaper = prefs.useWallpaper,
                showLabels = prefs.showLabels,
                pinnedKeys = prefs.pinnedKeys(),
                recentKeys = prefs.recentKeys(),
                weatherCity = prefs.weatherCity,
                weather = prefs.cachedWeather(),
                showCoach = !prefs.coachShown,
            )
        }
        registerPackageReceiver()
        registerBatteryReceiver()
        reloadApps()
        refreshWallpaper()
        refreshWeather(force = false)
    }

    override fun onCleared() {
        runCatching { appContext.unregisterReceiver(packageReceiver) }
        runCatching { appContext.unregisterReceiver(batteryReceiver) }
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
            val wallpaper = withContext(Dispatchers.IO) { loadWallpaper(appContext) }
            _uiState.update {
                it.copy(wallpaper = wallpaper?.image, wallpaperAccent = wallpaper?.accent)
            }
        }
    }

    // ------------------------------------------------------------- weather

    /** Fetches the forecast when the cached one is older than [CACHE_TTL_MS] (or [force]). */
    fun refreshWeather(force: Boolean) {
        if (!prefs.hasWeatherPlace) return
        val fresh = System.currentTimeMillis() - prefs.weatherUpdatedAt < CACHE_TTL_MS
        if (!force && fresh && _uiState.value.weather != null) return

        weatherJob?.cancel()
        _uiState.update { it.copy(weatherLoading = true, weatherError = false) }
        weatherJob = viewModelScope.launch {
            val weather = withContext(Dispatchers.IO) {
                weatherRepository.currentWeather(prefs.weatherLatitude, prefs.weatherLongitude)
            }
            if (weather != null) {
                prefs.saveWeather(weather)
                _uiState.update { it.copy(weather = weather, weatherLoading = false) }
            } else {
                _uiState.update {
                    it.copy(
                        weatherLoading = false,
                        weatherError = it.weather == null,
                    )
                }
            }
        }
    }

    /** Resolves [query] through the geocoding API and switches the weather card to that city. */
    fun setWeatherCity(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        weatherJob?.cancel()
        _uiState.update { it.copy(weatherLoading = true, weatherError = false) }
        weatherJob = viewModelScope.launch {
            val place: Place? = withContext(Dispatchers.IO) {
                weatherRepository.geocode(trimmed)
            }
            if (place == null) {
                _uiState.update { it.copy(weatherLoading = false, weatherError = false) }
                showMessage(appContext.getString(R.string.weather_city_not_found))
                return@launch
            }

            prefs.saveWeatherPlace(place)
            val weather = withContext(Dispatchers.IO) {
                weatherRepository.currentWeather(place.latitude, place.longitude)
            }
            if (weather != null) prefs.saveWeather(weather)
            _uiState.update {
                it.copy(
                    weatherCity = place.name,
                    weather = weather ?: it.weather,
                    weatherLoading = false,
                    weatherError = weather == null,
                )
            }
        }
    }

    // -------------------------------------------------------------- coach

    fun dismissCoach() {
        if (prefs.coachShown) return
        prefs.coachShown = true
        _uiState.update { it.copy(showCoach = false) }
    }

    // ----------------------------------------------------------- plumbing

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun showMessage(text: String) {
        _uiState.update { it.copy(message = text) }
    }

    private fun registerBatteryReceiver() {
        appContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
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

    private companion object {
        const val CACHE_TTL_MS = 30L * 60L * 1000L
    }
}
