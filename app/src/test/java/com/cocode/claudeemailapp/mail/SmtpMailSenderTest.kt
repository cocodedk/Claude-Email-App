package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class SmtpMailSenderTest {

    private fun creds(startTls: Boolean = false) = MailCredentials(
        displayName = "Agent Smith",
        emailAddress = "agent@example.com",
        password = "pw",
        imapHost = "imap.example.com",
        imapPort = 993,
        smtpHost = "smtp.example.com",
        smtpPort = if (startTls) 587 else 465,
        smtpUseStartTls = startTls,
        serviceAddress = "service@example.com",
        sharedSecret = "secret"
    )

    @Test
    fun smtpProperties_implicitTls_setsSslEnable() {
        val props = SmtpMailSender.smtpProperties(creds(startTls = false))
        assertEquals("smtp", props.getProperty("mail.transport.protocol"))
        assertEquals("smtp.example.com", props.getProperty("mail.smtp.host"))
        assertEquals("465", props.getProperty("mail.smtp.port"))
        assertEquals("true", props.getProperty("mail.smtp.auth"))
        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"))
        assertEquals("true", props.getProperty("mail.smtp.ssl.checkserveridentity"))
        assertNull(props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun smtpProperties_startTls_setsStartTlsFlags() {
        val props = SmtpMailSender.smtpProperties(creds(startTls = true))
        assertEquals("587", props.getProperty("mail.smtp.port"))
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"))
        assertEquals("true", props.getProperty("mail.smtp.starttls.required"))
        assertNull(props.getProperty("mail.smtp.ssl.enable"))
    }

    @Test
    fun authenticator_providesPasswordAuthentication() {
        val auth = SmtpMailSender.authenticator(creds())
        val method = auth.javaClass.getDeclaredMethod("getPasswordAuthentication").apply { isAccessible = true }
        val pa = method.invoke(auth) as jakarta.mail.PasswordAuthentication
        assertEquals("agent@example.com", pa.userName)
        assertEquals("pw", pa.password)
    }

    @Test
    fun send_buildsMimeWithHeadersAndInvokesTransport() = runTest {
        var captured: MimeMessage? = null
        val session = Session.getInstance(Properties())
        val sender = SmtpMailSender(
            sessionFactory = { _, _ -> session },
            transportSend = { captured = it }
        )

        val result = sender.send(
            creds(),
            OutgoingMessage(
                to = "service@example.com",
                subject = "AUTH:secret Hello",
                body = "body line",
                inReplyTo = "<orig@example.com>",
                references = listOf("<root@example.com>", "<mid@example.com>")
            )
        )

        val mime = requireNotNull(captured) { "transport was not invoked" }
        assertEquals("AUTH:secret Hello", mime.subject)
        assertEquals("agent@example.com", (mime.from.first() as jakarta.mail.internet.InternetAddress).address)
        assertEquals("Agent Smith", (mime.from.first() as jakarta.mail.internet.InternetAddress).personal)
        val toHeader = mime.getRecipients(jakarta.mail.Message.RecipientType.TO)
        assertEquals("service@example.com", (toHeader.first() as jakarta.mail.internet.InternetAddress).address)
        assertEquals("<orig@example.com>", mime.getHeader("In-Reply-To").first())
        assertEquals("<root@example.com> <mid@example.com>", mime.getHeader("References").first())
        assertNotNull(mime.sentDate)
        assertTrue(mime.content.toString().contains("body line"))
        assertNotNull(result.sentAt)
    }

    @Test
    fun send_emptyReferences_doesNotSetHeader() = runTest {
        var captured: MimeMessage? = null
        val sender = SmtpMailSender(
            sessionFactory = { _, _ -> Session.getInstance(Properties()) },
            transportSend = { captured = it }
        )
        sender.send(creds(), OutgoingMessage(to = "a@b.c", subject = "s", body = "b"))
        assertNull(requireNotNull(captured).getHeader("References"))
        assertNull(captured!!.getHeader("In-Reply-To"))
    }

    @Test
    fun send_blankDisplayName_ommitsPersonal() = runTest {
        var captured: MimeMessage? = null
        val sender = SmtpMailSender(
            sessionFactory = { _, _ -> Session.getInstance(Properties()) },
            transportSend = { captured = it }
        )
        sender.send(creds().copy(displayName = ""), OutgoingMessage("a@b.c", "s", "b"))
        assertNull((captured!!.from.first() as jakarta.mail.internet.InternetAddress).personal)
    }

    @Test
    fun send_transportThrows_wrapsInMailException() {
        val sender = SmtpMailSender(
            sessionFactory = { _, _ -> Session.getInstance(Properties()) },
            transportSend = { error("network down") }
        )
        val ex = assertThrows(MailException::class.java) {
            kotlinx.coroutines.runBlocking {
                sender.send(creds(), OutgoingMessage("a@b.c", "s", "b"))
            }
        }
        assertTrue(ex.message!!.contains("SMTP send failed"))
        assertNotNull(ex.cause)
    }

    @Test
    fun send_whenTransportSetsMessageId_returnsIt() = runTest {
        val sender = SmtpMailSender(
            sessionFactory = { _, _ -> Session.getInstance(Properties()) },
            transportSend = { it.setHeader("Message-ID", "<set-by-transport@x>") }
        )
        val result = sender.send(creds(), OutgoingMessage("a@b.c", "s", "b"))
        assertEquals("<set-by-transport@x>", result.messageId)
    }

    @Test
    fun send_whenMessageIdUnset_returnsEmptyString() = runTest {
        val sender = SmtpMailSender(
            sessionFactory = { _, _ -> Session.getInstance(Properties()) },
            transportSend = { /* no-op */ }
        )
        val result = sender.send(creds(), OutgoingMessage("a@b.c", "s", "b"))
        assertEquals("", result.messageId)
    }

    @Test
    fun send_injectedSessionUsed() = runTest {
        val session = Session.getInstance(Properties())
        var captured: Session? = null
        val sender = SmtpMailSender(
            sessionFactory = { _, _ -> session.also { captured = it } },
            transportSend = { /* no-op */ }
        )
        sender.send(creds(), OutgoingMessage("a@b.c", "s", "b"))
        assertSame(session, captured)
    }
}
