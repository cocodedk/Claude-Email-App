package com.cocode.claudeemailapp.data

import com.cocode.claudeemailapp.mail.FetchedMessage
import java.util.Date

/**
 * Groups a flat inbox list into conversations using the RFC-5322
 * In-Reply-To / References chain (the wire-protocol thread key
 * confirmed by the claude-email backend on 2026-04-23).
 *
 * Title, agent display name, and ordering are derived locally — the
 * envelope carries no `conversation_id`, no `title`, and no archive
 * status.
 */
object ConversationGrouper {

    fun group(messages: List<FetchedMessage>, selfEmail: String): List<Conversation> {
        if (messages.isEmpty()) return emptyList()
        val parent = HashMap<String, String>()

        fun find(x: String): String {
            var r = x
            while (parent[r] != r) {
                val p = parent[r]!!
                parent[r] = parent[p] ?: p
                r = parent[r]!!
            }
            return r
        }

        fun union(a: String, b: String) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        // Messages with blank Message-IDs would otherwise collapse into a single
        // "" bucket; mint a synthetic per-message key so each becomes its own thread.
        fun keyFor(index: Int, m: FetchedMessage): String =
            if (m.messageId.isBlank()) "__blank_$index" else m.messageId

        for ((i, m) in messages.withIndex()) {
            val k = keyFor(i, m)
            parent.putIfAbsent(k, k)
        }
        for ((i, m) in messages.withIndex()) {
            if (m.messageId.isBlank()) continue
            val k = keyFor(i, m)
            val refs = m.references + listOfNotNull(m.inReplyTo)
            for (r in refs) {
                if (r.isBlank()) continue
                parent.putIfAbsent(r, r)
                union(r, k)
            }
        }

        val buckets = LinkedHashMap<String, MutableList<FetchedMessage>>()
        for ((i, m) in messages.withIndex()) {
            val root = find(keyFor(i, m))
            buckets.getOrPut(root) { mutableListOf() }.add(m)
        }

        val self = selfEmail.trim().lowercase()
        return buckets.map { (rootId, msgs) ->
            val sorted = msgs.sortedBy { it.sentAt ?: it.receivedAt ?: EPOCH }
            val first = sorted.first()
            val last = sorted.last()
            val agentMsg = sorted.firstOrNull { it.from.trim().lowercase() != self } ?: first
            Conversation(
                id = rootId,
                title = stripReplyPrefix(first.subject),
                agentDisplay = agentMsg.fromName?.takeIf(String::isNotBlank) ?: agentMsg.from,
                agentEmail = agentMsg.from,
                latestAt = last.sentAt ?: last.receivedAt,
                messageCount = sorted.size,
                unreadCount = sorted.count { !it.isSeen },
                lastMessage = last,
                messages = sorted
            )
        }.sortedByDescending { it.latestAt ?: EPOCH }
    }

    private val PREFIX = Regex("""^\s*(re|fwd?|aw|sv|wg|fw)\s*:\s*""", RegexOption.IGNORE_CASE)
    private val BRACKETED = Regex("""^\s*\[\s*(re|fwd?|aw|sv|wg|fw)\s*]\s*:?\s*""", RegexOption.IGNORE_CASE)

    fun stripReplyPrefix(subject: String): String {
        var s = subject.trim()
        var prev: String
        do {
            prev = s
            s = BRACKETED.replace(s, "").trim()
            s = PREFIX.replace(s, "").trim()
        } while (s != prev)
        return s.ifBlank { "(no subject)" }
    }

    /** True when [subject] already begins with a reply-style prefix. */
    fun hasReplyPrefix(subject: String): Boolean {
        val t = subject.trim()
        return PREFIX.containsMatchIn(t) && PREFIX.find(t)?.range?.first == 0 ||
            BRACKETED.containsMatchIn(t) && BRACKETED.find(t)?.range?.first == 0
    }

    private val EPOCH = Date(0)
}
