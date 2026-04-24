package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.AppViewModel
import com.cocode.claudeemailapp.app.ClaudeEmailApp
import com.cocode.claudeemailapp.data.CredentialsStore
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.mail.MailFetcher
import com.cocode.claudeemailapp.mail.MailProbe
import com.cocode.claudeemailapp.mail.MailSender
import com.cocode.claudeemailapp.mail.ProbeResult
import com.cocode.claudeemailapp.mail.SendResult
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class AppRootTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeStore(initial: MailCredentials? = null) : CredentialsStore {
        private var current: MailCredentials? = initial
        override fun hasCredentials() = current != null
        override fun load() = current
        override fun save(credentials: MailCredentials) { current = credentials }
        override fun clear() { current = null }
    }

    private fun creds() = MailCredentials(
        displayName = "d",
        emailAddress = "me@ex.com",
        password = "pw",
        imapHost = "imap.ex",
        imapPort = 993,
        smtpHost = "smtp.ex",
        smtpPort = 465,
        smtpUseStartTls = false,
        serviceAddress = "svc@ex",
        sharedSecret = "s"
    )

    private fun message(id: String = "<a@b>", subject: String = "Subject") = FetchedMessage(
        messageId = id,
        from = "sender@x",
        fromName = "Sender",
        to = listOf("me@ex.com"),
        subject = subject,
        body = "body text",
        sentAt = Date(),
        receivedAt = Date(),
        inReplyTo = null,
        references = emptyList(),
        isSeen = false
    )

    private fun buildViewModel(
        store: CredentialsStore = FakeStore(),
        sender: MailSender = mockk(relaxed = true),
        fetcher: MailFetcher = mockk<MailFetcher>().apply {
            coEvery { fetchRecent(any(), any()) } returns emptyList()
        },
        probe: MailProbe = mockk(relaxed = true),
        pending: com.cocode.claudeemailapp.data.PendingCommandStore = FakePending(),
        conversationState: com.cocode.claudeemailapp.data.ConversationStateStore = FakeConvState()
    ): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        return AppViewModel(app, store, sender, fetcher, probe, pending, conversationState)
    }

    private class FakeConvState : com.cocode.claudeemailapp.data.ConversationStateStore {
        private var ids: Set<String> = emptySet()
        private var syncMs: Long = 60_000L
        private var onboardingSeen: Boolean = true
        private var recent: List<String> = emptyList()
        override fun loadArchivedIds(): Set<String> = ids
        override fun saveArchivedIds(ids: Set<String>) { this.ids = ids.toSet() }
        override fun loadSyncIntervalMs(): Long = syncMs
        override fun saveSyncIntervalMs(ms: Long) { syncMs = ms }
        override fun loadHasSeenOnboarding(): Boolean = onboardingSeen
        override fun markOnboardingSeen() { onboardingSeen = true }
        override fun loadRecentProjects(): List<String> = recent
        override fun pushRecentProject(project: String) {
            val t = project.trim()
            if (t.isBlank()) return
            recent = (listOf(t) + recent.filter { it != t }).take(5)
        }
    }

    private class FakePending : com.cocode.claudeemailapp.data.PendingCommandStore {
        private val entries = linkedMapOf<String, com.cocode.claudeemailapp.data.PendingCommand>()
        override fun add(pending: com.cocode.claudeemailapp.data.PendingCommand) { entries[pending.messageId] = pending }
        override fun findByMessageId(messageId: String) = entries[messageId]
        override fun findByTaskId(taskId: Long) = entries.values.firstOrNull { it.taskId == taskId }
        override fun all() = entries.values.toList()
        override fun clear() { entries.clear() }
        override fun applyInbound(envelope: com.cocode.claudeemailapp.protocol.Envelope, inReplyTo: String?) = null
    }

    @Test
    fun noCredentials_rendersSetupScreen() {
        composeRule.setContent {
            ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = buildViewModel()) }
        }
        composeRule.onNodeWithTag("setup_screen").assertIsDisplayed()
    }

    @Test
    fun withCredentials_rendersHomeScreen() {
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(message())
        val vm = buildViewModel(store = FakeStore(creds()), fetcher = fetcher)
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Subject").assertIsDisplayed()
    }

    @Test
    fun probeSuccess_routesToHomeFromSetup() {
        val probe = mockk<MailProbe>()
        coEvery { probe.probe(any()) } returns ProbeResult.Success
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns emptyList()
        val vm = buildViewModel(probe = probe, fetcher = fetcher)
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        vm.probeAndSave(creds())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun home_openMessage_routesToConversation() {
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(message(subject = "Open me"))
        val vm = buildViewModel(store = FakeStore(creds()), fetcher = fetcher)
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open me").performClick()
        composeRule.onNodeWithTag("conversation_screen").assertIsDisplayed()
    }

    @Test
    fun conversation_back_routesHome() {
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(message(subject = "Go"))
        val vm = buildViewModel(store = FakeStore(creds()), fetcher = fetcher)
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Go").performClick()
        composeRule.onNodeWithTag("conversation_back").performClick()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun home_new_message_button_opensCompose() {
        val vm = buildViewModel(store = FakeStore(creds()))
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_new_message_button").performClick()
        composeRule.onNodeWithTag("compose_screen").assertIsDisplayed()
    }

    @Test
    fun home_settings_opensSettings() {
        val vm = buildViewModel(store = FakeStore(creds()))
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_settings_button").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun settings_signOut_returnsToSetup() {
        val vm = buildViewModel(store = FakeStore(creds()))
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_settings_button").performClick()
        composeRule.onNodeWithTag("settings_signout").performClick()
        composeRule.onNodeWithTag("setup_screen").assertIsDisplayed()
    }

    @Test
    fun settings_edit_opensSetupWithPrefill() {
        val vm = buildViewModel(store = FakeStore(creds()))
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_settings_button").performClick()
        composeRule.onNodeWithTag("settings_edit").performClick()
        composeRule.onNodeWithTag("setup_screen").assertIsDisplayed()
        composeRule.onNodeWithText("me@ex.com").assertIsDisplayed()
    }

    @Test
    fun compose_cancel_returnsHome() {
        val vm = buildViewModel(store = FakeStore(creds()))
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_new_message_button").performClick()
        composeRule.onNodeWithTag("compose_cancel").performClick()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun settings_back_returnsHome() {
        val vm = buildViewModel(store = FakeStore(creds()))
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_settings_button").performClick()
        composeRule.onNodeWithTag("settings_back").performClick()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun sendMessage_fromConversation_triggersSender() {
        val sender = mockk<MailSender>()
        coEvery { sender.send(any(), any()) } returns SendResult("<m@id>", Date())
        val fetcher = mockk<MailFetcher>()
        coEvery { fetcher.fetchRecent(any(), any()) } returns listOf(message(subject = "Thread"))
        val vm = buildViewModel(store = FakeStore(creds()), sender = sender, fetcher = fetcher)
        composeRule.setContent { ClaudeEmailAppTheme { ClaudeEmailApp(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Thread").performClick()
        composeRule.onNodeWithTag("conversation_reply_field").performTextInput("hello")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("conversation_send_button").performClick()
        composeRule.waitForIdle()
        // High-UX behaviour: send from a conversation keeps the user on the
        // conversation (snackbar confirms via SnackbarHost) instead of jumping
        // back to Home, so the user can watch for the agent's reply in place.
        composeRule.onNodeWithTag("conversation_screen").assertIsDisplayed()
        io.mockk.coVerify { sender.send(any(), any()) }
    }
}
