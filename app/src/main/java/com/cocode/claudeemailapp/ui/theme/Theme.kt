package com.cocode.claudeemailapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GraphiteDarkScheme = darkColorScheme(
    primary = Snow,
    secondary = SignalCyan,
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
fun ClaudeEmailAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GraphiteDarkScheme,
        typography = Typography,
        content = content
    )
}
