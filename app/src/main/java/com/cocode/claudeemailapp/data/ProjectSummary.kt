package com.cocode.claudeemailapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectSummary(
    val name: String,
    val path: String,
    @SerialName("running_task_id") val runningTaskId: Long? = null,
    @SerialName("queue_depth") val queueDepth: Int = 0,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    /** Wire string per [com.cocode.claudeemailapp.protocol.AgentStatusValues]; null when missing. */
    @SerialName("agent_status") val agentStatus: String? = null
)

@Serializable
data class ListProjectsResponse(
    val projects: List<ProjectSummary> = emptyList()
)
