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

    private fun render(
        active: List<Conversation> = emptyList(),
        waiting: List<Conversation> = emptyList(),
        archived: List<Conversation> = emptyList(),
        state: AppViewModel.InboxState = AppViewModel.InboxState(),
        onOpenConversation: (Conversation) -> Unit = {},
        onArchiveToggle: (Conversation) -> Unit = {},
        onCompose: () -> Unit = {},
        onRefresh: () -> Unit = {},
        onOpenSettings: () -> Unit = {}
    ) {
        composeRule.setContent {
            HomeScreen(
                state = state,
                buckets = AppViewModel.HomeBuckets(active, waiting, archived),
                pending = emptyList(),
                onRefresh = onRefresh,
                onOpenConversation = onOpenConversation,
                onCompose = onCompose,
                onOpenSettings = onOpenSettings,
                onArchiveToggle = onArchiveToggle
            )
        }
    }

    @Test
    fun emptyActive_showsEmptyCopy() {
        render()
        composeRule.onNodeWithText("Nothing active").assertIsDisplayed()
        composeRule.onNodeWithTag("home_new_message_button").assertIsDisplayed()
    }

    @Test
    fun loadingState_disablesRefreshButton() {
        render(state = AppViewModel.InboxState(loading = true))
        composeRule.onNodeWithTag("home_refresh_button").assertIsNotEnabled()
    }

    @Test
    fun errorState_showsErrorCard() {
        render(state = AppViewModel.InboxState(error = "Bad creds"))
        composeRule.onNodeWithText("Sync failed").assertIsDisplayed()
        composeRule.onNodeWithText("Bad creds").assertIsDisplayed()
    }

    @Test
    fun activeBucket_rendersConversations() {
        render(
            active = listOf(
                conv(message("<1>", subject = "First", fromName = "Alice")),
                conv(message("<2>", subject = "Second", fromName = "Bob"))
            )
        )
        composeRule.onNodeWithText("First").assertIsDisplayed()
        composeRule.onNodeWithText("Second").assertIsDisplayed()
        composeRule.onNodeWithText("Alice").assertIsDisplayed()
        composeRule.onNodeWithText("Bob").assertIsDisplayed()
    }

    @Test
    fun clickingConversationCard_invokesCallback() {
        val c = conv(message("<1>", subject = "Click me"))
        var opened: Conversation? = null
        render(active = listOf(c), onOpenConversation = { opened = it })
        composeRule.onNodeWithText("Click me").performClick()
        assert(opened == c)
    }

    @Test
    fun heroButtons_callCallbacks() {
        var composed = false
        var refreshed = false
        var settingsOpened = false
        render(
            onCompose = { composed = true },
            onRefresh = { refreshed = true },
            onOpenSettings = { settingsOpened = true }
        )
        composeRule.onNodeWithTag("home_new_message_button").performClick()
        composeRule.onNodeWithTag("home_refresh_button").performClick()
        composeRule.onNodeWithTag("home_settings_button").performClick()
        assert(composed)
        assert(refreshed)
        assert(settingsOpened)
    }

    @Test
    fun selectingArchivedTab_showsArchivedConversations() {
        val arc = conv(message("<a>", subject = "Old thread"))
        render(archived = listOf(arc))
        composeRule.onNodeWithTag("home_filter_archived").performClick()
        composeRule.onNodeWithText("Old thread").assertIsDisplayed()
    }

    @Test
    fun selectingWaitingTab_showsWaitingConversations() {
        val w = conv(message("<w>", subject = "Needs reply"))
        render(waiting = listOf(w))
        composeRule.onNodeWithTag("home_filter_waiting").performClick()
        composeRule.onNodeWithText("Needs reply").assertIsDisplayed()
    }
}
