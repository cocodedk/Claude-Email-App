package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus

@Composable
internal fun HeroCard(
    loading: Boolean,
    buckets: AppViewModel.HomeBuckets,
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
            if (loading) {
                Text(
                    text = "Syncing…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HeroCounters(buckets = buckets)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onCompose, modifier = Modifier.testTag("home_new_message_button")) {
                    Text("New command")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier.testTag("home_refresh_button")
                ) { Text(if (loading) "Refreshing…" else "Refresh") }
                TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_settings_button")) {
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
internal fun PendingSummary(
    pending: List<PendingCommand>,
    onRetry: (PendingCommand) -> Unit = {},
    onCancel: (PendingCommand) -> Unit = {}
) {
    // Show in-flight AND recently-failed rows so the user can retry/cancel
    // them without digging into Diagnostics.
    val visible = pending.filter { it.status != PendingStatus.DONE }
    if (visible.isEmpty()) return
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
                text = "Pending — ${visible.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            for (p in visible.take(3)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(status = p.status)
                        p.taskId?.let {
                            Text(
                                text = "#$it",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = p.bodyPreview.take(80).replace('\n', ' '),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    pendingReasonLine(p.reason, p.retryAfterSeconds)?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    PendingRowActions(p = p, onRetry = onRetry, onCancel = onCancel)
                }
            }
        }
    }
}

internal val RETRYABLE_STATUSES = setOf(PendingStatus.FAILED, PendingStatus.ERROR)
internal val CANCELLABLE_STATUSES = setOf(
    PendingStatus.QUEUED,
    PendingStatus.RUNNING,
    PendingStatus.AWAITING_USER,
    PendingStatus.STALLED,
    PendingStatus.WAITING_ON_PEER
)

internal fun isRetryable(p: PendingCommand): Boolean = p.status in RETRYABLE_STATUSES
internal fun isCancellable(p: PendingCommand): Boolean =
    p.status in CANCELLABLE_STATUSES && p.taskId != null

@Composable
private fun PendingRowActions(
    p: PendingCommand,
    onRetry: (PendingCommand) -> Unit,
    onCancel: (PendingCommand) -> Unit
) {
    val retry = isRetryable(p)
    val cancel = isCancellable(p)
    if (!retry && !cancel) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (retry) {
            TextButton(
                onClick = { onRetry(p) },
                modifier = Modifier.testTag("pending_retry_${p.messageId}")
            ) { Text("Retry") }
        }
        if (cancel) {
            TextButton(
                onClick = { onCancel(p) },
                modifier = Modifier.testTag("pending_cancel_${p.messageId}")
            ) { Text("Cancel") }
        }
    }
}

@Composable
internal fun EmptyBucketCard(
    filter: AppViewModel.HomeFilter,
    onCompose: () -> Unit = {}
) {
    val (heading, body) = when (filter) {
        AppViewModel.HomeFilter.ACTIVE -> "Nothing active" to "Send a command to your claude-email service and the reply will land here."
        AppViewModel.HomeFilter.WAITING -> "No conversations need a reply" to "When the agent asks a question, it will show up here."
        AppViewModel.HomeFilter.ARCHIVED -> "Archive is empty" to "Swipe a conversation left to archive it."
    }
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(heading, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (filter == AppViewModel.HomeFilter.ACTIVE) {
                Button(
                    onClick = onCompose,
                    modifier = Modifier.testTag("empty_active_send_cta")
                ) { Text("Send first command") }
            }
        }
    }
}

@Composable
private fun HeroCounters(buckets: AppViewModel.HomeBuckets) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("home_counters"),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HeroCounter(label = "Active", value = buckets.active.size, accent = MaterialTheme.colorScheme.primary)
        HeroCounter(label = "Waiting", value = buckets.waiting.size, accent = MaterialTheme.colorScheme.secondary)
        HeroCounter(label = "Archived", value = buckets.archived.size, accent = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroCounter(label: String, value: Int, accent: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = accent
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun pendingReasonLine(reason: String?, retryAfterSeconds: Int?): String? {
    val countdown = retryAfterSeconds?.takeIf { it > 0 }?.let { "retry in ${it}s" }
    return listOfNotNull(reason?.takeIf(String::isNotBlank), countdown)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}
