package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.app.steering.SteeringIntent
import com.cocode.claudeemailapp.data.CredentialsStore
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.mail.MailFetcher
import com.cocode.claudeemailapp.mail.MailProbe
import com.cocode.claudeemailapp.mail.MailSender
import com.cocode.claudeemailapp.mail.OutgoingMessage
import com.cocode.claudeemailapp.mail.SendResult
import io.mockk.coEvery
import io.mockk.every
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
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Both reply paths must share one "Re: " guard: the steering path
 * (AppViewModel.dispatchSteering) and the conversation reply path
 * (AppRoot.replySubject). A subject that already carries a reply prefix,
 * in any case, must never be prefixed again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplySubjectTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- the shared helper ------------------------------------------------

    @Test
    fun helper_prefixesBareSubject() =
        assertEquals("Re: status update", replySubjectFor("status update"))

    @Test
    fun helper_leavesLowercasePrefixAlone() =
        assertEquals("re: status update", replySubjectFor("re: status update"))

    @Test
    fun helper_leavesUpperCasePrefixAlone() =
        assertEquals("RE: status update", replySubjectFor("RE: status update"))

    @Test
    fun helper_leavesMixedCasePrefixAlone() =
        assertEquals("Re: status update", replySubjectFor("Re: status update"))

    @Test
    fun helper_leavesSpacedPrefixAlone() =
        assertEquals("Re : status update", replySubjectFor("Re : status update"))

    @Test
    fun helper_leavesDoublePrefixAlone() =
        assertEquals("Re: Re: status update", replySubjectFor("Re: Re: status update"))

    // --- conversation reply path (AppRoot) --------------------------------

    @Test
    fun conversationReply_doesNotDoublePrefix() =
        assertEquals("Re: status update", replySubject("Re: status update", null))

    @Test
    fun conversationReply_doesNotDoublePrefixSpacedVariant() =
        assertEquals("Re : status update", replySubject("Re : status update", null))

    @Test
    fun conversationReply_prefixesBareSubject() =
        assertEquals("Re: status update", replySubject("status update", null))

    @Test
    fun conversationReply_keepsAuthPrefixAheadOfReplyPrefix() =
        assertEquals("AUTH:s3cret Re: status update", replySubject("Re: status update", "s3cret"))

    // --- steering path (AppViewModel.dispatchSteering) ---------------------

    @Test
    fun steering_doesNotDoublePrefix() = runTest(dispatcher) {
        assertEquals("RE: status update", steeringSubjectFor("RE: status update"))
    }

    @Test
    fun steering_prefixesBareSubject() = runTest(dispatcher) {
        assertEquals("Re: status update", steeringSubjectFor("status update"))
    }

    /** Drives the real dispatchSteering path and returns the subject that reached the sender. */
    private suspend fun TestScope.steeringSubjectFor(pendingSubject: String): String {
        val sender = mockk<MailSender>()
        val captured = slot<OutgoingMessage>()
        coEvery { sender.send(any(), capture(captured)) } returns SendResult("<reply@x>", Date())
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()
        val store = mockk<CredentialsStore>()
        every { store.hasCredentials() } returns true
        every { store.load() } returns credentials

        val vm = AppViewModel(
            mockk(relaxed = true), store, sender, fetcher, mockk<MailProbe>(relaxed = true),
            AppViewModelTest.FakePendingCommandStore(), mockk(relaxed = true), null
        )
        advanceUntilIdle()
        vm.dispatchSteering(pendingCommand(pendingSubject), SteeringIntent.Status)
        advanceUntilIdle()
        return captured.captured.subject
    }

    private fun pendingCommand(subject: String) = PendingCommand(
        messageId = "<m1@x>", sentAt = 0L, to = "svc@example.com", subject = subject,
        kind = "command", bodyPreview = "", taskId = 42L, project = "proj-x"
    )

    private val credentials = MailCredentials(
        displayName = "me",
        emailAddress = "me@example.com",
        password = "p",
        imapHost = "imap.example.com",
        imapPort = 993,
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUseStartTls = false,
        serviceAddress = "svc@example.com",
        sharedSecret = ""
    )
}
