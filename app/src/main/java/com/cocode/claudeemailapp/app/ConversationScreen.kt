package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.app.steering.SteeringBar
import com.cocode.claudeemailapp.app.steering.SteeringBarController
import com.cocode.claudeemailapp.app.steering.SteeringBarState
import com.cocode.claudeemailapp.app.steering.SteeringChip
import com.cocode.claudeemailapp.app.steering.SteeringChipVariant
import com.cocode.claudeemailapp.app.steering.SteeringIntent
import com.cocode.claudeemailapp.app.steering.SteeringTemplateSheet
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.mail.FetchedMessage
import kotlinx.coroutines.delay

@Composable
fun ConversationScreen(
    message: FetchedMessage,
    sending: Boolean,
    sendError: String?,
    onBack: () -> Unit,
    onSendReply: (body: String) -> Unit,
    pending: PendingCommand? = null,
    onSteeringIntent: (SteeringIntent) -> Unit = {},
    onDeleteMessage: () -> Unit = {},
    onArchiveMessage: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val latestIntent by rememberUpdatedState(onSteeringIntent)
    val controller = remember {
        SteeringBarController(scope).also { it.onIntent = { latestIntent(it) } }
    }
    val steering = SteeringBarState.from(pending)

    // Clear controller's in-flight flag whenever the pending command is
    // touched by an inbound envelope (ack/progress/result). Fires on every
    // lastUpdatedAt change; a no-op when nothing is in flight.
    LaunchedEffect(pending?.messageId, pending?.lastUpdatedAt) {
        controller.onAcked()
    }

    var reply by rememberSaveable(message.messageId) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("conversation_screen")
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                HeaderCard(
                    message = message,
                    onBack = onBack,
                    onDelete = onDeleteMessage,
                    onArchive = onArchiveMessage
                )
            }
            item(key = "body") { BodyCard(message = message) }
            sendError?.let {
                item(key = "error") { ErrorCard(message = it) }
            }
        }
        when (steering) {
            SteeringBarState.Idle -> SteeringBar(state = steering, controller = controller)
            is SteeringBarState.AwaitingUser -> SteeringTemplateSheet(
                onTemplateTap = { template ->
                    reply = if (reply.isBlank()) template else "$reply\n$template"
                }
            )
            SteeringBarState.Hidden -> Unit
        }
        ReplyComposer(
            reply = reply,
            onReplyChange = { reply = it },
            sending = sending,
            onSend = {
                val trimmed = reply.trim()
                if (trimmed.isNotBlank()) {
                    if (steering is SteeringBarState.AwaitingUser) {
                        onSteeringIntent(SteeringIntent.Reply(steering.askId, trimmed))
                    } else {
                        onSendReply(trimmed)
                    }
                    reply = ""
                }
            }
        )
    }
}

@Composable
private fun HeaderCard(
    message: FetchedMessage,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit
) {
    var deleteArmed by rememberSaveable(message.messageId) { mutableStateOf(false) }
    LaunchedEffect(deleteArmed) {
        if (deleteArmed) {
            delay(3_000)
            deleteArmed = false
        }
    }
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.subject.ifBlank { "(no subject)" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("conversation_subject")
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("conversation_back")) {
                    Text("Back")
                }
            }
            val senderLabel = message.fromName?.takeIf(String::isNotBlank)?.let {
                "$it <${message.from}>"
            } ?: message.from
            Text(
                text = "From: $senderLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (message.to.isNotEmpty()) {
                Text(
                    text = "To: ${message.to.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            message.envelope?.let { env ->
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KindChip(kind = env.kind)
                    env.taskId?.let {
                        Text(text = "task #$it", style = MaterialTheme.typography.labelMedium)
                    }
                }
                env.error?.let {
                    Text(
                        text = "${it.code}: ${it.message}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SteeringChip(
                    label = "Archive",
                    onClick = onArchive,
                    variant = SteeringChipVariant.Default,
                    modifier = Modifier.testTag("conversation_archive")
                )
                SteeringChip(
                    label = if (deleteArmed) "Delete · confirm" else "Delete",
                    onClick = {
                        if (deleteArmed) {
                            onDelete()
                            deleteArmed = false
                        } else {
                            deleteArmed = true
                        }
                    },
                    variant = if (deleteArmed) SteeringChipVariant.DangerArmed else SteeringChipVariant.Danger,
                    modifier = Modifier.testTag("conversation_delete")
                )
            }
        }
    }
}

@Composable
private fun BodyCard(message: FetchedMessage) {
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message.body.ifBlank { "(no text content)" },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Send failed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
