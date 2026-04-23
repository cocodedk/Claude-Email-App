package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.ComposeMessageScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme

@RunWith(AndroidJUnit4::class)
class ComposeMessageScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag("compose_screen").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun prefillsDefaultToAndProject() {
        composeRule.setContent { ClaudeEmailAppTheme {
            ComposeMessageScreen(
                defaultTo = "svc@ex",
                defaultProject = "/path/to/project",
                sending = false,
                sendError = null,
                onCancel = {},
                onSend = { _, _, _ -> }
            )
        } }
        composeRule.onNodeWithText("svc@ex").assertIsDisplayed()
        composeRule.onNodeWithText("/path/to/project").assertIsDisplayed()
    }

    @Test
    fun sendButton_disabledUntilAllRequiredFieldsFilled() {
        composeRule.setContent { ClaudeEmailAppTheme {
            ComposeMessageScreen(
                defaultTo = "",
                defaultProject = "",
                sending = false,
                sendError = null,
                onCancel = {},
                onSend = { _, _, _ -> }
            )
        } }
        scrollTo("compose_send")
        composeRule.onNodeWithTag("compose_send").assertIsNotEnabled()
    }

    @Test
    fun sendButton_enablesWhenToProjectBodyFilled() {
        composeRule.setContent { ClaudeEmailAppTheme {
            ComposeMessageScreen(
                defaultTo = "",
                defaultProject = "",
                sending = false,
                sendError = null,
                onCancel = {},
                onSend = { _, _, _ -> }
            )
        } }
        scrollTo("compose_to")
        composeRule.onNodeWithTag("compose_to").performTextInput("svc@ex")
        scrollTo("compose_project")
        composeRule.onNodeWithTag("compose_project").performTextInput("/p")
        scrollTo("compose_body")
        composeRule.onNodeWithTag("compose_body").performTextInput("hello")
        scrollTo("compose_send")
        composeRule.onNodeWithTag("compose_send").assertIsEnabled()
    }

    @Test
    fun send_invokesCallbackWithTrimmedFields() {
        var captured: Triple<String, String, String>? = null
        composeRule.setContent { ClaudeEmailAppTheme {
            ComposeMessageScreen(
                defaultTo = "svc@ex",
                defaultProject = "/p",
                sending = false,
                sendError = null,
                onCancel = {},
                onSend = { to, project, body -> captured = Triple(to, project, body) }
            )
        } }
        scrollTo("compose_body")
        composeRule.onNodeWithTag("compose_body").performTextInput("command")
        Espresso.closeSoftKeyboard()
        scrollTo("compose_send")
        composeRule.onNodeWithTag("compose_send").performClick()
        assert(captured == Triple("svc@ex", "/p", "command")) { "got $captured" }
    }

    @Test
    fun cancelButton_callsOnCancel() {
        var cancelled = false
        composeRule.setContent { ClaudeEmailAppTheme {
            ComposeMessageScreen(
                defaultTo = "svc@ex",
                defaultProject = "/p",
                sending = false,
                sendError = null,
                onCancel = { cancelled = true },
                onSend = { _, _, _ -> }
            )
        } }
        composeRule.onNodeWithTag("compose_cancel").performClick()
        assert(cancelled)
    }

    @Test
    fun errorState_showsErrorCard() {
        composeRule.setContent { ClaudeEmailAppTheme {
            ComposeMessageScreen(
                defaultTo = "svc@ex",
                defaultProject = "/p",
                sending = false,
                sendError = "smtp down",
                onCancel = {},
                onSend = { _, _, _ -> }
            )
        } }
        composeRule.onNodeWithText("Send failed").assertIsDisplayed()
        composeRule.onNodeWithText("smtp down").assertIsDisplayed()
    }

    @Test
    fun sendingState_showsSendingLabel() {
        composeRule.setContent { ClaudeEmailAppTheme {
            ComposeMessageScreen(
                defaultTo = "svc@ex",
                defaultProject = "/p",
                sending = true,
                sendError = null,
                onCancel = {},
                onSend = { _, _, _ -> }
            )
        } }
        scrollTo("compose_send")
        composeRule.onNodeWithText("Sending…").assertIsDisplayed()
    }
}
