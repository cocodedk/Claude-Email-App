package com.cocode.claudeemailapp.e2e

import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.mail.OutgoingMessage
import com.cocode.claudeemailapp.mail.SmtpMailSender
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.Envelopes
import com.cocode.claudeemailapp.protocol.envelope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Emit one real message per inbound envelope kind, for the backend repo's
 * cross-client differential.
 *
 * This is a **capture tool**, not an assertion about the app. The backend repo
 * (`claude-email`) freezes what this test puts on the wire as golden fixtures
 * and asserts that its JSON path treats those bytes exactly as it treats a
 * message built by an independent reference script — see
 * `tests/e2e/test_differential_clients.py` and `tests/e2e/fixtures/README.md`
 * there. The case table below is the shared contract: it mirrors
 * `tests/e2e/fixtures/_reference_client.py::CASES` field for field, and a drift
 * on either side surfaces as a differential failure rather than as a quietly
 * weaker test.
 *
 * Everything on the app's side of the wire is production code: [Envelopes]
 * builds the envelope, [OutgoingMessage.envelope] serializes it,
 * [SmtpMailSender] constructs the MIME message and sends it through the default
 * `Transport.send` over real implicit TLS. Nothing is injected or stubbed — the
 * only thing this test supplies is a trust anchor for the throwaway server's
 * certificate, and credentials pointing at it.
 *
 * It is driven by `tests/e2e/fixtures/capture_app_fixtures.py` in the backend
 * repo, which boots the server, writes [CONFIG_PATH] and harvests the delivered
 * bytes server-side. Without that file the test has no server to reach and
 * skips, exactly as [com.cocode.claudeemailapp.mail.MailIntegrationTest] does
 * without credentials.
 */
class EnvelopeCaptureTest {

    private data class Config(
        val smtpHost: String,
        val smtpPort: Int,
        val certPem: String,
        val from: String,
        val password: String,
        val to: String,
        val sharedSecret: String,
        val wrongSecret: String,
        val project: String
    )

    private fun loadConfig(): Config? {
        val file = File(System.getenv(CONFIG_ENV) ?: CONFIG_PATH)
        if (!file.isFile) return null
        // kotlinx.serialization rather than org.json: the latter is a stubbed
        // android.jar class in the unit-test source set and throws at runtime.
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        fun text(key: String) = json.getValue(key).jsonPrimitive.content
        return Config(
            smtpHost = text("smtp_host"),
            smtpPort = text("smtp_port").toInt(),
            certPem = text("cert_pem"),
            from = text("from"),
            password = text("password"),
            to = text("to"),
            sharedSecret = text("shared_secret"),
            wrongSecret = text("wrong_secret"),
            project = text("project")
        )
    }

    /**
     * Trust the harness certificate, and only it, process-wide.
     *
     * `SmtpMailSender` never names a socket factory, so Jakarta Mail resolves
     * `SSLSocketFactory.getDefault()` — which reads the default [SSLContext].
     * Installing it here rather than disabling verification keeps the app's own
     * `ssl.checkserveridentity=true` armed: the certificate carries
     * `IP:127.0.0.1` in its SAN, so the hostname check is a real check that has
     * to pass.
     */
    private fun installTrust(certPem: String) {
        val certificate = File(certPem).inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("harness", certificate)
        }
        val factory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        ).apply { init(store) }
        SSLContext.setDefault(
            SSLContext.getInstance("TLS").apply {
                init(null, factory.trustManagers, null)
            }
        )
    }

    /** The shared contract. One entry per inbound kind, plus a bad-credential command. */
    private fun cases(config: Config): List<Pair<String, Envelope>> {
        val auth = config.sharedSecret
        return listOf(
            "reply" to Envelopes.reply(
                taskId = 4242L,
                body = "differential probe: reply",
                askId = 77L,
                auth = auth
            ),
            "retry" to Envelopes.retry(
                taskId = 4242L,
                newBody = "differential probe: retry",
                auth = auth
            ),
            "commit" to Envelopes.commit(
                project = config.project,
                body = "differential probe: commit",
                auth = auth
            ),
            "reset" to Envelopes.reset(project = config.project, auth = auth),
            "confirm_reset" to Envelopes.confirmReset(
                project = config.project,
                token = "differential-reset-token",
                auth = auth
            ),
            "status" to Envelopes.status(
                taskId = 4242L,
                project = config.project,
                auth = auth
            ),
            "cancel" to Envelopes.cancel(
                project = config.project,
                drainQueue = true,
                auth = auth
            ),
            "list_projects" to Envelopes.listProjects(auth = auth),
            "command_bad_auth" to Envelopes.command(
                body = "differential probe: bad auth",
                project = config.project,
                auth = config.wrongSecret
            ),
            "command" to Envelopes.command(
                body = "differential probe: command",
                project = config.project,
                priority = 3,
                auth = auth
            )
        )
    }

    @Test
    fun captureEveryInboundKind() = runBlocking {
        val config = loadConfig()
        assumeTrue(
            "no capture harness at $CONFIG_PATH — run capture_app_fixtures.py " +
                "in the claude-email repo to drive this test",
            config != null
        )
        val resolved = config!!
        // A configuration file can outlive the server it describes (an
        // interrupted capture). Treat an unreachable harness as an absent one
        // rather than failing this repo's unit suite for the wrong reason.
        assumeTrue(
            "capture harness configured at ${resolved.smtpHost}:${resolved.smtpPort} " +
                "but nothing is listening there",
            reachable(resolved.smtpHost, resolved.smtpPort)
        )
        installTrust(resolved.certPem)

        val credentials = MailCredentials(
            displayName = "Differential Capture",
            emailAddress = resolved.from,
            password = resolved.password,
            imapHost = resolved.smtpHost,
            imapPort = 0,
            smtpHost = resolved.smtpHost,
            smtpPort = resolved.smtpPort,
            smtpUseStartTls = false,
            serviceAddress = resolved.to,
            sharedSecret = resolved.sharedSecret
        )
        val sender = SmtpMailSender()
        val sent = mutableSetOf<String>()
        for ((name, envelope) in cases(resolved)) {
            val outgoing = OutgoingMessage.envelope(
                to = resolved.to,
                subject = "differential $name",
                envelope = envelope
            )
            val result = sender.send(credentials, outgoing)
            assertTrue("no Message-ID minted for $name", result.messageId.isNotBlank())
            sent += name
        }
        assertEquals(cases(resolved).map { it.first }.toSet(), sent)
    }

    private fun reachable(host: String, port: Int): Boolean =
        runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 2000) }
        }.isSuccess

    private companion object {
        const val CONFIG_ENV = "DIFFERENTIAL_CAPTURE_CONFIG"
        const val CONFIG_PATH = "/tmp/claude-email-differential-capture/config.json"
    }
}
