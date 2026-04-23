package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class ConversationScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun message(subject: String = "Hello", body: String = "Body") = FetchedMessage(
        messageId = "<abc@x>",
        from = "agent@x",
        fromName = "Agent",
        to = listOf("me@x"),
        subject = subject,
        body = body,
        sentAt = Date(),
        receivedAt = Date(),
        inReplyTo = null,
        references = emptyList(),
        isSeen = false
    )

    private fun conv(m: FetchedMessage) = Conversation(
        id = m.messageId,
        title = m.subject.ifBlank { "(no subject)" },
        agentDisplay = m.fromName ?: m.from,
        agentEmail = m.from,
        latestAt = m.sentAt,
        messageCount = 1,
        unreadCount = if (m.isSeen) 0 else 1,
        lastMessage = m,
        messages = listOf(m)
    )

    private fun render(
        m: FetchedMessage = message(),
        sending: Boolean = false,
        sendError: String? = null,
        onBack: () -> Unit = {},
        onSendReply: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            ClaudeEmailAppTheme {
                ConversationScreen(
                    conversation = conv(m),
                    selfEmail = "me@x",
                    isArchived = false,
                    sending = sending,
                    sendError = sendError,
                    onBack = onBack,
                    onSendReply = onSendReply,
                    onArchiveToggle = {}
                )
            }
        }
    }

    @Test
    fun rendersSubjectAndBody() {
        render(m = message(subject = "The subject", body = "The body"))
        composeRule.onNodeWithTag("conversation_subject").assertIsDisplayed()
        composeRule.onNodeWithText("The body").assertIsDisplayed()
    }

    @Test
    fun emptySubjectAndBody_showFallbacks() {
        render(m = message(subject = "", body = ""))
        composeRule.onNodeWithText("(no subject)").assertIsDisplayed()
        composeRule.onNodeWithText("(no text content)").assertIsDisplayed()
    }

    @Test
    fun backButton_invokesCallback() {
        var backed = false
        render(onBack = { backed = true })
        composeRule.onNodeWithTag("conversation_back").performClick()
        assert(backed)
    }

    @Test
    fun sendButton_disabledUntilReplyTyped() {
        render()
        composeRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("conversation_reply_field").performTextInput("Hi back")
        composeRule.onNodeWithTag("conversation_send_button").assertIsEnabled()
    }

    @Test
    fun sendButton_invokesCallbackWithTrimmedBody() {
        var sent: String? = null
        render(onSendReply = { sent = it })
        composeRule.onNodeWithTag("conversation_reply_field").performTextInput("  hello  ")
        composeRule.onNodeWithTag("conversation_send_button").performClick()
        assert(sent == "hello")
    }

    @Test
    fun sendingState_disablesSendAndShowsLabel() {
        render(sending = true)
        composeRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()
        composeRule.onNodeWithText("Sending…").assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorCard() {
        render(sendError = "smtp rejected")
        composeRule.onNodeWithText("Send failed").assertIsDisplayed()
        composeRule.onNodeWithText("smtp rejected").assertIsDisplayed()
    }

    @Test
    fun sendingBlankWhitespace_doesNotTriggerCallback() {
        var sent: String? = null
        render(onSendReply = { sent = it })
        composeRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()
        assert(sent == null)
    }
}
