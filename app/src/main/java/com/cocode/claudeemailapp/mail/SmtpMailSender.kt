package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties

class SmtpMailSender(
    private val sessionFactory: (Properties, Authenticator) -> Session = DefaultSessionFactory,
    private val transportSend: (MimeMessage) -> Unit = DefaultTransportSend
) : MailSender {

    override suspend fun send(
        credentials: MailCredentials,
        message: OutgoingMessage
    ): SendResult = withContext(Dispatchers.IO) {
        val session = sessionFactory(smtpProperties(credentials), authenticator(credentials))
        val mime = MimeMessage(session).apply {
            setFrom(InternetAddress(credentials.emailAddress, credentials.displayName.ifBlank { null }))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(message.to))
            subject = message.subject
            if (message.contentType.startsWith("text/plain", ignoreCase = true)) {
                setText(message.body, "UTF-8")
            } else {
                setContent(message.body, message.contentType)
            }
            message.inReplyTo?.let { setHeader("In-Reply-To", it) }
            if (message.references.isNotEmpty()) {
                setHeader("References", message.references.joinToString(" "))
            }
            for ((name, value) in message.extraHeaders) setHeader(name, value)
            sentDate = Date()
        }
        try {
            transportSend(mime)
        } catch (t: Throwable) {
            throw MailException("SMTP send failed: ${t.message}", t)
        }
        SendResult(
            messageId = mime.messageID.orEmpty(),
            sentAt = mime.sentDate ?: Date()
        )
    }

    companion object {
        internal fun smtpProperties(credentials: MailCredentials): Properties = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", credentials.smtpHost)
            put("mail.smtp.port", credentials.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", MailTimeouts.CONNECT_MS.toString())
            put("mail.smtp.timeout", MailTimeouts.READ_MS.toString())
            put("mail.smtp.writetimeout", MailTimeouts.READ_MS.toString())
            if (credentials.smtpUseStartTls) {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            } else {
                put("mail.smtp.ssl.enable", "true")
            }
            put("mail.smtp.ssl.checkserveridentity", "true")
        }

        internal fun authenticator(credentials: MailCredentials): Authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(credentials.emailAddress, credentials.password)
        }

        private val DefaultSessionFactory: (Properties, Authenticator) -> Session =
            { props, auth -> Session.getInstance(props, auth) }

        private val DefaultTransportSend: (MimeMessage) -> Unit = { Transport.send(it) }
    }
}
