package com.flashcards.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = Navy,
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = OnBlueDark,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = BlueContainerLight,
)

@Composable
fun FlashcardsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
