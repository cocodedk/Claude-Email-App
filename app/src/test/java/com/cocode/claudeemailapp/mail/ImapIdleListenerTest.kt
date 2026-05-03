package com.cocode.claudeemailapp.mail

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImapIdleListenerTest {

    @Test
    fun `relays each session event to the activity callback`() = runTest {
        val received = mutableListOf<Int>()
        val session = FakeIdleSession(events = 3)
        val listener = ImapIdleListener(
            sessionFactory = { session },
            onActivity = { received += received.size }
        )
        val job = launch { listener.run() }
        session.awaitDrained()
        job.cancelAndJoin()
        assertEquals(listOf(0, 1, 2), received)
    }

    @Test
    fun `creates a new session and retries when the current one throws`() = runTest {
        val received = mutableListOf<Unit>()
        val sessions = ArrayDeque<ImapIdleSession>().apply {
            add(ThrowingIdleSession(IllegalStateException("connection dropped")))
            add(FakeIdleSession(events = 1))
        }
        val errors = mutableListOf<Throwable>()
        val listener = ImapIdleListener(
            sessionFactory = { sessions.removeFirst() },
            onActivity = { received += Unit },
            onError = { errors += it },
            backoffMs = 0
        )
        val job = launch { listener.run() }
        (sessions.lastOrNull() as? FakeIdleSession ?: error("no fake")).awaitDrained()
        job.cancelAndJoin()
        assertEquals(1, received.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is IllegalStateException)
    }

    @Test
    fun `lets cancellation exit run cleanly without retrying`() = runTest {
        var sessionCount = 0
        val listener = ImapIdleListener(
            sessionFactory = {
                sessionCount++
                FakeIdleSession(events = 0)
            },
            onActivity = { },
            backoffMs = 0
        )
        val job = launch { listener.run() }
        yield()
        job.cancelAndJoin()
        assertEquals(1, sessionCount)
    }
}

private class ThrowingIdleSession(private val cause: Throwable) : ImapIdleSession {
    override suspend fun listen(onEvent: suspend () -> Unit) {
        throw cause
    }
    override fun close() = Unit
}

private class FakeIdleSession(events: Int) : ImapIdleSession {
    private val remaining = events
    private val drained = CompletableDeferred<Unit>()

    override suspend fun listen(onEvent: suspend () -> Unit) {
        repeat(remaining) { onEvent() }
        drained.complete(Unit)
        // suspend until cancelled so listener doesn't loop
        kotlinx.coroutines.awaitCancellation()
    }

    override fun close() = Unit

    suspend fun awaitDrained() = drained.await()
}
