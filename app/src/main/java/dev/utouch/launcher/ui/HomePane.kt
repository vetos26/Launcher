package dev.utouch.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.utouch.launcher.LauncherUiState
import dev.utouch.launcher.R
import dev.utouch.launcher.data.AppEntry
import dev.utouch.launcher.data.NowPlaying
import dev.utouch.launcher.data.Weather
import dev.utouch.launcher.data.WidgetHostHolder
import dev.utouch.launcher.data.WidgetPos
import dev.utouch.launcher.data.WidgetRecord
import dev.utouch.launcher.data.weatherDescriptionRes
import dev.utouch.launcher.data.weatherGlyph
import dev.utouch.launcher.ui.theme.TextPrimary
import dev.utouch.launcher.ui.theme.TextSecondary
import dev.utouch.launcher.ui.theme.TextTertiary
import dev.utouch.launcher.ui.theme.UbuntuOrange
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/**
 * The Ubuntu Touch style home: a horizontally paged set of "scopes" — Today (clock, weather,
 * battery) and Apps (pinned grid) — with page dots and a hint that points at the left edge.
 */
@Composable
fun HomePane(
    state: LauncherUiState,
    onOpenLauncher: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenWeatherSettings: () -> Unit,
    onRefreshWeather: () -> Unit,
    onDismissCoach: () -> Unit,
    widgetHolder: WidgetHostHolder?,
    homeWidgets: List<WidgetRecord>,
    homeLayout: Map<Int, WidgetPos>,
    homeEditMode: Boolean,
    onEnterHomeEdit: () -> Unit,
    onExitHomeEdit: () -> Unit,
    onMoveHomeWidget: (Int, Float, Float) -> Unit,
    onResetHomeLayout: () -> Unit,
    onToggleNowPlaying: (Boolean) -> Unit,
    onOpenNowPlayingApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(modifier) {
        HomeBackground(wallpaper = state.wallpaper, accent = state.wallpaperAccent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> TodayPage(
                        state = state,
                        onOpenLauncher = onOpenLauncher,
                        onOpenWeatherSettings = onOpenWeatherSettings,
                        onRefreshWeather = onRefreshWeather,
                        widgetHolder = widgetHolder,
                        homeWidgets = homeWidgets,
                        homeLayout = homeLayout,
                        homeEditMode = homeEditMode,
                        onEnterHomeEdit = onEnterHomeEdit,
                        onExitHomeEdit = onExitHomeEdit,
                        onMoveHomeWidget = onMoveHomeWidget,
                        onResetHomeLayout = onResetHomeLayout,
                        onToggleNowPlaying = onToggleNowPlaying,
                        onOpenNowPlayingApp = onOpenNowPlayingApp,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> AppsPage(
                        state = state,
                        onOpenLauncher = onOpenLauncher,
                        onLaunch = onLaunch,
                        onLongPressApp = onLongPressApp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            HomeFooter(pagerState = pagerState, onOpenLauncher = onOpenLauncher)
        }

        if (state.showCoach) {
            CoachOverlay(onDismiss = onDismissCoach)
        }
    }
}

// ---------------------------------------------------------------- today scope

@Composable
private fun TodayPage(
    state: LauncherUiState,
    onOpenLauncher: () -> Unit,
    onOpenWeatherSettings: () -> Unit,
    onRefreshWeather: () -> Unit,
    widgetHolder: WidgetHostHolder?,
    homeWidgets: List<WidgetRecord>,
    homeLayout: Map<Int, WidgetPos>,
    homeEditMode: Boolean,
    onEnterHomeEdit: () -> Unit,
    onExitHomeEdit: () -> Unit,
    onMoveHomeWidget: (Int, Float, Float) -> Unit,
    onResetHomeLayout: () -> Unit,
    onToggleNowPlaying: (Boolean) -> Unit,
    onOpenNowPlayingApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var contentBottomDp by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(homeWidgets.isNotEmpty()) {
                detectTapGestures(
                    onLongPress = {
                        if (homeWidgets.isNotEmpty() && !homeEditMode) onEnterHomeEdit()
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            TodayHeader(onOpenLauncher = onOpenLauncher)

            Spacer(Modifier.weight(0.6f))

            WeatherCard(
                state = state,
                onOpenWeatherSettings = onOpenWeatherSettings,
                onRefreshWeather = onRefreshWeather,
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.onGloballyPositioned { coords ->
                    // Where the cards end: the automatic widget column starts right below.
                    contentBottomDp = with(density) {
                        (coords.positionInParent().y + coords.size.height).toDp().value
                    }
                },
            ) {
                BatteryCard(level = state.batteryLevel, charging = state.batteryCharging)
                val playing = state.nowPlaying
                if (state.notificationsEnabled && playing != null) {
                    Spacer(Modifier.height(14.dp))
                    NowPlayingCard(
                        playing = playing,
                        onToggle = { onToggleNowPlaying(playing.isPlaying) },
                        onOpen = { onOpenNowPlayingApp(playing.packageName) },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }

        if (homeWidgets.isNotEmpty()) {
            HomeWidgetCanvas(
                widgets = homeWidgets,
                holder = widgetHolder,
                layout = homeLayout,
                editMode = homeEditMode,
                contentTopDp = contentBottomDp,
                onMove = onMoveHomeWidget,
            )
        }

        if (homeEditMode) {
            HomeEditToolbar(
                onDone = onExitHomeEdit,
                onReset = onResetHomeLayout,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp),
            )
        }
    }
}

@Composable
private fun TodayHeader(onOpenLauncher: () -> Unit) {
    val now = rememberClock()
    val context = LocalContext.current
    val locale = Locale.getDefault()

    val timeText = remember(now) {
        android.text.format.DateFormat.getTimeFormat(context).format(
            Date.from(now.atZone(ZoneId.systemDefault()).toInstant()),
        )
    }
    val dateText = remember(now) {
        now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }
    val greeting = when (now.hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_day
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(greeting).uppercase(locale),
                style = MaterialTheme.typography.labelLarge,
                color = UbuntuOrange,
                letterSpacing = 1.6.sp,
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        MenuButton(onOpenLauncher = onOpenLauncher)
    }
}

@Composable
private fun WeatherCard(
    state: LauncherUiState,
    onOpenWeatherSettings: () -> Unit,
    onRefreshWeather: () -> Unit,
) {
    val weather = state.weather
    val city = state.weatherCity

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.weather_title).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = UbuntuOrange.copy(alpha = 0.9f),
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.weight(1f))
                if (state.weatherLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = UbuntuOrange,
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            when {
                city == null -> {
                    Text(
                        text = stringResource(R.string.weather_unset_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.weather_unset_body),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    TextButton(
                        onClick = onOpenWeatherSettings,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.weather_set_city),
                            style = MaterialTheme.typography.titleSmall,
                            color = UbuntuOrange,
                        )
                    }
                }

                weather != null -> WeatherContent(
                    weather = weather,
                    city = city,
                    onRefreshWeather = onRefreshWeather,
                )

                else -> {
                    Text(
                        text = stringResource(R.string.weather_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    TextButton(
                        onClick = onRefreshWeather,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.weather_retry),
                            style = MaterialTheme.typography.titleSmall,
                            color = UbuntuOrange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherContent(
    weather: Weather,
    city: String,
    onRefreshWeather: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${weather.temperature}°",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
            )
            Text(
                text = city,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary.copy(alpha = 0.9f),
            )
            Text(
                text = stringResource(weatherDescriptionRes(weather.code)),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Text(
            text = weatherGlyph(weather.code, weather.isDay),
            fontSize = 46.sp,
        )
    }
    TextButton(
        onClick = onRefreshWeather,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.weather_refresh),
            style = MaterialTheme.typography.labelSmall,
            color = UbuntuOrange,
        )
    }
}

@Composable
private fun BatteryCard(level: Int?, charging: Boolean) {
    val fraction = ((level ?: 0).coerceIn(0, 100)) / 100f
    val barColor = when {
        charging -> Color(0xFF5FD08A)
        level != null && level <= 15 -> Color(0xFFE86D6D)
        else -> UbuntuOrange
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.battery_title).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = level?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(barColor),
                )
            }
            if (charging) {
                Text(
                    text = stringResource(R.string.battery_charging),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5FD08A),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    playing: NowPlaying,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = UbuntuOrange,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.now_playing_title).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    letterSpacing = 1.6.sp,
                )
                Text(
                    text = playing.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playing.artist != null) {
                    Text(
                        text = playing.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(UbuntuOrange.copy(alpha = 0.18f))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (playing.isPlaying) {
                    PauseGlyph(color = UbuntuOrange)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.now_playing_play),
                        tint = UbuntuOrange,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Two bars — the material icon set we depend on has no pause glyph. */
@Composable
private fun PauseGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color),
            )
        }
    }
}

