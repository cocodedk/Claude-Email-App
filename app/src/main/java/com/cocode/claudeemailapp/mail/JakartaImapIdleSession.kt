package com.cocode.claudeemailapp.mail

import com.cocode.claudeemailapp.data.MailCredentials
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.event.MessageCountAdapter
import jakarta.mail.event.MessageCountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.eclipse.angus.mail.imap.IMAPFolder
import java.util.Properties
import kotlin.concurrent.thread

/**
 * Real jakarta-mail IMAP IDLE session. The blocking idle() call runs on a
 * dedicated daemon thread; new-message events are forwarded onto a channel
 * that the suspending listen() coroutine drains. close() shuts the folder,
 * which causes idle() to throw and the channel to close.
 *
 * No unit test — exercised only via emulator/device integration. Touching
 * jakarta-mail in JVM unit tests requires a real IMAP server and is brittle.
 *
 * RFC 2177 caps an IDLE session at 29 minutes server-side; one.com enforces
 * this. When the server drops the connection idle() throws → daemon-thread
 * loop exits → channel closes → suspending listen() returns → outer
 * ImapIdleListener.run() loop creates a fresh session. Reactive reconnect
 * is acceptable for v1; consider proactive cycling (mail.imaps.minidletime
 * or a 25m timer) if the 1–2s reconnect gap proves to drop replies.
 */
class JakartaImapIdleSession(
    private val credentials: MailCredentials,
    private val sessionFactory: (Properties) -> Session = { Session.getInstance(it) }
) : ImapIdleSession {

    @Volatile private var folder: IMAPFolder? = null
    @Volatile private var store: Store? = null

    override suspend fun listen(onEvent: suspend () -> Unit) = withContext(Dispatchers.IO) {
        // Nested try/finally so that a throw at any step (connect, getFolder,
        // open, listener registration) still tears down whatever was acquired.
        // The previous flat structure leaked the connected Store if open()
        // threw before the outer try block was entered.
        val props = ImapMailFetcher.imapProperties(credentials)
        val session = sessionFactory(props)
        val s = session.getStore("imaps")
        store = s
        try {
            s.connect(credentials.imapHost, credentials.imapPort, credentials.emailAddress, credentials.password)
            val f = s.getFolder("INBOX") as IMAPFolder
            f.open(Folder.READ_ONLY)
            folder = f
            try {
                val events = Channel<Unit>(Channel.UNLIMITED)
                f.addMessageCountListener(object : MessageCountAdapter() {
                    override fun messagesAdded(e: MessageCountEvent) { events.trySend(Unit) }
                })
                val idleThread = thread(name = "imap-idle", isDaemon = true) {
                    // Forward any IDLE-side failure (auth dropped, socket reset, BAD response)
                    // through the channel cause so listen()'s for-loop rethrows it and the
                    // outer ImapIdleListener sees it via its onError callback. Without the
                    // cause the listener silently reconnects with no visibility into why.
                    var cause: Throwable? = null
                    try { while (f.isOpen) f.idle() } catch (t: Throwable) { cause = t }
                    events.close(cause)
                }
                try {
                    for (event in events) onEvent()
                } finally {
                    idleThread.join(2_000)
                }
            } finally {
                try { if (f.isOpen) f.close(false) } catch (_: Throwable) {}
            }
        } finally {
            try { if (s.isConnected) s.close() } catch (_: Throwable) {}
        }
    }

    override fun close() {
        try { folder?.close(false) } catch (_: Throwable) {}
        try { store?.close() } catch (_: Throwable) {}
    }
}
