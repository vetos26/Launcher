package dev.utouch.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** One widget placement. Only the height is configurable; widgets always span the full width. */
data class WidgetRecord(
    val id: Int,
    val provider: String,
    val spanY: Int = 2,
)

sealed interface AddWidgetResult {
    /** The widget was bound right away. */
    data class Bound(val id: Int) : AddWidgetResult

    /** The system asks the user to confirm the binding (not the default launcher yet). */
    data class NeedsPermission(val id: Int, val intent: Intent) : AddWidgetResult

    data object Failed : AddWidgetResult
}

/**
 * Thin wrapper around [AppWidgetHost] that never throws into the UI: every platform call is
 * guarded, views are cached per widget id and reads go through [AppWidgetManager].
 */
class WidgetHostHolder(private val context: Context) {

    private val host = AppWidgetHost(context, HOST_ID)
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val viewCache = HashMap<Int, AppWidgetHostView>()
    private val iconCache = HashMap<String, ImageBitmap?>()

    fun start() {
        runCatching { host.startListening() }
    }

    fun stop() {
        runCatching { host.stopListening() }
    }

    fun delete(widgetId: Int) {
        viewCache.remove(widgetId)
        runCatching { host.deleteAppWidgetId(widgetId) }
    }

    fun providers(): List<AppWidgetProviderInfo> = runCatching {
        manager.installedProviders.sortedBy { label(it) }
    }.getOrDefault(emptyList())

    fun label(provider: AppWidgetProviderInfo): String = runCatching {
        provider.loadLabel(context.packageManager).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: provider.provider.packageName

    fun icon(provider: AppWidgetProviderInfo): ImageBitmap? =
        iconCache.getOrPut(provider.provider.flattenToString()) {
            runCatching {
                provider.loadIcon(context, context.resources.displayMetrics.densityDpi)
                    ?.renderBitmap(96)
                    ?.asImageBitmap()
            }.getOrNull()
        }

    fun providerInfo(widgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(widgetId) }.getOrNull()

    fun createView(record: WidgetRecord): AppWidgetHostView? {
        viewCache[record.id]?.let { return it }
        val info = providerInfo(record.id) ?: return null
        val view = runCatching { host.createView(context, record.id, info) }.getOrNull() ?: return null
        viewCache[record.id] = view
        return view
    }

    /** Tells the widget how much space it actually got, in dp. */
    fun resize(record: WidgetRecord, widthDp: Int, heightDp: Int) {
        runCatching {
            viewCache[record.id]?.updateAppWidgetSize(
                null,
                widthDp,
                heightDp,
                widthDp,
                heightDp,
            )
            manager.updateAppWidgetOptions(record.id, sizeOptions(widthDp, heightDp))
        }
    }

    fun startAdd(provider: AppWidgetProviderInfo): AddWidgetResult {
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull()
            ?: return AddWidgetResult.Failed
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider) }
            .getOrDefault(false)
        return if (bound) {
            AddWidgetResult.Bound(id)
        } else {
            AddWidgetResult.NeedsPermission(id, bindIntent(id, provider))
        }
    }

    fun configureIntent(widgetId: Int, provider: AppWidgetProviderInfo): Intent? {
        val configure = provider.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
    }

    private fun bindIntent(widgetId: Int, provider: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)

    private fun sizeOptions(widthDp: Int, heightDp: Int): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
    }

    private companion object {
        const val HOST_ID = 4201
    }
}

/** Height in dp a widget occupies for its row count (base row + gap). */
fun widgetHeightDp(record: WidgetRecord, minHeightDp: Int): Int {
    val span = record.spanY.coerceIn(1, 3)
    val computed = span * WIDGET_ROW_DP + (span - 1) * WIDGET_GAP_DP
    return maxOf(computed, minHeightDp)
}

const val WIDGET_ROW_DP = 96
const val WIDGET_GAP_DP = 10
const val WIDGET_MAX_SPAN = 3
