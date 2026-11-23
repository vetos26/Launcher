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

/** Wallpaper background with the colour it contributes to the launcher theme. */
data class Wallpaper(
    val image: ImageBitmap,
    val accent: Color?,
)

/**
 * Reads the user's wallpaper, scales it down and blurs it so text stays readable on top.
 * Returns null when the platform does not allow reading it; the caller then falls back to the
 * Ubuntu aubergine gradient.
 */
@Suppress("DEPRECATION")
fun loadWallpaper(context: Context, targetWidthPx: Int = 720): Wallpaper? {
    return try {
        val manager = WallpaperManager.getInstance(context)
        val drawable: Drawable = manager.drawable ?: return null
        val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: targetWidthPx
        val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: (targetWidthPx * 2)

        val scale = min(1f, targetWidthPx.toFloat() / sourceWidth)
        val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)

        Wallpaper(
            image = bitmap.downscaledBlur().asImageBitmap(),
            accent = bitmap.dominantAccent(),
        )
    } catch (error: Throwable) {
        // Wallpaper access is best effort: the launcher falls back to its own gradient.
        null
    }
}

/**
 * Very cheap blur: shrink the bitmap to ~96 px wide and run two box-blur passes. Drawn back at
 * full screen size with bilinear filtering it reads as a soft Suru-style background and costs
 * almost nothing even on old devices.
 */
private fun Bitmap.downscaledBlur(): Bitmap {
    val targetWidth = 96
    val targetHeight = (height.toFloat() * targetWidth / width).toInt().coerceAtLeast(1)
    var current = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    repeat(2) { current = current.boxBlur(2) }
    return current
}

private fun Bitmap.boxBlur(radius: Int): Bitmap {
    val source = IntArray(width * height)
    getPixels(source, 0, width, 0, 0, width, height)

    val horizontal = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var samples = 0
            for (dx in -radius..radius) {
                val xx = (x + dx).coerceIn(0, width - 1)
                val color = source[y * width + xx]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
                samples++
            }
            horizontal[y * width + x] = opaque(red / samples, green / samples, blue / samples)
        }
    }

    val vertical = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var samples = 0
            for (dy in -radius..radius) {
                val yy = (y + dy).coerceIn(0, height - 1)
                val color = horizontal[yy * width + x]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
                samples++
            }
            vertical[y * width + x] = opaque(red / samples, green / samples, blue / samples)
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(vertical, 0, width, 0, 0, width, height)
    return result
}

private fun opaque(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
