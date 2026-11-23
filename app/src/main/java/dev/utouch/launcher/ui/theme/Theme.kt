package dev.utouch.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val UtouchColorScheme = darkColorScheme(
    primary = UbuntuOrange,
    onPrimary = Color.White,
    primaryContainer = UbuntuOrangePressed,
    onPrimaryContainer = Color.White,
    secondary = UbuntuWarmGrey,
    onSecondary = Color(0xFF1B1B20),
    secondaryContainer = UbuntuAubergine,
    onSecondaryContainer = Color.White,
    tertiary = UbuntuAubergine,
    onTertiary = Color.White,
    background = SuruBackground,
    onBackground = TextPrimary,
    surface = SuruSurface,
    onSurface = TextPrimary,
    surfaceVariant = SuruSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = SuruSurfaceHigh,
    surfaceContainerHighest = Color(0xFF2D2D34),
    outline = SuruOutline,
    outlineVariant = Color(0x14FFFFFF),
    error = Color(0xFFE86D6D),
    onError = Color(0xFF241012),
    scrim = Color.Black,
)

private val UtouchShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun UtouchTheme(content: @Composable () -> Unit) {
    // The launcher is always dark, exactly like the Ubuntu Touch shell.
    MaterialTheme(
        colorScheme = UtouchColorScheme,
        typography = UtouchTypography,
        shapes = UtouchShapes,
        content = content,
    )
}
