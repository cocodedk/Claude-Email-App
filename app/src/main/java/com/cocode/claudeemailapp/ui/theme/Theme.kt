package com.cocode.claudeemailapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Snow,
    secondary = ColorTokens.darkSecondary,
    tertiary = SignalAmber,
    background = PitchBlack,
    surface = Carbon,
    surfaceVariant = Graphite,
    onPrimary = PitchBlack,
    onSecondary = PitchBlack,
    onTertiary = PitchBlack,
    onBackground = Snow,
    onSurface = Snow,
    onSurfaceVariant = ColorTokens.darkOnSurfaceVariant,
    outline = BorderStrong,
    outlineVariant = BorderSoft,
    error = ColorTokens.darkError,
    onError = PitchBlack
)

private val LightColorScheme = lightColorScheme(
    primary = Snow,
    secondary = SignalBlue,
    tertiary = SignalAmber,
    background = PitchBlack,
    surface = Carbon,
    surfaceVariant = Graphite,
    onPrimary = PitchBlack,
    onSecondary = PitchBlack,
    onTertiary = PitchBlack,
    onBackground = Snow,
    onSurface = Snow,
    onSurfaceVariant = SnowMuted,
    outline = BorderStrong,
    outlineVariant = BorderSoft,
    error = SignalRed,
    onError = PitchBlack
)

@Composable
fun ClaudeEmailAppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> if (darkTheme) DarkColorScheme else LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private object ColorTokens {
    val darkSecondary = SignalBlue
    val darkOnSurfaceVariant = SnowMuted
    val darkError = SignalRed
}
