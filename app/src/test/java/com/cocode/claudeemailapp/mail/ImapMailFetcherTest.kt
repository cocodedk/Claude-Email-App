package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.mail.BodyPart
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.Properties

class ImapMailFetcherTest {

    private fun creds() = MailCredentials(
        displayName = "n",
        emailAddress = "me@ex.com",
        password = "p",
        imapHost = "imap.ex.com",
        imapPort = 993,
        smtpHost = "smtp.ex.com",
        smtpPort = 465,
        smtpUseStartTls = false,
        serviceAddress = "",
        sharedSecret = ""
    )

    @Test
    fun imapProperties_setsProtocolAndHost() {
        val p = ImapMailFetcher.imapProperties(creds())
        assertEquals("imaps", p.getProperty("mail.store.protocol"))
        assertEquals("imap.ex.com", p.getProperty("mail.imaps.host"))
        assertEquals("993", p.getProperty("mail.imaps.port"))
        assertEquals("true", p.getProperty("mail.imaps.ssl.enable"))
    }

    @Test
    fun stripHtml_removesTagsAndCollapsesWhitespace() {
        val r = ImapMailFetcher.run { stripHtml("<p>hi   <b>there</b>\n\n<br/>friend</p>") }
        assertEquals("hi there friend", r)
    }

    @Test
    fun extractPlainText_textPlain_returnsContentString() {
        val part = mockk<Part>(relaxed = true)
        every { part.isMimeType("text/plain") } returns true
        every { part.content } returns "hello plain"
        val r = ImapMailFetcher.run { extractPlainText(part) }
        assertEquals("hello plain", r)
    }

    @Test
    fun extractPlainText_textHtml_stripsTags() {
        val part = mockk<Part>(relaxed = true)
        every { part.isMimeType("text/plain") } returns false
        every { part.isMimeType("multipart/*") } returns false
        every { part.isMimeType("text/html") } returns true
        every { part.content } returns "<p>hi</p>"
        val r = ImapMailFetcher.run { extractPlainText(part) }
        assertEquals("hi", r)
    }

    @Test
    fun extractPlainText_multipartPrefersPlain() {
        val plain = mockk<BodyPart>(relaxed = true)
        every { plain.isMimeType("text/plain") } returns true
        every { plain.isMimeType("multipart/*") } returns false
        every { plain.isMimeType("text/html") } returns false
        every { plain.content } returns "plain-body"

        val html = mockk<BodyPart>(relaxed = true)
        every { html.isMimeType("text/plain") } returns false
        every { html.isMimeType("text/html") } returns true
        every { html.content } returns "<b>html</b>"

        val multipart = mockk<Multipart>(relaxed = true)
        every { multipart.count } returns 2
        every { multipart.getBodyPart(0) } returns html
        every { multipart.getBodyPart(1) } returns plain

        val outer = mockk<Part>(relaxed = true)
        every { outer.isMimeType("text/plain") } returns false
        every { outer.isMimeType("multipart/*") } returns true
        every { outer.content } returns multipart

        val r = ImapMailFetcher.run { extractPlainText(outer) }
        assertEquals("plain-body", r)
    }

    @Test
    fun extractPlainText_multipartFallsBackToHtml() {
        val html = mockk<BodyPart>(relaxed = true)
        every { html.isMimeType("text/plain") } returns false
        every { html.isMimeType("text/html") } returns true
        every { html.isMimeType("multipart/*") } returns false
        every { html.content } returns "<i>only</i>"

        val multipart = mockk<Multipart>(relaxed = true)
        every { multipart.count } returns 1
        every { multipart.getBodyPart(0) } returns html

        val outer = mockk<Part>(relaxed = true)
        every { outer.isMimeType("text/plain") } returns false
        every { outer.isMimeType("multipart/*") } returns true
        every { outer.content } returns multipart

        val r = ImapMailFetcher.run { extractPlainText(outer) }
        assertEquals("only", r)
    }

    @Test
    fun extractPlainText_multipartNoTextParts_returnsEmpty() {
        val other = mockk<BodyPart>(relaxed = true)
        every { other.isMimeType(any()) } returns false
        every { other.content } returns Any()

        val multipart = mockk<Multipart>(relaxed = true)
        every { multipart.count } returns 1
        every { multipart.getBodyPart(0) } returns other

        val outer = mockk<Part>(relaxed = true)
        every { outer.isMimeType("text/plain") } returns false
        every { outer.isMimeType("multipart/*") } returns true
        every { outer.content } returns multipart

        val r = ImapMailFetcher.run { extractPlainText(outer) }
        assertEquals("", r)
    }

