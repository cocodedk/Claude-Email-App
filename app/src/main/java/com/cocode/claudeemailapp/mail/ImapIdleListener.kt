package com.cocode.claudeemailapp.mail

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class ImapIdleListener(
    private val sessionFactory: suspend () -> ImapIdleSession,
    private val onActivity: suspend () -> Unit,
    private val onError: (Throwable) -> Unit = {},
    private val backoffMs: Long = DEFAULT_BACKOFF_MS
) {
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            try {
                val session = sessionFactory()
                session.listen { onActivity() }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                onError(t)
                if (backoffMs > 0) delay(backoffMs)
            }
        }
    }

    companion object {
        const val DEFAULT_BACKOFF_MS = 5_000L
    }
}
