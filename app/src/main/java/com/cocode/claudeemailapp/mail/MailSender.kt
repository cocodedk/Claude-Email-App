package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials

interface MailSender {
    suspend fun send(credentials: MailCredentials, message: OutgoingMessage): SendResult
}
