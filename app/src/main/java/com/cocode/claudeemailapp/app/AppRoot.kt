package com.cocode.claudeemailapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.data.MailCredentials
import kotlinx.coroutines.launch

enum class Screen { Onboarding, Home, Setup, Settings, Conversation, Compose, Diagnostics, Projects, About }

@Composable
fun ClaudeEmailApp(
    viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory),
    prefill: MailCredentials? = null
) {
    val credentials by viewModel.credentials.collectAsState()
    val inbox by viewModel.inbox.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val homeBuckets by viewModel.homeBuckets.collectAsState()
    val archived by viewModel.archived.collectAsState()
    val syncIntervalMs by viewModel.syncIntervalMs.collectAsState()
    val probe by viewModel.probe.collectAsState()
    val send by viewModel.send.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsState()

    var screen by rememberSaveable {
        mutableStateOf(
            when {
                !hasSeenOnboarding -> Screen.Onboarding
                credentials == null -> Screen.Setup
                else -> Screen.Home
            }
        )
    }
    var editingCredentials by rememberSaveable { mutableStateOf(false) }
    var selectedConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedComposeProject by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    LaunchedEffect(credentials, hasSeenOnboarding) {
        val target = when {
            !hasSeenOnboarding -> Screen.Onboarding
            credentials == null && screen != Screen.Setup -> Screen.Setup
            credentials != null && screen == Screen.Setup && !editingCredentials -> Screen.Home
            else -> screen
        }
        if (target != screen) screen = target
    }

    val backTarget: Screen? = when (screen) {
        Screen.Conversation, Screen.Compose, Screen.Settings, Screen.Projects, Screen.About -> Screen.Home
        Screen.Diagnostics -> Screen.Settings
        Screen.Setup -> if (editingCredentials) Screen.Settings else null
        Screen.Onboarding, Screen.Home -> null
    }
    BackHandler(enabled = backTarget != null) {
        if (screen == Screen.Compose) selectedComposeProject = null
        if (screen == Screen.Setup && editingCredentials) editingCredentials = false
        backTarget?.let { screen = it }
    }

    DisposableEffect(credentials) {
        if (credentials != null) viewModel.startInboxPolling()
        onDispose { viewModel.stopInboxPolling() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setForegroundActive(true)
                Lifecycle.Event.ON_STOP -> viewModel.setForegroundActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.snackbar_message_sent)) }
            viewModel.clearSendResult()
            if (screen == Screen.Compose) screen = Screen.Home
        }
        send.lastError?.let {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.snackbar_send_failed, it)) }
        }
    }

    fun toggleArchiveWithUndo(conversation: Conversation) {
        val wasArchived = conversation.id in archived
        viewModel.setConversationArchived(conversation.id, !wasArchived)
        scope.launch {
            val msg = resources.getString(if (wasArchived) R.string.snackbar_unarchived else R.string.snackbar_archived)
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = resources.getString(R.string.snackbar_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.setConversationArchived(conversation.id, wasArchived)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (screen != Screen.Onboarding) {
                AppTopBar(screen = screen, editingCredentials = editingCredentials)
            }
        }
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
                syncIntervalMs = syncIntervalMs,
                viewModel = viewModel,
                prefill = prefill,
                selectedComposeProject = selectedComposeProject,
                onSelectComposeProject = { selectedComposeProject = it }
            )
        }
    }
}
