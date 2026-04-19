package com.cocode.claudeemailapp.app

import android.app.Application
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
        pending: PendingCommandStore = FakePendingCommandStore()
    ): AppViewModel {
        val app = mockk<Application>(relaxed = true)
        return AppViewModel(app, store, sender, fetcher, probe, pending)
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
}
