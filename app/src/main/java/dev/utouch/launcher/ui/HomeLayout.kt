package dev.utouch.launcher.ui

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import dev.utouch.launcher.R
import dev.utouch.launcher.data.WidgetHostHolder
import dev.utouch.launcher.data.WidgetPos
import dev.utouch.launcher.data.WidgetRecord
import dev.utouch.launcher.data.widgetHeightDp
import dev.utouch.launcher.ui.theme.TextPrimary
import dev.utouch.launcher.ui.theme.UbuntuOrange

/** Page margins for free widget placement, in dp. */
private const val HOME_MARGIN_DP = 14f
private const val HOME_GAP_DP = 10f

/**
 * Free-form widget layer of the home screen.
 *
 * Every widget is placed with an absolute offset. Widgets that the user has never dragged sit in
 * the automatic column right below the cards; once dragged, the position is written to prefs and
 * the widget stays exactly where it was dropped. Nothing here touches the pager or the drawer.
 */
@Composable
fun HomeWidgetCanvas(
    widgets: List<WidgetRecord>,
    holder: WidgetHostHolder?,
    layout: Map<Int, WidgetPos>,
    editMode: Boolean,
    contentTopDp: Float,
    bottomInsetDp: Float = 0f,
    onMove: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pageWidth = maxWidth.value
        val pageHeight = maxHeight.value
        val fullWidth = (pageWidth - 2 * HOME_MARGIN_DP).coerceAtLeast(1f)
        val halfWidth = ((pageWidth - 2 * HOME_MARGIN_DP - HOME_GAP_DP) / 2f).coerceAtLeast(1f)

        // Cursor for widgets that were never dragged: they keep the tidy column below the cards.
        var cursor = contentTopDp + 12f

        widgets.forEach { record ->
            val info = remember(record.id) { holder?.providerInfo(record.id) }
            val width = if (record.spanX <= 1) halfWidth else fullWidth
            val height = widgetHeightDp(record, info?.minHeight ?: 0).toFloat()
            val saved = layout[record.id]

            val maxX = (pageWidth - HOME_MARGIN_DP - width).coerceAtLeast(HOME_MARGIN_DP)
            val maxY = (pageHeight - height - bottomInsetDp).coerceAtLeast(0f)
            val startX = (saved?.x ?: HOME_MARGIN_DP).coerceIn(HOME_MARGIN_DP, maxX)
            val startY = (saved?.y ?: cursor).coerceIn(0f, maxY)

            if (saved == null) cursor = startY + height + HOME_GAP_DP

            DraggableHomeWidget(
                record = record,
                holder = holder,
                baseX = startX,
                baseY = startY,
                width = width,
                height = height,
                minX = HOME_MARGIN_DP,
                maxX = maxX,
                maxY = maxY,
                editMode = editMode,
                onDrop = { x, y -> onMove(record.id, x, y) },
            )
        }
    }
}

@Composable
private fun DraggableHomeWidget(
    record: WidgetRecord,
    holder: WidgetHostHolder?,
    baseX: Float,
    baseY: Float,
    width: Float,
    height: Float,
    minX: Float,
    maxX: Float,
    maxY: Float,
    editMode: Boolean,
    onDrop: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    // Drag offset in px, committed to dp only when the finger is lifted.
    var dragPx by remember(record.id) { mutableStateOf(Offset.Zero) }

    val xDp = baseX + with(density) { dragPx.x.toDp().value }
    val yDp = baseY + with(density) { dragPx.y.toDp().value }

    Box(
        modifier = Modifier
            .offset(x = xDp.dp, y = yDp.dp)
            .width(width.dp)
            .height(height.dp)
            .zIndex(if (editMode) 3f else 1f),
    ) {
        HomeWidgetSlot(
            record = record,
            holder = holder,
            modifier = Modifier.fillMaxSize(),
        )

        if (editMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.5.dp, UbuntuOrange.copy(alpha = 0.8f), RoundedCornerShape(24.dp)),
            )
            // A platform overlay is composed *after* the widget view, so it sits on top of the
            // widget and receives the touches the widget would otherwise swallow.
            DragTouchOverlay(
                onDrag = { dx, dy -> dragPx = Offset(dx, dy) },
                onDrop = {
                    val dx = with(density) { dragPx.x.toDp().value }
                    val dy = with(density) { dragPx.y.toDp().value }
                    val targetX = (baseX + dx).coerceIn(minX, maxX)
                    val targetY = (baseY + dy).coerceIn(0f, maxY)
                    dragPx = Offset.Zero
                    onDrop(targetX, targetY)
                },
            )
        }
    }
}

/**
 * Transparent platform view used only while moving widgets: it reports raw drag deltas and consumes
 * every touch so the widget underneath stays passive.
 */
@Composable
private fun DragTouchOverlay(
    onDrag: (Float, Float) -> Unit,
    onDrop: () -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            View(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                var startX = 0f
                var startY = 0f
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            startY = event.rawY
                            onDrag(0f, 0f)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            onDrag(event.rawX - startX, event.rawY - startY)
                            true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            onDrop()
                            true
                        }

                        else -> false
                    }
                }
            }
        },
    )
}

/** Bottom pill shown while the home screen is in move mode. */
@Composable
fun HomeEditToolbar(
    onDone: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .clip(RoundedCornerShape(26.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF232329).copy(alpha = 0.94f))
            .border(
                1.dp,
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_edit_hint),
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_edit_reset),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary.copy(alpha = 0.75f),
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(UbuntuOrange)
                .clickable(onClick = onDone)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_edit_done),
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}
