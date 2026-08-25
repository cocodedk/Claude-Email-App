package com.cocode.claudeemailapp.e2e

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/**
 * Makes the harness's throwaway certificate trustable — and proves it was not
 * trusted to begin with.
 *
 * The production mail code sets `mail.smtp.ssl.checkserveridentity=true` and
 * never sets a socket factory, so Angus Mail falls through to
 * `SSLSocketFactory.getDefault()`. Installing the harness CA as the process's
 * default [SSLContext] is therefore configuration of the *trust anchor*, and
 * nothing else: the socket, the handshake, the hostname check and every byte of
 * SMTP and IMAP above it are the real implementation. Nothing in the mail path
 * is replaced or stubbed.
 *
 * The certificate carries `IP:10.0.2.2` in its SAN, so the hostname check the
 * app performs is a real check that really has to pass.
 */
object E2eTrust {

    private fun trustManagers(pem: String): Array<javax.net.ssl.TrustManager> {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray()))
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("claude-email-app-e2e", cert)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(store)
        return factory.trustManagers
    }

    /**
     * Assert the platform's own trust store rejects the harness certificate.
     *
     * This is the negative control for [installAsDefault], and its scope is
     * exactly this: the harness CA is *not* a platform trust anchor, so a
     * handshake that succeeds only after [installAsDefault] proves the app
     * resolved `SSLSocketFactory.getDefault()` and did not pin the system trust
     * store — the install was necessary as well as sufficient. It does **not**
     * prove the app verifies anything. An app-side trust-all regression (a
     * permissive `TrustManager`, a custom `SSLSocketFactory`, or
     * `mail.imaps.ssl.trust` / `mail.smtp.ssl.trust`) would leave this control
     * green, because the [SSLContext] built here is the test's own and never
     * touches the app's mail stack. That the app's verification is armed is
     * established by inspection instead: `SmtpMailSender`, `ImapMailFetcher`
     * and `MailProbe` all set `ssl.checkserveridentity=true`, and nothing under
     * `app/src/main` sets `ssl.trust`, a socket factory, a `TrustManager` or a
     * `HostnameVerifier`.
     *
     * It builds its own [SSLContext] from the system trust managers rather than
     * using `SSLSocketFactory.getDefault()`, because that factory is cached
     * process-wide on first use and touching it here would pin the *system*
     * trust store for the rest of the process — defeating the very install this
     * guards. The failure it demands is specifically an [SSLHandshakeException]:
     * a refused connection would otherwise satisfy the control for the wrong
     * reason.
     */
    fun assertUntrustedBySystem(host: String, port: Int) {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val ctx = SSLContext.getInstance("TLS").apply { init(null, factory.trustManagers, null) }
        val failure = runCatching {
            (ctx.socketFactory.createSocket(host, port) as SSLSocket).use { socket ->
                socket.soTimeout = 15_000
                socket.startHandshake()
            }
        }.exceptionOrNull()
        check(failure is SSLHandshakeException) {
            "expected the platform trust store to reject the harness certificate " +
                "with an SSLHandshakeException, got: " +
                (failure?.toString() ?: "a successful handshake") +
                " — the trust anchor this test installs would then be doing nothing, " +
                "so nothing it asserts about the encrypted path would be attributable " +
                "to the app's own TLS configuration"
        }
    }

    /** Trust the harness CA — and only it — for the rest of this process. */
    fun installAsDefault(pem: String) {
        val ctx = SSLContext.getInstance("TLS").apply { init(null, trustManagers(pem), null) }
        SSLContext.setDefault(ctx)
    }
}
