package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.EnvelopeJson
import jakarta.mail.Address
import jakarta.mail.FetchProfile
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class ImapMailFetcher(
    private val sessionFactory: (Properties) -> Session = DefaultSessionFactory,
    private val storeConnector: (Session, MailCredentials) -> Store = DefaultStoreConnector
) : MailFetcher {

    override suspend fun fetchRecent(
        credentials: MailCredentials,
        count: Int
    ): List<FetchedMessage> = withContext(Dispatchers.IO) {
        val session = sessionFactory(imapProperties(credentials))
        val store = try {
            storeConnector(session, credentials)
        } catch (t: Throwable) {
            throw MailException("IMAP connect failed: ${t.message}", t)
        }
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val total = inbox.messageCount
                if (total == 0) return@withContext emptyList()
                val start = maxOf(1, total - count + 1)
                val messages = inbox.getMessages(start, total)
                // Bulk-prefetch everything toFetched() needs except the body.
                // Without this each field access (subject, from, headers, …)
                // round-trips to the server, so 50 messages ≈ 300 round-trips.
                inbox.fetch(messages, InboxFetchProfile)
                messages.reversed().map { it.toFetched() }
            } finally {
                if (inbox.isOpen) inbox.close(false)
            }
        } catch (t: Throwable) {
            if (t is MailException) throw t
            throw MailException("IMAP fetch failed: ${t.message}", t)
        } finally {
            try { if (store.isConnected) store.close() } catch (_: Throwable) {}
        }
    }

    companion object {
        internal fun imapProperties(credentials: MailCredentials): Properties = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", credentials.imapHost)
            put("mail.imaps.port", credentials.imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
            put("mail.imaps.timeout", READ_TIMEOUT_MS.toString())
        }

        internal fun Message.toFetched(): FetchedMessage {
            val fromAddress = from?.firstOrNull() as? InternetAddress
            val toAddresses = (getRecipients(Message.RecipientType.TO) ?: emptyArray<Address>())
                .mapNotNull { (it as? InternetAddress)?.address }
            val inReplyTo = getHeader("In-Reply-To")?.firstOrNull()?.trim()
            val references = getHeader("References")?.firstOrNull()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                ?: emptyList()
            val messageId = getHeader("Message-ID")?.firstOrNull()?.trim().orEmpty()
            val rawContentType = try { contentType.orEmpty() } catch (_: Throwable) { "" }
            val isJson = rawContentType.contains("application/json", ignoreCase = true)
            val rawJsonBody = if (isJson) readRawText(this) else ""
            val envelope = if (isJson) tryParseEnvelope(rawJsonBody) else null
            val bodyForUi = envelope?.body?.takeIf(String::isNotBlank)
                ?: if (isJson) rawJsonBody else extractPlainText(this)
            return FetchedMessage(
                messageId = messageId,
                from = fromAddress?.address.orEmpty(),
                fromName = fromAddress?.personal,
                to = toAddresses,
                subject = subject.orEmpty(),
                body = bodyForUi,
                sentAt = sentDate,
                receivedAt = receivedDate,
                inReplyTo = inReplyTo,
                references = references,
                isSeen = flags.contains(Flags.Flag.SEEN),
                contentType = rawContentType,
                envelope = envelope
            )
        }

        internal fun readRawText(part: Part): String = try {
            when (val body = part.content) {
                is String -> body
                is java.io.InputStream -> body.bufferedReader(Charsets.UTF_8).use { it.readText() }
                else -> part.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
        } catch (_: Throwable) {
            try { part.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty() } catch (_: Throwable) { "" }
        }

        internal fun tryParseEnvelope(raw: String): Envelope? {
            if (raw.isBlank()) return null
            return try {
                EnvelopeJson.decodeFromString(Envelope.serializer(), raw)
            } catch (_: Throwable) {
                null
            }
        }

        internal fun extractPlainText(part: Part): String {
            return try {
                when {
                    part.isMimeType("text/plain") -> part.content?.toString().orEmpty()
                    part.isMimeType("multipart/*") -> {
                        val multipart = part.content as? Multipart ?: return ""
                        val plain = firstMatching(multipart, "text/plain")
                        if (plain != null) return plain
                        firstMatching(multipart, "text/html")?.let { return stripHtml(it) }
                        ""
                    }
                    part.isMimeType("text/html") -> stripHtml(part.content?.toString().orEmpty())
                    else -> {
                        if (part is MimeBodyPart && part.disposition == null) {
                            part.content?.toString().orEmpty()
                        } else ""
                    }
                }
            } catch (_: Throwable) {
                ""
            }
        }

        private fun firstMatching(multipart: Multipart, mime: String): String? {
            for (i in 0 until multipart.count) {
                val child = multipart.getBodyPart(i)
                if (child.isMimeType(mime)) return child.content?.toString()
                if (child.isMimeType("multipart/*")) {
                    val nested = child.content as? Multipart ?: continue
                    firstMatching(nested, mime)?.let { return it }
                }
            }
            return null
        }

        internal fun stripHtml(html: String): String =
            html.replace(Regex("<[^>]+>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

        private val InboxFetchProfile: FetchProfile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(FetchProfile.Item.CONTENT_INFO)
            add("Message-ID")
            add("In-Reply-To")
            add("References")
        }

        private val DefaultSessionFactory: (Properties) -> Session = { Session.getInstance(it) }
        private val DefaultStoreConnector: (Session, MailCredentials) -> Store = { session, c ->
            session.getStore("imaps").also {
                it.connect(c.imapHost, c.imapPort, c.emailAddress, c.password)
            }
        }

        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
