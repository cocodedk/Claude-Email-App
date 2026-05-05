package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials

interface MailFetcher {
    suspend fun fetchRecent(credentials: MailCredentials, count: Int = 50): List<FetchedMessage>

    // Best-effort: implementations must not throw on partial-match or transport failure.
    suspend fun markSeen(credentials: MailCredentials, messageIds: List<String>)
}
