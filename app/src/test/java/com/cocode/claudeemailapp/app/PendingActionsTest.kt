package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.protocol.Kinds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingActionsTest {

    private fun p(status: String, taskId: Long? = 42L) = PendingCommand(
        messageId = "<m@x>",
        sentAt = 0L,
        to = "svc@x",
        subject = "sub",
        kind = Kinds.COMMAND,
        bodyPreview = "body",
        taskId = taskId,
        status = status
    )

    @Test
    fun retryable_failed() { assertTrue(isRetryable(p(PendingStatus.FAILED))) }

    @Test
    fun retryable_error() { assertTrue(isRetryable(p(PendingStatus.ERROR))) }

    @Test
    fun notRetryable_whenInFlight() {
        assertFalse(isRetryable(p(PendingStatus.QUEUED)))
        assertFalse(isRetryable(p(PendingStatus.RUNNING)))
        assertFalse(isRetryable(p(PendingStatus.AWAITING_USER)))
    }

    @Test
    fun notRetryable_whenDone() { assertFalse(isRetryable(p(PendingStatus.DONE))) }

    @Test
    fun cancellable_queued() { assertTrue(isCancellable(p(PendingStatus.QUEUED))) }

    @Test
    fun cancellable_running() { assertTrue(isCancellable(p(PendingStatus.RUNNING))) }

    @Test
    fun cancellable_awaitingUser() { assertTrue(isCancellable(p(PendingStatus.AWAITING_USER))) }

    @Test
    fun notCancellable_awaitingAck_noTaskIdYet() {
        // before ACK lands, taskId is null — can't send a Cancel envelope.
        assertFalse(isCancellable(p(PendingStatus.AWAITING_ACK, taskId = null)))
    }

    @Test
    fun notCancellable_withoutTaskId() {
        assertFalse(isCancellable(p(PendingStatus.RUNNING, taskId = null)))
    }

    @Test
    fun notCancellable_whenTerminal() {
        assertFalse(isCancellable(p(PendingStatus.FAILED)))
        assertFalse(isCancellable(p(PendingStatus.ERROR)))
        assertFalse(isCancellable(p(PendingStatus.DONE)))
    }
}
