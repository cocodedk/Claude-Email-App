package com.cocode.claudeemailapp.e2e

import android.content.Intent
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.MainActivity
import com.cocode.claudeemailapp.app.PrefillCredentials
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The whole system, end to end, with nothing in the mail path replaced.
 *
 * The app runs on an emulator. It reaches a real GreenMail on the host through
 * 10.0.2.2 — the emulator's NAT alias for host loopback — over real TLS, using
 * its own production [com.cocode.claudeemailapp.mail.SmtpMailSender] and
 * [com.cocode.claudeemailapp.mail.ImapMailFetcher] (Angus Mail). On the other
 * side of the same server sits the real `main.py` poller, which authenticates
 * the envelope, enqueues it, and runs a real CLI process. There is no fake
 * transport, no fake MIME, no injected fetcher: the only thing configured for
 * the test is the *trust anchor* for the harness's throwaway certificate, and
 * [E2eTrust.assertUntrustedBySystem] proves that certificate is genuinely
 * untrusted until it is installed.
 *
 * Why this fails if the implementation is reverted:
 *
 * * break the SMTP sender, the MIME/envelope serialiser, or the shared-secret
 *   `meta.auth` field, and the backend never authenticates or executes — the
 *   host-side ledger check in `scripts/e2e/harness.py` finds zero executions
 *   and the run is red;
 * * break the IMAP fetcher, the envelope parser or the conversation rendering,
 *   and no `Task #N done` card reaches the screen, so the final wait times out;
 * * remove the harness trust anchor, or make the app pin the system trust store
 *   instead of resolving `SSLSocketFactory.getDefault()`, and the TLS handshake
 *   fails: nothing is delivered and every oracle goes red. Note what this does
 *   *not* cover — an app-side trust-all regression would leave
 *   [E2eTrust.assertUntrustedBySystem] green, because that control builds its
 *   own `SSLContext` and never touches the app's mail stack. See its KDoc.
 *
 * The on-screen assertion is anchored on the task number the backend's queue
 * mints. The test cannot supply it — it reads it off the ack card and then
 * requires the result card to agree. The stronger oracle, the CLI's own stdout
 * token, is deliberately never given to the device: `scripts/e2e/oracle.py`
 * checks it against the mail actually delivered, so no device-side assertion
 * could fabricate it. See `docs/e2e-app-emulator.md`.
 */
@RunWith(AndroidJUnit4::class)
class RealMailE2ETest {

    private val cfg = E2eConfig.require()

    private val scenarioRule = ActivityScenarioRule<MainActivity>(prefillIntent(cfg))

    private val composeRule = AndroidComposeTestRule(scenarioRule) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity) { "MainActivity never reached the resumed state" }
    }

    /** The trust anchor and a signed-out device must both precede the launch. */
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(E2eEnvironmentRule(cfg))
        .around(composeRule)

    private val app by lazy { E2eAppDriver(composeRule) }

    @Test
    fun appSendsRealCommandAndRendersTheBackendsRealReply() {
        app.skipOnboardingIfShown()
        app.signInThroughSetupScreen(timeoutMs = PROBE_TIMEOUT_MS)
        app.sendCommand(
            to = cfg.credentials.serviceAddress,
            project = cfg.project,
            body = "run the e2e probe ${cfg.commandNonce}",
            timeoutMs = SEND_TIMEOUT_MS
        )
        app.openFirstConversation(timeoutMs = REPLY_TIMEOUT_MS)

        // The reply, rendered. The task number is minted by the backend's queue,
        // so the test cannot supply it — it can only read it off the screen and
        // then require the *result* card to agree with the *ack* card about it.
        val ack = app.awaitRenderedTextContaining(ACK_PREFIX, timeoutMs = REPLY_TIMEOUT_MS)
        // Anchored on the backend's wording: the ack card's merged text also
        // contains the sender address, whose digits an unanchored match eats.
        val taskId = requireNotNull(
            Regex(Regex.escape(ACK_PREFIX) + "(\\d+)").find(ack)
        ) { "the rendered ack carried no task number: '$ack'" }.groupValues[1]
        app.awaitRenderedText("Task #$taskId done", timeoutMs = RESULT_TIMEOUT_MS)
    }

    /**
     * The debug-only prefill seam (`BuildConfig.ALLOW_PREFILL`) that already
     * exists for exactly this: point a debug build at a test server without
     * touching production code or typing host/port into the form.
     */
    private fun prefillIntent(cfg: E2eConfig): Intent {
        val c = cfg.credentials
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(PrefillCredentials.EXTRA_DISPLAY_NAME, c.displayName)
            .putExtra(PrefillCredentials.EXTRA_EMAIL, c.emailAddress)
            .putExtra(PrefillCredentials.EXTRA_PASSWORD, c.password)
            .putExtra(PrefillCredentials.EXTRA_IMAP_HOST, c.imapHost)
            .putExtra(PrefillCredentials.EXTRA_IMAP_PORT, c.imapPort.toString())
            .putExtra(PrefillCredentials.EXTRA_SMTP_HOST, c.smtpHost)
            .putExtra(PrefillCredentials.EXTRA_SMTP_PORT, c.smtpPort.toString())
            .putExtra(PrefillCredentials.EXTRA_SMTP_STARTTLS, c.smtpUseStartTls.toString())
            .putExtra(PrefillCredentials.EXTRA_SERVICE_ADDRESS, c.serviceAddress)
            .putExtra(PrefillCredentials.EXTRA_SHARED_SECRET, c.sharedSecret)
    }

    private companion object {
        /** The backend's own wording on a queued command — see json_kinds.handle_command. */
        const val ACK_PREFIX = "Queued as task #"

        /** A real IMAP + SMTP login against the container, on a cold JVM. */
        const val PROBE_TIMEOUT_MS = 90_000L
        const val SEND_TIMEOUT_MS = 90_000L
        /** Poll → authenticate → enqueue → spawn worker → CLI → SMTP reply → poll. */
        const val REPLY_TIMEOUT_MS = 240_000L
        const val RESULT_TIMEOUT_MS = 240_000L
    }
}
