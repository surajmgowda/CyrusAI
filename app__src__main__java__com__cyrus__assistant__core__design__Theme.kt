package com.cyrus.assistant.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyrusDarkColors = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF003254),
    secondary = Color(0xFF68DFC2),
    tertiary = Color(0xFFFFB86C),
    background = Color(0xFF0E141B),
    surface = Color(0xFF151E27),
    surfaceVariant = Color(0xFF1E2A36)
)

private val CyrusLightColors = lightColorScheme(
    primary = Color(0xFF005FA3),
    onPrimary = Color.White,
    secondary = Color(0xFF006B58),
    tertiary = Color(0xFF9C4D00),
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EFF6)
)

@Composable
fun CyrusTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CyrusDarkColors else CyrusLightColors,
        content = content
    )
}
