package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.Kinds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxNotifierDecisionTest {

    private fun msg(kind: String?): FetchedMessage = FetchedMessage(
        messageId = "<m@x>",
        from = "claude@example.com",
        fromName = null,
        to = listOf("u@example.com"),
        subject = "s",
        body = "b",
        sentAt = null,
        receivedAt = null,
        inReplyTo = null,
        references = emptyList(),
        isSeen = false,
        contentType = "application/json",
        envelope = if (kind == null) null else Envelope(kind = kind)
    )

    @Test
    fun `skip when disabled`() {
        assertFalse(InboxNotifier.shouldPost(msg(Kinds.RESULT), enabled = false, isForeground = false))
    }

    @Test
    fun `skip when foreground`() {
        assertFalse(InboxNotifier.shouldPost(msg(Kinds.RESULT), enabled = true, isForeground = true))
    }

    @Test
    fun `skip ACK kind`() {
        assertFalse(InboxNotifier.shouldPost(msg(Kinds.ACK), enabled = true, isForeground = false))
    }

    @Test
    fun `post on PROGRESS QUESTION RESULT ERROR`() {
        for (k in listOf(Kinds.PROGRESS, Kinds.QUESTION, Kinds.RESULT, Kinds.ERROR)) {
            assertTrue("kind=$k", InboxNotifier.shouldPost(msg(k), enabled = true, isForeground = false))
        }
    }

    @Test
    fun `fail open on null envelope`() {
        assertTrue(InboxNotifier.shouldPost(msg(null), enabled = true, isForeground = false))
    }

    @Test
    fun `fail open on unknown kind`() {
        assertTrue(InboxNotifier.shouldPost(msg("future_kind"), enabled = true, isForeground = false))
    }
}
