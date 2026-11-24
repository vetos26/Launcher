package dev.utouch.launcher

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.app.SearchManager
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.utouch.launcher.data.AppEntry
import dev.utouch.launcher.data.AppShortcut
import dev.utouch.launcher.data.Contact
import dev.utouch.launcher.data.ContactsRepository
import dev.utouch.launcher.data.LauncherNotificationService
import dev.utouch.launcher.data.LauncherPrefs
import dev.utouch.launcher.data.NotificationCenter
import dev.utouch.launcher.data.NowPlaying
import dev.utouch.launcher.data.PackageRepository
import dev.utouch.launcher.data.Place
import dev.utouch.launcher.data.ShortcutRepository
import dev.utouch.launcher.data.SystemSetting
import dev.utouch.launcher.data.Weather
import dev.utouch.launcher.data.WeatherRepository
import dev.utouch.launcher.data.WidgetPos
import dev.utouch.launcher.data.WidgetRecord
import dev.utouch.launcher.data.loadWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tabs of the launcher drawer, mirroring the Ubuntu Touch "dash". */
enum class DrawerTab { APPS, RECENTS, WIDGETS, SETTINGS }

data class LauncherUiState(
    val loading: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
    val pinnedKeys: List<String> = emptyList(),
    val hiddenKeys: Set<String> = emptySet(),
    val widgets: List<WidgetRecord> = emptyList(),
    val widgetsEnabled: Boolean = false,
    val homeWidgetIds: List<Int> = emptyList(),
    /** Keys of the apps placed in the home dock, in display order. */
    val dockKeys: List<String> = emptyList(),
    /** Free positions of home widgets in dp; a widget without an entry is auto-placed. */
    val homeLayout: Map<Int, WidgetPos> = emptyMap(),
    /** True while the home screen is in "move widgets around" mode. */
    val homeEditMode: Boolean = false,
    /** Unread notifications per package; empty unless the user enabled the feature. */
    val badges: Map<String, Int> = emptyMap(),
    /** Currently playing media, if any and if the feature is enabled. */
    val nowPlaying: NowPlaying? = null,
    /** The notification-listener access bit as the system sees it right now. */
    val notificationsEnabled: Boolean = false,
    val notificationAccess: Boolean = false,
    val recentKeys: List<String> = emptyList(),
    val query: String = "",
    val includeSystemApps: Boolean = true,
    val useWallpaper: Boolean = true,
    val showLabels: Boolean = true,
    val wallpaper: ImageBitmap? = null,
    val wallpaperAccent: Color? = null,
    val contacts: List<Contact> = emptyList(),
    val contactsAllowed: Boolean = false,
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
    private val contactsRepository = ContactsRepository(application)
    private val shortcutRepository = ShortcutRepository(application)
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
    private var contactsJob: Job? = null

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
        // First run: seed the dock with the pinned apps so it is not an empty bar.
        if (!prefs.dockInitialized) {
            prefs.saveDock(prefs.pinnedKeys().take(4))
            prefs.dockInitialized = true
        }
        _uiState.update {
            it.copy(
                includeSystemApps = prefs.includeSystemApps,
                useWallpaper = prefs.useWallpaper,
                showLabels = prefs.showLabels,
                pinnedKeys = prefs.pinnedKeys(),
                hiddenKeys = prefs.hiddenKeys(),
                widgets = prefs.widgets(),
                widgetsEnabled = prefs.widgetsEnabled,
                homeWidgetIds = prefs.homeWidgetIds(),
                dockKeys = prefs.dockKeys(),
                homeLayout = prefs.homePositions(),
                notificationsEnabled = prefs.notificationsEnabled,
                notificationAccess = hasNotificationAccess(),
                recentKeys = prefs.recentKeys(),
                contactsAllowed = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.READ_CONTACTS,
                ) == PackageManager.PERMISSION_GRANTED,
                weatherCity = prefs.weatherCity,
                weather = prefs.cachedWeather(),
                showCoach = !prefs.coachShown,
            )
        }
        observeNotifications()
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

    // ----------------------------------------------------------------- dock

    fun addToDock(key: String) {
        _uiState.update { state ->
            if (key in state.dockKeys) return@update state
            val keys = state.dockKeys + key
            prefs.saveDock(keys)
            state.copy(dockKeys = keys)
        }
    }

    fun removeFromDock(key: String) {
        _uiState.update { state ->
            val keys = state.dockKeys - key
            prefs.saveDock(keys)
            state.copy(dockKeys = keys)
        }
    }

    fun toggleDock(key: String) {
        if (key in _uiState.value.dockKeys) removeFromDock(key) else addToDock(key)
    }

    // ------------------------------------------------------- notifications

    private fun hasNotificationAccess(): Boolean = runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)
    }.getOrDefault(false)

    private fun observeNotifications() {
        viewModelScope.launch {
            NotificationCenter.badges.collect { badges ->
                val enabled = _uiState.value.notificationsEnabled
                _uiState.update { it.copy(badges = if (enabled) badges else emptyMap()) }
            }
        }
        viewModelScope.launch {
            NotificationCenter.nowPlaying.collect { playing ->
                val enabled = _uiState.value.notificationsEnabled
                _uiState.update { it.copy(nowPlaying = if (enabled) playing else null) }
            }
        }
    }

    fun setNotificationsEnabled(value: Boolean) {
        prefs.notificationsEnabled = value
        _uiState.update {
            it.copy(
                notificationsEnabled = value,
                notificationAccess = hasNotificationAccess(),
                badges = if (value) it.badges else emptyMap(),
                nowPlaying = if (value) it.nowPlaying else null,
            )
        }
        if (value) {
            requestNotificationRebind()
        } else {
            NotificationCenter.clear()
        }
    }

    /** Called when the launcher comes back to the foreground (e.g. from the system settings). */
    fun refreshNotificationAccess() {
        val access = hasNotificationAccess()
        if (access == _uiState.value.notificationAccess) return
        _uiState.update { it.copy(notificationAccess = access) }
        if (access && _uiState.value.notificationsEnabled) requestNotificationRebind()
    }

    private fun requestNotificationRebind() {
        runCatching {
            NotificationListenerService.requestRebind(
                android.content.ComponentName(appContext, LauncherNotificationService::class.java),
            )
        }
    }

    fun openNotificationAccessSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
    }

    fun toggleNowPlaying(playing: Boolean) {
        NotificationCenter.togglePlayPause(appContext, playing)
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
        searchContacts(query)
    }

    // ------------------------------------------------------------ contacts

    fun setContactsAllowed(allowed: Boolean) {
        _uiState.update { it.copy(contactsAllowed = allowed) }
        if (allowed) {
            searchContacts(_uiState.value.query)
        } else {
            _uiState.update { it.copy(contacts = emptyList()) }
        }
    }

    private fun searchContacts(query: String) {
        contactsJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2 || !_uiState.value.contactsAllowed) {
            _uiState.update { it.copy(contacts = emptyList()) }
            return
        }
        contactsJob = viewModelScope.launch {
            delay(CONTACT_DEBOUNCE_MS)
            val contacts = withContext(Dispatchers.IO) { contactsRepository.search(trimmed) }
            _uiState.update { it.copy(contacts = contacts) }
        }
    }

    fun dial(contact: Contact) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure { showMessage(appContext.getString(R.string.cannot_launch, contact.name)) }
    }

    // ---------------------------------------------------- search side doors

    fun openSystemSetting(setting: SystemSetting) {
        val intent = Intent(setting.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure { showMessage(appContext.getString(R.string.cannot_open_settings)) }
    }

    fun webSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, trimmed)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure {
                val fallback = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(trimmed)),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { appContext.startActivity(fallback) }
                    .onFailure { showMessage(appContext.getString(R.string.cannot_launch, trimmed)) }
            }
    }

    fun openHomeSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { appContext.startActivity(intent) }.isSuccess) return
        }
        showMessage(appContext.getString(R.string.cannot_open_settings))
    }

    // --------------------------------------------------------- shortcuts

    suspend fun shortcutsFor(packageName: String): List<AppShortcut> =
        withContext(Dispatchers.IO) { shortcutRepository.shortcutsFor(packageName) }

    fun launchShortcut(app: AppEntry, shortcut: AppShortcut) {
        if (shortcutRepository.start(shortcut)) {
            _uiState.update { it.copy(recentKeys = prefs.pushRecent(app.key)) }
        } else {
            showMessage(appContext.getString(R.string.cannot_launch, app.label))
        }
    }

    // ----------------------------------------------------------- widgets

    fun setWidgetsEnabled(value: Boolean) {
        prefs.widgetsEnabled = value
        _uiState.update { it.copy(widgetsEnabled = value) }
    }

    fun addWidget(record: WidgetRecord) {
        _uiState.update { state ->
            if (state.widgets.any { it.id == record.id }) return@update state
            val widgets = state.widgets + record
            prefs.saveWidgets(widgets)
            state.copy(widgets = widgets, widgetsEnabled = true)
        }
    }

    fun removeWidget(widgetId: Int) {
        _uiState.update { state ->
            val widgets = state.widgets.filterNot { it.id == widgetId }
            val home = state.homeWidgetIds - widgetId
            val layout = state.homeLayout - widgetId
            prefs.saveWidgets(widgets)
            prefs.saveHomeWidgetIds(home)
            prefs.saveHomePositions(layout)
            state.copy(widgets = widgets, homeWidgetIds = home, homeLayout = layout)
        }
    }

    /** Pins or unpins a widget on the home screen (the widgets scope stays the editor). */
    fun toggleHomeWidget(widgetId: Int) {
        _uiState.update { state ->
            val home = if (widgetId in state.homeWidgetIds) {
                state.homeWidgetIds - widgetId
            } else {
                state.homeWidgetIds + widgetId
            }
            prefs.saveHomeWidgetIds(home)
            // Unpinning forgets the free position, so re-pinning starts from the auto slot.
            val layout = if (widgetId in home) state.homeLayout else state.homeLayout - widgetId
            if (layout !== state.homeLayout) prefs.saveHomePositions(layout)
            state.copy(homeWidgetIds = home, homeLayout = layout)
        }
    }

    fun setHomeEditMode(value: Boolean) {
        _uiState.update { it.copy(homeEditMode = value) }
    }

    /** Commits a dragged widget position (already clamped by the caller, in dp). */
    fun moveHomeWidget(widgetId: Int, x: Float, y: Float) {
        _uiState.update { state ->
            val layout = state.homeLayout + (widgetId to WidgetPos(x, y))
            prefs.saveHomePositions(layout)
            state.copy(homeLayout = layout)
        }
    }

    /** Drops every manual position: widgets go back to their automatic slots. */
    fun resetHomeLayout() {
        prefs.saveHomePositions(emptyMap())
        _uiState.update { it.copy(homeLayout = emptyMap()) }
    }

    fun setWidgetWidth(widgetId: Int, spanX: Int) {
        _uiState.update { state ->
            val widgets = state.widgets.map {
                if (it.id == widgetId) it.copy(spanX = spanX.coerceIn(1, 2)) else it
            }
            prefs.saveWidgets(widgets)
            state.copy(widgets = widgets)
        }
    }

    fun setWidgetSpan(widgetId: Int, spanY: Int) {
        _uiState.update { state ->
            val widgets = state.widgets.map {
                if (it.id == widgetId) it.copy(spanY = spanY) else it
            }
            prefs.saveWidgets(widgets)
            state.copy(widgets = widgets)
        }
    }

    // ------------------------------------------------------------ hidden

    fun toggleHidden(key: String) {
        _uiState.update { state ->
            val hidden = if (key in state.hiddenKeys) {
                state.hiddenKeys - key
            } else {
                state.hiddenKeys + key
            }
            prefs.saveHidden(hidden)
            state.copy(hiddenKeys = hidden)
        }
    }

    /** Opens a package by name — used by the "now playing" card. */
    fun openPackage(packageName: String) {
        val app = _uiState.value.apps.firstOrNull { it.packageName == packageName }
        if (app != null) {
            launch(app)
            return
        }
        runCatching {
            val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) appContext.startActivity(intent)
        }
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
        const val CONTACT_DEBOUNCE_MS = 250L
    }
}
