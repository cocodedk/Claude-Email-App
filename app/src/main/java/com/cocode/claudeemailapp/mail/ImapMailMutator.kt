package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.search.HeaderTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.angus.mail.imap.IMAPFolder
import java.util.Properties

/**
 * IMAP-backed implementation of [MailMutator]. Delete permanently
 * expunges the matched message from the Inbox; archive copies it to the
 * server's Archive folder (detected via the RFC 6154 \\Archive SPECIAL-USE
 * attribute, with a pragmatic fallback list) and then expunges from
 * Inbox.
 */
class ImapMailMutator(
    private val sessionFactory: (Properties) -> Session = DefaultSessionFactory,
    private val storeConnector: (Session, MailCredentials) -> Store = DefaultStoreConnector
) : MailMutator {

    override suspend fun delete(credentials: MailCredentials, messageId: String) =
        mutate(credentials) { inbox, _ ->
            val match = findByMessageId(inbox, messageId)
                ?: throw MailException("message not found on server: $messageId")
            inbox.setFlags(arrayOf(match), Flags(Flags.Flag.DELETED), true)
        }

    override suspend fun archive(credentials: MailCredentials, messageId: String) =
        mutate(credentials) { inbox, store ->
            val match = findByMessageId(inbox, messageId)
                ?: throw MailException("message not found on server: $messageId")
            val archive = pickArchiveFolder(store)
                ?: throw MailException("no Archive folder on this account")
            if (!archive.exists()) archive.create(Folder.HOLDS_MESSAGES)
            inbox.copyMessages(arrayOf(match), archive)
            inbox.setFlags(arrayOf(match), Flags(Flags.Flag.DELETED), true)
        }

    private suspend fun mutate(
        credentials: MailCredentials,
        block: (Folder, Store) -> Unit
    ) = withContext(Dispatchers.IO) {
        val session = sessionFactory(ImapMailFetcher.imapProperties(credentials))
        val store = try {
            storeConnector(session, credentials)
        } catch (t: Throwable) {
            throw MailException("IMAP connect failed: ${t.message}", t)
        }
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            try {
                block(inbox, store)
            } finally {
                // close(true) triggers EXPUNGE, permanently removing
                // messages flagged \\Deleted in this session.
                if (inbox.isOpen) inbox.close(true)
            }
        } catch (t: Throwable) {
            if (t is MailException) throw t
            throw MailException("IMAP mutation failed: ${t.message}", t)
        } finally {
            try { if (store.isConnected) store.close() } catch (_: Throwable) {}
        }
    }

    companion object {
        internal fun findByMessageId(inbox: Folder, messageId: String): Message? {
            val normalized = messageId.trim()
            val candidates = inbox.search(HeaderTerm("Message-ID", normalized))
            // HeaderTerm does substring match; verify exact before acting so
            // we never delete the wrong message on a near-miss.
            return candidates.firstOrNull { m ->
                m.getHeader("Message-ID")?.any { it.trim() == normalized } == true
            }
        }

        /**
         * Prefer RFC 6154 \\Archive-tagged folder, fall back to common
         * Gmail/Outlook/generic names. Returns null if nothing usable is
         * found — caller decides whether to create one or error.
         */
        internal fun pickArchiveFolder(store: Store): Folder? {
            val byAttribute = store.defaultFolder.list("*").firstOrNull { f ->
                (f as? IMAPFolder)?.attributes?.any { it.equals("\\Archive", ignoreCase = true) } == true
            }
            if (byAttribute != null) return byAttribute
            val fallback = listOf("[Gmail]/All Mail", "Archive", "INBOX.Archive")
            return fallback.firstNotNullOfOrNull { name ->
                runCatching { store.getFolder(name).takeIf { it.exists() } }.getOrNull()
            }
        }

        private val DefaultSessionFactory: (Properties) -> Session = { Session.getInstance(it) }
        private val DefaultStoreConnector: (Session, MailCredentials) -> Store = { session, c ->
            session.getStore("imaps").also {
                it.connect(c.imapHost, c.imapPort, c.emailAddress, c.password)
            }
        }
    }
}
