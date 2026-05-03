package com.cocode.claudeemailapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row in the Projects tab. Mirrors the `data.projects[]` shape that
 * `kind=list_projects` ack envelopes carry from the backend.
 *
 * Idle project → [runningTaskId] null, [queueDepth] 0, [lastActivityAt] null.
 */
@Serializable
data class ProjectSummary(
    val name: String,
    val path: String,
    @SerialName("running_task_id") val runningTaskId: Long? = null,
    @SerialName("queue_depth") val queueDepth: Int = 0,
    @SerialName("last_activity_at") val lastActivityAt: String? = null
)

@Serializable
data class ListProjectsResponse(
    val projects: List<ProjectSummary> = emptyList()
)
