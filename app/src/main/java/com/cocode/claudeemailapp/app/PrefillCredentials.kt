package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.BuildConfig
import com.cocode.claudeemailapp.data.MailCredentials

object PrefillCredentials {
    const val EXTRA_DISPLAY_NAME = "prefill_display_name"
    const val EXTRA_EMAIL = "prefill_email"
    const val EXTRA_PASSWORD = "prefill_password"
    const val EXTRA_IMAP_HOST = "prefill_imap_host"
    const val EXTRA_IMAP_PORT = "prefill_imap_port"
    const val EXTRA_SMTP_HOST = "prefill_smtp_host"
    const val EXTRA_SMTP_PORT = "prefill_smtp_port"
    const val EXTRA_SMTP_STARTTLS = "prefill_smtp_starttls"
    const val EXTRA_SERVICE_ADDRESS = "prefill_service_address"
    const val EXTRA_SHARED_SECRET = "prefill_shared_secret"

    fun fromExtras(get: (String) -> String?): MailCredentials? {
        if (!BuildConfig.ALLOW_PREFILL) return null
        val email = get(EXTRA_EMAIL)?.takeIf { it.isNotBlank() } ?: return null
        val password = get(EXTRA_PASSWORD)?.takeIf { it.isNotBlank() } ?: return null
        val imapHost = get(EXTRA_IMAP_HOST)?.takeIf { it.isNotBlank() } ?: return null
        val smtpHost = get(EXTRA_SMTP_HOST)?.takeIf { it.isNotBlank() } ?: return null
        return MailCredentials(
            displayName = get(EXTRA_DISPLAY_NAME).orEmpty(),
            emailAddress = email,
            password = password,
            imapHost = imapHost,
            imapPort = get(EXTRA_IMAP_PORT)?.toIntOrNull() ?: 993,
            smtpHost = smtpHost,
            smtpPort = get(EXTRA_SMTP_PORT)?.toIntOrNull() ?: 465,
            smtpUseStartTls = get(EXTRA_SMTP_STARTTLS)?.equals("true", ignoreCase = true) == true,
            serviceAddress = get(EXTRA_SERVICE_ADDRESS).orEmpty(),
            sharedSecret = get(EXTRA_SHARED_SECRET).orEmpty()
        )
    }
}
