package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.EnvelopeMeta
import com.cocode.claudeemailapp.protocol.Kinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedRepliesTest {

    private fun message(envelope: Envelope?): FetchedMessage = FetchedMessage(
        messageId = "m-${System.nanoTime()}",
        from = "a@example.com",
        fromName = null,
        to = listOf("b@example.com"),
        subject = "s",
        body = "b",
        sentAt = null,
        receivedAt = null,
        inReplyTo = null,
        references = emptyList(),
        isSeen = true,
        envelope = envelope
    )

    private fun question(replies: List<String>?): FetchedMessage =
        message(Envelope(kind = Kinds.QUESTION, meta = EnvelopeMeta(suggestedReplies = replies)))

    @Test fun `null returns empty`() = assertTrue(validSuggestedReplies(null).isEmpty())

    @Test fun `empty list returns empty`() = assertTrue(validSuggestedReplies(emptyList()).isEmpty())

    @Test fun `short non-blank entries pass through`() =
        assertEquals(listOf("yes", "no"), validSuggestedReplies(listOf("yes", "no")))

    @Test fun `blank and whitespace-only entries dropped`() =
        assertEquals(listOf("yes"), validSuggestedReplies(listOf("yes", "", "   ")))

    @Test fun `entries trimmed`() =
        assertEquals(listOf("yes", "no"), validSuggestedReplies(listOf("  yes  ", "no")))

    @Test fun `entries over 30 chars dropped`() {
        val longOne = "a".repeat(31)
        assertEquals(listOf("ok"), validSuggestedReplies(listOf("ok", longOne)))
    }

    @Test fun `exactly 30 chars accepted`() {
        val maxOne = "a".repeat(30)
        assertEquals(listOf(maxOne), validSuggestedReplies(listOf(maxOne)))
    }

    @Test fun `cap to first 4 entries`() =
        assertEquals(
            listOf("a", "b", "c", "d"),
            validSuggestedReplies(listOf("a", "b", "c", "d", "e", "f"))
        )

    @Test fun `dedup preserves first occurrence`() =
        assertEquals(listOf("yes", "no"), validSuggestedReplies(listOf("yes", "no", "yes")))

    // --- pickSuggestedReplies(messages) ---

    @Test fun `picker empty messages returns empty`() =
        assertTrue(pickSuggestedReplies(emptyList()).isEmpty())

    @Test fun `picker last is not a question returns empty`() {
        val msgs = listOf(
            question(listOf("yes", "no")),
            message(Envelope(kind = Kinds.RESULT, meta = EnvelopeMeta(suggestedReplies = listOf("stale"))))
        )
        assertTrue(pickSuggestedReplies(msgs).isEmpty())
    }

    @Test fun `picker last is question with chips returns validated chips`() =
        assertEquals(
            listOf("yes", "no"),
            pickSuggestedReplies(listOf(question(listOf("  yes  ", "no", ""))))
        )

    @Test fun `picker last is question with no chips returns empty`() =
        assertTrue(pickSuggestedReplies(listOf(question(null))).isEmpty())

    @Test fun `picker only considers latest question (earlier chips ignored)`() =
        assertEquals(
            listOf("ok"),
            pickSuggestedReplies(listOf(question(listOf("old1", "old2")), question(listOf("ok"))))
        )

    @Test fun `picker last has null envelope returns empty`() =
        assertTrue(pickSuggestedReplies(listOf(message(envelope = null))).isEmpty())
}
