package com.localmacrotracker.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AccentGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7DFCC),
    onPrimaryContainer = TextPrimary,
    secondary = EstimatedColor,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBADFF7),
    onSecondaryContainer = TextPrimary,
    tertiary = MacroCalories,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = Divider,
    outlineVariant = Color(0xFFE8F2EC)
)

@Composable
fun MacroTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
