package com.cocode.claudeemailapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.mail.ImapMailFetcher
import com.cocode.claudeemailapp.protocol.Kinds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full round-trip against the real Claude-Email backend: drives the production UI to
 * enter credentials, compose, and send a `kind=command` envelope, then polls IMAP for
 * the corresponding `kind=ack` and `kind=result` replies. Opt-in — runs only when the
 * test.mail.* and SHARED_SECRET arguments are forwarded via InstrumentationRunnerArguments.
 * Creates a real backend task every run.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndEnvelopeFlowTest {

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
            displayName = "E2E",
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

    @Test
    fun sendCommandFromApp_receivesAckAndResultEnvelopes() {
        val c = creds()
        assumeTrue("test.mail.* and SHARED_SECRET args required", c != null)
        c!!

        // Fresh state: if signed in from a prior run, sign out through the UI so we
        // exercise the Setup → probe → save path end-to-end.
        resetToSetupIfNeeded()

        fillSetupAndSubmit(c)

        // Probe hits real IMAP+SMTP then credentials save → AppRoot auto-navigates to Home.
        waitUntilTag("home_screen", 30_000)

        val identifier = "e2e-ui-${System.currentTimeMillis()}"
        val body = "$identifier\necho hello from instrumented UI and exit"

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

        // Snackbar + auto-nav back to Home signals SMTP send completed.
        waitUntilTag("home_screen", 30_000)

        val fetcher = ImapMailFetcher()
        val originalId = awaitOriginalMessageId(fetcher, c, identifier)
        println("E2E command sent: identifier=$identifier messageId=$originalId")

        val ack = awaitReplyEnvelope(fetcher, c, originalId, Kinds.ACK, timeoutMs = 180_000)
        assertNotNull("no ack envelope within 180s (messageId=$originalId)", ack)
        val ackEnv = ack!!.envelope!!
        assertEquals(Kinds.ACK, ackEnv.kind)
        assertNotNull("ack should carry task_id", ackEnv.taskId)
        println("E2E ack: taskId=${ackEnv.taskId} body=${ackEnv.body}")

        val result = awaitReplyEnvelope(fetcher, c, originalId, Kinds.RESULT, timeoutMs = 120_000)
        assertNotNull("no result envelope within 120s (messageId=$originalId)", result)
        val resultEnv = result!!.envelope!!
        assertEquals(Kinds.RESULT, resultEnv.kind)
        assertEquals("result should reference same task", ackEnv.taskId, resultEnv.taskId)
        println("E2E result: taskId=${resultEnv.taskId} data=${resultEnv.data} body=${resultEnv.body}")
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
        composeRule.onNodeWithTag("setup_shared_secret").performTextInput(c.sharedSecret)
        dismissKeyboard()
        scrollSetupTo("setup_submit")
        dismissKeyboard()
        composeRule.onNodeWithTag("setup_submit").performClick()
    }

    private fun awaitOriginalMessageId(
        fetcher: ImapMailFetcher,
        c: MailCredentials,
        identifier: String
    ): String {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            runBlocking {
                fetcher.fetchRecent(c, count = 30)
                    .firstOrNull { it.subject.contains(identifier) }
                    ?.messageId
                    ?.takeIf(String::isNotBlank)
            }?.let { return it }
            Thread.sleep(3_000)
        }
        error("sent message with subject containing '$identifier' never appeared in INBOX within 60s")
    }

    private fun awaitReplyEnvelope(
        fetcher: ImapMailFetcher,
        c: MailCredentials,
        originalId: String,
        expectedKind: String,
        timeoutMs: Long
    ): FetchedMessage? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var pollCount = 0
        while (System.currentTimeMillis() < deadline) {
            pollCount++
            val fetched: List<FetchedMessage> = runBlocking { fetcher.fetchRecent(c, count = 40) }
            val elapsedSec = (timeoutMs - (deadline - System.currentTimeMillis())) / 1000
            val line = "poll#$pollCount kind=$expectedKind t=${elapsedSec}s fetched=${fetched.size} orig=$originalId"
            println(line)
            android.util.Log.d("E2ETest", line)
            fetched.take(5).forEach { m ->
                val detail = "  subj='${m.subject.take(50)}' inReplyTo=${m.inReplyTo} envKind=${m.envelope?.kind}"
                println(detail)
                android.util.Log.d("E2ETest", detail)
            }
            val hit = fetched.firstOrNull { m ->
                val env = m.envelope ?: return@firstOrNull false
                env.kind == expectedKind &&
                    (m.inReplyTo?.trim() == originalId ||
                        m.references.any { it.trim() == originalId })
            }
            if (hit != null) {
                val matchLine = "poll#$pollCount MATCH $expectedKind msgId=${hit.messageId}"
                println(matchLine)
                android.util.Log.d("E2ETest", matchLine)
                return hit
            }
            Thread.sleep(2_000)
        }
        return null
    }
}
