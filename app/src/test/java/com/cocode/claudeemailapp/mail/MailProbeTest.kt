package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.Transport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class MailProbeTest {

    private fun creds(startTls: Boolean = false) = MailCredentials(
        displayName = "n",
        emailAddress = "me@ex.com",
        password = "p",
        imapHost = "imap.ex.com",
        imapPort = 993,
        smtpHost = "smtp.ex.com",
        smtpPort = if (startTls) 587 else 465,
        smtpUseStartTls = startTls,
        serviceAddress = "",
        sharedSecret = ""
    )

    @Test
    fun imapProperties_includesTimeouts() {
        val props = MailProbe.imapProperties(creds())
        assertEquals("imaps", props.getProperty("mail.store.protocol"))
        assertNotNull(props.getProperty("mail.imaps.connectiontimeout"))
        assertNotNull(props.getProperty("mail.imaps.timeout"))
    }

    @Test
    fun smtpProperties_implicitTlsSetsSsl() {
        val props = MailProbe.smtpProperties(creds(startTls = false))
        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"))
        assertNull(props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun smtpProperties_startTlsSetsFlags() {
        val props = MailProbe.smtpProperties(creds(startTls = true))
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"))
        assertEquals("true", props.getProperty("mail.smtp.starttls.required"))
        assertNull(props.getProperty("mail.smtp.ssl.enable"))
    }

    private fun okImapStore(): Store {
        val inbox = mockk<Folder>(relaxed = true)
        every { inbox.isOpen } returns true
        val store = mockk<Store>(relaxed = true)
        every { store.getFolder("INBOX") } returns inbox
        every { store.isConnected } returns true
        return store
    }

    private fun okTransport(): Transport {
        val t = mockk<Transport>(relaxed = true)
        every { t.isConnected } returns true
        return t
    }

    @Test
    fun probe_happyPath_returnsSuccess() = runTest {
        val probe = MailProbe(
            imapSessionFactory = { Session.getInstance(Properties()) },
            imapStoreConnector = { _, _ -> okImapStore() },
            smtpSessionFactory = { Session.getInstance(Properties()) },
            smtpTransportConnector = { _, _ -> okTransport() }
        )
        val result = probe.probe(creds())
        assertEquals(ProbeResult.Success, result)
    }

    @Test
    fun probe_imapFailureReturnsImapFailureStage() = runTest {
        val probe = MailProbe(
            imapSessionFactory = { Session.getInstance(Properties()) },
            imapStoreConnector = { _, _ -> error("login denied") },
            smtpSessionFactory = { Session.getInstance(Properties()) },
            smtpTransportConnector = { _, _ -> okTransport() }
        )
        val result = probe.probe(creds())
        assertTrue(result is ProbeResult.Failure)
        val failure = result as ProbeResult.Failure
        assertEquals(ProbeResult.Stage.IMAP, failure.stage)
        assertEquals("login denied", failure.message)
        assertNotNull(failure.cause)
    }

    @Test
    fun probe_imapSucceedsSmtpFailsReturnsSmtpStage() = runTest {
        val probe = MailProbe(
            imapSessionFactory = { Session.getInstance(Properties()) },
            imapStoreConnector = { _, _ -> okImapStore() },
            smtpSessionFactory = { Session.getInstance(Properties()) },
            smtpTransportConnector = { _, _ -> error("auth fail") }
        )
        val result = probe.probe(creds())
        val failure = result as ProbeResult.Failure
        assertEquals(ProbeResult.Stage.SMTP, failure.stage)
        assertEquals("auth fail", failure.message)
    }

    @Test
    fun probe_exceptionWithNullMessageReturnsEmptyString() = runTest {
        val probe = MailProbe(
            imapSessionFactory = { Session.getInstance(Properties()) },
            imapStoreConnector = { _, _ -> throw RuntimeException() },
            smtpSessionFactory = { Session.getInstance(Properties()) },
            smtpTransportConnector = { _, _ -> okTransport() }
        )
        val failure = probe.probe(creds()) as ProbeResult.Failure
        assertEquals("", failure.message)
    }

    @Test
    fun probe_closesResourcesEvenOnFailure() = runTest {
        val store = okImapStore()
        val probe = MailProbe(
            imapSessionFactory = { Session.getInstance(Properties()) },
            imapStoreConnector = { _, _ -> store },
            smtpSessionFactory = { Session.getInstance(Properties()) },
            smtpTransportConnector = { _, _ -> error("smtp fail") }
        )
        probe.probe(creds())
        verify { store.close() }
    }

    @Test
    fun probe_swallowsCloseFailures() = runTest {
        val store = okImapStore()
        every { store.close() } throws RuntimeException("close oops")
        val transport = okTransport()
        every { transport.close() } throws RuntimeException("close oops 2")
        val probe = MailProbe(
            imapSessionFactory = { Session.getInstance(Properties()) },
            imapStoreConnector = { _, _ -> store },
            smtpSessionFactory = { Session.getInstance(Properties()) },
            smtpTransportConnector = { _, _ -> transport }
        )
        val result = probe.probe(creds())
        assertEquals(ProbeResult.Success, result)
    }

    @Test
    fun probe_injectedSessionsUsed() = runTest {
        val imapSession = Session.getInstance(Properties())
        val smtpSession = Session.getInstance(Properties())
        var imapCaptured: Session? = null
        var smtpCaptured: Session? = null
        val probe = MailProbe(
            imapSessionFactory = { imapSession.also { imapCaptured = it } },
            imapStoreConnector = { _, _ -> okImapStore() },
            smtpSessionFactory = { smtpSession.also { smtpCaptured = it } },
            smtpTransportConnector = { _, _ -> okTransport() }
        )
        probe.probe(creds())
        assertSame(imapSession, imapCaptured)
        assertSame(smtpSession, smtpCaptured)
    }
}
