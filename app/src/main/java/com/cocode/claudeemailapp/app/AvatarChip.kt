package com.cocode.claudeemailapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Round avatar with initials derived from the display name (or local
 * part of the email). Background colour is a stable hash of the email,
 * so the same agent always gets the same tint across refreshes.
 */
@Composable
internal fun AvatarChip(displayName: String, email: String, size: Dp = 36.dp) {
    val initials = initialsFor(displayName, email)
    val color = colorFor(email)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp
        )
    }
}

internal fun initialsFor(displayName: String, email: String): String {
    val source = displayName.takeIf { it.isNotBlank() } ?: email.substringBefore('@')
    val parts = source.trim().split(Regex("[\\s._-]+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].first().toString() + parts[1].first().toString()).uppercase()
    }
}

@Composable
private fun colorFor(email: String): Color {
    // Identity tints only — colorScheme.error is reserved for destructive
    // affordances and would otherwise paint ~1 in 5 senders red.
    val palette = listOf(
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.inversePrimary,
        MaterialTheme.colorScheme.outline
    )
    val hash = email.lowercase().hashCode()
    val index = ((hash % palette.size) + palette.size) % palette.size
    return palette[index]
}
