package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.AppViewModel
import com.cocode.claudeemailapp.app.HomeScreen
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.mail.FetchedMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun message(
        id: String,
        subject: String = "s",
        body: String = "b",
        from: String = "agent@b",
        fromName: String? = "Agent"
    ) = FetchedMessage(
        messageId = id,
        from = from,
        fromName = fromName,
        to = listOf("me@c"),
        subject = subject,
        body = body,
        sentAt = Date(),
        receivedAt = Date(),
        inReplyTo = null,
        references = emptyList(),
        isSeen = false
    )

    private fun conv(message: FetchedMessage, title: String = message.subject) = Conversation(
        id = message.messageId,
        title = title,
        agentDisplay = message.fromName ?: message.from,
        agentEmail = message.from,
        latestAt = message.sentAt,
        messageCount = 1,
        unreadCount = if (message.isSeen) 0 else 1,
        lastMessage = message,
        messages = listOf(message)
    )

    @Test
    fun emptyState_showsEmptyCard() {
        composeRule.setContent {
            HomeScreen(
                state = AppViewModel.InboxState(),
                conversations = emptyList(),
                pending = emptyList(),
                onRefresh = {},
                onOpenConversation = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("Inbox is empty").assertIsDisplayed()
        composeRule.onNodeWithTag("home_new_message_button").assertIsDisplayed()
    }

    @Test
    fun loadingState_disablesRefreshButton() {
        composeRule.setContent {
            HomeScreen(
                state = AppViewModel.InboxState(loading = true),
                conversations = emptyList(),
                pending = emptyList(),
                onRefresh = {},
                onOpenConversation = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithTag("home_refresh_button").assertIsNotEnabled()
    }

    @Test
    fun errorState_showsErrorCard() {
        composeRule.setContent {
            HomeScreen(
                state = AppViewModel.InboxState(error = "Bad creds"),
                conversations = emptyList(),
                pending = emptyList(),
                onRefresh = {},
                onOpenConversation = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("Sync failed").assertIsDisplayed()
        composeRule.onNodeWithText("Bad creds").assertIsDisplayed()
    }

    @Test
    fun populatedState_rendersConversations() {
        val convs = listOf(
            conv(message("<1>", subject = "First", fromName = "Alice")),
            conv(message("<2>", subject = "Second", fromName = "Bob"))
        )
        composeRule.setContent {
            HomeScreen(
                state = AppViewModel.InboxState(),
                conversations = convs,
                pending = emptyList(),
                onRefresh = {},
                onOpenConversation = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("First").assertIsDisplayed()
        composeRule.onNodeWithText("Second").assertIsDisplayed()
        composeRule.onNodeWithText("Alice").assertIsDisplayed()
        composeRule.onNodeWithText("Bob").assertIsDisplayed()
    }

    @Test
    fun clickingConversationCard_invokesCallback() {
        val c = conv(message("<1>", subject = "Click me"))
        var opened: Conversation? = null
        composeRule.setContent {
            HomeScreen(
                state = AppViewModel.InboxState(),
                conversations = listOf(c),
                pending = emptyList(),
                onRefresh = {},
                onOpenConversation = { opened = it },
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("Click me").performClick()
        assert(opened == c)
    }

    @Test
    fun heroButtons_callCallbacks() {
        var composed = false
        var refreshed = false
        var settingsOpened = false
        composeRule.setContent {
            HomeScreen(
                state = AppViewModel.InboxState(),
                conversations = emptyList(),
                pending = emptyList(),
                onRefresh = { refreshed = true },
                onOpenConversation = {},
                onCompose = { composed = true },
                onOpenSettings = { settingsOpened = true }
            )
        }
        composeRule.onNodeWithTag("home_new_message_button").performClick()
        composeRule.onNodeWithTag("home_refresh_button").performClick()
        composeRule.onNodeWithTag("home_settings_button").performClick()
        assert(composed)
        assert(refreshed)
        assert(settingsOpened)
    }

    @Test
    fun unknownSender_rendersFallback() {
        val c = conv(message("<1>", subject = "S", from = "", fromName = null))
        composeRule.setContent {
            HomeScreen(
                state = AppViewModel.InboxState(),
                conversations = listOf(c.copy(agentDisplay = "")),
                pending = emptyList(),
                onRefresh = {},
                onOpenConversation = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("(unknown sender)").assertIsDisplayed()
    }
}
