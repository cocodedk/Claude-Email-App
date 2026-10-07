package com.cocode.claudeemailapp.app

import androidx.compose.runtime.Composable
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.mail.FetchedMessage

/** The Conversation screen with the actions the app root wires to the view model. */
@Composable
internal fun ConversationRoute(
    conversations: List<Conversation>,
    selectedConversationId: String?,
    credentials: MailCredentials?,
    archived: Set<String>,
    pending: List<PendingCommand>,
    send: AppViewModel.SendState,
    viewModel: AppViewModel,
    onScreenChange: (Screen) -> Unit,
    onSelectComposeProject: (String?) -> Unit,
    onArchiveToggle: (Conversation) -> Unit
) {
    val conversation = conversations.firstOrNull { it.id == selectedConversationId }
    val matchedPending = conversation?.let { matchPendingForConversation(it, pending) }
    if (conversation == null) {
        onScreenChange(Screen.Home)
    } else {
        ConversationScreen(
            conversation = conversation,
            selfEmail = credentials?.emailAddress.orEmpty(),
            isArchived = conversation.id in archived,
            sending = send.sending,
            sendError = send.lastError,
            onBack = { onScreenChange(Screen.Home) },
            onSendReply = { body ->
                val latest = conversation.lastMessage
                viewModel.sendMessage(
                    to = replyTo(latest, credentials?.emailAddress),
                    subject = replySubject(conversation.title, credentials?.sharedSecret),
                    body = body,
                    inReplyTo = latest.messageId.takeIf(String::isNotBlank),
                    references = buildReferences(latest)
                )
            },
            onArchiveToggle = { onArchiveToggle(conversation) },
            pending = matchedPending,
            onSteeringIntent = { intent ->
                matchedPending?.let { viewModel.dispatchSteering(it, intent) }
            },
            onRetryCommand = {
                matchedPending?.let { p ->
                    viewModel.sendCommand(
                        to = p.to,
                        project = p.project.orEmpty(),
                        body = p.bodyPreview
                    )
                }
            },
            onOpenSettings = { onScreenChange(Screen.Settings) },
            onEditCommand = {
                onSelectComposeProject(null)
                onScreenChange(Screen.Compose)
            },
            onOpenDiagnostics = { onScreenChange(Screen.Diagnostics) },
            onMarkRead = { viewModel.markConversationRead(conversation.id) }
        )
    }
}

internal fun matchPendingForConversation(
    conversation: Conversation,
    pendings: List<PendingCommand>
): PendingCommand? {
    if (pendings.isEmpty()) return null
    val ids = conversation.messages.map { it.messageId }.toSet()
    pendings.firstOrNull { it.messageId in ids }?.let { return it }
    for (m in conversation.messages) {
        m.inReplyTo?.let { irt -> pendings.firstOrNull { it.messageId == irt }?.let { return it } }
        if (m.references.isNotEmpty()) {
            pendings.firstOrNull { it.messageId in m.references }?.let { return it }
        }
    }
    val taskIds = conversation.messages.mapNotNull { it.envelope?.taskId }.toSet()
    return pendings.firstOrNull { it.taskId in taskIds }
}

private fun replyTo(message: FetchedMessage, selfAddress: String?): String {
    val from = message.from
    if (from.isNotBlank() && !from.equals(selfAddress, ignoreCase = true)) return from
    return message.to.firstOrNull { !it.equals(selfAddress, ignoreCase = true) } ?: from
}

private fun replySubject(title: String, sharedSecret: String?): String {
    val base = if (title.trim().startsWith("Re:", ignoreCase = true)) title else "Re: $title"
    return when {
        sharedSecret.isNullOrBlank() -> base
        base.contains("AUTH:") -> base
        else -> "AUTH:$sharedSecret $base"
    }
}

private fun buildReferences(message: FetchedMessage): List<String> {
    val refs = message.references.toMutableList()
    if (message.messageId.isNotBlank() && message.messageId !in refs) refs.add(message.messageId)
    return refs
}

