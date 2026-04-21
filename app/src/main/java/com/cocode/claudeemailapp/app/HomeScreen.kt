package com.cocode.claudeemailapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Kinds
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    state: AppViewModel.InboxState,
    pending: List<PendingCommand>,
    onRefresh: () -> Unit,
    onOpenMessage: (FetchedMessage) -> Unit,
    onCompose: () -> Unit,
    onOpenSettings: () -> Unit,
    pendingMutationIds: Set<String> = emptySet(),
    onSwipeDelete: (String) -> Unit = {},
    onSwipeArchive: (String) -> Unit = {}
) {
    val visibleMessages = state.messages.filter { it.messageId !in pendingMutationIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeroCard(
                loading = state.loading,
                messageCount = visibleMessages.size,
                onCompose = onCompose,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings
            )
        }
        state.error?.let {
            item { ErrorCard(message = it) }
        }
        if (pending.isNotEmpty()) {
            item { PendingSummary(pending = pending) }
        }
        if (visibleMessages.isEmpty() && !state.loading && state.error == null) {
            item { EmptyCard() }
        }
        items(visibleMessages, key = { it.messageId.ifBlank { it.subject + it.sentAt?.time } }) { message ->
            SwipeableMessageCard(
                message = message,
                onOpen = { onOpenMessage(message) },
                onSwipeDelete = { onSwipeDelete(message.messageId) },
                onSwipeArchive = { onSwipeArchive(message.messageId) }
            )
        }
    }
}

@Composable
private fun HeroCard(
    loading: Boolean,
    messageCount: Int,
    onCompose: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Inbox", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = if (loading) "Syncing…" else "$messageCount messages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCompose,
                    modifier = Modifier.testTag("home_new_message_button")
                ) { Text("New command") }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier.testTag("home_refresh_button")
                ) { Text(if (loading) "Refreshing…" else "Refresh") }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_settings_button")
                ) { Text("Settings") }
            }
        }
    }
}

@Composable
private fun PendingSummary(pending: List<PendingCommand>) {
    val live = pending.filter { it.status !in setOf(PendingStatus.DONE, PendingStatus.FAILED, PendingStatus.ERROR) }
    if (live.isEmpty()) return
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("pending_summary")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Pending — ${live.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            for (p in live.take(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status = p.status)
                    p.taskId?.let { Text(text = "#$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                    Text(
                        text = p.bodyPreview.take(80).replace('\n', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessageCard(
    message: FetchedMessage,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = message.fromName?.takeIf(String::isNotBlank) ?: message.from.ifBlank { "(unknown sender)" },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.padding(start = 8.dp))
                Text(
                    text = formatTimestamp(message.sentAt ?: message.receivedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            message.envelope?.let { env ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KindChip(kind = env.kind)
                    env.taskId?.let { Text(text = "#$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Text(
                text = message.subject.ifBlank { "(no subject)" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (message.body.isNotBlank()) {
                Text(
                    text = message.body.take(200).replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Sync failed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun EmptyCard() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Inbox is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Send a command to your claude-email service and the reply will land here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun KindChip(kind: String) {
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
internal fun StatusChip(status: String) {
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
private fun ChipPill(label: String, accent: Color) {
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

internal fun formatTimestamp(date: Date?, now: Long = System.currentTimeMillis()): String {
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
