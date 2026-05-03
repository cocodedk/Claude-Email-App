package com.cocode.claudeemailapp.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Chat-bus presence per project (separate concern from worker tasks).
 * Wire values: "connected" / "disconnected" / "absent". Unknown values
 * deserialize to null so future server enum extensions don't crash older
 * clients.
 */
enum class AgentStatus {
    CONNECTED, DISCONNECTED, ABSENT;

    companion object {
        fun fromWire(value: String?): AgentStatus? = when (value) {
            "connected" -> CONNECTED
            "disconnected" -> DISCONNECTED
            "absent" -> ABSENT
            else -> null
        }
    }
}

internal object AgentStatusSerializer : KSerializer<AgentStatus?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AgentStatus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AgentStatus?) {
        encoder.encodeString(when (value) {
            AgentStatus.CONNECTED -> "connected"
            AgentStatus.DISCONNECTED -> "disconnected"
            AgentStatus.ABSENT -> "absent"
            null -> ""
        })
    }

    override fun deserialize(decoder: Decoder): AgentStatus? =
        AgentStatus.fromWire(decoder.decodeString())
}

@Serializable
data class ProjectSummary(
    val name: String,
    val path: String,
    @SerialName("running_task_id") val runningTaskId: Long? = null,
    @SerialName("queue_depth") val queueDepth: Int = 0,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("agent_status")
    @Serializable(with = AgentStatusSerializer::class)
    val agentStatus: AgentStatus? = null
)

@Serializable
data class ListProjectsResponse(
    val projects: List<ProjectSummary> = emptyList()
)
