package com.cocode.claudeemailapp.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocode.claudeemailapp.app.steering.SteeringIntent
import com.cocode.claudeemailapp.app.steering.envelopeForSteering
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.data.ConversationGrouper
import com.cocode.claudeemailapp.data.ConversationStateStore
import com.cocode.claudeemailapp.data.CredentialsStore
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingCommandStore
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.mail.ImapMailFetcher
import com.cocode.claudeemailapp.mail.MailException
import com.cocode.claudeemailapp.mail.MailFetcher
import com.cocode.claudeemailapp.mail.MailProbe
import com.cocode.claudeemailapp.mail.MailSender
import com.cocode.claudeemailapp.mail.OutgoingMessage
import com.cocode.claudeemailapp.mail.ProbeResult
import com.cocode.claudeemailapp.mail.SmtpMailSender
import com.cocode.claudeemailapp.protocol.Envelopes
import com.cocode.claudeemailapp.protocol.envelope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppViewModel(
    application: Application,
    private val credentialsStore: CredentialsStore,
    private val mailSender: MailSender,
    private val mailFetcher: MailFetcher,
    private val mailProbe: MailProbe,
    private val pendingStore: PendingCommandStore,
    private val conversationStateStore: ConversationStateStore
) : AndroidViewModel(application) {

    enum class HomeFilter { ACTIVE, WAITING, ARCHIVED }

    data class HomeBuckets(
        val active: List<Conversation> = emptyList(),
        val waiting: List<Conversation> = emptyList(),
        val archived: List<Conversation> = emptyList()
    ) {
        operator fun get(filter: HomeFilter): List<Conversation> = when (filter) {
            HomeFilter.ACTIVE -> active
            HomeFilter.WAITING -> waiting
            HomeFilter.ARCHIVED -> archived
        }
    }

    data class InboxState(
        val loading: Boolean = false,
        val messages: List<FetchedMessage> = emptyList(),
        val error: String? = null,
        val lastFetchedAt: Long? = null
    )

    data class ProbeState(
        val running: Boolean = false,
        val result: ProbeResult? = null
    )

    data class SendState(
        val sending: Boolean = false,
        val lastError: String? = null,
        val justSentMessageId: String? = null
    )

    private val _credentials = MutableStateFlow(credentialsStore.load())
    val credentials: StateFlow<MailCredentials?> = _credentials.asStateFlow()

    private val _inbox = MutableStateFlow(InboxState())
    val inbox: StateFlow<InboxState> = _inbox.asStateFlow()

    private val _probe = MutableStateFlow(ProbeState())
    val probe: StateFlow<ProbeState> = _probe.asStateFlow()

    private val _send = MutableStateFlow(SendState())
    val send: StateFlow<SendState> = _send.asStateFlow()

    private val _pending = MutableStateFlow(pendingStore.all())
    val pending: StateFlow<List<PendingCommand>> = _pending.asStateFlow()

    val conversations: StateFlow<List<Conversation>> = combine(_inbox, _credentials) { inbox, creds ->
        if (creds == null) emptyList()
        else ConversationGrouper.group(inbox.messages, creds.emailAddress)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _archived = MutableStateFlow(conversationStateStore.loadArchivedIds())
    val archived: StateFlow<Set<String>> = _archived.asStateFlow()

    private val _syncIntervalMs = MutableStateFlow(conversationStateStore.loadSyncIntervalMs())
    val syncIntervalMs: StateFlow<Long> = _syncIntervalMs.asStateFlow()

    private val _isForegroundActive = MutableStateFlow(true)

    private val _hasSeenOnboarding = MutableStateFlow(conversationStateStore.loadHasSeenOnboarding())
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    private val _recentProjects = MutableStateFlow(conversationStateStore.loadRecentProjects())
    val recentProjects: StateFlow<List<String>> = _recentProjects.asStateFlow()

    fun markOnboardingSeen() {
        if (_hasSeenOnboarding.value) return
        conversationStateStore.markOnboardingSeen()
        _hasSeenOnboarding.value = true
    }

    /** Effective poll interval given user preference + current fg/bg state. */
    internal fun effectivePollIntervalMs(): Long {
        val pref = _syncIntervalMs.value
        if (pref <= 0L) return 0L
        return if (_isForegroundActive.value) FOREGROUND_POLL_INTERVAL_MS else pref
    }

    /** Lifecycle hook — invoke on activity ON_START / ON_STOP. */
    fun setForegroundActive(active: Boolean) {
        if (_isForegroundActive.value == active) return
        _isForegroundActive.value = active
        if (_credentials.value == null) return
        val interval = effectivePollIntervalMs()
        stopInboxPolling()
        if (interval > 0) startInboxPolling(interval)
    }

    val homeBuckets: StateFlow<HomeBuckets> = combine(conversations, _pending, _archived) { convs, pend, arc ->
        val (archivedC, liveC) = convs.partition { it.id in arc }
        val askingIds = pend.filter { it.status == PendingStatus.AWAITING_USER }
            .map { it.messageId }.toSet()
        val (waiting, active) = liveC.partition { c ->
            c.messages.any { it.messageId in askingIds }
        }
        HomeBuckets(active = active, waiting = waiting, archived = archivedC)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeBuckets())

    fun setSyncIntervalMs(ms: Long) {
        if (ms == _syncIntervalMs.value) return
        _syncIntervalMs.value = ms
        conversationStateStore.saveSyncIntervalMs(ms)
        stopInboxPolling()
        val effective = effectivePollIntervalMs()
        if (effective > 0) startInboxPolling(effective)
    }

    fun setConversationArchived(conversationId: String, archived: Boolean) {
        val current = _archived.value
        val updated = if (archived) current + conversationId else current - conversationId
        if (updated == current) return
        _archived.value = updated
        conversationStateStore.saveArchivedIds(updated)
    }

    private var pollingJob: Job? = null

    init {
        if (_credentials.value != null) refreshInbox()
    }

    fun startInboxPolling(intervalMs: Long = effectivePollIntervalMs()) {
        if (pollingJob?.isActive == true) return
        if (intervalMs <= 0) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                if (_credentials.value != null && !_inbox.value.loading) refreshInbox()
            }
        }
    }

    fun stopInboxPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun probeAndSave(credentials: MailCredentials) {
        viewModelScope.launch {
            _probe.value = ProbeState(running = true)
            val result = mailProbe.probe(credentials)
            _probe.value = ProbeState(running = false, result = result)
            if (result is ProbeResult.Success) {
                credentialsStore.save(credentials)
                _credentials.value = credentials
                refreshInbox()
            }
        }
    }

    fun clearProbeResult() {
        _probe.value = ProbeState()
    }

    fun refreshInbox() {
        val creds = _credentials.value ?: return
        viewModelScope.launch {
            _inbox.value = _inbox.value.copy(loading = true, error = null)
            try {
                val messages = mailFetcher.fetchRecent(creds, count = 50)
                _inbox.value = InboxState(
                    loading = false,
                    messages = messages,
                    lastFetchedAt = System.currentTimeMillis()
                )
                reconcilePending(messages)
            } catch (e: MailException) {
                _inbox.value = _inbox.value.copy(loading = false, error = e.message)
            } catch (t: Throwable) {
                _inbox.value = _inbox.value.copy(loading = false, error = t.message)
            }
        }
    }

    private fun reconcilePending(messages: List<FetchedMessage>) {
        var touched = false
        for (m in messages) {
            val env = m.envelope ?: continue
            val updated = pendingStore.applyInbound(env, m.inReplyTo)
            if (updated != null) touched = true
        }
        if (touched) _pending.value = pendingStore.all()
    }

    fun sendMessage(
        to: String,
        subject: String,
        body: String,
        inReplyTo: String? = null,
        references: List<String> = emptyList()
    ) {
        val creds = _credentials.value ?: return
        viewModelScope.launch {
            _send.value = SendState(sending = true)
            try {
                val result = mailSender.send(
                    credentials = creds,
                    message = OutgoingMessage(
                        to = to,
                        subject = subject,
                        body = body,
                        inReplyTo = inReplyTo,
                        references = references
                    )
                )
                _send.value = SendState(sending = false, justSentMessageId = result.messageId)
                refreshInbox()
            } catch (e: MailException) {
                _send.value = SendState(sending = false, lastError = e.message)
            } catch (t: Throwable) {
                _send.value = SendState(sending = false, lastError = t.message)
            }
        }
    }

    fun sendCommand(
        to: String,
        project: String,
        body: String,
        priority: Int? = null,
        planFirst: Boolean? = null
    ) {
        val creds = _credentials.value ?: return
        viewModelScope.launch {
            _send.value = SendState(sending = true)
            try {
                val envelope = Envelopes.command(
                    body = body,
                    project = project,
                    priority = priority,
                    planFirst = planFirst,
                    auth = creds.sharedSecret.takeIf(String::isNotBlank)
                )
                val subject = firstLineSummary(body)
                val outgoing = OutgoingMessage.envelope(
                    to = to,
                    subject = subject,
                    envelope = envelope
                )
                val result = mailSender.send(creds, outgoing)
                if (result.messageId.isNotBlank()) {
                    val pending = PendingCommand(
                        messageId = result.messageId,
                        sentAt = result.sentAt.time,
                        to = to,
                        subject = subject,
                        kind = envelope.kind,
                        bodyPreview = body.take(200),
                        project = project
                    )
                    pendingStore.add(pending)
                    _pending.value = pendingStore.all()
                    if (project.isNotBlank()) {
                        conversationStateStore.pushRecentProject(project)
                        _recentProjects.value = conversationStateStore.loadRecentProjects()
                    }
                }
                _send.value = SendState(sending = false, justSentMessageId = result.messageId)
                refreshInbox()
            } catch (e: MailException) {
                _send.value = SendState(sending = false, lastError = e.message)
            } catch (t: Throwable) {
                _send.value = SendState(sending = false, lastError = t.message)
            }
        }
    }

    fun dispatchSteering(pending: PendingCommand, intent: SteeringIntent) {
        val creds = _credentials.value ?: return
        val envelope = envelopeForSteering(
            pending = pending,
            intent = intent,
            auth = creds.sharedSecret.takeIf(String::isNotBlank)
        ) ?: return
        viewModelScope.launch {
            _send.value = SendState(sending = true)
            try {
                val outgoing = OutgoingMessage.envelope(
                    to = pending.to,
                    subject = "Re: ${pending.subject}",
                    envelope = envelope,
                    inReplyTo = pending.messageId.takeIf(String::isNotBlank)
                )
                val result = mailSender.send(creds, outgoing)
                _send.value = SendState(sending = false, justSentMessageId = result.messageId)
                refreshInbox()
            } catch (e: MailException) {
                _send.value = SendState(sending = false, lastError = e.message)
            } catch (t: Throwable) {
                _send.value = SendState(sending = false, lastError = t.message)
            }
        }
    }

    fun clearSendResult() {
        _send.value = SendState()
    }

    fun signOut() {
        stopInboxPolling()
        credentialsStore.clear()
        pendingStore.clear()
        _credentials.value = null
        _inbox.value = InboxState()
        _pending.value = emptyList()
    }

    companion object {
        /**
         * Inbox poll cadence while the activity is in the foreground. Background
         * polling falls back to the user-selected [syncIntervalMs] to keep battery
         * cost down; the picker in Settings configures that bg value.
         */
        const val FOREGROUND_POLL_INTERVAL_MS: Long = 15_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                AppViewModel(
                    application = app,
                    credentialsStore = CredentialsStore(app),
                    mailSender = SmtpMailSender(),
                    mailFetcher = ImapMailFetcher(),
                    mailProbe = MailProbe(),
                    pendingStore = PendingCommandStore(app),
                    conversationStateStore = ConversationStateStore(app)
                )
            }
        }
    }
}

private fun firstLineSummary(body: String): String {
    val first = body.lineSequence().firstOrNull()?.trim().orEmpty()
    return first.take(80).ifBlank { "Command" }
}
