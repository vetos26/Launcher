package dev.utouch.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.utouch.launcher.LauncherUiState
import dev.utouch.launcher.R
import dev.utouch.launcher.data.AppEntry
import dev.utouch.launcher.data.Calculator
import dev.utouch.launcher.data.Contact
import dev.utouch.launcher.data.SYSTEM_SETTINGS
import dev.utouch.launcher.data.SystemSetting
import dev.utouch.launcher.data.matchesQuery
import dev.utouch.launcher.data.matchesSystemSetting
import dev.utouch.launcher.ui.theme.TextPrimary
import dev.utouch.launcher.ui.theme.TextSecondary
import dev.utouch.launcher.ui.theme.TextTertiary
import dev.utouch.launcher.ui.theme.UbuntuOrange

/**
 * Universal search: arithmetic, apps, contacts, system settings and a web fallback.
 */
@Composable
fun SearchScope(
    state: LauncherUiState,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenContact: (Contact) -> Unit,
    onOpenSetting: (SystemSetting) -> Unit,
    onWebSearch: (String) -> Unit,
    onRequestContactsPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = state.query.trim()
    val calculation = remember(query) { Calculator.evaluate(query) }
    val clipboard = LocalClipboardManager.current

    val apps = remember(state.apps, state.hiddenKeys, query) {
        state.apps
            .filterNot { it.key in state.hiddenKeys }
            .filter { matchesQuery(query, it.label, it.packageName) }
            .take(6)
    }

    val settings = SYSTEM_SETTINGS
        .map { it to stringResource(it.title) }
        .filter { (setting, title) -> matchesSystemSetting(setting, title, query) }
        .take(5)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (calculation != null) {
            item(key = "calculation") {
                ResultRow(
                    title = query,
                    subtitle = "= $calculation",
                    onClick = { clipboard.setText(AnnotatedString(calculation)) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(UbuntuOrange.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "=",
                            color = UbuntuOrange,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (apps.isNotEmpty()) {
            item(key = "header-apps") {
                SectionHeader(stringResource(R.string.search_apps))
            }
            items(items = apps, key = { "app-${it.key}" }) { app ->
                ResultRow(
                    title = app.label,
                    subtitle = app.packageName,
                    onClick = { onLaunchApp(app) },
                ) {
                    AppIconImage(entry = app, size = 36.dp)
                }
            }
        }

        item(key = "header-contacts") {
            SectionHeader(stringResource(R.string.search_contacts))
        }

        if (!state.contactsAllowed) {
            item(key = "contacts-permission") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.contacts_permission_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.contacts_permission_body),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                    TextButton(
                        onClick = onRequestContactsPermission,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.contacts_permission_action),
                            style = MaterialTheme.typography.titleSmall,
                            color = UbuntuOrange,
                        )
                    }
                }
            }
        } else if (state.contacts.isEmpty()) {
            item(key = "contacts-empty") {
                Text(
                    text = stringResource(R.string.contacts_empty),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        } else {
            items(items = state.contacts, key = { "contact-${it.id}-${it.number}" }) { contact ->
                ResultRow(
                    title = contact.name,
                    subtitle = contact.number,
                    onClick = { onOpenContact(contact) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }

        if (settings.isNotEmpty()) {
            item(key = "header-settings") {
                SectionHeader(stringResource(R.string.search_settings))
            }
            items(items = settings, key = { "setting-${it.first.id}" }) { entry ->
                ResultRow(
                    title = entry.second,
                    subtitle = null,
                    onClick = { onOpenSetting(entry.first) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        item(key = "header-web") {
            SectionHeader(stringResource(R.string.search_web))
        }
        item(key = "web") {
            ResultRow(
                title = stringResource(R.string.search_web_action, query),
                subtitle = null,
                onClick = { onWebSearch(query) },
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
