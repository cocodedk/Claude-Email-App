package com.cocode.claudeemailapp.data

data class MailCredentials(
    val displayName: String,
    val emailAddress: String,
    val password: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUseStartTls: Boolean,
    val serviceAddress: String,
    val sharedSecret: String
)
