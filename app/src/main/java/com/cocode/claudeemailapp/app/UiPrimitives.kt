package com.cocode.claudeemailapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.protocol.Kinds
import java.text.DateFormat
import java.util.Date

@Composable
fun KindChip(kind: String) {
    val colors = MaterialTheme.colorScheme
    val (label, accent) = when (kind) {
        Kinds.ACK -> stringResource(R.string.kind_ack) to colors.tertiary
        Kinds.PROGRESS -> stringResource(R.string.kind_progress) to colors.primary
        Kinds.QUESTION -> stringResource(R.string.kind_question) to colors.secondary
        Kinds.RESULT -> stringResource(R.string.kind_result) to colors.primary
        Kinds.ERROR -> stringResource(R.string.kind_error) to colors.error
        Kinds.COMMAND -> stringResource(R.string.kind_command) to colors.outline
        Kinds.REPLY -> stringResource(R.string.kind_reply) to colors.outline
        else -> kind to colors.outline
    }
    ChipPill(label = label, accent = accent)
}

@Composable
fun StatusChip(status: String) {
    val colors = MaterialTheme.colorScheme
    val (label, accent) = when (status) {
        PendingStatus.AWAITING_ACK -> stringResource(R.string.status_awaiting_ack) to colors.secondary
        PendingStatus.QUEUED -> stringResource(R.string.status_queued) to colors.tertiary
        PendingStatus.RUNNING -> stringResource(R.string.status_running) to colors.primary
        PendingStatus.AWAITING_USER -> stringResource(R.string.status_awaiting_user) to colors.secondary
        PendingStatus.STALLED -> stringResource(R.string.status_stalled) to colors.tertiary
        PendingStatus.WAITING_ON_PEER -> stringResource(R.string.status_waiting_on_peer) to colors.secondary
        PendingStatus.DONE -> stringResource(R.string.status_done) to colors.primary
        PendingStatus.FAILED -> stringResource(R.string.status_failed) to colors.error
        PendingStatus.ERROR -> stringResource(R.string.status_error) to colors.error
        else -> status to colors.outline
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

/**
 * Shared error/status card used by the home, conversation, and compose
 * screens. The defaults match the in-thread "Send failed" / "Sync failed"
 * sites; pass [cornerRadius] / [horizontalPadding] / [verticalPadding] to
 * match the slightly chunkier home-screen variant.
 */
@Composable
internal fun StatusCard(
    title: String,
    message: String,
    cornerRadius: Dp = 18.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 12.dp
) {
    ElevatedCard(
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** How long ago something happened, before it is turned into text. */
sealed interface Age {
    data object Now : Age
    data class Minutes(val count: Long) : Age
    data class Hours(val count: Long) : Age
    data class Days(val count: Long) : Age
    data class Dated(val date: Date) : Age
}

fun ageOf(date: Date?, now: Long = System.currentTimeMillis()): Age? {
    if (date == null) return null
    val diff = now - date.time
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> Age.Now
        diff < hour -> Age.Minutes(diff / minute)
        diff < day -> Age.Hours(diff / hour)
        diff < 7 * day -> Age.Days(diff / day)
        else -> Age.Dated(date)
    }
}

@Composable
fun formatTimestamp(date: Date?, now: Long = System.currentTimeMillis()): String =
    when (val age = ageOf(date, now)) {
        null -> ""
        Age.Now -> stringResource(R.string.time_now)
        is Age.Minutes -> stringResource(R.string.time_minutes, age.count)
        is Age.Hours -> stringResource(R.string.time_hours, age.count)
        is Age.Days -> stringResource(R.string.time_days, age.count)
        is Age.Dated -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(age.date)
    }
