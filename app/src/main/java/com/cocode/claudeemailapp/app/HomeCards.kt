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
    conversationCount: Int,
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
                text = if (loading) "Syncing…" else pluralize(conversationCount, "conversation"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
internal fun PendingSummary(pending: List<PendingCommand>) {
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
            }
        }
    }
}

@Composable
internal fun HomeErrorCard(message: String) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "Sync failed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
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

private fun pluralize(n: Int, singular: String): String =
    if (n == 1) "1 $singular" else "$n ${singular}s"
