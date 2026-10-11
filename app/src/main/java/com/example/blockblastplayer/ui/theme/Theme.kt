package com.example.blockblastplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1565C0)
private val LightBlue = Color(0xFF4FC3F7)
private val Amber = Color(0xFFFFB74D)
private val Green = Color(0xFF81C784)

private val DarkColors = darkColorScheme(
    primary = LightBlue,
    onPrimary = Color(0xFF00344F),
    secondary = Amber,
    onSecondary = Color(0xFF3E2600),
    tertiary = Green,
    background = Color(0xFF0F1626),
    surface = Color(0xFF1A2337),
    onBackground = Color(0xFFE6EDF7),
    onSurface = Color(0xFFE6EDF7)
)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Color(0xFFB26A00),
    onSecondary = Color.White,
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFF5F7FB),
    surface = Color.White
)

@Composable
fun BlockBlastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}