package dev.utouch.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a [Drawable] (app icon) into a square [Bitmap].
 *
 * Adaptive icons need explicit bounds, otherwise they paint nothing, so the bounds are set
 * before drawing.
 */
fun Drawable.renderBitmap(sizePx: Int = 144): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap
}

/** Average, slightly saturated colour of an icon – used for icon plates and fallbacks. */
fun Bitmap.dominantAccent(): Color {
    val step = max(1, min(width, height) / 28)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var samples = 0

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            if (AndroidColor.alpha(pixel) > 200) {
                val pr = AndroidColor.red(pixel)
                val pg = AndroidColor.green(pixel)
                val pb = AndroidColor.blue(pixel)
                val isGrey = max(pr, max(pg, pb)) - min(pr, min(pg, pb)) < 24
                // Ignore near-grey pixels: they are usually padding, not the brand colour.
                if (!isGrey) {
                    r += pr
                    g += pg
                    b += pb
                    samples++
                }
            }
            x += step
        }
        y += step
    }

    if (samples == 0) return FALLBACK_ACCENT

    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
        (r / samples).toInt().coerceIn(0, 255),
        (g / samples).toInt().coerceIn(0, 255),
        (b / samples).toInt().coerceIn(0, 255),
        hsv,
    )
    hsv[1] = max(hsv[1], 0.45f)
    hsv[2] = hsv[2].coerceIn(0.55f, 0.92f)
    return Color(AndroidColor.HSVToColor(hsv))
}

val FALLBACK_ACCENT: Color = Color(0xFF7A7A85)

/**
 * Reads the user's wallpaper and scales it down to a sensible size for a full screen
 * background. Returns null when the platform does not allow reading it.
 */
@Suppress("DEPRECATION")
fun loadWallpaper(context: Context, maxWidthPx: Int = 1080): ImageBitmap? {
    return try {
        val manager = WallpaperManager.getInstance(context)
        val drawable: Drawable = manager.drawable ?: return null
        val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxWidthPx
        val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: (maxWidthPx * 2)

        val scale = min(1f, maxWidthPx.toFloat() / sourceWidth)
        val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (error: Throwable) {
        // Wallpaper access is best effort: the launcher falls back to its own gradient.
        null
    }
}
