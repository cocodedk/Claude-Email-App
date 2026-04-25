package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.app.steering.SteeringBar
import com.cocode.claudeemailapp.app.steering.SteeringBarController
import com.cocode.claudeemailapp.app.steering.SteeringBarState
import com.cocode.claudeemailapp.app.steering.SteeringChip
import com.cocode.claudeemailapp.app.steering.SteeringChipVariant
import com.cocode.claudeemailapp.app.steering.SteeringIntent
import com.cocode.claudeemailapp.app.steering.SteeringTemplateSheet
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.mail.FetchedMessage

@Composable
fun ConversationScreen(
    conversation: Conversation,
    selfEmail: String,
    isArchived: Boolean,
    sending: Boolean,
    sendError: String?,
    onBack: () -> Unit,
    onSendReply: (body: String) -> Unit,
    onArchiveToggle: () -> Unit,
    pending: PendingCommand? = null,
    onSteeringIntent: (SteeringIntent) -> Unit = {},
    onRetryCommand: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditCommand: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val latestIntent by rememberUpdatedState(onSteeringIntent)
    val controller = remember {
        SteeringBarController(scope).also { it.onIntent = { latestIntent(it) } }
    }
    val steering = SteeringBarState.from(pending)

    LaunchedEffect(pending?.messageId, pending?.lastUpdatedAt) {
        controller.onAcked()
    }

    var reply by rememberSaveable(conversation.id) { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Auto-scroll to the newest message when the conversation opens or grows.
    // Item 0 is the header; the optional sendError card sits below the messages.
    LaunchedEffect(conversation.id, conversation.messages.size, sendError) {
        val target = conversation.messages.size + if (sendError != null) 1 else 0
        if (target > 0) listState.scrollToItem(target)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("conversation_screen")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                ConversationHeaderCard(
                    conversation = conversation,
                    isArchived = isArchived,
                    onBack = onBack,
                    onArchiveToggle = onArchiveToggle
                )
            }
            items(conversation.messages, key = { "msg-${it.messageId}" }) { msg ->
                ThreadMessageCard(
                    message = msg,
                    isFromSelf = msg.from.equals(selfEmail, ignoreCase = true),
                    onRetry = onRetryCommand,
                    onOpenSettings = onOpenSettings,
                    onEditCommand = onEditCommand,
                    onOpenDiagnostics = onOpenDiagnostics
                )
            }
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
private fun ConversationHeaderCard(
    conversation: Conversation,
    isArchived: Boolean,
    onBack: () -> Unit,
    onArchiveToggle: () -> Unit
) {
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
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("conversation_subject")
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("conversation_back")) {
                    Text("Back")
                }
            }
            Text(
                text = "${conversation.agentDisplay} · ${conversation.messageCount} ${if (conversation.messageCount == 1) "message" else "messages"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteeringChip(
                    label = if (isArchived) "Unarchive" else "Archive",
                    onClick = onArchiveToggle,
                    variant = SteeringChipVariant.Default,
                    modifier = Modifier.testTag("conversation_archive")
                )
            }
        }
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
