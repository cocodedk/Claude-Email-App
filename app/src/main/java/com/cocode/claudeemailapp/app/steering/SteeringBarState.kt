package com.cocode.claudeemailapp.app.steering

import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus

/**
 * Visual state of the task-steering bar, derived purely from the matched
 * [PendingCommand]. Ephemeral UI concerns (armed/sending/undo) live in
 * [SteeringBarController] on top of this.
 */
sealed interface SteeringBarState {
    data object Hidden : SteeringBarState
    data object Idle : SteeringBarState
    data class AwaitingUser(val askId: String) : SteeringBarState

    companion object {
        fun from(pending: PendingCommand?): SteeringBarState {
            if (pending == null) return Hidden
            return when (pending.status) {
                PendingStatus.AWAITING_ACK,
                PendingStatus.QUEUED,
                PendingStatus.RUNNING -> Idle
                PendingStatus.AWAITING_USER -> pending.askId?.let(::AwaitingUser) ?: Idle
                else -> Hidden
            }
        }
    }
}
