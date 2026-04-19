package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.protocol.Envelopes
import com.cocode.claudeemailapp.protocol.Kinds
import com.cocode.claudeemailapp.protocol.envelope
import com.cocode.claudeemailapp.support.TestMailConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test

/**
 * Integration tests against a real IMAP/SMTP server.
 * Skipped when test credentials are absent (local.properties or env vars).
 *
 * Covers the production default factory lambdas in SmtpMailSender, ImapMailFetcher, and MailProbe
 * that construct real Session/Store/Transport instances.
 */
class MailIntegrationTest {

    private var resolved: TestMailConfig.Resolved? = null

    @Before
    fun loadCredentials() {
        resolved = TestMailConfig.load()
        assumeNotNull(resolved)
    }

    @Test
    fun probe_succeedsAgainstRealServer() = runBlocking {
        val probe = MailProbe()
        val result = probe.probe(resolved!!.credentials)
        assertEquals("Probe failure: $result", ProbeResult.Success, result)
    }

    @Test
    fun fetcher_returnsInboxAgainstRealServer() = runBlocking {
        val fetcher = ImapMailFetcher()
        val messages = fetcher.fetchRecent(resolved!!.credentials, count = 5)
        // Messages may be empty on a fresh account; still exercises the connect path.
        assertNotNull(messages)
    }

    @Test
    fun commandScenario_receivesAckFromBackend() = runBlocking {
        val sharedSecret = System.getProperty("SHARED_SECRET").orEmpty()
        org.junit.Assume.assumeTrue("SHARED_SECRET not set", sharedSecret.isNotBlank())

        val sender = SmtpMailSender()
        val fetcher = ImapMailFetcher()
        val creds = resolved!!.credentials
        val recipient = resolved!!.recipient

        val env = Envelopes.command(
            body = "echo hello and exit",
            project = "test-01",
            auth = sharedSecret
        )
        val outgoing = OutgoingMessage.envelope(
            to = recipient,
            subject = "echo hello and exit",
            envelope = env
        )
        val sent = sender.send(creds, outgoing)
        val originalId = sent.messageId
        println("Scenario command sent: to=$recipient messageId=$originalId project=test-01")

        val deadline = System.currentTimeMillis() + 90_000
        var ack: FetchedMessage? = null
        while (System.currentTimeMillis() < deadline && ack == null) {
            Thread.sleep(5_000)
            val msgs = fetcher.fetchRecent(creds, count = 30)
            ack = msgs.firstOrNull { m ->
                m.envelope != null &&
                    (m.inReplyTo?.trim() == originalId ||
                        m.references.any { it.trim() == originalId })
            }
        }
        if (ack == null) {
            val msgs = fetcher.fetchRecent(creds, count = 30)
            println("=== Inbox snapshot (no ack matched) ===")
            msgs.take(10).forEach { m ->
                println("  from=${m.from} subject=${m.subject.take(40)} ct=${m.contentType} inReplyTo=${m.inReplyTo} refs=${m.references.take(1)}")
            }
        }
        assertNotNull("no ack received within 90s (originalId=$originalId)", ack)
        val parsed = ack!!.envelope!!
        println("Scenario ack: taskId=${parsed.taskId} data=${parsed.data} body=${parsed.body}")
        assertEquals("ack", parsed.kind)
        assertNotNull("ack should carry task_id", parsed.taskId)
    }

    @Test
    fun envelope_sendsAndParsesRoundTrip() = runBlocking {
        val sender = SmtpMailSender()
        val fetcher = ImapMailFetcher()
        val creds = resolved!!.credentials
        val identifier = "claude-email-app-envelope-${System.currentTimeMillis()}"
        val env = Envelopes.command(
            body = "roundtrip $identifier",
            project = "/tmp/integration",
            priority = 3,
            auth = null
        )
        sender.send(
            creds,
            OutgoingMessage.envelope(
                to = creds.emailAddress,
                subject = identifier,
                envelope = env
            )
        )
        // IMAP sync is usually near-instant for self-addressed mail, but allow the server a beat.
        Thread.sleep(2000)
        val recent = fetcher.fetchRecent(creds, count = 20)
        val match = recent.firstOrNull { it.subject.contains(identifier) }
        assertNotNull("envelope email did not appear in inbox", match)
        println("Integration envelope: contentType=${match!!.contentType} bodyHead=${match.body.take(80)}")
        val parsed = match.envelope
        assertNotNull("inbound Content-Type sniff missed the JSON body; contentType=${match.contentType}", parsed)
        assertEquals(Kinds.COMMAND, parsed!!.kind)
        assertEquals("roundtrip $identifier", parsed.body)
        assertEquals("/tmp/integration", parsed.project)
        assertEquals(3, parsed.priority)
    }

    @Test
    fun sender_deliversLiveMessage() = runBlocking {
        val sender = SmtpMailSender()
        val stamp = System.currentTimeMillis()
        val identifier = "claude-email-app-integration-$stamp"
        val result = sender.send(
            resolved!!.credentials,
            OutgoingMessage(
                to = resolved!!.recipient,
                subject = "Integration test $identifier",
                body = "This is an automated test email sent by MailIntegrationTest.\n" +
                    "Identifier: $identifier\n" +
                    "Sent at epoch: $stamp\n" +
                    "From: ${resolved!!.credentials.emailAddress}\n" +
                    "To: ${resolved!!.recipient}"
            )
        )
        assertNotNull(result)
        assertTrue("sentAt should be set", result.sentAt.time > 0)
        println("Integration send delivered: identifier=$identifier to=${resolved!!.recipient}")
    }
}
