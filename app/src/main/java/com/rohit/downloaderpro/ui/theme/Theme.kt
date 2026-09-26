package com.rohit.downloaderpro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AppBg,
    secondary = AudioBlue,
    tertiary = InstagramPink,
    background = AppBg,
    surface = CardBg,
    onBackground = TextMain,
    onSurface = TextMain,
    outline = BorderColor,
)

@Composable
fun YtDlpAppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