    @Test
    fun extractPlainText_multipartContentNotMultipartReturnsEmpty() {
        val outer = mockk<Part>(relaxed = true)
        every { outer.isMimeType("text/plain") } returns false
        every { outer.isMimeType("multipart/*") } returns true
        every { outer.content } returns "not-a-multipart"
        val r = ImapMailFetcher.run { extractPlainText(outer) }
        assertEquals("", r)
    }

    @Test
    fun extractPlainText_mimeBodyPartNoDisposition_returnsString() {
        val part = mockk<MimeBodyPart>(relaxed = true)
        every { part.isMimeType(any()) } returns false
        every { part.disposition } returns null
        every { part.content } returns "raw body"
        val r = ImapMailFetcher.run { extractPlainText(part) }
        assertEquals("raw body", r)
    }

    @Test
    fun extractPlainText_exceptionInsideReturnsEmpty() {
        val part = mockk<Part>(relaxed = true)
        every { part.isMimeType(any()) } throws RuntimeException("boom")
        val r = ImapMailFetcher.run { extractPlainText(part) }
        assertEquals("", r)
    }

    @Test
    fun toFetched_mapsHeadersAndBody() {
        val msg = mockk<Message>(relaxed = true)
        every { msg.from } returns arrayOf(InternetAddress("sender@x.com", "Sender Name"))
        every { msg.getRecipients(Message.RecipientType.TO) } returns arrayOf(InternetAddress("me@ex.com"))
        every { msg.getHeader("In-Reply-To") } returns arrayOf("<parent@x>")
        every { msg.getHeader("References") } returns arrayOf("<root@x> <mid@x>")
        every { msg.getHeader("Message-ID") } returns arrayOf("<self@x>")
        every { msg.subject } returns "Subject"
        every { msg.sentDate } returns Date(1_700_000_000_000L)
        every { msg.receivedDate } returns Date(1_700_000_001_000L)
        every { msg.flags } returns Flags(Flags.Flag.SEEN)
        every { msg.isMimeType("text/plain") } returns true
        every { msg.content } returns "body"

        val fetched = ImapMailFetcher.run { msg.toFetched() }
        assertEquals("<self@x>", fetched.messageId)
        assertEquals("sender@x.com", fetched.from)
        assertEquals("Sender Name", fetched.fromName)
        assertEquals(listOf("me@ex.com"), fetched.to)
        assertEquals("Subject", fetched.subject)
        assertEquals("body", fetched.body)
        assertEquals("<parent@x>", fetched.inReplyTo)
        assertEquals(listOf("<root@x>", "<mid@x>"), fetched.references)
        assertTrue(fetched.isSeen)
    }

    @Test
    fun toFetched_handlesNullsGracefully() {
        val msg = mockk<Message>(relaxed = true)
        every { msg.from } returns null
        every { msg.getRecipients(Message.RecipientType.TO) } returns null
        every { msg.getHeader("In-Reply-To") } returns null
        every { msg.getHeader("References") } returns null
        every { msg.getHeader("Message-ID") } returns null
        every { msg.subject } returns null
        every { msg.sentDate } returns null
        every { msg.receivedDate } returns null
        every { msg.flags } returns Flags()
        every { msg.isMimeType(any()) } returns false
        every { msg.content } returns ""

        val fetched = ImapMailFetcher.run { msg.toFetched() }
        assertEquals("", fetched.messageId)
        assertEquals("", fetched.from)
        assertNull(fetched.fromName)
        assertTrue(fetched.to.isEmpty())
        assertEquals("", fetched.subject)
        assertNull(fetched.inReplyTo)
        assertTrue(fetched.references.isEmpty())
        assertFalse(fetched.isSeen)
        assertNull(fetched.sentAt)
    }

    @Test
    fun fetchRecent_returnsEmptyWhenMailboxEmpty() = runTest {
        val inbox = mockk<Folder>(relaxed = true)
        every { inbox.messageCount } returns 0
        every { inbox.isOpen } returns true

        val store = mockk<Store>(relaxed = true)
        every { store.getFolder("INBOX") } returns inbox
        every { store.isConnected } returns true

        val fetcher = ImapMailFetcher(
            sessionFactory = { Session.getInstance(Properties()) },
            storeConnector = { _, _ -> store }
        )
        val result = fetcher.fetchRecent(creds(), count = 50)
        assertTrue(result.isEmpty())
        verify { inbox.open(Folder.READ_ONLY) }
        verify { inbox.close(false) }
        verify { store.close() }
    }

