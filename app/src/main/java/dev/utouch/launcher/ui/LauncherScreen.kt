package dev.utouch.launcher.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.utouch.launcher.DrawerTab
import dev.utouch.launcher.LauncherViewModel
import dev.utouch.launcher.data.AppEntry
import dev.utouch.launcher.data.AppShortcut
import dev.utouch.launcher.data.WidgetHostHolder
import dev.utouch.launcher.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Root of the launcher: the home pane, the left edge swipe and the Unity 8 style drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(viewModel: LauncherViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val widgetHolder = remember { WidgetHostHolder(context) }
    val drawerProgress = remember { Animatable(0f) }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var tab by rememberSaveable { mutableStateOf(DrawerTab.APPS) }

    val scrimVisible by remember { derivedStateOf { drawerProgress.value > 0.02f } }

    fun animateDrawer(open: Boolean) {
        scope.launch {
            drawerProgress.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (open) 280 else 220,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    fun closeDrawer() {
        viewModel.setQuery("")
        animateDrawer(false)
    }

    fun settleDrawer() {
        scope.launch {
            drawerProgress.animateTo(
                targetValue = if (drawerProgress.value > 0.35f) 1f else 0f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            )
        }
    }

    BackHandler(enabled = true) {
        if (drawerProgress.value > 0.01f) closeDrawer()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshWallpaper()
        viewModel.refreshWeather(force = false)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { widgetHolder.start() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { widgetHolder.stop() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val paneWidth = minOf(maxWidth * 0.88f, 430.dp)
        val paneWidthPx = with(LocalDensity.current) { paneWidth.toPx() }

        HomePane(
            state = state,
            onOpenLauncher = {
                viewModel.dismissCoach()
                animateDrawer(true)
            },
            onLaunch = viewModel::launch,
            onLongPressApp = { selectedApp = it },
            onOpenWeatherSettings = {
                tab = DrawerTab.SETTINGS
                viewModel.dismissCoach()
                animateDrawer(true)
            },
            onRefreshWeather = { viewModel.refreshWeather(force = true) },
            onDismissCoach = viewModel::dismissCoach,
            widgetHolder = widgetHolder,
            homeWidgets = if (state.widgetsEnabled) {
                state.widgets.filter { it.id in state.homeWidgetIds }
            } else {
                emptyList()
            },
            homeLayout = state.homeLayout,
            homeEditMode = state.homeEditMode,
            onEnterHomeEdit = { viewModel.setHomeEditMode(true) },
            onExitHomeEdit = { viewModel.setHomeEditMode(false) },
            onMoveHomeWidget = viewModel::moveHomeWidget,
            onResetHomeLayout = viewModel::resetHomeLayout,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val open = drawerProgress.value
                    val scale = 1f - 0.07f * open
                    scaleX = scale
                    scaleY = scale
                    translationX = paneWidthPx * 0.4f * open
                    alpha = 1f - 0.35f * open
                },
        )

        if (scrimVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.55f * drawerProgress.value }
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { closeDrawer() })
                    },
            )
        }

        // Left edge: drag to reveal the drawer, exactly like the Ubuntu Touch edge swipe.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(26.dp)
                .pointerInput(paneWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                drawerProgress.snapTo(
                                    (drawerProgress.value + dragAmount / paneWidthPx)
                                        .coerceIn(0f, 1f),
                                )
                            }
                        },
                        onDragEnd = { settleDrawer() },
                        onDragCancel = { settleDrawer() },
                    )
                },
        )

        LauncherDrawer(
            state = state,
            tab = tab,
            versionName = viewModel.versionName,
            onTabChange = { tab = it },
            onClose = { closeDrawer() },
            onLaunch = { app ->
                closeDrawer()
                viewModel.launch(app)
            },
            onLongPressApp = { selectedApp = it },
            onQueryChange = viewModel::setQuery,
            onTogglePin = viewModel::togglePin,
            onClearRecents = viewModel::clearRecents,
            onRefresh = viewModel::reloadApps,
            onIncludeSystemAppsChange = viewModel::setIncludeSystemApps,
            onUseWallpaperChange = viewModel::setUseWallpaper,
            onShowLabelsChange = viewModel::setShowLabels,
            onSetWeatherCity = viewModel::setWeatherCity,
            onRefreshWeather = { viewModel.refreshWeather(force = true) },
            onOpenContact = viewModel::dial,
            onOpenSetting = viewModel::openSystemSetting,
            onWebSearch = viewModel::webSearch,
            onContactsPermissionResult = viewModel::setContactsAllowed,
            onToggleHidden = viewModel::toggleHidden,
            onOpenHomeSettings = viewModel::openHomeSettings,
            widgetHolder = widgetHolder,
            onEnableWidgets = { viewModel.setWidgetsEnabled(true) },
            onAddWidget = viewModel::addWidget,
            onRemoveWidget = viewModel::removeWidget,
            onSetWidgetSpan = viewModel::setWidgetSpan,
            onToggleHomeWidget = viewModel::toggleHomeWidget,
            onSetWidgetWidth = viewModel::setWidgetWidth,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(paneWidth)
                .graphicsLayer {
                    translationX = -paneWidthPx * (1f - drawerProgress.value)
                },
        )
    }

    val app = selectedApp
    if (app != null) {
        val shortcuts by produceState(
            initialValue = emptyList<AppShortcut>(),
            key1 = app.key,
        ) {
            value = viewModel.shortcutsFor(app.packageName)
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedApp = null },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E24),
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                    )
                }
            },
        ) {
            AppDetailsSheet(
                app = app,
                isPinned = app.key in state.pinnedKeys,
                shortcuts = shortcuts,
                onShortcut = { shortcut ->
                    selectedApp = null
                    viewModel.launchShortcut(app, shortcut)
                },
                onOpen = {
                    selectedApp = null
                    closeDrawer()
                    viewModel.launch(app)
                },
                onTogglePin = { viewModel.togglePin(app.key) },
                onAppInfo = {
                    selectedApp = null
                    viewModel.openAppInfo(app)
                },
                onUninstall = {
                    selectedApp = null
                    viewModel.requestUninstall(app)
                },
            )
        }
    }
}
