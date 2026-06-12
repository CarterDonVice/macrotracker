package com.localmacrotracker.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF1A5C38),
    onPrimaryContainer = Color(0xFFB7F5D4),
    secondary = EstimatedColor,
    onSecondary = Color(0xFF003060),
    secondaryContainer = Color(0xFF1A3060),
    onSecondaryContainer = Color(0xFFB8D9FF),
    tertiary = MacroCalories,
    onTertiary = Color(0xFF3A1000),
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color(0xFF3A0000),
    outline = Divider,
    outlineVariant = Color(0xFF48484A)
)

@Composable
fun MacroTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
