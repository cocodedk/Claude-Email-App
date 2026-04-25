package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
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
                PendingRow(p = p, onRetry = onRetry, onCancel = onCancel)
            }
        }
    }
}

@Composable
private fun PendingRow(
    p: PendingCommand,
    onRetry: (PendingCommand) -> Unit,
    onCancel: (PendingCommand) -> Unit
) {
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

internal fun pendingReasonLine(reason: String?, retryAfterSeconds: Int?): String? {
    val countdown = retryAfterSeconds?.takeIf { it > 0 }?.let { "retry in ${it}s" }
    return listOfNotNull(reason?.takeIf(String::isNotBlank), countdown)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

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
