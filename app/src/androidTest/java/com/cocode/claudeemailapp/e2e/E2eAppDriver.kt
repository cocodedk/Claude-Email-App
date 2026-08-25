package com.cocode.claudeemailapp.e2e

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.Espresso

/**
 * Drives the app's production UI — no view models are reached into, no state is
 * set directly. Every step here is a tap or a keystroke a person could perform,
 * which is what makes the round trip it triggers a real one.
 */
class E2eAppDriver(private val rule: ComposeTestRule) {

    fun skipOnboardingIfShown() {
        awaitAny(listOf("onboarding_screen", "setup_screen", "home_screen"), 60_000)
        if (count("onboarding_screen") > 0) {
            rule.onNodeWithTag("onboarding_skip").performClick()
            awaitTag("setup_screen", 20_000)
        }
    }

    /** Submits the prefilled form. The probe behind it is a real IMAP+SMTP login. */
    fun signInThroughSetupScreen(timeoutMs: Long) {
        awaitTag("setup_screen", 30_000)
        scrollTo("setup_screen", "setup_submit")
        dismissKeyboard()
        rule.onNodeWithTag("setup_submit").performClick()
        awaitTag("home_screen", timeoutMs)
    }

    fun sendCommand(to: String, project: String, body: String, timeoutMs: Long) {
        rule.onNodeWithTag("home_new_message_button").performClick()
        awaitTag("compose_screen", 20_000)

        typeInto("compose_to", to, clearFirst = true)
        typeInto("compose_project", project, clearFirst = true)
        typeInto("compose_body", body, clearFirst = false)

        scrollTo("compose_screen", "compose_send")
        dismissKeyboard()
        rule.onNodeWithTag("compose_send").performClick()
        // Compose only returns to Home once the real SMTP send has completed.
        awaitTag("home_screen", timeoutMs)
    }

    fun openFirstConversation(timeoutMs: Long) {
        awaitTag("conversation_card", timeoutMs)
        rule.onAllNodesWithTag("conversation_card")[0].performClick()
        awaitTag("conversation_screen", 20_000)
    }

    fun awaitRenderedText(text: String, timeoutMs: Long) {
        try {
            rule.waitUntil(timeoutMillis = timeoutMs) {
                runCatching {
                    rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
        } catch (timeout: ComposeTimeoutException) {
            // A bare "condition not satisfied" says nothing about what the app
            // did render, which is the only thing that explains the failure.
            throw AssertionError(
                "'$text' never rendered within ${timeoutMs}ms. On screen: " +
                    renderedTexts().joinToString(" | "),
                timeout
            )
        }
    }

    /** Every string currently displayed, for failure messages. */
    fun renderedTexts(): List<String> = runCatching {
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString("") { it.text }?.take(200)
            }
    }.getOrDefault(listOf("<no compose hierarchy>"))

    /**
     * Waits for a rendered node containing [fragment] and returns the whole
     * string that node is actually displaying — read back out of the semantics
     * tree, so it is what a person would see, not what the test supplied.
     */
    fun awaitRenderedTextContaining(fragment: String, timeoutMs: Long): String {
        awaitRenderedText(fragment, timeoutMs)
        val node = rule.onAllNodesWithText(fragment, substring = true).fetchSemanticsNodes().first()
        return node.config.getOrNull(SemanticsProperties.Text)
            .orEmpty().joinToString("") { it.text }
    }

    private fun typeInto(tag: String, text: String, clearFirst: Boolean) {
        scrollTo(if (tag.startsWith("compose")) "compose_screen" else "setup_screen", tag)
        if (clearFirst) rule.onNodeWithTag(tag).performTextClearance()
        rule.onNodeWithTag(tag).performTextInput(text)
        dismissKeyboard()
    }

    /** Both forms are lazy scrollers; a field below the fold has no node at all. */
    private fun scrollTo(container: String, tag: String) {
        runCatching { rule.onNodeWithTag(container).performScrollToNode(hasTestTag(tag)) }
    }

    private fun awaitTag(tag: String, timeoutMs: Long) =
        rule.waitUntil(timeoutMillis = timeoutMs) { count(tag) > 0 }

    private fun awaitAny(tags: List<String>, timeoutMs: Long) =
        rule.waitUntil(timeoutMillis = timeoutMs) { tags.sumOf { count(it) } > 0 }

    /**
     * Counts matching nodes, treating "no compose hierarchy yet" as zero, so the
     * surrounding wait can do its job while the activity is still attaching. A
     * screen that genuinely never appears still fails on the wait's timeout.
     */
    private fun count(tag: String): Int =
        runCatching { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size }.getOrDefault(0)

    private fun dismissKeyboard() {
        runCatching { Espresso.closeSoftKeyboard() }
    }
}
