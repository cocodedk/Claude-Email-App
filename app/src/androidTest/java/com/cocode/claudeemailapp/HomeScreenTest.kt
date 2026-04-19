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
import com.cocode.claudeemailapp.mail.FetchedMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun message(id: String, subject: String = "s", body: String = "b", fromName: String? = null) =
        FetchedMessage(
            messageId = id,
            from = "a@b",
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

    @Test
    fun emptyState_showsEmptyCard() {
        composeRule.setContent {
            HomeScreen(
                pending = emptyList(),
                state = AppViewModel.InboxState(),
                onRefresh = {},
                onOpenMessage = {},
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
                pending = emptyList(),
                state = AppViewModel.InboxState(loading = true),
                onRefresh = {},
                onOpenMessage = {},
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
                pending = emptyList(),
                state = AppViewModel.InboxState(error = "Bad creds"),
                onRefresh = {},
                onOpenMessage = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("Sync failed").assertIsDisplayed()
        composeRule.onNodeWithText("Bad creds").assertIsDisplayed()
    }

    @Test
    fun populatedState_rendersMessages() {
        val messages = listOf(message("<1>", subject = "First", fromName = "Alice"), message("<2>", subject = "Second"))
        composeRule.setContent {
            HomeScreen(
                pending = emptyList(),
                state = AppViewModel.InboxState(messages = messages),
                onRefresh = {},
                onOpenMessage = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("First").assertIsDisplayed()
        composeRule.onNodeWithText("Second").assertIsDisplayed()
        composeRule.onNodeWithText("Alice").assertIsDisplayed()
    }

    @Test
    fun clickingMessageCard_invokesOpenMessage() {
        val msg = message("<1>", subject = "Click me")
        var opened: FetchedMessage? = null
        composeRule.setContent {
            HomeScreen(
                pending = emptyList(),
                state = AppViewModel.InboxState(messages = listOf(msg)),
                onRefresh = {},
                onOpenMessage = { opened = it },
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("Click me").performClick()
        assert(opened == msg)
    }

    @Test
    fun heroButtons_callCallbacks() {
        var composed = false
        var refreshed = false
        var settingsOpened = false
        composeRule.setContent {
            HomeScreen(
                pending = emptyList(),
                state = AppViewModel.InboxState(messages = emptyList()),
                onRefresh = { refreshed = true },
                onOpenMessage = {},
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
    fun populatedState_unknownSender_rendersFallback() {
        val msg = message("<1>", subject = "S").copy(from = "", fromName = null)
        composeRule.setContent {
            HomeScreen(
                pending = emptyList(),
                state = AppViewModel.InboxState(messages = listOf(msg)),
                onRefresh = {},
                onOpenMessage = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("(unknown sender)").assertIsDisplayed()
    }

    @Test
    fun populatedState_emptySubject_showsFallback() {
        val msg = message("<1>", subject = "", body = "").copy(from = "a@b", fromName = null)
        composeRule.setContent {
            HomeScreen(
                pending = emptyList(),
                state = AppViewModel.InboxState(messages = listOf(msg)),
                onRefresh = {},
                onOpenMessage = {},
                onCompose = {},
                onOpenSettings = {}
            )
        }
        composeRule.onNodeWithText("(no subject)").assertIsDisplayed()
    }
}
