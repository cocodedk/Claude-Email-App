package com.cocode.claudeemailapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cocode.claudeemailapp.BuildConfig
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand

@Composable
internal fun AppScreenContent(
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
    when (screen) {
        Screen.Onboarding -> OnboardingScreen(
            onFinish = {
                viewModel.markOnboardingSeen()
                onScreenChange(if (credentials == null) Screen.Setup else Screen.Home)
            }
        )
        Screen.Setup -> SetupScreen(
            viewModel = viewModel,
            initial = if (editingCredentials) credentials else prefill
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
            onCompose = {
                onSelectComposeProject(null)
                onScreenChange(Screen.Compose)
            },
            onOpenSettings = { onScreenChange(Screen.Settings) },
            onArchiveToggle = onArchiveToggle,
            onRetryPending = { p ->
                viewModel.sendCommand(to = p.to, project = p.project.orEmpty(), body = p.bodyPreview)
            },
            onCancelPending = { p ->
                viewModel.dispatchSteering(p, com.cocode.claudeemailapp.app.steering.SteeringIntent.Cancel)
            },
            onOpenProjects = {
                viewModel.refreshProjects()
                onScreenChange(Screen.Projects)
            },
            onOpenAbout = { onScreenChange(Screen.About) }
        )
        Screen.Projects -> {
            val projects by viewModel.projects.collectAsState()
            ProjectsScreen(
                state = projects,
                onRefresh = { viewModel.refreshProjects() },
                onProjectTap = { p ->
                    onSelectComposeProject(p.path)
                    onScreenChange(Screen.Compose)
                },
                onCompose = {
                    onSelectComposeProject(null)
                    onScreenChange(Screen.Compose)
                }
            )
        }
        Screen.Settings -> credentials?.let {
            val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
            SettingsScreen(
                credentials = it,
                syncIntervalMs = syncIntervalMs,
                onSyncIntervalChange = { viewModel.setSyncIntervalMs(it) },
                notificationsEnabled = notificationsEnabled,
                onNotificationsEnabledChange = { viewModel.setNotificationsEnabled(it) },
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
        Screen.About -> AboutScreen(
            version = BuildConfig.VERSION_NAME,
            showPrivacyLink = PRIVACY_URL != null,
            onOpenLink = rememberLinkOpener(),
            onBack = { onScreenChange(Screen.Home) }
        )
        Screen.Diagnostics -> DiagnosticsScreen(
            credentials = credentials,
            inbox = inbox,
            sendError = send.lastError,
            pending = pending,
            syncIntervalMs = syncIntervalMs,
            onBack = { onScreenChange(Screen.Settings) }
        )
        Screen.Conversation -> ConversationRoute(
            conversations = conversations,
            selectedConversationId = selectedConversationId,
            credentials = credentials,
            archived = archived,
            pending = pending,
            send = send,
            viewModel = viewModel,
            onScreenChange = onScreenChange,
            onSelectComposeProject = onSelectComposeProject,
            onArchiveToggle = onArchiveToggle
        )
        Screen.Compose -> ComposeMessageScreen(
            defaultTo = credentials?.serviceAddress.orEmpty(),
            defaultProject = selectedComposeProject.orEmpty(),
            sending = send.sending,
            sendError = send.lastError,
            onCancel = {
                onSelectComposeProject(null)
                onScreenChange(Screen.Home)
            },
            onSend = { to, project, body ->
                viewModel.sendCommand(to = to, project = project, body = body)
                onSelectComposeProject(null)
            }
        )
    }
}
