package com.cocode.claudeemailapp.app.steering

import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.protocol.Envelope
import com.cocode.claudeemailapp.protocol.Envelopes

/**
 * Translate a [SteeringIntent] into an outbound [Envelope] for the given
 * pending command. Returns null when the intent cannot be built with the
 * available data (e.g. Cancel/Reset without a known project, Reply without
 * a known task id) — the caller should treat this as a silent no-op.
 */
fun envelopeForSteering(
    pending: PendingCommand,
    intent: SteeringIntent,
    auth: String?
): Envelope? = when (intent) {
    SteeringIntent.Status -> Envelopes.status(
        taskId = pending.taskId,
        project = pending.project,
        auth = auth
    )
    SteeringIntent.Cancel -> pending.project?.let {
        Envelopes.cancel(project = it, drainQueue = false, auth = auth)
    }
    SteeringIntent.CancelDrainQueue -> pending.project?.let {
        Envelopes.cancel(project = it, drainQueue = true, auth = auth)
    }
    SteeringIntent.Reset -> pending.project?.let {
        Envelopes.reset(project = it, auth = auth)
    }
    is SteeringIntent.Reply -> pending.taskId?.let { taskId ->
        Envelopes.reply(
            taskId = taskId,
            body = intent.body,
            askId = intent.askId.toLongOrNull(),
            auth = auth
        )
    }
}
