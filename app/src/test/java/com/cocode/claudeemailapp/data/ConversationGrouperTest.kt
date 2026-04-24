package com.cocode.claudeemailapp.data

import com.cocode.claudeemailapp.mail.FetchedMessage
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationGrouperTest {

    private val self = "me@example.com"

    private fun msg(
        id: String,
        subject: String = "hi",
        from: String = "agent@x",
        fromName: String? = "Agent",
        inReplyTo: String? = null,
        references: List<String> = emptyList(),
        sentAt: Long = 1_000L,
        seen: Boolean = true
    ) = FetchedMessage(
        messageId = id,
        from = from,
        fromName = fromName,
        to = listOf(self),
        subject = subject,
        body = "",
        sentAt = Date(sentAt),
        receivedAt = Date(sentAt),
        inReplyTo = inReplyTo,
        references = references,
        isSeen = seen
    )

    @Test
    fun emptyInput_returnsEmpty() {
        assertEquals(emptyList<Conversation>(), ConversationGrouper.group(emptyList(), self))
    }

    @Test
    fun singleMessage_oneConversation_titleFromSubject() {
        val out = ConversationGrouper.group(listOf(msg("<a>", subject = "Refactor auth")), self)
        assertEquals(1, out.size)
        assertEquals("Refactor auth", out[0].title)
        assertEquals(1, out[0].messageCount)
    }

    @Test
    fun replyPrefix_isStrippedRecursively() {
        assertEquals("hello", ConversationGrouper.stripReplyPrefix("Re: Re: Fwd: hello"))
        assertEquals("hello", ConversationGrouper.stripReplyPrefix("RE: hello"))
        assertEquals("hello", ConversationGrouper.stripReplyPrefix("  Re : hello"))
        assertEquals("(no subject)", ConversationGrouper.stripReplyPrefix("   "))
    }

    @Test
    fun inReplyTo_groupsTwoMessages() {
        val a = msg("<a>", subject = "X", sentAt = 1_000)
        val b = msg("<b>", subject = "Re: X", inReplyTo = "<a>", sentAt = 2_000)
        val out = ConversationGrouper.group(listOf(a, b), self)
        assertEquals(1, out.size)
        assertEquals(2, out[0].messageCount)
        assertEquals("<b>", out[0].lastMessage.messageId)
    }

    @Test
    fun referencesChain_groupsThreeMessages() {
        val a = msg("<a>", sentAt = 1_000)
        val b = msg("<b>", references = listOf("<a>"), sentAt = 2_000)
        val c = msg("<c>", references = listOf("<a>", "<b>"), sentAt = 3_000)
        val out = ConversationGrouper.group(listOf(c, a, b), self)
        assertEquals(1, out.size)
        assertEquals(3, out[0].messageCount)
        assertEquals("<c>", out[0].lastMessage.messageId)
    }

    @Test
    fun unrelatedMessages_returnTwoConversationsSortedByLatestDesc() {
        val a = msg("<a>", subject = "Old", sentAt = 1_000)
        val b = msg("<b>", subject = "New", sentAt = 5_000)
        val out = ConversationGrouper.group(listOf(a, b), self)
        assertEquals(2, out.size)
        assertEquals("New", out[0].title)
        assertEquals("Old", out[1].title)
    }

    @Test
    fun unreadCount_excludesSeen() {
        val a = msg("<a>", seen = false, sentAt = 1_000)
        val b = msg("<b>", inReplyTo = "<a>", seen = true, sentAt = 2_000)
        val c = msg("<c>", inReplyTo = "<b>", seen = false, sentAt = 3_000)
        val out = ConversationGrouper.group(listOf(a, b, c), self)
        assertEquals(2, out[0].unreadCount)
    }

    @Test
    fun agentIdentity_picksFirstNonSelfFrom() {
        val mine = msg("<m>", from = self, fromName = "Me", sentAt = 1_000)
        val theirs = msg("<t>", from = "agent@x", fromName = "Agent X", inReplyTo = "<m>", sentAt = 2_000)
        val out = ConversationGrouper.group(listOf(mine, theirs), self)
        assertEquals("Agent X", out[0].agentDisplay)
        assertEquals("agent@x", out[0].agentEmail)
    }

    @Test
    fun selfEmail_isCaseInsensitive() {
        val mine = msg("<m>", from = "ME@Example.COM", fromName = "Me", sentAt = 1_000)
        val theirs = msg("<t>", from = "agent@x", inReplyTo = "<m>", sentAt = 2_000)
        val out = ConversationGrouper.group(listOf(mine, theirs), self)
        assertEquals("agent@x", out[0].agentEmail)
    }

    @Test
    fun missingParent_referencedButNotInList_stillGroupsChildren() {
        val b = msg("<b>", inReplyTo = "<missing>", sentAt = 1_000)
        val c = msg("<c>", inReplyTo = "<missing>", sentAt = 2_000)
        val out = ConversationGrouper.group(listOf(b, c), self)
        assertEquals(1, out.size)
        assertTrue(out[0].messageCount == 2)
    }
}
