package com.cocode.claudeemailapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommandStore
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.mail.ImapMailFetcher
import com.cocode.claudeemailapp.protocol.Kinds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in live demo against the real backend: sends a command, waits for the
 * backend's ack, force-refreshes the inbox so the app's PendingCommandStore
 * flips the matching record to status=queued, then opens that reply in the
 * Conversation screen and parks there for 60 seconds so the tester can
 * watch the steering bar render with real state.
 *
 * Reads the same runner arguments as EndToEndEnvelopeFlowTest (populated
 * from .env / .env.test via app/build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
class SteeringLiveFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val args get() = InstrumentationRegistry.getArguments()
    private fun arg(key: String): String = args.getString(key).orEmpty()

    private fun creds(): MailCredentials? {
        val email = arg("test.mail.email")
        val pw = arg("test.mail.password")
        val imapHost = arg("test.mail.imap.host")
        val imapPort = arg("test.mail.imap.port").toIntOrNull()
        val smtpHost = arg("test.mail.smtp.host")
        val smtpPort = arg("test.mail.smtp.port").toIntOrNull()
        val startTls = arg("test.mail.smtp.starttls").toBooleanStrictOrNull() ?: false
        val recipient = arg("test.mail.recipient").ifBlank { email }
        val secret = arg("SHARED_SECRET")
        if (email.isBlank() || pw.isBlank() || imapHost.isBlank() || imapPort == null ||
            smtpHost.isBlank() || smtpPort == null || secret.isBlank()
        ) return null
        return MailCredentials(
            displayName = "E2E-Steering",
            emailAddress = email,
            password = pw,
            imapHost = imapHost,
            imapPort = imapPort,
            smtpHost = smtpHost,
            smtpPort = smtpPort,
            smtpUseStartTls = startTls,
            serviceAddress = recipient,
            sharedSecret = secret
        )
    }

    @Test
    fun sendCommand_openThreadWhileRunning_showsSteeringBar() {
        val c = creds()
        assumeTrue("test.mail.* and SHARED_SECRET args required", c != null)
        c!!

        resetToSetupIfNeeded()
        fillSetupAndSubmit(c)
        waitUntilTag("home_screen", 30_000)

        val identifier = "[steer-${System.currentTimeMillis()}]"
        // Include a sleep so the running window is long enough to watch.
        val body = "$identifier\necho 'steering-bar demo' && sleep 30 && echo done"

        composeRule.onNodeWithTag("home_new_message_button").performClick()
        waitUntilTag("compose_screen", 5_000)
        scrollComposeTo("compose_to")
        composeRule.onNodeWithTag("compose_to").performTextClearance()
        composeRule.onNodeWithTag("compose_to").performTextInput(c.serviceAddress)
        dismissKeyboard()
        scrollComposeTo("compose_project")
        composeRule.onNodeWithTag("compose_project").performTextInput("test-01")
        dismissKeyboard()
        scrollComposeTo("compose_body")
        composeRule.onNodeWithTag("compose_body").performTextInput(body)
        dismissKeyboard()
        scrollComposeTo("compose_send")
        dismissKeyboard()
        composeRule.onNodeWithTag("compose_send").performClick()

        waitUntilTag("home_screen", 30_000)

        val fetcher = ImapMailFetcher()
        val originalId = awaitOutboundMessageId(identifier)
        android.util.Log.d("SteeringLive", "command sent messageId=$originalId")

        // Block until the backend's ack is in the mailbox.
        val ack = awaitReplyEnvelope(fetcher, c, originalId, Kinds.ACK, timeoutMs = 180_000)
        assertNotNull("no ack envelope within 180s (messageId=$originalId)", ack)
        android.util.Log.d("SteeringLive", "ack received subj='${ack!!.subject}' id=${ack.messageId}")

        // Match the *clickable* MessageCard that contains our identifier.
        // MessageCard merges its descendants, so the subject lives in the
        // card's own merged Text list — use hasText on the card directly,
        // not hasAnyDescendant (which finds nothing when descendants are
        // merged). The raw identifier also appears in the non-clickable
        // PendingSummary card; scoping by testTag excludes it.
        val ackCard: SemanticsMatcher = hasTestTag("message_card") and
            hasText(identifier, substring = true)

        // Try tapping Refresh every 5s until the card appears; give up to 3 minutes.
        val deadline = System.currentTimeMillis() + 180_000L
        while (System.currentTimeMillis() < deadline) {
            if (composeRule.onAllNodes(ackCard).fetchSemanticsNodes().isNotEmpty()) break
            runCatching { composeRule.onNodeWithTag("home_refresh_button").performClick() }
            Thread.sleep(5_000)
        }
        // Diagnostic: if the card still isn't there, dump every message-card's
        // subtree so we can see what the app actually has vs. what we're
        // looking for.
        if (composeRule.onAllNodes(ackCard).fetchSemanticsNodes().isEmpty()) {
            val count = composeRule.onAllNodes(hasTestTag("message_card")).fetchSemanticsNodes().size
            android.util.Log.d("SteeringLive", "ack card NOT found — $count message_card nodes")
            composeRule.onAllNodes(hasTestTag("message_card"))
                .printToLog("SteeringLive", maxDepth = 3)
        }
        // The card may be below the fold — scroll the Home LazyColumn to it.
        composeRule.onNodeWithTag("home_screen").performScrollToNode(ackCard)
        composeRule.onAllNodes(ackCard).onFirst().performClick()

        waitUntilTag("conversation_screen", 10_000)
        // This is the whole point: the steering bar should now render because
        // the matched PendingCommand is queued/running.
        composeRule.onNodeWithTag("steering_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("steering_chip_status").assertIsDisplayed()
        composeRule.onNodeWithTag("steering_chip_cancel").assertIsDisplayed()
        composeRule.onNodeWithTag("steering_chip_more").assertIsDisplayed()
        android.util.Log.d("SteeringLive", "steering bar displayed — holding 60s")

        // Park the conversation open so the tester can watch it on the phone.
        // If the backend finishes during the hold, pending flips to done and
        // the bar hides — also a valid thing to observe.
        Thread.sleep(60_000)
    }

    private fun dismissKeyboard() {
        try {
            Espresso.closeSoftKeyboard()
        } catch (_: Throwable) {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        }
    }

    private fun waitUntilTag(tag: String, timeoutMs: Long) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tagCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun scrollSetupTo(tag: String) {
        composeRule.onNodeWithTag("setup_screen").performScrollToNode(hasTestTag(tag))
    }

    private fun scrollComposeTo(tag: String) {
        composeRule.onNodeWithTag("compose_screen").performScrollToNode(hasTestTag(tag))
    }

    private fun resetToSetupIfNeeded() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            tagCount("home_screen") + tagCount("setup_screen") > 0
        }
        if (tagCount("home_screen") > 0) {
            composeRule.onNodeWithTag("home_settings_button").performClick()
            waitUntilTag("settings_signout", 5_000)
            composeRule.onNodeWithTag("settings_signout").performClick()
            waitUntilTag("setup_screen", 5_000)
        }
        composeRule.onNodeWithTag("setup_screen").assertIsDisplayed()
    }

    private fun fillSetupAndSubmit(c: MailCredentials) {
        scrollSetupTo("setup_email")
        composeRule.onNodeWithTag("setup_email").performTextClearance()
        composeRule.onNodeWithTag("setup_email").performTextInput(c.emailAddress)
        dismissKeyboard()
        scrollSetupTo("setup_password")
        composeRule.onNodeWithTag("setup_password").performTextInput(c.password)
        dismissKeyboard()
        scrollSetupTo("setup_imap_host")
        composeRule.onNodeWithTag("setup_imap_host").performTextClearance()
        composeRule.onNodeWithTag("setup_imap_host").performTextInput(c.imapHost)
        dismissKeyboard()
        scrollSetupTo("setup_imap_port")
        composeRule.onNodeWithTag("setup_imap_port").performTextClearance()
        composeRule.onNodeWithTag("setup_imap_port").performTextInput(c.imapPort.toString())
        dismissKeyboard()
        scrollSetupTo("setup_smtp_host")
        composeRule.onNodeWithTag("setup_smtp_host").performTextClearance()
        composeRule.onNodeWithTag("setup_smtp_host").performTextInput(c.smtpHost)
        dismissKeyboard()
        scrollSetupTo("setup_smtp_port")
        composeRule.onNodeWithTag("setup_smtp_port").performTextClearance()
        composeRule.onNodeWithTag("setup_smtp_port").performTextInput(c.smtpPort.toString())
        dismissKeyboard()
        scrollSetupTo("setup_service_address")
        composeRule.onNodeWithTag("setup_service_address").performTextClearance()
        composeRule.onNodeWithTag("setup_service_address").performTextInput(c.serviceAddress)
        dismissKeyboard()
        scrollSetupTo("setup_shared_secret")
        composeRule.onNodeWithTag("setup_shared_secret").performTextClearance()
        composeRule.onNodeWithTag("setup_shared_secret").performTextInput(c.sharedSecret)
        dismissKeyboard()
        scrollSetupTo("setup_submit")
        dismissKeyboard()
        composeRule.onNodeWithTag("setup_submit").performClick()
    }

    private fun awaitOutboundMessageId(identifier: String): String {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PendingCommandStore(ctx)
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val hit = store.all().firstOrNull { p ->
                p.subject.contains(identifier) && p.messageId.isNotBlank()
            }
            if (hit != null) return hit.messageId
            Thread.sleep(500)
        }
        error("PendingCommandStore never recorded a send for '$identifier' within 30s")
    }

    private fun awaitReplyEnvelope(
        fetcher: ImapMailFetcher,
        c: MailCredentials,
        originalId: String,
        expectedKind: String,
        timeoutMs: Long
    ): FetchedMessage? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val fetched = runBlocking { fetcher.fetchRecent(c, count = 200) }
            val hit = fetched.firstOrNull { m ->
                val env = m.envelope ?: return@firstOrNull false
                env.kind == expectedKind &&
                    (m.inReplyTo?.trim() == originalId ||
                        m.references.any { it.trim() == originalId })
            }
            if (hit != null) return hit
            Thread.sleep(2_000)
        }
        return null
    }
}
