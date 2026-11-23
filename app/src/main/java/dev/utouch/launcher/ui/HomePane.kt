package dev.utouch.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.utouch.launcher.LauncherUiState
import dev.utouch.launcher.R
import dev.utouch.launcher.data.AppEntry
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
 * The Ubuntu Touch style home: greeting and clock, a floating panel with pinned apps and a
 * hint that the launcher drawer lives behind the left edge.
 */
@Composable
fun HomePane(
    state: LauncherUiState,
    onOpenLauncher: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinned = state.pinnedApps
    val suggested = state.apps.take(8)
    val boardApps = if (pinned.isNotEmpty()) pinned.take(8) else suggested

    Box(modifier) {
        HomeBackground(state.wallpaper)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            HomeHeader(onOpenLauncher = onOpenLauncher)

            Spacer(Modifier.weight(0.5f))

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
                    onLaunch = onLaunch,
                    onLongPress = onLongPressApp,
                )
            }

            Spacer(Modifier.weight(1f))

            HomeHint(onOpenLauncher = onOpenLauncher)
        }
    }
}

@Composable
private fun HomeHeader(onOpenLauncher: () -> Unit) {
    val now = rememberClock()
    val context = LocalContext.current
    val locale = Locale.getDefault()

    val timeText = remember(now) {
        android.text.format.DateFormat.getTimeFormat(context).format(
            Date.from(now.atZone(ZoneId.systemDefault()).toInstant()),
        )
    }
    val dateText = remember(now) {
        now.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale),
        )
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
}

@Composable
private fun AppBoard(
    title: String,
    subtitle: String?,
    apps: List<AppEntry>,
    showLabels: Boolean,
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

@Composable
private fun HomeHint(onOpenLauncher: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = onOpenLauncher)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(PaddingValues(horizontal = 20.dp, vertical = 12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = null,
                tint = UbuntuOrange,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.open_launcher),
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
        }
    }
}

/** Wallpaper (dimmed) when available, otherwise the Ubuntu aubergine gradient. */
@Composable
private fun HomeBackground(wallpaper: ImageBitmap?) {
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
                                Color(0xF017171B),
                                Color(0xF5121215),
                                Color(0xFF101013),
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

        // Soft Ubuntu orange glow in the top right corner.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val center = Offset(size.width * 0.85f, size.height * 0.08f)
                    val radius = size.minDimension * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(UbuntuOrange.copy(alpha = 0.20f), Color.Transparent),
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
