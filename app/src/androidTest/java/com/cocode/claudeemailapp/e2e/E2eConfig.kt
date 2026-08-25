package com.cocode.claudeemailapp.e2e

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.cocode.claudeemailapp.data.MailCredentials

/**
 * The run's coordinates, handed in by `scripts/e2e/harness.py` as
 * instrumentation runner arguments.
 *
 * Missing arguments are a hard failure, not an `assumeTrue` skip. A skipped
 * test reports as a pass, and this test is the whole assertion of the slice —
 * a green gate must mean the round trip actually happened, never "the harness
 * forgot to pass an argument".
 */
data class E2eConfig(
    val credentials: MailCredentials,
    val caPem: String,
    val imapsPort: Int,
    val project: String,
    /**
     * Tags the outbound command so the host can find its ledger entry.
     *
     * The device is deliberately *not* told the CLI's output token: that string
     * exists only on the host, so no device-side assertion could ever fabricate
     * it. `scripts/e2e/oracle.py` checks it against the delivered mail.
     */
    val commandNonce: String
) {
    companion object {
        private fun Bundle.require(key: String): String =
            getString(key)?.takeIf { it.isNotBlank() }
                ?: error(
                    "missing instrumentation runner argument '$key' — run this test " +
                        "through scripts/e2e/harness.py, which boots the mail server, " +
                        "the backend and the TLS terminators it needs."
                )

        fun require(): E2eConfig {
            val args = InstrumentationRegistry.getArguments()
            val imapsPort = args.require("e2e.imaps.port").toInt()
            return E2eConfig(
                credentials = MailCredentials(
                    displayName = "E2E App",
                    emailAddress = args.require("e2e.app.email"),
                    password = args.require("e2e.app.password"),
                    imapHost = HOST,
                    imapPort = imapsPort,
                    smtpHost = HOST,
                    smtpPort = args.require("e2e.smtps.port").toInt(),
                    smtpUseStartTls = false,
                    serviceAddress = args.require("e2e.service.address"),
                    sharedSecret = args.require("e2e.shared.secret")
                ),
                caPem = String(
                    android.util.Base64.decode(
                        args.require("e2e.ca.pem.b64"), android.util.Base64.DEFAULT
                    )
                ),
                imapsPort = imapsPort,
                project = args.require("e2e.project"),
                commandNonce = args.require("e2e.command.nonce")
            )
        }

        /** The emulator's NAT alias for the host loopback the servers bind to. */
        const val HOST = "10.0.2.2"
    }
}
