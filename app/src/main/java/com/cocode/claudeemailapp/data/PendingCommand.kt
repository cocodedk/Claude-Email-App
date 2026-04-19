package com.cocode.claudeemailapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Local record of an outbound command awaiting a reply.
 *
 * Status transitions, driven by inbound envelope kinds matched via In-Reply-To:
 *
 *   awaiting_ack  ← on initial send, task_id unknown
 *     └─> queued       on kind=ack      (task_id assigned, branch populated)
 *     └─> error        on kind=error    (no task created; lastError set)
 *   queued/running
 *     └─> running      on kind=progress
 *     └─> awaiting_user on kind=question
 *     └─> done | failed on kind=result
 */
@Serializable
data class PendingCommand(
    val messageId: String,
    val sentAt: Long,
    val to: String,
    val subject: String,
    val kind: String,
    val bodyPreview: String,
    val taskId: Long? = null,
    val branch: String? = null,
    val status: String = PendingStatus.AWAITING_ACK,
    @SerialName("last_updated_at") val lastUpdatedAt: Long = sentAt,
    val lastError: String? = null,
    /** Set when the backend asks a question (kind=question); echo back as meta.ask_id on reply. */
    val askId: String? = null
)

object PendingStatus {
    const val AWAITING_ACK = "awaiting_ack"
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val AWAITING_USER = "awaiting_user"
    const val DONE = "done"
    const val FAILED = "failed"
    const val ERROR = "error"
}
