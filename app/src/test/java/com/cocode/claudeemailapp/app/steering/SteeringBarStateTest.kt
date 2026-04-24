package com.cocode.claudeemailapp.app.steering

import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SteeringBarStateTest {
    private fun pending(status: String, askId: String? = null) = PendingCommand(
        messageId = "m1",
        sentAt = 0L,
        to = "svc@x",
        subject = "s",
        kind = "command",
        bodyPreview = "",
        status = status,
        askId = askId,
        project = "p"
    )

    @Test fun nullPending_mapsToHidden() {
        assertEquals(SteeringBarState.Hidden, SteeringBarState.from(null))
    }

    @Test fun running_mapsToIdle() {
        assertEquals(SteeringBarState.Idle, SteeringBarState.from(pending(PendingStatus.RUNNING)))
    }

    @Test fun queued_mapsToIdle() {
        assertEquals(SteeringBarState.Idle, SteeringBarState.from(pending(PendingStatus.QUEUED)))
    }

    @Test fun awaitingAck_mapsToIdle() {
        assertEquals(SteeringBarState.Idle, SteeringBarState.from(pending(PendingStatus.AWAITING_ACK)))
    }

    @Test fun awaitingUser_withAskId_mapsToAwaitingUser() {
        val s = SteeringBarState.from(pending(PendingStatus.AWAITING_USER, askId = "7"))
        assertEquals(SteeringBarState.AwaitingUser("7"), s)
    }

    @Test fun awaitingUser_withoutAskId_fallsBackToIdle() {
        assertEquals(SteeringBarState.Idle, SteeringBarState.from(pending(PendingStatus.AWAITING_USER, askId = null)))
    }

    @Test fun done_mapsToHidden() {
        assertEquals(SteeringBarState.Hidden, SteeringBarState.from(pending(PendingStatus.DONE)))
    }

    @Test fun failed_mapsToHidden() {
        assertEquals(SteeringBarState.Hidden, SteeringBarState.from(pending(PendingStatus.FAILED)))
    }

    @Test fun error_mapsToHidden() {
        assertEquals(SteeringBarState.Hidden, SteeringBarState.from(pending(PendingStatus.ERROR)))
    }
}
