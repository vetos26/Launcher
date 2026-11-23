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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
) {
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
            SectionHeader(stringResource(R.string.settings_apps))
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
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
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
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
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
