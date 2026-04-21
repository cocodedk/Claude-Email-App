package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.ConversationScreen
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

    private fun message() = FetchedMessage(
        messageId = "<abc@x>", from = "a@x", fromName = null,
        to = listOf("me@x"), subject = "Subj", body = "Body",
        sentAt = Date(), receivedAt = Date(),
        inReplyTo = null, references = emptyList(), isSeen = false
    )

    @Test
    fun singleTapDelete_doesNotFire_thenSecondTapFires() {
        var deleted = 0
        composeRule.setContent {
            ClaudeEmailAppTheme {
                ConversationScreen(
                    message = message(),
                    sending = false,
                    sendError = null,
                    onBack = {},
                    onSendReply = {},
                    onDeleteMessage = { deleted++ }
                )
            }
        }
        composeRule.onNodeWithTag("conversation_delete").performClick()
        composeRule.waitForIdle()
        assert(deleted == 0) { "single tap must not fire delete; got $deleted" }
        composeRule.onNodeWithTag("conversation_delete").performClick()
        composeRule.waitForIdle()
        assert(deleted == 1) { "second tap should fire delete once; got $deleted" }
    }

    @Test
    fun singleTapArchive_firesImmediately() {
        var archived = 0
        composeRule.setContent {
            ClaudeEmailAppTheme {
                ConversationScreen(
                    message = message(),
                    sending = false,
                    sendError = null,
                    onBack = {},
                    onSendReply = {},
                    onArchiveMessage = { archived++ }
                )
            }
        }
        composeRule.onNodeWithTag("conversation_archive").performClick()
        composeRule.waitForIdle()
        assert(archived == 1) { "archive should fire on single tap; got $archived" }
    }
}