// ----------------------------------------------------------------- apps scope

@Composable
private fun AppsPage(
    state: LauncherUiState,
    onOpenLauncher: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinned = state.pinnedApps
    val suggested = state.apps.take(8)
    val boardApps = if (pinned.isNotEmpty()) pinned.take(8) else suggested

    Column(modifier = modifier.padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.apps_page_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(R.string.apps_page_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            MenuButton(onOpenLauncher = onOpenLauncher)
        }

        Spacer(Modifier.weight(0.4f))

        when {
            state.loading && state.apps.isEmpty() -> LoadingBoard()
            state.apps.isEmpty() -> EmptyBoard()
            else -> AppBoard(
                title = stringResource(
                    if (pinned.isNotEmpty()) R.string.pinned_title else R.string.suggested_title,
                ),
                subtitle = if (pinned.isEmpty()) stringResource(R.string.suggested_subtitle) else null,
                apps = boardApps,
                showLabels = state.showLabels,
                badges = state.badges,
                onLaunch = onLaunch,
                onLongPress = onLongPressApp,
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AppBoard(
    title: String,
    subtitle: String?,
    apps: List<AppEntry>,
    showLabels: Boolean,
    badges: Map<String, Int> = emptyMap(),
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    text = apps.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                apps.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        row.forEach { app ->
                            AppTile(
                                entry = app,
                                showLabel = showLabels,
                                onClick = { onLaunch(app) },
                                onLongClick = { onLongPress(app) },
                                modifier = Modifier.weight(1f),
                                badgeCount = badges[app.packageName] ?: 0,
                            )
                        }
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBoard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = UbuntuOrange,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.launcher_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun EmptyBoard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// --------------------------------------------------------------------- chrome

@Composable
private fun MenuButton(onOpenLauncher: () -> Unit) {
    IconButton(
        onClick = onOpenLauncher,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = stringResource(R.string.open_launcher),
            tint = TextPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PageDots(pagerState: PagerState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pagerState.pageCount) { index ->
            val selected = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .size(width = if (selected) 16.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) UbuntuOrange else Color.White.copy(alpha = 0.28f)),
            )
        }
    }
}

@Composable
private fun HomeFooter(pagerState: PagerState, onOpenLauncher: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageDots(pagerState)

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.home_hint),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** First-run coach mark for the left-edge gesture. */
@Composable
private fun CoachOverlay(onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "coach")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "coachShift",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 90.dp)
                .offset(y = (-shift * 12).dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF232329),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = UbuntuOrange,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.coach_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.coach_body),
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.coach_got_it),
                            style = MaterialTheme.typography.titleSmall,
                            color = UbuntuOrange,
                        )
                    }
                }
            }
        }
    }
}

/** Blurred wallpaper (or the Ubuntu aubergine gradient) with a soft accent glow. */
@Composable
private fun HomeBackground(wallpaper: ImageBitmap?, accent: Color?) {
    val glow = remember(accent) {
        if (accent != null) lerp(UbuntuOrange, accent, 0.45f) else UbuntuOrange
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xC8141418),
                                Color(0xE0121215),
                                Color(0xFF0F0F12),
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF2E1C28),
                                Color(0xFF1D1720),
                                Color(0xFF111114),
                            ),
                        ),
                    ),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val center = Offset(size.width * 0.85f, size.height * 0.08f)
                    val radius = size.minDimension * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.22f), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                },
        )
    }
}

@Composable
private fun rememberClock(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            now = LocalDateTime.now()
        }
    }
    return now
}
