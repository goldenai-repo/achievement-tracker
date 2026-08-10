package com.goldenai.achievements.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D5E0F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF8E287),
    onPrimaryContainer = Color(0xFF221B00),
    secondary = Color(0xFF665E40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEE2BC),
    onSecondaryContainer = Color(0xFF211B04),
    tertiary = Color(0xFF43664E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5ECCE),
    onTertiaryContainer = Color(0xFF00210F),
    background = Color(0xFFFFF9EE),
    onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFF9EE),
    onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFEAE2D0),
    onSurfaceVariant = Color(0xFF4B4739),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDBC66E),
    onPrimary = Color(0xFF3A3000),
    primaryContainer = Color(0xFF534600),
    onPrimaryContainer = Color(0xFFF8E287),
    secondary = Color(0xFFD1C6A1),
    onSecondary = Color(0xFF363016),
    secondaryContainer = Color(0xFF4E472A),
    onSecondaryContainer = Color(0xFFEEE2BC),
    tertiary = Color(0xFFA9D0B3),
    onTertiary = Color(0xFF143723),
    tertiaryContainer = Color(0xFF2C4E38),
    onTertiaryContainer = Color(0xFFC5ECCE),
    background = Color(0xFF15130B),
    onBackground = Color(0xFFE8E2D4),
    surface = Color(0xFF15130B),
    onSurface = Color(0xFFE8E2D4),
    surfaceVariant = Color(0xFF4B4739),
    onSurfaceVariant = Color(0xFFCDC6B4),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
