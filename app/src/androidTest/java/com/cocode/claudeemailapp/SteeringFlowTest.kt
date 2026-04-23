package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.ConversationScreen
import com.cocode.claudeemailapp.app.steering.SteeringIntent
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.mail.FetchedMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme

@RunWith(AndroidJUnit4::class)
class SteeringFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun twoTapsOnCancelFireCancelIntent() {
        val fired = mutableListOf<SteeringIntent>()
        val msg = FetchedMessage(
            messageId = "<m1@x>",
            from = "svc@x",
            fromName = null,
            to = listOf("me@x"),
            subject = "s",
            body = "b",
            sentAt = null,
            receivedAt = null,
            inReplyTo = null,
            references = emptyList(),
            isSeen = false
        )
        val pending = PendingCommand(
            messageId = "<m1@x>",
            sentAt = 0L,
            to = "svc@x",
            subject = "s",
            kind = "command",
            bodyPreview = "",
            taskId = 42L,
            status = PendingStatus.RUNNING,
            project = "p"
        )

        val convo = Conversation(
            id = msg.messageId,
            title = msg.subject,
            agentDisplay = msg.from,
            agentEmail = msg.from,
            latestAt = null,
            messageCount = 1,
            unreadCount = 1,
            lastMessage = msg,
            messages = listOf(msg)
        )
        composeRule.setContent { ClaudeEmailAppTheme {
            ConversationScreen(
                conversation = convo,
                selfEmail = "me@x",
                isArchived = false,
                sending = false,
                sendError = null,
                onBack = {},
                onSendReply = {},
                onArchiveToggle = {},
                pending = pending,
                onSteeringIntent = { fired += it }
            )
        } }

        composeRule.onNodeWithTag("steering_chip_cancel").performClick()
        composeRule.onNodeWithTag("steering_chip_cancel").performClick()

        assertEquals(listOf<SteeringIntent>(SteeringIntent.Cancel), fired)
    }
}
