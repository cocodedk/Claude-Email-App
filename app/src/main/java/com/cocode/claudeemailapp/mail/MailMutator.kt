package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials

/**
 * Server-side mutations on individual messages, addressed by RFC 5322
 * Message-ID. Both operations are destructive from the Inbox's point of
 * view: delete expunges the message outright, archive copies it to the
 * server's Archive folder and then expunges from Inbox.
 */
interface MailMutator {
    suspend fun delete(credentials: MailCredentials, messageId: String)
    suspend fun archive(credentials: MailCredentials, messageId: String)
}
