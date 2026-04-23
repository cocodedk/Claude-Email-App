package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.ConversationScreen
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ConversationActionsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun message(id: String, body: String = "Body $id", from: String = "agent@x") = FetchedMessage(
        messageId = id, from = from, fromName = "Agent",
        to = listOf("me@x"), subject = "Subj", body = body,
        sentAt = Date(), receivedAt = Date(),
        inReplyTo = null, references = emptyList(), isSeen = true
    )

    private fun conv(messages: List<FetchedMessage>) = Conversation(
        id = messages.first().messageId,
        title = "Subj",
        agentDisplay = "Agent",
        agentEmail = "agent@x",
        latestAt = messages.last().sentAt,
        messageCount = messages.size,
        unreadCount = 0,
        lastMessage = messages.last(),
        messages = messages
    )

    @Test
    fun archiveButton_firesOnce() {
        var toggled = 0
        val c = conv(listOf(message("<a>")))
        composeRule.setContent {
            ClaudeEmailAppTheme {
                ConversationScreen(
                    conversation = c,
                    selfEmail = "me@x",
                    isArchived = false,
                    sending = false,
                    sendError = null,
                    onBack = {},
                    onSendReply = {},
                    onArchiveToggle = { toggled++ }
                )
            }
        }
        composeRule.onNodeWithTag("conversation_archive").performClick()
        composeRule.waitForIdle()
        assert(toggled == 1) { "archive should fire once; got $toggled" }
    }

    @Test
    fun archiveButton_inArchivedView_labelsUnarchive() {
        val c = conv(listOf(message("<a>")))
        composeRule.setContent {
            ClaudeEmailAppTheme {
                ConversationScreen(
                    conversation = c,
                    selfEmail = "me@x",
                    isArchived = true,
                    sending = false,
                    sendError = null,
                    onBack = {},
                    onSendReply = {},
                    onArchiveToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("UNARCHIVE").assertIsDisplayed()
    }

    @Test
    fun multiMessageThread_rendersAllAndDistinguishesSelf() {
        val c = conv(listOf(
            message("<a>", from = "me@x", body = "my first"),
            message("<b>", from = "agent@x", body = "agent reply"),
            message("<c>", from = "me@x", body = "my followup")
        ))
        composeRule.setContent {
            ClaudeEmailAppTheme {
                ConversationScreen(
                    conversation = c,
                    selfEmail = "me@x",
                    isArchived = false,
                    sending = false,
                    sendError = null,
                    onBack = {},
                    onSendReply = {},
                    onArchiveToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("my first").assertIsDisplayed()
        composeRule.onNodeWithText("agent reply").assertIsDisplayed()
        composeRule.onNodeWithText("my followup").assertIsDisplayed()
        composeRule.onNodeWithText("You").assertIsDisplayed()
    }
}
