package com.cocode.claudeemailapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.protocol.Kinds
import java.text.DateFormat
import java.util.Date

@Composable
fun KindChip(kind: String) {
    val (label, accent) = when (kind) {
        Kinds.ACK -> "ack" to MaterialTheme.colorScheme.tertiary
        Kinds.PROGRESS -> "running" to MaterialTheme.colorScheme.primary
        Kinds.QUESTION -> "needs reply" to MaterialTheme.colorScheme.secondary
        Kinds.RESULT -> "done" to MaterialTheme.colorScheme.primary
        Kinds.ERROR -> "error" to MaterialTheme.colorScheme.error
        Kinds.COMMAND -> "command" to MaterialTheme.colorScheme.outline
        Kinds.REPLY -> "reply" to MaterialTheme.colorScheme.outline
        else -> kind to MaterialTheme.colorScheme.outline
    }
    ChipPill(label = label, accent = accent)
}

@Composable
fun StatusChip(status: String) {
    val (label, accent) = when (status) {
        PendingStatus.AWAITING_ACK -> "awaiting ack" to MaterialTheme.colorScheme.secondary
        PendingStatus.QUEUED -> "queued" to MaterialTheme.colorScheme.tertiary
        PendingStatus.RUNNING -> "running" to MaterialTheme.colorScheme.primary
        PendingStatus.AWAITING_USER -> "needs reply" to MaterialTheme.colorScheme.secondary
        PendingStatus.DONE -> "done" to MaterialTheme.colorScheme.primary
        PendingStatus.FAILED -> "failed" to MaterialTheme.colorScheme.error
        PendingStatus.ERROR -> "error" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.outline
    }
    ChipPill(label = label, accent = accent)
}

@Composable
fun ChipPill(label: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

fun formatTimestamp(date: Date?, now: Long = System.currentTimeMillis()): String {
    if (date == null) return ""
    val diff = now - date.time
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "now"
        diff < hour -> "${diff / minute}m"
        diff < day -> "${diff / hour}h"
        diff < 7 * day -> "${diff / day}d"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
    }
}