    @Test
    fun fetchRecent_returnsMessagesReversedNewestFirst() = runTest {
        val messages = (1..3).map { i ->
            mockk<Message>(relaxed = true).also { m ->
                every { m.from } returns arrayOf(InternetAddress("from$i@x"))
                every { m.getRecipients(Message.RecipientType.TO) } returns arrayOf(InternetAddress("me@x"))
                every { m.getHeader(any()) } returns null
                every { m.subject } returns "s$i"
                every { m.flags } returns Flags()
                every { m.isMimeType("text/plain") } returns true
                every { m.content } returns "b$i"
                every { m.sentDate } returns Date(1_700_000_000_000L + i)
                every { m.receivedDate } returns Date(1_700_000_000_000L + i)
            }
        }

        val inbox = mockk<Folder>(relaxed = true)
        every { inbox.messageCount } returns 3
        every { inbox.getMessages(1, 3) } returns messages.toTypedArray()
        every { inbox.isOpen } returns true

        val store = mockk<Store>(relaxed = true)
        every { store.getFolder("INBOX") } returns inbox
        every { store.isConnected } returns true

        val fetcher = ImapMailFetcher(
            sessionFactory = { Session.getInstance(Properties()) },
            storeConnector = { _, _ -> store }
        )
        val result = fetcher.fetchRecent(creds(), count = 50)
        assertEquals(listOf("s3", "s2", "s1"), result.map { it.subject })
    }

    @Test
    fun fetchRecent_wrapsConnectFailure() {
        val fetcher = ImapMailFetcher(
            sessionFactory = { Session.getInstance(Properties()) },
            storeConnector = { _, _ -> error("auth no") }
        )
        val ex = assertThrows(MailException::class.java) {
            kotlinx.coroutines.runBlocking { fetcher.fetchRecent(creds()) }
        }
        assertTrue(ex.message!!.contains("IMAP connect failed"))
    }

    @Test
    fun fetchRecent_wrapsFetchFailure() {
        val inbox = mockk<Folder>(relaxed = true)
        every { inbox.messageCount } throws RuntimeException("io fail")
        every { inbox.isOpen } returns true

        val store = mockk<Store>(relaxed = true)
        every { store.getFolder("INBOX") } returns inbox
        every { store.isConnected } returns true

        val fetcher = ImapMailFetcher(
            sessionFactory = { Session.getInstance(Properties()) },
            storeConnector = { _, _ -> store }
        )
        val ex = assertThrows(MailException::class.java) {
            kotlinx.coroutines.runBlocking { fetcher.fetchRecent(creds()) }
        }
        assertTrue(ex.message!!.contains("IMAP fetch failed"))
    }

    @Test
    fun fetchRecent_slicesToCount() = runTest {
        val totalCount = 100
        val slice = 5
        val inbox = mockk<Folder>(relaxed = true)
        every { inbox.messageCount } returns totalCount
        every { inbox.getMessages(96, 100) } returns arrayOf()
        every { inbox.isOpen } returns true

        val store = mockk<Store>(relaxed = true)
        every { store.getFolder("INBOX") } returns inbox
        every { store.isConnected } returns true

        val fetcher = ImapMailFetcher(
            sessionFactory = { Session.getInstance(Properties()) },
            storeConnector = { _, _ -> store }
        )
        fetcher.fetchRecent(creds(), count = slice)
        verify { inbox.getMessages(96, 100) }
    }

    @Test
    fun fetchRecent_closeFailuresAreSwallowed() = runTest {
        val inbox = mockk<Folder>(relaxed = true)
        every { inbox.messageCount } returns 0
        every { inbox.isOpen } returns true

        val store = mockk<Store>(relaxed = true)
        every { store.getFolder("INBOX") } returns inbox
        every { store.isConnected } returns true
        every { store.close() } throws RuntimeException("close fail")

        val fetcher = ImapMailFetcher(
            sessionFactory = { Session.getInstance(Properties()) },
            storeConnector = { _, _ -> store }
        )
        val result = fetcher.fetchRecent(creds())
        assertTrue(result.isEmpty())
    }
}
