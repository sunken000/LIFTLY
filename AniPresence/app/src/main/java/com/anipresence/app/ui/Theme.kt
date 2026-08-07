package com.anipresence.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF625B71),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
)

@Composable
fun AniPresenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
