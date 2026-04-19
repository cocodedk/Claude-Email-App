package com.cocode.claudeemailapp.support

import com.cocode.claudeemailapp.data.MailCredentials

object TestMailConfig {

    data class Resolved(val credentials: MailCredentials, val recipient: String)

    fun load(): Resolved? {
        val email = System.getProperty("test.mail.email").orEmpty()
        val password = System.getProperty("test.mail.password").orEmpty()
        val imapHost = System.getProperty("test.mail.imap.host").orEmpty()
        val imapPort = System.getProperty("test.mail.imap.port")?.toIntOrNull()
        val smtpHost = System.getProperty("test.mail.smtp.host").orEmpty()
        val smtpPort = System.getProperty("test.mail.smtp.port")?.toIntOrNull()
        val smtpStartTls = System.getProperty("test.mail.smtp.starttls")?.toBooleanStrictOrNull() ?: false
        val recipient = System.getProperty("test.mail.recipient").orEmpty().ifBlank { email }
        if (email.isBlank() || password.isBlank() ||
            imapHost.isBlank() || imapPort == null ||
            smtpHost.isBlank() || smtpPort == null) return null
        return Resolved(
            credentials = MailCredentials(
                displayName = "Integration Test",
                emailAddress = email,
                password = password,
                imapHost = imapHost,
                imapPort = imapPort,
                smtpHost = smtpHost,
                smtpPort = smtpPort,
                smtpUseStartTls = smtpStartTls,
                serviceAddress = email,
                sharedSecret = ""
            ),
            recipient = recipient
        )
    }
}
