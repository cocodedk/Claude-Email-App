package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.SwipeableMessageCard
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SwipeableMessageCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun message() = FetchedMessage(
        messageId = "<abc@x>", from = "sender@x", fromName = "S",
        to = listOf("me@ex.com"), subject = "Hello", body = "Body",
        sentAt = Date(), receivedAt = Date(),
        inReplyTo = null, references = emptyList(), isSeen = false
    )

    @Test
    fun swipeLeft_firesDelete() {
        var deleted = false
        composeRule.setContent {
            ClaudeEmailAppTheme {
                SwipeableMessageCard(
                    message = message(),
                    onOpen = {},
                    onSwipeDelete = { deleted = true },
                    onSwipeArchive = {}
                )
            }
        }
        composeRule.onNodeWithTag("swipeable_message_card").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assert(deleted)
    }

    @Test
    fun swipeRight_firesArchive() {
        var archived = false
        composeRule.setContent {
            ClaudeEmailAppTheme {
                SwipeableMessageCard(
                    message = message(),
                    onOpen = {},
                    onSwipeDelete = {},
                    onSwipeArchive = { archived = true }
                )
            }
        }
        composeRule.onNodeWithTag("swipeable_message_card").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assert(archived)
    }
}
