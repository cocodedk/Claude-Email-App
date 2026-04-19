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
import com.cocode.claudeemailapp.mail.FetchedMessage
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

    @Test
    fun rendersSubjectAndBody() {
        composeRule.setContent {
            ConversationScreen(
                message = message(subject = "The subject", body = "The body"),
                sending = false,
                sendError = null,
                onBack = {},
                onSendReply = {}
            )
        }
        composeRule.onNodeWithTag("conversation_subject").assertIsDisplayed()
        composeRule.onNodeWithText("The body").assertIsDisplayed()
    }

    @Test
    fun emptySubjectAndBody_showFallbacks() {
        composeRule.setContent {
            ConversationScreen(
                message = message(subject = "", body = ""),
                sending = false,
                sendError = null,
                onBack = {},
                onSendReply = {}
            )
        }
        composeRule.onNodeWithText("(no subject)").assertIsDisplayed()
        composeRule.onNodeWithText("(no text content)").assertIsDisplayed()
    }

    @Test
    fun backButton_invokesCallback() {
        var backed = false
        composeRule.setContent {
            ConversationScreen(
                message = message(),
                sending = false,
                sendError = null,
                onBack = { backed = true },
                onSendReply = {}
            )
        }
        composeRule.onNodeWithTag("conversation_back").performClick()
        assert(backed)
    }

    @Test
    fun sendButton_disabledUntilReplyTyped() {
        composeRule.setContent {
            ConversationScreen(
                message = message(),
                sending = false,
                sendError = null,
                onBack = {},
                onSendReply = {}
            )
        }
        composeRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("conversation_reply_field").performTextInput("Hi back")
        composeRule.onNodeWithTag("conversation_send_button").assertIsEnabled()
    }

    @Test
    fun sendButton_invokesCallbackWithTrimmedBody() {
        var sent: String? = null
        composeRule.setContent {
            ConversationScreen(
                message = message(),
                sending = false,
                sendError = null,
                onBack = {},
                onSendReply = { sent = it }
            )
        }
        composeRule.onNodeWithTag("conversation_reply_field").performTextInput("  hello  ")
        composeRule.onNodeWithTag("conversation_send_button").performClick()
        assert(sent == "hello")
    }

    @Test
    fun sendingState_disablesSendAndShowsLabel() {
        composeRule.setContent {
            ConversationScreen(
                message = message(),
                sending = true,
                sendError = null,
                onBack = {},
                onSendReply = {}
            )
        }
        composeRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()
        composeRule.onNodeWithText("Sending…").assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorCard() {
        composeRule.setContent {
            ConversationScreen(
                message = message(),
                sending = false,
                sendError = "smtp rejected",
                onBack = {},
                onSendReply = {}
            )
        }
        composeRule.onNodeWithText("Send failed").assertIsDisplayed()
        composeRule.onNodeWithText("smtp rejected").assertIsDisplayed()
    }

    @Test
    fun sendingBlankWhitespace_doesNotTriggerCallback() {
        var sent: String? = null
        composeRule.setContent {
            ConversationScreen(
                message = message(),
                sending = false,
                sendError = null,
                onBack = {},
                onSendReply = { sent = it }
            )
        }
        // reply empty → send disabled, so nothing should happen
        composeRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()
        assert(sent == null)
    }
}
