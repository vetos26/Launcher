package dev.utouch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.utouch.launcher.LauncherUiState
import dev.utouch.launcher.R
import dev.utouch.launcher.ui.theme.TextPrimary
import dev.utouch.launcher.ui.theme.TextSecondary
import dev.utouch.launcher.ui.theme.TextTertiary
import dev.utouch.launcher.ui.theme.UbuntuOrange

/** The settings "scope" of the launcher drawer. */
@Composable
fun SettingsScope(
    state: LauncherUiState,
    versionName: String,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    onUseWallpaperChange: (Boolean) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClearRecents: () -> Unit,
    onSetWeatherCity: (String) -> Unit,
    onRefreshWeather: () -> Unit,
    onToggleHidden: (String) -> Unit,
    onOpenHomeSettings: () -> Unit,
) {
    var cityDialog by remember { mutableStateOf(false) }
    var cityQuery by remember { mutableStateOf("") }
    var hiddenDialog by remember { mutableStateOf(false) }

    if (cityDialog) {
        AlertDialog(
            onDismissRequest = { cityDialog = false },
            containerColor = Color(0xFF232329),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.weather_city_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                OutlinedTextField(
                    value = cityQuery,
                    onValueChange = { cityQuery = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.weather_city_dialog_hint),
                            color = TextTertiary,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = UbuntuOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = UbuntuOrange,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetWeatherCity(cityQuery)
                        cityDialog = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.weather_set_city),
                        color = UbuntuOrange,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { cityDialog = false }) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        color = TextSecondary,
                    )
                }
            },
        )
    }

    if (hiddenDialog) {
        AlertDialog(
            onDismissRequest = { hiddenDialog = false },
            containerColor = Color(0xFF232329),
            titleContentColor = TextPrimary,
            title = {
                Text(
                    text = stringResource(R.string.settings_hidden_apps),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                if (state.apps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.hidden_apps_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(items = state.apps, key = { it.key }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIconImage(entry = app, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = app.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Switch(
                                    checked = app.key in state.hiddenKeys,
                                    onCheckedChange = { onToggleHidden(app.key) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = UbuntuOrange,
                                        checkedBorderColor = UbuntuOrange,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                                        uncheckedBorderColor = Color.White.copy(alpha = 0.16f),
                                    ),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { hiddenDialog = false }) {
                    Text(text = stringResource(android.R.string.ok), color = UbuntuOrange)
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            SectionHeader(stringResource(R.string.settings_appearance))
        }
        item {
            SwitchRow(
                title = R.string.setting_wallpaper,
                description = R.string.setting_wallpaper_desc,
                checked = state.useWallpaper,
                onCheckedChange = onUseWallpaperChange,
            )
        }
        item {
            SwitchRow(
                title = R.string.setting_labels,
                description = R.string.setting_labels_desc,
                checked = state.showLabels,
                onCheckedChange = onShowLabelsChange,
            )
        }

        item {
            SectionHeader(stringResource(R.string.settings_weather))
        }
        item {
            ActionRow(
                icon = Icons.Default.Place,
                title = stringResource(R.string.weather_city_dialog_title),
                subtitle = state.weatherCity ?: stringResource(R.string.weather_city_none),
                onClick = {
                    cityQuery = state.weatherCity.orEmpty()
                    cityDialog = true
                },
            )
        }
        item {
            ActionRow(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.weather_refresh),
                subtitle = if (state.weatherLoading) {
                    stringResource(R.string.launcher_loading)
                } else {
                    stringResource(R.string.weather_title)
                },
                onClick = onRefreshWeather,
            )
        }

        item {
            SectionHeader(stringResource(R.string.settings_apps))
        }
        item {
            ActionRow(
                icon = Icons.Default.Home,
                title = stringResource(R.string.settings_default_launcher),
                subtitle = stringResource(R.string.settings_default_launcher_desc),
                onClick = onOpenHomeSettings,
            )
        }
        item {
            ActionRow(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_hidden_apps),
                subtitle = stringResource(R.string.settings_hidden_apps_count, state.hiddenKeys.size),
                onClick = { hiddenDialog = true },
            )
        }
        item {
            SwitchRow(
                title = R.string.setting_system,
                description = R.string.setting_system_desc,
                checked = state.includeSystemApps,
                onCheckedChange = onIncludeSystemAppsChange,
            )
        }
        item {
            ActionRow(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.action_reload),
                subtitle = stringResource(R.string.apps_count, state.apps.size),
                onClick = onRefresh,
            )
        }

        item {
            SectionHeader(stringResource(R.string.settings_recents))
        }
        item {
            ActionRow(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.action_clear_recents),
                subtitle = stringResource(R.string.recents_count, state.recentKeys.size),
                onClick = onClearRecents,
            )
        }

        item {
            SectionHeader(stringResource(R.string.settings_about))
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(R.string.about_body),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Text(
                text = stringResource(description),
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = UbuntuOrange,
                checkedBorderColor = UbuntuOrange,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.16f),
            ),
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = UbuntuOrange,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}
