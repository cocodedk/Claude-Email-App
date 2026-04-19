package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class MailProbe(
    private val imapSessionFactory: (Properties) -> Session = DefaultImapSessionFactory,
    private val imapStoreConnector: (Session, MailCredentials) -> Store = DefaultImapStoreConnector,
    private val smtpSessionFactory: (Properties) -> Session = DefaultSmtpSessionFactory,
    private val smtpTransportConnector: (Session, MailCredentials) -> Transport = DefaultSmtpTransportConnector
) {

    suspend fun probe(credentials: MailCredentials): ProbeResult = withContext(Dispatchers.IO) {
        probeImap(credentials)?.let { return@withContext it }
        probeSmtp(credentials)?.let { return@withContext it }
        ProbeResult.Success
    }

    internal fun probeImap(credentials: MailCredentials): ProbeResult.Failure? {
        val session = imapSessionFactory(imapProperties(credentials))
        var store: Store? = null
        return try {
            store = imapStoreConnector(session, credentials)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            inbox.close(false)
            null
        } catch (t: Throwable) {
            ProbeResult.Failure(ProbeResult.Stage.IMAP, t.message.orEmpty(), t)
        } finally {
            try { store?.takeIf { it.isConnected }?.close() } catch (_: Throwable) {}
        }
    }

    internal fun probeSmtp(credentials: MailCredentials): ProbeResult.Failure? {
        val session = smtpSessionFactory(smtpProperties(credentials))
        var transport: Transport? = null
        return try {
            transport = smtpTransportConnector(session, credentials)
            null
        } catch (t: Throwable) {
            ProbeResult.Failure(ProbeResult.Stage.SMTP, t.message.orEmpty(), t)
        } finally {
            try { transport?.takeIf { it.isConnected }?.close() } catch (_: Throwable) {}
        }
    }

    companion object {
        internal fun imapProperties(credentials: MailCredentials): Properties = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", credentials.imapHost)
            put("mail.imaps.port", credentials.imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", TIMEOUT_MS.toString())
            put("mail.imaps.timeout", TIMEOUT_MS.toString())
        }

        internal fun smtpProperties(credentials: MailCredentials): Properties = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", credentials.smtpHost)
            put("mail.smtp.port", credentials.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", TIMEOUT_MS.toString())
            put("mail.smtp.timeout", TIMEOUT_MS.toString())
            put("mail.smtp.writetimeout", TIMEOUT_MS.toString())
            if (credentials.smtpUseStartTls) {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            } else {
                put("mail.smtp.ssl.enable", "true")
            }
            put("mail.smtp.ssl.checkserveridentity", "true")
        }

        private val DefaultImapSessionFactory: (Properties) -> Session = { Session.getInstance(it) }
        private val DefaultImapStoreConnector: (Session, MailCredentials) -> Store = { session, c ->
            session.getStore("imaps").also {
                it.connect(c.imapHost, c.imapPort, c.emailAddress, c.password)
            }
        }
        private val DefaultSmtpSessionFactory: (Properties) -> Session = { Session.getInstance(it) }
        private val DefaultSmtpTransportConnector: (Session, MailCredentials) -> Transport = { session, c ->
            session.getTransport("smtp").also {
                it.connect(c.smtpHost, c.smtpPort, c.emailAddress, c.password)
            }
        }

        private const val TIMEOUT_MS = 15_000
    }
}
