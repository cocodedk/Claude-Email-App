package com.cocode.claudeemailapp.app.steering

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.claudeemailapp.ui.theme.BorderSoft
import com.cocode.claudeemailapp.ui.theme.BorderStrong
import com.cocode.claudeemailapp.ui.theme.SignalCyan
import com.cocode.claudeemailapp.ui.theme.SignalRed
import com.cocode.claudeemailapp.ui.theme.Snow
import com.cocode.claudeemailapp.ui.theme.SnowMuted

/** Visual variant of a steering chip — matches the v1 mockup's chip styles. */
enum class SteeringChipVariant { Default, Danger, DangerArmed, Ghost, Sending }

private data class ChipColors(val border: Color, val label: Color, val fill: Color)

private fun colorsFor(variant: SteeringChipVariant): ChipColors = when (variant) {
    SteeringChipVariant.Default -> ChipColors(BorderStrong, Snow, Color.Transparent)
    SteeringChipVariant.Danger -> ChipColors(SignalRed.copy(alpha = 0.5f), SignalRed, Color.Transparent)
    SteeringChipVariant.DangerArmed -> ChipColors(SignalRed, SignalRed, SignalRed.copy(alpha = 0.12f))
    SteeringChipVariant.Ghost -> ChipColors(BorderSoft, SnowMuted, Color.Transparent)
    SteeringChipVariant.Sending -> ChipColors(SignalCyan.copy(alpha = 0.45f), SignalCyan, Color.Transparent)
}

private val ChipLabelStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 10.5.sp,
    letterSpacing = 0.8.sp
)

private val IconStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 11.sp
)

@Composable
fun SteeringChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SteeringChipVariant = SteeringChipVariant.Default,
    icon: String? = null,
    enabled: Boolean = true
) {
    val colors = colorsFor(variant)
    val shape = RoundedCornerShape(999.dp)
    val chipAlpha = when {
        !enabled -> 0.35f
        variant == SteeringChipVariant.Sending -> 0.85f
        else -> 1f
    }
    val hapticClick = com.cocode.claudeemailapp.app.rememberHapticClick(onClick)
    val clickable = enabled && variant != SteeringChipVariant.Sending
    Row(
        modifier = modifier
            .alpha(chipAlpha)
            .clip(shape)
            .background(colors.fill, shape)
            .border(BorderStroke(1.dp, colors.border), shape)
            .clickable(enabled = clickable, onClick = hapticClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (variant == SteeringChipVariant.Sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = SignalCyan,
                trackColor = SignalCyan.copy(alpha = 0.25f)
            )
        } else if (icon != null) {
            Text(text = icon, color = colors.label, style = IconStyle)
        }
        Text(
            text = label.uppercase(),
            color = colors.label,
            style = ChipLabelStyle
        )
    }
}
