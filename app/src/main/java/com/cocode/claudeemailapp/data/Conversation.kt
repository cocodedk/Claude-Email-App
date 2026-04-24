package com.cocode.claudeemailapp.data

import com.cocode.claudeemailapp.mail.FetchedMessage
import java.util.Date

data class Conversation(
    val id: String,
    val title: String,
    val agentDisplay: String,
    val agentEmail: String,
    val latestAt: Date?,
    val messageCount: Int,
    val unreadCount: Int,
    val lastMessage: FetchedMessage,
    val messages: List<FetchedMessage>
)
