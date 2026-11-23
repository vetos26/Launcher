package dev.utouch.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.utouch.launcher.LauncherUiState
import dev.utouch.launcher.R
import dev.utouch.launcher.data.AddWidgetResult
import dev.utouch.launcher.data.WIDGET_MAX_SPAN
import dev.utouch.launcher.data.WidgetHostHolder
import dev.utouch.launcher.data.WidgetRecord
import dev.utouch.launcher.data.widgetHeightDp
import dev.utouch.launcher.ui.theme.TextPrimary
import dev.utouch.launcher.ui.theme.TextSecondary
import dev.utouch.launcher.ui.theme.TextTertiary
import dev.utouch.launcher.ui.theme.UbuntuOrange

/**
 * Widgets live on their own scope, never inside the pager, so their gestures and the launcher
 * gestures cannot fight. Everything is behind the beta flag in [LauncherUiState.widgetsEnabled].
 */
@Composable
fun WidgetsScope(
    state: LauncherUiState,
    holder: WidgetHostHolder?,
    onEnableWidgets: () -> Unit,
    onAddWidget: (WidgetRecord) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onSetWidgetSpan: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var pendingId by remember { mutableStateOf<Int?>(null) }
    var pendingProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var configureId by remember { mutableStateOf<Int?>(null) }

    fun commitWidget(widgetId: Int) {
        val info = holder?.providerInfo(widgetId)
        onAddWidget(
            WidgetRecord(
                id = widgetId,
                provider = info?.provider?.flattenToString().orEmpty(),
                spanY = info?.let { defaultSpan(it) } ?: 2,
            ),
        )
        pickerOpen = false
    }

    val configureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val widgetId = configureId ?: return@rememberLauncherForActivityResult
        configureId = null
        if (result.resultCode == Activity.RESULT_OK) {
            commitWidget(widgetId)
        } else {
            holder?.delete(widgetId)
        }
    }

    fun maybeConfigure(widgetId: Int, provider: AppWidgetProviderInfo): Boolean {
        val intent = holder?.configureIntent(widgetId, provider) ?: return false
        configureId = widgetId
        configureLauncher.launch(intent)
        return true
    }

    val bindLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val widgetId = pendingId
        val provider = pendingProvider
        pendingId = null
        pendingProvider = null
        if (widgetId == null) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            if (provider == null || !maybeConfigure(widgetId, provider)) {
                commitWidget(widgetId)
            }
        } else {
            holder?.delete(widgetId)
        }
    }

    fun addProvider(provider: AppWidgetProviderInfo) {
        val activeHolder = holder ?: return
        when (val result = activeHolder.startAdd(provider)) {
            is AddWidgetResult.Bound -> {
                if (!maybeConfigure(result.id, provider)) commitWidget(result.id)
            }

            is AddWidgetResult.NeedsPermission -> {
                pendingId = result.id
                pendingProvider = provider
                bindLauncher.launch(result.intent)
            }

            AddWidgetResult.Failed -> Unit
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.widgets_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(R.string.widgets_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
            if (state.widgetsEnabled) {
                RoundIconButton(
                    icon = if (editMode) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = stringResource(
                        if (editMode) R.string.widgets_done else R.string.widgets_edit,
                    ),
                    onClick = { editMode = !editMode },
                )
                Spacer(Modifier.width(8.dp))
                RoundIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.widgets_add),
                    onClick = { pickerOpen = true },
                    highlighted = true,
                )
            }
        }

        when {
            !state.widgetsEnabled -> ScopeMessage(
                title = stringResource(R.string.widgets_disabled_title),
                body = stringResource(R.string.widgets_disabled_body),
                action = stringResource(R.string.widgets_enable),
                onAction = onEnableWidgets,
            )

            state.widgets.isEmpty() -> ScopeMessage(
                title = stringResource(R.string.widgets_empty_title),
                body = stringResource(R.string.widgets_empty_body),
                action = stringResource(R.string.widgets_add),
                onAction = { pickerOpen = true },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = state.widgets, key = { it.id }) { record ->
                    WidgetCard(
                        record = record,
                        holder = holder,
                        editMode = editMode,
                        onRemove = {
                            holder?.delete(record.id)
                            onRemoveWidget(record.id)
                        },
                        onSetSpan = { span ->
                            onSetWidgetSpan(record.id, span.coerceIn(1, WIDGET_MAX_SPAN))
                        },
                    )
                }
            }
        }
    }

    if (pickerOpen && holder != null) {
        WidgetPickerSheet(
            holder = holder,
            onDismiss = { pickerOpen = false },
            onPick = { provider ->
                pickerOpen = false
                addProvider(provider)
            },
        )
    }
}

@Composable
private fun WidgetCard(
    record: WidgetRecord,
    holder: WidgetHostHolder?,
    editMode: Boolean,
    onRemove: () -> Unit,
    onSetSpan: (Int) -> Unit,
) {
    val info = remember(record.id) { holder?.providerInfo(record.id) }
    val minHeightDp = info?.minHeight ?: 0

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val heightDp = widgetHeightDp(record, minHeightDp)

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heightDp.dp)
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    val view = remember(record.id) { holder?.createView(record) }
                    if (view != null) {
                        LaunchedEffect(record.spanY, widthDp, heightDp) {
                            holder?.resize(record, widthDp, heightDp)
                        }
                        AndroidView(
                            factory = { view },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.widgets_missing_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                            )
                            Text(
                                text = stringResource(R.string.widgets_missing_body),
                                modifier = Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                        }
                    }
                }

                if (editMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.widgets_height),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        SmallButton(
                            label = "−",
                            enabled = record.spanY > 1,
                            onClick = { onSetSpan(record.spanY - 1) },
                        )
                        Text(
                            text = record.spanY.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                        )
                        SmallButton(
                            label = "+",
                            enabled = record.spanY < WIDGET_MAX_SPAN,
                            onClick = { onSetSpan(record.spanY + 1) },
                        )
                        Spacer(Modifier.weight(1f))
                        SmallButton(
                            icon = Icons.Default.Delete,
                            label = null,
                            onClick = onRemove,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Color.White.copy(
                    alpha = if (enabled) 0.10f else 0.04f,
                ),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.widgets_remove),
                tint = if (enabled) UbuntuOrange else TextTertiary,
                modifier = Modifier.size(16.dp),
            )

            label != null -> Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) TextPrimary else TextTertiary,
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) UbuntuOrange.copy(alpha = 0.9f)
                else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ScopeMessage(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
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
        TextButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
            Text(text = action, color = UbuntuOrange, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun WidgetPickerSheet(
    holder: WidgetHostHolder,
    onDismiss: () -> Unit,
    onPick: (AppWidgetProviderInfo) -> Unit,
) {
    val providers = remember(holder) { holder.providers() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.ui.graphics.Color(0xFF1E1E24),
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.widgets_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            if (providers.isEmpty()) {
                Text(
                    text = stringResource(R.string.widgets_no_providers),
                    modifier = Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .padding(top = 8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    items(items = providers, key = { it.provider.flattenToString() }) { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onPick(provider) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = remember(provider.provider.flattenToString()) {
                                holder.icon(provider)
                            }
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                                        ),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = holder.label(provider),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = provider.provider.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun defaultSpan(provider: AppWidgetProviderInfo): Int = when {
    provider.minHeight >= 240 -> 3
    provider.minHeight >= 140 -> 2
    else -> 1
}
