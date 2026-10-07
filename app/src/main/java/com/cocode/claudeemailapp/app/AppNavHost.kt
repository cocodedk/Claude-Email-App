package com.cocode.claudeemailapp.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand

@Composable
internal fun AppNavHost(
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
    credentials: MailCredentials?,
    inbox: AppViewModel.InboxState,
    conversations: List<Conversation>,
    homeBuckets: AppViewModel.HomeBuckets,
    archived: Set<String>,
    pending: List<PendingCommand>,
    send: AppViewModel.SendState,
    probe: AppViewModel.ProbeState,
    editingCredentials: Boolean,
    onEditCredentials: () -> Unit,
    selectedConversationId: String?,
    onSelectConversation: (String?) -> Unit,
    onArchiveToggle: (Conversation) -> Unit,
    syncIntervalMs: Long,
    viewModel: AppViewModel,
    prefill: MailCredentials?,
    selectedComposeProject: String?,
    onSelectComposeProject: (String?) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    Crossfade(
        targetState = screen,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 220),
        label = "screen"
    ) { current -> AppScreenContent(
        screen = current,
        onScreenChange = onScreenChange,
        credentials = credentials,
        inbox = inbox,
        conversations = conversations,
        homeBuckets = homeBuckets,
        archived = archived,
        pending = pending,
        send = send,
        probe = probe,
        editingCredentials = editingCredentials,
        onEditCredentials = onEditCredentials,
        selectedConversationId = selectedConversationId,
        onSelectConversation = onSelectConversation,
        onArchiveToggle = onArchiveToggle,
        syncIntervalMs = syncIntervalMs,
        viewModel = viewModel,
        prefill = prefill,
        selectedComposeProject = selectedComposeProject,
        onSelectComposeProject = onSelectComposeProject
    ) }
}
