package com.smarttracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF7FAFA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE6EEEE),
    onSurfaceVariant = Color(0xFF3F4948),
    error = Color(0xFFB91C1C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF0F4F4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF161B1B),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF253030),
    onSurfaceVariant = Color(0xFFBFC9C8),
    error = Color(0xFFFCA5A5),
)

fun isDarkMode(mode: String, systemDark: Boolean): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> systemDark
}

@Composable
fun AppTheme(mode: String, content: @Composable () -> Unit) {
    val dark = isDarkMode(mode, isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
