package dev.utouch.launcher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.utouch.launcher.DrawerTab
import dev.utouch.launcher.LauncherUiState
import dev.utouch.launcher.R
import dev.utouch.launcher.data.AppEntry
import dev.utouch.launcher.data.Contact
import dev.utouch.launcher.data.SystemSetting
import dev.utouch.launcher.data.WidgetHostHolder
import dev.utouch.launcher.data.WidgetRecord
import dev.utouch.launcher.ui.theme.TextPrimary
import dev.utouch.launcher.ui.theme.TextSecondary
import dev.utouch.launcher.ui.theme.TextTertiary
import dev.utouch.launcher.ui.theme.UbuntuOrange
import java.util.Locale

/**
 * The Unity 8 / Ubuntu Touch launcher drawer: it slides in from the left edge over the home
 * screen and holds the app grid, the recents scope and the settings scope.
 */
@Composable
fun LauncherDrawer(
    state: LauncherUiState,
    tab: DrawerTab,
    versionName: String,
    onTabChange: (DrawerTab) -> Unit,
    onClose: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onQueryChange: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onClearRecents: () -> Unit,
    onRefresh: () -> Unit,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    onUseWallpaperChange: (Boolean) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onSetWeatherCity: (String) -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenContact: (Contact) -> Unit,
    onOpenSetting: (SystemSetting) -> Unit,
    onWebSearch: (String) -> Unit,
    onContactsPermissionResult: (Boolean) -> Unit,
    onToggleHidden: (String) -> Unit,
    onOpenHomeSettings: () -> Unit,
    widgetHolder: WidgetHostHolder?,
    onEnableWidgets: () -> Unit,
    onAddWidget: (WidgetRecord) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onSetWidgetSpan: (Int, Int) -> Unit,
    onToggleHomeWidget: (Int) -> Unit,
    onSetWidgetWidth: (Int, Int) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onContactsPermissionResult,
    )

    LaunchedEffect(state.query) {
        if (state.query.isNotBlank() && tab != DrawerTab.APPS) {
            onTabChange(DrawerTab.APPS)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
        color = Color(0xFF1A1A1F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        shadowElevation = 28.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.close_launcher),
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    DrawerTab.APPS -> if (state.query.isNotBlank()) {
                        SearchScope(
                            state = state,
                            onLaunchApp = onLaunch,
                            onOpenContact = onOpenContact,
                            onOpenSetting = onOpenSetting,
                            onWebSearch = onWebSearch,
                            onRequestContactsPermission = {
                                contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                        )
                    } else {
                        AppsScope(
                            state = state,
                            onLaunch = onLaunch,
                            onLongPressApp = onLongPressApp,
                        )
                    }

                    DrawerTab.RECENTS -> RecentsScope(
                        state = state,
                        onLaunch = onLaunch,
                        onLongPressApp = onLongPressApp,
                        onTogglePin = onTogglePin,
                    )

                    DrawerTab.WIDGETS -> WidgetsScope(
                        state = state,
                        holder = widgetHolder,
                        onEnableWidgets = onEnableWidgets,
                        onAddWidget = onAddWidget,
                        onRemoveWidget = onRemoveWidget,
                        onSetWidgetSpan = onSetWidgetSpan,
                        onToggleHomeWidget = onToggleHomeWidget,
                        onSetWidgetWidth = onSetWidgetWidth,
                    )

                    DrawerTab.SETTINGS -> SettingsScope(
                        state = state,
                        versionName = versionName,
                        onIncludeSystemAppsChange = onIncludeSystemAppsChange,
                        onUseWallpaperChange = onUseWallpaperChange,
                        onShowLabelsChange = onShowLabelsChange,
                        onRefresh = onRefresh,
                        onClearRecents = onClearRecents,
                        onSetWeatherCity = onSetWeatherCity,
                        onRefreshWeather = onRefreshWeather,
                        onToggleHidden = onToggleHidden,
                        onOpenHomeSettings = onOpenHomeSettings,
                        onNotificationsEnabledChange = onNotificationsEnabledChange,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                    )
                }
            }

            DrawerDash(selected = tab, onSelect = onTabChange)
        }
    }
}

// ------------------------------------------------------------------- apps scope

private sealed interface GridRow {
    val id: String

    data class Header(override val id: String, val title: String) : GridRow
    data class App(override val id: String, val entry: AppEntry) : GridRow
}

