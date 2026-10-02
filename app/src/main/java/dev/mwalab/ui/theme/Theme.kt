package dev.mwalab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = LabMint, onPrimary = LabNight,
    primaryContainer = Color(0xFF155A58), onPrimaryContainer = Color(0xFFC1F6EE),
    secondary = LabAmber, onSecondary = LabNight,
    secondaryContainer = Color(0xFF59431A), onSecondaryContainer = Color(0xFFFFE8B8),
    tertiary = Color(0xFF9ED0ED), onTertiary = LabNight,
    tertiaryContainer = Color(0xFF24475D), onTertiaryContainer = Color(0xFFCDEAF9),
    background = LabNight, onBackground = Color(0xFFE5F2F0),
    surface = LabNight, onSurface = Color(0xFFE5F2F0),
    surfaceVariant = Color(0xFF1D3A49), onSurfaceVariant = Color(0xFFC5D9DA),
    surfaceContainerLowest = Color(0xFF07131B),
    surfaceContainerLow = Color(0xFF102735),
    surfaceContainer = Color(0xFF172E3A),
    surfaceContainerHigh = Color(0xFF203B48),
    surfaceContainerHighest = Color(0xFF294653),
    outline = Color(0xFF8CAEB0), outlineVariant = Color(0xFF3E5D65),
    error = Color(0xFFFF8B91), onError = LabNight,
    errorContainer = Color(0xFF6D2532), onErrorContainer = Color(0xFFFFDDE0),
)

private val LightColors = lightColorScheme(
    primary = LabTeal, onPrimary = Color.White,
    primaryContainer = Color(0xFFC4ECE6), onPrimaryContainer = LabInk,
    secondary = Color(0xFF725100), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8BA), onSecondaryContainer = LabInk,
    tertiary = Color(0xFF315E75), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8EBF4), onTertiaryContainer = LabInk,
    background = LabPaper, onBackground = LabInk,
    surface = Color.White, onSurface = LabInk,
    surfaceVariant = Color(0xFFE1EBEA), onSurfaceVariant = Color(0xFF34505B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEDF4F2),
    surfaceContainer = Color(0xFFE8F1EF),
    surfaceContainerHigh = Color(0xFFE1ECEA),
    surfaceContainerHighest = Color(0xFFD8E7E4),
    outline = Color(0xFF698589), outlineVariant = Color(0xFFBDD2D0),
    error = LabRed, onError = Color.White,
    errorContainer = Color(0xFFFFDDE0), onErrorContainer = Color(0xFF680F1B),
)

/** Stable competition palettes; wallpaper dynamic color is intentionally never used. */
@Composable
fun MWALabTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, content = content)
}
