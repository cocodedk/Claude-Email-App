package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.protocol.Envelope
import java.util.Date

data class OutgoingMessage(
    val to: String,
    val subject: String,
    val body: String,
    val contentType: String = "text/plain; charset=utf-8",
    val inReplyTo: String? = null,
    val references: List<String> = emptyList(),
    val extraHeaders: Map<String, String> = emptyMap()
) {
    companion object
}

data class SendResult(
    val messageId: String,
    val sentAt: Date
)

data class FetchedMessage(
    val messageId: String,
    val from: String,
    val fromName: String?,
    val to: List<String>,
    val subject: String,
    val body: String,
    val sentAt: Date?,
    val receivedAt: Date?,
    val inReplyTo: String?,
    val references: List<String>,
    val isSeen: Boolean,
    val contentType: String = "text/plain",
    val envelope: Envelope? = null
)

sealed class ProbeResult {
    object Success : ProbeResult()
    data class Failure(val stage: Stage, val message: String, val cause: Throwable? = null) : ProbeResult()

    enum class Stage { IMAP, SMTP }
}

class MailException(message: String, cause: Throwable? = null) : Exception(message, cause)
