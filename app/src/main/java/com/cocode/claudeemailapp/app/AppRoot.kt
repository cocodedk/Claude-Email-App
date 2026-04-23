package com.cocode.claudeemailapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocode.claudeemailapp.mail.FetchedMessage
import kotlinx.coroutines.launch

enum class Screen { Home, Setup, Settings, Conversation, Compose }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeEmailApp(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val credentials by viewModel.credentials.collectAsState()
    val inbox by viewModel.inbox.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val probe by viewModel.probe.collectAsState()
    val send by viewModel.send.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val mutation by viewModel.messageMutation.state.collectAsState()

    var screen by rememberSaveable { mutableStateOf(if (credentials == null) Screen.Setup else Screen.Home) }
    var editingCredentials by rememberSaveable { mutableStateOf(false) }
    var selectedMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(credentials) {
        if (credentials != null && screen == Screen.Setup && !editingCredentials) {
            screen = Screen.Home
        }
        if (credentials == null) {
            screen = Screen.Setup
        }
    }

    DisposableEffect(credentials) {
        if (credentials != null) viewModel.startInboxPolling()
        onDispose { viewModel.stopInboxPolling() }
    }

    LaunchedEffect(probe.result) {
        val result = probe.result
        if (result is com.cocode.claudeemailapp.mail.ProbeResult.Success && editingCredentials) {
            editingCredentials = false
            screen = Screen.Settings
            viewModel.clearProbeResult()
        }
    }

    LaunchedEffect(send.justSentMessageId, send.lastError) {
        send.justSentMessageId?.let {
            scope.launch { snackbarHostState.showSnackbar("Message sent") }
            viewModel.clearSendResult()
            if (screen == Screen.Compose || screen == Screen.Conversation) {
                screen = Screen.Home
            }
        }
        send.lastError?.let {
            scope.launch { snackbarHostState.showSnackbar("Send failed: $it") }
        }
    }

    LaunchedEffect(mutation.lastScheduled) {
        val s = mutation.lastScheduled ?: return@LaunchedEffect
        val label = when (s.action) {
            MessageMutationController.Action.DELETE -> "Message deleted"
            MessageMutationController.Action.ARCHIVE -> "Message archived"
        }
        val result = snackbarHostState.showSnackbar(
            message = label,
            actionLabel = "Undo",
            duration = androidx.compose.material3.SnackbarDuration.Short
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.messageMutation.undo(s.messageId)
        } else {
            viewModel.messageMutation.consumeLastScheduled()
        }
    }

    LaunchedEffect(mutation.lastError) {
        mutation.lastError?.let {
            scope.launch { snackbarHostState.showSnackbar("Server error: $it") }
            viewModel.messageMutation.clearError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (screen) {
                            Screen.Setup -> if (editingCredentials) "Edit credentials" else "Setup"
                            Screen.Home -> "Claude Email"
                            Screen.Settings -> "Settings"
                            Screen.Conversation -> "Conversation"
                            Screen.Compose -> "New command"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
        ) {
            when (screen) {
                Screen.Setup -> SetupScreen(
                    viewModel = viewModel,
                    initial = if (editingCredentials) credentials else null
                )
                Screen.Home -> HomeScreen(
                    state = inbox,
                    conversations = conversations,
                    pending = pending,
                    onRefresh = { viewModel.refreshInbox() },
                    onOpenConversation = { conversation ->
                        selectedMessageId = conversation.lastMessage.messageId
                        screen = Screen.Conversation
                    },
                    onCompose = { screen = Screen.Compose },
                    onOpenSettings = { screen = Screen.Settings }
                )
                Screen.Settings -> credentials?.let {
                    SettingsScreen(
                        credentials = it,
                        onBack = { screen = Screen.Home },
                        onSignOut = {
                            viewModel.signOut()
                            screen = Screen.Setup
                        },
                        onEdit = {
                            editingCredentials = true
                            screen = Screen.Setup
                        }
                    )
                }
                Screen.Conversation -> {
                    val message = inbox.messages.firstOrNull { it.messageId == selectedMessageId }
                    val matchedPending = message?.let { matchPendingForMessage(it, pending) }
                    if (message == null) {
                        screen = Screen.Home
                    } else {
                        ConversationScreen(
                            message = message,
                            sending = send.sending,
                            sendError = send.lastError,
                            onBack = { screen = Screen.Home },
                            onSendReply = { body ->
                                viewModel.sendMessage(
                                    to = replyTo(message, credentials?.emailAddress),
                                    subject = replySubject(message.subject, credentials?.sharedSecret),
                                    body = body,
                                    inReplyTo = message.messageId.takeIf(String::isNotBlank),
                                    references = buildReferences(message)
                                )
                            },
                            pending = matchedPending,
                            onSteeringIntent = { intent ->
                                matchedPending?.let { viewModel.dispatchSteering(it, intent) }
                            },
                            onDeleteMessage = {
                                viewModel.messageMutation.scheduleDelete(message.messageId)
                                screen = Screen.Home
                            },
                            onArchiveMessage = {
                                viewModel.messageMutation.scheduleArchive(message.messageId)
                                screen = Screen.Home
                            }
                        )
                    }
                }
                Screen.Compose -> ComposeMessageScreen(
                    defaultTo = credentials?.serviceAddress.orEmpty(),
                    defaultProject = "",
                    sending = send.sending,
                    sendError = send.lastError,
                    onCancel = { screen = Screen.Home },
                    onSend = { to, project, body ->
                        viewModel.sendCommand(to = to, project = project, body = body)
                    }
                )
            }
        }
    }
}

/**
 * Resolve which PendingCommand a given fetched message belongs to. We accept
 * multiple matches because RFC 5322 threading varies: some backends reply to
 * the original outgoing command, others thread against the ack and use the
 * References header for the full chain. Falling back to taskId catches
 * hand-crafted messages that carry an envelope but skip threading entirely.
 */
internal fun matchPendingForMessage(
    message: FetchedMessage,
    pendings: List<com.cocode.claudeemailapp.data.PendingCommand>
): com.cocode.claudeemailapp.data.PendingCommand? {
    if (pendings.isEmpty()) return null
    // The user opened their own outgoing command (rare — usually in Sent only).
    pendings.firstOrNull { it.messageId == message.messageId }?.let { return it }
    // Direct reply.
    val inReplyTo = message.inReplyTo
    if (!inReplyTo.isNullOrBlank()) {
        pendings.firstOrNull { it.messageId == inReplyTo }?.let { return it }
    }
    // Deeper in the thread — the original command sits in References.
    if (message.references.isNotEmpty()) {
        pendings.firstOrNull { it.messageId in message.references }?.let { return it }
    }
    // Last resort: match by task id carried on the envelope.
    val taskId = message.envelope?.taskId
    if (taskId != null) {
        pendings.firstOrNull { it.taskId == taskId }?.let { return it }
    }
    return null
}

private fun replyTo(message: FetchedMessage, selfAddress: String?): String {
    val from = message.from
    if (from.isNotBlank() && !from.equals(selfAddress, ignoreCase = true)) return from
    return message.to.firstOrNull { !it.equals(selfAddress, ignoreCase = true) } ?: from
}

private fun replySubject(original: String, sharedSecret: String?): String {
    val base = if (original.trim().startsWith("Re:", ignoreCase = true)) original else "Re: $original"
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