@Composable
private fun AppsScope(
    state: LauncherUiState,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
) {
    val query = state.query.trim()

    val visibleApps = remember(state.apps, state.includeSystemApps, state.hiddenKeys, query) {
        val base = state.apps
            .filterNot { it.key in state.hiddenKeys }
            .filter { state.includeSystemApps || !it.isSystem }
        if (query.isEmpty()) {
            base
        } else {
            base.filter { app ->
                app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    val pinned = remember(state.pinnedKeys, state.apps) { state.pinnedApps }
    val pinnedTitle = stringResource(R.string.pinned_title)

    val rows = remember(visibleApps, pinned, query, pinnedTitle) {
        val pinnedKeys = pinned.map { it.key }.toSet()
        buildList {
            if (query.isEmpty() && pinned.isNotEmpty()) {
                add(GridRow.Header("header-pinned", pinnedTitle))
                pinned.forEach { add(GridRow.App("pinned-${it.key}", it)) }
            }

            if (query.isNotEmpty()) {
                visibleApps.forEach { add(GridRow.App("search-${it.key}", it)) }
            } else {
                var letter: Char? = null
                visibleApps.filterNot { it.key in pinnedKeys }.forEach { app ->
                    val initial = app.initial
                    if (initial != letter) {
                        letter = initial
                        add(GridRow.Header("header-$initial", initial.toString()))
                    }
                    add(GridRow.App(app.key, app))
                }
            }
        }
    }

    if (rows.none { it is GridRow.App }) {
        ScopeEmptyState(
            title = if (query.isEmpty()) {
                stringResource(R.string.home_empty)
            } else {
                stringResource(R.string.no_results, query)
            },
            body = if (query.isEmpty()) {
                stringResource(R.string.home_empty_body)
            } else {
                stringResource(R.string.no_results_body)
            },
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            when (row) {
                is GridRow.Header -> item(
                    key = row.id,
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "header",
                ) {
                    SectionHeader(row.title)
                }

                is GridRow.App -> item(
                    key = row.id,
                    contentType = "app",
                ) {
                    AppTile(
                        entry = row.entry,
                        showLabel = state.showLabels,
                        onClick = { onLaunch(row.entry) },
                        onLongClick = { onLongPressApp(row.entry) },
                        iconSize = 46.dp,
                        badgeCount = state.badges[row.entry.packageName] ?: 0,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- recents scope

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentsScope(
    state: LauncherUiState,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    val recents = remember(state.recentKeys, state.apps, state.hiddenKeys) {
        state.recentApps.filterNot { it.key in state.hiddenKeys }
    }

    if (recents.isEmpty()) {
        ScopeEmptyState(
            title = stringResource(R.string.recents_empty),
            body = stringResource(R.string.recents_empty_body),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items = recents, key = { it.key }) { app ->
            val pinned = app.key in state.pinnedKeys
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPressApp(app) },
                    )
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconImage(entry = app, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onTogglePin(app.key) }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(
                            if (pinned) R.string.action_unpin else R.string.action_pin,
                        ),
                        tint = if (pinned) UbuntuOrange else TextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------- pieces

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(23.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(UbuntuOrange),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.clear_search),
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = UbuntuOrange.copy(alpha = 0.9f),
        letterSpacing = 1.6.sp,
    )
}

@Composable
fun ScopeEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Four rounded squares, drawn locally because material-icons-core has no widget glyph. */
@Composable
private fun WidgetsDashIcon(tint: Color) {
    Canvas(modifier = Modifier.size(19.dp)) {
        val gap = size.minDimension * 0.16f
        val cell = (size.minDimension - gap) / 2f
        val corner = CornerRadius(cell * 0.3f, cell * 0.3f)
        for (row in 0..1) {
            for (column in 0..1) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/** Ubuntu Touch style "dash" at the bottom of the drawer. */
@Composable
private fun DrawerDash(selected: DrawerTab, onSelect: (DrawerTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.List,
            labelRes = R.string.tab_apps,
            selected = selected == DrawerTab.APPS,
            onClick = { onSelect(DrawerTab.APPS) },
        )
        DashItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Refresh,
            labelRes = R.string.tab_recents,
            selected = selected == DrawerTab.RECENTS,
            onClick = { onSelect(DrawerTab.RECENTS) },
        )
        DashItem(
            modifier = Modifier.weight(1f),
            labelRes = R.string.tab_widgets,
            selected = selected == DrawerTab.WIDGETS,
            onClick = { onSelect(DrawerTab.WIDGETS) },
        ) { tint -> WidgetsDashIcon(tint) }
        DashItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Settings,
            labelRes = R.string.tab_settings,
            selected = selected == DrawerTab.SETTINGS,
            onClick = { onSelect(DrawerTab.SETTINGS) },
        )
    }
}

@Composable
private fun DashItem(
    @StringRes labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: (@Composable (Color) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (selected) UbuntuOrange else TextSecondary
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = stringResource(labelRes),
                tint = tint,
                modifier = Modifier.size(21.dp),
            )

            customIcon != null -> Box(
                modifier = Modifier.size(21.dp),
                contentAlignment = Alignment.Center,
            ) {
                customIcon(tint)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
        )
    }
}
