package com.cocode.claudeemailapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.mail.FetchedMessage
import kotlinx.coroutines.launch

enum class Screen { Home, Setup, Settings, Conversation, Compose, Diagnostics }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeEmailApp(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val credentials by viewModel.credentials.collectAsState()
    val inbox by viewModel.inbox.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val homeBuckets by viewModel.homeBuckets.collectAsState()
    val archived by viewModel.archived.collectAsState()
    val probe by viewModel.probe.collectAsState()
    val send by viewModel.send.collectAsState()
    val pending by viewModel.pending.collectAsState()

    var screen by rememberSaveable { mutableStateOf(if (credentials == null) Screen.Setup else Screen.Home) }
    var editingCredentials by rememberSaveable { mutableStateOf(false) }
    var selectedConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(credentials) {
        if (credentials != null && screen == Screen.Setup && !editingCredentials) screen = Screen.Home
        if (credentials == null) screen = Screen.Setup
    }

    DisposableEffect(credentials) {
        if (credentials != null) viewModel.startInboxPolling()
        onDispose { viewModel.stopInboxPolling() }
    }

    LaunchedEffect(probe.result) {
        if (probe.result is com.cocode.claudeemailapp.mail.ProbeResult.Success && editingCredentials) {
            editingCredentials = false
            screen = Screen.Settings
            viewModel.clearProbeResult()
        }
    }

    LaunchedEffect(send.justSentMessageId, send.lastError) {
        send.justSentMessageId?.let {
            scope.launch { snackbarHostState.showSnackbar("Message sent") }
            viewModel.clearSendResult()
            if (screen == Screen.Compose) screen = Screen.Home
        }
        send.lastError?.let { scope.launch { snackbarHostState.showSnackbar("Send failed: $it") } }
    }

    fun toggleArchiveWithUndo(conversation: Conversation) {
        val wasArchived = conversation.id in archived
        viewModel.setConversationArchived(conversation.id, !wasArchived)
        scope.launch {
            val msg = if (wasArchived) "Unarchived" else "Archived"
            val result = snackbarHostState.showSnackbar(message = msg, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.setConversationArchived(conversation.id, wasArchived)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppTopBar(screen = screen, editingCredentials = editingCredentials) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(innerPadding)) {
            AppNavHost(
                screen = screen,
                onScreenChange = { screen = it },
                credentials = credentials,
                inbox = inbox,
                conversations = conversations,
                homeBuckets = homeBuckets,
                archived = archived,
                pending = pending,
                send = send,
                probe = probe,
                editingCredentials = editingCredentials,
                onEditCredentials = { editingCredentials = true },
                selectedConversationId = selectedConversationId,
                onSelectConversation = { selectedConversationId = it },
                onArchiveToggle = ::toggleArchiveWithUndo,
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(screen: Screen, editingCredentials: Boolean) {
    TopAppBar(
        title = {
            Text(
                text = when (screen) {
                    Screen.Setup -> if (editingCredentials) "Edit credentials" else "Setup"
                    Screen.Home -> "Claude Email"
                    Screen.Settings -> "Settings"
                    Screen.Conversation -> "Conversation"
                    Screen.Compose -> "New command"
                    Screen.Diagnostics -> "Diagnostics"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        windowInsets = WindowInsets.statusBars
    )
}

@Composable
private fun AppNavHost(
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
    credentials: com.cocode.claudeemailapp.data.MailCredentials?,
    inbox: AppViewModel.InboxState,
    conversations: List<Conversation>,
    homeBuckets: AppViewModel.HomeBuckets,
    archived: Set<String>,
    pending: List<com.cocode.claudeemailapp.data.PendingCommand>,
    send: AppViewModel.SendState,
    probe: AppViewModel.ProbeState,
    editingCredentials: Boolean,
    onEditCredentials: () -> Unit,
    selectedConversationId: String?,
    onSelectConversation: (String?) -> Unit,
    onArchiveToggle: (Conversation) -> Unit,
    viewModel: AppViewModel
) {
    when (screen) {
        Screen.Setup -> SetupScreen(
            viewModel = viewModel,
            initial = if (editingCredentials) credentials else null
        )
        Screen.Home -> HomeScreen(
            state = inbox,
            buckets = homeBuckets,
            pending = pending,
            onRefresh = { viewModel.refreshInbox() },
            onOpenConversation = {
                onSelectConversation(it.id)
                onScreenChange(Screen.Conversation)
            },
            onCompose = { onScreenChange(Screen.Compose) },
            onOpenSettings = { onScreenChange(Screen.Settings) },
            onArchiveToggle = onArchiveToggle
        )
        Screen.Settings -> credentials?.let {
            SettingsScreen(
                credentials = it,
                onBack = { onScreenChange(Screen.Home) },
                onSignOut = {
                    viewModel.signOut()
                    onScreenChange(Screen.Setup)
                },
                onEdit = {
                    onEditCredentials()
                    onScreenChange(Screen.Setup)
                },
                onOpenDiagnostics = { onScreenChange(Screen.Diagnostics) }
            )
        }
        Screen.Diagnostics -> DiagnosticsScreen(
            credentials = credentials,
            inbox = inbox,
            sendError = send.lastError,
            pending = pending,
            onBack = { onScreenChange(Screen.Settings) }
        )
        Screen.Conversation -> {
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
                    }
                )
            }
        }
        Screen.Compose -> ComposeMessageScreen(
            defaultTo = credentials?.serviceAddress.orEmpty(),
            defaultProject = "",
            sending = send.sending,
            sendError = send.lastError,
            onCancel = { onScreenChange(Screen.Home) },
            onSend = { to, project, body ->
                viewModel.sendCommand(to = to, project = project, body = body)
            }
        )
    }
}

internal fun matchPendingForConversation(
    conversation: Conversation,
    pendings: List<com.cocode.claudeemailapp.data.PendingCommand>
): com.cocode.claudeemailapp.data.PendingCommand? {
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

