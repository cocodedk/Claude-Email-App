package com.cocode.claudeemailapp.app

import android.app.Application
import com.cocode.claudeemailapp.app.steering.SteeringIntent
import com.cocode.claudeemailapp.data.CredentialsStore
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingCommandStore
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.mail.MailException
import com.cocode.claudeemailapp.mail.MailFetcher
import com.cocode.claudeemailapp.mail.MailProbe
import com.cocode.claudeemailapp.mail.MailSender
import com.cocode.claudeemailapp.mail.OutgoingMessage
import com.cocode.claudeemailapp.mail.ProbeResult
import com.cocode.claudeemailapp.mail.SendResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun creds(email: String = "me@ex.com") = MailCredentials(
        displayName = "me",
        emailAddress = email,
        password = "p",
        imapHost = "imap.ex",
        imapPort = 993,
        smtpHost = "smtp.ex",
        smtpPort = 465,
        smtpUseStartTls = false,
        serviceAddress = "svc@ex",
        sharedSecret = "s"
    )

    private fun buildVm(
        initialCreds: MailCredentials? = null,
        store: CredentialsStore = FakeCredentialsStore(initialCreds),
        sender: MailSender = mockk(relaxed = true),
        fetcher: MailFetcher = mockk<MailFetcher>().apply {
            coEvery { fetchRecent(any(), any()) } returns emptyList()
        },
        probe: MailProbe = mockk(relaxed = true),
        pending: PendingCommandStore = FakePendingCommandStore(),
        conversationState: com.cocode.claudeemailapp.data.ConversationStateStore = FakeConversationStateStore(),
        notifier: InboxNotifier? = null
    ): AppViewModel {
        val app = mockk<Application>(relaxed = true)
        return AppViewModel(app, store, sender, fetcher, probe, pending, conversationState, notifier)
    }

    private class FakeConversationStateStore(
        initialSyncMs: Long = 60_000L,
        private var onboardingSeen: Boolean = false
    ) : com.cocode.claudeemailapp.data.ConversationStateStore {
        private var ids: Set<String> = emptySet()
        private var syncMs: Long = initialSyncMs
        private var recent: List<String> = emptyList()
        override fun loadArchivedIds(): Set<String> = ids
        override fun saveArchivedIds(ids: Set<String>) { this.ids = ids.toSet() }
        override fun loadSyncIntervalMs(): Long = syncMs
        override fun saveSyncIntervalMs(ms: Long) { syncMs = ms }
        override fun loadHasSeenOnboarding(): Boolean = onboardingSeen
        override fun markOnboardingSeen() { onboardingSeen = true }
        override fun loadRecentProjects(): List<String> = recent
        override fun pushRecentProject(project: String) {
            val t = project.trim()
            if (t.isBlank()) return
            recent = (listOf(t) + recent.filter { it != t }).take(5)
        }
        override fun clear() {
            ids = emptySet()
            syncMs = com.cocode.claudeemailapp.data.ConversationStateStore.DEFAULT_SYNC_INTERVAL_MS
            recent = emptyList()
        }
    }

    private class FakeCredentialsStore(initial: MailCredentials? = null) : CredentialsStore {
        private var current: MailCredentials? = initial
        override fun hasCredentials(): Boolean = current != null
        override fun load(): MailCredentials? = current
        override fun save(credentials: MailCredentials) { current = credentials }
        override fun clear() { current = null }
    }

    class FakePendingCommandStore : PendingCommandStore {
        val entries = linkedMapOf<String, PendingCommand>()
        override fun add(pending: PendingCommand) { entries[pending.messageId] = pending }
        override fun findByMessageId(messageId: String) = entries[messageId]
        override fun findByTaskId(taskId: Long) = entries.values.firstOrNull { it.taskId == taskId }
        override fun all() = entries.values.toList()
        override fun clear() { entries.clear() }
        override fun applyInbound(envelope: com.cocode.claudeemailapp.protocol.Envelope, inReplyTo: String?) = null
    }

    @Test
    fun initialState_noCredentials_inboxIdleNoRefresh() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        val vm = buildVm(fetcher = fetcher)
        advanceUntilIdle()
        assertNull(vm.credentials.value)
        assertFalse(vm.inbox.value.loading)
        assertTrue(vm.inbox.value.messages.isEmpty())
        coVerify(exactly = 0) { fetcher.fetchRecent(any(), any()) }
    }

    @Test
    fun initialState_withCredentials_refreshesInbox() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        val msg = fakeMessage("<abc@x>", "hi")
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(msg)

        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()

        assertEquals(listOf(msg), vm.inbox.value.messages)
        coVerify { fetcher.fetchRecent(any(), 50) }
    }

    @Test
    fun probeAndSave_success_persistsAndTriggersRefresh() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()
        val probe = mockk<MailProbe>()
        coEvery { probe.probe(any()) } returns ProbeResult.Success
        val store = FakeCredentialsStore()

        val vm = buildVm(store = store, fetcher = fetcher, probe = probe)
        vm.probeAndSave(creds())
        advanceUntilIdle()

        assertEquals(ProbeResult.Success, vm.probe.value.result)
        assertFalse(vm.probe.value.running)
        assertEquals(creds(), store.load())
        assertEquals(creds(), vm.credentials.value)
        coVerify { fetcher.fetchRecent(any(), 50) }
    }

    @Test
    fun probeAndSave_failure_doesNotPersist() = runTest(dispatcher) {
        val probe = mockk<MailProbe>()
        val failure = ProbeResult.Failure(ProbeResult.Stage.IMAP, "nope")
        coEvery { probe.probe(any()) } returns failure
        val store = FakeCredentialsStore()

        val vm = buildVm(store = store, probe = probe)
        vm.probeAndSave(creds())
        advanceUntilIdle()

        assertEquals(failure, vm.probe.value.result)
        assertNull(store.load())
        assertNull(vm.credentials.value)
    }

    @Test
    fun clearProbeResult_resetsState() = runTest(dispatcher) {
        val probe = mockk<MailProbe>()
        coEvery { probe.probe(any()) } returns ProbeResult.Failure(ProbeResult.Stage.SMTP, "x")
        val vm = buildVm(probe = probe)
        vm.probeAndSave(creds())
        advanceUntilIdle()
        vm.clearProbeResult()
        assertNull(vm.probe.value.result)
        assertFalse(vm.probe.value.running)
    }

    @Test
    fun refreshInbox_success_populatesMessages() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        val msg = fakeMessage("<x@y>", "subject")
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(msg)
        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()
        vm.refreshInbox()
        advanceUntilIdle()
        assertEquals(listOf(msg), vm.inbox.value.messages)
        assertNull(vm.inbox.value.error)
    }

    @Test
    fun refreshInbox_mailException_storesMessage() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } throws MailException("imap down")
        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()
        assertEquals("imap down", vm.inbox.value.error)
        assertFalse(vm.inbox.value.loading)
    }

    @Test
    fun refreshInbox_unknownThrowable_storesMessage() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } throws IllegalStateException("other")
        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()
        assertEquals("other", vm.inbox.value.error)
    }

    @Test
    fun refreshInbox_withoutCredentials_isNoop() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        val vm = buildVm(fetcher = fetcher)
        vm.refreshInbox()
        advanceUntilIdle()
        coVerify(exactly = 0) { fetcher.fetchRecent(any(), any()) }
    }

    @Test
    fun sendMessage_success_refreshesInboxAndTracksId() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()
        val captured = slot<OutgoingMessage>()
        coEvery { sender.send(any(), capture(captured)) } returns SendResult("<mid@x>", Date())

        val vm = buildVm(initialCreds = creds(), sender = sender, fetcher = fetcher)
        advanceUntilIdle()
        vm.sendMessage(to = "to@x", subject = "s", body = "b", inReplyTo = "<r@x>", references = listOf("<root@x>"))
        advanceUntilIdle()

        assertEquals("<mid@x>", vm.send.value.justSentMessageId)
        assertEquals("to@x", captured.captured.to)
        assertEquals("s", captured.captured.subject)
        assertEquals("b", captured.captured.body)
        assertEquals("<r@x>", captured.captured.inReplyTo)
        assertEquals(listOf("<root@x>"), captured.captured.references)
    }

    @Test
    fun sendMessage_mailException_setsErrorMessage() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        coEvery { sender.send(any(), any()) } throws MailException("smtp fail")
        val vm = buildVm(initialCreds = creds(), sender = sender)
        advanceUntilIdle()
        vm.sendMessage("to@x", "s", "b")
        advanceUntilIdle()
        assertEquals("smtp fail", vm.send.value.lastError)
        assertFalse(vm.send.value.sending)
    }

    @Test
    fun sendMessage_unknownThrowable_setsErrorMessage() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        coEvery { sender.send(any(), any()) } throws RuntimeException("other")
        val vm = buildVm(initialCreds = creds(), sender = sender)
        advanceUntilIdle()
        vm.sendMessage("to@x", "s", "b")
        advanceUntilIdle()
        assertEquals("other", vm.send.value.lastError)
    }

    @Test
    fun sendMessage_withoutCredentials_isNoop() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        val vm = buildVm(sender = sender)
        vm.sendMessage("to@x", "s", "b")
        advanceUntilIdle()
        coVerify(exactly = 0) { sender.send(any(), any()) }
    }

    @Test
    fun clearSendResult_resetsState() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        coEvery { sender.send(any(), any()) } returns SendResult("id", Date())
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()
        val vm = buildVm(initialCreds = creds(), sender = sender, fetcher = fetcher)
        advanceUntilIdle()
        vm.sendMessage("to@x", "s", "b")
        advanceUntilIdle()
        vm.clearSendResult()
        assertNull(vm.send.value.justSentMessageId)
        assertNull(vm.send.value.lastError)
        assertFalse(vm.send.value.sending)
    }

    @Test
    fun dispatchSteering_status_sendsStatusEnvelope() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        val captured = slot<OutgoingMessage>()
        coEvery { sender.send(any(), capture(captured)) } returns SendResult("<reply@x>", Date())
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()

        val vm = buildVm(initialCreds = creds(), sender = sender, fetcher = fetcher)
        advanceUntilIdle()
        val pending = PendingCommand(
            messageId = "<m1@x>", sentAt = 0L, to = "svc@x", subject = "s",
            kind = "command", bodyPreview = "", taskId = 42L, project = "proj-x"
        )
        vm.dispatchSteering(pending, SteeringIntent.Status)
        advanceUntilIdle()

        val body = captured.captured.body
        assertTrue("expected status kind, got: $body", body.contains("\"kind\":\"status\""))
        assertTrue("expected task_id 42, got: $body", body.contains("\"task_id\":42"))
        assertEquals("Re: s", captured.captured.subject)
        assertEquals("<m1@x>", captured.captured.inReplyTo)
    }

    @Test
    fun dispatchSteering_reply_doesNotRequireProject() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        val captured = slot<OutgoingMessage>()
        coEvery { sender.send(any(), capture(captured)) } returns SendResult("<reply@x>", Date())
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()

        val vm = buildVm(initialCreds = creds(), sender = sender, fetcher = fetcher)
        advanceUntilIdle()
        val pending = PendingCommand(
            messageId = "<m1@x>", sentAt = 0L, to = "svc@x", subject = "s",
            kind = "command", bodyPreview = "", taskId = 7L, project = null, askId = "9"
        )
        vm.dispatchSteering(pending, SteeringIntent.Reply(askId = "9", body = "yes"))
        advanceUntilIdle()

        val body = captured.captured.body
        assertTrue("expected reply kind, got: $body", body.contains("\"kind\":\"reply\""))
        assertTrue("expected body echoed, got: $body", body.contains("\"body\":\"yes\""))
    }

    @Test
    fun sendCommand_storesProjectOnPending() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        coEvery { sender.send(any(), any()) } returns SendResult("<mid@x>", Date())
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()
        val vm = buildVm(initialCreds = creds(), sender = sender, fetcher = fetcher)
        advanceUntilIdle()
        vm.sendCommand(to = "svc@x", project = "proj-x", body = "hello")
        advanceUntilIdle()
        assertEquals("proj-x", vm.pending.value.single().project)
    }

    @Test
    fun signOut_clearsCredentialsAndInbox() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(fakeMessage("<a@b>", "t"))
        val store = FakeCredentialsStore(creds())
        val vm = buildVm(store = store, fetcher = fetcher)
        advanceUntilIdle()
        assertTrue(vm.inbox.value.messages.isNotEmpty())
        vm.signOut()
        assertNull(vm.credentials.value)
        assertTrue(vm.inbox.value.messages.isEmpty())
        assertNull(store.load())
    }

    @Test
    fun signOut_clearsArchivesIntervalAndRecents() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()
        val state = FakeConversationStateStore(initialSyncMs = 30_000L)
        state.saveArchivedIds(setOf("c-1"))
        state.pushRecentProject("alpha")
        val vm = buildVm(store = FakeCredentialsStore(creds()), fetcher = fetcher, conversationState = state)
        advanceUntilIdle()

        vm.signOut()

        assertEquals(emptySet<String>(), vm.archived.value)
        assertEquals(
            com.cocode.claudeemailapp.data.ConversationStateStore.DEFAULT_SYNC_INTERVAL_MS,
            vm.syncIntervalMs.value
        )
        assertEquals(emptyList<String>(), vm.recentProjects.value)
        assertEquals(emptySet<String>(), state.loadArchivedIds())
        assertEquals(emptyList<String>(), state.loadRecentProjects())
    }

    /** One row builder for `data.projects[]` in list_projects acks. Keeps test fixtures tight. */
    private fun projectRow(
        name: String,
        path: String = "/$name",
        runningTaskId: Long? = null,
        queueDepth: Int = 0,
        lastActivityAt: String? = null,
        agentStatus: String? = null,
        taskState: String? = null
    ): kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("name", kotlinx.serialization.json.JsonPrimitive(name))
        put("path", kotlinx.serialization.json.JsonPrimitive(path))
        runningTaskId?.let { put("running_task_id", kotlinx.serialization.json.JsonPrimitive(it)) }
        put("queue_depth", kotlinx.serialization.json.JsonPrimitive(queueDepth))
        lastActivityAt?.let { put("last_activity_at", kotlinx.serialization.json.JsonPrimitive(it)) }
        agentStatus?.let { put("agent_status", kotlinx.serialization.json.JsonPrimitive(it)) }
        taskState?.let { put("task_state", kotlinx.serialization.json.JsonPrimitive(it)) }
    }

    private fun listProjectsAckMessage(
        messageId: String = "<list-ack@x>",
        vararg rows: kotlinx.serialization.json.JsonObject
    ): FetchedMessage {
        val data = kotlinx.serialization.json.buildJsonObject {
            put("projects", kotlinx.serialization.json.buildJsonArray { rows.forEach { add(it) } })
        }
        return fakeMessage(messageId, "Re: list").copy(
            envelope = com.cocode.claudeemailapp.protocol.Envelope(kind = "ack", data = data)
        )
    }

    private fun fakeMessage(id: String, subject: String) = FetchedMessage(
        messageId = id,
        from = "x@y",
        fromName = null,
        to = emptyList(),
        subject = subject,
        body = "",
        sentAt = null,
        receivedAt = null,
        inReplyTo = null,
        references = emptyList(),
        isSeen = false
    )

    @Test
    fun markConversationRead_callsFetcherWithUnreadIdsAndClearsLocalUnreadCount() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        val unreadA = fakeMessage("<a@x>", "Subject")
        val unreadB = fakeMessage("<b@x>", "Re: Subject")
            .copy(inReplyTo = "<a@x>", references = listOf("<a@x>"))
        val seenC = fakeMessage("<c@x>", "Re: Subject")
            .copy(inReplyTo = "<b@x>", references = listOf("<a@x>", "<b@x>"), isSeen = true)
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(unreadA, unreadB, seenC)
        coEvery { fetcher.markSeen(any(), any()) } returns Unit

        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()

        // Sanity: grouped into one conversation, two unread.
        val conv = vm.conversations.value.single()
        assertEquals(2, conv.unreadCount)

        vm.markConversationRead(conv.id)
        advanceUntilIdle()

        val captured = slot<List<String>>()
        coVerify { fetcher.markSeen(any(), capture(captured)) }
        assertEquals(setOf("<a@x>", "<b@x>"), captured.captured.toSet())
        assertEquals(0, vm.conversations.value.single().unreadCount)
    }

    @Test
    fun markConversationRead_alreadyAllSeen_doesNotCallFetcher() = runTest(dispatcher) {
        val fetcher = mockk<MailFetcher>()
        val seen = fakeMessage("<a@x>", "s").copy(isSeen = true)
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(seen)
        coEvery { fetcher.markSeen(any(), any()) } returns Unit

        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()
        vm.markConversationRead(vm.conversations.value.single().id)
        advanceUntilIdle()

        coVerify(exactly = 0) { fetcher.markSeen(any(), any()) }
    }

    @Test
    fun sendCommand_setsPreferLiveAgent_whenProjectAgentIsConnected() = runTest(dispatcher) {
        val ackMsg = listProjectsAckMessage(rows = arrayOf(projectRow("p", agentStatus = "connected")))
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(ackMsg)
        val sender = mockk<MailSender>()
        coEvery { sender.send(any(), any()) } returns com.cocode.claudeemailapp.mail.SendResult("<sent@x>", java.util.Date())

        val vm = buildVm(initialCreds = creds(), sender = sender, fetcher = fetcher)
        advanceUntilIdle()
        vm.sendCommand(to = "svc@ex", project = "/p", body = "go")
        advanceUntilIdle()

        coVerify {
            sender.send(any(), match { msg ->
                msg.body.contains("\"prefer_live_agent\":true")
            })
        }
    }

    @Test
    fun sendCommand_omitsPreferLiveAgent_whenProjectAgentIsAbsent() = runTest(dispatcher) {
        val sender = mockk<MailSender>()
        coEvery { sender.send(any(), any()) } returns com.cocode.claudeemailapp.mail.SendResult("<sent@x>", java.util.Date())

        val vm = buildVm(initialCreds = creds(), sender = sender)
        advanceUntilIdle()
        vm.sendCommand(to = "svc@ex", project = "/never-listed", body = "go")
        advanceUntilIdle()

        coVerify {
            sender.send(any(), match { msg ->
                !msg.body.contains("prefer_live_agent")
            })
        }
    }

    @Test
    fun refreshProjects_sendsListProjectsEnvelopeViaSender() = runTest(dispatcher) {
        val sender = mockk<MailSender>(relaxed = true)
        val vm = buildVm(initialCreds = creds(), sender = sender)
        advanceUntilIdle()

        vm.refreshProjects()
        advanceUntilIdle()

        coVerify {
            sender.send(any(), match { msg ->
                msg.body.contains("\"kind\":\"list_projects\"") &&
                    msg.contentType == com.cocode.claudeemailapp.protocol.ENVELOPE_CONTENT_TYPE
            })
        }
        assertTrue(vm.projects.value.loading || vm.projects.value.lastFetchedAt != null)
    }

    @Test
    fun refreshInbox_sortsConnectedAgentProjectsFirst() = runTest(dispatcher) {
        val ackMsg = listProjectsAckMessage(
            messageId = "<sort-ack@x>",
            rows = arrayOf(
                projectRow("alpha", agentStatus = "absent"),
                projectRow("bravo", agentStatus = "connected"),
                projectRow("zulu", agentStatus = "connected")
            )
        )
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(ackMsg)

        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()

        val names = vm.projects.value.projects.map { it.name }
        assertEquals(listOf("bravo", "zulu", "alpha"), names)
    }

    @Test
    fun refreshInbox_v1Sort_queuedAndRunningOutrankLiveAndIdle() = runTest(dispatcher) {
        val ackMsg = listProjectsAckMessage(
            messageId = "<sort-v1-ack@x>",
            rows = arrayOf(
                projectRow("idle"),
                projectRow("live", agentStatus = "connected"),
                projectRow("queued", queueDepth = 3),
                projectRow("running", runningTaskId = 7L)
            )
        )
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(ackMsg)

        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()

        assertEquals(
            listOf("running", "queued", "live", "idle"),
            vm.projects.value.projects.map { it.name }
        )
    }

    @Test
    fun refreshInbox_v2Sort_taskStatePriorityOverAgentLive() = runTest(dispatcher) {
        val ackMsg = listProjectsAckMessage(
            messageId = "<sort-v2-ack@x>",
            rows = arrayOf(
                projectRow("offline-no-task", agentStatus = "offline"),
                projectRow("live-no-task", agentStatus = "online"),
                projectRow("error-stale", agentStatus = "stale", taskState = "error"),
                projectRow("waiting-online", agentStatus = "online", taskState = "waiting"),
                projectRow("working-offline", agentStatus = "offline", taskState = "working")
            )
        )
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(ackMsg)

        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()

        assertEquals(
            listOf("working-offline", "waiting-online", "error-stale", "live-no-task", "offline-no-task"),
            vm.projects.value.projects.map { it.name }
        )
    }

    @Test
    fun refreshInbox_parsesListProjectsAckIntoProjectsState() = runTest(dispatcher) {
        val ackMsg = listProjectsAckMessage(
            rows = arrayOf(
                projectRow("claude-email", path = "/p/claude-email", runningTaskId = 42L,
                    queueDepth = 2, lastActivityAt = "2026-05-03T09:24:00Z"),
                projectRow("babakcast", path = "/p/babakcast")
            )
        )
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(ackMsg)

        val vm = buildVm(initialCreds = creds(), fetcher = fetcher)
        advanceUntilIdle()

        val state = vm.projects.value
        assertEquals(2, state.projects.size)
        assertEquals("claude-email", state.projects[0].name)
        assertEquals(42L, state.projects[0].runningTaskId)
        assertEquals(2, state.projects[0].queueDepth)
        assertEquals("babakcast", state.projects[1].name)
        assertNull(state.projects[1].runningTaskId)
        assertEquals(0, state.projects[1].queueDepth)
    }

    @Test
    fun notifier_suppressedOnFirstPoll_thenFiresForNewMessages() = runTest(dispatcher) {
        val notifier = mockk<InboxNotifier>(relaxed = true)
        val m1 = fakeMessage("<m1@x>", "first")
        val m2 = fakeMessage("<m2@x>", "second")
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returnsMany listOf(
            listOf(m1),
            listOf(m2, m1),
            listOf(m2, m1)
        )

        val vm = buildVm(initialCreds = creds(), fetcher = fetcher, notifier = notifier)
        advanceUntilIdle()  // first poll (from init) — backlog suppressed
        io.mockk.verify(exactly = 0) { notifier.handle(any()) }

        vm.refreshInbox()
        advanceUntilIdle()
        io.mockk.verify(exactly = 1) { notifier.handle(eq(m2)) }

        vm.refreshInbox()
        advanceUntilIdle()
        io.mockk.verify(exactly = 1) { notifier.handle(eq(m2)) }  // no new calls
    }

    @Test
    fun effectivePoll_foregroundWithUserPref_returnsFifteenSecondFloor() = runTest(dispatcher) {
        val state = FakeConversationStateStore(initialSyncMs = 60_000L)
        val vm = buildVm(conversationState = state)
        advanceUntilIdle()
        assertEquals(AppViewModel.FOREGROUND_POLL_INTERVAL_MS, vm.effectivePollIntervalMs())
    }

    @Test
    fun effectivePoll_backgroundReturnsUserPref() = runTest(dispatcher) {
        val state = FakeConversationStateStore(initialSyncMs = 300_000L)
        val vm = buildVm(conversationState = state)
        advanceUntilIdle()
        vm.setForegroundActive(false)
        assertEquals(300_000L, vm.effectivePollIntervalMs())
    }

    @Test
    fun effectivePoll_manualUserPref_returnsZeroRegardlessOfState() = runTest(dispatcher) {
        val state = FakeConversationStateStore(initialSyncMs = 0L)
        val vm = buildVm(conversationState = state)
        advanceUntilIdle()
        assertEquals(0L, vm.effectivePollIntervalMs())
        vm.setForegroundActive(false)
        assertEquals(0L, vm.effectivePollIntervalMs())
    }

    @Test
    fun setForegroundActive_flipFalseThenTrue_restoresForegroundInterval() = runTest(dispatcher) {
        val state = FakeConversationStateStore(initialSyncMs = 60_000L)
        val vm = buildVm(conversationState = state)
        advanceUntilIdle()
        vm.setForegroundActive(false)
        assertEquals(60_000L, vm.effectivePollIntervalMs())
        vm.setForegroundActive(true)
        assertEquals(AppViewModel.FOREGROUND_POLL_INTERVAL_MS, vm.effectivePollIntervalMs())
    }
}
